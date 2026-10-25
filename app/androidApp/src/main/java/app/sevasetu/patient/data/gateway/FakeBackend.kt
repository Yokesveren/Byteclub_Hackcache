package app.sevasetu.patient.data.gateway

import app.sevasetu.patient.data.db.SevaDao
import app.sevasetu.patient.data.model.ChatReplyDto
import app.sevasetu.patient.data.model.ClockInfoDto
import app.sevasetu.patient.data.model.ConsentDto
import app.sevasetu.patient.data.model.DepartmentDto
import app.sevasetu.patient.data.model.DoctorDto
import app.sevasetu.patient.data.model.HospitalDto
import app.sevasetu.patient.data.model.JourneyEventDto
import app.sevasetu.patient.data.model.LastReasonDto
import app.sevasetu.patient.data.model.MyJourneyDto
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.data.model.NextFollowUpDto
import app.sevasetu.patient.data.model.PastJourneyItemDto
import app.sevasetu.patient.data.model.PatientDto
import app.sevasetu.patient.data.model.RegisterVisitParamDto
import app.sevasetu.patient.data.model.SmsDto
import app.sevasetu.patient.data.model.SymptomCheckDto
import app.sevasetu.patient.data.model.UrgentFlagDto
import app.sevasetu.patient.data.model.WaitEstimateDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class FakeBackend(
    private val dao: SevaDao
) : BackendGateway {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _visitEventFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    private val scope = CoroutineScope(Dispatchers.IO)

    var simulateNoInternet: Boolean = false
    var clockSpeed: Int = 1 // 1, 30, 120
    var isAutopilotEnabled: Boolean = false
        set(value) {
            field = value
            if (value) startAutopilot() else stopAutopilot()
        }

    private var autopilotJob: Job? = null
    private val baseRealTime = System.currentTimeMillis()
    private val baseSimulatedCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
        set(Calendar.HOUR_OF_DAY, 10)
        set(Calendar.MINUTE, 30)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private val urgentFlagsMap = mutableMapOf<String, UrgentFlagDto>()
    private val journeyEventsLog = mutableListOf<JourneyEventDto>()

    init {
        scope.launch {
            seedDataIfNeeded()
        }
    }

    private fun checkOnline() {
        if (simulateNoInternet) {
            throw NetworkException()
        }
    }

    fun getSimulatedTimeMs(): Long {
        val elapsedReal = System.currentTimeMillis() - baseRealTime
        return baseSimulatedCalendar.timeInMillis + (elapsedReal * clockSpeed)
    }

    private fun formatIsoTime(ms: Long = getSimulatedTimeMs()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(ms))
    }

    private fun formatClockTimeString(ms: Long = getSimulatedTimeMs()): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return sdf.format(Date(ms))
    }

    private fun formatWindowTime(ms: Long): String {
        val roundedMs = ((ms + 250000) / 300000) * 300000
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return sdf.format(Date(roundedMs))
    }

    private suspend fun seedDataIfNeeded() {
        if (dao.getHospitals().isEmpty()) {
            val hospitals = listOf(
                HospitalDto("H01", "Chinar District Hospital", "Hospital Road, Anantnag, J&K", 33.73, 75.14),
                HospitalDto("H02", "Anantnag Sub-District Hospital", "Main Market, Anantnag, J&K", 33.72, 75.15),
                HospitalDto("H03", "Baramulla Community Health Centre", "CHC Road, Baramulla, J&K", 34.20, 74.35)
            )
            dao.insertHospitals(hospitals.map { app.sevasetu.patient.data.db.HospitalEntity(it.id, it.name, it.address, it.lat, it.lng, json.encodeToString(it.services)) })

            val departments = listOf(
                DepartmentDto("H01-GM", "M", "General Medicine", mapOf("hi" to "सामान्य चिकित्सा", "ta" to "பொது மருத்துவம்")),
                DepartmentDto("H01-PED", "P", "Paediatrics", mapOf("hi" to "बाल रोग", "ta" to "குழந்தைகள் மருத்துவம்")),
                DepartmentDto("H01-MAT", "A", "Maternal Health", mapOf("hi" to "मातृ स्वास्थ्य", "ta" to "தாய் சேய் மருத்துவம்")),
                DepartmentDto("H01-ORT", "O", "Orthopaedics", mapOf("hi" to "अस्थि रोग", "ta" to "எலும்பியல்"))
            )
            dao.insertDepartments(departments.map { app.sevasetu.patient.data.db.DepartmentEntity(it.id, "H01", it.code, it.name, json.encodeToString(it.nameI18n)) })

            val doctors = listOf(
                DoctorDto("H01-D01", "Dr. Aamir Lone", "Room 3", 1, "SS:H01:F1:W103", true, "in"),
                DoctorDto("H01-D02", "Dr. Sunita Sharma", "Room 7", 0, "SS:H01:F0:W007", true, "in"),
                DoctorDto("H01-D03", "Dr. Farooq Bhat", "Room 10", 0, "SS:H01:F0:W010", true, "in"),
                DoctorDto("H01-D04", "Dr. Rajesh Kaul", "Room 14", 1, "SS:H01:F1:W114", true, "in")
            )
            dao.insertDoctors(doctors.map { app.sevasetu.patient.data.db.DoctorEntity(it.id, "H01", when(it.id) { "H01-D01" -> "M"; "H01-D02" -> "P"; "H01-D03" -> "A"; else -> "O" }, it.name, it.room, it.floor, it.roomWaypointId, it.stairFree, it.status, it.statusNote) })

            // Seed waiting patients
            seedInitialPatients()
        }
    }

    private suspend fun seedInitialPatients() {
        val seededNames = listOf("Mohammad", "Tariq", "Fatima", "Sajad", "Bilal", "Ghulam", "Zahida", "Parveena")
        val doctors = dao.getDoctors()
        var tokenCounter = 10
        doctors.forEach { doc ->
            for (i in 1..4) {
                tokenCounter++
                val code = when(doc.departmentCode) { "M" -> "M"; "P" -> "P"; "A" -> "A"; else -> "O" }
                val token = "$code-${String.format(Locale.US, "%03d", tokenCounter)}"
                val name = seededNames[(tokenCounter + i) % seededNames.size]
                val visitId = "seeded-visit-$token"
                val p = PatientDto("p-$token", name, "adult", 45, emptyList(), "hi", "9876543210", "Anantnag")
                val param = RegisterVisitParamDto(visitId, "H01", doc.departmentCode, "app", "green", null, formatIsoTime(), "Routine checkup", null, p)
                registerVisitInternal(param, tokenOverride = token)
            }
        }
    }

    override suspend fun registerVisit(p: RegisterVisitParamDto): MyTurnDto = withContext(Dispatchers.IO) {
        checkOnline()
        return@withContext registerVisitInternal(p)
    }

    private suspend fun registerVisitInternal(p: RegisterVisitParamDto, tokenOverride: String? = null): MyTurnDto {
        val docList = dao.getDoctors().filter { it.departmentCode == p.departmentCode }
        val doc = docList.firstOrNull() ?: dao.getDoctors().first()

        val isPriorityByAgeOrSpecial = p.patient.ageGroup in listOf("infant", "elderly") ||
                p.patient.specialNeeds.contains("pregnant") ||
                p.suggestedBand == "yellow"

        val finalBand = if (isPriorityByAgeOrSpecial) "yellow" else "green"
        val bandLabel = if (finalBand == "yellow") "Priority" else "Routine"
        val bandProvisional = isPriorityByAgeOrSpecial && p.suggestedBand != "yellow"

        val token = tokenOverride ?: run {
            val count = dao.getAllFakeBackendVisits().size + 10
            "${p.departmentCode}-${String.format(Locale.US, "%03d", count)}"
        }

        val myTurn = buildMyTurnDto(
            visitId = p.visitId,
            token = token,
            offlineToken = p.offlineToken,
            firstName = p.patient.firstName,
            hospitalId = p.hospitalId,
            departmentCode = p.departmentCode,
            doctorId = doc.id,
            band = finalBand,
            bandLabel = bandLabel,
            bandProvisional = bandProvisional,
            status = "waiting",
            reasonCode = if (p.offlineToken != null) "token_confirmed" else null,
            reasonMessage = if (p.offlineToken != null) "Your offline token was confirmed by the hospital system." else null
        )

        val entity = app.sevasetu.patient.data.db.FakeBackendVisitEntity(
            visitId = p.visitId,
            hospitalId = p.hospitalId,
            departmentCode = p.departmentCode,
            doctorId = doc.id,
            token = token,
            offlineToken = p.offlineToken,
            firstName = p.patient.firstName,
            band = finalBand,
            bandLabel = bandLabel,
            bandProvisional = bandProvisional,
            status = "waiting",
            createdAtEpoch = System.currentTimeMillis(),
            lastReasonCode = myTurn.lastReason?.reasonCode,
            lastReasonMessage = myTurn.lastReason?.message,
            lastReasonAt = System.currentTimeMillis(),
            rawJson = json.encodeToString(myTurn)
        )
        dao.saveFakeBackendVisit(entity)
        _visitEventFlow.emit(p.visitId)

        return getMyTurnInternal(p.visitId)
    }

    /**
     * A provisional ticket built on the phone while the hospital is unreachable (S8). The token shown is the
     * phone's own [RegisterVisitParamDto.offlineToken]; the official one arrives when the outbox syncs.
     */
    suspend fun provisionalTicket(p: RegisterVisitParamDto): MyTurnDto = withContext(Dispatchers.IO) {
        val local = registerVisitInternal(p)
        local.copy(token = p.offlineToken ?: local.token, estimate = null, nowServing = null)
    }

    override suspend fun getMyTurn(visitId: String): MyTurnDto = withContext(Dispatchers.IO) {
        checkOnline()
        return@withContext getMyTurnInternal(visitId)
    }

    private suspend fun getMyTurnInternal(visitId: String): MyTurnDto {
        val entity = dao.getFakeBackendVisit(visitId)
            ?: throw IllegalArgumentException("Visit $visitId not found")

        val doctorEntity = dao.getDoctors().firstOrNull { it.id == entity.doctorId } ?: dao.getDoctors().first()
        val departmentEntity = dao.getDepartments().firstOrNull { it.code == entity.departmentCode } ?: dao.getDepartments().first()
        val hospitalEntity = dao.getHospitals().firstOrNull { it.id == entity.hospitalId } ?: dao.getHospitals().first()

        val waitingList = dao.getWaitingVisitsForDoctor(doctorEntity.id)
        val index = waitingList.indexOfFirst { it.visitId == visitId }
        val peopleAhead = if (index >= 0) index else 0

        val calledVisit = dao.getCalledVisitForDoctor(doctorEntity.id)
        val nowServingToken = calledVisit?.token ?: "None"

        val isPaused = doctorEntity.status in listOf("break", "away", "off")
        val consultMin = 6.0
        val estimateMin = (peopleAhead * consultMin).toInt()
        val nowMs = getSimulatedTimeMs()
        val windowStartMs = nowMs + (estimateMin * 60 * 1000L * 0.75).toLong()
        val windowEndMs = nowMs + (estimateMin * 60 * 1000L * 1.25).toLong()

        val estimate = if (entity.status == "waiting") {
            WaitEstimateDto(
                peopleAhead = peopleAhead,
                estimateMin = estimateMin,
                windowStart = if (isPaused) null else formatWindowTime(windowStartMs),
                windowEnd = if (isPaused) null else formatWindowTime(windowEndMs),
                paused = isPaused,
                consultMin = consultMin
            )
        } else null

        val lastReason = if (entity.lastReasonCode != null) {
            LastReasonDto(
                type = "status_change",
                reasonCode = entity.lastReasonCode,
                message = entity.lastReasonMessage ?: entity.lastReasonCode,
                at = formatIsoTime(entity.lastReasonAt ?: System.currentTimeMillis())
            )
        } else null

        val flag = urgentFlagsMap[visitId]

        return MyTurnDto(
            visitId = entity.visitId,
            token = entity.token,
            offlineToken = entity.offlineToken,
            firstName = entity.firstName,
            hospital = HospitalDto(hospitalEntity.id, hospitalEntity.name, hospitalEntity.address, hospitalEntity.lat, hospitalEntity.lng),
            department = DepartmentDto(departmentEntity.id, departmentEntity.code, departmentEntity.name, json.decodeFromString(departmentEntity.nameI18nJson)),
            doctor = DoctorDto(doctorEntity.id, doctorEntity.name, doctorEntity.room, doctorEntity.floor, doctorEntity.roomWaypointId, doctorEntity.stairFree, doctorEntity.status, doctorEntity.statusNote),
            band = entity.band,
            bandLabel = entity.bandLabel,
            bandProvisional = entity.bandProvisional,
            status = entity.status,
            nowServing = nowServingToken,
            estimate = estimate,
            lastReason = lastReason,
            urgentFlag = flag,
            waitAnywhereAllowed = entity.band == "green" && flag == null,
            slipQr = "SS1:SLIP:${hospitalEntity.id}:${entity.visitId}",
            lastUpdated = formatClockTimeString()
        )
    }

    private fun buildMyTurnDto(
        visitId: String,
        token: String,
        offlineToken: String?,
        firstName: String,
        hospitalId: String,
        departmentCode: String,
        doctorId: String,
        band: String,
        bandLabel: String,
        bandProvisional: Boolean,
        status: String,
        reasonCode: String? = null,
        reasonMessage: String? = null
    ): MyTurnDto {
        return MyTurnDto(
            visitId = visitId,
            token = token,
            offlineToken = offlineToken,
            firstName = firstName,
            hospital = HospitalDto(hospitalId, "Chinar District Hospital", "Hospital Road, Anantnag, J&K", 33.73, 75.14),
            department = DepartmentDto("H01-$departmentCode", departmentCode, "Department $departmentCode"),
            doctor = DoctorDto(doctorId, "Dr. Aamir Lone", "Room 3", 1, "SS:H01:F1:W103"),
            band = band,
            bandLabel = bandLabel,
            bandProvisional = bandProvisional,
            status = status,
            nowServing = "M-001",
            estimate = WaitEstimateDto(3, 18, formatWindowTime(getSimulatedTimeMs() + 15*60*1000), formatWindowTime(getSimulatedTimeMs() + 25*60*1000)),
            lastReason = reasonCode?.let { LastReasonDto("update", it, reasonMessage ?: it, formatIsoTime()) },
            slipQr = "SS1:SLIP:$hospitalId:$visitId",
            lastUpdated = formatClockTimeString()
        )
    }

    override suspend fun linkSlip(hospitalId: String, token: String): MyTurnDto? = withContext(Dispatchers.IO) {
        checkOnline()
        val visit = dao.getFakeBackendVisitByToken(hospitalId, token) ?: return@withContext null
        return@withContext getMyTurnInternal(visit.visitId)
    }

    override suspend fun raiseUrgentFlag(p: UrgentFlagDto): UrgentFlagDto = withContext(Dispatchers.IO) {
        checkOnline()
        val receivedAt = formatIsoTime()
        val updated = p.copy(receivedAt = receivedAt)
        p.visitId?.let { urgentFlagsMap[it] = updated }
        _visitEventFlow.emit(p.visitId ?: p.id)
        return@withContext updated
    }

    override suspend fun getUrgentFlag(id: String): UrgentFlagDto? = withContext(Dispatchers.IO) {
        checkOnline()
        return@withContext urgentFlagsMap.values.firstOrNull { it.id == id || it.visitId == id }
    }

    override suspend fun recordSymptomCheck(p: SymptomCheckDto) = withContext(Dispatchers.IO) {
        checkOnline()
        // Recorded locally in fake backend log
    }

    override suspend fun ingestJourneyEvents(list: List<JourneyEventDto>): Int = withContext(Dispatchers.IO) {
        checkOnline()
        journeyEventsLog.addAll(list)
        return@withContext list.size
    }

    override suspend fun getMyJourney(visitId: String): MyJourneyDto = withContext(Dispatchers.IO) {
        checkOnline()
        return@withContext MyJourneyDto(
            firstName = "Kamla",
            journey = listOf(
                PastJourneyItemDto("2026-08-13", "Chinar DH", "General Medicine", "Dr. Aamir Lone", "green", "2026-09-03", true, "Routine follow-up completed. Vitals stable."),
                PastJourneyItemDto("2026-09-03", "Chinar DH", "General Medicine", "Dr. Aamir Lone", "yellow", "2026-10-15", true, "Mild hypertension noted. Meds adjusted."),
                PastJourneyItemDto("2026-10-15", "Chinar DH", "General Medicine", "Dr. Aamir Lone", "green", "2026-11-20", true, "Blood pressure normal.")
            ),
            nextFollowUp = NextFollowUpDto(
                on = "2026-11-20",
                consultOk = true,
                state = "due"
            )
        )
    }

    override suspend fun getClock(): ClockInfoDto = withContext(Dispatchers.IO) {
        checkOnline()
        val ms = getSimulatedTimeMs()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date(ms))
        return@withContext ClockInfoDto(
            now = formatIsoTime(ms),
            speed = clockSpeed,
            today = todayStr
        )
    }

    override suspend fun listHospitals(): List<HospitalDto> = withContext(Dispatchers.IO) {
        checkOnline()
        return@withContext dao.getHospitals().map { HospitalDto(it.id, it.name, it.address, it.lat, it.lng) }
    }

    override suspend fun listDepartments(): List<DepartmentDto> = withContext(Dispatchers.IO) {
        checkOnline()
        return@withContext dao.getDepartments().map { DepartmentDto(it.id, it.code, it.name, json.decodeFromString(it.nameI18nJson)) }
    }

    override suspend fun listDoctors(): List<DoctorDto> = withContext(Dispatchers.IO) {
        checkOnline()
        return@withContext dao.getDoctors().map { DoctorDto(it.id, it.name, it.room, it.floor, it.roomWaypointId, it.stairFree, it.status, it.statusNote) }
    }

    override fun observeVisit(visitId: String, hospitalId: String?): Flow<Unit> {
        return _visitEventFlow.asSharedFlow()
            .filter { it == visitId || it == "ALL" }
            .map { Unit }
    }

    // The help chat is online only (PRD 7.3); the simulator has no hospital to ask, so it answers as if offline.
    override suspend fun chatAsk(hospitalId: String, lang: String, text: String, sessionId: String, visitId: String?): ChatReplyDto =
        throw NetworkException("Chat is available when you are online")

    override suspend fun getMySms(visitId: String): List<SmsDto> = emptyList()

    // Demo panel staff actions
    suspend fun callNext(doctorId: String) = withContext(Dispatchers.IO) {
        val currentCalled = dao.getCalledVisitForDoctor(doctorId)
        if (currentCalled != null) {
            val finished = currentCalled.copy(status = "done", lastReasonCode = "consultation_completed", lastReasonMessage = "Consultation completed.")
            dao.saveFakeBackendVisit(finished)
            _visitEventFlow.emit(finished.visitId)
        }

        val waitingList = dao.getWaitingVisitsForDoctor(doctorId)
        if (waitingList.isNotEmpty()) {
            val next = waitingList.first().copy(status = "called", lastReasonCode = "patient_called", lastReasonMessage = "Please proceed to doctor's room.")
            dao.saveFakeBackendVisit(next)
            _visitEventFlow.emit(next.visitId)
        }
    }

    suspend fun confirmPriority(visitId: String) = withContext(Dispatchers.IO) {
        val v = dao.getFakeBackendVisit(visitId) ?: return@withContext
        val updated = v.copy(band = "yellow", bandLabel = "Priority", bandProvisional = false, lastReasonCode = "clinical_review", lastReasonMessage = "Priority confirmed by clinical review.")
        dao.saveFakeBackendVisit(updated)
        _visitEventFlow.emit(visitId)
    }

    suspend fun sendToEmergency(visitId: String) = withContext(Dispatchers.IO) {
        val v = dao.getFakeBackendVisit(visitId) ?: return@withContext
        val updated = v.copy(status = "emergency", band = "red", bandLabel = "Emergency", lastReasonCode = "emergency_transfer", lastReasonMessage = "Referred immediately to Casualty/Emergency.")
        dao.saveFakeBackendVisit(updated)
        _visitEventFlow.emit(visitId)
    }

    suspend fun markFlagSeen(visitIdOrFlagId: String) = withContext(Dispatchers.IO) {
        val flag = urgentFlagsMap[visitIdOrFlagId]
            ?: urgentFlagsMap.values.firstOrNull { it.id == visitIdOrFlagId || it.visitId == visitIdOrFlagId }
        if (flag != null) {
            val seen = flag.copy(seenAt = formatIsoTime(), seenBy = "Dr. Aamir Lone")
            flag.visitId?.let { urgentFlagsMap[it] = seen }
            urgentFlagsMap[flag.id] = seen
            _visitEventFlow.emit(flag.visitId ?: flag.id)
        }
    }

    suspend fun addPatients(count: Int) = withContext(Dispatchers.IO) {
        val doctors = dao.getDoctors()
        var currentTotal = dao.getAllFakeBackendVisits().size + 20
        for (i in 1..count) {
            currentTotal++
            val doc = doctors[i % doctors.size]
            val token = "${doc.departmentCode}-${String.format(Locale.US, "%03d", currentTotal)}"
            val visitId = "sim-added-$currentTotal"
            val p = PatientDto("p-$currentTotal", "Patient $currentTotal", "adult", 35, emptyList(), "en")
            val param = RegisterVisitParamDto(visitId, "H01", doc.departmentCode, "app", "green", null, formatIsoTime(), "Walk-in patient", null, p)
            registerVisitInternal(param, token)
        }
    }

    suspend fun loadDemoPatientKamla(): MyTurnDto = withContext(Dispatchers.IO) {
        val visitId = "kamla-demo-visit"
        val existing = dao.getFakeBackendVisit(visitId)
        if (existing != null) {
            return@withContext getMyTurnInternal(visitId)
        }
        val p = PatientDto(
            id = "patient-kamla-123",
            firstName = "Kamla",
            ageGroup = "elderly",
            ageYears = 62,
            specialNeeds = listOf("walking"),
            language = "ks",
            phone = "9876543210",
            area = "Anantnag"
        )
        val param = RegisterVisitParamDto(
            visitId = visitId,
            hospitalId = "H01",
            departmentCode = "M",
            channel = "app",
            suggestedBand = "green",
            offlineToken = null,
            createdAt = formatIsoTime(),
            doctorNote = "Cough 5 days, fever, no breathing difficulty reported, age 62",
            preferredDoctorId = null,
            patient = p
        )
        return@withContext registerVisitInternal(param, "M-017")
    }

    fun getJourneyEventsLog(): List<JourneyEventDto> = journeyEventsLog.toList()

    private fun startAutopilot() {
        autopilotJob?.cancel()
        autopilotJob = scope.launch {
            while (isAutopilotEnabled) {
                delay(30000)
                val doctors = dao.getDoctors()
                doctors.forEach { doc ->
                    callNext(doc.id)
                }
            }
        }
    }

    private fun stopAutopilot() {
        autopilotJob?.cancel()
        autopilotJob = null
    }
}
