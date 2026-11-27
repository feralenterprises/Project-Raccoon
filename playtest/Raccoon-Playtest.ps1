<#
Project Raccoon - FR-006 Windows playtest launcher (verification kit, not a product).

Everything runs from this folder: its own Java runtime, its own pinned RuneLite 1.13.8 developer
client, and an isolated RuneLite home (.\home) and temp folder (.\tmp). Your normal RuneLite
folder (%USERPROFILE%\.runelite) is never read or written; the launcher snapshots it before and
after every client step and reports any difference.

Steps
  SelfCheck  verify every kit file against kit-manifest.json and show the isolation paths
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
$Java = Join-Path $Kit 'runtime\bin\java.exe'
$IsoHome = Join-Path $Kit 'home'
$IsoTmp = Join-Path $Kit 'tmp'
$Receipts = Join-Path $Kit 'receipts'
$RealRuneLite = Join-Path $env:USERPROFILE '.runelite'
$ClientLib = Join-Path $Kit 'lib\client'
$DevLib = Join-Path $Kit 'lib\dev'
$Official = Join-Path $Kit 'official\project-raccoon.jar'

function Say([string]$text, [string]$color = 'Gray') { Write-Host $text -ForegroundColor $color }

function Read-Manifest {
	if (-not (Test-Path $ManifestPath)) { throw "kit-manifest.json is missing: this folder is not a complete playtest kit." }
	return Get-Content $ManifestPath -Raw | ConvertFrom-Json
}

function Test-KitIntegrity($manifest) {
	$bad = @()
	foreach ($f in $manifest.files) {
		$p = Join-Path $Kit $f.path
		if (-not (Test-Path -LiteralPath $p)) { $bad += "missing $($f.path)"; continue }
		$h = (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()
		if ($h -ne $f.sha256) { $bad += "changed $($f.path)" }
	}
	return ,$bad
}

function Get-Snapshot([string]$dir) {
	if (-not (Test-Path -LiteralPath $dir)) { return [pscustomobject]@{ exists = $false; files = 0; digest = 'absent' } }
	$rows = Get-ChildItem -LiteralPath $dir -Recurse -Force -File -ErrorAction SilentlyContinue |
		Sort-Object FullName |
		ForEach-Object { '{0}|{1}|{2}' -f $_.FullName.Substring($dir.Length), $_.Length, $_.LastWriteTimeUtc.Ticks }
	$text = ($rows -join "`n")
	$sha = [System.Security.Cryptography.SHA256]::Create()
	$digest = -join ($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($text)) | ForEach-Object { $_.ToString('x2') })
	return [pscustomobject]@{ exists = $true; files = @($rows).Count; digest = $digest }
}

function Get-JavaPrefsCount {
	$key = 'HKCU:\Software\JavaSoft\Prefs'
	if (-not (Test-Path $key)) { return 0 }
	return @(Get-ChildItem $key -Recurse -ErrorAction SilentlyContinue).Count
}

function Assert-NoRuneLiteRunning {
	$running = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(RuneLite|JagexLauncher)$' })
	if ($running.Count -gt 0) {
		if ($NonInteractive) { throw "RuneLite or the Jagex Launcher is running; close it first." }
		Say "Please close RuneLite and the Jagex Launcher first (they could change your normal RuneLite folder during the test)." Yellow
		Read-Host "Press Enter when they are closed" | Out-Null
		Assert-NoRuneLiteRunning
	}
}

function New-RunDir([string]$name) {
	$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')
	$dir = Join-Path $Receipts "$name-$stamp"
	New-Item -ItemType Directory -Force -Path $dir | Out-Null
	return $dir
}

function Get-JvmArgs($manifest, [string]$runDir) {
	New-Item -ItemType Directory -Force -Path $IsoHome, $IsoTmp | Out-Null
	return @(
		'-ea', '-Xmx1024m',
		"-Duser.home=$IsoHome",
		"-Djava.io.tmpdir=$IsoTmp",
		"-Draccoon.playtest.receipts=$runDir",
		"-Draccoon.playtest.candidate=$($manifest.candidate)",
		"-Draccoon.playtest.kit=$($manifest.kitCommit)",
		"-Draccoon.playtest.kit.dir=$Kit",
		"-Draccoon.playtest.kind=$($manifest.kind)"
	)
}

function Invoke-Isolated([string]$runDir, [scriptblock]$body) {
	Assert-NoRuneLiteRunning
	$before = Get-Snapshot $RealRuneLite
	$prefsBefore = Get-JavaPrefsCount
	try { & $body }
	finally {
		$after = Get-Snapshot $RealRuneLite
		$prefsAfter = Get-JavaPrefsCount
		$unchanged = ($before.digest -eq $after.digest) -and ($prefsBefore -eq $prefsAfter)
		$iso = [ordered]@{
			normalRuneLiteFolder = '%USERPROFILE%\.runelite'
			existsBefore = $before.exists; filesBefore = $before.files; digestBefore = $before.digest
			existsAfter = $after.exists; filesAfter = $after.files; digestAfter = $after.digest
			javaPrefsKeysBefore = $prefsBefore; javaPrefsKeysAfter = $prefsAfter
			isolatedHome = '<kit>\home'; isolatedTemp = '<kit>\tmp'
			result = $(if ($unchanged) { 'PASS' } else { 'FAIL' })
		}
		$iso | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $runDir 'isolation.json')
		if ($unchanged) { Say "Isolation: your normal RuneLite folder was not changed." Green }
		else { Say "Isolation FAILED: your normal RuneLite folder or Java settings changed during this step. Please report this." Red }
	}
}

function Invoke-Evaluate([string]$runDir, [string[]]$evalArgs) {
	$cp = "$ClientLib\*;$DevLib\*"
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

function Invoke-SelfCheck {
	$m = Read-Manifest
	$bad = Test-KitIntegrity $m
	Say "Kit for candidate $($m.candidate) (kit build $($m.kitCommit), $($m.kind))"
	Say "  official JAR  $($m.official.sha256)  $($m.official.bytes) bytes"
	Say "  isolated home $IsoHome"
	Say "  isolated temp $IsoTmp"
	Say "  your normal RuneLite folder (never used): $RealRuneLite"
	if ($bad.Count -gt 0) { $bad | ForEach-Object { Say "  INTEGRITY $_" Red }; throw "Kit integrity check failed: download the kit again." }
	Say "Kit integrity: all $($m.files.Count) files match the manifest." Green
	return $m
}

function Invoke-R1 {
	$m = Invoke-SelfCheck
	$run = New-RunDir 'R1'
	Copy-Item (Join-Path $Kit 'fixtures\runelite-plugin.properties') $run
	Invoke-Isolated $run {
		Push-Location $run
		try { & $Java (Get-JvmArgs $m $run) -cp "$ClientLib\*;$DevLib\*" com.feralenterprises.raccoon.playtest.R1Runner | Out-Host }
		finally { Pop-Location }
	}
	return (Invoke-Evaluate $run @('R1', (Get-LatestReceipt $run 'R1')))
}

function Invoke-R2 {
	$m = Invoke-SelfCheck
	$run = New-RunDir 'R2'
	Say "R2: the test client opens, reaches the login screen, runs the replay on the client thread, then closes itself. Do not log in." Cyan
	Invoke-Isolated $run {
		Push-Location $run
		try {
			& $Java (Get-JvmArgs $m $run) '-Draccoon.playtest.mode=R2' '-Draccoon.playtest.exitAfterR2=true' `
				-cp "$ClientLib\*;$DevLib\*" com.feralenterprises.raccoon.playtest.PlaytestClient --developer-mode --debug | Out-Host
		}
		finally { Pop-Location }
	}
	return (Invoke-Evaluate $run @('R2', (Get-LatestReceipt $run 'R2')))
}

function Invoke-R3 {
	$m = Invoke-SelfCheck
	$run = New-RunDir 'R3'
	Get-Content (Join-Path $Kit 'R3-CHECKLIST.txt') | ForEach-Object { Say $_ Cyan }
	if (-not $NonInteractive) { Read-Host "Press Enter to open the test client" | Out-Null }
	Invoke-Isolated $run {
		Push-Location $run
		try {
			& $Java (Get-JvmArgs $m $run) '-Draccoon.playtest.mode=R3' `
				-cp "$ClientLib\*;$DevLib\*" com.feralenterprises.raccoon.playtest.PlaytestClient --developer-mode --debug | Out-Host
		}
		finally { Pop-Location }
	}
	return (Invoke-Evaluate $run @('R3', (Get-LatestReceipt $run 'R3')))
}

function Initialize-Sideload($m, [string]$run) {
	$side = Join-Path $IsoHome '.runelite\sideloaded-plugins'
	New-Item -ItemType Directory -Force -Path $side | Out-Null
	Get-ChildItem -LiteralPath $side -Force | Remove-Item -Force -Recurse
	Copy-Item $Official (Join-Path $side 'project-raccoon.jar')
	$h = (Get-FileHash (Join-Path $side 'project-raccoon.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
	if ($h -ne $m.official.sha256) { throw "Sideloaded JAR hash $h does not match the retained official JAR." }
	$logs = Join-Path $IsoHome '.runelite\logs'
	$log = Join-Path $logs 'client.log'
	if (Test-Path $log) { Move-Item $log (Join-Path $logs ("client-before-" + (Split-Path $run -Leaf) + ".log")) }
	return $log
}

function Save-LogExcerpt([string]$log, [string]$run) {
	if (-not (Test-Path $log)) { return }
	# only plugin-loading, Raccoon and problem lines: no chat, names or account details
	Select-String -LiteralPath $log -Pattern 'side-?load', 'Raccoon', 'raccoon', 'PluginManager', ' ERROR ', ' WARN ' |
		ForEach-Object { $_.Line } | Set-Content -Encoding UTF8 (Join-Path $run 'client-log-excerpt.txt')
}

function Invoke-R4 {
	$m = Invoke-SelfCheck
	$run = New-RunDir 'R4'
	Get-Content (Join-Path $Kit 'R4-CHECKLIST.txt') | ForEach-Object { Say $_ Cyan }
	if (-not $NonInteractive) { Read-Host "Press Enter to open the client with the official JAR" | Out-Null }
	$log = $null
	Invoke-Isolated $run {
		$script:log = Initialize-Sideload $m $run
		# no Raccoon classes on this classpath: only the pinned RuneLite client; the plugin comes
		# solely from the side-loaded official JAR
		& $Java (Get-JvmArgs $m $run) -cp "$ClientLib\*" net.runelite.client.RuneLite --developer-mode --debug | Out-Host
	}
	$log = $script:log
	Save-LogExcerpt $log $run
	$side = Join-Path $IsoHome '.runelite\sideloaded-plugins\project-raccoon.jar'
	return (Invoke-Evaluate $run @('R4', $log, $side, $m.official.sha256, $ClientLib))
}

function Invoke-Smoke {
	# CI automation: the R4 launch path unattended, for a limited time. Not an R4 receipt.
	$m = Invoke-SelfCheck
	$run = New-RunDir 'Smoke'
	$log = $null
	Invoke-Isolated $run {
		$script:log = Initialize-Sideload $m $run
		$argList = @(Get-JvmArgs $m $run) + @('-cp', "$ClientLib\*", 'net.runelite.client.RuneLite', '--developer-mode', '--debug')
		$quoted = ($argList | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
		$p = Start-Process -FilePath $Java -ArgumentList $quoted -PassThru -NoNewWindow
		$deadline = (Get-Date).AddSeconds($SmokeSeconds)
		while (-not $p.HasExited -and (Get-Date) -lt $deadline) {
			Start-Sleep -Seconds 5
			if ((Test-Path $script:log) -and (Select-String -LiteralPath $script:log -Pattern 'Project Raccoon started' -Quiet)) {
				Start-Sleep -Seconds 10
				break
			}
		}
		if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
	}
	$log = $script:log
	Save-LogExcerpt $log $run
	$side = Join-Path $IsoHome '.runelite\sideloaded-plugins\project-raccoon.jar'
	[void](Invoke-Evaluate $run @('R4', $log, $side, $m.official.sha256, $ClientLib))
	return 0
}

function Invoke-Collect {
	if (-not (Test-Path $Receipts)) { Say "No receipts yet."; return 2 }
	$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')
	$sums = Join-Path $Receipts 'SHA256SUMS.txt'
	Get-ChildItem -LiteralPath $Receipts -Recurse -File | Where-Object { $_.Name -ne 'SHA256SUMS.txt' } | ForEach-Object {
		'{0}  {1}' -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.FullName.Substring($Receipts.Length + 1)
	} | Set-Content -Encoding UTF8 $sums
	$zip = Join-Path $Kit "raccoon-receipts-$stamp.zip"
	Compress-Archive -Path (Join-Path $Receipts '*') -DestinationPath $zip
	Say "Receipts saved to $zip - send this file to the reviewer." Green
	return 0
}

function Invoke-Reset {
	if (-not $NonInteractive) {
		$ok = Read-Host "Delete the kit's isolated RuneLite home and temp folders (this signs you out of the test client only)? Type YES"
		if ($ok -ne 'YES') { Say "Nothing deleted."; return 0 }
	}
	foreach ($d in @($IsoHome, $IsoTmp)) { if (Test-Path $d) { Remove-Item -LiteralPath $d -Recurse -Force } }
	Say "Isolated folders deleted. Receipts were kept." Green
	return 0
}

function Show-Menu {
	while ($true) {
		Say ""
		Say "Project Raccoon playtest kit" White
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
		catch { Say "Problem: $($_.Exception.Message)" Red; Say "Nothing was changed outside this folder. Send a screenshot of this window to the reviewer." Yellow }
	}
}

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
