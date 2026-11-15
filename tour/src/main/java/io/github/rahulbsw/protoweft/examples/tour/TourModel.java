package io.github.rahulbsw.protoweft.examples.tour;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;

/**
 * Which chat model the agent uses. {@code tour.model=auto} (the default) picks the scripted model
 * when {@code OPENAI_API_KEY} is empty and the OpenAI model (any OpenAI-compatible server) when it
 * is set; {@code scripted} or {@code openai} force one.
 */
public record TourModel(boolean scripted, String openAiModel) {

    public String describe() {
        return scripted ? "scripted (no LLM key needed)" : "OpenAI-compatible, " + openAiModel;
    }

    static boolean scripted(Environment env) {
        String mode = env.getProperty("tour.model", "auto").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "scripted" -> true;
            case "openai" -> false;
            default -> env.getProperty("spring.ai.openai.api-key", "").isBlank();
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class Setup {

        @Bean
        TourModel tourModel(Environment env) {
            return new TourModel(scripted(env), env.getProperty("spring.ai.openai.chat.options.model", ""));
        }

        /** Takes the OpenAI model's place when the tour runs scripted. */
        @Bean
        @Primary
        @Conditional(ScriptedSelected.class)
        ChatModel scriptedChatModel() {
            return new ScriptedChatModel();
        }
    }

    static final class ScriptedSelected implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return scripted(context.getEnvironment());
        }
    }
}
