plugins { java }

dependencies {
  implementation(springLibs.spring.springBootStarterWeb)
  implementation(jsonLibs.jackson.jacksonDatabind)
  implementation(jsonLibs.bundles.jacksonModule)
  implementation(awsLibs.s3)
  implementation(manyBoms.spring.springBootStarterJdbc)
  implementation(manyBoms.sts)
  implementation(manyBoms.jacksonDatabind)
  compileOnly(libs.spring.boot.dependencies)
  implementation(libs.s3)
  testImplementation(mockitoLibs.mockito.core)
  testImplementation(mockitoLibs.mockito.junit.jupiter)
  testImplementation(junitLibs.junitJupiter)
  testImplementation(junitLibs.junitJupiterParams)
}
