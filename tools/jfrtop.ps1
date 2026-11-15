# Summarises a JFR recording of the game: where the chunk-generation threads spend their time.
#   .\tools\jfrtop.ps1 [path-to.jfr] [filterRegex]
param([string]$Jfr = "$env:TEMP\wg_bench.jfr", [string]$Filter = ".")
$j = Join-Path $env:USERPROFILE ".gradle\jdks\eclipse_adoptium-25-amd64-windows.2\bin"
$txt = "$Jfr.txt"
& "$j\jfr.exe" print --stack-depth 64 --events jdk.ExecutionSample $Jfr > $txt
$self = @{}; $incl = @{}; $n = 0; $inStack = $false; $st = New-Object System.Collections.Generic.List[string]
foreach ($l in [IO.File]::ReadAllLines($txt)) {
    if ($l -match '^\s*stackTrace = \[') { $inStack = $true; $st.Clear(); continue }
    if ($inStack) {
        if ($l -match '^\s*\]') { $inStack = $false
            if ($st.Count -gt 0 -and ($st -match $Filter).Count -gt 0) { $n++; $self[$st[0]]++; foreach ($k in ($st | Select-Object -Unique)) { $incl[$k]++ } }
            continue }
        $st.Add(($l.Trim() -replace '\s+line:.*$', '' -replace 'com\.worldgenv2\.', '' -replace '\(.*', '')) } }
"samples matching '$Filter': $n"
"--SELF"; $self.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 28 | ForEach-Object { "{0,6} {1}" -f $_.Value, $_.Key }
"--INCLUSIVE"; $incl.GetEnumerator() | Where-Object { $_.Key -match '^(engine|hytale|density|worldstructure|minecraft|biome|props|material|curve)' } | Sort-Object Value -Descending | Select-Object -First 34 | ForEach-Object { "{0,6} {1}" -f $_.Value, $_.Key }

