package dev.aga.gradle.versioncatalogs.service

import java.util.function.Supplier
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.model.ObjectFactory

internal val MutableVersionCatalogContainer.objects: ObjectFactory
  get() = accessField("objects")

/*
Below methods inspired by / taken from
 https://github.com/F43nd1r/bomVersionCatalog/blob/master/bom-version-catalog/src/main/kotlin/com/faendir/gradle/extensions.kt
 */
internal val MutableVersionCatalogContainer.dependencyResolutionServices:
  Supplier<DependencyResolutionServices>
  get() = accessField("dependencyResolutionServices")

internal fun <T> MutableVersionCatalogContainer.accessField(name: String): T {
  return this.javaClass.superclass.getDeclaredField(name).apply { isAccessible = true }.get(this)
    as T
}
