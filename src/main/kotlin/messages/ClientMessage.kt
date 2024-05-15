package messages

import kotlinx.serialization.Serializable

/**
 * Message sent by the Client.
 */
@Serializable
data class ClientMessage(val op: ClientOperations, val content: String) {}