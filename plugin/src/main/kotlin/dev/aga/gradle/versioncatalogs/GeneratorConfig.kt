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
     * Function to generate the name of the library in the generated catalog. The default behavior
     * takes the output of the [aliasPrefixGenerator] and the output of the [aliasSuffixGenerator]
     * and concatenates them together with a `-`. If the `prefix` is blank, only the `suffix` is
     * used. Alias generation can be customized by overriding the [aliasPrefixGenerator] and the
     * [aliasSuffixGenerator]. If this function itself is overridden, those two functions will not
     * be used unless explicitly invoked by the overridden function.
     *
     * @see DEFAULT_ALIAS_GENERATOR
     */
    var libraryAliasGenerator: (String, String) -> String = { groupId, artifactId ->
        val prefix = aliasPrefixGenerator(groupId, artifactId)
        val suffix = aliasSuffixGenerator(prefix, groupId, artifactId)
        DEFAULT_ALIAS_GENERATOR(prefix, suffix)
    }

    /**
     * Function to generate the prefix of the name of the library in the generated catalog. The
     * function takes the `groupId` and `artifactId` of the library and returns a string to use as
     * the prefix of the alias. The prefix is then concatenated with the suffix generated by
     * [aliasSuffixGenerator].
     *
     * @see DEFAULT_ALIAS_PREFIX_GENERATOR
     */
    var aliasPrefixGenerator = DEFAULT_ALIAS_PREFIX_GENERATOR

    /**
     * Function to generate the suffix of the name of the library in the generated catalog. The
     * function takes the prefix generated by [aliasPrefixGenerator], the `groupId`, and the
     * `artifactId` as arguments and returns a string to use as the suffix of the alias by appending
     * it to the prefix.
     *
     * @see DEFAULT_ALIAS_SUFFIX_GENERATOR
     */
    var aliasSuffixGenerator = DEFAULT_ALIAS_SUFFIX_GENERATOR

    /**
     * Function to generate the version reference to use in the generated catalog. The default
     * function removes the string 'version' from the name (in any case) and then replaces multiple
     * occurrences of '.' with a single one. It then converts the string to camelCase.
     */
    var versionNameGenerator = DEFAULT_VERSION_NAME_GENERATOR

    /**
     * Regex to filter the groups (groupId) of dependencies which should be included in the
     * generated catalog. The dependencies which match the regex will be excluded. The default value
     * is `null`.
     */
    var excludeGroups: String? = null

    /**
     * Regex to filter the name (artifactId) of dependencies which should be included in the
     * generated catalog. Dependency names which match the regex will be excluded. The default value
     * is `null`.
     */
    var excludeNames: String? = null

    /**
     * The directory to store our cached TOML file. By default, it will be stored in
     * `build/catalogs` relative to the directory of where the settings file exists. When
     * customizing the cache directory, you probably want to make sure it is cleaned up by the
     * `clean` task. If you pass in a relative path it will be resolved from the root directory. An
     * absolute path will be used exactly as provided.
     */
    @Incubating
    var cacheDirectory: File =
        settings.rootDir.resolve(Paths.get("build", "version-catalogs").toFile())
        set(value) {
            field =
                if (value.isAbsolute) {
                    value
                } else {
                    settings.rootDir.resolve(value)
                }
        }

    /** Whether to enable the caching functionality. Disabled by default. See [cacheDirectory] */
    @Incubating var cacheEnabled = false

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
    var propertyOverrides: Map<String, Any> = emptyMap()

    /**
     * Convenience function to construct a [PropertyOverride] that references a version alias from
     * the same TOML file in which the BOM was sourced.
     *
     * @param alias the version alias to lookup
     */
    fun versionRef(alias: String): PropertyOverride {
        return TomlVersionRef(catalogParser, alias)
    }

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

    /** The provider for the source BOM to generate the dependency from. */
    lateinit var source: () -> Any

    internal lateinit var catalogParser: CatalogParser

    /**
     * Specify the source BOM to generate the version catalog from using standard dependency
     * notation ```group:artifact:version```
     */
    fun from(notation: String) {
        from { dependency(notation) }
    }

    /**
     * Use the library with the given alias from `gradle/libs.versions.toml` as the source of the
     * generated catalog. This is a shortcut for
     *
     * ```kotlin
     * toml {
     *  libraryAlias = "the-bom"
     * }
     * ```
     *
     * And is meant to be used as such:
     * ```kotlin
     * from(toml("my-bom"))
     * ```
     */
    fun toml(libraryAliasName: String): SourceConfig.() -> Unit {
        return { toml { libraryAlias = libraryAliasName } }
    }

    /**
     * Specify the source BOM to generate the version catalog from. BOMs can be specified by using a
     * reference to a library in a toml file, or by using regular dependency notation. To use a toml
     *
     * ```kotlin
     * from {
     *   toml {
     *     libraryName = "spring-boot-dependencies"
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
        val cfg = SourceConfig(settings).apply(sc)
        if (cfg.hasTomlConfig()) {
            catalogParser = FileCatalogParser(cfg.tomlConfig.file)
            source = { catalogParser.findLibrary(cfg.tomlConfig.libraryAlias) }
        } else if (cfg.hasDependency()) {
            source = { cfg.dependencyNotation }
        }
    }

    class SourceConfig(private val settings: Settings) {
        internal lateinit var tomlConfig: TomlConfig
        internal lateinit var dependencyNotation: Any

        fun toml(tc: TomlConfig.() -> Unit) {
            val cfg = TomlConfig(settings).apply(tc)
            require(cfg.isInitialized()) { "Library name must be set" }
            tomlConfig = cfg
        }

        fun dependency(notation: Any) {
            this.dependencyNotation = notation
        }

        internal fun hasTomlConfig(): Boolean {
            return ::tomlConfig.isInitialized
        }

        internal fun hasDependency(): Boolean {
            return ::dependencyNotation.isInitialized
        }
    }

    class TomlConfig(private val settings: Settings) {
        /** The name of the library in the TOML catalog file */
        lateinit var libraryAlias: String

        /** The catalog file containing the BOM library entry */
        var file: File =
            settings.rootDir.toPath().resolve(Paths.get("gradle", "libs.versions.toml")).toFile()

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

        internal fun isInitialized(): Boolean {
            return ::libraryAlias.isInitialized
        }
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
            if (nice) {
                nicePrefix
            } else {
                val split = group.split(".")
                if (INVALID_PREFIXES.contains(split.last())) {
                    require(split.size >= 2) {
                        "Cannot generate alias for ${group}:${artifact}, please provide custom generator"
                    }
                    caseChange(
                        "${split[split.size - 2]}-${split.last()}",
                        CaseFormat.LOWER_HYPHEN,
                        CaseFormat.CAMEL,
                    )
                } else {
                    split.last()
                }
            }
        }

        /**
         * Default function to generate the suffix of the library's alias. The logic is as follows:
         * 1. Replace any '.' characters with a '-'
         * 2. Convert the entirety of the string to camelCase
         */
        @JvmStatic
        val DEFAULT_ALIAS_SUFFIX_GENERATOR: (String, String, String) -> String = { _, _, artifact ->
            val replaced = artifact.replace('.', '-')
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
