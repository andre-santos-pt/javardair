import com.github.javaparser.ast.body.MethodDeclaration
import kotlinx.serialization.json.*
import model.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pt.iscte.javardair.toJson
import pt.iscte.javardair.toTransformation
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files


class TestSerializeAndBack {

    fun writeFile(path: String, src: String) {
        val file = File(path)
        Files.deleteIfExists(file.toPath())
        if(!Files.exists(file.toPath()))
            Files.createDirectories(file.parentFile.toPath());
        PrintWriter(file).use { out ->
            out.println(src)
        }
    }

    // all methods of the project
    fun Project.methods(): Set<MethodDeclaration> {
        return getSetOfCompilationUnit().flatMap { it.types }.flatMap { it.methods }.toSet()
    }

    @Test
    fun test() {
        // base version
        writeFile("temp/base/Test.java", """
                //9e30e98a-36db-47f4-836c-16c390a1d2d7
                package test;

                //13c9f311-0d07-46aa-8591-ef22c6ab8e49
                class Test {

                    //d0779f95-d537-4501-b708-fc50747e6616
                    void method(int param) {

                    }
                }
        """.trimIndent()
        )

        // branch version
        writeFile("temp/branch/Test.java", """
           //9e30e98a-36db-47f4-836c-16c390a1d2d7
            package test;

            //13c9f311-0d07-46aa-8591-ef22c6ab8e49
            class Test {

                //d0779f95-d537-4501-b708-fc50747e6616
                String methodo(String parama) {
                    return parama;
                }

                //83dba2c8-a01d-4352-b8df-794ab8b44e3a
                int newMethod() {
                    return 10;
                }
            }
        """.trimIndent()
        )

        val projBase = Project("temp/base/")
        val projBranch = Project("temp/branch/")


        val factoryOfTransformations = FactoryOfTransformations(projBase, projBranch)
        val listOfTransformations = factoryOfTransformations.getListOfAllTransformations()
        println(factoryOfTransformations)

        val serializedTransformations = listOfTransformations.map { it.toJson(projBase).toString() }
        serializedTransformations.forEach { println(it)}

        val deserializedTransformations = serializedTransformations.mapNotNull { json ->
            (Json.parseToJsonElement(json) as JsonObject).toTransformation(projBase)
        }.toSet()

        applyTransformationsTo(projBase, deserializedTransformations)

        projBase.getSetOfCompilationUnit().forEach { println(it) }

        val baseMethods = projBase.methods()
        val branchMethods = projBranch.methods()

        //println(baseMethods)
        //println(branchMethods)
        assertTrue(baseMethods == branchMethods)

    }
}