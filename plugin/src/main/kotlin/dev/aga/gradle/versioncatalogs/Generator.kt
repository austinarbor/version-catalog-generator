package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.model.Version
import dev.aga.gradle.versioncatalogs.service.DependencyResolver
import dev.aga.gradle.versioncatalogs.service.GradleDependencyResolver
import dev.aga.gradle.versioncatalogs.tasks.SaveTask
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.exists
import org.apache.commons.text.StringSubstitutor
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.get
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
        val resolver = GradleDependencyResolver(conf.objects, dependencyResolutionServices)
        return generate(name, conf.objects, conf.config, resolver)
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
        objectFactory: ObjectFactory,
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

        val cachedPath = config.cacheDirectory.resolve(cachedCatalogName(name, bomDep))
        if (cachedPath.exists()) {
            return create(name) { from(objectFactory.fileCollection().from(cachedPath)) }
        }

        return create(name) {
            val seenModules = mutableSetOf<String>()
            val queue = ArrayDeque(listOf(bomDep))
            val container = TomlContainer()
            var rootDep = true
            while (queue.isNotEmpty()) {
                val dep = queue.removeFirst()
                val (model, parentModel) = resolver.resolve(dep)
                loadBom(model, parentModel, config, queue, seenModules, rootDep, container)
                rootDep = false
            }

            config.settings.gradle.projectsEvaluated {
                registerSaveTask(rootProject, config.cacheDirectory, name, bomDep, container)
            }
        }
    }

    internal fun registerSaveTask(
        project: Project,
        cachePath: Path,
        catalogName: String,
        bom: Dependency,
        container: TomlContainer
    ) {
        val fileName = cachedCatalogName(catalogName, bom)
        registerSaveTask(project, cachePath, fileName, container)
    }

    private fun registerSaveTask(
        project: Project,
        cachePath: Path,
        fileName: String,
        container: TomlContainer
    ) {
        with(project) {
            val task =
                tasks.register<SaveTask>("save${fileName}") {
                    destinationDir.set(cachePath.toFile())
                    destinationFile.set(project.file(fileName))
                    contents.set(container.toToml())
                }
            project.tasks["assemble"].finalizedBy(task)
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
        val newProps = getProperties(model, parentModel)
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
    ): Map<String, String> {
        val parentProps = getModelProperties(parentModel, mutableMapOf())
        val modelProps = getModelProperties(model, parentProps.toMutableMap())
        val newProps = HashMap(parentProps).apply { putAll(modelProps) }

        return newProps
    }

    fun getModelProperties(
        model: Model?,
        extraProperties: MutableMap<String, String> = mutableMapOf(),
    ): Map<String, String> {
        if (model == null) {
            return emptyMap()
        }
        val newProps =
            with(model.properties) {
                propertyNames()
                    .asSequence()
                    .mapNotNull { it as? String }
                    .map { mapVersion(model, it) to getProperty(it) }
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

    internal fun cachedCatalogName(name: String, dep: Dependency) =
        "libs.${name}-${dep.artifactId}-${dep.version}.toml"

    /*
    Below methods inspired by / taken from
     https://github.com/F43nd1r/bomVersionCatalog/blob/master/bom-version-catalog/src/main/kotlin/com/faendir/gradle/extensions.kt
     */
    private val MutableVersionCatalogContainer.dependencyResolutionServices:
        Supplier<DependencyResolutionServices>
        get() = accessField("dependencyResolutionServices")

    private fun <T> MutableVersionCatalogContainer.accessField(name: String): T {
        return this.javaClass.superclass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(this) as T
    }
}
