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

import org.apache.maven.settings.Mirror
import org.apache.maven.settings.Profile
import org.apache.maven.settings.Server
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.gradle.api.credentials.HttpHeaderCredentials
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class MavenSettingsPluginTest extends AbstractMavenSettingsTest {

    @Test
    void applyMavenSettingsPlugin() {
        project.with {
            apply plugin: 'org.bonitasoft.maven-settings'
        }

        assertTrue(project.plugins.hasPlugin(MavenSettingsPlugin.class))
    }

    @Test
    void declareGlobalMirror() {
        withSettings {
            mirrors.add new Mirror(id: 'myrepo', mirrorOf: '*', url: 'http://maven.foo.bar')
        }

        addPluginWithSettings()

        project.with {
            repositories {
                mavenLocal()
                jcenter()
                mavenCentral()
            }
        }

        project.evaluate()

        assertThat(project.repositories, hasSize(2))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myrepo'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('MavenLocal'))))
    }

    @Test
    void respectsMirrorExcludes() {
        withSettings {
            mirrors.add new Mirror(id: 'myrepo', mirrorOf: '*,!some-repo', url: 'http://maven.foo.bar')
        }

        addPluginWithSettings()

        project.with {
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    name "some-repo"
                    url "https://example.com"
                }
                maven {
                    name "some-other-repo"
                    url "https://example.com"
                }
            }
        }

        project.evaluate()

        assertThat(project.repositories, hasSize(3))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myrepo'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('MavenLocal'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('some-repo'))))
    }

    @Test
    void declareExternalMirrorWithFileRepo() {
        withSettings {
            mirrors.add new Mirror(id: 'myrepo', mirrorOf: 'external:*', url: 'http://maven.foo.bar')
        }

        addPluginWithSettings()

        project.with {
            repositories {
                mavenLocal()
                jcenter()
                mavenCentral()
                maven {
                    name 'myLocal'
                    url "file://${project.buildDir}/.m2"
                }
            }
        }

        project.evaluate()

        assertThat(project.repositories, hasSize(3))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myrepo'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('MavenLocal'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myLocal'))))
    }

    @Test
    void declareExternalMirrorWithLocalhostRepo() {
        withSettings {
            mirrors.add new Mirror(id: 'myrepo', mirrorOf: 'external:*', url: 'http://maven.foo.bar')
        }

        addPluginWithSettings()

        project.with {
            repositories {
                mavenLocal()
                jcenter()
                mavenCentral()
                maven {
                    name 'myLocal'
                    url "http://localhost/maven"
                }
            }
        }

        project.evaluate()

        assertThat(project.repositories, hasSize(3))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myrepo'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('MavenLocal'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myLocal'))))
    }

    @Test
    void declareMavenCentralMirror() {
        withSettings {
            mirrors.add new Mirror(id: 'myrepo', mirrorOf: 'central', url: 'http://maven.foo.bar')
        }

        addPluginWithSettings()

        project.with {
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    name 'myRemote'
                    url "https://maven.foobar.org/repo"
                }
            }
        }

        project.evaluate()

        assertThat(project.repositories, hasSize(3))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myrepo'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('MavenLocal'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myRemote'))))
    }

    @Test
    void declareMavenCentralMirrorWithoutCentralRepo() {
        withSettings {
            mirrors.add new Mirror(id: 'myrepo', mirrorOf: 'central', url: 'http://maven.foo.bar')
        }

        addPluginWithSettings()

        project.with {
            repositories {
                mavenLocal()
                maven {
                    name 'myRemote'
                    url "https://maven.foobar.org/repo"
                }
            }
        }

        project.evaluate()

        assertThat(project.repositories, hasSize(2))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('MavenLocal'))))
        assertThat(project.repositories, hasItem(hasProperty('name', equalTo('myRemote'))))
    }

    @Test
    void profileActiveWithSettingsActiveProfile() {
        def props = new Properties()
        props.setProperty('myprop', 'true')

        withSettings {
            profiles.add new Profile(id: 'myprofile', properties: props)
            activeProfiles = ['myprofile']
        }

        addPluginWithSettings()

        project.evaluate()

        assertThat(project.properties, hasEntry('myprop', 'true'))
    }

    @Test
    void profileActiveWithExtensionActiveProfile() {
        def props = new Properties()
        props.setProperty('myprop', 'true')

        withSettings {
            profiles.add new Profile(id: 'myprofile', properties: props)
        }

        addPluginWithSettings()

        project.with {
            mavenSettings {
                activeProfiles = ['myprofile']
            }
        }

        project.evaluate()

        assertThat(project.properties, hasEntry('myprop', 'true'))
    }

    @Test
    void credentialsAddedToNamedRepository() {
        withSettings {
            servers.add new Server(id: 'central', username: 'first.last', password: 'secret')
        }
        
        addPluginWithSettings()

        project.with {
            repositories {
                maven {
                    name 'central'
                    url 'https://repo1.maven.org/maven2/'
                }
            }
        }

        project.evaluate()

        assertEquals('first.last', project.repositories.central.credentials.username)
        assertEquals('secret', project.repositories.central.credentials.password)
    }

    @Test
    void basicAuthCredentialsOnlyAddedToMavenRepositories() {
        withSettings {
            servers.add new Server(id: 'flat', username: 'first.last', password: 'secret')
        }

        addPluginWithSettings()

        project.with {
            repositories {
                flatDir {
                    name = 'flat'
                    dirs '.'
                }
            }
        }

        project.evaluate()
    }

    @Test
    void headersAddedToMavenRepositories() {
        def text = '''
            <configuration>
              <httpHeaders>
                <property>
                  <name>Auth-Token</name>
                  <value>secret</value>
                </property>
              </httpHeaders>
            </configuration>
        '''

        def dom = Xpp3DomBuilder.build(new ByteArrayInputStream(text.getBytes()),"UTF-8");
        withSettings {
            servers.add new Server(id: 'central', configuration: dom)
        }

        addPluginWithSettings()

        project.with {
            repositories {
                maven {
                    name 'central'
                    url 'https://repo1.maven.org/maven2/'
                }
            }
        }

        project.evaluate()

        assertEquals('Auth-Token', project.repositories.central.getCredentials(HttpHeaderCredentials.class).getName())
        assertEquals('secret', project.repositories.central.getCredentials(HttpHeaderCredentials.class).getValue())
    }

    @Test
    void basicAuthCredentialsAddedToPublishingRepository() {
        withSettings {
            servers.add new Server(id: 'central', username: 'first.last', password: 'secret')
        }

        addPluginWithSettings()

        project.with {
            apply plugin: 'maven-publish'

            publishing {
                repositories {
                    maven {
                        name 'central'
                        url 'https://repo1.maven.org/maven2/'
                    }
                }
            }
        }

        project.evaluate()

        assertEquals('first.last', project.publishing.repositories.central.credentials.username)
        assertEquals('secret', project.publishing.repositories.central.credentials.password)
    }
}
