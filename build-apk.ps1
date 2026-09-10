param(
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [Parameter(Mandatory=$true)][string]$JdkPath,
    [string]$BuildDirectory = "$PSScriptRoot\build-manual",
    [string]$SigningKey = "$PSScriptRoot\build-manual\debug.jks",
    [string]$AmapSdkPath = "$PSScriptRoot\build-manual\amap-sdk.jar",
    [string]$AmapKeyFile = '',
    [string]$NetworkLibDirectory = "$PSScriptRoot\build-manual\network",
    [string]$SigningAlias = 'androiddebugkey',
    [string]$SigningPasswordEnv = ''
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
if (!(Test-Path -LiteralPath $AmapSdkPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $AmapSdkPath) | Out-Null
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/amap/api/3dmap-location-search/11.2.100_loc11.2.100_sea9.8.1/3dmap-location-search-11.2.100_loc11.2.100_sea9.8.1.jar' -OutFile $AmapSdkPath
}
$AmapSdkPath=(Resolve-Path -LiteralPath $AmapSdkPath).Path
New-Item -ItemType Directory -Force -Path $NetworkLibDirectory | Out-Null
$networkJars=@()
foreach($library in (Get-Content -LiteralPath "$PSScriptRoot\dependencies-lock.json" -Raw | ConvertFrom-Json)) {
    $jarFile=Join-Path $NetworkLibDirectory $library.file
    if(!(Test-Path -LiteralPath $jarFile)){Invoke-WebRequest -Uri $library.url -OutFile $jarFile}
    if((Get-FileHash -LiteralPath $jarFile -Algorithm SHA256).Hash -ne $library.sha256){throw "Dependency checksum mismatch: $($library.file)"}
    $networkJars+=(Resolve-Path -LiteralPath $jarFile).Path
}
if ((Get-FileHash -LiteralPath $AmapSdkPath -Algorithm SHA256).Hash -ne 'AC3EBAAFA350784474178E9DFE0CC5083D4F616F52D410E223AED792CE9D6E52') { throw 'AMap SDK checksum mismatch' }
$amapKeyValue=$env:AMAP_ANDROID_KEY
if ($AmapKeyFile) { $amapKeyValue=(Get-Content -LiteralPath $AmapKeyFile -Raw).Trim() }
if ($amapKeyValue -and $amapKeyValue -notmatch '^[a-zA-Z0-9]{20,100}$') { throw 'Invalid Android map key format' }
$manifest=(Get-Content -LiteralPath "$PSScriptRoot\app\src\main\AndroidManifest.xml" -Raw).Replace('<manifest ', '<manifest package="cn.lvxu.travel" ')
$manifest=$manifest.Replace('${AMAP_ANDROID_KEY}',[string]$amapKeyValue)
[IO.File]::WriteAllText("$BuildDirectory\AndroidManifest.xml",$manifest,[Text.UTF8Encoding]::new($false))
Run-Tool "$bt\aapt2.exe" @('compile','--dir',"$PSScriptRoot\app\src\main\res",'-o',"$BuildDirectory\resources.zip")
Run-Tool "$bt\aapt2.exe" @('link','-o',"$BuildDirectory\unsigned.apk",'-I',$platform,'--manifest',"$BuildDirectory\AndroidManifest.xml",'--java',"$BuildDirectory\generated",'--min-sdk-version','26','--target-sdk-version','35','--version-code','8','--version-name','0.2.5','-A',"$PSScriptRoot\app\src\main\assets", "$BuildDirectory\resources.zip")
$sources=@(Get-ChildItem "$PSScriptRoot\app\src\main\java","$BuildDirectory\generated" -Filter *.java -Recurse | ForEach-Object FullName)
# Windows AAPT2 can emit backslashes in nested asset names. Android AssetManager
# looks up forward-slash paths, so normalize ZIP names before signing.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assetArchive=[IO.Compression.ZipFile]::Open("$BuildDirectory\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try {
    foreach($entry in @($assetArchive.Entries | Where-Object { $_.FullName.Contains('\') })) {
        $normalizedName=$entry.FullName.Replace('\','/')
        $buffer=[IO.MemoryStream]::new()
        $stream=$entry.Open()
        try { $stream.CopyTo($buffer) } finally { $stream.Dispose() }
        $entry.Delete()
        $replacement=$assetArchive.CreateEntry($normalizedName)
        $stream=$replacement.Open()
        try { $buffer.Position=0; $buffer.CopyTo($stream) } finally { $stream.Dispose(); $buffer.Dispose() }
    }
    $sdkArchive=[IO.Compression.ZipFile]::OpenRead($AmapSdkPath)
    try {
        foreach($sdkEntry in $sdkArchive.Entries) {
            if (($sdkEntry.FullName.StartsWith('assets/') -or $sdkEntry.FullName.StartsWith('lib/')) -and !$sdkEntry.FullName.EndsWith('/')) {
                $level=if($sdkEntry.FullName.EndsWith('.so')){[IO.Compression.CompressionLevel]::NoCompression}else{[IO.Compression.CompressionLevel]::Optimal}
                $added=$assetArchive.CreateEntry($sdkEntry.FullName,$level)
                $reader=$sdkEntry.Open();$writer=$added.Open()
                try { $reader.CopyTo($writer) } finally { $reader.Dispose();$writer.Dispose() }
            }
        }
    } finally { $sdkArchive.Dispose() }
} finally { $assetArchive.Dispose() }
$compileClasspath=(@($platform,$AmapSdkPath)+$networkJars) -join ';'
Run-Tool "$JdkPath\bin\javac.exe" (@('-encoding','UTF-8','-source','17','-target','17','-classpath',$compileClasspath,'-d',"$BuildDirectory\classes")+$sources)
Compress-Archive -Path "$BuildDirectory\classes\*" -DestinationPath "$BuildDirectory\classes.zip" -Force
Run-Tool "$bt\d8.bat" (@('--lib',$platform,'--min-api','26','--output',"$BuildDirectory\dex", "$BuildDirectory\classes.zip",$AmapSdkPath)+$networkJars)
Push-Location "$BuildDirectory\dex"
try { $dexFiles=@(Get-ChildItem -Filter 'classes*.dex' | ForEach-Object Name);Run-Tool "$bt\aapt.exe" (@('add',"$BuildDirectory\unsigned.apk")+$dexFiles) } finally { Pop-Location }
Run-Tool "$bt\zipalign.exe" @('-f','-P','16','4',"$BuildDirectory\unsigned.apk","$BuildDirectory\aligned.apk")
if(!(Test-Path -LiteralPath $SigningKey)) { New-Item -ItemType Directory -Force -Path (Split-Path $SigningKey) | Out-Null; Run-Tool "$JdkPath\bin\keytool.exe" @('-genkeypair','-keystore',$SigningKey,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Lvxu,C=CN','-keyalg','RSA','-keysize','2048','-validity','10000') }
$signPass=if($SigningPasswordEnv){"env:$SigningPasswordEnv"}else{'pass:android'}
Run-Tool "$bt\apksigner.bat" @('sign','--ks',$SigningKey,'--ks-key-alias',$SigningAlias,'--ks-pass',$signPass,'--key-pass',$signPass,'--out',"$BuildDirectory\lvxu-debug.apk","$BuildDirectory\aligned.apk")
Run-Tool "$bt\apksigner.bat" @('verify','--verbose',"$BuildDirectory\lvxu-debug.apk")
Write-Output "APK: $BuildDirectory\lvxu-debug.apk"
