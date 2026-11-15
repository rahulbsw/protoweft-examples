/// <reference types="vite/client" />

declare module "virtual:tour-snippets" {
  const snippets: Record<string, { lang: string; file: string; code: string }>;
  export default snippets;
}

/** True in the GitHub Pages build: always replay, never look for a server. */
declare const __TOUR_FORCE_REPLAY__: boolean;
