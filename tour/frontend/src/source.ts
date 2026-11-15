import type { Exchange, Run } from "./types";

/** Where runs come from: the live tour app, or recordings of it (GitHub Pages, or the app stopped). */
export interface Source {
  mode: "live" | "replay";
  model: string;
  baseUrl: string;
  run(stepId: string): Promise<Run>;
}

export class ServerGone extends Error {}

const replayUrl = (name: string) => `${import.meta.env.BASE_URL}replay/${name}.json`;

/** The live app, when it answers; otherwise null. */
export async function connectLive(): Promise<Source | null> {
  if (__TOUR_FORCE_REPLAY__) return null;
  try {
    const res = await fetch("/tour/api/info", { signal: AbortSignal.timeout(1500) });
    if (!res.ok) return null;
    const info = await res.json();
    return {
      mode: "live",
      model: info.model,
      baseUrl: info.baseUrl,
      async run(stepId) {
        let res: Response;
        try {
          res = await fetch(`/tour/api/steps/${stepId}/run`, { method: "POST" });
        } catch {
          throw new ServerGone("The tour app stopped.");
        }
        if (!res.ok) throw new ServerGone(`The tour app answered ${res.status}.`);
        return res.json();
      },
    };
  } catch {
    return null;
  }
}

export async function replay(): Promise<Source> {
  const index = await fetch(replayUrl("index")).then((r) => r.json()).catch(() => ({ model: "scripted" }));
  return {
    mode: "replay",
    model: `${index.model} (recorded)`,
    baseUrl: "http://localhost:8080",
    async run(stepId) {
      const res = await fetch(replayUrl(stepId));
      if (!res.ok) throw new Error(`No recording for ${stepId}.`);
      return res.json();
    },
  };
}

/** Polls the live app for exchanges after a mark: the page's own AG-UI traffic. */
export async function liveExchanges(since: number): Promise<{ mark: number; exchanges: Exchange[] }> {
  const res = await fetch(`/tour/api/exchanges?since=${since}`);
  return res.json();
}
