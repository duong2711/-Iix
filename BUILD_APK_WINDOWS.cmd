@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM Requires an Android SDK (e.g. from Android Studio) and Java 17.
if not defined ANDROID_HOME (
  if defined ANDROID_SDK_ROOT (
    set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
  ) else (
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
  )
)
if not exist "%ANDROID_HOME%\platforms\android-35\android.jar" (
  echo [ERROR] Chua co Android SDK 35 tai: %ANDROID_HOME%
  echo Cai Android Studio ^> SDK Manager ^> Android SDK Platform 35.
  pause
  exit /b 1
)
if not exist "%ANDROID_HOME%\build-tools\35.0.0\aapt2.exe" (
  echo [ERROR] Chua co Android Build-Tools 35.0.0.
  echo Cai Android Studio ^> SDK Manager ^> SDK Tools.
  pause
  exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Chua co Java 17. Cai JDK 17, hoac dung Android Studio.
  pause
  exit /b 1
)
set "GRADLE_EXEC=gradle"
where gradle >nul 2>&1
if errorlevel 1 (
  set "GRADLE_EXEC=%TEMP%\xiaoai-gradle-8.9\gradle-8.9\bin\gradle.bat"
  if not exist "!GRADLE_EXEC!" (
    echo Dang tai Gradle 8.9 (can internet) ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $p=Join-Path $env:TEMP 'xiaoai-gradle-8.9'; New-Item -ItemType Directory -Force -Path $p ^| Out-Null; $z=Join-Path $p 'gradle.zip'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $z; Expand-Archive $z -DestinationPath $p -Force"
    if errorlevel 1 (
      echo [ERROR] Khong tai duoc Gradle. Kiem tra internet.
      pause
      exit /b 1
    )
  )
)
call "!GRADLE_EXEC!" --no-daemon assembleDebug
if errorlevel 1 (
  echo [ERROR] Build that bai. Xem log phia tren.
  pause
  exit /b 1
)
if exist "app\build\outputs\apk\debug\app-debug.apk" (
  copy /Y "app\build\outputs\apk\debug\app-debug.apk" "XiaoAIBackBridge-debug.apk" >nul
  echo.
  echo [OK] APK: %CD%\XiaoAIBackBridge-debug.apk
) else (
  echo [ERROR] Build xong nhung khong tim thay APK.
)
pause
