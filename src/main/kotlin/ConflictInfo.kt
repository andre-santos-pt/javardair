import kotlinx.serialization.Serializable

@Serializable
data class ConflictInfo(val isClear: Boolean, val content: String) {}