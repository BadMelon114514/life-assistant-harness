import SwiftUI

struct MonitorView: View {
    @State private var overview: JSONObject = [:]
    @State private var loading = true
    @State private var error: String?
    @Environment(\.palette) private var p

    private var summary: JSONObject { overview.dict("summary") }
    private var teammates: [JSONObject] { overview.arr("teammates") }
    private var logs: [JSONObject] { Array(overview.arr("logs").suffix(40)) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                if let error {
                    Text("⚠️ " + error)
                        .font(.system(size: 12))
                        .foregroundColor(p.error)
                }
                statsGrid
                SectionTitle(text: "团队成员")
                if teammates.isEmpty {
                    EmptyState(text: loading ? "加载中…" : "暂无成员")
                } else {
                    ForEach(Array(teammates.enumerated()), id: \.offset) { _, mate in
                        TeammateCard(mate: mate)
                    }
                }
                SectionTitle(text: "最近日志")
                AppCard {
                    VStack(alignment: .leading, spacing: 7) {
                        if logs.isEmpty {
                            Text("暂无日志").font(.system(size: 12)).foregroundColor(p.muted)
                        } else {
                            ForEach(Array(logs.enumerated()), id: \.offset) { _, log in
                                LogRow(log: log)
                            }
                        }
                    }
                }
            }
            .padding(14)
        }
        .background(p.background.ignoresSafeArea())
        .refreshable { await load() }
        .task { await startPolling() }
        .onReceive(NotificationCenter.default.publisher(for: .monitorTick)) { _ in
            Task { await load() }
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("进程监控")
                    .font(.system(size: 20, weight: .semibold))
                Text("每 5 秒自动刷新")
                    .font(.system(size: 11))
                    .foregroundColor(p.muted)
            }
            Spacer()
            if loading {
                ProgressView().scaleEffect(0.8)
            }
            Button { Task { await load() } } label: {
                Image(systemName: "arrow.clockwise").font(.system(size: 14))
            }
            .foregroundColor(p.onSurfaceVariant)
        }
    }

    private var statsGrid: some View {
        LazyVGrid(
            columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)],
            spacing: 10
        ) {
            StatCell(title: "成员总数", value: String(summary.int("total")), color: p.primary)
            StatCell(title: "工作中", value: String(summary.int("working")), color: p.warning)
            StatCell(title: "空闲", value: String(summary.int("idle")), color: p.muted)
            StatCell(title: "平均进度", value: String(Int(summary.double("avgProgress"))) + "%", color: p.success)
        }
    }

    private func startPolling() async {
        await load()
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if Task.isCancelled { break }
            await load()
        }
    }

    private func load() async {
        do {
            overview = try await Api.getObject("/api/monitor/overview")
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

private struct StatCell: View {
    let title: String
    let value: String
    let color: Color
    @Environment(\.palette) private var p

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 11))
                .foregroundColor(p.muted)
            Text(value)
                .font(.system(size: 22, weight: .semibold))
                .foregroundColor(color)
                .monospacedDigit()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(p.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(p.outline, lineWidth: 1)
        )
    }
}

private struct TeammateCard: View {
    let mate: JSONObject
    @Environment(\.palette) private var p

    private var status: String { mate.str("status") }
    private var progress: Double { mate.double("progress") }

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: 7) {
                HStack(alignment: .center, spacing: 8) {
                    Text(mate.str("name").isEmpty ? "（未命名）" : mate.str("name"))
                        .font(.system(size: 14, weight: .semibold))
                    StatusPill(text: statusLabel(status), color: statusColor(status, p))
                    Spacer(minLength: 0)
                }
                Text(mate.str("role"))
                    .font(.system(size: 11))
                    .foregroundColor(p.muted)
                ProgressView(value: progress, total: 100)
                    .tint(statusColor(status, p))
                HStack {
                    Text("进度 " + String(Int(progress)) + "%")
                    Spacer()
                    Text("心跳 " + formatTime(mate.str("heartbeatAt")))
                }
                .font(.system(size: 10))
                .foregroundColor(p.muted)
                if !mate.str("currentTask").isEmpty {
                    Text("当前任务：" + mate.str("currentTask"))
                        .font(.system(size: 12))
                        .foregroundColor(p.onSurfaceVariant)
                        .lineLimit(2)
                }
            }
        }
    }
}

private struct LogRow: View {
    let log: JSONObject
    @Environment(\.palette) private var p

    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            Text(formatTime(log.str("createdAt")))
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(p.muted)
            Text("[" + log.str("source") + "]")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(sourceColor)
            Text(log.str("message"))
                .font(.system(size: 11))
                .foregroundColor(p.onSurfaceVariant)
                .lineLimit(3)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var sourceColor: Color {
        switch log.str("level").uppercased() {
        case "ERROR": return p.error
        case "WARN": return p.warning
        default: return p.primary
        }
    }
}
