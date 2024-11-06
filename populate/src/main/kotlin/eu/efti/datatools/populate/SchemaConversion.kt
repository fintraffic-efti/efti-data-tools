package eu.efti.datatools.populate

import eu.efti.datatools.schema.EftiSchemas
import eu.efti.datatools.schema.XmlSchemaElement
import eu.efti.datatools.schema.XmlUtil.asIterable
import eu.efti.datatools.schema.XmlUtil.serializeToString
import eu.efti.datatools.schema.XmlUtil.deserializeToDocument
import org.w3c.dom.Document
import org.w3c.dom.Node
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource

object SchemaConversion {
    fun commonToIdentifiers(common: Document): Document {
        val identifier = clone(common)

        dropNodesNotInSchema(EftiSchemas.readConsignmentIdentifiersSchema(), identifier.firstChild)

        return deserializeToDocument(
            // Note: this is a dirty way of fixing the namespace, but it is simple and works in our context.
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
}