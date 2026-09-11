package com.pailer.localtune.data

// Letra de uma musica. Fonte, texto plano (sempre preenchido, sem tags de tempo nem linhas de
// metadado) e as linhas ja parseadas - `timeMs` nao nulo em pelo menos uma linha => letra
// sincronizada (LRC), da pra destacar a linha atual conforme a musica toca.
//
// A letra NAO e gravada nos arquivos de audio do usuario (ADR-008): mora so em
// filesDir/lyrics/<songId>.lrc + index.json (ver LyricsRepository) e entra no backup.

enum class LyricsSource { NONE, EMBEDDED, MANUAL, LRCLIB }

data class LyricsLine(
    // null = linha sem marca de tempo (texto puro, ou linha solta de um LRC misto)
    val timeMs: Long?,
    val text: String,
)

data class Lyrics(
    val songId: Long,
    val source: LyricsSource,
    // Sempre preenchido quando ha letra: tags de tempo removidas, linhas de metadado ([ar:]/[ti:]
    // /...) descartadas, espacamento entre estrofes preservado. Usado no editor e no render sem sync.
    val plainText: String,
    val lines: List<LyricsLine>,
    val synced: Boolean,
    val updatedAt: Long,
) {
    val isEmpty: Boolean
        get() = source == LyricsSource.NONE || plainText.isBlank()

    companion object {
        val EMPTY = Lyrics(
            songId = -1L,
            source = LyricsSource.NONE,
            plainText = "",
            lines = emptyList(),
            synced = false,
            updatedAt = 0L,
        )
    }
}
