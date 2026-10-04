package ma.tany.core.model.common

import kotlinx.serialization.json.Json

/**
 * The single JSON configuration for the TANY API.
 * - `ignoreUnknownKeys`: contract rule 1 (additive evolution — unknown fields are ignored).
 * - `explicitNulls = false`: absent optional fields decode to `null` (flags change response shapes, contract § 4).
 * - `coerceInputValues`: a `null` sent for a field with a default keeps the default.
 */
val TanyJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = false
}
