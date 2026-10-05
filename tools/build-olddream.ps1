param([switch]$PublishOnly)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
$destination = 'E:\rxdev\webhost\wow\OldDreamWOW.apk'

if (!$PublishOnly) {
    Push-Location $repo
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'APK build failed; published APK was not updated.' }
    } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK missing: $apk" }
if (!(Test-Path -LiteralPath (Split-Path $destination) -PathType Container)) { throw 'Publish directory missing.' }
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
$staging = $destination + '.uploading'
Copy-Item -LiteralPath $apk -Destination $staging -Force
if ((Get-FileHash -LiteralPath $staging -Algorithm SHA256).Hash -ne $hash) { throw 'APK copy hash mismatch.' }
Move-Item -LiteralPath $staging -Destination $destination -Force
Write-Output "Published: $destination"
Write-Output "SHA256: $hash"
