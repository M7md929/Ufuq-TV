param([string]$JdkPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(-not $JdkPath -or -not(Test-Path -LiteralPath "$JdkPath\bin\javac.exe")){throw 'Pass -JdkPath with JDK 17 or newer.'}
$project=Split-Path -Parent $PSScriptRoot
$classes=Join-Path $project 'build\checks'
New-Item -ItemType Directory -Force $classes | Out-Null
& "$JdkPath\bin\javac.exe" -encoding UTF-8 -d $classes "$project\src\om\quiet\tv\MonkeySchedule.java" "$project\src\om\quiet\tv\MonkeyMotion.java" "$PSScriptRoot\MonkeyConfiguredScheduleCheck.java" "$PSScriptRoot\MonkeyMotionCheck.java" "$PSScriptRoot\MonkeyPeekCheck.java"
if($LASTEXITCODE -ne 0){throw 'Compilation failed.'}
foreach($check in @('MonkeyConfiguredScheduleCheck','MonkeyMotionCheck','MonkeyPeekCheck')){
    & "$JdkPath\bin\java.exe" -cp $classes "om.quiet.tv.$check"
    if($LASTEXITCODE -ne 0){throw "Check failed: $check"}
}
