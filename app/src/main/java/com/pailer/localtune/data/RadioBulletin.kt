package com.pailer.localtune.data

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
)

data class NewsStory(
    val title: String,
    val source: String,
    val summary: String = "",
    // Chamada completa e categoria crua do manifest do feed de broadcast ("headline"/"category",
    // ver BroadcastFeedRepository) - usadas so na tarja "Noticia da vez" da cena da radio.
    // title continua sendo a versao curta (chave de reserva/anti-repeticao depende dela).
    val headline: String = "",
    val category: String = "",
)

data class RadioScriptLine(
    val speaker: RadioSpeaker,
    val text: String,
)

// Nomes dos slots preservados por compatibilidade com o pacote de voz que a central de
// broadcast usa pra gravar os boletins (Fran/Nico) - o rotulo "Female" e so o nome do slot A,
// nao implica genero da voz.
enum class RadioSpeaker {
    Female,
    Male,
}

data class RadioScript(
    val story: NewsStory,
    val lines: List<RadioScriptLine>,
    val source: RadioScriptSource,
    // Default Normal so os construtores que nao vem do feed remoto (scripts de teste em
    // LocalTuneViewModel.kt) - o feed remoto sempre passa duration explicito (ver
    // BroadcastFeedRepository.durationFromSeconds).
    val duration: RadioBulletinDuration = RadioBulletinDuration.Normal,
    // Boletim "especial" (recado/publi por pedido direto, content_type == "especial" no
    // manifest da central de broadcast - ver ADR-037 em docs/DECISIONS.md). Furando fila:
    // BroadcastFeedRepository tenta baixar um especial antes de qualquer outro item do
    // feed, e LocalTuneViewModel insere no INICIO do buffer local (addFirst) em vez do
    // fim, pra tocar antes do que ja estava esperando a vez.
    val isSpecial: Boolean = false,
) {
    val displayText: String
        get() = story.title

    val spokenText: String
        get() = lines.joinToString(" ") { it.text }
}

enum class RadioScriptSource {
    LocalLlm,
    BroadcastFeed,
    Fallback,
}

// Tarja "Noticia da vez" da cena da radio (pedido do usuario 24/09/2026). Categoria vem crua do
// manifest (slug em ingles, ver docs/BROADCAST_METADATA.md); boletins salvos no buffer antes
// desse campo existir so tem ela embutida no source ("Pailer FM Broadcast · culture").
fun RadioScript.newsCategoryLabel(): String {
    if (isSpecial) return "Especial"
    val raw = story.category.ifBlank { story.source.substringAfter("·", "").trim() }.lowercase()
    return when (raw) {
        "", "general", "geral" -> "Geral"
        "culture" -> "Cultura"
        "curiosities" -> "Curiosidades"
        "geopolitics" -> "Geopolítica"
        "health" -> "Saúde"
        "science" -> "Ciência"
        "technology" -> "Tecnologia"
        "space" -> "Espaço"
        "history" -> "História"
        "music" -> "Música"
        "cinema" -> "Cinema"
        "games" -> "Games"
        "human" -> "Humano"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}

// Chamada completa (headline do manifest), caindo pro title curto em boletins antigos do buffer.
fun RadioScript.newsCallout(): String =
    story.headline.ifBlank { story.title }.trim()
