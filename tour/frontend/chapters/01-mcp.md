---
id: mcp
title: MCP
nav: MCP · tools for models
protocol: MCP
---
# MCP: give a model your tools

The **Model Context Protocol** connects AI applications to tools, data and prompts. Anthropic introduced it in late 2024, and today Claude, ChatGPT, IDEs and most agent frameworks speak it. It is JSON-RPC 2.0, over stdio for local servers or over **Streamable HTTP** for remote ones.

Our app is an MCP server: `protoweft.server.mcp.enabled: true` publishes the `@AgentTool` methods at `/mcp`. Now we call it as an MCP client.

:::step mcp.connect Connect and negotiate a version
The client sends `initialize` with the newest revision it knows, the server answers with the one they'll use, and the client confirms with `notifications/initialized`. The `Mcp-Session-Id` header ties the following requests together. Protoweft records what was negotiated in a **compliance report** (see the Compliance tab).

{{snippet mcp-connect}}
:::

:::step mcp.list List the tools
`tools/list` returns each tool with a JSON Schema for its arguments, generated from the Java method's parameters and `@ToolParam` descriptions.

{{snippet mcp-list}}
:::

:::step mcp.call Call a tool
`tools/call` sends the arguments; the result comes back as content blocks. In application code it's one `invoke`, and the code would be identical for any other protocol.

{{snippet mcp-call}}
:::

> **Takeaway:** `McpClientConnector` is a `ToolConnector`. Your code calls `listTools()` and `invoke()`, and never touches JSON-RPC.
