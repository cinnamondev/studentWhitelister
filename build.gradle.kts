import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
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
    implementation("com.discord4j:discord4j-core:3.3.2")
    implementation("commons-validator:commons-validator:1.10.0")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    paperweight.paperDevBundle("26.1.2.build.+")
    //compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
}

group = "com.github.cinnamondev"
version = "1.31"
description = "studentWhitelister"
java {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
}

//paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
//tasks.assemble {
//    dependsOn(tasks.reobfJar)
//}

tasks {
    compileJava {
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release = 25
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    runServer {
        minecraftVersion("26.1.2")
        runDirectory.set(layout.projectDirectory.dir("testserver/testserver"))
    }
}

paperPluginYaml {
    main = "com.github.cinnamondev.studentWhitelister.StudentWhitelister"
    bootstrapper = "com.github.cinnamondev.studentWhitelister.StudentWhitelisterBootstrap"
    apiVersion = "26.1"
    authors.add("cinnamondev")
    dependencies {
        server("floodgate", PaperPluginYaml.Load.BEFORE, false)
    }
}
