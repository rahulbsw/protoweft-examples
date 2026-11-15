// Protoweft Tour: a browser tutorial and live demo of MCP, UTCP, A2A, RAP and AG-UI, served by one
// Spring Boot application. The page is built separately with npm into src/main/resources/static
// (see README.md or run ./tour/run.sh).
plugins {
    id("org.springframework.boot")
}

val springAiVersion = project.property("springAiVersion")

dependencies {
    implementation(platform("io.github.rahulbsw:protoweft-bom:${project.property("protoweftVersion")}"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.property("springBootVersion")}"))
    implementation("io.github.rahulbsw:protoweft-spring-boot-starter")
    implementation("io.github.rahulbsw:protoweft-spring-ai")
    implementation("io.github.rahulbsw:protoweft-mcp")
    implementation("io.github.rahulbsw:protoweft-utcp")
    implementation("io.github.rahulbsw:protoweft-a2a")
    implementation("io.github.rahulbsw:protoweft-rap")
    implementation("io.github.rahulbsw:protoweft-agui")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Used only when OPENAI_API_KEY is set; otherwise a scripted model answers (ScriptedChatModel).
    implementation("org.springframework.ai:spring-ai-starter-model-openai:$springAiVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// RecordReplayTest writes the replay recordings the page plays without a server.
// Declared as an output, so Gradle re-runs the tests when the recordings were edited and restores
// them from the build cache on a hit, rather than skipping the recording.
tasks.withType<Test>().configureEach {
    val replay = layout.projectDirectory.dir("frontend/public/replay")
    systemProperty("tour.replayDir", replay.asFile.absolutePath)
    outputs.dir(replay)
}
