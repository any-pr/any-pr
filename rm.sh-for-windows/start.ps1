#requires -Version 5.1
<#
    WARNING / 警告
    本脚本会以 TrustedInstaller 权限递归删除 C 盘内容，
    将导致系统不可用且无法修复，只能重装。
    仅限在隔离的虚拟机环境中运行。
    作者不对任何数据丢失、系统损坏或误操作负责。
    继续即表示你已知悉并自行承担全部风险。
#>

[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# ============================================================
# NSudo 直链
# ============================================================
$NsudoUrlX64   = 'https://github.com/CaoHaoran-Dev/any-pr/releases/download/rmforwin-v1.0.0/NSudoLCx64.exe'
$NsudoUrlArm64 = 'https://github.com/CaoHaoran-Dev/any-pr/releases/download/rmforwin-v1.0.0/NSudoLC_ARM64.exe'
$NsudoUrlX86   = 'https://github.com/CaoHaoran-Dev/any-pr/releases/download/rmforwin-v1.0.0/NSudoLC_win32.exe'

$WorkDir    = Join-Path $env:TEMP "nsudo_online_$([guid]::NewGuid().ToString('N'))"
$NsudoExe   = Join-Path $WorkDir 'NSudoLC.exe'

# ============================================================
# 架构判断
# ============================================================
$ArchDir = 'x86'
$NsudoUrl = $NsudoUrlX86

switch ($env:PROCESSOR_ARCHITECTURE.ToUpperInvariant()) {
    'AMD64' {
        $ArchDir = 'x64'
        $NsudoUrl = $NsudoUrlX64
    }
    'ARM64' {
        $ArchDir = 'arm64'
        $NsudoUrl = $NsudoUrlArm64
    }
    default {
        $ArchDir = 'x86'
        $NsudoUrl = $NsudoUrlX86
    }
}

# ============================================================
# 警告
# ============================================================
function Show-Warning {
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host ' 警告 / WARNING' -ForegroundColor Red
    Write-Host '------------------------------------------------------------' -ForegroundColor Red
    Write-Host ' 本脚本会以 TrustedInstaller 权限递归删除 C 盘内容，' -ForegroundColor Yellow
    Write-Host ' 将导致系统不可用且无法修复，只能重装。' -ForegroundColor Yellow
    Write-Host ''
    Write-Host ' 仅限在隔离的虚拟机环境中运行。' -ForegroundColor Yellow
    Write-Host ' 作者不对任何数据丢失、系统损坏或误操作负责。' -ForegroundColor Yellow
    Write-Host ' 继续即表示你已知悉并自行承担全部风险。' -ForegroundColor Yellow
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host ''
}

# ============================================================
# 确认
# ============================================================
function Confirm-Continue {
    if ($Force) {
        Write-Host '已指定 -Force，跳过交互确认。' -ForegroundColor Yellow
        return $true
    }

    $answer = Read-Host '你已阅读警告，确定继续吗？输入 YES 继续'
    if ($answer -ceq 'YES') {
        return $true
    }

    Write-Host '已取消。' -ForegroundColor Green
    return $false
}

# ============================================================
# 下载 NSudoLC
# ============================================================
function Get-Nsudo {
    if (-not (Test-Path -LiteralPath $WorkDir)) {
        New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
    }

    Write-Host "架构：$ArchDir" -ForegroundColor Cyan
    Write-Host "正在下载 NSudoLC：$NsudoUrl" -ForegroundColor Cyan

    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $NsudoUrl -OutFile $NsudoExe -UseBasicParsing

    if (-not (Test-Path -LiteralPath $NsudoExe)) {
        throw "下载失败，未找到文件：$NsudoExe"
    }

    $size = (Get-Item -LiteralPath $NsudoExe).Length
    Write-Host ("下载完成，大小 {0:N0} 字节" -f $size) -ForegroundColor Cyan
}

# ============================================================
# 主流程
# ============================================================
Show-Warning

if (-not (Confirm-Continue)) {
    exit 0
}

Write-Host '5 秒内按 Ctrl+C 可取消...' -ForegroundColor Yellow
Start-Sleep -Seconds 5

try {
    Get-Nsudo

    Write-Host "正在以 TrustedInstaller 权限执行删除..." -ForegroundColor Red
    $nsudoArgs = @(
        '-U:T',
        '-P:E',
        'cmd',
        '/c',
        'del /f /s /q C:\*.*'
    )

    $proc = Start-Process -FilePath $NsudoExe -ArgumentList $nsudoArgs -Wait -PassThru -NoNewWindow
    Write-Host "执行完毕，退出码 $($proc.ExitCode)。" -ForegroundColor Cyan
}
catch {
    Write-Host "发生错误：$($_.Exception.Message)" -ForegroundColor Red
}
finally {
    if (Test-Path -LiteralPath $WorkDir) {
        Remove-Item -LiteralPath $WorkDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host '按 Enter 退出...' -ForegroundColor DarkGray
[void][Console]::ReadLine()