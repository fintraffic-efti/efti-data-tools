package eu.efti.datatools.schema

/**
 * Version of the eFTI schemas.
 *
 * The versions are structurally very different from each other, so some features of this library are only available
 * for some of the versions. See [EftiSchemaId] for the schemas of each version.
 */
enum class EftiSchemaVersion(
    /**
     * Human readable name of the version, used in error messages.
     */
    val displayName: String,
) {
    /**
     * The original eFTI schemas, where the document element is `consignment` and the whole document is in a single
     * namespace.
     */
    V0("v0"),

    /**
     * Schemas of the eFTI Delegated Act (EU) 2024/2024, known as V1.0. These documents are message envelopes whose
     * elements are spread over several namespaces.
     */
    V1("v1"),
    ;

    override fun toString(): String = displayName
}
