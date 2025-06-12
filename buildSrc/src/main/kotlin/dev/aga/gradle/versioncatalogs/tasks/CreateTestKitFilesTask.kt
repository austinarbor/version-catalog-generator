package dev.aga.gradle.versioncatalogs.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class CreateTestKitFilesTask @Inject constructor(private val projectLayout: ProjectLayout) : DefaultTask() {

    @get:OutputDirectory
    val outputDir = projectLayout.buildDirectory.dir("testkit")

    @get:OutputFile
    val testkitClasspath = outputDir.map { it.file("testkit-classpath.txt") }

    @get:OutputFile
    val testkitGradleProperties = outputDir.map { it.file("testkit-gradle.properties")}

    @get:InputFiles
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val jacocoClasspath: Property<String>


    @TaskAction
    fun createTestkitFiles() {
        val jacocoPath = jacocoClasspath.get().replace('\\', '/')
        testkitClasspath.get().asFile.writeText(runtimeClasspath.joinToString("\n"))
        testkitGradleProperties.get().asFile.writeText("org.gradle.jvmargs=-javaagent:${jacocoPath}=destfile=${projectLayout.buildDirectory.asFile.get()}/jacoco/test.exec")
    }
}
