package eu.efti.datatools.schema

/**
 * Location of the eFTI subset annotations for a schema that does not declare them itself.
 *
 * The v1 message schemas, such as `FTI010s.xsd`, carry no subset annotations. The subsets live in a separate
 * schema, the eFTI XM SubMap, which describes the same elements and tags each of them with the subsets it belongs
 * to. The two schemas cannot be matched by element name or type, because they use a different document element and
 * even different generated type names for the same concept. They can be matched by `eFTI_ID`, which identifies the
 * same element in both.
 *
 * @param xsdPath path of the main xsd of the subset schema, relative to the root of the [XsdSource]
 * @param rootElement document element of the subset schema
 */
data class SubsetSource(
    val xsdPath: String,
    val rootElement: XmlSchemaElement.XmlName,
)

/**
 * Identifies an eFTI consignment schema.
 *
 * The schema files themselves are not shipped with this library: they are supplied by the user through an
 * [XsdSource]. This enum only describes which file to load and which document element to expect in it.
 *
 * @property mainXsdPath path of the main xsd file, relative to the root of the [XsdSource]
 * @property rootElement expected namespace and local name of the document element
 * @property version version of the eFTI schemas that this schema belongs to
 */
enum class EftiSchemaId(
    val mainXsdPath: String,
    val rootElement: XmlSchemaElement.XmlName,
    val version: EftiSchemaVersion,
    /**
     * Where to read subset annotations from, or null if this schema declares them itself.
     */
    val subsetSource: SubsetSource? = null,
) {
    CONSIGNMENT_COMMON(
        mainXsdPath = "consignment-common.xsd",
        rootElement = XmlSchemaElement.XmlName("http://efti.eu/v1/consignment/common", "consignment"),
        version = EftiSchemaVersion.V0,
    ),
    CONSIGNMENT_IDENTIFIER(
        mainXsdPath = "consignment-identifier.xsd",
        rootElement = XmlSchemaElement.XmlName("http://efti.eu/v1/consignment/identifier", "consignment"),
        version = EftiSchemaVersion.V0,
    ),

    /**
     * The v1 equivalent of [CONSIGNMENT_COMMON]. The document element is the message envelope
     * `FTI010GetCmdsResponse`, which carries the consignment itself in its `SpecifiedSupplyChainConsignment` child.
     *
     * These schema files declare no eFTI subsets of their own, so the subsets are read from the eFTI XM SubMap
     * schema and matched by `eFTI_ID`, see [subsetSource].
     *
     * Note that the paths are relative to the root of the v1 schemas, so `--schema-dir` must point at the directory
     * that holds both the `FTI010` and the `eFTI XM SubMap` directories.
     */
    CMDS_RESPONSE_V1(
        mainXsdPath = "FTI010/FTI010s.xsd",
        rootElement = XmlSchemaElement.XmlName(
            "urn:eu:move:eFTI:data:standard:FTI010GetCmdsResponse:1",
            "FTI010GetCmdsResponse",
        ),
        version = EftiSchemaVersion.V1,
        subsetSource = SubsetSource(
            xsdPath = "eFTI XM SubMap/eFTIXMa.xsd",
            rootElement = XmlSchemaElement.XmlName("urn:eu:move:eFTI:data:standard:eFTIXM:1", "eFTIXM"),
        ),
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
