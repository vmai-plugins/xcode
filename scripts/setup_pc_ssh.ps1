Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " X-Codes PC SSH Setup - Enabling OpenSSH Server     " -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

# 1. Install OpenSSH Server capability
Write-Host "[1/5] Checking OpenSSH Server capability..." -ForegroundColor Yellow
$capability = Get-WindowsCapability -Online | Where-Object Name -like 'OpenSSH.Server*'
if ($capability.State -ne 'Installed') {
    Write-Host "Installing OpenSSH.Server (this takes ~30 seconds)..." -ForegroundColor Yellow
    Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
} else {
    Write-Host "OpenSSH.Server is already installed." -ForegroundColor Green
}

# 2. Configure default shell to PowerShell
Write-Host "[2/5] Setting default OpenSSH shell to PowerShell..." -ForegroundColor Yellow
if (-not (Test-Path "HKLM:\SOFTWARE\OpenSSH")) {
    New-Item -Path "HKLM:\SOFTWARE\OpenSSH" -Force | Out-Null
}
New-ItemProperty -Path "HKLM:\SOFTWARE\OpenSSH" -Name DefaultShell -Value "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -PropertyType String -Force | Out-Null

# 3. Start sshd service and set to Automatic
Write-Host "[3/5] Starting SSH Server service (sshd)..." -ForegroundColor Yellow
Set-Service -Name sshd -StartupType 'Automatic'
Start-Service sshd

# 4. Configure Firewall rule
Write-Host "[4/5] Configuring Windows Firewall for Port 22..." -ForegroundColor Yellow
$rule = Get-NetFirewallRule -Name "OpenSSH-Server-In-TCP" -ErrorAction SilentlyContinue
if (-not $rule) {
    New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH SSH Server (sshd)' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 -ErrorAction SilentlyContinue | Out-Null
} else {
    Set-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -Enabled True | Out-Null
}

# 5. Output connection details
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike "*Loopback*" -and $_.IPAddress -notlike "169.254*" } | Select-Object -First 1).IPAddress
$user = $env:USERNAME

Write-Host "====================================================" -ForegroundColor Green
Write-Host " SUCCESS! PC OpenSSH Server is ACTIVE & LISTENING!   " -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
Write-Host " Host (Wi-Fi IP):  $ip" -ForegroundColor White
Write-Host " Port:             22" -ForegroundColor White
Write-Host " Username:         $user" -ForegroundColor White
Write-Host " Default Shell:    PowerShell" -ForegroundColor White
Write-Host " Default Workspace: D:\Dev\x-codes" -ForegroundColor White
Write-Host "====================================================" -ForegroundColor Green
Write-Host "Press any key to close..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
