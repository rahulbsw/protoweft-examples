# Spring AI example

A Spring Boot 4 application where a Spring AI `ChatClient` calls Protoweft tools and is served
as an **A2A** agent, using `protoweft-spring-ai`.

- `AssistantTools` holds plain `@AgentTool` bean methods. The Protoweft starter registers them in
  its `ToolRegistry` bean as source `local`, so the model sees `local__temperature`,
  `local__time` and `local__whoami`.
- `AssistantConfiguration` wires everything with explicit beans:
  - The `ChatClient` gets every registry tool through `ProtoweftToolCallbacks.from(registry)`.
  - A `MessageChatMemoryAdvisor` keeps one conversation per A2A context.
  - The `AgentHandler` bean is `SpringAiAgents.streamingHandler(chatClient)`. The starter serves
    it over A2A (`protoweft.server.a2a.enabled: true`), and each answer chunk is streamed as a
    chunk of the task's `response` artifact.
- The model is Spring AI's OpenAI chat model, configured from the environment.

| Endpoint | Protocol |
|---|---|
| `GET /.well-known/agent-card.json`, `POST /a2a` | A2A 1.0 agent card and JSON-RPC (streaming included) |

## Run

```bash
export OPENAI_API_KEY=sk-...          # without it the app starts, but model calls fail
export OPENAI_MODEL=gpt-4o-mini       # optional; this is the default
./gradlew :spring-ai-example:bootRun
```

The app listens on port 8080; use `--server.port=…` to change it. For an OpenAI-compatible
server, such as Ollama, set `spring.ai.openai.base-url` to its URL including `/v1`
(`http://localhost:11434/v1`); see
[Using an OpenAI-compatible server](../README.md#using-an-openai-compatible-server).

### More tools from an MCP server

Every client configured under `protoweft.mcp.clients` becomes a registry source, and the
`ChatClient` sees its tools on its next request (the callbacks list the registry on each request):

```bash
./gradlew :spring-ai-example:bootRun \
  --args='--protoweft.mcp.clients.remote.url=http://localhost:3001'
```

The model then also sees `remote__<tool>` for each tool of that server. The URL is the server's
base URL, and the Streamable HTTP endpoint defaults to `/mcp` (`protoweft.mcp.clients.remote.endpoint`).
UTCP manuals (`protoweft.utcp.manuals`) and A2A agents (`protoweft.a2a.agents`) work the same way.

### Tools from published MCP servers

Any published MCP server works, local (stdio) or remote (HTTP), with no code changes. This example
adds two servers that run locally through [`uvx`](https://docs.astral.sh/uv/) and need no API key:

- `time`: [`mcp-server-time`](https://github.com/modelcontextprotocol/servers/tree/main/src/time),
  an official reference server. A good first check that everything works.
- `stocks`: [`yfmcp`](https://pypi.org/project/yfmcp/), a community server for Yahoo Finance data:
  quotes, price history, financials, analyst targets and news.

```bash
export OPENAI_API_KEY=...    # any OpenAI-compatible API, see "Using an OpenAI-compatible server"
export OPENAI_MODEL=...      # a model with tool calling
./gradlew :spring-ai-example:bootRun --args='--protoweft.mcp.clients.time.command=uvx --protoweft.mcp.clients.time.args[0]=mcp-server-time --protoweft.mcp.clients.stocks.command=uvx --protoweft.mcp.clients.stocks.args[0]=yfmcp@0.14.1'
```

The same in `application.yml`:

```yaml
protoweft:
  mcp:
    clients:
      time:   { command: uvx, args: [mcp-server-time] }
      stocks: { command: uvx, args: ["yfmcp@0.14.1"] }
```

Then ask the agent (see [Try it](#try-it) for the `curl` command):

| Ask | Tools it uses | Answer (example) |
|---|---|---|
| What time is it in Tokyo right now? | `time__get_current_time` | It's currently 6:27 AM on November 17 in Tokyo. |
| What is AAPL trading at, and what is the analysts' mean price target? | `stocks__…` | AAPL is at $352.78; the analysts' mean target is $345.63 (range $250–$425). |
| What time is it in New York, and what is NVDA trading at? | both servers | It is 4:29 PM EST in New York. NVDA is trading at $156.14. |

- **Startup:** each server starts with the application. If one fails to start, the application
  stops; set `protoweft.fail-fast=false` to start without it.
- **Pin versions** of community servers (`uvx` uses `name@version`), so a new release can't change
  their tools under you.
- **Behind a corporate proxy that inspects TLS,** `uvx` may fail to download the servers. Set
  `UV_NATIVE_TLS=1` so it trusts the system's certificates.
- **Remote servers** use `url` instead of `command`, as in the previous section.
- The stock data comes from an unofficial Yahoo Finance API, for demonstration only; it isn't
  financial advice and can be delayed or unavailable.

## Try it

```bash
curl -s localhost:8080/.well-known/agent-card.json

curl -s -X POST localhost:8080/a2a -H 'Content-Type: application/json' -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{"messageId":"m1","role":"ROLE_USER","parts":[{"text":"What is the weather in Paris?"}]}}}'
```

The answer is the task's `response` artifact. Streaming clients (`SendStreamingMessage`) also
receive each answer chunk as it arrives. To keep a conversation going, send the next message with
the `contextId` the first reply returned.

Without `OPENAI_API_KEY` the task fails with the model client's error, reported as an A2A
`TASK_STATE_FAILED` status.

## Test

```bash
./gradlew :spring-ai-example:test
```

`SpringAiExampleTest` runs the application with a scripted `ChatModel` bean marked `@Primary`
instead of OpenAI, so it needs no network. It talks to the application through Protoweft's A2A
client and checks three things:

- a blocking answer that uses `local__temperature`;
- a streamed answer where `local__whoami` sees the caller;
- conversation memory across two tasks in one context.
