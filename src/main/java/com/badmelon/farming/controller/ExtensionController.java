/*
 * 扩展端口 /api/extensions：
 * 面向开发者/管理员的系统可扩展性接口。
 * - GET  /api/extensions           查看系统已注册的扩展点与能力清单
 * - POST /api/extensions/cli-allowlist  追加自定义低风险 CLI 命令白名单（可扩展性核心）
 * 通过该端口可安全地扩展系统能力，无需修改源码。
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.Setting;
import com.badmelon.farming.service.FileOpsService;
import com.badmelon.farming.service.OpenclawImportService;
import com.badmelon.farming.service.SettingsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/extensions"})
public class ExtensionController {
    private final FileOpsService fileOpsService;
    private final OpenclawImportService openclawImportService;
    private final SettingsService settingsService;

    public ExtensionController(FileOpsService fileOpsService, OpenclawImportService openclawImportService,
                               SettingsService settingsService) {
        this.fileOpsService = fileOpsService;
        this.openclawImportService = openclawImportService;
        this.settingsService = settingsService;
    }

    /** 查看系统已注册的扩展点与能力清单 */
    @GetMapping
    public Map<String, Object> overview() {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("success", true);

        // 1. 低风险 CLI 白名单（可扩展）
        Map<String, Object> cli = new LinkedHashMap<String, Object>();
        cli.put("defaultCommands", new ArrayList<String>(new TreeSet<String>(this.fileOpsService.getAllowlist())));
        Setting ext = this.settingsService.get("cli.allowlist");
        cli.put("extendedCommands", ext != null && ext.getSettingValue() != null && !ext.getSettingValue().isBlank()
                ? new ArrayList<String>(new TreeSet<String>(List.of(ext.getSettingValue().split("[,;]")))) : new ArrayList<String>());
        resp.put("cliAllowlist", cli);

        // 2. 内置库能力
        Map<String, Object> library = new LinkedHashMap<String, Object>();
        library.put("description", this.openclawImportService.describe());
        library.put("skillResource", "classpath:/openclaw/skills.json");
        library.put("mcpResource", "classpath:/openclaw/mcp.json");
        library.put("skillImportApi", "POST /api/skills/import-openclaw");
        library.put("mcpImportApi", "POST /api/mcp/import-openclaw");
        resp.put("builtinLibrary", library);

        // 3. 内置工具能力（Agent Loop 调度）
        List<Map<String, String>> tools = new ArrayList<Map<String, String>>();
        tools.add(tool("get_weather", "\u5929\u6c14\u67e5\u8be2"));
        tools.add(tool("get_hot_topics", "\u4eca\u65e5\u70ed\u70b9"));
        tools.add(tool("analyze_data", "\u6570\u636e\u5206\u6790\u4e0e\u56fe\u8868"));
        tools.add(tool("run_cli", "\u4f4e\u98ce\u9669\u547d\u4ee4\u884c\uff08\u767d\u540d\u5355\uff09"));
        tools.add(tool("dispatch_teammates", "\u591a Agent \u534f\u540c\u8c03\u5ea6"));
        tools.add(tool("import_library", "\u5185\u7f6e\u5e93\u5bfc\u5165"));
        tools.add(tool("write_file", "\u6587\u4ef6\u5199\u5165\uff08\u9700\u786e\u8ba4\uff09"));
        tools.add(tool("delete_file", "\u6587\u4ef6\u5220\u9664\uff08\u9700\u786e\u8ba4\uff09"));
        tools.add(tool("describe_workspace", "\u9879\u76ee\u76ee\u5f55\u67e5\u770b"));
        resp.put("builtinTools", tools);

        // 4. 工具策略配置
        Map<String, Object> policy = new LinkedHashMap<String, Object>();
        Setting deny = this.settingsService.get("tool_policy_deny");
        Setting allow = this.settingsService.get("tool_policy_allow");
        policy.put("deny", deny != null ? deny.getSettingValue() : "");
        policy.put("allow", allow != null ? allow.getSettingValue() : "");
        policy.put("hint", "\u8bbe\u7f6e\u952e tool_policy_deny / tool_policy_allow \u53ef\u542f\u7528/\u7981\u7528\u5177\u4f53\u5de5\u5177");
        resp.put("toolPolicy", policy);

        return resp;
    }

    /** 追加自定义低风险 CLI 命令白名单（以逗号分隔），持久化到设置 cli.allowlist */
    @PostMapping(value={"/cli-allowlist"})
    public Map<String, Object> extendCliAllowlist(@RequestBody Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<String, Object>();
        String commands = body.get("commands") != null ? body.get("commands").toString() : "";
        if (commands.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u53c2\u6570 commands \u4e0d\u80fd\u4e3a\u7a7a\uff08\u7528\u9017\u53f7\u5206\u9694\u591a\u4e2a\u547d\u4ee4\uff09");
            return resp;
        }
        // 合并已有 + 新增
        java.util.Set<String> merged = new java.util.HashSet<String>();
        Setting existing = this.settingsService.get("cli.allowlist");
        if (existing != null && existing.getSettingValue() != null && !existing.getSettingValue().isBlank()) {
            for (String c : existing.getSettingValue().split("[,;]")) {
                if (!c.trim().isEmpty()) {
                    merged.add(c.trim());
                }
            }
        }
        int added = 0;
        for (String c : commands.split("[,;]")) {
            String t = c.trim();
            if (!t.isEmpty() && merged.add(t)) {
                added++;
            }
        }
        String mergedStr = String.join(",", merged);
        this.settingsService.save("cli.allowlist", mergedStr, "\u4f4e\u98ce\u9669\u547d\u4ee4\u767d\u540d\u5355\u6269\u5c55\u9879\uff08\u9017\u53f7\u5206\u9694\uff09", 0);
        resp.put("success", true);
        resp.put("added", added);
        resp.put("total", merged.size());
        resp.put("allowlist", new ArrayList<String>(merged));
        return resp;
    }

    private Map<String, String> tool(String name, String desc) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("name", name);
        m.put("description", desc);
        return m;
    }
}
