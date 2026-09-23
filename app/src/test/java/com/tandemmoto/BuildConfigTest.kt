package com.tandemmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Smoke test that keeps the unit-test pipeline exercised until Phase 1 adds
// state-machine tests for link/micmode/voice (see CONTRIBUTING.md "Testing expectations").
class BuildConfigTest {
    @Test
    fun applicationIdMatchesNamespace() {
        assertEquals("com.tandemmoto", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun versionNameIsSet() {
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
    }
}
