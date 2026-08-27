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
    val duration: RadioBulletinDuration = RadioBulletinDuration.Short,
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
        val scriptContext = RadioScriptContext(radioName = radioName, duration = settings.duration)
        return stories.map { story ->
            when (settings.mode) {
                RadioBulletinMode.Off -> null
                RadioBulletinMode.Headlines -> fallbackWriter.writeHeadline(story, scriptContext)
                RadioBulletinMode.Dialogue -> writeDialogue(story, scriptContext, settings.preferLocalWriter)
            }
        }.filterNotNull()
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

private class FallbackRadioScriptWriter : RadioScriptWriter {
    override suspend fun write(story: NewsStory, context: RadioScriptContext): RadioScript =
        withContext(Dispatchers.Default) {
            val cleanTitle = story.title.toRadioSentence()
            RadioScript(
                story = story,
                source = RadioScriptSource.Fallback,
                lines = listOf(
                    RadioScriptLine(
                        RadioSpeaker.Female,
                        "Noticia rapida: $cleanTitle.",
                    ),
                    RadioScriptLine(
                        RadioSpeaker.Male,
                        "${REACTION_LINES.random()} O detalhe vem de ${story.source}.",
                    ),
                    RadioScriptLine(
                        RadioSpeaker.Female,
                        "${CLOSING_LINES.random()} Agora voltamos para a Radio ${context.radioName}.",
                    ),
                ).fitFor(context.duration),
            )
        }

    suspend fun writeHeadline(story: NewsStory, context: RadioScriptContext): RadioScript =
        withContext(Dispatchers.Default) {
            RadioScript(
                story = story,
                source = RadioScriptSource.Fallback,
                lines = listOf(
                    RadioScriptLine(
                        RadioSpeaker.Female,
                        "Noticia rapida: ${story.title.toRadioSentence()}.",
                    ),
                    RadioScriptLine(
                        RadioSpeaker.Female,
                        "${CLOSING_LINES.random()} Agora voltamos para a Radio ${context.radioName}.",
                    ),
                ),
            )
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

    private companion object {
        // Falas curtas de comentario/reacao pro locutor, sorteadas a cada boletim pra dar um
        // tom bem humorado sem repetir sempre a mesma piada.
        val REACTION_LINES = listOf(
            "Pois e, quem diria.",
            "Isso mesmo que voce ouviu.",
            "E olha que o dia ainda nem acabou.",
            "Ninguem esperava por essa.",
            "Anota ai pra puxar assunto depois.",
            "A vida real de novo mais estranha que novela.",
            "Duvido voce adivinhar o que vem depois dessa.",
            "E la se vai mais uma pro grupo da familia.",
        )
        val CLOSING_LINES = listOf(
            "Fica a dica.",
            "Por hoje e so, mas a gente volta com mais.",
            "Guarda essa ai pra contar pros amigos.",
            "Sigam ligados, o mundo nao para de surpreender.",
            "E assim caminha a humanidade.",
            "Ninguem soube explicar direito, mas seguimos.",
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
