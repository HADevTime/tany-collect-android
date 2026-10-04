package ma.tany.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.TanyJson
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyAmountTest {
    @Serializable
    private data class Holder(val amount: MoneyAmount, val optional: MoneyAmount? = null)

    @Test
    fun decodesJsonNumbersExactlyIntoCentimes() {
        assertEquals(15000L, TanyJson.decodeFromString<Holder>("""{"amount":150}""").amount.centimes)
        assertEquals(15050L, TanyJson.decodeFromString<Holder>("""{"amount":150.5}""").amount.centimes)
        assertEquals(9999L, TanyJson.decodeFromString<Holder>("""{"amount":99.99}""").amount.centimes)
        // 0.1 + 0.2 style values never go through binary floating point.
        assertEquals(30L, TanyJson.decodeFromString<Holder>("""{"amount":0.30}""").amount.centimes)
        assertEquals(null, TanyJson.decodeFromString<Holder>("""{"amount":1,"optional":null}""").optional)
    }

    @Test
    fun encodesExactPlainNumbers() {
        assertEquals("""{"amount":150}""", TanyJson.encodeToString(Holder(MoneyAmount(15000))))
        assertEquals("""{"amount":150.5}""", TanyJson.encodeToString(Holder(MoneyAmount(15050))))
        assertEquals("""{"amount":1500}""", TanyJson.encodeToString(Holder(MoneyAmount(150000))))
        assertEquals("""{"amount":0.05}""", TanyJson.encodeToString(Holder(MoneyAmount(5))))
    }

    @Test
    fun roundTripIsLossless() {
        listOf("0", "1", "49.5", "250.75", "99999.99").forEach { text ->
            assertEquals(text, MoneyAmount.parse(text).toWire())
        }
    }
}
