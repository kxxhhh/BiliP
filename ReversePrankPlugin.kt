package com.android.purebilibili.feature.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.PlayerPlugin
import com.android.purebilibili.core.plugin.SkipAction
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.design_system.ui.components.AppSwitchPreference
import com.android.purebilibili.design_system.ui.components.AppOutlinedTextField
import com.android.purebilibili.design_system.ui.icons.CupertinoIcons
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

private const val TAG = "ReversePrankPlugin"

/**
 *  倒放恶作剧插件
 *
 * 在视频播放时随机触发倒放效果，制造"视频卡住了"的错觉，增加趣味性。
 * 插件会随机在视频播放过程中触发倒放，持续1-3秒后恢复正常播放。
 */
class ReversePrankPlugin : PlayerPlugin {

    override val id = "reverse_prank"
    override val name = "倒放恶作剧"
    override val description = "随机触发倒放效果，制造视频卡住的错觉"
    override val version = "1.0.0"
    override val author = "BiliPai"
    override val icon: ImageVector = CupertinoIcons.Default.PlayCircle

    // 插件配置
    private var config = ReversePrankConfig()

    // 恶作剧状态
    private var isPrankActive = false
    private var prankStartTime = 0L
    private var prankDuration = 0L
    private var originalPosition = 0L
    private var hasTriggeredThisVideo = false

    // 随机触发概率控制
    private var lastTriggerCheck = 0L
    private val triggerCheckInterval = 2000L // 每2秒检查一次是否触发

    override suspend fun onEnable() {
        Logger.d(TAG, " 倒放恶作剧插件已启用")
        loadConfig()
    }

    override suspend fun onDisable() {
        resetPrankState()
        Logger.d(TAG, "🔴 倒放恶作剧插件已禁用")
    }

    override suspend fun onVideoLoad(bvid: String, cid: Long) {
        resetPrankState()
        hasTriggeredThisVideo = false
        Logger.d(TAG, " 新视频加载，重置恶作剧状态: $bvid")
    }

    override suspend fun onPositionUpdate(positionMs: Long): SkipAction? {
        if (!config.enabled) return SkipAction.None

        val currentTime = System.currentTimeMillis()

        // 如果正在恶作剧中
        if (isPrankActive) {
            val elapsed = currentTime - prankStartTime
            if (elapsed >= prankDuration) {
                // 恶作剧结束，恢复正常播放
                isPrankActive = false
                Logger.d(TAG, " 恶作剧结束，恢复正常播放")
                return SkipAction.None
            } else {
                // 继续倒放，返回比当前位置更早的时间
                val reverseProgress = elapsed.toFloat() / prankDuration.toFloat()
                val targetPosition = (originalPosition - (prankDuration * reverseProgress * 0.3)).toLong()
                val finalPosition = maxOf(0L, targetPosition)

                if (finalPosition < positionMs) {
                    return SkipAction.SkipTo(
                        positionMs = finalPosition,
                        reason = if (config.showToast) "视频好像卡住了..." else ""
                    )
                }
            }
            return SkipAction.None
        }

        // 检查是否应该触发恶作剧
        if (!hasTriggeredThisVideo &&
            currentTime - lastTriggerCheck >= triggerCheckInterval &&
            shouldTriggerPrank()) {

            lastTriggerCheck = currentTime
            hasTriggeredThisVideo = true

            // 触发恶作剧
            triggerReversePrank(positionMs)
            return SkipAction.None
        }

        lastTriggerCheck = currentTime
        return SkipAction.None
    }

    override fun onUserSeek(positionMs: Long) {
        // 用户主动拖动进度条时，重置恶作剧状态
        if (isPrankActive) {
            resetPrankState()
            Logger.d(TAG, " 用户拖动进度条，重置恶作剧状态")
        }
    }

    override fun onVideoEnd() {
        resetPrankState()
        hasTriggeredThisVideo = false
    }

    /**
     * 判断是否应该触发恶作剧
     */
    private fun shouldTriggerPrank(): Boolean {
        if (config.triggerProbability <= 0) return false

        // 基于配置的概率随机决定是否触发
        return Random.nextFloat() < config.triggerProbability
    }

    /**
     * 触发倒放恶作剧
     */
    private fun triggerReversePrank(currentPosition: Long) {
        isPrankActive = true
        prankStartTime = System.currentTimeMillis()
        prankDuration = Random.nextLong(
            config.minPrankDuration,
            config.maxPrankDuration + 1
        )
        originalPosition = currentPosition

        Logger.d(TAG, " 触发倒放恶作剧！持续时间: ${prankDuration}ms")
    }

    /**
     * 重置恶作剧状态
     */
    private fun resetPrankState() {
        isPrankActive = false
        prankStartTime = 0L
        prankDuration = 0L
        originalPosition = 0L
    }

    /**
     * 加载配置
     */
    private suspend fun loadConfig() {
        try {
            val context = PluginManager.getContext()
            val jsonStr = PluginStore.getConfigJson(context, id)
            if (jsonStr != null) {
                config = Json.decodeFromString<ReversePrankConfig>(jsonStr)
            }
            Logger.d(TAG, " 加载配置: enabled=${config.enabled}, probability=${config.triggerProbability}")
        } catch (e: Exception) {
            Logger.e(TAG, " 加载配置失败", e)
            config = ReversePrankConfig()
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
        var minDuration by remember { mutableStateOf(config.minPrankDuration / 1000f) }
        var maxDuration by remember { mutableStateOf(config.maxPrankDuration / 1000f) }
        var showToast by remember { mutableStateOf(config.showToast) }

        // 加载配置
        LaunchedEffect(Unit) {
            loadConfig()
            enabled = config.enabled
            triggerProbability = config.triggerProbability
            minDuration = config.minPrankDuration / 1000f
            maxDuration = config.maxPrankDuration / 1000f
            showToast = config.showToast
        }

        fun updateConfig() {
            config = config.copy(
                enabled = enabled,
                triggerProbability = triggerProbability,
                minPrankDuration = (minDuration * 1000).toLong(),
                maxPrankDuration = (maxDuration * 1000).toLong(),
                showToast = showToast
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
                icon = CupertinoIcons.Default.PlayCircle,
                title = "启用恶作剧",
                subtitle = "随机在视频播放时触发倒放效果",
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
                    valueRange = 0.01f..0.5f,
                    steps = 49
                )

                // 最小持续时间
                Text(
                    text = "最小倒放时间: ${minDuration.toInt()}秒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = minDuration,
                    onValueChange = { newValue ->
                        minDuration = newValue.coerceAtMost(maxDuration - 0.5f)
                        updateConfig()
                    },
                    valueRange = 0.5f..5f,
                    steps = 9
                )

                // 最大持续时间
                Text(
                    text = "最大倒放时间: ${maxDuration.toInt()}秒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = maxDuration,
                    onValueChange = { newValue ->
                        maxDuration = newValue.coerceAtLeast(minDuration + 0.5f)
                        updateConfig()
                    },
                    valueRange = 1f..10f,
                    steps = 18
                )

                // 显示提示开关
                AppSwitchPreference(
                    icon = CupertinoIcons.Default.InfoCircle,
                    title = "显示提示文字",
                    subtitle = "倒放时显示\"视频好像卡住了...\"",
                    checked = showToast,
                    onCheckedChange = { newValue ->
                        showToast = newValue
                        updateConfig()
                    }
                )

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
                            text = "🎭 插件说明",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• 每次观看视频时有设定概率触发倒放效果\n• 倒放持续时间在设定范围内随机\n• 用户拖动进度条会重置恶作剧状态\n• 每个视频只触发一次，避免过度干扰",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 倒放恶作剧插件配置
 */
@Serializable
data class ReversePrankConfig(
    val enabled: Boolean = true,
    val triggerProbability: Float = 0.15f, // 15%概率触发
    val minPrankDuration: Long = 1000L,    // 最小1秒
    val maxPrankDuration: Long = 3000L,    // 最大3秒
    val showToast: Boolean = true          // 显示提示文字
)