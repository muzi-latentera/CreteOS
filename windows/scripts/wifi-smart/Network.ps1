# A connected cable alone is insufficient: inspect the route Windows will use.
function Get-StreamNetwork([string]$RouteTarget='1.1.1.1') {
 try {
  if(-not ('CreteOSRouteNative' -as [type])) {
   Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class CreteOSRouteNative { [DllImport("iphlpapi.dll")] public static extern uint GetBestInterfaceEx(byte[] address, out uint index); }'
  }
  $ip=$null
  if(![Net.IPAddress]::TryParse($RouteTarget,[ref]$ip)) {
   $lookup=[Net.Dns]::BeginGetHostAddresses($RouteTarget,$null,$null)
   if(!$lookup.AsyncWaitHandle.WaitOne(1000)){return @{wired=$false}}
   $ip=[Net.Dns]::EndGetHostAddresses($lookup)|Where-Object AddressFamily -eq InterNetwork|Select-Object -First 1
  }
  if(!$ip -or $ip.AddressFamily -ne 'InterNetwork'){return @{wired=$false}}
  $address=New-Object byte[] 16;$address[0]=2
  [Array]::Copy($ip.GetAddressBytes(),0,$address,4,4)
  [uint32]$index=0
  if([CreteOSRouteNative]::GetBestInterfaceEx($address,[ref]$index) -ne 0){return @{wired=$false}}
  foreach($adapter in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
   if($adapter.OperationalStatus -ne 'Up'){continue}
   $properties=$adapter.GetIPProperties();$v4=$properties.GetIPv4Properties()
   if(!$v4 -or $v4.Index -ne $index){continue}
   $wired=[int]$adapter.NetworkInterfaceType -in @(6,62,69,117) -and $adapter.Description -notmatch '(?i)virtual|vpn|tap-windows|tunnel'
   return @{wired=[bool]$wired;interfaceName=$adapter.Name;linkMbps=[Math]::Round($adapter.Speed/1000000)}
  }
 } catch {}
 return @{wired=$false}
}
