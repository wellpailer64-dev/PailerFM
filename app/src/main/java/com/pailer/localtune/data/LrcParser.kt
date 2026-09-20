package com.pailer.localtune.data

// Parser de letra em formato LRC (com marcas de tempo) OU texto puro. Funcao pura, sem imports
// Android nem org.json - da pra testar direto (ver docs/TODO.md P2, alvo de teste unitario).
//
// LRC de exemplo:
//   [ti:Titulo]
//   [ar:Artista]
//   [offset:+200]
//   [00:12.34]Primeira linha
//   [00:15.00][01:04.00]Refrao que repete
//
// Texto puro: cada linha nao vazia vira uma LyricsLine(timeMs = null).
object LrcParser {

    // [mm:ss], [mm:ss.xx] (centesimos) ou [mm:ss.xxx] (milis). Aceita ':' no lugar do '.'.
    private val TIME_TAG = Regex("""\[(\d{1,3}):([0-5]?\d)(?:[.:](\d{1,3}))?]""")

    // Linha SO de metadado: [ar:...], [ti:...], [al:...], [length:...], [by:...], [offset:...] etc.
    private val META_LINE = Regex("""^\[[a-zA-Z]{1,10}:[^]]*]$""")

    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d{1,6})\s*]""", RegexOption.IGNORE_CASE)

    /**
     * Parseia LRC ou texto puro em linhas ordenadas por tempo. Texto puro (sem nenhuma tag de
     * tempo) devolve uma LyricsLine(null, ...) por linha nao vazia, na ordem original.
     */
    fun parse(raw: String): List<LyricsLine> {
        if (raw.isBlank()) return emptyList()
        val offsetMs = parseOffsetMs(raw)
        val rawLines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        val timed = mutableListOf<LyricsLine>()
        var sawAnyTimeTag = false
        val untimedFallback = mutableListOf<LyricsLine>()

        for (line in rawLines) {
            val trimmed = line.trim()
            val matches = TIME_TAG.findAll(line).toList()
            if (matches.isNotEmpty()) {
                sawAnyTimeTag = true
                val text = line.replace(TIME_TAG, "").trim()
                for (m in matches) {
                    val timeMs = tagToMs(m) + offsetMs
                    timed += LyricsLine(timeMs.coerceAtLeast(0L), text)
                }
            } else if (trimmed.isNotEmpty() && !META_LINE.matches(trimmed)) {
                untimedFallback += LyricsLine(null, trimmed)
            }
        }

        if (!sawAnyTimeTag) return untimedFallback

        // LRC misto: mantem tambem as linhas soltas sem tempo, mas no fim (nao da pra posicionar).
        val ordered = timed.sortedBy { it.timeMs ?: Long.MAX_VALUE }
        return ordered + untimedFallback
    }

    fun isSynced(lines: List<LyricsLine>): Boolean = lines.any { it.timeMs != null }

    /**
     * Texto plano pro editor e pro render sem sync: tira as tags de tempo, descarta linhas de
     * metadado, mantem as linhas em branco (espacamento entre estrofes).
     */
    fun flattenToPlain(raw: String): String {
        if (raw.isBlank()) return ""
        val out = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .map { it.replace(TIME_TAG, "").trim() }
            .filterNot { META_LINE.matches(it) }
            .joinToString("\n")
        // Colapsa 3+ quebras seguidas em no maximo uma linha em branco.
        return out.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /**
     * Indice da ultima linha cujo `timeMs` <= `positionMs`. -1 antes da primeira marca de tempo.
     * `lines` e assumido ordenado por tempo (as sem tempo, no fim, sao ignoradas).
     */
    fun currentLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        var result = -1
        for ((i, line) in lines.withIndex()) {
            val t = line.timeMs ?: break
            if (t <= positionMs) result = i else break
        }
        return result
    }

    // Normaliza uma linha pra comparacao (minusculo, sem pontuacao, espacos colapsados) - usado so
    // por chorusWindows() abaixo, pra "Refrão!" e "refrão" contarem como a mesma linha.
    private val NON_WORD_CHARS = Regex("[^\\p{L}\\p{N} ]")
    private val EXTRA_SPACES = Regex("\\s+")
    private fun normalizeForMatch(text: String): String =
        text.lowercase().replace(NON_WORD_CHARS, " ").replace(EXTRA_SPACES, " ").trim()

    /**
     * Heuristica de deteccao de refrao (pedido do usuario 20/09/2026, pra saber QUANDO mostrar um
     * take especial durante o refrao): so funciona com letra SINCRONIZADA (linhas com timeMs), sem
     * isso nao ha como saber o instante exato de cada trecho. Ideia: verso normalmente nao se
     * repete palavra por palavra, refrao sim - entao qualquer linha cujo texto (normalizado)
     * aparece 2+ vezes na musica e candidata a refrao. Agrupa linhas candidatas CONSECUTIVAS (sem
     * nenhuma linha "nao repetida" no meio) em blocos - cada bloco de 2+ linhas e uma OCORRENCIA do
     * refrao, e vira uma janela de tempo (inicio da 1a linha do bloco ate o inicio da linha
     * seguinte, ou +4s se for a ultima linha da musica). Retorna uma janela por ocorrencia (o
     * refrao costuma repetir 2-4x numa musica) - nao so a primeira.
     *
     * Nao e perfeito (uma linha de verso que por acaso se repete em outro verso tambem conta), mas
     * e um heuristico razoavel sem precisar de nenhum servico externo de deteccao de estrutura.
     */
    fun chorusWindows(lines: List<LyricsLine>): List<LongRange> {
        val timed = lines.filter { it.timeMs != null }.sortedBy { it.timeMs }
        if (timed.size < 4) return emptyList()

        val normalized = timed.map { normalizeForMatch(it.text) }
        val counts = normalized.filter { it.length >= 2 }.groupingBy { it }.eachCount()
        val repeatedTexts = counts.filterValues { it >= 2 }.keys
        if (repeatedTexts.isEmpty()) return emptyList()

        val windows = mutableListOf<LongRange>()
        var i = 0
        while (i < timed.size) {
            if (normalized[i] in repeatedTexts) {
                var j = i
                while (j + 1 < timed.size && normalized[j + 1] in repeatedTexts) j++
                // Bloco de 1 linha so (um "oh oh" solto repetido no meio de versos, por exemplo)
                // nao conta como refrao de verdade - exige pelo menos 2 linhas seguidas.
                if (j > i) {
                    val startMs = timed[i].timeMs!!
                    val endMs = timed.getOrNull(j + 1)?.timeMs ?: (timed[j].timeMs!! + CHORUS_TAIL_MS)
                    windows += startMs..endMs
                }
                i = j + 1
            } else {
                i++
            }
        }
        return windows
    }

    // Quanto tempo depois da ULTIMA linha do refrao a janela continua, so quando esse refrao e
    // tambem a ultima coisa cantada na musica (sem proxima linha pra marcar o fim de verdade).
    private const val CHORUS_TAIL_MS = 4_000L

    private fun tagToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val frac = match.groupValues[3]
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100L
            2 -> frac.toLong() * 10L
            else -> frac.take(3).toLong()
        }
        return (minutes * 60L + seconds) * 1000L + fracMs
    }

    private fun parseOffsetMs(raw: String): Long =
        OFFSET_TAG.find(raw)?.groupValues?.get(1)?.toLongOrNull()?.let { -it } ?: 0L
    // Convencao do LRC: offset positivo = letra deve aparecer MAIS CEDO, entao subtrai do tempo.
}
