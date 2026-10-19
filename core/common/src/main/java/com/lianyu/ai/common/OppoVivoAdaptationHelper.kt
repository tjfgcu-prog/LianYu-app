package com.lianyu.ai.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * OPPO / vivo 深度适配辅助类。
 *
 * ColorOS 与 OriginOS 的后台管理非常激进，应用若需保持消息推送和虚拟恋人主动发消息，
 * 必须引导用户完成：忽略电池优化、允许自启动、允许后台活动、允许后台高耗电、通知权限等。
 * 本类聚合所有相关系统设置页的精确跳转，并优先尝试新版 ROM 路径，再回退旧版路径。
 */
object OppoVivoAdaptationHelper {



    /**
     * 是否已经忽略电池优化（Doze 白名单）。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 打开系统的“忽略电池优化”申请页。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStartActivity(context, intent)
    }

    /**
     * 打开电池优化设置页（用户手动选择“不优化”）。
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStartActivity(context, intent)
    }

    /**
     * 打开应用详情页（通用兜底）。
     */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        safeStartActivity(context, intent)
    }

    /**
     * 打开通知设置页。
     */
    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        safeStartActivity(context, intent)
    }

    /**
     * 打开自启动 / 应用启动管理设置页。
     *
     * ColorOS 12+：设置 → 应用 → 自启动管理
     * OriginOS 3+：i管家 → 应用管理 → 自启动
     */
    fun openAutoStartSettings(context: Context) {
        val candidates = when {
            RomUtils.isOppo -> getOppoAutoStartCandidates()
            RomUtils.isVivo -> getVivoAutoStartCandidates()
            else -> emptyList()
        }

        val component = RomUtils.findAvailableComponent(context, candidates)
        val intent = if (component != null) {
            Intent().setComponent(component)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        safeStartActivity(context, intent)
    }

    /**
     * 打开后台高耗电 / 耗电保护管理页。
     *
     * ColorOS：手机管家 → 权限隐私 → 后台自启 / 耗电保护
     * OriginOS：i管家 → 电池管理 → 后台高耗电
     */
    fun openBackgroundPowerSettings(context: Context) {
        val candidates = when {
            RomUtils.isOppo -> getOppoBackgroundPowerCandidates()
            RomUtils.isVivo -> getVivoBackgroundPowerCandidates()
            else -> emptyList()
        }

        val component = RomUtils.findAvailableComponent(context, candidates)
        val intent = if (component != null) {
            Intent().setComponent(component)
        } else {
            openAppDetailsSettings(context)
            return
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        safeStartActivity(context, intent)
    }

    /**
     * 打开后台弹出界面 / 悬浮窗权限设置页。
     *
     * 在 OPPO/vivo 上，锁屏或后台弹通知时可能需要此权限。
     */
    fun openBackgroundPopupSettings(context: Context) {
        val candidates = when {
            RomUtils.isOppo -> getOppoBackgroundPopupCandidates()
            RomUtils.isVivo -> getVivoBackgroundPopupCandidates()
            else -> emptyList()
        }

        val component = RomUtils.findAvailableComponent(context, candidates)
        val intent = if (component != null) {
            Intent().setComponent(component)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        safeStartActivity(context, intent)
    }







    private fun getOppoAutoStartCandidates(): List<Pair<String, String>> = listOf(
        // ColorOS 12+
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        // ColorOS 11 / 旧版
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        // 更旧版 / Realme
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // ColorOS 13+ 新包名
        "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity"
    )

    private fun getVivoAutoStartCandidates(): List<Pair<String, String>> = listOf(
        // OriginOS / FuntouchOS 10+
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        // iQOO / 部分新版
        "com.iqoo.secure" to "com.iqoo.secure.safecenter.BgStartUpManagerActivity",
        // 旧版 FuntouchOS
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        // 部分 vivo 机型
        "com.vivo.abe" to "com.vivo.abe.ui.ExActivity"
    )

    private fun getOppoBackgroundPowerCandidates(): List<Pair<String, String>> = listOf(
        // ColorOS 12+ 耗电保护
        "com.coloros.safecenter" to "com.coloros.safecenter.powermanager.PowerConsumptionActivity",
        // ColorOS 13+ 新版
        "com.oplus.battery" to "com.oplus.battery.CompeletePowerControlActivity",
        // Realme
        "com.realme.safecenter" to "com.realme.safecenter.power.PowerConsumptionActivity",
        // 旧版
        "com.oppo.safe" to "com.oppo.safe.power.PowerConsumptionActivity"
    )

    private fun getVivoBackgroundPowerCandidates(): List<Pair<String, String>> = listOf(
        // i管家 → 电池管理 → 后台高耗电
        "com.iqoo.secure" to "com.iqoo.secure.safecenter.GuidePageActivity",
        // 旧版
        "com.vivo.abe" to "com.vivo.abe.MainActivity",
        // 权限管理 → 耗电管理
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.PurviewTabActivity"
    )

    private fun getOppoBackgroundPopupCandidates(): List<Pair<String, String>> = listOf(
        // ColorOS 悬浮窗管理
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity",
        // 新版
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.floatwindow.FloatWindowListActivity",
        // 旧版
        "com.oppo.safe" to "com.oppo.safe.permission.PermissionTopActivity"
    )

    private fun getVivoBackgroundPopupCandidates(): List<Pair<String, String>> = listOf(
        // 后台弹出界面
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.KeepBGActivity",
        // iQOO
        "com.iqoo.secure" to "com.iqoo.secure.safecenter.KeepBGActivity",
        // 旧版
        "com.vivo.abe" to "com.vivo.abe.activity.KeepBGActivity"
    )

    private fun safeStartActivity(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // 兜底：跳应用详情
            try {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}
