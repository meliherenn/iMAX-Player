package com.imax.player.core.common

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager

object DeviceUtils {
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun isTablet(context: Context): Boolean {
        val config = context.resources.configuration
        val screenWidthDp = config.smallestScreenWidthDp
        return screenWidthDp >= 600
    }

    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun isPortrait(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    fun screenWidthDp(context: Context): Int {
        return context.resources.configuration.screenWidthDp
    }

    fun screenHeightDp(context: Context): Int {
        return context.resources.configuration.screenHeightDp
    }
}
