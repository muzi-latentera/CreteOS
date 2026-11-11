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
    private readonly string heartbeat;
    private readonly Guid livePlan;
    private long nextHeartbeat;
    private long? unknownSince;
    private Process hibernation;
    private long hibernateRequestedAt;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern IntPtr RegisterPowerSettingNotification(IntPtr recipient, ref Guid setting, uint flags);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnregisterPowerSettingNotification(IntPtr handle);

    [DllImport("cfgmgr32.dll", EntryPoint = "CM_Get_Device_ID_List_SizeW", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern uint GetIdListSize(out uint length, string filter, uint flags);

    [DllImport("cfgmgr32.dll", EntryPoint = "CM_Get_Device_ID_ListW", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern uint GetIdList(string filter, [Out] char[] buffer, uint length, uint flags);

    [DllImport("powrprof.dll")]
    private static extern uint PowerGetActiveScheme(IntPtr key, out IntPtr scheme);

    [DllImport("kernel32.dll")]
    private static extern IntPtr LocalFree(IntPtr memory);

    private bool LivePlanIsActive()
    {
        IntPtr scheme;
        if (PowerGetActiveScheme(IntPtr.Zero, out scheme) != 0)
            throw new InvalidOperationException("Could not read the active power plan");
        try { return (Guid)Marshal.PtrToStructure(scheme, typeof(Guid)) == livePlan; }
        finally { LocalFree(scheme); }
    }

    private void RequestHibernate(long now)
    {
        if (heartbeat == null || !LivePlanIsActive() || hibernation != null)
            return;

        Record("Requesting Windows hibernation");
        hibernateRequestedAt = now;
        hibernation = Process.Start(new ProcessStartInfo(
            Path.Combine(Environment.SystemDirectory, "shutdown.exe"), "/h")
        {
            UseShellExecute = false,
            CreateNoWindow = true
        });
        if (hibernation == null)
            throw new InvalidOperationException("Could not start Windows hibernation");
    }

    private void CheckHibernate(long now)
    {
        if (hibernation == null)
            return;

        if (hibernation.HasExited && hibernation.ExitCode != 0)
            throw new InvalidOperationException("Windows rejected hibernation, exit code " + hibernation.ExitCode);
        if (now - hibernateRequestedAt >= 20000)
            throw new InvalidOperationException("Windows did not report suspension after the hibernation request");
    }

    private void Record(string message)
    {
        File.AppendAllText(log, DateTimeOffset.Now.ToString("o") + " " + message + Environment.NewLine);
    }

    private SmartLid(string path, int delaySeconds, int observeSeconds, string[] ids, string heartbeatPath, Guid scheme)
    {
        log = path;
        dockIds = ids;
        countdown = new LidCountdown(delaySeconds * 1000L);
        stopAt = observeSeconds == 0 ? long.MaxValue : observeSeconds * 1000L;
        heartbeat = heartbeatPath;
        livePlan = scheme;
        tick = new System.Windows.Forms.Timer { Interval = 250 };
        tick.Tick += delegate { Advance(); };
        CreateHandle(new CreateParams());
        try
        {
            RegisterLid();
            Record((heartbeat == null ? "Dry run" : "Live mode") + " started, delay " + delaySeconds + " seconds");
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

        if (heartbeat != null)
        {
            CheckHibernate(now);
            if (lid == LidState.Unknown || dock == DockState.Unknown)
            {
                if (!unknownSince.HasValue)
                    unknownSince = now;
                if (now - unknownSince.Value >= 20000)
                    throw new InvalidOperationException("Lid or dock state stayed unknown; restoring Windows lid handling");
            }
            else
            {
                unknownSince = null;
                if (now >= nextHeartbeat)
                {
                    using (File.Open(heartbeat, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite)) { }
                    File.SetLastWriteTimeUtc(heartbeat, DateTime.UtcNow);
                    nextHeartbeat = now + 2000;
                }
            }
        }

        string change = countdown.Update(lid, dock, now);
        if (change == "Would hibernate" && heartbeat != null)
        {
            RequestHibernate(now);
            return;
        }
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
                if (hibernation != null)
                {
                    Record(kind == Suspend ? "Windows suspend notification received" : "Windows resumed");
                    hibernation.Dispose();
                    hibernation = null;
                }
                lid = LidState.Unknown;
                unknownSince = null;
                nextHeartbeat = 0;
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
        if (hibernation != null)
            hibernation.Dispose();
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
            Guid scheme = Guid.Empty;
            bool live = args.Length >= 8 && args[0] == "--live";
            if (args.Length < 6 || (!live && args[0] != "--observe")
                || !int.TryParse(args[1], out delay) || delay < 1 || delay > 600
                || !int.TryParse(args[2], out duration)
                || (live ? duration != 0 || !Guid.TryParse(args[5], out scheme) || scheme == Guid.Empty
                         : duration <= delay || duration > 3600))
            {
                Console.Error.WriteLine("Usage: SmartLid --observe <delay> <duration> <log> <dock ID> <dock ID> [...]");
                Console.Error.WriteLine("       SmartLid --live <delay> 0 <log> <heartbeat> <managed plan GUID> <dock ID> <dock ID> [...]");
                return 2;
            }

            var ids = args.Skip(live ? 6 : 4).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
            if (ids.Length < 2 || ids.Any(id => !id.StartsWith("USB\\VID_", StringComparison.OrdinalIgnoreCase)))
            {
                Console.Error.WriteLine("Supply at least two distinct, observed dock USB instance IDs.");
                return 2;
            }

            bool owns;
            using (var single = new Mutex(true, "Local\\CreteOS.SmartLid", out owns))
            {
                if (!owns)
                    return 3;
                Application.SetUnhandledExceptionMode(UnhandledExceptionMode.ThrowException);
                string heartbeatPath = live ? Path.GetFullPath(args[4]) : null;
                using (var observer = new SmartLid(Path.GetFullPath(args[3]), delay, duration, ids, heartbeatPath, scheme))
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
