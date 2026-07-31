package com.lianyu.ai.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChatBubble

import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.FrameRateManager
import com.lianyu.ai.uicommon.component.ChatBackgroundPickerDialog
import com.lianyu.ai.uicommon.component.getChatBackgroundKey
import com.lianyu.ai.uicommon.component.setChatBackgroundKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 总设置页 — 收纳全部次要设置项，按功能领域分组。
 *
 *   1. 记忆与管理  — 记忆管理、上下文记忆
 *   2. AI 配置    — API 设置
 *   3. 外观       — 主题模式、聊天背景
 *   4. 外观与对话  — 语言、帧率、思考设置
 *   5. 系统与维护  — TTS、Token、更新、权限
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    onNavigateBack: () -> Unit,
    // 记忆与管理
    onMemoryClick: () -> Unit,
    onContextMemoryClick: () -> Unit,
    // AI配置
    onSettingsClick: () -> Unit,
    // 外观
    onThemeClick: () -> Unit,
    onFrameRateClick: () -> Unit,
    onTtsSettingsClick: () -> Unit,
    onTokenUsageClick: () -> Unit,
    
    onDataBackupClick: () -> Unit,
    
    onYandereModeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var isVisible by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { delay(80); isVisible = true }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colorScheme.background).windowInsetsPadding(WindowInsets.statusBars),
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.general_settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // === 记忆与管理 ===
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.Memory, stringResource(R.string.memory_management), stringResource(R.string.memory_management_desc), onMemoryClick),
                    MenuItemData(Icons.Filled.Memory, stringResource(R.string.context_memory), stringResource(R.string.context_memory_desc), onContextMemoryClick)
                ),
                isVisible = isVisible, delayMillis = 20
            )

            Spacer(modifier = Modifier.height(12.dp))

            // === AI 配置 ===
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.Settings, stringResource(R.string.api_settings), stringResource(R.string.api_settings_desc), onSettingsClick)
                ),
                isVisible = isVisible, delayMillis = 60
            )

            Spacer(modifier = Modifier.height(12.dp))

            // === 外观：主题模式 / 聊天背景 ===
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.Brush, stringResource(R.string.theme_mode), stringResource(R.string.theme_mode_desc), onThemeClick),
                    MenuItemData(Icons.Filled.Palette, stringResource(R.string.chat_background), stringResource(R.string.chat_background_desc), onClick = { showBackgroundDialog = true })
                ),
                isVisible = isVisible, delayMillis = 100
            )

            Spacer(modifier = Modifier.height(12.dp))

            // === 第一组：外观与对话 ===
            val currentFrameRate = FrameRateManager.getSavedFrameRate(context)
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.Refresh, stringResource(R.string.framerate), currentFrameRate.label, onFrameRateClick),
                    
                    ThinkingSettingsEntry()
                ),
                isVisible = isVisible, delayMillis = 140
            )

            Spacer(modifier = Modifier.height(12.dp))

            

            // === 第二组：系统与维护 ===
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.RecordVoiceOver, stringResource(R.string.tts_settings), stringResource(R.string.tts_settings_desc), onTtsSettingsClick),
                    MenuItemData(Icons.Filled.Token, stringResource(R.string.token_usage), stringResource(R.string.token_usage_desc), onTokenUsageClick),
                    
                    MenuItemData(Icons.Filled.SaveAlt, stringResource(R.string.data_backup), stringResource(R.string.data_backup_desc), onDataBackupClick)
                    
                ),
                isVisible = isVisible, delayMillis = 220
            )
            Spacer(modifier = Modifier.height(12.dp))

            // === 第三组：病娇模式（独立卡片）===
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.Science, stringResource(R.string.yandere_mode), stringResource(R.string.yandere_mode_desc), onYandereModeClick)
                ),
                isVisible = isVisible, delayMillis = 260
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showBackgroundDialog) {
        ChatBackgroundPickerDialog(
            currentKey = getChatBackgroundKey(context),
            onDismiss = { showBackgroundDialog = false },
            onSelect = { key -> setChatBackgroundKey(context, key); showBackgroundDialog = false }
        )
    }
}

// ============================================================================
// 思考设置入口（带弹窗）
// ============================================================================

@Composable
private fun ThinkingSettingsEntry(): MenuItemData {
    val context = LocalContext.current
    val settingsStore = remember { AppSettingsStore(context) }
    val showReasoning by settingsStore.showReasoningFlow.collectAsState(initial = false)
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        ThinkingSettingsDialog(showDialog, settingsStore)
    }

    return MenuItemData(
        icon = Icons.Filled.Psychology,
        title = stringResource(R.string.thinking_settings),
        subtitle = if (showReasoning) stringResource(R.string.thinking_enabled) else stringResource(R.string.thinking_disabled),
        onClick = { showDialog.value = true }
    )
}

@Composable
private fun ThinkingSettingsDialog(showDialog: MutableState<Boolean>, settingsStore: AppSettingsStore) {
    val scope = rememberCoroutineScope()
    val showReasoning by settingsStore.showReasoningFlow.collectAsState(initial = false)
    val sendReasoning by settingsStore.sendReasoningFlow.collectAsState(initial = false)
    val autoCollapse by settingsStore.autoCollapseReasoningFlow.collectAsState(initial = true)
    val respField by settingsStore.reasoningResponseFieldFlow.collectAsState(initial = "reasoning_content")
    val reqField by settingsStore.reasoningRequestFieldFlow.collectAsState(initial = "reasoning_content")

    var localShow by remember { mutableStateOf(showReasoning) }
    var localSend by remember { mutableStateOf(sendReasoning) }
    var localCollapse by remember { mutableStateOf(autoCollapse) }
    var localResp by remember { mutableStateOf(respField) }
    var localReq by remember { mutableStateOf(reqField) }

    // 修复：DataStore Flow 首帧只能拿到占位默认值，真实值要等一次协程调度后才到达；
    // 上面的 remember 只在弹窗第一次进入组合时执行一次，会把占位值"钉死"，
    // 导致弹窗打开时开关状态和菜单项小字显示的真实状态不一致。
    // 这里用 LaunchedEffect 在真实值到达后同步一次本地状态（仅同步一次，避免覆盖用户正在编辑的修改）。
    var localStateInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(showReasoning, sendReasoning, autoCollapse, respField, reqField) {
        if (!localStateInitialized) {
            localShow = showReasoning
            localSend = sendReasoning
            localCollapse = autoCollapse
            localResp = respField
            localReq = reqField
            localStateInitialized = true
        }
    }

    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text("思考设置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("启用思考过程显示"); Switch(checked = localShow, onCheckedChange = { localShow = it })
                }
                if (localShow) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("思考完成时自动折叠"); Switch(checked = localCollapse, onCheckedChange = { localCollapse = it })
                    }
                    OutlinedTextField(localResp, { localResp = it }, label = { Text("响应字段名") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    OutlinedTextField(localReq, { localReq = it }, label = { Text("请求字段名") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("发送思考内容"); Switch(checked = localSend, onCheckedChange = { localSend = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    settingsStore.setShowReasoning(localShow)
                    settingsStore.setAutoCollapseReasoning(localCollapse)
                    settingsStore.setReasoningResponseField(localResp)
                    settingsStore.setReasoningRequestField(localReq)
                    settingsStore.setSendReasoning(localSend)
                }
                showDialog.value = false
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { showDialog.value = false }) { Text("取消") } }
    )
}
