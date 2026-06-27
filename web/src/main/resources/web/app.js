// Minimal Quillbox web client. The account lives in memory and is posted with each
// request to the backend, which performs the actual IMAP/SMTP work.
let account = null;
let messages = [];

const $ = (id) => document.getElementById(id);

function saveAccount() {
  account = {
    displayName: $("displayName").value || $("email").value,
    email: $("email").value.trim(),
    incomingHost: $("incomingHost").value.trim(),
    incomingPort: parseInt($("incomingPort").value || "993", 10),
    protocol: $("protocol").value,
    incomingSecurity: "SSL_TLS",
    smtpHost: $("smtpHost").value.trim(),
    smtpPort: parseInt($("smtpPort").value || "587", 10),
    smtpSecurity: "STARTTLS",
    username: $("username").value.trim(),
    password: $("password").value,
  };
  $("setup").classList.add("hidden");
  $("app").classList.remove("hidden");
  $("title").textContent = "Quillbox — " + account.displayName;
  refresh();
}

function signOut() {
  account = null;
  messages = [];
  $("app").classList.add("hidden");
  $("setup").classList.remove("hidden");
}

async function refresh() {
  setStatus("Loading…");
  try {
    const res = await fetch("/api/inbox", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ account, limit: 100 }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ error: res.statusText }));
      setStatus("Sync failed: " + err.error);
      return;
    }
    messages = await res.json();
    setStatus(messages.length + " message(s)");
    renderList();
  } catch (e) {
    setStatus("Sync failed: " + e.message);
  }
}

function renderList() {
  const list = $("list");
  list.innerHTML = "";
  messages.forEach((m, i) => {
    const div = document.createElement("div");
    div.className = "item";
    div.onclick = () => renderDetail(i);
    div.innerHTML =
      '<div class="from"></div><div class="subject"></div><div class="muted date"></div>';
    div.querySelector(".from").textContent = m.from || "(unknown)";
    div.querySelector(".subject").textContent = m.subject;
    div.querySelector(".date").textContent = new Date(m.sentDate).toLocaleString();
    list.appendChild(div);
  });
}

function renderDetail(i) {
  const m = messages[i];
  const d = $("detail");
  d.classList.remove("muted");
  d.innerHTML = "";
  const h = document.createElement("h2");
  h.textContent = m.subject;
  const from = document.createElement("div");
  from.textContent = "From: " + m.from;
  const to = document.createElement("div");
  to.className = "muted";
  to.textContent = "To: " + m.to;
  const hr = document.createElement("hr");
  const body = document.createElement("div");
  // Render text only (HTML rendering with image controls is a planned enhancement).
  body.style.whiteSpace = "pre-wrap";
  body.textContent = m.bodyText || "(no text content)";
  d.append(h, from, to, hr, body);
}

function setStatus(text) {
  $("status").textContent = text;
}

function openCompose() { $("composeDialog").showModal(); }
function closeCompose() { $("composeDialog").close(); }

async function sendMessage() {
  const req = {
    account,
    to: split($("to").value),
    cc: split($("cc").value),
    bcc: split($("bcc").value),
    subject: $("subject").value,
    body: $("body").value,
  };
  const res = await fetch("/api/send", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (res.ok) {
    closeCompose();
    setStatus("Sent");
  } else {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    alert("Send failed: " + err.error);
  }
}

function split(v) {
  return v.split(/[,;]/).map((s) => s.trim()).filter((s) => s.length > 0);
}
