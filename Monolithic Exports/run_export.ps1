<#
.SYNOPSIS
    Runs the Monolithic Project Exporter from PowerShell.
.EXAMPLE
    .\run_export.ps1
    .\run_export.ps1 -Timestamped
#>
param(
    [switch]$Timestamped
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -Path $scriptDir

$argsList = @("export_monolith.py")
if ($Timestamped) {
    $argsList += "--timestamped"
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Starting Monolithic Project Exporter" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

python @argsList
if ($LASTEXITCODE -ne 0) {
    Write-Error "Monolithic Export failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}
