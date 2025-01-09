package eu.efti.datatools.app

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters
import eu.efti.datatools.populate.EftiDomPopulator
import eu.efti.datatools.populate.EftiDomPopulator.TextContentOverride
import eu.efti.datatools.populate.RepeatablePopulateMode
import eu.efti.datatools.populate.SchemaConversion.commonToIdentifiers
import eu.efti.datatools.schema.EftiSchemas
import eu.efti.datatools.schema.EftiSchemas.consignmentCommonSchema
import eu.efti.datatools.schema.EftiSchemas.consignmentIdentifierSchema
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.w3c.dom.Document
import java.io.File
import javax.xml.validation.Schema
import kotlin.system.exitProcess

class TextContentOverrideConverter : IStringConverter<TextContentOverride> {
    override fun convert(raw: String): TextContentOverride {
        val parts = raw.split(XPATH_VALUE_SEPARATOR)
        if (parts.size != 2) {
            throw ParameterException(""""$raw" should be of the form: <xpath-expression>$XPATH_VALUE_SEPARATOR<value>""")
        }
        val (xpathString, valueString) = parts.map(String::trim)

        val xpath = TextContentOverride.XPathRawAndCompiled.tryToParse(xpathString)
            ?: throw ParameterException("Invalid xpath: $raw")

        return TextContentOverride(xpath, valueString)
    }

    companion object {
        private const val XPATH_VALUE_SEPARATOR = ":="
    }
}

class TextContentOverrideValidator : IParameterValidator {
    override fun validate(name: String, value: String) {
        try {
            TextContentOverrideConverter().convert(value)
        } catch (e: ParameterException) {
            throw ParameterException("Parameter $name: ${e.message}")
        }
    }
}

enum class SchemaOption {
    both, common, identifier
}

class CommandMain {
    @Parameter(names = ["--help", "-h"], help = true, description = "Print this help.")
    var help = false
}

@Parameters(commandDescription = "Populate random documents")
class CommandPopulate {
    @Parameter(
        names = ["--seed", "-s"],
        description = "Seed to use, by default a random seed is used. Identical seeds should produce identical documents for a given version of the application."
    )
    var seed: Long? = null

    @Parameter(
        names = ["--schema", "-x"],
        description = "Schema to use"
    )
    var schema: SchemaOption = SchemaOption.common

    @Parameter(
        names = ["--repeatable-mode", "-r"],
        description = "How many instances of a repeatable element should be generated"
    )
    var repeatableMode: RepeatablePopulateMode = RepeatablePopulateMode.MINIMUM_ONE

    @Parameter(
        names = ["--text-overrides", "-t"],
        converter = TextContentOverrideConverter::class,
        validateWith = [TextContentOverrideValidator::class],
        description = """Override to apply to the populated document. The format is "<xpath-expression>:=<value>". Expression can use local xml names, namespaces can be ignored. If multiple instances of this parameter are defined, then each override will be applied in the given order."""
    )
    var textOverrides: List<TextContentOverride> = emptyList()

    @Parameter(names = ["--output", "-o", "-oc"], required = false, description = "Output file for common.")
    var pathCommon: String? = null

    @Parameter(names = ["--output-identifiers", "-oi"], required = false, description = "Output file for identifiers.")
    var pathIdentifiers: String? = null

    @Parameter(names = ["--overwrite", "-w"], required = false, description = "Overwrite existing document.")
    var overwrite: Boolean = false

    @Parameter(names = ["--pretty", "-p"], required = false, description = "Pretty print.")
    var pretty: Boolean = true
}

fun main(argv: Array<String>) {
    val mainArgs = CommandMain()
    val populateArgs = CommandPopulate()
    val parser = JCommander.newBuilder()
        .addObject(mainArgs)
        .addCommand("populate", populateArgs)
        .build()

    try {
        parser.parse(*argv)
    } catch (e: ParameterException) {
        System.err.println(e.message)
        exitProcess(1)
    }

    if (mainArgs.help) {
        parser.usage()
    } else if (parser.parsedCommand == "populate") {
        doPopulate(populateArgs)
    }
}

private fun doPopulate(populateArgs: CommandPopulate) {
    if (populateArgs.seed == null) {
        populateArgs.seed = randomShortSeed()
    }
    if (populateArgs.pathCommon == null && populateArgs.schema in setOf(SchemaOption.both, SchemaOption.common)) {
        populateArgs.pathCommon = "consignment-${populateArgs.seed}-common.xml"
    }
    if (populateArgs.pathIdentifiers == null && populateArgs.schema in setOf(
            SchemaOption.both,
            SchemaOption.identifier
        )
    ) {
        populateArgs.pathIdentifiers = "consignment-${populateArgs.seed}-identifiers.xml"
    }

    println("Generating with:")
    println(
        listOf(
            "schema" to populateArgs.schema,
            "seed" to populateArgs.seed,
            "repeatable mode" to populateArgs.repeatableMode.name,
            "overrides" to populateArgs.textOverrides.map { """Set "${it.xpath.raw}" to "${it.value}"""" },
            "output common" to populateArgs.pathCommon,
            "output identifiers" to populateArgs.pathIdentifiers,
            "overwrite" to populateArgs.overwrite,
            "pretty" to populateArgs.pretty,
        )
            .filter { it.second != null }
            .joinToString("\n") { (label, value) -> """  * $label: $value""" }
    )

    val fileCommon = populateArgs.pathCommon?.let(::File)
    val fileIdentifiers = populateArgs.pathIdentifiers?.let(::File)
    if (!populateArgs.overwrite) {
        if (fileCommon?.exists() == true) {
            println("Output file ${populateArgs.pathCommon} already exists")
            exitProcess(1)
        }
        if (fileIdentifiers?.exists() == true) {
            println("Output file ${populateArgs.pathIdentifiers} already exists")
            exitProcess(1)
        }
    }

    val doc = EftiDomPopulator(checkNotNull(populateArgs.seed), populateArgs.repeatableMode)
        .populate(
            schema = when (populateArgs.schema) {
                SchemaOption.both -> consignmentCommonSchema
                SchemaOption.common -> consignmentCommonSchema
                SchemaOption.identifier -> consignmentIdentifierSchema
            },
            overrides = populateArgs.textOverrides,
            namespaceAware = false,
        )

    val validateAndWrite = documentValidatorAndWriter(populateArgs.pretty)

    when (populateArgs.schema) {
        SchemaOption.both -> {
            val identifiers = commonToIdentifiers(doc)
            validateAndWrite(EftiSchemas.javaCommonSchema, doc, checkNotNull(fileCommon))
            validateAndWrite(EftiSchemas.javaIdentifiersSchema, identifiers, checkNotNull(fileIdentifiers))
        }

        SchemaOption.common -> {
            validateAndWrite(EftiSchemas.javaCommonSchema, doc, checkNotNull(fileCommon))
        }

        SchemaOption.identifier -> {
            validateAndWrite(EftiSchemas.javaIdentifiersSchema, doc, checkNotNull(fileIdentifiers))
        }
    }
}

private fun documentValidatorAndWriter(prettyPrint: Boolean): (schema: Schema, doc: Document, file: File) -> Unit =
    { schema, doc, file ->
        XmlUtil.validate(doc, schema)?.also {
            throw IllegalStateException("Application produced an invalid document. Please report the parameters and the this error message to the maintainers. Validation error: $it")
        }
        file.printWriter().use { out ->
            out.print(serializeToString(doc, prettyPrint = prettyPrint))
        }
    }

private fun randomShortSeed() =
    System.currentTimeMillis().let { number -> number - (number / 100000 * 100000) }