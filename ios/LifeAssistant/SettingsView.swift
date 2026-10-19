import SwiftUI

struct SettingsView: View {
    @ObservedObject var hub: SocketHub
    @State private var baseUrl = Prefs.baseUrl
    @State private var apiKey = ""
    @State private var testing = false
    @State private var message: String?
    @State private var showShutdownConfirm = false
    @AppStorage("themeMode") private var themeMode = "system"
    @Environment(\.palette) private var p

    private let themes = [("system", "跟随系统"), ("light", "浅色"), ("dark", "深色")]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                serverCard
                apiKeyCard
                themeCard
                dangerCard
                Text("生活助理 Harness · iOS 客户端 v2.0.0")
                    .font(.system(size: 10))
                    .foregroundColor(p.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 4)
            }
            .padding(14)
        }
        .background(p.background.ignoresSafeArea())
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .alert("关闭后端服务", isPresented: $showShutdownConfirm) {
            Button("取消", role: .cancel) {}
            Button("确认关闭", role: .destructive) { shutdown() }
        } message: {
            Text("将停止桌面端 8080 与上下文存储 8765 服务，需要重新启动桌面程序才能恢复。")
        }
    }

    private var serverCard: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 8) {
                SectionTitle(text: "服务器地址")
                TextField("http://192.168.1.10:8080", text: $baseUrl)
                    .font(.system(size: 13))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(p.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                HStack(spacing: 8) {
                    Button(testing ? "测试中…" : "保存并测试") { saveAndTest() }
                        .buttonStyle(.borderedProminent)
                        .tint(p.primary)
                        .disabled(testing)
                    if let message {
                        Text(message)
                            .font(.system(size: 11))
                            .foregroundColor(message.hasPrefix("连接成功") ? p.success : p.error)
                            .lineLimit(2)
                    }
                }
                Text("模拟器可用 http://127.0.0.1:8080；真机请填电脑的局域网 IP。")
                    .font(.system(size: 10))
                    .foregroundColor(p.muted)
            }
        }
    }

    private var apiKeyCard: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 8) {
                SectionTitle(text: "模型 API Key")
                SecureField("sk-…（留空表示不修改）", text: $apiKey)
                    .font(.system(size: 13))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(p.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                Button("保存 API Key") { saveKey() }
                    .buttonStyle(.bordered)
                    .tint(p.primary)
                    .disabled(apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Text("密钥保存到桌面端设置，不会回显到客户端。")
                    .font(.system(size: 10))
                    .foregroundColor(p.muted)
            }
        }
    }

    private var themeCard: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 8) {
                SectionTitle(text: "外观主题")
                HStack(spacing: 8) {
                    ForEach(themes, id: \.0) { key, label in
                        Button { themeMode = key } label: {
                            Text(label)
                                .font(.system(size: 12))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(themeMode == key ? p.primaryContainer : p.surface)
                                .foregroundColor(themeMode == key ? p.primary : p.onSurfaceVariant)
                                .clipShape(Capsule())
                                .overlay(Capsule().stroke(p.outline, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var dangerCard: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 8) {
                SectionTitle(text: "危险操作")
                Button(role: .destructive) {
                    showShutdownConfirm = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "power")
                        Text("关闭后端服务")
                    }
                    .font(.system(size: 13))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(p.error.opacity(0.10))
                    .foregroundColor(p.error)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                Text("关闭后 8080 与 8765 都会停止，需要重新启动桌面程序或桌面快捷方式。")
                    .font(.system(size: 10))
                    .foregroundColor(p.muted)
            }
        }
    }

    private func saveAndTest() {
        Prefs.baseUrl = baseUrl
        baseUrl = Prefs.baseUrl
        Prefs.newSession()
        testing = true
        message = nil
        Task { @MainActor in
            do {
                let result = try await Api.testConnection()
                message = result
                hub.connect()
            } catch {
                message = "连接失败：" + error.localizedDescription
            }
            testing = false
        }
    }

    private func saveKey() {
        let value = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        Task { @MainActor in
            do {
                _ = try await Api.post("/api/settings/api-key", ["value": value])
                apiKey = ""
                message = "✅ API Key 已保存"
            } catch {
                message = "保存失败：" + error.localizedDescription
            }
        }
    }

    private func shutdown() {
        Task { @MainActor in
            do {
                _ = try await Api.postWithHeaders(
                    "/api/system/shutdown",
                    [:],
                    headers: ["X-Shutdown-Confirm": "yes"]
                )
            } catch {
                // 后端关闭时连接可能被直接断开，视为成功。
            }
            hub.markDown()
        }
    }
}
