package com.pailer.localtune.data

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val dateAdded: Long,
    val genre: String,
    val year: Int = 0,
    val contentUri: Uri,
) {
    val artworkUri: Uri?
        get() = if (albumId > 0) {
            ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)
        } else {
            null
        }

    fun toMediaItem(radioName: String = ""): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .apply { if (radioName.isNotBlank()) setStation(radioName) }
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        private val ALBUM_ART_BASE_URI = Uri.parse("content://media/external/audio/albumart")
    }
}

data class LocalAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val songs: List<LocalSong>,
    // Tipo escolhido a mao pelo usuario (null = automatico, ver autoKind) - ver
    // MusicLibraryRepository.setAlbumKindOverride.
    val kindOverride: AlbumKind? = null,
    // Total de faixas do lancamento lido da tag do arquivo ("3/12" -> 12), 0 = desconhecido.
    // So preenchido pros candidatos a EP/single (ver albumKindNeedsTrackTotal) - o MediaStore
    // guarda so o numero da faixa e descarta o total.
    val trackTotal: Int = 0,
) {
    // So id+title (sem o artist agregado) - albumsFrom ja agrupa por "$albumId:$album", entao
    // esse par ja identifica o album sozinho. Incluir o artist aqui quebrava ocultar/favoritar
    // albuns tipo "WhatsApp Audio": o campo artist e um resumo de ate 2 artistas distintos das
    // faixas, que muda toda vez que uma nova mensagem de voz de outro remetente chega, fazendo a
    // chave salva em hiddenAlbumKeys/favoriteAlbumKeys nao bater mais com o album reconstruido.
    val key: String = "$id:$title"
    val dateAdded: Long = songs.maxOfOrNull { it.dateAdded } ?: 0L
    val genre: String = songs.firstOrNull { it.genre.isNotBlank() }?.genre.orEmpty()
    // Ano de lancamento (MediaStore YEAR/tag do arquivo) - 0 quando nenhuma faixa tem o ano
    // preenchido. Mesmo padrao de genre acima (primeira faixa com o dado, nao "mais comum").
    val year: Int = songs.firstOrNull { it.year > 0 }?.year ?: 0

    // Compilacoes/albuns montados a mao (varios artistas sob o mesmo nome de album) tem o
    // mesmo ALBUM_ID no MediaStore, entao content://.../albumart/<id> devolve a MESMA capa
    // pra todas as faixas mesmo elas tendo capas embutidas diferentes de verdade no arquivo.
    // Usado pra decidir mosaico (em vez de 1 capa so) e extracao por-faixa via MediaMetadataRetriever.
    val isVariousArtists: Boolean = songs.map { it.artist }.distinct().size > 1

    val autoKind: AlbumKind = detectAlbumKind(title, songs, isVariousArtists, trackTotal)
    val kind: AlbumKind = kindOverride ?: autoKind

    // Pasta tratada como PLAYLIST (ex.: baixada de uma playlist do YouTube) em vez de album:
    // aparece na secao "Playlists" da Biblioteca (fora de "Albuns"), mostra a capa embutida de
    // cada faixa em vez de uma capa unica, e a radio criada a partir dela toca embaralhada.
    val isPlaylist: Boolean = kind == AlbumKind.Playlist
}

enum class AlbumKind(val label: String) {
    Album("Álbum"),
    EP("EP"),
    Single("Single"),
    Playlist("Playlist"),
}

// Deteccao automatica do tipo (pedido do usuario 24/09/2026). Ordem importa:
// 1. Varios artistas -> Playlist SEMPRE primeiro: faixa solta dentro de pasta de playlist nunca
//    pode virar "single" (cada faixa ali e de um artista/lancamento diferente).
// 2. Marcacao explicita no nome ("... - Single", "... EP", "(E.P.)").
// 3. Total de faixas da tag (trackTotal): ate 3 = Single, 4 a 6 = EP, mais que isso = Album -
//    mesmo que a pasta so tenha parte das faixas.
// 4. Sem total na tag, fica conservador: a pasta pode ser um album INCOMPLETO (testado na
//    biblioteca real 24/09/2026: "Heathen Chemistry" so com as faixas 1-6 virava EP, "Kid A" com 1
//    faixa virava single). Entao so vira Single quando o nome do album e igual ao de uma das
//    faixas (jeito tipico de single vir tageado) e nunca vira EP sem marcacao/total.
// Na duvida fica Album; o usuario corrige pelo menu (Tipo).
private val SINGLE_MARKER = Regex("""(?i)[\s(\[-]single[)\]]?\s*$""")
private val EP_MARKER = Regex("""(?:^|[\s(\[-])(EP|E\.P\.|ep)[)\]]?\s*$""")
private const val SHORT_RELEASE_MAX_MS = 30 * 60 * 1000L

private fun normalizeReleaseTitle(value: String): String =
    value.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("""\s(feat\.?|ft\.?)\s.*$"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

// So vale a pena ler o total de faixas do arquivo (MediaMetadataRetriever, caro) quando o
// resultado pode mudar o tipo: album curto de um artista so, sem marcacao no nome.
internal fun albumKindNeedsTrackTotal(title: String, songs: List<LocalSong>): Boolean =
    songs.isNotEmpty() &&
        songs.size <= 6 &&
        songs.map { it.artist }.distinct().size == 1 &&
        !SINGLE_MARKER.containsMatchIn(title) &&
        !EP_MARKER.containsMatchIn(title)

internal fun detectAlbumKind(
    title: String,
    songs: List<LocalSong>,
    isVariousArtists: Boolean,
    trackTotal: Int = 0,
): AlbumKind {
    if (isVariousArtists) return AlbumKind.Playlist
    if (SINGLE_MARKER.containsMatchIn(title)) return AlbumKind.Single
    if (EP_MARKER.containsMatchIn(title)) return AlbumKind.EP
    if (songs.isEmpty()) return AlbumKind.Album
    if (trackTotal > 0) {
        return when {
            trackTotal <= 3 -> AlbumKind.Single
            trackTotal <= 6 -> AlbumKind.EP
            else -> AlbumKind.Album
        }
    }
    if (songs.size > 3 || songs.sumOf { it.durationMs } >= SHORT_RELEASE_MAX_MS) return AlbumKind.Album
    val albumName = normalizeReleaseTitle(title)
    val titleMatchesTrack = albumName.isNotBlank() && songs.any { normalizeReleaseTitle(it.title) == albumName }
    return if (titleMatchesTrack) AlbumKind.Single else AlbumKind.Album
}

data class LocalArtist(
    val name: String,
    val songs: List<LocalSong>,
) {
    val key: String = name.lowercase().trim()
    val albumCount: Int = songs.map { it.album }.distinct().size
}

data class DuplicateArtistGroup(
    val canonicalName: String,
    val variants: List<String>,
    val totalSongs: Int,
)

data class AlbumMetadataEdit(
    val albumId: Long,
    val originalTitle: String,
    val title: String,
    val genre: String,
)

data class PendingTagChange(
    val id: String,
    val albumTitle: String,
    val artistName: String,
    val artworkUri: Uri?,
    val songCount: Int,
    val fields: List<String>,
    val songIds: List<Long>,
    val albumValue: String?,
    val artistValue: String?,
    val genreValue: String?,
    val yearValue: Int?,
    // So preenchido pra edicao de UMA faixa (ver saveTrackTitleOverride) - edicao de album nunca
    // mexe no titulo da faixa, so em album/artista/genero/ano compartilhados por todo o album.
    val titleValue: String? = null,
    // Nao-nulo so pra mudanca ESCOPADA A UMA FAIXA (trackPendingTagChanges) - usado pra marcar o
    // "ja gravado" por faixa (trackTagWriteAppliedKey) em vez de por album, senao colidiria com a
    // chave de "ja gravado" de edicao de album (tagWriteAppliedKey usa albumId+titulo do album).
    val trackId: Long? = null,
    val writeFingerprint: String,
)

data class TagWriteResult(
    val albumCount: Int,
    val updatedSongCount: Int,
    val failedSongCount: Int,
)

data class ArtworkCandidate(
    val previewUrl: String,
    val fullUrl: String,
    val label: String,
)

data class LocalRadio(
    val name: String,
    val description: String,
    val songs: List<LocalSong>,
    val coverSongs: List<LocalSong> = songs.distinctBy { it.albumId }.take(4),
    // Radio criada pelo usuario a partir de um album/artista especifico (ver
    // MusicLibraryRepository.createRadioFromAlbum/createRadioFromArtist) - diferente das
    // radios de genero/perfil, essa pode ser excluida. customId identifica a definicao
    // persistida ("album:<id>:..." ou "artist:<chave>").
    val isCustom: Boolean = false,
    val customId: String? = null,
    // true quando a definicao personalizada tem mais de uma fonte (album/artista/categoria) -
    // ver MusicLibraryRepository.addSourceToCustomRadio. Radio com fonte unica de album toca
    // em ordem de faixa (ver radioSessionFrom); a partir de 2 fontes vira sempre embaralhada,
    // nao faz mais sentido "ordem de album" misturando material de fontes diferentes.
    val hasMultipleSources: Boolean = false,
    // As 3 radios padrao fixas no topo da grade (pedido do usuario 22/09/2026: "Surprise Me e a
    // rádio recente no topo... e adicionar uma rádio (músicas curtidas)... essas 3 rádios padrão
    // no topo, fixas") - ver MusicLibraryRepository.radiosFrom (ordem/geracao) e
    // MinimalRadioCard/PlaylistsScreen em LocalTuneApp.kt (stroke vermelho fino + fixadas antes
    // do resto, independente de ordenacao alfabetica/uso).
    val isPinned: Boolean = false,
)

// Playlist montada pelo usuario (Biblioteca > Playlists > "Nova playlist", pedido 24/09/2026).
// Guarda FONTES, nao so faixas: "song:<id>", "artist:<LocalArtist.key>", "album:<LocalAlbum.key>"
// (album, EP ou single), "genre:<nome da categoria>". Artista/album/categoria sao resolvidos toda
// vez (ver MusicLibraryRepository.resolveUserPlaylist), entao musica nova daquele artista entra
// sozinha. Tirar uma faixa que veio de artista/album/categoria vai pra excludedSongIds.
data class UserPlaylist(
    val id: String,
    val name: String,
    val sources: List<String>,
    val excludedSongIds: Set<Long> = emptySet(),
)

data class ResolvedUserPlaylist(
    val playlist: UserPlaylist,
    val songs: List<LocalSong>,
) {
    val id: String get() = playlist.id
    val name: String get() = playlist.name
}
