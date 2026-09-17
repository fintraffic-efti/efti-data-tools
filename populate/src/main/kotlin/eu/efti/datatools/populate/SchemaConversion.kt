package eu.efti.datatools.populate

import eu.efti.datatools.schema.EftiSchema
import eu.efti.datatools.schema.EftiSchemaId
import eu.efti.datatools.schema.EftiSchemaVersion
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
     * Note: this conversion is only available for the eFTI [EftiSchemaVersion.V0] schemas. The v1 schemas have no
     * equivalent identifier schema, and their documents span several namespaces, which this conversion does not
     * support.
     *
     * @param identifierSchema consignment identifier schema to convert into
     * @param common consignment common document
     * @throws UnsupportedOperationException if the given schema is not a v0 identifier schema
     */
    fun commonToIdentifiers(identifierSchema: EftiSchema, common: Document): Document {
        if (identifierSchema.id != EftiSchemaId.CONSIGNMENT_IDENTIFIER) {
            throw UnsupportedOperationException(
                """
                   Converting a consignment common document into a consignment identifiers document is not 
                   supported for ${identifierSchema.id} (eFTI ${identifierSchema.id.version}). The conversion is 
                   currently available for the eFTI ${EftiSchemaVersion.V0} schemas only.
                """.trimIndent(),
            )
        }

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
