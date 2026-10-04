package ma.tany.core.model

import ma.tany.core.model.common.TanyJson

object Fixtures {
    fun read(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    inline fun <reified T> decode(name: String): T = TanyJson.decodeFromString(read(name))
}
