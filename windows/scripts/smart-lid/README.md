# G1 smart lid diagnostic

This is the attended dry-run build. It reads lid and dock state and logs when it would hibernate. It does not invoke hibernation, edit the power plan, install a startup task or change display topology.

Build with Windows' in-box .NET Framework C# compiler from PowerShell:

```powershell
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$source = (Resolve-Path .\SmartLid.cs).ProviderPath
$exe = Join-Path (Get-Location).Path 'SmartLid.exe'
& $compiler /nologo /warnaserror+ /optimize+ /r:System.Windows.Forms.dll "/out:$exe" $source
& $exe --self-test
```

Run the observer from the logged-in desktop session. The arguments set the countdown, the observation lifetime and the log location, followed by the dock's observed USB instance IDs. Use the exact IDs for this dock; rediscover them on a different unit.

```powershell
& $exe --observe 30 300 (Join-Path $env:TEMP 'smart-lid.log') 'USB\VID_343C&PID_0000\000000000000' 'USB\VID_0B95&PID_1790\00C1A9E9'
```

The observer gets the lid state via the Windows power notification and checks currently present Plug and Play devices. Both dock devices must be present. One matching device or an enumeration error makes dock state unknown and cancels any countdown. Device/display changes prompt a refresh, with a three-second rescan as a fallback and a fresh check at the deadline. The countdown uses a monotonic clock, starts only with a confirmed closed lid and absent dock, and records only one expiry per countdown.

Before deployment, validate actual lid-open and lid-close notifications and the dock transitions in an attended session. An attended timer test requires a reversible temporary lid policy so the native lid-close action does not win the race. A live version still needs an explicit hibernation path, tested startup and a tested rollback or watchdog for process failure. On the source G1, the existing Windows lid action is Hibernate on both AC and DC and the power button is Do nothing. Leave that policy in place until deployment is ready.
