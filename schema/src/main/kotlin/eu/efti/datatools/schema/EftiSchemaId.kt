package eu.efti.datatools.schema

/**
 * Identifies an eFTI consignment schema.
 *
 * The schema files themselves are not shipped with this library: they are supplied by the user through an
 * [XsdSource]. This enum only describes which file to load and which document element to expect in it.
 *
 * @property mainXsdPath path of the main xsd file, relative to the root of the [XsdSource]
 * @property rootElement expected namespace and local name of the document element
 */
enum class EftiSchemaId(
    val mainXsdPath: String,
    val rootElement: XmlSchemaElement.XmlName,
) {
    CONSIGNMENT_COMMON(
        mainXsdPath = "consignment-common.xsd",
        rootElement = XmlSchemaElement.XmlName("http://efti.eu/v1/consignment/common", "consignment"),
    ),
    CONSIGNMENT_IDENTIFIER(
        mainXsdPath = "consignment-identifier.xsd",
        rootElement = XmlSchemaElement.XmlName("http://efti.eu/v1/consignment/identifier", "consignment"),
    ),
    ;

    val namespaceURI: String get() = rootElement.namespaceURI
}
