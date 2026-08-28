package eu.efti.datatools.schema

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class EftiSchemaTest {
    @Test
    fun `should parse identifier schema to XmlSchemaElement`() {
        val element = EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_IDENTIFIER, TestSchemas.xsdDirectory).xmlSchema

        // Do some minimal assertions.
        assertAll(
            { assertThat(element.name.localPart, equalTo("consignment")) },
            { assertThat(element.children, hasSize(4)) },
        )
    }

    @Test
    fun `should parse common schema to XmlSchemaElement`() {
        val element = EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, TestSchemas.xsdDirectory).xmlSchema

        // Do some minimal assertions.
        val applicableServiceCharge =
            checkNotNull(element.children.find { it.name.localPart == "applicableServiceCharge" })

        assertAll(
            { assertThat(element.name.localPart, equalTo("consignment")) },
            { assertThat(element.children, hasSize(43)) },
            { assertThat(applicableServiceCharge.subsets, hasSize(24)) },
            { assertThat(applicableServiceCharge.subsets, hasItem(SubsetId("LT01"))) },
        )
    }

    @Test
    fun `should read schema from a classpath root that is not the root of the classpath`() {
        val schema = EftiSchema.fromClasspath(EftiSchemaId.CONSIGNMENT_COMMON, "/$classpathPrefix")

        assertAll(
            { assertThat(schema.xmlSchema.name.localPart, equalTo("consignment")) },
            { assertThat(schema.javaSchema, notNullValue()) },
        )
    }

    @Test
    fun `should read schema from the root of the classpath`() {
        val schema = EftiSchema.fromClasspath(EftiSchemaId.CONSIGNMENT_IDENTIFIER, "/")

        assertAll(
            {
                assertThat(
                    schema.xmlSchema.name.namespaceURI,
                    equalTo(EftiSchemaId.CONSIGNMENT_IDENTIFIER.namespaceURI),
                )
            },
            { assertThat(schema.javaSchema, notNullValue()) },
        )
    }

    @Test
    fun `should read schema from a directory`() {
        val schema = EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, TestSchemas.xsdDirectory)

        assertThat(schema.xmlSchema.children, not(empty()))
    }

    @Test
    fun `should collect subset ids of the common schema`() {
        assertThat(SubsetId("BE03a") in TestSchemas.common.subsetIds, equalTo(true))
    }

    @Test
    fun `subset ids should be specific to the schema of the instance`() {
        assertAll(
            { assertThat(TestSchemas.identifier.subsetIds.isNotEmpty(), equalTo(true)) },
            { assertThat(TestSchemas.identifier.subsetIds, not(equalTo(TestSchemas.common.subsetIds))) },
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["BE03a", "SI03"])
    fun `hasSubset should return true for a subset that does exist`(subsetId: String) {
        assertThat(TestSchemas.common.hasSubset(SubsetId(subsetId)), equalTo(true))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "this isn't a subset",
            "BE0", // Partial subset id
            "be03a", // Valid subset id but in lowercase
        ],
    )
    fun `hasSubset should return false for subset that does not exist`(invalidSubsetId: String) {
        assertThat(TestSchemas.common.hasSubset(SubsetId(invalidSubsetId)), equalTo(false))
    }

    @Test
    fun `filterSubsets should drop nodes that are not in the requested subsets`() {
        val doc = XmlUtil.deserializeToDocument(
            """
            <consignment xmlns="${EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI}">
                <contractTermsText>terms</contractTermsText>
                <information>info</information>
            </consignment>
            """.trimIndent(),
        )

        val filtered = TestSchemas.common.filterSubsets(doc, setOf(SubsetId("FI01")))

        assertAll(
            {
                assertThat(
                    "contractTermsText belongs to FI01 and is kept",
                    filtered.getElementsByTagName("*").length,
                    equalTo(2),
                )
            },
            {
                assertThat(
                    "information does not belong to FI01 and is dropped",
                    filtered.documentElement.getElementsByTagName("information").length,
                    equalTo(0),
                )
            },
        )
    }

    @Test
    fun `filterSubsets should reject a document that is not valid against the schema`() {
        val doc = XmlUtil.deserializeToDocument(
            """
            <consignment xmlns="${EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI}">
                <notAnEftiElement/>
            </consignment>
            """.trimIndent(),
        )

        val exception = assertThrows<IllegalArgumentException> {
            TestSchemas.common.filterSubsets(doc, setOf(SubsetId("FI01")))
        }

        assertThat(exception.message, containsString("not valid"))
    }

    @Test
    fun `dropNodesNotInSchema should drop elements that the schema does not declare`() {
        val doc = XmlUtil.deserializeToDocument(
            """
            <consignment xmlns="${EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI}">
                <information>info</information>
                <notAnEftiElement>junk</notAnEftiElement>
            </consignment>
            """.trimIndent(),
        )

        val filtered = TestSchemas.common.dropNodesNotInSchema(doc)

        assertAll(
            {
                assertThat(
                    "information is declared by the schema and is kept",
                    filtered.documentElement.getElementsByTagName("information").length,
                    equalTo(1),
                )
            },
            {
                assertThat(
                    "notAnEftiElement is not declared by the schema and is dropped",
                    filtered.documentElement.getElementsByTagName("notAnEftiElement").length,
                    equalTo(0),
                )
            },
            {
                assertThat(
                    "input document is not modified",
                    doc.documentElement.getElementsByTagName("*").length,
                    equalTo(2),
                )
            },
        )
    }

    @Test
    fun `dropNodesNotInSchema should ignore namespaces when requested`() {
        val doc = XmlUtil.deserializeToDocument(
            """
            <consignment xmlns="${EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI}">
                <carrierAcceptanceDateTime formatId="205">202410141513+00</carrierAcceptanceDateTime>
                <information>info</information>
            </consignment>
            """.trimIndent(),
        )

        // The document is in the common namespace, but it is filtered against the identifier schema.
        val namespaceAware = TestSchemas.identifier.dropNodesNotInSchema(doc)
        val namespaceUnaware = TestSchemas.identifier.dropNodesNotInSchema(doc, namespaceAware = false)

        assertAll(
            {
                assertThat(
                    "nothing matches the identifier schema when namespaces are taken into account",
                    namespaceAware.documentElement.getElementsByTagName("*").length,
                    equalTo(0),
                )
            },
            {
                assertThat(
                    "carrierAcceptanceDateTime is declared by the identifier schema and is kept",
                    namespaceUnaware.documentElement.getElementsByTagName("carrierAcceptanceDateTime").length,
                    equalTo(1),
                )
            },
            {
                assertThat(
                    "information is not declared by the identifier schema and is dropped",
                    namespaceUnaware.documentElement.getElementsByTagName("information").length,
                    equalTo(0),
                )
            },
        )
    }

    @Test
    fun `should fail with a helpful message when the schema directory does not exist`(@TempDir tempDir: File) {
        val missing = File(tempDir, "no-such-directory")

        val exception = assertThrows<EftiSchemaException> {
            EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, missing)
        }

        assertThat(checkNotNull(exception.message), containsString(missing.absolutePath))
    }

    @Test
    fun `should fail with a helpful message when the main xsd is missing`(@TempDir tempDir: File) {
        val exception = assertThrows<EftiSchemaException> {
            EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, tempDir)
        }

        assertThat(checkNotNull(exception.message), containsString(EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath))
    }

    @Test
    fun `should fail with a helpful message when an imported xsd is missing`(@TempDir tempDir: File) {
        File(TestSchemas.xsdDirectory, EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath)
            .copyTo(File(tempDir, EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath))

        val exception = assertThrows<EftiSchemaException> {
            EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, tempDir)
        }

        assertThat(checkNotNull(exception.message), containsString("types/types.xsd"))
    }

    @Test
    fun `should fail with a helpful message when the schema does not declare the expected document element`(
        @TempDir tempDir: File,
    ) {
        // Provide the identifier schema under the name of the common schema, so that the file is found and compiles
        // but does not contain the expected document element.
        listOf(EftiSchemaId.CONSIGNMENT_IDENTIFIER.mainXsdPath, "types", "codes").forEach { name ->
            File(TestSchemas.xsdDirectory, name).copyRecursively(File(tempDir, name))
        }
        File(tempDir, EftiSchemaId.CONSIGNMENT_IDENTIFIER.mainXsdPath)
            .renameTo(File(tempDir, EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath))

        val exception = assertThrows<EftiSchemaException> {
            EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, tempDir)
        }

        assertThat(checkNotNull(exception.message), containsString(EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI))
    }

    companion object {
        private val classpathPrefix: String =
            checkNotNull(System.getProperty("eu.efti.datatools.test.xsdClasspathPrefix")) {
                "System property eu.efti.datatools.test.xsdClasspathPrefix must be set by the build"
            }
    }
}
