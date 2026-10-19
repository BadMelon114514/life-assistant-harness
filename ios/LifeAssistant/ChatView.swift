import SwiftUI

struct ChatView: View {
    @StateObject private var model = ChatModel()
    @State private var input = ""
    @Environment(\.palette) private var p
    @FocusState private var inputFocused: Bool

    private let suggestions = [
        "现在几点了？我在哪个城市？",
        "帮我记住：我喜欢喝拿铁",
        "今天有什么热点？",
        "整理一下本周的任务"
    ]

    var body: some View {
        VStack(spacing: 0) {
            header
            if let error = model.error {
                banner(error, color: p.error)
            }
            if let notice = model.notice {
                banner(notice, color: p.primary)
            }
            messageList
            inputBar
        }
        .background(p.background.ignoresSafeArea())
        .task { await model.refreshHistory() }
        .alert(
            "需要确认",
            isPresented: Binding(
                get: { model.pendingConfirm != nil },
                set: { newValue in
                    if !newValue { model.resolveConfirm(false) }
                }
            ),
            presenting: model.pendingConfirm
        ) { request in
            Button("取消", role: .cancel) { model.resolveConfirm(false) }
            Button("确认执行", role: .destructive) { model.resolveConfirm(true) }
        } message: { request in
            Text(request.title + "\n\n" + request.detail)
        }
        .sheet(item: $model.draft) { info in
            DraftSheet(info: info, onImport: { model.importDraft() }, onClose: { model.draft = nil })
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("生活助理")
                    .font(.system(size: 18, weight: .semibold))
                Text("会话 " + model.session)
                    .font(.system(size: 10))
                    .foregroundColor(p.muted)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Spacer()
            Button { Task { await model.refreshHistory() } } label: {
                Image(systemName: "arrow.clockwise").font(.system(size: 14))
            }
            .foregroundColor(p.onSurfaceVariant)
            Button { model.newSession() } label: {
                Image(systemName: "square.and.pencil").font(.system(size: 14))
            }
            .foregroundColor(p.onSurfaceVariant)
            Button { model.clearHistory() } label: {
                Image(systemName: "trash").font(.system(size: 14))
            }
            .foregroundColor(p.onSurfaceVariant)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(p.background)
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if model.messages.isEmpty {
                        welcome
                    }
                    ForEach(model.messages) { message in
                        let isTail = message.id == model.messages.last?.id
                        MessageBubble(
                            message: message,
                            thinking: model.streaming && isTail && message.content.isEmpty,
                            streaming: model.streaming && isTail
                        )
                        .id(message.id)
                    }
                    Color.clear.frame(height: 1).id("bottom")
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
            }
            .onChange(of: model.messages.count) { _ in scroll(proxy) }
            .onChange(of: model.messages.last?.content ?? "") { _ in scroll(proxy) }
        }
    }

    private var welcome: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("你好，我是生活助理")
                    .font(.system(size: 16, weight: .semibold))
                Text("可以帮你查时间定位、记事情、安排任务、整理知识库，也能调度 MCP 与技能。")
                    .font(.system(size: 12))
                    .foregroundColor(p.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                ForEach(suggestions, id: \.self) { text in
                    Button {
                        input = text
                        inputFocused = true
                    } label: {
                        HStack(spacing: 8) {
                            Text(text)
                                .font(.system(size: 12))
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 0)
                            Image(systemName: "arrow.up.right")
                                .font(.system(size: 10))
                                .foregroundColor(p.muted)
                        }
                        .padding(.vertical, 8)
                        .padding(.horizontal, 10)
                        .background(p.surfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .foregroundColor(p.onBackground)
                }
            }
        }
    }

    private func banner(_ text: String, color: Color) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(text)
                .font(.system(size: 12))
                .foregroundColor(color)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            Button {
                model.error = nil
                model.notice = nil
            } label: {
                Image(systemName: "xmark").font(.system(size: 10))
            }
            .foregroundColor(p.muted)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(color.opacity(0.10))
    }

    private var inputBar: some View {
        let canSend = !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return HStack(alignment: .bottom, spacing: 8) {
            TextField("输入消息…", text: $input, axis: .vertical)
                .lineLimit(1...4)
                .focused($inputFocused)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(p.surface)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(p.outline, lineWidth: 1)
                )
            Button {
                if model.streaming {
                    model.stop()
                } else {
                    sendMessage()
                }
            } label: {
                Image(systemName: model.streaming ? "stop.fill" : "arrow.up")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(p.onPrimary)
                    .frame(width: 40, height: 40)
                    .background(model.streaming ? p.error : p.primary)
                    .clipShape(Circle())
            }
            .disabled(!model.streaming && !canSend)
            .opacity(!model.streaming && !canSend ? 0.4 : 1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(p.background)
    }

    private func sendMessage() {
        let text = input
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !model.streaming else { return }
        input = ""
        model.send(text)
    }

    private func scroll(_ proxy: ScrollViewProxy) {
        withAnimation(.easeOut(duration: 0.15)) {
            proxy.scrollTo("bottom", anchor: .bottom)
        }
    }
}

struct MessageBubble: View {
    let message: ChatMsg
    let thinking: Bool
    let streaming: Bool
    @Environment(\.palette) private var p

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            if message.isUser { Spacer(minLength: 44) }
            if !message.isUser { avatar }
            bubble
            if !message.isUser { Spacer(minLength: 20) }
        }
        .frame(maxWidth: .infinity, alignment: message.isUser ? .trailing : .leading)
    }

    private var bubble: some View {
        Group {
            if thinking && message.content.isEmpty {
                Text("正在思考…")
                    .font(.system(size: 13))
                    .foregroundColor(p.muted)
            } else {
                MarkdownText(text: streaming ? message.content + " ▍" : message.content)
                    .font(.system(size: 13.5))
                    .foregroundColor(p.onBackground)
            }
        }
        .padding(10)
        .background(message.isUser ? p.primaryContainer : p.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .frame(maxWidth: 300, alignment: .leading)
    }

    private var avatar: some View {
        Text("助")
            .font(.system(size: 12, weight: .semibold))
            .foregroundColor(p.primary)
            .frame(width: 28, height: 28)
            .background(p.primaryContainer)
            .clipShape(Circle())
    }
}

struct DraftSheet: View {
    let info: DraftInfo
    let onImport: () -> Void
    let onClose: () -> Void
    @Environment(\.palette) private var p

    private var name: String { info.payload.str("name") }
    private var description: String { info.payload.str("description") }
    private var content: String { info.payload.str("content") }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(info.kindTitle)
                        .font(.system(size: 12))
                        .foregroundColor(p.primary)
                    Text(name.isEmpty ? "（未命名）" : name)
                        .font(.system(size: 18, weight: .semibold))
                    if !description.isEmpty {
                        Text(description)
                            .font(.system(size: 13))
                            .foregroundColor(p.onSurfaceVariant)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    AppCard {
                        Text("安全扫描：导入前请确认内容来自可信来源，不会执行系统命令或读取敏感数据。")
                            .font(.system(size: 11))
                            .foregroundColor(p.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if !content.isEmpty {
                        SectionTitle(text: "内容预览")
                        Text(String(content.prefix(1200)))
                            .font(.system(size: 12, design: .monospaced))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(p.background.ignoresSafeArea())
            .navigationTitle("导入草稿")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("忽略", action: onClose)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("导入", action: onImport)
                }
            }
        }
    }
}
