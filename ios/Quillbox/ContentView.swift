import SwiftUI

struct ContentView: View {
    @EnvironmentObject var state: AppState
    @Environment(\.colorScheme) private var colorScheme

    /// Colours of the web UI's top bar and page background (styles.css), painted behind the
    /// status bar and the home indicator so the page looks edge-to-edge without ever sitting
    /// under the system bars.
    private var topBarColor: Color { colorScheme == .dark ? Color(red: 0.06, green: 0.06, blue: 0.06) : Color(red: 0.047, green: 0.231, blue: 0.369) }
    private var pageColor: Color { colorScheme == .dark ? Color(red: 0.16, green: 0.16, blue: 0.16) : Color.white }

    var body: some View {
        if state.choosingServer || state.serverBase == nil {
            ServerSetupView()
        } else {
            GeometryReader { geo in
                ZStack {
                    pageColor.ignoresSafeArea()
                    VStack(spacing: 0) {
                        topBarColor.frame(height: geo.safeAreaInsets.top)
                        QuillboxWebView(state: state, url: state.serverBase!, reloadToken: state.reloadToken)
                        pageColor.frame(height: geo.safeAreaInsets.bottom)
                    }
                    .padding(.leading, geo.safeAreaInsets.leading)
                    .padding(.trailing, geo.safeAreaInsets.trailing)
                    .ignoresSafeArea()
                    if let err = state.loadError {
                        ConnectionErrorView(message: err)
                    }
                }
            }
        }
    }
}

/// First-run screen: where is the Quillbox server?
struct ServerSetupView: View {
    @EnvironmentObject var state: AppState
    @State private var url: String = ""
    @State private var checking = false
    @State private var problem: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("http://192.168.1.20:8080", text: $url)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .submitLabel(.go)
                        .onSubmit { connect() }
                } header: {
                    Text("Quillbox server address")
                } footer: {
                    Text("Quillbox for iPhone and iPad shows the same Outlook-style mail client as the desktop and web versions, served by a Quillbox server on your own network. Run the server on a PC, Mac, Raspberry Pi or NAS (the \"quillbox-web\" download, or the desktop app started with HOST=0.0.0.0) and enter its address here — for example http://192.168.1.20:8080. Use https:// when the server is reachable from the internet.")
                }
                if let problem {
                    Section { Text(problem).foregroundColor(.red) }
                }
                Section {
                    Button {
                        connect()
                    } label: {
                        HStack {
                            if checking { ProgressView().padding(.trailing, 6) }
                            Text(checking ? "Checking…" : "Connect")
                        }
                    }
                    .disabled(checking || url.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .navigationTitle("Quillbox")
            .toolbar {
                if !state.serverURL.isEmpty {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { state.choosingServer = false }
                    }
                }
            }
            .onAppear { url = state.serverURL }
        }
    }

    @MainActor
    private func connect() {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        problem = nil
        checking = true
        Task {
            defer { checking = false }
            var s = trimmed
            if !s.lowercased().hasPrefix("http://") && !s.lowercased().hasPrefix("https://") { s = "http://" + s }
            while s.hasSuffix("/") { s.removeLast() }
            guard let probe = URL(string: s + "/index.html") else { problem = "That is not a valid address."; return }
            do {
                var req = URLRequest(url: probe)
                req.timeoutInterval = 8
                let (data, response) = try await URLSession.shared.data(for: req)
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                let body = String(data: data, encoding: .utf8) ?? ""
                if status == 200 && body.contains("Quillbox") {
                    state.useServer(s)
                } else {
                    problem = "Something answered at \(s), but it does not look like a Quillbox server (HTTP \(status))."
                }
            } catch {
                problem = "Could not reach \(s): \(error.localizedDescription)"
            }
        }
    }
}

struct ConnectionErrorView: View {
    @EnvironmentObject var state: AppState
    let message: String

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "wifi.exclamationmark").font(.system(size: 44)).foregroundColor(.secondary)
            Text("Cannot reach the Quillbox server").font(.headline)
            Text(message).font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center)
            Text(state.serverURL).font(.footnote).foregroundColor(.secondary)
            HStack {
                Button("Retry") { state.retry() }.buttonStyle(.borderedProminent)
                Button("Change server") { state.changeServer() }.buttonStyle(.bordered)
            }
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}
