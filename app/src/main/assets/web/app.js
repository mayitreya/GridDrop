// GridDrop browser client. Runs entirely inside iOS Safari — no app install.
// RECEIVE mode = this browser uploads to Android (resumable, chunked).
// SEND mode    = this browser downloads from Android (native, Range-resumable).

const CHUNK = 4 * 1024 * 1024; // 4 MiB slices
const MAX_RETRIES = 6;

const $ = (id) => document.getElementById(id);

// Uploads still in flight. A 1s poller watches each; once the server reports every one
// "completed", we reload so the page reflects the finished transfer without a manual refresh.
const activeUploads = new Set();

async function main() {
  let info;
  try {
    info = await fetch('/api/info').then((r) => r.json());
  } catch (e) {
    $('mode-label').textContent = 'Could not reach the host.';
    return;
  }

  if (info.role === 'RECEIVE') {
    $('mode-label').textContent = 'Upload files to the other device';
    $('send-panel').hidden = false;
    setupUploader();
  } else {
    $('mode-label').textContent = 'Download files from the other device';
    $('download-panel').hidden = false;
    renderDownloads(info.files || []);
    // Keep the list in sync with what the host adds, so both sides show the same files.
    setInterval(async () => {
      try {
        const fresh = await fetch('/api/info').then((r) => r.json());
        renderDownloads(fresh.files || []);
      } catch (e) {
        /* transient — try again next tick */
      }
    }, 3000);
  }
}

// ---------- SEND mode (downloads) ----------
let downloadSig = null;
function renderDownloads(files) {
  // Only touch the DOM when the file set actually changed (avoids clobbering in-progress taps).
  const sig = files.map((f) => `${f.id}:${f.size}`).join('|');
  if (sig === downloadSig) return;
  downloadSig = sig;

  const list = $('download-list');
  list.innerHTML = '';
  $('download-empty').hidden = files.length > 0;
  for (const f of files) {
    const li = document.createElement('li');
    li.innerHTML =
      `<div class="name">${escapeHtml(f.name)}</div>` +
      `<div class="meta">${humanSize(f.size)}</div>` +
      `<a class="dl" href="/d/${encodeURIComponent(f.id)}" download="${escapeHtml(f.name)}">Download ↓</a>`;
    list.appendChild(li);
  }
}

// ---------- RECEIVE mode (resumable uploads) ----------
function setupUploader() {
  $('file-input').addEventListener('change', (e) => {
    for (const file of e.target.files) uploadFile(file);
    e.target.value = '';
  });
}

async function uploadFile(file) {
  const ui = makeRow(file);
  let id = null;
  try {
    // 1. Create the server-side session (or reuse if resuming a same-named file).
    const init = await postJson(
      `/api/upload/init?name=${encodeURIComponent(file.name)}&size=${file.size}`,
    );
    let offset = init.offset || 0;
    id = init.id;

    activeUploads.add(id);
    pollStatus(id);

    while (offset < file.size) {
      const end = Math.min(offset + CHUNK, file.size);
      const slice = file.slice(offset, end);
      offset = await putChunkWithRetry(id, offset, slice, file.size, ui);
      ui.progress(offset / file.size);
    }

    await postJson(`/api/upload/finish?id=${id}`);
    ui.done();
    // The status poller will observe "completed" and reload the page.
  } catch (err) {
    if (id) activeUploads.delete(id); // failed uploads no longer block the auto-reload
    ui.error(err && err.message ? err.message : 'Upload failed');
  }
}

// Polls /api/upload/status once per second while [id] is uploading. When the server marks it
// completed, drop it from the active set; once nothing is uploading, reload so the finished
// transfer is reflected immediately.
function pollStatus(id) {
  const timer = setInterval(async () => {
    try {
      const st = await fetch(`/api/upload/status?id=${id}`).then((r) => r.json());
      if (st.status === 'completed') {
        clearInterval(timer);
        activeUploads.delete(id);
        if (activeUploads.size === 0) window.location.reload();
      }
    } catch (e) {
      /* transient — keep polling */
    }
  }, 1000);
}

// Sends one chunk; on failure, re-syncs the true byte offset from the server and retries with
// exponential backoff, so a dropped connection resumes instead of restarting at 0%.
async function putChunkWithRetry(id, offset, blob, total, ui) {
  let attempt = 0;
  let start = offset;
  while (true) {
    try {
      const res = await fetch(`/api/upload/chunk?id=${id}&offset=${start}`, {
        method: 'PUT',
        body: blob,
        headers: { 'Content-Type': 'application/octet-stream' },
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const body = await res.json();
      return body.offset; // new total bytes written
    } catch (e) {
      attempt++;
      if (attempt > MAX_RETRIES) throw e;
      ui.status(`Connection hiccup — resuming (try ${attempt})…`, 'err');
      await sleep(Math.min(1000 * 2 ** attempt, 8000));
      // Ask the server exactly how many bytes are safely on disk, then realign our slice.
      const st = await fetch(`/api/upload/status?id=${id}`).then((r) => r.json());
      const written = st.offset || 0;
      if (written >= offset) {
        // Chunk actually landed before the error — advance past it.
        return written;
      }
      start = written; // rewind to the real durable offset and resend the remainder
      blob = blob.slice(start - offset);
      offset = start;
    }
  }
}

// ---------- helpers ----------
function makeRow(file) {
  const li = document.createElement('li');
  li.innerHTML =
    `<div class="name">${escapeHtml(file.name)}</div>` +
    `<div class="meta">${humanSize(file.size)}</div>` +
    `<div class="bar"><span></span></div>` +
    `<div class="status">Starting…</div>`;
  $('upload-list').prepend(li);
  const fill = li.querySelector('.bar > span');
  const status = li.querySelector('.status');
  return {
    progress(p) {
      fill.style.width = Math.round(p * 100) + '%';
      status.textContent = Math.round(p * 100) + '%';
      status.className = 'status';
    },
    status(msg, cls) {
      status.textContent = msg;
      status.className = 'status ' + (cls || '');
    },
    done() {
      fill.style.width = '100%';
      status.textContent = 'Saved ✓';
      status.className = 'status done';
    },
    error(msg) {
      status.textContent = msg;
      status.className = 'status err';
    },
  };
}

async function postJson(url) {
  const res = await fetch(url, { method: 'POST' });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return res.json();
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function humanSize(b) {
  if (b < 1024) return b + ' B';
  const u = ['KB', 'MB', 'GB'];
  let n = b / 1024, i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return n.toFixed(1) + ' ' + u[i];
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

main();
