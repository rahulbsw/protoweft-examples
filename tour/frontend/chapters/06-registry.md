---
id: registry
title: One registry
nav: One registry
protocol: all of them
---
# One registry, every protocol

Here is the point of a protocol-neutral API: put all of them behind one `ToolRegistry`. Each source keeps its protocol, and your code sees tools named `<source>.<tool>`. The A2A agent shows up too, as a tool called `ask`.

{{snippet registry}}

:::step registry.list List every tool from four protocols
One `listTools()` across MCP, UTCP, A2A and RAP. The panel shows each connector starting up in its own protocol.
:::

:::step registry.call Call the same tool through every protocol
The same question, four transports, one answer. The last line asks the A2A agent, which uses the tool itself.

{{snippet registry-call}}
:::

:::step registry.compliance Compare what each connector speaks
Every connector publishes a **compliance report**: the SDK it is built on, the wire revisions it speaks, the version it negotiated, and what it doesn't implement. Open the Compliance tab.
:::

## Where next

- Add Protoweft to your project: `io.github.rahulbsw:protoweft-bom` and the modules you need. See the [Protoweft README](https://github.com/rahulbsw/protoweft#readme).
- More examples, including LangChain4j, ARGI and a Spring Boot server with auth: [protoweft-examples](https://github.com/rahulbsw/protoweft-examples).
- Run this tour yourself: `./tour/run.sh`, then open http://localhost:8080.
