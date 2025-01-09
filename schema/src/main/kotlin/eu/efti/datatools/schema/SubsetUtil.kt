package eu.efti.datatools.schema

import eu.efti.datatools.schema.EftiSchemas.javaCommonSchema
import eu.efti.datatools.schema.EftiSchemas.readConsignmentCommonSchema
import eu.efti.datatools.schema.XmlUtil.clone
import eu.efti.datatools.schema.XmlUtil.validate
import org.w3c.dom.Document
import org.w3c.dom.Node

object SubsetUtil {
    fun filterCommonSubsets(doc: Document, subsets: Set<XmlSchemaElement.SubsetId>): Document {
        return validate(doc, javaCommonSchema)
            ?.let { error -> throw IllegalArgumentException("Not a valid common schema document: $error") }
            ?: clone(doc)
                .also { cloned ->
                    dropNodesNotInSubsets(subsets, readConsignmentCommonSchema(), cloned.firstChild)
                }
    }

    fun dropNodesNotInSubsets(subsets: Set<XmlSchemaElement.SubsetId>, schema: XmlSchemaElement, node: Node) {
        require(subsets.isNotEmpty()) {
            "subsets must be non-empty"
        }

        XmlUtil.dropNodesRecursively(
            schema = schema,
            node = node,
            namespaceAware = true
        ) { candidateNode: Node, maybeSchemaElement: XmlSchemaElement? ->
            val schemaElement = checkNotNull(maybeSchemaElement) {
                "Schema element for ${candidateNode.localName} must not be null"
            }

            val isInRequestedSubsets = schemaElement.subsets.any { subset -> subset in subsets }
            !isInRequestedSubsets
        }
    }
}
