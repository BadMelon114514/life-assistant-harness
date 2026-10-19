# -*- coding: utf-8 -*-
"""iOS 工程静态校验：文件完整性、Info.plist、pbxproj 结构、Swift 括号平衡、Scheme、资产。

本机无 Swift/Xcode 工具链，只能做静态检查；真机/模拟器编译需在 macOS + Xcode 完成。
"""
import json
import os
import plistlib
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
IOS = os.path.join(ROOT, "ios")
SRC = os.path.join(IOS, "LifeAssistant")
PBX = os.path.join(IOS, "LifeAssistant.xcodeproj", "project.pbxproj")
SCHEME = os.path.join(IOS, "LifeAssistant.xcodeproj", "xcshareddata", "xcschemes", "LifeAssistant.xcscheme")

SWIFT_FILES = [
    "App.swift", "Api.swift", "ChatModel.swift", "ChatView.swift", "Common.swift",
    "JsonHelpers.swift", "KnowledgeView.swift", "McpView.swift", "MemoryView.swift",
    "MonitorSocket.swift", "MonitorView.swift", "MoreView.swift", "Prefs.swift",
    "SettingsView.swift", "SkillsView.swift", "TasksView.swift", "Theme.swift",
]

results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(("PASS " if ok else "FAIL ") + name + ((" | " + str(detail)) if detail else ""))


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


# 1. 文件完整性
for name in SWIFT_FILES:
    path = os.path.join(SRC, name)
    check("存在 " + name, os.path.isfile(path) and os.path.getsize(path) > 100, os.path.getsize(path) if os.path.isfile(path) else 0)
check("存在 Info.plist", os.path.isfile(os.path.join(SRC, "Info.plist")))
check("存在 Assets.xcassets", os.path.isdir(os.path.join(SRC, "Assets.xcassets")))
check("存在 project.pbxproj", os.path.isfile(PBX))
check("存在 shared scheme", os.path.isfile(SCHEME))

# 2. Info.plist
try:
    with open(os.path.join(SRC, "Info.plist"), "rb") as fh:
        info = plistlib.load(fh)
    check("Info.plist 可解析", True)
    check("显示名 生活助理", info.get("CFBundleDisplayName") == "生活助理", info.get("CFBundleDisplayName"))
    check("允许本地 http", info.get("NSAppTransportSecurity", {}).get("NSAllowsArbitraryLoads") is True)
    check("UILaunchScreen 存在", "UILaunchScreen" in info)
    check("BundleId 变量", info.get("CFBundleIdentifier") == "$(PRODUCT_BUNDLE_IDENTIFIER)")
except Exception as exc:  # noqa: BLE001
    check("Info.plist 可解析", False, exc)

# 3. pbxproj 文本结构与引用
pbx = read(PBX)
check("pbxproj 大括号平衡", pbx.count("{") == pbx.count("}"), "%s/%s" % (pbx.count("{"), pbx.count("}")))
check("pbxproj 小括号平衡", pbx.count("(") == pbx.count(")"), "%s/%s" % (pbx.count("("), pbx.count(")")))
defined = set(re.findall(r"^\t\t([0-9A-F]{24})\s.*?= \{", pbx, re.M))
referenced = set(re.findall(r"[0-9A-F]{24}", pbx))
undefined = referenced - defined
check("pbxproj 引用 id 均有定义", not undefined, sorted(undefined)[:5])
check("objectVersion 56", "objectVersion = 56;" in pbx)
check("rootObject 存在", re.search(r"rootObject = [0-9A-F]{24}", pbx) is not None)
for name in SWIFT_FILES:
    check("pbxproj 引用 " + name, ("path = " + name + ";") in pbx and (name + " in Sources") in pbx)
check("pbxproj Sources 数 17", pbx.count("in Sources */,") == 17, pbx.count("in Sources */,"))
check("pbxproj Resources 含资产", "Assets.xcassets in Resources" in pbx)
check("bundle id 正确", "PRODUCT_BUNDLE_IDENTIFIER = com.badmelon.farming.ios;" in pbx)
check("部署目标 16.0", pbx.count("IPHONEOS_DEPLOYMENT_TARGET = 16.0;") >= 2)
check("Info.plist 路径", "INFOPLIST_FILE = LifeAssistant/Info.plist;" in pbx)
disk_swift = sorted(n for n in os.listdir(SRC) if n.endswith(".swift"))
check("pbxproj 与磁盘 swift 文件一致", disk_swift == sorted(SWIFT_FILES), list(set(disk_swift) ^ set(SWIFT_FILES)))

# 4. Scheme
try:
    tree = ET.parse(SCHEME)
    text = read(SCHEME)
    target_id = re.search(r'BlueprintIdentifier = "([0-9A-F]{24})"', text).group(1)
    check("Scheme 可解析", True)
    check("Scheme target 在 pbxproj 中", target_id in defined, target_id)
    check("Scheme 构建 LifeAssistant.app", 'BuildableName = "LifeAssistant.app"' in text)
except Exception as exc:  # noqa: BLE001
    check("Scheme 可解析", False, exc)

# 5. Swift 括号/引号平衡（去掉双引号字符串内容后统计）
for name in SWIFT_FILES:
    text = read(os.path.join(SRC, name))
    stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', text, flags=re.S)
    ok = (stripped.count("{") == stripped.count("}") and
          stripped.count("(") == stripped.count(")") and
          stripped.count("[") == stripped.count("]"))
    check("Swift 括号平衡 " + name, ok,
          "braces={}/{} parens={}/{} brackets={}/{}".format(
              stripped.count("{"), stripped.count("}"), stripped.count("("), stripped.count(")"),
              stripped.count("["), stripped.count("]")))
    check("Swift 导入 " + name, text.startswith("import ") or text.startswith("@main"), text.splitlines()[0][:40])

# 6. 跨文件符号：Api/Common 工具确实存在
api_text = read(os.path.join(SRC, "Api.swift"))
common_text = read(os.path.join(SRC, "Common.swift"))
for symbol in ["static func getArray", "static func getObject", "static func post(", "static func postWithHeaders", "static func delete", "static func testConnection", "static func streamChat"]:
    check("Api 提供 " + symbol, symbol in api_text)
for symbol in ["struct AppCard", "struct StatusPill", "struct EmptyState", "struct SectionTitle", "func formatTime", "func statusLabel", "func statusColor", "struct MarkdownText", "struct ToastBanner"]:
    check("Common 提供 " + symbol, symbol in common_text)

# 7. 视图与模型符号闭合检查
all_text = "\n".join(read(os.path.join(SRC, n)) for n in SWIFT_FILES)
for symbol in ["struct ChatView", "struct MonitorView", "struct TasksView", "struct KnowledgeView", "struct MemoryView",
               "struct McpView", "struct SkillsView", "struct SettingsView", "struct MoreView", "struct AboutView",
               "struct OfflineView", "struct RootView", "struct MainTabView", "final class SocketHub",
               "final class ChatModel", "final class MonitorSocket", "struct ThemedRoot", "enum Api", "enum Prefs"]:
    check("符号存在 " + symbol, symbol in all_text)
for ref in ["/api/assistant/chat-stream", "/api/assistant/confirm", "/api/monitor/overview", "/api/tasks",
            "/api/knowledge/save-text", "/api/memory", "/api/mcp", "/api/skills", "/api/system/shutdown", "/ws/monitor"]:
    check("接口引用 " + ref, ref in all_text)

# 8. Assets 与图标
try:
    for asset in ["Contents.json", "AppIcon.appiconset/Contents.json", "AccentColor.colorset/Contents.json"]:
        with open(os.path.join(SRC, "Assets.xcassets", asset), "r", encoding="utf-8") as fh:
            json.load(fh)
    check("Assets JSON 可解析", True)
    icon = os.path.join(SRC, "Assets.xcassets", "AppIcon.appiconset", "AppIcon.png")
    check("AppIcon.png 存在", os.path.isfile(icon), os.path.getsize(icon) if os.path.isfile(icon) else 0)
    try:
        from PIL import Image
        with Image.open(icon) as img:
            check("AppIcon 尺寸 1024", img.size == (1024, 1024), img.size)
    except ImportError:
        pass
except Exception as exc:  # noqa: BLE001
    check("Assets JSON 可解析", False, exc)

# 9. Swift 语法树解析（可选，需 tree_sitter + tree_sitter_swift）
try:
    from tree_sitter import Language, Parser
    import tree_sitter_swift
    parser = Parser(Language(tree_sitter_swift.language()))
    broken = []
    for name in SWIFT_FILES:
        with open(os.path.join(SRC, name), "rb") as fh:
            source = fh.read()
        tree = parser.parse(source)
        stack = [tree.root_node]
        errors = []
        while stack:
            node = stack.pop()
            if node.type == "ERROR" or node.is_missing:
                errors.append(node)
            stack.extend(node.children)
        if errors:
            broken.append(name)
    check("Swift 语法树无错误节点", not broken, broken)
except ImportError:
    print("SKIP tree_sitter 未安装（可选）")

# 10. 可选：pbxproj 第三方解析
try:
    from pbxproj import XcodeProject
    project = XcodeProject.load(PBX)
    targets = project.objects.get_targets()
    names = [t.name for t in targets]
    check("pbxproj 第三方解析", "LifeAssistant" in names, names)
    check("pbxproj target 类型 application",
          any(t.productType == "com.apple.product-type.application" for t in targets))
    phases = [project.objects[key].isa for key in targets[0].buildPhases]
    check("pbxproj 构建阶段含 Sources", "PBXSourcesBuildPhase" in phases, phases)
except ImportError:
    print("SKIP pbxproj 第三方库未安装")
except Exception as exc:  # noqa: BLE001
    check("pbxproj 第三方解析", False, exc)

failed = [r for r in results if not r[1]]
report = "\n".join(("PASS " if ok else "FAIL ") + name + ((" | " + str(detail)) if detail else "") for name, ok, detail in results)
report += "\nTOTAL %d  PASS %d  FAIL %d\n" % (len(results), len(results) - len(failed), len(failed))
with open(os.path.join(ROOT, "logs", "ios_verify_report.txt"), "w", encoding="utf-8") as fh:
    fh.write(report)
print("TOTAL %d  PASS %d  FAIL %d" % (len(results), len(results) - len(failed), len(failed)))
for name, ok, detail in failed:
    print("FAIL " + name + ((" | " + str(detail)) if detail else ""))
sys.exit(1 if failed else 0)
