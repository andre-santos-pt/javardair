package pt.iscte.javardair.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ConflictInfo(
    val collaborator: String,
    val conflictMessage: String,
    val conflictUUID: String,
    val conflictingTransformation: JsonObject, // oposite
    val transformation: JsonObject
)