import kotlinx.serialization.Serializable

@Serializable
data class Conflict(val isClear: Boolean, val content: String) {}