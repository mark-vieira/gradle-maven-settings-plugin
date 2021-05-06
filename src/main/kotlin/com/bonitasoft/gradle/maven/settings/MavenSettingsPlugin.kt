package com.bonitasoft.gradle.maven.settings

import org.apache.maven.model.Profile
import org.apache.maven.model.Repository
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
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.authentication.http.HttpHeaderAuthentication
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.util.function.Predicate

const val EXTENSION_NAME = "mavenSettings"

class MavenSettingsPlugin : Plugin<Project> {

    lateinit var settings: Settings

    override fun apply(project: Project) {
        val logger = project.logger
        val extension = project.extensions.create(EXTENSION_NAME, MavenSettingsPluginExtension::class.java, project)

        project.afterEvaluate {
            loadSettings(extension, logger)
            activateProfiles(project, extension)
            registerMirrors(project)
            applyRepoCredentials(project, project.repositories)
            applyRepoCredentials(project, project.extensions.findByType(PublishingExtension::class.java)?.repositories)
        }
    }

    private fun loadSettings(extension: MavenSettingsPluginExtension, logger: Logger) {
        val settingsLoader = LocalMavenSettingsLoader(extension, logger)
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
        val activeProfiles = profileSelector.getActiveProfiles(settings.profiles.map { SettingsUtils.convertFromSettingsProfile(it) }.toList(), activationContext) { }
        project.logger.info("Active maven profiles: ${activeProfiles?.map { it.id }}")
        activeProfiles.forEach { profile ->
            applyProfile(profile, project)
        }
    }

    private fun applyProfile(profile: Profile, project: Project) {
        project.logger.info("Applying maven profile ${profile.id}")
        for (entry in profile.properties) {
            project.logger.info("Applying property ${entry.key}")
            project.extensions.getByType(ExtraPropertiesExtension::class.java).set(entry.key.toString(), entry.value.toString())
            project.logger.debug("Property applied with value ${entry.value}")
        }
        for (repo in profile.repositories) {
            project.repositories.maven {
                configureMavenRepo(it, repo)
                project.logger.info("Imported repository ${it.name} from active profile ${profile.id} configured in maven's settings.xml")
            }
        }
    }

    private fun configureMavenRepo(gradleRepository: MavenArtifactRepository, mavenRepository: Repository) {
        gradleRepository.name = mavenRepository.id
        gradleRepository.url = URI(mavenRepository.url)
        if (mavenRepository.releases != null) {
            gradleRepository.mavenContent { content ->
                if (mavenRepository.releases.isEnabled && !mavenRepository.snapshots.isEnabled) {
                    content.releasesOnly()
                }
                if (mavenRepository.releases.isEnabled && !mavenRepository.snapshots.isEnabled) {
                    content.snapshotsOnly()
                }
            }
        }
    }

    private fun ArtifactRepository.getMirror(settings: Settings): Mirror? {
        return settings.mirrors.firstOrNull { mirror ->
            var canMatch = false
            for (mirrorOf in mirror.mirrorOf.split(",")) {
                when (mirrorOf) {
                    "*" -> canMatch = true
                    "central" -> canMatch = this is MavenArtifactRepository && this.url.toString() == ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
                    "external:*" -> canMatch =
                            (this is MavenArtifactRepository
                                    && this.url.scheme != "file"
                                    && !InetAddress.getByName(this.url.host).run { isLoopbackAddress || NetworkInterface.getByInetAddress(this) != null })
                    "!$name" -> return@firstOrNull false
                    name -> return@firstOrNull true
                    else -> continue
                }
            }
            return@firstOrNull canMatch
        }
    }

    private fun registerMirrors(project: Project) {
        project.repositories.toList().filter { repo ->
            if (repo.name.equals(ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME)) {
                return@filter false
            }
            repo.getMirror(settings)?.also { mirror ->
                if (!project.repositories.names.contains(mirror.id)) {
                    project.repositories.maven { repo ->
                        repo.name = mirror.id
                        repo.url = URI.create(mirror.url)
                        project.logger.info("Replaced '${repo.name}' with mirror ${mirror.id} configured in maven's settings.xml")
                    }
                } else {
                    project.logger.info("Replaced '${repo.name}' with mirror ${mirror.id} (already created) configured in maven's settings.xml")
                }
            } != null
        }.forEach {
            project.repositories.remove(it)
        }
    }

    private fun applyRepoCredentials(project: Project, repositories: RepositoryHandler?) {
        repositories?.all { repo ->
            if (repo is MavenArtifactRepository) {
                settings.servers.forEach { server ->
                    if (repo.name == server.id) {
                        addCredentials(project, server, repo)
                    }
                }
            }
        }
    }

    private fun createMirrorRepository(project: Project, mirror: Mirror) {
        createMirrorRepository(project, mirror) { true }
    }


    private fun createMirrorRepository(project: Project, mirror: Mirror, predicate: (MavenArtifactRepository) -> Boolean) {
        var replaceAll = true
        var replaceExternal = true


        val excludedRepositoryNames: List<String> = mirror.mirrorOf.split(",").filter { it.startsWith("!") }.map { it.substring(1) }
        val removedRepositories = mutableListOf<String>()
        if (project.repositories.removeIf {
                    if (it is MavenArtifactRepository && it.name != ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
                            && predicate(it) && !excludedRepositoryNames.contains(it.getName())) {
                        removedRepositories.add(it.name)
                        return@removeIf true
                    }
                    return@removeIf false
                }) {
            project.repositories.maven { repo ->
                repo.name = mirror.id
                repo.url = URI.create(mirror.url)
                project.logger.info("Replaced repositories $removedRepositories with mirror ${mirror.id} configured in maven's settings.xml")
            }
        }
    }

    private fun addCredentials(project: Project, server: Server?, repo: MavenArtifactRepository) {
        if (server?.username != null && server.password != null) {
            repo.credentials {
                it.username = server.username
                it.password = server.password
            }
            project.logger.info("Added credentials '${server.username}' on repository ${repo.name} configured in maven's settings.xml")
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
                    project.logger.info("Added credentials '$credName' in headers on repository ${repo.name} configured in maven's settings.xml")
                }
            }
            repo.authentication.create("header", HttpHeaderAuthentication::class.java)
        }
    }
}