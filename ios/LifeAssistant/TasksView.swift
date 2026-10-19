import SwiftUI

struct TasksView: View {
    @State private var items: [JSONObject] = []
    @State private var filter = "all"
    @State private var loading = true
    @State private var error: String?
    @State private var busy = false
    @State private var showAdd = false
    @State private var newTitle = ""
    @State private var newDescription = ""
    @State private var deletingKey: String?
    @State private var deletingTitle = ""
    @Environment(\.palette) private var p

    private let filters: [(String, String)] = [
        ("all", "全部"), ("pending", "待处理"), ("in_progress", "进行中"), ("completed", "已完成")
    ]

    private var filtered: [JSONObject] {
        filter == "all" ? items : items.filter { $0.str("status") == filter }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                if let error {
                    Text("⚠️ " + error).font(.system(size: 12)).foregroundColor(p.error)
                }
                filterBar
                if filtered.isEmpty {
                    EmptyState(text: loading ? "加载中…" : "没有符合条件的任务")
                } else {
                    ForEach(Array(filtered.enumerated()), id: \.offset) { _, task in
                        TaskCard(
                            task: task,
                            busy: busy,
                            onClaim: { perform { try await claim(task) } },
                            onProgress: { perform { try await bumpProgress(task) } },
                            onComplete: { perform { try await complete(task) } },
                            onDelete: {
                                deletingKey = task.str("taskKey")
                                deletingTitle = task.str("title")
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
        .alert("删除任务", isPresented: Binding(
            get: { deletingKey != nil },
            set: { if !$0 { deletingKey = nil } }
        )) {
            Button("取消", role: .cancel) { deletingKey = nil }
            Button("删除", role: .destructive) {
                let key = deletingKey
                deletingKey = nil
                if let key { perform { try await Api.delete("/api/tasks/" + key) } }
            }
        } message: {
            Text("确定删除「" + deletingTitle + "」？")
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("任务看板").font(.system(size: 20, weight: .semibold))
                Text("共 " + String(items.count) + " 个任务").font(.system(size: 11)).foregroundColor(p.muted)
            }
            Spacer()
            if busy { ProgressView().scaleEffect(0.8) }
            Button { showAdd = true } label: {
                Image(systemName: "plus.circle.fill").font(.system(size: 22))
            }
            .foregroundColor(p.primary)
        }
    }

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filters, id: \.0) { key, label in
                    Button { filter = key } label: {
                        Text(label)
                            .font(.system(size: 12))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(filter == key ? p.primaryContainer : p.surface)
                            .foregroundColor(filter == key ? p.primary : p.onSurfaceVariant)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(p.outline, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var addSheet: some View {
        NavigationStack {
            Form {
                Section("任务标题") {
                    TextField("例如：整理本周会议纪要", text: $newTitle)
                }
                Section("任务描述") {
                    TextField("补充背景、目标与验收标准", text: $newDescription, axis: .vertical)
                        .lineLimit(3...6)
                }
            }
            .navigationTitle("新建任务")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showAdd = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建") {
                        let title = newTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !title.isEmpty else { return }
                        let description = newDescription
                        showAdd = false
                        newTitle = ""
                        newDescription = ""
                        perform {
                            _ = try await Api.post("/api/tasks", [
                                "title": title,
                                "description": description,
                                "blockedBy": [String]()
                            ])
                        }
                    }
                    .disabled(newTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func load() async {
        do {
            items = try await Api.getArray("/api/tasks")
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func perform(_ action: @escaping () async throws -> Void) {
        Task { @MainActor in
            busy = true
            do {
                try await action()
            } catch {
                self.error = error.localizedDescription
            }
            busy = false
            await load()
        }
    }

    private func claim(_ task: JSONObject) async throws {
        _ = try await Api.post("/api/tasks/" + task.str("taskKey") + "/claim", ["owner": "iOS 助手"])
    }

    private func bumpProgress(_ task: JSONObject) async throws {
        let next = min(100, task.int("progress") + 10)
        _ = try await Api.post("/api/tasks/" + task.str("taskKey") + "/progress", [
            "progress": next,
            "status": "in_progress"
        ])
    }

    private func complete(_ task: JSONObject) async throws {
        _ = try await Api.post("/api/tasks/" + task.str("taskKey") + "/complete", ["result": "由 iOS 端标记完成"])
    }
}

private struct TaskCard: View {
    let task: JSONObject
    let busy: Bool
    let onClaim: () -> Void
    let onProgress: () -> Void
    let onComplete: () -> Void
    let onDelete: () -> Void
    @Environment(\.palette) private var p

    private var status: String { task.str("status") }

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 7) {
                HStack(alignment: .top, spacing: 8) {
                    Text(task.str("title").isEmpty ? "（无标题）" : task.str("title"))
                        .font(.system(size: 14, weight: .semibold))
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                    StatusPill(text: statusLabel(status), color: statusColor(status, p))
                }
                if !task.str("description").isEmpty {
                    Text(task.str("description"))
                        .font(.system(size: 12))
                        .foregroundColor(p.onSurfaceVariant)
                        .lineLimit(3)
                }
                if status == "in_progress" {
                    ProgressView(value: task.double("progress"), total: 100)
                        .tint(p.warning)
                }
                HStack(spacing: 8) {
                    Text("负责人：" + (task.str("owner").isEmpty ? "未认领" : task.str("owner")))
                    Spacer()
                    Text(formatTime(task.str("updatedAt")))
                }
                .font(.system(size: 10))
                .foregroundColor(p.muted)
                if !task.str("result").isEmpty {
                    Text("结果：" + task.str("result"))
                        .font(.system(size: 11))
                        .foregroundColor(p.onSurfaceVariant)
                        .lineLimit(2)
                }
                HStack(spacing: 6) {
                    if status == "pending" {
                        Button("认领", action: onClaim)
                    }
                    if status == "in_progress" {
                        Button("+10%", action: onProgress)
                        Button("完成", action: onComplete)
                    }
                    Spacer(minLength: 0)
                    Button(role: .destructive, action: onDelete) {
                        Image(systemName: "trash").font(.system(size: 13))
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
