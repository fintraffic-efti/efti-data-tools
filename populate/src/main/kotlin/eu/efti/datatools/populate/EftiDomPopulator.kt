package eu.efti.datatools.populate

import eu.efti.datatools.schema.EftiSchema
import eu.efti.datatools.schema.XmlSchemaElement
import eu.efti.datatools.schema.XmlSchemaElement.XmlName
import eu.efti.datatools.schema.XmlSchemaElement.XmlType
import eu.efti.datatools.schema.XmlUtil.asIterable
import eu.efti.datatools.schema.XmlUtil.deserializeToDocument
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
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
class EftiDomPopulator(
    private val schema: EftiSchema,
    seed: Long,
    private val repeatableMode: RepeatablePopulateMode = RepeatablePopulateMode.RANDOM,
) {
    data class XPathRawAndCompiled(val raw: String, val compiled: XPathExpression) {
        companion object {
            private val xpathFactory = XPathFactory.newInstance()

            fun tryToParse(expression: String): XPathRawAndCompiled? {
                val xpath = xpathFactory.newXPath()
                return try {
                    XPathRawAndCompiled(expression, xpath.compile(expression))
                } catch (@Suppress("detekt:SwallowedException") _: XPathExpressionException) {
                    null
                }
            }
        }
    }

    sealed interface Override

    data class DeleteNodeOverride(val xpath: XPathRawAndCompiled) : Override {
        companion object {
            fun tryToParse(expression: String): DeleteNodeOverride? =
                XPathRawAndCompiled.tryToParse(expression)?.let { DeleteNodeOverride(it) }
        }
    }

    data class TextContentOverride(val xpath: XPathRawAndCompiled, val value: String) : Override {
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

    data class ValueTypeMatcher(val typeLocalPart: String, val typeNamespace: String? = null) : SchemaValueMatcher {
        override fun match(name: XmlName, type: XmlType) =
            (typeNamespace == null || type.name?.namespaceURI == typeNamespace) &&
                type.name?.localPart == typeLocalPart
    }

    object EnumTypeMatcher : SchemaValueMatcher {
        override fun match(name: XmlName, type: XmlType) =
            type.enumerationValues.isNotEmpty()
    }

    /**
     * Matches an element whose type declares an attribute of the given name with the given fixed value. The fixed
     * attribute tells how the text content of the element is to be interpreted, so it also tells how that content
     * is to be generated. For example, an id element whose `schemeID` is fixed to "RFC 9562-4" must hold a UUID.
     */
    data class FixedAttributeValueMatcher(
        val attributeLocalPart: String,
        val attributeFixedValue: String,
    ) : SchemaValueMatcher {
        override fun match(name: XmlName, type: XmlType) =
            type.attributes.any {
                it.name.localPart == attributeLocalPart && it.fixedValue == attributeFixedValue
            }
    }

    private val gen = EftiValueGeneratorFactory(seed)

    /**
     * Value generators of the schema being populated, selected by its document element.
     */
    private val valueGenerators: ValueGeneratorSet = ValueGenerators(gen).forRootElement(schema.xmlSchema.name)

    /**
     * Populate a pseudo-random document of the schema of this populator.
     * @param overrides overrides to apply to the populated document
     * @param namespaceAware if false, xpath expressions of the overrides may ignore namespaces
     */
    @JvmOverloads
    fun populate(
        overrides: List<Override> = emptyList(),
        namespaceAware: Boolean = true,
    ): Document = populate(schema.xmlSchema, overrides, namespaceAware)

    internal fun populate(
        schema: XmlSchemaElement,
        overrides: List<Override> = emptyList(),
        namespaceAware: Boolean = true,
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
        overrides: List<Override>,
        namespaceAware: Boolean,
    ): Document = if (overrides.isNotEmpty()) {
        val overridesDoc = if (!namespaceAware) {
            // Java xpath implementation is strict about namespaces. If we want to ignore default namespace in
            // xpath expressions, we need to remove namespaces altogether from the document...
            removeNamespaces(originalDoc)
        } else {
            originalDoc
        }

        overrides.forEach { override ->
            when (override) {
                is DeleteNodeOverride -> EftiXPathDocumentManipulator.deleteNode(
                    overridesDoc,
                    override.xpath.compiled,
                )

                is TextContentOverride -> EftiXPathDocumentManipulator.setTextContent(
                    overridesDoc,
                    override.xpath.compiled,
                    override.value,
                )
            }
        }

        if (!namespaceAware) {
            // ...however, we want to produce documents that pass validation. Therefore, we need to restore
            // the namespaces.
            restoreNamespacesFromSchema(schema, overridesDoc)
        } else {
            overridesDoc
        }
    } else {
        originalDoc
    }

    private fun populateElement(doc: Document, parent: Node, parentPath: ValuePath, schema: XmlSchemaElement) {
        val currentPath = parentPath.append(schema)

        val repeatGenerator = gen.forPath(currentPath)
        val repeatRange = when (repeatableMode) {
            RepeatablePopulateMode.RANDOM ->
                schema.cardinality.min to repeatGenerator.nextLong(
                    0,
                    min(schema.cardinality.max ?: 3, 3),
                ) + max(schema.cardinality.min, 2)

            RepeatablePopulateMode.MINIMUM_ONE ->
                max(schema.cardinality.min, 1) to repeatGenerator.nextLong(
                    0,
                    min(schema.cardinality.max ?: 3, 3),
                ) + max(schema.cardinality.min, 2)

            RepeatablePopulateMode.EXACTLY_ONE -> 1 to 2
        }
        val count = repeatGenerator.nextLong(
            startInclusive = repeatRange.first.toLong(),
            endExclusive = repeatRange.second.toLong(),
        )

        repeat(count.toInt()) { repeatIndex ->
            val element = parent.appendChild(doc.createElementNS(schema.name.namespaceURI, schema.name.localPart))

            schema.type.attributes.forEach { schemaAttribute ->
                val attribute =
                    doc.createAttributeNS(schemaAttribute.name.namespaceURI, schemaAttribute.name.localPart)

                val fixedValue = schemaAttribute.fixedValue
                if (fixedValue != null) {
                    // A document is only valid if an attribute with a fixed value has exactly that value.
                    attribute.value = fixedValue
                } else if (schemaAttribute.type.isTextContentType) {
                    val generator = findMostSpecificGenerator(schemaAttribute.name, schemaAttribute.type)
                    attribute.value =
                        generator(currentPath.append(repeatIndex).append(schemaAttribute), 0, schemaAttribute.type)
                }
                element.attributes.setNamedItem(attribute)
            }

            schema.children.forEach { child ->
                populateElement(doc, element, currentPath.append(repeatIndex), child)
            }

            val fixedValue = schema.fixedValue
            if (fixedValue != null) {
                // A document is only valid if an element with a fixed value has exactly that value.
                element.textContent = fixedValue
            } else if (schema.type.isTextContentType) {
                val generator = findMostSpecificGenerator(schema.name, schema.type)
                element.textContent = generator(currentPath.append(repeatIndex), repeatIndex, schema.type)
            }
        }
    }

    private fun findMostSpecificGenerator(name: XmlName, type: XmlType): XmlValueGenerator =
        requireNotNull(valueGenerators.findRule(name, type)?.second) {
            val typeNames = sequenceOf(type).plus(type.baseTypes)
                .map { it.name?.localPart ?: "<anonymous>" }
                .joinToString(" -> ")
            "No ${valueGenerators.description} value generator is defined for element \"${name.localPart}\" of" +
                " type $typeNames. Please report this element and type to the maintainers."
        }

    companion object {
        private val factory = DocumentBuilderFactory.newInstance()

        private fun newDocument(): Document {
            val builder = factory.newDocumentBuilder()
            val doc: Document = builder.newDocument()
            return doc
        }

        /**
         * Restore the namespaces that were removed by [removeNamespaces].
         *
         * The namespaces cannot be restored by simply declaring one namespace on the document element: the v1
         * schemas spread a single document over several namespaces, for example the message envelope, the reusable
         * components and the datatypes each have their own. Therefore the document is rebuilt by walking it
         * alongside the schema, taking the namespace of each element and attribute from the schema.
         */
        private fun restoreNamespacesFromSchema(
            schema: XmlSchemaElement,
            originalDoc: Document,
        ): Document {
            val doc: Document = newDocument()

            doc.appendChild(copyWithNamespaces(doc, originalDoc.documentElement, schema))

            // Another serialization round is required to normalize the namespace declarations.
            return deserializeToDocument(serializeToString(doc, prettyPrint = false), namespaceAware = true)
        }

        private fun copyWithNamespaces(doc: Document, source: Element, schema: XmlSchemaElement?): Element {
            val target = createElement(doc, localNameOf(source), schema?.name?.namespaceURI)

            source.attributes.asIterable()
                // Namespace declarations are plain attributes in a document that was parsed without namespace
                // awareness. They must not be copied, because the namespaces are taken from the schema instead.
                .filterNot { attribute -> isNamespaceDeclaration(attribute) }
                .forEach { attribute ->
                    val attributeName = localNameOf(attribute)
                    val attributeNamespace = schema?.type?.attributes
                        ?.find { it.name.localPart == attributeName }
                        ?.name
                        ?.namespaceURI

                    if (attributeNamespace.isNullOrEmpty()) {
                        target.setAttribute(attributeName, attribute.nodeValue)
                    } else {
                        target.setAttributeNS(attributeNamespace, attributeName, attribute.nodeValue)
                    }
                }

            source.childNodes.asIterable().forEach { child ->
                when (child.nodeType) {
                    Node.ELEMENT_NODE -> {
                        val childElement = child as Element
                        val childSchema = schema?.children
                            ?.find { it.name.localPart == localNameOf(childElement) }
                        target.appendChild(copyWithNamespaces(doc, childElement, childSchema))
                    }

                    Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                        target.appendChild(doc.createTextNode(child.nodeValue))

                    else -> Unit
                }
            }

            return target
        }

        private fun createElement(doc: Document, localName: String, namespaceURI: String?): Element =
            if (namespaceURI.isNullOrEmpty()) {
                doc.createElement(localName)
            } else {
                doc.createElementNS(namespaceURI, localName)
            }

        private fun isNamespaceDeclaration(attribute: Node): Boolean {
            val name = attribute.nodeName
            return name == "xmlns" || name.startsWith("xmlns:")
        }

        private fun localNameOf(node: Node): String = node.localName ?: node.nodeName

        private fun NamedNodeMap.asIterable(): Iterable<Node> =
            (0 until this.length).asSequence().map { this.item(it) }.asIterable()

        private fun removeNamespaces(doc: Document): Document {
            // Note: a clumsy way of making unaware of namespaces
            return deserializeToDocument(serializeToString(doc, prettyPrint = false), namespaceAware = false)
        }
    }
}
