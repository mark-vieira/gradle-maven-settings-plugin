package com.bonitasoft.gradle.maven.settings

import org.apache.maven.settings.Mirror
import org.apache.maven.settings.Profile
import org.apache.maven.settings.Server
import org.apache.maven.settings.Settings
import org.apache.maven.settings.io.DefaultSettingsWriter
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.publish.PublishingExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class MavenSettingsPluginTest {

    val settingsDir = File("build/tmp/.m2/")
    val settingsFile = File(settingsDir, "settings.xml")
    lateinit var project: Project

    @Before
    fun createSettingsXml() {
        settingsFile
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

    fun Settings.mirror(config: Mirror.() -> Unit) {
        this.mirrors.add(Mirror().apply(config))
    }

    fun Settings.profile(config: Profile.() -> Unit) {
        this.profiles.add(Profile().apply(config))
    }

    fun Settings.server(config: Server.() -> Unit) {
        this.servers.add(Server().apply(config))
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
}