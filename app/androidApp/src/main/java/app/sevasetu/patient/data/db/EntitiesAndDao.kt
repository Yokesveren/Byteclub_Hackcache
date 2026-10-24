package app.sevasetu.patient.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity(tableName = "stored_visits")
data class StoredVisitEntity(
    @PrimaryKey val visitId: String,
    val hospitalId: String,
    val departmentCode: String,
    val token: String,
    val offlineToken: String?,
    val isConfirmed: Boolean,
    val jsonContent: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "patient_profiles")
data class PatientProfileEntity(
    @PrimaryKey val id: String,
    val firstName: String,
    val ageGroup: String,
    val ageYears: Int?,
    val specialNeedsJson: String,
    val language: String,
    val phone: String?,
    val area: String?,
    val abhaRef: String?
)

@Entity(tableName = "registration_drafts")
data class RegistrationDraftEntity(
    @PrimaryKey val id: String = "current_draft",
    val step: Int,
    val draftJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "outbox_items")
data class OutboxItemEntity(
    @PrimaryKey val id: String,
    val functionName: String,
    val argsJson: String,
    val priority: Int, // 0 = urgent flag, 1 = Priority reg, 2 = Routine reg, 3 = symptom check, 4 = journey event
    val createdAt: Long,
    val attempts: Int = 0,
    val state: String = "PENDING", // PENDING, SENDING, DONE, FAILED
    val lastError: String? = null
)

@Entity(tableName = "cached_journeys")
data class CachedJourneyEntity(
    @PrimaryKey val visitId: String,
    val journeyJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "hospitals")
data class HospitalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val servicesJson: String
)

@Entity(tableName = "departments")
data class DepartmentEntity(
    @PrimaryKey val id: String,
    val hospitalId: String,
    val code: String,
    val name: String,
    val nameI18nJson: String
)

@Entity(tableName = "doctors")
data class DoctorEntity(
    @PrimaryKey val id: String,
    val hospitalId: String,
    val departmentCode: String,
    val name: String,
    val room: String,
    val floor: Int,
    val roomWaypointId: String,
    val stairFree: Boolean,
    val status: String,
    val statusNote: String?
)

@Entity(tableName = "fake_backend_state")
data class FakeBackendStateEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "fake_backend_visits")
data class FakeBackendVisitEntity(
    @PrimaryKey val visitId: String,
    val hospitalId: String,
    val departmentCode: String,
    val doctorId: String,
    val token: String,
    val offlineToken: String?,
    val firstName: String,
    val band: String,
    val bandLabel: String,
    val bandProvisional: Boolean,
    val status: String,
    val createdAtEpoch: Long,
    val lastReasonCode: String?,
    val lastReasonMessage: String?,
    val lastReasonAt: Long?,
    val rawJson: String
)

@Dao
interface SevaDao {
    // Stored Visits
    @Query("SELECT * FROM stored_visits ORDER BY updatedAt DESC")
    fun getAllStoredVisits(): List<StoredVisitEntity>

    @Query("SELECT * FROM stored_visits WHERE visitId = :visitId LIMIT 1")
    fun getStoredVisit(visitId: String): StoredVisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStoredVisit(visit: StoredVisitEntity)

    // Drafts
    @Query("SELECT * FROM registration_drafts WHERE id = 'current_draft' LIMIT 1")
    fun getRegistrationDraft(): RegistrationDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveRegistrationDraft(draft: RegistrationDraftEntity)

    @Query("DELETE FROM registration_drafts WHERE id = 'current_draft'")
    fun clearRegistrationDraft()

    // Outbox
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun enqueueOutbox(item: OutboxItemEntity)

    @Query("SELECT * FROM outbox_items WHERE state = 'PENDING' ORDER BY priority ASC, createdAt ASC")
    fun getPendingOutboxItems(): List<OutboxItemEntity>

    @Update
    fun updateOutboxItem(item: OutboxItemEntity)

    @Query("DELETE FROM outbox_items WHERE id = :id")
    fun deleteOutboxItem(id: String)

    @Query("SELECT * FROM outbox_items ORDER BY createdAt DESC")
    fun getAllOutboxItems(): List<OutboxItemEntity>

    // Public cached tables
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHospitals(items: List<HospitalEntity>)

    @Query("SELECT * FROM hospitals")
    fun getHospitals(): List<HospitalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDepartments(items: List<DepartmentEntity>)

    @Query("SELECT * FROM departments")
    fun getDepartments(): List<DepartmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDoctors(items: List<DoctorEntity>)

    @Query("SELECT * FROM doctors")
    fun getDoctors(): List<DoctorEntity>

    // Cached Journeys
    @Query("SELECT * FROM cached_journeys WHERE visitId = :visitId LIMIT 1")
    fun getCachedJourney(visitId: String): CachedJourneyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveCachedJourney(item: CachedJourneyEntity)

    // FakeBackend DAO operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveFakeBackendVisit(visit: FakeBackendVisitEntity)

    @Query("SELECT * FROM fake_backend_visits WHERE visitId = :visitId LIMIT 1")
    fun getFakeBackendVisit(visitId: String): FakeBackendVisitEntity?

    @Query("SELECT * FROM fake_backend_visits WHERE hospitalId = :hospitalId AND token = :token LIMIT 1")
    fun getFakeBackendVisitByToken(hospitalId: String, token: String): FakeBackendVisitEntity?

    @Query("SELECT * FROM fake_backend_visits WHERE doctorId = :doctorId AND status = 'waiting' ORDER BY CASE WHEN band = 'yellow' THEN 0 ELSE 1 END, createdAtEpoch ASC")
    fun getWaitingVisitsForDoctor(doctorId: String): List<FakeBackendVisitEntity>

    @Query("SELECT * FROM fake_backend_visits WHERE doctorId = :doctorId AND status = 'called' LIMIT 1")
    fun getCalledVisitForDoctor(doctorId: String): FakeBackendVisitEntity?

    @Query("SELECT * FROM fake_backend_visits")
    fun getAllFakeBackendVisits(): List<FakeBackendVisitEntity>

    @Query("SELECT * FROM fake_backend_state WHERE `key` = :key LIMIT 1")
    fun getFakeState(key: String): FakeBackendStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveFakeState(state: FakeBackendStateEntity)
}

@Database(
    entities = [
        StoredVisitEntity::class,
        PatientProfileEntity::class,
        RegistrationDraftEntity::class,
        OutboxItemEntity::class,
        CachedJourneyEntity::class,
        HospitalEntity::class,
        DepartmentEntity::class,
        DoctorEntity::class,
        FakeBackendStateEntity::class,
        FakeBackendVisitEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SevaDatabase : RoomDatabase() {
    abstract fun dao(): SevaDao
}
