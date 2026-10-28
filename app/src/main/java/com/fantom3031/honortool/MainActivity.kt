package com.fantom3031.honortool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fantom3031.honortool.core.ServiceBridge
import com.fantom3031.honortool.settings.WorldReadable
import com.fantom3031.honortool.ui.AppRoot
import com.fantom3031.honortool.ui.theme.HonorToolTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ServiceBridge.init(applicationContext)
        WorldReadable.fix(this)
        setContent {
            HonorToolTheme {
                AppRoot()
            }
        }
    }
}
