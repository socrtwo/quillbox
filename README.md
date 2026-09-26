# quillbox
Email client for Android, desktop, web and iOS: downloads IMAP/POP3 mail, composes with a
formatting toolbar, and keeps junk out of the Inbox with open DNS blocklists, an offline
brand-impersonation detector, a learning classifier and user rules. The web client is laid out
like Microsoft 365 Outlook.

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

## Web client (Outlook-style, with built-in junk protection)

`web/` is a self-hosted web mail client: a Ktor backend that talks IMAP/POP3/SMTP on your behalf,
plus a browser front end laid out like Microsoft 365 Outlook (folder pane, message list, reading
pane, command bar, keyboard shortcuts, dark mode, responsive down to phone width).

### Run it

```bash
cd web
./gradlew run            # http://localhost:8080
```

Environment variables: `PORT` (default 8080), `HOST` (default 0.0.0.0), `QUILLBOX_DATA_DIR`
(default `~/.quillbox`; holds per-account rules, settings, the learned classifier and the
analysis cache — **never passwords**).

To try it without a real mailbox:

```bash
cd web
./gradlew demo           # in-memory IMAP/SMTP seeded with genuine, spoofed and blacklisted mail
```

then sign in as `demo@quillbox.test` / `demo` and, under *Server settings*, use IMAP
`127.0.0.1:3143` (security *None*) and SMTP `127.0.0.1:3025` (*None*).

### Setting up an account

Enter your name, address and password. The server looks the settings up for you (built-in
provider table → Mozilla ISPDB → the domain's own autoconfig → MX record → port probe), shows
which provider it found and any caveats (for example that Gmail, Yahoo, iCloud, AOL and
Fastmail need an *app password*), and lets you test the connection before signing in. Tick
*Keep me signed in* to store the account in the browser.

### Junk protection (on by default)

Every new message is analysed on the server and, when it is junk, moved to the Junk folder
automatically (POP3 accounts get a local Junk view instead, since POP3 has no folders). Signals,
all free and needing no API key:

| Signal | What it does |
|---|---|
| **DNS blocklists** | The sending IPs from the `Received` chain are checked against Spamhaus ZEN, SpamCop, Barracuda and PSBL; the sender, Reply-To and link domains against Spamhaus DBL, SURBL and URIBL. Lists can be toggled, re-weighted and extended with your own zones in *Settings → Junk email*. Spamhaus refuses queries via public resolvers (8.8.8.8, 1.1.1.1); the UI shows *refused* when that happens. |
| **Authentication results** | SPF / DKIM / DMARC verdicts recorded by your own mail provider. |
| **Impersonation detector** | An offline dictionary of ~2,500 organisations (Fortune/Global 500 companies, banks, insurers, retailers, carriers, government agencies, universities) and the domains they really send from; every domain was checked to resolve in DNS when the table was generated (`web/src/main/resources/brands/*.tsv`, regenerated with `scripts/gen-brands.py`). The sender's display name, address, subject and body signature are scanned for a claimed organisation and compared with the real sending domain; look-alike domains (`paypa1.com`, `arnazon.com`, `secure-paypal-login.net`, `paypal.com.verify.ru`) are caught with homoglyph normalisation and edit distance. A mismatch is shown as **"Claims to be PayPal — domain is not PayPal's"**. |
| **Learned classifier** | A naive-Bayes model that trains on your *Junk* / *Not junk* clicks (seeded with a small built-in corpus, and deliberately weak until you have taught it). |
| **Content heuristics** | Urgency and prize language, link-text/href mismatches, bare-IP links, shorteners, hidden text, dangerous attachment types, Reply-To on another domain, and so on. |
| **Rules, safe and blocked senders** | Deterministic and always win. |

Every verdict is explained: the reading pane shows the **sender's address in large bold type** with
the domain underlined and coloured by verdict, badges for the claimed organisation and the
authentication results, and a *Why?* list with the weight of each signal.

Per-message buttons (toolbar, hover actions, context menu and keyboard):

- **Junk** (`J`) moves the message to Junk and trains the classifier; **Not junk** (`Shift+J`)
  restores it, trains the classifier and guarantees it is never auto-filed again. Both have *Undo*.
- **Analyse & make rule** runs the full analysis and proposes a rule that would catch this message
  and others like it (sender domain or exact address, spoofed display name, distinctive subject
  phrase, scam phrase). Each condition explains why it was chosen. Edit it, *Preview matches*
  against the current folder, then *Save rule* or *Save & apply to current mail* to file the
  matching messages that are already in the folder.

Optionally, a locally running open-source language model served by [Ollama](https://ollama.com)
(*Settings → AI models*) adds a second opinion and extra rule conditions. Nothing is ever sent to
a cloud service; the built-in engine needs no model at all.

### Other features

Reply / reply all / forward with quoting, rich-text compose with attachments (drag-and-drop or
paste), drafts, flags, read/unread, move, archive, delete with undo, server-side search, remote
image blocking with per-sender allow list, sanitised HTML rendering in a sandboxed frame,
attachment download, message headers view, print, unsubscribe, three reading-pane layouts,
compact/comfortable density, and Outlook-style shortcuts (`N`, `R`, `A`, `F`, `Del`, `E`, `J`,
`U`, `S`, `/`, `?`).

### API

The backend exposes a JSON API (`/api/session`, `/api/folders`, `/api/messages`, `/api/message`,
`/api/junk`, `/api/analyze`, `/api/rules/preview`, `/api/rules/apply`, `/api/settings`, …) with a
bearer token from `/api/session`. The original stateless endpoints `/api/inbox` and `/api/send`
are unchanged, so the iOS client keeps working.

## Other platforms

Quillbox started as an Android app; sibling clients live in their own folders. Each is built
by its own GitHub Actions workflow.

| Folder      | Target                          | Stack                                              | Build artifact |
|-------------|---------------------------------|----------------------------------------------------|----------------|
| `app/`      | Android (and **ChromeOS**)      | Kotlin, Jetpack Compose, Room, Hilt, Jakarta Mail  | `.apk`         |
| `desktop/`  | Windows / macOS / Linux / Raspberry Pi OS | Native launcher (Compose Desktop) around the embedded web client | `.msi`/`.dmg`/`.deb` (x64 + arm64) + portable archives |
| `web/`      | Browser (self-hosted)           | Ktor backend (JVM, Jakarta Mail) + Outlook-style web UI, junk engine | server `.zip`  |
| `ios/`      | iPhone / iPad                   | SwiftUI client calling the `web/` backend over REST | `.app` (simulator) |

Notes on the architecture choices:

- **ChromeOS** runs the Android APK directly — no separate build.
- **Desktop** is the web client packaged as a native app: it embeds the same backend and
  Outlook-style UI (compiled straight from `web/`), starts it on `127.0.0.1` and opens it in
  your browser, with a tray icon to reopen or quit. Run locally with `cd desktop && ./gradlew run`;
  package installers with `./gradlew packageDistributionForCurrentOS` (.msi / .dmg / .deb, the
  arm64 .deb being the Raspberry Pi OS package).
- **Web**: browsers cannot open IMAP/SMTP sockets, so `web/` is a small backend that does the
  mail work and serves a browser UI. See the section above.
- **iOS**: Jakarta Mail is JVM-only, so the iPhone app is a thin SwiftUI client that talks to
  the `web/` backend's REST API. Set the server URL on the setup screen. The Xcode project is
  generated from `ios/project.yml` via [XcodeGen](https://github.com/yonaskolb/XcodeGen)
  (`cd ios && xcodegen generate && open Quillbox.xcodeproj`).

## Releases

Pushing a tag such as `v1.1.0` makes GitHub Actions build every target and attach the files
to one GitHub Release (`Releases → Quillbox v1.1.0`):

| Target | Release file |
|---|---|
| Android, ChromeOS | `quillbox-v1.1.0.apk` (signed when the signing secrets exist, else `-unsigned`) |
| Windows | `quillbox-desktop-v1.1.0-windows-x64.msi`, or the portable `.zip` |
| macOS | `quillbox-desktop-v1.1.0-macos-arm64.dmg`, or the portable `.tar.gz` |
| Linux x64 | `quillbox-desktop-v1.1.0-linux-x64.deb`, or the portable `.tar.gz` |
| Raspberry Pi OS (64-bit) / Linux arm64 | `quillbox-desktop-v1.1.0-linux-arm64.deb`, or the portable `.tar.gz` |
| Web / any OS with Java 17+ (servers, Termux, ChromeOS Linux) | `quillbox-web-v1.1.0-any-jvm.zip` → unzip, run `bin/quillbox-web` |
| iOS | `Quillbox-iOS-simulator.app.zip` (unsigned simulator build; a device build needs an Apple Developer account) |

### Making a release from Termux (Android)

Windows, macOS and iOS packages can only be built on those systems, so from a phone the job
is: build and test what the phone can (the web/JVM server), then push a tag and let GitHub
Actions build all eight targets. Everything below is typed into Termux.

One-time setup:

```bash
pkg update && pkg upgrade -y
pkg install -y git openjdk-17 gh unzip
termux-setup-storage                                  # optional: reach ~/storage/downloads
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"
gh auth login                                         # GitHub CLI login (HTTPS + browser/token)
git clone https://github.com/socrtwo/quillbox.git
cd quillbox
mkdir -p ~/.gradle && printf 'org.gradle.jvmargs=-Xmx1536m\norg.gradle.daemon=false\n' >> ~/.gradle/gradle.properties
```

Build and test the web/JVM server on the phone (this is the same zip the release ships):

```bash
cd ~/quillbox/web
chmod +x gradlew
./gradlew build distZip --no-daemon                   # runs the junk-engine tests, writes build/distributions/quillbox-web-1.1.0.zip
cd build/distributions && unzip -o quillbox-web-1.1.0.zip && cd quillbox-web-1.1.0
PORT=8080 ./bin/quillbox-web                          # open http://localhost:8080 in the phone browser
```

Cut the release (all targets are built by GitHub Actions and attached to one GitHub Release):

```bash
cd ~/quillbox
git checkout main && git pull
# bump the version first if needed: app/build.gradle.kts (versionCode/versionName),
#   web/build.gradle.kts (version), desktop/build.gradle.kts (version, packageVersion)
git commit -am "Release v1.1.0"        # only if you changed something
git push
git tag -a v1.1.0 -m "Quillbox 1.1.0"
git push origin v1.1.0                 # <- this triggers the Android, Desktop, Web and iOS release builds

gh run list --limit 8                  # watch the four workflows
gh run watch                           # follow one interactively
gh release view v1.1.0                 # list the attached files when they are done
gh release download v1.1.0 -D ~/storage/downloads/quillbox-v1.1.0
```

If the repository has no signing secrets, the APK arrives unsigned; sign it on the phone:

```bash
pkg install -y apksigner
keytool -genkeypair -v -keystore ~/quillbox-release.jks -alias quillbox -keyalg RSA -keysize 2048 -validity 10000
apksigner sign --ks ~/quillbox-release.jks --ks-key-alias quillbox \
  --out quillbox-v1.1.0.apk ~/storage/downloads/quillbox-v1.1.0/quillbox-v1.1.0-unsigned.apk
```

To have CI sign it instead, add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
and `KEY_PASSWORD` (see *Automated signed builds in CI* above):

```bash
base64 -w 0 ~/quillbox-release.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body quillbox
gh secret set KEY_PASSWORD
```

[docs/RELEASING.md](docs/RELEASING.md) has the longer version, including the unofficial way
to build the APK inside Termux and what a phone cannot build (Windows/macOS installers,
a signed iOS app).

## Security note

Credentials and downloaded mail are stored locally only and are transmitted solely to the
user's own mail servers (on the web/iOS clients, via your own Quillbox backend). Use real
credentials only on your own device; the repository ships with placeholder values only.

The web backend keeps passwords in memory for the life of a session and writes only rules,
settings and analysis results to disk. Put it behind HTTPS (a reverse proxy such as Caddy or
nginx) whenever it is reachable from outside the machine it runs on. The junk engine's only
outbound traffic is DNS: blocklist lookups for sending IPs and domains.
