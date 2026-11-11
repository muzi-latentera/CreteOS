# G1 smart lid and TV dock

Close the lid and connect the TV dock within 30 seconds to keep the G1 awake. If it stays closed and undocked, the helper requests Windows hibernation. Opening the lid cancels the countdown. If the dock is removed while the lid is closed, a fresh countdown starts. The power button and the dock's display topology use their existing Windows settings.

## Install or remove

On a replacement Windows device, connect the same TV dock with the lid open. Run `INSTALL-SMART-LID.cmd` in the recovery pack. It finds the AV and ASIX USB devices in this dock, copies the helper into native Local AppData, preserves the current Windows plan, and starts it at sign-in. It creates a separate power plan and sets that plan's AC and battery lid actions to Do nothing. The delay defaults to 30 seconds.

Run `REMOVE-SMART-LID.cmd` to stop the helper, remove both scheduled tasks, and restore the original Windows power plan. Use a separately reviewed USB device pair for a different dock. The install script accepts `-DockIds <id1>,<id2>` and `-DelaySeconds <seconds>`. Use two exact PnP instance IDs confirmed present with the TV off and absent with the dock unplugged. Do not restore the old G1 power-plan GUIDs or import its tasks on the replacement.

Both scheduled actions use a hidden launcher, and the supervisor queries Windows power plans without a console window. The desktop observer keeps a heartbeat. Its supervisor restores the native lid action if the observer exits. A separate once-per-minute recovery task also restores it if both stop responding. On machines where Windows rejects a background S4U task, the recovery task runs only in the signed-in desktop session. The helper itself requires a signed-in desktop. If a session or Windows terminates abruptly, a desktop-only recovery task cannot run until the next sign-in. Check the G1 has actually hibernated before putting it away.

The helper checks both USB devices again at the deadline and uses Windows' standard hibernation command. A partially enumerated dock cancels the pending countdown. If lid or dock state remains unknown for 20 seconds, or a hibernation request fails, the worker exits and Windows' original lid action is restored. Logs are in `%LOCALAPPDATA%\CreteOS-Bootstrap\SmartLid`. The installed managed plan is named **CreteOS Smart Lid**.

## Build and diagnostics

Build with Windows' in-box .NET Framework C# compiler from PowerShell:

```powershell
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$source = (Resolve-Path .\SmartLid.cs).ProviderPath
$exe = Join-Path (Get-Location).Path 'SmartLid.exe'
& $compiler /nologo /warnaserror+ /optimize+ /r:System.Windows.Forms.dll "/out:$exe" $source
& $exe --self-test
```

An optional bounded diagnostic can be run from a logged-in session when the live observer is stopped. It logs when it would hibernate and never changes the power plan.

```powershell
& $exe --observe 30 300 (Join-Path $env:TEMP 'smart-lid.log') 'USB\VID_343C&PID_0000\000000000000' 'USB\VID_0B95&PID_1790\00C1A9E9'
```
