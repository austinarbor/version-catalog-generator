package dev.aga.gradle.versioncatalogs

import dev.aga.gradle.versioncatalogs.service.FileCatalogParser
import java.io.File
import java.nio.file.Paths
import net.pearx.kasechange.CaseFormat
import net.pearx.kasechange.formatter.format
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
     * occurrences of '.' with a single one.
     */
    var versionNameGenerator = DEFAULT_VERSION_NAME_GENERATOR

    /** The provider for the source BOM to generate the dependency from. */
    lateinit var source: () -> Any

    /**
     * Specify the source BOM to generate the version catalog from using standard dependency
     * notation ```group:artifact:version```
     */
    fun from(notation: String) {
        from { dependency(notation) }
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
            val parser = FileCatalogParser(cfg.tomlConfig.file)
            source = { parser.findLibrary(cfg.tomlConfig.libraryAlias) }
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
         * Default function to generate the prefix of the library's alias. If the `groupId` starts
         * with `com.fasterxml.jackson`, we return `jackson`. If the `groupId` starts with
         * `org.springframework`, we return `spring`. Otherwise, the below logic applies:
         * 1. The `groupId` is split by `.`
         * 2. If the split only returns a list of one item and the value is any one of
         *    [INVALID_PREFIXES], an [IllegalArgumentException] is thrown.
         * 3. Otherwise if the split returns a list of more than one item and the last value is any
         *    one of [INVALID_PREFIXES], the last two values are turned concatenated with a `-`
         * 4. In any other scenario, the last item in the list is returned
         */
        @JvmStatic
        val DEFAULT_ALIAS_PREFIX_GENERATOR: (String, String) -> String = { group, artifact ->
            if (group.startsWith("com.fasterxml.jackson")) {
                "jackson"
            } else if (group.startsWith("org.springframework")) {
                "spring"
            } else {
                val split = group.split(".")
                if (INVALID_PREFIXES.contains(split.last())) {
                    require(split.size >= 2) {
                        "Cannot generate alias for ${group}:${artifact}, please provide custom generator"
                    }
                    "${split[split.size - 2]}-${split.last()}"
                } else {
                    split.last()
                }
            }
        }

        /**
         * Default function to generate the suffix of the library's alias. The logic is as follows:
         * 1. Split the `artifactId` by `-`
         * 2. If the resulting list has a size of one, return the `artifactId`
         * 3. Otherwise, if the first item in the list does not match the `prefix`, return the
         *    `artifactId`
         * 4. Otherwise, if the first item in the list matches the prefix, return every item in the
         *    list after the first item concatenated together with `-`.
         */
        @JvmStatic
        val DEFAULT_ALIAS_SUFFIX_GENERATOR: (String, String, String) -> String =
            { prefix, _, artifact ->
                var art = artifact
                if (prefix == "spring") {
                    art = art.replace("spring-", "")
                }
                val split = art.split("-")
                if (split.size == 1 || prefix != split[0]) {
                    art
                } else {
                    split.subList(1, split.size).joinToString("-")
                }
            }

        /** Alias prefix generator function to always return an empty string. */
        @JvmStatic val NO_ALIAS_PREFIX: (String, String) -> String = { _, _ -> "" }

        /**
         * Default function to generate the alias for the version, based on the version in the
         * version string. This function will first replace all case-insensitive instances of the
         * string *version* with an empty string. Then, all instances of two or more consecutive
         * periods are replaced with a single period. Then, any leading or trailing periods are
         * trimmed. Finally, all periods are replaced with a hyphen.
         */
        @JvmStatic
        val DEFAULT_VERSION_NAME_GENERATOR: (String) -> String = { version ->
            val versionRegEx = "version".toRegex(RegexOption.IGNORE_CASE)
            val dotRegex = """\.{2,}""".toRegex()
            // replace all instances (case insensitively) of the string 'version' with empty string
            version
                .replace(versionRegEx, "")
                .replace(dotRegex, ".") // replace 2 or more consecutive periods with a single one
                .trim('.') // trim leading and trailing periods
                .replace('.', '-') // replace dots with hyphens
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

        /**
         * Convenience lambda to generate library aliases just using the artifact name and
         * converting it to camelCase. This is a shortcut for
         *
         * ```kotlin
         * aliasPrefixGenerator = NO_ALIAS_PREFIX
         * aliasSuffixGenerator = { prefix, group, artifact ->
         *  caseChange(artifact, CaseFormat.LOWER_HYPHEN, CaseFormat.CAMEL)
         * }
         * ```
         *
         * @see caseChange
         * @see libraryAliasGenerator
         * @see aliasPrefixGenerator
         * @see aliasSuffixGenerator
         */
        @JvmStatic
        val CAMEL_CASE_NAME_LIBRARY_ALIAS_GENERATOR = { _: String, artifact: String ->
            caseChange(str = artifact, from = CaseFormat.LOWER_HYPHEN, to = CaseFormat.CAMEL)
        }
    }
}
