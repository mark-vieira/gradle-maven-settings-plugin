# Gradle Maven settings plugin

This Gradle plugin provides a migration path for projects coming from a Maven ecosystem. It exposes standard Maven
configuration located in [settings files](http://maven.apache.org/settings.htm) to your Gradle project. This allows 
projects to continue to leverage functionality provided by Maven such as profiles and mirrors as well use existing
settings configuration to store and encrypt repository authenticaiton credentials.