// Spring Boot application: a Spring AI ChatClient that uses Protoweft tools, served over A2A.
plugins {
    id("org.springframework.boot")
}

val springAiVersion = project.property("springAiVersion")

dependencies {
    implementation(platform("io.github.rahulbsw:protoweft-bom:${project.property("protoweftVersion")}"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.property("springBootVersion")}"))
    implementation("io.github.rahulbsw:protoweft-spring-ai")
    // The starter scans @AgentTool beans, builds the ToolRegistry (plus MCP clients from config)
    // and serves the AgentHandler bean over A2A.
    implementation("io.github.rahulbsw:protoweft-spring-boot-starter")
    implementation("io.github.rahulbsw:protoweft-a2a")
    implementation("io.github.rahulbsw:protoweft-mcp")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Any Spring AI chat model works; OpenAI's also talks to OpenAI-compatible servers (e.g. Ollama).
    implementation("org.springframework.ai:spring-ai-starter-model-openai:$springAiVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
