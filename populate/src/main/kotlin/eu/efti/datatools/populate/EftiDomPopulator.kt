package eu.efti.datatools.populate

import eu.efti.datatools.schema.XmlSchemaElement
import eu.efti.datatools.schema.XmlSchemaElement.XmlName
import eu.efti.datatools.schema.XmlSchemaElement.XmlType
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.bootstrap.DOMImplementationRegistry
import org.w3c.dom.ls.DOMImplementationLS
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory
import kotlin.math.max
import kotlin.math.min

typealias XmlValueGenerator = (valuePath: ValuePath, repeatIndex: Int, type: XmlType) -> String

enum class RepeatablePopulateMode {
    RANDOM,
    MINIMUM_ONE,
    EXACTLY_ONE,
}

@Suppress("detekt:MagicNumber")
class EftiDomPopulator(seed: Long, private val repeatableMode: RepeatablePopulateMode = RepeatablePopulateMode.RANDOM) {
    data class TextContentOverride(val xpath: XPathRawAndCompiled, val value: String) {
        data class XPathRawAndCompiled(val raw: String, val compiled: XPathExpression) {
            companion object {
                private val xpathFactory = XPathFactory.newInstance()

                fun tryToParse(expression: String): XPathRawAndCompiled? {
                    val xpath = xpathFactory.newXPath()
                    return try {
                        XPathRawAndCompiled(expression, xpath.compile(expression))
                    } catch (e: XPathExpressionException) {
                        null
                    }
                }
            }
        }

        companion object {
            fun tryToParse(expression: String, value: String): TextContentOverride? =
                XPathRawAndCompiled.tryToParse(expression)?.let { TextContentOverride(it, value) }
        }
    }

    interface SchemaValueMatcher {
        fun match(name: XmlName, type: XmlType): Boolean
    }

    data class ValueNameMatcher(val localPart: String) : SchemaValueMatcher {
        override fun match(name: XmlName, type: XmlType) = name.localPart == localPart
    }

    data class ValueTypeMatcher(val typeLocalPart: String) : SchemaValueMatcher {
        override fun match(name: XmlName, type: XmlType) =
            type.name.localPart == typeLocalPart
    }

    object EnumTypeMatcher : SchemaValueMatcher {
        override fun match(name: XmlName, type: XmlType) =
            type.enumerationValues.isNotEmpty()
    }

    private val gen = EftiValueGeneratorFactory(seed)

    private val dateTimeFormatter205 = DateTimeFormatter.ofPattern("uuuuMMddHHmmxx")

    private val enumerationGenerator: XmlValueGenerator = { valuePath, _, type ->
        gen.forPath(valuePath).nextChoice(
            type.enumerationValues,
        )
    }

    private val generators: List<Pair<SchemaValueMatcher, XmlValueGenerator>> = listOf(
        ValueNameMatcher("schemeAgencyId") to noArgsGenerator { it.nextToken() },
        ValueNameMatcher("sequenceNumber") to repeatIndexGenerator { repeatIndex -> repeatIndex.toString() },
        ValueTypeMatcher("base64Binary") to noArgsGenerator {
            Base64.getEncoder().encodeToString(it.nextToken().toByteArray())
        },
        ValueTypeMatcher("boolean") to noArgsGenerator { it.nextBoolean().toString() },
        ValueTypeMatcher("DateTimeFormat") to noArgsGenerator {
            // Note: for simplicity, always use the same format
            "205"
        },
        ValueTypeMatcher("DateTime") to noArgsGenerator {
            // Note: for simplicity, always use the same format
            dateTimeFormatter205.format(OffsetDateTime.ofInstant(it.nextInstant(), ZoneOffset.UTC))
        },
        ValueTypeMatcher("decimal") to noArgsGenerator { String.format(Locale.UK, "%.2f", it.nextDouble(0.0, 10.0)) },
        ValueTypeMatcher("Identifier17") to noArgsGenerator { it.nextToken() },
        ValueTypeMatcher("integer") to noArgsGenerator { it.nextInt(1000, 9999).toString() },
        ValueTypeMatcher("string") to noArgsGenerator { it.nextToken(4) },
        EnumTypeMatcher to enumerationGenerator,
    )

    fun populate(
        schema: XmlSchemaElement,
        overrides: List<TextContentOverride> = emptyList(),
        namespaceAware: Boolean = true
    ): Document {
        val doc: Document = newDocument()

        val element = doc.appendChild(doc.createElementNS(schema.name.namespaceURI, schema.name.localPart))
        schema.children.forEach { child ->
            populateElement(doc, element, ValuePath(emptyList()).append(schema), child)
        }

        return applyOverrides(schema, doc, overrides, namespaceAware)
    }

    private fun applyOverrides(
        schema: XmlSchemaElement,
        originalDoc: Document,
        overrides: List<TextContentOverride>,
        namespaceAware: Boolean
    ): Document {
        return if (overrides.isNotEmpty()) {
            val overridesDoc = if (!namespaceAware) {
                // Java xpath implementation is strict about namespaces. If we want to ignore default namespace in
                // xpath expressions, we need to remove namespaces altogether from the document...
                removeNamespaces(originalDoc)
            } else {
                originalDoc
            }

            overrides.forEach { override ->
                EftiTextContentWriter.setTextContent(overridesDoc, override.xpath.compiled, override.value)
            }

            if (!namespaceAware) {
                // ...however, we want to produce documents that pass validation. Therefore, we need to restore
                // the namespace declaration.
                restoreEftiNamespace(schema, overridesDoc)
            } else {
                overridesDoc
            }
        } else {
            originalDoc
        }
    }

    private fun noArgsGenerator(block: (gen: EftiValueGeneratorFactory.EftiValueGenerator) -> String): XmlValueGenerator =
        { valuePath, _, _ -> block(gen.forPath(valuePath)) }

    private fun repeatIndexGenerator(block: (Int) -> String): XmlValueGenerator =
        { _, repeatIndex, _ -> block(repeatIndex) }

    private fun populateElement(doc: Document, parent: Node, parentPath: ValuePath, schema: XmlSchemaElement) {
        val currentPath = parentPath.append(schema)

        val repeatGenerator = gen.forPath(currentPath)
        val repeatRange = when (repeatableMode) {
            RepeatablePopulateMode.RANDOM ->
                schema.cardinality.min to repeatGenerator.nextLong(
                    0,
                    min(schema.cardinality.max ?: 3, 3)
                ) + max(schema.cardinality.min, 1)

            RepeatablePopulateMode.MINIMUM_ONE ->
                max(schema.cardinality.min, 1) to repeatGenerator.nextLong(
                    0,
                    min(schema.cardinality.max ?: 3, 3)
                ) + max(schema.cardinality.min, 2)

            RepeatablePopulateMode.EXACTLY_ONE -> 1 to 2
        }
        val count = repeatGenerator.nextLong(
            startInclusive = repeatRange.first.toLong(),
            endExclusive = repeatRange.second.toLong()
        )

        repeat(count.toInt()) { repeatIndex ->
            val element = parent.appendChild(doc.createElementNS(schema.name.namespaceURI, schema.name.localPart))

            schema.type.attributes.forEach { schemaAttribute ->
                val attribute =
                    doc.createAttributeNS(schemaAttribute.name.namespaceURI, schemaAttribute.name.localPart)

                if (schemaAttribute.type.isTextContentType) {
                    val generator = findMostSpecificGenerator(schemaAttribute.name, schemaAttribute.type)
                    attribute.value = generator(currentPath.append(repeatIndex).append(schemaAttribute), 0, schemaAttribute.type)
                }
                element.attributes.setNamedItem(attribute)
            }

            schema.children.forEach { child ->
                populateElement(doc, element, currentPath.append(repeatIndex), child)
            }

            if (schema.type.isTextContentType) {
                val generator = findMostSpecificGenerator(schema.name, schema.type)
                element.textContent = generator(currentPath.append(repeatIndex), repeatIndex, schema.type)
            }
        }
    }

    private fun findMostSpecificGenerator(name: XmlName, type: XmlType): XmlValueGenerator =
        requireNotNull(
            sequenceOf(type).plus(type.baseTypes)
                .firstNotNullOfOrNull { t ->
                    generators.firstOrNull { it.first.match(name, t) }?.second
                },
        ) {
            "No generator found for: $name $type"
        }

    companion object {
        private val factory = DocumentBuilderFactory.newInstance()

        private fun newDocument(): Document {
            val builder = factory.newDocumentBuilder()
            val doc: Document = builder.newDocument()
            return doc
        }

        private fun restoreEftiNamespace(
            schema: XmlSchemaElement,
            originalDoc: Document
        ): Document {
            val doc: Document = newDocument()

            // Create new root in the desired namespace
            val root = doc.appendChild(doc.createElementNS(schema.name.namespaceURI, schema.name.localPart))

            // Import children from the original document
            originalDoc.firstChild.childNodes.asIterable().forEach { child ->
                val importedChild = doc.importNode(child, /* deep */ true)
                root.appendChild(importedChild)
            }

            // Another serialization round is required to convert all elements to the desired namespace
            return tryDeserializeToDocument(serialize(doc), namespaceAware = true)
        }

        private fun removeNamespaces(doc: Document): Document {
            // Note: a clumsy way of making unaware of namespaces
            return tryDeserializeToDocument(serialize(doc), namespaceAware = false)
        }

        private fun NodeList.asIterable(): Iterable<Node> =
            (0 until this.length).asSequence().map { this.item(it) }.asIterable()

        private fun tryDeserializeToDocument(xml: String, namespaceAware: Boolean = true): Document = try {
            val factory = DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = namespaceAware }
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(xml)))
        } catch (e: SAXException) {
            throw IllegalArgumentException("Could not parse document:\n$xml", e)
        }

        private fun serialize(doc: Document): String {
            val registry = DOMImplementationRegistry.newInstance()
            val domImplLS = registry.getDOMImplementation("LS") as DOMImplementationLS

            val lsSerializer = domImplLS.createLSSerializer()
            val domConfig = lsSerializer.domConfig
            domConfig.setParameter("format-pretty-print", true)

            val byteArrayOutputStream = ByteArrayOutputStream()
            val lsOutput = domImplLS.createLSOutput()
            lsOutput.encoding = "UTF-8"
            lsOutput.byteStream = byteArrayOutputStream

            lsSerializer.write(doc, lsOutput)
            return byteArrayOutputStream.toString(Charsets.UTF_8)
        }
    }
}
