plugins {
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("pl.syntaxdevteam.plugindeployer") version "1.0.6-R0.2-SNAPSHOT"
}

group = "pl.syntaxdevteam"
version = "1.0.0-R0.1-Alpha"
description = "Lightweight and versatile player plot protection plugin"

val paperApiVersion = providers.gradleProperty("paperApiVersion")
    .getOrElse("1.21.11-R0.1-SNAPSHOT")
val worldGuardVersion = providers.gradleProperty("worldGuardVersion")
    .getOrElse("7.0.13")

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

    maven("https://repo.menthamc.org/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
    maven("https://repo.codemc.org/repository/maven-public/") // VaultUnlockedAPI
    maven("https://jitpack.io") // VaultAPI
    maven("https://repo.essentialsx.net/releases/") // EssentialsX
    maven("https://repo.leavesmc.org/snapshots/") {
        name = "leavesmc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("pl.syntaxdevteam:syntaxcore:1.4.0-R0.1-SNAPSHOT")
    compileOnly("pl.syntaxdevteam:messageHandler-paper:1.2.2-R0.4-SNAPSHOT")
    compileOnly("pl.syntaxdevteam:cleanerx:1.5.8")
    compileOnly("org.eclipse.aether:aether-api:1.1.0")
    compileOnly("org.yaml:snakeyaml:2.5")
    compileOnly("com.google.code.gson:gson:2.14.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:5.1.0")
    compileOnly("net.kyori:adventure-text-minimessage:5.1.0")
    compileOnly("net.kyori:adventure-text-serializer-gson:5.1.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:5.1.0")
    compileOnly("net.kyori:adventure-text-serializer-ansi:5.1.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.10")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    compileOnly("org.postgresql:postgresql:42.7.13")
    compileOnly("com.h2database:h2:2.4.240")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("io.github.miniplaceholders:miniplaceholders-kotlin-ext:2.3.0")
    compileOnly("com.github.milkbowl:VaultAPI:1.7.1")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.13")
    compileOnly("net.coreprotect:coreprotect:22.4")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:$worldGuardVersion") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-core:$worldGuardVersion") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.9") {
        isTransitive = false
    }
}

val targetJavaVersion = providers.gradleProperty("javaVersion")
    .map(String::toInt)
    .getOrElse(21)
kotlin {
    jvmToolchain(targetJavaVersion)
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

tasks.processResources {
    val props = mapOf("version" to project.version, "description" to project.description)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

plugindeployer {
    paper { dir = "/home/debian/poligon/Paper/26.2/plugins" } //ostatnia wersja dla Paper
    folia { dir = "/home/debian/poligon/Folia/26.2/plugins" } //ostatnia wersja dla Folia
}
