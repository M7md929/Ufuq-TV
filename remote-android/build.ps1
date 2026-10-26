param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$BuildToolsVersion = '36.1.0',
    [string]$PlatformVersion = 'android-36.1'
)
$ErrorActionPreference = 'Stop'
if (-not $JdkPath -or -not (Test-Path -LiteralPath "$JdkPath\bin\javac.exe")) {
    throw 'Pass -JdkPath with a JDK 17 or newer directory.'
}
$projectPath = $PSScriptRoot
$buildPath = Join-Path $projectPath 'build'
$toolsPath = Join-Path $SdkPath "build-tools\$BuildToolsVersion"
$androidJar = Join-Path $SdkPath "platforms\$PlatformVersion\android.jar"
$javaExe = Join-Path $JdkPath 'bin\java.exe'
$jarExe = Join-Path $JdkPath 'bin\jar.exe'
function Run-Native([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed: $Program ($LASTEXITCODE)" }
}
# Rebuild generated files so removed source classes and resources cannot survive an update.
foreach ($generatedFolder in @('gen','classes','dex')) {
    $generatedPath = [System.IO.Path]::GetFullPath((Join-Path $buildPath $generatedFolder))
    $expectedParent = [System.IO.Path]::GetFullPath($buildPath).TrimEnd('\') + '\'
    if (-not $generatedPath.StartsWith($expectedParent,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Generated path is outside the project build directory.'
    }
    if (Test-Path -LiteralPath $generatedPath) { Remove-Item -LiteralPath $generatedPath -Recurse -Force }
}
foreach ($folder in @($buildPath,"$buildPath\gen","$buildPath\classes","$buildPath\dex","$projectPath\signing")) {
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
}
Run-Native "$toolsPath\aapt2.exe" @('compile','--dir',"$projectPath\res",'-o',"$buildPath\resources.zip")
Run-Native "$toolsPath\aapt2.exe" @('link','-o',"$buildPath\base.apk",'-I',$androidJar,'--manifest',"$projectPath\AndroidManifest.xml",'-A',"$projectPath\assets",'--java',"$buildPath\gen","$buildPath\resources.zip")
$sources = @(Get-ChildItem -LiteralPath "$projectPath\src","$buildPath\gen" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Run-Native "$JdkPath\bin\javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',"$buildPath\classes") + $sources)
Run-Native $jarExe @('--create','--file',"$buildPath\classes.jar",'-C',"$buildPath\classes",'.')
Run-Native $javaExe @('-cp',"$toolsPath\lib\d8.jar",'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','26','--output',"$buildPath\dex","$buildPath\classes.jar")
Copy-Item -LiteralPath "$buildPath\base.apk" -Destination "$buildPath\unsigned.apk" -Force
Run-Native $jarExe @('uf',"$buildPath\unsigned.apk",'-C',"$buildPath\dex",'classes.dex')
Run-Native "$toolsPath\zipalign.exe" @('-f','4',"$buildPath\unsigned.apk","$buildPath\aligned.apk")
$keyPath = "$projectPath\signing\ufuq-remote-development.jks"
if (-not (Test-Path -LiteralPath $keyPath)) {
    Run-Native "$JdkPath\bin\keytool.exe" @('-genkeypair','-keystore',$keyPath,'-alias','ufuq-remote-development','-storepass','android','-keypass','android','-dname','CN=Ufuq Remote Development','-keyalg','RSA','-keysize','2048','-validity','10000')
}
Run-Native $javaExe @('-jar',"$toolsPath\lib\apksigner.jar",'sign','--ks',$keyPath,'--ks-key-alias','ufuq-remote-development','--ks-pass','pass:android','--key-pass','pass:android','--out',"$buildPath\Ufuq-Remote.apk","$buildPath\aligned.apk")
Run-Native $javaExe @('-jar',"$toolsPath\lib\apksigner.jar",'verify','--verbose',"$buildPath\Ufuq-Remote.apk")
Get-Item -LiteralPath "$buildPath\Ufuq-Remote.apk" | Select-Object FullName,Length
