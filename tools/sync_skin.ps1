# tools/sync_skin.ps1 - Pipeline Sync Script for Sunset Soundwaves
param(
    [string]$SkinPath = "",
    [switch]$BuildAndDeploy = $false
)

$ErrorActionPreference = "Stop"

# 1. Locate skin JSON
if (-not $SkinPath) {
    $searchDirs = @("f:\Downloads", "$env:USERPROFILE\Downloads")
    $foundFiles = @()
    foreach ($dir in $searchDirs) {
        if (Test-Path $dir) {
            $files = Get-ChildItem -Path $dir -Filter "sunset-soundwaves-skin*.json" | Sort-Object LastWriteTime -Descending
            if ($files) {
                $foundFiles += $files
            }
        }
    }

    if ($foundFiles.Count -gt 0) {
        $SkinPath = $foundFiles[0].FullName
        Write-Host "Found latest exported skin: $SkinPath (Modified: $($foundFiles[0].LastWriteTime))" -ForegroundColor Cyan
    } else {
        Write-Error "No sunset-soundwaves-skin*.json found in Downloads. Specify -SkinPath <path>."
        exit 1
    }
}

# 2. Validate and Copy to App Assets
$targetAsset = "app\src\main\assets\default_skin.json"
Write-Host "Syncing skin to $targetAsset..." -ForegroundColor Green
Copy-Item -Path $SkinPath -Destination $targetAsset -Force

# 3. Quick verify elements
$jsonContent = Get-Content -Path $targetAsset -Raw | ConvertFrom-Json
$ptElements = $jsonContent.viewports.portrait.elements | ForEach-Object { "$($_.id) ($($_.x),$($_.y) $($_.width)x$($_.height))" }
Write-Host "Portrait Elements Synced: ($($jsonContent.viewports.portrait.elements.Count) items)" -ForegroundColor Yellow
$ptElements | ForEach-Object { Write-Host "  - $_" }

if ($BuildAndDeploy) {
    Write-Host "Building debug APK..." -ForegroundColor Cyan
    $env:JAVA_HOME = "C:\Users\neila\.jdks\jbr-21.0.11"
    & "C:\Users\neila\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat" assembleDebug

    Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "Sunset-Pixels.apk" -Force
    Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "Sunset-Soundwaves.apk" -Force

    $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $adb) {
        $targetDevice = (& $adb devices | Select-String -Pattern "(\S+)\s+device\b" | ForEach-Object { $_.Matches.Groups[1].Value } | Where-Object { $_ -notlike "emulator*" } | Select-Object -First 1)
        if ($targetDevice) {
            Write-Host "Deploying to physical device: $targetDevice" -ForegroundColor Green
            & $adb -s $targetDevice install -r "Sunset-Pixels.apk"
            & $adb -s $targetDevice shell monkey -p com.retro.pixelplayer -c android.intent.category.LAUNCHER 1
            Write-Host "App deployed and launched successfully!" -ForegroundColor Green
        } else {
            Write-Warning "No physical Android device connected to ADB."
        }
    }
}
Write-Host "Skin sync complete!" -ForegroundColor Green
