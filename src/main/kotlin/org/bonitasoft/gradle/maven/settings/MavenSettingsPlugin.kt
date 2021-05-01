package org.bonitasoft.gradle.maven.settings

import org.apache.maven.model.path.DefaultPathTranslator
import org.apache.maven.model.profile.DefaultProfileActivationContext
import org.apache.maven.model.profile.DefaultProfileSelector
import org.apache.maven.model.profile.activation.*
import org.apache.maven.settings.Mirror
import org.apache.maven.settings.Server
import org.apache.maven.settings.Settings
import org.apache.maven.settings.SettingsUtils
import org.apache.maven.settings.building.SettingsBuildingException
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.gradle.api.GradleScriptException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.authentication.http.HttpHeaderAuthentication
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI

const val EXTENSION_NAME = "mavenSettings"

class MavenSettingsPlugin : Plugin<Project> {

    lateinit var settings: Settings

    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, MavenSettingsPluginExtension::class.java, project)

        project.afterEvaluate {
            loadSettings(extension)
            activateProfiles(project, extension)
            registerMirrors(project)
            applyRepoCredentials(project.repositories)
            applyRepoCredentials(project.extensions.findByType(PublishingExtension::class.java)?.repositories)
        }
    }

    private fun loadSettings(extension: MavenSettingsPluginExtension) {
        val settingsLoader = LocalMavenSettingsLoader(extension)
        try {
            settings = settingsLoader.loadSettings()
        } catch (e: SettingsBuildingException) {
            throw GradleScriptException("Unable to read local Maven settings.", e)
        }
    }

    private fun activateProfiles(project: Project, extension: MavenSettingsPluginExtension) {
        val profileSelector = DefaultProfileSelector()
        val activationContext = DefaultProfileActivationContext()
        val profileActivators: List<ProfileActivator> = listOf(JdkVersionProfileActivator(), OperatingSystemProfileActivator(),
                PropertyProfileActivator(), FileProfileActivator().setPathTranslator(DefaultPathTranslator()))
        profileActivators.forEach { profileSelector.addProfileActivator(it) }

        activationContext.activeProfileIds = extension.activeProfiles.toList() + settings.activeProfiles
        activationContext.projectDirectory = project.projectDir
        activationContext.setSystemProperties(System.getProperties())
        if (extension.exportGradleProps) {
            activationContext.userProperties = project.properties.mapValues { e -> e.value.toString() }
        }
        profileSelector.getActiveProfiles(settings.profiles.map { SettingsUtils.convertFromSettingsProfile(it) }.toList(), activationContext, { }).forEach { profile ->
            for (entry in profile.properties) {
                project.extensions.getByType(ExtraPropertiesExtension::class.java).set(entry.key.toString(), entry.value.toString())
            }
        }
    }

    private fun registerMirrors(project: Project) {
        val globalMirror: Mirror? = settings.mirrors.firstOrNull { it.mirrorOf.split(",").contains("*") }
        if (globalMirror != null) {
            project.logger.info("Found global mirror in settings.xml. Replacing Maven repositories with mirror located at ${globalMirror.url}")
            createMirrorRepository(project, globalMirror)
            return
        }

        val externalMirror: Mirror? = settings.mirrors.firstOrNull { it.mirrorOf.split(",").contains("external:*") }
        if (externalMirror != null) {
            project.logger.info("Found external mirror in settings.xml. Replacing non-local Maven repositories with mirror located at ${externalMirror.url}")
            createMirrorRepository(project, externalMirror) {
                val host = InetAddress.getByName(it.url.host)
                // only match repositories not on localhost and not file based
                it.url.scheme != "file" && !(host.isLoopbackAddress || NetworkInterface.getByInetAddress(host) != null)
            }
            return
        }

        val centralMirror: Mirror? = settings.mirrors.firstOrNull { it.mirrorOf.split(",").contains("central") }
        if (centralMirror != null) {
            project.logger.info("Found central mirror in settings.xml. Replacing Maven Central repository with mirror located at ${centralMirror.url}")
            createMirrorRepository(project, centralMirror) { it ->
                ArtifactRepositoryContainer.MAVEN_CENTRAL_URL.startsWith(it.url.toString())
            }
        }
    }

    private fun applyRepoCredentials(repositories: RepositoryHandler?) {
        repositories?.all { repo ->
            if (repo is MavenArtifactRepository) {
                settings.servers.forEach { server ->
                    if (repo.name == server.id) {
                        addCredentials(server, repo)
                    }
                }
            }
        }
    }

    private fun createMirrorRepository(project: Project, mirror: Mirror) {
        createMirrorRepository(project, mirror) { true }
    }

    private fun createMirrorRepository(project: Project, mirror: Mirror, predicate: (MavenArtifactRepository) -> Boolean) {
        var mirrorFound = false
        val excludedRepositoryNames: List<String> = mirror.mirrorOf.split(",").filter { it.startsWith("!") }.map { it.substring(1) }
        project.repositories.all { repo ->
            if (repo is MavenArtifactRepository && repo.name != ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
                    && (repo.url != URI.create(mirror.url)) && predicate(repo) && !excludedRepositoryNames.contains(repo.getName())) {
                project.repositories.remove(repo)
                mirrorFound = true
            }
        }

        if (mirrorFound) {
            val server = settings.getServer(mirror.id)
            project.repositories.maven { repo ->
                repo.name = mirror.name ?: mirror.id
                repo.url = URI.create(mirror.url)
                addCredentials(server, repo)
            }
        }
    }

    private fun addCredentials(server: Server?, repo: MavenArtifactRepository) {
        if (server?.username != null && server.password != null) {
            repo.credentials {
                it.username = server.username
                it.password = server.password
            }
        } else if (server?.configuration != null) {
            val dom = server.configuration as Xpp3Dom
            val headers = dom.getChild("httpHeaders").getChildren("property")
            if (headers?.isNotEmpty() == true) {
                var credName: String? = null
                var credPassword: String? = null
                for (header in headers) {
                    credName = header.getChild("name").value
                    credPassword = header.getChild("value").value
                }
                if(credName?.isNotBlank() == true && credPassword?.isNotBlank() == true)
                repo.credentials(HttpHeaderCredentials::class.java) {
                    it.name = credName
                    it.value = credPassword
                }
            }
            repo.authentication.create("header", HttpHeaderAuthentication::class.java)
        }
    }
}