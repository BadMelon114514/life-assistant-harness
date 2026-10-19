import SwiftUI

struct KnowledgeView: View {
    @State private var items: [JSONObject] = []
    @State private var keyword = ""
    @State private var loading = true
    @State private var error: String?
    @State private var selected: JSONObject?
    @State private var showAdd = false
    @State private var newFileName = ""
    @State private var newContent = ""
    @State private var newTags = ""
    @State private var deletingKey: String?
    @Environment(\.palette) private var p

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                searchBar
                if let error {
                    Text("⚠️ " + error).font(.system(size: 12)).foregroundColor(p.error)
                }
                if items.isEmpty {
                    EmptyState(text: loading ? "加载中…" : (keyword.isEmpty ? "知识库还没有文档" : "没有匹配的文档"))
                } else {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, doc in
                        KnowledgeCard(doc: doc, onOpen: { selected = doc }, onDelete: {
                            deletingKey = doc.str("docKey")
                        })
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
            if let doc = selected {
                KnowledgeDetail(doc: doc, onClose: { selected = nil }, onDelete: {
                    selected = nil
                    deletingKey = doc.str("docKey")
                })
            }
        }
        .alert("删除文档", isPresented: Binding(
            get: { deletingKey != nil },
            set: { if !$0 { deletingKey = nil } }
        )) {
            Button("取消", role: .cancel) { deletingKey = nil }
            Button("删除", role: .destructive) {
                let key = deletingKey
                deletingKey = nil
                if let key { perform { try await Api.delete("/api/knowledge/" + key) } }
            }
        } message: {
            Text("删除后不可恢复，确定继续？")
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("知识库").font(.system(size: 20, weight: .semibold))
                Text("共 " + String(items.count) + " 篇文档").font(.system(size: 11)).foregroundColor(p.muted)
            }
            Spacer()
            if loading { ProgressView().scaleEffect(0.8) }
            Button { showAdd = true } label: {
                Image(systemName: "plus.circle.fill").font(.system(size: 22))
            }
            .foregroundColor(p.primary)
        }
    }

    private var searchBar: some View {
        HStack(spacing: 8) {
            TextField("搜索标题、摘要或正文", text: $keyword)
                .font(.system(size: 13))
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(p.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(p.outline, lineWidth: 1)
                )
                .onSubmit { Task { await load() } }
            Button { Task { await load() } } label: {
                Image(systemName: "magnifyingglass").font(.system(size: 14))
            }
            .foregroundColor(p.onSurfaceVariant)
        }
    }

    private var addSheet: some View {
        NavigationStack {
            Form {
                Section("文件名") {
                    TextField("例如：2026 消费电子报告.md", text: $newFileName)
                }
                Section("标签") {
                    TextField("用逗号分隔，例如：报告,消费电子", text: $newTags)
                }
                Section("正文") {
                    TextField("粘贴或输入文档内容", text: $newContent, axis: .vertical)
                        .lineLimit(6...14)
                }
            }
            .navigationTitle("新增文档")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showAdd = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let fileName = newFileName.trimmingCharacters(in: .whitespacesAndNewlines)
                        let content = newContent
                        guard !fileName.isEmpty, !content.isEmpty else { return }
                        let tags = newTags
                        showAdd = false
                        newFileName = ""
                        newContent = ""
                        newTags = ""
                        perform {
                            _ = try await Api.post("/api/knowledge/save-text", [
                                "fileName": fileName,
                                "content": content,
                                "tags": tags
                            ])
                        }
                    }
                    .disabled(newFileName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || newContent.isEmpty)
                }
            }
        }
    }

    private func load() async {
        do {
            let query = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
            items = try await Api.getArray(
                "/api/knowledge",
                query: query.isEmpty ? nil : ["keyword": query]
            )
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
            } catch {
                self.error = error.localizedDescription
            }
            await load()
        }
    }
}

private struct KnowledgeCard: View {
    let doc: JSONObject
    let onOpen: () -> Void
    let onDelete: () -> Void
    @Environment(\.palette) private var p

    private var preview: String {
        let summary = doc.str("summary")
        if !summary.isEmpty { return summary }
        return String(doc.str("content").prefix(120))
    }

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top, spacing: 8) {
                    Text(doc.str("fileName").isEmpty ? "（未命名）" : doc.str("fileName"))
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    StatusPill(
                        text: doc.str("parseStatus").isEmpty ? "已入库" : doc.str("parseStatus"),
                        color: p.primary
                    )
                }
                Text(doc.str("docKey"))
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(p.muted)
                    .lineLimit(1)
                if !doc.str("tags").isEmpty {
                    Text(doc.str("tags"))
                        .font(.system(size: 11))
                        .foregroundColor(p.primary)
                        .lineLimit(1)
                }
                if !preview.isEmpty {
                    Text(preview)
                        .font(.system(size: 12))
                        .foregroundColor(p.onSurfaceVariant)
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                HStack {
                    Text(formatTime(doc.str("createdAt")))
                    Spacer()
                    Button("查看", action: onOpen).font(.system(size: 12))
                    Button(role: .destructive, action: onDelete) {
                        Image(systemName: "trash").font(.system(size: 12))
                    }
                    .foregroundColor(p.muted)
                }
                .font(.system(size: 10))
                .foregroundColor(p.muted)
                .buttonStyle(.borderless)
            }
        }
    }
}

private struct KnowledgeDetail: View {
    let doc: JSONObject
    let onClose: () -> Void
    let onDelete: () -> Void
    @Environment(\.palette) private var p

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(doc.str("fileName"))
                        .font(.system(size: 18, weight: .semibold))
                    Text(doc.str("docKey") + " · " + doc.str("fileType"))
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(p.muted)
                    if !doc.str("tags").isEmpty {
                        Text("标签：" + doc.str("tags"))
                            .font(.system(size: 12))
                            .foregroundColor(p.primary)
                    }
                    if !doc.str("summary").isEmpty {
                        SectionTitle(text: "摘要")
                        Text(doc.str("summary"))
                            .font(.system(size: 13))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    SectionTitle(text: "正文")
                    Text(doc.str("content"))
                        .font(.system(size: 13))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(p.background.ignoresSafeArea())
            .navigationTitle("文档详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭", action: onClose)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(role: .destructive, action: onDelete) { Text("删除") }
                }
            }
        }
    }
}
