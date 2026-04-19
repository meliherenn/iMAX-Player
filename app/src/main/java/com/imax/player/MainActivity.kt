package com.imax.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.imax.player.core.common.DeviceUtils
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.designsystem.theme.ImaxTheme
import com.imax.player.ui.navigation.ImaxNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    // Shared PiP state: observed by PlayerScreen to hide overlays
    private val _isPipMode = MutableStateFlow(false)
    val isPipMode: StateFlow<Boolean> = _isPipMode.asStateFlow()

    // Set to true by PlayerScreen via a side-effect when player is active
    var isPlayerScreenActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val appLanguage = settingsDataStore.settings.first().appLanguage
            val localeList = if (appLanguage == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(appLanguage)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }

        val deviceUiMode = DeviceUtils.resolveUiMode(this)

        setContent {
            ImaxTheme(isTv = deviceUiMode.isTv) {
                ImaxNavHost(deviceUiMode = deviceUiMode)
            }
        }
    }

    // ─── PiP ───────────────────────────────────────────────────────────────────

    /**
     * Called when the user presses Home while in-app.
     * If the player is active, enter PiP instead of backgrounding.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerScreenActive) {
            enterPipMode()
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isPipMode.value = isInPictureInPictureMode
    }
}
