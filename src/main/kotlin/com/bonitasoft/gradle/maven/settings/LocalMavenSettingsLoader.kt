package com.bonitasoft.gradle.maven.settings

import org.apache.maven.settings.DefaultMavenSettingsBuilder
import org.apache.maven.settings.Server
import org.apache.maven.settings.Settings
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest
import org.apache.maven.settings.building.SettingsBuildingException
import org.gradle.api.logging.Logger
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION
import org.sonatype.plexus.components.sec.dispatcher.SecUtil
import java.io.File
import java.nio.file.Paths
import kotlin.math.log


class LocalMavenSettingsLoader(private val extension: MavenSettingsPluginExtension, private val logger: Logger) {
    //This configuration file is the one inside the maven installation
    private val globalSettingsFile = File(System.getenv("M2_HOME").orEmpty()).toPath().resolve("conf").resolve("settings.xml")
    private val settingsSecurityFile = File(System.getProperty("user.home").orEmpty()).resolve(".m2").resolve("settings-security.xm")
    private val cipher = DefaultPlexusCipher()


    /**
     * Loads and merges Maven settings from global and local user configuration files. Returned
     * {@link org.apache.maven.settings.Settings} object includes decrypted credentials.
     *
     * @return Effective settings
     * @throws SettingsBuildingException If the effective settings cannot be built
     */
    fun loadSettings(): Settings {
        val settingsBuildingResult = DefaultSettingsBuilderFactory().newInstance().build(DefaultSettingsBuildingRequest().apply {
            userSettingsFile = extension.getUserSettingsFile()
            globalSettingsFile = this@LocalMavenSettingsLoader.globalSettingsFile.toFile()
            systemProperties = System.getProperties()
            if (globalSettingsFile.exists()) {
                logger.info("Using maven global settings.xml: ${globalSettingsFile.absolutePath}")
            }
            if (userSettingsFile.exists()) {
                logger.info("Using maven user settings.xml does not exists: ${userSettingsFile.absolutePath}")
            } else {
                logger.info("No maven user settings.xml does not exists: ${userSettingsFile.absolutePath}")
            }
        })
        settingsBuildingResult.problems.forEach {
            logger.warn("Maven: $it")
        }
        return settingsBuildingResult.effectiveSettings.decryptCredentials()
    }

    private fun Settings.decryptCredentials(): Settings {
        try {
            val masterPassword = when {
                settingsSecurityFile.exists() && !settingsSecurityFile.isDirectory ->
                    cipher.decryptDecorated(SecUtil.read(settingsSecurityFile.absolutePath, true).master, SYSTEM_PROPERTY_SEC_LOCATION)
                else -> null
            }

            servers.forEach { server ->
                logger.debug("Processing credentials for server ${server.id}")
                server.password?.apply {
                    server.password = handlePasswordDecryption(server, this, masterPassword)
                }
                server.passphrase?.apply {
                    server.passphrase = handlePasswordDecryption(server, this, masterPassword)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Unable to decrypt local Maven settings credentials.", e)
        }
        return this
    }

    private fun handlePasswordDecryption(server: Server, password: String, masterPassword: String?): String {
        if (password.startsWith("\${env.")) {
            logger.warn("It looks like the password provided for the server ${server.id} uses an unknown env variable ${
                password.substring("\${env.".length, password.length - 1)
            }")
        } else if (cipher.isEncryptedString(password)) {
            if (masterPassword == null) {
                throw RuntimeException("Maven settings contains encrypted credentials yet no settings-security.xml exists.")
            }
            val decryptDecorated = cipher.decryptDecorated(password, masterPassword)
            logger.debug("Successfully decrypted password/passphrase for server ${server.id}")
            return decryptDecorated
        }
        return password
    }
}
