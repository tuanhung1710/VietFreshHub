using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

internal static class VietFreshVmLifecycle
{
    private const string Name = "VietFreshVmLifecycle";
    private const string Vmrun = @"C:\Program Files\VMware\VMware Workstation\vmrun.exe";
    private static readonly string Root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), Name);
    private static readonly ManualResetEvent Finished = new ManualResetEvent(false);
    private static readonly object Gate = new object();
    private static readonly ServiceMainDelegate MainCallback = ServiceMain;
    private static readonly HandlerDelegate HandlerCallback = Handler;
    private static IntPtr statusHandle;
    private static uint currentState;
    private static uint checkpoint;
    private static int stopping;

    [StructLayout(LayoutKind.Sequential)]
    private struct ServiceStatus { public uint Type, State, Accepted, ExitCode, Specific, Checkpoint, WaitHint; }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct ServiceEntry { [MarshalAs(UnmanagedType.LPWStr)] public string Name; public ServiceMainDelegate Callback; }
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] private delegate void ServiceMainDelegate(uint count, IntPtr args);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] private delegate uint HandlerDelegate(uint control, uint eventType, IntPtr eventData, IntPtr context);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern bool StartServiceCtrlDispatcher([In] ServiceEntry[] table);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern IntPtr RegisterServiceCtrlHandlerEx(string name, HandlerDelegate handler, IntPtr context);
    [DllImport("advapi32.dll", SetLastError = true)] private static extern bool SetServiceStatus(IntPtr handle, ref ServiceStatus status);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern IntPtr OpenSCManager(string machine, string database, uint access);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern IntPtr OpenService(IntPtr manager, string name, uint access);
    [DllImport("advapi32.dll", SetLastError = true)] private static extern bool ChangeServiceConfig2(IntPtr service, uint level, ref uint timeout);
    [DllImport("advapi32.dll")] private static extern bool CloseServiceHandle(IntPtr handle);

    private static void Main(string[] args)
    {
        if (args.Length == 1 && args[0] == "--configure-preshutdown")
        {
            IntPtr manager = OpenSCManager(null, null, 1);
            if (manager == IntPtr.Zero) throw new Win32Exception();
            IntPtr service = OpenService(manager, Name, 2);
            try
            {
                if (service == IntPtr.Zero) throw new Win32Exception();
                uint timeout = 300000;
                if (!ChangeServiceConfig2(service, 7, ref timeout)) throw new Win32Exception();
                Console.WriteLine("Preshutdown timeout configured: 300000 ms");
            }
            finally { if (service != IntPtr.Zero) CloseServiceHandle(service); CloseServiceHandle(manager); }
            return;
        }
        if (!StartServiceCtrlDispatcher(new[] { new ServiceEntry { Name = Name, Callback = MainCallback }, new ServiceEntry() })) throw new Win32Exception();
    }

    private static void Log(string text)
    {
        lock (Gate) { File.AppendAllText(Path.Combine(Root, "lifecycle.log"), DateTime.UtcNow.ToString("O") + " " + text + Environment.NewLine); }
    }
    private static void Report(uint state, bool failed)
    {
        lock (Gate)
        {
            currentState = state;
            ServiceStatus status = new ServiceStatus { Type = 0x10, State = state, Accepted = state == 4 ? 0x101u : 0u,
                ExitCode = failed ? 1066u : 0u, Specific = failed ? 1u : 0u,
                Checkpoint = (state == 2 || state == 3) ? ++checkpoint : 0u, WaitHint = (state == 2 || state == 3) ? 30000u : 0u };
            SetServiceStatus(statusHandle, ref status);
        }
    }
    private static string Vmx()
    {
        string value = File.ReadAllText(Path.Combine(Root, "vmx-path.txt")).Trim();
        if (!Path.IsPathRooted(value) || !value.EndsWith(".vmx", StringComparison.OrdinalIgnoreCase) || value.Contains("\"") || value.Contains("\n") || !File.Exists(value)) throw new InvalidOperationException("Invalid VM path.");
        return value;
    }
    private static bool Run(string action, string ending, int timeout, uint pendingState)
    {
        ProcessStartInfo info = new ProcessStartInfo(Vmrun, "-T ws " + action + " \"" + Vmx() + "\" " + ending);
        info.UseShellExecute = false; info.CreateNoWindow = true; info.RedirectStandardError = true; info.RedirectStandardOutput = true;
        using (Process process = new Process())
        {
            process.StartInfo = info;
            process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs e) { if (e.Data != null) Log(e.Data); };
            process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs e) { if (e.Data != null) Log(e.Data); };
            process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine();
            Stopwatch watch = Stopwatch.StartNew();
            while (!process.WaitForExit(5000))
            {
                Report(pendingState, false);
                if (watch.ElapsedMilliseconds > timeout) { process.Kill(); Log("vmrun timed out: " + action); return false; }
            }
            process.WaitForExit();
            Log("vmrun " + action + " exited " + process.ExitCode);
            return process.ExitCode == 0;
        }
    }
    private static void ServiceMain(uint count, IntPtr args)
    {
        statusHandle = RegisterServiceCtrlHandlerEx(Name, HandlerCallback, IntPtr.Zero);
        if (statusHandle == IntPtr.Zero) return;
        Report(2, false);
        ThreadPool.QueueUserWorkItem(delegate(object unused)
        {
            try { Log("Starting existing Ubuntu VM."); if (!Run("start", "nogui", 90000, 2)) throw new InvalidOperationException("VM startup failed."); Report(4, false); }
            catch (Exception error) { Log(error.ToString()); Report(1, true); Finished.Set(); }
        });
        Finished.WaitOne();
    }
    private static uint Handler(uint control, uint eventType, IntPtr eventData, IntPtr context)
    {
        if (control == 4) { Report(currentState, false); return 0; }
        if (control != 1 && control != 15 && control != 5) return 120;
        if (Interlocked.Exchange(ref stopping, 1) != 0) return 0;
        Report(3, false);
        ThreadPool.QueueUserWorkItem(delegate(object unused)
        {
            bool ok = false;
            try
            {
                Log("Graceful Ubuntu shutdown requested.");
                ok = Run("stop", "soft", 240000, 3);
                if (!ok) { Log("Graceful shutdown failed; attempting safe suspend, never hard power-off."); ok = Run("suspend", "soft", 45000, 3); }
            }
            catch (Exception error) { Log(error.ToString()); }
            finally { Report(1, !ok); Finished.Set(); }
        });
        return 0;
    }
}
