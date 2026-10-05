package com.lianyu.ai.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lianyu.ai.common.UserSelfProfile

/** 总设置顶部的"自己设定"卡片：我的信息入口 + 角色头像一排。 */
@Composable
internal fun SelfSettingsCard(
    isVisible: Boolean,
    onSelfProfileClick: () -> Unit,
    onCompanionClick: (Long) -> Unit,
    viewModel: SelfProfileViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val companions by viewModel.companions.collectAsState()
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SolidMenuItem(
                icon = Icons.Filled.Person,
                title = "我的信息",
                subtitle = "角色眼中的你：姓名、性别、年龄、职业等",
                onClick = onSelfProfileClick,
                showDivider = companions.isNotEmpty()
            )
            if (companions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "点头像，单独设定\u201c这个角色眼中的我\u201d",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(companions, key = { it.id }) { c ->
                        Column(
                            modifier = Modifier
                                .width(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCompanionClick(c.id) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE5E5E5)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (c.avatarUrl != null) {
                                    AsyncImage(
                                        model = c.avatarUrl,
                                        contentDescription = c.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Filled.Person, c.name, tint = Color(0xFFAAAAAA), modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                c.name,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/** 我的信息 */
@Composable
fun SelfProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: SelfProfileViewModel = viewModel()
) {
    val initial = remember { viewModel.getSelf() }
    SelfProfileEditor(
        title = "我的信息",
        hint = "这些信息会提供给所有角色（含群聊）。姓名同时是聊天里显示的昵称，留空则不改昵称。",
        initial = initial,
        placeholders = UserSelfProfile(),
        onSave = { viewModel.saveSelf(it) },
        onNavigateBack = onNavigateBack
    )
}

/** 这个角色眼中的我 */
@Composable
fun CompanionSelfProfileScreen(
    companionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: SelfProfileViewModel = viewModel()
) {
    val companions by viewModel.companions.collectAsState()
    val companionName = companions.firstOrNull { it.id == companionId }?.name ?: "角色"
    val initial = remember(companionId) { viewModel.getCompanionSelf(companionId) }
    val base = remember { viewModel.getSelf() }
    SelfProfileEditor(
        title = "${companionName}眼中的我",
        hint = "填了的项优先于\u201c我的信息\u201d，没填的沿用\u201c我的信息\u201d。只影响提示词，不改聊天里显示的昵称。",
        initial = initial,
        placeholders = base,
        onSave = { viewModel.saveCompanionSelf(companionId, it) },
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelfProfileEditor(
    title: String,
    hint: String,
    initial: UserSelfProfile,
    placeholders: UserSelfProfile,
    onSave: (UserSelfProfile) -> Unit,
    onNavigateBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial.name) }
    var gender by remember { mutableStateOf(initial.gender) }
    var age by remember { mutableStateOf(initial.age) }
    var occupation by remember { mutableStateOf(initial.occupation) }
    var callName by remember { mutableStateOf(initial.callName) }
    var note by remember { mutableStateOf(initial.note) }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colorScheme.background).windowInsetsPadding(WindowInsets.statusBars),
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colorScheme.onSurface)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onSave(
                            UserSelfProfile(
                                name = name.trim(),
                                gender = gender.trim(),
                                age = age.trim(),
                                occupation = occupation.trim(),
                                callName = callName.trim(),
                                note = note.trim()
                            )
                        )
                        onNavigateBack()
                    }) { Text("保存") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ProfileField("姓名", name, { name = it }, placeholders.name)
            ProfileField("性别", gender, { gender = it }, placeholders.gender)
            ProfileField("年龄", age, { age = it.filter(Char::isDigit).take(3) }, placeholders.age, keyboardType = KeyboardType.Number)
            ProfileField("职业/身份", occupation, { occupation = it }, placeholders.occupation)
            ProfileField("希望被怎么称呼", callName, { callName = it }, placeholders.callName)
            ProfileField(
                "补充说明", note, { note = it.take(UserSelfProfile.NOTE_MAX) }, placeholders.note,
                singleLine = false,
                supportingText = "${note.length}/${UserSelfProfile.NOTE_MAX}"
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(8.dp)
    )
}
