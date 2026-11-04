# ==============================================================================
#  X-Codes Universal PC MCP Server (Model Context Protocol JSON-RPC 2.0)
#  Zero-dependency, runs natively on any Windows PC via built-in PowerShell.
# ==============================================================================

param(
    [int]$Port = 8080,
    [string]$AuthKey = ""
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 1. Discover local and Tailscale IPs
$wifiIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "*Wi*Fi*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty IPAddress -First 1)
if (-not $wifiIp) {
    $wifiIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch "Loopback" -and $_.IPAddress -notmatch "169.254" } | Select-Object -ExpandProperty IPAddress -First 1)
}
$tailscaleIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "*Tailscale*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty IPAddress -First 1)
$pcName = $env:COMPUTERNAME
$userName = $env:USERNAME

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   X-Codes Universal PC MCP Bridge (Model Context Protocol)      " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " PC Name:       $pcName ($userName)" -ForegroundColor White
Write-Host " Port:          $Port" -ForegroundColor White
if ($wifiIp) {
    Write-Host " Wi-Fi URL:     http://$wifiIp`:$Port/mcp" -ForegroundColor Yellow
}
if ($tailscaleIp) {
    Write-Host " Tailscale URL: http://$tailscaleIp`:$Port/mcp" -ForegroundColor Magenta
}
Write-Host " Localhost:     http://localhost:$Port/mcp" -ForegroundColor DarkGray
if ($AuthKey) {
    Write-Host " Auth Key:      Active (requires Bearer or X-MCP-Key)" -ForegroundColor Red
} else {
    Write-Host " Auth Key:      None (Open LAN mode)" -ForegroundColor DarkGray
}
Write-Host "-----------------------------------------------------------------" -ForegroundColor DarkGray
Write-Host " In X-Codes Android App:" -ForegroundColor Cyan
Write-Host " 1. Go to: Settings > Connectors & Integrations > MCP" -ForegroundColor White
Write-Host " 2. Tap:   [+ Custom MCP Server]" -ForegroundColor White
Write-Host " 3. Name:  $pcName" -ForegroundColor White
Write-Host " 4. URL:   http://$($wifiIp):$Port/mcp" -ForegroundColor Green
Write-Host " 5. Tap:   [Save & Connect] -> [Test & Discover Tools]" -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Server is listening... (Press Ctrl+C to stop)" -ForegroundColor Yellow
Write-Host ""

# 2. Define MCP Tools Metadata
$mcpTools = @(
    @{
        name = "pc_system_info"
        description = "Get real-time PC specifications, CPU load, RAM usage, storage drives, battery status, and OS info."
        inputSchema = @{
            type = "object"
            properties = @{}
        }
    },
    @{
        name = "pc_run_command"
        description = "Execute any PowerShell, CMD, Git, build, or script command on this PC and return stdout/stderr."
        inputSchema = @{
            type = "object"
            properties = @{
                command = @{
                    type = "string"
                    description = "The shell command to execute."
                }
                working_directory = @{
                    type = "string"
                    description = "Optional folder to run the command in. Defaults to current directory."
                }
                timeout_seconds = @{
                    type = "number"
                    description = "Timeout in seconds (default: 60)."
                }
            }
            required = @("command")
        }
    },
    @{
        name = "pc_read_file"
        description = "Read the contents of a file on this PC."
        inputSchema = @{
            type = "object"
            properties = @{
                path = @{
                    type = "string"
                    description = "Absolute or relative path to the file."
                }
                max_lines = @{
                    type = "number"
                    description = "Maximum lines to read (default 500)."
                }
            }
            required = @("path")
        }
    },
    @{
        name = "pc_write_file"
        description = "Create or overwrite a file on this PC with new content."
        inputSchema = @{
            type = "object"
            properties = @{
                path = @{
                    type = "string"
                    description = "Absolute or relative path to the file."
                }
                content = @{
                    type = "string"
                    description = "The full text content to write."
                }
                append = @{
                    type = "boolean"
                    description = "If true, append to file instead of overwriting."
                }
            }
            required = @("path", "content")
        }
    },
    @{
        name = "pc_list_files"
        description = "List files and directories in a PC folder with sizes and modification times."
        inputSchema = @{
            type = "object"
            properties = @{
                path = @{
                    type = "string"
                    description = "Path to directory. Defaults to current directory."
                }
                recursive = @{
                    type = "boolean"
                    description = "Whether to list subdirectories recursively (default false)."
                }
            }
        }
    },
    @{
        name = "pc_open_path"
        description = "Open an app, folder, URL, or file on the Windows desktop."
        inputSchema = @{
            type = "object"
            properties = @{
                target = @{
                    type = "string"
                    description = "Folder path, file path, app executable, or URL to open."
                }
            }
            required = @("target")
        }
    },
    @{
        name = "pc_get_processes"
        description = "List active processes running on this PC sorted by CPU or memory consumption."
        inputSchema = @{
            type = "object"
            properties = @{
                top = @{
                    type = "number"
                    description = "Number of processes to return (default 15)."
                }
                sort_by = @{
                    type = "string"
                    enum = @("cpu", "memory")
                    description = "Sort criteria ('cpu' or 'memory')."
                }
            }
        }
    }
)

# 3. Tool Implementations
function Invoke-PcTool([string]$ToolName, [hashtable]$ToolArgs) {
    switch ($ToolName) {
        "pc_system_info" {
            $os = Get-CimInstance Win32_OperatingSystem
            $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
            $drives = Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" | ForEach-Object {
                "$($_.DeviceID) [Free: $([math]::Round($_.FreeSpace / 1GB, 1)) GB / Total: $([math]::Round($_.Size / 1GB, 1)) GB]"
            }
            $totalRam = [math]::Round($os.TotalVisibleMemorySize / 1MB, 2)
            $freeRam = [math]::Round($os.FreePhysicalMemory / 1MB, 2)
            $usedRam = [math]::Round($totalRam - $freeRam, 2)
            $ramPct = [math]::Round(($usedRam / $totalRam) * 100, 1)

            $info = @"
Computer Name:    $($env:COMPUTERNAME)
Logged-in User:   $($env:USERNAME)
OS Name:          $($os.Caption) ($($os.Version))
Architecture:     $($os.OSArchitecture)
CPU:              $($cpu.Name) ($($cpu.NumberOfLogicalProcessors) logical cores)
RAM Usage:        $usedRam GB / $totalRam GB ($ramPct% used)
Free RAM:         $freeRam GB
Local Storage:    $($drives -join ', ')
Wi-Fi IP:         $wifiIp
Tailscale IP:     $tailscaleIp
Current Time:     $((Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))
"@
            return $info
        }

        "pc_run_command" {
            $cmd = $ToolArgs["command"]
            if (-not $cmd) { return "Error: 'command' argument is required." }
            $workDir = $ToolArgs["working_directory"]
            if ($workDir -and (Test-Path $workDir)) {
                Push-Location $workDir
            }
            $timeoutSec = if ($ToolArgs["timeout_seconds"]) { [int]$ToolArgs["timeout_seconds"] } else { 60 }

            $startTime = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $processInfo = New-Object System.Diagnostics.ProcessStartInfo
                $processInfo.FileName = "powershell.exe"
                $processInfo.Arguments = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command `"$cmd`""
                $processInfo.RedirectStandardOutput = $true
                $processInfo.RedirectStandardError = $true
                $processInfo.UseShellExecute = $false
                $processInfo.CreateNoWindow = $true

                $process = New-Object System.Diagnostics.Process
                $process.StartInfo = $processInfo
                $process.Start() | Out-Null

                $stdoutTask = $process.StandardOutput.ReadToEndAsync()
                $stderrTask = $process.StandardError.ReadToEndAsync()

                $completed = $process.WaitForExit($timeoutSec * 1000)
                if (-not $completed) {
                    $process.Kill()
                    return "Error: Command timed out after $timeoutSec seconds."
                }
                $stdout = $stdoutTask.Result
                $stderr = $stderrTask.Result
                $startTime.Stop()

                $output = ""
                if ($stdout) { $output += $stdout.Trim() }
                if ($stderr) {
                    if ($output) { $output += "`n`n[STDERR]:`n" }
                    $output += $stderr.Trim()
                }
                if (-not $output) {
                    $output = "(Command executed successfully with no output. Exit code: $($process.ExitCode))"
                }
                return $output
            } finally {
                if ($workDir) { Pop-Location }
            }
        }

        "pc_read_file" {
            $path = $ToolArgs["path"]
            if (-not (Test-Path $path)) { return "Error: File not found at path: $path" }
            $maxLines = if ($ToolArgs["max_lines"]) { [int]$ToolArgs["max_lines"] } else { 500 }
            $lines = Get-Content -Path $path -TotalCount $maxLines -Raw
            return $lines
        }

        "pc_write_file" {
            $path = $ToolArgs["path"]
            $content = $ToolArgs["content"]
            $append = [bool]$ToolArgs["append"]
            $parent = Split-Path -Path $path -Parent
            if ($parent -and -not (Test-Path $parent)) {
                New-Item -ItemType Directory -Path $parent -Force | Out-Null
            }
            if ($append) {
                Add-Content -Path $path -Value $content -Encoding UTF8
                return "Successfully appended to $path"
            } else {
                Set-Content -Path $path -Value $content -Encoding UTF8
                return "Successfully wrote $([System.Text.Encoding]::UTF8.GetByteCount($content)) bytes to $path"
            }
        }

        "pc_list_files" {
            $target = if ($ToolArgs["path"]) { $ToolArgs["path"] } else { (Get-Location).Path }
            if (-not (Test-Path $target)) { return "Error: Directory not found: $target" }
            $recurse = [bool]$ToolArgs["recursive"]
            $items = Get-ChildItem -Path $target -Recurse:$recurse | Select-Object -First 100 | ForEach-Object {
                $type = if ($_.PSIsContainer) { "[DIR] " } else { "[FILE]" }
                $size = if (-not $_.PSIsContainer) { "$([math]::Round($_.Length / 1KB, 1)) KB" } else { "-" }
                "{0} {1,-35} {2,10}  {3}" -f $type, $_.Name, $size, $_.LastWriteTime.ToString("yyyy-MM-dd HH:mm")
            }
            return ($items -join "`n")
        }

        "pc_open_path" {
            $target = $ToolArgs["target"]
            Start-Process $target
            return "Successfully launched '$target' on Windows desktop."
        }

        "pc_get_processes" {
            $top = if ($ToolArgs["top"]) { [int]$ToolArgs["top"] } else { 15 }
            $sort = if ($ToolArgs["sort_by"] -eq "cpu") { "CPU" } else { "WorkingSet64" }
            $procs = Get-Process | Sort-Object -Property $sort -Descending | Select-Object -First $top | ForEach-Object {
                $memMb = [math]::Round($_.WorkingSet64 / 1MB, 1)
                $cpuSec = [math]::Round($_.CPU, 1)
                "{0,-25} PID: {1,-6} RAM: {2,7} MB  CPU: {3,6}s" -f $_.ProcessName, $_.Id, $memMb, $cpuSec
            }
            return ($procs -join "`n")
        }

        default {
            return "Error: Unknown tool '$ToolName'"
        }
    }
}

# 4. TCP HTTP Server Loop
$tcpListener = New-Object System.Net.Sockets.TcpListener ([System.Net.IPAddress]::Any, $Port)
try {
    $tcpListener.Start()
} catch {
    Write-Error "Could not start listener on port $Port : $_"
    exit 1
}

try {
    while ($true) {
        $client = $tcpListener.AcceptTcpClient()
        $stream = $client.GetStream()
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        $writer = New-Object System.IO.StreamWriter($stream, (New-Object System.Text.UTF8Encoding($false)))
        $writer.AutoFlush = $true

        try {
            # Read HTTP Request line
            $requestLine = $reader.ReadLine()
            if (-not $requestLine) {
                $client.Close()
                continue
            }

            $parts = $requestLine.Split(" ")
            $method = $parts[0]
            $url = if ($parts.Length -gt 1) { $parts[1] } else { "/" }

            # Read Headers
            $headers = @{}
            $contentLength = 0
            while ($true) {
                $line = $reader.ReadLine()
                if ([string]::IsNullOrEmpty($line)) { break }
                $headerParts = $line.Split(":", 2)
                if ($headerParts.Length -eq 2) {
                    $key = $headerParts[0].Trim().ToLower()
                    $val = $headerParts[1].Trim()
                    $headers[$key] = $val
                    if ($key -eq "content-length") {
                        [int]::TryParse($val, [ref]$contentLength) | Out-Null
                    }
                }
            }

            # Optional Auth Check
            if ($AuthKey) {
                $authHeader = $headers["authorization"]
                $mcpKeyHeader = $headers["x-mcp-key"]
                $providedKey = ""
                if ($authHeader -and $authHeader.StartsWith("Bearer ", [System.StringComparison]::OrdinalIgnoreCase)) {
                    $providedKey = $authHeader.Substring(7).Trim()
                } elseif ($mcpKeyHeader) {
                    $providedKey = $mcpKeyHeader.Trim()
                }
                if ($providedKey -ne $AuthKey) {
                    $errJson = '{"jsonrpc":"2.0","error":{"code":-32000,"message":"Unauthorized: Invalid or missing API key."}}'
                    $writer.Write("HTTP/1.1 401 Unauthorized`r`nContent-Type: application/json`r`nAccess-Control-Allow-Origin: *`r`nContent-Length: $($errJson.Length)`r`n`r`n$errJson")
                    $client.Close()
                    continue
                }
            }

            # Handle CORS preflight
            if ($method -eq "OPTIONS") {
                $writer.Write("HTTP/1.1 200 OK`r`nAccess-Control-Allow-Origin: *`r`nAccess-Control-Allow-Methods: GET, POST, OPTIONS`r`nAccess-Control-Allow-Headers: Content-Type, Authorization, X-MCP-Key`r`nContent-Length: 0`r`n`r`n")
                $client.Close()
                continue
            }

            # Handle GET requests (Dashboard & Health)
            if ($method -eq "GET") {
                $html = @"
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>X-Codes PC MCP Bridge</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 40px; margin: 0; }
        .card { max-width: 650px; margin: 0 auto; background: #1e293b; border-radius: 12px; padding: 28px; border: 1px solid #334155; }
        h1 { color: #38bdf8; margin-top: 0; font-size: 24px; }
        .badge { display: inline-block; background: #059669; color: #fff; padding: 4px 10px; border-radius: 999px; font-size: 13px; font-weight: bold; }
        .url-box { background: #0f172a; padding: 12px 16px; border-radius: 8px; font-family: monospace; font-size: 15px; color: #4ade80; margin: 12px 0; border: 1px solid #334155; word-break: break-all; }
        .label { color: #94a3b8; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; }
        ul { padding-left: 20px; color: #cbd5e1; }
        li { margin: 6px 0; }
    </style>
</head>
<body>
    <div class="card">
        <span class="badge">● Active & Ready</span>
        <h1>X-Codes PC MCP Bridge</h1>
        <p>This PC is active and ready to connect with X-Codes Android IDE.</p>
        <div class="label">Connect in X-Codes (Wi-Fi):</div>
        <div class="url-box">http://$wifiIp`:$Port/mcp</div>
        $(if ($tailscaleIp) { "<div class='label'>Connect in X-Codes (Tailscale):</div><div class='url-box'>http://$tailscaleIp`:$Port/mcp</div>" })
        <div class="label">PC Details:</div>
        <ul>
            <li><strong>Host:</strong> $pcName ($userName)</li>
            <li><strong>Port:</strong> $Port</li>
            <li><strong>Advertised Tools:</strong> $($mcpTools.Count) tools available</li>
        </ul>
    </div>
</body>
</html>
"@
                $htmlBytes = [System.Text.Encoding]::UTF8.GetBytes($html)
                $writer.Write("HTTP/1.1 200 OK`r`nContent-Type: text/html; charset=utf-8`r`nAccess-Control-Allow-Origin: *`r`nContent-Length: $($htmlBytes.Length)`r`n`r`n$html")
                $client.Close()
                continue
            }

            # Read Body for POST
            $body = ""
            if ($contentLength -gt 0) {
                $chars = New-Object char[] $contentLength
                $readCount = 0
                while ($readCount -lt $contentLength) {
                    $read = $reader.Read($chars, $readCount, $contentLength - $readCount)
                    if ($read -le 0) { break }
                    $readCount += $read
                }
                $body = New-Object string ($chars, 0, $readCount)
            }

            # Parse JSON-RPC Request
            $jsonReq = $null
            try {
                $jsonReq = ConvertFrom-Json $body -ErrorAction Stop
            } catch {
                $errJson = '{"jsonrpc":"2.0","error":{"code":-32700,"message":"Parse error: invalid JSON"}}'
                $writer.Write("HTTP/1.1 400 Bad Request`r`nContent-Type: application/json`r`nAccess-Control-Allow-Origin: *`r`nContent-Length: $($errJson.Length)`r`n`r`n$errJson")
                $client.Close()
                continue
            }

            $reqId = $jsonReq.id
            $rpcMethod = $jsonReq.method
            Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] RPC Method: $rpcMethod (ID: $reqId)" -ForegroundColor DarkCyan

            # Handle MCP Methods
            $responseObj = $null

            if ($rpcMethod -eq "tools/list") {
                $responseObj = @{
                    jsonrpc = "2.0"
                    id = $reqId
                    result = @{
                        tools = $mcpTools
                    }
                }
            }
            elseif ($rpcMethod -eq "tools/call") {
                $toolName = $jsonReq.params.name
                $arguments = @{}
                if ($jsonReq.params.arguments) {
                    $jsonReq.params.arguments.PSObject.Properties | ForEach-Object {
                        $arguments[$_.Name] = $_.Value
                    }
                }
                Write-Host "  -> Invoking Tool: $toolName" -ForegroundColor Yellow
                $callResult = Invoke-PcTool -ToolName $toolName -ToolArgs $arguments

                $responseObj = @{
                    jsonrpc = "2.0"
                    id = $reqId
                    result = @{
                        content = @(
                            @{
                                type = "text"
                                text = [string]$callResult
                            }
                        )
                    }
                }
            }
            elseif ($rpcMethod -eq "initialize" -or $rpcMethod -eq "ping") {
                $responseObj = @{
                    jsonrpc = "2.0"
                    id = $reqId
                    result = @{
                        protocolVersion = "2024-11-05"
                        capabilities = @{
                            tools = @{
                                listChanged = $false
                            }
                        }
                        serverInfo = @{
                            name = "X-Codes Universal PC MCP Bridge"
                            version = "1.0.0"
                        }
                    }
                }
            }
            else {
                $responseObj = @{
                    jsonrpc = "2.0"
                    id = $reqId
                    error = @{
                        code = -32601
                        message = "Method not found: '$rpcMethod'"
                    }
                }
            }

            $respJson = ConvertTo-Json $responseObj -Depth 10 -Compress
            $respBytes = [System.Text.Encoding]::UTF8.GetBytes($respJson)
            $writer.Write("HTTP/1.1 200 OK`r`nContent-Type: application/json; charset=utf-8`r`nAccess-Control-Allow-Origin: *`r`nContent-Length: $($respBytes.Length)`r`n`r`n$respJson")

        } catch {
            Write-Warning "Client error: $_"
        } finally {
            $client.Close()
        }
    }
} finally {
    $tcpListener.Stop()
    Write-Host "Server stopped." -ForegroundColor Red
}
