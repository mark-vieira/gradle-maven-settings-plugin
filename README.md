[ ![Download](https://api.bintray.com/packages/markvieira/maven/gradle-maven-settings-plugin/images/download.svg) ](https://bintray.com/markvieira/maven/gradle-maven-settings-plugin/_latestVersion)

# Gradle Maven settings plugin

This Gradle plugin provides a migration path for projects coming from a Maven ecosystem. It exposes standard Maven
configuration located in [settings files](http://maven.apache.org/settings.html) to your Gradle project. This allows 
projects to continue to leverage functionality provided by Maven such as mirrors as well use existing
settings configuration to store encrypted repository authentication credentials.

## Usage
To use the plugin, add the following to your `build.gradle` file.

    buildscript {
        repositories {
            jcenter()
        }
        
        dependencies {
            classpath 'net.linguica.gradle:maven-settings-plugin:0.3'
        }
    }

    apply plugin: 'net.linguica.maven-settings'
    
For Gradle 2.1+ you can use the new plugin mechanism to download the plugin from the 
[Gradle Plugin Portal](http://plugins.gradle.org/).
    
    plugins {
      id "net.linguica.maven-settings" version "0.3"
    }

## Mirrors
The plugin exposes Maven-like mirror capabilities. The plugin will properly register and enforce any 
mirrors defined in a `settings.xml` with `<mirrorOf>` values of `*`, `external:*` or `central`. Existing 
`repositories {...}` definitions that match these identifiers will be removed. 

## Credentials
The plugin will attempt to apply credentials located in `<server>` elements to appropriate Maven repository 
definitions in your build script. This is done by matching the `<id>` element in the `settings.xml` file to the `name`
property of the repository definition.

    repositories {
        maven {
            name = 'myRepo' // should match <id>myRepo</id> of appropriate <server> in settings.xml
            url = 'https://intranet.foo.org/repo'
        }
    }

Server credentials are used for mirrors as well. When mirrors are added the plugin will look for a `<server>` element 
with the same `<id>` and the configured credentials are used and [decrypted](http://maven.apache.org/guides/mini/guide-encryption.html) 
if necessary.

> **Note:** Currently only Basic Authentication using username and password is supported at this time.

## Profiles
Profiles defined in a `settings.xml` will have their properties exported to the Gradle project when the profile is considered
active. Active profiles are those listed in the `<activeProfiles>` section of the `settings.xml`, the `activeProfiles`
property of the `mavenSettings {...}` configuration closure, or those that satisfy the given profile's `<activation>`
criteria.

## Configuration
Configuration of the Maven settings plugin is done via the `mavenSettings {...}` configuration closure. The following 
properties are available.

* `userSettingsFileName` - String representing the path of the file to be used as the user settings file. This defaults to 
`'$USER_HOME/.m2/settings.xml'`
* `activeProfiles` - List of profile ids to treat as active.
* `exportGradleProps` - Flag indicating whether or not Gradle project properties should be exported for the purposes of 
settings file property interpolation and profile activation. This defaults to `true`.