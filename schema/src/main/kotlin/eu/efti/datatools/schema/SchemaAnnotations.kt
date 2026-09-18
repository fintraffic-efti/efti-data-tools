package eu.efti.datatools.schema

import org.apache.xmlbeans.SchemaLocalElement
import org.apache.xmlbeans.SimpleValue
import javax.xml.namespace.QName

/**
 * Reads the eFTI annotations of a schema element. The two eFTI schema versions use different annotation dialects,
 * and both are read unconditionally: a v0 schema carries none of the v1 annotations and the other way round, so
 * each dialect simply yields nothing on the other version and the parser does not need to know the version.
 */
internal object SchemaAnnotations {
    /**
     * Subsets that an element belongs to, in either dialect.
     *
     * v0 declares them in `xsd:appinfo`, as `<efti><subset id="EU01" status="M"/></efti>`. v1 declares them in
     * `xsd:documentation`, as repeated `<eFTI_subset>EU01</eFTI_subset>` elements without a status.
     */
    fun subsets(schemaElement: SchemaLocalElement): Set<SubsetId> =
        (v0Subsets(schemaElement) + v1Subsets(schemaElement)).map(::SubsetId).toSet()

    /**
     * The `eFTI_ID` that the v1 schemas assign to an element, or null if the element has none. The same id
     * identifies the same element across v1 schema files, which is what makes it possible to read subsets from one
     * v1 schema and apply them to another, see [SubsetOverlay].
     */
    fun eftiId(schemaElement: SchemaLocalElement): String? =
        documentationChildren(schemaElement, "eFTI_ID").firstOrNull()

    private fun v0Subsets(schemaElement: SchemaLocalElement): Sequence<String> = schemaElement
        .annotation
        ?.applicationInformation
        ?.asSequence()
        ?.flatMap { appInfo ->
            appInfo
                .selectChildren(QName("efti")).asSequence()
                .flatMap { efti -> efti.selectChildren(QName("subset")).asSequence() }
        }
        ?.mapNotNull { subset -> (subset.selectAttribute(QName("id")) as? SimpleValue)?.stringValue }
        ?: emptySequence()

    private fun v1Subsets(schemaElement: SchemaLocalElement): Sequence<String> =
        documentationChildren(schemaElement, "eFTI_subset")

    private fun documentationChildren(schemaElement: SchemaLocalElement, localName: String): Sequence<String> =
        schemaElement
            .annotation
            ?.userInformation
            ?.asSequence()
            ?.flatMap { documentation -> documentation.selectChildren(QName(localName)).asSequence() }
            ?.mapNotNull { child -> (child as? SimpleValue)?.stringValue }
            ?.filter { value -> value.isNotBlank() }
            ?: emptySequence()
}
