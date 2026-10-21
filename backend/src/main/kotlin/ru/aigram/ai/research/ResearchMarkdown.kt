package ru.aigram.ai.research

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.markdown.MarkdownRenderer
import java.util.Collections
import java.util.StringTokenizer

/** CommonMark owns parsing, including escaped delimiters and nested labels. */
class ResearchMarkdown {
    private val parser = Parser.builder().extensions(listOf(AutolinkExtension.create())).build()
    private val renderer = MarkdownRenderer.builder().build()

    fun links(value: String): List<String> {
        val links = mutableListOf<String>()
        parser.parse(value).accept(object : AbstractVisitor() {
            override fun visit(link: Link) {
                links.add(link.destination)
            }
        })
        return links
    }

    fun mapLinks(value: String, replace: (String, String) -> String): String {
        val normalized = Collections.list(StringTokenizer(value, " \n\t", true)).joinToString("") { part ->
            val token = part.toString()
            if (token.startsWith("t.me/")) "https://$token" else token
        }
        val document = parser.parse(normalized)
        val links = mutableListOf<Link>()
        document.accept(object : AbstractVisitor() {
            override fun visit(link: Link) {
                links.add(link)
            }
        })
        if (links.isEmpty()) return value
        links.stream().forEach { link ->
            link.insertBefore(Text(replace(label(link), link.destination)))
            link.unlink()
        }
        return renderer.render(document).trim()
    }

    private fun label(node: Node): String {
        val text = StringBuilder()
        node.accept(object : AbstractVisitor() {
            override fun visit(node: Text) {
                text.append(node.literal)
            }
        })
        return text.toString()
    }
}
