package app

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MD:
  private val options = MutableDataSet()

  private val parser = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build()

  def render(markdown: String): String =
    val document = parser.parse(markdown)

    renderer.render(document)
