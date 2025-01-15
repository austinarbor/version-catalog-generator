package dev.aga.gradle.versioncatalogs.model

data class Version(val value: String, val unwrapped: String, val resolvedValue: String = "") {
  val isRef = value != resolvedValue
}
