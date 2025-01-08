package eu.efti.datatools.schema

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.bootstrap.DOMImplementationRegistry
import org.w3c.dom.ls.DOMImplementationLS
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Source
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema

object XmlUtil {
    fun clone(doc: Document): Document {
        val domResult = DOMResult()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), domResult)
        return checkNotNull(domResult.node as Document)
    }

    fun validate(doc: Document, javaSchema: Schema): String? {
        val xmlSource: Source = DOMSource(doc)
        val error = try {
            javaSchema.newValidator().validate(xmlSource)
            null
        } catch (e: SAXException) {
            e.message
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return error
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
        dropCondition: (node: Node, maybeSchemaElement: XmlSchemaElement?) -> Boolean
    ) {
        fun isTextNode(node: Node): Boolean = node.nodeType == Node.TEXT_NODE

        node.childNodes
            .asIterable()
            .filterNot(::isTextNode)
            .filter { childNode ->
                val schemaElement = schema.children.find { sc ->
                    sc.name.localPart == childNode.localName && (!namespaceAware || sc.name.namespaceURI == childNode.namespaceURI)
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
            .filterNot(::isTextNode)
            .forEach { childNode ->
                val childSchema = schema.children.first { it.name.localPart == childNode.localName }
                dropNodesRecursively(childSchema, childNode, false, dropCondition)
            }
    }
}
