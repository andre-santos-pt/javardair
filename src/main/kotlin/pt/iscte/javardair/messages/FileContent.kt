package pt.iscte.javardair.messages

import kotlinx.serialization.Serializable

@Serializable
class FileContent(val fileName: String, val fileContent: String)