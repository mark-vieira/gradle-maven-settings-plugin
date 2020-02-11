plugins {
    groovy
    `java-gradle-plugin`
    maven
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.10.1"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String?
            artifactId = "maven-settings-gitlab"
            version = project.version as String?

            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
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
    implementation("org.sonatype.plexus:plexus-cipher:1.4")
    implementation("org.sonatype.plexus:plexus-sec-dispatcher:1.3")
    testImplementation("junit:junit:4.11")
    testImplementation("org.hamcrest:hamcrest-library:1.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
    targetCompatibility = JavaVersion.VERSION_1_6
}

pluginBundle {
    website = "https://github.com/acidbee/gradle-maven-settings-plugin"
    vcsUrl = "https://github.com/acidbee/gradle-maven-settings-plugin"
    description = "Gradle plugin for exposing Maven settings file configuration to Gradle project. Supports header authentication for GitLab"
    tags = listOf("settings", "maven", "gitlab")

    mavenCoordinates {
        groupId = "co.coxes.gradle"
        artifactId = "maven-settings-gitlab-plugin"
    }
}

gradlePlugin {
    plugins {
        create("mavenSettings") {
            id = "co.coxes.maven-settings-gitlab"
            displayName = "Maven Settings Plugin (GitLab Support)"
            implementationClass = "net.linguica.gradle.maven.settings.MavenSettingsPlugin"
        }
    }
}
