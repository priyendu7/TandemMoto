package com.tandemmoto.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tandemmoto.ui.navigation.AppNavHost
import com.tandemmoto.ui.theme.TandemMotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TandemMotoTheme {
                AppNavHost()
            }
        }
    }
}
