package com.bonitasoft.gradle.maven.settings

import org.apache.maven.settings.RepositoryPolicy
import org.apache.maven.settings.Settings
import org.apache.maven.settings.io.DefaultSettingsWriter
import org.assertj.core.api.Assertions.assertThat
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.publish.PublishingExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.EnvironmentVariables
import java.io.File
import java.nio.file.Paths

class MavenSettingsPluginTest {

    val settingsFile = Paths.get("build", "tmp", ".m2", "settings.xml").toFile()
    lateinit var project: Project

    @Rule @JvmField
    var envVar = EnvironmentVariables()

    @Before
    fun createSettingsXml() {
        envVar.set("MY_TOKEN", "secret_from_env")
        project = ProjectBuilder.builder().build()
    }

    @After
    fun deleteSettingsXml() {
        settingsFile.delete()
    }

    fun withSettings(configureClosure: Settings.() -> Unit) {
        val settings = Settings()
        configureClosure.invoke(settings)
        DefaultSettingsWriter().write(settingsFile, null, settings)
    }

    fun applyPlugin() {
        applyPlugin {

        }
    }

    fun applyPlugin(pluginConfiguration: MavenSettingsPluginExtension.() -> Unit) {
        project.pluginManager.apply("com.bonitasoft.gradle.maven-settings")

        project.extensions.configure(MavenSettingsPluginExtension::class.java) {
            it.userSettingsFileName = settingsFile.canonicalPath
        }
        pluginConfiguration.invoke(project.extensions.getByType(MavenSettingsPluginExtension::class.java))
        (project as DefaultProject).evaluate()
    }

    @Test
    fun `should apply plugin using it's id`() {
        project.pluginManager.apply("com.bonitasoft.gradle.maven-settings")

        (project as DefaultProject).evaluate()

        assertThat(project.plugins.hasPlugin(MavenSettingsPlugin::class.java)).isTrue
    }

    @Test
    fun `should mirror all repos`() {
        withSettings {
            mirror {
                id = "myRepo"
                mirrorOf = "*"
                url = "http://maven.foo.bar"
            }
        }


        project.run {
            repositories.apply {
                mavenLocal()
                mavenCentral()
                jcenter()
            }
        }

        applyPlugin()


        assertThat(project.repositories.names).containsOnly("myRepo", "MavenLocal")
    }

    @Test
    fun `should mirror all repo excluding the ones specified`() {
        withSettings {
            mirror {
                id = "myRepo"
                mirrorOf = "*,!some-repo"
                url = "http://maven.foo.bar"
            }
        }

        project.run {
            repositories.apply {
                mavenLocal()
                mavenCentral()
                maven {
                    it.name = "some-repo"
                    it.url = uri("https://example.com")
                }
                maven {
                    it.name = "some-other-repo"
                    it.url = uri("https://example.com")
                }
            }
        }

        applyPlugin()


        assertThat(project.repositories.names).containsOnly("myRepo", "MavenLocal", "some-repo")
    }

    @Test
    fun `should mirror all external repos with file repo declared`() {
        withSettings {
            mirror {
                id = "myRepo"
                mirrorOf = "external:*"
                url = "http://maven.foo.bar"
            }
        }


        project.run {
            repositories.apply {
                mavenLocal()
                jcenter()
                mavenCentral()
                maven {
                    it.name = "myLocal"
                    it.url = uri("file://${project.buildDir}/.m2")
                }
            }
        }
        applyPlugin()

        assertThat(project.repositories.names).containsOnly("myRepo", "MavenLocal", "myLocal")
    }

    @Test
    fun `should mirror all external repos with localhost repo declared`() {
        withSettings {
            mirror {
                id = "myRepo"
                mirrorOf = "external:*"
                url = "http://maven.foo.bar"
            }
        }

        project.run {
            repositories.apply {
                mavenLocal()
                jcenter()
                mavenCentral()
                maven {
                    it.name = "myLocal"
                    it.url = uri("http://localhost/maven")
                }
            }
        }

        applyPlugin()

        assertThat(project.repositories.names).containsOnly("myRepo", "MavenLocal", "myLocal")
    }

    @Test
    fun `should miror only central repo`() {
        withSettings {
            mirror {
                id = "myRepo"
                mirrorOf = "central"
                url = "http://maven.foo.bar"
            }
        }


        project.run {
            repositories.apply {
                mavenLocal()
                mavenCentral()
                maven {
                    it.name = "myRemote"
                    it.url = uri("https://maven.foobar.org/repo")
                }
            }
        }

        applyPlugin()

        assertThat(project.repositories.names).containsOnly("myRepo", "MavenLocal", "myRemote")
    }

    @Test
    fun `should not miror central repo if not declared`() {
        withSettings {
            mirror {
                id = "myRepo"
                mirrorOf = "central"
                url = "http://maven.foo.bar"
            }
        }

        applyPlugin()

        project.run {
            repositories.apply {
                mavenLocal()
                maven {
                    it.name = "myRemote"
                    it.url = uri("https://maven.foobar.org/repo")
                }
            }
        }


        applyPlugin()

        assertThat(project.repositories.names).containsOnly("myRemote", "MavenLocal")
    }

    @Test
    fun `should have property set from default profile`() {
        withSettings {
            profile {
                id = "myprofile"
                properties = mapOf("myprop" to "true").toProperties()
            }
            activeProfiles = listOf("myprofile")
        }

        applyPlugin()


        assertThat(project.properties).containsEntry("myprop", "true")
    }

    @Test
    fun `should have property set from profile activated using properties`() {
        withSettings {
            profile {
                id = "myprofile"
                properties = mapOf("myprop" to "true").toProperties()
            }
        }

        applyPlugin {
            activeProfiles = arrayOf("myprofile")
        }


        assertThat(project.properties).containsEntry("myprop", "true")
    }

    @Test
    fun `should set credentials on existing repo`() {
        withSettings {
            server { id = "central"; username = "first.last"; password = "secret" }
        }


        project.run {
            repositories.apply {
                maven {
                    it.name = "central"
                    it.url = uri("https://repo1.maven.org/maven2/")
                }
            }
        }

        applyPlugin()

        assertThat((project.repositories.getByName("central") as MavenArtifactRepository).credentials).satisfies {
            assertThat(it.username).isEqualTo("first.last")
            assertThat(it.password).isEqualTo("secret")
        }
    }


    @Test
    fun `should set credentials when evaluated from environment`() {
        withSettings {
            server { id = "some.custom.repo"; username = "customUser"; password = "\${env.MY_TOKEN}" }
        }


        project.run {
            repositories.apply {
                maven {
                    it.name = "some.custom.repo"
                    it.url = uri("https://repo1.maven.org/maven2/")
                }
            }
        }

        applyPlugin()

        assertThat((project.repositories.getByName("some.custom.repo") as MavenArtifactRepository).credentials).satisfies {
            assertThat(it.username).isEqualTo("customUser")
            assertThat(it.password).isEqualTo("secret_from_env")
        }
    }


    @Test
    fun `should not fail the build when missing credentials when evaluated from environment`() {
        withSettings {
            server { id = "some.custom.repo"; username = "customUser"; password = "\${env.MY_UNKNOWN_TOKEN}" }
        }


        project.run {
            repositories.apply {
                maven {
                    it.name = "some.custom.repo"
                    it.url = uri("https://repo1.maven.org/maven2/")
                }
            }
        }

        applyPlugin()

        assertThat((project.repositories.getByName("some.custom.repo") as MavenArtifactRepository).credentials).satisfies {
            assertThat(it.username).isEqualTo("customUser")
            assertThat(it.password).isEqualTo("\${env.MY_UNKNOWN_TOKEN}")
        }
    }

    @Test
    fun `should not set credential on flat repo`() {
        withSettings {
            server { id = "flat"; username = "first.last"; password = "secret" }
        }


        project.run {
            repositories.apply {
                flatDir {
                    it.name = "flat"
                    it.dirs(".")
                }
            }
        }

        applyPlugin()
        // no failure
    }

    @Test
    fun `should set http headers credentials`() {
        withSettings {
            server {
                id = "central"; configuration = Xpp3DomBuilder.build("""
            <configuration>
                <httpHeaders>
                    <property>
                        <name>Auth-Token</name>
                        <value>secret</value>
                    </property>
                </httpHeaders>
            </configuration>
            """.trimIndent().byteInputStream(), "UTF-8")
            }
        }


        project.run {
            repositories.apply {
                maven {
                    it.name = "central"
                    it.url = uri("https://repo1.maven.org/maven2/")
                }
            }
        }

        applyPlugin()

        assertThat((project.repositories.getByName("central") as MavenArtifactRepository).getCredentials(HttpHeaderCredentials::class.java)).satisfies {
            assertThat(it.name).isEqualTo("Auth-Token")
            assertThat(it.value).isEqualTo("secret")
        }
    }

    @Test
    fun `should add credentials to the publishing repo`() {
        withSettings {
            server { id = "central"; username = "first.last"; password = "secret" }
        }


        project.run {
            pluginManager.apply("maven-publish")
            extensions.getByType(PublishingExtension::class.java).repositories.apply {
                maven {
                    it.name = "central"
                    it.url = uri("https://repo1.maven.org/maven2/")
                }
            }
        }

        applyPlugin()

        assertThat(project.extensions.getByType(PublishingExtension::class.java).repositories.getByName("central") as MavenArtifactRepository).satisfies {
            assertThat(it.credentials.username).isEqualTo("first.last")
            assertThat(it.credentials.password).isEqualTo("secret")
        }
    }

    @Test
    fun `should add repositories`() {
        withSettings {
            profile {
                id = "profile1"
                repository {
                    id = "repoFromProfile1"
                    url = "http://maven.repo1.com"
                    releases = RepositoryPolicy().apply {
                        isEnabled = false
                    }
                }
            }
            profile {
                id = "profile2"
                repository {
                    id = "repoFromProfile2"
                    url = "http://maven.repo2.com"
                }
            }
            profile {
                id = "profile3"
                repository {
                    id = "repoFromProfile3"
                    url = "http://maven.repo3.com"
                }
            }
            server {
                id = "repoFromProfile2"
                username = "myUser"
                password = "myPassword"
            }
            activeProfiles = listOf("profile1", "profile2")
        }
        project.run {
            pluginManager.apply("maven-publish")
            repositories.apply {
                mavenLocal()
                mavenCentral()
                maven {
                    it.name = "custom1"
                    it.url = uri("https://maven.custom.com")
                }
            }
        }

        applyPlugin()

        //No profile 3, profile is not activated
        assertThat(project.repositories.names).containsOnly("repoFromProfile1", "repoFromProfile2", "MavenLocal", "MavenRepo", "custom1")
        assertThat(project.repositories.map { (it as MavenArtifactRepository).url.toString() }).contains(
                "https://repo.maven.apache.org/maven2/",
                "https://maven.custom.com",
                "http://maven.repo1.com",
                "http://maven.repo2.com"
        )
        //credentials are still applied
        assertThat(project.repositories.getByName("repoFromProfile1") as MavenArtifactRepository).satisfies {
            it.content {
                assertThat((it as MavenRepositoryContentDescriptor).snapshotsOnly())
            }
        }
        assertThat(project.repositories.getByName("repoFromProfile2") as MavenArtifactRepository).satisfies {
            assertThat(it.credentials.username).isEqualTo("myUser")
            assertThat(it.credentials.password).isEqualTo("myPassword")
        }
    }
}