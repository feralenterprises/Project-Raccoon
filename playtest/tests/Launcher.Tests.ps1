<#
FR-006 kit review (a0ef3e4c): launcher safety regressions, Windows PowerShell 5.1, no Pester.
Each case dot-sources a fresh copy of Raccoon-Playtest.ps1 inside a scratch kit with a scratch
%USERPROFILE%, so nothing outside the scratch folders is touched (except one test-only registry
key under HKCU\Software\JavaSoft\Prefs\raccoon-kit-test, removed afterwards).
usage: powershell -NoProfile -ExecutionPolicy Bypass -File Launcher.Tests.ps1 <path to Raccoon-Playtest.ps1>
#>
param([Parameter(Mandatory = $true)][string]$Launcher)

$ErrorActionPreference = 'Stop'
$script:failures = @()
$script:passed = 0
$realProfile = $env:USERPROFILE
$realLocal = $env:LOCALAPPDATA
$realRoaming = $env:APPDATA

function Check([string]$name, [bool]$ok, [string]$detail = '') {
	if ($ok) { $script:passed++; Write-Host "  ok   $name" }
	else { $script:failures += $name; Write-Host "  FAIL $name $detail" -ForegroundColor Red }
}

function New-TestKit {
	$root = Join-Path $env:RUNNER_TEMP ("kit-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
	if (-not $env:RUNNER_TEMP) { $root = Join-Path ([IO.Path]::GetTempPath()) ("kit-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8)) }
	$kit = Join-Path $root 'kit'
	$profileDir = Join-Path $root 'profile'
	New-Item -ItemType Directory -Force -Path (Join-Path $kit 'lib\client'), (Join-Path $kit 'lib\dev'), (Join-Path $kit 'lib\isolation'), (Join-Path $kit 'official'), (Join-Path $profileDir '.runelite'), (Join-Path $root 'localappdata'), (Join-Path $root 'appdata') | Out-Null
	Copy-Item $Launcher (Join-Path $kit 'Raccoon-Playtest.ps1')
	Set-Content -Path (Join-Path $kit 'lib\client\client.jar') -Value 'c'
	Set-Content -Path (Join-Path $kit 'lib\dev\dev.jar') -Value 'd'
	Set-Content -Path (Join-Path $kit 'lib\isolation\iso.jar') -Value 'i'
	Set-Content -Path (Join-Path $kit 'official\project-raccoon.jar') -Value 'o'
	$files = Get-ChildItem $kit -Recurse -File | Sort-Object FullName | ForEach-Object {
		[ordered]@{ path = $_.FullName.Substring($kit.Length + 1); sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
	}
	$m = [ordered]@{ candidate = 'test'; kitCommit = 'test'; kind = 'test'; official = [ordered]@{ sha256 = 'x'; bytes = 1 }; files = @($files) }
	$m | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $kit 'kit-manifest.json')
	return [pscustomobject]@{ root = $root; kit = $kit; profile = $profileDir }
}

function Set-KitEnv($t) {
	# the launcher must be dot-sourced at script scope (a function scope would drop its functions)
	$env:USERPROFILE = $t.profile
	$env:LOCALAPPDATA = Join-Path $t.root 'localappdata'
	$env:APPDATA = Join-Path $t.root 'appdata'
	$env:RACCOON_PLAYTEST_NO_MAIN = '1'
}

function Try-Call([scriptblock]$b) {
	try { & $b; return $null } catch { return $_.Exception.Message }
}

try {
	Write-Host "isolation failure fails closed and latches further launches"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$run = New-RunDir 'T'
	$ok = Invoke-Isolated $run { Set-Content -Path (Join-Path $env:USERPROFILE '.runelite\unexpected.txt') -Value 'x' }
	Check 'Invoke-Isolated returns false on an unexpected write' ($ok -eq $false)
	Check 'a latch file is written' (Test-Path (Join-Path $t.kit 'KIT-STOPPED.txt'))
	$err = Try-Call { Assert-NotLatched }
	Check 'further launches are refused while latched' ($null -ne $err)
	$code = Invoke-R1
	Check 'a step after a latch returns non-zero without launching' ($code -ne 0)

	Write-Host "an unreadable watched location makes the check incomplete (fail closed)"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$locked = Join-Path $t.profile '.runelite\locked'
	New-Item -ItemType Directory -Force -Path (Join-Path $locked 'inner') | Out-Null
	Set-Content -Path (Join-Path $locked 'inner\f.txt') -Value 'x'
	& icacls $locked /deny "${env:USERNAME}:(OI)(CI)(RD)" | Out-Null
	try {
		$snap = Get-Snapshot (Join-Path $t.profile '.runelite')
		Check 'snapshot reports incomplete on an access error' ($snap.complete -eq $false)
		$run = New-RunDir 'T'
		$script:bodyRan = $false
		$ok = Invoke-Isolated $run { $script:bodyRan = $true }
		Check 'an incomplete check fails closed' ($ok -eq $false)
		Check 'an incomplete preflight does not run the step' (-not $script:bodyRan)
		Check 'an incomplete preflight latches the kit' (Test-Path (Join-Path $t.kit 'KIT-STOPPED.txt'))
	}
	finally { & icacls $locked /remove:d "${env:USERNAME}" | Out-Null }

	Write-Host "a throwing preflight snapshot does not run the step and latches (review of b428f283)"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	function Get-ProfileSnapshot { throw 'simulated preflight failure' }
	$run = New-RunDir 'T'
	$script:bodyRan = $false
	$ok = $null
	$err = Try-Call { $script:okResult = Invoke-Isolated $run { $script:bodyRan = $true } }
	Check 'a throwing preflight is handled, not escaped' ($null -eq $err) $err
	Check 'a throwing preflight returns false' ($script:okResult -eq $false)
	Check 'a throwing preflight does not run the step' (-not $script:bodyRan)
	Check 'a throwing preflight latches the kit' (Test-Path (Join-Path $t.kit 'KIT-STOPPED.txt'))
	Check 'a throwing preflight still writes the isolation receipt' (Test-Path (Join-Path $run 'isolation.json'))

	Write-Host "a throwing postflight snapshot latches and fails the step"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$script:snapCalls = 0
	function Get-ProfileSnapshot {
		$script:snapCalls++
		if ($script:snapCalls -ge 2) { throw 'simulated postflight failure' }
		return [pscustomobject]@{ parts = [ordered]@{ x = 'a' }; complete = $true }
	}
	$run = New-RunDir 'T'
	$script:bodyRan = $false
	$err = Try-Call { $script:okResult = Invoke-Isolated $run { $script:bodyRan = $true } }
	Check 'a throwing postflight is handled, not escaped' ($null -eq $err) $err
	Check 'the step ran before the postflight' $script:bodyRan
	Check 'a throwing postflight returns false' ($script:okResult -eq $false)
	Check 'a throwing postflight latches the kit' (Test-Path (Join-Path $t.kit 'KIT-STOPPED.txt'))

	Write-Host "a step that throws fails, keeps its isolation receipt and never reports PASS"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$run = New-RunDir 'T'
	$err = Try-Call { $script:okResult = Invoke-Isolated $run { throw 'simulated step failure' } }
	Check 'a throwing step is handled, not escaped' ($null -eq $err) $err
	Check 'a throwing step returns false' ($script:okResult -eq $false)
	$isoFile = Join-Path $run 'isolation.json'
	$iso = $(if (Test-Path $isoFile) { Get-Content -Raw $isoFile | ConvertFrom-Json } else { $null })
	Check 'a throwing step keeps the isolation receipt' ($null -ne $iso)
	Check 'a throwing step is not recorded as PASS' (($null -ne $iso) -and ($iso.result -ne 'PASS'))
	Check 'a throwing step records its error' (($null -ne $iso) -and ("$($iso.stepError)" -like '*simulated step failure*'))

	Write-Host "Windows Credential Manager: RuneLite SessionStore key is watched (auth investigation)"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$script:credCalls = 0
	function Get-SessionStoreCredential { $script:credCalls++; return [pscustomobject]@{ present = ($script:credCalls -ge 2); complete = $true } }
	$run = New-RunDir 'T'
	$script:okResult = Invoke-Isolated $run { }
	Check 'a credential created during a no-login step fails isolation' ($script:okResult -eq $false)
	Check 'a credential created during a no-login step latches' (Test-Path (Join-Path $t.kit 'KIT-STOPPED.txt'))

	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	function Get-SessionStoreCredential { return [pscustomobject]@{ present = $false; complete = $false } }
	$run = New-RunDir 'T'
	$script:bodyRan = $false
	$script:okResult = Invoke-Isolated $run { $script:bodyRan = $true }
	Check 'an unreadable credential state stops the step before it runs' ((-not $script:bodyRan) -and ($script:okResult -eq $false))

	Write-Host "a new top-level folder in LOCALAPPDATA or APPDATA (sign-in browser data elsewhere) latches"
	$t = New-TestKit
	Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$run = New-RunDir 'T'
	$script:okResult = Invoke-Isolated $run { New-Item -ItemType Directory -Path (Join-Path $env:LOCALAPPDATA 'runelite_jagexauth.exe.WebView2') | Out-Null } @('windowsCredential')
	Check 'a new LOCALAPPDATA top-level folder fails isolation even in R3' ($script:okResult -eq $false)
	$run = New-RunDir 'T2'
	Remove-Item -LiteralPath (Join-Path $t.kit 'KIT-STOPPED.txt') -Force
	$script:okResult = Invoke-Isolated $run { New-Item -ItemType Directory -Path (Join-Path $env:APPDATA 'SomethingNew') | Out-Null }
	Check 'a new APPDATA top-level folder fails isolation' ($script:okResult -eq $false)

	Write-Host "R3 sign-in: only the absent-to-present key is an expected, recorded change"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$script:credCalls = 0
	function Get-SessionStoreCredential { $script:credCalls++; return [pscustomobject]@{ present = ($script:credCalls -ge 2); complete = $true } }
	$run = New-RunDir 'T'
	$script:okResult = Invoke-Isolated $run { } @('windowsCredential')
	Check 'the expected key creation passes when allowed' ($script:okResult -eq $true)
	Check 'the kit records that it created the key' (Test-Path (Join-Path $t.kit 'KIT-CREATED-CREDENTIAL.txt'))
	$iso = Get-Content -Raw (Join-Path $run 'isolation.json') | ConvertFrom-Json
	Check 'the isolation receipt lists the expected change' (@($iso.expectedChanges) -contains 'windowsCredential')

	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$script:credCalls = 0
	function Get-SessionStoreCredential { $script:credCalls++; return [pscustomobject]@{ present = ($script:credCalls -ge 2); complete = $true } }
	$run = New-RunDir 'T'
	$script:okResult = Invoke-Isolated $run { Set-Content -Path (Join-Path $env:USERPROFILE '.runelite\x.txt') -Value 'x' } @('windowsCredential')
	Check 'the allowance never covers other changes' ($script:okResult -eq $false)

	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$script:credCalls = 0
	function Get-SessionStoreCredential { $script:credCalls++; return [pscustomobject]@{ present = ($script:credCalls -lt 2); complete = $true } }
	$run = New-RunDir 'T'
	$script:okResult = Invoke-Isolated $run { } @('windowsCredential')
	Check 'a removed key is never an expected change' ($script:okResult -eq $false)

	Write-Host "only R3 is given the allowance"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$script:allowSeen = @{}
	function Invoke-Isolated([string]$runDir, [scriptblock]$body, [string[]]$allowCreated = @()) { $script:allowSeen[(Split-Path $runDir -Leaf).Split('-')[0]] = (@($allowCreated) -join ','); return $true }
	function Invoke-Evaluate([string]$runDir, [string[]]$evalArgs) { return 0 }
	function Initialize-Sideload($m, [string]$run) { return (Join-Path $run 'client.log') }
	[void](Invoke-R2); [void](Invoke-R3); [void](Invoke-R4)
	Check 'R3 allows only the SessionStore key' ($script:allowSeen['R3'] -eq 'windowsCredential') ("seen: " + (($script:allowSeen.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '; '))
	Check 'R2 has no allowance' ($script:allowSeen['R2'] -eq '')
	Check 'R4 has no allowance' ($script:allowSeen['R4'] -eq '')

	Write-Host "reset removes the key only when the kit created it"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$script:removed = 0
	function Remove-SessionStoreCredential { $script:removed++ }
	function Get-SessionStoreCredential { return [pscustomobject]@{ present = ($script:removed -eq 0); complete = $true } }
	[void](Invoke-Reset)
	Check 'without the kit marker the key is left alone' ($script:removed -eq 0)
	Set-Content -Path (Join-Path $t.kit 'KIT-CREATED-CREDENTIAL.txt') -Value 'created'
	$code = Invoke-Reset
	Check 'with the kit marker the key is removed' ($script:removed -eq 1)
	Check 'the marker is cleared after removal' (-not (Test-Path (Join-Path $t.kit 'KIT-CREATED-CREDENTIAL.txt')))

	Write-Host "Java preferences are compared by value, not by key count"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$key = 'HKCU:\Software\JavaSoft\Prefs\raccoon-kit-test'
	New-Item -Path $key -Force | Out-Null
	try {
		New-ItemProperty -Path $key -Name 'k' -Value 'one' -Force | Out-Null
		$a = Get-JavaPrefsDigest
		Set-ItemProperty -Path $key -Name 'k' -Value 'two'
		$b = Get-JavaPrefsDigest
		Check 'a changed preference value changes the digest' ($a.digest -ne $b.digest)
	}
	finally { Remove-Item -Path $key -Recurse -Force }

	Write-Host "integrity covers unlisted files and classpaths use listed JARs only"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	Set-Content -Path (Join-Path $t.kit 'lib\client\extra.jar') -Value 'e'
	$bad = Test-KitIntegrity (Read-Manifest)
	Check 'an unlisted file fails integrity' (@($bad | Where-Object { $_ -like 'unlisted*extra.jar*' }).Count -eq 1) ($bad -join '; ')
	$cp = Get-KitClasspath (Read-Manifest) @('lib\client', 'lib\isolation')
	Check 'the classpath excludes an unlisted JAR' (-not ($cp -like '*extra.jar*'))
	Check 'the classpath lists explicit JARs, no wildcard' (($cp -like '*client.jar*') -and -not ($cp -like '*`**'))

	Write-Host "a residual side-loaded JAR is removed before R2/R3"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$side = Join-Path $t.kit 'home\.runelite\sideloaded-plugins'
	New-Item -ItemType Directory -Force -Path $side | Out-Null
	Set-Content -Path (Join-Path $side 'project-raccoon.jar') -Value 'old'
	Clear-SideloadDir
	Check 'the side-load folder is empty' (@(Get-ChildItem -LiteralPath $side -Force).Count -eq 0)

	Write-Host "deletes and side-load writes refuse reparse points"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$outside = Join-Path $t.root 'outside'
	New-Item -ItemType Directory -Force -Path $outside | Out-Null
	Set-Content -Path (Join-Path $outside 'sentinel.txt') -Value 'keep'
	New-Item -ItemType Directory -Force -Path (Join-Path $t.kit 'home\.runelite') | Out-Null
	& cmd /c mklink /J (Join-Path $t.kit 'home\.runelite\sideloaded-plugins') $outside | Out-Null
	$err = Try-Call { Clear-SideloadDir }
	Check 'side-load cleanup refuses a junction' ($null -ne $err)
	$code = Invoke-Reset
	Check 'reset refuses a junction inside the kit home' ($code -ne 0)
	Check 'the junction target is untouched' (Test-Path (Join-Path $outside 'sentinel.txt'))

	Write-Host "client steps name their run folder after the step (CI R2 attempt at 3409297a)"
	$t = New-TestKit; Set-KitEnv $t; . (Join-Path $t.kit 'Raccoon-Playtest.ps1') -NonInteractive
	$code = Invoke-R2
	$dirs = @(Get-ChildItem -LiteralPath (Join-Path $t.kit 'receipts') -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
	Check 'an R2 run folder R2-<UTC> is created' (@($dirs | Where-Object { $_ -match '^R2-\d{8}-\d{6}$' }).Count -eq 1) ($dirs -join ', ')
	Check 'R2 without a Java runtime reports failure' ($code -ne 0)

	Write-Host "no unconditional reassurance"
	$text = Get-Content -Raw $Launcher
	Check 'no "Nothing was changed outside this folder"' (-not $text.Contains('Nothing was changed outside this folder'))
	Check 'no "never read or written"' (-not $text.Contains('never read or written'))
}
finally {
	$env:USERPROFILE = $realProfile
	$env:LOCALAPPDATA = $realLocal
	$env:APPDATA = $realRoaming
	Remove-Item Env:\RACCOON_PLAYTEST_NO_MAIN -ErrorAction SilentlyContinue
}

Write-Host ("launcher tests: {0} passed, {1} failed" -f $script:passed, $script:failures.Count)
if ($script:failures.Count -gt 0) { exit 1 }
exit 0
