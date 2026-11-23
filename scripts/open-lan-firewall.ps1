#Requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'
$name = 'Suming-9099-LAN'
$node = (Get-Command node.exe -ErrorAction Stop).Source
$options = @{
    DisplayName = 'Suming frontend 9099 (LAN only)'
    Direction = 'Inbound'
    Action = 'Allow'
    Enabled = 'True'
    Profile = 'Any'
    Protocol = 'TCP'
    LocalPort = 9099
    RemoteAddress = 'LocalSubnet'
    Program = $node
}
$existing = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
if ($existing) { Set-NetFirewallRule -Name $name @options | Out-Null }
else { New-NetFirewallRule -Name $name @options | Out-Null }
Write-Host 'Allowed TCP 9099 for this Node.js executable from the local subnet only. No backend or database ports were opened.'
