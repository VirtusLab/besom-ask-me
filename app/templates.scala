package app

object Templates:
  def index(): String =
    s"""
       |<!DOCTYPE html>
       |<html lang="en">
       |
       |<head>
       |    <meta charset="UTF-8">
       |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |    <title>Besom Docs Assistant</title>
       |    <link href="https://cdn.jsdelivr.net/npm/tailwindcss@2.2.19/dist/tailwind.min.css" rel="stylesheet">
       |    <link rel="preconnect" href="https://fonts.googleapis.com">
       |    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
       |    <link href="https://fonts.googleapis.com/css2?family=Open+Sans:wght@300&display=swap" rel="stylesheet">
       |    <!-- htmx fix for: https://github.com/bigskysoftware/htmx/issues/566 -->
       |    <style>
       |        h1 {
       |            font-family: 'Open Sans', sans-serif;
       |        }
       |        .htmx-indicator {
       |          opacity: 0;
       |        }
       |        .htmx-request .htmx-indicator {
       |          opacity: 1;
       |        }
       |    </style>
       |</head>
       |
       |<body class="bg-zinc-100 flex items-center justify-center h-screen">
       |    <div class="max-w-3xl">
       |        <h1 class="text-center text-6xl font-bold mb-8">Ask me about Besom:</h1>
       |        <div class="relative">
       |          <form>
       |            <div class="relative w-full">
       |              <input
       |                type="text"
       |                class="w-full p-4 rounded border focus:border-red-500 focus:outline-none pr-10"
       |                name="q"
       |                id="q"
       |                placeholder="your inquiry about Besom goes here..."
       |                hx-post="/inquire" 
       |                hx-trigger="keyup changed delay:1000ms" 
       |                hx-target="#response" 
       |                hx-swap="innerHTML"
       |                hx-indicator="#spinner"
       |              />
       |              <img id="spinner" class="htmx-indicator absolute right-3.5 top-1/2 transform -translate-y-1/2" src="/static/bars.svg"/>
       |            </div>
       |          </form>
       |        </div>
       |        <br/>
       |        <div id="response">
       |        </div>
       |    </div>
       |    <script 
       |      src="https://unpkg.com/htmx.org@1.9.6" 
       |      integrity="sha384-FhXw7b6AlE/jyjlZH5iHa/tTe9EpJ1Y55RjcgPbjeWMskSxZt1v9qkxLJWNJaGni" 
       |    crossorigin="anonymous"></script>
       |</body>
       |</html>
       """.stripMargin

  def response(response: String): String =
    s"""
       |        <div class="mx-auto p-4 border rounded bg-white">
       |            <p>$response</p>
       |        </div>
       |""".stripMargin
