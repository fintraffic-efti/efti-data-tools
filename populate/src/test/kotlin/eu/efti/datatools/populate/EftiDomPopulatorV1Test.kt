package eu.efti.datatools.populate

import eu.efti.datatools.schema.TestSchemas
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.asIterable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Tests for populating documents of the v1 eFTI schemas.
 */
class EftiDomPopulatorV1Test {
    private val seed = 42L
    private val uuidV4Regex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    @Test
    fun `should populate a valid v1 document`() {
        assertThat(XmlUtil.validate(populate(), TestSchemas.cmdsResponseV1.javaSchema), nullValue())
    }

    @Test
    fun `should populate a v1 document`() {
        val doc = populate()

        assertAll(
            { assertThat(doc.documentElement.localName, equalTo("FTI010GetCmdsResponse")) },
            {
                assertThat(
                    doc.documentElement.namespaceURI,
                    equalTo("urn:eu:move:eFTI:data:standard:FTI010GetCmdsResponse:1"),
                )
            },
            {
                assertThat(
                    "the consignment content is populated",
                    childElementNames(doc.documentElement),
                    hasItem("SpecifiedSupplyChainConsignment"),
                )
            },
            { assertThat(elements(doc).size, greaterThan(100)) },
        )
    }

    @Test
    fun `should populate a v1 document across several namespaces`() {
        val allElements = elements(populate())
        val namespaces = allElements.map { it.namespaceURI }.toSet()

        assertAll(
            {
                assertThat(
                    namespaces,
                    hasItem("urn:eu:move:eFTI:data:standard:FTI010GetCmdsResponse:1"),
                )
            },
            {
                assertThat(
                    namespaces,
                    hasItem("urn:eu:move:eFTI:data:standard:ReusableAggregateBusinessInformationEntity:34"),
                )
            },
            {
                assertThat(
                    "no element is left without a namespace",
                    allElements.count { it.namespaceURI == null },
                    equalTo(0),
                )
            },
        )
    }

    @Test
    fun `should restore all namespaces when overrides are applied without namespace awareness`() {
        // This is the code path that the command line application uses: namespaces are removed so that the xpath
        // expressions of the overrides can ignore them, and restored afterwards. A v1 document spans several
        // namespaces, so they cannot be restored by declaring a single namespace on the document element.
        val withOverrides = EftiDomPopulator(TestSchemas.cmdsResponseV1, seed, RepeatablePopulateMode.EXACTLY_ONE)
            .populate(
                overrides = listOf(
                    checkNotNull(
                        EftiDomPopulator.TextContentOverride.tryToParse(
                            "/FTI010GetCmdsResponse/ExchangedDocument/ID",
                            "overridden-id",
                        ),
                    ),
                ),
                namespaceAware = false,
            )

        val ids = elements(withOverrides).filter { it.localName == "ID" }

        assertAll(
            {
                assertThat(
                    "namespaces of the whole document are restored",
                    elements(withOverrides).map { it.namespaceURI }.toSet().size,
                    greaterThan(1),
                )
            },
            {
                assertThat(
                    "no element is left without a namespace",
                    elements(withOverrides).count { it.namespaceURI == null },
                    equalTo(0),
                )
            },
            { assertThat("the override is applied", ids.map { it.textContent }, hasItem("overridden-id")) },
        )
    }

    @Test
    fun `should generate the format attribute of a v1 date time from the schema enumeration`() {
        // The v1 schemas declare the "format" attribute as a restricted type that is named after the primitive it
        // restricts ("string") but only allows enumerated values. The enumeration must win over the generic
        // string generator, which would produce an arbitrary token. There are several such types, each allowing a
        // different set of date/time format codes.
        val dateTimeStrings = elements(populate()).filter { it.localName == "DateTimeString" }
        val allowedFormats = setOf("102", "203", "205", "207")

        assertAll(
            { assertThat("the document contains date/time elements", dateTimeStrings.size, greaterThan(0)) },
            {
                assertThat(
                    "every generated format is one of the codes that the schema allows",
                    dateTimeStrings.map { it.getAttribute("format") }.filterNot { it in allowedFormats },
                    empty(),
                )
            },
            {
                assertThat(
                    "the content of a date/time element is generated",
                    dateTimeStrings.map { it.textContent.length }.toSet(),
                    everyItem(greaterThan(0)),
                )
            },
        )
    }

    @Test
    fun `should generate a uuid for an id whose schemeID is fixed to the RFC 9562 version 4 scheme`() {
        // The v1 schemas do not model these ids as anything more specific than a token of at most 36 characters.
        // The fixed "schemeID" attribute is what tells that the content must be a version 4 UUID.
        val uuidIds = elements(populate())
            .filter { it.getAttribute("schemeID") == "RFC 9562-4" }

        assertAll(
            { assertThat("the document contains such ids", uuidIds.size, greaterThan(0)) },
            {
                assertThat(
                    "every one of them holds a version 4 uuid",
                    uuidIds.map { it.textContent }.filterNot { it.matches(uuidV4Regex) },
                    empty(),
                )
            },
        )
    }

    @Test
    fun `should produce identical documents for identical seeds`() {
        assertThat(
            XmlUtil.serializeToString(populate()),
            equalTo(XmlUtil.serializeToString(populate())),
        )
    }

    private fun populate(): Document =
        EftiDomPopulator(TestSchemas.cmdsResponseV1, seed, RepeatablePopulateMode.EXACTLY_ONE).populate()

    private fun childElementNames(element: Element): List<String> =
        element.childNodes.asIterable().filterIsInstance<Element>().map { it.localName }

    private fun elements(doc: Document): List<Element> =
        doc.getElementsByTagName("*").asIterable().filterIsInstance<Element>()
}
