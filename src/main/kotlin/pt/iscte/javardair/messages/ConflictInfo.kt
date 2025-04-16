package pt.iscte.javardair.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ConflictInfo(val conflictMessage: String, val conflictUUID: String, val conflictedTransformation: JsonObject, val conflictTransformationMessage: String) {}