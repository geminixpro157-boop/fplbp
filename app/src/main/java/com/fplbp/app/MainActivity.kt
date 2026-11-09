package com.fplbp.app

import android.Manifest
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fplbp.app.ui.AppRoot
import com.fplbp.app.ui.MainViewModel
import com.fplbp.app.ui.theme.FplTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val vm: MainViewModel by viewModels()

    /** When the connection comes back after a failed refresh, try again right away. */
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(net: Network) {
            if (vm.sync.value.error != null) vm.refresh()
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(network) }
    }

    override fun onStop() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(network) }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so the status/navigation bar icons must be light regardless of the phone's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { FplTheme(dark = true) { AppRoot(vm) } }

        // While the app is on screen: refresh every 2 minutes, but only when a match is being played.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(LIVE_REFRESH_MS)
                    if (vm.hasLiveMatch()) vm.refresh()
                }
            }
        }
    }

    companion object {
        private const val LIVE_REFRESH_MS = 2 * 60_000L
    }
}
