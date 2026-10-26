package io.github.xavitje.signcoach.domain.print

/** Eén inlogkaartje: voornaam en pincode (null = nog geen leesbare pincode, docent vult hem in). */
data class LoginCard(val firstName: String, val pin: String?)

/**
 * Maakt een A4-document met inlogkaartjes voor een klas (3 naast elkaar, om uit te knippen).
 * Elke leerling krijgt een kaartje met de klascode, zijn naam en pincode, en de drie stappen
 * om in te loggen.
 */
object LoginCards {
    fun html(className: String, classCode: String, cards: List<LoginCard>): String {
        val items = cards.joinToString("\n") { card ->
            """
            <div class="card">
              <div class="brand">SignCoach</div>
              <div class="row"><span>Klascode</span><b class="mono">${escape(classCode)}</b></div>
              <div class="row"><span>Naam</span><b>${escape(card.firstName)}</b></div>
              <div class="row"><span>Pincode</span><b class="mono">${card.pin?.let(::escape) ?: "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"}</b></div>
              <div class="steps">Open SignCoach &rarr; <b>Ik ben een leerling</b> &rarr; klascode, naam, pincode</div>
            </div>
            """.trimIndent()
        }
        return """
            <!doctype html>
            <html lang="nl"><head><meta charset="utf-8"><title>Inlogkaartjes ${escape(className)}</title>
            <style>
              @page { size: A4; margin: 12mm; }
              body { font-family: -apple-system, 'Segoe UI', Roboto, sans-serif; color: #241B3A; margin: 0; }
              h1 { font-size: 16pt; margin: 0 0 4mm; }
              .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4mm; }
              .card { border: 1.5px dashed #6C6383; border-radius: 4mm; padding: 4mm 5mm; break-inside: avoid; page-break-inside: avoid; }
              .brand { font-weight: 800; font-size: 13pt; color: #2D2350; margin-bottom: 2mm; }
              .row { display: flex; justify-content: space-between; align-items: baseline; border-bottom: 1px solid #C9BEDD; padding: 1.5mm 0; font-size: 10.5pt; }
              .row span { color: #4A3B72; }
              .mono { font-family: 'Courier New', monospace; font-size: 13pt; letter-spacing: 1px; }
              .steps { font-size: 8.5pt; color: #4A3B72; margin-top: 2mm; line-height: 1.35; }
            </style></head>
            <body>
              <h1>Inlogkaartjes &middot; ${escape(className)}</h1>
              <div class="grid">
            $items
              </div>
            </body></html>
        """.trimIndent()
    }

    fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
