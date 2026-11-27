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

function Check([string]$name, [bool]$ok, [string]$detail = '') {
	if ($ok) { $script:passed++; Write-Host "  ok   $name" }
	else { $script:failures += $name; Write-Host "  FAIL $name $detail" -ForegroundColor Red }
}

function New-TestKit {
	$root = Join-Path $env:RUNNER_TEMP ("kit-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
	if (-not $env:RUNNER_TEMP) { $root = Join-Path ([IO.Path]::GetTempPath()) ("kit-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8)) }
	$kit = Join-Path $root 'kit'
	$profileDir = Join-Path $root 'profile'
	New-Item -ItemType Directory -Force -Path (Join-Path $kit 'lib\client'), (Join-Path $kit 'lib\dev'), (Join-Path $kit 'lib\isolation'), (Join-Path $kit 'official'), (Join-Path $profileDir '.runelite') | Out-Null
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
		$ok = Invoke-Isolated $run { }
		Check 'an incomplete check fails closed' ($ok -eq $false)
	}
	finally { & icacls $locked /remove:d "${env:USERNAME}" | Out-Null }

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

	Write-Host "no unconditional reassurance"
	$text = Get-Content -Raw $Launcher
	Check 'no "Nothing was changed outside this folder"' (-not $text.Contains('Nothing was changed outside this folder'))
	Check 'no "never read or written"' (-not $text.Contains('never read or written'))
}
finally {
	$env:USERPROFILE = $realProfile
	Remove-Item Env:\RACCOON_PLAYTEST_NO_MAIN -ErrorAction SilentlyContinue
}

Write-Host ("launcher tests: {0} passed, {1} failed" -f $script:passed, $script:failures.Count)
if ($script:failures.Count -gt 0) { exit 1 }
exit 0
