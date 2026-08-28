package eu.efti.datatools.schema

import eu.efti.datatools.schema.XmlUtil.dropNodesRecursively
import org.w3c.dom.Node

object SubsetUtil {
    /**
     * Drop recursively all nodes that are not included in the given subsets. The subset ids are not validated.
     *
     * Note: to filter an eFTI consignment common document, prefer [EftiSchemas.filterCommonSubsets].
     *
     * @param subsets set of subsets to keep
     * @param schema schema element for `node`
     * @param node xml node to start from
     */
    @JvmStatic
    fun dropNodesNotInSubsets(subsets: Set<SubsetId>, schema: XmlSchemaElement, node: Node) {
        require(subsets.isNotEmpty()) {
            "subsets must be non-empty"
        }

        dropNodesRecursively(
            schema = schema,
            node = node,
            namespaceAware = true,
        ) { candidateNode: Node, maybeSchemaElement: XmlSchemaElement? ->
            val schemaElement = checkNotNull(maybeSchemaElement) {
                "Schema element for ${candidateNode.localName} must not be null"
            }

            val isInRequestedSubsets = schemaElement.subsets.any { subset -> subset in subsets }
            !isInRequestedSubsets
        }
    }
}
