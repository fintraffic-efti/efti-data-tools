package eu.efti.datatools.schema

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

class XmlUtilValidationTest {
    private val schema: Schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(
        StreamSource(
            StringReader(
                """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="child" maxOccurs="unbounded">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="code" type="countryCode"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                    <xs:simpleType name="countryCode">
                        <xs:restriction base="xs:token">
                            <xs:length value="2"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """.trimIndent(),
            ),
        ),
    )

    @Test
    fun `should report no errors for a valid document`() {
        val doc = XmlUtil.deserializeToDocument("<root><child><code>FI</code></child></root>")

        assertThat(XmlUtil.validationErrors(doc, schema), hasSize(0))
        assertThat(XmlUtil.validate(doc, schema), nullValue())
    }

    @Test
    fun `should report the path of the element that is not valid`() {
        val doc = XmlUtil.deserializeToDocument(
            "<root><child><code>FI</code></child><child><code>y</code></child></root>",
        )

        val error = XmlUtil.validationErrors(doc, schema).first()

        // Without the path, an error about a value of a shared type does not say which element to look at.
        assertThat(error.path, equalTo("/root/child[2]/code"))
        assertThat(error.message, containsString("is not facet-valid"))
        assertThat(error.toString(), containsString("/root/child[2]/code: cvc-length-valid"))
    }

    @Test
    fun `should not use a position when an element has no siblings of the same name`() {
        val doc = XmlUtil.deserializeToDocument("<root><child><code>y</code></child></root>")

        assertThat(XmlUtil.validationErrors(doc, schema).first().path, equalTo("/root/child/code"))
    }

    @Test
    fun `should report every invalid element and not only the first one`() {
        val doc = XmlUtil.deserializeToDocument(
            "<root><child><code>y</code></child><child><code>z</code></child></root>",
        )

        val paths = XmlUtil.validationErrors(doc, schema)
            .filter { it.message.contains("cvc-length-valid") }
            .map { it.path }

        assertThat(paths, contains("/root/child[1]/code", "/root/child[2]/code"))
    }

    @Test
    fun `should include the paths in the message of validate`() {
        val doc = XmlUtil.deserializeToDocument("<root><child><code>y</code></child></root>")

        assertThat(checkNotNull(XmlUtil.validate(doc, schema)), containsString("/root/child/code: "))
    }

    @Test
    fun `should limit the number of reported errors`() {
        val children = (1..20).joinToString("") { "<child><code>y</code></child>" }
        val doc = XmlUtil.deserializeToDocument("<root>$children</root>")

        val message = checkNotNull(XmlUtil.validate(doc, schema))

        assertThat(message.lines(), hasSize(11))
        assertThat(message, containsString("more validation errors"))
    }
}
