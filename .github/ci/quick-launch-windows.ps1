# Owner-shaped verification of START-R02.cmd's preparation on a real Windows machine, under Windows PowerShell 5.1
# (what START-R02.cmd runs): JAVA_HOME is an existing Java 17 (as for Burrow) and no Temurin 11 is set up. Runs the
# wrapper's own functions: JDK provisioning (tampered download refused, pinned download verified, reuse), toolchain,
# prepare, the launcher's check on the real account, and the wrapper's checkout rules. The private review inputs never
# enter CI, so the package steps and the client launch itself are not run here.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$script:failures = 0
function Assert([string]$Name, [bool]$Ok) {
    if ($Ok) { Write-Host "PASS  $Name" } else { Write-Host "FAIL  $Name"; $script:failures++ }
}

foreach ($f in @('playtest\Start-OwnerPlaytest.ps1')) {
    $tokens = $null; $errors = $null
    [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $repo $f), [ref]$tokens, [ref]$errors)
    Assert "Windows PowerShell $($PSVersionTable.PSVersion) parses $f" ($errors.Count -eq 0)
}
. (Join-Path $repo 'playtest\Start-OwnerPlaytest.ps1')

$script:GitExe = Find-Git
$env:PATH = "$(Split-Path -Parent $script:GitExe);$env:PATH"
Assert "git found ($script:GitExe)" (Test-Path -LiteralPath $script:GitExe)

$java17 = $env:JAVA_HOME_17_X64
Assert 'the machine has a Java 17 like the owner' ([bool]$java17 -and (Test-Path -LiteralPath (Join-Path $java17 'bin\java.exe')))
$env:JAVA_HOME = $java17
$global = @{}
foreach ($scope in 'User','Machine') { foreach ($v in 'JAVA_HOME','PATH','GRADLE_USER_HOME') { $global["$scope/$v"] = [Environment]::GetEnvironmentVariable($v, $scope) } }

$root = Join-Path $env:RUNNER_TEMP 'RaccoonPlaytest'
$tamper = Join-Path $env:RUNNER_TEMP 'RaccoonTamper'
$refused = $false
try { Install-PinnedJdk $tamper ($JdkZipUrl + '.sha256.txt') $JdkZipHash | Out-Null } catch { $refused = $_.Exception.Message -match 'pinned SHA-256'; Write-Host $_.Exception.Message }
Assert 'a download that does not match the pinned SHA-256 is refused' $refused
Assert 'a refused download installs nothing and leaves nothing behind' (!(Test-Path -LiteralPath (Join-Path $tamper "jdk\$JdkName")) -and
    @(Get-ChildItem -LiteralPath (Join-Path $tamper 'jdk') -Force -ErrorAction SilentlyContinue).Count -eq 0)

$started = Get-Date
$jdk = Use-PinnedJdk $root
Write-Host "provisioned in $([int]((Get-Date) - $started).TotalSeconds) s: $jdk"
Assert 'the pinned JDK is provisioned under the Raccoon playtest folder' ($jdk -eq (Join-Path $root "jdk\$JdkName") -and (Test-PinnedJdk $jdk))
Assert 'this process (only) uses it' ($env:JAVA_HOME -eq $jdk -and $env:GRADLE_USER_HOME -eq (Join-Path $root 'gradle-home'))
$ErrorActionPreference = 'Continue'
$version = & (Join-Path $jdk 'bin\java.exe') -version 2>&1 | Out-String
$ErrorActionPreference = 'Stop'
Assert 'it runs as Temurin-11.0.33+10' ($version -match '\bTemurin-11\.0\.33\+10\b')
$same = $true
foreach ($scope in 'User','Machine') { foreach ($v in 'JAVA_HOME','PATH','GRADLE_USER_HOME') { $same = $same -and ($global["$scope/$v"] -ceq [Environment]::GetEnvironmentVariable($v, $scope)) } }
Assert 'user and machine JAVA_HOME, PATH and GRADLE_USER_HOME are unchanged' $same
Assert "the existing Java 17 is untouched" (Test-Path -LiteralPath (Join-Path $java17 'bin\java.exe'))
Assert 'a second start reuses the provisioned JDK without downloading' ((Install-PinnedJdk $root 'https://invalid.invalid/none.zip' '00') -eq $jdk)

$quoted = $true
try { $quoted = Test-PinnedJdk ('"' + $java17 + '"') } catch { $quoted = 'threw' }
Assert 'a JAVA_HOME written with quotes is simply not the pinned JDK (no error)' ($quoted -eq $false)
Assert 'a quoted JAVA_HOME of the pinned JDK is still recognised' (Test-PinnedJdk ('"' + $jdk + '"'))
$saved = $env:JAVA_HOME; $env:JAVA_HOME = '"' + $jdk + '"'
Assert 'a quoted JAVA_HOME of the pinned JDK is used unquoted' ((Use-PinnedJdk $root) -eq $jdk -and $env:JAVA_HOME -eq $jdk)
$env:JAVA_HOME = $saved
$keep = Join-Path $env:RUNNER_TEMP 'keep-me'; New-Item -ItemType Directory -Force -Path $keep | Out-Null; Set-Content -LiteralPath (Join-Path $keep 'k.txt') 'k'
$own = Join-Path $root 'jdk\.extract-linktest'; New-Item -ItemType Directory -Force -Path $own | Out-Null
cmd.exe /c mklink /J (Join-Path $own 'nested') $keep | Out-Null
Remove-OwnFolder $own
Assert 'cleanup removes a nested junction itself, never its target' (!(Test-Path -LiteralPath $own) -and (Test-Path -LiteralPath (Join-Path $keep 'k.txt')))

$toolchain = $true
try { Confirm-Toolchain $repo } catch { $toolchain = $false; Write-Host $_.Exception.Message }
Assert 'the wrapper toolchain check passes (pinned JDK, Gradle 8.10 pin and run)' $toolchain

$sha = (& $script:GitExe -C $repo rev-parse HEAD).Trim()
Assert 'a fresh checkout needs preparation' (Test-NeedingPrepare $repo $sha)
$launcher = Join-Path $repo 'playtest\r02-playtest.cmd'
$env:JAVA_HOME = $java17
$stopped = $null
try { Invoke-Prepare $repo } catch { $stopped = $_.Exception.Message; Write-Host $stopped }
$env:JAVA_HOME = $jdk
Assert 'a failing preparation (here: Java 17 instead of the pin) stops the wrapper, never passes silently' ($stopped -match 'Preparing the launcher failed')
& $launcher prepare | Out-Host
Write-Host "r02-playtest.cmd prepare exit code: $LASTEXITCODE"
$prepared = $true
try { Invoke-Prepare $repo } catch { $prepared = $false; Write-Host $_.Exception.Message }
Assert 'the wrapper preparation succeeds (launcher, isolation JAR, host compiled against RuneLite 1.13.8, classpath resolved)' $prepared
Assert 'after preparation the wrapper does not prepare again' (!(Test-NeedingPrepare $repo $sha))
Assert 'after preparation the checkout is still clean (the wrapper refuses untracked files)' (!(& $script:GitExe -C $repo status --porcelain --untracked-files=all))

$report = @(& $launcher check)
$checkExit = $LASTEXITCODE
Assert "a refused preflight exits nonzero through r02-playtest.cmd ($checkExit)" ($checkExit -ne 0)
$report | Out-Host
foreach ($line in @('PASS  JDK Eclipse Adoptium 11.0.33', 'PASS  checkout clean', 'PASS  host JAR built from HEAD', 'RuneLite classpath JARs match the lock',
                    'PASS  preferences-isolation JAR', 'PASS  RuneLite and the Jagex Launcher are not running', 'PASS  protected locations outside the profile readable')) {
    Assert "launcher check on this Windows account: $line" (@($report | Where-Object { $_.Contains($line) }).Count -gt 0)
}
Assert 'launcher check reads the real account and its elevation' (@($report | Where-Object { $_ -match '^(PASS  ordinary account|FAIL  running elevated)' }).Count -eq 1)
$unexpected = @($report | Where-Object { $_.StartsWith('FAIL') -and $_ -notmatch 'running elevated|no --package given' })
$unexpected | ForEach-Object { Write-Host "unexpected: $_" }
Assert 'no other preflight failure (the package is private; the CI account runs elevated)' ($unexpected.Count -eq 0)

# The wrapper's checkout rules on a GitHub Desktop-shaped checkout of the canonical branch.
& $script:GitExe -C $repo update-ref "refs/remotes/origin/$Branch" HEAD
& $script:GitExe -C $repo checkout -q -B $Branch
& $script:GitExe -C $repo branch -q -u "origin/$Branch"
$candidate = $null
try { $candidate = Get-CheckedOutCandidate $repo } catch { Write-Host $_.Exception.Message }
Assert 'the wrapper accepts a clean, prepared checkout of the canonical branch' ($candidate -ceq $sha)
$live = 'equal'
try { Confirm-RemoteCandidate $sha } catch { $live = $_.Exception.Message; Write-Host $live }
Assert 'the live GitHub tip is read without credentials (public repository)' ($live -eq 'equal' -or $live -match 'GitHub has a different playtest commit')

if ($script:failures -ne 0) { Write-Host "quick-launch-windows: $script:failures failed"; exit 1 }
Write-Host 'quick-launch-windows: all passed'
exit 0
