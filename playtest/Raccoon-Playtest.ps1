<#
Project Raccoon - FR-006 Windows playtest launcher (verification kit, not a product).

Everything runs from this folder: its own Java runtime, its own pinned RuneLite 1.13.8 developer
client, and an isolated RuneLite home (.\home) and temp folder (.\tmp). The kit is designed not
to use your normal RuneLite folder; it checks the watched locations before and after every client
step, and if a check fails or cannot be completed the kit stops itself (KIT-STOPPED.txt) and
refuses further client launches until the reviewer has looked at it.

Steps
  SelfCheck  verify every kit file against kit-manifest.json (and that nothing unlisted is present)
  R1         deterministic regression replay (all 20 permanent suites; no game client)
  R2         native adapter replay with synthetic fixtures on the genuine client thread
             (client opens, reaches the login screen, runs, closes itself; no login needed)
  R3         genuine observations: you log in and play; the witness only observes
  R4         the exact official JAR in the genuine client: start and stop it twice
  Collect    zip all receipts for review
  Reset      delete the isolated home and temp folders (signs you out of the kit client)
  Smoke      automation only (CI): launch the R4 client unattended for a limited time
#>
[CmdletBinding()]
param(
	[ValidateSet('Menu', 'SelfCheck', 'R1', 'R2', 'R3', 'R4', 'Collect', 'Reset', 'Smoke')]
	[string]$Step = 'Menu',
	[switch]$NonInteractive,
	[int]$SmokeSeconds = 240
)

$ErrorActionPreference = 'Stop'
$Kit = Split-Path -Parent $MyInvocation.MyCommand.Path
$ManifestPath = Join-Path $Kit 'kit-manifest.json'
$Latch = Join-Path $Kit 'KIT-STOPPED.txt'
$Java = Join-Path $Kit 'runtime\bin\java.exe'
$IsoHome = Join-Path $Kit 'home'
$IsoTmp = Join-Path $Kit 'tmp'
$Receipts = Join-Path $Kit 'receipts'
$Sideload = Join-Path $IsoHome '.runelite\sideloaded-plugins'
$IsoLogs = Join-Path $IsoHome '.runelite\logs'
$Official = Join-Path $Kit 'official\project-raccoon.jar'

# RuneLite's developer Jagex sign-in (1.13.8 rs/auth/SessionStore) keeps its session file in the
# isolated .runelite folder, encrypted with a key it stores in Windows Credential Manager under this
# generic target (machine persistence), outside the kit. The kit watches its presence only (names,
# never secrets) and never reads it.
$CredentialTarget = 'RuneLite SessionStore'
$CredentialMarker = Join-Path $Kit 'KIT-CREATED-CREDENTIAL.txt'

# files the kit itself creates while running; everything else must be in the manifest
$RuntimeOwned = @('kit-manifest.json', 'KIT-STOPPED.txt', 'KIT-CREATED-CREDENTIAL.txt')
$RuntimeOwnedDirs = @('receipts', 'home', 'tmp')

function Say([string]$text, [string]$color = 'Gray') { Write-Host $text -ForegroundColor $color }

# ------------------------------------------------------------------ latch (fail closed)

function Test-Latched { return (Test-Path -LiteralPath $Latch) }

function Assert-NotLatched {
	if (Test-Latched) {
		throw "The kit stopped itself after a safety check failed or could not be completed (see KIT-STOPPED.txt). Send that file and the receipts to the reviewer. Do not run further steps."
	}
}

function Set-Latch([string]$reason) {
	$text = @(
		"The Project Raccoon playtest kit stopped itself at $((Get-Date).ToUniversalTime().ToString('u')).",
		"Reason: $reason",
		"No further test-client launches are allowed from this folder.",
		"Send this file and the receipts zip (menu option 6) to the reviewer."
	)
	Set-Content -LiteralPath $Latch -Encoding UTF8 -Value $text
}

# ------------------------------------------------------------------ path safety

function Assert-SafePath([string]$path) {
	# the path must stay inside the kit, and neither it nor any existing parent up to the kit may
	# be a junction, symbolic link or other reparse point
	$full = [System.IO.Path]::GetFullPath($path)
	$kitFull = [System.IO.Path]::GetFullPath($Kit).TrimEnd('\')
	if (-not $full.StartsWith($kitFull + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
		throw "Refusing to use a path outside the kit folder: $full"
	}
	$p = $full
	while ($p.Length -ge $kitFull.Length) {
		if (Test-Path -LiteralPath $p) {
			$item = Get-Item -LiteralPath $p -Force
			if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
				throw "Refusing to use $p because it is a junction or link (reparse point). Extract the kit to a normal folder such as C:\RaccoonPlaytest."
			}
		}
		$parent = Split-Path -Parent $p
		if (-not $parent -or $parent -eq $p) { break }
		$p = $parent
	}
}

function Assert-NoReparseInside([string]$dir) {
	Assert-SafePath $dir
	if (-not (Test-Path -LiteralPath $dir)) { return }
	$errs = $null
	$links = @(Get-ChildItem -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue -ErrorVariable errs |
		Where-Object { $_.Attributes -band [System.IO.FileAttributes]::ReparsePoint })
	if (@($errs).Count -gt 0) { throw "Could not fully inspect $dir before changing it; nothing was deleted." }
	if ($links.Count -gt 0) { throw "Refusing to change ${dir}: it contains a junction or link ($($links[0].FullName))." }
}

function Remove-KitContents([string]$dir) {
	Assert-NoReparseInside $dir
	if (Test-Path -LiteralPath $dir) {
		Get-ChildItem -LiteralPath $dir -Force | Remove-Item -Recurse -Force
	}
}

# ------------------------------------------------------------------ manifest, integrity, classpaths

function Read-Manifest {
	if (-not (Test-Path $ManifestPath)) { throw "kit-manifest.json is missing: this folder is not a complete playtest kit." }
	return Get-Content $ManifestPath -Raw | ConvertFrom-Json
}

function Test-KitIntegrity($manifest) {
	$bad = @()
	$listed = @{}
	foreach ($f in $manifest.files) {
		$listed[$f.path.ToLowerInvariant()] = $true
		$p = Join-Path $Kit $f.path
		if (-not (Test-Path -LiteralPath $p)) { $bad += "missing $($f.path)"; continue }
		$h = (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()
		if ($h -ne $f.sha256) { $bad += "changed $($f.path)" }
	}
	$errs = $null
	$present = @(Get-ChildItem -LiteralPath $Kit -Recurse -Force -File -ErrorAction SilentlyContinue -ErrorVariable errs)
	if (@($errs).Count -gt 0) { $bad += "could not list every kit file" }
	foreach ($item in $present) {
		$rel = $item.FullName.Substring($Kit.Length + 1)
		$top = ($rel -split '\\')[0]
		if ($RuntimeOwnedDirs -contains $top) { continue }
		if ($RuntimeOwned -contains $rel) { continue }
		if ($rel -like 'raccoon-receipts-*.zip') { continue }
		if (-not $listed.ContainsKey($rel.ToLowerInvariant())) { $bad += "unlisted $rel" }
	}
	return ,$bad
}

function Get-KitClasspath($manifest, [string[]]$dirs) {
	# explicit JARs from the manifest only, in manifest order; never a wildcard
	$jars = @()
	foreach ($d in $dirs) {
		foreach ($f in $manifest.files) {
			if ($f.path.StartsWith($d + '\', [System.StringComparison]::OrdinalIgnoreCase) -and $f.path.EndsWith('.jar')) {
				$jars += (Join-Path $Kit $f.path)
			}
		}
	}
	if ($jars.Count -eq 0) { throw "No JARs listed for $($dirs -join ', ')." }
	return ($jars -join ';')
}

# ------------------------------------------------------------------ isolation evidence

function Get-Snapshot([string]$dir) {
	if (-not (Test-Path -LiteralPath $dir)) { return [pscustomobject]@{ exists = $false; files = 0; digest = 'absent'; complete = $true } }
	$errs = $null
	$rows = Get-ChildItem -LiteralPath $dir -Recurse -Force -File -ErrorAction SilentlyContinue -ErrorVariable errs |
		Sort-Object FullName |
		ForEach-Object { '{0}|{1}|{2}' -f $_.FullName.Substring($dir.Length), $_.Length, $_.LastWriteTimeUtc.Ticks }
	$text = (@($rows) -join "`n")
	$sha = [System.Security.Cryptography.SHA256]::Create()
	$digest = -join ($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($text)) | ForEach-Object { $_.ToString('x2') })
	return [pscustomobject]@{ exists = $true; files = @($rows).Count; digest = $digest; complete = (@($errs).Count -eq 0) }
}

function Get-SessionStoreCredential {
	# presence only: cmdkey lists credential names, never their secrets
	try {
		$out = & cmdkey.exe "/list:$CredentialTarget"
		if ($LASTEXITCODE -ne 0) { return [pscustomobject]@{ present = $false; complete = $false } }
		$present = @($out | Where-Object { "$_" -match [regex]::Escape("target=$CredentialTarget") }).Count -gt 0
		return [pscustomobject]@{ present = $present; complete = $true }
	}
	catch { return [pscustomobject]@{ present = $false; complete = $false } }
}

function Remove-SessionStoreCredential {
	& cmdkey.exe "/delete:$CredentialTarget" | Out-Null
}

function Get-JavaPrefsDigest {
	# every key, value name, value kind and value data under the shared Java preferences key
	$key = 'HKCU:\Software\JavaSoft\Prefs'
	if (-not (Test-Path $key)) { return [pscustomobject]@{ digest = 'absent'; complete = $true; keys = 0 } }
	$errs = $null
	$keys = @(Get-Item $key) + @(Get-ChildItem $key -Recurse -ErrorAction SilentlyContinue -ErrorVariable errs)
	$lines = foreach ($k in ($keys | Sort-Object Name)) {
		"[" + $k.Name + "]"
		foreach ($n in ($k.GetValueNames() | Sort-Object)) {
			'{0}={1}:{2}' -f $n, $k.GetValueKind($n), (@($k.GetValue($n)) -join ',')
		}
	}
	$sha = [System.Security.Cryptography.SHA256]::Create()
	$digest = -join ($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes((@($lines) -join "`n"))) | ForEach-Object { $_.ToString('x2') })
	return [pscustomobject]@{ digest = $digest; complete = (@($errs).Count -eq 0); keys = $keys.Count }
}

# The injected game client also reads %USERPROFILE% (not only user.home), so the profile's
# top-level entries and the classic Jagex cache locations are watched as well.
function Get-ProfileSnapshot {
	$profileDir = $env:USERPROFILE
	$parts = [ordered]@{}
	$complete = $true
	foreach ($d in @('.runelite', 'jagexcache')) {
		$s = Get-Snapshot (Join-Path $profileDir $d)
		$parts[$d] = $s.digest
		$complete = $complete -and $s.complete
	}
	foreach ($f in @('jagex_cl_oldschool_LIVE.dat', 'random.dat', '.jagex_cache_32')) {
		$p = Join-Path $profileDir $f
		$parts[$f] = $(if (Test-Path -LiteralPath $p) { $i = Get-Item -LiteralPath $p -Force; '{0}|{1}' -f $i.Length, $i.LastWriteTimeUtc.Ticks } else { 'absent' })
	}
	$errs = $null
	$top = @(Get-ChildItem -LiteralPath $profileDir -Force -ErrorAction SilentlyContinue -ErrorVariable errs |
		Where-Object { $_.Name -notmatch '^(ntuser|NTUSER)' } | ForEach-Object { $_.Name } | Sort-Object)
	$complete = $complete -and (@($errs).Count -eq 0)
	$parts['profileTopLevel'] = ($top -join '|')
	# the developer Jagex sign-in runs a WebView2 window; a browser data folder created outside the
	# kit would appear as a new top-level folder here (names only)
	foreach ($pair in @(@('localAppDataTopLevel', $env:LOCALAPPDATA), @('appDataTopLevel', $env:APPDATA))) {
		if (-not $pair[1]) { $parts[$pair[0]] = 'unset'; continue }
		$errs = $null
		$names = @(Get-ChildItem -LiteralPath $pair[1] -Force -ErrorAction SilentlyContinue -ErrorVariable errs | ForEach-Object { $_.Name } | Sort-Object)
		$complete = $complete -and (@($errs).Count -eq 0)
		$parts[$pair[0]] = ($names -join '|')
	}
	$prefs = Get-JavaPrefsDigest
	$parts['javaPreferences'] = $prefs.digest
	$complete = $complete -and $prefs.complete
	$cred = Get-SessionStoreCredential
	$parts['windowsCredential'] = $(if ($cred.present) { 'present' } else { 'absent' })
	$complete = $complete -and $cred.complete
	return [pscustomobject]@{ parts = $parts; complete = $complete }
}

function Compare-ProfileSnapshot($before, $after) {
	$changed = @()
	foreach ($k in $before.parts.Keys) { if ($before.parts[$k] -ne $after.parts[$k]) { $changed += $k } }
	foreach ($k in @('profileTopLevel', 'localAppDataTopLevel', 'appDataTopLevel')) {
		if ($before.parts[$k] -ne $after.parts[$k]) {
			$b = @($before.parts[$k] -split '\|' | Where-Object { $_ }); $a = @($after.parts[$k] -split '\|' | Where-Object { $_ })
			$added = @($a | Where-Object { $b -notcontains $_ }).Count; $removed = @($b | Where-Object { $a -notcontains $_ }).Count
			$changed += ("{0}: {1} added, {2} removed" -f $k, $added, $removed)
		}
	}
	return ,$changed
}

function Assert-NoRuneLiteRunning {
	$running = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(RuneLite|JagexLauncher)$' })
	if ($running.Count -gt 0) {
		if ($NonInteractive) { throw "RuneLite or the Jagex Launcher is running; close it first." }
		Say "Please close RuneLite and the Jagex Launcher first (they could change the watched folders during the test)." Yellow
		Read-Host "Press Enter when they are closed" | Out-Null
		Assert-NoRuneLiteRunning
	}
}

function New-RunDir([string]$name) {
	Assert-SafePath $Receipts
	$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')
	$dir = Join-Path $Receipts "$name-$stamp"
	New-Item -ItemType Directory -Force -Path $dir | Out-Null
	return $dir
}

function Get-JvmArgs($manifest, [string]$runDir) {
	Assert-SafePath $IsoHome
	Assert-SafePath $IsoTmp
	New-Item -ItemType Directory -Force -Path $IsoHome, $IsoTmp | Out-Null
	return @(
		'-ea', '-Xmx1024m',
		"-Duser.home=$IsoHome",
		"-Djava.io.tmpdir=$IsoTmp",
		# Java preferences in files inside the kit instead of the shared registry key
		'-Djava.util.prefs.PreferencesFactory=playtest.isolation.KitPreferencesFactory',
		"-Draccoon.playtest.prefs.dir=$(Join-Path $IsoHome 'java-prefs')",
		"-Draccoon.playtest.receipts=$runDir",
		"-Draccoon.playtest.candidate=$($manifest.candidate)",
		"-Draccoon.playtest.kit=$($manifest.kitCommit)",
		"-Draccoon.playtest.kit.dir=$Kit",
		"-Draccoon.playtest.kind=$($manifest.kind)"
	)
}

# Runs $body between two snapshots of the watched locations, failing closed:
# - preflight snapshot throws or is incomplete: the body is NOT run; the kit latches
# - the body throws: the step fails (never PASS); the postflight still runs
# - postflight snapshot throws or is incomplete, or anything changed: the kit latches
# The isolation receipt is written in every case. Returns $true only when the preflight and
# postflight were complete, nothing changed and the body completed without an exception.
# $allowCreated names snapshot parts whose absent-to-present change is expected for this step
# (R3 only: 'windowsCredential', RuneLite creating its sign-in key); every other change latches.
function Invoke-Isolated([string]$runDir, [scriptblock]$body, [string[]]$allowCreated = @()) {
	Assert-NotLatched
	Assert-NoRuneLiteRunning
	$before = $null; $after = $null
	$preflightError = $null; $postflightError = $null; $bodyError = $null
	$bodyRan = $false
	try { $before = Get-ProfileSnapshot }
	catch { $preflightError = $_.Exception.Message }
	$preflightOk = ($null -eq $preflightError) -and ($null -ne $before) -and $before.complete
	if ($preflightOk) {
		$bodyRan = $true
		try { & $body | Out-Host }
		catch { $bodyError = $_.Exception.Message }
		try { $after = Get-ProfileSnapshot }
		catch { $postflightError = $_.Exception.Message }
	}
	$postflightOk = $preflightOk -and ($null -eq $postflightError) -and ($null -ne $after) -and $after.complete
	$changes = @()
	$expected = @()
	if ($postflightOk) {
		foreach ($c in (Compare-ProfileSnapshot $before $after)) {
			if (($allowCreated -contains $c) -and $before.parts[$c] -eq 'absent' -and $after.parts[$c] -eq 'present') { $expected += $c }
			else { $changes += $c }
		}
	}
	$isolationOk = $postflightOk -and ($changes.Count -eq 0)
	$ok = $isolationOk -and ($null -eq $bodyError)
	$result = $(if (-not $preflightOk) { 'PREFLIGHT_FAILED' } elseif (-not $postflightOk) { 'INCOMPLETE' } elseif ($changes.Count -gt 0) { 'FAIL' } elseif ($bodyError) { 'STEP_FAILED' } else { 'PASS' })
	$iso = [ordered]@{
		watched = '%USERPROFILE%: .runelite, jagexcache, jagex_cl_oldschool_LIVE.dat, random.dat, .jagex_cache_32, top-level entries; HKCU\Software\JavaSoft\Prefs (keys and values); Windows Credential Manager RuneLite SessionStore (presence); %LOCALAPPDATA% and %APPDATA% top-level folder names'
		preflightComplete = $preflightOk
		stepRan = $bodyRan
		postflightComplete = $postflightOk
		changes = @($changes)
		expectedChanges = @($expected)
		isolatedHome = '<kit>\home'; isolatedTemp = '<kit>\tmp'
		preflightError = $preflightError
		stepError = $bodyError
		postflightError = $postflightError
		result = $result
	}
	try { $iso | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $runDir 'isolation.json') }
	catch { $isolationOk = $false; $ok = $false; Say "The isolation receipt could not be written: $($_.Exception.Message)" Red }
	if ($isolationOk -and ($expected -contains 'windowsCredential')) {
		Set-Content -LiteralPath $CredentialMarker -Encoding UTF8 -Value @(
			"RuneLite created its Jagex sign-in encryption key ('$CredentialTarget') in Windows Credential Manager during $(Split-Path $runDir -Leaf).",
			"Menu option 7 removes it (only because this kit created it).")
		Say "Expected: RuneLite created its sign-in encryption key ('$CredentialTarget') in Windows Credential Manager. Menu option 7 removes it." Yellow
	}
	if ($isolationOk) {
		Say "Isolation check: no unexpected change detected in the watched locations." Green
	}
	else {
		$reason = $(if (-not $preflightOk) { 'the isolation check before the step could not be completed, so the step was not run' + $(if ($preflightError) { " ($preflightError)" } else { '' }) }
			elseif (-not $postflightOk) { 'the isolation check after the step could not be completed' + $(if ($postflightError) { " ($postflightError)" } else { '' }) }
			else { 'a watched location changed: ' + ($changes -join '; ') })
		Set-Latch $reason
		Say "STOPPED: $reason." Red
		Say "The kit will not launch the test client again. Send KIT-STOPPED.txt and the receipts to the reviewer." Red
	}
	if ($bodyError) { Say "The step failed: $bodyError" Red }
	return $ok
}

function Start-Client([string[]]$argList, [string]$workDir, [int]$timeoutSeconds, [string]$stopWhenLog, [string]$stopPattern) {
	# runs a client JVM with a time limit, so an unattended or failed run can never hang
	$quoted = ($argList | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
	$p = Start-Process -FilePath $Java -ArgumentList $quoted -WorkingDirectory $workDir -PassThru -NoNewWindow
	$deadline = (Get-Date).AddSeconds($timeoutSeconds)
	while (-not $p.HasExited -and (Get-Date) -lt $deadline) {
		Start-Sleep -Seconds 5
		if ($stopWhenLog -and (Test-Path $stopWhenLog) -and (Select-String -LiteralPath $stopWhenLog -Pattern $stopPattern -Quiet)) {
			Start-Sleep -Seconds 10
			break
		}
	}
	if (-not $p.HasExited) {
		Say "Stopping the client (time limit $timeoutSeconds s reached or run complete)." Yellow
		Stop-Process -Id $p.Id -Force
		$p.WaitForExit()
		return $false
	}
	return $true
}

function Invoke-Evaluate([string]$runDir, [string[]]$evalArgs) {
	$cp = Get-KitClasspath (Read-Manifest) @('lib\client', 'lib\isolation', 'lib\dev')
	$out = & $Java -cp $cp com.feralenterprises.raccoon.playtest.Evaluate @evalArgs
	$code = $LASTEXITCODE
	$out | ForEach-Object { "$_" } | Tee-Object -FilePath (Join-Path $runDir 'verdicts.txt') | ForEach-Object { Say $_ }
	return $code
}

function Get-LatestReceipt([string]$runDir, [string]$step) {
	$f = Get-ChildItem -LiteralPath $runDir -Filter "$step-*.jsonl" -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
	if ($f) { return $f.FullName }
	return (Join-Path $runDir "$step-missing.jsonl")
}

# ------------------------------------------------------------------ side-load and logs

function Clear-SideloadDir {
	# a JAR left over from R4 must never be side-loaded into an R2/R3 client
	Remove-KitContents $Sideload
	if ((Test-Path -LiteralPath $Sideload) -and @(Get-ChildItem -LiteralPath $Sideload -Force).Count -gt 0) {
		throw "The side-load folder could not be emptied."
	}
}

function Set-AsideClientLog([string]$run) {
	Assert-SafePath $IsoLogs
	$log = Join-Path $IsoLogs 'client.log'
	if (Test-Path -LiteralPath $log) { Move-Item -LiteralPath $log (Join-Path $IsoLogs ("client-before-" + (Split-Path $run -Leaf) + ".log")) }
	return $log
}

function Initialize-Sideload($m, [string]$run) {
	Clear-SideloadDir
	New-Item -ItemType Directory -Force -Path $Sideload | Out-Null
	Assert-SafePath $Sideload
	Copy-Item -LiteralPath $Official (Join-Path $Sideload 'project-raccoon.jar')
	$h = (Get-FileHash (Join-Path $Sideload 'project-raccoon.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
	if ($h -ne $m.official.sha256) { throw "Sideloaded JAR hash $h does not match the retained official JAR." }
	return (Set-AsideClientLog $run)
}

function Test-NoSideloadInLog([string]$log) {
	if (-not (Test-Path -LiteralPath $log)) { return $true }
	return -not (Select-String -LiteralPath $log -Pattern 'Side-loading plugin' -Quiet)
}

function Save-LogExcerpt([string]$log, [string]$run) {
	if (-not (Test-Path $log)) { return }
	# plugin-loading and Raccoon lines only: no general errors, chat, names or account details
	Select-String -LiteralPath $log -Pattern 'Side-loading plugin', 'PluginManager - Plugin RaccoonPlugin', 'PluginManager - Loaded plugin RaccoonPlugin', 'c\.f\.raccoon\.' |
		ForEach-Object { $_.Line } | Set-Content -Encoding UTF8 (Join-Path $run 'client-log-excerpt.txt')
}

# ------------------------------------------------------------------ steps

function Invoke-SelfCheck {
	$m = Read-Manifest
	$bad = Test-KitIntegrity $m
	Say "Kit for candidate $($m.candidate) (kit build $($m.kitCommit), $($m.kind))"
	Say "  official JAR  $($m.official.sha256)  $($m.official.bytes) bytes"
	Say "  isolated home $IsoHome"
	Say "  isolated temp $IsoTmp"
	$cred = Get-SessionStoreCredential
	Say ("  Windows Credential Manager '{0}': {1}{2}" -f $CredentialTarget, $(if (-not $cred.complete) { 'unreadable' } elseif ($cred.present) { 'present' } else { 'absent' }),
		$(if (Test-Path -LiteralPath $CredentialMarker) { ' (created by this kit)' } else { '' }))
	if ($bad.Count -gt 0) { $bad | Select-Object -First 20 | ForEach-Object { Say "  INTEGRITY $_" Red }; throw "Kit integrity check failed: download the kit again." }
	Say "Kit integrity: all $($m.files.Count) listed files match and nothing unlisted is present." Green
	if (Test-Latched) { Say "This kit has stopped itself (KIT-STOPPED.txt); client steps are disabled." Red }
	return $m
}

function Invoke-Guarded([scriptblock]$guardedAction) {
	# the parameter name must not collide with variables the steps use ($step): scriptblocks
	# resolve names dynamically, so a parameter called $step would shadow the caller's
	if (Test-Latched) {
		Say "The kit stopped itself earlier (KIT-STOPPED.txt). Client steps are disabled; send the file and receipts to the reviewer." Red
		return 1
	}
	try { return (& $guardedAction) }
	catch { Say "Problem: $($_.Exception.Message)" Red; return 1 }
}

function Invoke-R1 {
	return (Invoke-Guarded {
		$m = Invoke-SelfCheck
		$run = New-RunDir 'R1'
		Copy-Item (Join-Path $Kit 'fixtures\runelite-plugin.properties') $run
		$cp = Get-KitClasspath $m @('lib\client', 'lib\isolation', 'lib\dev')
		$iso = Invoke-Isolated $run {
			Push-Location $run
			try { & $Java (Get-JvmArgs $m $run) -cp $cp com.feralenterprises.raccoon.playtest.R1Runner | Out-Host }
			finally { Pop-Location }
		}
		$code = Invoke-Evaluate $run @('R1', (Get-LatestReceipt $run 'R1'))
		if (-not $iso) { return 1 }
		return $code
	})
}

function Invoke-ClientStep([string]$step, [string]$checklist, [int]$timeout) {
	return (Invoke-Guarded {
		$m = Invoke-SelfCheck
		$run = New-RunDir $step
		if ($checklist) {
			Get-Content (Join-Path $Kit $checklist) | ForEach-Object { Say $_ Cyan }
			if (-not $NonInteractive) { Read-Host "Press Enter to open the test client" | Out-Null }
		}
		Clear-SideloadDir
		$log = Set-AsideClientLog $run
		$cp = Get-KitClasspath $m @('lib\client', 'lib\isolation', 'lib\dev')
		$extra = @("-Draccoon.playtest.mode=$step")
		if ($step -eq 'R2') { $extra += '-Draccoon.playtest.exitAfterR2=true' }
		# only R3 signs in, so only R3 may see RuneLite create its sign-in key
		$allow = @()
		if ($step -eq 'R3') { $allow = @('windowsCredential') }
		$iso = Invoke-Isolated $run {
			$argList = @(Get-JvmArgs $m $run) + $extra + @('-cp', $cp, 'com.feralenterprises.raccoon.playtest.PlaytestClient', '--developer-mode', '--debug')
			[void](Start-Client $argList $run $timeout $null $null)
		} $allow
		$code = Invoke-Evaluate $run @($step, (Get-LatestReceipt $run $step))
		if (-not (Test-NoSideloadInLog $log)) {
			Say "FAIL: a plugin was side-loaded into the $step client; only the candidate and the witness may run there." Red
			return 1
		}
		if (-not $iso) { return 1 }
		return $code
	})
}

function Invoke-R2 { return (Invoke-ClientStep 'R2' $null 600) }

function Invoke-R3 { return (Invoke-ClientStep 'R3' 'R3-CHECKLIST.txt' 14400) }

function Invoke-R4 {
	return (Invoke-Guarded {
		$m = Invoke-SelfCheck
		$run = New-RunDir 'R4'
		Get-Content (Join-Path $Kit 'R4-CHECKLIST.txt') | ForEach-Object { Say $_ Cyan }
		if (-not $NonInteractive) { Read-Host "Press Enter to open the client with the official JAR" | Out-Null }
		$log = Initialize-Sideload $m $run
		# no Raccoon classes on this classpath: only the pinned RuneLite client (and the kit's
		# preferences isolation); the plugin comes solely from the side-loaded official JAR
		$cp = Get-KitClasspath $m @('lib\client', 'lib\isolation')
		$script:clientExit = $null
		$iso = Invoke-Isolated $run {
			& $Java (Get-JvmArgs $m $run) -cp $cp net.runelite.client.RuneLite --developer-mode --debug | Out-Host
			$script:clientExit = $LASTEXITCODE
		}
		Save-LogExcerpt $log $run
		$code = Invoke-Evaluate $run @('R4', $log, (Join-Path $Sideload 'project-raccoon.jar'), $m.official.sha256,
			"$(Join-Path $Kit 'lib\client');$(Join-Path $Kit 'lib\isolation')", "$script:clientExit")
		if (-not $iso) { return 1 }
		return $code
	})
}

function Invoke-Smoke {
	# CI automation: the R4 launch path unattended, for a limited time. Not an R4 receipt.
	return (Invoke-Guarded {
		$m = Invoke-SelfCheck
		$run = New-RunDir 'Smoke'
		$log = Initialize-Sideload $m $run
		$cp = Get-KitClasspath $m @('lib\client', 'lib\isolation')
		$iso = Invoke-Isolated $run {
			$argList = @(Get-JvmArgs $m $run) + @('-cp', $cp, 'net.runelite.client.RuneLite', '--developer-mode', '--debug')
			[void](Start-Client $argList $run $SmokeSeconds $log 'Project Raccoon started')
		}
		Save-LogExcerpt $log $run
		$code = Invoke-Evaluate $run @('LaunchPath', $log, (Join-Path $Sideload 'project-raccoon.jar'), $m.official.sha256,
			"$(Join-Path $Kit 'lib\client');$(Join-Path $Kit 'lib\isolation')")
		if (-not $iso) { return 1 }
		return $code
	})
}

function Invoke-Collect {
	if (-not (Test-Path $Receipts)) { Say "No receipts yet."; return 2 }
	Assert-NoReparseInside $Receipts
	$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')
	$sums = Join-Path $Receipts 'SHA256SUMS.txt'
	Get-ChildItem -LiteralPath $Receipts -Recurse -File | Where-Object { $_.Name -ne 'SHA256SUMS.txt' } | ForEach-Object {
		'{0}  {1}' -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.FullName.Substring($Receipts.Length + 1)
	} | Set-Content -Encoding UTF8 $sums
	$items = @(Join-Path $Receipts '*')
	if (Test-Latched) { $items += $Latch }
	$zip = Join-Path $Kit "raccoon-receipts-$stamp.zip"
	Compress-Archive -Path $items -DestinationPath $zip
	Say "Receipts saved to $zip - send this file to the reviewer." Green
	return 0
}

function Invoke-Reset {
	if (-not $NonInteractive) {
		$ok = Read-Host "Delete the kit's isolated RuneLite home and temp folders (this signs you out of the test client only)? Type YES"
		if ($ok -ne 'YES') { Say "Nothing deleted."; return 0 }
	}
	try {
		foreach ($d in @($IsoHome, $IsoTmp)) { Assert-NoReparseInside $d }
		if (Test-Path -LiteralPath $CredentialMarker) {
			# only a key this kit saw RuneLite create is removed; a pre-existing one is never touched
			Remove-SessionStoreCredential
			$c = Get-SessionStoreCredential
			if (-not $c.complete -or $c.present) { throw "the '$CredentialTarget' entry could not be confirmed removed" }
			Remove-Item -LiteralPath $CredentialMarker -Force
			Say "Removed the '$CredentialTarget' entry the kit created." Green
		}
		foreach ($d in @($IsoHome, $IsoTmp)) { if (Test-Path -LiteralPath $d) { Remove-KitContents $d; Remove-Item -LiteralPath $d -Force -Recurse } }
	}
	catch { Say "Reset stopped: $($_.Exception.Message)" Red; return 1 }
	Say "Isolated folders deleted. Receipts were kept." Green
	return 0
}

function Show-Menu {
	while ($true) {
		Say ""
		Say "Project Raccoon playtest kit" White
		if (Test-Latched) { Say "  (stopped after a failed safety check: only 1 and 6 are available)" Red }
		Say "  1  Check the kit (start here)"
		Say "  2  R1 - automatic regression replay (about 1 minute, no game)"
		Say "  3  R2 - automatic client replay (client opens and closes itself, no login)"
		Say "  4  R3 - play and observe (log in and follow the checklist)"
		Say "  5  R4 - official plugin file start/stop test"
		Say "  6  Save receipts to a zip for the reviewer"
		Say "  7  Delete the test client's data"
		Say "  Q  Quit"
		$c = Read-Host "Choose"
		try {
			switch ($c.Trim().ToUpperInvariant()) {
				'1' { [void](Invoke-SelfCheck) }
				'2' { [void](Invoke-R1) }
				'3' { [void](Invoke-R2) }
				'4' { [void](Invoke-R3) }
				'5' { [void](Invoke-R4) }
				'6' { [void](Invoke-Collect) }
				'7' { [void](Invoke-Reset) }
				'Q' { return }
				default { Say "Please type a number from the list." Yellow }
			}
		}
		catch { Say "Problem: $($_.Exception.Message)" Red; Say "Stop here and send a screenshot of this window to the reviewer." Yellow }
	}
}

# test seam: the launcher regression tests dot-source this file without running a step
if ($env:RACCOON_PLAYTEST_NO_MAIN -eq '1') { return }

switch ($Step) {
	'Menu' { Show-Menu; exit 0 }
	'SelfCheck' { [void](Invoke-SelfCheck); exit 0 }
	'R1' { exit (Invoke-R1) }
	'R2' { exit (Invoke-R2) }
	'R3' { exit (Invoke-R3) }
	'R4' { exit (Invoke-R4) }
	'Collect' { exit (Invoke-Collect) }
	'Reset' { exit (Invoke-Reset) }
	'Smoke' { exit (Invoke-Smoke) }
}
