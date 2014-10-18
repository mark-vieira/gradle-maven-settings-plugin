# Gradle Maven settings plugin

This Gradle plugin provides a migration path for projects coming from a Maven ecosystem. It exposes standard Maven
configuration located in [settings files](http://maven.apache.org/settings.htm) to your Gradle project. This allows 
projects to continue to leverage functionality provided by Maven such as mirrors as well use existing
settings configuration to store encrypted repository authentication credentials.

## Mirrors
The Maven settings plugin exposes Maven-like mirror capabilities. The plugin will properly register and enforce any 
mirrors defined in a `settings.xml` with `<mirrorOf>` values of `*`, `external:*` or `central`. Existing 
`repositories {...}` definitions that match these identifiers will be removed. Credentials located in a matching
`<server>` element are also used, and [decrypted](http://maven.apache.org/guides/mini/guide-encryption.html) if necessary.

> **Note:** Currently only Basic Authentication using username and password is supported at this time.

## Configuration
Configuration of the Maven settings plugin is done via the `mavenSettings {...}` configuration closure. The following 
properties are available.

* `userSettingsFileName` - String representing the path of the file to be used as the user settings file. This defaults to 
`'$USER_HOME/.m2/settings.xml'`