using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
public class LAUNCHER_NAME {
    public static void Main(string[] args) {
        string directory = Environment.GetEnvironmentVariable("AIGRAM_SYNTHETIC_UPDATES");
        string log = Environment.GetEnvironmentVariable("AIGRAM_SYNTHETIC_LAUNCHES");
        string mode = "LAUNCHER_MODE";
        File.AppendAllText(log, mode + ":" + String.Join("|", args) + "\n");
        if (mode == "old") return;
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
