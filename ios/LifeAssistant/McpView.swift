import SwiftUI

struct McpView: View {
    @State private var items: [JSONObject] = []
    @State private var loading = true
    @State private var error: String?
    @State private var message: String?
    @State private var showAdd = false
    @State private var newName = ""
    @State private var newEndpoint = ""
    @State private var newDescription = ""
    @State private var deletingId: Int?
    @State private var deletingName = ""
    @State private var busy = false
    @Environment(\.palette) private var p

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                if let error {
                    Text("⚠️ " + error).font(.system(size: 12)).foregroundColor(p.error)
                }
                if let message {
                    Text(message)
                        .font(.system(size: 12))
                        .foregroundColor(message.hasPrefix("❌") ? p.error : p.success)
                }
                if items.isEmpty {
                    EmptyState(text: loading ? "加载中…" : "还没有 MCP 服务")
                } else {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, server in
                        McpCard(
                            server: server,
                            busy: busy,
                            onToggle: { perform { _ = try await Api.post("/api/mcp/" + String(server.int("id")) + "/toggle") } },
                            onTest: { testServer(server) },
                            onDelete: {
                                deletingId = server.int("id")
                                deletingName = server.str("name")
                            }
                        )
                    }
                }
            }
            .padding(14)
        }
        .background(p.background.ignoresSafeArea())
        .refreshable { await load() }
        .task { await load() }
        .sheet(isPresented: $showAdd) { addSheet }
        .alert("删除 MCP 服务", isPresented: Binding(
            get: { deletingId != nil },
            set: { if !$0 { deletingId = nil } }
        )) {
            Button("取消", role: .cancel) { deletingId = nil }
            Button("删除", role: .destructive) {
                let id = deletingId
                deletingId = nil
                if let id { perform { try await Api.delete("/api/mcp/" + String(id)) } }
            }
        } message: {
            Text("确定删除「" + deletingName + "」？")
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("MCP 服务").font(.system(size: 20, weight: .semibold))
                Text("共 " + String(items.count) + " 个服务").font(.system(size: 11)).foregroundColor(p.muted)
            }
            Spacer()
            if busy || loading { ProgressView().scaleEffect(0.8) }
            Button { showAdd = true } label: {
                Image(systemName: "plus.circle.fill").font(.system(size: 22))
            }
            .foregroundColor(p.primary)
        }
    }

    private var addSheet: some View {
        NavigationStack {
            Form {
                Section("名称") {
                    TextField("例如：我的天气服务", text: $newName)
                }
                Section("HTTP 端点") {
                    TextField("http://127.0.0.1:8080/api/...", text: $newEndpoint)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
                Section("描述") {
                    TextField("这个服务提供什么能力", text: $newDescription, axis: .vertical)
                        .lineLimit(2...4)
                }
            }
            .navigationTitle("新增 MCP 服务")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showAdd = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                        let endpoint = newEndpoint.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !name.isEmpty, !endpoint.isEmpty else { return }
                        let description = newDescription
                        showAdd = false
                        newName = ""
                        newEndpoint = ""
                        newDescription = ""
                        perform {
                            _ = try await Api.post("/api/mcp", [
                                "name": name,
                                "serverType": "http",
                                "endpoint": endpoint,
                                "description": description,
                                "config": "{}"
                            ])
                        }
                    }
                    .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || newEndpoint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func load() async {
        do {
            items = try await Api.getArray("/api/mcp")
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func testServer(_ server: JSONObject) {
        busy = true
        Task { @MainActor in
            do {
                let response = try await Api.post("/api/mcp/" + String(server.int("id")) + "/test")
                let ok = response.bool("success")
                let detail = response.str("message")
                message = (ok ? "✅ " : "❌ ") + server.str("name") + "：" + (detail.isEmpty ? (ok ? "连接成功" : "连接失败") : detail)
            } catch {
                self.error = error.localizedDescription
            }
            busy = false
            await load()
        }
    }

    private func perform(_ action: @escaping () async throws -> Void) {
        Task { @MainActor in
            busy = true
            do {
                try await action()
                message = nil
            } catch {
                self.error = error.localizedDescription
            }
            busy = false
            await load()
        }
    }
}

private struct McpCard: View {
    let server: JSONObject
    let busy: Bool
    let onToggle: () -> Void
    let onTest: () -> Void
    let onDelete: () -> Void
    @Environment(\.palette) private var p

    private var active: Bool { server.str("status") == "active" }

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 7) {
                HStack(alignment: .center, spacing: 8) {
                    Text(server.str("name").isEmpty ? "（未命名）" : server.str("name"))
                        .font(.system(size: 14, weight: .semibold))
                    StatusPill(text: active ? "已启用" : "已停用", color: active ? p.success : p.muted)
                    Spacer(minLength: 0)
                }
                Text(server.str("serverType") + " · " + server.str("endpoint"))
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(p.muted)
                    .lineLimit(2)
                if !server.str("description").isEmpty {
                    Text(server.str("description"))
                        .font(.system(size: 12))
                        .foregroundColor(p.onSurfaceVariant)
                        .lineLimit(2)
                }
                HStack(spacing: 10) {
                    Button(active ? "停用" : "启用", action: onToggle)
                    Button("测试", action: onTest)
                    Spacer(minLength: 0)
                    Button(role: .destructive, action: onDelete) {
                        Image(systemName: "trash").font(.system(size: 12))
                    }
                    .foregroundColor(p.muted)
                }
                .font(.system(size: 12))
                .buttonStyle(.borderless)
                .disabled(busy)
            }
        }
    }
}
