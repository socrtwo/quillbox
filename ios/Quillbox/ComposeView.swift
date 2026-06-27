import SwiftUI

struct ComposeView: View {
    @EnvironmentObject var state: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var to = ""
    @State private var cc = ""
    @State private var bcc = ""
    @State private var subject = ""
    @State private var messageBody = ""
    @State private var error: String?
    @State private var sending = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("To (comma-separated)", text: $to)
                        .textInputAutocapitalization(.never)
                    TextField("Cc", text: $cc).textInputAutocapitalization(.never)
                    TextField("Bcc", text: $bcc).textInputAutocapitalization(.never)
                    TextField("Subject", text: $subject)
                }
                Section {
                    TextEditor(text: $messageBody).frame(minHeight: 180)
                }
                if let error { Text(error).foregroundColor(.red) }
            }
            .navigationTitle("New Message")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") { Task { await sendTapped() } }
                        .disabled(sending)
                }
            }
        }
    }

    private func sendTapped() async {
        sending = true
        error = nil
        let result = await state.send(
            to: split(to), cc: split(cc), bcc: split(bcc), subject: subject, body: messageBody
        )
        sending = false
        if let result {
            error = "Send failed: \(result)"
        } else {
            dismiss()
        }
    }

    private func split(_ value: String) -> [String] {
        value.split(whereSeparator: { $0 == "," || $0 == ";" })
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }
}
