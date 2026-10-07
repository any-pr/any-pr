# test-dll.ps1 —— 编译后快速验证 AmvConverter.dll
$ErrorActionPreference = 'Stop'
Add-Type -Path (Join-Path $PSScriptRoot 'AmvConverter.dll')

Write-Host ("DLL version: " + [AmvTools.AmvConverter]::Version)

$in  = (Resolve-Path (Join-Path $PSScriptRoot '..\test\media\video.mp4')).Path
$out = Join-Path $PSScriptRoot '..\test\out\cs-dll.amv'
if (Test-Path $out) { Remove-Item $out -Force }

$rc = [AmvTools.AmvConverter]::Convert($in, $out)
Write-Host ("Convert return code: " + $rc)
if ($rc -eq 0) {
    Write-Host ("Output: " + (Resolve-Path $out).Path + " (" + (Get-Item $out).Length + " bytes)")
} else {
    Write-Host ("LastError: " + [AmvTools.AmvConverter]::LastError)
}
exit $rc
