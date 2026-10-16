package com.lianyu.ai.database.model

/**
 * 封面图在固定相框里的显示方式。
 * scale：相对"铺满相框"的放大倍数；
 * offsetX / offsetY：偏移量，单位是相框宽 / 高的比例。
 */
data class BannerTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    fun sanitized(): BannerTransform = BannerTransform(
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
        offsetX = offsetX.coerceIn(-MAX_SCALE, MAX_SCALE),
        offsetY = offsetY.coerceIn(-MAX_SCALE, MAX_SCALE)
    )

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}
