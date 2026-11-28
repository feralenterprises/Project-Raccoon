$ErrorActionPreference = 'Stop'

$Branch = 'test/r02-windows-playtest'
$CanonicalUrl = 'https://github.com/feralenterprises/Project-Raccoon.git'
$ReviewName = 'Project-Raccoon-R02-Review.zip'
$LaneName = 'Project-Raccoon-R02-Lane-Inputs.zip'
$ReviewHash = 'ff4a67ab234b101c60c7d68ac5b714643c5952358683a90e1630189156f1d3ba'
$LaneHash = '1f6a821d37705b7c7acdca9d598c2c5e7f74f7462536c9bf0f9039086545cece'
$GradleHash = '682b4df7fe5accdca84a4d1ef6a3a6ab096b3efd5edf7de2bd8c758d95a93703'
# The pinned JDK (ui-pins.properties jdk.version) is provisioned privately for Raccoon. The global JAVA_HOME, PATH and
# any other Java (for example Burrow's Java 17) are never changed; only this process and its children use it.
$JdkName = 'jdk-11.0.33+10'
$JdkImplementor = 'Temurin-11.0.33+10'
$JdkZipUrl = 'https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.33%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.33_10.zip'
$JdkZipHash = 'cff2e75657c96a718974732b4a7b2c81a245a0b2ea44e6d8ea08cd0248d8fa9d'

function Read-Git([string[]]$Arguments) {
    $result = & $script:GitExe @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git could not verify the checkout. Open GitHub Desktop and fetch or pull origin, then retry."
    }
    $result
}

function Find-Git {
    $git = Get-Command git.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($git) { return $git.Source }
    $desktopPath = Join-Path $env:LOCALAPPDATA 'GitHubDesktop'
    if (Test-Path -LiteralPath $desktopPath -PathType Container) {
        foreach ($app in (Get-ChildItem -LiteralPath $desktopPath -Directory -Filter 'app-*' | Sort-Object LastWriteTime -Descending)) {
            $candidate = Join-Path $app.FullName 'resources\app\git\cmd\git.exe'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    throw 'Open or install GitHub Desktop, or install Git for Windows, then retry.'
}

function Get-CheckedOutCandidate([string]$Repo) {
    $root = Read-Git @('-C',$Repo,'rev-parse','--show-toplevel')
    if ([IO.Path]::GetFullPath($root).TrimEnd('\','/') -ine $Repo.TrimEnd('\','/')) {
        throw 'Use GitHub Desktop > Repository > Show in Explorer. Enter the checkout folder itself.'
    }
    $origin = Read-Git @('-C',$Repo,'remote','get-url','origin')
    if ($origin -notmatch '\A(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)feralenterprises/Project-Raccoon(?:\.git)?/?\z') {
        throw 'Origin must be feralenterprises/Project-Raccoon on GitHub.'
    }
    $branchNow = Read-Git @('-C',$Repo,'branch','--show-current')
    if ($branchNow -cne $Branch) {
        throw "In GitHub Desktop, select $Branch. Leave it selected for routine playtests."
    }
    $upstream = Read-Git @('-C',$Repo,'rev-parse','--abbrev-ref','--symbolic-full-name','@{upstream}')
    if ($upstream -cne "origin/$Branch") {
        throw "Select the GitHub Desktop branch that tracks origin/$Branch."
    }
    if (Read-Git @('-C',$Repo,'status','--porcelain','--untracked-files=all')) {
        throw 'The checkout has local or untracked changes. Use GitHub Desktop to review and move or resolve them, then retry.'
    }
    $head = Read-Git @('-C',$Repo,'rev-parse','HEAD')
    $tracking = Read-Git @('-C',$Repo,'rev-parse',"refs/remotes/origin/$Branch")
    if ($head -cnotmatch '\A[0-9a-f]{40}\z' -or $tracking -cne $head) {
        throw 'The local candidate differs from origin. Pull origin in GitHub Desktop and retry.'
    }
    $head
}

function Confirm-RemoteCandidate([string]$Sha) {
    $ref = "refs/heads/$Branch"
    $tip = @(Read-Git @('ls-remote','--exit-code',$CanonicalUrl,$ref))
    if ($tip.Count -ne 1 -or $tip[0] -cne "$Sha`t$ref") {
        throw 'GitHub has a different playtest commit. Fetch/Pull origin in GitHub Desktop and retry.'
    }
}

function Confirm-Archive([string]$Path, [string]$Hash) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf) -or (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash -ine $Hash) {
        throw "Missing or changed private archive: $Path. Copy the two original ZIPs from the verified owner package into %USERPROFILE%\RaccoonPlaytest\inputs."
    }
}

function Get-PlaytestRoot {
    Join-Path $env:USERPROFILE 'RaccoonPlaytest'
}

function Test-PinnedJdk([string]$JdkHome) {
    if (!$JdkHome) { return $false }
    foreach ($exe in @('bin\java.exe','bin\javac.exe','release')) {
        if (!(Test-Path -LiteralPath (Join-Path $JdkHome $exe) -PathType Leaf)) { return $false }
    }
    $release = Get-Content -LiteralPath (Join-Path $JdkHome 'release')
    return ($release -ccontains 'IMPLEMENTOR="Eclipse Adoptium"') -and ($release -ccontains "IMPLEMENTOR_VERSION=`"$JdkImplementor`"") -and
        ($release -ccontains 'OS_NAME="Windows"') -and ($release -ccontains 'OS_ARCH="x86_64"')
}

function Remove-OwnFolder([string]$Path) {
    # Only plain folders the wrapper made under the playtest root; never through a link or junction.
    if (!(Test-Path -LiteralPath $Path)) { return }
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing to remove $Path (it is a link or junction). Send this message to Solis." }
    Remove-Item -LiteralPath $Path -Recurse -Force
}

function Install-PinnedJdk([string]$Root, [string]$Url = $JdkZipUrl, [string]$Hash = $JdkZipHash) {
    $jdkRoot = Join-Path $Root 'jdk'
    $managed = Join-Path $jdkRoot $JdkName
    if (Test-PinnedJdk $managed) { return $managed }
    New-Item -ItemType Directory -Force -Path $jdkRoot | Out-Null
    if (Test-Path -LiteralPath $managed) { Remove-OwnFolder $managed }
    $id = [guid]::NewGuid().ToString('N')
    $zip = Join-Path $jdkRoot ".download-$id.zip"
    $extract = Join-Path $jdkRoot ".extract-$id"
    try {
        Write-Host "Downloading Temurin 11.0.33+10 for Raccoon only (about 190 MB, first time only). Your other Java installs are not changed."
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        $client = New-Object Net.WebClient
        try { $client.DownloadFile($Url, $zip) } catch { throw "Could not download the Raccoon Java runtime. Check the internet connection and retry. ($($_.Exception.Message))" } finally { $client.Dispose() }
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash -ine $Hash) {
            throw 'The downloaded Java runtime does not match its pinned SHA-256. Nothing was installed. Retry; if it repeats, send this message to Solis.'
        }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::ExtractToDirectory($zip, $extract)
        $unpacked = Join-Path $extract $JdkName
        if (!(Test-PinnedJdk $unpacked)) { throw 'The downloaded Java runtime is not Temurin 11.0.33+10 for Windows x64. Send this message to Solis.' }
        [IO.Directory]::Move($unpacked, $managed)
    } finally {
        if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
        if (Test-Path -LiteralPath $extract) { Remove-OwnFolder $extract }
    }
    if (!(Test-PinnedJdk $managed)) { throw 'The Raccoon Java runtime could not be installed. Send this message to Solis.' }
    $managed
}

function Use-PinnedJdk([string]$Root) {
    # An already-pinned JAVA_HOME is used as is; anything else (Java 17, unset) gets the private pinned JDK.
    $jdk = if (Test-PinnedJdk $env:JAVA_HOME) { $env:JAVA_HOME } else { Install-PinnedJdk $Root }
    # Process scope only: this window and the programs it starts. Nothing global is written.
    $env:JAVA_HOME = $jdk
    $env:PATH = "$(Join-Path $jdk 'bin');$env:PATH"
    foreach ($name in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','JAVA_OPTIONS','CLASSPATH')) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
    # Raccoon's own Gradle home: no user-wide gradle.properties (org.gradle.java.home, JVM flags) or init scripts from
    # other projects apply, and nothing Raccoon downloads lands in theirs.
    $env:GRADLE_USER_HOME = Join-Path $Root 'gradle-home'
    $jdk
}

function Confirm-Toolchain([string]$Repo) {
    if (!$env:JAVA_HOME) { throw 'The Raccoon Java runtime was not set up. Send this message to Solis.' }
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (!(Test-Path -LiteralPath $java -PathType Leaf) -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe') -PathType Leaf)) {
        throw 'The Raccoon Java runtime is incomplete. Delete %USERPROFILE%\RaccoonPlaytest\jdk and retry.'
    }
    $properties = Get-Content -LiteralPath (Join-Path $Repo 'gradle\wrapper\gradle-wrapper.properties')
    if ($properties -cnotcontains "distributionSha256Sum=$GradleHash" -or
        $properties -cnotcontains 'distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-all.zip') {
        throw 'The candidate does not have the expected Gradle 8.10 wrapper pin. Send this message to Solis.'
    }
    $ErrorActionPreference = 'Continue'
    $version = & $java -version 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $version -notmatch '\bTemurin-11\.0\.33\+10\b') {
        throw 'The Raccoon Java runtime is not Temurin 11.0.33+10. Delete %USERPROFILE%\RaccoonPlaytest\jdk and retry.'
    }
    $version = & (Join-Path $Repo 'gradlew.bat') --no-daemon --version 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^Gradle 8\.10\s*$') {
        Write-Host $version
        throw 'The checkout must run Gradle 8.10. Check connectivity for the first preparation and retry.'
    }
}

function Test-NeedingPrepare([string]$Repo, [string]$Sha) {
    $classpath = Join-Path $Repo 'playtest\host-plugin\build\playtest\classpath.txt'
    foreach ($file in @(
        $classpath,
        (Join-Path $Repo 'build\libs\raccoon-r02-launcher.jar'),
        (Join-Path $Repo 'build\libs\raccoon-r02-isolation.jar')
    )) {
        if (!(Test-Path -LiteralPath $file -PathType Leaf)) { return $true }
    }
    $pins = @(Get-Content -LiteralPath $classpath | Where-Object { $_ -cmatch '^# commit ' })
    return ($pins.Count -ne 1 -or $pins[0] -cne "# commit $Sha")
}

function Invoke-Playtest {
    # Runs on the owner's regular Windows account. The launcher's preflight refuses an elevated start and keeps the
    # client inside its own playtest profile; it never uses, changes or removes the account's own RuneLite sign-in.
    $script:GitExe = Find-Git
    $env:PATH = "$(Split-Path -Parent $script:GitExe);$env:PATH"
    $repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
    $sha = Get-CheckedOutCandidate $repo
    # Solis promotes only explicitly cleared exact SHAs to this owner-facing branch.
    Confirm-RemoteCandidate $sha

    $root = Get-PlaytestRoot
    $inputs = Join-Path $root 'inputs'
    $separator = [IO.Path]::DirectorySeparatorChar
    if (([IO.Path]::GetFullPath($root) + $separator).StartsWith($repo.TrimEnd($separator) + $separator,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Keep %USERPROFILE%\RaccoonPlaytest (private inputs and the Raccoon Java runtime) outside the GitHub Desktop checkout.'
    }
    $reviewZip = Join-Path $inputs $ReviewName
    $lanes = Join-Path $inputs $LaneName
    Confirm-Archive $reviewZip $ReviewHash
    Confirm-Archive $lanes $LaneHash
    $jdk = Use-PinnedJdk $root
    Write-Host "Raccoon Java runtime: $jdk"
    Confirm-Toolchain $repo

    $review = Join-Path $inputs 'Project-Raccoon-R02'
    if (!(Test-Path -LiteralPath $review)) {
        $temp = Join-Path $inputs ('.extract-' + [guid]::NewGuid().ToString('N'))
        try {
            Expand-Archive -LiteralPath $reviewZip -DestinationPath $temp
            [IO.Directory]::Move((Join-Path $temp 'Project-Raccoon-R02'),$review)
        } finally {
            if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
        }
    }
    $launcher = Join-Path $repo 'playtest\r02-playtest.cmd'
    if ((Get-CheckedOutCandidate $repo) -cne $sha) { throw 'The checkout changed. Pull origin and retry.' }
    if (Test-NeedingPrepare $repo $sha) {
        Write-Host "Preparing $sha. The first build can take a few minutes."
        & $launcher prepare | Out-Host
        if ($LASTEXITCODE -ne 0) { return $LASTEXITCODE }
        if (Test-NeedingPrepare $repo $sha) { throw 'Preparation did not produce the expected build. Send the console output to Solis.' }
    }
    if ((Get-CheckedOutCandidate $repo) -cne $sha) { throw 'The checkout changed during preparation. Pull origin and retry.' }
    Write-Host 'Close every RuneLite window (including the Burrow developer client) and the Jagex Launcher before continuing.'
    $report = @(& $launcher check --package $review --lane-inputs $lanes)
    $exitCode = $LASTEXITCODE
    $report | Out-Host
    if ($exitCode -ne 0) { return $exitCode }
    if ($report -cnotcontains 'RESULT: READY') { throw 'Preflight did not report RESULT: READY. Send the console output to Solis.' }
    if ((Get-CheckedOutCandidate $repo) -cne $sha) { throw 'The checkout changed after preflight. Pull origin and retry.' }
    Confirm-RemoteCandidate $sha
    Write-Host 'No-login fixture review. Stay at the RuneLite login screen.'
    & $launcher launch --package $review --lane-inputs $lanes | Out-Host
    return $LASTEXITCODE
}

if ($MyInvocation.InvocationName -ne '.') {
    try {
        exit (Invoke-Playtest)
    } catch {
        Write-Host $_.Exception.Message
        exit 1
    }
}
