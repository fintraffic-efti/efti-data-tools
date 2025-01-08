package eu.efti.datatools.schema

data class XmlSchemaElement(
    val name: XmlName,
    val type: XmlType,
    val cardinality: XmlCardinality,
    val children: List<XmlSchemaElement>,
    val subsets: Set<SubsetId>,
) {
    data class XmlAttribute(val name: XmlName, val type: XmlType)

    data class XmlName(val namespaceURI: String, val localPart: String)

    data class XmlType(
        val name: XmlName,
        val enumerationValues: List<String> = emptyList(),
        val attributes: List<XmlAttribute> = emptyList(),
        val baseTypes: List<XmlType> = emptyList(),
        val isTextContentType: Boolean,
    )

    data class XmlCardinality(val min: Long = 0, val max: Long? = null)

    data class SubsetId(val id: String) {
        init {
            require(id.isNotBlank()) { "id must not be blank" }
        }
    }
}
