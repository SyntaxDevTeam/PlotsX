import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.plugin.deployer)
}

group = "pl.syntaxdevteam"
version = "1.0.0-RC-2"
description = "Lightweight and versatile player plot protection plugin"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://maven.playpro.com/")
    maven("https://maven.enginehub.org/repo/")

    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/") //SyntaxDevTeam
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/") //SyntaxDevTeam

    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
    maven("https://repo.codemc.org/repository/maven-public/") // VaultUnlockedAPI
    maven("https://jitpack.io") // VaultAPI
    maven("https://repo.essentialsx.net/releases/") // EssentialsX
    maven("https://repo.leavesmc.org/snapshots/") {
        name = "leavesmc-repo"
    }
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.sqlite)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.mariadb)
    testRuntimeOnly(libs.postgresql)

    compileOnly(libs.paper.api)
    compileOnly(libs.syntaxcore)
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
    compileOnly(libs.mariadb)
    compileOnly(libs.sqlite)
    compileOnly(libs.postgresql)
    compileOnly(libs.h2)
    compileOnly(libs.hikari)
    compileOnly(libs.luckperms)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.miniplaceholders)
    compileOnly(libs.vault)
    compileOnly(libs.vault.unlocked)
    compileOnly(libs.coreprotect)
    compileOnly(libs.worldguard.bukkit) {
        isTransitive = false
    }
    compileOnly(libs.worldguard.core) {
        isTransitive = false
    }
    // WorldEdit 7.4.x is compiled for Java 25. PlotsX still targets Java 21.
    compileOnly(libs.worldedit.bukkit) {
        isTransitive = false
    }
    compileOnly(libs.worldedit.core) {
        isTransitive = false
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn("shadowJar")
    }
    runServer {
        minecraftVersion("1.20.6")
        runDirectory(file("run/paper"))
    }
    runPaper.folia.registerTask()
}

val runtimeLibraryVersions = mapOf(
    "syntaxcoreVersion" to libs.versions.syntaxcore.get(),
    "messagehandlerVersion" to libs.versions.messagehandler.get(),
    "caffeineVersion" to libs.versions.caffeine.get(),
    "aetherVersion" to libs.versions.aether.get(),
    "snakeyamlVersion" to libs.versions.snakeyaml.get(),
    "gsonVersion" to libs.versions.gson.get(),
    "adventureVersion" to libs.versions.adventure.get(),
    "antVersion" to libs.versions.ant.get(),
    "mariadbVersion" to libs.versions.mariadb.get(),
    "sqliteVersion" to libs.versions.sqlite.get(),
    "postgresqlVersion" to libs.versions.postgresql.get(),
    "h2Version" to libs.versions.h2.get(),
    "hikariVersion" to libs.versions.hikari.get(),
)

tasks.processResources {
    val props = mapOf("version" to project.version, "description" to project.description)
    inputs.properties(props + runtimeLibraryVersions)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
    filesMatching("paper-libraries.yml") {
        expand(runtimeLibraryVersions)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PlotsX-Paper")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()

    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    filesMatching("META-INF/*.kotlin_module") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds every currently supported PlotsX platform artifact."
    dependsOn(tasks.named("build"))
}

val apiJar = tasks.register<Jar>("apiJar") {
    description = "Generates a compile-only API jar for PlotsX integration authors."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("pl/syntaxdevteam/plotsx/api/**")
        exclude("pl/syntaxdevteam/plotsx/api/internal/**")
    }
}
tasks.named("assemble") { dependsOn(apiJar) }

plugindeployer {
    paper { dir = "/home/debian/poligon/Paper/26.2/plugins" } //ostatnia wersja dla Paper
    folia { dir = "/home/debian/poligon/Folia/26.2/plugins" } //ostatnia wersja dla Folia
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
