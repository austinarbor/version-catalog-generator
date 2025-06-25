plugins { java }

dependencies {
  implementation(springLibs.spring.springBootStarterWeb)
  implementation(jsonLibs.jackson.jacksonDatabind)
  implementation(jsonLibs.bundles.jacksonModule)
  testImplementation(mockitoLibs.mockito.mockitoCore)
  testImplementation(junitLibs.junitJupiter)
}
