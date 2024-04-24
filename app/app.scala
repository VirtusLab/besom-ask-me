package app

import besom.cfg.*

case class Config(port: Int, openAIApiKey: String, jdbcUrl: String, docsBaseUrl: String) derives Configured

@main def main() =
  given Config = resolveConfiguration[Config]

  given db: Db = Db()

  db.withDbConn {
    Migrations.runMigrations

    // to pre-warm the postgresml's e5-small model
    db.queryEmbeddings("How does lifting work?").get
  }

  Http.startServer()
