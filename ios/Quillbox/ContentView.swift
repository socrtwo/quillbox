import SwiftUI

struct ContentView: View {
    @EnvironmentObject var state: AppState

    var body: some View {
        if state.account == nil {
            SetupView()
        } else {
            InboxView()
        }
    }
}

struct SetupView: View {
    @EnvironmentObject var state: AppState

    @State private var displayName = ""
    @State private var email = ""
    @State private var incomingHost = ""
    @State private var incomingPort = "993"
    @State private var proto = "IMAP"
    @State private var smtpHost = ""
    @State private var smtpPort = "587"
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Quillbox server") {
                    TextField("Server URL", text: $state.serverURL)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
                Section("Account") {
                    TextField("Display name", text: $displayName)
                    TextField("Email address", text: $email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    Picker("Protocol", selection: $proto) {
                        Text("IMAP").tag("IMAP")
                        Text("POP3").tag("POP3")
                    }
                }
                Section("Incoming") {
                    TextField("Incoming host", text: $incomingHost)
                        .textInputAutocapitalization(.never)
                    TextField("Incoming port", text: $incomingPort)
                        .keyboardType(.numberPad)
                }
                Section("Outgoing (SMTP)") {
                    TextField("SMTP host", text: $smtpHost)
                        .textInputAutocapitalization(.never)
                    TextField("SMTP port", text: $smtpPort)
                        .keyboardType(.numberPad)
                }
                Section("Credentials") {
                    TextField("Username", text: $username)
                        .textInputAutocapitalization(.never)
                    SecureField("Password", text: $password)
                }
                Button("Connect") {
                    state.connect(makeAccount())
                }
            }
            .navigationTitle("Add Mail Account")
        }
    }

    private func makeAccount() -> Account {
        Account(
            displayName: displayName.isEmpty ? email : displayName,
            email: email.trimmingCharacters(in: .whitespaces),
            incomingHost: incomingHost.trimmingCharacters(in: .whitespaces),
            incomingPort: Int(incomingPort) ?? 993,
            proto: proto,
            incomingSecurity: "SSL_TLS",
            smtpHost: smtpHost.trimmingCharacters(in: .whitespaces),
            smtpPort: Int(smtpPort) ?? 587,
            smtpSecurity: "STARTTLS",
            username: username.trimmingCharacters(in: .whitespaces),
            password: password
        )
    }
}
