import SwiftUI

@main
struct QuillboxApp: App {
    @StateObject private var state = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(state)
        }
    }
}

@MainActor
final class AppState: ObservableObject {
    /// Base URL of the Quillbox web backend (the Ktor server under /web).
    @Published var serverURL: String = "https://your-quillbox-server.example.com"
    @Published var account: Account? = nil
    @Published var messages: [Message] = []
    @Published var status: String? = nil
    @Published var loading: Bool = false

    private var api: MailAPI { MailAPI(baseURL: serverURL) }

    func connect(_ account: Account) {
        self.account = account
        Task { await refresh() }
    }

    func signOut() {
        account = nil
        messages = []
        status = nil
    }

    func refresh() async {
        guard let account else { return }
        loading = true
        status = nil
        do {
            messages = try await api.inbox(account: account)
            status = "\(messages.count) message(s)"
        } catch {
            status = "Sync failed: \(error.localizedDescription)"
        }
        loading = false
    }

    func send(to: [String], cc: [String], bcc: [String], subject: String, body: String) async -> String? {
        guard let account else { return "No account" }
        do {
            try await api.send(SendRequest(account: account, to: to, cc: cc, bcc: bcc, subject: subject, body: body))
            return nil
        } catch {
            return error.localizedDescription
        }
    }
}
