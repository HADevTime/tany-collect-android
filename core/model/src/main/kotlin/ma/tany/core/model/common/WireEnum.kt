package ma.tany.core.model.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Enum whose JSON value ([wire]) is the exact backend value (API_CONTRACT_V1 § 3).
 *
 * Contract rule 1: clients tolerate any unknown enum value. Every TANY enum therefore declares an
 * `UNKNOWN` entry (wire = "") that unknown values decode to — a new backend value never crashes
 * decoding; the UI renders a neutral fallback.
 */
interface WireEnum {
    val wire: String
}

open class WireEnumSerializer<E>(
    serialName: String,
    entries: List<E>,
    private val unknown: E,
) : KSerializer<E> where E : Enum<E>, E : WireEnum {
    private val byWire: Map<String, E> = entries.filter { it != unknown }.associateBy { it.wire }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ma.tany.$serialName", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): E = byWire[decoder.decodeString()] ?: unknown

    override fun serialize(encoder: Encoder, value: E) {
        if (value == unknown) throw SerializationException("Refusing to send an UNKNOWN ${descriptor.serialName} to the backend")
        encoder.encodeString(value.wire)
    }

    fun fromWire(value: String?): E = value?.let { byWire[it] } ?: unknown
}
