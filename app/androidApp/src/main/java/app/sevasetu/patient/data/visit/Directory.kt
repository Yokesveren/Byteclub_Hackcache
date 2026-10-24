package app.sevasetu.patient.data.visit

import android.content.Context
import app.sevasetu.patient.data.gateway.BackendGateway
import app.sevasetu.patient.data.model.DepartmentDto
import app.sevasetu.patient.data.model.DoctorDto
import app.sevasetu.patient.data.model.HospitalDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DirectoryData(
    val hospitals: List<HospitalDto> = emptyList(),
    val departments: List<DepartmentDto> = emptyList(),
    val doctors: List<DoctorDto> = emptyList(),
    val fetchedAt: Long = 0,
)

/**
 * The public hospital tables (PRD 8.1 first-launch download): hospitals, departments and doctors with their status.
 * Refreshed from the backend when online and kept on the phone, so registration and "Before you visit" work offline.
 * Ids carry the hospital (`H01-GM`, `H01-D01`), which is how rows are matched to a hospital.
 */
class Directory(context: Context, private val backend: BackendGateway) {

    private val sp = context.getSharedPreferences("seva_directory", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _data = MutableStateFlow(
        sp.getString("data", null)?.let { runCatching { json.decodeFromString<DirectoryData>(it) }.getOrNull() } ?: BUILT_IN
    )
    val data: StateFlow<DirectoryData> = _data.asStateFlow()

    /** True when the shown list came from the hospital (not the built-in copy) at [DirectoryData.fetchedAt]. */
    val isFresh: Boolean get() = _data.value.fetchedAt > 0

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val d = DirectoryData(backend.listHospitals(), backend.listDepartments(), backend.listDoctors(), System.currentTimeMillis())
            if (d.hospitals.isNotEmpty()) {
                _data.value = d
                sp.edit().putString("data", json.encodeToString(DirectoryData.serializer(), d)).apply()
            }
            true
        }.getOrDefault(false)
    }

    fun hospital(id: String): HospitalDto? = _data.value.hospitals.firstOrNull { it.id == id }
    fun departments(hospitalId: String): List<DepartmentDto> = _data.value.departments.filter { it.id.startsWith("$hospitalId-") }
    fun doctors(hospitalId: String): List<DoctorDto> = _data.value.doctors.filter { it.id.startsWith("$hospitalId-") }

    companion object {
        private fun dept(h: String, suffix: String, code: String, name: String, hi: String, ur: String) =
            DepartmentDto("$h-$suffix", code, name, mapOf("en" to name, "hi" to hi, "ur" to ur))

        /** The demo hospitals as shipped (same as supabase/seed.sql), for a first launch with no signal. */
        val BUILT_IN = DirectoryData(
            hospitals = listOf(
                HospitalDto("H01", "Chinar District Hospital (Demo)", "Hospital Road, Anantnag (fictional)", 33.7311, 75.1487, emptyMap()),
                HospitalDto("H02", "Jhelum Sub-District Hospital (Demo)", "Main Bazaar, Bijbehara (fictional)", 33.7928, 75.107, emptyMap()),
                HospitalDto("H03", "Lidder Community Health Centre (Demo)", "Pahalgam Road, Seer (fictional)", 33.835, 75.241, emptyMap()),
            ),
            departments = listOf("H01", "H02", "H03").flatMap { h ->
                listOfNotNull(
                    dept(h, "GM", "M", "General Medicine", "सामान्य चिकित्सा", "جنرل میڈیسن"),
                    dept(h, "PD", "P", "Paediatrics", "बाल रोग", "بچوں کا شعبہ"),
                    if (h != "H03") dept(h, "MH", "A", "Maternal Health", "मातृ स्वास्थ्य", "زچہ و بچہ") else null,
                    if (h == "H01") dept(h, "OR", "O", "Orthopaedics", "हड्डी रोग", "ہڈیوں کا شعبہ") else null,
                )
            },
        )
    }
}
