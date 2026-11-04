<#
.SYNOPSIS
    Autonomous Laptop & Hardware Cluster Auto-Detector for X-Codes & VM Project Hub.
.DESCRIPTION
    Monitors laptops on the local Wi-Fi / Tailscale mesh, measures latency, checks
    Ollama AI acceleration readiness, and synchronizes live status with VM Project Hub.
#>

param(
    [string]$TargetLaptopIp = "192.168.29.227",
    [int]$OllamaPort = 11434,
    [string]$VpsHost = "200.234.41.231",
    [int]$IntervalSeconds = 30,
    [switch]$RunOnce
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   X-Codes Hardware Cluster Auto-Detector (Zero-Drop)     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target High-Compute Laptop: $TargetLaptopIp" -ForegroundColor Yellow
Write-Host "Master 24/7 Hub: $VpsHost" -ForegroundColor Yellow
Write-Host ""

function Send-ClusterHeartbeat {
    param(
        [string]$NodeId,
        [string]$Status,
        [int]$LatencyMs,
        [bool]$OllamaReady,
        [array]$Models
    )

    $php = @"
<?php
require_once '/home/vmstudio/htdocs/vmstudio.digital/wp-load.php';
if (class_exists('VMPJ_Cluster')) {
    VMPJ_Cluster::heartbeat([
        'id'           => '$NodeId',
        'status'       => '$Status',
        'latency_ms'   => $LatencyMs,
        'ollama_ready' => $(if ($OllamaReady) { 'true' } else { 'false' }),
        'models'       => $(if ($Models.Count -gt 0) { '["' + ($Models -join '","') + '"]' } else { '[]' }),
    ]);
}
"@
    $b64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($php))
    ssh -o StrictHostKeyChecking=no -o ConnectTimeout=3 root@$VpsHost "echo '$b64' | base64 -d > /tmp/hb_auto.php && php /tmp/hb_auto.php && rm -f /tmp/hb_auto.php" 2>$null
}

do {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] Probing Cluster Nodes..." -ForegroundColor Gray

    # 1. Probe Laptop 2 (24GB GPU Node)
    $pinger = New-Object System.Net.NetworkInformation.Ping
    $ping = $null
    try {
        $ping = $pinger.Send($TargetLaptopIp, 1000)
    } catch {}
    $isOnline = $false
    $latency = 0
    $ollamaReady = $false
    $models = @()

    if ($ping -and $ping.Status -eq [System.Net.NetworkInformation.IPStatus]::Success) {
        $isOnline = $true
        $latency = [int]$ping.RoundtripTime
        Write-Host "  [+] Laptop 2 (24GB GPU): ONLINE (${latency}ms)" -ForegroundColor Green

        # Check Ollama AI Engine
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $connect = $tcp.BeginConnect($TargetLaptopIp, $OllamaPort, $null, $null)
            $wait = $connect.AsyncWaitHandle.WaitOne(1000, $false)
            if ($wait -and $tcp.Connected) {
                $tcp.EndConnect($connect)
                $tcp.Close()
                $ollamaReady = $true

                # Fetch available models
                try {
                    $resp = Invoke-RestMethod -Uri "http://$TargetLaptopIp`:$OllamaPort/api/tags" -TimeoutSec 2 -ErrorAction SilentlyContinue
                    if ($resp.models) {
                        $models = $resp.models | ForEach-Object { $_.name }
                        Write-Host "      Ollama Ready: $($models -join ', ')" -ForegroundColor Cyan
                    }
                } catch {}
            } else {
                $tcp.Close()
                Write-Host "      Ollama: Offline/Standby (Port $OllamaPort closed)" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "      Ollama: Offline/Standby" -ForegroundColor Yellow
        }

        # Sync to VM Project Hub
        Send-ClusterHeartbeat -NodeId "laptop-gpu" -Status "online" -LatencyMs $latency -OllamaReady $ollamaReady -Models $models
    } else {
        Write-Host "  [-] Laptop 2 (24GB GPU): OFFLINE (Auto-routing 100% to VPS Cloud)" -ForegroundColor DarkGray
        Send-ClusterHeartbeat -NodeId "laptop-gpu" -Status "offline" -LatencyMs 0 -OllamaReady $false -Models @()
    }

    # 2. Local MSI (Laptop 1) Heartbeat
    Send-ClusterHeartbeat -NodeId "laptop-msi" -Status "online" -LatencyMs 0 -OllamaReady $false -Models @()

    if ($RunOnce) { break }
    Start-Sleep -Seconds $IntervalSeconds
} while ($true)
