param(
    [string] $SigningProperties = "signing\release-4pda-20260707.properties"
)

$ErrorActionPreference = "Stop"

if (!(Test-Path $SigningProperties)) {
    throw "Нет файла подписи: $SigningProperties"
}

$sdkLine = Get-Content local.properties | Where-Object { $_ -match "^sdk.dir=" } | Select-Object -First 1
if (!$sdkLine) {
    throw "В local.properties не найден sdk.dir"
}

$sdk = ($sdkLine -replace "^sdk.dir=", "") -replace "\\\\", "\"
$sdk = $sdk -replace "^([A-Za-z])\\:", '$1:'
$buildTools = Join-Path $sdk "build-tools\36.1.0"
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"

foreach ($tool in @($zipalign, $apksigner)) {
    if (!(Test-Path $tool)) {
        throw "Не найден Android build tool: $tool"
    }
}

$signing = @{}
Get-Content $SigningProperties | ForEach-Object {
    if ($_ -match "^([^=]+)=(.*)$") {
        $signing[$matches[1].Trim()] = $matches[2]
    }
}
$storeFile = $signing.storeFile
$storePassword = $signing.storePassword
$keyPassword = $signing.keyPassword
$keyAlias = $signing.keyAlias

foreach ($required in @("storeFile", "storePassword", "keyPassword", "keyAlias")) {
    if (!$signing[$required]) {
        throw "В $SigningProperties не задано поле $required"
    }
}

if (!(Test-Path $storeFile)) {
    throw "Не найден keystore: $storeFile"
}

.\gradlew.bat :app:assembleRelease --console=plain

$releaseDir = "app\build\outputs\apk\release"
$unsigned = Join-Path $releaseDir "app-release-unsigned.apk"
$aligned = Join-Path $releaseDir "pray-times-0.1.1-4pda-aligned.apk"
$signed = Join-Path $releaseDir "pray-times-0.1.1-4pda-signed.apk"

Remove-Item -LiteralPath $aligned, $signed, "$signed.idsig" -ErrorAction SilentlyContinue
& $zipalign -f -p 4 $unsigned $aligned
& $apksigner sign `
    --ks $storeFile `
    --ks-key-alias $keyAlias `
    --ks-pass "pass:$storePassword" `
    --key-pass "pass:$keyPassword" `
    --v1-signing-enabled true `
    --v2-signing-enabled true `
    --v3-signing-enabled true `
    --v4-signing-enabled false `
    --out $signed `
    $aligned

& $apksigner verify --verbose --print-certs $signed
Get-Item $signed
