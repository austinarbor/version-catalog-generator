package dev.aga.gradle.versioncatalogs

import org.apache.commons.text.StringSubstitutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StringSubstitutorsTest {
    @Test
    fun testUnwrap() {
        val sub = StringSubstitutor(mapOf<String, String>())
        val actual1 = sub.unwrap("\${x.y.z}")
        assertThat(actual1).isEqualTo("x.y.z")

        val actual2 = sub.unwrap("abcd")
        assertThat(actual2).isEqualTo("abcd")

        val actual3 = sub.unwrap("\${x.y.z")
        assertThat(actual3).isEqualTo("\${x.y.z")

        val actual4 = sub.unwrap("x.y.z}")
        assertThat(actual4).isEqualTo("x.y.z}")

        val actual5 = sub.unwrap("\${x.y.z}/")
        assertThat(actual5).isEqualTo("x.y.z")
    }
}
