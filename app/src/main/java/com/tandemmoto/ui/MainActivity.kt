package com.tandemmoto.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tandemmoto.ui.navigation.AppNavHost
import com.tandemmoto.ui.theme.TandemMotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(); the splash closes by itself on the first frame.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TandemMotoTheme {
                AppNavHost()
            }
        }
    }
}
