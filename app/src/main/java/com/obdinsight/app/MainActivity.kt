package com.obdinsight.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.obdinsight.app.ui.nav.AppRoot
import com.obdinsight.app.ui.theme.ObdInsightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ObdInsightApp
        setContent {
            ObdInsightTheme {
                AppRoot(app)
            }
        }
    }
}
