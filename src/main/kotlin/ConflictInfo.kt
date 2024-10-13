import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import model.UUID
import model.transformations.Transformation

/**
 * TODO MUDAR A ESTRUTURA DO QUE VEM NESTA MENSAGEM - Talvez passar com param as Transformaçoes
 */

@Serializable
data class ConflictInfo(val conflictMessage: String, val conflictUUID: String, val conflictedTransformation: JsonObject, val conflictTransformationMessage: String) {}