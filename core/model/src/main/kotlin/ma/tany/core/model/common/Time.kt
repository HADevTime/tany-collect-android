package ma.tany.core.model.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/**
 * TANY business time (contract § 2): every civil day (`YYYY-MM-DD`) and opening hour (`HH:MM`) is expressed in
 * Africa/Casablanca, and pickup/return instants are ALWAYS displayed in that zone — never in the phone's zone.
 * The offset comes from the runtime tzdb (no hard-coded Morocco offset anywhere).
 */
object BusinessTime {
    const val ZONE_ID: String = "Africa/Casablanca"
    val zone: ZoneId = ZoneId.of(ZONE_ID)

    fun at(instant: Instant): ZonedDateTime = instant.atZone(zone)

    fun businessDate(instant: Instant): LocalDate = at(instant).toLocalDate()
}

/** ISO 8601 UTC instants with milliseconds (`2026-10-04T09:00:00.000Z`). */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ma.tany.Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            throw SerializationException("Invalid instant: '$text'", e)
        }
    }

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

/** Civil business day `YYYY-MM-DD` (Africa/Casablanca). */
object BusinessDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ma.tany.BusinessDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate {
        val text = decoder.decodeString()
        return try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            throw SerializationException("Invalid business date: '$text'", e)
        }
    }

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
}

/** Local business clock time `HH:MM` (opening hours, Africa/Casablanca). */
object BusinessClockTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ma.tany.BusinessClockTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalTime {
        val text = decoder.decodeString()
        return try {
            LocalTime.parse(text)
        } catch (e: DateTimeParseException) {
            throw SerializationException("Invalid business time: '$text'", e)
        }
    }

    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
}
