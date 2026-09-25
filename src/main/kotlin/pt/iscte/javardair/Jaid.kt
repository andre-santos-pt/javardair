package pt.iscte.javardair

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.comments.LineComment
import com.github.javaparser.ast.expr.SimpleName
import kotlinx.serialization.json.*
import model.Project
import model.UUID
import model.transformations.*
import model.uuid
import java.nio.file.Path

// map a transformation to a json object
fun Transformation.toJson(project: Project): JsonObject {
    val fields =
        mutableMapOf<String, JsonElement>("code" to JsonPrimitive(this::class.java.simpleName))
    when (this) {

        // TODO RemoveFile

        is AddFile -> {
            val relPath = Path.of(project.path).relativize(getNewNode().storage.get().path).toString()
            fields["path"] = JsonPrimitive(relPath)
            fields["content"] = JsonPrimitive(getNewNode().toString())
        }

        is SignatureChanged -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["name"] = JsonPrimitive(getNewName().toString())
            fields["parameters"] = JsonArray(getNewParameters().map {
                JsonObject(
                    mapOf<String, JsonElement>(
                        "type" to JsonPrimitive(it.type.toString()),
                        "name" to JsonPrimitive(it.name.toString())
                    )
                )
            })
        }

        is AddCallable -> {
            fields["owner-uuid"] =
                JsonPrimitive(getParentNode().uuid.toString())
            fields["constructor"] =
                JsonPrimitive(getNode().isConstructorDeclaration)
            fields["body"] = JsonPrimitive(getNode().toString())
            fields["index"] = JsonPrimitive(getIndex())
        }

        is ReturnTypeChangedMethod -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["returnType"] = JsonPrimitive(getNewReturnType().toString())
        }

        is BodyChangedCallable -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["body"] = JsonPrimitive(getNewBody().toString())
        }

        is RemoveCallable -> {
            fields["owner-uuid"] =
                JsonPrimitive(getParentNode().uuid.toString())
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
        }

        is AddField -> {
            fields["owner-uuid"] =
                JsonPrimitive(getParentNode().uuid.toString())
            val comment = getNode().comment.orElse(null)
            if (comment != null) {
                fields["uuid-comment"] = JsonPrimitive(comment.content)
            }
            fields["newField"] = JsonPrimitive(getNode().toString())
            fields["index"] = JsonPrimitive(getIndex())
        }

        is RemoveField -> {
            fields["owner-uuid"] =
                JsonPrimitive(getParentNode().uuid.toString())
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
        }

        is RenameField -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["name"] = JsonPrimitive(getNewName().toString())
        }

        is TypeChangedField -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["type"] = JsonPrimitive(getNewType().toString())
        }

        is InitializerChangedField -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["initializer"] =
                JsonPrimitive(getNewInitializer().toString())
        }

        // TODO MoveCallableIntraType
//        is MoveCallableIntraType -> {
//            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
//            fields["location-index"] = JsonPrimitive(this.getPrivateField("locationIndex") as Int)
//            fields["order-index"] = JsonPrimitive(getOrderIndex())
//        }

    }
    return JsonObject(fields)
}


// deserialize a transformation from a json object
fun JsonObject.toTransformation(projectTrunk: Project, projectLocalPath: String): Transformation? {
    fun JsonObject.field(name: String): String =
        this[name]?.jsonPrimitive?.content
            ?: throw Exception("Field $name not found")

    return when (val code = field("code")) {

        // TODO RemoveFile

        AddFile::class.java.simpleName ->
            AddFile(StaticJavaParser.parse(field("content")).apply {
                setStorage(Path.of(projectLocalPath).resolve(Path.of(field("path"))))
                println("AddFile: ${storage.get().path}")
            })

        SignatureChanged::class.java.simpleName ->
            SignatureChanged(
                projectTrunk,
                projectTrunk.getMethodByUUID(UUID(field("uuid")))!!,
                NodeList(this["parameters"]?.jsonArray?.map {
                    it as JsonObject
                    Parameter(
                        StaticJavaParser.parseType(it.field("type")),
                        it.field("name")
                    )
                }),
                SimpleName(field("name"))
            )

        AddCallable::class.java.simpleName ->
            AddCallable(
                projectTrunk,
                projectTrunk.getTypeByUUID(UUID(field("owner-uuid")))!!,
                StaticJavaParser.parseMethodDeclaration(field("body")),
                field("index").toInt()
            )

        ReturnTypeChangedMethod::class.java.simpleName ->
            ReturnTypeChangedMethod(
                projectTrunk,
                projectTrunk.getMethodByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseType(field("returnType"))

            )

        BodyChangedCallable::class.java.simpleName ->
            BodyChangedCallable(
                projectTrunk,
                projectTrunk.getMethodByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseBlock(field("body"))
            )

        RemoveCallable::class.java.simpleName ->
            RemoveCallable(
                projectTrunk.getTypeByUUID(UUID(field("owner-uuid")))!!,
                projectTrunk.getMethodByUUID(UUID(field("uuid")))!!,
            )


        AddField::class.java.simpleName -> {
            val fieldDeclaration =
                StaticJavaParser.parseBodyDeclaration(field("newField")) as FieldDeclaration
            val uuidComment = field("uuid-comment")
            fieldDeclaration.setComment(LineComment(uuidComment))
            AddField(
                projectTrunk,
                projectTrunk.getTypeByUUID(UUID(field("owner-uuid")))!!,
                fieldDeclaration,
                field("index").toInt()
            )
        }

        RemoveField::class.java.simpleName ->
            RemoveField(
                projectTrunk.getTypeByUUID(UUID(field("owner-uuid")))!!,
                projectTrunk.getFieldByUUID(UUID(field("uuid")))!!
            )

        RenameField::class.java.simpleName ->
            RenameField(
                projectTrunk.getFieldByUUID(UUID(field("uuid")))!!,
                SimpleName(field("name"))
            )

        TypeChangedField::class.java.simpleName ->
            TypeChangedField(
                projectTrunk,
                projectTrunk.getFieldByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseType(field("type"))
            )

        InitializerChangedField::class.java.simpleName ->
            InitializerChangedField(
                projectTrunk,
                projectTrunk.getFieldByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseExpression(field("initializer"))
            )

        // TODO MoveCallableIntraType
//         MoveCallableIntraType::class.java.simpleName -> {
//             val callable = project.getElementByUUID(UUID(field("uuid"))) as CallableDeclaration<*>
//             //val callable.parentNode.get() as ClassOrInterfaceDeclaration
//             MoveCallableIntraType(callable, field("location-index").toInt(), field("order-index").toInt()
//
//             )
//         }
        else -> {
            System.err.println("Unknown transformation code: $code")
            null
        }
    }
}

fun JsonArray.decodeTransformations(projectTrunk: Project, projectLocalPath:String) =
    mapNotNull { json ->
        (json as JsonObject).toTransformation(projectTrunk, projectLocalPath)
    }