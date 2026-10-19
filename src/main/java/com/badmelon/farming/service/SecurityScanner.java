/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SecurityScanner {
    private static final List<Rule> RULES = List.of(new Rule("\u547d\u4ee4\u6267\u884c", "\u68c0\u6d4b\u5230\u547d\u4ee4\u6267\u884c\u8c03\u7528\uff08Runtime.exec / ProcessBuilder\uff09", "Runtime\\.getRuntime\\(\\)\\s*\\.exec|ProcessBuilder\\s*\\(|\\bexec\\(\\s*[\"'](sh|bash|cmd|powershell|python|perl|wget|curl)"), new Rule("\u5371\u9669Shell\u547d\u4ee4", "\u68c0\u6d4b\u5230\u5371\u9669 shell \u547d\u4ee4\uff08rm -rf / \u683c\u5f0f\u5316 / \u6743\u9650\u63d0\u5347\uff09", "\\brm\\s+-rf\\s+/|\\bmkfs\\b|\\bformat\\s+c:|\\bsudo\\b|\\bchmod\\s+777|\\bchown\\b\\s+root|\\bpasswd\\b|\\bshutdown\\b|\\breboot\\b"), new Rule("\u4e0b\u8f7d\u5e76\u6267\u884c", "\u68c0\u6d4b\u5230\u4e0b\u8f7d\u5e76\u6267\u884c\u6076\u610f\u811a\u672c\u7684\u6a21\u5f0f\uff08curl/wget + \u7ba1\u9053\u6267\u884c\uff09", "(curl|wget)\\s+[^|;&\\n]*\\s*\\|\\s*(sh|bash|python|perl|node|php)"), new Rule("\u53cd\u5f39Shell", "\u68c0\u6d4b\u5230\u53cd\u5f39 shell \u7279\u5f81", "\\bbash\\s+-i\\s*[>|]|/dev/tcp/|\\bnc\\s+-e\\s+/bin/|\\bncat\\s+[^\\n]*\\s+-e|powershell[^\\n]*Invoke-PowerShellTcp"), new Rule("PowerShell\u4e0b\u8f7d\u6267\u884c", "\u68c0\u6d4b\u5230 PowerShell \u8fdc\u7a0b\u4e0b\u8f7d\u6267\u884c", "powershell[^\\n]*(-enc|-ec|FromBase64|IEX\\s*\\(|Invoke-WebRequest[^\\n]*\\|\\s*IEX)"), new Rule("Base64\u6df7\u6dc6", "\u68c0\u6d4b\u5230\u7591\u4f3c Base64 \u7f16\u7801\u7684\u547d\u4ee4\u6df7\u6dc6", "[A-Za-z0-9+/]{80,}={0,2}"), new Rule("Eval\u52a8\u6001\u6267\u884c", "\u68c0\u6d4b\u5230 eval/\u52a8\u6001\u4ee3\u7801\u6267\u884c", "\\beval\\(|\\bFunction\\s*\\([^)]*\\)\\s*\\{|\\bsetTimeout\\([^,]*['\"`]|\\bchild_process\\b|\\bexecSync\\b"), new Rule("\u654f\u611f\u8def\u5f84\u8bbf\u95ee", "\u68c0\u6d4b\u5230\u8bbf\u95ee\u7cfb\u7edf\u654f\u611f\u8def\u5f84", "/etc/passwd|/etc/shadow|/root/|C:\\\\Windows\\\\System32|/proc/|/dev/sd|\\\\AppData\\\\Roaming\\\\Microsoft\\\\Windows\\\\Start Menu"), new Rule("\u5185\u7f51IP", "\u68c0\u6d4b\u5230\u786c\u7f16\u7801\u7684\u5185\u7f51/\u4fdd\u7559 IP\uff08\u53ef\u80fd\u7528\u4e8e\u5185\u7f51\u6e17\u900f\uff09", "(192\\.168\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|127\\.0\\.0\\.1|169\\.254\\.|0\\.0\\.0\\.0)"), new Rule("\u51ed\u636e\u7a83\u53d6", "\u68c0\u6d4b\u5230\u53ef\u80fd\u7684\u51ed\u636e/\u5bc6\u94a5\u7a83\u53d6\u884c\u4e3a", "keylogger|dump\\s+creds|secretsdump|mimikatz|/etc/shadow|SAM\\b|\\bhistory\\s+-c|Get-ItemProperty[^\\n]*HKCU"), new Rule("\u6301\u4e45\u5316\u540e\u95e8", "\u68c0\u6d4b\u5230\u7cfb\u7edf\u81ea\u542f/\u6301\u4e45\u5316\u540e\u95e8\u7279\u5f81", "\\breg\\s+add[^\\n]*Run\\b|HKCU\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run|crond|/etc/cron\\.d|schtasks\\s+/create|systemctl\\s+enable"), new Rule("\u6570\u636e\u5916\u4f20", "\u68c0\u6d4b\u5230\u5411\u5916\u53d1\u9001\u6570\u636e\u7684\u53ef\u7591\u884c\u4e3a", "curl[^\\n]*(-d|--data|--request\\s+POST)[^\\n]*http|Invoke-WebRequest[^\\n]*POST|smtp\\.|mail\\.\\s*send|base64[^\\n]*http"));

    public Result scan(String content) {
        if (content == null || content.isBlank()) {
            return new Result(true, "safe", 0);
        }
        Result result = new Result(true, "safe", 0);
        for (Rule rule : RULES) {
            if (!rule.pattern.matcher(content).find()) continue;
            LinkedHashMap<String, String> hit = new LinkedHashMap<String, String>();
            hit.put("name", rule.name);
            hit.put("desc", rule.desc);
            result.hits.add(hit);
        }
        result.riskCount = result.hits.size();
        if (result.riskCount > 0) {
            result.safe = false;
            result.level = this.isDanger(result) ? "danger" : "risk";
        }
        return result;
    }

    private boolean isDanger(Result result) {
        for (Map<String, String> h : result.hits) {
            String name = h.get("name");
            if (!"\u547d\u4ee4\u6267\u884c".equals(name) && !"\u4e0b\u8f7d\u5e76\u6267\u884c".equals(name) && !"\u53cd\u5f39Shell".equals(name) && !"PowerShell\u4e0b\u8f7d\u6267\u884c".equals(name) && !"\u51ed\u636e\u7a83\u53d6".equals(name)) continue;
            return true;
        }
        return false;
    }

    public static class Result {
        public boolean safe;
        public String level;
        public int riskCount;
        public List<Map<String, String>> hits = new ArrayList<Map<String, String>>();

        Result(boolean safe, String level, int riskCount) {
            this.safe = safe;
            this.level = level;
            this.riskCount = riskCount;
        }
    }

    public static class Rule {
        final String name;
        final String desc;
        final Pattern pattern;

        Rule(String name, String desc, String regex) {
            this.name = name;
            this.desc = desc;
            this.pattern = Pattern.compile(regex, 34);
        }
    }
}

