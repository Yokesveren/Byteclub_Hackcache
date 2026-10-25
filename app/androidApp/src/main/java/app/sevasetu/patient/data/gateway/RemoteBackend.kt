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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server answered, but refused the request (a validation or safety rule). Not an offline case. */
class BackendException(val status: Int, message: String) : Exception(message)

/**
 * The shared SevaSetu backend (`supabase/`, contract in `docs/CONTRACT.md`): the same database the staff website uses.
 * The app holds only the anon key, so every call is one of the patient RPCs or a read of a public table.
 * Unreachable server -> [NetworkException], so callers fall back to the outbox exactly as with the simulator.
 */
class RemoteBackend(
    baseUrl: String,
    private val anonKey: String,
    private val client: OkHttpClient = defaultClient(),
) : BackendGateway {

    private val baseUrl = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    override suspend fun registerVisit(p: RegisterVisitParamDto): MyTurnDto =
        decode(rpc("register_visit", buildJsonObject { put("p", json.encodeToJsonElement(p)) }))
            ?: throw BackendException(200, "The hospital did not return a ticket")

    override suspend fun getMyTurn(visitId: String): MyTurnDto =
        decode(rpc("get_my_turn", buildJsonObject { put("p_visit_id", visitId) }))
            ?: throw BackendException(404, "This visit was not found at the hospital")

    override suspend fun linkSlip(hospitalId: String, token: String): MyTurnDto? =
        decode(rpc("link_slip", buildJsonObject { put("p_hospital_id", hospitalId); put("p_token", token) }))

    override suspend fun raiseUrgentFlag(p: UrgentFlagDto): UrgentFlagDto {
        // The server answers with { id, received_at, seen_at, seen_by }; keep everything the app sent.
        val ack = decode<UrgentFlagDto>(rpc("raise_urgent_flag", buildJsonObject { put("p", json.encodeToJsonElement(p)) }))
        return if (ack == null) p else p.copy(receivedAt = ack.receivedAt, seenAt = ack.seenAt, seenBy = ack.seenBy)
    }

    override suspend fun getUrgentFlag(id: String): UrgentFlagDto? =
        decode(rpc("get_urgent_flag", buildJsonObject { put("p_id", id) }))

    override suspend fun recordSymptomCheck(p: SymptomCheckDto) {
        rpc("record_symptom_check", buildJsonObject { put("p", json.encodeToJsonElement(p)) })
    }

    override suspend fun ingestJourneyEvents(list: List<JourneyEventDto>): Int {
        if (list.isEmpty()) return 0
        val events = buildJsonArray { list.forEach { add(json.encodeToJsonElement(it)) } }
        return decode<Int>(rpc("ingest_journey_events", buildJsonObject { put("p_events", events) })) ?: 0
    }

    override suspend fun getMyJourney(visitId: String): MyJourneyDto =
        decode(rpc("get_my_journey", buildJsonObject { put("p_visit_id", visitId) }))
            ?: throw BackendException(404, "No health journey for this visit")

    override suspend fun getClock(): ClockInfoDto =
        decode(rpc("get_clock", JsonObject(emptyMap()))) ?: throw BackendException(200, "No clock")

    override suspend fun listHospitals(): List<HospitalDto> =
        decode(get("/rest/v1/hospitals?select=id,name,address,lat,lng,services,phone&order=id")) ?: emptyList()

    override suspend fun chatAsk(hospitalId: String, lang: String, text: String, sessionId: String, visitId: String?): ChatReplyDto =
        decode(rpc("chat_ask", buildJsonObject {
            putJsonObject("p") {
                put("hospital_id", hospitalId); put("lang", lang); put("text", text); put("session_id", sessionId)
                if (visitId != null) put("visit_id", visitId)
            }
        })) ?: throw BackendException(200, "No answer")

    override suspend fun getMySms(visitId: String): List<SmsDto> =
        decode(rpc("get_my_sms", buildJsonObject { put("p_visit_id", visitId) })) ?: emptyList()

    override suspend fun listDepartments(): List<DepartmentDto> =
        decode(get("/rest/v1/departments?select=id,code,name,name_i18n&order=id")) ?: emptyList()

    override suspend fun listDoctors(): List<DoctorDto> =
        decode(get("/rest/v1/doctors?select=id,name,room,floor,room_waypoint_id,stair_free,status,status_note&order=id")) ?: emptyList()

    /**
     * Realtime broadcast (contract section 5): emits whenever staff change this visit, the visit's flag is seen,
     * or the hospital's queue moves. Payloads carry only ids, so collectors always refetch `get_my_turn`.
     */
    override fun observeVisit(visitId: String, hospitalId: String?): Flow<Unit> =
        broadcasts(listOfNotNull("visit:$visitId", hospitalId?.let { "hospital:$it" }))

    /** Supabase Realtime speaks the Phoenix channel protocol over one websocket. */
    fun broadcasts(topics: List<String>): Flow<Unit> = callbackFlow {
        val url = baseUrl.replaceFirst("http", "ws") + "/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        var ref = 0
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                topics.forEach { topic ->
                    val join = buildJsonObject {
                        put("topic", "realtime:$topic")
                        put("event", "phx_join")
                        putJsonObject("payload") {
                            putJsonObject("config") {
                                putJsonObject("broadcast") { put("ack", false); put("self", false) }
                                putJsonObject("presence") { put("key", "") }
                                put("postgres_changes", buildJsonArray {})
                                put("private", false)
                            }
                            put("access_token", anonKey)
                        }
                        put("ref", (++ref).toString())
                        put("join_ref", ref.toString())
                    }
                    webSocket.send(join.toString())
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = runCatching { json.parseToJsonElement(text).jsonObject["event"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                if (event == "broadcast") trySend(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(NetworkException("Live updates disconnected"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        // Phoenix drops sockets that stay silent for 60 s.
        val heartbeat = launch {
            while (isActive) {
                delay(25_000)
                socket.send("""{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"hb"}""")
            }
        }
        awaitClose {
            heartbeat.cancel()
            socket.close(1000, null)
        }
    }

    private inline fun <reified T> decode(body: String): T? {
        val element = json.parseToJsonElement(body.ifBlank { "null" })
        return if (element is JsonNull) null else json.decodeFromJsonElement(element)
    }

    private suspend fun rpc(name: String, args: JsonElement): String =
        send(request("/rest/v1/rpc/$name").post(args.toString().toRequestBody(JSON_TYPE)).build())

    private suspend fun get(path: String): String = send(request(path).get().build())

    private fun request(path: String) = Request.Builder()
        .url(baseUrl + path)
        .header("apikey", anonKey)
        .header("Authorization", "Bearer $anonKey")

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException("Cannot reach the hospital server")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw BackendException(it.code, errorMessage(text) ?: "Server error ${it.code}")
            text
        }
    }

    private fun errorMessage(body: String): String? =
        runCatching { json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
