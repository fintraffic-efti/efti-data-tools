package eu.efti.datatools.schema

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class EftiSchemasTest {
    @AfterEach
    fun clearInstanceCache() {
        EftiSchemas.clearCache()
    }

    @Test
    fun `should read schemas from a classpath root that is not the root of the classpath`() {
        val schemas = EftiSchemas.fromClasspath("/$classpathPrefix")

        assertAll(
            { assertThat(schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON).name.localPart, equalTo("consignment")) },
            { assertThat(schemas.javaSchema(EftiSchemaId.CONSIGNMENT_COMMON), notNullValue()) },
        )
    }

    @Test
    fun `should read schemas from the root of the classpath`() {
        val schemas = EftiSchemas.fromClasspath()

        assertAll(
            {
                assertThat(
                    schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_IDENTIFIER).name.namespaceURI,
                    equalTo(EftiSchemaId.CONSIGNMENT_IDENTIFIER.namespaceURI),
                )
            },
            { assertThat(schemas.javaSchema(EftiSchemaId.CONSIGNMENT_IDENTIFIER), notNullValue()) },
        )
    }

    @Test
    fun `should read schemas from a directory`() {
        val schemas = EftiSchemas.fromDirectory(TestSchemas.xsdDirectory)

        assertThat(
            schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON).children.map { it.name.localPart },
            equalTo(TestSchemas.consignmentCommonSchema.children.map { it.name.localPart }),
        )
    }

    @Test
    fun `should collect subset ids of the common schema`() {
        val schemas = EftiSchemas.fromDirectory(TestSchemas.xsdDirectory)

        assertThat(
            SubsetId("BE03a") in schemas.subsetIds(EftiSchemaId.CONSIGNMENT_COMMON),
            equalTo(true),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["BE03a", "SI03"])
    fun `hasCommonSubset should return true for a subset that does exist`(subsetId: String) {
        assertThat(TestSchemas.schemas.hasCommonSubset(SubsetId(subsetId)), equalTo(true))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "this isn't a subset",
            "BE0", // Partial subset id
            "be03a", // Valid subset id but in lowercase
        ],
    )
    fun `hasCommonSubset should return false for subset that does not exist`(invalidSubsetId: String) {
        assertThat(TestSchemas.schemas.hasCommonSubset(SubsetId(invalidSubsetId)), equalTo(false))
    }

    @Test
    fun `filterCommonSubsets should drop nodes that are not in the requested subsets`() {
        val doc = XmlUtil.deserializeToDocument(
            """
            <consignment xmlns="${EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI}">
                <contractTermsText>terms</contractTermsText>
                <information>info</information>
            </consignment>
            """.trimIndent(),
        )

        val filtered = TestSchemas.schemas.filterCommonSubsets(doc, setOf(SubsetId("FI01")))

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
    fun `filterCommonSubsets should reject a document that is not valid against the common schema`() {
        val doc = XmlUtil.deserializeToDocument(
            """
            <consignment xmlns="${EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI}">
                <notAnEftiElement/>
            </consignment>
            """.trimIndent(),
        )

        val exception = assertThrows<IllegalArgumentException> {
            TestSchemas.schemas.filterCommonSubsets(doc, setOf(SubsetId("FI01")))
        }

        assertThat(exception.message, containsString("not valid"))
    }

    @Test
    fun `should fail with a helpful message when the schema directory does not exist`(@TempDir tempDir: File) {
        val missing = File(tempDir, "no-such-directory")

        val exception = assertThrows<EftiSchemaException> { EftiSchemas.fromDirectory(missing) }

        assertThat(checkNotNull(exception.message), containsString(missing.absolutePath))
    }

    @Test
    fun `should fail with a helpful message when the main xsd is missing`(@TempDir tempDir: File) {
        val schemas = EftiSchemas.fromDirectory(tempDir)

        val exception = assertThrows<EftiSchemaException> { schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON) }

        assertThat(checkNotNull(exception.message), containsString(EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath))
    }

    @Test
    fun `should fail with a helpful message when an imported xsd is missing`(@TempDir tempDir: File) {
        File(TestSchemas.xsdDirectory, EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath)
            .copyTo(File(tempDir, EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath))

        val schemas = EftiSchemas.fromDirectory(tempDir)

        val exception = assertThrows<EftiSchemaException> { schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON) }

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

        val schemas = EftiSchemas.fromDirectory(tempDir)

        val exception = assertThrows<EftiSchemaException> { schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON) }

        assertThat(checkNotNull(exception.message), containsString(EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI))
    }

    @Test
    fun `should return the same instance for equal sources`() {
        assertAll(
            {
                assertThat(
                    EftiSchemas.fromDirectory(TestSchemas.xsdDirectory),
                    sameInstance(EftiSchemas.fromDirectory(File(TestSchemas.xsdDirectory, "."))),
                )
            },
            {
                assertThat(
                    EftiSchemas.fromClasspath("/$classpathPrefix"),
                    sameInstance(EftiSchemas.fromClasspath("$classpathPrefix/")),
                )
            },
        )
    }

    @Test
    fun `should return distinct instances for distinct sources`() {
        assertThat(
            EftiSchemas.fromClasspath("/"),
            not(sameInstance(EftiSchemas.fromClasspath("/$classpathPrefix"))),
        )
    }

    @Test
    fun `should compile a schema only once per instance`() {
        val schemas = EftiSchemas.fromDirectory(TestSchemas.xsdDirectory)

        assertAll(
            {
                assertThat(
                    schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON),
                    sameInstance(schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON)),
                )
            },
            {
                assertThat(
                    schemas.javaSchema(EftiSchemaId.CONSIGNMENT_COMMON),
                    sameInstance(schemas.javaSchema(EftiSchemaId.CONSIGNMENT_COMMON)),
                )
            },
        )
    }

    @Test
    fun `should bypass the instance cache when requested`() {
        assertAll(
            {
                assertThat(
                    EftiSchemas.uncached(DirectoryXsdSource(TestSchemas.xsdDirectory)),
                    not(sameInstance(EftiSchemas.fromDirectory(TestSchemas.xsdDirectory))),
                )
            },
            {
                val before = EftiSchemas.fromDirectory(TestSchemas.xsdDirectory)
                EftiSchemas.clearCache()
                assertThat(EftiSchemas.fromDirectory(TestSchemas.xsdDirectory), not(sameInstance(before)))
            },
        )
    }

    companion object {
        private val classpathPrefix: String =
            checkNotNull(System.getProperty("eu.efti.datatools.test.xsdClasspathPrefix")) {
                "System property eu.efti.datatools.test.xsdClasspathPrefix must be set by the build"
            }
    }
}
