package messages

import kotlinx.serialization.Serializable

/**
 * Message sent by the Server.
 */
@Serializable
data class ServerMessage(val op: ServerOperations, val content: String) {}