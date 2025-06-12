plugins { alias(libs.plugins.spotless) }

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/*")
    ktfmt().googleStyle()
    endWithNewline()
  }
  kotlinGradle {
    target("**/*.kts")
    targetExclude("**/build/*")
    ktfmt().googleStyle()
    endWithNewline()
  }
}
