package eu.efti.datatools.populate

import eu.efti.datatools.schema.EftiSchema
import eu.efti.datatools.schema.EftiSchemaId
import eu.efti.datatools.schema.XmlUtil.deserializeToDocument
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.w3c.dom.Document

/**
 * Internal utility of the eFTI data tools, not part of the supported api of this library.
 */
object SchemaConversion {
    /**
     * Convert a consignment common document into a consignment identifiers document.
     *
     * @param identifierSchema consignment identifier schema to convert into
     * @param common consignment common document
     */
    fun commonToIdentifiers(identifierSchema: EftiSchema, common: Document): Document {
        // Note: the document is in the common namespace, so the elements must be matched by local name only.
        val identifier = identifierSchema.dropNodesNotInSchema(common, namespaceAware = false)

        return deserializeToDocument(
            // Note: this is a dirty way of fixing the namespace, but it is simple and works in our context.
            serializeToString(identifier).replace(
                EftiSchemaId.CONSIGNMENT_COMMON.namespaceURI,
                EftiSchemaId.CONSIGNMENT_IDENTIFIER.namespaceURI,
            ),
        )
    }
}
