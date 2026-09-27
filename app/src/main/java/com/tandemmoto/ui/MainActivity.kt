package com.tandemmoto.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tandemmoto.TandemMotoApp
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

    // On screen: the link service may take the microphone type now (#70).
    override fun onResume() {
        super.onResume()
        (application as TandemMotoApp).onVisible(true)
    }

    override fun onStop() {
        (application as TandemMotoApp).onVisible(false)
        super.onStop()
    }
}
