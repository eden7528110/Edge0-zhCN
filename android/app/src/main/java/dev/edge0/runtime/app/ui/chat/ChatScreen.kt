// ui/chat/ChatScreen.kt - the chat page: top bar (drawer button left, context usage
// right), centered message column (max 768dp) sticking to bottom, user bubbles on the
// right, full-width assistant markdown + reasoning, action row on the last message,
// Working... shimmer, destructive error card, rounded composer card.
package dev.edge0.runtime.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.edge0.runtime.app.data.MessageView
import dev.edge0.runtime.app.data.MsgStatus
import dev.edge0.runtime.app.ui.markdown.MarkdownText
import dev.edge0.runtime.app.ui.theme.LocalFontScale

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    modelLabel: String,
    onOpenDrawer: () -> Unit,
    onOpenModels: () -> Unit,
    onNewChat: () -> Unit,
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val genState by vm.genState.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()

    val busy = genState is dev.edge0.runtime.app.runtime.GenState.Streaming ||
        genState is dev.edge0.runtime.app.runtime.GenState.Cancelling || streaming != null
    val loading = genState is dev.edge0.runtime.app.runtime.GenState.Loading

    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    // stick to bottom: streaming deltas and new messages follow; a user scroll up detaches without interrupting generation
    LaunchedEffect(messages.size, streaming?.text, streaming?.thinking) {
        if (atBottom) listState.scrollToItem(Int.MAX_VALUE / 2)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // -- top bar --
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Menu, "对话列表")
            }
            Spacer(Modifier.weight(1f))
            // centered title: perceived model name + On-device Inference subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val short = modelLabel.lowercase()
                val title = when {
                    "35b" in short -> "Edge0 35B"
                    "8b" in short -> "Edge0 8B"
                    else -> "Edge0 Chat"
                }
                Text(title, style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 16.sp * LocalFontScale.current))
                Text("端侧本地推理", style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp * LocalFontScale.current,
                    color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Spacer(Modifier.weight(1f))
            // new-thread button (lifted out of the drawer, beside the model switch)
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp).clickable(onClick = onNewChat)) {}
                Icon(Icons.Outlined.Edit, "新对话",
                     modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(12.dp))
            // top-right model switch (opens the models page)
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp).clickable(onClick = onOpenModels)) {}
                Icon(Icons.Outlined.SwapHoriz, "切换模型（$modelLabel）",
                     modifier = Modifier.size(20.dp),
                     tint = MaterialTheme.colorScheme.onSurface)
                val dot = when {
                    loading -> MaterialTheme.colorScheme.tertiary
                    busy -> MaterialTheme.colorScheme.primary
                    modelLabel.isNotBlank() -> Color(0xFF22C55E)
                    else -> MaterialTheme.colorScheme.outline
                }
                Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(8.dp)
                    .background(dot, CircleShape))
            }
            Spacer(Modifier.width(8.dp))
        }

        // -- message column --
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                       contentPadding = androidx.compose.foundation.layout.PaddingValues(
                           start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                       horizontalAlignment = Alignment.CenterHorizontally) {
                if (messages.isEmpty() && streaming == null) {
                    item {
                        WelcomePage(onPick = { pr -> vm.draft.value = pr; vm.sendMessage() })
                    }
                }
                items(messages.size) { i ->
                    val m = messages[i]
                    MessageRow(
                        m = m,
                        isLastAssistant = m.role == "assistant" &&
                            i == messages.indexOfLast { it.role == "assistant" },
                        showActions = !busy && m.status != MsgStatus.STREAMING,
                        onEdit = { text -> vm.editUserMessage(m.id, text) },
                        onDelete = { vm.deleteMessage(m.id) },
                        onRegenerate = { vm.regenerate() },
                    )
                }
                streaming?.let { s ->
                    item {
                        AssistantBody(
                            body = s.text, thinking = s.thinking,
                            reasoning = ReasoningState(
                                active = s.thinkingActive, startedAt = s.thinkStartedMs,
                                endedAt = s.thinkEndedMs),
                            prefilling = s.prefilling,
                            status = null, isStreaming = true,
                        )
                    }
                }
                item { Spacer(Modifier.height(2.dp)) }
            }
            if (!atBottom) {
                FloatingActionButton(
                    onClick = { scrollScope.launch { listState.animateScrollToItem(Int.MAX_VALUE / 2) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                        .size(36.dp),
                    shape = CircleShape, containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) { Icon(Icons.Outlined.ArrowDownward, "滚动到底部",
                        modifier = Modifier.size(18.dp)) }
            }
        }

        // -- error / guidance banner --
        banner?.let { msg ->
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error,
                         modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(msg, modifier = Modifier.weight(1f), maxLines = 3,
                         overflow = TextOverflow.Ellipsis,
                         style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { vm.banner.value = null }) {
                        Icon(Icons.Outlined.Close, "关闭", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // -- composer (input card + send button; other controls moved to Settings/top bar) --
        Composer(
            value = draft, onValueChange = { vm.draft.value = it },
            busy = busy,
            enabledSend = draft.isNotBlank() && !loading && modelLabel.isNotBlank(),
            onSend = { vm.sendMessage() }, onStop = { vm.cancel() },
        )
    }
}

@Composable
private fun MessageRow(m: MessageView, isLastAssistant: Boolean, showActions: Boolean,
                       onEdit: (String) -> Unit, onDelete: () -> Unit, onRegenerate: () -> Unit) {
    if (m.role == "user") {
        var editing by remember(m.id) { mutableStateOf(false) }
        var buf by remember(m.id) { mutableStateOf(m.content) }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp),
               horizontalAlignment = Alignment.End) {
            if (editing) {
                Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(8.dp)) {
                        BasicTextField(value = buf, onValueChange = { buf = it },
                                       textStyle = MaterialTheme.typography.bodyLarge.copy(
                                           color = MaterialTheme.colorScheme.onSurface),
                                       modifier = Modifier.fillMaxWidth().height(88.dp))
                        Row(horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 modifier = Modifier.padding(8.dp)
                                     .clickableText { editing = false })
                            Text("保存", color = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.padding(8.dp).clickableText {
                                     if (buf.isNotBlank()) onEdit(buf.trim())
                                     editing = false
                                 })
                        }
                    }
                }
            } else {
                Surface(shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.widthIn(max = 560.dp)) {
                    Text(m.content, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                         style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.sp * LocalFontScale.current))
                }
                if (showActions) {
                    ActionRow(text = m.content, canEdit = true, canRegenerate = false,
                              onEdit = { editing = true }, onDelete = onDelete,
                              onRegenerate = {}, endAligned = true)
                }
            }
        }
    } else {
        AssistantBody(
            body = m.content, thinking = m.thinking ?: "",
            // persisted row: startedAt at assistant-row creation, endedAt plus thinking duration
            reasoning = ReasoningState(active = false, startedAt = m.createdAt,
                                       endedAt = m.thinkingMs?.let { m.createdAt + it }),
            prefilling = false,
            status = m.status, isStreaming = false,
            extra = {
                if (showActions) Row {
                    Column(Modifier.weight(1f)) {
                        StatsLine(newTokens = m.newTokens, ttftMs = m.ttftMs,
                                  prefillTokS = m.prefillTokS, decodeTokS = m.decodeTokS,
                                  memBytes = m.memBytes)
                        ActionRow(text = m.content, canEdit = false,
                                  canRegenerate = isLastAssistant,
                                  onEdit = {}, onDelete = onDelete,
                                  onRegenerate = onRegenerate)
                    }
                }
            },
        )
    }
}

private data class ReasoningState(val active: Boolean, val startedAt: Long?, val endedAt: Long?)

@Composable
private fun AssistantBody(body: String, thinking: String, reasoning: ReasoningState,
                          prefilling: Boolean, status: String?, isStreaming: Boolean,
                          extra: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().widthIn(max = 768.dp).padding(top = 10.dp)) {
        if (thinking.isNotEmpty()) {
            ReasoningBlock(thinking = thinking, active = reasoning.active,
                           startedAtMs = reasoning.startedAt, endedAtMs = reasoning.endedAt,
                           nowMs = System.currentTimeMillis())
        }
        if (prefilling && isStreaming) {
            WorkingShimmer("正在处理…")
        }
        if (body.isNotEmpty()) {
            MarkdownText(body, Modifier.fillMaxWidth())
        }
        status?.let { st ->
            val chip = when (st) {
                MsgStatus.CANCELLED -> "已取消"
                MsgStatus.INTERRUPTED -> "已中断"
                MsgStatus.ERROR -> "错误"
                else -> null
            }
            if (chip != null) {
                Text(chip, style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.error),
                     modifier = Modifier.padding(top = 2.dp))
            }
        }
        extra?.invoke()
    }
}

// lightweight text button
private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

// starter prompts (three fixed questions; display truncates with ellipsis, send uses full text)
private val PRESET_PROMPTS = listOf(
    "漂浮在水中的冰块融化后，水位会上升吗？请用两句话解释。",
    "用恰好六个词写一个有反转的科幻故事。",
    "用 3、3、8、8 每个数字恰好一次，只使用基本四则运算和括号，算出 24。",
)

@Composable
private fun WelcomePage(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 110.dp),
           horizontalAlignment = Alignment.CenterHorizontally) {
        Text("edge0", style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold, fontSize = 34.sp * LocalFontScale.current))
        Spacer(Modifier.height(10.dp))
        Text("隐私 AI，完全在你的设备上本地运行",
             style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant))
        Spacer(Modifier.height(30.dp))
        PRESET_PROMPTS.forEach { pr ->
            Surface(Modifier.padding(vertical = 5.dp).widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onPick(pr) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Text(pr, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                     maxLines = 1, overflow = TextOverflow.Ellipsis,
                     style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp * LocalFontScale.current))
            }
        }
    }
}
