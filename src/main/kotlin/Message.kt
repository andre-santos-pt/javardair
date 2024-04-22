import kotlinx.serialization.Serializable

@Serializable
data class Message(val op: Operations, val content: String)