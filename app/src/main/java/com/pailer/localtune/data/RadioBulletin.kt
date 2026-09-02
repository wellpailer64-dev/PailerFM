package com.pailer.localtune.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

enum class RadioBulletinMode {
    Off,
    Headlines,
    Dialogue,
}

enum class RadioBulletinDuration(val seconds: Int) {
    Short(20),
    Normal(30),
    Long(45),
}

data class RadioBulletinSettings(
    val mode: RadioBulletinMode = RadioBulletinMode.Dialogue,
    val songsBetweenBulletins: Int = 3,
    val preferLocalWriter: Boolean = true,
)

data class NewsStory(
    val title: String,
    val source: String,
    val summary: String = "",
)

data class RadioScriptLine(
    val speaker: RadioSpeaker,
    val text: String,
)

// Nomes dos slots preservados por compatibilidade com o pacote de voz (femaleVits/maleVits,
// femaleSpeakerId/maleSpeakerId em RadioVoicePackageRepository/LocalRadioVoiceEngine) - os dois
// hoje apontam pra vozes masculinas do pacote Kokoro instalado (ver ADR-014), o rotulo "Female"
// e so o nome do slot A, nao implica genero da voz carregada.
enum class RadioSpeaker {
    Female,
    Male,
}

data class RadioScript(
    val story: NewsStory,
    val lines: List<RadioScriptLine>,
    val source: RadioScriptSource,
) {
    val displayText: String
        get() = story.title

    // Usado so pelo fallback de TTS do sistema, que fala com uma unica voz (nao alterna por
    // locutor); incluir o rotulo aqui so fazia a voz ler "Locutora:"/"Locutor:" em voz alta.
    val spokenText: String
        get() = lines.joinToString(" ") { it.text }
}

data class RadioLastPlayedTrack(
    val title: String,
    val artist: String = "",
)

fun RadioScript.withLastPlayedIntro(track: RadioLastPlayedTrack?): RadioScript {
    val playedTrack = track ?: return this
    val cleanTitle = playedTrack.title.toRadioSentence().limitWords(14)
    if (cleanTitle.isBlank() || lines.isEmpty()) return this
    val cleanArtist = playedTrack.artist.toRadioSentence().limitWords(8)
    val intro = if (cleanArtist.isBlank()) {
        "Você acaba de ouvir $cleanTitle, e vamos às notícias."
    } else {
        "Você acaba de ouvir $cleanTitle, de $cleanArtist, e vamos às notícias."
    }
    return copy(lines = lines.mapIndexed { index, line ->
        if (index == 0) line.copy(text = "$intro ${line.text}") else line
    })
}

enum class RadioScriptSource {
    LocalLlm,
    Fallback,
}

data class LocalRadioWriterStatus(
    val isInstalled: Boolean = false,
    val modelName: String = "Redator local",
    val detail: String = "Pacote de LLM ainda nao instalado",
)

data class RadioScriptContext(
    val radioName: String,
    val duration: RadioBulletinDuration,
)

interface RadioScriptWriter {
    suspend fun write(story: NewsStory, context: RadioScriptContext): RadioScript
}

class RadioBulletinRepository(context: Context) {
    private val newsRepository = NewsBulletinRepository(context)
    private val localWriter = OptionalLocalLlmRadioScriptWriter(context)
    private val fallbackWriter = FallbackRadioScriptWriter()

    fun localWriterStatus(): LocalRadioWriterStatus = localWriter.status()

    suspend fun loadScripts(settings: RadioBulletinSettings, radioName: String): List<RadioScript> {
        if (settings.mode == RadioBulletinMode.Off) return emptyList()
        val stories = newsRepository.loadStories()
        return stories.map { story ->
            // Duracao nao e mais escolha do usuario: cada materia decide sozinha quanto da pra
            // render (quanto mais resumo/gancho tiver, mais fala) - ver pickDuration().
            val scriptContext = RadioScriptContext(radioName = radioName, duration = pickDuration(story))
            when (settings.mode) {
                RadioBulletinMode.Off -> null
                RadioBulletinMode.Headlines -> fallbackWriter.writeHeadline(story, scriptContext)
                RadioBulletinMode.Dialogue -> fallbackWriter.write(story, scriptContext)
            }
        }.filterNotNull()
    }

    suspend fun loadLiveTestScript(settings: RadioBulletinSettings, radioName: String): RadioScript? {
        if (settings.mode == RadioBulletinMode.Off) return null
        val story = newsRepository.loadStories().firstOrNull() ?: return null
        val context = RadioScriptContext(radioName = radioName, duration = pickDuration(story))
        val baseScript = when (settings.mode) {
            RadioBulletinMode.Off -> return null
            RadioBulletinMode.Headlines -> fallbackWriter.writeHeadline(story, context)
            RadioBulletinMode.Dialogue -> fallbackWriter.write(story, context)
        }
        return enhanceScript(baseScript, settings, radioName)
    }

    suspend fun enhanceScript(script: RadioScript, settings: RadioBulletinSettings, radioName: String): RadioScript {
        if (settings.mode != RadioBulletinMode.Dialogue || !settings.preferLocalWriter) return script
        if (!localWriter.status().isInstalled) {
            Log.d(TAG_RADIO_WRITER, "redator local ausente; usando roteiro base")
            return script
        }
        val context = RadioScriptContext(radioName = radioName, duration = pickDuration(script.story))
        Log.d(TAG_RADIO_WRITER, "redator local iniciando para '${script.story.title}'")
        return runCatching {
            localWriter.write(script.story, context)
        }.onSuccess {
            Log.d(TAG_RADIO_WRITER, "redator local gerou ${it.lines.size} falas para '${script.story.title}'")
        }.onFailure {
            Log.w(TAG_RADIO_WRITER, "redator local falhou; usando roteiro base para '${script.story.title}'", it)
        }.getOrDefault(script)
    }

    // Materia rica (resumo longo e/ou com numero/valor concreto pra comentar) rende bate-bola
    // mais longo; manchete seca sem resumo fica curta - nao da pra fingir profundidade que a
    // fonte nao tem.
    private fun pickDuration(story: NewsStory): RadioBulletinDuration {
        val summaryLength = story.summary.trim().length
        val hasHook = extractHook("${story.title} ${story.summary}") != null
        return when {
            summaryLength >= 140 && hasHook -> RadioBulletinDuration.Long
            summaryLength >= 60 -> RadioBulletinDuration.Normal
            else -> RadioBulletinDuration.Short
        }
    }

}

private class OptionalLocalLlmRadioScriptWriter(
    private val context: Context,
) : RadioScriptWriter {
    private val packageRepository = RadioWriterPackageRepository(context)

    fun status(): LocalRadioWriterStatus {
        val status = packageRepository.status()
        return LocalRadioWriterStatus(
            isInstalled = status.isInstalled,
            modelName = status.packageName,
            detail = if (status.isInstalled) {
                "${status.modelName}. ${status.detail}"
            } else {
                status.detail
            },
        )
    }

    override suspend fun write(story: NewsStory, context: RadioScriptContext): RadioScript =
        withContext(Dispatchers.Default) {
            val config = packageRepository.config() ?: error("Pacote de redator local ausente")
            val generated = withTimeoutOrNull(LOCAL_WRITER_TIMEOUT_MS) {
                LocalLlamaTextGenerator.generate(config, buildPrompt(story, context))
            } ?: error("Redator local demorou demais")
            RadioScript(
                story = story,
                source = RadioScriptSource.LocalLlm,
                lines = parseGeneratedLines(generated),
            )
        }

    private fun buildPrompt(story: NewsStory, context: RadioScriptContext): String {
        val title = story.title.toRadioSentence().limitWords(22)
        val summary = story.summary.ifBlank { "Sem resumo disponível." }.toRadioSentence().limitWords(55)
        return """
            <|im_start|>system
            Roteirista da Pailer FM. PT-BR correto. Responda só JSON válido:
            [{"speaker":"Female","text":"..."},{"speaker":"Male","text":"..."}]
            Use exatamente 4 falas curtas:
            1 Female/Frankie: notícia.
            2 Male/Nicky: detalhe da matéria e leitura crítica.
            3 Female/Frankie: provocação esperançosa, sem ingenuidade.
            4 Male/Nicky: contra provocação e volta para a Rádio ${context.radioName}.
            Nicky conhece capitalismo, imperialismo, lobby e corporativismo, mas não cite isso toda hora.
            Não invente fatos. Máximo 18 palavras por fala.
            <|im_end|>
            <|im_start|>user
            Fonte: ${story.source}
            Título: $title
            Resumo: $summary
            /no_think
            <|im_end|>
            <|im_start|>assistant
            <think>

            </think>

            [
        """.trimIndent()
    }

    private fun parseGeneratedLines(generated: String): List<RadioScriptLine> {
        val json = generated.substringAfter('[').substringBeforeLast(']', "").let {
            if (it.isBlank()) generated else "[$it]"
        }
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val speaker = when (item.optString("speaker")) {
                "Female" -> RadioSpeaker.Female
                "Male" -> RadioSpeaker.Male
                else -> return@mapNotNull null
            }
            val text = item.optString("text").toRadioSentence().ensureFinalPeriod()
            if (text.isBlank()) null else RadioScriptLine(speaker, text.limitWords(32))
        }.takeIf { lines ->
            lines.size >= 4 && lines.firstOrNull()?.speaker == RadioSpeaker.Female
        } ?: error("Redator local devolveu roteiro inválido")
    }

    private companion object {
        // 02/09/2026: decode real medido em ~27s neste aparelho (Dimensity 1200, 3 threads,
        // Qwen3 1.7B Q4_K_M) - ver ADR-002. Timeout antigo de 35s já dava folga, o gargalo real
        // era o timeout nativo (ver LOCAL_WRITER_NATIVE_TIMEOUT_MS). Mantido com folga generosa
        // porque prepareUpcomingBulletin() roda em background durante a música, não bloqueia nada.
        const val LOCAL_WRITER_TIMEOUT_MS = 55_000L
    }
}

// Os dois locutores fixos do bate-bola (ver ADR-014). Nomes dos personagens, nao dos slots de
// voz - "Frankie" fala pelo slot Female, "Nicky" pelo slot Male, os dois com vozes masculinas
// do pacote Kokoro instalado. Se chamam pelo nome durante o boletim de proposito (pedido do
// usuario, "elemento surpresa" dos nomes).
private const val FRANKIE = "Frankie"
private const val NICKY = "Nicky"
private const val TAG_RADIO_WRITER = "PailerRadioWriter"

private enum class NewsTopic {
    CIENCIA_TECNOLOGIA,
    POLITICA,
    ECONOMIA,
    MUNDO_CONFLITO,
    SAUDE,
    CULTURA_POP,
    ESPORTE,
    CLIMA_NATUREZA,
    CURIOSIDADE,
    GERAL,
}

// Opiniao (inventada, mas sempre amarrada ao tema) de cada personagem sobre o assunto do
// boletim - e o que faz o "comentario" parecer que leu e entendeu a materia em vez de reagir
// generico igual pra qualquer noticia.
private data class TopicBank(val optimist: List<String>, val pessimist: List<String>)

private val TOPIC_BANK: Map<NewsTopic, TopicBank> = mapOf(
    NewsTopic.POLITICA to TopicBank(
        optimist = listOf(
            "eu ainda acho que pressão pública bem feita arranca alguma coisa desse tabuleiro viciado.",
            "dá para desconfiar do poder e, mesmo assim, cobrar saída concreta.",
            "quando gente comum presta atenção, o roteiro oficial pelo menos precisa suar mais.",
        ),
        pessimist = listOf(
            "política institucional adora vender crise como inevitável e acordo de bastidor como maturidade.",
            "quando o poder explica demais, quase sempre tem alguém lucrando no silêncio.",
            "isso tem cheiro de manutenção do mesmo jogo, só trocaram a iluminação do palco.",
        ),
    ),
    NewsTopic.ECONOMIA to TopicBank(
        optimist = listOf(
            "se essa conta for disputada direito, trabalhador também consegue arrancar fôlego.",
            "número econômico só presta quando melhora vida real, e é isso que tem que ser cobrado.",
            "até no mercado mais frio existe brecha quando a conversa sai da planilha e vai para a rua.",
        ),
        pessimist = listOf(
            "capital chama de ajuste o que, para gente comum, chega como aluguel, comida e ansiedade.",
            "mercado comemora antes porque quase nunca é ele que sangra depois.",
            "no capitalismo tardio, até alívio vem com taxa de administração.",
        ),
    ),
    NewsTopic.CIENCIA_TECNOLOGIA to TopicBank(
        optimist = listOf(
            "tecnologia com controle público e cabeça humana ainda pode virar ferramenta de libertação.",
            "quando pesquisa séria escapa do marketing, ela consegue melhorar a vida de verdade.",
            "avanço técnico não precisa servir só investidor; também pode servir gente cansada.",
        ),
        pessimist = listOf(
            "toda novidade tecnológica chega prometendo futuro e escondendo a planilha de demissões.",
            "big tech chama vigilância de experiência personalizada e ainda cobra assinatura.",
            "se a máquina aprende tudo sobre nós, alguém está transformando vida privada em ativo.",
        ),
    ),
    NewsTopic.SAUDE to TopicBank(
        optimist = listOf(
            "saúde pública forte ainda é uma das poucas ideias civilizadas que esse mundo produziu.",
            "quando ciência chega sem virar luxo, aí sim dá para comemorar de peito aberto.",
            "cuidar de gente deveria ser prioridade, não produto premium.",
        ),
        pessimist = listOf(
            "o problema é quando cura vira portfólio e sofrimento vira mercado recorrente.",
            "eu só acredito no avanço quando ele chega antes no posto do que na reunião de acionistas.",
            "saúde tratada como negócio sempre encontra um jeito elegante de deixar alguém do lado de fora.",
        ),
    ),
    NewsTopic.CULTURA_POP to TopicBank(
        optimist = listOf(
            "cultura ainda abre fresta onde a propaganda queria parede lisa.",
            "mesmo no meio da indústria, às vezes aparece uma obra que fala mais alto que a marca.",
            "quando arte encontra público de verdade, nem algoritmo segura completamente.",
        ),
        pessimist = listOf(
            "indústria cultural transforma angústia em conteúdo e chama isso de conexão.",
            "fama hoje é linha de produção: viraliza, monetiza, descarta e repete.",
            "o algoritmo finge descobrir talento, mas adora mesmo é previsibilidade vendável.",
        ),
    ),
    NewsTopic.ESPORTE to TopicBank(
        optimist = listOf(
            "a beleza do esporte é que, às vezes, o corpo humano bagunça a planilha dos donos.",
            "torcida organizada pelo afeto ainda é uma coisa que o dinheiro não compra inteira.",
            "quando o jogo é bom, ele lembra que vida coletiva também pode ter alegria.",
        ),
        pessimist = listOf(
            "esporte moderno vende paixão popular e entrega camarote, bet e contrato opaco.",
            "quando o dinheiro entra demais no gramado, até o improviso precisa de patrocinador.",
            "torcedor entrega alma, dirigente entrega coletiva, e a conta nunca fecha igual para os dois.",
        ),
    ),
    NewsTopic.CLIMA_NATUREZA to TopicBank(
        optimist = listOf(
            "ainda dá para tratar clima como projeto coletivo, não como nota de rodapé.",
            "quando a reação vem antes do desastre, a humanidade até parece capaz de aprender.",
            "proteção ambiental séria é menos romantismo e mais sobrevivência organizada.",
        ),
        pessimist = listOf(
            "o planeta manda a fatura e o corporativismo tenta pagar com campanha bonita.",
            "chamam de evento extremo para não dizer que o modelo inteiro virou extremo.",
            "natureza não negocia com lobby, mas parece que ninguém avisou a sala do conselho.",
        ),
    ),
    NewsTopic.CURIOSIDADE to TopicBank(
        optimist = listOf(
            "curiosidade boa rompe o tédio industrial de um mundo que quer tudo padronizado.",
            "essas histórias lembram que a realidade ainda escapa da embalagem.",
            "quando algo estranho aparece, pelo menos o mundo admite que não cabe inteiro numa planilha.",
        ),
        pessimist = listOf(
            "até curiosidade hoje vira isca de atenção para vender anúncio no intervalo.",
            "o estranho me interessa, mas eu sempre procuro quem está empacotando o espanto.",
            "se viralizou rápido demais, alguém já deve estar medindo quanto dá para extrair disso.",
        ),
    ),
    NewsTopic.MUNDO_CONFLITO to TopicBank(
        optimist = listOf(
            "mesmo em tabuleiro imperial, diplomacia e pressão popular ainda conseguem abrir fresta.",
            "quando a história aperta, solidariedade internacional vira mais que palavra bonita.",
            "a saída decente quase nunca vem dos fortes, mas ela existe quando gente comum se recusa ao cinismo.",
        ),
        pessimist = listOf(
            "império nunca chama interesse de interesse; chama de segurança, estabilidade ou missão.",
            "geopolítica costuma ser gente poderosa movendo peças e gente comum enterrando consequência.",
            "quando potência fala em ordem mundial, eu olho logo para quem vai pagar em silêncio.",
        ),
    ),
    NewsTopic.GERAL to TopicBank(
        optimist = listOf(
            "ainda dá para arrancar sentido desse caos quando a gente olha com honestidade.",
            "nem toda estrutura vence para sempre; às vezes uma fissura pequena muda o rumo.",
            "eu sigo achando que lucidez também serve para construir, não só para reclamar.",
        ),
        pessimist = listOf(
            "quase toda manchete tem uma superfície brilhante e uma engrenagem feia trabalhando embaixo.",
            "se parece simples demais, provavelmente esconderam a cadeia de interesses no rodapé.",
            "no fim das contas, o sistema terceiriza o dano e privatiza o aplauso.",
        ),
    ),
)

// Palavras-chave por tema, checadas em titulo+resumo em minusculo. Ordem importa: a primeira
// que bater decide o tema (evita ambiguidade tipo "estudo" aparecendo num texto de politica).
// Top-level (nao dentro de um writer especifico) porque tanto o roteirista quanto
// RadioBulletinRepository.pickDuration() precisam classificar/extrair gancho da materia.
private val TOPIC_KEYWORDS: List<Pair<NewsTopic, List<String>>> = listOf(
    NewsTopic.MUNDO_CONFLITO to listOf(
        "guerra", "conflito", "ataque", "exercito", "tropas", "missil", "invasao",
        "bombardeio", "cessar-fogo", "refugiado",
    ),
    NewsTopic.POLITICA to listOf(
        "presidente", "governo", "eleicao", "eleicoes", "congresso", "senado", "camara",
        "ministro", "ministra", "stf", "politica", "deputado", "prefeito", "votacao",
    ),
    NewsTopic.ECONOMIA to listOf(
        "dolar", "inflacao", "bolsa", "juros", "economia", "imposto", "mercado", "pib",
        "orcamento", "preco", "precos", "salario",
    ),
    NewsTopic.SAUDE to listOf(
        "saude", "vacina", "doenca", "hospital", "virus", "tratamento", "remedio",
        "surto", "epidemia", "sus",
    ),
    NewsTopic.CIENCIA_TECNOLOGIA to listOf(
        "cientistas", "estudo", "pesquisa", "descoberta", "universidade", "nasa",
        "espaco", "planeta", "inteligencia artificial", "app", "robo", "tecnologia",
        "celular", "internet", "software", "hacker", "aplicativo",
    ),
    NewsTopic.CULTURA_POP to listOf(
        "filme", "serie", "musica", "cantor", "cantora", "ator", "atriz", "celebridade",
        "famoso", "show", "novela", "streaming",
    ),
    NewsTopic.ESPORTE to listOf(
        "jogo", "time", "campeonato", "gol", "copa", "atleta", "olimpiad", "selecao",
        "futebol", "clube",
    ),
    NewsTopic.CLIMA_NATUREZA to listOf(
        "clima", "chuva", "calor", "temperatura", "furacao", "seca", "enchente",
        "terremoto", "tempestade", "aquecimento",
    ),
    NewsTopic.CURIOSIDADE to listOf(
        "bizarro", "curios", "insolito", "recorde", "raro", "inusitado", "misterio",
        "estranho",
    ),
)

// TOPIC_KEYWORDS fica sem acento de proposito: normaliza os dois lados (chave e texto real do
// RSS, que vem acentuado) antes de comparar, em vez de manter uma lista acentuada na mao que
// quebra silenciosamente toda vez que "politica" (chave) precisa bater com "política" (texto
// de verdade) - "í" e "i" nao sao o mesmo caractere numa comparacao de substring direta.
private fun stripDiacritics(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

private fun classifyTopic(text: String): NewsTopic {
    val lower = stripDiacritics(text.lowercase())
    for ((topic, keywords) in TOPIC_KEYWORDS) {
        if (keywords.any { it in lower }) return topic
    }
    return NewsTopic.GERAL
}

// Primeiro numero "interessante" do texto (percentual, dinheiro ou contagem grande) pra dar
// uma referencia concreta na fala em vez de comentario generico solto no ar; tambem usado
// como sinal de que a materia tem substancia suficiente pra render um boletim mais longo.
private fun extractHook(text: String): String? {
    Regex("\\d+[.,]?\\d*\\s?%").find(text)?.let { return it.value }
    Regex("R\\$\\s?\\d+[.,]?\\d*\\s?(mil|milh(?:ões|oes|ão|ao)|bilh(?:ões|oes|ão|ao))?", RegexOption.IGNORE_CASE)
        .find(text)?.let { return it.value.trim() }
    Regex("\\b\\d{2,}\\b").find(text)?.let { return it.value }
    return null
}

private fun buildMatterBrief(story: NewsStory): String {
    val summary = story.summary.toRadioSentence()
    val title = story.title.toRadioSentence()
    val base = summary.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
        ?: "a matéria aponta para ${title.lowercaseFirstWord()}."
    return base.limitWords(28).ensureFinalPeriod()
}

private fun String.lowercaseFirstWord(): String =
    replaceFirstChar { if (it.isUpperCase()) it.lowercaseChar() else it }

private fun String.ensureFinalPeriod(): String =
    trim().let { text ->
        if (text.isBlank() || text.last() in ".!?") text else "$text."
    }

private class FallbackRadioScriptWriter : RadioScriptWriter {
    override suspend fun write(story: NewsStory, context: RadioScriptContext): RadioScript =
        withContext(Dispatchers.Default) {
            RadioScript(
                story = story,
                source = RadioScriptSource.Fallback,
                lines = buildDialogueLines(story, context),
            )
        }

    suspend fun writeHeadline(story: NewsStory, context: RadioScriptContext): RadioScript =
        withContext(Dispatchers.Default) {
            val cleanTitle = story.title.toRadioSentence()
            val topic = classifyTopic("${story.title} ${story.summary}")
            RadioScript(
                story = story,
                source = RadioScriptSource.Fallback,
                lines = listOf(
                    RadioScriptLine(
                        RadioSpeaker.Female,
                        "$cleanTitle. ${HEADLINE_TAGS.forTopic(topic).random()}",
                    ),
                    RadioScriptLine(
                        RadioSpeaker.Female,
                        "${CLOSING_LINES.random()} Agora voltamos para a Rádio ${context.radioName}.",
                    ),
                ),
            )
        }

    // Monta o dialogo de propósito, por duracao, em vez de gerar tudo e cortar no limite de
    // palavras (fitFor): assim os dois locutores sempre tem o mesmo numero de falas, nunca um
    // sozinho porque a fala do outro estourou o orcamento de palavras.
    private fun buildDialogueLines(story: NewsStory, context: RadioScriptContext): List<RadioScriptLine> {
        val cleanTitle = story.title.toRadioSentence()
        val corpus = "${story.title} ${story.summary}"
        val topic = classifyTopic(corpus)
        val hook = extractHook(corpus)
        val bank = TOPIC_BANK.forTopic(topic)
        val matterBrief = buildMatterBrief(story)

        // Titulo limitado antes de entrar no template: manchete real pode ter 20+ palavras, e
        // isso sozinho ja estourava o orcamento do modo Curto e deixava o Nicky sem fala (o
        // fitFor cortava a segunda linha inteira em vez de so aparar a manchete).
        val opener = RadioScriptLine(
            RadioSpeaker.Female,
            "$NICKY, ${OPENERS.random().format(cleanTitle.limitWords(16))}",
        )
        val nickyExplains = RadioScriptLine(
            RadioSpeaker.Male,
            "$FRANKIE, deixa eu traduzir sem release de assessoria: $matterBrief ${bank.pessimist.random()}",
        )
        val frankieProvokes = RadioScriptLine(
            RadioSpeaker.Female,
            "$NICKY, dá para enxergar o jogo sujo sem entregar a alma para o cinismo. ${bank.optimist.random()}",
        )
        val nickyCounterProvokes = RadioScriptLine(
            RadioSpeaker.Male,
            "$FRANKIE, otimismo sem análise material vira propaganda. ${hook?.let { HOOK_CALLOUTS.random().format(it) } ?: PESSIMIST_PUNCHLINES.random()}",
        )
        val extraContext = RadioScriptLine(
            RadioSpeaker.Male,
            "${NICKY_DETAILS.forTopic(topic).random()} ${hook?.let { "E esse detalhe de $it não entrou aí por acaso." } ?: "É nesse detalhe que a notícia pesa de verdade."}",
        )
        val musicCall = RadioScriptLine(
            RadioSpeaker.Female,
            "$NICKY, segue o mundo torto, segue a nossa trilha. Agora a música volta na Rádio ${context.radioName}.",
        )

        val lines = when (context.duration) {
            RadioBulletinDuration.Short -> listOf(opener, nickyExplains, frankieProvokes, nickyCounterProvokes, musicCall)
            RadioBulletinDuration.Normal -> listOf(opener, nickyExplains, frankieProvokes, nickyCounterProvokes, musicCall)
            RadioBulletinDuration.Long -> listOf(opener, nickyExplains, extraContext, frankieProvokes, nickyCounterProvokes, musicCall)
        }
        return lines.fitFor(context.duration)
    }

    private fun List<RadioScriptLine>.fitFor(duration: RadioBulletinDuration): List<RadioScriptLine> {
        val maxWords = when (duration) {
            RadioBulletinDuration.Short -> 155
            RadioBulletinDuration.Normal -> 190
            RadioBulletinDuration.Long -> 235
        }
        var words = 0
        return mapNotNull { line ->
            val allowed = maxWords - words
            if (allowed <= 0) return@mapNotNull null
            val trimmed = line.text.limitWords(allowed)
            words += trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            line.copy(text = trimmed)
        }
    }

    private fun <T> Map<NewsTopic, T>.forTopic(topic: NewsTopic): T =
        this[topic] ?: getValue(NewsTopic.GERAL)

    private companion object {
        // Abre o boletim: Frankie parafraseia a manchete pro Nicky, tom de quem ja leu e ja
        // tem gancho pra piada (setup de comedia, o "explica a materia" que da o contexto real
        // pro resto do dialogo).
        val OPENERS = listOf(
            "para tudo que eu vi: %s. Presta atenção nessa.",
            "olha o que me contaram: %s. Você não vai acreditar.",
            "deixa eu te contar: %s. Isso aqui é hoje.",
            "cola aqui: %s. Juro que não inventei.",
            "manchete fresquinha: %s. Segura essa.",
            "%s. Já pensou nisso?",
        )

        val HOOK_CALLOUTS = listOf(
            "reparou no número? %s. Isso sozinho já diz tudo.",
            "%s. Bota na conta e vê se fecha.",
            "olha o detalhe: %s. Ninguém repara, eu reparo.",
        )

        val NICKY_DETAILS: Map<NewsTopic, List<String>> = mapOf(
            NewsTopic.POLITICA to listOf(
                "Quando política vira manchete, eu procuro a coalizão, o financiador e quem some da foto.",
                "O resumo é simples: decisão pública mexe com vida real, não só com palanque e frase ensaiada.",
            ),
            NewsTopic.ECONOMIA to listOf(
                "Na economia, o número bonito costuma chegar primeiro no gráfico e bem depois na geladeira.",
                "Quando falam de mercado, eu quero saber onde isso aperta no bolso de quem vende tempo para sobreviver.",
            ),
            NewsTopic.CIENCIA_TECNOLOGIA to listOf(
                "Tecnologia boa precisa resolver problema humano, não só capturar dado e render apresentação bonita.",
                "A parte importante é separar avanço real de promessa embrulhada em brilho para agradar investidor.",
            ),
            NewsTopic.SAUDE to listOf(
                "Em saúde, manchete boa só vira vitória quando chega no atendimento de verdade, não só no pitch da indústria.",
                "O detalhe é saber se isso melhora a vida das pessoas ou só melhora o release e a margem.",
            ),
            NewsTopic.CULTURA_POP to listOf(
                "Na cultura pop, o barulho conta metade da história; a outra metade é quem lucra com ele.",
                "Fama parece leve, mas sempre tem contrato, agenda, plataforma e reputação por trás.",
            ),
            NewsTopic.ESPORTE to listOf(
                "No esporte, todo lance vem com placar, pressão, patrocinador e alguém fingindo que estava tudo calculado.",
                "O detalhe é que resultado bonito também cobra conta na próxima rodada, no corpo do atleta e no bolso do torcedor.",
            ),
            NewsTopic.CLIMA_NATUREZA to listOf(
                "Quando o assunto é clima, o planeta não está opinando; está mandando recibo de um modelo predatório.",
                "Natureza não negocia com coletiva de imprensa, lobby ou relatório de sustentabilidade bonito.",
            ),
            NewsTopic.CURIOSIDADE to listOf(
                "Curiosidade parece pequena, mas costuma revelar como o mundo funciona por baixo do verniz.",
                "Essas histórias estranhas são boas porque mostram o detalhe que a narrativa oficial deixou passar.",
            ),
            NewsTopic.MUNDO_CONFLITO to listOf(
                "Em conflito internacional, toda frase diplomática carrega recurso, rota, indústria e gente comum no rodapé.",
                "O mapa parece distante até a consequência bater na porta de alguém que nunca participou da reunião.",
            ),
            NewsTopic.GERAL to listOf(
                "O ponto é olhar menos para o susto da manchete e mais para a consequência material.",
                "Toda notícia tem uma superfície e uma conta escondida; eu sempre procuro quem paga essa conta.",
            ),
        )

        val PESSIMIST_PUNCHLINES = listOf(
            "no fim das contas, socializam o prejuízo e chamam o lucro de mérito.",
            "aposto o troco que isso vira case de sucesso antes de virar solução.",
            "isso aí tem cheiro de captura corporativa, e esse cheiro eu reconheço de longe.",
            "quando a história vem polida demais, eu procuro a sujeira atrás do balcão.",
        )

        val CLOSING_LINES = listOf(
            "Fica a dica.",
            "Por hoje é só, mas a gente volta com mais.",
            "Guarda essa aí pra contar pros amigos.",
            "Sigam ligados, o mundo não para de surpreender.",
        )

        val HEADLINE_TAGS: Map<NewsTopic, List<String>> = mapOf(
            NewsTopic.POLITICA to listOf("Política de novo dando o que falar.", "Isso aí vai render."),
            NewsTopic.ECONOMIA to listOf("O bolso sentindo essa.", "Fica de olho no seu bolso."),
            NewsTopic.CIENCIA_TECNOLOGIA to listOf("Ciência e tecnologia aprontando de novo.", "O futuro chegando sem avisar."),
            NewsTopic.SAUDE to listOf("Fica de olho na sua saúde.", "Vale anotar essa."),
            NewsTopic.CULTURA_POP to listOf("O mundo da fama sempre com novidade.", "Essa vai parar nos grupos."),
            NewsTopic.ESPORTE to listOf("O esporte sempre dá um jeito de surpreender.", "Torcedor vai comentar essa."),
            NewsTopic.CLIMA_NATUREZA to listOf("A natureza fazendo a sua parte.", "Vale se programar por causa dessa."),
            NewsTopic.CURIOSIDADE to listOf("Essa é daquelas pra contar por aí.", "Duvido você não comentar essa depois."),
            NewsTopic.MUNDO_CONFLITO to listOf("Mundo tenso, fica ligado.", "Isso aí merece atenção."),
            NewsTopic.GERAL to listOf("Fica a dica.", "Vale guardar essa."),
        )
    }
}

private fun String.toRadioSentence(): String =
    replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', '!', '?')

private fun String.limitWords(limit: Int): String {
    if (limit <= 0) return ""
    val words = split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (words.size <= limit) this else words.take(limit).joinToString(" ").trimEnd(',', ';', ':') + "."
}
