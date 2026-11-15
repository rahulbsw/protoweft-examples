package io.github.rahulbsw.protoweft.examples.tour;

import io.github.rahulbsw.protoweft.examples.tour.capture.WireCapture;
import io.github.rahulbsw.protoweft.examples.tour.steps.TourRunner;
import io.github.rahulbsw.protoweft.examples.tour.steps.TourSteps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Records every step for the page's replay mode (GitHub Pages, or the app stopped): runs it with
 * the scripted model and writes {@code frontend/public/replay/<step>.json}. Ids, tokens, ports and
 * times are replaced by stable placeholders, and JSON object keys are sorted (Java maps have no fixed
 * order between runs), so recording again gives the same files unless the protocols' traffic really
 * changed (CI checks that with git diff).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "tour.model=scripted")
class RecordReplayTest {

    @Autowired
    TourSteps steps;

    @Autowired
    TourRunner runner;

    @Autowired
    JsonMapper json;

    private JsonMapper sorted;

    @Test
    void recordEveryStep() throws Exception {
        sorted = json.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
        String dir = System.getProperty("tour.replayDir");
        Path out = dir == null ? Files.createTempDirectory("replay") : Path.of(dir);
        Files.createDirectories(out);
        String port = steps.baseUrl().replaceAll(".*:", "");
        List<IndexEntry> index = new ArrayList<>();
        for (TourSteps.Step step : steps.all()) {
            TourRunner.Run run = runner.run(step);
            assertThat(run.ok()).as(step.id() + ": " + run.error()).isTrue();
            write(out.resolve(step.id() + ".json"), normalize(sorted.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(renumber(run)), port));
            index.add(new IndexEntry(step.id(), step.chapter(), step.title()));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("model", "scripted");
        meta.put("steps", index);
        write(out.resolve("index.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
    }

    record IndexEntry(String id, String chapter, String title) {
    }

    /**
     * Exchange numbers start at 1 in each recording; JSON bodies get sorted keys. The MCP client
     * opens its notification stream (GET /mcp) in parallel with notifications/initialized, so the
     * stream is put right after the initialize request it follows.
     */
    private TourRunner.Run renumber(TourRunner.Run run) {
        List<WireCapture.Exchange> ordered = new ArrayList<>(run.exchanges());
        List<WireCapture.Exchange> streams = ordered.stream()
                .filter(e -> e.protocol().equals("MCP") && e.method().equals("GET")).toList();
        ordered.removeAll(streams);
        int at = 0;
        for (WireCapture.Exchange stream : streams) {
            while (at < ordered.size() && !(ordered.get(at).protocol().equals("MCP")
                    && ordered.get(at).requestBody().contains("\"initialize\""))) {
                at++;
            }
            at = Math.min(at + 1, ordered.size());
            ordered.add(at, stream);
        }
        List<WireCapture.Exchange> exchanges = new ArrayList<>();
        for (WireCapture.Exchange e : ordered) {
            exchanges.add(new WireCapture.Exchange(exchanges.size() + 1, e.protocol(), e.direction(), e.method(), e.path(),
                    e.status(), e.requestHeaders(), canonical(e.requestBody()), e.responseHeaders(), canonical(e.responseBody()), 0,
                    e.complete()));
        }
        return new TourRunner.Run(run.stepId(), run.ok(), run.summary(), run.data(), run.error(), exchanges, run.compliance());
    }

    /** A JSON body, or each JSON event of an SSE body, with sorted keys; anything else unchanged. */
    private String canonical(String body) {
        if (body.startsWith("{") || body.startsWith("[")) {
            return sortedJson(body);
        }
        return Pattern.compile("(?m)^data:(.*)$").matcher(body).replaceAll(m -> Matcher.quoteReplacement(
                "data:" + sortedJson(m.group(1).strip())));
    }

    private String sortedJson(String text) {
        try {
            return sorted.writeValueAsString(sorted.readValue(text, Object.class));
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern MCP_ID = Pattern.compile("\\b([0-9a-f]{8})-(\\d+)\\b(?!-)");
    private static final Pattern RAP_ID = Pattern.compile("pw([-_])[0-9a-f]{32}");
    private static final Pattern CALLBACK = Pattern.compile("/rap/callback/[A-Za-z0-9_-]{16,}");
    private static final Pattern ISO_TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    private static final Pattern EPOCH = Pattern.compile("(\\\\?\"timestamp\\\\?\"\\s*:\\s*)\\d{10,}");

    /** Replaces everything that differs between runs with placeholders numbered by first appearance. */
    static String normalize(String text, String port) {
        Map<String, String> seen = new HashMap<>();
        text = text.replace("localhost:" + port, "localhost:8080");
        text = replace(text, UUID, m -> seen.computeIfAbsent(m.group(), k ->
                String.format("00000000-0000-4000-8000-%012d", seen.size() + 1)));
        text = replace(text, RAP_ID, m -> seen.computeIfAbsent(m.group(), k ->
                "pw" + m.group(1) + String.format("%032d", seen.size() + 1)));
        text = replace(text, CALLBACK, m -> seen.computeIfAbsent(m.group(), k ->
                "/rap/callback/token-" + (seen.size() + 1)));
        text = replace(text, MCP_ID, m -> seen.computeIfAbsent(m.group(1), k ->
                String.format("%08d", seen.size() + 1)) + "-" + m.group(2));
        text = ISO_TIME.matcher(text).replaceAll("2026-01-01T00:00:00Z");
        return EPOCH.matcher(text).replaceAll("$11767225600000");
    }

    private static String replace(String text, Pattern pattern, java.util.function.Function<MatchResult, String> with) {
        return pattern.matcher(text).replaceAll(m -> Matcher.quoteReplacement(with.apply(m)));
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content + "\n");
    }
}
