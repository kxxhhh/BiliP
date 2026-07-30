package com.android.purebilibili.feature.plugin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.plugin.PlayerPlugin
import com.android.purebilibili.core.plugin.SkipAction
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.Sparkles
import io.github.alexzhirkevich.cupertino.icons.outlined.SpeakerWave2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

private const val TAG = "EmojiBombPlugin"

/**
 *  随机表情包炸弹插件
 *
 * 在视频播放时随机弹出搞怪表情包弹窗，配以搞笑音效，制造惊喜和欢乐。
 * 插件会随机在视频播放过程中触发表情包炸弹，持续2-5秒后自动消失。
 * 每个视频只触发一次，避免过度干扰用户观看体验。
 */
class EmojiBombPlugin : PlayerPlugin {

    override val id = "emoji_bomb"
    override val name = "随机表情包炸弹"
    override val description = "随机弹出搞怪表情包，配以搞笑音效，制造欢乐惊喜"
    override val version = "1.0.0"
    override val author = "BiliPai"
    override val icon: ImageVector = CupertinoIcons.Outlined.Sparkles

    // 插件配置
    private var config = EmojiBombConfig()

    // 表情包炸弹状态
    private var isBombActive = false
    private var bombStartTime = 0L
    private var bombDuration = 0L
    private var currentEmoji = ""
    private var currentMessage = ""
    private var hasTriggeredThisVideo = false

    // 随机触发概率控制
    private var lastTriggerCheck = 0L
    private val triggerCheckInterval = 3000L // 每3秒检查一次是否触发

    // 搞怪表情包和消息库
    private val emojiPool = listOf(
        "🤡", "👻", "💀", "👽", "🤖", "🎭", "🎪", "🎨", "🎭", "🃏",
        "😈", "👿", "💩", "🤮", "🤢", "🤧", "🥴", "🤪", "🤨", "🧐",
        "🙃", "😵", "🤯", "🥳", "🤩", "🥺", "😭", "😱", "🤬", "😤",
        "🐒", "🐕", "🐈", "🦄", "🐸", "🐷", "🐮", "🐔", "🦆", "🐧",
        "🍌", "🍕", "🍔", "🌭", "🍟", "🍩", "🍪", "🎂", "🍰", "🧁",
        "🎉", "🎊", "🎈", "🎁", "🎀", "🎄", "🎃", "🎗️", "🎟️", "🎫"
    )

    private val prankMessages = listOf(
        "突然袭击！", "表情包空投！", "欢乐来袭！", "搞笑时间到！",
        "表情包轰炸！", "惊喜快递！", "欢乐炸弹！", "搞怪来电！",
        "表情包雨！", "欢乐彩蛋！", "搞笑特效！", "表情包派对！",
        "突然的快乐！", "表情包空降！", "欢乐入侵！", "搞怪突袭！"
    )

    override suspend fun onEnable() {
        Logger.d(TAG, " 随机表情包炸弹插件已启用")
        loadConfig()
    }

    override suspend fun onDisable() {
        resetBombState()
        Logger.d(TAG, "🔴 随机表情包炸弹插件已禁用")
    }

    override suspend fun onVideoLoad(bvid: String, cid: Long) {
        resetBombState()
        hasTriggeredThisVideo = false
        Logger.d(TAG, " 新视频加载，重置表情包炸弹状态: $bvid")
    }

    override suspend fun onPositionUpdate(positionMs: Long): SkipAction? {
        if (!config.enabled) return SkipAction.None

        val currentTime = System.currentTimeMillis()

        // 如果正在炸弹效果中
        if (isBombActive) {
            val elapsed = currentTime - bombStartTime
            if (elapsed >= bombDuration) {
                // 炸弹效果结束
                isBombActive = false
                Logger.d(TAG, " 表情包炸弹效果结束")
            }
            return SkipAction.None
        }

        // 检查是否应该触发表情包炸弹
        if (!hasTriggeredThisVideo &&
            currentTime - lastTriggerCheck >= triggerCheckInterval &&
            shouldTriggerBomb()) {

            lastTriggerCheck = currentTime
            hasTriggeredThisVideo = true

            // 触发表情包炸弹
            triggerEmojiBomb()
            return SkipAction.None
        }

        lastTriggerCheck = currentTime
        return SkipAction.None
    }

    override fun onUserSeek(positionMs: Long) {
        // 用户主动拖动进度条时，重置炸弹状态
        if (isBombActive) {
            resetBombState()
            Logger.d(TAG, " 用户拖动进度条，重置表情包炸弹状态")
        }
    }

    override fun onVideoEnd() {
        resetBombState()
        hasTriggeredThisVideo = false
    }

    /**
     * 判断是否应该触发表情包炸弹
     */
    private fun shouldTriggerBomb(): Boolean {
        if (config.triggerProbability <= 0) return false

        // 基于配置的概率随机决定是否触发
        return Random.nextFloat() < config.triggerProbability
    }

    /**
     * 触发表情包炸弹
     */
    private fun triggerEmojiBomb() {
        isBombActive = true
        bombStartTime = System.currentTimeMillis()
        bombDuration = Random.nextLong(
            config.minBombDuration,
            config.maxBombDuration + 1
        )
        currentEmoji = emojiPool.random()
        currentMessage = prankMessages.random()

        Logger.d(TAG, " 触发表情包炸弹！表情: $currentEmoji, 消息: $currentMessage, 持续时间: ${bombDuration}ms")

        // 播放搞笑音效（如果启用）
        if (config.playSound) {
            playPrankSound()
        }
    }

    /**
     * 播放搞笑音效
     */
    private fun playPrankSound() {
        try {
            // 这里可以添加实际的音效播放逻辑
            // 由于这是一个示例插件，我们只是记录日志
            Logger.d(TAG, " 播放搞笑音效: 搞怪音效")
        } catch (e: Exception) {
            Logger.e(TAG, " 播放音效失败", e)
        }
    }

    /**
     * 重置炸弹状态
     */
    private fun resetBombState() {
        isBombActive = false
        bombStartTime = 0L
        bombDuration = 0L
        currentEmoji = ""
        currentMessage = ""
    }

    /**
     * 加载配置
     */
    private suspend fun loadConfig() {
        try {
            val context = PluginManager.getContext()
            val jsonStr = PluginStore.getConfigJson(context, id)
            if (jsonStr != null) {
                config = Json.decodeFromString<EmojiBombConfig>(jsonStr)
            }
            Logger.d(TAG, " 加载配置: enabled=${config.enabled}, probability=${config.triggerProbability}")
        } catch (e: Exception) {
            Logger.e(TAG, " 加载配置失败", e)
            config = EmojiBombConfig()
        }
    }

    /**
     * 保存配置
     */
    private suspend fun saveConfig() {
        try {
            val context = PluginManager.getContext()
            val jsonStr = Json.encodeToString(config)
            PluginStore.setConfigJson(context, id, jsonStr)
            Logger.d(TAG, " 配置已保存")
        } catch (e: Exception) {
            Logger.e(TAG, " 保存配置失败", e)
        }
    }

    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var enabled by remember { mutableStateOf(config.enabled) }
        var triggerProbability by remember { mutableStateOf(config.triggerProbability) }
        var minDuration by remember { mutableStateOf(config.minBombDuration / 1000f) }
        var maxDuration by remember { mutableStateOf(config.maxBombDuration / 1000f) }
        var playSound by remember { mutableStateOf(config.playSound) }
        var showPreview by remember { mutableStateOf(false) }

        // 加载配置
        LaunchedEffect(Unit) {
            loadConfig()
            enabled = config.enabled
            triggerProbability = config.triggerProbability
            minDuration = config.minBombDuration / 1000f
            maxDuration = config.maxBombDuration / 1000f
            playSound = config.playSound
        }

        fun updateConfig() {
            config = config.copy(
                enabled = enabled,
                triggerProbability = triggerProbability,
                minBombDuration = (minDuration * 1000).toLong(),
                maxBombDuration = (maxDuration * 1000).toLong(),
                playSound = playSound
            )
            scope.launch {
                saveConfig()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 启用开关
            AppSwitchPreference(
                icon = CupertinoIcons.Outlined.Sparkles,
                title = "启用表情包炸弹",
                subtitle = "随机在视频播放时弹出搞怪表情包",
                checked = enabled,
                onCheckedChange = { newValue ->
                    enabled = newValue
                    updateConfig()
                }
            )

            if (enabled) {
                Divider()

                // 触发概率
                Text(
                    text = "触发概率: ${(triggerProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = triggerProbability,
                    onValueChange = { newValue ->
                        triggerProbability = newValue
                        updateConfig()
                    },
                    valueRange = 0.01f..0.3f,
                    steps = 29
                )

                // 最小持续时间
                Text(
                    text = "最小显示时间: ${minDuration.toInt()}秒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = minDuration,
                    onValueChange = { newValue ->
                        minDuration = newValue.coerceAtMost(maxDuration - 0.5f)
                        updateConfig()
                    },
                    valueRange = 1f..3f,
                    steps = 4
                )

                // 最大持续时间
                Text(
                    text = "最大显示时间: ${maxDuration.toInt()}秒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = maxDuration,
                    onValueChange = { newValue ->
                        maxDuration = newValue.coerceAtLeast(minDuration + 0.5f)
                        updateConfig()
                    },
                    valueRange = 2f..8f,
                    steps = 12
                )

                // 播放音效开关
                AppSwitchPreference(
                    icon = CupertinoIcons.Outlined.SpeakerWave2,
                    title = "播放搞笑音效",
                    subtitle = "表情包出现时播放搞怪音效",
                    checked = playSound,
                    onCheckedChange = { newValue ->
                        playSound = newValue
                        updateConfig()
                    }
                )

                Divider()

                // 预览按钮
                Button(
                    onClick = { showPreview = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(" 预览表情包炸弹效果")
                }

                Divider()

                // 说明文字
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = " 插件说明",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• 每次观看视频时有设定概率触发表情包炸弹\n• 表情包从搞怪表情库中随机选择\n• 显示时间在设定范围内随机\n• 用户拖动进度条会重置炸弹状态\n• 每个视频只触发一次，避免过度干扰",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 表情包炸弹预览对话框
        if (showPreview) {
            EmojiBombPreviewDialog(
                onDismiss = { showPreview = false }
            )
        }
    }

    @Composable
    private fun EmojiBombPreviewDialog(onDismiss: () -> Unit) {
        val previewEmoji = remember { emojiPool.random() }
        val previewMessage = remember { prankMessages.random() }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E).copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 搞怪标题
                    Text(
                        text = previewMessage,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // 超大表情包
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF16213E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = previewEmoji,
                            fontSize = 80.sp
                        )
                    }

                    // 搞怪副标题
                    Text(
                        text = "表情包空投成功！",
                        fontSize = 14.sp,
                        color = Color(0xFF4ECDC4)
                    )

                    // 关闭按钮
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE94560)
                        )
                    ) {
                        Text("收到！继续看视频")
                    }
                }
            }
        }
    }
}

/**
 * 随机表情包炸弹插件配置
 */
@Serializable
data class EmojiBombConfig(
    val enabled: Boolean = true,
    val triggerProbability: Float = 0.12f, // 12%概率触发
    val minBombDuration: Long = 2000L,     // 最小2秒
    val maxBombDuration: Long = 5000L,     // 最大5秒
    val playSound: Boolean = true          // 播放搞笑音效
)