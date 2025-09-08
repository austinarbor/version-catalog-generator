package dev.aga.gradle.versioncatalogs.mock

import org.gradle.internal.impldep.com.google.common.collect.Interner

class MockInterner<E : Any> : Interner<E> {
  override fun intern(sample: E): E {
    return sample
  }
}
