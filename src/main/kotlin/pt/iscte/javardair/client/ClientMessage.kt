package pt.iscte.javardair.client

import kotlinx.serialization.Serializable

/**
 * Message sent by the Client.
 */
@Serializable
data class ClientMessage(val op: ClientOperation, val content: String) {}