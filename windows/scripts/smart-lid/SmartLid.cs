using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;

internal enum LidState { Unknown, Open, Closed }
internal enum DockState { Unknown, Absent, Present }

internal sealed class LidCountdown
{
    private readonly long delay;
    private long? deadline;
    private bool completed;

    public LidCountdown(long delayMilliseconds)
    {
        delay = delayMilliseconds;
    }

    public bool IsDue(long now)
    {
        return deadline.HasValue && now >= deadline.Value;
    }

    public string Update(LidState lid, DockState dock, long now)
    {
        if (lid != LidState.Closed || dock != DockState.Absent)
        {
            bool wasCounting = deadline.HasValue;
            deadline = null;
            completed = false;
            return wasCounting ? "Countdown cancelled" : null;
        }

        if (completed)
            return null;
        if (!deadline.HasValue)
        {
            deadline = now + delay;
            return "Countdown started";
        }
        if (now < deadline.Value)
            return null;

        deadline = null;
        completed = true;
        return "Would hibernate";
    }
}

internal sealed class SmartLid : NativeWindow, IDisposable
{
    private const int PowerBroadcast = 0x218;
    private const int DeviceChange = 0x219;
    private const int DisplayChange = 0x7e;
    private const int PowerSettingChange = 0x8013;
    private const int Suspend = 4;
    private const int ResumeAutomatic = 18;
    private const uint PresentDevices = 0x100;
    private static Guid lidSetting = new Guid("BA3E0F4D-B817-4094-A2D1-D56379E6A0F3");

    private readonly Stopwatch clock = Stopwatch.StartNew();
    private readonly string[] dockIds;
    private readonly string log;
    private readonly LidCountdown countdown;
    private readonly long stopAt;
    private readonly System.Windows.Forms.Timer tick;
    private IntPtr registration;
    private LidState lid;
    private DockState dock;
    private long nextScan;
    private bool ending;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern IntPtr RegisterPowerSettingNotification(IntPtr recipient, ref Guid setting, uint flags);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnregisterPowerSettingNotification(IntPtr handle);

    [DllImport("cfgmgr32.dll", EntryPoint = "CM_Get_Device_ID_List_SizeW", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern uint GetIdListSize(out uint length, string filter, uint flags);

    [DllImport("cfgmgr32.dll", EntryPoint = "CM_Get_Device_ID_ListW", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern uint GetIdList(string filter, [Out] char[] buffer, uint length, uint flags);

    private void Record(string message)
    {
        File.AppendAllText(log, DateTimeOffset.Now.ToString("o") + " " + message + Environment.NewLine);
    }

    private SmartLid(string path, int delaySeconds, int observeSeconds, string[] ids)
    {
        log = path;
        dockIds = ids;
        countdown = new LidCountdown(delaySeconds * 1000L);
        stopAt = observeSeconds * 1000L;
        tick = new System.Windows.Forms.Timer { Interval = 250 };
        tick.Tick += delegate { Advance(); };
        CreateHandle(new CreateParams());
        try
        {
            RegisterLid();
            Record("Dry run started, delay " + delaySeconds + " seconds");
            Advance();
            tick.Start();
        }
        catch
        {
            Dispose();
            throw;
        }
    }

    private void RegisterLid()
    {
        if (registration != IntPtr.Zero)
            UnregisterPowerSettingNotification(registration);
        registration = RegisterPowerSettingNotification(Handle, ref lidSetting, 0);
        if (registration == IntPtr.Zero)
            throw new Win32Exception(Marshal.GetLastWin32Error());
    }

    private DockState ReadDock()
    {
        // PnP can grow the list between the size query and the read.
        for (int attempt = 0; attempt < 3; attempt++)
        {
            uint length;
            if (GetIdListSize(out length, null, PresentDevices) != 0)
                return DockState.Unknown;
            var ids = new char[length];
            if (GetIdList(null, ids, length, PresentDevices) != 0)
                continue;

            var present = new System.Collections.Generic.HashSet<string>(
                new string(ids).Split('\0'), StringComparer.OrdinalIgnoreCase);
            int matches = dockIds.Count(present.Contains);
            return matches == dockIds.Length ? DockState.Present
                : matches == 0 ? DockState.Absent : DockState.Unknown;
        }

        return DockState.Unknown;
    }

    private void Advance()
    {
        if (ending)
            return;

        long now = clock.ElapsedMilliseconds;
        if (now >= stopAt)
        {
            ending = true;
            tick.Stop();
            Record("Dry run ended");
            Application.ExitThread();
            return;
        }

        if (now >= nextScan || countdown.IsDue(now))
        {
            DockState currentDock = ReadDock();
            if (currentDock != dock)
            {
                dock = currentDock;
                Record("Dock " + dock);
            }
            nextScan = now + 3000;
        }

        string change = countdown.Update(lid, dock, now);
        if (change != null)
            Record(change);
    }

    protected override void WndProc(ref Message message)
    {
        if (ending)
        {
            base.WndProc(ref message);
            return;
        }

        if (message.Msg == PowerBroadcast)
        {
            long kind = message.WParam.ToInt64();
            if (kind == PowerSettingChange && message.LParam != IntPtr.Zero
                && (Guid)Marshal.PtrToStructure(message.LParam, typeof(Guid)) == lidSetting
                && Marshal.ReadInt32(message.LParam, 16) == 4)
            {
                int value = Marshal.ReadInt32(message.LParam, 20);
                lid = value == 0 ? LidState.Closed : value == 1 ? LidState.Open : LidState.Unknown;
                Record("Lid " + lid);
                nextScan = 0;
                Advance();
            }
            else if (kind == Suspend || kind == ResumeAutomatic)
            {
                lid = LidState.Unknown;
                nextScan = 0;
                Advance();
                if (kind == ResumeAutomatic)
                    RegisterLid();
            }
        }
        else if (message.Msg == DeviceChange || message.Msg == DisplayChange)
        {
            // Let PnP settle, then re-enumerate the actual devices.
            nextScan = 0;
        }
        base.WndProc(ref message);
    }

    public void Dispose()
    {
        tick.Dispose();
        if (registration != IntPtr.Zero)
            UnregisterPowerSettingNotification(registration);
        DestroyHandle();
    }

    private static void Expect(string actual, string expected)
    {
        if (actual != expected)
            throw new InvalidOperationException("Expected " + expected + ", got " + actual);
    }

    private static void SelfTest()
    {
        var test = new LidCountdown(30000);
        Expect(test.Update(LidState.Unknown, DockState.Absent, 0), null);
        Expect(test.Update(LidState.Open, DockState.Absent, 0), null);
        Expect(test.Update(LidState.Closed, DockState.Absent, 0), "Countdown started");
        Expect(test.Update(LidState.Closed, DockState.Absent, 29999), null);
        Expect(test.Update(LidState.Closed, DockState.Absent, 30000), "Would hibernate");
        Expect(test.Update(LidState.Closed, DockState.Absent, 31000), null);
        Expect(test.Update(LidState.Open, DockState.Absent, 32000), null);
        Expect(test.Update(LidState.Closed, DockState.Absent, 33000), "Countdown started");
        Expect(test.Update(LidState.Open, DockState.Absent, 34000), "Countdown cancelled");
        Expect(test.Update(LidState.Closed, DockState.Absent, 35000), "Countdown started");
        Expect(test.Update(LidState.Closed, DockState.Present, 36000), "Countdown cancelled");
        Expect(test.Update(LidState.Closed, DockState.Present, 70000), null);
        Expect(test.Update(LidState.Closed, DockState.Absent, 71000), "Countdown started");
        Expect(test.Update(LidState.Closed, DockState.Present, 100999), "Countdown cancelled");
        Expect(test.Update(LidState.Closed, DockState.Absent, 101000), "Countdown started");
        Expect(test.Update(LidState.Closed, DockState.Unknown, 102000), "Countdown cancelled");
        Expect(test.Update(LidState.Closed, DockState.Absent, 103000), "Countdown started");
        Expect(test.Update(LidState.Unknown, DockState.Absent, 104000), "Countdown cancelled");
    }

    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            if (args.Length == 1 && args[0] == "--self-test")
            {
                SelfTest();
                Console.WriteLine("18 countdown assertions passed");
                return 0;
            }

            int delay;
            int duration;
            if (args.Length < 6 || args[0] != "--observe"
                || !int.TryParse(args[1], out delay) || delay < 1 || delay > 600
                || !int.TryParse(args[2], out duration) || duration <= delay || duration > 3600)
            {
                Console.Error.WriteLine("Usage: SmartLid --observe <delay seconds> <observation seconds> <log path> <dock USB ID> <dock USB ID> [...]");
                return 2;
            }

            var ids = args.Skip(4).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
            if (ids.Length < 2 || ids.Any(id => !id.StartsWith("USB\\VID_", StringComparison.OrdinalIgnoreCase)))
            {
                Console.Error.WriteLine("Supply at least two distinct, observed dock USB instance IDs.");
                return 2;
            }

            bool owns;
            using (var single = new Mutex(true, "Local\\CreteOS.SmartLidObservation", out owns))
            {
                if (!owns)
                    return 3;
                using (var observer = new SmartLid(Path.GetFullPath(args[3]), delay, duration, ids))
                    Application.Run();
            }
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine(error);
            return 1;
        }
    }
}
