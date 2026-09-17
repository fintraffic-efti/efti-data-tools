package eu.efti.datatools.schema

/**
 * Copies eFTI subsets from one parsed schema onto another, matching the elements of the two by `eFTI_ID`.
 *
 * The v1 message schemas, such as `FTI010s.xsd`, carry no subset annotations. The subsets live in the eFTI XM
 * SubMap schema, which describes the same elements and tags each of them with the subsets it belongs to. The two
 * schemas use a different document element and even different generated type names for the same concept, so they
 * cannot be matched by name or type. They can be matched by `eFTI_ID`, which identifies the same element in both.
 */
internal object SubsetOverlay {
    /**
     * @param target schema to copy the subsets onto
     * @param subsetSchema schema to read the subsets from
     * @param subsetSchemaDescription human readable description of `subsetSchema`, used in error messages
     * @param targetDescription human readable description of `target`, used in error messages
     * @return copy of `target` with its subsets filled in
     * @throws EftiSchemaException if `subsetSchema` does not describe the elements of `target`
     */
    fun apply(
        target: XmlSchemaElement,
        subsetSchema: XmlSchemaElement,
        subsetSchemaDescription: String,
        targetDescription: String,
    ): XmlSchemaElement {
        val subsetsByEftiId = mutableMapOf<String, Set<SubsetId>>()
        collectSubsetsByEftiId(subsetSchema, subsetsByEftiId)

        if (subsetsByEftiId.isEmpty()) {
            throw EftiSchemaException(
                """
                   Schema $subsetSchemaDescription does not declare any eFTI subsets. It is expected to be an eFTI 
                   XM SubMap schema, which annotates its elements with "eFTI_ID" and "eFTI_subset".
                """.trimIndent(),
            )
        }

        // Filtering with an incomplete map would silently drop elements from every subset, so it is better to refuse
        // than to produce quietly wrong documents.
        val unknown = eftiIds(target).filterNot(subsetsByEftiId::containsKey).distinct().sorted()
        if (unknown.isNotEmpty()) {
            throw EftiSchemaException(
                """
                   Schema $subsetSchemaDescription does not declare eFTI subsets for ${unknown.size} of the elements 
                   of $targetDescription, for example ${unknown.take(MAX_REPORTED_UNKNOWN_IDS).joinToString(", ")}. 
                   Please check that the two schemas are of the same eFTI version.
                """.trimIndent(),
            )
        }

        return applySubsets(target, subsetsByEftiId, inherited = emptySet())
    }

    private fun collectSubsetsByEftiId(element: XmlSchemaElement, into: MutableMap<String, Set<SubsetId>>) {
        val eftiId = element.eftiId
        if (eftiId != null) {
            into[eftiId] = into[eftiId].orEmpty() + element.subsets
        }
        element.children.forEach { child -> collectSubsetsByEftiId(child, into) }
    }

    private fun eftiIds(element: XmlSchemaElement): List<String> =
        listOfNotNull(element.eftiId) + element.children.flatMap(::eftiIds)

    /**
     * Copy subsets onto [element] and its descendants.
     *
     * Elements that the eFTI data model does not classify carry no `eFTI_ID`, for example the `DateTimeString`
     * wrappers, whose id belongs to their `format` attribute instead. They are structural rather than data carrying,
     * and they are often mandatory, so they belong to exactly the subsets their parent belongs to. Giving them no
     * subsets at all would drop them from every subset and produce invalid documents.
     */
    private fun applySubsets(
        element: XmlSchemaElement,
        subsetsByEftiId: Map<String, Set<SubsetId>>,
        inherited: Set<SubsetId>,
    ): XmlSchemaElement {
        val subsets = element.eftiId?.let { eftiId -> subsetsByEftiId[eftiId] } ?: inherited
        return element.copy(
            subsets = subsets,
            children = element.children.map { child -> applySubsets(child, subsetsByEftiId, inherited = subsets) },
        )
    }

    private const val MAX_REPORTED_UNKNOWN_IDS = 5
}
