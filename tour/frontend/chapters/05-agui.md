---
id: agui
title: AG-UI
nav: AG-UI · agents on screen
protocol: AG-UI
---
# AG-UI: put the agent on the user's screen

MCP connects agents to tools and A2A connects agents to each other. **AG-UI**, the Agent–User Interaction protocol, connects agents to **people**. The CopilotKit team created it. The page POSTs the conversation, and the agent answers with one stream of typed events: text as it's written, tool calls, state, errors. Frontends such as CopilotKit speak it.

This chapter's chat is a real AG-UI client: this page uses the official `@ag-ui/client` against our app's `/agui`. The agent is the same Spring AI agent, served by the starter:

<div class="chat-slot"></div>

:::step agui.chat Ask in the chat
Sends “What's the temperature in Oslo?” from this page. In the panel, the response is a Server-Sent Events stream: `RUN_STARTED`, the tool call (`TOOL_CALL_START`, `TOOL_CALL_ARGS`, `TOOL_CALL_RESULT`), the text streamed word by word (`TEXT_MESSAGE_CONTENT`), then `RUN_FINISHED`.
:::

:::step agui.theme Let the agent use the page's own tool
The page offers the agent a **frontend tool**, `set_theme`. Ask for a colour: the agent calls the tool, the page runs it (watch the accent colour change), and sends the result back on a second run.
:::

From Java, the same conversation is a few lines with `AgUiThread`. This is how the tour records it:

{{snippet agui-thread}}

> **Takeaway:** a Java agent behind any AG-UI frontend is `protoweft.server.agui.enabled: true` plus one bean. Try your own message in the chat, for example “Make the page orange”.
