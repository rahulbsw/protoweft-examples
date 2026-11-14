# Protoweft examples

Runnable examples for [Protoweft](https://github.com/rahulbsw/protoweft), the protocol backbone
for Java agents: MCP, A2A, UTCP, RAP and AG-UI behind one protocol-neutral Java 17 API.

Unlike the examples inside the Protoweft repository, these build against the **published**
artifacts, the way your own project would. The examples share the root build (`build.gradle.kts`,
`gradle.properties`); to start a project of your own, copy this repository and keep the examples
you need, or (once Protoweft is on Maven Central) start from one of its standalone
[templates](https://github.com/rahulbsw/protoweft/tree/main/templates).

| Example | What it shows | Needs an LLM key to run? |
|---|---|---|
| [`mcp-client-example`](mcp-client-example) | Calls a bundled stdio MCP server through `McpClientConnector` and prints its `ComplianceReport`. | No |
| [`plain-java-gateway`](plain-java-gateway) | One tool and one agent served over MCP, UTCP, A2A and RAP from an embedded Jetty server, and called back through one `ToolRegistry`, including a RAP subscription. | No |
| [`spring-boot-example`](spring-boot-example) | `@AgentTool` beans served over MCP and UTCP, and an agent over A2A, by `protoweft-spring-boot-starter`, with bearer-token auth and Actuator. | No |
| [`langchain4j-example`](langchain4j-example) | A LangChain4j AI service using Protoweft tools, served as an A2A agent. | Optional: without `OPENAI_API_KEY` it lists the tools the model would get |
| [`spring-ai-example`](spring-ai-example) | A Spring AI `ChatClient` using Protoweft tools, served as a streaming A2A agent. | Yes (`OPENAI_API_KEY`) |
| [`argi-example`](argi-example) | An ARGI `ReactAgent` whose tools are picked by the capability catalog, with human approval over A2A. | Yes (`OPENAI_API_KEY`) |
| [`agui-web`](agui-web) | A Spring AI agent served over AG-UI to a browser page built on `@ag-ui/client`, with a frontend tool. | Yes (`OPENAI_API_KEY`) |

The tests need no model or API key: the LLM-backed examples use a scripted chat
model.

## Requirements

- Java 17 or newer (the Gradle wrapper is included).
- Node 20+ only for the `agui-web` browser page.

## Build and run

```bash
./gradlew build                            # compile and test every example

./gradlew :mcp-client-example:run          # MCP client against a bundled stdio server
./gradlew :plain-java-gateway:run          # one tool and agent over MCP, UTCP, A2A and RAP
./gradlew :spring-boot-example:bootRun     # the same in Spring Boot, on port 8080

export OPENAI_API_KEY=sk-...               # for the LLM-backed examples (any OpenAI-compatible server works)
./gradlew :langchain4j-example:run
./gradlew :spring-ai-example:bootRun
./gradlew :argi-example:bootRun
./gradlew :agui-web:bootRun                # build the page first, see agui-web/README.md
```

The Spring Boot, Spring AI, ARGI and AG-UI examples each have a README with what to try against
them (curl, the MCP Inspector, and so on).

## Protoweft version

The examples use `protoweft-bom` at the version in [`gradle.properties`](gradle.properties)
(`protoweftVersion`). Until Protoweft's first release is on Maven Central that is
`0.1.0-SNAPSHOT`, resolved from the Central Portal snapshot repository, which Protoweft's `main`
branch publishes on every push. CI here builds daily against the latest snapshot.

To build against another version, or against a Protoweft checkout you installed with
`./gradlew publishToMavenLocal`:

```bash
./gradlew build -PprotoweftVersion=0.1.0
```

For a local install, also add `mavenLocal()` to the `repositories` block in
[`build.gradle.kts`](build.gradle.kts).

## Learn more

- [Protoweft README](https://github.com/rahulbsw/protoweft#readme) and
  [user guide](https://github.com/rahulbsw/protoweft/blob/main/docs/index.md)
- Report a problem with an example here; report a problem with Protoweft itself in
  [rahulbsw/protoweft](https://github.com/rahulbsw/protoweft/issues).

## License

[MIT](LICENSE)
