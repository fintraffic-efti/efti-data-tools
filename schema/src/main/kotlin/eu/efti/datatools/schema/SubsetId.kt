package eu.efti.datatools.schema

/**
 * Id of an eFTI subset, for example `FI01`. Subsets are declared in the schema annotations of the consignment
 * common schema.
 */
data class SubsetId(val id: String) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
    }
}
