package dev.aga.gradle.versioncatalogs

import java.io.File

class GeneratorConfig {
    /** The name of the library in the TOML catalog file */
    var sourceLibraryNameInCatalog = ""

    /** The catalog file containing the BOM library entry */
    var sourceCatalogFile = File("gradle/libs.versions.toml")

    /** The base url of the maven repository to fetch the pom from */
    var repoBaseUrl = "https://repo1.maven.org/maven2"

    /**
     * Function to generate the name of the library in the generated catalog The default function
     * takes the remainder after the last . in the group, and concatenates the entire artifact name.
     * For example, the module
     *
     * ```org.springframework.boot:spring-boot-starter-web``` would create a library
     * with the name ```boot.spring-boot-starter-web```
     */
    var libraryAliasGenerator = DEFAULT_ALIAS_GENERATOR

    /**
     * Function to generate the version reference to use in the generated catalog. The default
     * function removes the string 'version' from the name (in any case) and then replaces multiple
     * occurrences of '.' with a single one.
     */
    var versionNameGenerator = DEFAULT_VERSION_NAME_GENERATOR

    companion object {
        /** Set of prefixes which are not allowed for libraries in version catalogs */
        val INVALID_PREFIXES = setOf("bundles", "plugins", "versions")

        /**
         * Default function to generate a library's alias based on its groupId and artifactId. This
         * will use the last segment after the last . in the groupId, followed by the entirety of
         * the artifactId. If the last segment of the groupId is a disallowed word, it will use the
         * last two segments.
         */
        val DEFAULT_ALIAS_GENERATOR: (String, String) -> String = { group, artifact ->
            val split = group.split(".")
            if (INVALID_PREFIXES.contains(split.last())) {
                require(split.size >= 2) {
                    "Cannot generate alias for ${group}:${artifact}, please provide custom generator"
                }
                "${split[split.size - 2]}.${split.last()}.${artifact}"
            } else {
                "${split.last()}.${artifact}"
            }
        }

        /**
         * Default function to generate the alias for the version, based on the version in the
         * version string. This function will first replace all case-insensitive instances of the
         * string *version* with an empty string. Then, all instances of two or more consecutive
         * periods are replaced with a single period. Finally, any leading or trailing periods are
         * trimmed.
         */
        val DEFAULT_VERSION_NAME_GENERATOR: (String) -> String = { version ->
            val versionRegEx = "version".toRegex(RegexOption.IGNORE_CASE)
            val dotRegex = """\.{2,}""".toRegex()
            // replace all instances (case insensitively) of the string 'version' with empty string
            version
                .replace(versionRegEx, "")
                .replace(dotRegex, ".") // replace 2 or more consecutive periods with a single one
                .trim('.') // trim leading and trailing periods
        }
    }
}