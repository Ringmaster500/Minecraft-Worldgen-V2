$Port = 5005

# Check if already running on port 5005
$isOpen = $false
try {
    $client = [System.Net.Sockets.TcpClient]::new()
    $connect = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
    $success = $connect.AsyncWaitHandle.WaitOne(200, $false)
    if ($success -and $client.Connected) {
        $isOpen = $true
        $client.EndConnect($connect)
    }
    $client.Close()
} catch {}

if ($isOpen) {
    Write-Host "Minecraft is already running and listening on port $Port."
    exit 0
}

Write-Host "Starting Minecraft Client with Live Hot-Reloading..."
$cmdPath = Join-Path $PSScriptRoot "..\build\moddev\runClient.cmd"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "`"$cmdPath`""

Write-Host "Waiting for Minecraft JVM debug port $Port to open..."
$maxAttempts = 60
$attempt = 0

while ($attempt -lt $maxAttempts) {
    Start-Sleep -Milliseconds 700
    $attempt++
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $connect = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $success = $connect.AsyncWaitHandle.WaitOne(200, $false)
        if ($success -and $client.Connected) {
            $client.EndConnect($connect)
            $client.Close()
            Write-Host "Minecraft JVM is ready! Attaching VS Code debugger..."
            exit 0
        }
        $client.Close()
    } catch {}
}

Write-Warning "Proceeding to attach..."
exit 0
