import kotlinx.serialization.Serializable

@Serializable
data class Response(val op: Operations, val trans: String) {
    //constructor(op: Operations): this(op, "")
}