package pt.iscte.javardair.messages

/**
 * Types of messages that the Server can send.
 */
enum class ServerOperations {
    FETCH_RESPONSE, // Sends client the current files.
    PROPAGATE, // Propagates the changes to the other clients.
    NOTIFY_CONFLICTS, // Notifies clients about the existense of conflicts.
}