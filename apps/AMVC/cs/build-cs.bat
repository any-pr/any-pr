@echo off
rem Build C# AMV converter with the csc.exe that ships with Windows (.NET Framework).
rem Outputs:  cs\AmvConverter.dll  (core library for .NET callers)
rem           cs\AMVConverter.exe  (command-line tool, needs ffmpeg.exe nearby or on PATH)
cd /d "%~dp0"

set CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe
if not exist "%CSC%" (
  echo ERROR: csc.exe not found.
  exit /b 1
)

echo [1/2] Compiling AmvConverter.dll ...
"%CSC%" /nologo /target:library /optimize+ /codepage:65001 /out:AmvConverter.dll AmvConverterLib.cs
if errorlevel 1 exit /b 1

echo [2/2] Compiling AMVConverter.exe ...
set SLMG=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\System.Management.dll
if not exist "%SLMG%" set SLMG=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\System.Management.dll
"%CSC%" /nologo /target:exe /optimize+ /codepage:65001 /r:"%SLMG%" /out:AMVConverter.exe AmvConverterLib.cs AmvConverterCli.cs
if errorlevel 1 exit /b 1

if exist ..\dist (
  copy /y AmvConverter.dll ..\dist\ >nul
  copy /y AMVConverter.exe ..\dist\ >nul
)

echo.
echo Build OK:
dir /b AmvConverter.dll AMVConverter.exe
exit /b 0
