package eu.efti.datatools.populate

import eu.efti.datatools.schema.XmlUtil.asIterable
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory

object EftiTextContentWriter {
    private val xpathFactory = XPathFactory.newInstance()

    fun setTextContent(doc: Document, xpath: String, value: String) {
        val compiled = compileXpath(xpath)
        setTextContent(doc, compiled, value)
    }

    fun setTextContent(doc: Document, xpath: XPathExpression, value: String) {
        val nodes = xpath.evaluate(doc, XPathConstants.NODESET) as NodeList
        nodes.asIterable().forEach { node -> node.textContent = value }
    }

    private fun compileXpath(expression: String): XPathExpression {
        val xpath = xpathFactory.newXPath()
        return xpath.compile(expression)
    }
}