import SwiftUI

@main
struct LifeAssistantApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

/// 全局 WebSocket：快照刷新、助手通知、后端关闭。
@MainActor
final class SocketHub: ObservableObject {
    @Published var backendDown = false
    @Published var toast: String?

    private let socket = MonitorSocket()
    private var running = false
    private var retryTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?

    init() {
        socket.onEvent = { [weak self] object in
            Task { @MainActor in
                guard let self else { return }
                switch object.str("type") {
                case "snapshot":
                    NotificationCenter.default.post(name: .monitorTick, object: nil)
                case "assistant_notify":
                    let title = object.str("title")
                    let content = object.str("content")
                    let text = title.isEmpty ? content : title + "：" + content
                    self.showToast(text)
                    NotificationCenter.default.post(name: .assistantNotify, object: nil, userInfo: ["text": text])
                case "server_shutdown":
                    self.backendDown = true
                    self.disconnect()
                default:
                    break
                }
            }
        }
        socket.onClosed = { [weak self] in
            Task { @MainActor in
                guard let self, self.running, !self.backendDown else { return }
                self.scheduleReconnect()
            }
        }
    }

    func connect() {
        running = true
        backendDown = false
        socket.start()
    }

    func disconnect() {
        running = false
        retryTask?.cancel()
        socket.stop()
    }

    func markDown() {
        backendDown = true
        disconnect()
    }

    func showToast(_ text: String) {
        toast = text
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            self?.toast = nil
        }
    }

    private func scheduleReconnect() {
        retryTask?.cancel()
        retryTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard let self, self.running, !self.backendDown else { return }
            self.socket.start()
        }
    }
}

struct RootView: View {
    @AppStorage("themeMode") private var themeMode = "system"
    @StateObject private var hub = SocketHub()

    var body: some View {
        ThemedRoot {
            Group {
                if hub.backendDown {
                    OfflineView(hub: hub)
                } else {
                    MainTabView(hub: hub)
                }
            }
            .overlay(alignment: .top) {
                if let toast = hub.toast {
                    ToastBanner(text: toast)
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.2), value: hub.toast)
            .task { hub.connect() }
        }
        .preferredColorScheme(colorScheme)
    }

    private var colorScheme: ColorScheme? {
        switch themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}

struct MainTabView: View {
    @ObservedObject var hub: SocketHub
    @State private var selection = 0
    @Environment(\.palette) private var p

    var body: some View {
        TabView(selection: $selection) {
            ChatView()
                .tabItem { Label("对话", systemImage: "bubble.left.and.bubble.right") }
                .tag(0)
            MonitorView()
                .tabItem { Label("监控", systemImage: "waveform.path.ecg") }
                .tag(1)
            TasksView()
                .tabItem { Label("任务", systemImage: "checklist") }
                .tag(2)
            KnowledgeView()
                .tabItem { Label("知识", systemImage: "books.vertical") }
                .tag(3)
            MoreView(hub: hub)
                .tabItem { Label("更多", systemImage: "ellipsis.circle") }
                .tag(4)
        }
        .tint(p.primary)
    }
}
