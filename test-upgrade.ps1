param(
 [Parameter(Mandatory=$true)][string]$SdkPath,
 [Parameter(Mandatory=$true)][string]$JdkPath,
 [Parameter(Mandatory=$true)][string]$OldBuildDirectory,
 [Parameter(Mandatory=$true)][string]$OldApk,
 [Parameter(Mandatory=$true)][string]$NewApk,
 [Parameter(Mandatory=$true)][string]$BuildDirectory,
 [Parameter(Mandatory=$true)][string]$SigningKey,
 [string]$SigningAlias='androiddebugkey',
 [string]$SigningPasswordEnv='',
 [string]$Device='emulator-5580',
 [switch]$AllowEmulatorReset
)
$ErrorActionPreference='Stop'
if($Device -notmatch '^emulator-\d+$' -or !$AllowEmulatorReset){throw 'Use a disposable emulator and explicitly pass -AllowEmulatorReset.'}
$SdkPath=(Resolve-Path -LiteralPath $SdkPath).Path
$JdkPath=(Resolve-Path -LiteralPath $JdkPath).Path
$OldBuildDirectory=(Resolve-Path -LiteralPath $OldBuildDirectory).Path
$OldApk=(Resolve-Path -LiteralPath $OldApk).Path
$NewApk=(Resolve-Path -LiteralPath $NewApk).Path
$SigningKey=(Resolve-Path -LiteralPath $SigningKey).Path
New-Item -ItemType Directory -Force -Path $BuildDirectory | Out-Null
$BuildDirectory=(Resolve-Path -LiteralPath $BuildDirectory).Path
New-Item -ItemType Directory -Force -Path "$BuildDirectory/classes","$BuildDirectory/dex" | Out-Null
$env:JAVA_HOME=$JdkPath
$env:PATH="$JdkPath/bin;$env:PATH"
$bt=Join-Path $SdkPath 'build-tools/35.0.0'
$platform=Join-Path $SdkPath 'platforms/android-35/android.jar'
$adb=Join-Path $SdkPath 'platform-tools/adb.exe'
function Checked([string]$Tool,[string[]]$Arguments){& $Tool @Arguments;if($LASTEXITCODE -ne 0){throw "$Tool failed"}}
$manifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.lvxu.travel.upgrade"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="Upgrade fixture"/><instrumentation android:name="cn.lvxu.travel.UpgradeFixtureInstrumentation" android:targetPackage="cn.lvxu.travel"/></manifest>'
[IO.File]::WriteAllText("$BuildDirectory/AndroidManifest.xml",$manifest)
Checked "$bt/aapt2.exe" @('link','-o',"$BuildDirectory/unsigned.apk",'-I',$platform,'--manifest',"$BuildDirectory/AndroidManifest.xml")
Checked "$JdkPath/bin/javac.exe" @('-encoding','UTF-8','-source','17','-target','17','-classpath',"$platform;$OldBuildDirectory/classes",'-d',"$BuildDirectory/classes","$PSScriptRoot/tests/upgrade/cn/lvxu/travel/UpgradeFixtureInstrumentation.java")
Compress-Archive -Path "$BuildDirectory/classes/*" -DestinationPath "$BuildDirectory/classes.zip" -Force
Checked "$bt/d8.bat" @('--lib',$platform,'--classpath',"$OldBuildDirectory/classes.zip",'--min-api','26','--output',"$BuildDirectory/dex","$BuildDirectory/classes.zip")
Push-Location "$BuildDirectory/dex"
try{Checked "$bt/aapt.exe" @('add',"$BuildDirectory/unsigned.apk",'classes.dex')}finally{Pop-Location}
Checked "$bt/zipalign.exe" @('-f','4',"$BuildDirectory/unsigned.apk","$BuildDirectory/aligned.apk")
$pass=if($SigningPasswordEnv){"env:$SigningPasswordEnv"}else{'pass:android'}
Checked "$bt/apksigner.bat" @('sign','--ks',$SigningKey,'--ks-key-alias',$SigningAlias,'--ks-pass',$pass,'--key-pass',$pass,'--out',"$BuildDirectory/upgrade.apk","$BuildDirectory/aligned.apk")
& $adb -s $Device uninstall cn.lvxu.travel | Out-Null
Checked $adb @('-s',$Device,'install',$OldApk)
Checked $adb @('-s',$Device,'install','-r',"$BuildDirectory/upgrade.apk")
function Phase([string]$Name){
 $output=& $adb -s $Device shell am instrument -w -e phase $Name cn.lvxu.travel.upgrade/cn.lvxu.travel.UpgradeFixtureInstrumentation
 $output | Write-Output
 if($LASTEXITCODE -ne 0 -or !($output -match "PASS upgrade $Name") -or ($output -match 'FAIL|Process crashed')){throw "Upgrade $Name failed"}
}
Phase 'seed'
Checked $adb @('-s',$Device,'install','-r',$NewApk)
Phase 'verify'
