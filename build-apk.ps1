param(
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [Parameter(Mandatory=$true)][string]$JdkPath,
    [string]$BuildDirectory = "$PSScriptRoot\build-manual",
    [string]$SigningKey = "$PSScriptRoot\build-manual\debug.jks",
    [string]$AmapSdkPath = "$PSScriptRoot\build-manual\amap-sdk.jar",
    [string]$NetworkLibDirectory = "$PSScriptRoot\build-manual\network",
    [string]$BaiduSdkDirectory = "$PSScriptRoot\build-manual\baidu",
    [string]$SigningAlias = 'androiddebugkey',
    [string]$SigningPasswordEnv = '',
    [switch]$Release,
    [ValidateRange(1,2147483647)][int]$VersionCode = 15,
    [string]$VersionName = '0.6.0'
)
$ErrorActionPreference='Stop'
if($Release -and (!$PSBoundParameters.ContainsKey('SigningKey') -or !(Test-Path -LiteralPath $SigningKey))){throw 'Release requires an existing explicitly selected signing key.'}
$SdkPath=(Resolve-Path -LiteralPath $SdkPath).Path
$JdkPath=(Resolve-Path -LiteralPath $JdkPath).Path
$env:JAVA_HOME=$JdkPath
$env:PATH="$JdkPath\bin;$env:PATH"
$bt=Join-Path $SdkPath 'build-tools\35.0.0'
$platform=Join-Path $SdkPath 'platforms\android-35\android.jar'
function Run-Tool([string]$Tool,[string[]]$Arguments) { & $Tool @Arguments; if($LASTEXITCODE -ne 0){throw "$Tool failed ($LASTEXITCODE)"} }
New-Item -ItemType Directory -Force -Path $BuildDirectory,"$BuildDirectory\classes","$BuildDirectory\dex","$BuildDirectory\generated" | Out-Null
$BuildDirectory=(Resolve-Path -LiteralPath $BuildDirectory).Path
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
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force -Path $BaiduSdkDirectory | Out-Null
$BaiduSdkDirectory=(Resolve-Path -LiteralPath $BaiduSdkDirectory).Path
$baiduJars=@();$baiduArchives=@()
foreach($library in (Get-Content -LiteralPath "$PSScriptRoot\baidu-dependencies-lock.json" -Raw | ConvertFrom-Json)) {
    $aarFile=Join-Path $BaiduSdkDirectory $library.file
    if(!(Test-Path -LiteralPath $aarFile)){Invoke-WebRequest -Uri $library.url -OutFile $aarFile}
    if((Get-FileHash -LiteralPath $aarFile -Algorithm SHA256).Hash -ne $library.sha256){throw "Baidu SDK checksum mismatch: $($library.file)"}
    $baiduArchives+=$aarFile
    $archive=[IO.Compression.ZipFile]::OpenRead($aarFile)
    try {
        foreach($entry in $archive.Entries) {
            if($entry.FullName -eq 'classes.jar' -or ($entry.FullName.StartsWith('libs/') -and $entry.FullName.EndsWith('.jar'))) {
                $jarFile=Join-Path $BaiduSdkDirectory ($library.file+'-'+[IO.Path]::GetFileName($entry.FullName))
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$jarFile,$true)
                $baiduJars+=$jarFile
            }
        }
    } finally { $archive.Dispose() }
}
$manifest=(Get-Content -LiteralPath "$PSScriptRoot\app\src\main\AndroidManifest.xml" -Raw).Replace('<manifest ', '<manifest package="cn.lvxu.travel" ')
if($Release) {
    [xml]$releaseManifest=$manifest
    $releaseManifest.manifest.application.SetAttribute('debuggable','http://schemas.android.com/apk/res/android','false') | Out-Null
    $releaseManifest.manifest.application.SetAttribute('testOnly','http://schemas.android.com/apk/res/android','false') | Out-Null
    $manifest=$releaseManifest.OuterXml
}

[IO.File]::WriteAllText("$BuildDirectory\AndroidManifest.xml",$manifest,[Text.UTF8Encoding]::new($false))
Run-Tool "$bt\aapt2.exe" @('compile','--dir',"$PSScriptRoot\app\src\main\res",'-o',"$BuildDirectory\resources.zip")
Run-Tool "$bt\aapt2.exe" @('link','-o',"$BuildDirectory\unsigned.apk",'-I',$platform,'--manifest',"$BuildDirectory\AndroidManifest.xml",'--java',"$BuildDirectory\generated",'--min-sdk-version','26','--target-sdk-version','35','--version-code',[string]$VersionCode,'--version-name',$VersionName,'-A',"$PSScriptRoot\app\src\main\assets", "$BuildDirectory\resources.zip")
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
    foreach($aarFile in $baiduArchives) {
        $archive=[IO.Compression.ZipFile]::OpenRead($aarFile)
        try {
            foreach($entry in $archive.Entries) {
                if(($entry.FullName.StartsWith('assets/') -or $entry.FullName.StartsWith('jni/')) -and !$entry.FullName.EndsWith('/')) {
                    $targetName=if($entry.FullName.StartsWith('jni/')){'lib/'+$entry.FullName.Substring(4)}else{$entry.FullName}
                    if($assetArchive.GetEntry($targetName)){throw "Duplicate SDK asset: $targetName"}
                    $level=if($targetName.EndsWith('.so')){[IO.Compression.CompressionLevel]::NoCompression}else{[IO.Compression.CompressionLevel]::Optimal}
                    $added=$assetArchive.CreateEntry($targetName,$level)
                    $reader=$entry.Open();$writer=$added.Open()
                    try{$reader.CopyTo($writer)}finally{$reader.Dispose();$writer.Dispose()}
                }
            }
        } finally {$archive.Dispose()}
    }
} finally { $assetArchive.Dispose() }
$compileClasspath=(@($platform,$AmapSdkPath)+$networkJars+$baiduJars) -join ';'
Run-Tool "$JdkPath\bin\javac.exe" (@('-encoding','UTF-8','-source','17','-target','17','-classpath',$compileClasspath,'-d',"$BuildDirectory\classes")+$sources)
Compress-Archive -Path "$BuildDirectory\classes\*" -DestinationPath "$BuildDirectory\classes.zip" -Force
if($Release) {
    # Mapping contains original names. Keep it in the private build directory,
    # never inside the APK or the public release/source archive.
    Run-Tool "$JdkPath\bin\java.exe" (@('-Xmx4g','-cp',"$bt\lib\d8.jar",'com.android.tools.r8.R8','--release','--lib',$platform,'--min-api','26','--pg-conf',"$PSScriptRoot\app\proguard-rules.pro",'--pg-map-output',"$BuildDirectory\mapping-private.txt",'--output',"$BuildDirectory\dex", "$BuildDirectory\classes.zip",$AmapSdkPath)+$networkJars+$baiduJars)
} else {
    Run-Tool "$bt\d8.bat" (@('--lib',$platform,'--min-api','26','--output',"$BuildDirectory\dex", "$BuildDirectory\classes.zip",$AmapSdkPath)+$networkJars+$baiduJars)
}
Push-Location "$BuildDirectory\dex"
try { $dexFiles=@(Get-ChildItem -Filter 'classes*.dex' | ForEach-Object Name);Run-Tool "$bt\aapt.exe" (@('add',"$BuildDirectory\unsigned.apk")+$dexFiles) } finally { Pop-Location }
Run-Tool "$bt\zipalign.exe" @('-f','-P','16','4',"$BuildDirectory\unsigned.apk","$BuildDirectory\aligned.apk")
if(!(Test-Path -LiteralPath $SigningKey)) { New-Item -ItemType Directory -Force -Path (Split-Path $SigningKey) | Out-Null; Run-Tool "$JdkPath\bin\keytool.exe" @('-genkeypair','-keystore',$SigningKey,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Lvxu,C=CN','-keyalg','RSA','-keysize','2048','-validity','10000') }
$signPass=if($SigningPasswordEnv){"env:$SigningPasswordEnv"}else{'pass:android'}
$apkName=if($Release){'lvxu-release.apk'}else{'lvxu-debug.apk'}
Run-Tool "$bt\apksigner.bat" @('sign','--ks',$SigningKey,'--ks-key-alias',$SigningAlias,'--ks-pass',$signPass,'--key-pass',$signPass,'--out',"$BuildDirectory\$apkName","$BuildDirectory\aligned.apk")
Run-Tool "$bt\apksigner.bat" @('verify','--verbose',"$BuildDirectory\$apkName")
Write-Output "APK: $BuildDirectory\$apkName"
