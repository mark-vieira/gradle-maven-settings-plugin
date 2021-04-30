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

package org.bonitasoft.gradle.maven.settings

import org.gradle.api.Project

class MavenSettingsPluginExtension {
    private Project project

    /**
     * Name of settings file to use. String is evaluated using {@link org.gradle.api.Project#file(java.lang.Object)}.
     * Defaults to $USER_HOME/.m2/settings.xml.
     */
    String userSettingsFileName = System.getProperty("user.home") + "/.m2/settings.xml"

    /**
     * List of profile ids to treat as active.
     */
    String[] activeProfiles = []

    /**
     * Flag indicating whether or not Gradle project properties should be exported for the purposes of settings file
     * property interpolation and profile activation. Defaults to true.
     */
    boolean exportGradleProps = true

    MavenSettingsPluginExtension(Project project) {
        this.project = project
    }

    public File getUserSettingsFile() {
        return project.file(userSettingsFileName)
    }
}
