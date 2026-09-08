param(
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [Parameter(Mandatory=$true)][string]$JdkPath,
    [Parameter(Mandatory=$true)][string]$AppBuildDirectory,
    [Parameter(Mandatory=$true)][string]$SigningKey,
    [string]$Device='emulator-5580'
)
$ErrorActionPreference='Stop'
if($Device -notmatch '^emulator-\d+$'){throw 'Run these integration tests on a disposable emulator only.'}
$SdkPath=(Resolve-Path -LiteralPath $SdkPath).Path
$JdkPath=(Resolve-Path -LiteralPath $JdkPath).Path
$AppBuildDirectory=(Resolve-Path -LiteralPath $AppBuildDirectory).Path
$SigningKey=(Resolve-Path -LiteralPath $SigningKey).Path
$env:JAVA_HOME=$JdkPath
$env:PATH="$JdkPath/bin;$env:PATH"
$bt=Join-Path $SdkPath 'build-tools/35.0.0'
$platform=Join-Path $SdkPath 'platforms/android-35/android.jar'
$testBuild=Join-Path $AppBuildDirectory 'instrumentation'
New-Item -ItemType Directory -Force -Path $testBuild,"$testBuild/classes","$testBuild/dex" | Out-Null
function Invoke-Checked([string]$Tool,[string[]]$Arguments){& $Tool @Arguments;if($LASTEXITCODE -ne 0){throw "$Tool failed"}}
$manifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.lvxu.travel.tests"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="Maisui integration tests"/><instrumentation android:name="cn.lvxu.travel.IntegrationInstrumentation" android:targetPackage="cn.lvxu.travel"/></manifest>'
[IO.File]::WriteAllText("$testBuild/AndroidManifest.xml",$manifest)
Invoke-Checked "$bt/aapt2.exe" @('link','-o',"$testBuild/unsigned.apk",'-I',$platform,'--manifest',"$testBuild/AndroidManifest.xml")
$tests=@(Get-ChildItem "$PSScriptRoot/tests/android" -Recurse -Filter *.java | ForEach-Object FullName)
$testLibraries=@();$lock=Get-Content "$PSScriptRoot/dependencies-lock.json" -Raw|ConvertFrom-Json;foreach($dependency in $lock){$candidate=Join-Path (Split-Path $AppBuildDirectory -Parent) "vendor/network/$($dependency.file)";if(!(Test-Path $candidate)){$candidate=Join-Path "$PSScriptRoot/build-manual/network" $dependency.file}if(Test-Path $candidate){$testLibraries+=(Resolve-Path $candidate).Path}}
$testClasspath=(@($platform,"$AppBuildDirectory/classes")+$testLibraries)-join ';'
Invoke-Checked "$JdkPath/bin/javac.exe" (@('-encoding','UTF-8','-source','17','-target','17','-classpath',$testClasspath,'-d',"$testBuild/classes")+$tests)
Compress-Archive -Path "$testBuild/classes/*" -DestinationPath "$testBuild/tests.zip" -Force
Invoke-Checked "$bt/d8.bat" @('--lib',$platform,'--classpath',"$AppBuildDirectory/classes.zip",'--min-api','26','--output',"$testBuild/dex","$testBuild/tests.zip")
Push-Location "$testBuild/dex"
try{Invoke-Checked "$bt/aapt.exe" @('add',"$testBuild/unsigned.apk",'classes.dex')}finally{Pop-Location}
Invoke-Checked "$bt/zipalign.exe" @('-f','4',"$testBuild/unsigned.apk","$testBuild/aligned.apk")
Invoke-Checked "$bt/apksigner.bat" @('sign','--ks',$SigningKey,'--ks-pass','pass:android','--key-pass','pass:android','--out',"$testBuild/tests.apk","$testBuild/aligned.apk")
Invoke-Checked "$SdkPath/platform-tools/adb.exe" @('-s',$Device,'install','-r',"$testBuild/tests.apk")
Invoke-Checked "$SdkPath/platform-tools/adb.exe" @('-s',$Device,'shell','am','instrument','-w','cn.lvxu.travel.tests/cn.lvxu.travel.IntegrationInstrumentation')
