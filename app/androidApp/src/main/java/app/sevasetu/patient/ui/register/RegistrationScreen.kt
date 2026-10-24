package app.sevasetu.patient.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Boy
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.NetworkException
import app.sevasetu.patient.data.model.ConsentDto
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.data.model.PatientDto
import app.sevasetu.patient.data.model.RegisterVisitParamDto
import app.sevasetu.patient.data.model.SymptomCheckDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.PictureCard
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StepHeader
import app.sevasetu.patient.ui.symptom.SymptomCheckScreen
import app.sevasetu.patient.ui.symptom.SymptomResult
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun RegistrationScreen(
    onRegistrationComplete: (MyTurnDto) -> Unit,
    onEmergencyTriggered: () -> Unit,
    onEmergencyClick: () -> Unit,
    onBack: () -> Unit
) {
    val app = SevaApplication.instance
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) }
    var isAssistedMode by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("Kamla") }
    var ageGroup by remember { mutableStateOf("elderly") }
    var ageYearsText by remember { mutableStateOf("62") }
    var specialNeeds by remember { mutableStateOf(listOf("walking")) }
    var area by remember { mutableStateOf("Anantnag") }
    var phone by remember { mutableStateOf("9876543210") }
    var abhaRef by remember { mutableStateOf("") }
    var symptomResult by remember { mutableStateOf<SymptomResult?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val totalSteps = 6

    // The symptom check is a full screen with its own bar; inside this scrolling column it would get
    // unbounded height (a runtime crash), so step 5 replaces the registration screen instead.
    if (step == 5) {
        SymptomCheckScreen(
            onEmergencyTriggered = onEmergencyTriggered,
            onComplete = { result ->
                symptomResult = result
                step = 6
            },
            onEmergencyClick = onEmergencyClick,
            onBack = { step = 4 }
        )
        return
    }

    SevaScaffold(
        title = "OPD Registration",
        onBack = { if (step > 1) step-- else onBack() },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isAssistedMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "Assisted Mode: You are registering for someone else",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            when (step) {
                1 -> {
                    // Who is this for?
                    StepHeader(currentStep = 1, totalSteps = totalSteps, question = "Who is this registration for?")
                    Spacer(modifier = Modifier.height(24.dp))

                    PictureCard(
                        title = "Myself / खुद के लिए",
                        icon = Icons.Default.Person,
                        isSelected = !isAssistedMode,
                        onSelect = { isAssistedMode = false }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PictureCard(
                        title = "Someone else (Assisted Mode) / किसी अन्य के लिए",
                        icon = Icons.Default.Boy,
                        isSelected = isAssistedMode,
                        onSelect = { isAssistedMode = true }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    BigButton(text = "Next", onClick = { step = 2 })
                }

                2 -> {
                    // Patient Details
                    StepHeader(currentStep = 2, totalSteps = totalSteps, question = "Enter Patient Details")
                    Spacer(modifier = Modifier.height(16.dp))

                    InfoCard {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text("First Name / नाम") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = area,
                            onValueChange = { area = it },
                            label = { Text("Area / District (e.g. Anantnag)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = abhaRef,
                            onValueChange = { abhaRef = it },
                            label = { Text("ABHA Number (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    BigButton(text = "Next", enabled = firstName.isNotBlank(), onClick = { step = 3 })
                }

                3 -> {
                    // Age Group
                    StepHeader(currentStep = 3, totalSteps = totalSteps, question = "Select Age Group")
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        PictureCard(
                            title = "Infant (0-1 yr)",
                            icon = Icons.Default.ChildCare,
                            isSelected = ageGroup == "infant",
                            onSelect = { ageGroup = "infant" },
                            modifier = Modifier.weight(1f)
                        )
                        PictureCard(
                            title = "Child (1-18 yrs)",
                            icon = Icons.Default.Boy,
                            isSelected = ageGroup == "child",
                            onSelect = { ageGroup = "child" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        PictureCard(
                            title = "Adult (18-60 yrs)",
                            icon = Icons.Default.Person,
                            isSelected = ageGroup == "adult",
                            onSelect = { ageGroup = "adult" },
                            modifier = Modifier.weight(1f)
                        )
                        PictureCard(
                            title = "Elderly (60+ yrs)",
                            icon = Icons.Default.Elderly,
                            isSelected = ageGroup == "elderly",
                            onSelect = { ageGroup = "elderly" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    BigButton(text = "Next", onClick = { step = 4 })
                }

                4 -> {
                    // Special Needs
                    StepHeader(currentStep = 4, totalSteps = totalSteps, question = "Any Special Needs?")
                    Spacer(modifier = Modifier.height(16.dp))

                    InfoCard {
                        PictureCard(
                            title = "Walking difficulty / सीढ़ी चढ़ने में समस्या",
                            icon = Icons.AutoMirrored.Filled.Accessible,
                            isSelected = specialNeeds.contains("walking"),
                            onSelect = {
                                specialNeeds = if (specialNeeds.contains("walking")) specialNeeds - "walking" else specialNeeds + "walking"
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        PictureCard(
                            title = "Wheelchair / व्हीलचेयर",
                            icon = Icons.AutoMirrored.Filled.Accessible,
                            isSelected = specialNeeds.contains("wheelchair"),
                            onSelect = {
                                specialNeeds = if (specialNeeds.contains("wheelchair")) specialNeeds - "wheelchair" else specialNeeds + "wheelchair"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    BigButton(text = "Next to Symptom Check", onClick = { step = 5 })
                }

                5 -> Unit // shown full-screen above

                6 -> {
                    // Review & Submit
                    StepHeader(currentStep = 6, totalSteps = totalSteps, question = "Review & Register")
                    Spacer(modifier = Modifier.height(16.dp))

                    InfoCard {
                        Text(text = "Patient Name: $firstName", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(text = "Age Group: ${ageGroup.uppercase()}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Department Code: ${symptomResult?.departmentCode ?: "M"}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Suggested Band: ${symptomResult?.suggestedBand?.uppercase() ?: "GREEN"}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Doctor Note: ${symptomResult?.doctorNote ?: "Routine consultation"}", style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    BigButton(
                        text = if (isSubmitting) "Registering..." else "Submit OPD Registration",
                        enabled = !isSubmitting,
                        onClick = {
                            isSubmitting = true
                            submitError = null
                            scope.launch {
                                val visitId = UUID.randomUUID().toString()
                                val patientId = UUID.randomUUID().toString()
                                val p = PatientDto(
                                    id = patientId,
                                    firstName = firstName,
                                    ageGroup = ageGroup,
                                    ageYears = ageYearsText.toIntOrNull() ?: 45,
                                    specialNeeds = specialNeeds,
                                    language = "hi",
                                    phone = phone,
                                    area = area,
                                    consent = ConsentDto(terms = true, movement = true)
                                )

                                val param = RegisterVisitParamDto(
                                    visitId = visitId,
                                    hospitalId = "H01",
                                    departmentCode = symptomResult?.departmentCode ?: "M",
                                    channel = "app",
                                    suggestedBand = symptomResult?.suggestedBand ?: "green",
                                    doctorNote = symptomResult?.doctorNote ?: "Routine checkup",
                                    patient = p,
                                    symptomCheck = SymptomCheckDto(
                                        id = UUID.randomUUID().toString(),
                                        rulesetVersion = "demo-1",
                                        cluster = symptomResult?.cluster ?: "fever"
                                    )
                                )

                                try {
                                    val result = app.backendGateway.registerVisit(param)
                                    app.visits.save(result, confirmed = true)
                                    onRegistrationComplete(result)
                                } catch (e: NetworkException) {
                                    // Offline (F22, S8): a phone-made token marked "Waiting to be confirmed"; the outbox
                                    // sends the registration (with its offline time, capped server-side) once online.
                                    val isPriority = param.suggestedBand == "yellow" || p.ageGroup in listOf("infant", "elderly")
                                    val offlineParam = param.copy(
                                        offlineToken = offlineToken(param.departmentCode),
                                        createdAt = java.time.Instant.now().toString()
                                    )
                                    app.outboxManager.enqueueRegisterVisit(offlineParam, isPriority)
                                    val provisional = app.fakeBackend.provisionalTicket(offlineParam)
                                    app.visits.save(provisional, confirmed = false)
                                    onRegistrationComplete(provisional)
                                } catch (e: Exception) {
                                    // The hospital refused it (a rule or validation error): say so, keep the answers.
                                    submitError = e.localizedMessage ?: "The hospital could not register this visit. Please ask at the help desk."
                                    isSubmitting = false
                                }
                            }
                        }
                    )
                    submitError?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** A device-local token such as `M-x4F2` (S8): unique enough to tell offline tickets apart until confirmed. */
private fun offlineToken(departmentCode: String): String {
    val chars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789"
    return "$departmentCode-x" + (1..3).map { chars.random() }.joinToString("")
}
