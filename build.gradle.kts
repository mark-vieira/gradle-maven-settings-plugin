plugins {
    id("com.gradle.plugin-publish") version "0.14.0"
    `groovy-gradle-plugin`
    kotlin("jvm") version "1.4.32"
}

repositories {
    mavenCentral()
}

val mavenVersion by rootProject.extra { "3.6.3" }

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("org.apache.maven:maven-settings:${mavenVersion}")
    implementation("org.apache.maven:maven-settings-builder:${mavenVersion}")
    implementation("org.apache.maven:maven-model-builder:${mavenVersion}")
    implementation("org.apache.maven:maven-model:${mavenVersion}")
    implementation("org.apache.maven:maven-core:${mavenVersion}")
    implementation("org.sonatype.plexus:plexus-cipher:1.7")
    implementation("org.sonatype.plexus:plexus-sec-dispatcher:1.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.19.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

pluginBundle {
    website = "https://github.com/bonitasoft-labs/gradle-maven-settings-plugin"
    vcsUrl = "https://github.com/bonitasoft-labs/gradle-maven-settings-plugin"
    description = """
        | Gradle plugin for exposing Maven settings file configuration to Gradle project.
        |
        | This project is forked from https://github.com/mark-vieira/gradle-maven-settings-plugin
        | And will add more support of the `settings.xml` file (repositories in profiles)
    """.trimMargin()
    tags = listOf("settings", "maven")

    mavenCoordinates {
        groupId = "com.bonitasoft.gradle"
        artifactId = "maven-settings-plugin"
    }
}

gradlePlugin {
    plugins {
        create("mavenSettings") {
            id = "com.bonitasoft.gradle.maven-settings"
            displayName = "Maven Settings Plugin"
            implementationClass = "com.bonitasoft.gradle.maven.settings.MavenSettingsPlugin"
        }
    }
}
