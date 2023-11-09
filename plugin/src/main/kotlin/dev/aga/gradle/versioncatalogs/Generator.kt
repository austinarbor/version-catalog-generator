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
        conf: Action<VersionCatalogGeneratorPluginExtension>,
    ): VersionCatalogBuilder {
        (this as ExtensionAware).extensions.configure("generator", conf)

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
        return generate(name, conf, resolver)
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
        config: VersionCatalogGeneratorPluginExtension,
        resolver: DependencyResolver,
    ): VersionCatalogBuilder {
        // need to clean up this logic so that we don't double-resolve the first
        // dependency. I think the resolver interface/logic could use some
        // improvement as well
        val bomDep =
            when (val src = config.source()) {
                is Dependency -> resolver.resolve(src)
                else -> resolver.resolve(src)
            }.let {
                Dependency().apply {
                    this.groupId = it.groupId ?: it.parent?.groupId
                    this.artifactId = it.artifactId
                    this.version = it.version
                }
            }

        return create(name) {
            val props = mutableMapOf<String, String>()
            val seenModules = mutableSetOf<String>()
            val queue = ArrayDeque(listOf(bomDep))
            while (queue.isNotEmpty()) {
                val dep = queue.removeFirst()
                val pom = resolver.resolve(dep)
                loadBom(pom, config, queue, props, seenModules)
            }
        }
    }

    /**
     * Traverse the BOM and create a version reference for each property value. If the BOM contains
     * properties with names we have previously seen, we will ignore that version and the
     * dependencies mapped to that version.
     *
     * @param model the BOM
     * @param config [VersionCatalogGeneratorPluginExtension]
     * @param queue the BFS queue to add more BOMs into
     * @param props the version properties
     * @param seenModules the set of modules we have already created libraries for
     */
    internal fun VersionCatalogBuilder.loadBom(
        model: Model,
        config: VersionCatalogGeneratorPluginExtension,
        queue: MutableList<Dependency>,
        props: MutableMap<String, String>,
        seenModules: MutableSet<String>,
    ) {
        val (newProps, dupes) = getProperties(model, config.versionNameGenerator, props.keys)
        if (dupes.isNotEmpty()) {
            logger.warn(
                "found {} duplicate version keys while loading bom {}:{}:{}",
                dupes.size,
                model.groupId,
                model.artifactId,
                model.version,
            )
        }

        val subst = newProps.toSubstitutor()

        val finalProps = newProps.map { (k, v) -> subst.replace(k) to subst.replace(v) }.toMap()

        finalProps.forEach { (key, value) -> version(key, value) }
        props.putAll(finalProps)
        loadDependencies(model, config, queue, props, dupes, seenModules)
    }

    /**
     * Traverse the BOM and create a library from each dependency. Any dependency that has a version
     * that exists as a key in [excludedProps] will be ignored. Any dependencies we encounter in the
     * BOM that have `type == "pom" && scope == "import"` will be added to the [queue].
     *
     * @param model the BOM
     * @param config [VersionCatalogGeneratorPluginExtension]
     * @param queue the BFS queue to add more BOMs into
     * @param props the version properties
     * @param excludedProps the set of version properties to ignore
     * @param seenModules the set of modules we have already created libraries for
     */
    internal fun VersionCatalogBuilder.loadDependencies(
        model: Model,
        config: VersionCatalogGeneratorPluginExtension,
        queue: MutableList<Dependency>,
        props: Map<String, String>,
        excludedProps: Set<String>,
        seenModules: MutableSet<String>,
    ) {
        val substitutor = props.toSubstitutor()

        getNewDependencies(model, config, seenModules, substitutor, importFilter).forEach {
            (version, boms) ->
            boms.forEach { bom ->
                logger.info("${model.groupId}:${model.artifactId} contains other BOMs")
                createLibrary(bom, version, substitutor, config)
                // if the version is a property, replace it with the
                // actual version value
                if (version.isRef) {
                    bom.version = substitutor.replace(version.value)
                }
                queue.add(bom)
            }
        }

        getNewDependencies(model, config, seenModules, substitutor, jarFilter)
            .filter { skipAndLogExcluded(model, it, excludedProps) }
            .forEach { (version, deps) ->
                val aliases = mutableListOf<String>()
                deps.forEach { dep ->
                    val (alias, _) = createLibrary(dep, version, substitutor, config)
                    if (version.isRef) {
                        aliases += alias
                    }
                }
                if (aliases.isNotEmpty()) {
                    val bundleName = substitutor.unwrap(version.value).replace('.', '-')
                    bundle(bundleName, aliases)
                }
            }
    }

    /**
     * Create a library from the given information. If the [version] exists as a key in properties
     * then the library will be created with a versionRef to it. Otherwise, the version will be set
     * directly on the library
     *
     * @param dep the dependency
     * @param version the version of the dependency, may be a property of actual version
     * @param props the version properties
     * @param config the [VersionCatalogGeneratorPluginExtension]
     * @return the library's alias and true if the version was a reference, or false if it was not
     */
    internal fun VersionCatalogBuilder.createLibrary(
        dep: Dependency,
        version: Version,
        substitutor: StringSubstitutor,
        config: VersionCatalogGeneratorPluginExtension,
    ): Pair<String, Boolean> {
        val alias = config.libraryAliasGenerator(dep.groupId, dep.artifactId)
        val library = library(alias, dep.groupId, dep.artifactId)
        return if (version.isRef) {
            // aliases += alias
            library.versionRef(substitutor.unwrap(version.value))
            alias to true
        } else {
            library.version(version.value)
            alias to false
        }
    }

    internal fun getNewDependencies(
        model: Model,
        config: VersionCatalogGeneratorPluginExtension,
        seenModules: MutableSet<String> = mutableSetOf(),
        substitutor: StringSubstitutor,
        filter: (Dependency) -> Boolean,
    ): Map<Version, List<Dependency>> {
        val deps = model.dependencyManagement?.dependencies ?: listOf<Dependency>()
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
            .onEach { it.version = mapVersion(model, it.version, config.versionNameGenerator) }
            .groupBy { Version(it.version, substitutor.hasReplacement(it.version)) }
    }

    internal fun getProperties(
        model: Model,
        versionMapper: (String) -> String,
        existingProperties: Set<String> = setOf(),
    ): Pair<Map<String, String>, Set<String>> {
        val props = model.properties
        val (newProps, dupes) =
            props
                .propertyNames()
                .asSequence()
                .mapNotNull { it as? String }
                .map { mapVersion(model, it, versionMapper) to props.getProperty(it) }
                .partition { !existingProperties.contains(it.first) }

        // turn the dupes into a set of strings
        val dupeVersions = dupes.asSequence().map { it.first }.toSet()

        return newProps.toMap() to dupeVersions
    }

    internal fun mapGroup(model: Model, group: String): String {
        if ("\${project.groupId}" == group) {
            return model.groupId
        }
        return group
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
        if (excludedProps.contains(version.value)) {
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
