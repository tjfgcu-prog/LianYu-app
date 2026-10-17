package com.lianyu.ai.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.lianyu.ai.database.model.BannerTransform
import com.lianyu.ai.feature.profile.R
import androidx.compose.ui.draw.paint
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale


/**
 * 按图片实际尺寸限制偏移范围：图片以"铺满相框"为基准，放大后超出相框的部分才允许拖动。
 */
internal fun BannerTransform.clampedFor(
    imageW: Float, imageH: Float, frameW: Float, frameH: Float
): BannerTransform {
    if (frameW <= 0f || frameH <= 0f) return this
    var baseW = 1f
    var baseH = 1f
    if (imageW > 0f && imageH > 0f) {
        val fill = maxOf(frameW / imageW, frameH / imageH)
        baseW = imageW * fill / frameW
        baseH = imageH * fill / frameH
    }
    val s = scale.coerceIn(BannerTransform.MIN_SCALE, BannerTransform.MAX_SCALE)
    val limitX = ((baseW * s - 1f) / 2f).coerceAtLeast(0f)
    val limitY = ((baseH * s - 1f) / 2f).coerceAtLeast(0f)
    return BannerTransform(s, offsetX.coerceIn(-limitX, limitX), offsetY.coerceIn(-limitY, limitY))
}

/** 封面框和编辑弹窗共用，保证两边显示完全一致 */
internal fun Modifier.bannerTransform(t: BannerTransform, imageSize: Size): Modifier =
    graphicsLayer {
        val c = t.clampedFor(imageSize.width, imageSize.height, size.width, size.height)
        scaleX = c.scale
        scaleY = c.scale
        translationX = c.offsetX * size.width
        translationY = c.offsetY * size.height
    }

/**
 * 封面图：用 Modifier.paint 直接画图，不经过 Image 内部的裁剪，
 * 放大、移动后超出相框的部分才有内容可显示，最后由外层容器裁剪。
 */
@Composable
internal fun BannerImage(
    model: Any?,
    transform: BannerTransform,
    imageSize: Size,
    onImageSize: (Size) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .size(2048)
            .scale(Scale.FIT)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        onSuccess = { onImageSize(it.painter.intrinsicSize) }
    )
    Box(
        modifier = modifier
            .bannerTransform(transform, imageSize)
            .paint(painter, sizeToIntrinsics = false, contentScale = ContentScale.Crop)
    )
}

/** 封面框高度：屏幕高度的 32%，限制在 200~320dp；弹窗和个人中心共用，保证两边一致 */
@Composable
internal fun rememberBannerHeight(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return (screenHeightDp * 0.32f).dp.coerceIn(200.dp, 320.dp)
}
    
@Composable
internal fun BannerEditorDialog(
    imageUri: String,
    initial: BannerTransform,
    onConfirm: (BannerTransform) -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var transform by remember(imageUri) { mutableStateOf(initial) }
    var imageSize by remember(imageUri) { mutableStateOf(Size.Zero) }
    val bannerHeight = rememberBannerHeight()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surface
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .clipToBounds()
                        .background(colorScheme.surfaceVariant)
                        .pointerInput(imageUri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                transform = BannerTransform(
                                    scale = transform.scale * zoom,
                                    offsetX = transform.offsetX + pan.x / w,
                                    offsetY = transform.offsetY + pan.y / h
                                ).clampedFor(imageSize.width, imageSize.height, w, h)
                            }
                        }
                ) {
                    BannerImage(
                        model = imageUri,
                        transform = transform,
                        imageSize = imageSize,
                        onImageSize = { imageSize = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.banner_edit_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.banner_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { transform = BannerTransform() }) {
                            Text(stringResource(R.string.banner_edit_reset))
                        }
                        TextButton(onClick = onReplace) {
                            Text(stringResource(R.string.banner_edit_replace))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.banner_edit_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onConfirm(transform) }) {
                            Text(stringResource(R.string.banner_edit_confirm))
                        }
                    }
                }
            }
        }
    }
}
