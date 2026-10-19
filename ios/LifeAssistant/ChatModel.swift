import Foundation

@MainActor
final class ChatModel: ObservableObject {
    @Published var messages: [ChatMsg] = []
    @Published var streaming = false
    @Published var error: String?
    @Published var notice: String?
    @Published var pendingConfirm: ConfirmReq?
    @Published var draft: DraftInfo?
    @Published var session: String

    private var sendTask: Task<Void, Never>?
    private var stopping = false

    init() {
        session = Prefs.session
    }

    func refreshHistory() async {
        do {
            let rows = try await Api.getArray("/api/chat/history", query: ["session": session])
            messages = rows
                .filter { $0.str("role") == "user" || $0.str("role") == "assistant" }
                .map { ChatMsg(role: $0.str("role"), content: $0.str("content")) }
            error = nil
        } catch {
            self.error = "加载历史失败：" + error.localizedDescription
        }
    }

    func send(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !streaming else { return }
        error = nil
        notice = nil
        messages.append(ChatMsg(role: "user", content: text))
        messages.append(ChatMsg(role: "assistant", content: ""))
        let index = messages.count - 1
        streaming = true
        stopping = false
        let activeSession = session
        Task { _ = try? await Api.post("/api/chat/history", ["role": "user", "content": text, "session": activeSession]) }
        sendTask = Task { [weak self] in
            guard let self else { return }
            await self.runStream(index: index, text: text)
        }
    }

    func stop() {
        guard streaming else { return }
        stopping = true
        sendTask?.cancel()
        let activeSession = session
        Task { _ = try? await Api.post("/api/assistant/stop", ["session": activeSession]) }
    }

    func resolveConfirm(_ execute: Bool) {
        guard let request = pendingConfirm else { return }
        pendingConfirm = nil
        guard execute else {
            appendStatus("❌ 已取消该操作")
            return
        }
        var body: JSONObject
        if request.isFileOp {
            body = ["op": request.op, "path": request.arg1, "content": request.arg2]
        } else {
            body = ["endpoint": request.arg1, "args": request.arg2.isEmpty ? "{}" : request.arg2]
        }
        Task {
            do {
                let response = try await Api.post("/api/assistant/confirm", body)
                if response.bool("success") {
                    appendStatus("✅ 操作已执行：" + String(response.str("result").prefix(300)))
                } else {
                    let reason = response.str("error")
                    appendStatus("❌ 操作执行失败：" + (reason.isEmpty ? "未知错误" : reason))
                }
            } catch {
                appendStatus("❌ 操作执行失败：" + error.localizedDescription)
            }
        }
    }

    func importDraft() {
        guard let info = draft else { return }
        draft = nil
        let path = info.type == "skill_draft" ? "/api/skills/import-draft" : "/api/mcp/import-draft"
        Task {
            do {
                let response = try await Api.post(path, info.payload)
                let name = response.str("name").isEmpty ? info.payload.str("name") : response.str("name")
                notice = "✅ 已导入" + (info.type == "skill_draft" ? "技能" : "MCP 服务") + "「" + name + "」"
            } catch {
                notice = "导入失败：" + error.localizedDescription
            }
        }
    }

    func newSession() {
        sendTask?.cancel()
        sendTask = nil
        streaming = false
        stopping = false
        session = Prefs.newSession()
        messages = []
        pendingConfirm = nil
        draft = nil
        notice = nil
        error = nil
    }

    func clearHistory() {
        Task {
            do {
                try await Api.delete("/api/chat/history", query: ["session": session])
                messages = []
                notice = "已清空当前会话历史"
            } catch {
                self.error = "清空失败：" + error.localizedDescription
            }
        }
    }

    private func runStream(index: Int, text: String) async {
        var buffer = ""
        var doneText: String?
        var cancelled = false
        do {
            for try await event in Api.streamChat(message: text, session: session) {
                switch event {
                case .token(let chunk):
                    buffer += chunk
                    if messages.indices.contains(index) {
                        messages[index].content = buffer
                    }
                case .done(let result):
                    doneText = result
                case .confirm(let request):
                    pendingConfirm = request
                case .draft(let type, let payload):
                    draft = DraftInfo(type: type, payload: payload)
                case .summary(let summary):
                    notice = summary
                }
            }
        } catch is CancellationError {
            cancelled = true
        } catch {
            if !buffer.isEmpty {
                if messages.indices.contains(index) {
                    messages[index].content = buffer + "\n\n⚠️ 连接中断：" + error.localizedDescription
                }
                await persist(role: "assistant", content: buffer)
            } else if messages.indices.contains(index) {
                messages[index].content = "⚠️ 请求失败：" + error.localizedDescription
            }
            streaming = false
            sendTask = nil
            return
        }
        await finish(index: index, buffer: buffer, doneText: doneText, cancelled: cancelled || stopping)
    }

    private func finish(index: Int, buffer: String, doneText: String?, cancelled: Bool) async {
        var content = (doneText ?? buffer).trimmingCharacters(in: .whitespacesAndNewlines)
        if cancelled {
            content += (content.isEmpty ? "" : "\n\n") + "_（已终止）_"
        }
        content = await processSchedules(content)
        if content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            content = "（无回复内容）"
        }
        if messages.indices.contains(index) {
            messages[index].content = content
        }
        streaming = false
        stopping = false
        sendTask = nil
        await persist(role: "assistant", content: content)
    }

    private func processSchedules(_ text: String) async -> String {
        let pattern = "\\[\\[SCHEDULE:([^\\]]*)\\]\\]"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return text }
        let range = NSRange(location: 0, length: (text as NSString).length)
        let matches = regex.matches(in: text, range: range)
        for match in matches {
            let body = (text as NSString).substring(with: match.range(at: 1))
            var instruct = ""
            var delayMinutes = 0
            var title = ""
            for part in body.components(separatedBy: ";") {
                let pieces = part.components(separatedBy: "=")
                guard pieces.count >= 2 else { continue }
                let key = pieces[0].trimmingCharacters(in: .whitespaces)
                let value = pieces[1...].joined(separator: "=").trimmingCharacters(in: .whitespaces)
                switch key {
                case "instruct": instruct = value
                case "delayMinutes": delayMinutes = Int(value) ?? 0
                case "title": title = value
                default: break
                }
            }
            if !instruct.isEmpty {
                _ = try? await Api.post("/api/assistant/schedule", [
                    "instruct": instruct,
                    "delayMinutes": delayMinutes,
                    "title": title.isEmpty ? "⏰ 定时提醒" : title,
                    "session": session
                ])
            }
        }
        return regex.stringByReplacingMatches(in: text, range: range, withTemplate: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func persist(role: String, content: String) async {
        _ = try? await Api.post("/api/chat/history", ["role": role, "content": content, "session": session])
    }

    private func appendStatus(_ text: String) {
        if let last = messages.last, !last.isUser {
            messages[messages.count - 1].content += "\n\n" + text
        } else {
            messages.append(ChatMsg(role: "assistant", content: text))
        }
    }
}
