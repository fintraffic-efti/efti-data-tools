package eu.efti.datatools.schema

data class XmlSchemaElement(
    val name: XmlName,
    val type: XmlType,
    val cardinality: XmlCardinality,
    val children: List<XmlSchemaElement>,
    val subsets: Set<SubsetId>,
    /**
     * Value that the schema fixes for this element, or null if the element has no fixed value. A document is only
     * valid if an element with a fixed value has exactly that value.
     */
    val fixedValue: String? = null,
) {
    data class XmlAttribute(
        val name: XmlName,
        val type: XmlType,
        /**
         * Value that the schema fixes for this attribute, or null if the attribute has no fixed value.
         */
        val fixedValue: String? = null,
    )

    data class XmlName(val namespaceURI: String, val localPart: String)

    data class XmlType(
        /**
         * Qualified name of the type, or null if the type is anonymous, that is, declared inline in an element or
         * an attribute. The v1 eFTI schemas use anonymous types, for example for the `DateTimeString` elements.
         */
        val name: XmlName?,
        val enumerationValues: List<String> = emptyList(),
        val attributes: List<XmlAttribute> = emptyList(),
        val baseTypes: List<XmlType> = emptyList(),
        val isTextContentType: Boolean,
    )

    data class XmlCardinality(val min: Long = 0, val max: Long? = null)
}
