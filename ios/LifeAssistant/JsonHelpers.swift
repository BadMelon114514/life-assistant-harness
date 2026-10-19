import Foundation

typealias JSONObject = [String: Any]

extension Dictionary where Key == String, Value == Any {
    func str(_ key: String) -> String {
        if let value = self[key] as? String { return value }
        if let value = self[key] as? NSNumber { return value.stringValue }
        return ""
    }

    func int(_ key: String) -> Int {
        if let value = self[key] as? NSNumber { return value.intValue }
        if let value = self[key] as? String, let parsed = Int(value) { return parsed }
        return 0
    }

    func double(_ key: String) -> Double {
        if let value = self[key] as? NSNumber { return value.doubleValue }
        if let value = self[key] as? String, let parsed = Double(value) { return parsed }
        return 0
    }

    func bool(_ key: String) -> Bool {
        if let value = self[key] as? Bool { return value }
        if let value = self[key] as? NSNumber { return value.boolValue }
        if let value = self[key] as? String { return value == "true" || value == "1" }
        return false
    }

    func dict(_ key: String) -> JSONObject { self[key] as? JSONObject ?? [:] }

    func arr(_ key: String) -> [JSONObject] { self[key] as? [JSONObject] ?? [] }

    func strArray(_ key: String) -> [String] {
        (self[key] as? [Any])?.compactMap { $0 as? String } ?? []
    }
}

/// 高危操作确认请求：文件类为 op|path|content，MCP 类为 mcp|endpoint|args。
struct ConfirmReq: Identifiable, Equatable {
    let id = UUID()
    var op: String
    var arg1: String
    var arg2: String

    static let fileOps = ["write_file", "delete_file", "run_script", "mkdir"]

    var isFileOp: Bool { ConfirmReq.fileOps.contains(op) }

    var title: String {
        switch op {
        case "write_file": return "写入 / 覆盖文件"
        case "delete_file": return "删除文件 / 目录"
        case "run_script": return "执行项目内脚本"
        case "mkdir": return "创建目录"
        default: return "执行 MCP 工具"
        }
    }

    var detail: String {
        if isFileOp {
            var text = "操作类型：" + title + "\n目标路径：" + (arg1.isEmpty ? "（项目根目录）" : arg1)
            if !arg2.isEmpty {
                text += "\n\n内容预览：\n" + String(arg2.prefix(300))
            }
            return text
        }
        return "目标：" + arg1 + "\n\n参数：\n" + String((arg2.isEmpty ? "{}" : arg2).prefix(300))
    }

    static func parse(_ payload: String) -> ConfirmReq {
        let parts = payload.components(separatedBy: "|")
        let op = parts.isEmpty ? "" : parts[0]
        let arg1 = parts.count > 1 ? parts[1] : ""
        let arg2 = parts.count > 2 ? parts[2...].joined(separator: "|") : ""
        return ConfirmReq(op: op, arg1: arg1, arg2: arg2)
    }
}

/// 助手生成的技能 / MCP 草稿。
struct DraftInfo: Identifiable {
    let id = UUID()
    var type: String // skill_draft / mcp_draft
    var payload: JSONObject

    var kindTitle: String { type == "skill_draft" ? "技能草稿" : "MCP 服务草稿" }
}

struct ChatMsg: Identifiable, Equatable {
    let id = UUID()
    var role: String
    var content: String

    var isUser: Bool { role == "user" }
}
