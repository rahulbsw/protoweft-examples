---
id: rap
title: RAP
nav: RAP · tools that call back
protocol: RAP
---
# RAP: tools that answer later and keep streaming

The **Reactive Agent Protocol** comes from Hydro's Infinity agent runtime (AWS and UC Berkeley). Its idea: a tool call doesn't have to block. The caller sends a request with a **callback URL**, and the tool server POSTs the result back when it's ready. With a **subscription** it keeps posting events long after that.

Our app is both sides: it serves its tools over RAP under `/rap`, and the tour's RAP client receives its callbacks on the same app, at `/rap/callback/…`. In the panel, callbacks are indented and marked ↩.

{{snippet rap-connect}}

:::step rap.toolset Read the toolset
The caller reads `/rap/.well-known/rap-toolset` to learn the tools.
:::

:::step rap.call Call a tool and get the answer by callback
`POST /rap/invoke` is acknowledged at once. The result arrives as a separate `POST` to the callback URL. To your code, it's still a normal `invoke`.

{{snippet rap-call}}
:::

:::step rap.subscribe Subscribe to live updates
The `updates` tool answers "Watching Oslo" right away, then keeps sending events by callback until its final one. In Java they arrive as a `Flow.Publisher<ToolEvent>`.

{{snippet rap-subscribe}}

The tool itself is still a plain method. It asks RAP for a subscription:

{{snippet updates}}
:::

> **Takeaway:** subscriptions are part of Protoweft's core API (`ToolOutcome.subscription()`), so a tool that streams works the same for every caller.
