package org.bonitasoft.gradle.maven.settings

import org.apache.maven.settings.Settings
import org.apache.maven.settings.building.*
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher
import org.sonatype.plexus.components.sec.dispatcher.SecUtil
import java.io.File


class LocalMavenSettingsLoader(private val extension: MavenSettingsPluginExtension) {
    val globalSettingsFile = File(System.getenv("M2_HOME"), "conf/settings.xml")
    val settingsSecurityFileLocation = System.getProperty("user.home") + "/.m2/settings-security.xml"


    /**
     * Loads and merges Maven settings from global and local user configuration files. Returned
     * {@link org.apache.maven.settings.Settings} object includes decrypted credentials.
     *
     * @return Effective settings
     * @throws SettingsBuildingException If the effective settings cannot be built
     */
    fun loadSettings(): Settings {
        val settingsBuildingRequest: SettingsBuildingRequest = DefaultSettingsBuildingRequest()
        settingsBuildingRequest.userSettingsFile = extension.getUserSettingsFile()
        settingsBuildingRequest.globalSettingsFile = globalSettingsFile
        settingsBuildingRequest.systemProperties = System.getProperties()

        val factory = DefaultSettingsBuilderFactory()
        val settingsBuilder: DefaultSettingsBuilder = factory.newInstance()
        val settingsBuildingResult: SettingsBuildingResult = settingsBuilder.build(settingsBuildingRequest)
        val settings: Settings = settingsBuildingResult.effectiveSettings
        decryptCredentials(settings)

        return settings
    }

    fun decryptCredentials(settings: Settings): Unit {
        try {
            val masterPassword: String?
            val cipher = DefaultPlexusCipher()
            val settingsSecurityFile = File(settingsSecurityFileLocation)
            var hasSettingsSecurity = false

            if (settingsSecurityFile.exists() && !settingsSecurityFile.isDirectory) {
                val settingsSecurity = SecUtil.read(settingsSecurityFileLocation, true)
                masterPassword = cipher.decryptDecorated(settingsSecurity.master, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION)
                hasSettingsSecurity = true
            }else{
                masterPassword = null
            }

            for (server in settings.servers) {
                if (cipher.isEncryptedString(server.password)) {
                    if (hasSettingsSecurity) {
                        server.password = cipher.decryptDecorated(server.password, masterPassword!!)
                    } else {
                        throw RuntimeException ("Maven settings contains encrypted credentials yet no settings-security.xml exists.")
                    }
                }

                if (cipher.isEncryptedString(server.passphrase)) {
                    if (hasSettingsSecurity) {
                        server.passphrase = cipher.decryptDecorated(server.passphrase, masterPassword)
                    } else {
                        throw RuntimeException ("Maven settings contains encrypted credentials yet no settings-security.xml exists.")
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Unable to decrypt local Maven settings credentials.", e)
        }
    }
}
