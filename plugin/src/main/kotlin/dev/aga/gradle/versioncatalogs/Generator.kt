package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.model.PropertyOverride
import dev.aga.gradle.versioncatalogs.model.Version
import dev.aga.gradle.versioncatalogs.service.DependencyResolver
import dev.aga.gradle.versioncatalogs.service.GradleDependencyResolver
import dev.aga.gradle.versioncatalogs.service.PublishedArtifactResolver
import dev.aga.gradle.versioncatalogs.service.dependencyResolutionServices
import dev.aga.gradle.versioncatalogs.tasks.SaveTask
import java.io.File
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import org.apache.commons.text.StringSubstitutor
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.register
import org.slf4j.LoggerFactory
import org.tomlj.TomlContainer

object Generator {

    private val logger = LoggerFactory.getLogger(Generator::class.java)
    private val jarFilter = { dep: Dependency -> null == dep.type || "jar" == dep.type }
    private val importFilter = { dep: Dependency -> "pom" == dep.type && "import" == dep.scope }

    /** Getter for the extension */
    private val Settings.generatorExt: VersionCatalogGeneratorPluginExtension
        get() =
            (this as ExtensionAware).extensions.getByName("generator")
                as VersionCatalogGeneratorPluginExtension

    /**
     * Generate a version catalog with the provided [name]
     *
     * @param name the name to use for the generated catalog
     * @param conf the [VersionCatalogGeneratorPluginExtension] configuration
     * @return the [VersionCatalogBuilder]
     */
    fun Settings.generate(
        name: String,
        conf: Action<GeneratorConfig>,
    ): VersionCatalogBuilder {
        val action: Action<VersionCatalogGeneratorPluginExtension> = Action {
            val cfg = GeneratorConfig(settings)
            conf.execute(cfg)
            this.config = cfg
        }

        (this as ExtensionAware).extensions.configure("generator", action)

        return this.dependencyResolutionManagement.versionCatalogs.generate(name, generatorExt)
    }

    /**
     * Generate a version catalog with the provided [name].
     *
     * @param name the name of the version catalog
     * @param conf the configured extension
     * @return the [VersionCatalogBuilder]
     */
    internal fun MutableVersionCatalogContainer.generate(
        name: String,
        conf: VersionCatalogGeneratorPluginExtension,
    ): VersionCatalogBuilder {
        val artifactResolver = PublishedArtifactResolver(conf.objects, dependencyResolutionServices)
        val resolver = GradleDependencyResolver(artifactResolver)
        return generate(name, conf.config, resolver)
    }

    /**
     * Generate a version catalog with the provided [name].
     *
     * @param name the name of the version catalog
     * @param config the [VersionCatalogGeneratorPluginExtension]
     * @param resolver the [DependencyResolver]
     * @return the [VersionCatalogBuilder]
     */
    internal fun MutableVersionCatalogContainer.generate(
        name: String,
        config: GeneratorConfig,
        resolver: DependencyResolver,
    ): VersionCatalogBuilder {
        // need to clean up this logic so that we don't double-resolve the first
        // dependency. I think the resolver interface/logic could use some
        // improvement as well
        val bomDep =
            when (val src = config.source()) {
                is Dependency -> src
                is String -> src.toDependency()
                else -> throw IllegalArgumentException("Unable to resolve notation ${src}")
            }

        return create(name) {
            val seenModules = mutableSetOf<String>()
            val queue = ArrayDeque(listOf(bomDep))
            val container = TomlContainer()
            var rootDep = true
            if (config.generateBomEntry) {
                createLibrary(
                    bomDep,
                    Version(bomDep.version, bomDep.version, bomDep.version),
                    config,
                    true,
                    container,
                )
            }

            while (queue.isNotEmpty()) {
                val dep = queue.removeFirst()
                val (model, parentModel) = resolver.resolve(dep)
                loadBom(model, parentModel, config, queue, seenModules, rootDep, container)
                rootDep = false
            }

            if (config.saveGeneratedCatalog) {
                config.settings.gradle.projectsEvaluated {
                    registerSaveTask(rootProject, config.saveDirectory, name, container)
                }
            }
        }
    }

    private fun registerSaveTask(
        project: Project,
        cachePath: File,
        libraryName: String,
        container: TomlContainer,
    ) {
        with(project) {
            val task =
                tasks.register<SaveTask>("save${libraryName}") {
                    destinationDir.set(cachePath)
                    val fileName = "${libraryName}.versions.toml"
                    destinationFile.set(project.file(fileName))
                    contents.set(container.toToml())
                }
            // if we have an assemble task, finalize it by our task
            // otherwise run our task right away
            project.tasks.findByName("assemble")?.finalizedBy(task) ?: task.get().save()
        }
    }

    /**
     * Traverse the BOM and create a version reference for each property value. If the BOM contains
     * properties with names we have previously seen, we will ignore that version and the
     * dependencies mapped to that version.
     *
     * @param model the BOM
     * @param parentModel the parent of the BOM
     * @param config [GeneratorConfig]
     * @param queue the BFS queue to add more BOMs into
     * @param seenModules the set of modules we have already created libraries for
     * @param rootDep true if this is the very first BOM in the tree, otherwise false
     * @param container the container for the TOML file we are generating
     */
    internal fun VersionCatalogBuilder.loadBom(
        model: Model,
        parentModel: Model?,
        config: GeneratorConfig,
        queue: MutableList<Dependency>,
        seenModules: MutableSet<String>,
        rootDep: Boolean,
        container: TomlContainer,
    ) {
        val newProps = getProperties(model, parentModel, config.propertyOverrides)
        val substitutor = newProps.toSubstitutor()
        loadDependencies(model, config, queue, substitutor, seenModules, rootDep, container)
    }

    /**
     * Traverse the BOM and create a library from each dependency. Any dependency that has a version
     * that exists as a key in [excludedProps] will be ignored. Any dependencies we encounter in the
     * BOM that have `type == "pom" && scope == "import"` will be added to the [queue].
     *
     * @param model the BOM
     * @param config the [GeneratorConfig]
     * @param queue the BFS queue to add more BOMs into
     * @param substitutor the [StringSubstitutor] for variable resolution
     * @param seenModules the set of modules we have already created libraries for
     * @param rootDep true if this is the very first BOM in the tree, otherwise false
     * @param container the container for the TOML file we are generating
     */
    internal fun VersionCatalogBuilder.loadDependencies(
        model: Model,
        config: GeneratorConfig,
        queue: MutableList<Dependency>,
        substitutor: StringSubstitutor,
        seenModules: MutableSet<String>,
        rootDep: Boolean,
        container: TomlContainer,
    ) {
        val registeredVersions = mutableSetOf<String>()
        val deps = getNewDependencies(model, seenModules, substitutor, importFilter, config)
        deps.forEach { (version, boms) ->
            boms.forEach { bom ->
                logger.info("${model.groupId}:${model.artifactId} contains other BOMs")
                if (rootDep) {
                    maybeRegisterVersion(
                        version,
                        config.versionNameGenerator,
                        registeredVersions,
                        container,
                    )
                }
                createLibrary(bom, version, config, rootDep, container)
                // if the version is a property, replace it with the
                // actual version value
                if (version.isRef) {
                    bom.version = version.resolvedValue
                }
                queue.add(bom)
            }
        }

        getNewDependencies(model, seenModules, substitutor, jarFilter, config).forEach {
            (version, deps) ->
            if (rootDep) {
                maybeRegisterVersion(
                    version,
                    config.versionNameGenerator,
                    registeredVersions,
                    container,
                )
            }
            val aliases = mutableListOf<String>()
            deps.forEach { dep ->
                val alias = createLibrary(dep, version, config, rootDep, container)
                if (rootDep && version.isRef) {
                    aliases += alias
                }
            }
            if (aliases.isNotEmpty()) {
                registerBundle(version, aliases, config.versionNameGenerator, container)
            }
        }
    }

    internal fun VersionCatalogBuilder.maybeRegisterVersion(
        version: Version,
        versionNameGenerator: (String) -> String,
        registeredVersions: MutableSet<String>,
        container: TomlContainer,
    ) {
        val versionAlias = versionNameGenerator(version.unwrapped)
        if (version.isRef && registeredVersions.add(versionAlias)) {
            version(versionAlias, version.resolvedValue)
            container.addVersion(versionAlias, version.resolvedValue)
        }
    }

    internal fun VersionCatalogBuilder.registerBundle(
        version: Version,
        aliases: List<String>,
        versionNameGenerator: (String) -> String,
        container: TomlContainer,
    ) {
        val bundleName = versionNameGenerator(version.unwrapped).replace('.', '-')
        bundle(bundleName, aliases)
        container.addBundle(bundleName, aliases)
    }

    /**
     * Create a library from the given information. If the [version] exists as a key in properties
     * then the library will be created with a versionRef to it. Otherwise, the version will be set
     * directly on the library
     *
     * @param dep the dependency
     * @param version the version of the dependency, may be a property of actual version
     * @param config the [GeneratorConfig]
     * @param rootDep true if this is the very first BOM in the tree, otherwise false
     * @param container the container for the TOML file we are generating
     * @return the library's alias and true if the version was a reference, or false if it was not
     */
    internal fun VersionCatalogBuilder.createLibrary(
        dep: Dependency,
        version: Version,
        config: GeneratorConfig,
        rootDep: Boolean,
        container: TomlContainer,
    ): String {
        val alias = config.libraryAliasGenerator(dep.groupId, dep.artifactId)
        checkAlias(alias, config, container, dep, version)

        val library = library(alias, dep.groupId, dep.artifactId)
        // only register version aliases if we are in the top-level BOM
        if (rootDep && version.isRef) {
            val versionAlias = config.versionNameGenerator(version.unwrapped)
            library.versionRef(versionAlias)
            container.addLibrary(alias, dep.groupId, dep.artifactId, versionAlias, true)
        } else {
            val value =
                if (version.isRef) {
                    version.resolvedValue
                } else {
                    version.value
                }
            library.version(value)
            container.addLibrary(alias, dep.groupId, dep.artifactId, value, false)
        }
        return alias
    }

    internal fun checkAlias(
        alias: String,
        config: GeneratorConfig,
        container: TomlContainer,
        dep: Dependency,
        version: Version,
    ) {
        if (container.containsLibraryAlias(alias)) {
            val lib = container.getLibrary(alias)
            val ver = container.getLibraryVersionString(alias)
            val group = lib.getString("group")
            val name = lib.getString("name")
            val newVersion =
                if (version.isRef) {
                    config.versionNameGenerator(version.unwrapped)
                } else {
                    version.value
                }
            val msg =
                """
                Attempting to register a library with the alias ${alias} but the alias already exists.
                    Existing: ${group}:${name}:${ver}
                  Attempting: ${dep.groupId}:${dep.artifactId}:${newVersion}
                Please check the source BOM and either exclude the conflict or provide custom prefix/suffix generators.
            """
                    .trimIndent()
            throw IllegalArgumentException(msg)
        }
    }

    internal fun getNewDependencies(
        model: Model,
        seenModules: MutableSet<String> = mutableSetOf(),
        substitutor: StringSubstitutor,
        filter: (Dependency) -> Boolean,
        config: GeneratorConfig,
    ): Map<Version, List<Dependency>> {
        val deps = model.dependencyManagement?.dependencies.orEmpty()
        if (deps.isEmpty()) {
            logger.warn(
                "${model.groupId}:${model.artifactId}:${model.version} does not have any dependencies defined " +
                    "in dependencyManagement",
            )
        }

        return deps
            .asSequence()
            .onEach { it.groupId = mapGroup(model, it.groupId) }
            .filter(filter)
            .filterNot(config.excludeFilter)
            .filter { seenModules.add("${it.groupId}:${it.artifactId}") }
            .onEach { it.version = mapVersion(model, it.version) }
            .groupBy {
                Version(it.version, substitutor.unwrap(it.version), substitutor.replace(it.version))
            }
    }

    internal fun getProperties(
        model: Model,
        parentModel: Model?,
        propertyOverrides: Map<String, Any>,
    ): Map<String, String> {
        val parentProps = getModelProperties(parentModel, mutableMapOf(), propertyOverrides)
        val modelProps = getModelProperties(model, parentProps.toMutableMap(), propertyOverrides)
        val newProps = HashMap(parentProps).apply { putAll(modelProps) }

        return newProps
    }

    fun getModelProperties(
        model: Model?,
        extraProperties: MutableMap<String, String> = mutableMapOf(),
        propertyOverrides: Map<String, Any>,
    ): Map<String, String> {
        if (model == null) {
            return emptyMap()
        }
        val newProps =
            with(model.properties) {
                propertyNames()
                    .asSequence()
                    .mapNotNull { it as? String }
                    .map {
                        // if the overrides contains the property, use that value, otherwise
                        // use the actual value
                        mapVersion(model, it) to getPropertyValue(this, it, propertyOverrides)
                    }
                    .toMap()
            }
        extraProperties["project.version"] = model.version
        val substitutor = newProps.toMap(extraProperties).toSubstitutor()
        val final =
            newProps.asSequence().associate { (k, v) ->
                substitutor.replace(k) to substitutor.replace(v)
            }
        return final
    }

    private fun getPropertyValue(
        properties: Properties,
        propertyName: String,
        overrides: Map<String, Any>,
    ): String {
        return if (overrides.containsKey(propertyName)) {
            when (val override = overrides[propertyName] as Any) {
                is String -> override
                is PropertyOverride ->
                    override.getValue()
                        ?: throw IllegalArgumentException(
                            "No value found for property override ${propertyName}",
                        )
                else ->
                    throw IllegalArgumentException(
                        "Invalid type ${override::class.java} for property override ${propertyName}",
                    )
            }
        } else {
            properties.getProperty(propertyName)
        }
    }

    internal fun mapGroup(model: Model, group: String): String {
        if ("\${project.groupId}" == group) {
            return model.groupId
        }
        return group
    }

    internal fun mapVersion(model: Model, version: String): String {
        if ("\${project.version}" == version) {
            return model.version
        }
        return version
    }
}
