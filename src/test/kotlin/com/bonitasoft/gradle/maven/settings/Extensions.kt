package com.bonitasoft.gradle.maven.settings

import org.apache.maven.settings.*
import org.apache.maven.settings.io.DefaultSettingsWriter


fun Settings.mirror(config: Mirror.() -> Unit) {
    this.mirrors.add(Mirror().apply(config))
}

fun Settings.profile(config: Profile.() -> Unit) {
    this.profiles.add(Profile().apply(config))
}

fun Settings.server(config: Server.() -> Unit) {
    this.servers.add(Server().apply(config))
}

fun Profile.repository(config: Repository.() -> Unit) {
    this.repositories.add(Repository().apply(config))
}
