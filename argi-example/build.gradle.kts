// Spring Boot application: an ARGI ReactAgent that uses Protoweft tools picked by the capability
// catalog, served over A2A with human approval of a sensitive tool.
plugins {
    id("org.springframework.boot")
}

val springAiVersion = project.property("springAiVersion")

dependencies {
    implementation(platform("io.github.rahulbsw:protoweft-bom:${project.property("protoweftVersion")}"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.property("springBootVersion")}"))
    implementation("io.github.rahulbsw:protoweft-argi")
    // The starter scans @AgentTool beans into the ToolRegistry, builds the CapabilityCatalog
    // (protoweft.discovery.enabled) and serves the AgentHandler bean over A2A.
    implementation("io.github.rahulbsw:protoweft-spring-boot-starter")
    implementation("io.github.rahulbsw:protoweft-discovery")
    implementation("io.github.rahulbsw:protoweft-a2a")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Any Spring AI chat model works; OpenAI's also talks to OpenAI-compatible servers (e.g. Ollama).
    implementation("org.springframework.ai:spring-ai-starter-model-openai:$springAiVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
