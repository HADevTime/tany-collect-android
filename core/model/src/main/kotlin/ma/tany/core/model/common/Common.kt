package ma.tany.core.model.common

import kotlinx.serialization.Serializable

/** `{ ok: true }` acknowledgement. */
@Serializable
data class OkResponse(val ok: Boolean = false)

/** Opening-hours day keys (`mon`…`sun`, Monday first). */
@Serializable(with = Weekday.Serializer::class)
enum class Weekday(override val wire: String) : WireEnum {
    MON("mon"), TUE("tue"), WED("wed"), THU("thu"), FRI("fri"), SAT("sat"), SUN("sun"), UNKNOWN("");

    object Serializer : WireEnumSerializer<Weekday>("Weekday", entries, UNKNOWN)
}
