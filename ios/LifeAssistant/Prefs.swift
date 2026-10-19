import Foundation

/// 本地偏好：服务器地址、会话标识、主题模式。
enum Prefs {
    private static let baseUrlKey = "baseUrl"
    private static let sessionKey = "session"
    private static let themeKey = "themeMode"

    /// 模拟器直连本机后端；真机请在设置页改成局域网地址。
    static let defaultBaseUrl = "http://127.0.0.1:8080"

    static var baseUrl: String {
        get {
            let raw = UserDefaults.standard.string(forKey: baseUrlKey) ?? defaultBaseUrl
            var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            while value.hasSuffix("/") { value.removeLast() }
            return value.isEmpty ? defaultBaseUrl : value
        }
        set {
            var value = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
            while value.hasSuffix("/") { value.removeLast() }
            UserDefaults.standard.set(value, forKey: baseUrlKey)
        }
    }

    static var session: String {
        if let value = UserDefaults.standard.string(forKey: sessionKey), !value.isEmpty {
            return value
        }
        return newSession()
    }

    @discardableResult
    static func newSession() -> String {
        let value = "ios_" + String(Int(Date().timeIntervalSince1970 * 1000), radix: 36)
        UserDefaults.standard.set(value, forKey: sessionKey)
        return value
    }

    static var themeMode: String {
        get { UserDefaults.standard.string(forKey: themeKey) ?? "system" }
        set { UserDefaults.standard.set(newValue, forKey: themeKey) }
    }
}
