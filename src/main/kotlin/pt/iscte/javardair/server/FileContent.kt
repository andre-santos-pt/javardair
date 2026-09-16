package pt.iscte.javardair.server

import kotlinx.serialization.Serializable

@Serializable
class FileContent(val fileName: String, val fileContent: String)