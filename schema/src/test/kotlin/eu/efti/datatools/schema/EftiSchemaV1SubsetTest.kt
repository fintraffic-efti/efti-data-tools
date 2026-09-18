package eu.efti.datatools.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The v1 message schemas declare no eFTI subsets of their own, they are read from the eFTI XM SubMap schema and
 * matched by `eFTI_ID`, see [EftiSchemaId.subsetSource].
 */
class EftiSchemaV1SubsetTest {
    @Test
    fun `should find the subsets that the SubMap schema declares`() {
        val subsetIds = TestSchemas.cmdsResponseV1.subsetIds

        assertEquals(EXPECTED_SUBSET_COUNT, subsetIds.size)
        assertTrue(SubsetId("EU01") in subsetIds)
    }

    /**
     * Every element that the v1 schema annotates with an `eFTI_ID` must be found in the SubMap schema, otherwise the
     * element would silently be dropped from every subset. This guards the join between the two schemas: if a future
     * schema update breaks it, this fails instead of quietly producing documents with missing elements.
     */
    @Test
    fun `should resolve subsets for every element that has an efti id`() {
        val withEftiId = elements(TestSchemas.cmdsResponseV1.subsetAwareSchema).filter { it.eftiId != null }
        val withoutSubsets = withEftiId.filter { it.subsets.isEmpty() }

        assertEquals(
            emptyList<String>(),
            withoutSubsets.map { "${it.name.localPart} (${it.eftiId})" }.distinct().sorted(),
            "every element with an eFTI_ID should get subsets from the SubMap schema",
        )
        assertTrue(withEftiId.isNotEmpty())
    }

    private fun elements(root: XmlSchemaElement): List<XmlSchemaElement> =
        listOf(root) + root.children.flatMap(::elements)

    companion object {
        private const val EXPECTED_SUBSET_COUNT = 183
    }
}
