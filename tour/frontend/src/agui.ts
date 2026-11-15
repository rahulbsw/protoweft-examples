import { HttpAgent } from "@ag-ui/client";
import type { Exchange } from "./types";

// The page's own tool: the agent can call it, the page runs it and answers on the next run.
const setTheme = {
  name: "set_theme",
  description: "Change the page's accent colour",
  parameters: {
    type: "object",
    properties: { color: { type: "string", description: "A CSS colour, for example 'teal'" } },
    required: ["color"],
  },
};

function applyTheme(color: string): string {
  document.documentElement.style.setProperty("--accent", color);
  return `The page is now ${color}.`;
}

const pause = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Adds streamed text to a reply; a reply stays hidden until it has visible text (models often start with newlines). */
function append(el: HTMLElement, delta: string) {
  el.dataset.raw = (el.dataset.raw ?? "") + delta;
  el.textContent = el.dataset.raw.trim();
  el.hidden = !el.textContent;
}

/** The AG-UI chapter's chat: live through @ag-ui/client, or rebuilt from a recording. */
export class Chat {
  private agent = new HttpAgent({ url: "/agui" });

  constructor(private log: HTMLElement) {}

  private bubble(kind: string, text: string): HTMLElement {
    const div = document.createElement("div");
    div.className = `bubble ${kind}`;
    div.textContent = text;
    this.log.append(div);
    this.log.scrollTop = this.log.scrollHeight;
    return div;
  }

  private reply(): HTMLElement {
    const el = this.bubble("assistant", "");
    el.hidden = true;
    return el;
  }

  /** Sends a message to the live agent and answers its set_theme calls until nothing is pending. */
  async send(text: string): Promise<void> {
    this.bubble("user", text);
    this.agent.addMessage({ id: crypto.randomUUID(), role: "user", content: text });
    for (let round = 0; round < 5; round++) {
      const texts = new Map<string, HTMLElement>();
      const calls = new Map<string, HTMLElement>();
      await this.agent.runAgent({ tools: [setTheme] }, {
        onTextMessageStartEvent: ({ event }) => { texts.set(event.messageId, this.reply()); },
        onTextMessageContentEvent: ({ event }) => { append(texts.get(event.messageId)!, event.delta); },
        onToolCallStartEvent: ({ event }) => { calls.set(event.toolCallId, this.bubble("tool", `⚙ ${event.toolCallName}(…)`)); },
        onToolCallResultEvent: ({ event }) => {
          const card = calls.get(event.toolCallId);
          if (card) card.textContent += ` → ${typeof event.content === "string" ? event.content : "[parts]"}`;
        },
        onRunErrorEvent: ({ event }) => { this.bubble("error", `The agent failed: ${event.message}`); },
      });
      const answered = new Set(this.agent.messages.filter((m) => m.role === "tool").map((m) => m.toolCallId));
      const pending = this.agent.messages.flatMap((m) => ("toolCalls" in m ? m.toolCalls ?? [] : []))
        .filter((c) => c.function.name === setTheme.name && !answered.has(c.id));
      if (pending.length === 0) return;
      for (const call of pending) {
        const result = applyTheme(JSON.parse(call.function.arguments).color);
        this.bubble("tool", `🎨 set_theme → ${result}`);
        this.agent.addMessage({ id: crypto.randomUUID(), role: "tool", toolCallId: call.id, content: result });
      }
    }
  }

  /** Shows a recorded conversation from the AG-UI traffic: the requests' messages, the SSE events. */
  async replay(exchanges: Exchange[], onEvent: () => void): Promise<void> {
    for (const e of exchanges.filter((x) => x.path.startsWith("/agui") && x.method === "POST")) {
      const messages: { role: string; content: string }[] = JSON.parse(e.requestBody).messages ?? [];
      const last = messages[messages.length - 1];
      if (last?.role === "user") this.bubble("user", last.content);
      if (last?.role === "tool") this.bubble("tool", `🎨 set_theme → ${applyTheme(last.content.replace(/.*now (\S+)\./, "$1"))}`);
      const texts = new Map<string, HTMLElement>();
      const calls = new Map<string, { el: HTMLElement; name: string }>();
      for (const block of e.responseBody.split(/\n\n+/)) {
        const data = block.split("\n").filter((l) => l.startsWith("data:")).map((l) => l.slice(5)).join("");
        if (!data) continue;
        const ev = JSON.parse(data);
        switch (ev.type) {
          case "TEXT_MESSAGE_START": texts.set(ev.messageId, this.reply()); break;
          case "TEXT_MESSAGE_CONTENT": append(texts.get(ev.messageId)!, ev.delta); await pause(60); break;
          case "TOOL_CALL_START": calls.set(ev.toolCallId, { el: this.bubble("tool", `⚙ ${ev.toolCallName}(…)`), name: ev.toolCallName }); break;
          case "TOOL_CALL_RESULT": {
            const call = calls.get(ev.toolCallId);
            if (call) call.el.textContent += ` → ${typeof ev.content === "string" ? ev.content : "[parts]"}`;
            break;
          }
        }
        onEvent();
      }
      await pause(250);
    }
  }

  reset(): void {
    this.agent = new HttpAgent({ url: "/agui" });
    this.log.innerHTML = "";
  }
}
