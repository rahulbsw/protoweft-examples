import hljs from "highlight.js/lib/core";
import type { Compliance, Exchange } from "./types";

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

/** Pretty JSON (or one line when compact), highlighted; anything else as plain text. */
function pretty(text: string, compact = false): string {
  try {
    return hljs.highlight(JSON.stringify(JSON.parse(text), null, compact ? 0 : 2), { language: "json" }).value;
  } catch {
    return esc(text);
  }
}

/** A Server-Sent Events body as one block per event, labelled with its type. */
function events(text: string): string {
  return text.split(/\n\n+/).filter((b) => b.trim()).map((block) => {
    const data = block.split("\n").filter((l) => l.startsWith("data:")).map((l) => l.slice(5).trim()).join("\n");
    let type = block.match(/^event: *(.+)$/m)?.[1] ?? "";
    try {
      type ||= JSON.parse(data).type ?? JSON.parse(data).method ?? "";
    } catch { /* not JSON */ }
    return `<div class="sse"><span class="sse-type">${esc(type || "event")}</span><pre class="line">${pretty(data || block, true)}</pre></div>`;
  }).join("");
}

function body(text: string, headers: Record<string, string>): string {
  if (!text) return `<div class="empty">(no body)</div>`;
  return (headers["content-type"] ?? "").includes("text/event-stream") ? events(text) : `<pre>${pretty(text)}</pre>`;
}

function headers(h: Record<string, string>): string {
  const rows = Object.entries(h).map(([k, v]) => `<span class="h"><b>${esc(k)}:</b> ${esc(v)}</span>`).join("");
  return rows ? `<div class="headers">${rows}</div>` : "";
}

/** What an exchange is, in a few words: the JSON-RPC method, the AG-UI or RAP route. */
function label(e: Exchange): string {
  try {
    const req = JSON.parse(e.requestBody);
    if (req.method) return req.method;
    if (req.type) return req.type;
    if (e.direction === "callback" && req.event?.type) return `callback: ${req.event.type}`;
    if (e.direction === "callback") return "callback";
  } catch { /* not JSON */ }
  return e.method === "GET" ? "GET" : e.method;
}

export function exchange(e: Exchange): HTMLElement {
  const el = document.createElement("details");
  el.className = `exchange ${e.direction} p-${e.protocol.replace(/\W/g, "").toLowerCase()}`;
  el.open = Boolean(e.requestBody || e.responseBody);   // empty exchanges (session GET, DELETE) start folded
  const arrow = e.direction === "callback" ? "↩" : "→";
  const status = e.complete ? `${e.status}` : "streaming…";
  el.innerHTML = `
    <summary>
      <span class="badge">${esc(e.protocol)}</span>
      <span class="arrow">${arrow}</span>
      <span class="what">${esc(label(e))}</span>
      <span class="path">${esc(e.method)} ${esc(e.path.length > 46 ? e.path.slice(0, 43) + "…" : e.path)}</span>
      <span class="status s${String(e.status)[0]}">${status}</span>
    </summary>
    <div class="io">
      <div class="dir">${e.direction === "callback" ? "tool server → caller" : "client → server"}</div>
      ${headers(e.requestHeaders)}${body(e.requestBody, e.requestHeaders)}
      <div class="dir">${e.direction === "callback" ? "caller → tool server" : "server → client"} · ${e.status}</div>
      ${headers(e.responseHeaders)}${body(e.responseBody, e.responseHeaders)}
    </div>`;
  return el;
}

export function result(summary: string, data: unknown, ok: boolean): string {
  const details = data == null ? "" : `<pre>${pretty(JSON.stringify(data))}</pre>`;
  return `<div class="result ${ok ? "ok" : "fail"}"><div class="summary">${ok ? "✓" : "✗"} ${esc(summary)}</div>${details}</div>`;
}

export function compliance(reports: Compliance[]): string {
  if (!reports.length) return `<div class="empty">This step has no compliance report. Steps that use a connector show what it speaks.</div>`;
  return `<table class="compliance"><thead><tr><th>Protocol</th><th>Built on</th><th>Wire revisions</th><th>Negotiated</th><th>Not implemented</th></tr></thead><tbody>${
    reports.map((r) => `<tr><td><span class="badge p-${r.protocol.replace(/\W/g, "").toLowerCase()}">${esc(r.protocol)}</span></td>
      <td>${esc(r.sdk)}</td><td>${esc(r.wireRevisions.join(", "))}</td><td>${esc(r.negotiated ?? "–")}</td>
      <td class="unsupported">${esc(r.unsupported.join(", ") || "–")}</td></tr>`).join("")
  }</tbody></table>`;
}
