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

### Security note

Credentials and downloaded mail are stored locally only and are transmitted solely to the
user's own mail servers. Use real credentials only on your own device; the repository ships
with placeholder values only.
