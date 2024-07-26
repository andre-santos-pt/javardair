import kotlinx.serialization.Serializable

/**
 * TODO MUDAR A ESTRUTURA DO QUE VEM NESTA MENSAGEM
 * Importante: UUID e a diferença das outras versoes
 */

@Serializable
data class ConflictInfo(val conflictMessage: String, val conflictedNodeUUID: String) {}