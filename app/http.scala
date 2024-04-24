package app

import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.jdkhttp.*
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import java.util.concurrent.Executors

object Http:
  private val index =
    endpoint.get
      .out(htmlBodyUtf8)
      .handle(_ => Right(Templates.index()))

  private def inquire(using Config, Db) =
    endpoint.post
      .in("inquire")
      .in(formBody[Map[String, String]])
      .out(htmlBodyUtf8)
      .handle { form =>
        form.get("q").flatMap { s => if s.isBlank() then None else Some(s) } match
          case Some(question) =>
            val response = AI.askDocs(question)
            val rendered = MD.render(response)

            Right(Templates.response(rendered))

          case None => Right(Templates.response("Have nothing to ask?"))
      }

  def startServer()(using cfg: Config, db: Db) =
    JdkHttpServer()
      .options(JdkHttpServerOptions.Default.copy(interceptors = List(CORSInterceptor.default[Id])))
      .executor(Executors.newVirtualThreadPerTaskExecutor())
      .addEndpoint(staticResourcesGetServerEndpoint("static")(classOf[App].getClassLoader, "/"))
      .addEndpoint(inquire)
      .addEndpoint(index)
      .port(cfg.port)
      .start()
