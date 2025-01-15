package dev.aga.gradle.versioncatalogs.service

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model

interface DependencyResolver {
  fun resolve(source: Dependency): Pair<Model, Model?>
}
