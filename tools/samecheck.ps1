# Proves generation output is unchanged: wipes the saved chunks, starts the game, and hashes every block of a few areas.
#   .\tools\samecheck.ps1 [-Env "WG2_NO_MEMO=1"]     compare the printed hashes between runs/builds
param([string]$Env = "", [string]$Project = "hytale_default", [string]$World = "projtest")
$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
foreach ($kv in ($Env -split ";")) { if ($kv -match "^(\w+)=(.*)$") { Set-Item "env:$($Matches[1])" $Matches[2] } }
& "$PSScriptRoot\dev.ps1" stop | Out-Null; Start-Sleep 2
$saveDir = Join-Path $root "run\saves\$World"
if (Test-Path $saveDir) { Get-ChildItem $saveDir -Recurse -Directory | Where-Object { $_.Name -in "region","entities","poi" } | Sort-Object { $_.FullName.Length } -Descending | ForEach-Object { if (Test-Path $_.FullName) { [IO.Directory]::Delete($_.FullName, $true) } } }
& "$PSScriptRoot\dev.ps1" start 2>&1 | Select-Object -Last 1 | Out-Null
foreach ($kv in ($Env -split ";")) { if ($kv -match "^(\w+)=") { Remove-Item "env:$($Matches[1])" -ErrorAction SilentlyContinue } }
& "$PSScriptRoot\dev.ps1" call "/openproject?name=$Project" | Out-Null
& "$PSScriptRoot\dev.ps1" call "/world?name=$World" | Out-Null
for ($i = 0; $i -lt 120; $i++) { Start-Sleep 3; try { $s = Invoke-RestMethod "http://127.0.0.1:5006/state" -TimeoutSec 20; if ($s.inWorld) { break } } catch {} }
Start-Sleep 20
foreach ($pt in @(@(0, 0), @(3000, -2000), @(-5000, 7000))) {
    $r = Invoke-RestMethod "http://127.0.0.1:5006/hash?x=$($pt[0])&z=$($pt[1])&r=2" -TimeoutSec 900
    "area $($pt[0]),$($pt[1]): $($r.hash)"
}
