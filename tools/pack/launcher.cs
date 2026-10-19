using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;

namespace FarmingLauncher
{
    static class Program
    {
        static string BaseDir;
        static string RootDir;
        static string LogDir;

        static void Log(string msg)
        {
            try
            {
                Directory.CreateDirectory(LogDir);
                File.AppendAllText(Path.Combine(LogDir, "launcher.log"),
                    DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "  " + msg + "\r\n");
            }
            catch { }
        }

        static bool PortOpen(int port)
        {
            try
            {
                using (TcpClient c = new TcpClient())
                {
                    IAsyncResult ar = c.BeginConnect("127.0.0.1", port, null, null);
                    if (!ar.AsyncWaitHandle.WaitOne(800)) return false;
                    c.EndConnect(ar);
                    return c.Connected;
                }
            }
            catch { return false; }
        }

        static bool HttpOk(string url)
        {
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                req.Timeout = 3000;
                req.ReadWriteTimeout = 3000;
                using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                {
                    return (int)resp.StatusCode == 200;
                }
            }
            catch { return false; }
        }

        static void StartHidden(string exe, string args, string workDir, string logFile)
        {
            if (!File.Exists(exe)) { Log("missing: " + exe); return; }
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo();
                if (logFile == null || logFile.Length == 0)
                {
                    psi.FileName = exe;
                    psi.Arguments = args;
                    psi.UseShellExecute = false;
                    psi.CreateNoWindow = true;
                }
                else
                {
                    // 通过 cmd 重定向输出到日志，避免子进程 stdout 丢失
                    psi.FileName = "cmd.exe";
                    psi.Arguments = "/c \"\"" + exe + "\" " + args + " > \"" + logFile + "\" 2>&1\"";
                    psi.UseShellExecute = false;
                    psi.CreateNoWindow = true;
                }
                psi.WorkingDirectory = workDir;
                Process.Start(psi);
                Log("started: " + exe + " " + args);
            }
            catch (Exception e)
            {
                Log("start failed: " + exe + " : " + e.Message);
            }
        }

        static void Main()
        {
            BaseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
            RootDir = Path.GetFullPath(Path.Combine(BaseDir, ".."));
            LogDir = Path.Combine(RootDir, "logs");
            Log("launcher start, base=" + BaseDir + ", root=" + RootDir);

            if (!PortOpen(8765))
            {
                string ctxExe = Path.Combine(BaseDir, "context-store", "context-store.exe");
                if (!File.Exists(ctxExe)) ctxExe = Path.Combine(BaseDir, "context-store.exe");
                StartHidden(ctxExe, "--port 8765", RootDir, Path.Combine(LogDir, "ctx_server_exe.log"));
            }

            if (!PortOpen(8080))
                StartHidden(Path.Combine(BaseDir, "FarmingHarness", "FarmingHarness.exe"), "", RootDir, null);

            bool ready = false;
            for (int i = 0; i < 60; i++)
            {
                if (HttpOk("http://127.0.0.1:8080/actuator/health")) { ready = true; break; }
                Thread.Sleep(1000);
            }
            Log("app ready=" + ready);

            if (ready)
            {
                try { Process.Start("http://127.0.0.1:8080/"); }
                catch (Exception e) { Log("open browser failed: " + e.Message); }
            }
            else
            {
                try
                {
                    System.Windows.Forms.MessageBox.Show(
                        "服务启动超时。请检查：\r\n1) MySQL 是否运行\r\n2) 查看 logs\\launcher.log 与 logs\\app_exe_console.log",
                        "生活助理 Harness 启动失败");
                }
                catch { }
            }
        }
    }
}
