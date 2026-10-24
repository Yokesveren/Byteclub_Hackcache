package app.sevasetu.patient.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Boy
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.AccessibleForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.NetworkException
import app.sevasetu.patient.data.model.ConsentDto
import app.sevasetu.patient.data.model.DoctorDto
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.data.model.PatientDto
import app.sevasetu.patient.data.model.RegisterVisitParamDto
import app.sevasetu.patient.data.model.SymptomCheckDto
import app.sevasetu.patient.service.QueueWatcherService
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.PictureCard
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.components.StepHeader
import app.sevasetu.patient.ui.symptom.ChoiceRow
import app.sevasetu.patient.ui.symptom.SymptomCheckScreen
import app.sevasetu.patient.ui.symptom.SymptomResult
import app.sevasetu.patient.ui.theme.ColorError
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private const val STEPS = 6

/**
 * OPD registration (PRD 8.2), one question per screen: who, which hospital, name, age, special needs, then the
 * symptom check and a review. Answers survive rotation; the server picks the doctor, token and band.
 */
@Composable
fun RegistrationScreen(
    onRegistrationComplete: (MyTurnDto) -> Unit,
    onEmergencyTriggered: () -> Unit,
    onEmergencyClick: () -> Unit,
    onBack: () -> Unit
) {
    val app = SevaApplication.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val directory by app.directory.data.collectAsState()

    var step by rememberSaveable { mutableIntStateOf(1) }
    var forSomeoneElse by rememberSaveable { mutableStateOf(false) }
    var hospitalId by rememberSaveable { mutableStateOf(app.prefs.current.hospitalId) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var ageGroup by rememberSaveable { mutableStateOf("") }
    var ageYears by rememberSaveable { mutableStateOf("") }
    var needs by rememberSaveable { mutableStateOf(listOf<String>()) }
    var symptom by remember { mutableStateOf<SymptomResult?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val departments = directory.departments.filter { it.id.startsWith("$hospitalId-") }
    val hospital = directory.hospitals.firstOrNull { it.id == hospitalId }

    // Step 5 is the symptom check, a full screen of its own.
    if (step == 5) {
        SymptomCheckScreen(
            ageGroup = ageGroup,
            ageYears = ageYears.toIntOrNull(),
            pregnant = "pregnant" in needs,
            departments = departments.map { it.code }.toSet(),
            onEmergencyTriggered = { answers, version ->
                // S2: no registration. The outcome is still recorded (anonymous) for the outbreak view.
                scope.launch {
                    app.outboxManager.enqueueSymptomCheck(
                        SymptomCheckDto(
                            id = UUID.randomUUID().toString(),
                            rulesetVersion = version,
                            answers = answers,
                            hospitalId = hospitalId,
                            language = app.prefs.current.language,
                            outcome = "emergency",
                            area = area.ifBlank { null },
                            createdAt = Instant.now().toString()
                        )
                    )
                }
                onEmergencyTriggered()
            },
            onComplete = { symptom = it; step = 6 },
            onEmergencyClick = onEmergencyClick,
            onBack = { step = 4 }
        )
        return
    }

    fun submit() {
        val result = symptom ?: return
        submitting = true
        error = null
        scope.launch {
            val language = app.prefs.current.language
            val param = RegisterVisitParamDto(
                visitId = UUID.randomUUID().toString(),
                hospitalId = hospitalId,
                departmentCode = result.departmentCode,
                channel = "app",
                suggestedBand = result.suggestedBand,
                doctorNote = result.doctorNote,
                patient = PatientDto(
                    // The phone's own patient id for "myself", so past visits join up in the Health Journey.
                    id = if (forSomeoneElse) UUID.randomUUID().toString() else app.prefs.current.patientId,
                    firstName = firstName.trim(),
                    ageGroup = ageGroup,
                    ageYears = ageYears.toIntOrNull(),
                    specialNeeds = needs,
                    language = language,
                    phone = phone.filter { it.isDigit() }.ifBlank { null },
                    area = area.trim().ifBlank { null },
                    consent = ConsentDto(terms = true, movement = app.prefs.current.movementConsent)
                ),
                symptomCheck = SymptomCheckDto(
                    id = UUID.randomUUID().toString(),
                    rulesetVersion = result.rulesetVersion,
                    cluster = result.cluster,
                    answers = result.answers,
                    departmentCode = result.departmentCode,
                    suggestedBand = result.suggestedBand,
                    language = language
                )
            )
            val ticket = try {
                app.backendGateway.registerVisit(param).also { app.visits.save(it, confirmed = true) }
            } catch (e: NetworkException) {
                // Offline (S8): a phone-made token marked "Waiting to be confirmed"; the outbox sends the
                // registration with its original time (honoured up to 45 minutes back) once the phone is online.
                val offline = param.copy(offlineToken = offlineToken(param.departmentCode), createdAt = Instant.now().toString())
                val priority = offline.suggestedBand == "yellow" || ageGroup in setOf("infant", "elderly") || "pregnant" in needs
                app.outboxManager.enqueueRegisterVisit(offline, priority)
                provisional(offline, app.directory).also { app.visits.save(it, confirmed = false) }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "The hospital could not register this visit. Please ask at the help desk."
                submitting = false
                return@launch
            }
            if (!forSomeoneElse) app.prefs.update { it.copy(hospitalId = hospitalId) }
            if ("walking" in needs || "wheelchair" in needs) app.prefs.update { it.copy(avoidStairs = true) }
            if ("hearing" in needs) app.prefs.update { it.copy(flashOnCall = true, alertVibration = true) }
            app.nav.newSession()
            app.nav.setDestination(ticket.doctor.roomWaypointId)
            QueueWatcherService.start(context)
            onRegistrationComplete(ticket)
        }
    }

    SevaScaffold(
        title = if (forSomeoneElse) "Register someone" else "Register for OPD",
        onBack = { if (step > 1) step-- else onBack() },
        onListen = {
            app.speaker.speak(
                when (step) {
                    1 -> "Who is this registration for?"
                    2 -> "Which hospital are you going to?"
                    3 -> "What is the patient's first name?"
                    4 -> "How old is the patient, and do they need any help?"
                    else -> "Please check the details and register."
                },
                when (step) {
                    1 -> "यह पंजीकरण किसके लिए है?"
                    2 -> "आप किस अस्पताल जा रहे हैं?"
                    3 -> "मरीज़ का पहला नाम क्या है?"
                    4 -> "मरीज़ की उम्र क्या है, और क्या उन्हें किसी मदद की ज़रूरत है?"
                    else -> "कृपया विवरण जाँचें और पंजीकरण करें।"
                }
            )
        },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            val stepShown = if (step == 6) 6 else step
            when (step) {
                1 -> {
                    StepHeader(stepShown, STEPS, "Who is this for?")
                    Spacer(Modifier.height(20.dp))
                    PictureCard("Myself", Icons.Default.Person, !forSomeoneElse, { forSomeoneElse = false })
                    PictureCard("Someone else (I am helping them)", Icons.Default.PersonAdd, forSomeoneElse, { forSomeoneElse = true })
                    Spacer(Modifier.height(20.dp))
                    BigButton("Next", { step = 2 }, icon = Icons.AutoMirrored.Filled.ArrowForward)
                }

                2 -> {
                    StepHeader(stepShown, STEPS, "Which hospital?")
                    Spacer(Modifier.height(16.dp))
                    directory.hospitals.forEach { h ->
                        ChoiceRow(
                            icon = Icons.Default.LocalHospital,
                            text = h.name.removeSuffix(" (Demo)"),
                            second = h.address.removeSuffix(" (fictional)"),
                            selected = hospitalId == h.id,
                            onClick = { hospitalId = h.id }
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    BigButton("Next", { step = 3 }, icon = Icons.AutoMirrored.Filled.ArrowForward)
                }

                3 -> {
                    StepHeader(stepShown, STEPS, if (forSomeoneElse) "Their first name" else "Your first name")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "First name is enough. Phone and area are optional.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it.take(40) },
                        label = { Text("First name") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { v -> phone = v.filter { it.isDigit() }.take(10) },
                        label = { Text("Phone (optional, for SMS updates)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it.take(40) },
                        label = { Text("Area or district (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    BigButton("Next", { step = 4 }, enabled = firstName.isNotBlank(), icon = Icons.AutoMirrored.Filled.ArrowForward)
                }

                4 -> {
                    StepHeader(stepShown, STEPS, "Age and needs")
                    Spacer(Modifier.height(16.dp))
                    Row {
                        PictureCard("Baby\nunder 1", Icons.Default.ChildCare, ageGroup == "infant", { ageGroup = "infant" }, Modifier.weight(1f))
                        PictureCard("Child\n1 to 17", Icons.Default.Boy, ageGroup == "child", { ageGroup = "child" }, Modifier.weight(1f))
                    }
                    Row {
                        PictureCard("Adult\n18 to 59", Icons.Default.Person, ageGroup == "adult", { ageGroup = "adult" }, Modifier.weight(1f))
                        PictureCard("Elderly\n60 and over", Icons.Default.Elderly, ageGroup == "elderly", { ageGroup = "elderly" }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ageYears,
                        onValueChange = { v ->
                            ageYears = v.filter { it.isDigit() }.take(3)
                            ageYears.toIntOrNull()?.let { y ->
                                ageGroup = when { y < 1 -> "infant"; y < 18 -> "child"; y < 60 -> "adult"; else -> "elderly" }
                            }
                        },
                        label = { Text("Age in years (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("ANY HELP NEEDED?", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Choose all that apply. Staff will be told; the route will avoid stairs if needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    listOfNotNull(
                        Triple("walking", "Difficulty walking", Icons.AutoMirrored.Filled.Accessible),
                        Triple("wheelchair", "Uses a wheelchair", Icons.Default.AccessibleForward),
                        Triple("sight", "Low vision", Icons.Default.Visibility),
                        Triple("hearing", "Hard of hearing", Icons.Default.Hearing),
                        if (ageGroup == "adult") Triple("pregnant", "Pregnant", Icons.Default.PregnantWoman) else null,
                    ).forEach { (id, label, icon) ->
                        ChoiceRow(icon = icon, text = label, second = null, selected = id in needs, onClick = {
                            needs = if (id in needs) needs - id else needs + id
                        })
                    }
                    Spacer(Modifier.height(20.dp))
                    BigButton(
                        "Next: symptom check",
                        {
                            if (ageGroup != "adult") needs = needs - "pregnant"
                            step = 5
                        },
                        enabled = ageGroup.isNotBlank(),
                        icon = Icons.AutoMirrored.Filled.ArrowForward
                    )
                }

                else -> {
                    val r = symptom
                    StepHeader(stepShown, STEPS, "Check and register")
                    Spacer(Modifier.height(16.dp))
                    val dept = departments.firstOrNull { it.code == r?.departmentCode }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .background(LocalExtraColors.current.field)
                            .padding(20.dp)
                    ) {
                        Review("Patient", firstName.trim() + (ageYears.toIntOrNull()?.let { ", $it years" } ?: ", $ageGroup"))
                        Review("Hospital", hospital?.name?.removeSuffix(" (Demo)") ?: hospitalId)
                        Review("Department", dept?.name ?: (r?.departmentCode ?: "General Medicine"))
                        if (needs.isNotEmpty()) Review("Help needed", needs.joinToString(", "))
                        Review("Note for the doctor", r?.doctorNote ?: "")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                label = if (r?.suggestedBand == "yellow") "Priority" else "Routine",
                                type = r?.suggestedBand ?: "green",
                                provisional = true
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Staff will confirm the band. Feeling worse at any time? Press Emergency.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    BigButton(
                        text = if (submitting) "Registering…" else "Register and get my token",
                        onClick = { submit() },
                        enabled = !submitting && r != null
                    )
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = ColorError)
                    }
                }
            }
        }
    }
}

@Composable
private fun Review(label: String, value: String) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, color = Ink)
    Spacer(Modifier.height(10.dp))
}

/** A device-local token such as `M-x4F` (S8): tells offline tickets apart until the hospital confirms them. */
private fun offlineToken(departmentCode: String): String {
    val chars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789"
    return "$departmentCode-x" + (1..3).map { chars.random() }.joinToString("")
}

/** The ticket shown while offline: real hospital and department, no doctor or place yet (S8). */
private fun provisional(p: RegisterVisitParamDto, directory: app.sevasetu.patient.data.visit.Directory): MyTurnDto {
    val hospital = directory.hospital(p.hospitalId) ?: app.sevasetu.patient.data.visit.Directory.BUILT_IN.hospitals.first()
    val dept = directory.departments(p.hospitalId).firstOrNull { it.code == p.departmentCode }
        ?: app.sevasetu.patient.data.model.DepartmentDto("${p.hospitalId}-${p.departmentCode}", p.departmentCode, p.departmentCode)
    return MyTurnDto(
        visitId = p.visitId,
        token = p.offlineToken ?: "${p.departmentCode}-?",
        offlineToken = p.offlineToken,
        firstName = p.patient.firstName,
        hospital = hospital,
        department = dept,
        doctor = DoctorDto(id = "", name = "Doctor to be assigned", room = "Ask at the counter", floor = 0),
        band = p.suggestedBand,
        bandLabel = if (p.suggestedBand == "yellow") "Priority" else "Routine",
        bandProvisional = true,
        status = "waiting",
        slipQr = "SS1:SLIP:${p.hospitalId}:${p.visitId}",
        lastUpdated = Instant.now().toString()
    )
}
