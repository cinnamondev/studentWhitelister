import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("com.gradleup.shadow") version "9.2.2"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    maven { url = uri("https://repo.opencollab.dev/main/") }
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
}

dependencies {
    implementation("com.discord4j:discord4j-core:3.3.0")
    implementation("commons-validator:commons-validator:1.10.0")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    paperweight.paperDevBundle("1.21.9-R0.1-SNAPSHOT")
    //compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
}

group = "com.github.cinnamondev"
version = "1.3"
description = "studentWhitelister"
java {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
tasks.assemble {
    dependsOn(tasks.reobfJar)
}

tasks {
    compileJava {
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release = 21
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    runServer {
        minecraftVersion("1.21.10")
    }
}

paperPluginYaml {
    main = "com.github.cinnamondev.studentWhitelister.StudentWhitelister"
    bootstrapper = "com.github.cinnamondev.studentWhitelister.StudentWhitelisterBootstrap"
    apiVersion = "1.21"
    authors.add("cinnamondev")
    dependencies {
        server("floodgate", PaperPluginYaml.Load.BEFORE, false)
    }
}
