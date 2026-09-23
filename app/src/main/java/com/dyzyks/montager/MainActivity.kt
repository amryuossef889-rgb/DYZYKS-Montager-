package com.dyzyks.montager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.dyzyks.montager.ui.theme.DyzyksMontagerTheme
import com.dyzyks.montager.ui.theme.ResolveBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DyzyksMontagerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ResolveBackground)
                ) {
                    MontagerApp()
                }
            }
        }
    }
}
