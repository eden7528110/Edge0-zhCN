// ui/settings/SettingsScreen.kt - settings in a single-column card layout:
// appearance (theme / font scale), assistant (system prompt + thinking), sampling
// parameters, about. Writes go through SettingsStore.update.
package dev.edge0.runtime.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.edge0.runtime.app.Edge0App
import dev.edge0.runtime.app.data.AppSettings
import dev.edge0.runtime.app.ui.theme.LocalFontScale
import dev.edge0.runtime.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Edge0App
    val store = app.container.settings
    val s by store.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    fun patch(f: (AppSettings) -> AppSettings) = scope.launch { store.update(f) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }

        Group("外观") {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("主题", modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    val opts = listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色",
                                      ThemeMode.DARK to "深色")
                    opts.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                            onClick = { patch { cur -> cur.copy(themeMode = mode) } },
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
            Text("字体大小 ${"%.2f".format(s.fontScale)}", style = MaterialTheme.typography.bodySmall)
            Slider(value = s.fontScale, onValueChange = { v ->
                scope.launch { store.update { cur -> cur.copy(fontScale = v) } } },
                valueRange = 0.85f..1.3f)
        }

        Group("助手") {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("使用系统提示词", modifier = Modifier.weight(1f))
                Switch(checked = s.useSystemPrompt,
                       onCheckedChange = { c -> patch { cur -> cur.copy(useSystemPrompt = c) } })
            }
            if (s.useSystemPrompt) {
                OutlinedTextField(value = s.systemPrompt,
                    onValueChange = { v -> patch { cur -> cur.copy(systemPrompt = v) } },
                    label = { Text("系统提示词") },
                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("启用思考", modifier = Modifier.weight(1f))
                Switch(checked = s.enableThinking,
                       onCheckedChange = { c -> patch { cur -> cur.copy(enableThinking = c) } })
            }
        }

        Group("采样参数") {
            // defaults mirror the upstream sampling quartet
            ParamSlider("重复惩罚 (1.0=关闭)", s.repetitionPenalty, 1f, 1.5f) { v ->
                patch { cur -> cur.copy(repetitionPenalty = v) } }
            ParamSlider("温度 (0=贪婪)", s.temperature, 0f, 2f) { v ->
                patch { cur -> cur.copy(temperature = v) } }
            ParamSliderInt("top-k (0=关闭)", s.topK, 0, 100) { v ->
                patch { cur -> cur.copy(topK = v) } }
            ParamSlider("top-p (>=1=关闭)", s.topP, 0.1f, 1f) { v ->
                patch { cur -> cur.copy(topP = v) } }
            ParamSliderInt("最大新 token 数 (0=默认 1024)", s.maxNewTokens, 0, 2048) { v ->
                patch { cur -> cur.copy(maxNewTokens = v) } }
            OutlinedTextField(value = s.seed.toString(),
                onValueChange = { v -> v.toLongOrNull()?.let { seed ->
                    patch { cur -> cur.copy(seed = seed) } } },
                label = { Text("随机种子 (seed)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }

        Group("关于") {
            Text("llama.cpp（锁定版本构建） · " +
                 "应用 ${dev.edge0.runtime.app.BuildConfig.VERSION_NAME}" +
                 "+${dev.edge0.runtime.app.BuildConfig.GIT_SHA}",
                 style = MaterialTheme.typography.bodySmall)
            Text("推理引擎与界面打包于同一 APK",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold, fontSize = 11.sp * LocalFontScale.current,
            color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Surface(shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(12.dp), content = content)
        }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, min: Float, max: Float,
                        onValue: (Float) -> Unit) {
    Text("$label = ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
    Slider(value = value.coerceIn(min, max), onValueChange = onValue, valueRange = min..max,
           modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ParamSliderInt(label: String, value: Int, min: Int, max: Int,
                           onValue: (Int) -> Unit) {
    Text("$label = $value", style = MaterialTheme.typography.bodySmall)
    Slider(value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
           onValueChange = { onValue(it.toInt()) }, valueRange = min.toFloat()..max.toFloat(),
           modifier = Modifier.fillMaxWidth())
}
