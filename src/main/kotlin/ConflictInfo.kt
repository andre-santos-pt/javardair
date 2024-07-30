import kotlinx.serialization.Serializable
import model.transformations.Transformation

/**
 * TODO MUDAR A ESTRUTURA DO QUE VEM NESTA MENSAGEM - Talvez passar com param as Transformaçoes
 */

@Serializable
data class ConflictInfo(val conflictMessage: String, val conflictedNodeUUID: String, val conflictTransformation: Transformation) {}