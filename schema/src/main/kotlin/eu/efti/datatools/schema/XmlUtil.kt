package eu.efti.datatools.schema

import org.w3c.dom.Document
import org.xml.sax.SAXException
import java.io.IOException
import javax.xml.transform.Source
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema

object XmlUtil {
    fun validate(doc: Document, javaSchema: Schema): String? {
        val xmlSource: Source = DOMSource(doc)
        val error = try {
            javaSchema.newValidator().validate(xmlSource)
            null
        } catch (e: SAXException) {
            e.message
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return error
    }
}