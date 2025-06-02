package eu.efti.datatools.schema

import eu.efti.datatools.schema.EftiSchemas.consignmentCommonSchema
import eu.efti.datatools.schema.EftiSchemas.consignmentIdentifierSchema
import eu.efti.datatools.schema.EftiSchemas.javaCommonSchema
import eu.efti.datatools.schema.EftiSchemas.javaIdentifiersSchema
import eu.efti.datatools.schema.XmlUtil.clone
import eu.efti.datatools.schema.XmlUtil.dropNodesRecursively
import eu.efti.datatools.schema.XmlUtil.validate
import org.w3c.dom.Document
import org.w3c.dom.Node
import javax.xml.validation.Schema

object SubsetUtil {
    /**
     * Create a copy of the consignment common document and drop all elements that are not included in the given
     * subsets. The subset ids are not validated.
     * @param doc consignment common document
     * @param subsets set of subsets to keep
     * @return new document containing only elements that are included in the given subsets
     * @throws IllegalArgumentException if `doc` does not conform to consignment common schema
     */
    @JvmStatic
    fun filterCommonSubsets(doc: Document, subsets: Set<XmlSchemaElement.SubsetId>): Document =
        filterSubsets(doc, subsets, javaCommonSchema, consignmentCommonSchema)

    /**
     * Create a copy of the consignment identifier document and drop all elements that are not included in the given
     * subsets. The subset ids are not validated.
     * @param doc consignment identifier document
     * @param subsets set of subsets to keep
     * @return new document containing only elements that are included in the given subsets
     * @throws IllegalArgumentException if `doc` does not conform to consignment identifier schema
     */
    @JvmStatic
    fun filterIdentifierSubsets(doc: Document, subsets: Set<XmlSchemaElement.SubsetId>): Document =
        filterSubsets(doc, subsets, javaIdentifiersSchema, consignmentIdentifierSchema)

    /**
     * Drop recursively all nodes that are not included in the given subsets. The subset ids are not validated.
     * @param subsets set of subsets to keep
     * @param schema schema element for `node`
     * @param node xml node to start from
     */
    @JvmStatic
    fun dropNodesNotInSubsets(subsets: Set<XmlSchemaElement.SubsetId>, schema: XmlSchemaElement, node: Node) {
        require(subsets.isNotEmpty()) {
            "subsets must be non-empty"
        }

        dropNodesRecursively(
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

    private fun filterSubsets(
        doc: Document, subsets: Set<XmlSchemaElement.SubsetId>, javaSchema: Schema, schema: XmlSchemaElement
    ): Document {
        return validate(doc, javaSchema)
            ?.let { error -> throw IllegalArgumentException("Input document is not valid: $error") }
            ?: clone(doc)
                .also { cloned ->
                    dropNodesNotInSubsets(subsets, schema, cloned.firstChild)
                    validate(cloned, javaSchema)
                }
    }
}
