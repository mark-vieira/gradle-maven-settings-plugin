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

import org.gradle.api.GradleScriptException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.mvn3.org.apache.maven.settings.Mirror
import org.gradle.mvn3.org.apache.maven.settings.Server
import org.gradle.mvn3.org.apache.maven.settings.Settings
import org.gradle.mvn3.org.apache.maven.settings.building.SettingsBuildingException

public class MavenSettingsPlugin implements Plugin<Project> {
    public static final String MAVEN_SETTINGS_EXTENSION_NAME = "mavenSettings"
    private Settings settings

    @Override
    void apply(Project project) {
        MavenSettingsPluginExtension extension =
                project.extensions.create(MAVEN_SETTINGS_EXTENSION_NAME, MavenSettingsPluginExtension.class, project)

        project.afterEvaluate {
            loadSettings(project, extension)
            registerMirrors(project)
        }
    }

    private void loadSettings(Project project, MavenSettingsPluginExtension extension) {
        LocalMavenSettingsLoader settingsLoader = new LocalMavenSettingsLoader(extension)
        try {
            settings = settingsLoader.loadSettings()
        } catch (SettingsBuildingException e) {
            throw new GradleScriptException('Unable to read local Maven settings.', e)
        }
    }

    private void registerMirrors(Project project) {
        Mirror globalMirror = settings.mirrors.find { it.mirrorOf.split(',').contains('*') }
        if (globalMirror != null) {
            project.logger.info "Found global mirror in settings.xml. Replacing Maven repositories with mirror " +
                    "located at ${globalMirror.url}"
            createMirrorRepository(project, globalMirror)
            return;
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
            return;
        }

        Mirror centralMirror = settings.mirrors.find { it.mirrorOf.split(',').contains('central') }
        if (centralMirror != null) {
            project.logger.info "Found central mirror in settings.xml. Replacing Maven Central repository with " +
                    "mirror located at ${centralMirror.url}"
            createMirrorRepository(project, centralMirror) { MavenArtifactRepository repo ->
                repo.name == ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME
            }
        }
    }

    private void createMirrorRepository(Project project, Mirror mirror) {
        createMirrorRepository(project, mirror) { true }
    }

    private void createMirrorRepository(Project project, Mirror mirror, Closure predicate) {
        project.repositories.all { repo ->
            if (repo instanceof MavenArtifactRepository && repo.name != ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
                    && !repo.url.equals(URI.create(mirror.url)) && predicate(repo)) {
                project.repositories.remove(repo)
            }
        }

        Server server = settings.getServer(mirror.id)
        project.repositories.maven {
            name mirror.name ?: mirror.id
            url mirror.url
            if (server?.username != null && server?.password != null) {
                credentials {
                    username = server.username
                    password = server.password
                }
            }
        }
    }
}