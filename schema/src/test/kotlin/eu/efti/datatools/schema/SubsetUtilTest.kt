package eu.efti.datatools.schema

import eu.efti.datatools.schema.SubsetUtil.dropNodesNotInSubsets
import eu.efti.datatools.schema.XmlSchemaElement.SubsetId
import eu.efti.datatools.schema.XmlUtil.deserializeToDocument
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.xmlunit.matchers.CompareMatcher.isSimilarTo
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.streams.asStream

class SubsetUtilTest {
    @Test
    fun `should not accept empty subsets parameter`() {
        val doc = deserializeToDocument(
            """
            <root xmlns="http://example.com/test">
                <a>a</a>
            </root>
        """.removeExtraWhitespace()
        )
        val schema = xmlSchemaElement(
            "root",
            subsetIds = emptySet(),
            children = listOf(
                xmlSchemaElement(localName = "a", subsetIds = emptySet(), children = emptyList())
            ),
        )

        assertAll(
            { dropNodesNotInSubsets(setOf(SubsetId(randomAsciiLetterString())), schema, doc.firstChild) },
            {
                assertThrows<IllegalArgumentException> {
                    dropNodesNotInSubsets(emptySet(), schema, doc.firstChild)
                }
            },
        )
    }

    @ParameterizedTest
    @MethodSource("testCasesSubsetFiltering")
    fun `should filter various subsets`(case: TestCase) {
        val doc = deserializeToDocument(case.xml.removeExtraWhitespace())

        dropNodesNotInSubsets(case.requestedSubsetIds.map(::SubsetId).toSet(), case.schema, doc.firstChild)

        assertThat(
            doc,
            isSimilarTo(case.expectedXml.removeExtraWhitespace())
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["this isn't a subset",
            "BE0", // Partial subset id
            "be03a" // Valid subset id but in lowercase
        ]
    )
    fun `commonSchemaHasSubset should return false for subset that does not exist`(invalidSubsetId: String) {
        assertThat(
            SubsetUtil.commonSchemaHasSubset(SubsetId(invalidSubsetId)),
            equalTo(false)
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["BE03a", "SI03"])
    fun `commonSchemaHasSubset should return true for a subset that does exist`(subsetId: String) {
        assertThat(
            SubsetUtil.commonSchemaHasSubset(SubsetId(subsetId)),
            equalTo(true)
        )
    }

    companion object {
        data class TestCase(
            val xml: String,
            val expectedXml: String,
            val schema: XmlSchemaElement,
            val requestedSubsetIds: Set<String>
        )

        @JvmStatic
        fun testCasesSubsetFiltering(): Stream<TestCase> =
            sequenceOf(
                TestCase(
                    xml = """
                        <root xmlns="http://example.com/test">
                        </root>
                    """,
                    expectedXml = """
                        <root xmlns="http://example.com/test">
                        </root>
                    """,
                    schema = xmlSchemaElement(
                        "root",
                        subsetIds = emptySet(),
                        children = emptyList(),
                    ),
                    requestedSubsetIds = setOf("x")
                ),
                TestCase(
                    xml = """
                        <root xmlns="http://example.com/test">
                            <a>a</a>
                        </root>
                    """,
                    expectedXml = """
                        <root xmlns="http://example.com/test">
                            <a>a</a>
                        </root>
                    """,
                    schema = xmlSchemaElement(
                        "root",
                        subsetIds = emptySet(),
                        children = listOf(
                            xmlSchemaElement(localName = "a", subsetIds = setOf("x"), children = emptyList())
                        ),
                    ),
                    requestedSubsetIds = setOf("x")
                ),
                TestCase(
                    xml = """
                        <root xmlns="http://example.com/test">
                            <a>a</a>
                        </root>
                    """,
                    expectedXml = """
                        <root xmlns="http://example.com/test">
                            <a>a</a>
                        </root>
                    """,
                    schema = xmlSchemaElement(
                        "root",
                        subsetIds = emptySet(),
                        children = listOf(
                            xmlSchemaElement(localName = "a", subsetIds = setOf("x", "y"), children = emptyList())
                        ),
                    ),
                    requestedSubsetIds = setOf("y", "z")
                ),
                TestCase(
                    xml = """
                        <root xmlns="http://example.com/test">
                            <a>a</a>
                        </root>
                    """,
                    expectedXml = """
                        <root xmlns="http://example.com/test">
                        </root>
                    """,
                    schema = xmlSchemaElement(
                        "root",
                        subsetIds = emptySet(),
                        children = listOf(
                            xmlSchemaElement(localName = "a", subsetIds = setOf("x", "y"), children = emptyList())
                        ),
                    ),
                    requestedSubsetIds = setOf("z")
                ),
                TestCase(
                    xml = """
                        <root xmlns="http://example.com/test">
                            <a>a</a>
                        </root>
                    """,
                    expectedXml = """
                        <root xmlns="http://example.com/test">
                        </root>
                    """,
                    schema = xmlSchemaElement(
                        "root",
                        subsetIds = emptySet(),
                        children = listOf(
                            xmlSchemaElement(localName = "a", subsetIds = emptySet(), children = emptyList())
                        ),
                    ),
                    requestedSubsetIds = setOf("z")
                ),
                TestCase(
                    xml = """
                        <root xmlns="http://example.com/test">
                            <a>a1</a>
                            <a>a2</a>
                            <b>
                                <c>c</c>
                                <d>
                                    <e>e</e>
                                </d>
                            </b>
                        </root>
                    """,
                    expectedXml = """
                        <root xmlns="http://example.com/test">
                            <b>
                                <c>c</c>
                            </b>
                        </root>
                    """,
                    schema = xmlSchemaElement(
                        "root",
                        subsetIds = emptySet(),
                        children = listOf(
                            xmlSchemaElement(localName = "a", subsetIds = setOf("y"), children = emptyList()),
                            xmlSchemaElement(
                                localName = "b", subsetIds = setOf("x", "y"), children = listOf(
                                    xmlSchemaElement(
                                        localName = "c",
                                        subsetIds = setOf("y", "z"),
                                        children = emptyList()
                                    ),
                                    xmlSchemaElement(
                                        localName = "d", subsetIds = setOf("y"), children = listOf(
                                            xmlSchemaElement(
                                                localName = "e",
                                                subsetIds = setOf("x", "z"),
                                                children = emptyList()
                                            ),
                                        )
                                    ),
                                )
                            ),
                        ),
                    ),
                    requestedSubsetIds = setOf("x", "z")
                ),
            ).asStream()


        private fun xmlSchemaElement(localName: String, subsetIds: Set<String>, children: List<XmlSchemaElement>) =
            XmlSchemaElement(
                testName(localName),
                XmlSchemaElement.XmlType(
                    // Type names do not matter in this context
                    testName(randomAsciiLetterString()),
                    isTextContentType = false
                ),
                // Cardinality does not matter in this context
                XmlSchemaElement.XmlCardinality(1, randomLong()),
                children = children,
                subsets = subsetIds.map(::SubsetId).toSet()
            )

        private fun testName(localPart: String) = XmlSchemaElement.XmlName("http://example.com/test", localPart)

        private fun String.removeExtraWhitespace(): String = this.lineSequence().map(String::trim).joinToString("")

        private fun randomAsciiLetter(): Char = "abcdefghijklmnopqrstuvwxyz".random()

        private fun randomAsciiLetterString(length: Int = 6): String =
            (1..length).joinToString("") { randomAsciiLetter().toString() }

        private fun randomLong(): Long = Random.nextLong(1, 10)
    }
}

