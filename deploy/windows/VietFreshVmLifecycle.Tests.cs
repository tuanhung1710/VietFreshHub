using System;

internal static class VmLifecycleTests
{
    private static void Require(bool value, string message) { if (!value) throw new Exception(message); }
    private static void Main()
    {
        int attempts = 0, waits = 0;
        bool recovered = VietFreshVmLifecycle.StartWithRetry(
            delegate { attempts++; return attempts == 2; }, delegate { waits++; });
        Require(recovered && attempts == 2 && waits == 1, "A transient startup failure must recover on the second attempt.");
        attempts = waits = 0;
        bool failed = VietFreshVmLifecycle.StartWithRetry(
            delegate { attempts++; return false; }, delegate { waits++; });
        Require(!failed && attempts == 3 && waits == 2, "Persistent startup failures must stop after three attempts.");
        attempts = waits = 0;
        bool immediate = VietFreshVmLifecycle.StartWithRetry(
            delegate { attempts++; return true; }, delegate { waits++; });
        Require(immediate && attempts == 1 && waits == 0, "Successful startup must not wait or launch again.");
        Console.WriteLine("PASS: immediate startup, transient recovery and bounded failure retries. No VM was started or stopped by these tests.");
    }
}
