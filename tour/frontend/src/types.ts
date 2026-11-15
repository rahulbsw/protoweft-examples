/** One HTTP exchange on a protocol endpoint, as the tour app captured it. */
export interface Exchange {
  seq: number;
  protocol: string;
  direction: "request" | "callback";
  method: string;
  path: string;
  status: number;
  requestHeaders: Record<string, string>;
  requestBody: string;
  responseHeaders: Record<string, string>;
  responseBody: string;
  durationMs: number;
  complete: boolean;
}

export interface Compliance {
  protocol: string;
  sdk: string;
  wireRevisions: string[];
  negotiated: string | null;
  supported: string[];
  unsupported: string[];
}

/** The outcome of one ▶ Run (live from the app, or a recording). */
export interface Run {
  stepId: string;
  ok: boolean;
  summary: string;
  data: unknown;
  error: string | null;
  exchanges: Exchange[];
  compliance: Compliance[];
}
