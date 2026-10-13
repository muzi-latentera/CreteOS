# Start CreteOS and GPD tools in Xbox Mode

## Prerequisites

Use a Windows 11 build that offers **Settings → Gaming → Xbox Mode / Full screen experience** and its home-app selector. Availability and wording depend on Windows/device support. Check [Microsoft's Xbox Mode guidance](https://support.microsoft.com/en-us/servicing/os/windows/docs/2025/10/windows-gaming-full-screen-experience).

Install and launch the Windows CreteOS build normally first. Install the GPD utility appropriate to the exact device (for example Motion Assistant) and verify that it controls TDP from the desktop. CreteOS does not implement TDP, fan or controller firmware controls.

Install [AnyFSE from its upstream releases](https://github.com/ashpynov/AnyFSE/releases) using its documented installer. AnyFSE registers a Windows gaming home app; a fullscreen launcher does not supply that registration itself.

## Configure using AnyFSE

1. In AnyFSE, choose a custom launcher and browse to the installed **creteos.exe**. Set the launch command to that executable and its process name to `creteos.exe`. The main window title is `CreteOS`.
2. Add the actual GPD utility executable to AnyFSE's startup applications and enable it. Use only the utility's documented arguments for minimized/background launch. Enable administrator launch only if that utility requires it.
3. Avoid launching the same GPD utility from both AnyFSE and another startup entry. Keep any device services/drivers it needs installed and enabled. CreteOS itself should run with normal user privileges.
4. Save the AnyFSE configuration. In Windows' home-app selector choose **AnyFSE**, then enable entering Xbox Mode at startup. Stock AnyFSE's registered display name remains AnyFSE even when it starts CreteOS.
5. Reboot and perform the device checks below. AnyFSE is the home-app bridge; CreteOS is the launcher you see; the GPD utility owns power controls.

Startup here means the selected user's Windows session after sign-in. This setup does not configure automatic sign-in or replace Windows before sign-in.

## Optional profile generator

This repository can generate a reviewable AnyFSE JSON profile. Node 24 is needed only to run this development helper, not to run the installed launcher. From `windows/`, with your actual paths:

```powershell
npm run prepare:anyfse -- --launcher "C:\Apps\CreteOS\creteos.exe" --gpd-tool "C:\GPD\MotionAssistant.exe"
```

To preserve an existing profile, pass `--config "C:\path\existing-AnyFSE.json"`. Add `--gpd-admin` only if required and `--gpd-args "documented arguments"` only when supported. `--output` chooses a new file; existing output files are never overwritten. The helper cannot validate installed Windows files from another OS.

The generator merges other settings/startup apps, enables one matching GPD entry and replaces launcher matching fields. It does not install AnyFSE, apply settings or enable Xbox Mode. Use the GUI for initial configuration whenever possible.

For manual JSON application, fully close AnyFSE's configuration UI and running home app, back up the existing `AnyFSE.json`, review the generated profile and replace the configuration in the location used by your installed AnyFSE version. Current upstream uses its package **LocalState** folder (or an executable-adjacent `LocalState` fallback); do not assume a ProgramData path. Reopen AnyFSE and verify the launcher and startup entries before saving. Select the home app/startup mode separately in Windows settings.

## Device acceptance checks

- After reboot/sign-in, CreteOS becomes the visible home app and the GPD utility starts once.
- Change TDP through the native utility on battery and AC; verify the change with that utility's own reporting.
- Launch a Steam game and a non-Steam game; confirm controller input, window focus and return to CreteOS after exit.
- Use the device's Xbox/Guide action to return home, then return to the running game.
- Suspend/resume with a game running and with the launcher idle; verify focus, controller input and GPD profiles.
- Open the Windows desktop, switch the selected home app back to Xbox and back to AnyFSE, and confirm the selection persists.
- Check an administrator-dependent utility after reboot: resolve any prompts using its supported startup mechanism rather than granting administrator rights to the whole launcher.

These checks have not yet been performed on a GPD device. Record the device model, Windows build, AnyFSE version and utility version when validating.

## Return to the desktop

Use Windows' desktop-switch action, disable entering Xbox Mode at startup, or select the Xbox app as the home app again. Remove the GPD startup entry from AnyFSE if restoring its previous startup mechanism. Restore the backed-up AnyFSE JSON if needed. No global Windows shell replacement is applied by CreteOS.
