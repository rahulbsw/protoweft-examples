---
id: utcp
title: UTCP
nav: UTCP · call tools directly
protocol: UTCP
---
# UTCP: describe the tool, then call it directly

The **Universal Tool Calling Protocol** takes a different approach: no wrapper server in the middle. A UTCP **manual** is a JSON document that lists tools and, for each one, a **call template** saying how to call it natively, for example an HTTP method and URL. The client reads the manual once, then calls the tools itself.

That's a good fit for existing HTTP APIs: describe them and agents can use them. Our app serves its tools as a UTCP manual at `/utcp`.

:::step utcp.manual Read the manual
One `GET /utcp`. Look at the response: every tool has a `tool_call_template` with the URL and method to call. The tool list comes from the manual itself, with no second request.

{{snippet utcp-manual}}
:::

:::step utcp.call Call a tool through its call template
The client follows the template: a plain `POST /utcp/tools/forecast` with the arguments as the JSON body. There's no JSON-RPC envelope and no session, just HTTP.

{{snippet utcp-call}}
:::

> **Takeaway:** a different protocol, the same `ToolConnector` API. Compare this panel with the MCP chapter: same tools, very different wire.
