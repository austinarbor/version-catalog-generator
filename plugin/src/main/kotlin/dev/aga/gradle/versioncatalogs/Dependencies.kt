package dev.aga.gradle.versioncatalogs

import org.apache.maven.model.Dependency

fun Dependency(group: String, name: String, version: String): Dependency {
  return Dependency().apply {
    groupId = group
    artifactId = name
    this.version = version
  }
}

fun String.toDependency(): Dependency {
  val split = split(":")
  require(split.size == 3) {
    "String must contain exactly 3 segments separated by a ':'. Provided string contains ${split.size}."
  }
  return Dependency(split[0], split[1], split[2])
}
