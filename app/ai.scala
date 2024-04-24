package app

import scala.util.*

import sttp.openai.OpenAISyncClient
import sttp.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.openai.requests.completions.chat.message.{Message, Content}

object AI:
  def askDocs(question: String)(using conf: Config, db: Db): String =
    val openAI = OpenAISyncClient(conf.openAIApiKey)

    val contentFromDb = db.queryEmbeddings(question)

    val prompt = contentFromDb match
      case None =>
        s"""You are a programming assistant. User has asked this question:
           |  $question
           |We weren't able to find anything about that in our database. 
           |Please respond politely and explain that you have no information about this subject.
           |""".stripMargin
      case Some(result) =>
        s"""You are a programming assistant. User has asked this question:
           |  $question
           |We were able to find material regarding this topic in our database:
           |
           |${result.content}
           |
           |Please use the document above to formulate an answer for the user. You can use
           |markdown with code snippets in your response. In the end of your response inform
           |the user that more information can be found at this url:
           |
           |${result.url}
           |""".stripMargin

    val bodyMessages: Seq[Message] = Seq(
      Message.UserMessage(
        content = Content.TextContent(prompt)
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = bodyMessages
    )

    Try(openAI.createChatCompletion(chatRequestBody)) match
      case Failure(exception) =>
        scribe.error("Failed to ask OpenAI", exception)
        "Oops, something is not right!"
      case Success(response) =>
        response.choices.headOption match
          case None =>
            scribe.error("OpenAI response is empty")
            "Oops, something is not right!"
          case Some(chatResponse) =>
            chatResponse.message.content
