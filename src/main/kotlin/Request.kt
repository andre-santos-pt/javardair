import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Request(val op: Operations, val projectName: String, val trans: String) {
    /**fun toJson(): JsonObject {
        val fields = mutableMapOf<String, JsonElement>()
        fields["op"] = JsonPrimitive(op.toString())
        fields["projectName"] = JsonPrimitive(projectName)
        fields["trans"] = JsonPrimitive(trans)
        return JsonObject(fields)
    }

    fun toRequest()
    **/
}