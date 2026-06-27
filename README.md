# quillbox
Android email client: downloads IMAP/POP3 mail, composes new messages with a standard formatting toolbar, and routes incoming mail into folders (e.g. Spam) via user-defined rules.

---

## Quillbox (Android app)

A native Android email client. Quillbox connects to a user's own mail server over
IMAP or POP3, stores messages locally, lets you compose richly formatted mail over
SMTP, and routes incoming mail into folders with a user-managed rules engine.

- **Application ID:** `info.socrtwo.quillbox`
- **minSdk:** 26 (Android 8.0) · **compile/target SDK:** 35 (Android 15)

### Tech stack

| Concern            | Choice                                                        |
|--------------------|--------------------------------------------------------------|
| Language / build   | Kotlin, Gradle Kotlin DSL, Gradle wrapper 8.13               |
| UI                 | Jetpack Compose, Material 3                                   |
| Architecture       | MVVM — `ViewModel` + Kotlin `Flow`/`StateFlow`               |
| Persistence        | Room                                                          |
| Dependency inject. | Hilt                                                          |
| Async              | Kotlin Coroutines                                             |
| Mail protocols     | Jakarta Mail — `org.eclipse.angus:angus-mail` (IMAP/POP3/SMTP) |

### Features

- **Account setup** — host, port, username, password, protocol (IMAP/POP3) and an
  SSL·TLS / STARTTLS / None security selector, with an optional *Verify & Save* that
  connects before persisting. Separate SMTP host/port/security for sending.
- **Mail download** — a repository layer connects, fetches message headers + bodies,
  and stores them in Room. Folder list → message list → message detail screens, all
  with **pull-to-refresh**.
- **Compose** — a rich-text editor with a formatting toolbar (bold, italic, underline,
  bulleted list, numbered list, attach, send). Supports To/Cc/Bcc, subject and file
  attachments (via the Storage Access Framework). Sends over SMTP using the account
  credentials and files a copy into *Sent*.
- **Rules engine** — a user-manageable rule list. Each rule combines criteria
  (*sender contains* / *subject contains* / *body keyword*) with **AND/OR** logic and an
  action (**move to folder** e.g. Spam, **mark read**, or **delete**). Rules evaluate
  against incoming mail on every fetch. A **Default Spam Filter** rule is seeded on first
  launch. Rules are persisted in Room.

### Project layout

```
app/src/main/java/info/socrtwo/quillbox/
├── QuillboxApplication.kt        // @HiltAndroidApp
├── MainActivity.kt               // Compose host + NavHost
├── data/
│   ├── model/                    // enums + RuleCriterion
│   ├── local/                    // Room: entities, DAOs, database, converters
│   ├── mail/                     // MailClient (Jakarta Mail) + DTOs
│   ├── rules/                    // RulesEngine
│   └── repository/               // Account / Mail / Rule repositories
├── di/                           // Hilt modules
└── ui/                           // theme, navigation + per-screen Compose + ViewModels
```

### Build the debug APK

Requires JDK 17+ and the Android SDK (platform 35, build-tools). The Gradle wrapper is
committed, so:

```bash
./gradlew assembleDebug
```

The sideloadable debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a device with `adb install app/build/outputs/apk/debug/app-debug.apk`.

> **CI:** `.github/workflows/android.yml` builds `assembleDebug` on every push and uploads
> `app-debug.apk` as a build artifact (the `quillbox-debug-apk` artifact).

### Build a signed release APK

Release signing is configured in `app/build.gradle.kts` and reads the keystore path and
passwords from `local.properties` or environment variables — **nothing is hardcoded**, and
no keystore is committed. To produce a signed release build:

1. Generate a keystore (one-time, kept outside the repo):

   ```bash
   keytool -genkeypair -v -keystore quillbox-release.jks \
     -alias quillbox -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Provide the credentials via **either** `local.properties`:

   ```properties
   RELEASE_STORE_FILE=/absolute/path/to/quillbox-release.jks
   RELEASE_STORE_PASSWORD=********
   RELEASE_KEY_ALIAS=quillbox
   RELEASE_KEY_PASSWORD=********
   ```

   **or** environment variables:

   ```bash
   export QUILLBOX_RELEASE_STORE_FILE=/absolute/path/to/quillbox-release.jks
   export QUILLBOX_RELEASE_STORE_PASSWORD=********
   export QUILLBOX_RELEASE_KEY_ALIAS=quillbox
   export QUILLBOX_RELEASE_KEY_PASSWORD=********
   ```

3. Build:

   ```bash
   ./gradlew assembleRelease
   ```

   The APK is written to `app/build/outputs/apk/release/app-release.apk`. If no keystore is
   configured the release `signingConfig` is simply left unset.

### Automated signed builds in CI

`.github/workflows/android.yml` can sign the release APK automatically when a keystore is
provided via repository **Secrets** (the keystore is never committed). When the secrets are
absent, the release step still runs but produces an *unsigned* APK.

Set these four secrets under **Settings → Secrets and variables → Actions → New repository
secret**:

| Secret name        | Value                                                            |
|--------------------|-----------------------------------------------------------------|
| `KEYSTORE_BASE64`  | Base64 of your `.jks` file (see below)                          |
| `KEYSTORE_PASSWORD`| The keystore password                                           |
| `KEY_ALIAS`        | The key alias (e.g. `quillbox`)                                 |
| `KEY_PASSWORD`     | The key password (same as the keystore password if you reused it)|

Produce the base64 of the keystore (one line, no wrapping):

```bash
base64 -w 0 quillbox-release.jks > keystore.b64   # Linux / Termux
# macOS: base64 -i quillbox-release.jks -o keystore.b64
```

Open `keystore.b64`, copy its entire contents, and paste it as the `KEYSTORE_BASE64` secret.

Once the secrets are set, every build uploads a **signed** `app-release.apk` as the
`quillbox-release-apk` artifact, ready to sideload — no manual signing needed. The decoded
keystore lives only in the runner's temp dir for the duration of the build and is never
written to the workspace or committed.

## Other platforms

Quillbox started as an Android app; sibling clients live in their own folders. Each is built
by its own GitHub Actions workflow.

| Folder      | Target                          | Stack                                              | Build artifact |
|-------------|---------------------------------|----------------------------------------------------|----------------|
| `app/`      | Android (and **ChromeOS**)      | Kotlin, Jetpack Compose, Room, Hilt, Jakarta Mail  | `.apk`         |
| `desktop/`  | Windows / macOS / Linux         | Compose Multiplatform Desktop (JVM), Jakarta Mail  | app image + `.msi`/`.dmg`/`.deb` |
| `web/`      | Browser                         | Ktor backend (JVM, Jakarta Mail) + static web UI   | server `.zip`  |
| `ios/`      | iPhone / iPad                   | SwiftUI client calling the `web/` backend over REST | `.app` (simulator) |

Notes on the architecture choices:

- **ChromeOS** runs the Android APK directly — no separate build.
- **Desktop** reuses the same Jakarta Mail protocol logic on the desktop JVM.
  Run locally with `cd desktop && ./gradlew run`; package installers with
  `./gradlew packageDistributionForCurrentOS`.
- **Web**: browsers cannot open IMAP/SMTP sockets, so `web/` is a small backend that does the
  mail work and serves a browser UI. Run with `cd web && ./gradlew run` (defaults to
  `http://localhost:8080`).
- **iOS**: Jakarta Mail is JVM-only, so the iPhone app is a thin SwiftUI client that talks to
  the `web/` backend's REST API. Set the server URL on the setup screen. The Xcode project is
  generated from `ios/project.yml` via [XcodeGen](https://github.com/yonaskolb/XcodeGen)
  (`cd ios && xcodegen generate && open Quillbox.xcodeproj`).

These are scaffolds that share the protocol approach but not a single build; the Android client
remains the most complete.

## Security note

Credentials and downloaded mail are stored locally only and are transmitted solely to the
user's own mail servers (on the web/iOS clients, via your own Quillbox backend). Use real
credentials only on your own device; the repository ships with placeholder values only.
