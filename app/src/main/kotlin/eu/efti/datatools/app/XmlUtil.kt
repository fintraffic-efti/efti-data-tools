package eu.efti.datatools.app

import eu.efti.datatools.schema.EftiSchemas
import eu.efti.datatools.schema.XmlSchemaElement
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.bootstrap.DOMImplementationRegistry
import org.w3c.dom.ls.DOMImplementationLS
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayOutputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource

object XmlUtil {
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

    fun commonToIdentifier(common: Document): Document {
        val identifier = clone(common)

        dropNodesNotInSchema(EftiSchemas.readConsignmentIdentifiersSchema(), identifier.firstChild)

        return tryDeserializeToDocument(
            serializeToString(identifier).replace(
                "http://efti.eu/v1/consignment/common",
                "http://efti.eu/v1/consignment/identifier",
            ),
        )
    }

    private fun dropNodesNotInSchema(schema: XmlSchemaElement, node: Node) {
        fun isTextNode(node: Node): Boolean = node.nodeType == Node.TEXT_NODE

        node.childNodes
            .asIterable()
            .filterNot(::isTextNode)
            .filter { it.localName !in schema.children.map { sc -> sc.name.localPart } }
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
                dropNodesNotInSchema(childSchema, childNode)
            }
    }

    private fun clone(doc: Document): Document {
        val domResult = DOMResult()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), domResult)
        return checkNotNull(domResult.node as Document)
    }

    private fun tryDeserializeToDocument(xml: String, namespaceAware: Boolean = true): Document = try {
        val factory = DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = namespaceAware }
        val builder = factory.newDocumentBuilder()
        builder.parse(InputSource(StringReader(xml)))
    } catch (e: SAXException) {
        throw IllegalArgumentException("Could not parse document:\n$xml", e)
    }

    private fun NodeList.asIterable(): Iterable<Node> =
        (0 until this.length).asSequence().map { this.item(it) }.asIterable()
}