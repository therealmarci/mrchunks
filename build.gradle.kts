plugins {
    java
    id("com.modrinth.minotaur") version "2.10.0"
}

group = "at.marcelr"

// Versionen kommen aus gradle.properties: "version" ist die Plugin-Version (SemVer), "mcVersion" die
// Minecraft-Version dieses Branches. Veröffentlicht wird beides zusammen, z.B. "mc26.3-v1.2.0".
val pluginVersion = project.version.toString()
val mcVersion: String by project
val paperApi: String by project
version = "mc$mcVersion-v$pluginVersion"

val modrinthProjectId: String by project
val githubRepository: String by project
val supportedGameVersions: String by project
val isPrerelease = pluginVersion.let { it.startsWith("0.") || it.contains("-") }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    // Optional: echte Webkarte. Wird nur genutzt, wenn squaremap auf dem Server installiert ist.
    compileOnly("xyz.jpenilla:squaremap-api:1.4.3")

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
        // In der plugin.yml als SemVer mit Build-Info ("1.2.0+mc26.3"), weil Paper selbst ein "v" davorsetzt.
        "version" to "$pluginVersion+mc$mcVersion",
        "pluginVersion" to pluginVersion,
        "mcVersion" to mcVersion,
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
    versionName.set("MRchunks $pluginVersion (MC $mcVersion)")
    // 0.x und Versionen mit Suffix (1.0.0-rc.1) sind Vorabversionen -> auf Modrinth als "beta".
    versionType.set(if (isPrerelease) "beta" else "release")
    uploadFile.set(tasks.jar)
    gameVersions.addAll(supportedGameVersions.split(",").map { it.trim() })
    loaders.addAll("paper", "purpur")
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("Siehe CHANGELOG.md"))
}
