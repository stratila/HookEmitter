plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"
    id("idea")
    id("com.diffplug.spotless") version "8.1.0"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

idea {
    module {
        isDownloadSources = true
    }
}

spotless {
    java {
        googleJavaFormat("1.28.0")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("org.apache.commons:commons-text:1.12.0")
}

tasks.shadowJar {
    relocate(
        "org.apache.commons.text",
        "io.stratila.hook.emitter.libs.commons.text"
    )
}
