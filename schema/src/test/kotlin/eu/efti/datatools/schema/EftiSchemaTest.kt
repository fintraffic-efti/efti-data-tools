package eu.efti.datatools.schema

import eu.efti.datatools.schema.EftiSchemas.consignmentCommonSchema
import eu.efti.datatools.schema.EftiSchemas.consignmentIdentifierSchema
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class EftiSchemaTest {
    @Test
    fun `should parse identifier schema to XmlSchemaElement`() {
        val element = consignmentIdentifierSchema

        // Do some minimal assertions.
        assertAll(
            { assertThat(element.name.localPart, equalTo("consignment")) },
            { assertThat(element.children, hasSize(4)) },
        )
    }

    @Test
    fun `should parse common schema to XmlSchemaElement`() {
        val element = consignmentCommonSchema

        // Do some minimal assertions.
        val applicableServiceCharge =
            checkNotNull(element.children.find { it.name.localPart == "applicableServiceCharge" })

        assertAll(
            { assertThat(element.name.localPart, equalTo("consignment")) },
            { assertThat(element.children, hasSize(43)) },
            { assertThat(applicableServiceCharge.subsets, hasSize(24)) },
            { assertThat(applicableServiceCharge.subsets, hasItem(XmlSchemaElement.SubsetId("LT01"))) },
        )
    }
}
