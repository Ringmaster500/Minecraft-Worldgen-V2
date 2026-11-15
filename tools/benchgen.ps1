# Times how long the game takes to generate a fresh view radius of the open world setup.
#   .\tools\benchgen.ps1            restarts the game, opens hytale_default, teleports somewhere never visited, waits until idle
param([switch]$Keep, [switch]$Profile, [string]$Project = "hytale_default", [string]$World = "projtest", [int]$Runs = 2)
$ErrorActionPreference = "Continue"
$env:WG2_COUNTERS = "1"   # the game writes tile/chunk counters to %TEMP%\wg2_counts.txt
$root = Split-Path $PSScriptRoot -Parent
$bridge = "http://127.0.0.1:5006"
$j = Join-Path $env:USERPROFILE ".gradle\jdks\eclipse_adoptium-25-amd64-windows.2\bin"
function Busy { $p = (& "$j\jcmd.exe" | Select-String "devlaunch").ToString().Split(" ")[0]; $o = & "$j\jcmd.exe" $p Thread.print 2>$null; ($o | Select-String "StagedChunkGenerator.generateChunk").Count }
if (-not $Keep) {
    # fresh world files so every run generates the same chunks from scratch
    $saveDir = Join-Path $root "run\saves\$World"
    if (Test-Path $saveDir) { Get-ChildItem $saveDir -Recurse -Directory | Where-Object { $_.Name -in "region","entities","poi" } | Sort-Object { $_.FullName.Length } -Descending | ForEach-Object { if (Test-Path $_.FullName) { [IO.Directory]::Delete($_.FullName, $true) } } }
    & "$PSScriptRoot\dev.ps1" stop | Out-Null; Start-Sleep 2
    & "$PSScriptRoot\dev.ps1" start 2>&1 | Select-Object -Last 1 | Out-Null
}
& "$PSScriptRoot\dev.ps1" call "/openproject?name=$Project" | Out-Null
$sw = [Diagnostics.Stopwatch]::StartNew()
& "$PSScriptRoot\dev.ps1" call "/world?name=$World" | Out-Null
if ($Profile) { Start-Sleep 12; $p0 = (& "$j\jcmd.exe" | Select-String "devlaunch").ToString().Split(" ")[0]; & "$j\jcmd.exe" $p0 JFR.start name=spawn duration=25s filename="$env:TEMP\wg_bench.jfr" settings=profile | Out-Null }
for ($i = 0; $i -lt 120; $i++) { Start-Sleep 3; try { $s = Invoke-RestMethod "$bridge/state" -TimeoutSec 20; if ($s.inWorld) { break } } catch {} }
$idle = 0; while ($sw.Elapsed.TotalSeconds -lt 600) { Start-Sleep 2; if ((Busy) -eq 0) { $idle++ } else { $idle = 0 }; if ($idle -ge 3) { break } }
"spawn area ready after $([int]($sw.Elapsed.TotalSeconds - 6))s"
$spots = @(@(20000, 20000), @(-20000, 15000), @(5000, -30000))
for ($r = 0; $r -lt $Runs; $r++) {
    $x = $spots[$r % 3][0]; $z = $spots[$r % 3][1]
    try { & "$PSScriptRoot\dev.ps1" call "/tp?x=$x&z=$z&up=0" | Out-Null } catch {}
    $gp = (& "$j\jcmd.exe" | Select-String "devlaunch").ToString().Split(" ")[0]; $cpu0 = (Get-Process -Id $gp).TotalProcessorTime.TotalSeconds
    $sw.Restart(); $idle = 0
    if ($false) { $p = (& "$j\jcmd.exe" | Select-String "devlaunch").ToString().Split(" ")[0]; & "$j\jcmd.exe" $p JFR.start name=bench duration=40s filename="$env:TEMP\wg_bench.jfr" settings=profile | Out-Null }
    $started = $false
    while ($sw.Elapsed.TotalSeconds -lt 600) { Start-Sleep 1; $b = Busy; if ($b -gt 0) { $started = $true; $idle = 0 } elseif ($started -or $sw.Elapsed.TotalSeconds -gt 20) { $idle++ }; if ($idle -ge 3) { break } }
    $cpu = (Get-Process -Id $gp).TotalProcessorTime.TotalSeconds - $cpu0
    "fresh view radius at $x,$z generated in $([int]($sw.Elapsed.TotalSeconds - 3))s ($([int]$cpu) CPU-seconds, $([math]::Round($cpu / [math]::Max(1, $sw.Elapsed.TotalSeconds - 3), 1)) cores busy)"
}









