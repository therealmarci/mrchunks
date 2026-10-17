plugins {
    java
    id("com.modrinth.minotaur") version "2.10.0"
}

group = "at.marcelr"
// version kommt aus gradle.properties

val modrinthProjectId: String by project
val githubRepository: String by project
val supportedGameVersions: String by project
val isPrerelease = project.version.toString().let { it.startsWith("0.") || it.contains("-") }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.200-stable")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Unit-Tests für die server-unabhängige Logik (src/test/java). Laufen bei jedem "build".
tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "modrinthProjectId" to modrinthProjectId,
        "githubRepository" to githubRepository,
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "build-info.properties")) {
        expand(props)
    }
}

// Veröffentlichung auf Modrinth: ./gradlew modrinth (braucht die Umgebungsvariable MODRINTH_TOKEN).
// Normalerweise erledigt das der GitHub-Workflow .github/workflows/release.yml beim Pushen eines Tags.
modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(modrinthProjectId)
    versionNumber.set(project.version.toString())
    versionName.set("MRchunks ${project.version}")
    // 0.x und Versionen mit Suffix (1.0.0-rc.1) sind Vorabversionen -> auf Modrinth als "beta".
    versionType.set(if (isPrerelease) "beta" else "release")
    uploadFile.set(tasks.jar)
    gameVersions.addAll(supportedGameVersions.split(",").map { it.trim() })
    loaders.addAll("paper", "purpur")
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("Siehe CHANGELOG.md"))
}
