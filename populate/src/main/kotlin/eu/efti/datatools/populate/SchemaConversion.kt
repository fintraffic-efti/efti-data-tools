package eu.efti.datatools.populate

import eu.efti.datatools.schema.EftiSchemas.consignmentIdentifierSchema
import eu.efti.datatools.schema.XmlSchemaElement
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.clone
import eu.efti.datatools.schema.XmlUtil.deserializeToDocument
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.w3c.dom.Document
import org.w3c.dom.Node

object SchemaConversion {
    fun commonToIdentifiers(common: Document): Document {
        val identifier = clone(common)

        dropNodesNotInSchema(consignmentIdentifierSchema, identifier.firstChild)

        return deserializeToDocument(
            // Note: this is a dirty way of fixing the namespace, but it is simple and works in our context.
            serializeToString(identifier).replace(
                "http://efti.eu/v1/consignment/common",
                "http://efti.eu/v1/consignment/identifier",
            ),
        )
    }

    private fun dropNodesNotInSchema(schema: XmlSchemaElement, node: Node) {
        XmlUtil.dropNodesRecursively(
            schema = schema,
            node = node,
            namespaceAware = false
        ) { _: Node, maybeSchemaElement: XmlSchemaElement? ->
            maybeSchemaElement == null
        }
    }
}
