package eu.efti.datatools.schema

/**
 * Identifies an eFTI consignment schema.
 *
 * The schema files themselves are not shipped with this library: they are supplied by the user through an
 * [XsdSource]. This enum only describes which file to load and which document element to expect in it.
 *
 * @property mainXsdPath path of the main xsd file, relative to the root of the [XsdSource]
 * @property rootElement expected namespace and local name of the document element
 * @property version version of the eFTI schemas that this schema belongs to
 * @property supportsSubsets true if the schema declares eFTI subsets, so that subset filtering can be used
 */
enum class EftiSchemaId(
    val mainXsdPath: String,
    val rootElement: XmlSchemaElement.XmlName,
    val version: EftiSchemaVersion,
    val supportsSubsets: Boolean,
) {
    CONSIGNMENT_COMMON(
        mainXsdPath = "consignment-common.xsd",
        rootElement = XmlSchemaElement.XmlName("http://efti.eu/v1/consignment/common", "consignment"),
        version = EftiSchemaVersion.V0,
        supportsSubsets = true,
    ),
    CONSIGNMENT_IDENTIFIER(
        mainXsdPath = "consignment-identifier.xsd",
        rootElement = XmlSchemaElement.XmlName("http://efti.eu/v1/consignment/identifier", "consignment"),
        version = EftiSchemaVersion.V0,
        supportsSubsets = true,
    ),

    /**
     * The v1 equivalent of [CONSIGNMENT_COMMON]. The document element is the message envelope
     * `FTI010GetCmdsResponse`, which carries the consignment itself in its `SpecifiedSupplyChainConsignment` child.
     *
     * Note: these schema files do not declare eFTI subsets, so subset filtering is not available for this schema.
     */
    CONSIGNMENT_COMMON_V1(
        mainXsdPath = "FTI010s.xsd",
        rootElement = XmlSchemaElement.XmlName(
            "urn:eu:move:eFTI:data:standard:FTI010GetCmdsResponse:1",
            "FTI010GetCmdsResponse",
        ),
        version = EftiSchemaVersion.V1,
        supportsSubsets = false,
    ),
    ;

    val namespaceURI: String get() = rootElement.namespaceURI

    companion object {
        /**
         * All schemas of the given version.
         */
        @JvmStatic
        fun ofVersion(version: EftiSchemaVersion): List<EftiSchemaId> = entries.filter { it.version == version }
    }
}
