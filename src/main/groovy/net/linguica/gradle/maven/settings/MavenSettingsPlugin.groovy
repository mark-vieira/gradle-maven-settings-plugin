/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.linguica.gradle.maven.settings

import groovy.transform.CompileStatic
import org.apache.maven.model.InputLocation
import org.apache.maven.model.Profile
import org.apache.maven.model.building.ModelProblem
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.path.DefaultPathTranslator
import org.apache.maven.model.profile.DefaultProfileActivationContext
import org.apache.maven.model.profile.DefaultProfileSelector
import org.apache.maven.model.profile.activation.FileProfileActivator
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator
import org.apache.maven.model.profile.activation.ProfileActivator
import org.apache.maven.model.profile.activation.PropertyProfileActivator
import org.apache.maven.settings.Mirror
import org.apache.maven.settings.Server
import org.apache.maven.settings.Settings
import org.apache.maven.settings.SettingsUtils
import org.apache.maven.settings.building.SettingsBuildingException
import org.gradle.api.GradleScriptException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension

import javax.annotation.Nullable
import java.util.Map.Entry

@CompileStatic
class MavenSettingsPlugin implements Plugin<Project> {
    public static final String MAVEN_SETTINGS_EXTENSION_NAME = "mavenSettings"

    private Settings settings

    @Override
    void apply(Project project) {
        MavenSettingsPluginExtension extension =
                project.extensions.create(MAVEN_SETTINGS_EXTENSION_NAME, MavenSettingsPluginExtension.class, project)

        project.afterEvaluate {
            loadSettings(extension)
            activateProfiles(project, extension)
            registerMirrors(project)
            applyRepoCredentials(project.repositories)
            applyRepoCredentials(project.extensions.findByType(PublishingExtension)?.repositories)
        }
    }

    private void loadSettings(MavenSettingsPluginExtension extension) {
        LocalMavenSettingsLoader settingsLoader = new LocalMavenSettingsLoader(extension)
        try {
            settings = settingsLoader.loadSettings()
        } catch (SettingsBuildingException e) {
            throw new GradleScriptException('Unable to read local Maven settings.', e)
        }
    }

    private void activateProfiles(Project project, MavenSettingsPluginExtension extension) {
        DefaultProfileSelector profileSelector = new DefaultProfileSelector()
        DefaultProfileActivationContext activationContext = new DefaultProfileActivationContext()
        List<ProfileActivator> profileActivators = [new JdkVersionProfileActivator(), new OperatingSystemProfileActivator(),
                                                new PropertyProfileActivator(), new FileProfileActivator().setPathTranslator(new DefaultPathTranslator())]
        profileActivators.each { profileSelector.addProfileActivator(it) }

        activationContext.setActiveProfileIds(extension.activeProfiles.toList() + settings.activeProfiles)
        activationContext.setProjectDirectory(project.projectDir)
        activationContext.setSystemProperties(System.getProperties())
        if (extension.exportGradleProps) {
            activationContext.setUserProperties(project.properties.collectEntries { key, value -> [key, value.toString()] } as Map<String, String>)
        }

        List<Profile> profiles = profileSelector.getActiveProfiles(settings.profiles.collect { return SettingsUtils.convertFromSettingsProfile(it) },
                activationContext, new ModelProblemCollector() {
            @Override
            void add(ModelProblem.Severity severity, String s, InputLocation inputLocation, Exception e) {

            }
        })

        profiles.each { profile ->
            for (Entry entry: profile.properties) {
                project.extensions.getByType(ExtraPropertiesExtension).set(entry.key.toString(), entry.value.toString())
            }
        }
    }

    private void registerMirrors(Project project) {
        Mirror globalMirror = settings.mirrors.find { it.mirrorOf.split(',').contains('*') }
        if (globalMirror != null) {
            project.logger.info "Found global mirror in settings.xml. Replacing Maven repositories with mirror " +
                    "located at ${globalMirror.url}"
            createMirrorRepository(project, globalMirror)
            return
        }

        Mirror externalMirror = settings.mirrors.find { it.mirrorOf.split(',').contains('external:*') }
        if (externalMirror != null) {
            project.logger.info "Found external mirror in settings.xml. Replacing non-local Maven repositories " +
                    "with mirror located at ${externalMirror.url}"
            createMirrorRepository(project, externalMirror) { MavenArtifactRepository repo ->
                InetAddress host = InetAddress.getByName(repo.url.host)
                // only match repositories not on localhost and not file based
                repo.url.scheme != 'file' && !(host.anyLocalAddress || host.isLoopbackAddress() || NetworkInterface.getByInetAddress(host) != null)
            }
            return
        }

        Mirror centralMirror = settings.mirrors.find { it.mirrorOf.split(',').contains('central') }
        if (centralMirror != null) {
            project.logger.info "Found central mirror in settings.xml. Replacing Maven Central repository with " +
                    "mirror located at ${centralMirror.url}"
            createMirrorRepository(project, centralMirror) { MavenArtifactRepository repo ->
                ArtifactRepositoryContainer.MAVEN_CENTRAL_URL.startsWith(repo.url.toString())
            }
        }
    }

    private void applyRepoCredentials(@Nullable RepositoryHandler repositories) {
        repositories?.all { repo ->
            if (repo instanceof MavenArtifactRepository) {
                settings.servers.each { server ->
                    if (repo.name == server.id) {
                        addCredentials(server, repo as MavenArtifactRepository)
                    }
                }
            }
        }
    }

    private void createMirrorRepository(Project project, Mirror mirror) {
        createMirrorRepository(project, mirror) { true }
    }

    private void createMirrorRepository(Project project, Mirror mirror, Closure predicate) {
        boolean mirrorFound = false
        List<String> excludedRepositoryNames = mirror.mirrorOf.split(',').findAll { it.startsWith("!") }.collect { it.substring(1) }
        project.repositories.all { repo ->
            if (repo instanceof MavenArtifactRepository && repo.name != ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
                    && repo.url != URI.create(mirror.url) && predicate(repo) && !excludedRepositoryNames.contains(repo.getName())) {
                project.repositories.remove(repo)
                mirrorFound = true
            }
        }

        if (mirrorFound) {
            Server server = settings.getServer(mirror.id)
            project.repositories.maven { MavenArtifactRepository repo ->
                repo.name = mirror.name ?: mirror.id
                repo.url = mirror.url
                addCredentials(server, repo)
            }
        }
    }

    private static addCredentials(Server server, MavenArtifactRepository repo) {
        if (server?.username != null && server?.password != null) {
            repo.credentials {
                it.username = server.username
                it.password = server.password
            }
        }
    }
}
