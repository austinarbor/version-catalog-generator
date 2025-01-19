package dev.aga.gradle.versioncatalogs.model

/** Class representing a library that has been generated in the catalog */
data class GeneratedLibrary(
  /** The alias the library is registered under */
  val alias: String,
  /** The group of the dependency */
  val group: String,
  /** The name / artifaftId of the dependency */
  val name: String,
  /**
   * The version of the dependency. If `versionRef` is non-null, this is the value set for the
   * `versionRef`.
   */
  val version: String,
  /**
   * If the library was registered with a `versionRef` this will have a value, otherwise it will be
   * `null`
   */
  val versionRef: String? = null,
)
