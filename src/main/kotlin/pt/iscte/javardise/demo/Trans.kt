package pt.iscte.javardise.demo

import com.github.javaparser.JavaParser
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.Type
import kotlinx.serialization.json.*
import model.Project
import model.UUID
import model.transformations.*
import model.uuid

// map a transformation to a json object
    fun Transformation.toJson(): JsonObject {
    val fields = mutableMapOf<String, JsonElement>("code" to JsonPrimitive(this::class.java.simpleName))
    when (this) {
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
            fields["owner-uuid"] = JsonPrimitive(getParentNode().uuid.toString())
            fields["constructor"] = JsonPrimitive(getNode().isConstructorDeclaration)
            fields["body"] = JsonPrimitive(getNode().toString())
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
            fields["owner-uuid"] = JsonPrimitive(getParentNode().uuid.toString())
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
        }

        /**
        is AddField -> {
            fields["owner-uuid"] = JsonPrimitive(getParentNode().uuid.toString())
            fields["body"] = JsonPrimitive(getNode().toString())
        }
        **/

        is RemoveField -> {
            fields["owner-uuid"] = JsonPrimitive(getParentNode().uuid.toString())
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
            fields["initializer"] = JsonPrimitive(getNewInitializer().toString())
        }

        is MoveCallableIntraType -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["order-index"] = JsonPrimitive(getOrderIndex())

        }

    }
    return JsonObject(fields)
}


// deserialize a transformation from a json object
fun JsonObject.toTransformation(project: Project): Transformation {
    fun JsonObject.field(name: String): String =
        this[name]?.jsonPrimitive?.content ?: throw Exception("Field $name not found")

    return when (val code = field("code")) {
        SignatureChanged::class.java.simpleName ->
            SignatureChanged(
                project,
                project.getMethodByUUID(UUID(field("uuid")))!!,
                NodeList<Parameter>(this["parameters"]?.jsonArray?.map {
                    it as JsonObject
                    Parameter(StaticJavaParser.parseType(it.field("type")), it.field("name"))
                }),
                SimpleName(field("name"))
            )

        AddCallable::class.java.simpleName ->
            AddCallable(
                project,
                project.getTypeByUUID(UUID(field("owner-uuid")))!!,
                StaticJavaParser.parseMethodDeclaration(field("body"))
            )

        ReturnTypeChangedMethod::class.java.simpleName ->
            ReturnTypeChangedMethod(
                project,
                project.getMethodByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseType(field("returnType"))

            )
        BodyChangedCallable::class.java.simpleName ->
            BodyChangedCallable(
                project,
                project.getMethodByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseBlock(field("body"))
            )
        RemoveCallable::class.java.simpleName ->
            RemoveCallable(
                project.getTypeByUUID(UUID(field("owner-uuid")))!!,
                project.getMethodByUUID(UUID(field("uuid")))!!,
            )
        /**
        AddField::class.java.simpleName ->
            AddField(
                project,
                project.getTypeByUUID(UUID(field("owner-uuid")))!!,
                StaticJavaParser.parseExpression(field("body"))
            )
        **/
        RemoveField::class.java.simpleName ->
            RemoveField(
                project.getTypeByUUID(UUID(field("owner-uuid")))!!,
                project.getFieldByUUID(UUID(field("uuid")))!!
            )
        RenameField::class.java.simpleName ->
            RenameField(
                project.getFieldByUUID(UUID(field("uuid")))!!,
                SimpleName(field("name"))
            )
        TypeChangedField::class.java.simpleName ->
            TypeChangedField(
                project,
                project.getFieldByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseType(field("type"))
            )
        InitializerChangedField::class.java.simpleName ->
            InitializerChangedField(
                project,
                project.getFieldByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseExpression(field("initializer"))
            )

        else -> throw Exception("Transformation not found $code")
    }
}