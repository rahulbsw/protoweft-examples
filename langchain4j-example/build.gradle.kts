plugins {
    application
}

dependencies {
    implementation(platform("io.github.rahulbsw:protoweft-bom:${project.property("protoweftVersion")}"))
    implementation("io.github.rahulbsw:protoweft-langchain4j")
    implementation("io.github.rahulbsw:protoweft-a2a")
    implementation("io.github.rahulbsw:protoweft-server-jetty")
    // Any LangChain4j chat model works; OpenAI's also talks to OpenAI-compatible servers (e.g. Ollama).
    implementation("dev.langchain4j:langchain4j-open-ai:${project.property("langchain4jVersion")}")
    runtimeOnly("org.slf4j:slf4j-simple:${project.property("slf4jVersion")}")
}

application {
    mainClass.set("io.github.rahulbsw.protoweft.examples.langchain4j.LangChain4jExample")
}
