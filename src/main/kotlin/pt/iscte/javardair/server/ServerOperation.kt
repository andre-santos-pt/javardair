package pt.iscte.javardair.server

/**
 * Types of messages that the Server can send.
 */
enum class ServerOperation {
    FETCH_RESPONSE, // Sends client the current files.
    PROPAGATE, // Propagates the changes to the other clients.
    NOTIFY_CONFLICTS, // Notifies clients about the existense of conflicts.
}