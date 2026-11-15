import { defineConfig, type Plugin } from "vite";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const SOURCES = join(__dirname, "../src/main");

/** Every file under a directory, recursively. */
function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : [path];
  });
}

/** Removes the common indentation of a block of lines. */
function dedent(lines: string[]): string {
  const indent = Math.min(...lines.filter((l) => l.trim()).map((l) => l.match(/^ */)![0].length));
  return lines.map((l) => l.slice(indent)).join("\n");
}

/**
 * Code snippets for the chapters, cut from the tour's real sources between
 * `tour:begin <name>` and `tour:end` comments, so the tutorial always shows the code that runs.
 * Import them with `import snippets from "virtual:tour-snippets"`.
 */
function snippets(): Plugin {
  const id = "virtual:tour-snippets";
  return {
    name: "tour-snippets",
    resolveId: (source) => (source === id ? "\0" + id : null),
    load(resolved) {
      if (resolved !== "\0" + id) return null;
      const out: Record<string, { lang: string; file: string; code: string }> = {};
      for (const file of files(SOURCES).filter((f) => /\.(java|ya?ml)$/.test(f))) {
        this.addWatchFile(file);
        const lines = readFileSync(file, "utf8").split("\n");
        let name: string | null = null;
        let block: string[] = [];
        for (const line of lines) {
          const begin = line.match(/(?:\/\/|#) tour:begin (\S+)/);
          if (begin) {
            name = begin[1];
            block = [];
          } else if (name && /(?:\/\/|#) tour:end/.test(line)) {
            out[name] = {
              lang: file.endsWith(".java") ? "java" : "yaml",
              file: relative(join(__dirname, ".."), file),
              code: dedent(block),
            };
            name = null;
          } else if (name) {
            block.push(line);
          }
        }
      }
      return `export default ${JSON.stringify(out)};`;
    },
  };
}

// `vite build` makes the live page Spring Boot serves; `vite build --mode pages` makes the
// replay-only site for GitHub Pages (relative paths, so it works under any base path).
export default defineConfig(({ mode }) => ({
  base: mode === "pages" ? "./" : "/",
  plugins: [snippets()],
  define: { __TOUR_FORCE_REPLAY__: JSON.stringify(mode === "pages") },
  build: {
    outDir: mode === "pages" ? "dist" : "../src/main/resources/static",
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000,
  },
  server: { proxy: { "/tour/api": "http://localhost:8080", "/agui": "http://localhost:8080" } },
}));
