package dev.aga.gradle.versioncatalogs.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory

abstract class SaveTask : DefaultTask() {
    @get:Input abstract val contents: Property<String>
    @get:OutputDirectory abstract val destinationDir: DirectoryProperty
    @get:OutputFile abstract val destinationFile: RegularFileProperty

    private val logger = LoggerFactory.getLogger(SaveTask::class.java)

    @TaskAction
    fun save() {
        with(destinationDir.get()) {
            asFile.mkdirs()
            file(destinationFile.get().asFile.name).asFile.writeText(contents.get())
        }
    }
}
