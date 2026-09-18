package eu.efti.datatools.schema

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * Tests for the v1 eFTI schemas. The v1 schemas differ from the v0 schemas in ways that matter to this library:
 * they use anonymous types, spread the document over several namespaces, and do not declare eFTI subsets.
 */
class EftiSchemaV1Test {
    @Test
    fun `should parse the v1 common schema to XmlSchemaElement`() {
        val element = TestSchemas.cmdsResponseV1.xmlSchema

        assertAll(
            { assertThat(element.name.localPart, equalTo("FTI010GetCmdsResponse")) },
            {
                assertThat(
                    element.name.namespaceURI,
                    equalTo("urn:eu:move:eFTI:data:standard:FTI010GetCmdsResponse:1"),
                )
            },
            { assertThat(element.children.map { it.name.localPart }, hasItem("SpecifiedSupplyChainConsignment")) },
            { assertThat(element.children, not(empty())) },
        )
    }

    @Test
    fun `should read the v1 java schema for validation`() {
        assertThat(TestSchemas.cmdsResponseV1.javaSchema, notNullValue())
    }

    @Test
    fun `v1 documents should span several namespaces`() {
        // The envelope is in the message namespace, but the consignment content comes from the reusable
        // components namespace.
        val consignment = checkNotNull(
            TestSchemas.cmdsResponseV1.xmlSchema.children.find {
                it.name.localPart == "SpecifiedSupplyChainConsignment"
            },
        )

        assertAll(
            {
                assertThat(
                    consignment.name.namespaceURI,
                    equalTo("urn:eu:move:eFTI:data:standard:FTI010GetCmdsResponse:1"),
                )
            },
            {
                assertThat(
                    consignment.children.map { it.name.namespaceURI }.toSet(),
                    hasItem("urn:eu:move:eFTI:data:standard:ReusableAggregateBusinessInformationEntity:34"),
                )
            },
        )
    }

    @Test
    fun `should parse anonymous types without a name`() {
        // The v1 schemas declare the type of DateTimeString inline, so the type has no name. The v0 schemas do not
        // use anonymous types at all.
        val dateTimeString = checkNotNull(
            findFirst(TestSchemas.cmdsResponseV1.xmlSchema) {
                it.name.localPart ==
                    "DateTimeString"
            },
        ) {
            "Expected to find a DateTimeString element in the v1 schema"
        }

        assertAll(
            { assertThat("anonymous type has no name", dateTimeString.type.name, nullValue()) },
            {
                assertThat(
                    "anonymous type still carries its attributes",
                    dateTimeString.type.attributes.map { it.name.localPart },
                    hasItem("format"),
                )
            },
            {
                assertThat(
                    "anonymous type still carries its base types",
                    dateTimeString.type.baseTypes,
                    not(empty()),
                )
            },
        )
    }

    @Test
    fun `v1 schema should declare the subsets that are read from the SubMap schema`() {
        assertAll(
            { assertThat(TestSchemas.cmdsResponseV1.subsetIds, not(empty())) },
            { assertThat(EftiSchemaId.CMDS_RESPONSE_V1.version, equalTo(EftiSchemaVersion.V1)) },
        )
    }

    private fun findFirst(
        element: XmlSchemaElement,
        predicate: (XmlSchemaElement) -> Boolean,
    ): XmlSchemaElement? = if (predicate(element)) {
        element
    } else {
        element.children.firstNotNullOfOrNull { child -> findFirst(child, predicate) }
    }
}
