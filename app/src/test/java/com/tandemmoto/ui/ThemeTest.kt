package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun primaryColor(dark: Boolean): Color {
        var primary = Color.Unspecified
        compose.setContent {
            TandemMotoTheme(darkTheme = dark) { primary = MaterialTheme.colorScheme.primary }
        }
        compose.waitForIdle()
        return primary
    }

    @Test
    @Config(sdk = [30])
    fun fallbackSchemeOnAndroid11() {
        assertNotEquals(Color.Unspecified, primaryColor(dark = false))
    }

    @Test
    @Config(sdk = [34])
    fun dynamicSchemeOnAndroid14() {
        assertNotEquals(Color.Unspecified, primaryColor(dark = true))
    }
}
