package dev.aga.gradle.versioncatalogs

import org.apache.commons.text.StringSubstitutor

fun StringSubstitutor.unwrap(s: String): String {
  val numStart = variablePrefixMatcher.isMatch(s, 0)
  if (numStart == 0) {
    return s
  }

  for (i in numStart until s.length) {
    variableSuffixMatcher
      .isMatch(s, i)
      .takeIf { it > 0 }
      ?.also {
        return s.substring(numStart, i)
      }
  }
  return s
}

fun StringSubstitutor.hasReplacement(s: String): Boolean {
  return replace(s) != s
}

fun Map<String, String>.toSubstitutor(): StringSubstitutor {
  return StringSubstitutor(this).apply { isEnableSubstitutionInVariables = true }
}
