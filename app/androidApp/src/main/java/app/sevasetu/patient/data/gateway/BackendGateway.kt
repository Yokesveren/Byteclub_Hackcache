package app.sevasetu.patient.data.gateway

import app.sevasetu.patient.data.model.ChatReplyDto
import app.sevasetu.patient.data.model.ClockInfoDto
import app.sevasetu.patient.data.model.DepartmentDto
import app.sevasetu.patient.data.model.DoctorDto
import app.sevasetu.patient.data.model.HospitalDto
import app.sevasetu.patient.data.model.JourneyEventDto
import app.sevasetu.patient.data.model.MyJourneyDto
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.data.model.RegisterVisitParamDto
import app.sevasetu.patient.data.model.SmsDto
import app.sevasetu.patient.data.model.SymptomCheckDto
import app.sevasetu.patient.data.model.UrgentFlagDto
import kotlinx.coroutines.flow.Flow

class NetworkException(message: String = "No internet connection (simulated)") : Exception(message)

interface BackendGateway {
    suspend fun registerVisit(p: RegisterVisitParamDto): MyTurnDto
    suspend fun getMyTurn(visitId: String): MyTurnDto
    suspend fun linkSlip(hospitalId: String, token: String): MyTurnDto?
    suspend fun raiseUrgentFlag(p: UrgentFlagDto): UrgentFlagDto
    suspend fun getUrgentFlag(id: String): UrgentFlagDto?
    suspend fun recordSymptomCheck(p: SymptomCheckDto)
    suspend fun ingestJourneyEvents(list: List<JourneyEventDto>): Int
    suspend fun getMyJourney(visitId: String): MyJourneyDto
    suspend fun getClock(): ClockInfoDto
    suspend fun listHospitals(): List<HospitalDto>
    suspend fun listDepartments(): List<DepartmentDto>
    suspend fun listDoctors(): List<DoctorDto>
    /** Emits when this visit (or, with [hospitalId], that hospital's queue) changes; callers then refetch. */
    fun observeVisit(visitId: String, hospitalId: String? = null): Flow<Unit>
    /** The help chat; online only (the simulator answers as if offline). */
    suspend fun chatAsk(hospitalId: String, lang: String, text: String, sessionId: String, visitId: String?): ChatReplyDto
    /** Texts the hospital sent to this visit's phone number. */
    suspend fun getMySms(visitId: String): List<SmsDto>
}
