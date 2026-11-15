package io.github.rahulbsw.protoweft.examples.tour;

import io.github.rahulbsw.protoweft.agui.AgUi;
import io.github.rahulbsw.protoweft.agui.AgUiAgent;
import io.github.rahulbsw.protoweft.core.agent.AgentDescriptor;
import io.github.rahulbsw.protoweft.core.agent.AgentHandler;
import io.github.rahulbsw.protoweft.core.tool.ToolRegistry;
import io.github.rahulbsw.protoweft.springai.ProtoweftToolCallbacks;
import io.github.rahulbsw.protoweft.springai.SpringAiAgents;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The agent of the tour: a Spring AI ChatClient that uses the Protoweft tools. The starter serves
 * the {@link AgentHandler} bean over A2A and the {@link AgUiAgent} bean over AG-UI.
 */
@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    private static final String SYSTEM = "You are a concise weather assistant. Use local__temperature and "
            + "local__forecast for weather questions. When asked to change the page's colour, call set_theme.";

    // tour:begin agent
    /** A2A: one conversation per A2A context, remembered by the chat-memory advisor. */
    @Bean
    public AgentHandler weatherAgent(ChatClient.Builder builder, ChatMemory memory, ToolRegistry registry) {
        ChatClient chat = builder.clone()
                .defaultSystem(SYSTEM)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultTools(ProtoweftToolCallbacks.from(registry))
                .build();
        return SpringAiAgents.streamingHandler(chat);
    }

    /** AG-UI: the browser sends the whole conversation on every run, so no memory here. */
    @Bean
    public AgUiAgent webAgent(ChatClient.Builder builder, ToolRegistry registry) {
        ChatClient chat = builder.clone()
                .defaultSystem(SYSTEM)
                .defaultTools(ProtoweftToolCallbacks.from(registry))
                .build();
        return AgUi.agent(SpringAiAgents.streamingHandler(chat));
    }
    // tour:end

    @Bean
    public AgentDescriptor weatherAgentCard() {
        return SpringAiAgents.descriptor("Weather agent", "Answers weather questions with Protoweft tools", "1.0.0",
                SpringAiAgents.skill("weather", "Weather", "Current temperature and forecasts for a city", "weather"));
    }
}
