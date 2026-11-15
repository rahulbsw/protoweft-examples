# Protoweft Tour

A tutorial and live demo in one page. One Spring Boot app serves the same weather tools and the
same agent over **MCP, UTCP, A2A, RAP and AG-UI**, and a browser page walks through them chapter by
chapter. Each ▶ Run calls the app with Protoweft's own clients, and the right-hand panel shows the
real HTTP traffic: JSON-RPC, SSE streams and RAP callbacks.

**Try it without installing anything:** http://github.datasierra.com/protoweft-examples/ plays
recordings of every step (replay mode).

| Chapter | What you run |
|---|---|
| 0 · Why Protoweft | One request to each of the five endpoints |
| 1 · MCP | Connect and negotiate, `tools/list`, `tools/call` |
| 2 · UTCP | Read the manual, call a tool through its HTTP call template |
| 3 · A2A | Agent card, ask the agent (it uses a tool), a follow-up in the same context |
| 4 · RAP | Toolset, a call answered by callback, a subscription that keeps streaming |
| 5 · AG-UI | A chat in the page with `@ag-ui/client`, including a frontend tool that recolours the page |
| 6 · One registry | All four tool protocols in one `ToolRegistry`, the same answer through each, compliance reports |

## Run it

```bash
./tour/run.sh
```

It builds the page (Node.js 20+), starts the app, and prints:

```
│  Protoweft Tour is running → open http://localhost:8080  │
```

Open that URL. Ctrl+C stops it. `PORT=9090 ./tour/run.sh` uses another port.

By hand, the same is:

```bash
cd tour/frontend && npm ci --ignore-scripts && npm run build && cd -
./gradlew :tour:bootRun      # Gradle shows "EXECUTING" while the app runs; that's normal
```

### With a real LLM

Without a key, a scripted model answers, the same way every time. To use a real model (any
OpenAI-compatible server), set:

```bash
export OPENAI_API_KEY=...
export OPENAI_MODEL=gpt-4o-mini                                 # a model with tool calling
export SPRING_AI_OPENAI_BASE_URL=http://localhost:11434/v1      # only for a non-OpenAI server; keep the /v1
./tour/run.sh
```

The top bar shows which model is in use. `TOUR_MODEL=scripted` forces the scripted model even when
a key is set.

## Without the app: replay mode

When the app isn't running, for example on GitHub Pages, the page plays recordings of every step
from `frontend/public/replay/`, and the top bar says **Replay**. If the app stops while you're
using the page, a banner offers to switch to replay.

- `.github/workflows/pages.yml` publishes it on every change to `main`, at
  http://github.datasierra.com/protoweft-examples/.
- `cd tour/frontend && npm run build:pages` builds the replay-only site into `frontend/dist`, with
  relative paths, so any static host and base path work.
- The recordings are made by `RecordReplayTest` with the scripted model. Ids, tokens, ports and
  times are normalized, so they only change when the protocols' traffic changes. `./gradlew
  :tour:test` re-records them. CI fails if the committed recordings are out of date.

## Recording a video

- **`?large`** (`http://localhost:8080/?large`) makes the text bigger.
- **R** runs the next step of the chapter, and **←** / **→** change chapter.
- Click an exchange's header to fold it.
- The default accent is indigo, so "Make the page teal" shows a visible change.

## How it's built

| Where | What |
|---|---|
| `src/main/resources/application.yml` | The five servers, turned on by the Protoweft Spring Boot starter |
| `TourTools` | The `@AgentTool` methods: `temperature`, `forecast`, and the RAP subscription `updates` |
| `AgentConfiguration` | The Spring AI agent: with chat memory for A2A, without it for AG-UI (the page sends the whole conversation) |
| `ScriptedChatModel`, `TourModel` | The no-key model, and which model is used |
| `steps/TourSteps` | Every ▶ Run step, as Protoweft client code |
| `capture/WireCaptureFilter` | Records the protocol endpoints' HTTP traffic without buffering streams |
| `RapCallbacks` | Receives RAP callbacks on the app itself, so they appear in the panel |
| `frontend/chapters/*.md` | The chapter text. `:::step <id> <title>` makes a step; `{{snippet <name>}}` inserts code |

Code shown in the chapters is cut from the Java sources between `// tour:begin <name>` and
`// tour:end`, so the tutorial always shows the code that runs.

The app has no authentication, so the traffic stays readable. A real deployment adds an
`InboundAuthenticator`, as in [spring-boot-example](../spring-boot-example).

## Test

```bash
./gradlew :tour:test
```

`TourStepsTest` runs every step against the app with the scripted model. It checks:
- MCP-only traffic for the MCP steps;
- A2A memory across a follow-up;
- RAP results and events arriving as callbacks;
- AG-UI's SSE stream and frontend tool;
- the same answer through every registry source, and five compliance reports.
