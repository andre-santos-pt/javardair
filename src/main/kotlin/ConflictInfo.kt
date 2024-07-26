import kotlinx.serialization.Serializable

/**
 * TODO MUDAR A ESTRUTURA DO QUE VEM NESTA MENSAGEM - Talvez passar com param as Transformaçoes
 */

@Serializable
data class ConflictInfo(val conflictMessage: String, val conflictedNodeUUID: String) {}