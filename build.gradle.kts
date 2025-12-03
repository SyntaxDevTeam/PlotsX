plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "3.0.1"
    id("pl.syntaxdevteam.plugindeployer") version "1.0.4"
}

group = "pl.syntaxdevteam"
version = "1.0.0-Alpha-4"
description = ""

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://maven.playpro.com/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/") //SyntaxDevTeam
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/") //SyntaxDevTeam
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("pl.syntaxdevteam:core:1.2.6")
    compileOnly("pl.syntaxdevteam:messageHandler-paper:1.0.0")
    compileOnly("pl.syntaxdevteam:cleanerx:1.5.3")
    compileOnly("org.eclipse.aether:aether-api:1.1.0")
    compileOnly("org.yaml:snakeyaml:2.5")
    compileOnly("com.google.code.gson:gson:2.13.2")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.25.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.25.0")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.25.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.25.0")
    compileOnly("net.kyori:adventure-text-serializer-ansi:4.25.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.5")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    compileOnly("org.postgresql:postgresql:42.7.7")
    compileOnly("com.h2database:h2:2.3.232")
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("io.github.miniplaceholders:miniplaceholders-kotlin-ext:2.3.0")
    compileOnly("com.github.milkbowl:VaultAPI:1.7.1")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.13")
    compileOnly("net.coreprotect:coreprotect:22.4")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    build {
        dependsOn("shadowJar")
    }
    runServer {
        minecraftVersion("1.21.10")
        runDirectory(file("run/paper"))
    }
    runPaper.folia.registerTask()
}

tasks.processResources {
    val props = mapOf("version" to version, "description" to description)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

plugindeployer {
    paper { dir = "/home/debian/poligon/Paper/1.21.11/plugins" } //ostatnia wersja dla Paper
    folia { dir = "/home/debian/poligon/Folia/1.21.8/plugins" } //ostatnia wersja dla Folia
}