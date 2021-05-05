package com.bonitasoft.gradle.maven.settings

import org.gradle.api.Project
import java.io.File
import javax.inject.Inject

abstract class MavenSettingsPluginExtension @Inject constructor(val project: Project) {

    private val objects = project.objects

    /**
     * Name of settings file to use. String is evaluated using {@link org.gradle.api.Project#file(java.lang.Object)}.
     * Defaults to $USER_HOME/.m2/settings.xml.
     */
    var userSettingsFileName: String = System.getProperty("user.home") + "/.m2/settings.xml"

    /**
     * List of profile ids to treat as active.
     */
    var activeProfiles: Array<String> = emptyArray()

    /**
     * Flag indicating whether or not Gradle project properties should be exported for the purposes of settings file
     * property interpolation and profile activation. Defaults to true.
     */
    var exportGradleProps = true

    fun getUserSettingsFile(): File {
        return project.file(userSettingsFileName)
    }
    /*

    // Example of a property that is mandatory. The task will
    // fail if this property is not set as is annotated with @Optional.
    val message: Property<String> = objects.property(String::class.java)

    // Example of a property that is optional.
    val tag: Property<String> = objects.property(String::class.java)

    // Example of a property with a default set with .convention
    val outputFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file(DEFAULT_OUTPUT_FILE)
    )
     */
}