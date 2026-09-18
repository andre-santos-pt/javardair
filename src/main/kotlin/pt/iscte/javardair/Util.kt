package pt.iscte.javardair

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.jvm.isAccessible


fun Any.getPrivateField(name: String): Any? {
    val f = this::class.members.find { it.name == name }
    if(f == null)
        throw NoSuchFieldException("Field $name not found in ${this::class.simpleName}")
    else {
        f.isAccessible = true
        return f.call(this)
    }
}

object JsonPretty {
    private val jsonPretty = Json { prettyPrint = true }

    fun print(json: String): String {
        val jsonElement = Json.parseToJsonElement(json)
        return jsonPretty.encodeToString(JsonElement.serializer(), jsonElement)
    }
}