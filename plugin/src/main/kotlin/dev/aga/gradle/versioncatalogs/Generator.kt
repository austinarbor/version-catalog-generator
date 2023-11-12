package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.model.Version
import dev.aga.gradle.versioncatalogs.service.DependencyResolver
import dev.aga.gradle.versioncatalogs.service.GradleDependencyResolver
import java.util.function.Supplier
import org.apache.commons.text.StringSubstitutor
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.plugins.ExtensionAware
import org.slf4j.LoggerFactory

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
            val props = mutableMapOf<String, String>()
            val seenModules = mutableSetOf<String>()
            val queue = ArrayDeque(listOf(bomDep))
            while (queue.isNotEmpty()) {
                val dep = queue.removeFirst()
                val (model, parentModel) = resolver.resolve(dep)
                loadBom(model, parentModel, config, queue, props, seenModules)
            }
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
     * @param props the version properties
     * @param seenModules the set of modules we have already created libraries for
     */
    internal fun VersionCatalogBuilder.loadBom(
        model: Model,
        parentModel: Model?,
        config: GeneratorConfig,
        queue: MutableList<Dependency>,
        props: MutableMap<String, String>,
        seenModules: MutableSet<String>,
    ) {
        val (newProps, dupes) = getProperties(model, parentModel, props.keys)
        if (dupes.isNotEmpty()) {
            logger.warn(
                "found {} duplicate version keys while loading bom {}:{}:{}",
                dupes.size,
                model.groupId,
                model.artifactId,
                model.version,
            )
        }

        val substitutor = newProps.toSubstitutor()
        val usedVersions = loadDependencies(model, config, queue, substitutor, dupes, seenModules)
        newProps.filterKeys { k -> usedVersions.contains(k) }.forEach { (k, v) -> props[k] = v }
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
     * @param excludedProps the set of version properties to ignore
     * @param seenModules the set of modules we have already created libraries for
     */
    internal fun VersionCatalogBuilder.loadDependencies(
        model: Model,
        config: GeneratorConfig,
        queue: MutableList<Dependency>,
        substitutor: StringSubstitutor,
        excludedProps: Set<String>,
        seenModules: MutableSet<String>,
    ): Set<String> {
        val usedVersions = mutableSetOf<String>()
        val deps = getNewDependencies(model, seenModules, substitutor, importFilter)
        deps.forEach { (version, boms) ->
            boms.forEach { bom ->
                logger.info("${model.groupId}:${model.artifactId} contains other BOMs")
                if (version.isRef && usedVersions.add(version.unwrapped)) {
                    registerVersion(version, config.versionNameGenerator)
                }
                createLibrary(bom, version, config)
                // if the version is a property, replace it with the
                // actual version value
                if (version.isRef) {
                    bom.version = substitutor.replace(version.value)
                }
                queue.add(bom)
            }
        }

        getNewDependencies(model, seenModules, substitutor, jarFilter)
            .filter { skipAndLogExcluded(model, it, excludedProps) }
            .forEach { (version, deps) ->
                if (version.isRef && usedVersions.add(version.unwrapped)) {
                    registerVersion(version, config.versionNameGenerator)
                }
                val aliases = mutableListOf<String>()
                deps.forEach { dep ->
                    val alias = createLibrary(dep, version, config)
                    if (version.isRef) {
                        aliases += alias
                    }
                }
                if (aliases.isNotEmpty()) {
                    registerBundle(version, aliases, config.versionNameGenerator)
                }
            }

        return usedVersions
    }

    internal fun VersionCatalogBuilder.registerVersion(
        version: Version,
        versionNameGenerator: (String) -> String,
    ) {
        val versionAlias = versionNameGenerator(version.unwrapped)
        version(versionAlias, version.resolvedValue)
    }

    internal fun VersionCatalogBuilder.registerBundle(
        version: Version,
        aliases: List<String>,
        versionNameGenerator: (String) -> String,
    ) {
        val bundleName = versionNameGenerator(version.unwrapped).replace('.', '-')
        bundle(bundleName, aliases)
    }

    /**
     * Create a library from the given information. If the [version] exists as a key in properties
     * then the library will be created with a versionRef to it. Otherwise, the version will be set
     * directly on the library
     *
     * @param dep the dependency
     * @param version the version of the dependency, may be a property of actual version
     * @param config the [GeneratorConfig]
     * @return the library's alias and true if the version was a reference, or false if it was not
     */
    internal fun VersionCatalogBuilder.createLibrary(
        dep: Dependency,
        version: Version,
        config: GeneratorConfig,
    ): String {
        val alias = config.libraryAliasGenerator(dep.groupId, dep.artifactId)
        val library = library(alias, dep.groupId, dep.artifactId)
        if (version.isRef) {
            val versionAlias = config.versionNameGenerator(version.unwrapped)
            library.versionRef(versionAlias)
        } else {
            library.version(version.value)
        }
        return alias
    }

    internal fun getNewDependencies(
        model: Model,
        seenModules: MutableSet<String> = mutableSetOf(),
        substitutor: StringSubstitutor,
        filter: (Dependency) -> Boolean,
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
            .filter { seenModules.add("${it.groupId}:${it.artifactId}") }
            .onEach { it.version = mapVersion(model, it.version) }
            .groupBy {
                Version(it.version, substitutor.unwrap(it.version), substitutor.replace(it.version))
            }
    }

    internal fun getProperties(
        model: Model,
        parentModel: Model?,
        existingProperties: Set<String> = setOf(),
    ): Pair<Map<String, String>, Set<String>> {
        val (parentProps, parentDupes) = getModelProperties(parentModel, existingProperties)
        val (modelProps, modelDupes) =
            getModelProperties(model, existingProperties, parentProps.toMutableMap())
        // turn the dupes into a set of strings
        val dupeVersions =
            (parentDupes.asSequence() + modelDupes.asSequence()).map { it.key }.toSet()

        val newProps = HashMap(parentProps).apply { putAll(modelProps) }

        return newProps to dupeVersions
    }

    fun getModelProperties(
        model: Model?,
        existingProperties: Set<String>,
        extraProperties: MutableMap<String, String> = mutableMapOf(),
    ): Pair<Map<String, String>, Map<String, String>> {
        if (model == null) {
            return emptyMap<String, String>() to emptyMap()
        }
        val (newProps, dupes) =
            with(model.properties) {
                propertyNames()
                    .asSequence()
                    .mapNotNull { it as? String }
                    .map { mapVersion(model, it) to getProperty(it) }
                    .partition { !existingProperties.contains(it.first) }
            }
        extraProperties["project.version"] = model.version
        val substitutor = newProps.toMap(extraProperties).toSubstitutor()
        val final =
            newProps.associate { (k, v) -> substitutor.replace(k) to substitutor.replace(v) }
        return final to dupes.toMap()
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

    internal fun mapVersion(
        model: Model,
        version: String,
        versionMapper: (String) -> String,
    ): String {
        if ("\${project.version}" == version) {
            return model.version
        }
        return versionMapper(version)
    }

    private fun skipAndLogExcluded(
        source: Model,
        e: Map.Entry<Version, List<Dependency>>,
        excludedProps: Set<String>,
    ): Boolean {
        val (version, deps) = e
        if (excludedProps.contains(version.unwrapped)) {
            deps.forEach {
                logger.warn(
                    "excluding {}:{}:{} found in {}:{}:{}",
                    it.groupId,
                    it.artifactId,
                    it.version,
                    source.groupId,
                    source.artifactId,
                    source.version,
                )
            }
            return false
        } else {
            return true
        }
    }

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
