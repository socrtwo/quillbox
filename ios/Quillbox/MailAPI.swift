import Foundation

enum MailAPIError: LocalizedError {
    case badURL
    case server(String)
    var errorDescription: String? {
        switch self {
        case .badURL: return "Invalid server URL"
        case .server(let m): return m
        }
    }
}

/// Thin REST client for the Quillbox web backend. The iPhone app does no IMAP/SMTP
/// itself — it calls the backend, which performs the mail work.
struct MailAPI {
    let baseURL: String

    func inbox(account: Account, limit: Int = 100) async throws -> [Message] {
        let data = try await post(path: "/api/inbox", body: InboxRequest(account: account, limit: limit))
        return try JSONDecoder().decode([Message].self, from: data)
    }

    func send(_ request: SendRequest) async throws {
        _ = try await post(path: "/api/send", body: request)
    }

    private func post<T: Encodable>(path: String, body: T) async throws -> Data {
        guard let url = URL(string: baseURL.trimmingCharacters(in: .whitespaces) + path) else {
            throw MailAPIError.badURL
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: req)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            if let apiError = try? JSONDecoder().decode(ApiError.self, from: data) {
                throw MailAPIError.server(apiError.error)
            }
            throw MailAPIError.server("Request failed (\(http.statusCode))")
        }
        return data
    }
}
