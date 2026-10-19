import SwiftUI

struct MoreView: View {
    @ObservedObject var hub: SocketHub
    @Environment(\.palette) private var p

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    AppCard {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("生活助理 Harness")
                                .font(.system(size: 16, weight: .semibold))
                            Text("对话、监控、任务、知识与记忆，都在这里。")
                                .font(.system(size: 12))
                                .foregroundColor(p.muted)
                        }
                    }
                    entries
                    Text("v2.0.0 · 原生 SwiftUI 客户端")
                        .font(.system(size: 10))
                        .foregroundColor(p.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 4)
                }
                .padding(14)
            }
            .background(p.background.ignoresSafeArea())
            .navigationTitle("更多")
        }
    }

    private var entries: some View {
        VStack(spacing: 0) {
            entryLink("知识库", icon: "books.vertical", subtitle: "文档入库与检索") {
                KnowledgeView()
            }
            divider
            entryLink("记忆库", icon: "brain.head.profile", subtitle: "长期记忆与重要度") {
                MemoryView()
            }
            divider
            entryLink("MCP 服务", icon: "powerplug", subtitle: "外部能力接入与测试") {
                McpView()
            }
            divider
            entryLink("技能中心", icon: "square.stack.3d.up", subtitle: "可复用的任务指令") {
                SkillsView()
            }
            divider
            entryLink("设置", icon: "gearshape", subtitle: "服务器、密钥与主题") {
                SettingsView(hub: hub)
            }
            divider
            entryLink("关于", icon: "info.circle", subtitle: "版本与连接说明") {
                AboutView()
            }
        }
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(p.outline, lineWidth: 1)
        )
    }

    private var divider: some View {
        Divider().background(p.outline).padding(.leading, 44)
    }

    private func entryLink<Destination: View>(
        _ title: String,
        icon: String,
        subtitle: String,
        @ViewBuilder destination: @escaping () -> Destination
    ) -> some View {
        NavigationLink {
            destination()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 15))
                    .foregroundColor(p.primary)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(p.onBackground)
                    Text(subtitle)
                        .font(.system(size: 10))
                        .foregroundColor(p.muted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 11))
                    .foregroundColor(p.muted)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct AboutView: View {
    @Environment(\.palette) private var p

    private let features = [
        "流式对话：SSE token 追加、停止生成、高危操作确认",
        "进程监控：团队状态、进度与日志实时刷新",
        "任务看板：认领、推进、完成与新建任务",
        "知识库：文本入库、关键词检索与摘要",
        "记忆库：长期偏好与重要度管理",
        "MCP 服务：启用、停用、测试与新增",
        "技能中心：管理可复用的任务指令",
        "主题切换：跟随系统 / 浅色 / 深色",
        "一键关闭后端：联动停止桌面端全部服务"
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppCard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("生活助理 Harness")
                            .font(.system(size: 17, weight: .semibold))
                        Text("原生 SwiftUI 客户端，连接桌面版后端。")
                            .font(.system(size: 12))
                            .foregroundColor(p.muted)
                        Text("版本 2.0.0")
                            .font(.system(size: 11))
                            .foregroundColor(p.muted)
                    }
                }
                SectionTitle(text: "支持的功能")
                AppCard {
                    VStack(alignment: .leading, spacing: 7) {
                        ForEach(Array(features.enumerated()), id: \.offset) { _, item in
                            HStack(alignment: .top, spacing: 6) {
                                Text("•").font(.system(size: 11))
                                Text(item)
                                    .font(.system(size: 12))
                                    .foregroundColor(p.onSurfaceVariant)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                }
                SectionTitle(text: "连接方式")
                AppCard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("· iOS 模拟器：设置页服务器地址填 http://127.0.0.1:8080")
                        Text("· 真机：填电脑局域网 IP，例如 http://192.168.1.10:8080")
                        Text("· 电脑需先启动桌面程序（或 生活助理Harness一键启动.exe）")
                    }
                    .font(.system(size: 12))
                    .foregroundColor(p.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(14)
        }
        .background(p.background.ignoresSafeArea())
        .navigationTitle("关于")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct OfflineView: View {
    @ObservedObject var hub: SocketHub
    @State private var retrying = false
    @State private var error: String?
    @Environment(\.palette) private var p

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "power.circle")
                .font(.system(size: 46))
                .foregroundColor(p.muted)
            Text("服务已关闭")
                .font(.system(size: 20, weight: .semibold))
            Text("桌面端后端已停止，对话、监控与任务暂时不可用。\n重新启动桌面程序后可继续使用。")
                .font(.system(size: 12))
                .foregroundColor(p.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let error {
                Text(error)
                    .font(.system(size: 11))
                    .foregroundColor(p.error)
                    .multilineTextAlignment(.center)
            }
            Button(retrying ? "正在重试…" : "重试连接") {
                retry()
            }
            .buttonStyle(.borderedProminent)
            .tint(p.primary)
            .disabled(retrying)
            Spacer()
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(p.background.ignoresSafeArea())
    }

    private func retry() {
        retrying = true
        error = nil
        Task { @MainActor in
            do {
                _ = try await Api.testConnection()
                hub.backendDown = false
                hub.connect()
            } catch {
                self.error = "仍无法连接：" + error.localizedDescription
            }
            retrying = false
        }
    }
}
