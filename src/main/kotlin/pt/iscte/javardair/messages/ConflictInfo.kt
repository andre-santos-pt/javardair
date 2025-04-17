package pt.iscte.javardair.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ConflictInfo(
    val conflictMessage: String,
    //val transformationPair: Pair<JsonObject, JsonObject>
    val conflictUUID: String,
    val conflictingTransformation: JsonObject // oposite
    //val conflictTransformationMessage: String
)