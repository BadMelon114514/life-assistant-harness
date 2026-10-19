import SwiftUI

struct SkillsView: View {
    @State private var items: [JSONObject] = []
    @State private var loading = true
    @State private var error: String?
    @State private var selected: JSONObject?
    @State private var showAdd = false
    @State private var newName = ""
    @State private var newCategory = "通用"
    @State private var newDescription = ""
    @State private var newContent = ""
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
                if items.isEmpty {
                    EmptyState(text: loading ? "加载中…" : "还没有技能")
                } else {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, skill in
                        SkillCard(
                            skill: skill,
                            busy: busy,
                            onOpen: { selected = skill },
                            onToggle: { perform { _ = try await Api.post("/api/skills/" + String(skill.int("id")) + "/toggle") } },
                            onDelete: {
                                deletingId = skill.int("id")
                                deletingName = skill.str("name")
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
        .sheet(isPresented: Binding(get: { selected != nil }, set: { if !$0 { selected = nil } })) {
            if let skill = selected {
                SkillDetail(skill: skill, onClose: { selected = nil })
            }
        }
        .alert("删除技能", isPresented: Binding(
            get: { deletingId != nil },
            set: { if !$0 { deletingId = nil } }
        )) {
            Button("取消", role: .cancel) { deletingId = nil }
            Button("删除", role: .destructive) {
                let id = deletingId
                deletingId = nil
                if let id { perform { try await Api.delete("/api/skills/" + String(id)) } }
            }
        } message: {
            Text("确定删除「" + deletingName + "」？")
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("技能中心").font(.system(size: 20, weight: .semibold))
                Text("共 " + String(items.count) + " 个技能").font(.system(size: 11)).foregroundColor(p.muted)
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
                    TextField("例如：周报助手", text: $newName)
                }
                Section("分类") {
                    TextField("例如：写作", text: $newCategory)
                }
                Section("描述") {
                    TextField("一句话说明技能用途", text: $newDescription)
                }
                Section("技能内容（指令）") {
                    TextField("写下技能的具体要求", text: $newContent, axis: .vertical)
                        .lineLimit(5...12)
                }
            }
            .navigationTitle("新增技能")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showAdd = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !name.isEmpty else { return }
                        let category = newCategory
                        let description = newDescription
                        let content = newContent
                        showAdd = false
                        newName = ""
                        newDescription = ""
                        newContent = ""
                        perform {
                            _ = try await Api.post("/api/skills", [
                                "name": name,
                                "category": category,
                                "description": description,
                                "content": content,
                                "enabled": true
                            ])
                        }
                    }
                    .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func load() async {
        do {
            items = try await Api.getArray("/api/skills")
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
}

private struct SkillCard: View {
    let skill: JSONObject
    let busy: Bool
    let onOpen: () -> Void
    let onToggle: () -> Void
    let onDelete: () -> Void
    @Environment(\.palette) private var p

    private var enabled: Bool { skill.bool("enabled") }

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .center, spacing: 8) {
                    Text(skill.str("name").isEmpty ? "（未命名）" : skill.str("name"))
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                    StatusPill(text: enabled ? "已启用" : "已停用", color: enabled ? p.success : p.muted)
                    Spacer(minLength: 0)
                }
                Text(skill.str("category").isEmpty ? "未分类" : skill.str("category"))
                    .font(.system(size: 10))
                    .foregroundColor(p.primary)
                if !skill.str("description").isEmpty {
                    Text(skill.str("description"))
                        .font(.system(size: 12))
                        .foregroundColor(p.onSurfaceVariant)
                        .lineLimit(2)
                }
                HStack(spacing: 10) {
                    Button("详情", action: onOpen)
                    Button(enabled ? "停用" : "启用", action: onToggle)
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

private struct SkillDetail: View {
    let skill: JSONObject
    let onClose: () -> Void
    @Environment(\.palette) private var p

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(skill.str("name"))
                        .font(.system(size: 18, weight: .semibold))
                    Text(skill.str("category") + " · " + (skill.bool("enabled") ? "已启用" : "已停用"))
                        .font(.system(size: 11))
                        .foregroundColor(p.muted)
                    if !skill.str("description").isEmpty {
                        SectionTitle(text: "描述")
                        Text(skill.str("description"))
                            .font(.system(size: 13))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    SectionTitle(text: "技能内容")
                    MarkdownText(text: skill.str("content"))
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(p.background.ignoresSafeArea())
            .navigationTitle("技能详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭", action: onClose)
                }
            }
        }
    }
}
