plugins {
  `kotlin-dsl`
  alias(libs.plugins.spotless)
}

repositories { gradlePluginPortal() }

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
