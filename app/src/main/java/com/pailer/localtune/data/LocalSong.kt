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
) {
    val key: String = "$id:$title:$artist"
    val dateAdded: Long = songs.maxOfOrNull { it.dateAdded } ?: 0L
    val genre: String = songs.firstOrNull { it.genre.isNotBlank() }?.genre.orEmpty()

    // Compilacoes/albuns montados a mao (varios artistas sob o mesmo nome de album) tem o
    // mesmo ALBUM_ID no MediaStore, entao content://.../albumart/<id> devolve a MESMA capa
    // pra todas as faixas mesmo elas tendo capas embutidas diferentes de verdade no arquivo.
    // Usado pra decidir mosaico (em vez de 1 capa so) e extracao por-faixa via MediaMetadataRetriever.
    val isVariousArtists: Boolean = songs.map { it.artist }.distinct().size > 1
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
)
