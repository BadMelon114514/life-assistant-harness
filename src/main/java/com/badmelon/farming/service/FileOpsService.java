/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class FileOpsService {
    private final Path workspaceRoot;
    private final com.badmelon.farming.service.SettingsService settingsService;

    public FileOpsService(com.badmelon.farming.service.SettingsService settingsService) {
        String dir = System.getProperty("user.dir");
        this.workspaceRoot = Paths.get(dir == null || dir.isBlank() ? "." : dir, new String[0]).toAbsolutePath().normalize();
        this.settingsService = settingsService;
    }

    public Path getWorkspaceRoot() {
        return this.workspaceRoot;
    }

    private Path resolveSafe(String rel) throws IOException {
        if (rel == null || rel.isBlank()) {
            throw new IOException("\u8def\u5f84\u4e0d\u80fd\u4e3a\u7a7a");
        }
        Path target = this.workspaceRoot.resolve(rel.trim().replace('\\', '/')).normalize();
        if (!target.startsWith(this.workspaceRoot)) {
            throw new IOException("\u62d2\u7edd\u8bbf\u95ee\u9879\u76ee\u76ee\u5f55\u4e4b\u5916\u7684\u8def\u5f84: " + rel);
        }
        return target;
    }

    public Map<String, Object> list(String rel) throws IOException {
        Path p = this.resolveSafe(rel);
        if (!Files.isDirectory(p, new LinkOption[0])) {
            throw new IOException("\u76ee\u6807\u4e0d\u662f\u76ee\u5f55: " + (rel.isBlank() ? "." : rel));
        }
        ArrayList items = new ArrayList();
        try (Stream<Path> stream = Files.list(p);){
            stream.sorted().forEach(f -> {
                LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", f.getFileName().toString());
                m.put("dir", Files.isDirectory(f, new LinkOption[0]));
                m.put("size", Files.isDirectory(f, new LinkOption[0]) ? 0L : this.sizeQuiet((Path)f));
                items.add(m);
            });
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("path", this.workspaceRoot.relativize(p).toString().replace('\\', '/'));
        result.put("items", items);
        return result;
    }

    public String read(String rel) throws IOException {
        Path p = this.resolveSafe(rel);
        if (!Files.isRegularFile(p, new LinkOption[0])) {
            throw new IOException("\u76ee\u6807\u4e0d\u662f\u6587\u4ef6: " + rel);
        }
        if (Files.size(p) > 200000L) {
            throw new IOException("\u6587\u4ef6\u8fc7\u5927\uff08>200KB\uff09\uff0c\u4ec5\u652f\u6301\u8bfb\u53d6\u5c0f\u6587\u4ef6: " + rel);
        }
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public Path write(String rel, String content) throws IOException {
        Path p = this.resolveSafe(rel);
        if (Files.isDirectory(p, new LinkOption[0])) {
            throw new IOException("\u76ee\u6807\u662f\u76ee\u5f55\uff0c\u65e0\u6cd5\u5199\u5165: " + rel);
        }
        Path parent = p.getParent();
        if (parent != null) {
            Files.createDirectories(parent, new FileAttribute[0]);
        }
        Files.write(p, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        return p;
    }

    public void delete(String rel) throws IOException {
        Path p = this.resolveSafe(rel);
        if (!Files.exists(p, new LinkOption[0])) {
            throw new IOException("\u76ee\u6807\u4e0d\u5b58\u5728: " + rel);
        }
        if (Files.isDirectory(p, new LinkOption[0])) {
            try (Stream<Path> stream = Files.walk(p, new FileVisitOption[0]);){
                stream.sorted(Comparator.reverseOrder()).forEach(f -> {
                    try {
                        Files.deleteIfExists(f);
                    }
                    catch (IOException iOException) {
                        // empty catch block
                    }
                });
            }
        } else {
            Files.delete(p);
        }
    }

    public Path mkdir(String rel) throws IOException {
        Path p = this.resolveSafe(rel);
        Files.createDirectories(p, new FileAttribute[0]);
        return p;
    }

    public String runScript(String rel, String args) throws IOException {
        Path p = this.resolveSafe(rel);
        if (!Files.isRegularFile(p, new LinkOption[0])) {
            throw new IOException("\u811a\u672c\u4e0d\u5b58\u5728: " + rel);
        }
        String name = p.getFileName().toString().toLowerCase();
        ArrayList<String> cmd = new ArrayList<String>();
        if (name.endsWith(".py")) {
            cmd.add("python");
        } else if (name.endsWith(".js")) {
            cmd.add("node");
        } else {
            throw new IOException("\u4ec5\u652f\u6301\u8fd0\u884c .py / .js \u5de5\u5177\u811a\u672c: " + rel);
        }
        cmd.add(p.toString());
        if (args != null && !args.isBlank()) {
            for (String a : args.trim().split("\\s+")) {
                if (a.isBlank()) continue;
                cmd.add(a);
            }
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(this.workspaceRoot.toFile());
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(60L, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "(\u811a\u672c\u6267\u884c\u8d85\u65f6\uff0c\u5df2\u5f3a\u5236\u7ec8\u6b62)";
            }
            return (String)(proc.exitValue() == 0 ? "" : "(\u9000\u51fa\u7801 " + proc.exitValue() + ")\n") + out;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "(\u811a\u672c\u6267\u884c\u88ab\u4e2d\u65ad)";
        }
    }

    /** 低风险命令白名单：仅允许只读/诊断类命令，禁止任何修改或危险操作（可通过设置键 cli.allowlist 追加） */
    private static final java.util.Set<String> DEFAULT_LOW_RISK_COMMANDS = java.util.Set.of(
        "ipconfig", "ping", "netstat", "systeminfo", "tasklist", "whoami",
        "ver", "hostname", "date", "time", "echo", "dir", "path", "vol",
        "nslookup", "tracert", "sc query", "driverquery"
    );

    private static final String[] SHELL_METACHARS = new String[]{"&", "|", ";", ">", "<", "$", "`", "\n", "\r", "&&", "||"};

    /** 获取当前生效的低风险命令白名单：默认集 + 设置追加集 */
    public java.util.Set<String> getAllowlist() {
        java.util.Set<String> all = new java.util.HashSet<String>(DEFAULT_LOW_RISK_COMMANDS);
        try {
            com.badmelon.farming.entity.Setting setting = this.settingsService.get("cli.allowlist");
            if (setting != null && setting.getSettingValue() != null && !setting.getSettingValue().isBlank()) {
                for (String c : setting.getSettingValue().split("[,;]")) {
                    String t = c.trim();
                    if (!t.isEmpty()) {
                        all.add(t);
                    }
                }
            }
        } catch (Exception e) {
            // 设置读取失败时使用默认白名单
        }
        return all;
    }

    /**
     * 执行低风险只读命令（白名单机制）。
     * 仅允许白名单中的命令，参数禁止包含 shell 元字符。
     */
    public String runCli(String command, String args) throws IOException {
        if (command == null || command.isBlank()) {
            throw new IOException("\u547d\u4ee4\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String cmd = command.trim().toLowerCase();
        boolean allowed = false;
        for (String c : this.getAllowlist()) {
            if (cmd.equals(c) || cmd.startsWith(c + " ")) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IOException("\u547d\u4ee4\u4e0d\u5728\u4f4e\u98ce\u9669\u767d\u540d\u5355\u4e2d\uff0c\u5df2\u62d2\u7edd\u6267\u884c: " + command);
        }
        String full = (command + " " + (args == null ? "" : args)).trim();
        for (String m : SHELL_METACHARS) {
            if (full.contains(m)) {
                throw new IOException("\u547d\u4ee4\u53c2\u6570\u542b\u6709\u7981\u6b62\u7684 shell \u5143\u5b57\u7b26\u300c" + m + "\u300d\uff0c\u5df2\u62d2\u7edd\u6267\u884c");
            }
        }
        ArrayList<String> cmdList = new ArrayList<String>();
        cmdList.add("cmd.exe");
        cmdList.add("/c");
        cmdList.add(full);
        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.directory(this.workspaceRoot.toFile());
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(20L, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "(\u547d\u4ee4\u6267\u884c\u8d85\u65f6\uff0c\u5df2\u5f3a\u5236\u7ec8\u6b62)";
            }
            return (String)(proc.exitValue() == 0 ? "" : "(\u9000\u51fa\u7801 " + proc.exitValue() + ")\n") + out;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "(\u547d\u4ee4\u6267\u884c\u88ab\u4e2d\u65ad)";
        }
    }

    public String describeWorkspace() {
        StringBuilder sb = new StringBuilder();
        sb.append("\u9879\u76ee\u6839\u76ee\u5f55: ").append(this.workspaceRoot).append("\n");
        try (Stream<Path> stream = Files.list(this.workspaceRoot);){
            stream.sorted().forEach(f -> {
                try {
                    boolean dir = Files.isDirectory(f, new LinkOption[0]);
                    sb.append(dir ? "[\u76ee\u5f55] " : "[\u6587\u4ef6] ").append(f.getFileName());
                    if (!dir) {
                        sb.append(" (").append(this.sizeQuiet((Path)f)).append(" B)");
                    }
                    sb.append("\n");
                }
                catch (Exception exception) {
                    // empty catch block
                }
            });
        }
        catch (IOException iOException) {
            // empty catch block
        }
        return sb.toString();
    }

    private long sizeQuiet(Path f) {
        try {
            return Files.size(f);
        }
        catch (IOException e) {
            return 0L;
        }
    }
}

