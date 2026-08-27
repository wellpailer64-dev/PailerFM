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
    val contentUri: Uri,
) {
    val artworkUri: Uri?
        get() = if (albumId > 0) {
            ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)
        } else {
            null
        }

    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
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

data class LocalRadio(
    val name: String,
    val description: String,
    val songs: List<LocalSong>,
    val coverSongs: List<LocalSong> = songs.distinctBy { it.albumId }.take(4),
)
