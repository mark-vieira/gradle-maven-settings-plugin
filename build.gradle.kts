plugins {
    id("com.gradle.plugin-publish") version "1.2.0"
    `groovy-gradle-plugin`
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("maven-publish")
}

group = "com.bonitasoft.gradle"

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
    testImplementation("com.github.stefanbirkner:system-rules:1.19.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(arrayOf("--release", "11"))
}

tasks.withType<JavaCompile>().configureEach {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = "11"
  }
}

gradlePlugin {
    website.set("https://github.com/bonitasoft-labs/gradle-maven-settings-plugin")
    vcsUrl.set("https://github.com/bonitasoft-labs/gradle-maven-settings-plugin")    
    plugins {
        create("mavenSettings") {
            id = "com.bonitasoft.gradle.maven-settings"
            implementationClass = "com.bonitasoft.gradle.maven.settings.MavenSettingsPlugin"
            displayName = "Maven Settings Plugin"
            description = """
            | Gradle plugin for exposing Maven settings file configuration to Gradle project.
            |
            | This project is forked from https://github.com/mark-vieira/gradle-maven-settings-plugin
                | And will add more support of the `settings.xml` file (repositories in profiles)
                """.trimMargin()
            tags.set(listOf("settings", "maven"))
        }
    }
}
