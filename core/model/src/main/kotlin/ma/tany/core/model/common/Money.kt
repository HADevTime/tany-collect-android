package ma.tany.core.model.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A backend money amount, held as integer centimes — never a floating-point value.
 *
 * Wire format (contract § 0): JSON number in major units (MAD/DH), ≤ 2 decimals (`150`, `150.5`, `99.99`).
 * The JSON literal is parsed as text into [BigDecimal], so no binary floating point is ever involved.
 * Amounts sent back to the server (e.g. `confirmedAmount`) are re-encoded from the exact centimes,
 * which keeps the backend's exact-equality checks safe.
 *
 * Android never recalculates authoritative totals: it displays the server's amounts.
 */
@Serializable(with = MoneyAmountSerializer::class)
@JvmInline
value class MoneyAmount(val centimes: Long) : Comparable<MoneyAmount> {
    val major: BigDecimal get() = BigDecimal.valueOf(centimes, 2)

    val isZero: Boolean get() = centimes == 0L

    override fun compareTo(other: MoneyAmount): Int = centimes.compareTo(other.centimes)

    /** Canonical wire text: plain notation, no exponent, no useless trailing zeros (`150`, `150.5`). */
    fun toWire(): String {
        val stripped = major.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
    }

    override fun toString(): String = "MoneyAmount(${toWire()})"

    companion object {
        val ZERO = MoneyAmount(0)

        fun parse(text: String): MoneyAmount {
            val value = text.trim().toBigDecimalOrNull() ?: throw SerializationException("Invalid money amount: '$text'")
            // Backend rounds to 2 decimals (Math.round(x*100)/100); HALF_UP mirrors that for any legacy value.
            return MoneyAmount(value.setScale(2, RoundingMode.HALF_UP).unscaledValue().longValueExact())
        }

        fun ofMajor(units: Long): MoneyAmount = MoneyAmount(Math.multiplyExact(units, 100L))
    }
}

object MoneyAmountSerializer : KSerializer<MoneyAmount> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ma.tany.MoneyAmount", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): MoneyAmount {
        val text = if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content else decoder.decodeString()
        return MoneyAmount.parse(text)
    }

    override fun serialize(encoder: Encoder, value: MoneyAmount) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toWire()))
        } else {
            encoder.encodeString(value.toWire())
        }
    }
}
