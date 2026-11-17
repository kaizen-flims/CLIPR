package com.clipr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.ui.navigation.CliprAppScaffold
import com.clipr.app.ui.theme.CliprTheme

/**
 * Single-Activity entry point for CLIPR.
 * Enforces locked Obsidian Black (#080808) system bars and mounts the 5-destination navigation shell.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val obsidianBarColor = CliprBrandConfig.OBSIDIAN_BLACK_ARGB.toInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(obsidianBarColor),
            navigationBarStyle = SystemBarStyle.dark(obsidianBarColor)
        )
        super.onCreate(savedInstanceState)

        setContent {
            CliprTheme {
                CliprAppScaffold()
            }
        }
    }
}
