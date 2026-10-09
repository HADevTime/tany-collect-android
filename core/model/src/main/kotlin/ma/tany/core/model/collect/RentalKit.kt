package ma.tany.core.model.collect

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ma.tany.core.model.common.InstantSerializer
import ma.tany.core.model.common.WireEnum
import ma.tany.core.model.common.WireEnumSerializer
import java.time.Instant

// Rental kit V1 (tany-backend docs/RENTAL-KIT.md, API_CONTRACT_V1 § 11) — additive; `kit = null` = module OFF or a booking
// made before it: the historical `includedAccessories` checklist stays in use. The merchant records what is physically
// handed over / returned (differences only); a difference never blocks, TANY instructs it — never an amount here.

/** Kind of kit element. The TANY transport bag is always served LAST by the backend (never reordered here). */
@Serializable(with = KitItemType.Serializer::class)
enum class KitItemType(override val wire: String) : WireEnum {
    ACCESSORY("ACCESSORY"),
    TRANSPORT_BAG("TRANSPORT_BAG"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<KitItemType>("KitItemType", entries, UNKNOWN)
}

/** State recorded for one element at one step. */
@Serializable(with = KitCheckState.Serializer::class)
enum class KitCheckState(override val wire: String) : WireEnum {
    PRESENT("PRESENT"),
    MISSING("MISSING"),
    DAMAGED("DAMAGED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<KitCheckState>("KitCheckState", entries, UNKNOWN)
}

/** `booking.kit` — snapshot FROZEN at booking creation + the recorded checks (pickup, customer, return). */
@Serializable
data class RentalKit(
    val items: List<RentalKitItem> = emptyList(),
    val count: Int = 0,
    val totalQuantity: Int = 0,
    val expectedAtReturnCount: Int = 0,
    val pickup: KitPickupCheck? = null,
    val customerPickup: KitCustomerCheck? = null,
    @SerialName("return") val returnCheck: KitReturnCheck? = null,
)

@Serializable
data class RentalKitItem(
    val id: String,
    val type: KitItemType = KitItemType.UNKNOWN,
    /** Served in the `Accept-Language` locale (stored values) — product data, shown verbatim. */
    val name: String,
    val description: String? = null,
    /** Relative to the API (or absolute); null ⇒ neutral thumbnail. */
    val image: String? = null,
    val quantity: Int = 1,
    val required: Boolean = true,
    val position: Int = 0,
    /** False when noted MISSING at the handover: never expected back, never checked at the return. */
    val handedOver: Boolean = true,
    val pickupState: KitCheckState? = null,
    val customerPickupState: KitCheckState? = null,
    val returnState: KitCheckState? = null,
)

@Serializable
data class KitPickupCheck(
    @Serializable(with = InstantSerializer::class) val checkedAt: Instant? = null,
    val allPresent: Boolean = true,
    val missingCount: Int = 0,
)

@Serializable
data class KitCustomerCheck(
    @Serializable(with = InstantSerializer::class) val checkedAt: Instant? = null,
    val allPresent: Boolean = true,
    val reportedCount: Int = 0,
)

@Serializable
data class KitReturnCheck(
    @Serializable(with = InstantSerializer::class) val checkedAt: Instant? = null,
    val allPresent: Boolean = true,
    val issueCount: Int = 0,
)

/** One recorded difference. Only DIFFERENCES are sent: an element not cited is PRESENT (`checks: []` = all present). */
@Serializable
data class KitCheck(val itemId: String, val state: KitCheckState)

@Serializable
data class KitChecks(@EncodeDefault val checks: List<KitCheck> = emptyList())

/** Elements actually handed over (server flag) — the only ones expected and checked at the return. */
val RentalKit.returnItems: List<RentalKitItem> get() = items.filter { it.handedOver }

/** Handover check: every element is handed over unless the merchant noted it [missing]; differences only. */
fun RentalKit.handoverChecks(missing: Set<String>): KitChecks =
    KitChecks(items.filter { it.id in missing }.map { KitCheck(it.id, KitCheckState.MISSING) })

/**
 * Return check: [issues] = MISSING / DAMAGED per element (PRESENT entries are dropped). Only handed-over elements can be
 * cited (the server refuses the others).
 */
fun RentalKit.returnChecks(issues: Map<String, KitCheckState>): KitChecks =
    KitChecks(
        returnItems.mapNotNull { item ->
            issues[item.id]?.takeIf { it == KitCheckState.MISSING || it == KitCheckState.DAMAGED }?.let { KitCheck(item.id, it) }
        },
    )
