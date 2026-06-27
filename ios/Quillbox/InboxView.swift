import SwiftUI

struct InboxView: View {
    @EnvironmentObject var state: AppState
    @State private var composing = false

    var body: some View {
        NavigationStack {
            List(state.messages) { message in
                NavigationLink(value: message) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(message.from.isEmpty ? "(unknown)" : message.from)
                            .font(.subheadline).bold()
                            .lineLimit(1)
                        Text(message.subject).font(.body).lineLimit(1)
                        Text(Date(timeIntervalSince1970: Double(message.sentDate) / 1000.0),
                             style: .date)
                            .font(.caption).foregroundColor(.secondary)
                    }
                }
            }
            .overlay {
                if state.loading { ProgressView() }
            }
            .navigationTitle(state.account?.displayName ?? "Quillbox")
            .navigationDestination(for: Message.self) { MessageDetailView(message: $0) }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Sign out") { state.signOut() }
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { Task { await state.refresh() } } label: { Image(systemName: "arrow.clockwise") }
                    Button { composing = true } label: { Image(systemName: "square.and.pencil") }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if let status = state.status {
                    Text(status).font(.caption).foregroundColor(.secondary).padding(6)
                }
            }
            .sheet(isPresented: $composing) {
                ComposeView()
            }
        }
    }
}

struct MessageDetailView: View {
    let message: Message

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                Text(message.subject).font(.title2).bold()
                Text("From: \(message.from)").font(.subheadline)
                Text("To: \(message.to)").font(.caption).foregroundColor(.secondary)
                Divider()
                Text(message.bodyText.isEmpty ? "(no text content)" : message.bodyText)
                    .font(.body)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .navigationTitle("Message")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// Message must be Hashable to drive navigationDestination(for:).
extension Message: Hashable {
    static func == (lhs: Message, rhs: Message) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}
