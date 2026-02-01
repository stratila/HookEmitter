plugins {
    id("java")
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
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("org.apache.commons:commons-text:1.12.0")
}

