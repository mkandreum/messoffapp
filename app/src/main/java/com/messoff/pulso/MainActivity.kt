package com.messoff.pulso

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.messoff.pulso.ui.PulsoRoot

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.auth.value is AuthState.Loading }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
            navigationBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
        )
        setContent { PulsoRoot(vm) }
        vm.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
    }

    companion object {
        const val EXTRA_EVENT = "com.messoff.extra.EVENT"
        const val EXTRA_NEW = "com.messoff.extra.NEW"
    }
}
