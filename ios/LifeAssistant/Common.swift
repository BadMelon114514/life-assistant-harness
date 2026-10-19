import SwiftUI

extension Notification.Name {
    static let monitorTick = Notification.Name("monitorTick")
    static let assistantNotify = Notification.Name("assistantNotify")
    static let backendDown = Notification.Name("backendDown")
}

struct AppCard<Content: View>: View {
    @Environment(\.palette) private var p
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(p.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(p.outline, lineWidth: 1)
            )
    }
}

struct StatusPill: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .medium))
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }
}

struct EmptyState: View {
    let text: String
    @Environment(\.palette) private var p

    var body: some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundColor(p.muted)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 36)
    }
}

struct SectionTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

func formatTime(_ raw: String) -> String {
    if raw.isEmpty { return "" }
    var text = raw.replacingOccurrences(of: "T", with: " ")
    if text.hasSuffix("Z") { text.removeLast() }
    return String(text.prefix(16))
}

func statusLabel(_ status: String) -> String {
    switch status.lowercased() {
    case "pending": return "待处理"
    case "in_progress", "working": return "进行中"
    case "completed", "done": return "已完成"
    case "blocked": return "阻塞"
    case "idle": return "空闲"
    case "shutdown": return "已停止"
    case "active": return "已启用"
    case "inactive": return "已停用"
    case "error": return "异常"
    default: return status.isEmpty ? "未知" : status
    }
}

func statusColor(_ status: String, _ p: Palette) -> Color {
    switch status.lowercased() {
    case "completed", "done", "active": return p.success
    case "in_progress", "working": return p.warning
    case "blocked", "error": return p.error
    case "idle": return p.primary
    case "shutdown", "inactive": return p.muted
    default: return p.onSurfaceVariant
    }
}

/// 轻量 Markdown：代码块、标题、列表、行内粗体/代码。
struct MarkdownText: View {
    let text: String
    @Environment(\.palette) private var p

    private enum Block {
        case paragraph(String)
        case heading(Int, String)
        case bullet(String)
        case code(String)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                switch block {
                case .paragraph(let content):
                    Text(inline(content))
                        .fixedSize(horizontal: false, vertical: true)
                case .heading(let level, let content):
                    Text(content)
                        .font(.system(size: level <= 1 ? 16 : (level == 2 ? 14.5 : 13.5), weight: .semibold))
                case .bullet(let content):
                    HStack(alignment: .top, spacing: 6) {
                        Text("•").font(.system(size: 12))
                        Text(inline(content))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                case .code(let content):
                    ScrollView(.horizontal, showsIndicators: false) {
                        Text(content)
                            .font(.system(size: 11.5, design: .monospaced))
                            .padding(8)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(p.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var blocks: [Block] {
        var output: [Block] = []
        var inCode = false
        var codeLines: [String] = []
        for raw in text.components(separatedBy: "\n") {
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                if inCode {
                    output.append(.code(codeLines.joined(separator: "\n")))
                    codeLines = []
                    inCode = false
                } else {
                    inCode = true
                }
                continue
            }
            if inCode {
                codeLines.append(raw)
                continue
            }
            if trimmed.isEmpty { continue }
            if trimmed.hasPrefix("#") {
                let level = trimmed.prefix(while: { $0 == "#" }).count
                let content = trimmed.drop(while: { $0 == "#" }).trimmingCharacters(in: .whitespaces)
                if !content.isEmpty {
                    output.append(.heading(level, content))
                    continue
                }
            }
            if trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") {
                output.append(.bullet(String(trimmed.dropFirst(2))))
                continue
            }
            output.append(.paragraph(trimmed))
        }
        if inCode, !codeLines.isEmpty {
            output.append(.code(codeLines.joined(separator: "\n")))
        }
        return output
    }

    private func inline(_ raw: String) -> AttributedString {
        if let attributed = try? AttributedString(
            markdown: raw,
            options: AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        ) {
            return attributed
        }
        return AttributedString(raw)
    }
}

struct ToastBanner: View {
    let text: String
    @Environment(\.palette) private var p

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "bell.badge")
                .font(.system(size: 12))
            Text(text)
                .font(.system(size: 12))
                .lineLimit(3)
            Spacer(minLength: 0)
        }
        .foregroundColor(p.onPrimary)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(p.primary)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .shadow(color: Color.black.opacity(0.18), radius: 10, y: 4)
        .padding(.horizontal, 16)
    }
}
