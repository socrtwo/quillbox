import Foundation

// Mirrors the JSON the Quillbox web backend expects/returns.
struct Account: Codable, Equatable {
    var displayName: String = ""
    var email: String = ""
    var incomingHost: String = ""
    var incomingPort: Int = 993
    var proto: String = "IMAP"          // maps to JSON key "protocol"
    var incomingSecurity: String = "SSL_TLS"
    var smtpHost: String = ""
    var smtpPort: Int = 587
    var smtpSecurity: String = "STARTTLS"
    var username: String = ""
    var password: String = ""

    enum CodingKeys: String, CodingKey {
        case displayName, email, incomingHost, incomingPort
        case proto = "protocol"
        case incomingSecurity, smtpHost, smtpPort, smtpSecurity, username, password
    }
}

struct Message: Codable, Identifiable {
    // No server id; synthesize a stable one for SwiftUI lists.
    var id: String { "\(sentDate)-\(subject)-\(from)" }
    let from: String
    let to: String
    let subject: String
    let bodyText: String
    let bodyHtml: String?
    let sentDate: Int64
    let hasAttachments: Bool
}

struct InboxRequest: Codable {
    let account: Account
    let limit: Int
}

struct SendRequest: Codable {
    let account: Account
    let to: [String]
    let cc: [String]
    let bcc: [String]
    let subject: String
    let body: String
}

struct ApiError: Codable { let error: String }
