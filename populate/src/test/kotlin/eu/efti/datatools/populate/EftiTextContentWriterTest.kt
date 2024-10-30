package eu.efti.datatools.populate

import eu.efti.datatools.populate.EftiTextContentWriter.setTextContent
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xmlunit.matchers.CompareMatcher.isSimilarTo
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random
import kotlin.streams.asStream

class EftiTextContentWriterTest {
    @ParameterizedTest
    @MethodSource("testCasesSetTextContent")
    fun `should replace text content with various XPaths and documents`(case: TestCase) {
        val doc = tryDeserializeToDocument(case.original, namespaceAware = false)
        setTextContent(doc, case.xpath, case.value)

        assertThat(doc, isSimilarTo(case.expected))
    }

    companion object {
        data class TestCase(val xpath: String, val value: String, val original: String, val expected: String)

        @JvmStatic
        fun testCasesSetTextContent(): java.util.stream.Stream<TestCase> =
            sequenceOf(
                TestCase(
                    xpath = "root/child",
                    value = "new-value",
                    original = """
                        <root>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>
                        </root>
                    """.trimIndent(),
                ),
                TestCase(
                    xpath = "root/child",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>
                          <child>new-value</child>
                        </root>
                    """.trimIndent(),
                ),
                TestCase(
                    xpath = "root/child/text()",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>
                          <child>new-value</child>
                        </root>
                    """.trimIndent(),
                ),
                TestCase(
                    xpath = "root/child/text()",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                          <child>second</child>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>
                          <child>new-value</child>
                          <child>new-value</child>
                        </root>
                    """.trimIndent(),
                ),
                TestCase(
                    xpath = "root/child[2]",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                          <child>second</child>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>
                          <child>first</child>
                          <child>new-value</child>
                        </root>
                    """.trimIndent(),
                ),
                TestCase(
                    xpath = "root/child/@attr2",
                    value = "new-value",
                    original = """
                        <root>
                          <child attr1="11" attr2="12">first</child>
                          <child attr1="21" attr2="22">second</child>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>
                          <child attr1="11" attr2="new-value">first</child>
                          <child attr1="21" attr2="new-value">second</child>
                        </root>
                    """.trimIndent(),
                ),
                TestCase(
                    xpath = "root/child[@attr='11']/@attr",
                    value = "new-value",
                    original = """
                        <root>
                          <child attr="11">first</child>
                          <child attr="21">second</child>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>
                          <child attr="new-value">first</child>
                          <child attr="21">second</child>
                        </root>
                    """.trimIndent(),
                ),
                TestCase(
                    xpath = "root",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                          <child>second</child>
                        </root>
                    """.trimIndent(),
                    expected = """
                        <root>new-value</root>
                    """.trimIndent(),
                ),
                "random-value-${Random.nextLong()}".let { value ->
                    TestCase(
                        xpath = "root/child",
                        value = value,
                        original = """
                            <root>
                              <child>first</child>
                              <child>second</child>
                            </root>
                        """.trimIndent(),
                        expected = """
                            <root>
                              <child>$value</child>
                              <child>$value</child>
                            </root>
                        """.trimIndent(),
                    )
                },
            ).asStream()


        fun tryDeserializeToDocument(xml: String, namespaceAware: Boolean = true): Document = try {
            val factory = DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = namespaceAware }
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(xml)))
        } catch (e: SAXException) {
            throw IllegalArgumentException("Could not parse document:\n$xml", e)
        }
    }
}