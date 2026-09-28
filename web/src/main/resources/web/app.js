/* Quillbox web client — Outlook-style three-pane mail with built-in junk protection.
 * Talks to the Ktor backend at the same origin; the session token lives in sessionStorage.
 */
"use strict";

// ============================================================================ utilities
const $ = (id) => document.getElementById(id);
const qs = (sel, root = document) => root.querySelector(sel);
const qsa = (sel, root = document) => Array.from(root.querySelectorAll(sel));

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === "class") node.className = v;
    else if (k === "html") node.innerHTML = v;
    else if (k === "text") node.textContent = v;
    else if (k.startsWith("on") && typeof v === "function") node.addEventListener(k.slice(2), v);
    else if (k === "style" && typeof v === "object") Object.assign(node.style, v);
    else if (k === "dataset") Object.assign(node.dataset, v);
    else node.setAttribute(k, v === true ? "" : v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    node.appendChild(typeof c === "string" ? document.createTextNode(c) : c);
  }
  return node;
}
const icon = (name, size = 16) => {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("width", size); svg.setAttribute("height", size);
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", "#i-" + name);
  svg.appendChild(use);
  return svg;
};
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function fmtDate(ms, { long = false } = {}) {
  if (!ms) return "";
  const d = new Date(ms);
  if (long) return d.toLocaleString(undefined, { weekday: "short", year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  if (sameDay) return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  const diff = (now - d) / 86400000;
  if (diff < 7 && d.getFullYear() === now.getFullYear()) return d.toLocaleDateString(undefined, { weekday: "short" }) + " " + d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  if (d.getFullYear() === now.getFullYear()) return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}
function dayGroup(ms) {
  const d = new Date(ms), now = new Date();
  const start = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const days = Math.floor((start(now) - start(d)) / 86400000);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 7) return "This week";
  if (days < 14) return "Last week";
  if (days < 31) return "This month";
  return "Older";
}
function fmtSize(b) {
  if (b == null || b < 0) return "";
  if (b >= 1048576) return (b / 1048576).toFixed(1) + " MB";
  if (b >= 1024) return Math.round(b / 1024) + " KB";
  return b + " B";
}
function initials(name, address) {
  const src = (name || "").trim() || (address || "").split("@")[0] || "?";
  const parts = src.replace(/[^\p{L}\p{N} ._-]/gu, "").split(/[\s._-]+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return src.slice(0, 2).toUpperCase();
}
function avatarColor(key) {
  let h = 0;
  for (const c of String(key || "")) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const palette = ["#6b69d6", "#0f6cbd", "#c239b3", "#ca5010", "#038387", "#8764b8", "#498205", "#c19c00", "#e3008c", "#4f6bed", "#00b294", "#d13438"];
  return palette[h % palette.length];
}
function debounce(fn, ms) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; }

// ------------------------------------------------------------------ native shells
// The Android app and the iOS app show this very page in a WebView. They identify themselves
// (a JS bridge on Android, a user-agent token on iOS) so that pop-up windows become in-app
// dialogs, printing goes through the platform, and wording fits a phone.
const NATIVE = window.QuillboxAndroid ? "android" : /QuillboxApp\/[\w.]+ \(iOS\)/.test(navigator.userAgent) ? "ios" : null;
const IOS_BRIDGE = (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.quillbox) || null;
function openExternal(url) {
  if (NATIVE === "android") { try { window.QuillboxAndroid.openExternal(url); return; } catch {} }
  if (NATIVE === "ios" && IOS_BRIDGE) { try { IOS_BRIDGE.postMessage({ type: "open", url }); return; } catch {} }
  const w = window.open(url, "_blank", "noopener");
  if (!w) location.href = url;
}
/** Shows a document in a new browser window, or in an in-app dialog where pop-ups are unavailable. */
function popup(title, { html, text }) {
  if (!NATIVE) {
    const w = window.open("", "_blank");
    if (w) { w.document.write(html || `<title>${esc(title)}</title><pre style="white-space:pre-wrap;font-family:monospace">${esc(text || "")}</pre>`); w.document.close(); return; }
  }
  $("popupTitle").textContent = title;
  const body = $("popupBody"); body.innerHTML = "";
  if (html) { const f = el("iframe", { class: "body-frame popup-frame", sandbox: "allow-same-origin", title }); body.appendChild(f); f.srcdoc = html; f.onload = () => { try { f.style.height = Math.min(Math.max(f.contentDocument.documentElement.scrollHeight + 24, 200), 4000) + "px"; } catch {} }; }
  else body.appendChild(el("pre", { class: "body-text mono", text: text || "" }));
  $("popupDialog").showModal();
}
function nativePrint(title) {
  if (NATIVE === "android") { try { window.QuillboxAndroid.print(title || "Quillbox message"); return true; } catch {} }
  if (NATIVE === "ios" && IOS_BRIDGE) { try { IOS_BRIDGE.postMessage({ type: "print", title: title || "Quillbox message" }); return true; } catch {} }
  return false;
}
function splitAddresses(s) { return String(s || "").split(/[,;]/).map((x) => x.trim()).filter(Boolean); }
function domainOf(addr) { const i = (addr || "").lastIndexOf("@"); return i >= 0 ? addr.slice(i + 1).toLowerCase() : ""; }

// ------------------------------------------------------------------ toasts
function toast(message, { action, onAction, danger = false, timeout = 6000 } = {}) {
  const host = $("toasts");
  const t = el("div", { class: "toast" + (danger ? " danger" : "") }, el("span", { text: message }));
  if (action) t.appendChild(el("button", { text: action, onclick: () => { onAction && onAction(); t.remove(); } }));
  t.appendChild(el("button", { html: "&#x2715;", title: "Dismiss", onclick: () => t.remove(), style: { color: "inherit", opacity: ".7" } }));
  host.appendChild(t);
  if (timeout) setTimeout(() => t.remove(), timeout);
  return t;
}

// ============================================================================ state
const prefs = {
  get(k, d) { try { const v = localStorage.getItem("qb." + k); return v === null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem("qb." + k, JSON.stringify(v)); } catch {} },
};
const state = {
  token: null, account: null, settings: null, capabilities: null,
  folders: [], folder: "INBOX", folderRole: "inbox",
  messages: [], total: 0, pending: 0, version: 0, offset: 0, pageSize: 50,
  selected: new Set(), open: null, openDetail: null, filter: "all", search: null,
  loading: false, pollTimer: null, lastActivity: Date.now(), imagesOnce: false, compose: null,
  lastFocusedUid: null, ollamaModels: [],
};

// ============================================================================ API
async function api(path, { method = "GET", body, query, raw = false } = {}) {
  const url = new URL(path, location.origin);
  if (query) for (const [k, v] of Object.entries(query)) if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, v);
  const headers = { Accept: "application/json" };
  if (state.token) headers.Authorization = "Bearer " + state.token;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch(url, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  if (res.status === 401) { signOut(true); throw new Error("Session expired — please sign in again"); }
  if (raw) return res;
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { error: text }; }
  if (!res.ok) throw new Error((data && data.error) || res.statusText || "Request failed");
  return data;
}
function attachmentUrl(folder, uid, { index, cid, inline } = {}) {
  const u = new URL("/api/attachment", location.origin);
  u.searchParams.set("token", state.token); u.searchParams.set("folder", folder); u.searchParams.set("uid", uid);
  if (index !== undefined) u.searchParams.set("index", index);
  if (cid) u.searchParams.set("cid", cid);
  if (inline) u.searchParams.set("inline", "true");
  return u.toString();
}

// ============================================================================ theme & prefs
function applyTheme() {
  let theme = prefs.get("theme", "system");
  const dark = theme === "dark" || (theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  qs("#btnTheme use").setAttribute("href", dark ? "#i-sun" : "#i-moon");
  document.documentElement.dataset.density = prefs.get("density", "comfortable");
  const ws = $("workspace");
  ws.classList.remove("pane-bottom", "pane-off");
  const pane = prefs.get("pane", "right");
  if (pane === "bottom") ws.classList.add("pane-bottom");
  if (pane === "off") ws.classList.add("pane-off");
}
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", applyTheme);

// ============================================================================ setup wizard
const setup = {
  discovered: null, provider: null, catalogue: null, regionFilter: "all", rememberTouched: false,
  show() {
    $("app").classList.add("hidden"); $("setup").classList.remove("hidden");
    $("suOrigin").textContent = NATIVE === "android" ? "the Quillbox app on this phone" : location.host;
    if (NATIVE === "android" && !this.rememberTouched) $("suRemember").checked = true;
    this.step(0);
    this.loadProviders();
  },
  step(n) {
    for (const i of [0, 1, 2]) { $("setupStep" + i).classList.toggle("hidden", n !== i); $("step" + i).classList.toggle("done", i <= n); }
    if (n === 1) setTimeout(() => ($("suName").value ? $("suEmail") : $("suName")).focus(), 50);
  },
  error(msg, second = false) {
    const box = $(second ? "suError2" : "suError");
    box.classList.toggle("hidden", !msg); box.textContent = msg || "";
  },
  // ---- step 0: provider picker
  async loadProviders() {
    if (!this.catalogue) {
      try { this.catalogue = await api("/api/providers"); }
      catch (e) { this.catalogue = { regions: [], providers: [] }; toast("Could not load the provider list: " + e.message, { danger: true }); }
      this.renderRegions();
    }
    this.renderProviders();
  },
  renderRegions() {
    const host = $("suRegionTabs"); host.innerHTML = "";
    const present = new Set(this.catalogue.providers.filter((p) => p.domains.length).map((p) => p.region));
    const tabs = [{ id: "all", label: "All" }, ...this.catalogue.regions.filter((r) => present.has(r.id) && r.id !== "popular")];
    for (const r of tabs) host.appendChild(el("button", { class: r.id === this.regionFilter ? "active" : "", onclick: () => { this.regionFilter = r.id; this.renderRegions(); this.renderProviders(); } }, r.label));
  },
  providerMark(p) { return el("span", { class: "provider-mark", style: { background: avatarColor(p.id) }, text: initials(p.label.replace(/\(.*?\)/g, "")) }); },
  tile(p) {
    const domains = p.domains.slice(0, 3).join(", ") + (p.domains.length > 3 ? ", …" : "");
    return el("button", { class: "provider-tile" + (p.unsupported ? " unsupported" : ""), type: "button", title: p.status || domains, onclick: () => this.choose(p) },
      this.providerMark(p), el("span", { class: "txt" }, el("b", { text: p.label }), el("span", { text: p.status || domains })));
  },
  otherTile() {
    return el("button", { class: "provider-tile other", type: "button", onclick: () => this.chooseOther() },
      el("span", { class: "provider-mark", style: { background: "var(--text-4)" } }, icon("mail", 18)), el("span", { class: "txt" }, el("b", { text: "Other" }), el("span", { text: "Any IMAP or POP3 mailbox — looked up from your address" })));
  },
  renderProviders() {
    const host = $("suProviders"); host.innerHTML = "";
    const q = $("suProviderSearch").value.trim().toLowerCase();
    const all = this.catalogue.providers.filter((p) => p.domains.length);
    const byLabel = (a, b) => a.label.localeCompare(b.label);
    if (q) {
      const hits = all.filter((p) => p.label.toLowerCase().includes(q) || p.id.includes(q) || p.domains.some((d) => d.includes(q)) || p.regionLabel.toLowerCase().includes(q))
        .sort((a, b) => (b.popular > 0) - (a.popular > 0) || (a.popular || 99) - (b.popular || 99) || byLabel(a, b));
      if (!hits.length) host.appendChild(el("div", { class: "provider-empty", text: `No provider matches "${q}". Choose Other and Quillbox will look the settings up from your address.` }));
      hits.forEach((p) => host.appendChild(this.tile(p)));
      host.appendChild(this.otherTile());
      return;
    }
    if (this.regionFilter === "all") {
      host.appendChild(el("div", { class: "section", text: "Most popular" }));
      all.filter((p) => p.popular > 0).sort((a, b) => a.popular - b.popular).forEach((p) => host.appendChild(this.tile(p)));
      for (const r of this.catalogue.regions) {
        if (r.id === "popular") continue;
        const items = all.filter((p) => p.region === r.id).sort(byLabel);
        if (!items.length) continue;
        host.appendChild(el("div", { class: "section", text: r.label }));
        items.forEach((p) => host.appendChild(this.tile(p)));
      }
    } else {
      const region = this.catalogue.regions.find((r) => r.id === this.regionFilter);
      host.appendChild(el("div", { class: "section", text: region ? region.label : "" }));
      all.filter((p) => p.region === this.regionFilter).sort(byLabel).forEach((p) => host.appendChild(this.tile(p)));
    }
    host.appendChild(el("div", { class: "section", text: "Not listed" }));
    host.appendChild(this.otherTile());
  },
  choose(p) {
    this.provider = p;
    $("suChosen").classList.remove("hidden");
    const mark = $("suChosenMark"); mark.style.background = avatarColor(p.id); mark.textContent = initials(p.label.replace(/\(.*?\)/g, ""));
    $("suChosenLabel").textContent = p.label;
    $("suChosenDomains").textContent = p.domains.slice(0, 4).join(", ") + (p.domains.length > 4 ? ", …" : "");
    $("suLead1").textContent = `Enter your ${p.label.replace(/ \(.*?\)/g, "")} address and password.`;
    $("suEmail").placeholder = "you@" + p.domains[0];
    const notes = [];
    if (p.unsupported) notes.push(`<div class="error-box" style="margin:0 0 8px">${esc(p.notes[0] || "This provider offers no IMAP/SMTP access.")}</div>`);
    else {
      if (p.status) notes.push(`<div><b>${esc(p.status)}</b></div>`);
      notes.push(`<div class="small muted">IMAP ${esc(p.incomingHost)}:${p.incomingPort} · SMTP ${esc(p.smtpHost)}:${p.smtpPort}</div>`);
      if (p.oauthOnly) notes.push(`<div class="error-box" style="margin:8px 0 0">This provider may no longer accept password sign-in for IMAP. Try an app password; if it is rejected, the provider requires OAuth, which Quillbox does not support yet.</div>`);
      if (p.notes.length) notes.push(`<ul>${p.notes.map((n) => `<li>${esc(n)}</li>`).join("")}</ul>`);
      if (p.appPasswordUrl) notes.push(`<div style="margin-top:6px"><a href="${esc(p.appPasswordUrl)}" target="_blank" rel="noopener">Create an app password ↗</a></div>`);
    }
    const box = $("suProviderNotes"); box.innerHTML = notes.join(""); box.classList.remove("hidden");
    $("suPasswordHint").textContent = p.appPasswordUrl ? `${p.label.replace(/ \(.*?\)/g, "")} requires an app password rather than your normal password.` : (p.usernameLocalPart ? "Sign in with the part of your address before the @." : "Your mailbox password.");
    this.error(null);
    this.step(1);
  },
  chooseOther() {
    this.provider = null;
    $("suChosen").classList.add("hidden"); $("suProviderNotes").classList.add("hidden");
    $("suLead1").textContent = "Enter your address and password. Quillbox finds the server settings for you, the same way Outlook does.";
    $("suEmail").placeholder = "you@example.com";
    $("suPasswordHint").innerHTML = "Gmail, Yahoo, iCloud, AOL and Fastmail require an <em>app password</em> rather than your normal password.";
    this.error(null);
    this.step(1);
  },
  // ---- step 1: credentials
  async continueFromStep1() {
    const email = $("suEmail").value.trim().toLowerCase();
    const pw = $("suPassword").value;
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return this.error("Enter a valid email address.");
    if (!pw) return this.error("Enter your password (or an app password).");
    if (this.provider && this.provider.unsupported) return this.error(`${this.provider.label} cannot be used with a mail client: ${this.provider.notes[0] || "no IMAP/SMTP access."}`);
    this.error(null);
    const btn = $("suContinue"); btn.disabled = true; btn.innerHTML = '<span class="spinner"></span> ' + (this.provider ? "Preparing settings…" : "Looking up your provider…");
    try {
      const d = this.provider
        ? await api("/api/provider", { query: { id: this.provider.id, email } })
        : await api("/api/autodiscover", { query: { email } });
      this.discovered = d;
      this.fill(d, email);
      if (this.provider && !this.provider.domains.includes(email.split("@")[1]) && !["google-workspace", "microsoft365"].includes(this.provider.id)) {
        this.error(`${email.split("@")[1]} is not one of ${this.provider.label}'s usual domains. The settings below are ${this.provider.label}'s — check them, or go back and choose Other to look the domain up.`, true);
      }
      this.step(2);
    } catch (e) {
      this.error("Could not look up settings: " + e.message);
    } finally { btn.disabled = false; btn.textContent = "Continue"; }
  },
  fill(d, email) {
    $("suProtocol").value = d.protocol || "IMAP";
    $("suIncomingSecurity").value = d.incomingSecurity || "SSL_TLS";
    $("suIncomingHost").value = d.incomingHost || "";
    $("suIncomingPort").value = d.incomingPort || 993;
    $("suSmtpHost").value = d.smtpHost || "";
    $("suSmtpPort").value = d.smtpPort || 587;
    $("suSmtpSecurity").value = d.smtpSecurity || "STARTTLS";
    $("suUsername").value = d.username || email;
    const box = $("suProvider");
    box.classList.remove("hidden");
    const notes = (d.notes || []).map((n) => `<li>${esc(n)}</li>`).join("");
    if (d.found) {
      $("suLead").textContent = "Check the details below, then sign in.";
      box.innerHTML = `<b>${esc(d.provider || d.domain)}</b> — settings from ${esc(d.source)}.<div class="small muted">IMAP ${esc(d.incomingHost)}:${d.incomingPort} · SMTP ${esc(d.smtpHost)}:${d.smtpPort}</div>` +
        (d.oauthOnly ? `<div class="error-box" style="margin:8px 0 0">This provider may no longer accept password sign-in for IMAP. Try an app password; if it is rejected, the provider requires OAuth, which Quillbox does not support yet.</div>` : "") +
        (notes ? `<ul>${notes}</ul>` : "") +
        (d.appPasswordUrl ? `<div style="margin-top:6px"><a href="${esc(d.appPasswordUrl)}" target="_blank" rel="noopener">Create an app password ↗</a></div>` : "");
      $("suAdvanced").open = false;
    } else {
      $("suLead").textContent = "We could not find settings automatically. Enter them below.";
      box.innerHTML = `<b>No automatic settings for ${esc(d.domain)}</b>${notes ? `<ul>${notes}</ul>` : ""}`;
      $("suAdvanced").open = true;
    }
  },
  account() {
    const email = $("suEmail").value.trim().toLowerCase();
    return {
      displayName: $("suName").value.trim() || email,
      email,
      incomingHost: $("suIncomingHost").value.trim(),
      incomingPort: parseInt($("suIncomingPort").value, 10) || 993,
      protocol: $("suProtocol").value,
      incomingSecurity: $("suIncomingSecurity").value,
      smtpHost: $("suSmtpHost").value.trim(),
      smtpPort: parseInt($("suSmtpPort").value, 10) || 587,
      smtpSecurity: $("suSmtpSecurity").value,
      username: $("suUsername").value.trim() || email,
      password: $("suPassword").value,
    };
  },
  async verify() {
    this.error(null, true);
    $("suStatus").innerHTML = '<span class="spinner"></span> Testing incoming and outgoing servers…';
    try {
      const r = await api("/api/verify", { method: "POST", body: { account: this.account() } });
      $("suStatus").innerHTML = `Incoming: <b>${esc(r.incoming)}</b> · SMTP: <b>${esc(r.smtp)}</b>`;
      if (!r.ok) this.error("One of the servers rejected the connection. Check the settings and password.", true);
    } catch (e) { $("suStatus").textContent = ""; this.error(e.message, true); }
  },
  async signIn() {
    this.error(null, true);
    const btn = $("suSignIn"); btn.disabled = true; btn.innerHTML = '<span class="spinner"></span> Signing in…';
    try {
      const account = this.account();
      await session.open(account, $("suRemember").checked);
    } catch (e) {
      this.error(e.message, true);
    } finally { btn.disabled = false; btn.textContent = "Sign in"; }
  },
};

// ============================================================================ session
const session = {
  async open(account, remember) {
    const r = await api("/api/session", { method: "POST", body: { account } });
    state.token = r.token; state.account = account; state.settings = r.settings; state.capabilities = r.capabilities;
    sessionStorage.setItem("qb.token", r.token);
    const store = remember ? localStorage : sessionStorage;
    store.setItem("qb.account", JSON.stringify(account));
    (remember ? sessionStorage : localStorage).removeItem("qb.account");
    await app.start();
  },
  async resume() {
    const token = sessionStorage.getItem("qb.token");
    const saved = localStorage.getItem("qb.account") || sessionStorage.getItem("qb.account");
    if (token) {
      state.token = token;
      try {
        const r = await api("/api/session");
        state.account = saved ? JSON.parse(saved) : { email: r.email, displayName: r.displayName, protocol: r.protocol };
        state.settings = r.settings; state.capabilities = r.capabilities;
        await app.start();
        return true;
      } catch { state.token = null; sessionStorage.removeItem("qb.token"); }
    }
    if (saved) {
      try {
        const account = JSON.parse(saved);
        if (account.password) { await this.open(account, !!localStorage.getItem("qb.account")); return true; }
        $("suEmail").value = account.email || ""; $("suName").value = account.displayName || "";
      } catch {}
    }
    return false;
  },
};
function signOut(expired = false) {
  const token = state.token;
  state.token = null; state.messages = []; state.selected.clear(); state.open = null;
  clearTimeout(state.pollTimer);
  sessionStorage.removeItem("qb.token"); sessionStorage.removeItem("qb.account");
  if (!expired) localStorage.removeItem("qb.account");
  if (token && !expired) fetch("/api/session", { method: "DELETE", headers: { Authorization: "Bearer " + token } }).catch(() => {});
  for (const d of qsa("dialog[open]")) d.close();
  setup.show();
  if (expired) toast("Your session expired. Please sign in again.", { danger: true });
}

// ============================================================================ app shell
const app = {
  async start() {
    $("setup").classList.add("hidden"); $("app").classList.remove("hidden");
    applyTheme();
    const email = state.account.email;
    $("accountLabel").textContent = email;
    const av = $("accountAvatar"); av.textContent = initials(state.account.displayName, email); av.style.background = avatarColor(email);
    state.lastActivity = Date.now();
    renderJunkStatus();
    await folders.load();
    await list.load("INBOX");
    if (!state.capabilities.folders) toast("POP3 account: junk mail is tracked locally because POP3 servers have no folders.", { timeout: 9000 });
  },
};

function renderJunkStatus() {
  const s = state.settings?.spam || {};
  const dot = $("junkDot");
  dot.className = "dot " + (s.enabled ? (state.pending > 0 ? "busy" : "") : "off");
  $("junkStatus").textContent = !s.enabled ? "Junk protection off" : state.pending > 0 ? `Scanning ${state.pending} message${state.pending === 1 ? "" : "s"}…` : (s.autoMoveToJunk ? "Junk protection on · auto-filing" : "Junk protection on · flag only");
}
async function refreshBayesStatus() {
  try {
    const st = await api("/api/spam/status");
    $("bayesStatus").textContent = `Learned from ${st.bayes.spam} junk / ${st.bayes.ham} good · ${st.autoMoved} auto-filed`;
  } catch {}
}

// ============================================================================ folders
const FOLDER_ICONS = { inbox: "inbox", junk: "junk", sent: "send", drafts: "drafts", trash: "trash", archive: "archive", other: "folder" };
const folders = {
  async load() {
    try {
      state.folders = await api("/api/folders");
      this.render();
    } catch (e) { toast("Could not load folders: " + e.message, { danger: true }); }
  },
  render() {
    const ul = $("folderList"); ul.innerHTML = "";
    const special = state.folders.filter((f) => f.role !== "other");
    const others = state.folders.filter((f) => f.role === "other");
    const add = (f) => {
      const li = el("li", { class: (f.path === state.folder ? "active " : "") + f.role, title: f.path, dataset: { path: f.path }, style: { "--depth": f.depth } },
        icon(FOLDER_ICONS[f.role] || "folder", 18), el("span", { class: "name", text: f.name }),
        f.unread > 0 ? el("span", { class: "count", text: String(f.unread) }) : (f.role === "junk" && f.total > 0 ? el("span", { class: "count muted", text: String(f.total) }) : null));
      li.onclick = () => { list.load(f.path); $("workspace").classList.remove("nav-open"); };
      ul.appendChild(li);
    };
    special.forEach(add);
    if (others.length) { ul.appendChild(el("li", { class: "section", text: "Folders", style: { height: "auto", cursor: "default" } })); others.forEach(add); }
  },
  byPath(p) { return state.folders.find((f) => f.path === p); },
  byRole(r) { return state.folders.find((f) => f.role === r); },
};

// ============================================================================ message list
const list = {
  async load(folder, { keepOpen = false, silent = false, rescan = false } = {}) {
    if (folder !== state.folder) { state.selected.clear(); state.offset = 0; state.search = null; $("searchInput").value = ""; $("searchClear").classList.add("hidden"); }
    state.folder = folder;
    const f = folders.byPath(folder);
    state.folderRole = f ? f.role : (folder === "INBOX" ? "inbox" : "other");
    $("folderTitle").textContent = f ? f.name : folder;
    folders.render();
    if (!keepOpen) reading.close();
    await this.fetch({ silent, rescan });
  },
  async fetch({ silent = false, rescan = false, more = false } = {}) {
    if (state.search) return this.search(state.search, silent);
    if (!silent) { state.loading = true; $("listStatus").innerHTML = '<span class="spinner"></span>'; }
    try {
      const limit = more ? state.messages.length + state.pageSize : Math.max(state.pageSize, state.messages.length || state.pageSize);
      const r = await api("/api/messages", { query: { folder: state.folder, limit, offset: 0, rescan: rescan ? "true" : "" } });
      if (r.folder !== state.folder) return;
      const before = new Set(state.messages.map((m) => m.uid));
      state.messages = [...r.messages].sort((a, b) => b.date - a.date); state.total = r.total; state.pending = r.pending; state.version = r.version;
      this.render();
      renderJunkStatus();
      $("analysisBanner").classList.toggle("hidden", !(state.pending > 0));
      $("analysisText").textContent = `Checking ${state.pending} message${state.pending === 1 ? "" : "s"} against blocklists and the impersonation detector…`;
      $("listStatus").textContent = r.total ? `${state.messages.length} of ${r.total}` : "";
      $("btnLoadMore").classList.toggle("hidden", state.messages.length >= r.total);
      if (state.open && !state.messages.some((m) => m.uid === state.open.uid) && before.has(state.open.uid) && state.folder === state.open.folder) {
        // The open message left this folder (moved by a rule or the junk filter).
        reading.close();
      }
      this.schedulePoll();
      await this.checkActivity();
    } catch (e) {
      if (!silent) { $("listStatus").textContent = ""; $("messageList").innerHTML = ""; $("messageList").appendChild(el("div", { class: "empty-list" }, icon("warning", 56), el("div", { text: e.message }))); }
    } finally { state.loading = false; }
  },
  async search(q, silent = false) {
    state.search = q;
    $("folderTitle").textContent = `Search: ${q}`;
    if (!silent) $("listStatus").innerHTML = '<span class="spinner"></span>';
    try {
      const r = await api("/api/search", { query: { folder: state.folder, q, limit: 200 } });
      state.messages = [...r.messages].sort((a, b) => b.date - a.date); state.pending = 0;
      this.render();
      $("analysisBanner").classList.add("hidden");
      $("listStatus").textContent = `${r.messages.length} result${r.messages.length === 1 ? "" : "s"} in ${folders.byPath(state.folder)?.name || state.folder}`;
      $("btnLoadMore").classList.add("hidden");
    } catch (e) { toast("Search failed: " + e.message, { danger: true }); }
  },
  schedulePoll() {
    clearTimeout(state.pollTimer);
    const ms = state.pending > 0 ? 2500 : (prefs.get("autoRefresh", 60) || 60) * 1000;
    state.pollTimer = setTimeout(async () => {
      if (!state.token || document.hidden) { this.schedulePoll(); return; }
      if (qs("dialog[open]") && state.pending === 0) { this.schedulePoll(); return; }
      await this.fetch({ silent: true });
      if (state.pending === 0) folders.load();
    }, ms);
  },
  async checkActivity() {
    try {
      const acts = await api("/api/spam/activity", { query: { since: state.lastActivity } });
      if (acts.length) {
        state.lastActivity = Math.max(...acts.map((a) => a.at));
        const junk = folders.byRole("junk");
        const label = acts.length === 1 ? `Quillbox moved "${acts[0].subject.slice(0, 50)}" to Junk` : `Quillbox moved ${acts.length} messages to Junk`;
        toast(label, { action: "Open Junk", onAction: () => junk && list.load(junk.path), timeout: 9000 });
        folders.load();
      }
    } catch {}
  },
  visible() {
    const f = state.filter;
    return state.messages.filter((m) => {
      if (f === "unread") return !m.read;
      if (f === "flagged") return m.flagged;
      if (f === "suspicious") return m.verdict && m.verdict.level !== "CLEAN";
      return true;
    });
  },
  render() {
    const host = $("messageList");
    host.innerHTML = "";
    const msgs = this.visible();
    const isJunk = state.folderRole === "junk";
    $("cmdJunk").classList.toggle("hidden", isJunk);
    $("cmdNotJunk").classList.toggle("hidden", !isJunk);
    const banner = $("listBanner");
    if (isJunk && !state.search) {
      const auto = state.messages.filter((m) => m.verdict?.autoMoved).length;
      banner.className = "banner"; banner.classList.remove("hidden");
      banner.innerHTML = `<svg width="16" height="16"><use href="#i-junk"/></svg><span class="grow">Messages here were filed as junk${auto ? ` — Quillbox moved ${auto} of them automatically` : ""}. Use <b>Not junk</b> to restore one and teach the filter.</span>`;
    } else banner.classList.add("hidden");
    if (!msgs.length) {
      host.appendChild(el("div", { class: "empty-list" }, icon("empty", 56), el("div", { text: state.messages.length ? "Nothing matches this filter" : (state.loading ? "Loading…" : "This folder is empty") })));
      return;
    }
    let group = null;
    const frag = document.createDocumentFragment();
    for (const m of msgs) {
      if (!state.search) {
        const g = dayGroup(m.date);
        if (g !== group) { group = g; frag.appendChild(el("div", { class: "day-sep", text: g })); }
      }
      frag.appendChild(this.row(m));
    }
    host.appendChild(frag);
    $("listPane").classList.toggle("selecting", state.selected.size > 0);
  },
  row(m) {
    const v = m.verdict;
    const level = v ? v.level : null;
    const div = el("div", {
      class: "msg" + (m.read ? "" : " unread") + (state.selected.has(m.uid) ? " selected" : "") + (state.open && state.open.uid === m.uid && state.open.folder === m.folder ? " open" : "") + (level === "SPAM" ? " spam" : ""),
      dataset: { uid: m.uid, folder: m.folder },
      tabindex: -1,
    });
    const cb = el("input", { type: "checkbox" }); cb.checked = state.selected.has(m.uid);
    cb.onclick = (e) => { e.stopPropagation(); this.toggleSelect(m.uid, e.shiftKey); };
    div.appendChild(el("label", { class: "cb", onclick: (e) => e.stopPropagation() }, cb));
    const av = el("div", { class: "avatar", text: initials(m.fromName, m.fromAddress), style: { background: avatarColor(m.fromAddress || m.fromName) } });
    div.appendChild(av);
    const badges = [];
    if (v) {
      if (v.brand?.mismatch && v.brand.knownBrand) badges.push(el("span", { class: "badge danger", title: v.brand.explanation }, icon("shield-x", 12), `Impersonates ${v.brand.claimedBrand}`));
      else if (level === "SPAM") badges.push(el("span", { class: "badge danger", title: `Junk score ${v.score}/100` }, icon("junk", 12), v.blacklistHits?.length ? "Blocklisted" : "Junk"));
      else if (level === "SUSPICIOUS") badges.push(el("span", { class: "badge warn", title: `Junk score ${v.score}/100` }, icon("warning", 12), "Suspicious"));
      else if (v.brand?.verified && v.brand.knownBrand && v.auth?.dmarc === "pass") badges.push(el("span", { class: "badge ok", title: v.brand.explanation }, icon("shield-ok", 12)));
    }
    const icons = el("span", { class: "msg-icons" });
    if (m.hasAttachments) icons.appendChild(icon("attach", 14));
    if (m.flagged) { const f = icon("flag-fill", 14); f.classList.add("flag"); icons.appendChild(f); }
    if (m.answered) icons.appendChild(icon("reply", 14));
    const main = el("div", { class: "msg-main" },
      el("div", { class: "msg-line" }, el("span", { class: "msg-from", text: m.fromName || m.fromAddress || "(unknown sender)", title: m.fromAddress }), ...badges, el("span", { class: "msg-date", text: fmtDate(m.date) })),
      el("div", { class: "msg-line" }, el("span", { class: "msg-subject", text: m.subject || "(no subject)" }), icons),
      el("div", { class: "msg-line msg-preview-line" }, el("span", { class: "msg-preview", text: m.preview || (state.pending ? "" : "") })));
    div.appendChild(main);
    const hover = el("div", { class: "msg-hover" },
      el("button", { class: "icon-btn", title: "Delete", onclick: (e) => { e.stopPropagation(); actions.delete([m]); } }, icon("trash")),
      state.folderRole === "junk"
        ? el("button", { class: "icon-btn", title: "Not junk", onclick: (e) => { e.stopPropagation(); actions.junk([m], false); } }, icon("shield-ok"))
        : el("button", { class: "icon-btn", title: "Junk", onclick: (e) => { e.stopPropagation(); actions.junk([m], true); } }, icon("junk")),
      el("button", { class: "icon-btn", title: m.flagged ? "Unflag" : "Flag", onclick: (e) => { e.stopPropagation(); actions.flag([m], !m.flagged); } }, icon(m.flagged ? "flag-fill" : "flag")),
      el("button", { class: "icon-btn", title: m.read ? "Mark unread" : "Mark read", onclick: (e) => { e.stopPropagation(); actions.read([m], !m.read); } }, icon(m.read ? "unread" : "read")));
    div.appendChild(hover);
    div.onclick = (e) => {
      if (e.ctrlKey || e.metaKey) { this.toggleSelect(m.uid, false); return; }
      if (e.shiftKey) { this.toggleSelect(m.uid, true); return; }
      reading.open(m);
    };
    div.oncontextmenu = (e) => { e.preventDefault(); contextMenu.show(e, m); };
    return div;
  },
  toggleSelect(uid, range) {
    if (range && state.lastFocusedUid != null) {
      const ids = this.visible().map((m) => m.uid);
      const a = ids.indexOf(state.lastFocusedUid), b = ids.indexOf(uid);
      if (a >= 0 && b >= 0) for (const id of ids.slice(Math.min(a, b), Math.max(a, b) + 1)) state.selected.add(id);
    } else {
      if (state.selected.has(uid)) state.selected.delete(uid); else state.selected.add(uid);
    }
    state.lastFocusedUid = uid;
    this.render();
  },
  selectAll(on) { state.selected.clear(); if (on) this.visible().forEach((m) => state.selected.add(m.uid)); this.render(); },
  targets() {
    if (state.selected.size) return state.messages.filter((m) => state.selected.has(m.uid));
    if (state.open) { const m = state.messages.find((x) => x.uid === state.open.uid && x.folder === state.open.folder); return m ? [m] : [state.open]; }
    return [];
  },
  removeLocal(items) {
    const ids = new Set(items.map((m) => m.uid));
    const idx = state.messages.findIndex((m) => ids.has(m.uid));
    state.messages = state.messages.filter((m) => !ids.has(m.uid));
    for (const id of ids) state.selected.delete(id);
    if (state.open && ids.has(state.open.uid)) {
      const next = state.messages[Math.min(idx, state.messages.length - 1)];
      if (next && prefs.get("openNext", true)) reading.open(next); else reading.close();
    }
    this.render();
  },
  patchLocal(items, patch) {
    const ids = new Set(items.map((m) => m.uid));
    state.messages = state.messages.map((m) => (ids.has(m.uid) ? { ...m, ...patch } : m));
    if (state.open && ids.has(state.open.uid)) Object.assign(state.open, patch);
    this.render();
    if (state.openDetail && ids.has(state.openDetail.uid)) { Object.assign(state.openDetail, patch); reading.renderToolbarState(); }
  },
  moveFocus(delta) {
    const ids = this.visible();
    if (!ids.length) return;
    let i = state.open ? ids.findIndex((m) => m.uid === state.open.uid) : -1;
    i = Math.max(0, Math.min(ids.length - 1, i + delta));
    reading.open(ids[i]);
    const row = qs(`.msg[data-uid="${ids[i].uid}"]`);
    row && row.scrollIntoView({ block: "nearest" });
  },
};

// ============================================================================ actions
const actions = {
  refs(items) { return items.map((m) => ({ folder: m.folder || state.folder, uid: m.uid })); },
  async junk(items, junk, { blockSender = false, safeSender = false } = {}) {
    if (!items.length) return;
    try {
      const r = await api("/api/junk", { method: "POST", body: { items: this.refs(items), junk, blockSender, safeSender } });
      if (state.capabilities.move || !state.capabilities.folders) list.removeLocal(items);
      const n = r.ok;
      const back = (r.moved && r.moved.length) ? r.moved : null;
      toast(junk ? `Moved ${n} message${n === 1 ? "" : "s"} to Junk and learned from ${n === 1 ? "it" : "them"}` : `Restored ${n} message${n === 1 ? "" : "s"} to the Inbox and learned from ${n === 1 ? "it" : "them"}`,
        back ? { action: "Undo", onAction: () => this.junk(back, !junk) } : {});
      if (r.errors?.length) toast(r.errors[0], { danger: true });
      folders.load(); refreshBayesStatus();
    } catch (e) { toast("Junk action failed: " + e.message, { danger: true }); }
  },
  async delete(items, permanent = false) {
    if (!items.length) return;
    if (permanent && !confirm(`Permanently delete ${items.length} message${items.length === 1 ? "" : "s"}? This cannot be undone.`)) return;
    try {
      const r = await api("/api/delete", { method: "POST", body: { items: this.refs(items), permanent } });
      list.removeLocal(items);
      const from = items[0].folder || state.folder;
      toast(`Deleted ${r.ok} message${r.ok === 1 ? "" : "s"}`, r.moved?.length ? { action: "Undo", onAction: () => this.move(r.moved, from) } : {});
      if (r.errors?.length) toast(r.errors[0], { danger: true });
      folders.load();
    } catch (e) { toast("Delete failed: " + e.message, { danger: true }); }
  },
  async move(items, to) {
    if (!items.length || !to) return;
    try {
      const r = await api("/api/move", { method: "POST", body: { items: this.refs(items), to } });
      list.removeLocal(items);
      const from = items[0].folder || state.folder;
      toast(`Moved ${r.ok} message${r.ok === 1 ? "" : "s"} to ${folders.byPath(to)?.name || to}`, r.moved?.length ? { action: "Undo", onAction: () => this.move(r.moved, from) } : {});
      if (r.errors?.length) toast(r.errors[0], { danger: true });
      folders.load();
    } catch (e) { toast("Move failed: " + e.message, { danger: true }); }
  },
  async archive(items) {
    let a = folders.byRole("archive");
    if (!a) { toast("No Archive folder on this account — create one on your mail server first.", { danger: true }); return; }
    return this.move(items, a.path);
  },
  async read(items, read) {
    if (!items.length) return;
    if (!read && state.open && items.some((m) => m.uid === state.open.uid)) clearTimeout(reading.markTimer);
    list.patchLocal(items, { read });
    try { await api("/api/flags", { method: "POST", body: { items: this.refs(items), read } }); folders.load(); }
    catch (e) { toast("Could not update: " + e.message, { danger: true }); }
  },
  async flag(items, flagged) {
    if (!items.length) return;
    list.patchLocal(items, { flagged });
    try { await api("/api/flags", { method: "POST", body: { items: this.refs(items), flagged } }); }
    catch (e) { toast("Could not update: " + e.message, { danger: true }); }
  },
  pickFolder(items) {
    const ul = $("mvList"); ul.innerHTML = "";
    for (const f of state.folders) {
      if (f.path === state.folder) continue;
      const li = el("li", { class: f.role, style: { "--depth": f.depth } }, icon(FOLDER_ICONS[f.role] || "folder", 18), el("span", { class: "name", text: f.name }));
      li.onclick = () => { $("moveDialog").close(); if (f.role === "junk") this.junk(items, true); else this.move(items, f.path); };
      ul.appendChild(li);
    }
    $("moveDialog").showModal();
  },
  async blockSender(m) {
    const addr = m.fromAddress;
    if (!addr) return;
    await this.junk([m], true, { blockSender: true });
    toast(`${addr} added to blocked senders`);
  },
  async trustSender(m) {
    const addr = m.fromAddress; if (!addr) return;
    const s = state.settings;
    if (!s.spam.safeSenders.includes(addr)) await settingsSave({ ...s, spam: { ...s.spam, safeSenders: [...s.spam.safeSenders, addr], blockedSenders: s.spam.blockedSenders.filter((x) => x !== addr) } });
    toast(`${addr} added to safe senders`);
    if (state.openDetail && state.openDetail.uid === m.uid) reading.open(m, { force: true });
  },
};

// ============================================================================ reading pane
const reading = {
  markTimer: null,
  close() {
    state.open = null; state.openDetail = null;
    $("reading").classList.add("hidden"); $("readingEmpty").classList.remove("hidden");
    $("workspace").classList.remove("show-reading");
    qsa(".msg.open").forEach((r) => r.classList.remove("open"));
  },
  async open(m, { force = false } = {}) {
    if (!force && state.open && state.open.uid === m.uid && state.open.folder === m.folder && state.openDetail) return;
    state.open = { uid: m.uid, folder: m.folder || state.folder };
    state.lastFocusedUid = m.uid; state.imagesOnce = false;
    qsa(".msg.open").forEach((r) => r.classList.remove("open"));
    qs(`.msg[data-uid="${m.uid}"]`)?.classList.add("open");
    $("readingEmpty").classList.add("hidden"); $("reading").classList.remove("hidden");
    $("workspace").classList.add("show-reading");
    $("rdSubject").textContent = m.subject || "(no subject)";
    $("rdVerdict").innerHTML = ""; $("rdAttachments").innerHTML = ""; $("rdBadges").innerHTML = "";
    $("rdFrame").classList.add("hidden"); $("rdText").classList.remove("hidden"); $("rdText").textContent = "Loading…";
    $("rdImagesBanner").classList.add("hidden");
    this.renderSender(m, null);
    try {
      const d = await api("/api/message", { query: { folder: state.open.folder, uid: m.uid } });
      if (!state.open || state.open.uid !== m.uid) return;
      state.openDetail = d;
      this.render(d);
      if (!d.read) {
        clearTimeout(this.markTimer);
        const delay = prefs.get("markReadDelay", 1);
        if (delay >= 0) this.markTimer = setTimeout(() => { if (state.open && state.open.uid === d.uid) actions.read([{ uid: d.uid, folder: state.open.folder }], true); }, delay * 1000);
      }
    } catch (e) { $("rdText").textContent = "Could not load this message: " + e.message; }
  },
  renderSender(m, d) {
    const v = (d && d.verdict) || m.verdict;
    const name = m.fromName || "", addr = m.fromAddress || "";
    $("rdAvatar").textContent = initials(name, addr); $("rdAvatar").style.background = avatarColor(addr || name);
    $("rdFromName").textContent = name || addr.split("@")[0];
    const a = $("rdFromAddress");
    a.innerHTML = "";
    const at = addr.lastIndexOf("@");
    if (at > 0) { a.appendChild(document.createTextNode(addr.slice(0, at + 1))); a.appendChild(el("wbr")); a.appendChild(el("span", { class: "domain", text: addr.slice(at + 1) })); }
    else a.textContent = addr || "(no address)";
    a.className = "sender-address";
    if (v) {
      if (v.level === "SPAM" || (v.brand?.mismatch && v.brand.knownBrand)) a.classList.add("danger");
      else if (v.level === "SUSPICIOUS" || v.brand?.mismatch) a.classList.add("warn");
      else if (v.brand?.verified && v.brand.knownBrand) a.classList.add("ok");
    }
    a.title = addr;
    $("rdDate").textContent = fmtDate(m.date, { long: true });
    const meta = $("rdMeta"); meta.innerHTML = "";
    const to = d ? d.to : m.to;
    if (to && to.length) meta.appendChild(el("span", {}, el("b", { text: "To: " }), to.join(", ")));
    if (d && d.cc && d.cc.length) meta.appendChild(el("span", {}, el("b", { text: "Cc: " }), d.cc.join(", ")));
    if (d && d.replyTo) meta.appendChild(el("span", { style: { color: "var(--warn)" } }, el("b", { text: "Replies go to: " }), d.replyTo));
    const badges = $("rdBadges"); badges.innerHTML = "";
    if (v) {
      const b = v.brand || {};
      if (b.mismatch && b.knownBrand) badges.appendChild(el("span", { class: "badge danger", title: b.explanation }, icon("shield-x", 12), `Claims to be ${b.claimedBrand} — domain is not ${b.claimedBrand}'s`));
      else if (b.mismatch) badges.appendChild(el("span", { class: "badge warn", title: b.explanation }, icon("warning", 12), `"${b.claimedBrand}" does not match ${b.senderDomain}`));
      else if (b.verified && b.knownBrand) badges.appendChild(el("span", { class: "badge ok", title: b.explanation }, icon("shield-ok", 12), `Genuine ${b.claimedBrand} domain`));
      if (b.lookalike) badges.appendChild(el("span", { class: "badge danger" }, "Look-alike domain"));
      if (b.freemailSender && b.claimedBrand) badges.appendChild(el("span", { class: "badge warn" }, "Sent from free webmail"));
      const au = v.auth || {};
      const authBadge = (label, val) => val && badges.appendChild(el("span", { class: "badge " + (val === "pass" ? "ok" : val === "fail" ? "danger" : "") , title: `${label} result reported by your mail provider` }, `${label} ${val}`));
      authBadge("DMARC", au.dmarc); authBadge("SPF", au.spf); authBadge("DKIM", au.dkim);
      for (const h of v.blacklistHits || []) badges.appendChild(el("span", { class: "badge danger", title: h.note }, icon("block", 12), `${h.list}: ${h.subject}`));
      if (v.safeSender) badges.appendChild(el("span", { class: "badge ok" }, "Safe sender"));
      if (v.trained === "ham") badges.appendChild(el("span", { class: "badge ok" }, "You marked: not junk"));
      if (v.trained === "spam") badges.appendChild(el("span", { class: "badge danger" }, "You marked: junk"));
      if (v.matchedRule) badges.appendChild(el("span", { class: "badge info", title: "A rule matched this message" }, icon("rules", 12), `Rule: ${v.matchedRule}`));
    }
  },
  render(d) {
    const m = { ...d };
    this.renderSender(m, d);
    this.renderToolbarState();
    this.renderVerdict(d);
    // attachments
    const atts = $("rdAttachments"); atts.innerHTML = "";
    for (const a of d.attachments.filter((x) => !x.inline)) {
      atts.appendChild(el("a", { class: "att", href: attachmentUrl(state.open.folder, d.uid, { index: a.index }), download: a.fileName, title: a.mimeType },
        icon("attach"), el("span", { text: a.fileName }), el("span", { class: "size", text: fmtSize(a.size) })));
    }
    // body
    const allowImages = state.imagesOnce || (state.settings.imageSenders || []).includes((d.fromAddress || "").toLowerCase()) || (d.verdict?.brand?.verified && d.verdict.level === "CLEAN" && prefs.get("imagesVerified", false));
    if (d.bodyHtml) {
      const { html, blockedImages } = sanitizeHtml(d.bodyHtml, { allowImages, folder: state.open.folder, uid: d.uid });
      $("rdText").classList.add("hidden");
      const frame = $("rdFrame"); frame.classList.remove("hidden");
      frame.style.height = "200px";
      frame.srcdoc = html;
      frame.onload = () => { this.fitFrame(); setTimeout(() => this.fitFrame(), 400); setTimeout(() => this.fitFrame(), 1500); };
      $("rdImagesBanner").classList.toggle("hidden", !(blockedImages > 0));
    } else {
      $("rdFrame").classList.add("hidden"); $("rdFrame").srcdoc = "";
      const pre = $("rdText"); pre.classList.remove("hidden");
      pre.innerHTML = "";
      linkify(d.bodyText || "(no text content)", pre);
      $("rdImagesBanner").classList.add("hidden");
    }
    $("readingScroll").scrollTop = 0;
  },
  fitFrame() {
    const f = $("rdFrame");
    try { const h = f.contentDocument?.documentElement?.scrollHeight || f.contentDocument?.body?.scrollHeight; if (h) f.style.height = Math.min(Math.max(h + 24, 120), 20000) + "px"; } catch {}
  },
  renderToolbarState() {
    const d = state.openDetail; if (!d) return;
    const isJunk = state.folderRole === "junk";
    $("rdJunk").classList.toggle("hidden", isJunk);
    $("rdNotJunk").classList.toggle("hidden", !isJunk);
    qs("#rdFlag use").setAttribute("href", d.flagged ? "#i-flag-fill" : "#i-flag");
    $("rdFlag").classList.toggle("active", !!d.flagged);
  },
  renderVerdict(d) {
    const host = $("rdVerdict"); host.innerHTML = "";
    const v = d.verdict; if (!v) return;
    const b = v.brand || {};
    let cls, title, iconName;
    if (v.level === "SPAM") { cls = "danger"; iconName = "shield-x"; title = b.mismatch && b.knownBrand ? `This message pretends to be from ${b.claimedBrand} — it is not.` : v.blockedSender ? "From a sender you blocked." : v.matchedRule ? `Filed by your rule "${v.matchedRule}".` : "This message looks like junk or a scam."; }
    else if (v.level === "SUSPICIOUS") { cls = "warn"; iconName = "warning"; title = b.mismatch ? "The sender's name does not match its domain — be careful." : "Some things about this message look suspicious."; }
    else if (v.safeSender) { cls = "ok"; iconName = "shield-ok"; title = "From a safe sender."; }
    else if (b.verified && b.knownBrand) { cls = "ok"; iconName = "shield-ok"; title = `Sent from a genuine ${b.claimedBrand} domain${v.auth?.dmarc === "pass" ? " and authenticated (DMARC pass)" : ""}.`; }
    else if (state.folderRole === "junk") { cls = "info"; iconName = "info"; title = "This message is in Junk but currently scores as clean."; }
    else return this.renderVerdictDetailsOnly(host, v);
    const body = el("div", { class: "v-body" }, el("div", { class: "v-title", text: `${title} ` }, el("span", { class: "small", style: { fontWeight: 400 }, text: `Junk score ${v.score}/100` })));
    const expl = b.explanation && (b.mismatch || b.verified) ? b.explanation : (v.reasons[0] ? `${v.reasons[0].title}: ${v.reasons[0].detail}` : "");
    if (expl) body.appendChild(el("div", { text: expl }));
    if (v.autoMoved) body.appendChild(el("div", { class: "small", text: `Quillbox moved this message to ${folders.byPath(v.autoMovedTo)?.name || v.autoMovedTo || "Junk"} automatically.` }));
    const act = el("div", { class: "v-actions" });
    if (state.folderRole !== "junk" && v.level !== "CLEAN") act.appendChild(el("button", { class: "btn", onclick: () => actions.junk([state.openDetail], true) }, icon("junk"), "Move to Junk"));
    if (state.folderRole === "junk" || (v.level !== "CLEAN")) act.appendChild(el("button", { class: "btn", onclick: () => state.folderRole === "junk" ? actions.junk([state.openDetail], false) : actions.trustSender(state.openDetail) }, icon("shield-ok"), state.folderRole === "junk" ? "Not junk" : "It's safe — trust sender"));
    if (v.level !== "CLEAN" && !v.blockedSender) act.appendChild(el("button", { class: "btn", onclick: () => actions.blockSender(state.openDetail) }, icon("block"), "Block sender"));
    act.appendChild(el("button", { class: "btn", onclick: () => analysis.open(state.openDetail) }, icon("sparkle"), "Analyse & make rule"));
    body.appendChild(act);
    body.appendChild(this.detailsBlock(v));
    host.appendChild(el("div", { class: "verdict " + cls }, icon(iconName, 22), body));
  },
  renderVerdictDetailsOnly(host, v) {
    const det = this.detailsBlock(v, "Junk check: clean (score " + v.score + "/100)");
    det.style.margin = "0 0 12px"; det.classList.add("small");
    host.appendChild(det);
  },
  detailsBlock(v, summary = "Why?") {
    const det = el("details", {}, el("summary", { text: summary }));
    const ul = el("ul", { class: "reason-list" });
    for (const r of v.reasons) {
      const w = r.weight;
      ul.appendChild(el("li", {}, el("span", { class: "w " + (w > 0 ? "pos" : w < 0 ? "neg" : "zero"), text: (w > 0 ? "+" : "") + w }), el("span", {}, el("span", { class: "t", text: r.title }), r.detail ? el("span", { class: "d", text: " — " + r.detail }) : null)));
    }
    if (!v.reasons.length) ul.appendChild(el("li", {}, el("span", { class: "w zero", text: "0" }), el("span", { class: "d", text: "Nothing suspicious was found." })));
    det.appendChild(ul);
    const kv = el("dl", { class: "kv" });
    const add = (k, val) => { if (val) { kv.appendChild(el("dt", { text: k })); kv.appendChild(el("dd", { text: val })); } };
    add("Sender domain", v.senderDomain);
    if (v.brand?.claimedBrand) add("Claims to be", `${v.brand.claimedBrand}${v.brand.claimSource ? ` (from ${v.brand.claimSource})` : ""}`);
    if (v.brand?.legitimateDomains?.length) add("Real domains", v.brand.legitimateDomains.slice(0, 6).join(", "));
    add("Sending servers", (v.originatingIps || []).join(", "));
    add("Link domains", (v.linkDomains || []).slice(0, 8).join(", "));
    const bl = (v.blacklistStatus || []).map((s) => `${s.list}: ${s.refused ? "refused" : s.error ? "error" : s.hits ? `${s.hits} hit` : s.queried ? "clear" : "n/a"}`).join(" · ");
    add("Blocklists", bl);
    if (v.bayesProbability != null) add("Learned classifier", Math.round(v.bayesProbability * 100) + "% spam-like");
    add("Checked", v.analyzedAt ? new Date(v.analyzedAt).toLocaleString() : "");
    det.appendChild(kv);
    return det;
  },
};

function linkify(text, host) {
  const re = /(https?:\/\/[^\s<>"']+)/g;
  let last = 0, m;
  while ((m = re.exec(text))) {
    host.appendChild(document.createTextNode(text.slice(last, m.index)));
    host.appendChild(el("a", { href: m[1], target: "_blank", rel: "noopener noreferrer", text: m[1], title: "Opens " + m[1] }));
    last = m.index + m[1].length;
  }
  host.appendChild(document.createTextNode(text.slice(last)));
}

/** Sanitises email HTML for display in the sandboxed frame. Scripts, forms, external CSS and
 *  (optionally) remote images are removed; cid: images are routed through the attachment API. */
function sanitizeHtml(html, { allowImages, folder, uid }) {
  const doc = new DOMParser().parseFromString(html, "text/html");
  let blockedImages = 0;
  doc.querySelectorAll("script, iframe, object, embed, link, meta, base, form, input, button, textarea, select, applet, frame, frameset, noscript, svg").forEach((n) => n.remove());
  const transparent = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
  for (const n of doc.querySelectorAll("*")) {
    for (const a of Array.from(n.attributes)) {
      const name = a.name.toLowerCase(), val = a.value.trim();
      if (name.startsWith("on") || name === "srcdoc") { n.removeAttribute(a.name); continue; }
      if ((name === "href" || name === "src" || name === "action" || name === "formaction" || name === "xlink:href" || name === "background" || name === "poster") && /^\s*(javascript|vbscript|data:text\/html)/i.test(val)) { n.removeAttribute(a.name); continue; }
      if (name === "style" && /expression\s*\(|javascript:/i.test(val)) n.removeAttribute("style");
      if (name === "style" && !allowImages && /url\s*\(/i.test(val)) { n.setAttribute("style", val.replace(/url\s*\([^)]*\)/gi, "none")); blockedImages++; }
    }
    const tag = n.tagName.toLowerCase();
    if (tag === "img" || tag === "image" || tag === "source" || tag === "video" || tag === "audio") {
      const src = n.getAttribute("src") || "";
      if (/^cid:/i.test(src)) { n.setAttribute("src", attachmentUrl(folder, uid, { cid: src.slice(4).replace(/^<|>$/g, "") })); }
      else if (/^data:image\//i.test(src)) { /* inline data image: fine */ }
      else if (src && !allowImages) { n.setAttribute("data-blocked-src", src); n.setAttribute("src", transparent); n.setAttribute("title", "Remote image blocked"); blockedImages++; }
      n.removeAttribute("srcset"); n.removeAttribute("loading");
      if (tag !== "img") n.remove();
    }
    if (tag === "a") { n.setAttribute("target", "_blank"); n.setAttribute("rel", "noopener noreferrer"); const href = n.getAttribute("href") || ""; if (href) n.setAttribute("title", "Opens " + href); }
  }
  if (!allowImages) for (const s of doc.querySelectorAll("style")) { if (/url\s*\(/i.test(s.textContent)) { s.textContent = s.textContent.replace(/url\s*\([^)]*\)/gi, "none"); blockedImages++; } s.textContent = s.textContent.replace(/@import[^;]+;/gi, ""); }
  const csp = `default-src 'none'; img-src data: 'self'${allowImages ? " https: http:" : ""}; style-src 'unsafe-inline'; font-src 'none'; media-src 'none'; frame-src 'none'; form-action 'none'`;
  const head = `<meta http-equiv="Content-Security-Policy" content="${csp}"><meta charset="utf-8"><base target="_blank"><style>html,body{margin:0;padding:8px;font-family:"Segoe UI",system-ui,sans-serif;font-size:14px;line-height:1.45;color:#242424;background:#fff;word-wrap:break-word;overflow-wrap:anywhere}img{max-width:100%;height:auto}table{max-width:100%}pre{white-space:pre-wrap}blockquote{border-left:3px solid #ccc;margin:8px 0;padding-left:10px;color:#555}</style>`;
  const body = doc.body ? doc.body.innerHTML : "";
  return { html: `<!doctype html><html><head>${head}</head><body>${body}</body></html>`, blockedImages };
}

// ============================================================================ compose
const compose = {
  open({ mode = "new", detail = null, draft = null } = {}) {
    state.compose = { mode, ref: detail ? { folder: state.open?.folder || detail.folder, uid: detail.uid } : null, attachments: [], draftUid: draft ? draft.uid : null, inReplyTo: null, references: null };
    $("cTo").value = ""; $("cCc").value = ""; $("cBcc").value = ""; $("cSubject").value = ""; $("cEditor").innerHTML = ""; $("cAtts").innerHTML = ""; $("cStatus").textContent = "";
    $("cCcRow").classList.add("hidden"); $("cBccRow").classList.add("hidden");
    const sig = state.settings?.signature ? `<br><br><div class="qb-signature">${state.settings.signature.replace(/\n/g, "<br>")}</div>` : "";
    const me = (state.account.email || "").toLowerCase();
    if (mode === "reply" || mode === "replyAll") {
      const replyTo = detail.replyTo || detail.fromAddress;
      $("cTo").value = replyTo;
      if (mode === "replyAll") {
        const cc = [...(detail.to || []), ...(detail.cc || [])].filter((a) => a.toLowerCase() !== me && a.toLowerCase() !== replyTo.toLowerCase());
        if (cc.length) { $("cCc").value = cc.join(", "); $("cCcRow").classList.remove("hidden"); }
      }
      $("cSubject").value = /^\s*re:/i.test(detail.subject) ? detail.subject : "Re: " + detail.subject;
      state.compose.inReplyTo = detail.messageId || null;
      state.compose.references = [detail.references, detail.messageId].filter(Boolean).join(" ") || null;
      $("cEditor").innerHTML = `<div><br></div>${sig}<div class="qb-quote"><br>On ${esc(fmtDate(detail.date, { long: true }))}, ${esc(detail.fromName || "")} &lt;${esc(detail.fromAddress)}&gt; wrote:<blockquote>${quoted(detail)}</blockquote></div>`;
      $("cTitle").textContent = mode === "replyAll" ? "Reply all" : "Reply";
    } else if (mode === "forward") {
      $("cSubject").value = /^\s*fwd?:/i.test(detail.subject) ? detail.subject : "Fwd: " + detail.subject;
      $("cEditor").innerHTML = `<div><br></div>${sig}<div class="qb-quote"><br>---------- Forwarded message ----------<br><b>From:</b> ${esc(detail.fromName || "")} &lt;${esc(detail.fromAddress)}&gt;<br><b>Date:</b> ${esc(fmtDate(detail.date, { long: true }))}<br><b>Subject:</b> ${esc(detail.subject)}<br><b>To:</b> ${esc((detail.to || []).join(", "))}<br><br>${quoted(detail)}</div>`;
      $("cTitle").textContent = "Forward";
      if (detail.attachments?.some((a) => !a.inline)) $("cStatus").textContent = "Note: attachments of the original are not forwarded automatically — re-attach them if needed.";
    } else if (mode === "draft" && draft) {
      $("cTo").value = (draft.to || []).join(", "); $("cSubject").value = draft.subject;
      if (draft.cc?.length) { $("cCc").value = draft.cc.join(", "); $("cCcRow").classList.remove("hidden"); }
      $("cEditor").innerHTML = draft.bodyHtml ? sanitizeHtml(draft.bodyHtml, { allowImages: true, folder: state.folder, uid: draft.uid }).html.replace(/^[\s\S]*<body>|<\/body>[\s\S]*$/g, "") : esc(draft.bodyText).replace(/\n/g, "<br>");
      state.compose.inReplyTo = draft.inReplyTo; state.compose.references = draft.references;
      $("cTitle").textContent = "Draft";
    } else {
      $("cEditor").innerHTML = `<div><br></div>${sig}`;
      $("cTitle").textContent = "New message";
    }
    $("composeDialog").showModal();
    (mode === "new" || mode === "forward" ? $("cTo") : $("cEditor")).focus();
    if (mode !== "new" && mode !== "forward") placeCaretAtStart($("cEditor"));
  },
  close() { $("composeDialog").close(); state.compose = null; },
  payload() {
    const editor = $("cEditor");
    const html = `<!doctype html><html><body style="font-family:Segoe UI,system-ui,sans-serif;font-size:14px">${editor.innerHTML}</body></html>`;
    const text = htmlToPlain(editor);
    return {
      to: splitAddresses($("cTo").value), cc: splitAddresses($("cCc").value), bcc: splitAddresses($("cBcc").value),
      subject: $("cSubject").value.trim(), body: text, html, attachments: state.compose.attachments,
      inReplyTo: state.compose.inReplyTo, references: state.compose.references,
      replyTo: (state.compose.mode === "reply" || state.compose.mode === "replyAll") ? state.compose.ref : null,
      draftUid: state.compose.draftUid,
    };
  },
  async send() {
    const p = this.payload();
    if (!p.to.length && !p.cc.length && !p.bcc.length) { toast("Add at least one recipient.", { danger: true }); $("cTo").focus(); return; }
    const bad = [...p.to, ...p.cc, ...p.bcc].find((a) => !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(a));
    if (bad) { toast(`"${bad}" is not a valid address.`, { danger: true }); return; }
    if (!p.subject && !confirm("Send without a subject?")) return;
    $("cSend").disabled = true; $("cStatus").innerHTML = '<span class="spinner"></span> Sending…';
    try {
      await api("/api/send", { method: "POST", body: p });
      const wasReply = !!p.replyTo;
      this.close();
      toast("Message sent");
      if (wasReply) list.patchLocal([{ uid: p.replyTo.uid }], { answered: true });
      folders.load();
    } catch (e) { $("cStatus").textContent = ""; toast("Send failed: " + e.message, { danger: true, timeout: 10000 }); }
    finally { $("cSend").disabled = false; }
  },
  async saveDraft() {
    if (!state.capabilities.drafts) { toast("Drafts need an IMAP account.", { danger: true }); return; }
    const p = this.payload();
    $("cStatus").innerHTML = '<span class="spinner"></span> Saving draft…';
    try {
      const r = await api("/api/draft", { method: "POST", body: { uid: state.compose.draftUid, to: p.to, cc: p.cc, bcc: p.bcc, subject: p.subject, body: p.body, html: p.html, inReplyTo: p.inReplyTo, references: p.references } });
      state.compose.draftUid = r.uid > 0 ? r.uid : null;
      $("cStatus").textContent = "Draft saved " + new Date().toLocaleTimeString();
      folders.load();
    } catch (e) { $("cStatus").textContent = ""; toast("Could not save draft: " + e.message, { danger: true }); }
  },
  async addFiles(files) {
    for (const f of files) {
      if (f.size > 25 * 1048576) { toast(`${f.name} is larger than 25 MB.`, { danger: true }); continue; }
      const base64 = await new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(String(r.result).split(",")[1]); r.onerror = rej; r.readAsDataURL(f); });
      state.compose.attachments.push({ fileName: f.name, mimeType: f.type || "application/octet-stream", base64 });
    }
    this.renderAtts();
  },
  renderAtts() {
    const host = $("cAtts"); host.innerHTML = "";
    state.compose.attachments.forEach((a, i) => host.appendChild(el("span", { class: "att" }, icon("attach"), a.fileName, el("button", { title: "Remove", html: "&#x2715;", onclick: () => { state.compose.attachments.splice(i, 1); this.renderAtts(); } }))));
  },
};
function quoted(detail) {
  if (detail.bodyHtml) return sanitizeHtml(detail.bodyHtml, { allowImages: true, folder: state.open?.folder || detail.folder, uid: detail.uid }).html.replace(/^[\s\S]*<body>|<\/body>[\s\S]*$/g, "");
  return esc(detail.bodyText || "").replace(/\n/g, "<br>");
}
function htmlToPlain(node) {
  const clone = node.cloneNode(true);
  clone.querySelectorAll("br").forEach((b) => b.replaceWith("\n"));
  clone.querySelectorAll("div, p, li, tr, h1, h2, h3, h4, blockquote").forEach((b) => { b.prepend("\n"); });
  clone.querySelectorAll("blockquote").forEach((b) => { b.textContent = b.textContent.split("\n").map((l) => "> " + l).join("\n"); });
  clone.querySelectorAll("a[href]").forEach((a) => { if (a.href && a.textContent.trim() && a.textContent.trim() !== a.href) a.append(` <${a.href}>`); });
  return clone.textContent.replace(/\n{3,}/g, "\n\n").trim();
}
function placeCaretAtStart(node) {
  const range = document.createRange(); range.setStart(node, 0); range.collapse(true);
  const sel = getSelection(); sel.removeAllRanges(); sel.addRange(range);
}

// ============================================================================ rule editor (shared)
const FIELDS = [["SENDER", "Sender address"], ["SENDER_NAME", "Sender name"], ["SENDER_DOMAIN", "Sender domain"], ["SUBJECT", "Subject"], ["BODY", "Message body"], ["RECIPIENT", "Recipient"], ["ATTACHMENT_NAME", "Attachment name"]];
const OPERATORS = [["CONTAINS", "contains"], ["NOT_CONTAINS", "does not contain"], ["EQUALS", "is exactly"], ["STARTS_WITH", "starts with"], ["ENDS_WITH", "ends with"], ["MATCHES_REGEX", "matches regex"]];
const ACTIONS = [["MOVE_TO_FOLDER", "Move to folder"], ["DELETE", "Delete"], ["MARK_READ", "Mark as read"], ["FLAG", "Flag"], ["MARK_SAFE", "Never treat as junk"]];
const select = (options, value, attrs = {}) => { const s = el("select", attrs); for (const [v, label] of options) s.appendChild(el("option", { value: v, text: label })); s.value = value; return s; };

function describeCriterion(c) {
  const f = (FIELDS.find((x) => x[0] === c.field) || [c.field, c.field])[1];
  const o = (OPERATORS.find((x) => x[0] === c.operator) || [c.operator, c.operator])[1];
  return `${f} ${o} "${c.value}"`;
}
function describeAction(r) {
  if (r.action === "MOVE_TO_FOLDER") return `move to ${r.targetFolder || "Junk"}`;
  return (ACTIONS.find((x) => x[0] === r.action) || ["", r.action])[1].toLowerCase();
}

/** Renders an editable rule form into [host]; returns { read() } producing a RuleDto. */
function ruleForm(host, rule, { rationales = {} } = {}) {
  host.innerHTML = "";
  const name = el("input", { type: "text", value: rule.name || "" });
  const logic = select([["OR", "any condition matches (OR)"], ["AND", "all conditions match (AND)"]], rule.logic || "OR");
  const action = select(ACTIONS, rule.action || "MOVE_TO_FOLDER");
  const folderOpts = [["Junk", "Junk Email"], ...state.folders.filter((f) => f.role !== "junk" && f.role !== "inbox").map((f) => [f.path, f.name])];
  if (rule.targetFolder && !folderOpts.some((o) => o[0] === rule.targetFolder)) folderOpts.push([rule.targetFolder, rule.targetFolder]);
  const target = select(folderOpts, rule.targetFolder || "Junk");
  const targetWrap = el("div", { class: "field" }, el("label", { text: "Destination folder" }), target);
  action.onchange = () => targetWrap.classList.toggle("hidden", action.value !== "MOVE_TO_FOLDER");
  targetWrap.classList.toggle("hidden", (rule.action || "MOVE_TO_FOLDER") !== "MOVE_TO_FOLDER");
  const crit = el("div", { class: "criteria" });
  const rows = [];
  const addRow = (c, why) => {
    const field = select(FIELDS, c.field || "SENDER"), op = select(OPERATORS, c.operator || "CONTAINS"), val = el("input", { type: "text", value: c.value || "", placeholder: "text to match" });
    const row = el("div", { class: "crit" }, field, op, val, el("button", { class: "icon-btn", title: "Remove condition", onclick: () => { row.remove(); rows.splice(rows.indexOf(entry), 1); } }, icon("close")));
    if (why) row.appendChild(el("div", { class: "why", html: `<b>Why:</b> ${esc(why)}` }));
    const entry = { field, op, val };
    rows.push(entry); crit.appendChild(row);
  };
  (rule.criteria || []).forEach((c, i) => addRow(c, rationales[i]));
  if (!rows.length) addRow({ field: "SENDER_DOMAIN", operator: "ENDS_WITH", value: "" });
  host.append(
    el("div", { class: "field" }, el("label", { text: "Rule name" }), name),
    el("div", { class: "field" }, el("label", { text: "Apply when" }), logic),
    el("div", { class: "field" }, el("label", { text: "Conditions" }), crit, el("div", {}, el("button", { class: "btn sm", onclick: () => addRow({}) }, icon("plus"), "Add condition"))),
    el("div", { class: "grid-2" }, el("div", { class: "field" }, el("label", { text: "Action" }), action), targetWrap)
  );
  return {
    read() {
      return {
        id: rule.id || ("rule-" + Math.random().toString(36).slice(2, 10)),
        name: name.value.trim() || "Untitled rule", enabled: rule.enabled !== false, logic: logic.value,
        criteria: rows.map((r) => ({ field: r.field.value, operator: r.op.value, value: r.val.value.trim() })).filter((c) => c.value),
        action: action.value, targetFolder: action.value === "MOVE_TO_FOLDER" ? target.value : null,
        priority: rule.priority ?? 100, createdBy: rule.createdBy || "user", createdAt: rule.createdAt || Date.now(), note: rule.note || "", hitCount: rule.hitCount || 0,
      };
    },
  };
}

async function settingsSave(next) {
  state.settings = await api("/api/settings", { method: "PUT", body: next });
  renderJunkStatus();
  return state.settings;
}

// ============================================================================ analysis dialog
const analysis = {
  form: null, result: null, message: null,
  async open(detail) {
    this.message = detail;
    const body = $("anBody"), foot = $("anFoot");
    body.innerHTML = ""; foot.innerHTML = "";
    body.appendChild(el("div", { style: { padding: "30px", textAlign: "center" } }, el("span", { class: "spinner" }), el("div", { class: "muted", style: { marginTop: "10px" }, text: state.settings.ollama?.enabled ? `Analysing headers, links and content, then asking the local ${state.settings.ollama.model} model…` : "Analysing headers, sending servers, links and content…" })));
    $("analysisDialog").showModal();
    try {
      const r = await api("/api/analyze", { method: "POST", body: { folder: state.open?.folder || detail.folder, uid: detail.uid } });
      this.result = r; this.render(r);
    } catch (e) { body.innerHTML = `<div class="error-box">${esc(e.message)}</div>`; }
  },
  render(r) {
    const v = r.verdict, b = v.brand || {}, p = r.proposal;
    const body = $("anBody"), foot = $("anFoot");
    body.innerHTML = "";
    const cls = v.level === "SPAM" ? "danger" : v.level === "SUSPICIOUS" ? "warn" : "ok";
    const left = el("div", {});
    const verdictCard = el("div", { class: "card" }, el("h4", { text: "Verdict" }),
      el("div", { class: "score " + cls }, el("div", { class: "num", text: String(v.score) }), el("div", { class: "bar" }, el("i", { style: { width: v.score + "%" } })), el("div", { style: { fontWeight: 700 }, text: v.level === "SPAM" ? "Junk" : v.level === "SUSPICIOUS" ? "Suspicious" : "Looks legitimate" })),
      el("div", { text: p.verdictSummary }));
    const who = el("div", { class: "card", style: { marginTop: "12px" } }, el("h4", { text: "Who is it really from?" }),
      el("dl", { class: "kv", style: { marginTop: 0 } },
        el("dt", { text: "Claims to be" }), el("dd", { text: b.claimedBrand ? `${b.claimedBrand}${b.claimSource ? ` (seen in the ${b.claimSource})` : ""}` : "No organisation claim detected" }),
        el("dt", { text: "Actually sent from" }), el("dd", { style: { fontWeight: 700, color: b.mismatch ? "var(--danger)" : b.verified ? "var(--ok)" : "inherit" }, text: b.senderDomain || "(unknown)" }),
        ...(b.legitimateDomains?.length ? [el("dt", { text: `${b.claimedBrand} really uses` }), el("dd", { text: b.legitimateDomains.slice(0, 5).join(", ") })] : []),
        el("dt", { text: "Assessment" }), el("dd", { text: b.explanation || "—" })));
    left.append(verdictCard, who);
    if (r.llm) {
      const l = r.llm;
      left.appendChild(el("div", { class: "card", style: { marginTop: "12px" } }, el("h4", { text: `Local AI model (${l.model})` }),
        l.error ? el("div", { class: "muted", text: "Unavailable: " + l.error }) :
          el("div", {}, el("div", {}, el("b", { text: l.isSpam === true ? "Spam / scam" : l.isSpam === false ? "Legitimate" : "Unsure" }), l.confidence != null ? el("span", { class: "muted", text: ` · ${Math.round(l.confidence * 100)}% confident` }) : null),
            l.claimedOrganisation ? el("div", { text: `Claims to be: ${l.claimedOrganisation}` }) : null, el("div", { class: "small", text: l.summary }))));
    }
    const det = reading.detailsBlock(v, "All signals considered"); det.open = false;
    left.appendChild(el("div", { class: "card", style: { marginTop: "12px" } }, det));
    const right = el("div", {});
    right.appendChild(el("div", { class: "card" }, el("h4", { text: "Proposed rule" }), el("p", { class: "small muted", style: { margin: "0 0 10px" }, text: p.summary }), el("div", { id: "anRuleForm" })));
    const preview = el("div", { id: "anPreview", class: "small muted", style: { marginTop: "10px" } });
    right.appendChild(preview);
    body.appendChild(el("div", { class: "analysis-grid" }, left, right));
    this.form = ruleForm(qs("#anRuleForm"), p.rule, { rationales: Object.fromEntries(p.criteria.map((c, i) => [i, c.rationale])) });
    const alsoThis = el("label", { class: "check", style: { margin: 0 } }, el("input", { type: "checkbox", checked: v.level !== "CLEAN" && state.folderRole !== "junk" }), el("span", { text: "Also move this message to Junk now" }));
    foot.append(
      el("span", { class: "grow" }, alsoThis),
      el("button", { class: "btn", onclick: () => this.preview() }, icon("eye"), "Preview matches"),
      el("button", { class: "btn", onclick: () => this.save(false, alsoThis.querySelector("input").checked) }, "Save rule"),
      el("button", { class: "btn primary", onclick: () => this.save(true, alsoThis.querySelector("input").checked) }, icon("check"), "Save & apply to current mail")
    );
  },
  async preview() {
    const rule = this.form.read();
    if (!rule.criteria.length) return toast("Add at least one condition.", { danger: true });
    const host = qs("#anPreview"); host.innerHTML = '<span class="spinner"></span> Checking the current folder…';
    try {
      const r = await api("/api/rules/preview", { method: "POST", body: { rule, folder: state.folder, limit: 300 } });
      host.innerHTML = "";
      host.appendChild(el("div", {}, el("b", { text: `${r.matches.length} of the ${r.scanned} most recent messages in ${folders.byPath(state.folder)?.name || state.folder} match.` }), r.matches.length === r.scanned && r.scanned > 5 ? el("div", { class: "error-box", style: { marginTop: "6px" }, text: "This rule matches everything — it is probably too broad." }) : null));
      if (r.matches.length) {
        const pl = el("div", { class: "preview-list" });
        r.matches.slice(0, 50).forEach((m) => pl.appendChild(el("div", { class: "row" }, el("span", { text: `${m.fromName || m.fromAddress}` , title: m.fromAddress }), el("span", { text: m.subject }))));
        host.appendChild(pl);
      }
    } catch (e) { host.textContent = "Preview failed: " + e.message; }
  },
  async save(apply, alsoThis) {
    const rule = this.form.read();
    if (!rule.criteria.length) return toast("Add at least one condition.", { danger: true });
    try {
      const s = state.settings;
      await settingsSave({ ...s, rules: [...s.rules.filter((r) => r.id !== rule.id), rule] });
      let msg = `Rule "${rule.name}" saved`;
      if (apply) {
        const r = await api("/api/rules/apply", { method: "POST", body: { rule, folder: state.folder, limit: 300 } });
        msg += ` · applied to ${r.ok} message${r.ok === 1 ? "" : "s"}`;
        if (r.errors?.length) toast(r.errors[0], { danger: true });
      }
      if (alsoThis && this.message && state.folderRole !== "junk") await actions.junk([this.message], true);
      $("analysisDialog").close();
      toast(msg, { action: "Manage rules", onAction: () => settingsUI.open("rules") });
      await list.fetch({ silent: true }); folders.load();
    } catch (e) { toast("Could not save: " + e.message, { danger: true }); }
  },
};

// ============================================================================ settings
const settingsUI = {
  tab: "general",
  open(tab = "general") { this.tab = tab; this.render(); $("settingsDialog").showModal(); },
  render() {
    qsa("#stNav button").forEach((b) => b.classList.toggle("active", b.dataset.tab === this.tab));
    const host = $("stBody"); host.innerHTML = "";
    this["render_" + this.tab](host);
  },
  row(title, desc, control) { return el("div", { class: "setting-row" }, el("div", { class: "txt" }, el("b", { text: title }), desc ? el("span", { text: desc }) : null), control); },
  toggle(checked, onchange) { const i = el("input", { type: "checkbox" }); i.checked = !!checked; i.onchange = () => onchange(i.checked); return el("label", { class: "switch" }, i, el("span")); },
  async patchSpam(patch) { const s = state.settings; await settingsSave({ ...s, spam: { ...s.spam, ...patch } }); },

  render_general(host) {
    host.append(el("h3", { text: "General" }), el("p", { text: "Appearance and reading behaviour. These are stored in this browser." }));
    const theme = select([["system", "Use system setting"], ["light", "Light"], ["dark", "Dark"]], prefs.get("theme", "system"), { style: { width: "220px" } });
    theme.onchange = () => { prefs.set("theme", theme.value); applyTheme(); };
    host.appendChild(this.row("Theme", "", theme));
    const density = select([["comfortable", "Comfortable (3 lines)"], ["compact", "Compact (2 lines)"]], prefs.get("density", "comfortable"), { style: { width: "220px" } });
    density.onchange = () => { prefs.set("density", density.value); applyTheme(); };
    host.appendChild(this.row("Message list density", "", density));
    const pane = select([["right", "Right of the list"], ["bottom", "Below the list"], ["off", "Off (open messages full-width)"]], prefs.get("pane", "right"), { style: { width: "220px" } });
    pane.onchange = () => { prefs.set("pane", pane.value); applyTheme(); };
    host.appendChild(this.row("Reading pane", "", pane));
    const mark = select([["0", "Immediately"], ["1", "After 1 second"], ["3", "After 3 seconds"], ["-1", "Never (mark manually)"]], String(prefs.get("markReadDelay", 1)), { style: { width: "220px" } });
    mark.onchange = () => prefs.set("markReadDelay", parseInt(mark.value, 10));
    host.appendChild(this.row("Mark as read when opened", "", mark));
    host.appendChild(this.row("Open the next message after delete / junk", "", this.toggle(prefs.get("openNext", true), (v) => prefs.set("openNext", v))));
    const refresh = select([["30", "Every 30 seconds"], ["60", "Every minute"], ["300", "Every 5 minutes"], ["0", "Manual only"]], String(prefs.get("autoRefresh", 60)), { style: { width: "220px" } });
    refresh.onchange = () => { prefs.set("autoRefresh", parseInt(refresh.value, 10) || 0); list.schedulePoll(); };
    host.appendChild(this.row("Check for new mail", "", refresh));
    host.appendChild(this.row("Show remote images from verified brands", "Load images automatically when the sender is a genuine, authenticated brand domain.", this.toggle(prefs.get("imagesVerified", false), (v) => prefs.set("imagesVerified", v))));
    host.appendChild(el("h4", { text: "Signature" }));
    const sig = el("textarea", { rows: 4, placeholder: "Added to new messages" }); sig.value = state.settings.signature || "";
    const saveSig = el("button", { class: "btn sm", onclick: async () => { await settingsSave({ ...state.settings, signature: sig.value }); toast("Signature saved"); } }, "Save signature");
    host.append(sig, el("div", { style: { marginTop: "8px" } }, saveSig));
  },

  render_junk(host) {
    const s = state.settings.spam;
    host.append(el("h3", { text: "Junk email protection" }), el("p", { text: "New mail is checked against open DNS blocklists, sender authentication results, the impersonation detector, your rules and a classifier that learns from your Junk / Not junk decisions. Nothing leaves your server except plain DNS queries." }));
    host.appendChild(this.row("Junk protection", "Analyse incoming mail and show verdicts.", this.toggle(s.enabled, (v) => this.patchSpam({ enabled: v }))));
    host.appendChild(this.row("Move junk to the Junk folder automatically", "When off, junk is only flagged in the Inbox.", this.toggle(s.autoMoveToJunk, (v) => this.patchSpam({ autoMoveToJunk: v }))));
    host.appendChild(this.row("Scan mail already in the Inbox", "On first sign-in, existing messages are analysed and filed too (otherwise only new arrivals are).", this.toggle(s.scanExisting, (v) => this.patchSpam({ scanExisting: v }))));
    host.appendChild(this.row("A confirmed blocklist hit is enough to call it junk", "Strong lists only (Spamhaus, SpamCop, Barracuda, or the sender's own domain on a domain list).", this.toggle(s.blacklistHitIsSpam, (v) => this.patchSpam({ blacklistHitIsSpam: v }))));
    host.appendChild(this.row("Impersonating a known brand is enough to call it junk", "e.g. \"PayPal\" in the sender name but sent from another domain.", this.toggle(s.brandMismatchIsSpam, (v) => this.patchSpam({ brandMismatchIsSpam: v }))));
    host.appendChild(this.row("Brand / organisation impersonation detection", "", this.toggle(s.brandDetection, (v) => this.patchSpam({ brandDetection: v }))));
    host.appendChild(this.row("Learned (Bayesian) classifier", "", this.toggle(s.useBayes, (v) => this.patchSpam({ useBayes: v }))));
    host.appendChild(this.row("Check link domains against domain blocklists", "", this.toggle(s.checkLinkDomains, (v) => this.patchSpam({ checkLinkDomains: v }))));
    host.appendChild(el("h4", { text: "Thresholds" }));
    const slider = (label, key, min, max) => {
      const r = el("input", { type: "range", min, max, value: s[key] }); const out = el("output", { text: s[key] });
      r.oninput = () => (out.textContent = r.value); r.onchange = () => this.patchSpam({ [key]: parseInt(r.value, 10) });
      return el("div", { class: "setting-row" }, el("div", { class: "txt", style: { flex: "0 0 200px" } }, el("b", { text: label })), el("div", { class: "range-row", style: { flex: 1 } }, r, out));
    };
    host.append(slider("Junk at score ≥", "spamThreshold", 30, 100), slider("Suspicious at score ≥", "suspiciousThreshold", 5, 80));
    host.appendChild(el("h4", { text: "DNS blocklists (free, open, no API key)" }));
    const table = el("table", { class: "table" }, el("tr", {}, el("th", { text: "On" }), el("th", { text: "List" }), el("th", { text: "Zone" }), el("th", { text: "Type" }), el("th", { text: "Weight" }), el("th")));
    s.blacklists.forEach((b, i) => {
      const on = el("input", { type: "checkbox" }); on.checked = b.enabled;
      on.onchange = () => { const bl = s.blacklists.map((x, j) => (j === i ? { ...x, enabled: on.checked } : x)); this.patchSpam({ blacklists: bl }); };
      const w = el("input", { type: "number", min: 5, max: 100, value: b.weight });
      w.onchange = () => { const bl = s.blacklists.map((x, j) => (j === i ? { ...x, weight: parseInt(w.value, 10) || 30 } : x)); this.patchSpam({ blacklists: bl }); };
      table.appendChild(el("tr", {}, el("td", {}, on), el("td", {}, b.homepage ? el("a", { href: b.homepage, target: "_blank", rel: "noopener", text: b.label }) : el("span", { text: b.label })), el("td", { class: "mono", text: b.zone }), el("td", { text: b.kind === "ip" ? "sending IP" : "domain / link" }), el("td", {}, w),
        el("td", {}, b.builtIn ? null : el("button", { class: "icon-btn", title: "Remove", onclick: () => this.patchSpam({ blacklists: s.blacklists.filter((_, j) => j !== i) }).then(() => this.render()) }, icon("close")))));
    });
    host.appendChild(table);
    const addLabel = el("input", { type: "text", placeholder: "Name" }), addZone = el("input", { type: "text", placeholder: "zone, e.g. bl.example.org" }), addKind = select([["ip", "sending IP"], ["domain", "domain / link"]], "ip");
    host.appendChild(el("div", { class: "list-editor" }, el("div", { class: "add" }, addLabel, addZone, addKind, el("button", { class: "btn sm", onclick: () => {
      if (!addZone.value.trim()) return;
      const id = "custom-" + addZone.value.trim().toLowerCase().replace(/[^a-z0-9]/g, "-");
      this.patchSpam({ blacklists: [...s.blacklists, { id, label: addLabel.value.trim() || addZone.value.trim(), zone: addZone.value.trim().toLowerCase(), kind: addKind.value, weight: 30, enabled: true, builtIn: false, homepage: "" }] }).then(() => this.render());
    } }, icon("plus"), "Add list"))));
    host.appendChild(el("p", { class: "small muted", text: "Spamhaus refuses queries that arrive through open public resolvers (Google 8.8.8.8, Cloudflare 1.1.1.1). If its status shows \"refused\", point the server at your ISP's or router's resolver, or run a local one such as Unbound." }));
    host.appendChild(el("h4", { text: "Learned classifier" }));
    const stats = el("div", { class: "small muted", text: "Loading…" });
    api("/api/spam/status").then((st) => { stats.textContent = `Trained on ${st.bayes.spam} junk and ${st.bayes.ham} legitimate messages (${st.bayes.vocabulary} tokens). ${st.autoMoved} messages have been filed automatically; ${st.cached} analysed.`; }).catch(() => {});
    host.appendChild(stats);
    host.appendChild(el("p", { class: "small muted", text: "Teach it with the Junk / Not junk buttons: each click trains the model on that message. Restoring a message from Junk also prevents it from ever being auto-filed again." }));
  },

  render_rules(host) {
    const s = state.settings;
    host.append(el("h3", { text: "Rules" }), el("p", { text: "Rules run on every new message before the junk score is computed; the first matching rule wins. Rules proposed by the analyser are marked AI." }));
    host.appendChild(el("div", { class: "btn-row", style: { marginBottom: "12px" } }, el("button", { class: "btn primary", onclick: () => this.editRule(null) }, icon("plus"), "New rule")));
    const rules = [...s.rules].sort((a, b) => a.priority - b.priority || a.createdAt - b.createdAt);
    if (!rules.length) host.appendChild(el("p", { class: "muted", text: "No rules yet." }));
    for (const r of rules) {
      const card = el("div", { class: "rule-card" },
        this.toggle(r.enabled, async (v) => { await settingsSave({ ...state.settings, rules: state.settings.rules.map((x) => (x.id === r.id ? { ...x, enabled: v } : x)) }); }),
        el("div", { class: "txt" }, el("div", {}, el("b", { text: r.name }), " ", r.createdBy === "ai" ? el("span", { class: "badge info" }, icon("sparkle", 12), "AI") : null, r.hitCount ? el("span", { class: "badge", style: { marginLeft: "6px" }, text: `${r.hitCount} hit${r.hitCount === 1 ? "" : "s"}` }) : null),
          el("div", { class: "crit-summary", text: r.criteria.map(describeCriterion).join(r.logic === "AND" ? "  AND  " : "  OR  ") }),
          el("div", { class: "action", text: "→ " + describeAction(r) + (r.note ? `  ·  ${r.note}` : "") })),
        el("button", { class: "btn sm", onclick: () => this.editRule(r) }, "Edit"),
        el("button", { class: "btn sm", title: "Apply to the current folder now", onclick: async () => { const x = await api("/api/rules/apply", { method: "POST", body: { rule: r, folder: state.folder, limit: 300 } }); toast(`Applied to ${x.ok} message${x.ok === 1 ? "" : "s"} in ${folders.byPath(state.folder)?.name || state.folder}`); list.fetch({ silent: true }); folders.load(); } }, "Run now"),
        el("button", { class: "icon-btn", title: "Delete rule", onclick: async () => { if (!confirm(`Delete rule "${r.name}"?`)) return; await settingsSave({ ...state.settings, rules: state.settings.rules.filter((x) => x.id !== r.id) }); this.render(); } }, icon("trash")));
      host.appendChild(card);
    }
  },
  editRule(rule) {
    const isNew = !rule;
    $("ruleTitle").textContent = isNew ? "New rule" : "Edit rule";
    const form = ruleForm($("ruleBody"), rule || { name: "", criteria: [], action: "MOVE_TO_FOLDER", targetFolder: "Junk" });
    const foot = $("ruleFoot"); foot.innerHTML = "";
    const prio = el("input", { type: "number", value: rule?.priority ?? 100, title: "Priority: lower runs first", style: { width: "80px" } });
    foot.append(el("span", { class: "grow small muted" }, "Priority ", prio),
      el("button", { class: "btn", onclick: () => $("ruleDialog").close() }, "Cancel"),
      el("button", { class: "btn primary", onclick: async () => {
        const r = form.read(); r.priority = parseInt(prio.value, 10) || 100;
        if (!r.criteria.length) return toast("Add at least one condition.", { danger: true });
        await settingsSave({ ...state.settings, rules: [...state.settings.rules.filter((x) => x.id !== r.id), r] });
        $("ruleDialog").close(); this.render(); toast("Rule saved");
      } }, "Save"));
    $("ruleDialog").showModal();
  },

  render_senders(host) {
    const s = state.settings.spam;
    host.append(el("h3", { text: "Safe and blocked senders" }), el("p", { text: "Enter full addresses or bare domains (sub-domains match). Safe senders are never treated as junk; blocked senders always are." }));
    const editor = (title, key) => {
      host.appendChild(el("h4", { text: title }));
      const box = el("div", { class: "list-editor" });
      const items = state.settings.spam[key];
      if (!items.length) box.appendChild(el("div", { class: "small muted", text: "None" }));
      items.forEach((v) => box.appendChild(el("div", { class: "item" }, el("span", { text: v }), el("button", { class: "icon-btn", title: "Remove", onclick: () => this.patchSpam({ [key]: items.filter((x) => x !== v) }).then(() => this.render()) }, icon("close")))));
      const input = el("input", { type: "text", placeholder: "name@example.com or example.com" });
      const add = async () => { const v = input.value.trim().toLowerCase(); if (!v) return; await this.patchSpam({ [key]: [...new Set([...items, v])] }); this.render(); };
      input.onkeydown = (e) => { if (e.key === "Enter") add(); };
      box.appendChild(el("div", { class: "add" }, input, el("button", { class: "btn sm", onclick: add }, icon("plus"), "Add")));
      host.appendChild(box);
    };
    editor("Safe senders", "safeSenders");
    editor("Blocked senders", "blockedSenders");
    host.appendChild(el("h4", { text: "Senders allowed to show remote images" }));
    const imgs = state.settings.imageSenders || [];
    const ib = el("div", { class: "list-editor" });
    if (!imgs.length) ib.appendChild(el("div", { class: "small muted", text: "None" }));
    imgs.forEach((v) => ib.appendChild(el("div", { class: "item" }, el("span", { text: v }), el("button", { class: "icon-btn", onclick: async () => { await settingsSave({ ...state.settings, imageSenders: imgs.filter((x) => x !== v) }); this.render(); } }, icon("close")))));
    host.appendChild(ib);
  },

  render_ai(host) {
    const o = state.settings.ollama || {};
    host.append(el("h3", { text: "AI models" }), el("p", { text: "The built-in analyser needs no setup: an offline organisation dictionary, look-alike domain detection, header forensics and a Bayesian classifier that learns from you. Optionally, a local open-source language model served by Ollama can add a second opinion and extra rule conditions. Nothing is sent to any cloud service." }));
    host.appendChild(el("div", { class: "card" }, el("h4", { text: "Built-in engine" }), el("div", { class: "small", text: "Always on. Runs on this server, no API keys, no network calls other than DNS blocklist lookups." })));
    host.appendChild(el("h4", { text: "Local language model (Ollama)" }));
    const url = el("input", { type: "url", value: o.url || "http://localhost:11434" });
    const model = el("input", { type: "text", value: o.model || "llama3.2", list: "ollamaModels" });
    const dl = el("datalist", { id: "ollamaModels" }); state.ollamaModels.forEach((m) => dl.appendChild(el("option", { value: m })));
    const enabled = this.toggle(o.enabled, async (v) => { await settingsSave({ ...state.settings, ollama: { enabled: v, url: url.value.trim(), model: model.value.trim() } }); });
    host.appendChild(this.row("Use a local Ollama model", "Adds a model assessment to \"Analyse & make rule\". Slower, but catches subtler scams.", enabled));
    host.appendChild(el("div", { class: "grid-2" }, el("div", { class: "field" }, el("label", { text: "Ollama URL" }), url), el("div", { class: "field" }, el("label", { text: "Model" }), model, dl)));
    const status = el("span", { class: "small muted" });
    host.appendChild(el("div", { class: "btn-row" },
      el("button", { class: "btn", onclick: async () => {
        status.innerHTML = '<span class="spinner"></span>';
        try { const r = await api("/api/ollama/test", { method: "POST", body: { enabled: true, url: url.value.trim(), model: model.value.trim() } }); state.ollamaModels = r.models || []; dl.innerHTML = ""; state.ollamaModels.forEach((m) => dl.appendChild(el("option", { value: m }))); status.textContent = r.ok ? `Connected. Models: ${r.models.join(", ") || "none installed — run: ollama pull llama3.2"}` : "Not reachable: " + r.error; }
        catch (e) { status.textContent = "Not reachable: " + e.message; }
      } }, "Test connection"),
      el("button", { class: "btn primary", onclick: async () => { await settingsSave({ ...state.settings, ollama: { enabled: state.settings.ollama?.enabled || false, url: url.value.trim(), model: model.value.trim() } }); toast("Saved"); } }, "Save"), status));
    host.appendChild(el("p", { class: "small muted", html: "Install from <a href='https://ollama.com' target='_blank' rel='noopener'>ollama.com</a>, then run <code>ollama pull llama3.2</code> (or any other model) on the machine that hosts Quillbox." }));
  },

  render_account(host) {
    const a = state.account;
    host.append(el("h3", { text: "Account" }), el("p", { text: a.email }));
    const kv = el("dl", { class: "kv" });
    const add = (k, v) => { kv.appendChild(el("dt", { text: k })); kv.appendChild(el("dd", { text: v || "—" })); };
    add("Name", a.displayName); add("Incoming", a.incomingHost ? `${a.protocol} ${a.incomingHost}:${a.incomingPort} (${a.incomingSecurity})` : a.protocol); add("Outgoing", a.smtpHost ? `SMTP ${a.smtpHost}:${a.smtpPort} (${a.smtpSecurity})` : ""); add("User name", a.username);
    add("Remembered on this device", localStorage.getItem("qb.account") ? "Yes (including password)" : "No");
    if (NATIVE === "ios") add("Quillbox server", location.origin);
    if (NATIVE === "android") add("Mail engine", "Running inside the Quillbox app on this phone (127.0.0.1)");
    host.appendChild(kv);
    host.appendChild(el("div", { class: "btn-row", style: { marginTop: "16px" } },
      el("button", { class: "btn", onclick: () => { localStorage.removeItem("qb.account"); toast("This device will ask for the password next time."); this.render(); } }, "Forget password on this device"),
      NATIVE === "ios" && IOS_BRIDGE ? el("button", { class: "btn", onclick: () => IOS_BRIDGE.postMessage({ type: "changeServer" }) }, icon("settings"), "Change server…") : null,
      el("button", { class: "btn danger", onclick: () => signOut(false) }, icon("signout"), "Sign out")));
  },
};

// ============================================================================ context menu
const contextMenu = {
  show(e, m) {
    const menu = $("ctxMenu"); menu.innerHTML = "";
    const items = state.selected.has(m.uid) ? list.targets() : [m];
    const isJunk = state.folderRole === "junk";
    const add = (label, ic, fn, cls = "") => menu.appendChild(el("button", { class: cls, onclick: () => { this.hide(); fn(); } }, icon(ic), label));
    add("Open", "mail", () => reading.open(m));
    add("Reply", "reply", async () => { await reading.open(m); compose.open({ mode: "reply", detail: state.openDetail }); });
    add("Forward", "forward", async () => { await reading.open(m); compose.open({ mode: "forward", detail: state.openDetail }); });
    menu.appendChild(el("div", { class: "sep" }));
    add(m.read ? "Mark as unread" : "Mark as read", m.read ? "unread" : "read", () => actions.read(items, !m.read));
    add(m.flagged ? "Unflag" : "Flag", "flag", () => actions.flag(items, !m.flagged));
    add("Move to…", "move", () => actions.pickFolder(items));
    add("Archive", "archive", () => actions.archive(items));
    menu.appendChild(el("div", { class: "sep" }));
    if (isJunk) add("Not junk", "shield-ok", () => actions.junk(items, false)); else add("Junk", "junk", () => actions.junk(items, true));
    add("Block sender", "block", () => actions.blockSender(m), "danger");
    add("Analyse & make rule", "sparkle", async () => { await reading.open(m); analysis.open(state.openDetail); });
    menu.appendChild(el("div", { class: "sep" }));
    add("Delete", "trash", () => actions.delete(items), "danger");
    menu.classList.remove("hidden");
    const x = Math.min(e.clientX, innerWidth - 220), y = Math.min(e.clientY, innerHeight - menu.offsetHeight - 10);
    menu.style.left = x + "px"; menu.style.top = y + "px";
  },
  hide() { $("ctxMenu").classList.add("hidden"); },
};
document.addEventListener("click", (e) => { if (!e.target.closest("#ctxMenu")) contextMenu.hide(); });
document.addEventListener("keydown", (e) => { if (e.key === "Escape") contextMenu.hide(); });

// ============================================================================ keyboard
document.addEventListener("keydown", (e) => {
  if (!state.token) return;
  const target = e.target;
  const typing = target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.isContentEditable);
  if ($("composeDialog").open && (e.ctrlKey || e.metaKey) && e.key === "Enter") { e.preventDefault(); compose.send(); return; }
  if (typing || qs("dialog[open]")) { if (e.key === "Escape" && target === $("searchInput")) { target.value = ""; state.search = null; list.fetch(); } return; }
  const t = list.targets();
  const k = e.key;
  if (k === "/" ) { e.preventDefault(); $("searchInput").focus(); return; }
  if (k === "?") { $("shortcutsDialog").showModal(); return; }
  if (k === "n" || k === "N") { compose.open({ mode: "new" }); return; }
  if (k === "ArrowDown" || k === "j" && e.ctrlKey) { e.preventDefault(); list.moveFocus(1); return; }
  if (k === "ArrowUp") { e.preventDefault(); list.moveFocus(-1); return; }
  if (k === "Escape") { if (state.selected.size) list.selectAll(false); else reading.close(); return; }
  if (!t.length) return;
  if (k === "Delete" || k === "Backspace") { e.preventDefault(); actions.delete(t); return; }
  if (k === "e" || k === "E") { actions.archive(t); return; }
  if (k === "J") { actions.junk(t, false); return; }
  if (k === "j") { actions.junk(t, state.folderRole !== "junk"); return; }
  if (k === "u" || k === "U") { actions.read(t, false); return; }
  if (k === "s" || k === "S") { actions.flag(t, !t[0].flagged); return; }
  if (k === "x" || k === "X") { if (state.open) list.toggleSelect(state.open.uid, false); return; }
  if (!state.openDetail) return;
  if (k === "r") { compose.open({ mode: "reply", detail: state.openDetail }); return; }
  if (k === "a") { compose.open({ mode: "replyAll", detail: state.openDetail }); return; }
  if (k === "f") { compose.open({ mode: "forward", detail: state.openDetail }); return; }
});

// ============================================================================ wiring
function wire() {
  $("suContinue").onclick = () => setup.continueFromStep1();
  $("suPassword").onkeydown = (e) => { if (e.key === "Enter") setup.continueFromStep1(); };
  $("suEmail").onkeydown = (e) => { if (e.key === "Enter") $("suPassword").focus(); };
  $("suBack").onclick = () => setup.step(1);
  $("suBack1").onclick = () => setup.step(0);
  $("suChangeProvider").onclick = () => setup.step(0);
  $("suProviderSearch").addEventListener("input", () => setup.catalogue && setup.renderProviders());
  $("suProviderSearch").addEventListener("keydown", (e) => { if (e.key === "Enter") { const first = qs("#suProviders .provider-tile"); if (first) first.click(); } });
  $("suVerify").onclick = () => setup.verify();
  $("suSignIn").onclick = () => setup.signIn();
  $("suProtocol").onchange = () => { if ($("suProtocol").value === "POP3" && $("suIncomingPort").value === "993") $("suIncomingPort").value = "995"; if ($("suProtocol").value === "IMAP" && $("suIncomingPort").value === "995") $("suIncomingPort").value = "993"; };

  $("navToggle").onclick = () => { const ws = $("workspace"); if (innerWidth <= 700) ws.classList.toggle("nav-open"); else ws.classList.toggle("nav-collapsed"); };
  // On phones the folder pane is a drawer: a tap anywhere outside it closes it.
  $("workspace").addEventListener("click", (e) => { const ws = $("workspace"); if (ws.classList.contains("nav-open") && !e.target.closest("#folderPane")) { ws.classList.remove("nav-open"); e.preventDefault(); e.stopPropagation(); } }, true);
  $("suRemember").addEventListener("change", () => { setup.rememberTouched = true; });
  $("btnNewMail").onclick = () => compose.open({ mode: "new" });
  $("btnRefresh").onclick = () => { list.fetch(); folders.load(); };
  $("btnTheme").onclick = () => { const cur = document.documentElement.dataset.theme; prefs.set("theme", cur === "dark" ? "light" : "dark"); applyTheme(); };
  $("btnSettings").onclick = () => settingsUI.open("general");
  $("btnShortcuts").onclick = () => $("shortcutsDialog").showModal();
  $("accountAvatar").onclick = () => settingsUI.open("account");
  qsa("#stNav button").forEach((b) => (b.onclick = () => { settingsUI.tab = b.dataset.tab; settingsUI.render(); }));
  qsa("[data-close]").forEach((b) => (b.onclick = () => $(b.dataset.close).close()));

  const search = $("searchInput");
  search.addEventListener("keydown", (e) => { if (e.key === "Enter") { const q = search.value.trim(); if (q) list.search(q); else { state.search = null; list.fetch(); } } });
  search.addEventListener("input", () => { $("searchClear").classList.toggle("hidden", !search.value); if (!search.value && state.search) { state.search = null; list.load(state.folder); } });
  $("searchClear").onclick = () => { search.value = ""; $("searchClear").classList.add("hidden"); state.search = null; list.load(state.folder); };

  qsa(".filters button").forEach((b) => (b.onclick = () => { qsa(".filters button").forEach((x) => x.classList.remove("active")); b.classList.add("active"); state.filter = b.dataset.filter; list.render(); }));
  $("cmdSelectAll").onclick = () => list.selectAll(state.selected.size !== list.visible().length);
  $("cmdDelete").onclick = () => actions.delete(list.targets());
  $("cmdArchive").onclick = () => actions.archive(list.targets());
  $("cmdJunk").onclick = () => actions.junk(list.targets(), true);
  $("cmdNotJunk").onclick = () => actions.junk(list.targets(), false);
  $("cmdMove").onclick = () => actions.pickFolder(list.targets());
  $("cmdRead").onclick = () => { const t = list.targets(); if (t.length) actions.read(t, !t.every((m) => m.read)); };
  $("cmdFlag").onclick = () => { const t = list.targets(); if (t.length) actions.flag(t, !t.every((m) => m.flagged)); };
  $("cmdRescan").onclick = async () => { toast("Re-scanning this folder…"); await list.fetch({ rescan: true }); };
  $("btnLoadMore").onclick = () => list.fetch({ more: true });

  $("rdBack").onclick = () => reading.close();
  $("rdReply").onclick = () => state.openDetail && compose.open({ mode: "reply", detail: state.openDetail });
  $("rdReplyAll").onclick = () => state.openDetail && compose.open({ mode: "replyAll", detail: state.openDetail });
  $("rdForward").onclick = () => state.openDetail && compose.open({ mode: "forward", detail: state.openDetail });
  $("rdDelete").onclick = () => state.openDetail && actions.delete([state.openDetail]);
  $("rdArchive").onclick = () => state.openDetail && actions.archive([state.openDetail]);
  $("rdMove").onclick = () => state.openDetail && actions.pickFolder([state.openDetail]);
  $("rdFlag").onclick = () => state.openDetail && actions.flag([state.openDetail], !state.openDetail.flagged);
  $("rdUnread").onclick = () => { if (state.openDetail) { clearTimeout(reading.markTimer); actions.read([state.openDetail], false); } };
  $("rdJunk").onclick = () => state.openDetail && actions.junk([state.openDetail], true);
  $("rdNotJunk").onclick = () => state.openDetail && actions.junk([state.openDetail], false);
  $("rdAnalyze").onclick = () => state.openDetail && analysis.open(state.openDetail);
  $("rdShowImages").onclick = () => { state.imagesOnce = true; reading.render(state.openDetail); };
  $("rdAlwaysImages").onclick = async () => { const a = (state.openDetail.fromAddress || "").toLowerCase(); await settingsSave({ ...state.settings, imageSenders: [...new Set([...(state.settings.imageSenders || []), a])] }); reading.render(state.openDetail); };
  $("rdMore").onclick = (e) => {
    const d = state.openDetail; if (!d) return;
    const menu = $("ctxMenu"); menu.innerHTML = "";
    const add = (label, ic, fn, cls = "") => menu.appendChild(el("button", { class: cls, onclick: () => { contextMenu.hide(); fn(); } }, icon(ic), label));
    const headerText = () => Object.entries(d.headers || {}).map(([k, v]) => `${k}: ${v}`).join("\n\n") || "(no headers captured)";
    const printable = (allowImages) => `<title>${esc(d.subject)}</title><h2>${esc(d.subject)}</h2><p><b>${esc(d.fromName)}</b> &lt;${esc(d.fromAddress)}&gt; · ${esc(fmtDate(d.date, { long: true }))}</p><hr>${d.bodyHtml ? sanitizeHtml(d.bodyHtml, { allowImages, folder: state.open.folder, uid: d.uid }).html : `<pre style="white-space:pre-wrap">${esc(d.bodyText)}</pre>`}`;
    add("View message headers", "list", () => popup("Message headers", { text: headerText() }));
    if (!NATIVE) add("Open in new window", "external", () => popup(d.subject, { html: printable(state.imagesOnce) }));
    add("Print", "print", () => {
      if (nativePrint(d.subject)) return;
      const w = window.open("", "_blank");
      if (!w) { window.print(); return; }
      w.document.write(printable(true)); w.document.close(); setTimeout(() => w.print(), 300);
    });
    if (d.listUnsubscribe) { const m = d.listUnsubscribe.match(/<(https?:[^>]+)>/); const mail = d.listUnsubscribe.match(/<mailto:([^>]+)>/); if (m || mail) add("Unsubscribe", "block", () => { if (m) openExternal(m[1]); else compose.open({ mode: "new" }), ($("cTo").value = mail[1].split("?")[0], $("cSubject").value = "unsubscribe"); }); }
    add("Trust sender (safe list)", "shield-ok", () => actions.trustSender(d));
    add("Block sender", "block", () => actions.blockSender(d), "danger");
    add("Delete permanently", "trash", () => actions.delete([d], true), "danger");
    menu.classList.remove("hidden");
    const r = e.currentTarget.getBoundingClientRect();
    menu.style.left = Math.min(r.left, innerWidth - 230) + "px"; menu.style.top = r.bottom + 4 + "px";
    e.stopPropagation();
  };

  // compose
  $("cClose").onclick = () => { if (!$("cEditor").innerText.trim() && !$("cTo").value || confirm("Discard this message?")) compose.close(); };
  $("cDiscard").onclick = () => { if (confirm("Discard this message?")) compose.close(); };
  $("cSend").onclick = () => compose.send();
  $("cSaveDraft").onclick = () => compose.saveDraft();
  $("cShowCc").onclick = () => { $("cCcRow").classList.remove("hidden"); $("cCc").focus(); };
  $("cShowBcc").onclick = () => { $("cBccRow").classList.remove("hidden"); $("cBcc").focus(); };
  qsa(".fmt-bar [data-cmd]").forEach((b) => (b.onmousedown = (e) => { e.preventDefault(); document.execCommand(b.dataset.cmd, false, b.dataset.arg || null); $("cEditor").focus(); }));
  $("cFontSize").onchange = () => { if ($("cFontSize").value) document.execCommand("fontSize", false, $("cFontSize").value); $("cFontSize").value = ""; $("cEditor").focus(); };
  $("cColor").oninput = () => { document.execCommand("foreColor", false, $("cColor").value); };
  $("cLink").onmousedown = (e) => { e.preventDefault(); const url = prompt("Link address:", "https://"); if (url) document.execCommand("createLink", false, url); };
  $("cAttach").onclick = () => $("cAttachInput").click();
  $("cAttachInput").onchange = () => { compose.addFiles(Array.from($("cAttachInput").files)); $("cAttachInput").value = ""; };
  $("cSignature").onclick = () => { if (state.settings.signature) document.execCommand("insertHTML", false, `<br><div>${state.settings.signature.replace(/\n/g, "<br>")}</div>`); else toast("Set a signature under Settings → General."); };
  $("cEditor").addEventListener("paste", (e) => { const files = Array.from(e.clipboardData?.files || []); if (files.length) { e.preventDefault(); compose.addFiles(files); } });
  $("cEditor").addEventListener("drop", (e) => { const files = Array.from(e.dataTransfer?.files || []); if (files.length) { e.preventDefault(); compose.addFiles(files); } });
  $("composeDialog").addEventListener("cancel", (e) => { e.preventDefault(); $("cClose").onclick(); });

  // drafts open in the editor
  const origOpen = reading.open.bind(reading);
  reading.open = async (m, opts) => {
    if (state.folderRole === "drafts" && state.capabilities.drafts) {
      try { const d = await api("/api/message", { query: { folder: m.folder || state.folder, uid: m.uid } }); compose.open({ mode: "draft", draft: d }); return; } catch (e) { toast(e.message, { danger: true }); }
    }
    return origOpen(m, opts);
  };

  window.addEventListener("resize", debounce(() => reading.fitFrame(), 150));
  document.addEventListener("visibilitychange", () => { if (!document.hidden && state.token) list.fetch({ silent: true }); });
}

// ============================================================================ native hooks
/** Android back button (and the iOS shell): returns true when something was closed. */
window.quillboxBack = function () {
  if (!$("ctxMenu").classList.contains("hidden")) { contextMenu.hide(); return true; }
  const dlg = qs("dialog[open]");
  if (dlg) { if (dlg.id === "composeDialog") $("cClose").onclick(); else dlg.close(); return true; }
  const ws = $("workspace");
  if (ws.classList.contains("nav-open")) { ws.classList.remove("nav-open"); return true; }
  if (state.token && ws.classList.contains("show-reading") && innerWidth <= 1000) { reading.close(); return true; }
  if (state.token && state.search) { $("searchClear").onclick(); return true; }
  if (!state.token && !$("setupStep2").classList.contains("hidden")) { setup.step(1); return true; }
  if (!state.token && !$("setupStep1").classList.contains("hidden")) { setup.step(0); return true; }
  return false;
};
/** mailto: links handed over by the shells (and the Android SENDTO intent). */
window.quillboxMailto = function (url) {
  if (!state.token) { toast("Sign in first, then the message will open."); return false; }
  let to = "", params = new URLSearchParams();
  try { const s = String(url).replace(/^mailto:/i, ""); const q = s.indexOf("?"); to = decodeURIComponent(q >= 0 ? s.slice(0, q) : s); if (q >= 0) params = new URLSearchParams(s.slice(q + 1)); } catch {}
  compose.open({ mode: "new" });
  $("cTo").value = [to, params.get("to") || ""].filter(Boolean).join(", ");
  if (params.get("cc")) { $("cCcRow").classList.remove("hidden"); $("cCc").value = params.get("cc"); }
  if (params.get("bcc")) { $("cBccRow").classList.remove("hidden"); $("cBcc").value = params.get("bcc"); }
  if (params.get("subject")) $("cSubject").value = params.get("subject");
  if (params.get("body")) $("cEditor").textContent = params.get("body");
  return true;
};
if (NATIVE) document.documentElement.dataset.native = NATIVE;

// ============================================================================ init
(async function init() {
  const shownAt = Date.now();
  applyTheme();
  wire();
  const resumed = await session.resume().catch(() => false);
  if (!resumed) setup.show();
  // Keep the splash up for a moment so it does not flash, then fade it out.
  setTimeout(() => { const sp = $("splash"); sp.classList.add("fade"); setTimeout(() => sp.remove(), 400); }, Math.max(0, 700 - (Date.now() - shownAt)));
})();
