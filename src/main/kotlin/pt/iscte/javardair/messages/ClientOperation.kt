package pt.iscte.javardair.messages

/**
 * Types of messages that the Client can send.
 */
enum class ClientOperation {
    HANDSHAKE, // Initial message sent to the Server with the Client personal info.
    FETCH_REQUEST, // Request server for the current files.
    UPDATE, // Sends the current changes to the server.
    PUSH, // Sends and tells server to propagate the changes, only when there aren't conflicts.
    FORCE_PUSH, // Sends and tells server to propagate the changes.
}