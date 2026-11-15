package io.github.rahulbsw.protoweft.examples.tour;

import io.github.rahulbsw.protoweft.examples.tour.capture.WireCapture;
import io.github.rahulbsw.protoweft.examples.tour.steps.TourRunner;
import io.github.rahulbsw.protoweft.examples.tour.steps.TourSteps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Every step of the tour, against the running application with the scripted model. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "tour.model=scripted")
class TourStepsTest {

    @Autowired
    TourSteps steps;

    @Autowired
    TourRunner runner;

    private TourRunner.Run run(String id) throws InterruptedException {
        TourRunner.Run run = runner.run(steps.find(id).orElseThrow());
        assertThat(run.ok()).as(id + ": " + run.error()).isTrue();
        return run;
    }

    private static List<String> protocols(TourRunner.Run run) {
        return run.exchanges().stream().map(WireCapture.Exchange::protocol).distinct().toList();
    }

    @Test
    void everyStepRunsAndShowsItsOwnTraffic() throws InterruptedException {
        for (TourSteps.Step step : steps.all()) {
            TourRunner.Run run = run(step.id());
            assertThat(run.exchanges()).as(step.id()).isNotEmpty();
            assertThat(run.exchanges()).as(step.id()).allSatisfy(e -> assertThat(e.complete()).isTrue());
        }
    }

    @Test
    void mcpCallsTheToolOverMcpOnly() throws InterruptedException {
        TourRunner.Run run = run("mcp.call");
        assertThat(run.summary()).matches("temperature\\(city=Oslo\\) → \\d+\\.\\d");
        assertThat(protocols(run)).containsExactly("MCP");
        assertThat(run.exchanges()).anySatisfy(e -> assertThat(e.requestBody()).contains("\"tools/call\""));
        assertThat(run.compliance()).singleElement().satisfies(c -> assertThat(c.protocol()).isEqualTo("MCP"));
    }

    @Test
    void theA2aAgentUsesTheToolAndRemembersTheConversation() throws InterruptedException {
        assertThat(run("a2a.ask").summary()).matches("Task COMPLETED: It's \\d+\\.\\d °C in Oslo right now\\.");
        assertThat(run("a2a.followup").summary()).matches("Task COMPLETED: It's \\d+\\.\\d °C in Bergen right now\\.");
    }

    @Test
    void rapResultsAndEventsComeBackAsCallbacks() throws InterruptedException {
        TourRunner.Run run = run("rap.subscribe");
        assertThat(run.summary()).isEqualTo("Watching Oslo, then 3 events");
        assertThat(run.exchanges()).filteredOn(e -> e.direction().equals("callback")).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void aguiStreamsTextAndCallsThePagesTool() throws InterruptedException {
        TourRunner.Run chat = run("agui.chat");
        assertThat(chat.summary()).matches("COMPLETED: It's \\d+\\.\\d °C in Oslo right now\\.");
        assertThat(chat.exchanges()).anySatisfy(e -> assertThat(e.responseBody()).contains("TOOL_CALL_START"));
        assertThat(run("agui.theme").summary()).isEqualTo("COMPLETED: Done. The page is now teal.");
    }

    @Test
    void oneRegistryGivesTheSameAnswerThroughEveryProtocol() throws InterruptedException {
        TourRunner.Run run = run("registry.call");
        @SuppressWarnings("unchecked")
        Map<String, String> answers = (Map<String, String>) run.data();
        String t = answers.get("mcp.temperature");
        assertThat(answers.get("utcp.temperature")).isEqualTo(t);
        assertThat(answers.get("rap.temperature")).isEqualTo(t);
        assertThat(answers.get("a2a.ask")).contains(t + " °C");
        assertThat(protocols(run)).contains("MCP", "UTCP", "A2A", "RAP");
        assertThat(run("registry.compliance").compliance()).extracting(TourRunner.Compliance::protocol)
                .containsExactly("MCP", "UTCP", "A2A", "RAP", "AG-UI");
    }
}
