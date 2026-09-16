package pt.iscte.javardair.server

import kotlinx.serialization.Serializable

/**
 * Message sent by the Server.
 */
@Serializable
data class ServerMessage(val op: ServerOperation, val content: String, val sender: String) {}