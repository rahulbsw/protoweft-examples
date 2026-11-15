---
id: intro
title: Why Protoweft
nav: Why Protoweft
protocol: all five
---
# One Java API, five agent protocols

Agents talk to tools, to other agents and to people, and each of those has its own protocol. Protoweft gives Java one protocol-neutral API for all of them, as a client and as a server.

This tour runs one small Spring Boot app that serves the same weather tools and the same agent over five protocols. Each chapter calls it the way a real client would, with Protoweft's own connectors, and the panel on the right shows **the real HTTP traffic** of every step.

<div class="map">
  <div class="mcp"><b>MCP</b>models ↔ tools</div>
  <div class="utcp"><b>UTCP</b>tools described, called directly</div>
  <div class="a2a"><b>A2A</b>agent ↔ agent</div>
  <div class="rap"><b>RAP</b>tools that call back and keep streaming</div>
  <div class="agui"><b>AG-UI</b>agent ↔ the user's screen</div>
</div>

## The tools: plain Java methods

Every protocol in this tour serves these two methods. `@AgentTool` is all it takes, and only the configuration names a protocol.

{{snippet tools}}

## The app: five servers from configuration

The Protoweft Spring Boot starter turns each `enabled: true` into a protocol endpoint, all on port 8080.

{{snippet config}}

:::step intro.endpoints Check that all five endpoints answer
One request per protocol: the A2A agent card, the UTCP manual, the RAP toolset, the AG-UI capabilities, and an MCP `initialize`. Watch the panel fill up with five different wire formats from one app.
:::

> **How to use the tour:** press **▶ Run** on each step, or press **R** to run the next one. **←** and **→** switch chapters. Click an exchange in the panel to fold it.
