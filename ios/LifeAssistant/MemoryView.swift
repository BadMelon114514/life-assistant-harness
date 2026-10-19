import SwiftUI

struct MemoryView: View {
    @State private var items: [JSONObject] = []
    @State private var loading = true
    @State private var error: String?
    @State private var message: String?
    @State private var showAdd = false
    @State private var newContent = ""
    @State private var newCategory = "偏好"
    @State private var newImportance = 5
    @State private var deletingKey: String?
    @Environment(\.palette) private var p

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                if let error {
                    Text("⚠️ " + error).font(.system(size: 12)).foregroundColor(p.error)
                }
                if let message {
                    Text(message).font(.system(size: 12)).foregroundColor(p.primary)
                }
                if items.isEmpty {
                    EmptyState(text: loading ? "加载中…" : "还没有记忆。试试对助手说「帮我记住：…」")
                } else {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        MemoryCard(item: item, onDelete: { deletingKey = item.str("memoryKey") })
                    }
                }
            }
            .padding(14)
        }
        .background(p.background.ignoresSafeArea())
        .refreshable { await load() }
        .task { await load() }
        .sheet(isPresented: $showAdd) { addSheet }
        .alert("删除记忆", isPresented: Binding(
            get: { deletingKey != nil },
            set: { if !$0 { deletingKey = nil } }
        )) {
            Button("取消", role: .cancel) { deletingKey = nil }
            Button("删除", role: .destructive) {
                let key = deletingKey
                deletingKey = nil
                if let key { perform { try await Api.delete("/api/memory/" + key) } }
            }
        } message: {
            Text("删除后助手将不再记得这条信息。")
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("记忆库").font(.system(size: 20, weight: .semibold))
                Text("共 " + String(items.count) + " 条记忆").font(.system(size: 11)).foregroundColor(p.muted)
            }
            Spacer()
            if loading { ProgressView().scaleEffect(0.8) }
            Button { showAdd = true } label: {
                Image(systemName: "plus.circle.fill").font(.system(size: 22))
            }
            .foregroundColor(p.primary)
        }
    }

    private var addSheet: some View {
        NavigationStack {
            Form {
                Section("记忆内容") {
                    TextField("例如：我喜欢喝拿铁", text: $newContent, axis: .vertical)
                        .lineLimit(3...6)
                }
                Section("分类") {
                    TextField("例如：偏好", text: $newCategory)
                }
                Section("重要度 " + String(newImportance)) {
                    Stepper("重要度", value: $newImportance, in: 1...10)
                }
            }
            .navigationTitle("新增记忆")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showAdd = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let content = newContent.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !content.isEmpty else { return }
                        let category = newCategory
                        let importance = newImportance
                        showAdd = false
                        newContent = ""
                        perform {
                            _ = try await Api.post("/api/memory", [
                                "memoryKey": "ios_" + String(Int(Date().timeIntervalSince1970 * 1000), radix: 36),
                                "content": content,
                                "importance": importance,
                                "category": category
                            ])
                        }
                    }
                    .disabled(newContent.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func load() async {
        do {
            items = try await Api.getArray("/api/memory")
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func perform(_ action: @escaping () async throws -> Void) {
        Task { @MainActor in
            do {
                try await action()
                message = nil
            } catch {
                self.error = error.localizedDescription
            }
            await load()
        }
    }
}

private struct MemoryCard: View {
    let item: JSONObject
    let onDelete: () -> Void
    @Environment(\.palette) private var p

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(item.str("content"))
                    .font(.system(size: 13))
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    StatusPill(text: item.str("category").isEmpty ? "未分类" : item.str("category"), color: p.primary)
                    Text("重要度 " + String(item.int("importance")))
                    Spacer()
                    Text(formatTime(item.str("updatedAt")))
                    Button(role: .destructive, action: onDelete) {
                        Image(systemName: "trash").font(.system(size: 12))
                    }
                    .buttonStyle(.borderless)
                    .foregroundColor(p.muted)
                }
                .font(.system(size: 10))
                .foregroundColor(p.muted)
            }
        }
    }
}
