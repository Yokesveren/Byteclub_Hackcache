package app.sevasetu.patient.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.NetworkException
import app.sevasetu.patient.data.model.ChatActionDto
import app.sevasetu.patient.data.model.ChatReplyDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.i18n.tr
import app.sevasetu.patient.ui.i18n.trNow
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.ColorPriorityContainer
import app.sevasetu.patient.ui.theme.ColorPriorityContent
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.Linen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private data class Turn(val question: String, val reply: ChatReplyDto?, val offline: Boolean = false)

private val SUGGESTIONS = listOf("What should I bring?", "Where is the pharmacy?", "How many people are ahead of me?", "OPD timings", "I lost my slip")

/**
 * Help chat (PRD 11.4, S7), online only. It answers from hospital-approved content, live hospital facts and the map;
 * a warning sign stops the chat and opens Emergency; diagnosis and medicine questions are refused.
 */
@Composable
fun ChatScreen(
    onEmergency: () -> Unit,
    onGoToPlace: (tag: String) -> Unit,
    onOpenScreen: (screen: String) -> Unit,
    onOpenHelpCards: () -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<Turn>() }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }
    val session = remember { "app-" + UUID.randomUUID() }
    val list = rememberLazyListState()

    LaunchedEffect(turns.size, turns.lastOrNull()?.reply) {
        if (turns.isNotEmpty()) list.animateScrollToItem(turns.size)
    }

    fun ask(q: String) {
        val question = q.trim()
        if (question.isEmpty() || busy || stopped) return
        text = ""
        busy = true
        turns += Turn(question, null)
        scope.launch {
            val visit = app.visits.active.value
            val hospital = visit?.hospital?.id ?: app.prefs.current.hospitalId
            val turn = try {
                Turn(question, app.backendGateway.chatAsk(hospital, app.prefs.current.language, question, session, visit?.visitId))
            } catch (e: NetworkException) {
                Turn(question, null, offline = true)
            } catch (e: Exception) {
                Turn(question, ChatReplyDto("help_desk", trNow("I can't help with that. Please ask a staff member at the help desk.")))
            }
            turns[turns.lastIndex] = turn
            busy = false
            if (turn.reply?.type == "emergency") {
                // S7: the chat stops and the Emergency screen opens.
                stopped = true
                app.speaker.speak(turn.reply.text)
                delay(1_500)
                onEmergency()
            }
        }
    }

    SevaScaffold(title = tr("Help chat"), onBack = onBack, onEmergencyClick = onEmergencyClick) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Bubble(
                        mine = false,
                        bg = Linen,
                        ink = Ink,
                        text = tr("Ask about places, timings, what to bring or what happens next. I can't advise on illness or medicines. Feeling worse? Press Emergency.")
                    )
                }
                itemsIndexed(turns) { _, t ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Bubble(mine = true, bg = Accent, ink = Color.White, text = t.question)
                        Spacer(Modifier.height(8.dp))
                        when {
                            t.offline -> OfflineCard(onOpenHelpCards)
                            t.reply == null -> Text("…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> ReplyBlock(t.reply, onEmergency, onGoToPlace, onOpenScreen)
                        }
                    }
                }
            }
            if (turns.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUGGESTIONS.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { s ->
                                    val label = tr(s)
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Ink,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Linen)
                                            .clickable { ask(label) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    enabled = !stopped,
                    placeholder = { Text(tr("Type a question…")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { ask(text) }),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { ask(text) }, enabled = !busy && !stopped && text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Ask"), tint = Accent)
                }
            }
        }
    }
}

@Composable
private fun Bubble(mine: Boolean, bg: Color, ink: Color, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = ink,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ReplyBlock(r: ChatReplyDto, onEmergency: () -> Unit, onGoToPlace: (String) -> Unit, onOpenScreen: (String) -> Unit) {
    val app = SevaApplication.instance
    val emergency = r.type == "emergency"
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (emergency) ColorEmergency else if (r.type == "refuse") ColorPriorityContainer else Linen)
                .clickable(onClickLabel = tr("LISTEN")) { app.speaker.speak(r.text) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (emergency) Text(tr("GO TO EMERGENCY NOW."), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text(
                r.text,
                style = MaterialTheme.typography.bodyLarge,
                color = when { emergency -> Color.White; r.type == "refuse" -> ColorPriorityContent; else -> Ink }
            )
        }
        val action = r.action
        if (emergency) {
            Spacer(Modifier.height(8.dp))
            BigButton(text = tr("Go to Emergency"), type = ButtonType.DANGER, onClick = onEmergency)
        } else if (action != null) {
            Spacer(Modifier.height(8.dp))
            ActionButton(action, onGoToPlace, onOpenScreen)
        }
    }
}

@Composable
private fun ActionButton(a: ChatActionDto, onGoToPlace: (String) -> Unit, onOpenScreen: (String) -> Unit) {
    val label = when {
        a.type == "place" -> tr("Show me the way")
        a.screen == "register" -> tr("Register")
        a.screen == "myturn" -> tr("Open my turn")
        a.screen == "haveslip" -> tr("I have my slip")
        a.screen == "navigate" -> tr("Find a place")
        a.screen == "journey" -> tr("Health journey")
        a.screen == "settings" -> tr("Settings")
        a.screen == "sms" -> tr("No data? Use SMS")
        else -> null
    } ?: return
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Accent)
            .clickable { if (a.type == "place") a.tag?.let(onGoToPlace) else a.screen?.let(onOpenScreen) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun OfflineCard(onOpenHelpCards: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ColorPriorityContainer)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiOff, contentDescription = null, tint = ColorPriorityContent)
            Spacer(Modifier.padding(4.dp))
            Text(tr("Chat is available when you are online."), style = MaterialTheme.typography.titleMedium, color = ColorPriorityContent)
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("The help cards work without signal."), style = MaterialTheme.typography.bodyMedium, color = ColorPriorityContent)
        Spacer(Modifier.height(10.dp))
        Text(
            tr("Read the help cards").uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ink).clickable(onClick = onOpenHelpCards)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
