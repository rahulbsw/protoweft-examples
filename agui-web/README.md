# AG-UI web example

A Spring AI agent served over [AG-UI](https://docs.ag-ui.com) with `protoweft-agui`, and a small
browser page that talks to it with the official `@ag-ui/client`.

- **The agent:** `WebAgentConfiguration` builds a `ChatClient` with the application's Protoweft
  tools (`WebTools.temperature`) and no chat-memory advisor, since AG-UI clients send the whole
  conversation with every run. The starter serves it at `POST /agui`
  (`protoweft.server.agui.enabled: true`), with its capabilities at `GET /agui/capabilities`.
- **The page** (`src/main/resources/static/`, script in `frontend/src/app.js`) shows:
  - streamed replies;
  - the agent's tool calls, as cards;
  - the shared state.

  It also offers the agent its own tool, `set_theme`. When the agent calls it, the page recolours
  itself and sends the result on the next run.

## Run it

```bash
# 1. Build the page script (Node 20+). The bundle goes to src/main/resources/static/app.js.
cd agui-web/frontend && npm ci --ignore-scripts && npm run build && cd -

# 2. Start the agent with a model (any OpenAI-compatible server works; see application.yml).
OPENAI_API_KEY=sk-... ./gradlew :agui-web:bootRun
```

Open http://localhost:8080 and try “What is the temperature in Oslo?” or “Make the page teal”.

## Tools from published MCP servers

Every server under `protoweft.mcp.clients` adds its tools to the agent, and the page shows each call
as a card. For example, in a file passed with `--spring.config.additional-location=file:mcp.yml`:

```yaml
protoweft:
  mcp:
    clients:
      time:   { command: uvx, args: [mcp-server-time] }        # official reference server
      fetch:  { command: uvx, args: [mcp-server-fetch] }       # official reference server: reads web pages
      stocks: { command: uvx, args: ["yfmcp@0.14.1"] }         # community Yahoo Finance server, no API key
      github:                                                  # GitHub's hosted MCP server
        url: https://api.githubcopilot.com
        endpoint: /mcp/
        headers:
          Authorization: "Bearer ${GITHUB_TOKEN}"               # a GitHub token, from the environment
          X-MCP-Toolsets: repos
          X-MCP-Readonly: "true"                                # the agent can read, never change
```

Then try "What's Apple trading at, and what's the analysts' mean price target?", "What time is it
in Tokyo?", "Fetch https://github.com/rahulbsw/protoweft and say in two sentences what it is" or
"What's the latest release of rahulbsw/protoweft on GitHub?". Use a model with tool calling (any
OpenAI-compatible API).

- **Startup:** each server starts with the application; one that fails to start stops it, unless
  `protoweft.fail-fast=false`.
- **Behind a proxy that inspects TLS:** `uvx` needs `UV_NATIVE_TLS=1`. Java needs the proxy's root
  certificate in its trust store, for example a copy of the JDK's `cacerts` with the certificate
  imported (`keytool -importcert`), passed with
  `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=<file> -Djavax.net.ssl.trustStorePassword=changeit"`.
- The stock data comes from an unofficial Yahoo Finance API, for demonstration only.

## From CopilotKit

CopilotKit reaches AG-UI agents through its runtime. Register this agent as an `HttpAgent` with
`url: "http://localhost:8080/agui"` (see [the AG-UI guide](https://github.com/rahulbsw/protoweft/blob/main/docs/protocols/agui.md)). A page
served from another origin also needs that origin in `protoweft.server.agui.allowed-origins`.

## Tests

`AgUiWebExampleTest` runs the application with a scripted chat model (no network) and drives it
over AG-UI with Protoweft's own client. It checks a Protoweft tool call shown in the stream, the
frontend tool round trip, and the served page and capabilities.
