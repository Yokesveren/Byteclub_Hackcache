package app.sevasetu.patient.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.db.CachedJourneyEntity
import app.sevasetu.patient.data.model.MyJourneyDto
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.EmptyState
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.LoadingState
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.ColorPriorityEdge
import app.sevasetu.patient.ui.theme.ColorRoutineEdge
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.Mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun day(iso: String): String = runCatching { LocalDate.parse(iso).format(DAY) }.getOrDefault(iso)

/**
 * My Health Journey (PRD 11.1, P1): past visits with the status staff recorded and the next follow-up.
 * Read-only, large type, kept on the phone for offline reading.
 */
@Composable
fun HealthJourneyScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val json = remember { Json { ignoreUnknownKeys = true } }
    val visitId = app.visits.active.value?.visitId ?: if (app.isLive) null else "kamla-demo-visit"
    var journey by remember { mutableStateOf<MyJourneyDto?>(null) }
    var loading by remember { mutableStateOf(visitId != null) }
    var offline by remember { mutableStateOf(false) }

    LaunchedEffect(visitId) {
        val id = visitId ?: return@LaunchedEffect
        val dao = app.database.dao()
        withContext(Dispatchers.IO) { dao.getCachedJourney(id) }?.let { cached ->
            journey = runCatching { json.decodeFromString<MyJourneyDto>(cached.journeyJson) }.getOrNull()
        }
        try {
            val fresh = app.backendGateway.getMyJourney(id)
            journey = fresh
            withContext(Dispatchers.IO) {
                dao.saveCachedJourney(CachedJourneyEntity(id, json.encodeToString(MyJourneyDto.serializer(), fresh)))
            }
        } catch (e: Exception) {
            offline = journey != null
        }
        loading = false
    }

    val j = journey
    SevaScaffold(
        title = "Health journey",
        onBack = onBack,
        isOffline = offline,
        onListen = j?.let { jj ->
            {
                val next = jj.nextFollowUp?.let { "Your next follow-up is on ${day(it.on)}. " } ?: ""
                app.speaker.speak(
                    next + jj.journey.joinToString(" ") { "On ${day(it.day)}, ${it.department} with ${it.doctor}. ${it.notes ?: ""}" }
                )
            }
        },
        onEmergencyClick = onEmergencyClick
    ) {
        when {
            loading && j == null -> LoadingState("Loading your visits…")
            visitId == null -> EmptyState("Your past visits will show here after you register or follow a slip.")
            j == null || (j.journey.isEmpty() && j.nextFollowUp == null) -> EmptyState(
                "No earlier visits recorded yet.\n\nAfter the doctor sees you, the visit and any follow-up date will appear here."
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                j.nextFollowUp?.let { f ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(CardShape).background(Mint).padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Ink)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("NEXT FOLLOW-UP", style = MaterialTheme.typography.labelLarge, color = Ink)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(day(f.on), style = MaterialTheme.typography.headlineLarge, color = Ink)
                        Text(
                            when (f.state) {
                                "done" -> "Done. Thank you for coming back."
                                "defaulted" -> "Missed. Please come in soon, or ask a health worker to call you."
                                else -> "Please come on this day and bring your previous prescription."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Text("EARLIER VISITS", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(10.dp))
                j.journey.sortedByDescending { it.day }.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 24.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    when (item.status) {
                                        "red" -> ColorEmergency
                                        "yellow" -> ColorPriorityEdge
                                        else -> ColorRoutineEdge
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        InfoCard(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(day(item.day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                StatusPill(
                                    label = when (item.status) { "red" -> "Emergency"; "yellow" -> "Priority"; else -> "Routine" },
                                    type = item.status
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("${item.department} · ${item.doctor}", style = MaterialTheme.typography.bodyLarge)
                            Text(item.hospital, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            item.notes?.takeIf { it.isNotBlank() }?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodyLarge)
                            }
                            item.followUpOn?.let {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Follow-up: ${day(it)}", style = MaterialTheme.typography.labelLarge, color = Accent)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
