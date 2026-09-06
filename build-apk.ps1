param(
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [Parameter(Mandatory=$true)][string]$JdkPath,
    [string]$BuildDirectory = "$PSScriptRoot\build-manual",
    [string]$SigningKey = "$PSScriptRoot\build-manual\debug.jks"
)
$ErrorActionPreference='Stop'
$SdkPath=(Resolve-Path -LiteralPath $SdkPath).Path
$JdkPath=(Resolve-Path -LiteralPath $JdkPath).Path
$env:JAVA_HOME=$JdkPath
$env:PATH="$JdkPath\bin;$env:PATH"
$bt=Join-Path $SdkPath 'build-tools\35.0.0'
$platform=Join-Path $SdkPath 'platforms\android-35\android.jar'
function Run-Tool([string]$Tool,[string[]]$Arguments) { & $Tool @Arguments; if($LASTEXITCODE -ne 0){throw "$Tool failed ($LASTEXITCODE)"} }
New-Item -ItemType Directory -Force -Path $BuildDirectory,"$BuildDirectory\classes","$BuildDirectory\dex","$BuildDirectory\generated" | Out-Null
$manifest=(Get-Content -LiteralPath "$PSScriptRoot\app\src\main\AndroidManifest.xml" -Raw).Replace('<manifest ', '<manifest package="cn.lvxu.travel" ')
[IO.File]::WriteAllText("$BuildDirectory\AndroidManifest.xml",$manifest,[Text.UTF8Encoding]::new($false))
Run-Tool "$bt\aapt2.exe" @('compile','--dir',"$PSScriptRoot\app\src\main\res",'-o',"$BuildDirectory\resources.zip")
Run-Tool "$bt\aapt2.exe" @('link','-o',"$BuildDirectory\unsigned.apk",'-I',$platform,'--manifest',"$BuildDirectory\AndroidManifest.xml",'--java',"$BuildDirectory\generated",'--min-sdk-version','26','--target-sdk-version','35','--version-code','2','--version-name','0.1.1',"$BuildDirectory\resources.zip")
$sources=@(Get-ChildItem "$PSScriptRoot\app\src\main\java","$BuildDirectory\generated" -Filter *.java -Recurse | ForEach-Object FullName)
Run-Tool "$JdkPath\bin\javac.exe" (@('-encoding','UTF-8','-source','17','-target','17','-classpath',$platform,'-d',"$BuildDirectory\classes")+$sources)
Compress-Archive -Path "$BuildDirectory\classes\*" -DestinationPath "$BuildDirectory\classes.zip" -Force
Run-Tool "$bt\d8.bat" @('--lib',$platform,'--min-api','26','--output',"$BuildDirectory\dex", "$BuildDirectory\classes.zip")
Push-Location "$BuildDirectory\dex"
try { Run-Tool "$bt\aapt.exe" @('add',"$BuildDirectory\unsigned.apk",'classes.dex') } finally { Pop-Location }
Run-Tool "$bt\zipalign.exe" @('-f','4',"$BuildDirectory\unsigned.apk","$BuildDirectory\aligned.apk")
if(!(Test-Path -LiteralPath $SigningKey)) { New-Item -ItemType Directory -Force -Path (Split-Path $SigningKey) | Out-Null; Run-Tool "$JdkPath\bin\keytool.exe" @('-genkeypair','-keystore',$SigningKey,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Lvxu,C=CN','-keyalg','RSA','-keysize','2048','-validity','10000') }
Run-Tool "$bt\apksigner.bat" @('sign','--ks',$SigningKey,'--ks-pass','pass:android','--key-pass','pass:android','--out',"$BuildDirectory\lvxu-debug.apk","$BuildDirectory\aligned.apk")
Run-Tool "$bt\apksigner.bat" @('verify','--verbose',"$BuildDirectory\lvxu-debug.apk")
Write-Output "APK: $BuildDirectory\lvxu-debug.apk"
