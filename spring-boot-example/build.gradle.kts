// Spring Boot application serving one set of @AgentTool methods over MCP, UTCP and A2A.
plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(platform("io.github.rahulbsw:protoweft-bom:${project.property("protoweftVersion")}"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.property("springBootVersion")}"))
    implementation("io.github.rahulbsw:protoweft-spring-boot-starter")
    // The protocols this application serves; the starter auto-configures only those present.
    implementation("io.github.rahulbsw:protoweft-mcp")
    implementation("io.github.rahulbsw:protoweft-utcp")
    implementation("io.github.rahulbsw:protoweft-a2a")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
