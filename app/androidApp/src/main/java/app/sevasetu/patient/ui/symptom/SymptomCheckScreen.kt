package app.sevasetu.patient.ui.symptom

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Coronavirus
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StepHeader
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

// The rules live in assets/symptom_rules_<version>.json so clinicians can review and change them without code.
// They are a demo set: a clinician must review them before any real use.

@Serializable
data class WarningSign(@SerialName("id") val id: String, @SerialName("text") val text: Map<String, String>)

@Serializable
data class SymptomRules(
    @SerialName("ruleset_version") val version: String,
    @SerialName("warning_signs") val warningSigns: List<WarningSign>
)

fun loadSymptomRules(context: Context): SymptomRules =
    context.assets.open("symptom_rules_demo-1.json").bufferedReader().use {
        Json { ignoreUnknownKeys = true }.decodeFromString<SymptomRules>(it.readText())
    }

data class SymptomResult(
    val departmentCode: String,
    val suggestedBand: String,  // green or yellow; never red (S2)
    val cluster: String?,       // fever, respiratory, diarrhoea, skin, injury (district outbreak view)
    val doctorNote: String,
    val answers: Map<String, String>,
    val rulesetVersion: String,
    val outcome: String = "department", // department or help_desk
)

/** The main problem today: what it means for the department and the outbreak view. */
private data class Problem(
    val id: String, val label: String, val hindi: String, val icon: ImageVector,
    val department: String, val cluster: String?
)

private val PROBLEMS = listOf(
    Problem("fever", "Fever", "बुखार", Icons.Default.Thermostat, "M", "fever"),
    Problem("cough", "Cough, cold or breathing", "खाँसी, ज़ुकाम", Icons.Default.Coronavirus, "M", "respiratory"),
    Problem("stomach", "Loose motions or vomiting", "दस्त या उल्टी", Icons.Default.WaterDrop, "M", "diarrhoea"),
    Problem("skin", "Skin rash or itching", "त्वचा पर दाने", Icons.Default.Sick, "M", "skin"),
    Problem("injury", "Injury, bone or joint pain", "चोट, हड्डी या जोड़ों का दर्द", Icons.Default.Healing, "O", "injury"),
    Problem("pregnancy", "Pregnancy check-up", "गर्भावस्था जाँच", Icons.Default.PregnantWoman, "A", null),
    Problem("child", "My child is unwell", "बच्चा बीमार है", Icons.Default.ChildCare, "P", null),
    Problem("other", "Something else / follow-up", "कुछ और / दोबारा दिखाना", Icons.Default.MedicalServices, "M", null),
)

private val DURATIONS = listOf("today" to "Since today", "2-3d" to "2 to 3 days", "4-7d" to "4 to 7 days", "week+" to "More than a week")

/**
 * Guided symptom check (PRD 8.3), fully offline. Warning signs come first and any "yes" ends the flow at the
 * Emergency screen without a token (S2). Otherwise it suggests a department and a band (never a diagnosis) and
 * writes a short note for the doctor.
 */
@Composable
fun SymptomCheckScreen(
    ageGroup: String,
    ageYears: Int?,
    pregnant: Boolean,
    departments: Set<String>,
    onEmergencyTriggered: (answers: Map<String, String>, version: String) -> Unit,
    onComplete: (SymptomResult) -> Unit,
    onEmergencyClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = SevaApplication.instance
    val rules = remember { loadSymptomRules(context) }
    var page by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var duration by remember { mutableStateOf<String?>(null) }
    val language = app.prefs.current.language

    fun dept(code: String) = when {
        code in departments -> code
        else -> "M"
    }

    SevaScaffold(
        title = "Symptom check",
        onBack = { if (page > 0) page-- else onBack() },
        onListen = {
            when (page) {
                0 -> app.speaker.speak(
                    "Do you have any of these right now? " + rules.warningSigns.joinToString(". ") { it.text["en"].orEmpty() },
                    "क्या आपको अभी इनमें से कुछ है? " + rules.warningSigns.joinToString("। ") { it.text["hi"] ?: it.text["en"].orEmpty() }
                )
                1 -> app.speaker.speak("What is the main problem today?", "आज मुख्य परेशानी क्या है?")
                else -> app.speaker.speak("Since when?", "कब से?")
            }
        },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (page) {
                0 -> {
                    StepHeader(currentStep = 1, totalSteps = 3, question = "Any of these right now?")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tap one if it is true. You will be sent to Emergency straight away, no token needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    rules.warningSigns.forEach { sign ->
                        WarningRow(
                            text = sign.text[language] ?: sign.text["en"].orEmpty(),
                            second = if (language == "en") sign.text["hi"] else sign.text["en"],
                            onClick = { onEmergencyTriggered(mapOf("warning_sign" to sign.id), rules.version) }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    BigButton(text = "None of these", icon = Icons.Default.Check, onClick = { page = 1 })
                }

                1 -> {
                    StepHeader(currentStep = 2, totalSteps = 3, question = "What is the main problem today?")
                    Spacer(modifier = Modifier.height(16.dp))
                    PROBLEMS
                        .filter { it.id != "pregnancy" || ageGroup == "adult" || pregnant }
                        .filter { it.id != "child" || ageGroup in setOf("infant", "child") }
                        .forEach { p ->
                            ChoiceRow(
                                icon = p.icon,
                                text = p.label,
                                second = p.hindi,
                                selected = problem == p,
                                onClick = { problem = p; page = 2 }
                            )
                        }
                    Spacer(modifier = Modifier.height(16.dp))
                    BigButton(
                        text = "Not sure? Ask the help desk",
                        type = ButtonType.SECONDARY,
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        onClick = {
                            onComplete(
                                SymptomResult(
                                    departmentCode = if (ageGroup in setOf("infant", "child")) dept("P") else "M",
                                    suggestedBand = "green",
                                    cluster = null,
                                    doctorNote = "Not sure of the problem; please review at the help desk.",
                                    answers = mapOf("main_problem" to "not_sure"),
                                    rulesetVersion = rules.version,
                                    outcome = "help_desk"
                                )
                            )
                        }
                    )
                }

                else -> {
                    val p = problem ?: PROBLEMS.last()
                    StepHeader(currentStep = 3, totalSteps = 3, question = "Since when?")
                    Spacer(modifier = Modifier.height(16.dp))
                    DURATIONS.forEach { (id, label) ->
                        ChoiceRow(icon = null, text = label, second = null, selected = duration == id, onClick = {
                            duration = id
                            val child = ageGroup in setOf("infant", "child")
                            val department = when {
                                p.id == "pregnancy" -> dept("A")
                                child -> dept("P")
                                else -> dept(p.department)
                            }
                            // Priority is only suggested here; staff confirm it, and the server may raise it (S3).
                            val priority = p.id == "pregnancy" || pregnant || ageGroup in setOf("infant", "elderly") ||
                                (p.id == "fever" && id in setOf("4-7d", "week+")) ||
                                (p.id == "stomach" && child)
                            val age = ageYears?.let { "age $it" } ?: "age group $ageGroup"
                            onComplete(
                                SymptomResult(
                                    departmentCode = department,
                                    suggestedBand = if (priority) "yellow" else "green",
                                    cluster = p.cluster,
                                    doctorNote = "${p.label}, ${label.lowercase()}, $age. No warning signs reported.",
                                    answers = mapOf("main_problem" to p.id, "duration" to id),
                                    rulesetVersion = rules.version
                                )
                            )
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningRow(text: String, second: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFEDEA))
            .border(1.5.dp, Color(0xFFF3B9B4), RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Yes, I have this") { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = ColorEmergency, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = Ink)
            second?.takeIf { it != text }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("YES", style = MaterialTheme.typography.labelLarge, color = ColorEmergency)
    }
}

@Composable
fun ChoiceRow(icon: ImageVector?, text: String, second: String?, selected: Boolean, onClick: () -> Unit) {
    val x = LocalExtraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) x.frame else x.field)
            .then(if (x.isHighContrast) Modifier.border(2.dp, Color.Black, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClickLabel = text) { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Mint else Accent, modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = if (selected) Color.White else Ink)
            second?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (selected) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Mint)
    }
}
