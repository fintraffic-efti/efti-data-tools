package eu.efti.datatools.app

import com.beust.jcommander.*
import eu.efti.datatools.populate.EftiDomPopulator
import eu.efti.datatools.populate.EftiDomPopulator.TextContentOverride
import eu.efti.datatools.populate.RepeatablePopulateMode
import eu.efti.datatools.schema.EftiSchemas
import org.w3c.dom.Document
import org.w3c.dom.bootstrap.DOMImplementationRegistry
import org.w3c.dom.ls.DOMImplementationLS
import java.io.ByteArrayOutputStream
import java.io.File
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
    common, identifier
}

class Args {
    @Parameter(names = ["--help", "-h"], help = true, description = "Print this help.")
    var help = false

    @Parameter(
        names = ["--seed", "-s"],
        description = "Seed to use, by default a random seed is used. Identical seeds should produce identical documents for a given version of the application."
    )
    var seed: Long? = null

    @Parameter(
        names = ["--schema", "-x"],
        description = "Schema to use"
    )
    var schema: SchemaOption? = SchemaOption.common

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

    @Parameter(names = ["--output", "-o"], required = false, description = "Output file.")
    var output: String? = null

    @Parameter(names = ["--overwrite", "-w"], required = false, description = "Overwrite existing document.")
    var overwrite: Boolean = false

    @Parameter(names = ["--pretty", "-p"], required = false, description = "Pretty print.")
    var pretty: Boolean = true
}

fun main(argv: Array<String>) {
    val args = Args()
    val parser = JCommander.newBuilder()
        .addObject(args)
        .build()
    try {
        parser.parse(*argv)
    } catch (e: ParameterException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    if (args.seed == null) {
        args.seed = randomShortSeed()
    }
    if (args.output == null) {
        args.output = "consignment-${args.schema}-${args.seed}.xml"
    }

    if (args.help) {
        parser.usage()
    } else {

        println(
            """|Generating with:
               |  * schema: ${args.schema}
               |  * seed: ${args.seed}
               |  * repeatable mode: ${args.repeatableMode.name}
               |  * overrides: ${args.textOverrides.map { """Set "${it.xpath.raw}" to "${it.value}"""" }}
               |  * output: ${args.output}
               |  * overwrite: ${args.overwrite}
               |  * pretty: ${args.pretty}
           """.trimMargin()
        )

        val file = File(checkNotNull(args.output))
        if (!args.overwrite && file.exists()) {
            println("Output file ${args.output} already exists")
            exitProcess(1)
        } else {
            val doc = EftiDomPopulator(checkNotNull(args.seed), args.repeatableMode)
                .populate(
                    schema = when (checkNotNull(args.schema)) {
                        SchemaOption.common -> EftiSchemas.readConsignmentCommonSchema()
                        SchemaOption.identifier -> EftiSchemas.readConsignmentIdentifiersSchema()
                    },
                    overrides = args.textOverrides,
                    namespaceAware = false,
                )

            file.printWriter().use { out ->
                out.print(serializeToString(doc, prettyPrint = args.pretty))
            }
        }
    }
}

private fun randomShortSeed() =
    System.currentTimeMillis().let { number -> number - (number / 100000 * 100000) }

private fun serializeToString(doc: Document, prettyPrint: Boolean = false): String {
    val registry = DOMImplementationRegistry.newInstance()
    val domImplLS = registry.getDOMImplementation("LS") as DOMImplementationLS

    val lsSerializer = domImplLS.createLSSerializer()
    val domConfig = lsSerializer.domConfig
    domConfig.setParameter("format-pretty-print", prettyPrint)

    val byteArrayOutputStream = ByteArrayOutputStream()
    val lsOutput = domImplLS.createLSOutput()
    lsOutput.encoding = "UTF-8"
    lsOutput.byteStream = byteArrayOutputStream

    lsSerializer.write(doc, lsOutput)
    return byteArrayOutputStream.toString(Charsets.UTF_8)
}
