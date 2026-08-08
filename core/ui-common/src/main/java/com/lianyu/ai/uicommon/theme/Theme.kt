package com.lianyu.ai.uicommon.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = Color(0xFF2D1F24),
    primaryContainer = PinkPrimary.copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFF2D1F24),
    secondary = Color(0xFF9B6B7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9B6B7A).copy(alpha = 0.1f),
    onSecondaryContainer = Color(0xFF9B6B7A),
    tertiary = PinkDark,
    onTertiary = Color.White,
    background = WeChatLightBackground,
    onBackground = WeChatLightTextPrimary,
    surface = WeChatLightSurface,
    onSurface = WeChatLightTextPrimary,
    surfaceVariant = WeChatLightCard,
    onSurfaceVariant = WeChatLightTextSecondary,
    outline = WeChatLightDivider,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = Color(0xFF2D1F24),
    primaryContainer = PinkPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = Color(0xFFF5E6EB),
    secondary = PinkLight,
    onSecondary = Color(0xFF2D2C24),
    secondaryContainer = PinkLight.copy(alpha = 0.1f),
    onSecondaryContainer = PinkLight,
    tertiary = PinkDark,
    onTertiary = Color.White,
    background = WeChatDarkBackground,
    onBackground = WeChatDarkTextPrimary,
    surface = WeChatDarkSurface,
    onSurface = WeChatDarkTextPrimary,
    surfaceVariant = WeChatDarkCard,
    onSurfaceVariant = WeChatDarkTextSecondary,
    outline = WeChatDarkDivider,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun LianYuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDarkTheme = isSystemInDarkTheme()

    val effectiveDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.let { baseScheme ->
        // 总背景（除聊天页面外所有页面的背景）：如果用户选的是纯色预设，直接覆盖主题的
        // background 角色。这样全应用所有读取 colorScheme.background 的页面（各设置页/
        // 首页/联系人页的 Scaffold、Box 默认背景色）都会自动联动，无需逐个页面改代码。
        // 渐变色 / 自定义图片背景无法用一个纯色表示，这种情况维持主题默认背景，
        // 由 MainScreen 根节点单独绘制（见 rememberAppBackgroundKey 的用法）。
        val appBgKey = com.lianyu.ai.uicommon.component.rememberAppBackgroundKey()
        val isCustomAppBg = com.lianyu.ai.uicommon.component.isCustomBackground(appBgKey)
        val (appBgColor, appBgGradient) = com.lianyu.ai.uicommon.component.getChatBackgroundByKey(context, appBgKey, effectiveDarkTheme)
        if (!isCustomAppBg && appBgGradient == null) {
            baseScheme.copy(background = appBgColor)
        } else {
            baseScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(effectiveDarkTheme) {
            val window = (view.context as Activity).window
            val navScrim = if (effectiveDarkTheme) {
                Color(0xFF000000).copy(alpha = 0.25f)
            } else {
                Color(0xFFFFFFFF).copy(alpha = 0.55f)
            }.toArgb()

            // 状态栏使用不透明纯色，浅色主题用纯白避免显粉；深色主题用背景色保持一致。
            window.statusBarColor = if (effectiveDarkTheme) {
                colorScheme.background.toArgb()
            } else {
                Color(0xFFFFFFFF).toArgb()
            }
            window.navigationBarColor = navScrim
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectiveDarkTheme
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
