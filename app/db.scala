package app

import com.augustnagro.magnum.*

class Db(private val ds: javax.sql.DataSource):

  def queryEmbeddings(query: String): Option[Db.QueryResult] =
    connect(ds) {
      sql"""WITH request AS (
              SELECT pgml.embed(
                'intfloat/e5-small',
                'query: ' || $query
              )::vector(384) AS query_embedding
            )
            SELECT
              id,
              url,
              content,
              1 - (
                embedding::vector <=> (SELECT query_embedding FROM request)
              ) AS cosine_similarity
            FROM docs_embeddings
            ORDER BY cosine_similarity DESC
            LIMIT 1""".query[Db.QueryResult].run().headOption
    }

  def withDbConn(f: DbCon ?=> Unit): Unit =
    connect(ds)(f)

object Db:
  import com.zaxxer.hikari.*

  case class QueryResult(id: Int, url: String, content: String, similarity: Double)

  def apply()(using Config): Db = new Db(getPostgresMLDataSource)

  def getPostgresMLDataSource(using conf: Config): javax.sql.DataSource =
    val config = new HikariConfig()
    config.setJdbcUrl(conf.jdbcUrl)
    config.setUsername("postgresml")
    config.setPassword("")

    HikariDataSource(config)
