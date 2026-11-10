plugins {
    java
    // Server-Interna (NMS) nur für die Dummy-Spieler (at.marcelr.mrchunks.dummy.nms). Seit 26.1 läuft der Server
    // mit Mojang-Namen, ein Remapping der fertigen JAR ist nicht nötig.
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
    id("com.modrinth.minotaur") version "2.10.0"
}

group = "at.marcelr"

// Versionen kommen aus gradle.properties: "version" ist die Plugin-Version (STA-1.4.0-bu001 bzw. BET-..., siehe
// docs/RELEASES.md; ältere Versionen "1.3.1"), "mcVersion" die
// Minecraft-Version dieses Branches. Veröffentlicht wird beides zusammen, z.B. "mc26.3-STA-1.4.0-bu001".
val pluginVersion = project.version.toString()
val mcVersion = providers.gradleProperty("mcVersion").get()
val paperApi = providers.gradleProperty("paperApi").get()
val mradminApi = providers.gradleProperty("mradminApi").get()
val newScheme = Regex("(?i)^(STA|BET)-.+").matches(pluginVersion)
version = if (newScheme) "mc$mcVersion-$pluginVersion" else "mc$mcVersion-v$pluginVersion"

val modrinthProjectId = providers.gradleProperty("modrinthProjectId").get()
val githubRepository = providers.gradleProperty("githubRepository").get()
val website = providers.gradleProperty("website").get()
val websiteSlug = providers.gradleProperty("websiteSlug").get()
val supportedGameVersions = providers.gradleProperty("supportedGameVersions").get()
// BET = Beta; ältere Versionen: 0.x und Versionen mit Suffix (1.1.0-rc.1)
val isPrerelease = if (newScheme) pluginVersion.startsWith("BET", ignoreCase = true)
    else pluginVersion.let { it.startsWith("0.") || it.contains("-") }

repositories {
    // Lokal gebaute MRadmin-API (gradlew :api:publishToMavenLocal in MRadmin) hat Vorrang vor JitPack.
    mavenLocal {
        content { includeGroup("com.github.therealmarci") }
    }
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") {
        content { includeGroupByRegex("com\\.github\\.therealmarci.*") }
    }
}

dependencies {
    // Paper-API plus Server-Klassen (für die Dummy-Spieler).
    paperweight.paperDevBundle(paperApi)
    // Optional: MRadmin (Modul-API). Nur zum Kompilieren - zur Laufzeit kommen die Klassen aus MRadmin, und nur wenn es
    // installiert ist (siehe api/MRadminLink).
    compileOnly("com.github.therealmarci:mradmin:$mradminApi")
    // Optional: loads the LuckPerms user of a dummy before it joins (chat and tab plugins ask for it).
    compileOnly("net.luckperms:api:5.5")

    // Für den Test des Web-Formulars der config.yml (YamlConfiguration).
    testImplementation("io.papermc.paper:paper-api:$paperApi")
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

// Commit and branch of this build, for MRadmin's plugin page (dev channel, "Dev <commit>"): from GitHub Actions,
// otherwise from the local git - so a local test build is recognised as a dev state and does not look like the last
// release. Empty without git.
fun git(vararg args: String): String = runCatching {
    providers.exec { commandLine("git", *args); isIgnoreExitValue = true }.standardOutput.asText.get().trim()
}.getOrDefault("")
val gitCommit: String = System.getenv("GITHUB_SHA") ?: git("rev-parse", "HEAD")
val gitBranch: String = System.getenv("GITHUB_REF_NAME") ?: git("rev-parse", "--abbrev-ref", "HEAD")

tasks.processResources {
    val props = mapOf(
        // In der plugin.yml als SemVer mit Build-Info ("1.2.0+mc26.3"), weil Paper selbst ein "v" davorsetzt.
        "version" to "$pluginVersion+mc$mcVersion",
        "pluginVersion" to pluginVersion,
        "mcVersion" to mcVersion,
        "modrinthProjectId" to modrinthProjectId,
        "githubRepository" to githubRepository,
        "website" to website,
        "websiteSlug" to websiteSlug,
        "gitCommit" to gitCommit,
        "gitBranch" to gitBranch,
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
