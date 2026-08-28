package eu.efti.datatools.schema

import eu.efti.datatools.populate.EftiDomPopulator
import eu.efti.datatools.populate.RepeatablePopulateMode
import eu.efti.datatools.schema.XmlUtil.serializeToString
import eu.efti.datatools.schema.XmlUtil.validate
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Document
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import javax.xml.validation.Schema
import kotlin.random.asKotlinRandom
import kotlin.streams.asStream

class SubsetUtilIT {
    @ParameterizedTest
    @MethodSource("filteringTestCases")
    fun `should produce valid documents by filtering random subsets on random documents`(testCase: PopulateTestCase) {
        val populator = EftiDomPopulator(TestSchemas.schemas, testCase.seed, testCase.repeatablePopulateMode)
        val doc = populator.populate(testCase.schemaId)

        val filtered = testCase.filteringFunction(doc, testCase.requestedSubsets)
        val validationError = validate(filtered, testCase.javaSchema)

        val debugDiff = DiffBuilder.compare(Input.fromDocument(doc)).withTest(Input.fromDocument(filtered))
            .checkForSimilar()
            .ignoreWhitespace()
            .build()
        // Print some debugging info to build confidence on the test arrangement.
        println("""Original and filtered document differ: ${debugDiff.hasDifferences()}""")

        // Optimization: catch assertion error so that we can generate full error message lazily
        try {
            assertThat(validationError, nullValue())
        } catch (e: AssertionError) {
            fail(e.message + "\nDocument was:\n ${serializeToString(filtered, prettyPrint = true)}", e)
        }
    }

    companion object {
        data class PopulateTestCase(
            val schemaVariant: String,
            val seed: Long,
            val repeatablePopulateMode: RepeatablePopulateMode,
            val requestedSubsets: Set<SubsetId>,
            val schemaId: EftiSchemaId,
            val javaSchema: Schema,
            val filteringFunction: (doc: Document, subsets: Set<SubsetId>) -> Document,
        ) {
            override fun toString(): String =
                """schemaVariant=$schemaVariant, seed=$seed, repeatablePopulateMode=$repeatablePopulateMode, requestedSubsets=$requestedSubsets"""
        }

        private fun collectSubsets(schema: XmlSchemaElement): Set<SubsetId> =
            schema.subsets + schema.children.flatMap { childSchema ->
                collectSubsets(childSchema)
            }

        @JvmStatic
        fun filteringTestCases(): java.util.stream.Stream<PopulateTestCase> {
            val subsets = collectSubsets(TestSchemas.consignmentCommonSchema)
            return populateTestCasesForVariant(subsets, "common")
                .asStream()
        }

        private fun populateTestCasesForVariant(
            allSubsets: Set<SubsetId>,
            schemaVariant: String,
        ): Sequence<PopulateTestCase> {
            val filterCommon = { doc: Document, subsets: Set<SubsetId> ->
                TestSchemas.schemas.filterCommonSubsets(doc, subsets)
            }
            val (schemaId, filteringFunction) = when (schemaVariant) {
                "common" -> EftiSchemaId.CONSIGNMENT_COMMON to filterCommon
                else -> throw IllegalArgumentException(schemaVariant)
            }
            val javaSchema = TestSchemas.schemas.javaSchema(schemaId)

            return (1..20).asSequence().map { seed ->
                val random = java.util.Random(seed.toLong()).asKotlinRandom()

                PopulateTestCase(
                    schemaVariant = schemaVariant,
                    seed = seed.toLong(),
                    repeatablePopulateMode = when (seed % 3) {
                        0 -> RepeatablePopulateMode.RANDOM
                        1 -> RepeatablePopulateMode.MINIMUM_ONE
                        2 -> RepeatablePopulateMode.EXACTLY_ONE
                        else -> RepeatablePopulateMode.RANDOM
                    },
                    requestedSubsets = generateSequence { allSubsets.random(random) }.take(1 + random.nextInt(3))
                        .toSet(),
                    schemaId = schemaId,
                    javaSchema = javaSchema,
                    filteringFunction = filteringFunction,
                )
            }
        }
    }
}
