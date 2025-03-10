import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import model.UUID
import model.transformations.Transformation

@Serializable
data class ConflictInfo(val conflictMessage: String, val conflictUUID: String, val conflictedTransformation: JsonObject, val conflictTransformationMessage: String) {}