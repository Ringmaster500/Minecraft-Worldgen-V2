# Dev environment driver for Worldgen V2 Studio.
#   .\tools\dev.ps1 start            build launch scripts + start the game client, wait for DevBridge
#   .\tools\dev.ps1 reload           compile + hot-swap changed classes into the running game
#   .\tools\dev.ps1 shot [name]      screenshot -> run\devbridge\<name>.png (default shot.png)
#   .\tools\dev.ps1 call "/open?screen=studio"   any DevBridge route (see DevBridge.java)
#   .\tools\dev.ps1 stop             kill the game
param(
    [Parameter(Position = 0)][string]$Cmd = "help",
    [Parameter(Position = 1)][string]$Arg = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $env:USERPROFILE ".gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
$env:JAVA_HOME = $jdk
$java = Join-Path $jdk "bin\java.exe"
$bridge = "http://127.0.0.1:5006"

function Test-Bridge {
    try { Invoke-RestMethod "$bridge/state" -TimeoutSec 2 | Out-Null; return $true } catch { return $false }
}
function Gradle([string[]]$tasks) {
    Push-Location $root
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"   # gradle prints warnings on stderr; judge by exit code only
    $extra = @(); if ($env:WG2_WITH_DH) { $extra += "-PwithDH" }   # also run Distant Horizons in the dev game
    try { & .\gradlew.bat @tasks @extra --console=plain -q; $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $prev; Pop-Location }
    if ($code -ne 0) { throw "gradle $tasks failed (exit $code)" }
}

switch ($Cmd) {
    "start" {
        if (Test-Bridge) { Write-Host "Game already running."; break }
        Gradle @("createLaunchScripts", "compileJava", "processResources")
        Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "`"$root\build\moddev\runClient.cmd`"" -WorkingDirectory "$root\run"
        Write-Host "Waiting for DevBridge (first launch can take a minute or two)..."
        for ($i = 0; $i -lt 180; $i++) { Start-Sleep 2; if (Test-Bridge) { Write-Host "Ready."; break } }
        if (-not (Test-Bridge)) { throw "DevBridge never came up; check run\logs\latest.log" }
        Write-Host "Waiting for the game to finish loading..."
        for ($i = 0; $i -lt 90; $i++) {
            try { $s = Invoke-RestMethod "$bridge/state" -TimeoutSec 5; if ($s.screen -match "TitleScreen") { Write-Host "Title screen reached."; break } } catch {}
            Start-Sleep 2
        }
    }
    "reload" {
        Gradle @("compileJava")
        & $java "$PSScriptRoot\DevReload.java" "$root\build\classes\java\main"
        if ($LASTEXITCODE -eq 2) { Write-Host "Structural change: run '.\tools\dev.ps1 stop' then 'start'." }
    }
    "shot" {
        $name = if ($Arg) { $Arg } else { "shot" }
        $dir = Join-Path $root "run\devbridge"
        New-Item -ItemType Directory -Force $dir | Out-Null
        $out = Join-Path $dir "$name.png"
        Invoke-WebRequest "$bridge/screenshot" -OutFile $out -TimeoutSec 15
        Write-Output $out
    }
    "call" {
        Invoke-RestMethod ($bridge + $Arg) -TimeoutSec 15 | ConvertTo-Json -Compress
    }
    "stop" {
        Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
            Where-Object { $_.CommandLine -match "worldgenv2" -and $_.CommandLine -match "fml.modFolders" } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host "Stopped $($_.ProcessId)" }
    }
    default { Get-Content $PSCommandPath -TotalCount 8 }
}
