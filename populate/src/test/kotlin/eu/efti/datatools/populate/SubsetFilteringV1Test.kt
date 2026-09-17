package eu.efti.datatools.populate

import eu.efti.datatools.schema.SubsetId
import eu.efti.datatools.schema.TestSchemas
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.asIterable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * End to end tests for subset filtering of v1 documents.
 *
 * The v1 message schemas declare no eFTI subsets themselves, they are read from the eFTI XM SubMap schema, so these
 * tests are what actually prove that reading subsets from the separate schema produces usable documents.
 */
class SubsetFilteringV1Test {
    @Test
    fun `should drop elements that are not in the requested subset`() {
        val doc = populateValidDocument()
        val filtered = TestSchemas.commonV1.filterSubsets(doc, setOf(SubsetId("EU01")))

        assertAll(
            { assertThat(elementNames(filtered).size, greaterThan(0)) },
            { assertThat(elementNames(filtered).size, lessThan(elementNames(doc).size)) },
            // The document element and a few well known elements of EU01 must survive.
            { assertThat(filtered.documentElement.localName, equalTo("FTI010GetCmdsResponse")) },
            { assertThat(elementNames(filtered), hasItem("SpecifiedSupplyChainConsignment")) },
        )
    }

    /**
     * Elements without an `eFTI_ID`, such as the mandatory `DateTimeString` wrappers, are structural rather than data
     * carrying. They are not classified into subsets of their own and must be kept whenever their parent is kept,
     * otherwise the filtered document would not be valid.
     */
    @Test
    fun `should keep mandatory structural elements that carry no subsets of their own`() {
        val filtered = TestSchemas.commonV1.filterSubsets(populateValidDocument(), setOf(SubsetId("EU01")))

        val issueDateTime = elements(filtered).single { it.localName == "IssueDateTime" }

        assertThat(
            issueDateTime.childNodes.asIterable().filterIsInstance<Element>().map { it.localName },
            hasItem("DateTimeString"),
        )
    }

    @Test
    fun `should produce a valid document`() {
        val filtered = TestSchemas.commonV1.filterSubsets(populateValidDocument(), setOf(SubsetId("EU01")))

        assertThat(XmlUtil.validate(filtered, TestSchemas.commonV1.javaSchema), nullValue())
    }

    @Test
    fun `should produce different documents for different subsets`() {
        val doc = populateValidDocument()

        val eu01 = elementNames(TestSchemas.commonV1.filterSubsets(doc, setOf(SubsetId("EU01"))))
        val eu02 = elementNames(TestSchemas.commonV1.filterSubsets(doc, setOf(SubsetId("EU02"))))

        assertThat(eu01, not(equalTo(eu02)))
    }

    private fun populateValidDocument(): Document = EftiDomPopulator(
        TestSchemas.commonV1,
        SEED,
        RepeatablePopulateMode.EXACTLY_ONE,
    ).populate()

    private fun elements(doc: Document): List<Element> =
        doc.getElementsByTagName("*").asIterable().filterIsInstance<Element>()

    private fun elementNames(doc: Document): List<String> = elements(doc).map { it.localName }

    companion object {
        private const val SEED = 42L
    }
}
