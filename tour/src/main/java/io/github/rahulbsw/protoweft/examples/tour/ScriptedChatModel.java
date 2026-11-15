package io.github.rahulbsw.protoweft.examples.tour;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for an LLM, so the tour works without an API key and answers the same way every
 * time (which the replay recordings rely on). It reads the last message and either calls one of
 * the offered tools or answers from a tool's result:
 * <ul>
 *   <li>a colour ("make the page teal") with {@code set_theme} offered → calls {@code set_theme};</li>
 *   <li>"forecast" → {@code local__forecast};</li>
 *   <li>"weather", "temperature" or "in &lt;City&gt;" → {@code local__temperature};</li>
 *   <li>a tool result → a sentence about it.</li>
 * </ul>
 */
public final class ScriptedChatModel implements ChatModel {

    private static final Pattern CITY = Pattern.compile("\\bin ([A-Z][a-zA-Z]+)");
    private static final List<String> COLOURS = List.of("teal", "purple", "orange", "crimson", "navy", "green",
            "blue", "red", "pink", "gold");

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        Message last = messages.get(messages.size() - 1);
        AssistantMessage answer = last instanceof ToolResponseMessage tool
                ? AssistantMessage.builder().content(report(tool, messages)).build()
                : decide(last.getText(), offered(prompt));
        return new ChatResponse(List.of(new Generation(answer)));
    }

    /** Streams the text word by word, like a real model; tool calls come in one chunk. */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        ChatResponse response = call(prompt);
        if (response.hasToolCalls()) {
            return Flux.just(response);
        }
        return Flux.fromArray(response.getResult().getOutput().getText().split("(?<= )"))
                .map(w -> new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(w).build()))));
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    private static AssistantMessage decide(String text, List<String> tools) {
        String lower = text.toLowerCase(Locale.ROOT);
        Optional<String> colour = COLOURS.stream().filter(lower::contains).findFirst();
        if (colour.isPresent() && tools.contains("set_theme")) {
            return call("set_theme", "{\"color\":\"" + colour.get() + "\"}");
        }
        Optional<String> city = city(text);
        if (lower.contains("forecast") && tools.contains("local__forecast")) {
            return call("local__forecast", "{\"city\":\"" + city.orElse("Oslo") + "\",\"days\":3}");
        }
        if ((lower.contains("weather") || lower.contains("temperature") || city.isPresent())
                && tools.contains("local__temperature")) {
            return call("local__temperature", "{\"city\":\"" + city.orElse("Oslo") + "\"}");
        }
        return AssistantMessage.builder()
                .content("I can look up temperatures and forecasts. Try: What's the weather in Oslo?").build();
    }

    private static String report(ToolResponseMessage tool, List<Message> messages) {
        ToolResponseMessage.ToolResponse response = tool.getResponses().get(0);
        String data = response.responseData();
        String args = messages.stream().filter(AssistantMessage.class::isInstance).map(AssistantMessage.class::cast)
                .flatMap(m -> m.getToolCalls().stream()).filter(c -> c.id().equals(response.id()))
                .map(AssistantMessage.ToolCall::arguments).reduce((a, b) -> b).orElse("");
        String city = args.replaceAll(".*\"city\":\"([^\"]+)\".*", "$1");
        return switch (response.name()) {
            case "local__temperature" -> "It's " + data + " °C in " + city + " right now.";
            case "local__forecast" -> "Here's the forecast for " + city + ": " + data;
            case "set_theme" -> "Done. " + data;
            default -> "The tool answered: " + data;
        };
    }

    private static Optional<String> city(String text) {
        Matcher m = CITY.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static List<String> offered(Prompt prompt) {
        return prompt.getOptions() instanceof ToolCallingChatOptions options
                ? options.getToolCallbacks().stream().map(ToolCallback::getToolDefinition).map(d -> d.name()).toList()
                : List.of();
    }

    private static AssistantMessage call(String tool, String arguments) {
        return AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", tool, arguments))).build();
    }
}
