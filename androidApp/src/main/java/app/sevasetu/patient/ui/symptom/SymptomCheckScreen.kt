package app.sevasetu.patient.ui.symptom

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.SymptomCheckDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StepHeader
import kotlinx.coroutines.launch
import java.util.UUID

// Clinicians must review symptom rules file
data class SymptomResult(
    val departmentCode: String,
    val suggestedBand: String,
    val cluster: String,
    val doctorNote: String,
    val isNotSure: Boolean = false
)

@Composable
fun SymptomCheckScreen(
    onEmergencyTriggered: () -> Unit,
    onComplete: (SymptomResult) -> Unit,
    onEmergencyClick: () -> Unit,
    onBack: () -> Unit
) {
    val app = SevaApplication.instance
    val scope = rememberCoroutineScope()

    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var hasFever by remember { mutableStateOf(false) }
    var hasCough by remember { mutableStateOf(false) }
    var isInjury by remember { mutableStateOf(false) }
    var isPregnant by remember { mutableStateOf(false) }

    val warningQuestions = listOf(
        "Chest pain, tightness or pressure?",
        "Difficulty breathing or severe shortness of breath?",
        "Fainting, collapse or not responding?",
        "Fits, seizures or severe bleeding?"
    )

    SevaScaffold(
        title = "Symptom Check",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (currentQuestionIndex < warningQuestions.size) {
                // Safety Warning Questions (S2 Rule)
                StepHeader(
                    currentStep = currentQuestionIndex + 1,
                    totalSteps = warningQuestions.size + 2,
                    question = warningQuestions[currentQuestionIndex]
                )

                Spacer(modifier = Modifier.height(24.dp))

                InfoCard {
                    Text(
                        text = "Safety Check: If you answer YES, you will be guided immediately to Casualty.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    BigButton(
                        text = "YES / हाँ",
                        onClick = {
                            scope.launch {
                                app.outboxManager.enqueueSymptomCheck(
                                    SymptomCheckDto(
                                        id = UUID.randomUUID().toString(),
                                        outcome = "emergency",
                                        cluster = "emergency"
                                    )
                                )
                                onEmergencyTriggered()
                            }
                        },
                        type = ButtonType.DANGER,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    BigButton(
                        text = "NO / नहीं",
                        onClick = {
                            currentQuestionIndex++
                        },
                        type = ButtonType.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Follow up routine symptom questions
                StepHeader(
                    currentStep = warningQuestions.size + 1,
                    totalSteps = warningQuestions.size + 2,
                    question = "Select any symptoms you have today:"
                )

                Spacer(modifier = Modifier.height(16.dp))

                InfoCard {
                    OutlinedButton(
                        onClick = { hasFever = !hasFever },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (hasFever) "[✓] Fever / बुखार" else "[  ] Fever / बुखार", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { hasCough = !hasCough },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (hasCough) "[✓] Cough / Cold / खाँसी" else "[  ] Cough / Cold / खाँसी", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { isInjury = !isInjury },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isInjury) "[✓] Joint Pain / Bone Injury" else "[  ] Joint Pain / Bone Injury", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { isPregnant = !isPregnant },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isPregnant) "[✓] Pregnancy related" else "[  ] Pregnancy related", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                BigButton(
                    text = "Continue to Department",
                    onClick = {
                        val dept = when {
                            isPregnant -> "A"
                            isInjury -> "O"
                            else -> "M"
                        }
                        val cluster = when {
                            hasFever -> "fever"
                            hasCough -> "respiratory"
                            isInjury -> "injury"
                            else -> "fever"
                        }
                        val note = "Reported symptoms: " + listOfNotNull(
                            if (hasFever) "Fever" else null,
                            if (hasCough) "Cough" else null,
                            if (isInjury) "Bone/Joint pain" else null
                        ).joinToString(", ").ifEmpty { "General consultation" }

                        onComplete(
                            SymptomResult(
                                departmentCode = dept,
                                suggestedBand = if (isPregnant || hasFever) "yellow" else "green",
                                cluster = cluster,
                                doctorNote = note
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                BigButton(
                    text = "Not sure? Ask Help Desk",
                    onClick = {
                        scope.launch {
                            app.outboxManager.enqueueSymptomCheck(
                                SymptomCheckDto(
                                    id = UUID.randomUUID().toString(),
                                    outcome = "help_desk"
                                )
                            )
                            onComplete(
                                SymptomResult(
                                    departmentCode = "M",
                                    suggestedBand = "green",
                                    cluster = "fever",
                                    doctorNote = "Referred via Help Desk",
                                    isNotSure = true
                                )
                            )
                        }
                    },
                    type = ButtonType.SECONDARY
                )
            }
        }
    }
}
