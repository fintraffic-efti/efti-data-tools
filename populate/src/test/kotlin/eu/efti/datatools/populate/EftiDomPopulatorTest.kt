package eu.efti.datatools.populate

import eu.efti.datatools.schema.EftiSchemas
import eu.efti.datatools.schema.EftiSchemas.consignmentCommonSchema
import eu.efti.datatools.schema.EftiSchemas.consignmentIdentifierSchema
import eu.efti.datatools.schema.XmlSchemaElement
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Document
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import javax.xml.validation.Schema
import kotlin.streams.asStream

@Suppress("SameParameterValue")
class EftiDomPopulatorTest {
    private val updateTestExpectations: Boolean = System.getenv("eu.efti.updateTestExpectations") == "true"

    @ParameterizedTest
    @MethodSource("populateTestCases")
    fun `should create valid documents with different generator seeds`(testCase: PopulateTestCase) {
        val populator = EftiDomPopulator(testCase.seed, testCase.repeatablePopulateMode)
        val doc = populator.populate(testCase.eftiSchema)

        val error = XmlUtil.validate(doc, testCase.javaSchema)

        // Optimization: catch assertion error so that we can generate full error message lazily
        try {
            assertThat(error, nullValue())
        } catch (e: AssertionError) {
            fail(e.message + "\nDocument was:\n ${formatXml(doc)}", e)
        }
    }

    @Test
    @Tag("expectation-update")
    fun `should populate common document that matches the expected document`() {
        val expectationFilename = "common-expected.xml"
        val eftiSchema = consignmentCommonSchema

        val populator = EftiDomPopulator(42, RepeatablePopulateMode.MINIMUM_ONE)
        val doc = populator.populate(eftiSchema)

        if (updateTestExpectations) {
            val updated = formatXml(doc)
            FileWriter(testResourceFile(expectationFilename)).use {
                it.write(updated)
                it.flush()
            }
            fail("Test expectations updated, run test again to verify")
        } else {
            val expected =
                InputStreamReader(classpathInputStream(expectationFilename)).use { it.readText() }

            assertAll(
                { assertThat(XmlUtil.validate(doc, EftiSchemas.javaCommonSchema), nullValue()) },
                {
                    // Use junit assertEquals because it formats the expected value better than hamcrest.
                    // Also, CompareMatcher.isSimilarTo does not work with consignment-common document, maybe it's too big?
                    assertEquals(
                        expected,
                        formatXml(doc),
                        "Populated document did not match the expected document, please update test expectations with: ./gradlew updateTestExpectations",
                    )
                },
            )
        }
    }

    @Test
    @Tag("expectation-update")
    fun `should apply overrides in order`() {
        val expectationFilename = "override-expected.xml"
        val seed = 23L
        val repeatableMode = RepeatablePopulateMode.MINIMUM_ONE

        val overrides = listOf(
            "consignment/deliveryEvent/actualOccurrenceDateTime" to "000000000000+0000",
            "/consignment/mainCarriageTransportMovement/modeCode" to "8",
        )
            .map { (expression, value) ->
                expression to EftiDomPopulator.TextContentOverride.tryToParse(expression, value)
            }
            .onEach { (expression, parsed) -> requireNotNull(parsed) { """Could not parse "$expression"""" } }
            .mapNotNull(Pair<String, EftiDomPopulator.TextContentOverride?>::second)

        val populator = EftiDomPopulator(seed, repeatableMode)
        val doc = populator.populate(consignmentIdentifierSchema, overrides, namespaceAware = false)

        if (updateTestExpectations) {
            val updated = formatXml(doc)
            FileWriter(testResourceFile(expectationFilename)).use {
                it.write(updated)
                it.flush()
            }
            fail("Test expectations updated, run test again to verify")
        } else {
            val expected =
                InputStreamReader(classpathInputStream(expectationFilename)).use { it.readText() }

            assertAll(
                { assertThat(XmlUtil.validate(doc, EftiSchemas.javaIdentifiersSchema), nullValue()) },
                {
                    // Use junit assertEquals because it formats the expected value better than hamcrest.
                    assertEquals(
                        expected,
                        formatXml(doc),
                        "Populated document did not match the expected document, please update test expectations with: ./gradlew updateTestExpectations",
                    )
                },
            )
        }
    }

    companion object {
        data class PopulateTestCase(
            val schemaVariant: String,
            val seed: Long,
            val repeatablePopulateMode: RepeatablePopulateMode,
            val eftiSchema: XmlSchemaElement,
            val javaSchema: Schema,
        ) {
            override fun toString(): String =
                """schemaVariant=$schemaVariant, seed=$seed, repeatablePopulateMode=$repeatablePopulateMode"""
        }

        @JvmStatic
        fun populateTestCases(): java.util.stream.Stream<PopulateTestCase> =
            populateTestCasesForVariant("identifier").plus(populateTestCasesForVariant("common")).asStream()

        private fun populateTestCasesForVariant(schemaVariant: String): Sequence<PopulateTestCase> {
            val (javaSchema, eftiSchema) = when (schemaVariant) {
                "common" -> EftiSchemas.javaCommonSchema to consignmentCommonSchema
                "identifier" -> EftiSchemas.javaIdentifiersSchema to consignmentIdentifierSchema
                else -> throw IllegalArgumentException(schemaVariant)
            }

            return (1..100).asSequence().map { seed ->
                PopulateTestCase(
                    schemaVariant = schemaVariant,
                    seed = seed.toLong(),
                    repeatablePopulateMode = when (seed % 3) {
                        0 -> RepeatablePopulateMode.RANDOM
                        1 -> RepeatablePopulateMode.MINIMUM_ONE
                        2 -> RepeatablePopulateMode.EXACTLY_ONE
                        else -> RepeatablePopulateMode.RANDOM
                    },
                    eftiSchema = eftiSchema,
                    javaSchema = javaSchema,
                )
            }
        }

        private fun formatXml(doc: Document): String = serializeToString(doc, prettyPrint = true)

        private fun classpathInputStream(filename: String): InputStream =
            checkNotNull(EftiDomPopulatorTest::class.java.getResourceAsStream(filename)) {
                "Could not open $filename"
            }

        private fun testResourceFile(filename: String): File {
            val packagePath = EftiDomPopulatorTest::class.java.packageName.replace(".", "/")
            return File("""src/test/resources/$packagePath/$filename""")
        }
    }
}
