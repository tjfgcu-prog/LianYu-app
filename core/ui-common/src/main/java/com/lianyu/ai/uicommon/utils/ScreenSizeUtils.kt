package com.lianyu.ai.uicommon.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

enum class DeviceScreenSize {
    COMPACT,
    MEDIUM,
    EXPANDED
}

@Composable
fun rememberDeviceScreenSize(): DeviceScreenSize {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(configuration, density) {
        val widthDp = configuration.screenWidthDp.toFloat()
        when {
            widthDp < 600f -> DeviceScreenSize.COMPACT
            widthDp < 840f -> DeviceScreenSize.MEDIUM
            else -> DeviceScreenSize.EXPANDED
        }
    }
}
