import Foundation

/// 监控 WebSocket：snapshot / assistant_notify / server_shutdown。
final class MonitorSocket {
    private var task: URLSessionWebSocketTask?
    private var closed = false

    var onEvent: ((JSONObject) -> Void)?
    var onClosed: (() -> Void)?

    func start() {
        stop()
        closed = false
        guard let url = URL(string: Api.baseUrl + "/ws/monitor") else {
            onClosed?()
            return
        }
        let socket = URLSession.shared.webSocketTask(with: url)
        task = socket
        socket.resume()
        listen()
    }

    private func listen() {
        task?.receive { [weak self] result in
            guard let self, !self.closed else { return }
            switch result {
            case .success(let message):
                var data: Data?
                switch message {
                case .string(let text): data = text.data(using: .utf8)
                case .data(let raw): data = raw
                default: data = nil
                }
                if let data, let object = (try? JSONSerialization.jsonObject(with: data)) as? JSONObject {
                    DispatchQueue.main.async { self.onEvent?(object) }
                }
                self.listen()
            case .failure:
                DispatchQueue.main.async { self.onClosed?() }
            }
        }
    }

    func stop() {
        closed = true
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
    }
}
