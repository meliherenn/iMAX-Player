package com.imax.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.imax.player.core.common.DeviceUtils
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.designsystem.theme.ImaxTheme
import com.imax.player.ui.navigation.ImaxNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply saved language before drawing Compose content
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
}
