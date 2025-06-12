plugins { alias(libs.plugins.spotless) }

spotless {
  kotlin {
    ktfmt().googleStyle()
    endWithNewline()
  }
  kotlinGradle {
    ktfmt().googleStyle()
    endWithNewline()
  }
}
