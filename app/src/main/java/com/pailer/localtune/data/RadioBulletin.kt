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
