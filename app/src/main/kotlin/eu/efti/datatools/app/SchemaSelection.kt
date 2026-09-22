package eu.efti.datatools.app

import eu.efti.datatools.schema.EftiSchemaId
import eu.efti.datatools.schema.EftiSchemaVersion
import java.io.File

/**
 * The role that a schema plays in the command line application, independent of the eFTI schema version.
 */
enum class SchemaRole {
    MAIN,
    IDENTIFIER,
}

/**
 * Thrown when the schema version cannot be detected, or when the requested operation is not available for the
 * detected version.
 */
class SchemaSelectionException(message: String) : RuntimeException(message)

/**
 * Detects which version of the eFTI schemas the user has made available, and maps the roles used by the command
 * line application onto the schemas of that version.
 *
 * The version is detected from the contents of the schema directory: each version has its own main xsd file, for
 * example `consignment-common.xsd` for v0 and `FTI010/FTI010s.xsd` for v1.
 */
object SchemaSelection {
    /**
     * @throws SchemaSelectionException if the directory does not contain a recognised set of eFTI schemas
     */
    fun detectVersion(schemaDir: File): EftiSchemaVersion {
        val detected = EftiSchemaVersion.entries.filter { version ->
            EftiSchemaId.ofVersion(version).any { id -> File(schemaDir, id.mainXsdPath).isFile }
        }

        return when (detected.size) {
            1 -> detected.single()

            0 -> throw SchemaSelectionException(
                """
                   Could not determine the eFTI schema version of directory "$schemaDir". Expected to find one of 
                   the following files there: ${mainXsdPaths()}. Make sure that --schema-dir points at a complete 
                   set of eFTI xsd files.
                """.trimIndent(),
            )

            else -> throw SchemaSelectionException(
                """
                   Directory "$schemaDir" contains schemas of several eFTI versions (${detected.joinToString(", ")}), 
                   so the version to use is ambiguous. Please point --schema-dir at a directory that contains the 
                   schemas of a single version.
                """.trimIndent(),
            )
        }
    }

    /**
     * @return the schema of the given version that plays the given role
     * @throws SchemaSelectionException if the version has no schema for the role
     */
    fun schemaIdFor(version: EftiSchemaVersion, role: SchemaRole): EftiSchemaId = when (role) {
        SchemaRole.MAIN -> when (version) {
            EftiSchemaVersion.V0 -> EftiSchemaId.CONSIGNMENT_COMMON
            EftiSchemaVersion.V1 -> EftiSchemaId.CMDS_RESPONSE_V1
        }

        SchemaRole.IDENTIFIER -> when (version) {
            EftiSchemaVersion.V0 -> EftiSchemaId.CONSIGNMENT_IDENTIFIER

            EftiSchemaVersion.V1 -> throw SchemaSelectionException(
                """
                   The eFTI $version schemas do not have a consignment identifier schema, so this operation is not 
                   available for them. It is currently supported for the eFTI ${EftiSchemaVersion.V0} schemas only.
                """.trimIndent(),
            )
        }
    }

    private fun mainXsdPaths(): String =
        EftiSchemaVersion.entries.joinToString(", ") { version ->
            EftiSchemaId.ofVersion(version)
                .map { it.mainXsdPath }
                .distinct()
                .joinToString("/") + " ($version)"
        }
}
