package eu.efti.datatools.populate

import eu.efti.datatools.populate.SchemaConversion.commonToIdentifiers
import eu.efti.datatools.schema.EftiSchemas
import eu.efti.datatools.schema.EftiSchemas.consignmentCommonSchema
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.w3c.dom.Document
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader

class SchemaConversionTest {
    private val updateTestExpectations: Boolean = System.getenv("eu.efti.updateTestExpectations") == "true"

    @Test
    @Tag("expectation-update")
    fun `should convert common document to identifiers document that matches expected document`() {
        val expectationFilename = "conversion-expected.xml"

        val identifiersDoc = commonToIdentifiers(
            EftiDomPopulator(1234, RepeatablePopulateMode.MINIMUM_ONE).populate(
                consignmentCommonSchema
            )
        )

        if (updateTestExpectations) {
            val updated = formatXml(identifiersDoc)
            FileWriter(testResourceFile(expectationFilename)).use {
                it.write(updated)
                it.flush()
            }
            fail("Test expectations updated, run test again to verify")
        } else {
            val expected =
                InputStreamReader(classpathInputStream(expectationFilename)).use { it.readText() }

            assertAll(
                { assertThat(XmlUtil.validate(identifiersDoc, EftiSchemas.javaIdentifiersSchema), nullValue()) },
                {
                    // Use junit assertEquals because it formats the expected value better than hamcrest.
                    // Also, CompareMatcher.isSimilarTo does not work with consignment-common document, maybe it's too big?
                    assertEquals(
                        expected, formatXml(identifiersDoc),
                        "Populated document did not match the expected document, please update test expectations with: ./gradlew updateTestExpectations"
                    )
                },
            )
        }
    }

    companion object {
        private fun formatXml(doc: Document): String = serializeToString(doc, prettyPrint = true)

        private fun classpathInputStream(filename: String): InputStream =
            checkNotNull(SchemaConversionTest::class.java.getResourceAsStream(filename)) {
                "Could not open $filename"
            }

        private fun testResourceFile(filename: String): File {
            val packagePath = SchemaConversionTest::class.java.packageName.replace(".", "/")
            return File("""src/test/resources/$packagePath/$filename""")
        }
    }
}
