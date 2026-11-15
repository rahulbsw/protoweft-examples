package io.github.rahulbsw.protoweft.examples.tour.steps;

import io.github.rahulbsw.protoweft.a2a.A2aAgentConnector;
import io.github.rahulbsw.protoweft.agui.AgUiClient;
import io.github.rahulbsw.protoweft.agui.AgUiClientConnector;
import io.github.rahulbsw.protoweft.agui.AgUiEvent;
import io.github.rahulbsw.protoweft.agui.AgUiThread;
import io.github.rahulbsw.protoweft.agui.RunAgentInput;
import io.github.rahulbsw.protoweft.core.ComplianceReport;
import io.github.rahulbsw.protoweft.core.ProtocolConnector;
import io.github.rahulbsw.protoweft.core.agent.AgentDescriptor;
import io.github.rahulbsw.protoweft.core.agent.AgentMessage;
import io.github.rahulbsw.protoweft.core.agent.AgentReply;
import io.github.rahulbsw.protoweft.core.tool.Content;
import io.github.rahulbsw.protoweft.core.tool.ToolEvent;
import io.github.rahulbsw.protoweft.core.tool.ToolInvocation;
import io.github.rahulbsw.protoweft.core.tool.ToolOutcome;
import io.github.rahulbsw.protoweft.core.tool.ToolRegistry;
import io.github.rahulbsw.protoweft.core.tool.ToolSpec;
import io.github.rahulbsw.protoweft.mcp.McpClientConnector;
import io.github.rahulbsw.protoweft.rap.RapCallbackEndpoint;
import io.github.rahulbsw.protoweft.rap.RapClientConnector;
import io.github.rahulbsw.protoweft.utcp.UtcpClientConnector;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The runnable steps of the tour, one per ▶ Run button. Each step calls this application with
 * Protoweft's own clients, over real HTTP, so the wire panel shows genuine protocol traffic.
 * The code between {@code tour:begin}/{@code tour:end} markers is shown in the tutorial as is.
 */
@Component
public class TourSteps {

    /** What a step produced: a one-line summary for the result panel, details, compliance reports. */
    public record Result(String summary, Object data, List<ComplianceReport> compliance) {
        static Result of(String summary, Object data, ProtocolConnector... connectors) {
            return new Result(summary, data, java.util.Arrays.stream(connectors).map(ProtocolConnector::compliance).toList());
        }
    }

    /** One runnable step. */
    public record Step(String id, String chapter, String title, Function<String, Result> body) {
    }

    private final Environment environment;
    private final RapCallbackEndpoint rapCallbacks;
    private final Map<String, Step> steps = new LinkedHashMap<>();
    private volatile String a2aContextId;

    public TourSteps(Environment environment, RapCallbackEndpoint rapCallbacks) {
        this.environment = environment;
        this.rapCallbacks = rapCallbacks;
        add("intro.endpoints", "intro", "Check all five endpoints", this::endpoints);
        add("mcp.connect", "mcp", "Connect", this::mcpConnect);
        add("mcp.list", "mcp", "List the tools", this::mcpList);
        add("mcp.call", "mcp", "Call a tool", this::mcpCall);
        add("utcp.manual", "utcp", "Read the manual", this::utcpManual);
        add("utcp.call", "utcp", "Call a tool", this::utcpCall);
        add("a2a.card", "a2a", "Read the agent card", this::a2aCard);
        add("a2a.ask", "a2a", "Ask the agent", this::a2aAsk);
        add("a2a.followup", "a2a", "Ask a follow-up", this::a2aFollowUp);
        add("rap.toolset", "rap", "Read the toolset", this::rapToolset);
        add("rap.call", "rap", "Call a tool", this::rapCall);
        add("rap.subscribe", "rap", "Subscribe to updates", this::rapSubscribe);
        add("agui.chat", "agui", "Chat with the agent", this::aguiChat);
        add("agui.theme", "agui", "Let the agent call the page's tool", this::aguiTheme);
        add("registry.list", "registry", "One registry, four protocols", this::registryList);
        add("registry.call", "registry", "Call through every protocol", this::registryCall);
        add("registry.compliance", "registry", "Compare compliance reports", this::registryCompliance);
    }

    private void add(String id, String chapter, String title, Function<String, Result> body) {
        steps.put(id, new Step(id, chapter, title, body));
    }

    public List<Step> all() {
        return List.copyOf(steps.values());
    }

    public Optional<Step> find(String id) {
        return Optional.ofNullable(steps.get(id));
    }

    /** This application's own URL; every step calls it as a remote service would. */
    public String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port");
    }

    // ── 0 · Intro ─────────────────────────────────────────────────────────────────────────────

    private Result endpoints(String base) {
        HttpClient http = HttpClient.newHttpClient();
        Map<String, Object> status = new LinkedHashMap<>();
        for (String path : List.of("/.well-known/agent-card.json", "/utcp", "/rap/.well-known/rap-toolset",
                "/agui/capabilities")) {
            status.put("GET " + path, get(http, base + path));
        }
        try (McpClientConnector mcp = McpClientConnector.http(base).build()) {
            mcp.start();
            status.put("POST /mcp (initialize)", "MCP " + mcp.compliance().negotiatedRevision());
        }
        return new Result("All five protocol endpoints answer on " + base, status, List.of());
    }

    private static String get(HttpClient http, String url) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return "HTTP " + r.statusCode();
        } catch (Exception e) {
            return "failed: " + e.getMessage();
        }
    }

    // ── 1 · MCP ───────────────────────────────────────────────────────────────────────────────

    private Result mcpConnect(String base) {
        // tour:begin mcp-connect
        try (McpClientConnector mcp = McpClientConnector.http(base).build()) {
            mcp.start();   // initialize, then notifications/initialized
            ComplianceReport report = mcp.compliance();
            // tour:end
            return Result.of("Connected: MCP " + report.negotiatedRevision() + " negotiated", report.wireRevisions(), mcp);
        }
    }

    private Result mcpList(String base) {
        try (McpClientConnector mcp = McpClientConnector.http(base).build()) {
            mcp.start();
            // tour:begin mcp-list
            List<ToolSpec> tools = mcp.listTools();   // tools/list
            // tour:end
            return Result.of(tools.size() + " tools: " + names(tools), specs(tools), mcp);
        }
    }

    private Result mcpCall(String base) {
        try (McpClientConnector mcp = McpClientConnector.http(base).build()) {
            mcp.start();
            // tour:begin mcp-call
            ToolOutcome outcome = mcp.invoke(ToolInvocation.of("temperature", Map.of("city", "Oslo")));   // tools/call
            // tour:end
            return Result.of("temperature(city=Oslo) → " + outcome.text(), outcome.text(), mcp);
        }
    }

    // ── 2 · UTCP ──────────────────────────────────────────────────────────────────────────────

    private Result utcpManual(String base) {
        // tour:begin utcp-manual
        try (UtcpClientConnector utcp = UtcpClientConnector.http("weather", base + "/utcp").build()) {
            utcp.start();                              // GET /utcp: the manual
            List<ToolSpec> tools = utcp.listTools();   // read from the manual, no extra request
            // tour:end
            return Result.of("The manual describes " + tools.size() + " tools: " + names(tools), specs(tools), utcp);
        }
    }

    private Result utcpCall(String base) {
        try (UtcpClientConnector utcp = UtcpClientConnector.http("weather", base + "/utcp").build()) {
            utcp.start();
            // tour:begin utcp-call
            // A plain HTTP call, made the way the manual's call template says: the arguments are the JSON body.
            ToolOutcome outcome = utcp.invoke(ToolInvocation.of("forecast",
                    Map.of("body", Map.of("city", "Oslo", "days", 2))));
            // tour:end
            return Result.of("forecast(Oslo, 2 days) → " + outcome.text(), outcome.text(), utcp);
        }
    }

    // ── 3 · A2A ───────────────────────────────────────────────────────────────────────────────

    private Result a2aCard(String base) {
        // tour:begin a2a-card
        try (A2aAgentConnector agent = A2aAgentConnector.builder(base).build()) {
            agent.start();   // GET /.well-known/agent-card.json
            AgentDescriptor card = agent.descriptor();
            // tour:end
            return Result.of("Agent card: " + card.name() + ", " + card.description(), card, agent);
        }
    }

    private Result a2aAsk(String base) {
        try (A2aAgentConnector agent = A2aAgentConnector.builder(base).build()) {
            agent.start();
            // tour:begin a2a-ask
            AgentReply reply = agent.send(AgentMessage.user("What's the weather in Oslo?"));   // SendMessage
            // tour:end
            a2aContextId = reply.contextId();
            return Result.of("Task " + reply.state() + ": " + reply.text().strip(), reply(reply), agent);
        }
    }

    private Result a2aFollowUp(String base) {
        if (a2aContextId == null) {
            a2aAsk(base);
        }
        try (A2aAgentConnector agent = A2aAgentConnector.builder(base).build()) {
            agent.start();
            // tour:begin a2a-followup
            // Same contextId: the agent remembers the conversation and knows we still mean the weather.
            AgentReply reply = agent.send(new AgentMessage(AgentMessage.Role.USER,
                    List.of(new Content.Text("And in Bergen?")), UUID.randomUUID().toString(), null, a2aContextId, Map.of()));
            // tour:end
            return Result.of("Task " + reply.state() + ": " + reply.text().strip(), reply(reply), agent);
        }
    }

    private static Map<String, Object> reply(AgentReply reply) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", reply.state().toString());
        out.put("taskId", reply.taskId());
        out.put("contextId", reply.contextId());
        out.put("text", reply.text().strip());
        return out;
    }

    // ── 4 · RAP ───────────────────────────────────────────────────────────────────────────────

    private RapClientConnector rap(String base) {
        // tour:begin rap-connect
        // RAP answers through callbacks: results and events are POSTed back to rapCallbacks (on /rap/callback/*).
        RapClientConnector rap = RapClientConnector.builder(base + "/rap").callbacks(rapCallbacks)
                .timeout(Duration.ofSeconds(30)).build();
        rap.start();   // GET /rap/.well-known/rap-toolset
        // tour:end
        return rap;
    }

    private Result rapToolset(String base) {
        try (RapClientConnector rap = rap(base)) {
            List<ToolSpec> tools = rap.listTools();
            return Result.of("The toolset has " + tools.size() + " tools: " + names(tools), specs(tools), rap);
        }
    }

    private Result rapCall(String base) {
        try (RapClientConnector rap = rap(base)) {
            // tour:begin rap-call
            ToolOutcome outcome = rap.invoke(ToolInvocation.of("forecast", Map.of("city", "Oslo", "days", 2)));
            // tour:end
            return Result.of("forecast(Oslo, 2 days) → " + outcome.text(), outcome.text(), rap);
        }
    }

    private Result rapSubscribe(String base) {
        try (RapClientConnector rap = rap(base)) {
            // tour:begin rap-subscribe
            ToolOutcome outcome = rap.invoke(ToolInvocation.of("updates", Map.of("city", "Oslo")));
            List<ToolEvent> events = collect(outcome);   // the events that keep arriving after the result
            // tour:end
            List<String> texts = events.stream().map(e -> e.text() + (e.last() ? " (final)" : "")).toList();
            return Result.of(outcome.text() + ", then " + events.size() + " events", texts, rap);
        }
    }

    private static List<ToolEvent> collect(ToolOutcome outcome) {
        List<ToolEvent> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        outcome.subscription().orElseThrow().events().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ToolEvent event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.orTimeout(30, TimeUnit.SECONDS).join();
        return events;
    }

    // ── 5 · AG-UI (the page does the same from the browser with @ag-ui/client) ─────────────────

    private Result aguiChat(String base) {
        return agui(base, "What's the temperature in Oslo?");
    }

    private Result aguiTheme(String base) {
        return agui(base, "Make the page teal");
    }

    private Result agui(String base, String text) {
        List<String> events = new ArrayList<>();
        // tour:begin agui-thread
        AgUiThread thread = AgUiThread.builder(AgUiClient.builder(base + "/agui").build())
                .listener(event -> events.add(event.getClass().getSimpleName()))
                // The client's own tool: the agent may call it, and the client answers on the next run.
                .clientTools(List.of(new RunAgentInput.Tool("set_theme", "Change the page's accent colour",
                                Map.of("type", "object", "properties", Map.of("color", Map.of("type", "string"))), null)),
                        call -> Optional.of("The page is now " + call.arguments().replaceAll(".*\"color\":\"([^\"]+)\".*", "$1") + "."))
                .build();
        AgUiThread.Turn turn = thread.send(text);   // POST /agui, events stream back as SSE
        // tour:end
        return new Result(turn.status() + ": " + turn.text().strip(), Map.of("events", events), List.of());
    }

    // ── 6 · One registry ──────────────────────────────────────────────────────────────────────

    private record Sources(McpClientConnector mcp, UtcpClientConnector utcp, A2aAgentConnector a2a,
                           RapClientConnector rap, ToolRegistry registry) implements AutoCloseable {
        @Override
        public void close() {
            registry.close();
        }
    }

    private Sources sources(String base) {
        // tour:begin registry
        McpClientConnector mcp = McpClientConnector.http(base).build();
        UtcpClientConnector utcp = UtcpClientConnector.http("weather", base + "/utcp").build();
        A2aAgentConnector a2a = A2aAgentConnector.builder(base).build();
        RapClientConnector rap = RapClientConnector.builder(base + "/rap").callbacks(rapCallbacks).build();
        for (ProtocolConnector c : List.of(mcp, utcp, a2a, rap)) {
            c.start();
        }
        ToolRegistry registry = new ToolRegistry()   // tools are named <source>.<tool>
                .register("mcp", mcp).register("utcp", utcp).register("a2a", a2a).register("rap", rap);
        // tour:end
        return new Sources(mcp, utcp, a2a, rap, registry);
    }

    private Result registryList(String base) {
        try (Sources s = sources(base)) {
            List<ToolSpec> tools = s.registry().listTools();
            return Result.of(tools.size() + " tools from four protocols", tools.stream().map(ToolSpec::name).toList(),
                    s.mcp(), s.utcp(), s.a2a(), s.rap());
        }
    }

    private Result registryCall(String base) {
        try (Sources s = sources(base)) {
            ToolRegistry registry = s.registry();
            Map<String, String> answers = new LinkedHashMap<>();
            // tour:begin registry-call
            answers.put("mcp.temperature", registry.invoke(ToolInvocation.of("mcp.temperature", Map.of("city", "Oslo"))).text());
            answers.put("utcp.temperature", registry.invoke(ToolInvocation.of("utcp.temperature",
                    Map.of("body", Map.of("city", "Oslo")))).text());
            answers.put("rap.temperature", registry.invoke(ToolInvocation.of("rap.temperature", Map.of("city", "Oslo"))).text());
            answers.put("a2a.ask", registry.invoke(ToolInvocation.of("a2a.ask",
                    Map.of("message", "What's the temperature in Oslo?"))).text().strip());   // the agent, as a tool
            // tour:end
            return Result.of("The same answer through MCP, UTCP, RAP and the A2A agent", answers,
                    s.mcp(), s.utcp(), s.a2a(), s.rap());
        }
    }

    private Result registryCompliance(String base) {
        try (Sources s = sources(base);
             AgUiClientConnector agui = AgUiClientConnector.builder(base + "/agui").build()) {
            agui.start();
            return Result.of("What each connector speaks, negotiated and supports", null,
                    s.mcp(), s.utcp(), s.a2a(), s.rap(), agui);
        }
    }

    private static String names(List<ToolSpec> tools) {
        return String.join(", ", tools.stream().map(ToolSpec::name).toList());
    }

    private static List<Map<String, Object>> specs(List<ToolSpec> tools) {
        return tools.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            m.put("description", t.description());
            m.put("inputSchema", t.inputSchema());
            return m;
        }).toList();
    }
}
