package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.model.PropertyOverride
import dev.aga.gradle.versioncatalogs.model.TomlVersionRef
import dev.aga.gradle.versioncatalogs.service.CatalogParser
import dev.aga.gradle.versioncatalogs.service.FileCatalogParser
import dev.aga.gradle.versioncatalogs.service.PublishedArtifactResolver
import dev.aga.gradle.versioncatalogs.service.dependencyResolutionServices
import dev.aga.gradle.versioncatalogs.service.objects
import java.io.File
import java.nio.file.Paths
import net.pearx.kasechange.CaseFormat
import net.pearx.kasechange.formatter.format
import org.apache.maven.model.Dependency
import org.gradle.api.Incubating
import org.gradle.api.initialization.Settings

class GeneratorConfig(val settings: Settings) {
    /**
     * The version catalog file to use when no specific file is otherwise set in the `from` block.
     * This will default to "gradle/libs.versions.toml" relative to the root directory of the
     * project. Starting with the 4.0 release, this will also be used as the source file to use to
     * look up version aliases when using `versionRef`.
     *
     * ```kotlin
     * defaultVersionCatalog = file("/path/to/libs.versions.toml")
     * // a-library-alias will be looked up in the above TOML file
     * from(toml("a-library-alias")) // deprecated
     * fromToml("a-library-alias"))
     * ```
     */
    var defaultVersionCatalog: File =
        settings.rootDir.toPath().resolve(Paths.get("gradle", "libs.versions.toml")).toFile()

    /**
     * Function to generate the name of the library in the generated catalog. The default behavior
     * takes the output of the [aliasPrefixGenerator] and the output of the [aliasSuffixGenerator]
     * and concatenates them together with a `-`. If the `prefix` is blank, only the `suffix` is
     * used. Alias generation can be customized by overriding the [aliasPrefixGenerator] and the
     * [aliasSuffixGenerator]. If this function itself is overridden, those two functions will not
     * be used unless explicitly invoked by the overridden function.
     *
     * @see DEFAULT_ALIAS_GENERATOR
     */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          libraryAliasGenerator = { groupId, artifactId ->
            val prefix = aliasPrefixGenerator(groupId, artifactId)
            val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
            DEFAULT_ALIAS_GENERATOR(prefix, suffix)
          }
        }
    """,
            ),
    )
    var libraryAliasGenerator: (String, String) -> String = { groupId, artifactId ->
        val prefix = aliasPrefixGenerator(groupId, artifactId)
        val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
        DEFAULT_ALIAS_GENERATOR(prefix, suffix)
    }
        set(value) {
            field = value
            usingConfig.libraryAliasGenerator = value
        }

    /**
     * Function to generate the prefix of the name of the library in the generated catalog. The
     * function takes the `groupId` and `artifactId` of the library and returns a string to use as
     * the prefix of the alias. The prefix is then concatenated with the suffix generated by
     * [aliasSuffixGenerator].
     *
     * @see DEFAULT_ALIAS_PREFIX_GENERATOR
     */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          aliasPrefixGenerator = GeneratorConfig.DEFAULT_ALIAS_PREFIX_GENERATOR
        }
    """,
            ),
    )
    var aliasPrefixGenerator = DEFAULT_ALIAS_PREFIX_GENERATOR
        set(value) {
            field = value
            usingConfig.aliasPrefixGenerator = value
        }

    /**
     * Function to generate the suffix of the name of the library in the generated catalog. The
     * function takes the prefix generated by [aliasPrefixGenerator], the `groupId`, and the
     * `artifactId` as arguments and returns a string to use as the suffix of the alias by appending
     * it to the prefix.
     *
     * @see DEFAULT_ALIAS_SUFFIX_GENERATOR
     */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          aliasSuffixGenerator = GeneratorConfig.DEFAULT_ALIAS_SUFFIX_GENERATOR
        }
    """,
            ),
    )
    var aliasSuffixGenerator = DEFAULT_ALIAS_SUFFIX_GENERATOR
        set(value) {
            field = value
            usingConfig.aliasSuffixGenerator = value
        }

    /**
     * Function to generate the version reference to use in the generated catalog. The default
     * function removes the string 'version' from the name (in any case) and then replaces multiple
     * occurrences of '.' with a single one. It then converts the string to camelCase.
     */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          versionNameGenerator = GeneratorConfig.DEFAULT_VERSION_NAME_GENERATOR
        }
    """,
            ),
    )
    var versionNameGenerator = DEFAULT_VERSION_NAME_GENERATOR
        set(value) {
            field = value
            usingConfig.versionNameGenerator = value
        }

    /**
     * Regex to filter the groups (groupId) of dependencies which should be included in the
     * generated catalog. The dependencies which match the regex will be excluded. The default value
     * is `null`.
     */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          excludeGroups = ".*"
        }
    """,
            ),
    )
    var excludeGroups: String? = null
        set(value) {
            field = value
            usingConfig.excludeGroups = value
        }

    /**
     * Regex to filter the name (artifactId) of dependencies which should be included in the
     * generated catalog. Dependency names which match the regex will be excluded. The default value
     * is `null`.
     */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          excludeNames = ".*"
        }
    """,
            ),
    )
    var excludeNames: String? = null
        set(value) {
            field = value
            usingConfig.excludeNames = value
        }

    /** When true, an entry for the BOM itself will be added to the catalog. */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          generateBomEntry = true
        }
    """,
            ),
    )
    var generateBomEntry: Boolean = false
        set(value) {
            field = value
            usingConfig.generateBomEntry = value
        }

    /**
     * The directory to store the generated TOML catalog file. By default, it will be stored in
     * `build/catalogs` relative to the directory of where the settings file exists. An absolute
     * path will be used exactly as provided.
     */
    @Incubating
    var saveDirectory: File =
        settings.rootDir.resolve(Paths.get("build", "version-catalogs").toFile())
        set(value) {
            field =
                if (value.isAbsolute) {
                    value
                } else {
                    settings.rootDir.resolve(value)
                }
        }

    /** Whether to enable the caching functionality. Disabled by default. See [saveDirectory] */
    @Incubating var saveGeneratedCatalog = false

    /**
     * Override property values that are set in the root BOM you are generating a catalog for. For
     * example if the BOM has the property `jackson-bom.version` with the value `2.15.3` but you'd
     * rather use `2.16.1`, you can pass in values to override the BOM.
     *
     * The valid types of values that can be set are either [String] or [PropertyOverride]. A string
     * value will be taken literally, while a [PropertyOverride] can be used for more advanced use
     * cases. The convenience function [versionRef] is available to create a [PropertyOverride] that
     * references a version alias from the same TOML file that contains the source BOM. As such,
     * using this function without sourcing your BOM from a TOML will cause an exception. Setting a
     * value of any type other than `String` or `PropertyOverride` will also cause an exception.
     *
     * ```kotlin
     * propertyOverrides = mapOf("jackson-bom" to "2.16.1", "mockito-bom" to versionRef("mockito"))
     * ```
     */
    @Deprecated(
        message = """Use "using" block instead""",
        replaceWith =
            ReplaceWith(
                """
        using {
          propertyOverrides = mapOf("a" to "1.0.0")
        }
    """,
            ),
    )
    var propertyOverrides: Map<String, Any> = emptyMap()
        set(value) {
            field = value
            usingConfig.propertyOverrides = value
        }

    internal var usingConfig =
        UsingConfig().apply {
            aliasPrefixGenerator = DEFAULT_ALIAS_PREFIX_GENERATOR
            aliasSuffixGenerator = DEFAULT_ALIAS_SUFFIX_GENERATOR
            versionNameGenerator = DEFAULT_VERSION_NAME_GENERATOR
            generateBomEntry = false
            propertyOverrides = emptyMap()
        }

    /**
     * Convenience function to construct a [PropertyOverride] that references a version alias from
     * the _first_ TOML that is declared. This is preserved for backwards compatibility in 3.x, but
     * will be changed or removed in a future release.
     *
     * @param alias the version alias to lookup
     */
    @Deprecated(
        """The functionality of versionRef will be changed in the next major release. 
            Marking as deprecated to bring attention to the change in functionality.
            Starting with the next major release, versionRef when called from the GeneratorConfig scope
            will look up the alias from the TOML defined in defaultVersionCatalog
            """,
        replaceWith =
            ReplaceWith(
                """
            defaultVersionCatalog = file("/path/to/somewhere/libs.versions.toml")
            // someAlias will be fetched from defaultVersionCatalog
            propertyOverrides = mapOf("my.version" to versionRef("someAlias"))
        """,
            ),
    )
    fun versionRef(alias: String): PropertyOverride {
        return TomlVersionRef(catalogParser, alias)
    }

    /**
     * List of lambdas which when invoked will return a [Pair] with the first element being a
     * [SourceConfig] and the second element being a list of BOMs to load from the [SourceConfig].
     * Each BOM may be represented as either a [Dependency] or a string notation of a dependency to
     * resolve.
     */
    internal val sources: MutableList<() -> Pair<SourceConfig, List<Any>>> = mutableListOf()

    internal lateinit var catalogParser: CatalogParser

    /**
     * Specify one or more source BOMs to generate the version catalog from using standard
     * dependency notation `group:artifact:version`
     *
     * @param notation the first BOM's dependency notation to generate the catalog from
     * @param others one or more other BOM dependency notations to include in the generated catalog
     */
    fun from(notation: String, vararg others: String) {
        from(notation = notation, others = others, uc = {})
    }

    /**
     * Specify one or more source BOMs to generate the version catalog from using standard
     * dependency notation `group:artifact:version`, and further customize the generation options by
     * setting the [UsingConfig]
     *
     * *Note: Due to the combination of varargs and a trailing lambda, it may not be possible to
     * call this method from the Groovy DSL.*
     *
     * @param notation the first BOM's dependency notation to generate the catalog from
     * @param others one or more other BOM dependency notations to include in the generated catalog
     * @param uc the configuration block for the [UsingConfig]
     */
    fun from(notation: String, vararg others: String, uc: UsingConfig.() -> Unit) {
        from {
            dependency(notation, *others)
            using(uc)
        }
    }

    /**
     * Specify one or more aliases from [defaultVersionCatalog] that are BOMs to generate the
     * version catalog from.
     *
     * @param libraryAliasName the first alias in [defaultVersionCatalog] to use in the generated
     *   version catalog
     * @param otherAliases one or more other aliases in [defaultVersionCatalog] to use in the
     *   generated version catalog
     */
    fun fromToml(libraryAliasName: String, vararg otherAliases: String) {
        fromToml(libraryAliasName = libraryAliasName, otherAliases = otherAliases, uc = {})
    }

    /**
     * Specify one or more BOM aliases from [defaultVersionCatalog] that to generate the version
     * catalog from and further customize the [UsingConfig].
     *
     * *Note: Due to the combination of varargs and a trailing lambda, it may not be possible to
     * call this method from the Groovy DSL.*
     *
     * @param libraryAliasName the first alias in [defaultVersionCatalog] to use in the generated
     *   version catalog
     * @param otherAliases one or more other aliases in [defaultVersionCatalog] to use in the
     *   generated version catalog
     * @param uc the configuration block for the [UsingConfig] inside of the [SourceConfig]
     */
    fun fromToml(
        libraryAliasName: String,
        vararg otherAliases: String,
        uc: UsingConfig.() -> Unit,
    ) {
        from {
            toml { libraryAliases = listOf(libraryAliasName, *otherAliases) }
            using(uc)
        }
    }

    /**
     * Use the library with the given alias from [defaultVersionCatalog] as the source of the
     * generated catalog. This is a shortcut for
     *
     * ```kotlin
     * toml {
     *  libraryAliases = listOf("my-bom", "another-optional-bom")
     * }
     * ```
     *
     * And is meant to be used as such:
     * ```kotlin
     * from(toml("my-bom", "another-optional-bom"))
     * ```
     *
     * @param libraryAliasName the first BOM alias to use from the TOML file to generate the catalog
     *   from
     * @param otherAliases one or more other BOM aliases to include in the generated catalog
     */
    @Deprecated(
        message = "Use fromToml instead of from(toml(...))",
        replaceWith = ReplaceWith(expression = """fromToml("my-alias", "my-other-alias")"""),
    )
    fun toml(libraryAliasName: String, vararg otherAliases: String): SourceConfig.() -> Unit {
        return { toml { libraryAliases = listOf(libraryAliasName, *otherAliases) } }
    }

    /**
     * Apply customization options to the generation logic. Options set here will be applied to
     * subsequent sources declared in a `from` function or block unless that option is specifically
     * overridden within that `from` declaration.
     *
     * ```kotlin
     * using {
     *   aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
     *   // etc
     * }
     * ```
     */
    fun using(uc: UsingConfig.() -> Unit) {
        uc(usingConfig)
    }

    /**
     * Specify the source BOM to generate the version catalog from. BOMs can be specified by using a
     * reference to a library in a TOML file, or by using regular dependency notation. To use a TOML
     *
     * ```kotlin
     * from {
     *   toml {
     *     libraryAliases = listOf("spring-boot-dependencies")
     *     file = File("gradle/libs.versions.toml") // optional, defaults to this value
     *   }
     * }
     * ```
     *
     * To use regular notation
     *
     * ```kotlin
     * from("org.springframework.boot:spring-boot-dependencies:3.1.2")
     * // or
     * from {
     *   dependency("org.springframework.boot:spring-boot-dependencies:3.1.2")
     * }
     * ```
     *
     * @param sc the config block
     */
    fun from(sc: SourceConfig.() -> Unit) {
        from(sc = sc, uc = {})
    }

    /**
     * Specify the source BOM to generate the version catalog from. BOMs can be specified by using a
     * reference to a library in a TOML file, or by using regular dependency notation. This function
     * is primarily provided to provide easier access to the [UsingConfig] when using the [fromToml]
     * shortcut function.
     *
     * To use a TOML
     *
     * ```kotlin
     * from {
     *   toml {
     *     libraryAliases = listOf("spring-boot-dependencies")
     *     file = File("gradle/libs.versions.toml") // optional, defaults to this value
     *   }
     * }
     * ```
     *
     * To use regular notation
     *
     * ```kotlin
     * from("org.springframework.boot:spring-boot-dependencies:3.1.2")
     * // or
     * from {
     *   dependency("org.springframework.boot:spring-boot-dependencies:3.1.2")
     * }
     * ```
     *
     * @param sc the config block
     * @param uc the configuration block for the [UsingConfig] within [SourceConfig]
     */
    internal fun from(sc: SourceConfig.() -> Unit, uc: UsingConfig.() -> Unit) {
        val cfg = SourceConfig(settings, defaultVersionCatalog).apply(sc).apply { using(uc) }
        if (cfg.hasTomlConfig()) {
            // to preserve backwards compatibility, only set the top-level
            // catalogParser from the first TOML config
            if (!::catalogParser.isInitialized) {
                catalogParser = FileCatalogParser(cfg.tomlConfig.file)
            }
            sources.add {
                cfg to cfg.tomlConfig.libraryAliases.map { catalogParser.findLibrary(it) }
            }
        } else if (cfg.hasDependency()) {
            sources.add { cfg to cfg.dependencyNotations }
        }
    }

    class UsingConfig {
        /**
         * Function to generate the name of the library in the generated catalog. The default
         * behavior takes the output of the [aliasPrefixGenerator] and the output of the
         * [aliasSuffixGenerator] and concatenates them together with a `-`. If the `prefix` is
         * blank, only the `suffix` is used. Alias generation can be customized by overriding the
         * [aliasPrefixGenerator] and the [aliasSuffixGenerator]. If this function itself is
         * overridden, those two functions will not be used unless explicitly invoked by the
         * overridden function.
         *
         * @see DEFAULT_ALIAS_GENERATOR
         */
        lateinit var libraryAliasGenerator: (String, String) -> String

        /**
         * Function to generate the prefix of the name of the library in the generated catalog. The
         * function takes the `groupId` and `artifactId` of the library and returns a string to use
         * as the prefix of the alias. The prefix is then concatenated with the suffix generated by
         * [aliasSuffixGenerator].
         *
         * @see DEFAULT_ALIAS_PREFIX_GENERATOR
         */
        lateinit var aliasPrefixGenerator: (String, String) -> String

        /**
         * Function to generate the suffix of the name of the library in the generated catalog. The
         * function takes the prefix generated by [aliasPrefixGenerator], the `groupId`, and the
         * `artifactId` as arguments and returns a string to use as the suffix of the alias by
         * appending it to the prefix.
         *
         * @see DEFAULT_ALIAS_SUFFIX_GENERATOR
         */
        lateinit var aliasSuffixGenerator: (String, String, String) -> String

        /**
         * Function to generate the version reference to use in the generated catalog. The default
         * function removes the string 'version' from the name (in any case) and then replaces
         * multiple occurrences of '.' with a single one. It then converts the string to camelCase.
         */
        lateinit var versionNameGenerator: (String) -> String

        /**
         * Regex to filter the groups (groupId) of dependencies which should be included in the
         * generated catalog. The dependencies which match the regex will be excluded. The default
         * value is `null`.
         */
        var excludeGroups: String? = null

        /**
         * Regex to filter the name (artifactId) of dependencies which should be included in the
         * generated catalog. Dependency names which match the regex will be excluded. The default
         * value is `null`.
         */
        var excludeNames: String? = null

        /** When true, an entry for the BOM itself will be added to the catalog. */
        var generateBomEntry: Boolean? = null

        /**
         * Override property values that are set in the root BOM you are generating a catalog for.
         * For example if the BOM has the property `jackson-bom.version` with the value `2.15.3` but
         * you'd rather use `2.16.1`, you can pass in values to override the BOM.
         *
         * The valid types of values that can be set are either [String] or [PropertyOverride]. A
         * string value will be taken literally, while a [PropertyOverride] can be used for more
         * advanced use cases. The convenience function [versionRef] is available to create a
         * [PropertyOverride] that references a version alias from the same TOML file that contains
         * the source BOM. As such, using this function without sourcing your BOM from a TOML will
         * cause an exception. Setting a value of any type other than `String` or `PropertyOverride`
         * will also cause an exception.
         *
         * ```kotlin
         * propertyOverrides = mapOf("jackson-bom" to "2.16.1", "mockito-bom" to versionRef("mockito"))
         * ```
         */
        var propertyOverrides: Map<String, Any> = emptyMap()

        internal val excludeFilter: (Dependency) -> Boolean by lazy {
            {
                val eg = excludeGroups
                val en = excludeNames
                if (eg == null && en == null) {
                    false
                } else {
                    // default to true because if one of the regexes is non-null, then
                    // the null value should basically be equivalent to always matching
                    val excludeGroup = eg?.toRegex()?.matches(it.groupId) ?: true
                    val excludeName = en?.toRegex()?.matches(it.artifactId) ?: true
                    excludeGroup && excludeName
                }
            }
        }

        companion object {
            fun merge(primary: UsingConfig, fallback: UsingConfig): UsingConfig {
                return UsingConfig().apply {
                    aliasPrefixGenerator =
                        if (primary::aliasPrefixGenerator.isInitialized) {
                            primary.aliasPrefixGenerator
                        } else {
                            fallback.aliasPrefixGenerator
                        }

                    aliasSuffixGenerator =
                        if (primary::aliasSuffixGenerator.isInitialized) {
                            primary.aliasSuffixGenerator
                        } else {
                            fallback.aliasSuffixGenerator
                        }

                    // this one is a little tricky to set
                    // if the primary config has a custom generator set, use that
                    // otherwise if the fallback has a custom generator set, use that
                    // if none of the above conditions are true, re-construct the default
                    // generator logic using the set prefix and suffix generators from above
                    libraryAliasGenerator =
                        if (primary::libraryAliasGenerator.isInitialized) {
                            primary.libraryAliasGenerator
                        } else if (fallback::libraryAliasGenerator.isInitialized) {
                            fallback.libraryAliasGenerator
                        } else {
                            { groupId, artifactId ->
                                val prefix = aliasPrefixGenerator(groupId, artifactId)
                                val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
                                DEFAULT_ALIAS_GENERATOR(prefix, suffix)
                            }
                        }

                    versionNameGenerator =
                        if (primary::versionNameGenerator.isInitialized) {
                            primary.versionNameGenerator
                        } else {
                            fallback.versionNameGenerator
                        }

                    excludeGroups =
                        if (primary.excludeGroups != null) {
                            primary.excludeGroups
                        } else {
                            fallback.excludeGroups
                        }

                    excludeNames =
                        if (primary.excludeNames != null) {
                            primary.excludeNames
                        } else {
                            fallback.excludeNames
                        }

                    generateBomEntry =
                        if (primary.generateBomEntry != null) {
                            primary.generateBomEntry
                        } else {
                            fallback.generateBomEntry
                        }

                    propertyOverrides =
                        if (primary.propertyOverrides.isNotEmpty()) {
                            primary.propertyOverrides
                        } else {
                            fallback.propertyOverrides
                        }
                }
            }
        }
    }

    class SourceConfig(
        private val settings: Settings,
        private val defaultVersionCatalog: File,
    ) {
        internal lateinit var tomlConfig: TomlConfig
        internal lateinit var dependencyNotations: List<Any>
        internal lateinit var catalogParser: CatalogParser
        internal var usingConfig: UsingConfig = UsingConfig()

        fun toml(tc: TomlConfig.() -> Unit) {
            val cfg = TomlConfig(settings, defaultVersionCatalog).apply(tc)
            require(cfg.isValid()) { "One or more library names must be set" }
            tomlConfig = cfg
            catalogParser = FileCatalogParser(cfg.file)
        }

        fun dependency(notation: Any, vararg others: Any) {
            this.dependencyNotations = listOf(notation, *others)
        }

        /**
         * Apply customization options to the generation logic. Options set here will be applied
         * only to the sources declared within this block. Any options which are not explicitly set
         * will take their values from the parent [GeneratorConfig]
         *
         * ```kotlin
         * using {
         *   aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
         *   // etc
         * }
         * ```
         */
        fun using(uc: UsingConfig.() -> Unit) {
            uc(usingConfig)
        }

        fun versionRef(alias: String): TomlVersionRef {
            return TomlVersionRef(catalogParser, alias)
        }

        internal fun hasTomlConfig(): Boolean {
            return ::tomlConfig.isInitialized
        }

        internal fun hasDependency(): Boolean {
            return ::dependencyNotations.isInitialized
        }
    }

    class TomlConfig(private val settings: Settings, defaultVersionCatalog: File) {
        /**
         * The name of the library in the TOML catalog file. Setting this will override any value(s)
         * set in [libraryAliases]
         */
        @Deprecated(
            message = "Use libraryAliases instead",
            replaceWith = ReplaceWith(expression = """libraryAliases = listOf("myAlias")"""),
        )
        var libraryAlias: String? = null
            set(value) {
                requireNotNull(value) { "libraryAlias cannot be null" }
                field = value
                libraryAliases = listOf(value)
            }

        /**
         * The name of the library aliases in the TOML catalog file to load BOMs from. Setting this
         * will replace any value previously set by [libraryAlias]
         */
        var libraryAliases: List<String> = emptyList()

        /**
         * The catalog file containing the BOM library entry. If not specified, will be set to
         * `defaultVersionCatalog` in the parent [GeneratorConfig]
         */
        var file: File = defaultVersionCatalog

        /**
         * If your TOML is a published artifact that can be found in one of the repositories you
         * have configured, you can use dependency function to and notation
         * `groupId:artifactId:version` to fetch the TOML from the repository.
         *
         * ```kotlin
         * file = artifact("io.micrometer.platform:micrometer-platform:4.3.6")
         * ```
         */
        fun artifact(notation: String): File {
            return with(settings.dependencyResolutionManagement.versionCatalogs) {
                val par = PublishedArtifactResolver(objects, dependencyResolutionServices)
                val dep = notation.toDependency()
                par.resolve(dep, "toml")
            }
        }

        internal fun isValid(): Boolean = libraryAliases.isNotEmpty()
    }

    companion object {
        /** Set of prefixes which are not allowed for libraries in version catalogs */
        val INVALID_PREFIXES = setOf("bundles", "plugins", "versions")

        /**
         * Default function to generate the alias from the provided prefix and suffix. If the prefix
         * is blank, the suffix is simply returned. Otherwise, the prefix is concatenated with the
         * suffix by a `-`.
         */
        @JvmStatic
        val DEFAULT_ALIAS_GENERATOR: (String, String) -> String = { prefix, suffix ->
            if (prefix.isBlank()) {
                suffix
            } else {
                "${prefix}-${suffix}"
            }
        }

        /**
         * Default function to generate the prefix of the library's alias. There are a series of
         * special cases we handle to make the prefix generation "nicer". Please see the
         * [documentation](https://austinarbor.github.io/version-catalog-generator/#_prefix_generation)
         * for a list of specially handled prefixes.
         *
         * If none of the above prefixes match, the logic is as follows:
         * 1. The `groupId` is split by `.`
         * 2. If the split only returns a list of one item and the value is any one of
         *    [INVALID_PREFIXES], an [IllegalArgumentException] is thrown.
         * 3. Otherwise, if the split returns a list of more than one item and the last value is any
         *    one of [INVALID_PREFIXES], the last two values are concatenated with a `-` and then
         *    the entirety of the string is converted to camelCase
         * 4. In any other scenario, the last item in the list is returned
         */
        @JvmStatic
        val DEFAULT_ALIAS_PREFIX_GENERATOR: (String, String) -> String = { group, artifact ->
            val (nice, nicePrefix) = nicePrefix(group)
            val split = group.split(".")
            when {
                nice -> nicePrefix
                INVALID_PREFIXES.contains(split.last()) -> {
                    require(split.size >= 2) {
                        "Cannot generate alias for ${group}:${artifact}, please provide custom generator"
                    }
                    caseChange(
                        "${split[split.size - 2]}-${split.last()}",
                        CaseFormat.LOWER_HYPHEN,
                        CaseFormat.CAMEL,
                    )
                }
                else -> split.last()
            }
        }

        /**
         * Default function to generate the suffix of the library's alias. The logic is as follows:
         * 1. Replace any '.' or '_' characters with a '-'
         * 2. Convert the entirety of the string to camelCase
         */
        @JvmStatic
        val DEFAULT_ALIAS_SUFFIX_GENERATOR: (String, String, String) -> String = { _, _, artifact ->
            val replaced = artifact.replace(Regex("[._]"), "-")
            caseChange(replaced, CaseFormat.LOWER_HYPHEN, CaseFormat.CAMEL)
        }

        /** Alias prefix generator function to always return an empty string. */
        @JvmStatic val NO_PREFIX: (String, String) -> String = { _, _ -> "" }

        /**
         * Default function to generate the alias for the version, based on the version in the
         * version string. This function will first replace all case-insensitive instances of the
         * string *version* with an empty string. Then, all instances of two or more consecutive
         * periods are replaced with a single period. Then, any leading or trailing periods are
         * trimmed. Finally, all periods are replaced with a hyphen, and the entire string is
         * converted to camelCase.
         */
        @JvmStatic
        val DEFAULT_VERSION_NAME_GENERATOR: (String) -> String = { version ->
            val versionRegEx = "version".toRegex(RegexOption.IGNORE_CASE)
            val dotRegex = """\.{2,}""".toRegex()
            // replace all instances (case insensitively) of the string 'version' with empty string
            val mapped =
                version
                    .replace(versionRegEx, "")
                    .replace(dotRegex, ".") // 2 or more consecutive become 1
                    .trim('.') // trim leading and trailing periods
                    .replace('.', '-') // replace dots with hyphens

            caseChange(mapped, CaseFormat.LOWER_HYPHEN, CaseFormat.CAMEL)
        }

        /**
         * Function to easily change the case of a string from one format to another
         *
         * @param str the string to change the casing of
         * @param from the starting format
         * @param to the desired format
         * @return the reformatted string
         */
        @JvmStatic
        fun caseChange(str: String, from: CaseFormat, to: CaseFormat): String {
            val split = from.splitToWords(str)
            return to.format(split)
        }

        private val prefixSubstitutions: Map<String, List<Pair<String, String>>> =
            mapOf(
                "com." to
                    listOf(
                        "fasterxml.jackson" to "jackson",
                        "oracle.database" to "oracle",
                        "google.android" to "android",
                        "facebook" to "facebook",
                    ),
                "org." to
                    listOf(
                        "springframework" to "spring",
                        "hibernate" to "hibernate",
                        "apache.httpcomponents" to "httpcomponents",
                        "apache.tomcat" to "tomcat",
                        "eclipse.jetty" to "jetty",
                        "elasticsearch" to "elasticsearch",
                        "firebirdsql" to "firebird",
                        "glassfish.jersey" to "jersey",
                        "jetbrains.kotlinx" to "kotlinx",
                        "jetbrains.kotlin" to "kotlin",
                        "junit" to "junit",
                        "mariadb" to "mariadb",
                        "neo4j" to "neo4j",
                    ),
                "io." to
                    listOf(
                        "projectreactor" to "projectreactor",
                        "zipkin" to "zipkin",
                        "dropwizard" to "dropwizard",
                    ),
                "jakarta." to listOf("" to "jakarta"),
                "commons-" to listOf("" to "commons"),
                "androidx." to listOf("" to "androidx"),
            )

        private fun nicePrefix(group: String): Pair<Boolean, String> {
            return prefixSubstitutions.entries
                .firstOrNull { (tld, _) -> group.startsWith(tld) }
                ?.let { (tld, pairs) ->
                    pairs.firstOrNull { (prefix, _) -> group.startsWith(prefix, tld.length) }
                }
                ?.let { (_, replacement) -> true to replacement } ?: return false to ""
        }
    }
}
