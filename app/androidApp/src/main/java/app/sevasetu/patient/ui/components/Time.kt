package app.sevasetu.patient.ui.components

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val IST = ZoneId.of("Asia/Kolkata")
private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Server times are ISO-8601 UTC and are shown in Asia/Kolkata (contract section 2).
 * Already-formatted values (the simulator sends "HH:mm") pass through unchanged.
 */
fun displayTime(value: String?): String? {
    if (value.isNullOrBlank()) return value
    return runCatching { OffsetDateTime.parse(value).atZoneSameInstant(IST).format(HH_MM) }
        .recoverCatching { Instant.parse(value).atZone(IST).format(HH_MM) }
        .getOrDefault(value)
}
