plugins {
  java
  alias(myLibs.plugins.kotlin.jvm)
}

dependencies {
  implementation(springLibs.spring.springBootStarterWeb)
  implementation(jsonLibs.jackson.jacksonDatabind)
  implementation(jsonLibs.bundles.jacksonModule)
  implementation(awsLibs.s3)
  implementation(manyBoms.spring.springBootStarterJdbc)
  implementation(manyBoms.sts)
  implementation(manyBoms.jacksonDatabind)
  compileOnly(libs.spring.boot.dependencies)
  implementation(myLibs.sqs)
  implementation(myLibs.bundles.merged)
  testImplementation(mockitoLibs.mockito.mockitoCore)
  testImplementation(mockitoLibs.mockito.mockitoJunitJupiter)
  testImplementation(junitLibs.junitJupiter)
  testImplementation(junitLibs.junitJupiterParams)
}
