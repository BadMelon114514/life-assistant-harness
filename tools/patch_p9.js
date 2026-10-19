
const fs = require('fs');
const p = 'src/main/java/com/badmelon/farming/service/ToolScheduler.java';
let s = fs.readFileSync(p, 'utf8');
function rep(oldStr, newStr, expected) {
  const n = s.split(oldStr).length - 1;
  if (n !== expected) throw new Error('expected ' + expected + ' got ' + n + ' for: ' + oldStr.slice(0, 80));
  s = s.split(oldStr).join(newStr);
}
rep('                this.addUsedTools(usedTools, roundResult.builtinName);\n', '', 1);
rep('                this.addUsedTools(usedTools, roundResult.mcpName);\n', '', 1);
rep('                this.addUsedTools(usedTools, roundResult.skillName);\n', '', 1);
rep('            if (!merged) {\n', '            usedTools.addAll(roundResult.usedCalls);\n            if (!merged) {\n', 1);
const oldAdd = [
'    private void addUsedTools(HashSet<String> usedTools, String names) {',
'        if (names == null) {',
'            return;',
'        }',
'        for (String n : names.split(",\\\\s*")) {',
'            if (n.isBlank()) continue;',
'            String lower = n.trim().toLowerCase();',
'            usedTools.add(lower);',
'            int sp = lower.indexOf(\' \');',
'            if (sp > 0) {',
'                usedTools.add(lower.substring(0, sp));',
'            }',
'        }',
'    }'
].join('\n');
const newAdd = [
'    /**',
'     * 调用签名：工具名 + 关键参数，用于区分“同一工具的不同参数调用”。',
'     * 仅按工具名去重会把“读取 A 文件”与“读取 B 文件”误判为重复。',
'     */',
'    private String callSignature(String toolName, JsonNode node) {',
'        String base = toolName == null ? "" : toolName.trim().toLowerCase();',
'        if (node == null) {',
'            return base;',
'        }',
'        StringBuilder sb = new StringBuilder(base);',
'        String[] fields = new String[]{"path", "file", "command", "args2", "doc", "operation", "col1", "col2", "topN", "query", "url", "city", "name", "id"};',
'        for (String f : fields) {',
'            JsonNode v = node.get(f);',
'            if (v == null || v.isNull()) continue;',
'            String text = v.asText("");',
'            if (text.isBlank()) continue;',
'            sb.append("|").append(f).append("=").append(text.trim());',
'        }',
'        JsonNode args = node.get("args");',
'        if (args != null && !args.isNull()) {',
'            String text = args.toString().trim();',
'            if (!text.isBlank() && !"{}".equals(text)) {',
'                sb.append("|args=").append(text);',
'            }',
'        }',
'        return sb.toString();',
'    }',
'',
'    private String usedDisplay(Set<String> usedTools) {',
'        ArrayList<String> names = new ArrayList<String>();',
'        for (String u : usedTools) {',
'            if (u == null || u.isBlank()) continue;',
'            int p = u.indexOf(0x7c);',
'            String d = p > 0 ? u.substring(0, p) : u;',
'            if (!names.contains(d)) names.add(d);',
'        }',
'        return String.join(", ", names);',
'    }'
].join('\n');
rep(oldAdd, newAdd, 1);
rep('                    result.log = "已调用内置工具「get_current_time」（时间强意图直调）";\n', '                    result.log = "已调用内置工具「get_current_time」（时间强意图直调）";\n                    result.usedCalls.add("get_current_time");\n', 1);
rep('                        result.log = "已调用 MCP 工具「local-geo」（定位强意图直调，自动获取本机时间与地理位置）";\n', '                        result.log = "已调用 MCP 工具「local-geo」（定位强意图直调，自动获取本机时间与地理位置）";\n                        result.usedCalls.add("local-geo");\n', 1);
rep('                    result.log = "已调用内置工具「analyze_data」（强意图直调）";\n', '                    result.log = "已调用内置工具「analyze_data」（强意图直调）";\n                    result.usedCalls.add("analyze_data");\n', 1);
rep('                    result.log = "已调用内置工具「dispatch_teammates」";\n', '                    result.log = "已调用内置工具「dispatch_teammates」";\n                    result.usedCalls.add("dispatch_teammates");\n', 1);
rep('                String toolName = t.path("tool").asText("");\n                String baseToolName = toolName.trim().toLowerCase().split("\\\\s+")[0];\n                if (used.contains(toolName.trim().toLowerCase()) || used.contains(baseToolName)) {\n                    results.append("【工具「").append(toolName).append("」本轮已执行过，跳过重复执行】\\n");\n                    continue;\n                }\n', '                String toolName = t.path("tool").asText("");\n                String callSig = this.callSignature(toolName, t);\n                if (used.contains(callSig)) {\n                    results.append("【工具「").append(toolName).append("」本轮已执行过相同调用，跳过重复执行】\\n");\n                    continue;\n                }\n', 1);
rep('                    names.add(toolName);\n', '                    names.add(toolName);\n                    result.usedCalls.add(callSig);\n', 4);
rep('            String name = decision.path("tool").asText("");\n            String args = decision.has("args") ? decision.path("args").toString() : "{}";\n            if (name.isBlank()) {\n                result.log = "MCP 决策缺少工具名";\n                return result;\n            }\n            if (used.contains(name.toLowerCase())) {\n                result.log = "MCP 工具「" + name + "」本轮已执行过，跳过重复调用";\n                return result;\n            }\n', '            String name = decision.path("tool").asText("");\n            String args = decision.has("args") ? decision.path("args").toString() : "{}";\n            if (name.isBlank()) {\n                result.log = "MCP 决策缺少工具名";\n                return result;\n            }\n            String callSig = this.callSignature(name, decision);\n            if (used.contains(callSig)) {\n                result.log = "MCP 工具「" + name + "」本轮已执行过相同调用，跳过重复调用";\n                return result;\n            }\n', 1);
rep('                result.log = "MCP 工具「" + name + "」被工具策略禁用";\n                return result;\n', '                result.log = "MCP 工具「" + name + "」被工具策略禁用";\n                result.usedCalls.add(callSig);\n                return result;\n', 1);
rep('                result.log = "MCP 工具「" + name + "」不存在，未执行任何调用";\n                return result;\n', '                result.log = "MCP 工具「" + name + "」不存在，未执行任何调用";\n                result.usedCalls.add(callSig);\n                return result;\n', 1);
rep('                result.log = "MCP 工具「" + matched.getName() + "」涉及高危操作，等待用户确认";\n                return result;\n', '                result.log = "MCP 工具「" + matched.getName() + "」涉及高危操作，等待用户确认";\n                result.usedCalls.add(callSig);\n                return result;\n', 1);
rep('            String out = this.callMcp(matched, args);\n            result.mcpResult = out;\n', '            String out = this.callMcp(matched, args);\n            result.usedCalls.add(callSig);\n            result.mcpResult = out;\n', 1);
rep('            if (used.contains(name.toLowerCase())) {\n                result.log = "Skill「" + name + "」本轮已应用过，跳过重复应用";\n                return result;\n            }\n', '            String skillSig = "skill|" + name.trim().toLowerCase();\n            if (used.contains(skillSig)) {\n                result.log = "Skill「" + name + "」本轮已应用过，跳过重复应用";\n                return result;\n            }\n', 1);
rep('                result.log = "已应用 Skill「" + s.getName() + "」";\n', '                result.usedCalls.add(skillSig);\n                result.log = "已应用 Skill「" + s.getName() + "」";\n', 1);
rep('        public int loopRounds = 0;\n', '        public List<String> usedCalls = new ArrayList<String>();\n        public int loopRounds = 0;\n', 1);
rep('            sb.append("\\n【本轮已执行过的工具】\\n").append(String.join(", ", usedTools)).append("\\n禁止重复调用上述工具；若信息已足够，直接 call=false。\\n");', '            sb.append("\\n【本轮已执行过的工具】\\n").append(this.usedDisplay(usedTools)).append("\\n禁止重复调用上述工具；同一工具如需不同参数（如读取另一个文件）可以继续调用；若信息已足够，直接 call=false。\\n");', 1);
fs.writeFileSync(p, s);
console.log('patched, bytes=' + Buffer.byteLength(s));
