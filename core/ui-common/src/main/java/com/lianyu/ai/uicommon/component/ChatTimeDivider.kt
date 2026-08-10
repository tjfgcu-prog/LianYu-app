package com.lianyu.ai.uicommon.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatTimeDivider(timestampMillis: Long) {
    val text = remember(timestampMillis) { formatDividerTime(timestampMillis) }
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDividerTime(timestampMillis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val messageDate = java.time.Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val pattern = if (messageDate == today) "HH:mm" else "MM月dd日 HH:mm"
    return java.time.format.DateTimeFormatter.ofPattern(pattern)
        .format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(zone))
}
