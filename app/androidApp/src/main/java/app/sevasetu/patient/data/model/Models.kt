package app.sevasetu.patient.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConsentDto(
    @SerialName("terms") val terms: Boolean = true,
    @SerialName("movement") val movement: Boolean = false
)

@Serializable
data class PatientDto(
    @SerialName("id") val id: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("age_group") val ageGroup: String, // infant, child, adult, elderly
    @SerialName("age_years") val ageYears: Int? = null,
    @SerialName("special_needs") val specialNeeds: List<String> = emptyList(), // walking, wheelchair, sight, hearing, pregnant, child
    @SerialName("language") val language: String = "en", // ks, ur, doi, hi, en
    @SerialName("phone") val phone: String? = null,
    @SerialName("area") val area: String? = null,
    @SerialName("abha_ref") val abhaRef: String? = null,
    @SerialName("consent") val consent: ConsentDto = ConsentDto()
)

@Serializable
data class SymptomCheckDto(
    @SerialName("id") val id: String,
    @SerialName("ruleset_version") val rulesetVersion: String = "demo-1",
    @SerialName("cluster") val cluster: String? = null, // fever, respiratory, diarrhoea, skin, injury
    @SerialName("answers") val answers: Map<String, String> = emptyMap(),
    @SerialName("hospital_id") val hospitalId: String? = null,
    @SerialName("language") val language: String? = "en",
    @SerialName("outcome") val outcome: String? = null, // emergency, help_desk
    @SerialName("department_code") val departmentCode: String? = null,
    @SerialName("suggested_band") val suggestedBand: String? = null,
    @SerialName("area") val area: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RegisterVisitParamDto(
    @SerialName("visit_id") val visitId: String,
    @SerialName("hospital_id") val hospitalId: String,
    @SerialName("department_code") val departmentCode: String,
    @SerialName("channel") val channel: String = "app",
    @SerialName("suggested_band") val suggestedBand: String = "green", // green or yellow
    @SerialName("offline_token") val offlineToken: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("doctor_note") val doctorNote: String? = null,
    @SerialName("preferred_doctor_id") val preferredDoctorId: String? = null,
    @SerialName("patient") val patient: PatientDto,
    @SerialName("symptom_check") val symptomCheck: SymptomCheckDto? = null
)

@Serializable
data class HospitalDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("address") val address: String,
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double,
    @SerialName("services") val services: Map<String, String> = mapOf("lab" to "up", "xray" to "down"),
    @SerialName("phone") val phone: String? = null // the hospital's number for SMS and missed calls
)

@Serializable
data class DepartmentDto(
    @SerialName("id") val id: String,
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("name_i18n") val nameI18n: Map<String, String> = emptyMap()
)

@Serializable
data class DoctorDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("room") val room: String,
    @SerialName("floor") val floor: Int,
    @SerialName("room_waypoint_id") val roomWaypointId: String? = null, // null: the room is not on a map yet (H02, H03)
    @SerialName("stair_free") val stairFree: Boolean = true,
    @SerialName("status") val status: String = "in", // in, delayed, break, away, off
    @SerialName("status_note") val statusNote: String? = null
)

@Serializable
data class WaitEstimateDto(
    @SerialName("people_ahead") val peopleAhead: Int,
    @SerialName("estimate_min") val estimateMin: Int,
    @SerialName("window_start") val windowStart: String?,
    @SerialName("window_end") val windowEnd: String?,
    @SerialName("paused") val paused: Boolean = false,
    @SerialName("consult_min") val consultMin: Double = 6.0
)

@Serializable
data class LastReasonDto(
    @SerialName("type") val type: String? = null,
    @SerialName("reason_code") val reasonCode: String,
    @SerialName("message") val message: String,
    @SerialName("at") val at: String
)

@Serializable
data class UrgentFlagDto(
    @SerialName("id") val id: String,
    @SerialName("visit_id") val visitId: String? = null,
    @SerialName("hospital_id") val hospitalId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("source") val source: String = "patient_app",
    @SerialName("message") val message: String? = "Feeling worse",
    @SerialName("waypoint_id") val waypointId: String? = null,
    @SerialName("raised_at") val raisedAt: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("seen_at") val seenAt: String? = null,
    @SerialName("seen_by") val seenBy: String? = null
)

@Serializable
data class MyTurnDto(
    @SerialName("visit_id") val visitId: String,
    @SerialName("token") val token: String,
    @SerialName("offline_token") val offlineToken: String? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("hospital") val hospital: HospitalDto,
    @SerialName("department") val department: DepartmentDto,
    @SerialName("doctor") val doctor: DoctorDto,
    @SerialName("band") val band: String, // green, yellow, red
    @SerialName("band_label") val bandLabel: String, // Routine, Priority, Emergency
    @SerialName("band_provisional") val bandProvisional: Boolean = false,
    @SerialName("status") val status: String, // waiting, called, done, emergency, cancelled
    @SerialName("now_serving") val nowServing: String? = null,
    @SerialName("estimate") val estimate: WaitEstimateDto? = null,
    @SerialName("last_reason") val lastReason: LastReasonDto? = null,
    @SerialName("urgent_flag") val urgentFlag: UrgentFlagDto? = null,
    @SerialName("wait_anywhere_allowed") val waitAnywhereAllowed: Boolean = false,
    @SerialName("slip_qr") val slipQr: String,
    @SerialName("last_updated") val lastUpdated: String
)

@Serializable
data class JourneyEventDto(
    @SerialName("id") val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("hospital_id") val hospitalId: String,
    @SerialName("type") val type: String, // entry, scan, room, wrong_turn, exit
    @SerialName("waypoint_id") val waypointId: String,
    @SerialName("occurred_at") val occurredAt: String
)

@Serializable
data class PastJourneyItemDto(
    @SerialName("day") val day: String,
    @SerialName("hospital") val hospital: String,
    @SerialName("department") val department: String,
    @SerialName("doctor") val doctor: String,
    @SerialName("status") val status: String, // red, yellow, green
    @SerialName("follow_up_on") val followUpOn: String? = null,
    @SerialName("consult_ok") val consultOk: Boolean = true,
    @SerialName("notes") val notes: String? = null
)

@Serializable
data class NextFollowUpDto(
    @SerialName("on") val on: String,
    @SerialName("consult_ok") val consultOk: Boolean = true,
    @SerialName("state") val state: String // due, done, defaulted
)

@Serializable
data class MyJourneyDto(
    @SerialName("first_name") val firstName: String,
    @SerialName("journey") val journey: List<PastJourneyItemDto> = emptyList(),
    @SerialName("next_follow_up") val nextFollowUp: NextFollowUpDto? = null
)

@Serializable
data class ClockInfoDto(
    @SerialName("now") val now: String,
    @SerialName("speed") val speed: Int = 1, // 1, 30, 120
    @SerialName("today") val today: String
)

/** A help-chat answer (PRD 11.4): only approved content, live facts or a place; warning signs give "emergency". */
@Serializable
data class ChatReplyDto(
    @SerialName("type") val type: String, // answer, emergency, refuse, help_desk
    @SerialName("text") val text: String,
    @SerialName("action") val action: ChatActionDto? = null,
    @SerialName("outcome") val outcome: String? = null
)

@Serializable
data class ChatActionDto(
    @SerialName("type") val type: String, // place or screen
    @SerialName("tag") val tag: String? = null,
    @SerialName("screen") val screen: String? = null
)

/** A text the hospital's SMS gateway sent to (or received from) the patient's phone number. */
@Serializable
data class SmsDto(
    @SerialName("direction") val direction: String, // in or out
    @SerialName("kind") val kind: String,
    @SerialName("body") val body: String,
    @SerialName("created_at") val createdAt: String
)
