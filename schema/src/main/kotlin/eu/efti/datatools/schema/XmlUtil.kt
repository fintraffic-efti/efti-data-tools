package eu.efti.datatools.schema

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.bootstrap.DOMImplementationRegistry
import org.w3c.dom.ls.DOMImplementationLS
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXNotSupportedException
import org.xml.sax.SAXParseException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema
import javax.xml.validation.Validator

object XmlUtil {
    /**
     * Xerces property that holds the element being validated when an error is reported. Supported by the validator
     * of the jdk, but treated as optional here so that validation still works without it.
     */
    private const val CURRENT_ELEMENT_NODE_PROPERTY = "http://apache.org/xml/properties/dom/current-element-node"

    /**
     * Maximum number of validation errors to include in the message of [validate]. A document that is invalid in
     * one way is often invalid in the same way many times over, so reporting every occurrence is rarely useful.
     */
    private const val MAX_REPORTED_ERRORS = 10

    /**
     * A schema validation error, and where in the document it was found.
     *
     * @property message the validation error as reported by the validator
     * @property path path of the element the error was reported for, for example
     * `/consignment/usedTransportEquipment[2]/id`, or null if the validator did not tell which element it was
     */
    data class ValidationError(val message: String, val path: String?) {
        override fun toString(): String = if (path == null) message else "$path: $message"
    }

    fun clone(doc: Document): Document {
        val domResult = DOMResult()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), domResult)
        return checkNotNull(domResult.node as Document)
    }

    /**
     * Validate a document against a schema.
     *
     * @return a description of the validation errors, including the path of the element each error was reported
     * for, or null if the document is valid
     */
    fun validate(doc: Document, javaSchema: Schema): String? {
        val errors = validationErrors(doc, javaSchema)
        return when {
            errors.isEmpty() -> null
            errors.size <= MAX_REPORTED_ERRORS -> errors.joinToString("\n")
            else -> errors.take(MAX_REPORTED_ERRORS).joinToString("\n") +
                "\n... and ${errors.size - MAX_REPORTED_ERRORS} more validation errors"
        }
    }

    /**
     * Validate a document against a schema and collect all errors.
     *
     * Unlike a plain validator, this reports the element each error was found in, which is what makes an error such
     * as "Value 'y' with length = '1' is not facet-valid" actionable.
     *
     * @return the validation errors in document order, empty if the document is valid
     */
    @Suppress("detekt:TooGenericExceptionThrown")
    fun validationErrors(doc: Document, javaSchema: Schema): List<ValidationError> {
        val validator = javaSchema.newValidator()
        val errors = mutableListOf<ValidationError>()

        val collect = { exception: SAXParseException ->
            errors.add(ValidationError(exception.message ?: exception.toString(), currentElementPath(validator)))
        }
        validator.errorHandler = object : ErrorHandler {
            // Warnings are not validity problems, so they are not collected.
            override fun warning(exception: SAXParseException) = Unit

            override fun error(exception: SAXParseException) {
                collect(exception)
            }

            override fun fatalError(exception: SAXParseException) {
                collect(exception)
            }
        }

        try {
            validator.validate(DOMSource(doc))
        } catch (e: SAXException) {
            // A fatal error stops validation, and is thrown even though it was handled above.
            if (errors.isEmpty()) {
                errors.add(ValidationError(e.message ?: e.toString(), null))
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        return errors
    }

    /**
     * Path of the element that the validator is currently validating, or null if the validator does not report it.
     */
    private fun currentElementPath(validator: Validator): String? {
        val node = try {
            validator.getProperty(CURRENT_ELEMENT_NODE_PROPERTY) as? Node
        } catch (@Suppress("detekt:SwallowedException") e: SAXNotRecognizedException) {
            null
        } catch (@Suppress("detekt:SwallowedException") e: SAXNotSupportedException) {
            null
        }
        return node?.let(::pathOf)
    }

    /**
     * Path of a node from the root of its document, using the same shape as the xpath expressions of the
     * command line application, for example `/consignment/usedTransportEquipment[2]/id`. A position is included
     * only when the node has siblings of the same name, so that unambiguous paths stay readable.
     */
    private fun pathOf(node: Node): String {
        val steps = generateSequence(node) { it.parentNode }
            .takeWhile { it.nodeType == Node.ELEMENT_NODE }
            .map { element ->
                val name = element.localName ?: element.nodeName
                val siblings = element.parentNode
                    ?.childNodes
                    ?.asIterable()
                    ?.filter { it.nodeType == Node.ELEMENT_NODE && (it.localName ?: it.nodeName) == name }
                    ?: emptyList()
                if (siblings.size > 1) "$name[${siblings.indexOf(element) + 1}]" else name
            }
            .toList()
            .reversed()
        return steps.joinToString("/", prefix = "/")
    }

    fun serializeToString(doc: Document, prettyPrint: Boolean = false): String {
        val registry = DOMImplementationRegistry.newInstance()
        val domImplLS = registry.getDOMImplementation("LS") as DOMImplementationLS

        val lsSerializer = domImplLS.createLSSerializer()
        val domConfig = lsSerializer.domConfig
        domConfig.setParameter("format-pretty-print", prettyPrint)

        val byteArrayOutputStream = ByteArrayOutputStream()
        val lsOutput = domImplLS.createLSOutput()
        lsOutput.encoding = "UTF-8"
        lsOutput.byteStream = byteArrayOutputStream

        lsSerializer.write(doc, lsOutput)
        return byteArrayOutputStream.toString(Charsets.UTF_8)
    }

    fun deserializeToDocument(xml: String, namespaceAware: Boolean = true): Document = try {
        val factory = DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = namespaceAware }
        val builder = factory.newDocumentBuilder()
        builder.parse(InputSource(StringReader(xml)))
    } catch (e: SAXException) {
        throw IllegalArgumentException("Could not parse document:\n$xml", e)
    }

    fun NodeList.asIterable(): Iterable<Node> =
        (0 until this.length).asSequence().map { this.item(it) }.asIterable()

    fun dropNodesRecursively(
        schema: XmlSchemaElement,
        node: Node,
        namespaceAware: Boolean,
        dropCondition: (node: Node, maybeSchemaElement: XmlSchemaElement?) -> Boolean,
    ) {
        fun isFilterableNode(node: Node): Boolean = when (node.nodeType) {
            Node.COMMENT_NODE, Node.TEXT_NODE -> false
            else -> true
        }

        node.childNodes
            .asIterable()
            .filter(::isFilterableNode)
            .filter { childNode ->
                val schemaElement = schema.children.find { sc ->
                    sc.name.localPart == childNode.localName &&
                        (!namespaceAware || sc.name.namespaceURI == childNode.namespaceURI)
                }
                dropCondition(childNode, schemaElement)
            }
            // Dump nodes to list to ensure node removals do not affect iteration
            .toList()
            .forEach {
                node.removeChild(it)
            }

        node.childNodes
            .asIterable()
            .filter(::isFilterableNode)
            .forEach { childNode ->
                val childSchema = schema.children.first { it.name.localPart == childNode.localName }
                dropNodesRecursively(childSchema, childNode, false, dropCondition)
            }
    }
}
