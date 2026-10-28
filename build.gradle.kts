import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.plugin.deployer)
    `maven-publish`
}

group = "pl.syntaxdevteam"
version = "1.0.1-R0.1-SNAPSHOT"
description = "Lightweight and versatile player plot protection plugin"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc-repo" }
    maven("https://oss.sonatype.org/content/groups/public/") { name = "sonatype" }
    maven("https://maven.playpro.com/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.essentialsx.net/releases/")
    maven("https://repo.leavesmc.org/snapshots/") { name = "leavesmc-repo" }
}

dependencies {
    testImplementation(libs.aether.api)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.paper.api)
    testImplementation(libs.junit4)
    testImplementation(libs.syntaxdb)
    testImplementation(libs.syntaxcore)
    // Direct JDBC dependencies remain test-only for low-level persistence contract tests.
    testImplementation(libs.sqlite)
    testRuntimeOnly(libs.mariadb)
    testRuntimeOnly(libs.postgresql)

    compileOnly(libs.paper.api)
    compileOnly(libs.syntaxcore)
    compileOnly(libs.syntaxdb)
    compileOnly(libs.messagehandler.paper)
    compileOnly(libs.cleanerx)
    compileOnly(libs.aether.api)
    compileOnly(libs.snakeyaml)
    compileOnly(libs.gson)
    compileOnly(libs.adventure.legacy)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.gson)
    compileOnly(libs.adventure.plain)
    compileOnly(libs.adventure.ansi)
    compileOnly(libs.luckperms)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.miniplaceholders)
    compileOnly(libs.vault)
    compileOnly(libs.vault.unlocked)
    compileOnly(libs.coreprotect)
    compileOnly(libs.worldguard.bukkit) { isTransitive = false }
    compileOnly(libs.worldguard.core) { isTransitive = false }
    // WorldEdit 7.4.x is compiled for Java 25. PlotsX still targets Java 21.
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }
}

kotlin { jvmToolchain(21) }

tasks {
    build { dependsOn("shadowJar") }
    runServer {
        minecraftVersion("1.20.6")
        runDirectory(file("run/paper"))
    }
    runPaper.folia.registerTask()
}

val runtimeLibraryVersions = mapOf(
    "kotlinVersion" to libs.versions.kotlin.get(),
    "syntaxcoreVersion" to libs.versions.syntaxcore.get(),
    "syntaxdbVersion" to libs.versions.syntaxdb.get(),
    "messagehandlerVersion" to libs.versions.messagehandler.get(),
    "caffeineVersion" to libs.versions.caffeine.get(),
    "aetherVersion" to libs.versions.aether.get(),
    "snakeyamlVersion" to libs.versions.snakeyaml.get(),
    "gsonVersion" to libs.versions.gson.get(),
    "adventureVersion" to libs.versions.adventure.get(),
    "antVersion" to libs.versions.ant.get(),
)

tasks.processResources {
    val props = mapOf("version" to project.version, "description" to project.description)
    inputs.properties(props + runtimeLibraryVersions)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") { expand(props) }
    filesMatching("paper-libraries.yml") { expand(runtimeLibraryVersions) }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PlotsX-Paper")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Kotlin belongs to Paper's library classloader together with SyntaxDB, not the plugin JAR.
    dependencies { exclude(dependency("org.jetbrains.kotlin:.*:.*")) }
    mergeServiceFiles()
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    filesMatching("META-INF/*.kotlin_module") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds every currently supported PlotsX platform artifact."
    dependsOn(tasks.named("build"))
}

val apiJar = tasks.register<Jar>("apiJar") {
    group = "build"
    description = "Generates a compile-only API jar for PlotsX integration authors."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("pl/syntaxdevteam/plotsx/api/**")
        exclude("pl/syntaxdevteam/plotsx/api/internal/**")
    }
}
val apiSourcesJar = tasks.register<Jar>("apiSourcesJar") {
    group = "build"
    description = "Generates sources for the public PlotsX API only."
    archiveClassifier.set("api-sources")
    from(sourceSets.main.get().allSource) {
        include("pl/syntaxdevteam/plotsx/api/**")
        exclude("pl/syntaxdevteam/plotsx/api/internal/**")
    }
}
tasks.named("assemble") { dependsOn(apiJar, apiSourcesJar) }

publishing {
    publications {
        create<MavenPublication>("plotsxApi") {
            artifactId = "plotsx-api"
            // Publish only public contracts, never the server plugin or its implementation dependencies.
            artifact(apiJar) { classifier = null }
            artifact(apiSourcesJar) { classifier = "sources" }
            pom {
                name.set("PlotsX API")
                description.set("Public API for Java and Kotlin integrations with PlotsX")
                url.set("https://github.com/SyntaxDevTeam/PlotsX")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("WieszczY85")
                        name.set("WieszczY")
                    }
                }
                // Kotlin is part of the API signatures; Paper remains provided by the consumer's server.
                withXml {
                    val dependency = asNode().appendNode("dependencies").appendNode("dependency")
                    dependency.appendNode("groupId", "org.jetbrains.kotlin")
                    dependency.appendNode("artifactId", "kotlin-stdlib")
                    dependency.appendNode("version", libs.versions.kotlin.get())
                    dependency.appendNode("scope", "compile")
                }
            }
        }
    }
    repositories {
        maven {
            name = "Nexus"
            val repository = if (project.version.toString().endsWith("-SNAPSHOT")) "maven-snapshots" else "maven-releases"
            url = uri("https://nexus.syntaxdevteam.pl/repository/$repository/")
            credentials {
                username = providers.gradleProperty("nexusUser").orElse(providers.environmentVariable("NEXUS_USERNAME")).orNull
                password = providers.gradleProperty("nexusPassword").orElse(providers.environmentVariable("NEXUS_PASSWORD")).orNull
            }
        }
    }
}

val verifyApiPackaging = tasks.register("verifyApiPackaging") {
    group = "verification"
    description = "Checks that the published API contains public contracts without plugin implementation or runtime libraries."
    val artifact = apiJar.flatMap { it.archiveFile }
    inputs.file(artifact)
    dependsOn(apiJar)
    doLast {
        ZipFile(artifact.get().asFile).use { jar ->
            val entries = jar.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()
            check(entries.containsAll(listOf(
                "pl/syntaxdevteam/plotsx/api/PlotsXApi.class",
                "pl/syntaxdevteam/plotsx/api/PlotSnapshot.class",
                "pl/syntaxdevteam/plotsx/api/PlotFlagChangedEvent.class",
            ))) { "Public API contracts are missing from the API JAR" }
            check(entries.all {
                it == "META-INF/MANIFEST.MF" ||
                    (it.startsWith("pl/syntaxdevteam/plotsx/api/") && !it.startsWith("pl/syntaxdevteam/plotsx/api/internal/"))
            }) { "The API JAR must contain only public API classes" }
        }
    }
}
tasks.named("check") { dependsOn(verifyApiPackaging) }

plugindeployer {
    paper { dir = "/home/debian/poligon/Paper/26.2/plugins" }
    folia { dir = "/home/debian/poligon/Folia/26.2/plugins" }
}

val compileLiveTest = tasks.register<JavaCompile>("compileLiveTest") {
    description = "Compiles the live test harness for PlotsX. This is a Paper plugin that runs integration tests against a live server."
    source(fileTree("src/liveTest/java") { include("**/*.java") })
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    destinationDirectory.set(layout.buildDirectory.dir("classes/liveTest"))
    options.release.set(21)
}
tasks.register<Jar>("liveTestJar") {
    description = "Generates a Paper plugin jar containing the live test harness for PlotsX."
    dependsOn(compileLiveTest)
    archiveClassifier.set("live-test")
    from(compileLiveTest.flatMap { it.destinationDirectory })
    from("src/liveTest/resources")
}

tasks.register<Jar>("foliaLiveTestJar") {
    description = "Builds the disposable two-player Folia acceptance plugin."
    dependsOn(compileLiveTest)
    archiveClassifier.set("folia-live-test")
    from(compileLiveTest.flatMap { it.destinationDirectory })
    from("src/foliaLiveTest/resources")
}

val verifyRuntimePackaging = tasks.register("verifyRuntimePackaging") {
    group = "verification"
    description = "Prevents duplicate Kotlin runtimes across the plugin and Paper library classloaders."
    val artifact = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
    inputs.file(artifact)
    dependsOn(tasks.named("shadowJar"))
    doLast {
        ZipFile(artifact.get().asFile).use { jar ->
            check(jar.stream().noneMatch { it.name.startsWith("kotlin/") && it.name.endsWith(".class") }) {
                "Kotlin runtime must be loaded by Paper, not bundled in the plugin JAR"
            }
            val manifest = jar.getInputStream(jar.getEntry("paper-libraries.yml")).bufferedReader().use { it.readText() }
            check(manifest.contains("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")) {
                "Runtime manifest must explicitly load Kotlin matching the compiler"
            }
            val loader = jar.getInputStream(jar.getEntry("pl/syntaxdevteam/plotsx/loader/SyntaxLoader.class")).use { it.readBytes() }
            check(!loader.toString(Charsets.ISO_8859_1).contains("kotlin/")) {
                "Bootstrap loader must not depend on Kotlin before libraries are resolved"
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyRuntimePackaging) }
