import "./style.css";
import { chapters, type Chapter } from "./chapters";
import { Chat } from "./agui";
import { connectLive, liveExchanges, preloadRecordings, replay, ServerGone, type Source } from "./source";
import * as view from "./wire";
import type { Exchange, Run } from "./types";

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const pause = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** What the AG-UI chapter's steps send from the page. */
const AGUI_PROMPTS: Record<string, string> = {
  "agui.chat": "What's the temperature in Oslo?",
  "agui.theme": "Make the page teal",
};

let source: Source;
let current: Chapter;
let chat: Chat | null = null;
let busy = false;

function badges() {
  const mode = $("mode");
  mode.className = `mode ${source.mode}`;
  mode.textContent = source.mode === "live" ? `● Live · ${source.baseUrl.replace("http://", "")}` : "● Replay · recorded traffic";
  $("model").textContent = `Model: ${source.model}`;
  $("replay-note").hidden = source.mode !== "replay";
}

function banner(message: string | null) {
  const el = $("banner");
  el.hidden = !message;
  if (!message) return;
  el.innerHTML = `<span>${message}</span><button id="to-replay">Switch to replay</button><button id="retry">Retry live</button>`;
  $("to-replay").onclick = async () => { source = await replay(); badges(); banner(null); };
  $("retry").onclick = async () => {
    const live = await connectLive();
    if (live) { source = live; badges(); banner(null); }
  };
}

function nav() {
  $("nav").innerHTML = chapters.map((c) =>
    `<a href="#/${c.id}" data-id="${c.id}"><span class="n">${c.number}</span>${c.nav}</a>`).join("");
}

function show(id: string) {
  current = chapters.find((c) => c.id === id) ?? chapters[0];
  document.querySelectorAll("#nav a").forEach((a) => a.classList.toggle("on", (a as HTMLElement).dataset.id === current.id));
  $("counter").textContent = `Chapter ${current.number} of ${chapters.length - 1}`;
  const i = chapters.indexOf(current);
  const prev = chapters[i - 1];
  const next = chapters[i + 1];
  $("chapter").innerHTML = `${current.html}
    <footer class="pager">${prev ? `<a href="#/${prev.id}">← ${prev.nav}</a>` : "<span></span>"}${
      next ? `<a href="#/${next.id}">${next.nav} →</a>` : ""}</footer>`;
  $("chapter").scrollTop = 0;
  chat = null;
  const slot = $("chapter").querySelector<HTMLElement>(".chat-slot");
  if (slot) mountChat(slot);
  clearPanel(`Press ▶ Run on a step to see its ${current.protocol || "protocol"} traffic here.`);
}

function mountChat(slot: HTMLElement) {
  slot.innerHTML = `<div class="chat"><div class="log"></div>
    <form class="ask"><input placeholder="Ask the agent… (try “Make the page orange”)" autocomplete="off"><button>Send</button></form></div>`;
  chat = new Chat(slot.querySelector(".log")!);
  slot.querySelector("form")!.addEventListener("submit", (e) => {
    e.preventDefault();
    const input = slot.querySelector("input")!;
    if (!input.value.trim()) return;
    if (source.mode !== "live") { banner("Typing your own messages needs the live app. Run ./tour/run.sh, then reload."); return; }
    if (busy) return;
    const text = input.value.trim();
    input.value = "";
    busy = true;
    clearPanel("Sending…");
    liveChat(null, text)
      .catch((err) => { banner(`${(err as Error).message} You can keep going with the recorded traffic.`); })
      .finally(() => { busy = false; });
  });
}

function clearPanel(hint: string) {
  $("summary").innerHTML = `<div class="hint">${hint}</div>`;
  $("wire").innerHTML = "";
  $("details").innerHTML = "";
  $("compliance").innerHTML = "";
  tab("wire");
}

function tab(name: string) {
  document.querySelectorAll<HTMLElement>(".tabs button").forEach((b) => b.classList.toggle("on", b.dataset.tab === name));
  for (const id of ["wire", "details", "compliance"]) $(id).hidden = id !== name;
}

function addExchange(e: Exchange) {
  $("wire").append(view.exchange(e));
  $("wire").scrollTop = $("wire").scrollHeight;
  $("count").textContent = String($("wire").children.length);
}

function finish(run: Run) {
  $("summary").innerHTML = view.result(run.summary, null, run.ok);
  $("details").innerHTML = view.result(run.summary, run.data, run.ok);
  $("compliance").innerHTML = view.compliance(run.compliance);
}

/** Runs a step: live from the app, or played back from its recording, one exchange at a time. */
async function runStep(stepId: string, button: HTMLButtonElement) {
  if (busy) return;
  busy = true;
  button.disabled = true;
  button.textContent = "Running…";
  clearPanel("Running…");
  $("count").textContent = "0";
  try {
    if (stepId in AGUI_PROMPTS && source.mode === "live") {
      await liveChat(stepId, AGUI_PROMPTS[stepId]);
    } else {
      const run = await source.run(stepId);
      if (stepId in AGUI_PROMPTS && chat) {
        const shown = new Set<number>();
        const all = run.exchanges;
        await chat.replay(all, () => all.filter((e) => !shown.has(e.seq)).slice(0, 1).forEach((e) => { shown.add(e.seq); addExchange(e); }));
        all.filter((e) => !shown.has(e.seq)).forEach(addExchange);
      } else {
        for (const e of run.exchanges) {
          addExchange(e);
          await pause(source.mode === "replay" ? 380 : 140);
        }
      }
      finish(run);
    }
    button.textContent = "✓ Run again";
    button.closest(".step")?.classList.add("done");
  } catch (err) {
    if (err instanceof ServerGone) banner(`${err.message} You can keep going with the recorded traffic.`);
    $("summary").innerHTML = view.result(String((err as Error).message), null, false);
    button.textContent = "▶ Run";
  } finally {
    busy = false;
    button.disabled = false;
  }
}

/** AG-UI from this page: the chat talks to /agui itself, and the panel shows that traffic as it happens. */
async function liveChat(stepId: string | null, text: string) {
  if (!chat) return;
  const { mark } = await liveExchanges(-1);
  let seen = mark;
  const shown = new Set<number>();
  const show = (exchanges: Exchange[]) => exchanges.filter((e) => !shown.has(e.seq)).forEach((e) => {
    shown.add(e.seq);
    seen = Math.max(seen, e.seq);
    addExchange(e);
  });
  const poll = setInterval(() => {
    liveExchanges(seen).then(({ exchanges }) => show(exchanges.filter((x) => x.complete))).catch(() => { /* the run reports it */ });
  }, 400);
  try {
    await chat.send(text).catch(() => { throw new ServerGone("The tour app stopped."); });
  } finally {
    await pause(500);
    clearInterval(poll);
  }
  show((await liveExchanges(seen)).exchanges);
  $("summary").innerHTML = view.result(`Sent “${text}” with @ag-ui/client from this page${stepId ? "" : " (your message)"}`, null, true);
}

async function main() {
  if (new URLSearchParams(location.search).has("large")) document.documentElement.classList.add("large");
  source = (await connectLive()) ?? (await replay());
  if (source.mode === "live") void preloadRecordings().catch(() => { /* replay just won't be offline */ });
  badges();
  nav();
  show(location.hash.slice(2));
  window.addEventListener("hashchange", () => show(location.hash.slice(2)));
  $("chapter").addEventListener("click", (e) => {
    const button = (e.target as HTMLElement).closest<HTMLButtonElement>("button.run");
    if (button) void runStep(button.dataset.step!, button);
  });
  document.querySelectorAll<HTMLElement>(".tabs button").forEach((b) => b.addEventListener("click", () => tab(b.dataset.tab!)));
  // Recording-friendly keys: ← → change chapter, R runs the next step of this chapter.
  document.addEventListener("keydown", (e) => {
    if ((e.target as HTMLElement).closest("input, textarea") || e.metaKey || e.ctrlKey) return;
    const i = chapters.indexOf(current);
    if (e.key === "ArrowRight" && chapters[i + 1]) location.hash = `#/${chapters[i + 1].id}`;
    if (e.key === "ArrowLeft" && chapters[i - 1]) location.hash = `#/${chapters[i - 1].id}`;
    if (e.key.toLowerCase() === "r") {
      const next = [...document.querySelectorAll<HTMLButtonElement>("button.run")].find((b) => !b.closest(".step")?.classList.contains("done"));
      if (next) { next.scrollIntoView({ block: "center", behavior: "smooth" }); void runStep(next.dataset.step!, next); }
    }
  });
}

void main();
