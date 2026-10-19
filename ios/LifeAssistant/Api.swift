import Foundation

struct ApiError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

enum ChatEvent {
    case token(String)
    case done(String)
    case confirm(ConfirmReq)
    case draft(String, JSONObject)
    case summary(String)
}

/// 后端 HTTP / SSE 客户端（与安卓端使用同一套接口契约）。
enum Api {
    static var baseUrl: String { Prefs.baseUrl }

    static func makeURL(_ path: String, query: [String: String]? = nil) throws -> URL {
        guard var components = URLComponents(string: baseUrl + path) else {
            throw ApiError(message: "服务器地址无效：" + baseUrl)
        }
        if let query {
            components.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components.url else { throw ApiError(message: "服务器地址无效") }
        return url
    }

    static func request(
        _ method: String,
        _ path: String,
        query: [String: String]? = nil,
        body: JSONObject? = nil,
        headers: [String: String] = [:],
        timeout: TimeInterval = 60
    ) async throws -> Data {
        let url = try makeURL(path, query: query)
        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.httpMethod = method
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ApiError(message: "服务器无响应")
        }
        guard (200..<300).contains(http.statusCode) else {
            let text = String(data: data, encoding: .utf8) ?? ""
            throw ApiError(message: "HTTP \(http.statusCode)：" + String(text.prefix(180)))
        }
        return data
    }

    static func getJSON(_ path: String, query: [String: String]? = nil) async throws -> Any {
        let data = try await request("GET", path, query: query)
        if data.isEmpty { return JSONObject() }
        return try JSONSerialization.jsonObject(with: data)
    }

    static func getArray(_ path: String, query: [String: String]? = nil) async throws -> [JSONObject] {
        (try await getJSON(path, query: query)) as? [JSONObject] ?? []
    }

    static func getObject(_ path: String, query: [String: String]? = nil) async throws -> JSONObject {
        (try await getJSON(path, query: query)) as? JSONObject ?? [:]
    }

    static func post(_ path: String, _ body: JSONObject = [:]) async throws -> JSONObject {
        let data = try await request("POST", path, body: body)
        if data.isEmpty { return [:] }
        return (try? JSONSerialization.jsonObject(with: data)) as? JSONObject ?? [:]
    }

    static func postWithHeaders(_ path: String, _ body: JSONObject, headers: [String: String]) async throws -> JSONObject {
        let data = try await request("POST", path, body: body, headers: headers)
        if data.isEmpty { return [:] }
        return (try? JSONSerialization.jsonObject(with: data)) as? JSONObject ?? [:]
    }

    static func delete(_ path: String, query: [String: String]? = nil) async throws {
        _ = try await request("DELETE", path, query: query)
    }

    static func testConnection() async throws -> String {
        let started = Date()
        _ = try await getJSON("/api/monitor/overview")
        let elapsed = Int(Date().timeIntervalSince(started) * 1000)
        return "连接成功（\(elapsed) ms）"
    }

    /// 流式对话：把 SSE 事件转换成 AsyncSequence。
    static func streamChat(message: String, session: String) -> AsyncThrowingStream<ChatEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let url = try makeURL("/api/assistant/chat-stream")
                    var request = URLRequest(url: url, timeoutInterval: 600)
                    request.httpMethod = "POST"
                    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                    request.httpBody = try JSONSerialization.data(withJSONObject: ["message": message, "session": session])

                    let (bytes, response) = try await URLSession.shared.bytes(for: request)
                    guard let http = response as? HTTPURLResponse else {
                        throw ApiError(message: "服务器无响应")
                    }
                    guard (200..<300).contains(http.statusCode) else {
                        throw ApiError(message: "HTTP \(http.statusCode)")
                    }

                    var eventName = ""
                    var dataLines: [String] = []

                    func dispatch() throws {
                        let payload = dataLines.joined(separator: "\n")
                        if eventName.isEmpty && payload.isEmpty { return }
                        let name = eventName
                        eventName = ""
                        dataLines = []
                        switch name {
                        case "token":
                            continuation.yield(.token(payload))
                        case "done":
                            continuation.yield(.done(payload))
                        case "confirm":
                            continuation.yield(.confirm(ConfirmReq.parse(payload)))
                        case "skill_draft", "mcp_draft":
                            let object = (try? JSONSerialization.jsonObject(with: Data(payload.utf8))) as? JSONObject ?? [:]
                            continuation.yield(.draft(name, object))
                        case "summary":
                            continuation.yield(.summary(payload))
                        case "error":
                            throw ApiError(message: payload.isEmpty ? "助手处理失败" : payload)
                        default:
                            break
                        }
                    }

                    for try await line in bytes.lines {
                        if line.hasPrefix("event:") {
                            eventName = String(line.dropFirst(6)).trimmingCharacters(in: .whitespaces)
                        } else if line.hasPrefix("data:") {
                            var value = String(line.dropFirst(5))
                            if value.hasPrefix(" ") { value.removeFirst() }
                            dataLines.append(value)
                        } else if line.isEmpty {
                            try dispatch()
                        }
                    }
                    try dispatch()
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
