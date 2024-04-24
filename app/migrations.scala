package app

import com.augustnagro.magnum.*

object Migrations:
  val localDocsRoot = "http://localhost:3000/besom/docs/"

  private val files = List(
    "api_reference.md",
    "apply_methods.md",
    "architecture.md",
    "basics.md",
    "changelog.md",
    "compiler_plugin.md",
    "components.md",
    "constructors.md",
    "context.md",
    "examples.md",
    "exports.md",
    "getting_started.md",
    "interpolator.md",
    "intro.md",
    "laziness.md",
    "lifting.md",
    "logging.md",
    "missing.md",
    "packages.md",
    "templates.md",
    "tutorial.md"
  )

  private def createTable(using DbCon): Unit =
    sql"""CREATE TABLE IF NOT EXISTS docs (
            id SERIAL PRIMARY KEY,
            url TEXT NOT NULL,
            content TEXT NOT NULL
          )""".update.run()

  private def checkDocsCount(using DbCon): Int =
    sql"SELECT COUNT(*) FROM docs".query[Int].run().head

  private def insertRow(url: String, content: String)(using DbCon): Unit =
    sql"""INSERT INTO docs (url, content)
          VALUES ($url, $content)""".update.run()

  private def createEmbeddingsTable(using DbCon): Unit =
    sql"""CREATE TABLE IF NOT EXISTS docs_embeddings AS
          SELECT id, url, content, pgml.embed('intfloat/e5-small', 'passage: ' || content)::vector(384) AS embedding
          FROM docs""".update.run()

  private def readFileFromResource(file: String): String =
    scala.util.Using(scala.io.Source.fromResource(file))(_.mkString).get

  private def readAllDocs(listOfFiles: List[String], baseUrl: String = localDocsRoot): Iterator[(String, String)] =
    listOfFiles.iterator.map { file =>
      val content = readFileFromResource(file)
      val url = s"$baseUrl${file.stripSuffix(".md")}"
      url -> content
    }

  private def createPgVectorExtension(using DbCon) =
    sql"""CREATE EXTENSION IF NOT EXISTS vector""".update.run()

  def runMigrations(using conn: DbCon, conf: Config) =
    createPgVectorExtension

    scribe.info("creating docs table")
    createTable
    scribe.info("done")

    scribe.info("checking docs count")
    if checkDocsCount == 0 then
      scribe.info(s"populating docs table, base url: ${conf.docsBaseUrl}")

      for (url, content) <- readAllDocs(files, conf.docsBaseUrl) do insertRow(url, content)

    scribe.info("done")

    scribe.info("creating embeddings table")
    createEmbeddingsTable
    scribe.info("done")
