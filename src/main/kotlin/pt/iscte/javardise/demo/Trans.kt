package pt.iscte.javardise.demo

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
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
            fields["new-name"] = JsonPrimitive(getNewName().toString()) // vai dizer new name mesmo que nao tenha sido a coisa q mudou is that okay?
            fields["new-parameters"] = JsonArray(getNode().parameters.map {
                JsonObject(
                    mapOf<String, JsonElement>(
                        "new-type" to JsonPrimitive(getNewParameters()[0].type.toString()),
                        "new-name" to JsonPrimitive(getNewParameters()[0].name.toString())
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
            fields["new-returnType"] = JsonPrimitive(getNewReturnType().toString())
        }

        is BodyChangedCallable -> {
            fields["uuid"] = JsonPrimitive(getNode().uuid.toString())
            fields["new-body"] = JsonPrimitive(getNewBody().toString())
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
                NodeList<Parameter>(this["new-parameters"]?.jsonArray?.map {
                    it as JsonObject
                    Parameter(StaticJavaParser.parseType(it.field("new-type")), it.field("new-name"))
                }),
                SimpleName(field("new-name"))
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
                StaticJavaParser.parseType(field("new-returnType"))

            )
        BodyChangedCallable::class.java.simpleName ->
            BodyChangedCallable(
                project,
                project.getMethodByUUID(UUID(field("uuid")))!!,
                StaticJavaParser.parseBlock(field("new-body"))
            )

        else -> throw Exception("Transformation not found $code")
    }
}