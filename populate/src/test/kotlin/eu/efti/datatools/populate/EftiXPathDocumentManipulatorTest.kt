package eu.efti.datatools.populate

import eu.efti.datatools.populate.EftiXPathDocumentManipulator.deleteNode
import eu.efti.datatools.populate.EftiXPathDocumentManipulator.setTextContent
import eu.efti.datatools.schema.XmlUtil.deserializeToDocument
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.xmlunit.matchers.CompareMatcher.isSimilarTo
import kotlin.random.Random
import kotlin.streams.asStream

class EftiXPathDocumentManipulatorTest {
    @ParameterizedTest
    @MethodSource("testCasesDelete")
    fun `should delete nodes with various XPaths and documents`(case: CaseD) {
        val doc = deserializeToDocument(case.original, namespaceAware = false)
        deleteNode(doc, case.xpath)

        assertThat(doc, isSimilarTo(case.expected))
    }

    @ParameterizedTest
    @MethodSource("testCasesSetTextContent")
    fun `should replace text content with various XPaths and documents`(case: CaseR) {
        val doc = deserializeToDocument(case.original, namespaceAware = false)
        setTextContent(doc, case.xpath, case.value)

        assertThat(doc, isSimilarTo(case.expected))
    }

    companion object {
        data class CaseD(val xpath: String, val original: String, val expected: String)
        data class CaseR(val xpath: String, val value: String, val original: String, val expected: String)

        private fun String.normalize() = this.lines().joinToString("") { it.trim() }

        @JvmStatic
        fun testCasesDelete(): java.util.stream.Stream<CaseD> =
            sequenceOf(
                CaseD(
                    xpath = "root/child",
                    original = """
                        <root>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                        </root>
                    """.normalize(),
                ),
                CaseD(
                    xpath = "root/child",
                    original = """
                        <root>
                            <child>foo</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                        </root>
                    """.normalize(),
                ),
                CaseD(
                    xpath = "root/child",
                    original = """
                        <root>
                            <child/>
                            <child>
                                <grandchild/>
                            </child>
                            <other/>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                            <other/>
                        </root>
                    """.normalize(),
                ),
                CaseD(
                    xpath = "root/child/grandchild",
                    original = """
                        <root>
                            <child/>
                            <child>
                                <grandchild/>
                            </child>
                            <child>
                                <grandchild/>
                            </child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                            <child/>
                            <child/>
                            <child/>
                        </root>
                    """.normalize(),
                ),
            ).asStream()

        @JvmStatic
        fun testCasesSetTextContent(): java.util.stream.Stream<CaseR> =
            sequenceOf(
                CaseR(
                    xpath = "root/child",
                    value = "new-value",
                    original = """
                        <root>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                        </root>
                    """.normalize(),
                ),
                CaseR(
                    xpath = "root/child",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                          <child>new-value</child>
                        </root>
                    """.normalize(),
                ),
                CaseR(
                    xpath = "root/child/text()",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                          <child>new-value</child>
                        </root>
                    """.normalize(),
                ),
                CaseR(
                    xpath = "root/child/text()",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                          <child>second</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                          <child>new-value</child>
                          <child>new-value</child>
                        </root>
                    """.normalize(),
                ),
                CaseR(
                    xpath = "root/child[2]",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                          <child>second</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                          <child>first</child>
                          <child>new-value</child>
                        </root>
                    """.normalize(),
                ),
                CaseR(
                    xpath = "root/child/@attr2",
                    value = "new-value",
                    original = """
                        <root>
                          <child attr1="11" attr2="12">first</child>
                          <child attr1="21" attr2="22">second</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                          <child attr1="11" attr2="new-value">first</child>
                          <child attr1="21" attr2="new-value">second</child>
                        </root>
                    """.normalize(),
                ),
                CaseR(
                    xpath = "root/child[@attr='11']/@attr",
                    value = "new-value",
                    original = """
                        <root>
                          <child attr="11">first</child>
                          <child attr="21">second</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>
                          <child attr="new-value">first</child>
                          <child attr="21">second</child>
                        </root>
                    """.normalize(),
                ),
                CaseR(
                    xpath = "root",
                    value = "new-value",
                    original = """
                        <root>
                          <child>first</child>
                          <child>second</child>
                        </root>
                    """.normalize(),
                    expected = """
                        <root>new-value</root>
                    """.normalize(),
                ),
                "random-value-${Random.nextLong()}".let { value ->
                    CaseR(
                        xpath = "root/child",
                        value = value,
                        original = """
                            <root>
                              <child>first</child>
                              <child>second</child>
                            </root>
                        """.normalize(),
                        expected = """
                            <root>
                              <child>$value</child>
                              <child>$value</child>
                            </root>
                        """.normalize(),
                    )
                },
            ).asStream()
    }
}