import { marked } from "marked";
import hljs from "highlight.js/lib/core";
import java from "highlight.js/lib/languages/java";
import yaml from "highlight.js/lib/languages/yaml";
import json from "highlight.js/lib/languages/json";
import bash from "highlight.js/lib/languages/bash";
import typescript from "highlight.js/lib/languages/typescript";
import snippets from "virtual:tour-snippets";

hljs.registerLanguage("java", java);
hljs.registerLanguage("yaml", yaml);
hljs.registerLanguage("json", json);
hljs.registerLanguage("bash", bash);
hljs.registerLanguage("typescript", typescript);

export interface Chapter {
  id: string;
  number: number;
  title: string;
  nav: string;
  protocol: string;
  html: string;
  steps: { id: string; title: string }[];
}

/**
 * A chapter file: front matter (id, title, nav, protocol), then Markdown in which
 * `:::step <step-id> <title>` … `:::` is a runnable step and `{{snippet <name>}}` is code
 * cut from the tour's Java sources.
 */
function parse(file: string, source: string): Chapter {
  const match = source.match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
  if (!match) throw new Error(`${file}: missing front matter`);
  const meta = Object.fromEntries(match[1].split("\n").map((l) => {
    const i = l.indexOf(":");
    return [l.slice(0, i).trim(), l.slice(i + 1).trim()];
  }));
  const steps: Chapter["steps"] = [];
  // Steps are rendered on their own, and snippets go in after Markdown, so their HTML is never re-parsed.
  const html = match[2].split(/^(:::step \S+ .+\n[\s\S]*?^:::)\s*$/m).map((part) => {
    const step = part.match(/^:::step (\S+) (.+)\n([\s\S]*?)^:::$/m);
    if (!step) return markdown(file, part);
    const [, id, title, inner] = step;
    steps.push({ id, title });
    return `<section class="step" data-step="${id}"><header><span class="num">${steps.length}</span>`
      + `<h3>${title}</h3><button class="run" data-step="${id}">▶ Run</button></header>${markdown(file, inner)}</section>`;
  }).join("\n");
  return {
    id: meta.id, number: Number(file.match(/(\d+)-/)![1]), title: meta.title, nav: meta.nav ?? meta.title,
    protocol: meta.protocol ?? "", html, steps,
  };
}

/** Markdown with highlighted code; a line `{{snippet <name>}}` becomes that snippet. */
function markdown(file: string, source: string): string {
  return marked.parse(source, { async: false })
    .replace(/<p>\{\{snippet ([\w-]+)\}\}<\/p>/g, (_, name: string) => {
      const s = snippets[name];
      if (!s) throw new Error(`${file}: unknown snippet ${name}`);
      return `<div class="snippet"><div class="file">${s.file}</div><pre><code class="hljs language-${s.lang}">${
        hljs.highlight(s.code, { language: s.lang }).value}</code></pre></div>`;
    })
    .replace(/<pre><code class="language-(\w+)">([\s\S]*?)<\/code><\/pre>/g, (_, lang: string, code: string) => {
      const text = code.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
        .replace(/&amp;/g, "&");
      const value = hljs.getLanguage(lang) ? hljs.highlight(text, { language: lang }).value : code;
      return `<pre><code class="hljs language-${lang}">${value}</code></pre>`;
    });
}

const files = import.meta.glob("../chapters/*.md", { query: "?raw", import: "default", eager: true }) as Record<string, string>;

export const chapters: Chapter[] = Object.entries(files)
  .map(([file, source]) => parse(file, source))
  .sort((a, b) => a.number - b.number);
