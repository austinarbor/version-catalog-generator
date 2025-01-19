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
  fun Settings.generate(name: String, conf: Action<GeneratorConfig>): VersionCatalogBuilder {
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
    val rootDeps: List<Pair<GeneratorConfig.UsingConfig, Dependency>> =
      config.sources.flatMap { src ->
        val (cfg, sources) = src()
        val mergedConfig = GeneratorConfig.UsingConfig.merge(cfg.usingConfig, config.usingConfig)
        sources.map {
          when (it) {
            is Dependency -> mergedConfig to it
            is String -> mergedConfig to it.toDependency()
            else -> throw IllegalArgumentException("Unable to resolve notation ${it}")
          }
        }
      }

    val action =
      Action<VersionCatalogBuilder> {
        val seenModules = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<GeneratorConfig.UsingConfig, Dependency>>(rootDeps)
        val container = TomlContainer()

        while (queue.isNotEmpty()) {
          val (using, dep) = queue.removeFirst()
          // Note: Dependency does not override equals, so we are relying on referential
          // equality
          val rootDep = rootDeps.any { it.second == dep }
          if (rootDep && using.generateBomEntry == true) {
            createLibrary(
              dep,
              Version(dep.version, dep.version, dep.version),
              using,
              true,
              container,
            )
          }
          val (model, parentModel) = resolver.resolve(dep)
          loadBom(model, parentModel, using, queue, seenModules, rootDep, container)
        }

        createBundles(container, config)

        if (config.saveGeneratedCatalog) {
          config.settings.gradle.projectsEvaluated {
            registerSaveTask(rootProject, config.saveDirectory, name, container)
          }
        }
      }

    return when {
      name in names -> getByName(name, action)
      else -> create(name, action)
    }
  }

  private fun VersionCatalogBuilder.createBundles(
    container: TomlContainer,
    config: GeneratorConfig,
  ) {
    container
      .groupBy {
        when (val bundleName = config.bundleMapping(it)) {
          null -> ""
          else -> bundleName
        }
      }
      .filterKeys { it.isNotBlank() }
      .mapValues { (_, values) -> values.map { it.alias } }
      .filterValues { it.isNotEmpty() }
      .forEach { (bundleName, aliases) -> createBundle(bundleName, aliases, container) }
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
   * properties with names we have previously seen, we will ignore that version and the dependencies
   * mapped to that version.
   *
   * @param model the BOM
   * @param parentModel the parent of the BOM
   * @param using [GeneratorConfig.UsingConfig]
   * @param queue the BFS queue to add more BOMs into
   * @param seenModules the set of modules we have already created libraries for
   * @param rootDep true if this is the very first BOM in the tree, otherwise false
   * @param container the container for the TOML file we are generating
   */
  internal fun VersionCatalogBuilder.loadBom(
    model: Model,
    parentModel: Model?,
    using: GeneratorConfig.UsingConfig,
    queue: MutableList<Pair<GeneratorConfig.UsingConfig, Dependency>>,
    seenModules: MutableSet<String>,
    rootDep: Boolean,
    container: TomlContainer,
  ) {
    val newProps = getProperties(model, parentModel, using.propertyOverrides)
    val substitutor = newProps.toSubstitutor()
    loadDependencies(model, using, queue, substitutor, seenModules, rootDep, container)
  }

  /**
   * Traverse the BOM and create a library from each dependency. Any dependency that has a version
   * that exists as a key in [excludedProps] will be ignored. Any dependencies we encounter in the
   * BOM that have `type == "pom" && scope == "import"` will be added to the [queue].
   *
   * @param model the BOM
   * @param using the [GeneratorConfig.UsingConfig]
   * @param queue the BFS queue to add more BOMs into
   * @param substitutor the [StringSubstitutor] for variable resolution
   * @param seenModules the set of modules we have already created libraries for
   * @param rootDep true if this is the very first BOM in the tree, otherwise false
   * @param container the container for the TOML file we are generating
   */
  @Suppress("detekt:NestedBlockDepth")
  internal fun VersionCatalogBuilder.loadDependencies(
    model: Model,
    using: GeneratorConfig.UsingConfig,
    queue: MutableList<Pair<GeneratorConfig.UsingConfig, Dependency>>,
    substitutor: StringSubstitutor,
    seenModules: MutableSet<String>,
    rootDep: Boolean,
    container: TomlContainer,
  ) {
    val registeredVersions = mutableSetOf<String>()
    val deps = getNewDependencies(model, seenModules, substitutor, importFilter, using)
    deps.forEach { (version, boms) ->
      boms.forEach { bom ->
        logger.info("${model.groupId}:${model.artifactId} contains other BOMs")
        if (using.generateBomEntryForNestedBoms == true) {
          if (rootDep) {
            maybeRegisterVersion(version, using.versionNameGenerator, registeredVersions, container)
          }
          createLibrary(bom, version, using, rootDep, container)
        }
        // if the version is a property, replace it with the
        // actual version value
        if (version.isRef) {
          bom.version = version.resolvedValue
        }
        queue.add(using to bom)
      }
    }

    getNewDependencies(model, seenModules, substitutor, jarFilter, using).forEach { (version, deps)
      ->
      if (rootDep) {
        maybeRegisterVersion(version, using.versionNameGenerator, registeredVersions, container)
      }
      deps.forEach { dep -> createLibrary(dep, version, using, rootDep, container) }
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

  internal fun VersionCatalogBuilder.createBundle(
    name: String,
    libraryAliases: List<String>,
    container: TomlContainer,
  ) {
    bundle(name, libraryAliases)
    container.addBundle(name, libraryAliases)
  }

  /**
   * Create a library from the given information. If the [version] exists as a key in properties
   * then the library will be created with a versionRef to it. Otherwise, the version will be set
   * directly on the library
   *
   * @param dep the dependency
   * @param version the version of the dependency, may be a property of actual version
   * @param using the [GeneratorConfig.UsingConfig]
   * @param rootDep true if this is the very first BOM in the tree, otherwise false
   * @param container the container for the TOML file we are generating
   * @return the library's alias and true if the version was a reference, or false if it was not
   */
  internal fun VersionCatalogBuilder.createLibrary(
    dep: Dependency,
    version: Version,
    using: GeneratorConfig.UsingConfig,
    rootDep: Boolean,
    container: TomlContainer,
  ): String {
    val alias = using.libraryAliasGenerator(dep.groupId, dep.artifactId)
    checkAlias(alias, using, container, dep, version)

    val library = library(alias, dep.groupId, dep.artifactId)
    // only register version aliases if we are in the top-level BOM
    if (rootDep && version.isRef) {
      val versionAlias = using.versionNameGenerator(version.unwrapped)
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
    using: GeneratorConfig.UsingConfig,
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
          using.versionNameGenerator(version.unwrapped)
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
    using: GeneratorConfig.UsingConfig,
  ): Map<Version, List<Dependency>> {
    val deps = model.dependencyManagement?.dependencies.orEmpty()
    if (deps.isEmpty()) {
      logger.warn(
        "${model.groupId}:${model.artifactId}:${model.version} does not have any dependencies defined " +
          "in dependencyManagement"
      )
    }

    return deps
      .asSequence()
      .onEach { it.groupId = mapGroup(model, it.groupId) }
      .filter(filter)
      .filterNot(using.excludeFilter)
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
      newProps.asSequence().associate { (k, v) -> substitutor.replace(k) to substitutor.replace(v) }
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
              "No value found for property override ${propertyName}"
            )
        else ->
          throw IllegalArgumentException(
            "Invalid type ${override::class.java} for property override ${propertyName}"
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
