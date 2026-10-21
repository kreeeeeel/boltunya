using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
public class LAUNCHER_NAME {
    public static void Main(string[] args) {
        string directory = Environment.GetEnvironmentVariable("AIGRAM_SYNTHETIC_UPDATES");
        string log = Environment.GetEnvironmentVariable("AIGRAM_SYNTHETIC_LAUNCHES");
        string mode = "LAUNCHER_MODE";
        string processes = Environment.GetEnvironmentVariable("AIGRAM_SYNTHETIC_PROCESSES");
        if (!String.IsNullOrEmpty(processes)) File.AppendAllText(processes, Process.GetCurrentProcess().Id + ":" + mode + "\n");
        File.AppendAllText(log, mode + ":" + String.Join("|", args) + "\n");
        if (mode == "old") {
            string release = Environment.GetEnvironmentVariable("AIGRAM_SYNTHETIC_EXIT_RELEASE");
            DateTime deadline = DateTime.UtcNow.AddSeconds(30);
            while (!String.IsNullOrEmpty(release) && !File.Exists(release) && DateTime.UtcNow < deadline) Thread.Sleep(50);
            return;
        }
        string nonce = Array.Find(args, value => value.StartsWith("--update-session="));
        if (nonce == null) return;
        nonce = nonce.Substring("--update-session=".Length);
        string started = Path.Combine(directory, "started-" + nonce + ".json");
        File.WriteAllText(started + ".tmp", "{\"nonce\":\"" + nonce + "\",\"processId\":" + Process.GetCurrentProcess().Id + "}");
        File.Move(started + ".tmp", started);
        Thread.Sleep(1000);
        if (mode == "crash") Environment.Exit(23);
        string healthy = Path.Combine(directory, "healthy-" + nonce + ".json");
        File.WriteAllText(healthy + ".tmp", "{\"nonce\":\"" + nonce + "\"}");
        File.Move(healthy + ".tmp", healthy);
        Thread.Sleep(2000);
    }
}
