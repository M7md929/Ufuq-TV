param([string]$JdkPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(-not $JdkPath -or -not(Test-Path -LiteralPath "$JdkPath\bin\javac.exe")){throw 'Pass -JdkPath with JDK 17 or newer.'}
$project=Split-Path -Parent $PSScriptRoot
$classes=Join-Path $project 'build\checks'
New-Item -ItemType Directory -Force $classes | Out-Null
& "$JdkPath\bin\javac.exe" -encoding UTF-8 -d $classes "$project\src\om\ufuq\remote\Wire.java" "$project\src\om\ufuq\remote\PairingSecret.java" "$project\src\om\ufuq\remote\MonkeyMotion.java" "$project\src\om\ufuq\remote\AdbWire.java" "$project\src\om\ufuq\remote\AdbPointer.java" "$project\src\om\ufuq\remote\WakeOnLan.java" "$PSScriptRoot\ProtocolChecks.java"
if($LASTEXITCODE -ne 0){throw 'Check compilation failed.'}
& "$JdkPath\bin\java.exe" -cp $classes om.ufuq.remote.ProtocolChecks
if($LASTEXITCODE -ne 0){throw 'Checks failed.'}
