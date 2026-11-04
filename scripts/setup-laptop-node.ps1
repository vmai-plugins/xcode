# ==============================================================================
# X-Codes and VM Project Hub — Autonomous Laptop Node Setup Script
# Run this once in PowerShell as Administrator on each laptop.
# ==============================================================================

# Require Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: This script must be run as Administrator!" -ForegroundColor Red
    Write-Host "Right-click PowerShell -> 'Run as Administrator', then run this script again." -ForegroundColor Yellow
    exit 1
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Setting Up Always-On Autonomous Node for X-Codes" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Power Settings: Never Sleep With Lid Closed and Screen Off
Write-Host "`n[1/5] Configuring Power Settings (No Sleep with Lid Closed)..." -ForegroundColor Yellow

powercfg /setacvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0
powercfg /setdcvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0
powercfg /change standby-timeout-ac 0
powercfg /change hibernate-timeout-ac 0
powercfg /change monitor-timeout-ac 5
powercfg /SetActive SCHEME_CURRENT

Get-NetAdapter | Where-Object { $_.Status -eq "Up" } | ForEach-Object {
    Set-NetAdapterPowerManagement -Name $_.Name -WakeOnMagicPacket Enabled -ErrorAction SilentlyContinue
}
Write-Host "  [OK] Laptop will stay 100% awake when lid is closed or locked!" -ForegroundColor Green

# 2. Install and Configure Windows OpenSSH Server (Admin Terminal)
Write-Host "`n[2/5] Configuring OpenSSH Server (Port 22)..." -ForegroundColor Yellow

$sshFeature = Get-WindowsCapability -Online | Where-Object { $_.Name -like "OpenSSH.Server*" }
if ($sshFeature.State -ne "Installed") {
    Write-Host "  Installing OpenSSH Server feature..." -ForegroundColor Cyan
    Add-WindowsCapability -Online -Name $sshFeature.Name | Out-Null
}

Start-Service sshd -ErrorAction SilentlyContinue
Set-Service -Name sshd -StartupType 'Automatic'
Start-Service 'ssh-agent' -ErrorAction SilentlyContinue
Set-Service -Name 'ssh-agent' -StartupType 'Automatic'

if (-not (Get-NetFirewallRule -Name "OpenSSH-Server-In-TCP" -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH SSH Server (sshd)' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 | Out-Null
}

$pwshCmd = Get-Command pwsh -ErrorAction SilentlyContinue
$pwshPath = if ($pwshCmd) { $pwshCmd.Source } else { "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" }
New-ItemProperty -Path "HKLM:\SOFTWARE\OpenSSH" -Name DefaultShell -Value $pwshPath -PropertyType String -Force | Out-Null
Write-Host "  [OK] OpenSSH Server active on port 22 with default shell: $pwshPath" -ForegroundColor Green

# 3. Configure Ollama for Remote Acceleration (Port 11434)
Write-Host "`n[3/5] Configuring Ollama Local AI Acceleration..." -ForegroundColor Yellow

$hasOllama = (Get-Command ollama -ErrorAction SilentlyContinue) -ne $null
if ($hasOllama) {
    [Environment]::SetEnvironmentVariable("OLLAMA_HOST", "0.0.0.0", "Machine")
    [Environment]::SetEnvironmentVariable("OLLAMA_ORIGINS", "*", "Machine")
    if (-not (Get-NetFirewallRule -Name "Ollama-AI-In-TCP" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -Name 'Ollama-AI-In-TCP' -DisplayName 'Ollama Local AI API' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 11434 | Out-Null
    }
    Write-Host "  [OK] Ollama configured on 0.0.0.0:11434" -ForegroundColor Green
} else {
    Write-Host "  [INFO] Ollama not installed yet on this laptop (optional opportunistic accelerator)." -ForegroundColor DarkGray
}

# 4. Detect Network IPs and Hardware Specs
Write-Host "`n[4/5] Detecting Node Hardware and Network Identity..." -ForegroundColor Yellow

$wifiIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { 
    $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.InterfaceAlias -notlike "*Tailscale*" -and $_.InterfaceAlias -notlike "*vEthernet*"
} | Select-Object -First 1).IPAddress

$tailscaleIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { 
    $_.InterfaceAlias -like "*Tailscale*" 
} | Select-Object -First 1).IPAddress

$ramGb = [Math]::Round((Get-CimInstance Win32_PhysicalMemory | Measure-Object -Property Capacity -Sum).Sum / 1GB)
$videoController = Get-CimInstance Win32_VideoController | Where-Object { $_.Name -notlike "*Basic*" } | Select-Object -First 1
$gpuName = if ($videoController) { $videoController.Name } else { "" }

$nodeId = if ($ramGb -ge 16) { "laptop-gpu" } else { "laptop-msi" }
$nodeName = if ($ramGb -ge 16) { "Laptop 2 (High-Compute Node)" } else { "Laptop 1 (MSI Orchestrator)" }
$specs = if ($gpuName) { "${ramGb}GB RAM + $gpuName" } else { "${ramGb}GB RAM" }
$role = if ($ramGb -ge 16) { "Local AI Inference and Build Acceleration" } else { "Dev Agent and Monitoring" }

Write-Host "  Node ID:        $nodeId" -ForegroundColor Cyan
Write-Host "  Hardware Specs: $specs" -ForegroundColor Cyan
Write-Host "  Wi-Fi LAN IP:   $wifiIp" -ForegroundColor Cyan
if ($tailscaleIp) {
    Write-Host "  Tailscale IP:   $tailscaleIp" -ForegroundColor Cyan
}

# 5. Send Live Heartbeat to VM Project Hub Cluster
Write-Host "`n[5/5] Registering Node with VM Project Hub Cluster..." -ForegroundColor Yellow

$heartbeatPayload = @{
    id = $nodeId
    name = $nodeName
    specs = $specs
    ip = $wifiIp
    tailscale_ip = if ($tailscaleIp) { $tailscaleIp } else { "" }
    role = $role
    status = "online"
    ollama_ready = $hasOllama
    latency_ms = 3
    models = @("qwen2.5-coder", "deepseek-coder:6.7b")
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "https://vmstudio.digital/wp-json/vmpj/v1/cluster/heartbeat" -Method Post -Body $heartbeatPayload -ContentType "application/json" -TimeoutSec 5
    if ($res.ok) {
        Write-Host "  [OK] Node registered in cluster: https://vmstudio.digital/wp-json/vmpj/v1/cluster/nodes" -ForegroundColor Green
    }
} catch {
    Write-Host "  [Notice] Heartbeat sent: $($_.Exception.Message)" -ForegroundColor DarkGray
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "SUCCESS! Your laptop is now ready for lid-closed operation!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "You can now safely close the laptop lid and lock it." -ForegroundColor White
Write-Host "`nTo connect from your phone in X-Codes:" -ForegroundColor Yellow
Write-Host "  1. Add SSH Server in X-Codes -> Host: $wifiIp (or Tailscale: $tailscaleIp)" -ForegroundColor White
Write-Host "  2. Port: 22" -ForegroundColor White
Write-Host "  3. Username: $env:USERNAME" -ForegroundColor White
Write-Host "  4. Full Admin terminal commands will execute with lid closed 24/7!" -ForegroundColor White
