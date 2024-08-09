import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class FileContent(val fileName: String, val fileContent: String) {
   /** fun toJson(): JsonObject {
        val fields = mutableMapOf<String, JsonElement>()
        fields["name"] = JsonPrimitive(name)
        fields["content"] = JsonPrimitive(content)
        return JsonObject(fields)
    }**/
}

/**fun JsonObject.toFileContent(): FileContent {
    fun JsonObject.fieldName(name: String): String =
        this[name]?.jsonPrimitive?.content ?: throw Exception("Field $name not found")

    fun JsonObject.fieldContent(content: String): String =
        this[content]?.jsonPrimitive?.content ?: throw Exception("Field $content not found")

    return FileContent(fieldName(name), fieldContent(content))
} **/