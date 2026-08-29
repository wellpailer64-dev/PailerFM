package com.pailer.localtune.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                RadioBulletinMode.Dialogue -> writeDialogue(story, scriptContext, settings.preferLocalWriter)
            }
        }.filterNotNull()
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

    private suspend fun writeDialogue(
        story: NewsStory,
        context: RadioScriptContext,
        preferLocalWriter: Boolean,
    ): RadioScript {
        if (preferLocalWriter && localWriter.status().isInstalled) {
            runCatching { return localWriter.write(story, context) }
        }
        return fallbackWriter.write(story, context)
    }
}

private class OptionalLocalLlmRadioScriptWriter(
    private val context: Context,
) : RadioScriptWriter {
    fun status(): LocalRadioWriterStatus {
        val modelDir = context.filesDir.resolve("radio_writer")
        val installed = modelDir.resolve("model.ready").exists()
        return LocalRadioWriterStatus(
            isInstalled = installed,
            modelName = "Redator local leve",
            detail = if (installed) {
                "Pacote local encontrado"
            } else {
                "Pacote de LLM ainda nao instalado"
            },
        )
    }

    override suspend fun write(story: NewsStory, context: RadioScriptContext): RadioScript =
        withContext(Dispatchers.Default) {
            // O pacote real de LLM entra aqui depois: prompt controlado, JSON curto e timeout agressivo.
            error("Local LLM writer is not wired yet")
        }
}

// Os dois locutores fixos do bate-bola (ver ADR-014). Nomes dos personagens, nao dos slots de
// voz - "Frankie" fala pelo slot Female, "Nicky" pelo slot Male, os dois com vozes masculinas
// do pacote Kokoro instalado. Se chamam pelo nome durante o boletim de proposito (pedido do
// usuario, "elemento surpresa" dos nomes).
private const val FRANKIE = "Frankie"
private const val NICKY = "Nicky"

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
            "eu acho até bonito, sempre tem alguém tentando resolver isso direito.",
            "sabe que eu ainda acredito que dá pra consertar política com boa vontade.",
            "no fim as coisas se ajeitam, sempre foi assim por aqui.",
        ),
        pessimist = listOf(
            "político prometendo é disco arranhado, toca sempre a mesma música.",
            "isso aí vira poeira debaixo do tapete antes do fim do mês.",
            "eu já vi esse filme, e o final nunca muda.",
        ),
    ),
    NewsTopic.ECONOMIA to TopicBank(
        optimist = listOf(
            "eu boto fé que o bolso do trabalhador sai ganhando dessa vez.",
            "toda crise por aí, um espertinho enxerga oportunidade.",
            "capital de giro é coisa que vai e volta, eu não me abalo.",
        ),
        pessimist = listOf(
            "dólar sobe, feijão sobe, só meu salário que fica parado.",
            "isso aí cheira a boleto novo chegando pra mim.",
            "economia boa é a que eu nunca vi de perto.",
        ),
    ),
    NewsTopic.CIENCIA_TECNOLOGIA to TopicBank(
        optimist = listOf(
            "isso aí é o tipo de coisa que muda o jogo, eu sinto no ar.",
            "cientista trabalhando é sempre boa notícia escondida.",
            "tecnologia dessas ainda vai facilitar a vida de todo mundo.",
        ),
        pessimist = listOf(
            "toda vez que sai invenção nova, alguém perde o emprego calado.",
            "eu não confio em nada que atualiza sozinho de madrugada.",
            "tecnologia é boa até o dia que ela decide não te obedecer mais.",
        ),
    ),
    NewsTopic.SAUDE to TopicBank(
        optimist = listOf(
            "toda notícia de saúde que anda pra frente eu recebo de braços abertos.",
            "isso aí é motivo de comemorar, cuidar bem de gente é sempre vitória.",
            "eu confio no avanço, ciência da saúde nunca para de me surpreender bem.",
        ),
        pessimist = listOf(
            "toda vez que prometem cura, o preço do remédio sobe primeiro.",
            "eu só acredito quando ver funcionando no posto perto de casa.",
            "saúde boa por aqui é sorte, não regra.",
        ),
    ),
    NewsTopic.CULTURA_POP to TopicBank(
        optimist = listOf(
            "isso aí vai animar todo mundo, adoro quando a cultura dá as caras.",
            "fama boa é a que dá assunto gostoso, essa entra na lista.",
            "esse tipo de notícia é a razão de eu ainda gostar de fofoca.",
        ),
        pessimist = listOf(
            "fama vem, fama vai, semana que vem ninguém lembra dessa.",
            "artista some rápido, só o meme que fica pra sempre.",
            "isso aí é assunto de vinte e quatro horas, no máximo.",
        ),
    ),
    NewsTopic.ESPORTE to TopicBank(
        optimist = listOf(
            "é por essas que eu nunca desisto de torcer.",
            "time que corre atrás sempre me arranca um sorriso.",
            "isso aí é disciplina e sorte andando juntas, eu aposto nisso.",
        ),
        pessimist = listOf(
            "todo time que vai bem assim acaba decepcionando na hora H.",
            "eu já aprendi a não comemorar antes da hora nesse esporte.",
            "isso aí anima até o próximo tropeço, que vem rápido.",
        ),
    ),
    NewsTopic.CLIMA_NATUREZA to TopicBank(
        optimist = listOf(
            "natureza sempre dá um jeito de se equilibrar, eu confio nisso.",
            "boa notícia do tempo eu levo como sinal de que o ano vai ser bom.",
            "chuva, sol ou calor, no fim sempre dá pra se adaptar.",
        ),
        pessimist = listOf(
            "clima assim só me lembra que vai faltar alguma coisa em casa.",
            "toda vez que a natureza avisa, a gente só descobre tarde demais.",
            "eu já separei o guarda-chuva e a fé, na dúvida.",
        ),
    ),
    NewsTopic.CURIOSIDADE to TopicBank(
        optimist = listOf(
            "isso aí é o tipo de história que alegra qualquer roda de conversa.",
            "o mundo ainda consegue me surpreender bonito de vez em quando.",
            "guarda essa, é das raras que dá vontade de contar duas vezes.",
        ),
        pessimist = listOf(
            "toda curiosidade dessas esconde um detalhe que ninguém quer contar.",
            "isso aí é estranho demais pra ser só coincidência.",
            "eu desconfio até de história bonita demais, minha praia é outra.",
        ),
    ),
    NewsTopic.MUNDO_CONFLITO to TopicBank(
        optimist = listOf(
            "mesmo no meio da confusão, sempre aparece alguém tentando resolver direito.",
            "eu boto fé que essa tensão toda ainda vira conversa, não vira briga.",
            "história mostra que essas coisas acabam se acertando, custa mas acerta.",
        ),
        pessimist = listOf(
            "conflito desses nunca acaba do jeito que prometem no começo.",
            "isso aí é briga de longe, mas quem sente o rombo é sempre gente comum.",
            "eu já vivi notícia assim antes, o final nunca muda de verdade.",
        ),
    ),
    NewsTopic.GERAL to TopicBank(
        optimist = listOf(
            "e mesmo assim eu aposto que essa história ainda tem final feliz.",
            "eu prefiro acreditar, sempre rendeu mais do que desconfiar.",
            "isso aí tem cara de começo de coisa boa, eu sinto.",
        ),
        pessimist = listOf(
            "eu já desconfio de notícia boa demais, sempre tem letra miúda.",
            "isso aí vai dar o que falar, e raramente é coisa boa.",
            "no fim das contas, sempre sobra pra quem menos pode.",
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

        // Titulo limitado antes de entrar no template: manchete real pode ter 20+ palavras, e
        // isso sozinho ja estourava o orcamento do modo Curto e deixava o Nicky sem fala (o
        // fitFor cortava a segunda linha inteira em vez de so aparar a manchete).
        val opener = RadioScriptLine(
            RadioSpeaker.Female,
            "$NICKY, ${OPENERS.random().format(cleanTitle.limitWords(16))}",
        )
        val pessimistReaction = RadioScriptLine(
            RadioSpeaker.Male,
            "$FRANKIE, ${bank.pessimist.random()}",
        )
        val optimistCounter = RadioScriptLine(
            RadioSpeaker.Female,
            bank.optimist.random(),
        )
        val hookOrPunch = RadioScriptLine(
            RadioSpeaker.Male,
            hook?.let { HOOK_CALLOUTS.random().format(it) } ?: PESSIMIST_PUNCHLINES.random(),
        )
        val optimistPunch = RadioScriptLine(
            RadioSpeaker.Female,
            OPTIMIST_PUNCHLINES.random(),
        )
        // Quem fecha o boletim alterna por notícia (hash do título) em vez de ser sempre o
        // mesmo locutor - participação igualitária também no fechamento, não só nas falas do meio.
        val nickyCloses = story.title.hashCode() and 1 == 0
        val closer = if (nickyCloses) {
            RadioScriptLine(RadioSpeaker.Male, "$FRANKIE, ${CLOSERS_NICKY.random()} Voltamos pra Rádio ${context.radioName}.")
        } else {
            RadioScriptLine(RadioSpeaker.Female, "$NICKY, ${CLOSERS_FRANKIE.random()} Voltamos pra Rádio ${context.radioName}.")
        }

        val lines = when (context.duration) {
            RadioBulletinDuration.Short -> listOf(opener, pessimistReaction)
            RadioBulletinDuration.Normal -> listOf(opener, pessimistReaction, optimistCounter, closer)
            RadioBulletinDuration.Long -> listOf(opener, pessimistReaction, optimistCounter, hookOrPunch, optimistPunch, closer)
        }
        return lines.fitFor(context.duration)
    }

    private fun List<RadioScriptLine>.fitFor(duration: RadioBulletinDuration): List<RadioScriptLine> {
        val maxWords = when (duration) {
            RadioBulletinDuration.Short -> 55
            RadioBulletinDuration.Normal -> 80
            RadioBulletinDuration.Long -> 115
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

        val PESSIMIST_PUNCHLINES = listOf(
            "no fim das contas, quem paga a conta é sempre o de sempre.",
            "aposto o troco que isso vira notícia ruim de novo semana que vem.",
            "isso aí tem cheiro de golpe, e eu tenho faro pra golpe.",
            "no meu bairro isso chamava atenção por motivo errado.",
        )

        val OPTIMIST_PUNCHLINES = listOf(
            "mas eu prefiro apostar que dá certo, sempre deu até agora.",
            "e mesmo assim eu digo: isso ainda pode virar a melhor notícia do mês.",
            "chama de sorte, chama de fé, eu chamo de oportunidade.",
            "no fim, quem se arrisca é quem sai contando história boa depois.",
        )

        val CLOSERS_NICKY = listOf(
            "eu já disse o que penso, guarda essa aí.",
            "vou ficar de olho nessa, coisa de gente desconfiada.",
            "duvido que isso acabe do jeito que estão prometendo.",
        )

        val CLOSERS_FRANKIE = listOf(
            "e mesmo assim, aposto que ainda vem coisa boa por aí.",
            "guarda essa com carinho, pode virar boa história.",
            "eu fico com a fé, sempre funcionou comigo.",
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
