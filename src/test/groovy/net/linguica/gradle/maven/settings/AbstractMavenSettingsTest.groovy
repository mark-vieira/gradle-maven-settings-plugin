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

import org.apache.maven.settings.Settings
import org.apache.maven.settings.io.DefaultSettingsWriter
import org.apache.maven.settings.io.SettingsWriter
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.ConfigureUtil
import org.junit.After
import org.junit.Before

abstract class AbstractMavenSettingsTest {
    File settingsDir = new File('build/tmp/.m2/')
    File settingsFile
    Project project

    @Before
    void createSettingsXml() {
        settingsFile = new File(settingsDir, 'settings.xml')
        project = new ProjectBuilder().build()
    }

    @After
    void deleteSettingsXml() {
        settingsFile.delete()
        project = null
    }

    void withSettings(@DelegatesTo(Settings) Closure configureClosure) {
        Settings settings = new Settings()
        ConfigureUtil.configure(configureClosure, settings)
        SettingsWriter writer = new DefaultSettingsWriter()
        writer.write(settingsFile, null, settings)
    }

    void addPluginWithSettings(Project project) {
        project.with {
            apply plugin: MavenSettingsPlugin

            mavenSettings {
                userSettingsFileName = settingsFile.canonicalPath
            }
        }
    }
}
