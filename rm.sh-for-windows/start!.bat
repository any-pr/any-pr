@echo off
setlocal
set "ArchDir=x86"
if /i "%PROCESSOR_ARCHITECTURE%"=="AMD64" set "ArchDir=x64"
if /i "%PROCESSOR_ARCHITECTURE%"=="ARM64" set "ArchDir=arm64"

echo ============================================================
echo  警告 / WARNING
echo ------------------------------------------------------------
echo  本脚本会以 TrustedInstaller 权限递归删除 C 盘内容，
echo  将导致系统不可用且无法修复，只能重装。
echo.
echo  仅限在隔离的虚拟机环境中运行。
echo  作者不对任何数据丢失、系统损坏或误操作负责。
echo  继续即表示你已知悉并自行承担全部风险。
echo ============================================================
echo.

choice /C YN /M "你已阅读警告，确定继续吗"
if errorlevel 2 (
    echo 已取消。
    exit /b
)

echo 5 秒内按 Ctrl+C 可取消...
timeout /t 5

start "" /wait "%~dp0nsudo\%ArchDir%\NSudoLC.exe" -U:T -P:E cmd /c "del /f /s /q C:\*.*"
echo 执行完毕，退出码 %errorlevel%。
pause