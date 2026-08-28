package eu.efti.datatools.schema

/**
 * Thrown when the schema files provided by the user cannot be located or do not match the expectations of this
 * library.
 */
class EftiSchemaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
