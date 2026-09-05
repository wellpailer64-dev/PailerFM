package com.pailer.localtune.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    // Default Normal so os construtores que nao vem de um writer de verdade (scripts de teste em
    // LocalTuneViewModel.kt/RadioVoiceSynthesisService.kt) - os dois writers reais sempre passam
    // context.duration explicito. Guardado aqui pra withPhilosophicalCloser() escolher a reflexao
    // do Nico (leve/funda) sem precisar recalcular a partir da materia.
    val duration: RadioBulletinDuration = RadioBulletinDuration.Normal,
    // true so quando o script foi gerado com lastPlayedTrack/upcomingTrack reais (sessao de radio
    // ativa) - false pro "nucleo" pre-aquecido sem radio nenhuma tocando (ver
    // LocalTuneViewModel.prewarmCoreBuffer/RadioBulletinRepository.enhanceScript). Substitui o
    // sinal antigo "source == LocalLlm && lines.size >= 6" em withLastPlayedIntro/
    // withPhilosophicalCloser (03/09/2026): esse sinal assumia que um script de 6 falas do LLM
    // sempre tinha contexto de faixa real, o que deixou de ser verdade com o pre-aquecimento.
    val hasTrackContext: Boolean = false,
) {
    val displayText: String
        get() = story.title

    // Usado so pelo fallback de TTS do sistema, que fala com uma unica voz (nao alterna por
    // locutor); incluir o rotulo aqui so fazia a voz ler "Locutora:"/"Locutor:" em voz alta.
    val spokenText: String
        get() = lines.joinToString(" ") { it.text }
}

// genre/year default vazio/0 pra nao quebrar os usos existentes (so title/artist) - reaproveitado
// tanto pra "ultima musica" (withLastPlayedIntro) quanto pra "proxima musica"
// (withPhilosophicalCloser, que usa genre/year pra escolher a curiosidade em musicTrivia()).
data class RadioLastPlayedTrack(
    val title: String,
    val artist: String = "",
    val genre: String = "",
    val year: Int = 0,
)

fun RadioScript.withLastPlayedIntro(track: RadioLastPlayedTrack?): RadioScript {
    val playedTrack = track ?: return this
    if (lines.isEmpty()) return this
    // Redator local (>=6 falas, COM contexto de faixa real - hasTrackContext) ja escreveu a
    // reacao a faixa anterior na propria fala 1, usando titulo/artista reais (ver
    // OptionalLocalLlmRadioScriptWriter.buildPrompt) - o template fixo abaixo ("Você acaba de
    // ouvir...") so entra quando o roteiro veio do fallback deterministico, o LLM nao emplacou
    // as 6 falas dessa vez, OU o script e um "nucleo" pre-aquecido sem contexto real ainda (ver
    // RadioScript.hasTrackContext) - pedido do usuario (03/09/2026): essa frase sempre igual era
    // exatamente o tipo de "texto pronto" que ele nao queria mais.
    if (hasTrackContext && lines.size >= 6) return this
    val cleanTitle = playedTrack.title.toRadioSentence().limitWords(14)
    if (cleanTitle.isBlank()) return this
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

// Cola a reacao da Fran chamando a proxima musica (nome do artista + curiosidade de
// genero/epoca, nunca inventada - ver musicTrivia()) no fim do boletim, e a reflexao
// existencialista/absurdista do Nico quando ainda nao tiver uma.
// Aplicada DEPOIS da geracao (igual withLastPlayedIntro) porque a proxima faixa da fila so e
// conhecida em tempo de reproducao, nunca em loadScripts() - ver LocalTuneViewModel.
//
// Reflexao do Nico (fala 5) e reacao de fechamento da Fran (fala 6): quando o redator local
// escreveu o bate-bola completo (ver OptionalLocalLlmRadioScriptWriter.buildPrompt), as duas
// JA SAO geradas na hora em cima da noticia/faixa real, nao frase generica de banco. Só cai
// pro banco fixo (NICO_REFLECTIONS_LIGHT/DEEP, FRAN_CLOSER_REACTIONS) quando o roteiro veio do
// fallback determinístico ou o LLM nao emplacou as 6 falas dessa vez - pedido do usuário
// (02/09 e reforçado em 03/09/2026): as frases prontas do banco soavam repetitivas/genéricas
// demais, o LLM e quem consegue comentar de fato "na hora" a partir do conteúdo real.
//
// A curiosidade musical (musicTrivia) continua vindo SEMPRE do banco fixo, mesmo quando o LLM
// escreveu a reacao - decisao deliberada anterior do usuario: o redator local e pequeno demais
// pra arriscar inventar dado sobre banda pouco conhecida da biblioteca (ver comentario em
// MUSIC_TRIVIA_BY_GENRE_FAMILY). O LLM só assina a reação/energia da fala, não o fato em si.
fun RadioScript.withPhilosophicalCloser(radioName: String, nextTrack: RadioLastPlayedTrack?): RadioScript {
    if (lines.isEmpty()) return this
    val llmWroteFullCloser = source == RadioScriptSource.LocalLlm &&
        lines.size >= 6 &&
        lines[4].speaker == RadioSpeaker.Male &&
        lines[5].speaker == RadioSpeaker.Female
    if (llmWroteFullCloser) {
        val franLine = RadioScriptLine(
            RadioSpeaker.Female,
            buildFranCloser(radioName, nextTrack, reaction = lines[5].text).limitWords(32),
        )
        return copy(lines = lines.take(5) + franLine)
    }
    val llmAlreadyWroteReflection = source == RadioScriptSource.LocalLlm &&
        lines.size >= 5 &&
        lines.last().speaker == RadioSpeaker.Male
    val franLine = RadioScriptLine(RadioSpeaker.Female, buildFranCloser(radioName, nextTrack, reaction = null).limitWords(32))
    if (llmAlreadyWroteReflection) {
        return copy(lines = lines + franLine)
    }
    val reflectionBank = if (duration == RadioBulletinDuration.Long) NICO_REFLECTIONS_DEEP else NICO_REFLECTIONS_LIGHT
    val nicoLine = RadioScriptLine(RadioSpeaker.Male, reflectionBank.random().ensureFinalPeriod().limitWords(28))
    return copy(lines = lines + nicoLine + franLine)
}

private fun buildFranCloser(radioName: String, nextTrack: RadioLastPlayedTrack?, reaction: String?): String {
    val track = nextTrack
    val cleanArtist = track?.artist?.toRadioSentence()?.limitWords(6).orEmpty()
    val opener = reaction?.trim()?.takeIf { it.isNotBlank() } ?: FRAN_CLOSER_REACTIONS.random()
    if (track == null || cleanArtist.isBlank()) {
        // Sem faixa seguinte conhecida (fim de fila, ou fila ainda nao carregada) - degrada pra
        // frase generica de sempre, sem citar artista nem curiosidade.
        return "$opener Segue o mundo torto, segue a nossa trilha. Agora a música volta na Rádio $radioName."
    }
    val trivia = musicTrivia(track.genre, track.year)
    return "$opener Chega mais $cleanArtist aí: $trivia Segue com a gente na Rádio $radioName."
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
    private val geminiWriter = RemoteGeminiRadioScriptWriter(context)
    private val fallbackWriter = FallbackRadioScriptWriter()

    fun localWriterStatus(): LocalRadioWriterStatus = localWriter.status()

    // Exposto pra UI mostrar/gerenciar a chave do Gemini nas Configuracoes - ver
    // GeminiWriterSettings/LocalTuneViewModel.
    fun geminiSettings(): GeminiWriterSettings = geminiWriter.settings

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

    suspend fun enhanceScript(
        script: RadioScript,
        settings: RadioBulletinSettings,
        radioName: String,
        lastPlayedTrack: RadioLastPlayedTrack? = null,
        upcomingTrack: RadioLastPlayedTrack? = null,
        onProgress: (String) -> Unit = {},
        onProgressPercent: (Int) -> Unit = {},
        // Pedido do usuario (04/09/2026): prewarmCoreBuffer usa isso pra pular o cooldown termico
        // (ver COOLDOWN_BETWEEN_BULLETINS_MS em LocalTuneViewModel) quando a escrita saiu 100% na
        // nuvem - so a sintese de voz continua pesando local nesse caso, bem menos sozinha do que
        // LLM local + sintese juntos (a causa original do cooldown, ver ADR-020/021). Callback em
        // vez de campo novo em RadioScript porque so esse UM caller precisa saber - nenhum outro
        // consumidor distingue Gemini de redator local por design (ver RemoteGeminiRadioScript
        // Writer.writeContextual).
        onWriterUsed: (usedGemini: Boolean) -> Unit = {},
    ): RadioScript {
        if (settings.mode != RadioBulletinMode.Dialogue || !settings.preferLocalWriter) return script
        val context = RadioScriptContext(radioName = radioName, duration = pickDuration(script.story))
        val trackContext = { s: RadioScript ->
            // Marca se a geracao teve faixa real disponivel - usado por withLastPlayedIntro pra
            // decidir se ainda precisa prepender a intro real (ver comentario no campo
            // RadioScript.hasTrackContext). Sem faixa nenhuma conhecida, fica false mesmo se
            // upcomingTrack tiver vindo (fala 6 nao depende disso, ver withPhilosophicalCloser).
            s.copy(hasTrackContext = lastPlayedTrack != null || upcomingTrack != null)
        }

        // Gemini primeiro quando tem chave configurada - a etapa de ESCRITA e o gargalo real
        // (ver ADR-020: prefill+decode do Qwen3 4B local passa de 300s), uma chamada de API
        // costuma responder em segundos. So a redacao vai pra nuvem; sintese de voz continua
        // 100% local (LocalRadioVoiceEngine), o audio final nunca sai do aparelho. Sem chave,
        // nem tenta - cai direto pro redator local, app continua 100% funcional offline.
        if (geminiWriter.isConfigured()) {
            onProgress("Escrevendo o bate-bola com o Gemini...")
            Log.d(TAG_RADIO_WRITER, "gemini iniciando para '${script.story.title}'")
            val result = runCatching {
                geminiWriter.writeContextual(script.story, context, lastPlayedTrack, upcomingTrack)
            }.map(trackContext).onSuccess {
                Log.d(TAG_RADIO_WRITER, "gemini gerou ${it.lines.size} falas para '${script.story.title}'")
            }.onFailure {
                Log.w(TAG_RADIO_WRITER, "gemini falhou; tentando redator local para '${script.story.title}'", it)
                onProgress("Gemini falhou (${it.message ?: it::class.simpleName}); tentando redator local...")
            }
            if (result.isSuccess) {
                onWriterUsed(true)
                return result.getOrThrow()
            }
        }

        onWriterUsed(false)
        if (!localWriter.status().isInstalled) {
            Log.d(TAG_RADIO_WRITER, "redator local ausente; usando roteiro base")
            return script
        }
        // Sem tempo fixo no texto (era "~70s", ficou desatualizado assim que
        // LOCAL_WRITER_TIMEOUT_MS mudou de novo) - LOCAL_WRITER_TIMEOUT_MS e a fonte da verdade.
        onProgress("Escrevendo o bate-bola com o redator local (Qwen3)...")
        Log.d(TAG_RADIO_WRITER, "redator local iniciando para '${script.story.title}'")
        return runCatching {
            localWriter.writeContextual(script.story, context, lastPlayedTrack, upcomingTrack, onProgress, onProgressPercent)
        }.map(trackContext).onSuccess {
            Log.d(TAG_RADIO_WRITER, "redator local gerou ${it.lines.size} falas para '${script.story.title}'")
        }.onFailure {
            Log.w(TAG_RADIO_WRITER, "redator local falhou; usando roteiro base para '${script.story.title}'", it)
            // Visivel na UI (RadioBulletinBufferStatusCard) alem do log - pedido do usuario
            // (03/09/2026): quer saber quando algo deu errado, nao so nos logs.
            onProgress("Redator local falhou (${it.message ?: it::class.simpleName}); usando roteiro padrão.")
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

// Conteudo de instrucao compartilhado entre o redator local (llama.cpp/Qwen3, formato ChatML,
// ver OptionalLocalLlmRadioScriptWriter.buildPrompt) e o redator remoto (Gemini, ver
// RemoteGeminiRadioScriptWriter) - so o "envelope" (ChatML vs system/user da API do Gemini)
// difere entre os dois motores, o texto da instrucao e IDENTICO de proposito (mesmo estilo de
// bate-bola dos dois locutores, mesma regra de acentuacao/tamanho, mesmo exemplo few-shot).
private fun buildSystemInstructions(): String = """
    Roteirista da Pailer FM. Escreva em português correto e completo, com todos os
    acentos (á é í ó ú â ê ô ã õ ç) e toda a pontuação (vírgulas, pontos) - o texto vai
    direto pra um sintetizador de voz que só pronuncia certo com acentuação correta.
    Fran e Nico são amigos de trabalho de longa data, têm intimidade pra brincar um com
    o outro no ar. Fran tem senso de humor de verdade: solta piada, faz alívio cômico,
    gosta de trazer informação útil/relevante e levantar o astral - sem soar boba ou
    ingênua. Nico é cético, sério, político (manja de capitalismo, imperialismo, lobby,
    corporativismo, mas não cita isso toda fala), e também tem humor - só que ácido,
    seco, cutuca a ferida do assunto (ironia/sarcasmo/deboche), nunca piadinha fofa. Nico
    é meio porra-louca, já viveu bastante, tem aquela pegada de motoqueiro de jaqueta de
    couro que não se abala com nada - isso aparece na ATITUDE da fala dele, não precisa
    citar jaqueta/moto toda hora.
    Responda só JSON válido, um array de 6 objetos:
    [{"speaker":"Female","text":"..."},{"speaker":"Male","text":"..."}]
    As instruções abaixo dizem O QUE cada fala deve fazer - NÃO são texto pra repetir.
    Nunca escreva a instrução em si (tipo a palavra "provocação") como se fosse a fala;
    escreva a fala de verdade, como um locutor falaria ao vivo, DIFERENTE a cada vez -
    nunca reuse a mesma abertura ou o mesmo bordão de um boletim pro outro. Exemplo
    (matéria fictícia sobre trânsito, só pra mostrar o estilo esperado):
    [{"speaker":"Female","text":"Isso aí que tocou já entra na lista dos meus favoritos. E olha a notícia que chegou: a prefeitura anunciou um novo corredor de ônibus pra Avenida Central."},
    {"speaker":"Male","text":"Bonito no papel. Só falta combinar com a Câmara, que adora aprovar promessa e esquecer o financiamento."},
    {"speaker":"Female","text":"Ei, deixa eu sonhar um pouco, Nico. Se sair do papel, é um baita alívio pra quem enfrenta esse trânsito todo dia."},
    {"speaker":"Male","text":"Sonha, mas guarda o troco: eu já vi essa novela passar umas cinco vezes e o final nunca muda."},
    {"speaker":"Male","text":"Tem um quê de Kafka nisso: a promessa vira regra, a regra vira espera, e a espera a gente já conhece de cor."},
    {"speaker":"Female","text":"Tá filosófico E ácido hoje, hein? Combinação perigosa. Mas segura aí que eu já trago a próxima faixa de volta."}]
    Estrutura das 6 falas desta matéria:
    1 Female/Fran: reaja de verdade à "Faixa que acabou de tocar" (cite o nome se vier
    informado, nunca invente um se vier "desconhecida") e emende pra notícia - varie
    sempre o jeito de puxar assunto, nunca use a mesma frase de novo. Se vier
    "desconhecida", SEMPRE responda com as 6 falas completas mesmo assim, só pulando a
    parte de citar a faixa - exemplo de fala 1 pra esse caso (faixa desconhecida):
    {"speaker":"Female","text":"Chegou uma notícia que vale a pena parar pra ouvir: a prefeitura anunciou um novo corredor de ônibus."}
    2 Male/Nico: traga um detalhe da matéria com leitura crítica - pode vir com o tom
    ácido/seco dele, sem crueldade gratuita.
    3 Female/Fran: reaja com otimismo realista OU com uma piada/comentário leve que
    alivia o clima, sem soar ingênua.
    4 Male/Nico: rebata com outra visão, pode cutucar com sarcasmo/ironia (cutuca a
    ferida do ASSUNTO, nunca da Fran), sem chamar de volta pra rádio (isso vem depois,
    automático).
    5 Male/Nico: feche com um comentário existencialista/absurdista GERADO NA HORA a
    partir do que ESSA notícia específica sugere - nunca uma frase genérica que serviria
    pra qualquer matéria. Pode se inspirar em Camus, Sartre, Nietzsche, Kafka, Beckett ou
    Cioran, mas só cite o nome do pensador se tiver certeza da atribuição; senão comente
    por tema/estilo sem citar ninguém. Pode vir com o mesmo tom ácido de sempre. Não
    repita o que já foi dito nas falas 1 a 4.
    6 Female/Fran: reaja ao comentário do Nico com humor de verdade (piada, provocação
    carinhosa ou concordância divertida - varie) e puxe a despedida numa nota positiva,
    levantando o astral; se o "Artista da próxima faixa" vier informado, cite o nome
    dele torcendo pra reação, mas NUNCA invente fato sobre a banda nem sobre a música
    (isso vem depois, automático). Não diga o nome da rádio nem "a música volta", isso
    também vem depois automático.
    Não invente fatos. Máximo 18 palavras por fala (falas 1 a 4 e 6) ou 28 palavras (fala 5).
""".trimIndent()

private fun buildUserContent(
    story: NewsStory,
    lastPlayedTrack: RadioLastPlayedTrack?,
    upcomingTrack: RadioLastPlayedTrack?,
): String {
    val title = story.title.toRadioSentence().limitWords(22)
    val summary = story.summary.ifBlank { "Sem resumo disponível." }.toRadioSentence().limitWords(55)
    // Faixa anterior/seguinte: usadas pra fala 1 (reacao a faixa que acabou de tocar) e fala 6
    // (reacao de fechamento puxando a proxima faixa) serem geradas na hora pelo LLM em vez de
    // template fixo (ver withLastPlayedIntro/withPhilosophicalCloser) - pedido do usuario
    // (03/09/2026): as frases prontas ali soavam sempre iguais. So o NOME da proxima faixa vai
    // pro prompt (nunca genero/ano) porque a curiosidade musical continua vindo do banco
    // verificado (musicTrivia) - o LLM e pequeno demais pra arriscar inventar dado de banda.
    val lastTrackLine = lastPlayedTrack?.let { track ->
        val cleanTitle = track.title.toRadioSentence().limitWords(14)
        val cleanArtist = track.artist.toRadioSentence().limitWords(8)
        if (cleanTitle.isBlank()) null
        else if (cleanArtist.isBlank()) cleanTitle else "$cleanTitle, de $cleanArtist"
    } ?: "desconhecida - não cite nome de faixa nenhuma, só emende direto pra notícia"
    val nextArtistLine = upcomingTrack?.artist?.toRadioSentence()?.limitWords(8)?.takeIf { it.isNotBlank() }
        ?: "desconhecido - não cite nome de artista nenhum, reaja de forma genérica"
    return """
        Fonte: ${story.source}
        Título: $title
        Resumo: $summary
        Faixa que acabou de tocar: $lastTrackLine
        Artista da próxima faixa: $nextArtistLine
    """.trimIndent()
}

// Guarda a chave de API do Gemini localmente no aparelho (SharedPreferences privado do app,
// mesmo mecanismo que o resto do app ja usa pra preferencias simples). NUNCA e commitada no
// git nem enviada pra lugar nenhum alem da propria chamada ao Gemini - pedido do usuario
// (03/09/2026): "não passa a chave pelo chat", entrada so pela UI de Configuracoes do app.
class GeminiWriterSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun apiKey(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    private companion object {
        const val PREFS_NAME = "gemini_writer"
        const val KEY_API_KEY = "api_key"
    }
}

// Redator remoto via Gemini (Google) - pedido do usuario (03/09/2026): a etapa de ESCRITA foi
// o gargalo real da sessao inteira de diagnostico de desempenho (ADR-020: prefill+decode do
// Qwen3 4B local passam de 300s no aparelho), enquanto uma chamada de API em geral responde
// em segundos. So a REDACAO vai pro Gemini - a sintese de voz continua 100% local/offline (ver
// LocalRadioVoiceEngine), o audio final do boletim nunca sai do aparelho, so o titulo/resumo
// da materia (texto publico de RSS) e o "roteiro" de contexto vao pra API. Sem chave
// configurada (GeminiWriterSettings.apiKey() == null), RadioBulletinRepository.enhanceScript()
// nem tenta esse caminho - cai direto pro redator local, mantendo o app 100% funcional offline
// como sempre foi (nenhum comportamento existente muda pra quem nao configurar a chave).
private class RemoteGeminiRadioScriptWriter(context: Context) {
    val settings = GeminiWriterSettings(context)

    fun isConfigured(): Boolean = settings.apiKey() != null

    suspend fun writeContextual(
        story: NewsStory,
        context: RadioScriptContext,
        lastPlayedTrack: RadioLastPlayedTrack?,
        upcomingTrack: RadioLastPlayedTrack?,
    ): RadioScript = withContext(Dispatchers.IO) {
        val apiKey = settings.apiKey() ?: error("Gemini sem chave de API configurada")
        val userContent = buildUserContent(story, lastPlayedTrack, upcomingTrack)
        val generated = callGeminiWithRetry(apiKey, userContent)
        val lines = parseGeneratedLines(generated)
        Log.d(TAG_RADIO_WRITER, "gemini texto: " + lines.joinToString(" | ") { "${it.speaker}: ${it.text}" })
        RadioScript(
            story = story,
            // Mesmo "source" do redator local de proposito - do ponto de vista do resto do app
            // (withLastPlayedIntro/withPhilosophicalCloser, RadioBulletinBufferStatusCard) e a
            // mesma coisa (roteiro gerado por LLM com qualidade pra usar de verdade), so muda
            // ONDE a geracao rodou. Nao ha hoje nenhum consumidor que precise distinguir local
            // de remoto - se precisar no futuro, adicionar um `RadioScriptSource.Gemini` novo.
            source = RadioScriptSource.LocalLlm,
            lines = lines,
            duration = context.duration,
        )
    }

    // Erro 503 "high demand"/429 "rate limit" costumam ser picos passageiros do lado da Google
    // (a propria mensagem do 503 diz "usually temporary") - vale tentar de novo com um pequeno
    // atraso antes de desistir e cair pro redator local, que nesse aparelho e MUITO mais lento
    // (ADR-020: Qwen3 4B passa de 300s). Nao reentra em 4xx que nao seja rate limit (ex.: 404 de
    // modelo errado, 400 de payload invalido) - repetir a mesma requisicao invalida so atrasa a
    // queda pro fallback sem chance nenhuma de dar certo. Roda em segundo plano durante a musica,
    // entao o tempo extro de 1-2 tentativas custa pouco perto do ganho de nao cair pro local.
    private suspend fun callGeminiWithRetry(apiKey: String, userContent: String): String {
        val systemInstruction = buildSystemInstructions()
        for (attempt in 0 until GEMINI_MAX_ATTEMPTS) {
            val result = runCatching {
                withTimeoutOrNull(GEMINI_TIMEOUT_MS) { callGemini(apiKey, systemInstruction, userContent) }
                    ?: error("Gemini demorou demais pra responder")
            }
            result.onSuccess { return it }
            val failure = result.exceptionOrNull()!!
            val isLastAttempt = attempt == GEMINI_MAX_ATTEMPTS - 1
            val retryable = RETRYABLE_ERROR_CODES.any { code -> failure.message?.contains("erro $code") == true }
            if (isLastAttempt || !retryable) throw failure
            Log.w(TAG_RADIO_WRITER, "gemini erro transitorio (tentativa ${attempt + 1}/$GEMINI_MAX_ATTEMPTS), tentando de novo em ${GEMINI_RETRY_DELAY_MS}ms", failure)
            delay(GEMINI_RETRY_DELAY_MS)
        }
        error("inalcancavel") // GEMINI_MAX_ATTEMPTS >= 1 garante return ou throw no loop acima
    }

    // Chamada sincrona (HttpURLConnection puro, mesmo padrao ja usado em
    // NewsBulletinRepository.fetchFeed - sem OkHttp/Retrofit no projeto) - roda dentro do
    // withContext(Dispatchers.IO) do caller. connectTimeout/readTimeout do proprio
    // HttpURLConnection ja limitam o tempo real mesmo sem suporte a cancelamento cooperativo
    // (mesma ressalva de outras chamadas bloqueantes deste projeto, ver ADR-020 pendencia 2).
    private fun callGemini(apiKey: String, systemInstruction: String, userContent: String): String {
        val connection = (URL("$GEMINI_ENDPOINT?key=$apiKey").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = GEMINI_TIMEOUT_MS.toInt()
            readTimeout = GEMINI_TIMEOUT_MS.toInt()
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))),
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userContent))),
                ),
            )
            // temperature equivalente ao motor local (RadioWriterPackageConfig). maxOutputTokens
            // alto (nao 800 como antes) porque o gemini-3.6-flash gasta a MAIOR parte do
            // orcamento de saida com "raciocinio" invisivel antes de escrever o texto final -
            // com 2048 ainda cortava no meio da 3a fala (descoberto 03/09/2026: "Unterminated
            // string" no parseGeneratedLines, so ~300 caracteres de texto real escritos).
            // thinkingConfig/thinkingBudget=0 foi tentado pra desligar esse raciocinio mas deu
            // erro 400 "invalid argument" (esquema mudou entre geracoes de modelo, nao
            // confirmado qual o campo certo) - removido; 8192 da folga de sobra tanto pro
            // raciocinio quanto pro JSON final de 6 falas curtas.
            put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 8192))
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        connection.disconnect()

        if (responseCode !in 200..299) {
            val apiMessage = runCatching {
                JSONObject(responseText).optJSONObject("error")?.optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            // 404 costuma ser o nome/versao do modelo desatualizado (a Google aposenta aliases
            // com frequencia) - lista os modelos que essa chave realmente enxerga no logcat pra
            // diagnostico, sem nunca imprimir a chave em si.
            if (responseCode == 404) logAvailableModels(apiKey)
            // Mensagem completa da API sempre anexada (nao so nos 500s) - "modelo nao encontrado"
            // generico escondia o motivo real do 404 durante o diagnostico de 03/09/2026.
            error("erro $responseCode${apiMessage?.let { ": $it" } ?: ""}")
        }

        val text = JSONObject(responseText)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            ?.optString("text")
        return text?.takeIf { it.isNotBlank() } ?: error("Gemini não devolveu texto (resposta vazia)")
    }

    private fun logAvailableModels(apiKey: String) {
        runCatching {
            val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()
            val names = JSONObject(text).optJSONArray("models")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("name") }
            }
            Log.w(TAG_RADIO_WRITER, "gemini modelos disponiveis pra essa chave: $names")
        }.onFailure {
            Log.w(TAG_RADIO_WRITER, "gemini: falha ao listar modelos disponiveis", it)
        }
    }

    private companion object {
        // Historico de tentativas (03/09/2026, chave nova do usuario):
        // - gemini-2.0-flash -> 404 (modelo aposentado)
        // - gemini-flash-latest -> 503 "high demand" (provavelmente aponta pra uma leva
        //   recem-lancada ainda sobrecarregada)
        // - gemini-2.5-flash -> 404, com mensagem explicita da propria API: "no longer
        //   available to new users... use models/gemini-3.6-flash"
        // Fixo em gemini-3.6-flash seguindo a recomendacao direta do erro acima. Se a Google
        // aposentar esse tambem no futuro, o log de logAvailableModels() (ver callGemini) mostra
        // a lista atual de modelos disponiveis pra escolher outro. Texto curto de roteiro de
        // radio (6 falas) nao precisa de um modelo maior/pro.
        const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"
        // Subiu de 30s pra 60s e depois pra 2min (03/09/2026, log real de campo): gemini-3.6-flash
        // as vezes gasta boa parte do orcamento de saida com "raciocinio" invisivel antes do JSON
        // final (mesmo motivo do maxOutputTokens alto acima) e passava dos 30s/60s por pouco,
        // caindo sem necessidade pro redator local - que e MUITO mais lento nesse aparelho
        // (ADR-020: Qwen3 4B passa de 300s). A geracao roda em segundo plano durante a musica
        // (refillBulletinBuffer, buffer de ate BULLETIN_BUFFER_TARGET boletins de folga) e nao trava a entrada da
        // radio, entao esperar mais o Gemini custa bem menos que cair pro motor local lento.
        const val GEMINI_TIMEOUT_MS = 120_000L
        // Erro transitorio (503/429/500) - ver callGeminiWithRetry: 2 tentativas no total (1
        // retry), com um respiro curto entre elas antes de desistir e cair pro redator local.
        const val GEMINI_MAX_ATTEMPTS = 2
        const val GEMINI_RETRY_DELAY_MS = 4_000L
        val RETRYABLE_ERROR_CODES = listOf(503, 429, 500)
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
        writeContextual(story, context, lastPlayedTrack = null, upcomingTrack = null)

    suspend fun writeContextual(
        story: NewsStory,
        context: RadioScriptContext,
        lastPlayedTrack: RadioLastPlayedTrack?,
        upcomingTrack: RadioLastPlayedTrack?,
        onProgress: (String) -> Unit = {},
        onProgressPercent: (Int) -> Unit = {},
    ): RadioScript =
        withContext(Dispatchers.Default) {
            val config = packageRepository.config() ?: error("Pacote de redator local ausente")
            // Percentual real (tokens gerados/teto) e mensagem por fase (lendo/escrevendo, com
            // segundos) vindo do callback nativo - ver LlamaProgressListener. Reserva o percentual
            // ate 95% porque o modelo quase sempre fecha o JSON antes do teto (predict) - chegar a
            // 100% so quando writeContextual retorna de fato. `reportStatus` guarda o parametro
            // onProgress numa val local pra nao colidir com o metodo onProgress da interface
            // dentro do object abaixo (mesmo nome, assinaturas diferentes).
            val reportStatus = onProgress
            val progressListener = object : LlamaProgressListener {
                override fun onPhase(phase: String, elapsedMs: Long) {
                    val message = when (phase) {
                        "lendo" -> "Lendo a matéria (redator local)..."
                        "escrevendo" -> "Matéria lida em ${elapsedMs / 1000}s. Escrevendo o bate-bola..."
                        else -> return
                    }
                    reportStatus(message)
                }

                override fun onProgress(current: Int, max: Int, elapsedMs: Long) {
                    if (max <= 0) return
                    val percent = ((current * 100) / max).coerceIn(0, 95)
                    onProgressPercent(percent)
                    reportStatus("Escrevendo o bate-bola... $percent% (${elapsedMs / 1000}s)")
                }
            }
            val generated = withTimeoutOrNull(LOCAL_WRITER_TIMEOUT_MS) {
                LocalLlamaTextGenerator.generate(config, buildPrompt(story, lastPlayedTrack, upcomingTrack), progressListener)
            } ?: error("Redator local demorou demais")
            val lines = parseGeneratedLines(generated)
            Log.d(TAG_RADIO_WRITER, "redator local texto: " + lines.joinToString(" | ") { "${it.speaker}: ${it.text}" })
            RadioScript(
                story = story,
                source = RadioScriptSource.LocalLlm,
                lines = lines,
                duration = context.duration,
            )
        }

    private fun buildPrompt(
        story: NewsStory,
        lastPlayedTrack: RadioLastPlayedTrack?,
        upcomingTrack: RadioLastPlayedTrack?,
    ): String {
        // Formato ChatML especifico do llama.cpp/Qwen3 - pre-semeia o "[" de abertura e um
        // bloco <think></think> vazio (ver ADR-002, Qwen3 as vezes gasta o orcamento de
        // tokens todo "pensando" em vez de escrever JSON direto). O Gemini (RemoteGeminiRadio
        // ScriptWriter) usa o MESMO conteudo de instrucao (buildSystemInstructions/
        // buildUserContent abaixo) mas sem esses marcadores - a API dele ja separa
        // system/user de outro jeito, e nao precisa do truque de pre-seed pra pular o "pensar".
        return """
            <|im_start|>system
            ${buildSystemInstructions()}
            <|im_end|>
            <|im_start|>user
            ${buildUserContent(story, lastPlayedTrack, upcomingTrack)}
            /no_think
            <|im_end|>
            <|im_start|>assistant
            <think>

            </think>

            [
        """.trimIndent()
    }

    private companion object {
        // 02/09/2026: decode real medido em ~27s neste aparelho (Dimensity 1200, 3 threads,
        // Qwen3 1.7B Q4_K_M) - ver ADR-002. Prompt cresceu (exemplo few-shot contra o bug de
        // "eco da instrução") então subiu de novo com folga. Mantido generoso porque
        // prepareUpcomingBulletin() roda em background durante a música, não bloqueia nada.
        // Subido de 70s pra 150s e depois pra 250s (03/09/2026, pedido do usuario: meta e NUNCA
        // cair no FallbackRadioScriptWriter, que usa frase fixa de conector - o fallback deve
        // ficar so como rede de seguranca). Cada timeout evitado e um boletim a menos soando
        // repetitivo. BULLETIN_PREP_TIMEOUT_MS (LocalTuneViewModel, 11min) ainda sobra folga de
        // sintese de voz depois disso.
        // 03/09/2026 (2a vez): subido pra 360s junto da troca pro Qwen3 4B (decode mais lento,
        // maxTokens 260->420) - sempre um pouco ACIMA de LOCAL_WRITER_NATIVE_TIMEOUT_MS (340s,
        // em RadioWriterPackageRepository.kt) pra deixar o timeout interno abortar primeiro e
        // devolver erro gracioso, em vez desse aqui cortar a chamada nativa no meio.
        const val LOCAL_WRITER_TIMEOUT_MS = 360_000L
    }
}

// Compartilhado entre redator local e remoto (Gemini) - ambos geram o mesmo formato de JSON
// (ver buildSystemInstructions) e precisam da mesma validacao/rede de seguranca.
private const val ACCENTED_CHARS = "áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ"

private fun parseGeneratedLines(generated: String): List<RadioScriptLine> {
    // O prompt ja pre-semeia o "[" de abertura no motor local (ver buildPrompt) - generated
    // normalmente vem sem ele; o Gemini nao tem esse pre-seed mas pode devolver com ou sem
    // colchetes dependendo de como respeitou a instrucao. Modelos pequenos (Qwen3 1.7B) as
    // vezes paravam de gerar cedo demais e devolviam so 1 objeto solto, sem colchete nenhum -
    // o parse antigo (substringAfter('[')) so cobria o caso "colchetes presentes", e falhava
    // com JSONException "cannot be converted to JSONArray" nesse caso (visto em campo
    // 03/09/2026, redator sem contexto de faixa). Tira qualquer colchete que ja exista dos
    // dois lados e sempre reembrulha, em vez de tentar adivinhar se o modelo fechou certo.
    val trimmed = generated.trim().removePrefix("[")
    val body = trimmed.substringBeforeLast(']')
    val array = JSONArray("[$body]")
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val speaker = when (item.optString("speaker")) {
            "Female" -> RadioSpeaker.Female
            "Male" -> RadioSpeaker.Male
            else -> return@mapNotNull null
        }
        val text = item.optString("text").toRadioSentence().ensureFinalPeriod()
        if (text.isBlank()) null else RadioScriptLine(speaker, text.limitWords(32))
        // Cap em 6: as 4 falas do bate-bola + a reflexao existencialista (fala 5) + a reacao
        // de fechamento (fala 6, ver buildSystemInstructions/withPhilosophicalCloser). Fica so
        // no >=4 pra baixo pra nao jogar fora um bate-bola bom so porque o modelo nao emplacou
        // a 5a/6a fala dessa vez - withLastPlayedIntro/withPhilosophicalCloser degradam pro
        // template fixo quando faltar a 6a fala.
    }.take(6).takeIf { lines ->
        lines.size >= 4 && lines.firstOrNull()?.speaker == RadioSpeaker.Female && hasAccentuation(lines)
    } ?: error("O redator devolveu um roteiro inválido")
}

// Texto de português corrido deste tamanho praticamente sempre tem pelo menos um caractere
// acentuado ("não", "é", "está", "notícia"...) - se não tiver nenhum, é sinal de que o
// modelo escreveu sem acento (mesma causa raiz do ADR-015, agora na saída do LLM em vez de
// string fixa no código) e a pronúncia vai sair errada. Cai no fallback nesse caso.
private fun hasAccentuation(lines: List<RadioScriptLine>): Boolean =
    lines.joinToString(" ") { it.text }.any { it in ACCENTED_CHARS }

// Os dois locutores fixos do bate-bola (ver ADR-014, renomeado de Frankie/masculino pra
// Fran/feminino na ADR-018 junto da troca de motor de voz pro Supertonic). Nomes dos
// personagens, nao dos slots de voz - "Fran" fala pelo slot Female, "Nico" pelo slot Male.
// Se chamam pelo nome durante o boletim de proposito (pedido do usuario, "elemento surpresa"
// dos nomes).
// Personalidade (pedido do usuario 04/09/2026, ver ADR-021 e buildSystemInstructions): sao
// amigos de trabalho antigos, tem intimidade pra brincar um com o outro. Fran tem senso de
// humor, faz alivio comico, traz informacao util/relevante e levanta o astral. Nico e cetico/
// serio/politico (mantem o conhecimento de capitalismo/imperialismo/lobby da ADR-014) mas
// tambem tem humor - acido, seco, cutuca a ferida do assunto (nunca da Fran); e meio
// porra-louca, ja viveu bastante, pegada de motoqueiro de jaqueta de couro que nao se abala
// com nada - isso vem via ATITUDE da fala, nao citacao literal repetida.
private const val FRAN = "Fran"
private const val NICO = "Nico"
private const val TAG_RADIO_WRITER = "PailerRadioWriter"

// Reflexao existencialista/absurdista do Nico no fechamento do boletim (withPhilosophicalCloser).
// Duas trilhas por PESO, nao por tema - existencialismo/absurdismo e sobre a condicao humana em
// geral, entao a mesma reflexao serve pra qualquer assunto; o que muda e a profundidade (ver
// RadioBulletinRepository.pickDuration, reaproveitado como sinal de "densidade da noticia").
// So cito frase literal de autor quando tenho certeza da atribuicao (Camus/Sartre/Nietzsche/
// Beckett, todas muito conhecidas); Kafka/Cioran entram por tema/estilo, nao por citacao, pra nao
// arriscar atribuir frase errada a eles.
private val NICO_REFLECTIONS_LIGHT = listOf(
    "Isso aqui parece saído de um conto do Kafka: ninguém entende as regras, mas todo mundo tem que seguir.",
    "Camus dizia que o mundo não vem com sentido de fábrica, a gente é que insiste em procurar um.",
    "Tem um quê de teatro do absurdo nisso, sabe? A cena não precisa fazer sentido pra continuar acontecendo.",
    "Às vezes eu acho que a gente só segue em frente porque parar dói mais do que continuar sem entender nada.",
    "Se Sísifo empurrasse essa notícia morro acima, ele ia rir antes de espernear.",
)
private val NICO_REFLECTIONS_DEEP = listOf(
    "Sartre dizia que o homem está condenado a ser livre; a gente escolhe até quando finge que não escolheu nada.",
    "Camus resolvia isso com Sísifo: mesmo empurrando a pedra de novo, é preciso imaginar Sísifo feliz.",
    "Nietzsche avisou: quem luta com monstro devia cuidar pra não virar um. Vale pra manchete, vale pra quem cobre ela.",
    "Beckett resumia bem: não posso continuar, vou continuar. É meio a lógica de toda estrutura que insiste em se sustentar mesmo quebrada.",
    "Isso tem cheiro de Cioran: existir já é meio inconveniente, e mesmo assim a gente segue dando entrevista sobre.",
)

// Curiosidade genérica sobre a proxima faixa (withPhilosophicalCloser/buildFranCloser) - SO
// fatos gerais e verdadeiros de genero/epoca, nunca especificos do artista (decisao do usuario:
// o redator local e pequeno demais pra arriscar inventar dado sobre banda pouco conhecida da
// biblioteca - mesma regra de "nao invente fatos" que ja vale pras noticias).
private val MUSIC_TRIVIA_BY_GENRE_FAMILY: List<Pair<List<String>, List<String>>> = listOf(
    listOf("punk", "post-punk", "post punk", "coldwave", "darkwave", "hardcore", "emo", "screamo") to listOf(
        "punk nasceu no fim dos anos 70 como reação direta ao rock progressivo, que tinha virado longo demais e cheio de solo.",
        "post-punk pegou a agressividade do punk e trocou parte do caos por letra mais introspectiva e som mais experimental.",
    ),
    listOf("metal", "doom", "sludge", "grind", "core") to listOf(
        "heavy metal puxou boa parte da distorção pesada do blues rock do fim dos anos 60.",
        "banda de metal clássico ficou famosa por show extremamente alto, alguns entraram pra história como os mais altos já registrados.",
    ),
    listOf("grunge") to listOf(
        "grunge misturou punk com rock mais lento e sujo, e virou o som que definiu Seattle no começo dos anos 90.",
    ),
    listOf("house", "techno", "trance", "edm", "eletronica", "electro", "dubstep", "drum and bass", "synth") to listOf(
        "house nasceu em Chicago no começo dos anos 80, em clube underground, e techno surgiu logo depois em Detroit.",
        "muita faixa eletrônica clássica foi feita com sintetizador analógico e sequenciador que travava ao vivo, parte do charme da época.",
    ),
    listOf("hip-hop", "hip hop", "rap", "trap", "boom bap") to listOf(
        "hip-hop nasceu no Bronx, em Nova York, nos anos 70, direto de festa de rua com DJ tocando break de disco.",
        "no começo, muito rap era feito sobre batida sampleada de disco de soul e funk antigo.",
    ),
    listOf("mpb", "samba", "bossa", "pagode", "forro", "axe", "sertanejo", "tropicalia") to listOf(
        "bossa nova nasceu no Rio de Janeiro no fim dos anos 50, misturando samba com harmonia de jazz.",
        "MPB virou um guarda-chuva enorme nos anos 60 e 70 pra qualquer coisa que misturasse música popular brasileira com ousadia.",
    ),
    listOf("jazz", "blues", "bebop", "swing") to listOf(
        "jazz nasceu em Nova Orleans no início do século 20, misturando blues, ragtime e música de banda militar.",
        "muito clássico de blues foi gravado quase direto, sem edição, o que dá aquele som cru e imperfeito de propósito.",
    ),
    listOf("soul", "funk", "r&b", "r b", "motown") to listOf(
        "soul nasceu misturando gospel com R&B, ganhando aquela potência vocal quase de igreja.",
        "funk clássico é construído em cima do groove do baixo e da bateria, a melodia vem depois.",
    ),
    listOf("pop") to listOf(
        "pop sempre foi feito pra rádio: refrão curto, fácil de cantar junto, gancho logo nos primeiros segundos.",
    ),
    listOf("folk", "country", "americana", "bluegrass") to listOf(
        "folk sempre teve essa vocação de contar história real, muitas vezes ligada a algum movimento social.",
    ),
)

private val MUSIC_TRIVIA_BY_DECADE: List<Pair<IntRange, String>> = listOf(
    1960..1969 to "os anos 60 foram quando o estúdio virou instrumento de verdade, não só lugar de gravar ao vivo.",
    1970..1979 to "nos anos 70, o álbum virou obra pra ouvir inteira, não só coleção de single solto.",
    1980..1989 to "os anos 80 foram a década em que o sintetizador deixou de ser exceção e virou som padrão de rádio.",
    1990..1999 to "nos anos 90, gravadora ainda apostava pesado em lançamento físico, CD vendia igual disco de ouro.",
    2000..2009 to "nos anos 2000, o download digital começou a virar a forma real de descobrir música nova.",
    2010..2029 to "de 2010 pra cá, streaming virou a porta de entrada principal pra descobrir artista novo.",
)

private val MUSIC_TRIVIA_FALLBACK = listOf(
    "toda música carrega um pedacinho da época e do lugar onde foi gravada, mesmo quando a letra não fala nada disso.",
    "vale prestar atenção no que toca em seguida, cada faixa tem uma história de estúdio por trás.",
)

private val FRAN_CLOSER_REACTIONS = listOf(
    "Tá filosófico hoje, hein, $NICO.",
    "Lá vem você fundo demais de novo, $NICO, mas faz sentido.",
    "Segura essa reflexão que eu trago a trilha de volta.",
    "Deixa comigo que eu trago a gente de volta pro chão.",
)

// gênero → época → genérico, na ordem pedida pelo usuario ("do gênero, ou da época").
private fun musicTrivia(genre: String, year: Int): String {
    val normalizedTags = splitGenreTags(genre).map { stripDiacritics(it.lowercase()) }
    val genreMatch = MUSIC_TRIVIA_BY_GENRE_FAMILY.firstOrNull { (keywords, _) ->
        normalizedTags.any { tag -> keywords.any { tag.contains(it) } }
    }?.second?.random()
    if (genreMatch != null) return genreMatch.ensureFinalPeriod()

    val decadeMatch = MUSIC_TRIVIA_BY_DECADE.firstOrNull { (range, _) -> year in range }?.second
    if (decadeMatch != null) return decadeMatch.ensureFinalPeriod()

    return MUSIC_TRIVIA_FALLBACK.random().ensureFinalPeriod()
}

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
// Fora de escopo da atualizacao de personalidade da ADR-021: essas frases fixas do fallback
// (so usadas quando LLM local E Gemini falham os dois, ver RadioBulletinRepository.
// enhanceScript) continuam so no tom serio/cetico/politico antigo, sem o humor novo da Fran
// nem o sarcasmo do Nico - reescrever as ~15 categorias x otimista/pessimista pra combinar
// exigiria um esforco fora de proporcao com o quao raro esse caminho e (rede de seguranca,
// "meta e nunca cair aqui" - ver comentario em enhanceScript). O redator via LLM (local/Gemini,
// caminho principal) e quem realmente fala com a personalidade nova, via
// buildSystemInstructions().
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
                duration = context.duration,
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
                duration = context.duration,
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
        // isso sozinho ja estourava o orcamento do modo Curto e deixava o Nico sem fala (o
        // fitFor cortava a segunda linha inteira em vez de so aparar a manchete).
        val opener = RadioScriptLine(
            RadioSpeaker.Female,
            "$NICO, ${OPENERS.random().format(cleanTitle.limitWords(16))}",
        )
        val nicoExplains = RadioScriptLine(
            RadioSpeaker.Male,
            "$FRAN, ${NICO_TRANSLATES.random()} $matterBrief ${bank.pessimist.random()}",
        )
        val franProvokes = RadioScriptLine(
            RadioSpeaker.Female,
            "$NICO, ${FRAN_PROVOKES.random()} ${bank.optimist.random()}",
        )
        // Ultima fala do "debate" propriamente dito - o bate-bola termina aqui agora; a
        // reflexao filosofica do Nico e a chamada de musica da Fran sao coladas depois via
        // withPhilosophicalCloser() (so tem a faixa seguinte de verdade em tempo de reproducao,
        // nunca aqui em loadScripts() - ver LocalTuneViewModel).
        val nicoCounterProvokes = RadioScriptLine(
            RadioSpeaker.Male,
            "$FRAN, ${NICO_COUNTERS.random()} ${hook?.let { HOOK_CALLOUTS.random().format(it) } ?: PESSIMIST_PUNCHLINES.random()}",
        )
        val extraContext = RadioScriptLine(
            RadioSpeaker.Male,
            "${NICO_DETAILS.forTopic(topic).random()} ${hook?.let { "E esse detalhe de $it não entrou aí por acaso." } ?: "É nesse detalhe que a notícia pesa de verdade."}",
        )

        val lines = when (context.duration) {
            RadioBulletinDuration.Short -> listOf(opener, nicoExplains, franProvokes, nicoCounterProvokes)
            RadioBulletinDuration.Normal -> listOf(opener, nicoExplains, franProvokes, nicoCounterProvokes)
            RadioBulletinDuration.Long -> listOf(opener, nicoExplains, extraContext, franProvokes, nicoCounterProvokes)
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
        // Abre o boletim: Fran parafraseia a manchete pro Nico, tom de quem ja leu e ja
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

        // Conector fixo do Nico antes do resumo da materia (buildDialogueLines/nicoExplains) -
        // ate 03/09/2026 era sempre a MESMA frase ("deixa eu traduzir sem release de assessoria:")
        // em todo boletim que caia no fallback; pedido do usuario pra variar, ja que era
        // exatamente esse tipo de "texto pronto" que soava repetitivo.
        val NICO_TRANSLATES = listOf(
            "deixa eu traduzir sem o verniz do release:",
            "tirando a maquiagem da assessoria de imprensa:",
            "sem o filtro do comunicado oficial:",
            "cortando o floreio de sempre:",
            "resumindo sem embalagem de marketing:",
        )

        // Conector fixo da Fran provocando de volta (buildDialogueLines/franProvokes) - mesmo
        // motivo do NICO_TRANSLATES acima: era sempre "dá para enxergar o jogo sujo sem entregar
        // a alma para o cinismo." igual em todo boletim de fallback.
        val FRAN_PROVOKES = listOf(
            "dá pra enxergar o jogo sujo sem entregar a alma pro cinismo.",
            "reconheço a armadilha e mesmo assim não compro o desânimo fácil.",
            "vejo a esperteza por trás disso e ainda escolho não desistir.",
            "sei ler nas entrelinhas sem virar cínica por isso.",
            "percebo o truque e mesmo assim guardo um fiapo de fé.",
        )

        // Conector fixo do Nico rebatendo (buildDialogueLines/nicoCounterProvokes) - mesmo motivo
        // acima: era sempre "otimismo sem análise material vira propaganda." igual em todo
        // boletim de fallback (a frase que mais incomodava o usuario, pedido 03/09/2026).
        val NICO_COUNTERS = listOf(
            "otimismo sem análise material vira propaganda.",
            "esperança sem olhar pra estrutura é só discurso bonito.",
            "fé cega em final feliz ajuda mais quem já está por cima.",
            "sem pé no chão, otimismo vira só mais um produto de marketing.",
            "acreditar sem entender o mecanismo é dar corda pro mesmo sistema.",
        )

        val NICO_DETAILS: Map<NewsTopic, List<String>> = mapOf(
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
    if (words.size <= limit) return this
    // Prefere cortar em fim de frase completa em vez de no meio de uma oracao - o corte bruto
    // por palavra deixava falas penduradas tipo "...pede que alguem a de a porta de uma cela,
    // como." (sem terminar o pensamento), confuso de ouvir - achado 03/09/2026, feedback do
    // usuario sobre a previa soar "sem pe nem cabeca". Acha a ultima frase completa (., !, ?)
    // que ainda cabe no limite de palavras; so cai pro corte bruto se nem a 1a frase inteira
    // couber (ou o texto nao tiver pontuacao de frase nenhuma pra se guiar).
    val sentences = Regex("(?<=[.!?])\\s+").split(this).map { it.trim() }.filter { it.isNotBlank() }
    val kept = StringBuilder()
    var wordCount = 0
    for (sentence in sentences) {
        val sentenceWordCount = sentence.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (wordCount + sentenceWordCount > limit) break
        if (kept.isNotEmpty()) kept.append(' ')
        kept.append(sentence)
        wordCount += sentenceWordCount
    }
    if (kept.isNotEmpty()) return kept.toString()
    return words.take(limit).joinToString(" ").trimEnd(',', ';', ':') + "."
}
