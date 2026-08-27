package com.pailer.localtune.player

import android.app.PendingIntent
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.ResultReceiver
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pailer.localtune.R
import com.pailer.localtune.data.AlbumGenreSuggestionRepository
import com.pailer.localtune.data.AlbumMetadataEdit
import com.pailer.localtune.data.DuplicateArtistGroup
import com.pailer.localtune.data.LocalAlbum
import com.pailer.localtune.data.LocalArtist
import com.pailer.localtune.data.LocalRadio
import com.pailer.localtune.data.LocalSong
import com.pailer.localtune.data.MusicLibraryRepository
import com.pailer.localtune.data.PendingTagChange
import com.pailer.localtune.data.RadioBulletinDuration
import com.pailer.localtune.data.RadioBulletinMode
import com.pailer.localtune.data.RadioBulletinRepository
import com.pailer.localtune.data.RadioBulletinSettings
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioScriptLine
import com.pailer.localtune.data.RadioScriptSource
import com.pailer.localtune.data.RadioSpeaker
import com.pailer.localtune.data.RadioVoicePackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.random.Random

data class LibraryUiState(
    val songs: List<LocalSong> = emptyList(),
    val historyIds: List<Long> = emptyList(),
    val favoriteAlbumKeys: Set<String> = emptySet(),
    val favoriteSongIds: Set<Long> = emptySet(),
    val favoriteArtistKeys: Set<String> = emptySet(),
    val lastSongId: Long? = null,
    val lastPositionMs: Long = 0L,
    val query: String = "",
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
)

data class LibraryContentUiState(
    val songs: List<LocalSong> = emptyList(),
    val albums: List<LocalAlbum> = emptyList(),
    val artists: List<LocalArtist> = emptyList(),
    val radios: List<LocalRadio> = emptyList(),
    val genreRadios: List<LocalRadio> = emptyList(),
    val availableGenres: List<String> = emptyList(),
    val recentlyAddedAlbums: List<LocalAlbum> = emptyList(),
    val listenAgain: List<LocalSong> = emptyList(),
    val suggestedAlbums: List<LocalAlbum> = emptyList(),
    val favoriteAlbums: List<LocalAlbum> = emptyList(),
    val continueSong: LocalSong? = null,
    val isBuilding: Boolean = false,
)

data class PlayerUiState(
    val songId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: android.net.Uri? = null,
    val playbackSource: String = "",
    val activeRadioName: String = "",
    val currentNewsHeadline: String = "",
    val upcomingTracks: List<String> = emptyList(),
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
)

data class MetadataUiState(
    val missingGenreAlbums: List<LocalAlbum> = emptyList(),
    val missingArtistAlbums: List<LocalAlbum> = emptyList(),
    val duplicateArtistGroups: List<DuplicateArtistGroup> = emptyList(),
    val pendingTagChanges: List<PendingTagChange> = emptyList(),
    val availableGenres: List<String> = emptyList(),
    val availableArtists: List<String> = emptyList(),
    val suggestions: Map<String, String> = emptyMap(),
    val isScanning: Boolean = false,
    val isSuggesting: Boolean = false,
    val message: String? = null,
)

data class RadioBulletinUiState(
    val settings: RadioBulletinSettings = RadioBulletinSettings(),
    val localWriterInstalled: Boolean = false,
    val localWriterName: String = "Redator local",
    val localWriterDetail: String = "Pacote de LLM ainda nao instalado",
)

data class RadioVoiceUiState(
    val isInstalled: Boolean = false,
    val isImporting: Boolean = false,
    val isTesting: Boolean = false,
    val isTestingBulletin: Boolean = false,
    val isEnabled: Boolean = false,
    val packageName: String = "Voz local",
    val engine: String = "",
    val femaleSpeaker: String = "",
    val maleSpeaker: String = "",
    val detail: String = "Nenhum pacote de voz instalado",
    val message: String? = null,
)

private data class LocalVoiceSynthesisResult(
    val file: File?,
    val detail: String,
    val elapsedMs: Long,
)

class LocalTuneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicLibraryRepository(application)
    private val bulletinRepository = RadioBulletinRepository(application)
    private val voicePackageRepository = RadioVoicePackageRepository(application)
    private val genreSuggestionRepository = AlbumGenreSuggestionRepository()
    private val historyPrefs = application.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
    private val favoritePrefs = application.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val radioPrefs = application.getSharedPreferences("radio_bulletins", Context.MODE_PRIVATE)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var ttsRequested = false
    private var lastRecordedMediaId: String? = null
    private var lastPositionPersistedAtMs: Long = 0L
    private var radioNewsEnabled = false
    private var completedRadioSongs = 0
    private var newsBulletins: List<RadioScript> = emptyList()
    private var nextBulletinIndex = 0
    private var speakingNews = false
    private var currentNewsHeadline = ""
    private var resumeAfterNews = false
    private var pendingVinheta = false
    private var announcementPlayer: MediaPlayer? = null
    private var announcementToken = 0
    private var announcementWatchdogJob: Job? = null
    private var libraryScanRunning = false
    private var playbackSource = ""
    private var activeRadioName = ""
    private var requestedTagWriteChanges: List<PendingTagChange> = emptyList()
    private var pendingDeleteSongs: List<LocalSong> = emptyList()
    private var contentBuildGeneration = 0
    private var lastUpcomingQueueKey = ""
    private var cachedUpcomingTracks: List<String> = emptyList()
    private var bulletinPrepJob: Job? = null
    private var preparedBulletinIndex = -1
    private var preparedBulletinFile: File? = null

    var libraryState = androidx.compose.runtime.mutableStateOf(LibraryUiState())
        private set

    var libraryContentState = androidx.compose.runtime.mutableStateOf(LibraryContentUiState())
        private set

    var playerState = androidx.compose.runtime.mutableStateOf(PlayerUiState())
        private set

    var metadataState = androidx.compose.runtime.mutableStateOf(MetadataUiState())
        private set

    var radioBulletinState = androidx.compose.runtime.mutableStateOf(loadRadioBulletinUiState())
        private set

    var radioVoiceState = androidx.compose.runtime.mutableStateOf(loadRadioVoiceUiState())
        private set

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlayerState(player)
            if (player.isPlaying) recordCurrentSong(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (radioNewsEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                completedRadioSongs += 1
                val interval = radioBulletinState.value.settings.songsBetweenBulletins.coerceAtLeast(1)
                if (completedRadioSongs % interval == 0) {
                    speakNextNewsBreak()
                } else if (interval > 1 && completedRadioSongs % interval == interval - 1) {
                    // Ultima musica antes do proximo boletim acabou de comecar: adianta a sintese
                    // da voz local em segundo plano (tem a duracao da faixa inteira disponivel),
                    // entao o boletim toca sem pausa de espera quando o intervalo fechar.
                    prepareUpcomingBulletin()
                }
            }
        }
    }

    init {
        connectToPlaybackService()
        viewModelScope.launch {
            while (true) {
                controller?.let { updatePlayerState(it) }
                delay(if ((controller?.mediaItemCount ?: 0) > 0) 1_500 else 3_000)
            }
        }
    }

    fun loadLibrary() {
        val state = libraryState.value
        if (state.isLoading || state.hasLoaded) return

        viewModelScope.launch {
            libraryState.value = state.copy(isLoading = true, error = null)
            val cachedSongs = runCatching { repository.loadCachedSongs() }.getOrDefault(emptyList())
            if (cachedSongs.isNotEmpty()) {
                applyLibrarySongs(cachedSongs, isLoading = false, hasLoaded = true, query = state.query)
            } else {
                libraryState.value = state.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = null,
                )
            }
        }
    }

    fun refreshLibrary() {
        metadataState.value = MetadataUiState()
        viewModelScope.launch {
            scanLibrary(includeDeviceGenres = true, showLoading = true)
        }
    }

    fun createDeleteRequest(songs: List<LocalSong>): PendingIntent? {
        pendingDeleteSongs = songs
        if (songs.isEmpty()) return null
        return runCatching { repository.createDeleteRequest(songs) }
            .onFailure { error -> showToast(error.message ?: "Nao consegui pedir permissao para apagar.") }
            .getOrNull()
    }

    fun confirmDeleteCompleted() {
        val count = pendingDeleteSongs.size
        pendingDeleteSongs = emptyList()
        showToast(if (count == 1) "Musica apagada do aparelho." else "$count musicas apagadas do aparelho.")
        refreshLibrary()
    }

    fun deletePendingSongsDirectly() {
        val songs = pendingDeleteSongs
        pendingDeleteSongs = emptyList()
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val deleted = repository.deleteSongsDirectly(songs)
            showToast(
                if (deleted == songs.size) {
                    if (deleted == 1) "Musica apagada do aparelho." else "$deleted musicas apagadas do aparelho."
                } else {
                    "Apagadas $deleted de ${songs.size} musicas."
                },
            )
            refreshLibrary()
        }
    }

    fun cancelPendingDelete() {
        pendingDeleteSongs = emptyList()
    }

    fun setRadioBulletinMode(mode: RadioBulletinMode) {
        updateRadioBulletinSettings(radioBulletinState.value.settings.copy(mode = mode))
    }

    fun setRadioBulletinDuration(duration: RadioBulletinDuration) {
        updateRadioBulletinSettings(radioBulletinState.value.settings.copy(duration = duration))
    }

    fun setRadioBulletinPreferLocalWriter(preferLocalWriter: Boolean) {
        updateRadioBulletinSettings(radioBulletinState.value.settings.copy(preferLocalWriter = preferLocalWriter))
    }

    fun importRadioVoicePackage(uri: Uri) {
        radioVoiceState.value = radioVoiceState.value.copy(
            isImporting = true,
            message = "Importando pacote de voz...",
        )
        viewModelScope.launch {
            runCatching { voicePackageRepository.importPackage(uri) }
                .onSuccess { status ->
                    radioPrefs.edit().putBoolean(KEY_RADIO_VOICE_ENABLED, false).apply()
                    radioVoiceState.value = status.toUiState(
                        message = "Pacote importado. Voz local fica desligada ate testarmos com seguranca.",
                    )
                    showToast("Pacote importado. Radio segue usando a voz do Android.")
                }
                .onFailure { error ->
                    val message = error.message ?: "Nao consegui importar o pacote de voz."
                    radioVoiceState.value = loadRadioVoiceUiState().copy(message = message)
                    showToast(message)
                }
        }
    }

    fun clearRadioVoicePackage() {
        val status = voicePackageRepository.clearPackage()
        radioVoiceState.value = status.toUiState(message = "Pacote de voz removido.")
        showToast("Pacote de voz removido.")
    }

    fun setRadioVoiceEnabled(enabled: Boolean) {
        radioPrefs.edit().putBoolean(KEY_RADIO_VOICE_ENABLED, enabled).apply()
        radioVoiceState.value = loadRadioVoiceUiState().copy(
            message = if (enabled) "Voz local experimental ativada." else "Voz local desligada. Usando Android.",
        )
    }

    fun testRadioVoicePackage() {
        if (!radioVoiceState.value.isInstalled) {
            radioVoiceState.value = radioVoiceState.value.copy(message = "Importe um pacote antes de testar.")
            return
        }
        radioVoiceState.value = radioVoiceState.value.copy(
            isTesting = true,
            message = "Testando voz local em processo separado...",
        )
        viewModelScope.launch {
            val script = RadioScript(
                story = com.pailer.localtune.data.NewsStory(
                    title = "Teste de voz local",
                    source = "Pailer Player",
                ),
                source = RadioScriptSource.Fallback,
                lines = listOf(
                    RadioScriptLine(
                        speaker = RadioSpeaker.Female,
                        text = "Teste da voz local do Pailer Player.",
                    ),
                ),
            )
            val result = requestLocalVoiceSynthesis(script, LOCAL_VOICE_TEST_TIMEOUT_MS)
            if (result?.file != null) {
                radioPrefs.edit().putBoolean(KEY_RADIO_VOICE_ENABLED, true).apply()
                radioVoiceState.value = loadRadioVoiceUiState().copy(
                    isTesting = false,
                    message = "Teste OK em ${"%.1f".format(result.elapsedMs / 1000.0)}s. Voz local ativada.",
                )
                playAnnouncementFile(result.file) {}
            } else {
                radioPrefs.edit().putBoolean(KEY_RADIO_VOICE_ENABLED, false).apply()
                radioVoiceState.value = loadRadioVoiceUiState().copy(
                    isTesting = false,
                    message = result?.detail
                        ?: "Teste sem resposta em ${LOCAL_VOICE_TEST_TIMEOUT_MS / 1000}s. Mantive a voz do Android.",
                )
            }
        }
    }

    fun testRadioBulletin() {
        if (!radioVoiceState.value.isInstalled) {
            radioVoiceState.value = radioVoiceState.value.copy(message = "Importe um pacote antes de testar.")
            return
        }
        radioVoiceState.value = radioVoiceState.value.copy(
            isTestingBulletin = true,
            message = "Testando boletim (dialogo com 2 locutores) em processo separado...",
        )
        viewModelScope.launch {
            val script = RadioScript(
                story = com.pailer.localtune.data.NewsStory(
                    title = "Teste de boletim",
                    source = "Pailer Player",
                ),
                source = RadioScriptSource.Fallback,
                lines = listOf(
                    RadioScriptLine(
                        speaker = RadioSpeaker.Female,
                        text = "Noticia rapida: cientistas brasileiros desenvolveram uma tecnica de reciclagem " +
                            "de plastico usando bacterias marinhas.",
                    ),
                    RadioScriptLine(
                        speaker = RadioSpeaker.Male,
                        text = "O estudo foi publicado essa semana e promete reduzir o descarte de garrafas " +
                            "PET em ate setenta por cento nos proximos anos.",
                    ),
                    RadioScriptLine(
                        speaker = RadioSpeaker.Female,
                        text = "Fica a dica, e agora voltamos para a nossa programação normal.",
                    ),
                ),
            )
            val result = requestLocalVoiceSynthesis(script, BULLETIN_PREP_TIMEOUT_MS)
            if (result?.file != null) {
                radioVoiceState.value = loadRadioVoiceUiState().copy(
                    isTestingBulletin = false,
                    message = "Boletim OK em ${"%.1f".format(result.elapsedMs / 1000.0)}s (2 locutores).",
                )
                playAnnouncementFile(result.file) {}
            } else {
                radioVoiceState.value = loadRadioVoiceUiState().copy(
                    isTestingBulletin = false,
                    message = result?.detail
                        ?: "Boletim sem resposta em ${BULLETIN_PREP_TIMEOUT_MS / 1000}s.",
                )
            }
        }
    }

    private fun updateRadioBulletinSettings(settings: RadioBulletinSettings) {
        radioPrefs.edit()
            .putString(KEY_RADIO_BULLETIN_MODE, settings.mode.name)
            .putString(KEY_RADIO_BULLETIN_DURATION, settings.duration.name)
            .putBoolean(KEY_RADIO_BULLETIN_LOCAL_WRITER, settings.preferLocalWriter)
            .apply()
        radioBulletinState.value = loadRadioBulletinUiState()
    }

    private fun loadRadioBulletinUiState(): RadioBulletinUiState {
        val mode = runCatching {
            RadioBulletinMode.valueOf(
                radioPrefs.getString(KEY_RADIO_BULLETIN_MODE, RadioBulletinMode.Dialogue.name)
                    ?: RadioBulletinMode.Dialogue.name,
            )
        }.getOrDefault(RadioBulletinMode.Dialogue)
        val duration = runCatching {
            RadioBulletinDuration.valueOf(
                radioPrefs.getString(KEY_RADIO_BULLETIN_DURATION, RadioBulletinDuration.Short.name)
                    ?: RadioBulletinDuration.Short.name,
            )
        }.getOrDefault(RadioBulletinDuration.Short)
        val settings = RadioBulletinSettings(
            mode = mode,
            songsBetweenBulletins = RADIO_BULLETIN_DEFAULT_INTERVAL,
            duration = duration,
            preferLocalWriter = radioPrefs.getBoolean(KEY_RADIO_BULLETIN_LOCAL_WRITER, true),
        )
        val status = bulletinRepository.localWriterStatus()
        return RadioBulletinUiState(
            settings = settings,
            localWriterInstalled = status.isInstalled,
            localWriterName = status.modelName,
            localWriterDetail = status.detail,
        )
    }

    private fun loadRadioVoiceUiState(): RadioVoiceUiState =
        voicePackageRepository.status().toUiState()

    private fun com.pailer.localtune.data.RadioVoicePackageStatus.toUiState(message: String? = null): RadioVoiceUiState =
        RadioVoiceUiState(
            isInstalled = isInstalled,
            isImporting = false,
            isTesting = false,
            isEnabled = isInstalled && radioPrefs.getBoolean(KEY_RADIO_VOICE_ENABLED, false),
            packageName = packageName,
            engine = engine,
            femaleSpeaker = femaleSpeaker,
            maleSpeaker = maleSpeaker,
            detail = detail,
            message = message,
        )

    private suspend fun scanLibrary(includeDeviceGenres: Boolean, showLoading: Boolean) {
        if (libraryScanRunning) return
        libraryScanRunning = true
        val state = libraryState.value
        if (showLoading) {
            libraryState.value = state.copy(
                isLoading = state.songs.isEmpty(),
                error = null,
            )
        }
        runCatching { repository.loadSongs(includeDeviceGenres = includeDeviceGenres) }
            .onSuccess { songs ->
                repository.saveCachedSongs(songs)
                applyLibrarySongs(
                    songs = songs,
                    isLoading = false,
                    hasLoaded = true,
                    query = libraryState.value.query,
                )
            }
            .onFailure { error ->
                libraryState.value = libraryState.value.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = if (libraryState.value.songs.isEmpty()) {
                        error.message ?: "Nao consegui ler suas musicas locais."
                    } else {
                        null
                    },
                )
            }
        libraryScanRunning = false
    }

    private fun applyLibrarySongs(
        songs: List<LocalSong>,
        isLoading: Boolean,
        hasLoaded: Boolean,
        query: String,
    ) {
        libraryState.value = LibraryUiState(
            songs = songs,
            historyIds = loadHistoryIds(),
            favoriteAlbumKeys = loadFavoriteAlbumKeys(),
            favoriteSongIds = loadFavoriteSongIds(),
            favoriteArtistKeys = loadFavoriteArtistKeys(),
            lastSongId = historyPrefs.getLong(KEY_LAST_SONG_ID, 0L).takeIf { it > 0L },
            lastPositionMs = historyPrefs.getLong(KEY_LAST_POSITION_MS, 0L),
            query = query,
            isLoading = isLoading,
            hasLoaded = hasLoaded,
        )
        libraryContentState.value = libraryContentState.value.copy(
            songs = filterSongs(songs, query),
            isBuilding = true,
        )
        rebuildLibraryContent()
    }

    fun setQuery(query: String) {
        libraryState.value = libraryState.value.copy(query = query)
        libraryContentState.value = libraryContentState.value.copy(
            songs = filterSongs(libraryState.value.songs, query),
            isBuilding = true,
        )
        rebuildLibraryContent()
    }

    fun filteredSongs(): List<LocalSong> {
        val state = libraryState.value
        val query = state.query.trim().lowercase()
        return filterSongs(state.songs, query)
    }

    private fun filterSongs(songs: List<LocalSong>, query: String): List<LocalSong> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return songs
        return songs.filter {
            it.title.lowercase().contains(cleanQuery) ||
                it.artist.lowercase().contains(cleanQuery) ||
                it.album.lowercase().contains(cleanQuery)
        }
    }

    private fun rebuildLibraryContent() {
        val generation = ++contentBuildGeneration
        val state = libraryState.value
        viewModelScope.launch(Dispatchers.Default) {
            val filtered = filterSongs(state.songs, state.query)
            val albums = repository.albumsFrom(filtered)
            val historyIds = state.historyIds
            val historyIdsSet = historyIds.toSet()
            val songsById = filtered.associateBy { it.id }
            val artistsList = repository.artistsFrom(filtered)
            val genreRadiosList = repository.allGenreRadios(filtered)
            val radiosList = repository.radiosFrom(filtered, precomputedGenreRadios = genreRadiosList)
            val availableGenresList = repository.availableGenres(filtered)
            val content = LibraryContentUiState(
                songs = filtered,
                albums = albums,
                artists = artistsList,
                radios = radiosList,
                genreRadios = genreRadiosList,
                availableGenres = availableGenresList,
                recentlyAddedAlbums = albums.sortedByDescending { it.dateAdded }.take(18),
                listenAgain = historyIds.mapNotNull { songsById[it] }.take(18)
                    .ifEmpty { filtered.sortedByDescending { it.dateAdded }.take(18) },
                suggestedAlbums = albums.sortedWith(
                    compareByDescending<LocalAlbum> { album -> album.songs.count { it.id in historyIdsSet } }
                        .thenByDescending { album -> album.songs.maxOfOrNull { it.dateAdded } ?: 0L }
                        .thenBy { it.title.lowercase() }
                ).take(20),
                favoriteAlbums = albums
                    .filter { it.key in state.favoriteAlbumKeys }
                    .sortedBy { it.title.lowercase() },
                continueSong = filtered.firstOrNull { it.id == state.lastSongId }
                    ?: historyIds.firstNotNullOfOrNull { id -> songsById[id] },
                isBuilding = false,
            )
            withContext(Dispatchers.Main) {
                if (generation == contentBuildGeneration) {
                    libraryContentState.value = content
                }
            }
        }
    }

    fun artists(songs: List<LocalSong>): List<LocalArtist> = repository.artistsFrom(songs)

    fun albums(songs: List<LocalSong>): List<LocalAlbum> = repository.albumsFrom(songs)

    fun radios(songs: List<LocalSong>): List<LocalRadio> = repository.radiosFrom(songs)

    fun genreRadios(songs: List<LocalSong>): List<LocalRadio> = repository.allGenreRadios(songs)

    fun availableGenres(songs: List<LocalSong>): List<String> = repository.availableGenres(songs)

    fun createRadioSession(radio: LocalRadio): List<LocalSong> = repository.radioSessionFrom(radio)

    fun listenAgainSongs(songs: List<LocalSong>): List<LocalSong> {
        val byId = songs.associateBy { it.id }
        val history = libraryState.value.historyIds.mapNotNull { byId[it] }
        return if (history.isNotEmpty()) history.take(18) else songs.sortedByDescending { it.dateAdded }.take(18)
    }

    fun continueSong(songs: List<LocalSong>): LocalSong? {
        val state = libraryState.value
        return songs.firstOrNull { it.id == state.lastSongId }
            ?: state.historyIds.firstNotNullOfOrNull { id -> songs.firstOrNull { it.id == id } }
    }

    fun continuePositionMs(): Long = libraryState.value.lastPositionMs

    fun recentlyAddedAlbums(songs: List<LocalSong>): List<LocalAlbum> =
        repository.albumsFrom(songs).sortedByDescending { it.dateAdded }.take(18)

    fun favoriteAlbums(songs: List<LocalSong>): List<LocalAlbum> {
        val favoriteKeys = libraryState.value.favoriteAlbumKeys
        return repository.albumsFrom(songs)
            .filter { it.key in favoriteKeys }
            .sortedBy { it.title.lowercase() }
    }

    fun isAlbumFavorite(album: LocalAlbum): Boolean = album.key in libraryState.value.favoriteAlbumKeys

    fun toggleAlbumFavorite(album: LocalAlbum) {
        val current = libraryState.value.favoriteAlbumKeys
        val updated = if (album.key in current) current - album.key else current + album.key
        libraryState.value = libraryState.value.copy(favoriteAlbumKeys = updated)
        rebuildLibraryContent()
        favoritePrefs.edit()
            .putStringSet(KEY_FAVORITE_ALBUMS, updated)
            .apply()
    }

    fun isSongFavorite(song: LocalSong): Boolean = song.id in libraryState.value.favoriteSongIds

    fun isCurrentSongFavorite(): Boolean =
        playerState.value.songId?.let { it in libraryState.value.favoriteSongIds } ?: false

    fun toggleSongFavorite(song: LocalSong) {
        toggleSongFavorite(song.id)
    }

    fun toggleCurrentSongFavorite() {
        playerState.value.songId?.let(::toggleSongFavorite)
    }

    private fun toggleSongFavorite(songId: Long) {
        val current = libraryState.value.favoriteSongIds
        val updated = if (songId in current) current - songId else current + songId
        libraryState.value = libraryState.value.copy(favoriteSongIds = updated)
        favoritePrefs.edit()
            .putStringSet(KEY_FAVORITE_SONGS, updated.map { it.toString() }.toSet())
            .apply()
    }

    fun isArtistFavorite(artist: LocalArtist): Boolean = artist.key in libraryState.value.favoriteArtistKeys

    fun toggleArtistFavorite(artist: LocalArtist) {
        val current = libraryState.value.favoriteArtistKeys
        val updated = if (artist.key in current) current - artist.key else current + artist.key
        libraryState.value = libraryState.value.copy(favoriteArtistKeys = updated)
        rebuildLibraryContent()
        favoritePrefs.edit()
            .putStringSet(KEY_FAVORITE_ARTISTS, updated)
            .apply()
    }

    fun suggestedAlbums(songs: List<LocalSong>): List<LocalAlbum> {
        val historyIds = libraryState.value.historyIds.toSet()
        return repository.albumsFrom(songs)
            .sortedWith(
                compareByDescending<LocalAlbum> { album -> album.songs.count { it.id in historyIds } }
                    .thenByDescending { album -> album.songs.maxOfOrNull { it.dateAdded } ?: 0L }
                    .thenBy { it.title.lowercase() }
            )
            .take(20)
    }

    fun scanMissingGenres() {
        val songs = libraryState.value.songs
        metadataState.value = metadataState.value.copy(isScanning = true, message = null)
        val missing = repository.albumsWithoutGenre(songs)
        metadataState.value = metadataState.value.copy(
            missingGenreAlbums = missing,
            availableGenres = repository.availableGenres(songs),
            isScanning = false,
            message = if (missing.isEmpty()) "Todos os albuns carregados ja tem genero." else "${missing.size} albuns para revisar.",
        )
    }

    fun suggestCurrentAlbumGenre() {
        val album = metadataState.value.missingGenreAlbums.firstOrNull() ?: return
        if (metadataState.value.isSuggesting) return

        viewModelScope.launch {
            metadataState.value = metadataState.value.copy(isSuggesting = true, message = "Buscando sugestao para ${album.title}...")
            val suggestions = metadataState.value.suggestions.toMutableMap()
            val suggestion = runCatching {
                genreSuggestionRepository.suggestGenre(album, metadataState.value.availableGenres)
            }.getOrNull()
            if (!suggestion.isNullOrBlank()) {
                suggestions[album.key] = suggestion
            }
            metadataState.value = metadataState.value.copy(
                suggestions = suggestions.toMap(),
                isSuggesting = false,
                message = if (suggestion.isNullOrBlank()) "Nao encontrei sugestao para este album." else "Sugestao encontrada: $suggestion",
            )
        }
    }

    fun approveAlbumGenre(album: LocalAlbum, genre: String) {
        repository.saveAlbumGenreOverride(album, genre)
        val updatedSongs = libraryState.value.songs.map { song ->
            if (song.albumId == album.id && song.album == album.title) song.copy(genre = genre) else song
        }
        libraryState.value = libraryState.value.copy(songs = updatedSongs)
        rebuildLibraryContent()
        viewModelScope.launch { repository.saveCachedSongs(updatedSongs) }
        val missing = repository.albumsWithoutGenre(updatedSongs)
        metadataState.value = metadataState.value.copy(
            missingGenreAlbums = missing,
            availableGenres = repository.availableGenres(updatedSongs),
            suggestions = metadataState.value.suggestions - album.key,
            message = if (missing.isEmpty()) "Revisao concluida." else "Genero salvo. Proximo album.",
        )
    }

    fun scanMissingArtists() {
        val songs = libraryState.value.songs
        metadataState.value = metadataState.value.copy(isScanning = true, message = null)
        val missing = repository.albumsWithoutRecognizedArtist(songs)
        metadataState.value = metadataState.value.copy(
            missingArtistAlbums = missing,
            availableArtists = repository.availableArtistNames(songs),
            isScanning = false,
            message = if (missing.isEmpty()) "Todos os albuns carregados ja tem artista." else "${missing.size} albuns sem artista reconhecido.",
        )
    }

    fun approveAlbumArtist(album: LocalAlbum, artist: String) {
        val cleanArtist = artist.trim()
        if (cleanArtist.isBlank()) return
        repository.saveAlbumArtistOverride(album, cleanArtist)
        val updatedSongs = libraryState.value.songs.map { song ->
            if (song.albumId == album.id && song.album == album.title) song.copy(artist = cleanArtist) else song
        }
        libraryState.value = libraryState.value.copy(songs = updatedSongs)
        rebuildLibraryContent()
        viewModelScope.launch { repository.saveCachedSongs(updatedSongs) }
        val missing = repository.albumsWithoutRecognizedArtist(updatedSongs)
        metadataState.value = metadataState.value.copy(
            missingArtistAlbums = missing,
            availableArtists = repository.availableArtistNames(updatedSongs),
            message = if (missing.isEmpty()) "Revisao de artistas concluida." else "Artista salvo. Proximo album.",
        )
    }

    fun saveArtistMetadataEdits(
        artist: LocalArtist,
        artistName: String,
        albumEdits: List<AlbumMetadataEdit>,
    ): LocalArtist {
        val cleanArtist = artistName.trim().ifBlank { artist.name }
        val artistSongIds = artist.songs.map { it.id }.toSet()
        val artistAlbums = repository.albumsFrom(artist.songs)
        val editByAlbum = albumEdits.associateBy { "${it.albumId}:${it.originalTitle}" }
        val albumByKey = artistAlbums.associateBy { "${it.id}:${it.title}" }
        val artistChanged = !cleanArtist.equals(artist.name, ignoreCase = false)
        val changedSongIds = mutableSetOf<Long>()

        artistAlbums.forEach { album ->
            val edit = editByAlbum["${album.id}:${album.title}"]
            val titleChanged = edit?.title?.trim()?.let { it.isNotBlank() && it != album.title } == true
            val genreChanged = edit?.genre?.trim()?.let { it != album.genre } == true
            if (artistChanged || titleChanged || genreChanged) {
                changedSongIds += album.songs.map { it.id }
            }
            if (artistChanged) {
                repository.saveAlbumArtistOverride(album, cleanArtist)
            }
            edit?.title?.trim()
                ?.takeIf { it.isNotBlank() && it != album.title }
                ?.let { repository.saveAlbumTitleOverride(album, it) }
            edit?.genre?.trim()
                ?.takeIf { it != album.genre }
                ?.let { repository.saveAlbumGenreOverride(album, it) }
        }

        val updatedSongs = libraryState.value.songs.map { song ->
            if (song.id !in artistSongIds) {
                song
            } else {
                val edit = editByAlbum["${song.albumId}:${song.album}"]
                val album = albumByKey["${song.albumId}:${song.album}"]
                val cleanTitle = edit?.title?.trim()
                val cleanGenre = edit?.genre?.trim()
                song.copy(
                    artist = if (artistChanged) cleanArtist else song.artist,
                    album = cleanTitle?.takeIf { it.isNotBlank() && it != song.album } ?: song.album,
                    genre = if (album != null && cleanGenre != null && cleanGenre != album.genre) cleanGenre else song.genre,
                )
            }
        }

        libraryState.value = libraryState.value.copy(songs = updatedSongs)
        rebuildLibraryContent()
        viewModelScope.launch { repository.saveCachedSongs(updatedSongs) }
        val pending = repository.pendingTagChanges(updatedSongs)
        requestedTagWriteChanges = pending.filter { change ->
            change.songIds.any { it in changedSongIds }
        }
        metadataState.value = metadataState.value.copy(
            pendingTagChanges = pending,
            message = if (changedSongIds.isEmpty()) {
                "Nada mudou neste artista."
            } else {
                "Metadados salvos. O Android vai pedir permissao para ${changedSongIds.size} faixas."
            },
        )

        return repository.artistsFrom(updatedSongs)
            .firstOrNull { it.name.equals(cleanArtist, ignoreCase = true) }
            ?: LocalArtist(cleanArtist, updatedSongs.filter { it.id in artistSongIds })
    }

    fun saveAlbumMetadataEdit(
        album: LocalAlbum,
        title: String,
        artistName: String,
        genre: String,
    ): LocalAlbum {
        val cleanTitle = title.trim().ifBlank { album.title }
        val cleanArtist = artistName.trim().ifBlank { album.artist }
        val cleanGenre = genre.trim()
        val titleChanged = cleanTitle != album.title
        val artistChanged = cleanArtist != album.artist
        val genreChanged = cleanGenre != album.genre
        val albumSongIds = album.songs.map { it.id }.toSet()

        if (titleChanged) repository.saveAlbumTitleOverride(album, cleanTitle)
        if (artistChanged) repository.saveAlbumArtistOverride(album, cleanArtist)
        if (genreChanged) repository.saveAlbumGenreOverride(album, cleanGenre)

        val updatedSongs = libraryState.value.songs.map { song ->
            if (song.id !in albumSongIds) {
                song
            } else {
                song.copy(
                    artist = if (artistChanged) cleanArtist else song.artist,
                    album = if (titleChanged) cleanTitle else song.album,
                    genre = if (genreChanged) cleanGenre else song.genre,
                )
            }
        }

        libraryState.value = libraryState.value.copy(songs = updatedSongs)
        rebuildLibraryContent()
        viewModelScope.launch { repository.saveCachedSongs(updatedSongs) }
        val pending = repository.pendingTagChanges(updatedSongs)
        requestedTagWriteChanges = if (titleChanged || artistChanged || genreChanged) {
            pending.filter { change -> change.songIds.any { it in albumSongIds } }
        } else {
            emptyList()
        }
        metadataState.value = metadataState.value.copy(
            pendingTagChanges = pending,
            message = if (requestedTagWriteChanges.isEmpty()) {
                "Nada mudou neste album."
            } else {
                "Metadados salvos. O Android vai pedir permissao para ${albumSongIds.size} faixas."
            },
        )

        return repository.albumsFrom(updatedSongs)
            .firstOrNull { updatedAlbum -> updatedAlbum.songs.any { it.id in albumSongIds } }
            ?: album.copy(
                title = cleanTitle,
                artist = cleanArtist,
                songs = album.songs.map {
                    it.copy(
                        artist = if (artistChanged) cleanArtist else it.artist,
                        album = if (titleChanged) cleanTitle else it.album,
                        genre = if (genreChanged) cleanGenre else it.genre,
                    )
                },
            )
    }

    fun scanPendingTagWrites() {
        val songs = libraryState.value.songs
        metadataState.value = metadataState.value.copy(isScanning = true, message = null)
        val pending = repository.pendingTagChanges(songs)
        metadataState.value = metadataState.value.copy(
            pendingTagChanges = pending,
            isScanning = false,
            message = if (pending.isEmpty()) {
                "Nenhuma alteracao pendente para gravar."
            } else {
                "${pending.size} albuns com tags internas prontas para revisar."
            },
        )
    }

    fun createPendingTagWriteRequest(): PendingIntent? {
        val songs = libraryState.value.songs
        val pending = repository.pendingTagChanges(songs)
        requestedTagWriteChanges = pending
        metadataState.value = metadataState.value.copy(
            pendingTagChanges = pending,
            message = if (pending.isEmpty()) {
                "Nenhuma alteracao pendente para gravar."
            } else {
                "O Android vai pedir permissao para atualizar ${pending.sumOf { it.songCount }} faixas."
            },
        )
        if (pending.isEmpty()) return null
        return runCatching { repository.createTagWriteRequest(pending, songs) }
            .onFailure { error ->
                metadataState.value = metadataState.value.copy(
                    message = error.message ?: "Nao consegui abrir a permissao de escrita.",
                )
            }
            .getOrNull()
    }

    fun createRecentMetadataEditWriteRequest(): PendingIntent? {
        val songs = libraryState.value.songs
        val changes = requestedTagWriteChanges
        metadataState.value = metadataState.value.copy(
            pendingTagChanges = repository.pendingTagChanges(songs),
            message = if (changes.isEmpty()) {
                "Nenhuma alteracao recente para gravar."
            } else {
                "O Android vai pedir permissao para atualizar ${changes.sumOf { it.songCount }} faixas."
            },
        )
        if (changes.isEmpty()) return null
        return runCatching { repository.createTagWriteRequest(changes, songs) }
            .onFailure { error ->
                metadataState.value = metadataState.value.copy(
                    message = error.message ?: "Nao consegui abrir a permissao de escrita.",
                )
            }
            .getOrNull()
    }

    fun writePendingTagsToMediaStore() {
        val songs = libraryState.value.songs
        val changes = requestedTagWriteChanges.ifEmpty { repository.pendingTagChanges(songs) }
        if (changes.isEmpty()) {
            showMetadataStatus("Nenhuma alteracao pendente para gravar.")
            return
        }

        writeTagChanges(changes, songs)
    }

    fun writeRecentMetadataEditTagsToMediaStore() {
        val songs = libraryState.value.songs
        val changes = requestedTagWriteChanges
        if (changes.isEmpty()) {
            showMetadataStatus("Nenhuma alteracao recente para gravar.")
            return
        }

        writeTagChanges(changes, songs)
    }

    private fun writeTagChanges(changes: List<PendingTagChange>, songs: List<LocalSong>) {
        metadataState.value = metadataState.value.copy(
            isScanning = true,
            pendingTagChanges = changes,
            message = "Gravando tags na biblioteca do Android...",
        )

        viewModelScope.launch {
            runCatching { repository.writePendingTagsToMediaStore(changes, songs) }
                .onSuccess { result ->
                    val pending = repository.pendingTagChanges(libraryState.value.songs)
                    requestedTagWriteChanges = emptyList()
                    val message = if (result.failedSongCount == 0) {
                        "${result.updatedSongCount} faixas atualizadas com sucesso."
                    } else {
                        "${result.updatedSongCount} faixas atualizadas; ${result.failedSongCount} falharam."
                    }
                    metadataState.value = metadataState.value.copy(
                        pendingTagChanges = pending,
                        isScanning = false,
                        message = message,
                    )
                    showToast(message)
                }
                .onFailure { error ->
                    val message = error.message ?: "Nao consegui gravar as tags."
                    metadataState.value = metadataState.value.copy(
                        isScanning = false,
                        message = message,
                    )
                    showToast(message)
                }
        }
    }

    fun cancelPendingTagWrite() {
        showMetadataStatus("Permissao cancelada. Nada foi alterado.")
    }

    private fun showMetadataStatus(message: String) {
        metadataState.value = metadataState.value.copy(
            isScanning = false,
            message = message,
        )
        showToast(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    fun scanDuplicateArtists() {
        val songs = libraryState.value.songs
        metadataState.value = metadataState.value.copy(isScanning = true, message = null)
        val groups = repository.duplicateArtistGroups(songs)
        metadataState.value = metadataState.value.copy(
            duplicateArtistGroups = groups,
            isScanning = false,
            message = if (groups.isEmpty()) "Nenhum artista duplicado evidente." else "${groups.size} grupos de artistas parecidos.",
        )
    }

    fun unifyArtistGroup(group: DuplicateArtistGroup) {
        repository.saveArtistUnification(group)
        val variants = group.variants.map { it.lowercase().trim() }.toSet()
        val updatedSongs = libraryState.value.songs.map { song ->
            if (song.artist.lowercase().trim() in variants) song.copy(artist = group.canonicalName) else song
        }
        libraryState.value = libraryState.value.copy(songs = updatedSongs)
        rebuildLibraryContent()
        viewModelScope.launch { repository.saveCachedSongs(updatedSongs) }
        val groups = repository.duplicateArtistGroups(updatedSongs)
        metadataState.value = metadataState.value.copy(
            duplicateArtistGroups = groups,
            message = "${group.variants.size} nomes unidos em ${group.canonicalName}.",
        )
    }

    fun playSongs(
        songs: List<LocalSong>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        startPositionMs: Long = 0L,
        keepRadioNews: Boolean = false,
        source: String = "",
        radioName: String = "",
        autoPlay: Boolean = true,
    ) {
        if (songs.isEmpty()) return
        if (!keepRadioNews) stopRadioNewsMode()
        playbackSource = source
        activeRadioName = radioName
        val index = if (shuffle && startIndex == 0 && songs.size > 1) {
            Random.nextInt(songs.size)
        } else {
            startIndex.coerceIn(songs.indices)
        }
        recordPlayback(songs[index].id)
        controller?.apply {
            shuffleModeEnabled = shuffle
            setMediaItems(songs.map { it.toMediaItem() }, index, startPositionMs.coerceAtLeast(0L))
            prepare()
            if (autoPlay) play()
        }
    }

    fun playRadioSession(songs: List<LocalSong>, radioName: String, startIndex: Int = 0) {
        startRadioNewsMode(radioName)
        val playVinhetas = startIndex == 0
        playSongs(
            songs = songs,
            startIndex = startIndex,
            keepRadioNews = true,
            source = "Rádio $radioName",
            radioName = radioName,
            autoPlay = !playVinhetas,
        )
        if (playVinhetas) {
            playRadioVinhetas(radioName)
        }
    }

    fun continuePlayback(songs: List<LocalSong>) {
        val song = continueSong(songs) ?: return
        val albumSongs = songs.filter { it.albumId == song.albumId && it.album == song.album }
            .sortedWith(compareBy<LocalSong> { it.trackNumber }.thenBy { it.title.lowercase() })
            .ifEmpty { songs }
        val index = albumSongs.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        playSongs(albumSongs, index, startPositionMs = continuePositionMs(), source = "Continuar ouvindo")
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() {
        if (activeRadioName.isNotBlank()) return
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        if (activeRadioName.isNotBlank()) return
        controller?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    private fun setupTextToSpeech(context: Context) {
        if (ttsRequested) return
        ttsRequested = true
        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale("pt", "BR")
                textToSpeech?.setSpeechRate(0.98f)
                textToSpeech?.setPitch(0.88f)
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    finishNewsBreak()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    finishNewsBreak()
                }
            })
        }
    }

    private fun startRadioNewsMode(radioName: String) {
        setupTextToSpeech(getApplication())
        val settings = radioBulletinState.value.settings
        radioNewsEnabled = settings.mode != RadioBulletinMode.Off
        completedRadioSongs = 0
        newsBulletins = emptyList()
        nextBulletinIndex = 0
        clearPreparedBulletin()
        speakingNews = false
        currentNewsHeadline = ""
        resumeAfterNews = false
        pendingVinheta = false
        activeRadioName = radioName
        playbackSource = "Rádio $radioName"

        viewModelScope.launch {
            newsBulletins = runCatching {
                bulletinRepository.loadScripts(settings, radioName)
            }.getOrDefault(emptyList())
        }
    }

    private fun stopRadioNewsMode() {
        radioNewsEnabled = false
        completedRadioSongs = 0
        nextBulletinIndex = 0
        clearPreparedBulletin()
        speakingNews = false
        currentNewsHeadline = ""
        resumeAfterNews = false
        pendingVinheta = false
        activeRadioName = ""
        announcementPlayer?.release()
        announcementPlayer = null
        textToSpeech?.stop()
    }

    private fun playRadioVinhetas(radioName: String) {
        pendingVinheta = true
        val complementRes = VINHETA_BY_RADIO_KEY[normalizeRadioKey(radioName)]
        playVinhetaResource(R.raw.radio_intro) {
            if (!pendingVinheta || activeRadioName != radioName) return@playVinhetaResource
            if (complementRes != null) {
                playVinhetaResource(complementRes) { finishVinhetas(radioName) }
            } else {
                finishVinhetas(radioName)
            }
        }
    }

    private fun finishVinhetas(radioName: String) {
        if (!pendingVinheta || activeRadioName != radioName) return
        pendingVinheta = false
        viewModelScope.launch {
            controller?.play()
            controller?.let { updatePlayerState(it) }
        }
    }

    private fun playVinhetaResource(resId: Int, onFinished: () -> Unit) {
        announcementPlayer?.release()
        announcementPlayer = null
        val player = runCatching {
            MediaPlayer.create(getApplication(), resId)
        }.getOrNull()
        if (player == null) {
            onFinished()
            return
        }
        player.setOnCompletionListener {
            it.release()
            if (announcementPlayer === it) announcementPlayer = null
            onFinished()
        }
        player.setOnErrorListener { errored, _, _ ->
            errored.release()
            if (announcementPlayer === errored) announcementPlayer = null
            onFinished()
            true
        }
        announcementPlayer = player
        armAnnouncementWatchdog()
        player.start()
    }

    private fun normalizeRadioKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    private fun prepareUpcomingBulletin() {
        if (!radioVoiceState.value.isEnabled || newsBulletins.isEmpty()) return
        if (bulletinPrepJob?.isActive == true) return
        val index = nextBulletinIndex % newsBulletins.size
        val script = newsBulletins[index]
        bulletinPrepJob = viewModelScope.launch {
            val file = synthesizeLocalVoiceSafely(script, BULLETIN_PREP_TIMEOUT_MS)
            if (file != null) {
                preparedBulletinIndex = index
                preparedBulletinFile = file
            }
        }
    }

    private fun clearPreparedBulletin() {
        bulletinPrepJob?.cancel()
        bulletinPrepJob = null
        preparedBulletinFile?.delete()
        preparedBulletinFile = null
        preparedBulletinIndex = -1
    }

    private fun speakNextNewsBreak() {
        if (!ttsReady || speakingNews || newsBulletins.isEmpty()) return
        val player = controller ?: return
        val bulletinIndex = nextBulletinIndex % newsBulletins.size
        val bulletin = newsBulletins[bulletinIndex]
        nextBulletinIndex += 1
        val readyFile = preparedBulletinFile.takeIf { preparedBulletinIndex == bulletinIndex && it?.exists() == true }
        if (preparedBulletinFile != null && readyFile == null) preparedBulletinFile?.delete()
        bulletinPrepJob?.cancel()
        bulletinPrepJob = null
        preparedBulletinFile = null
        preparedBulletinIndex = -1
        speakingNews = true
        currentNewsHeadline = bulletin.displayText
        controller?.let { updatePlayerState(it) }
        resumeAfterNews = player.isPlaying
        if (resumeAfterNews) player.pause()

        if (readyFile != null) {
            playAnnouncementFile(readyFile) { finishNewsBreak() }
            armAnnouncementWatchdog()
            return
        }

        viewModelScope.launch {
            val localFile = if (radioVoiceState.value.isEnabled) {
                synthesizeLocalVoiceSafely(bulletin, LOCAL_VOICE_TIMEOUT_MS)
            } else {
                null
            }
            if (!speakingNews || !radioNewsEnabled) return@launch
            if (localFile != null) {
                playAnnouncementFile(localFile) { finishNewsBreak() }
                armAnnouncementWatchdog()
                return@launch
            }
            val tts = textToSpeech
            val accepted = if (tts != null && ttsReady) {
                runCatching {
                    tts.speak(bulletin.spokenText, TextToSpeech.QUEUE_FLUSH, null, NEWS_UTTERANCE_ID)
                }.getOrDefault(TextToSpeech.ERROR)
            } else {
                TextToSpeech.ERROR
            }
            if (accepted != TextToSpeech.SUCCESS) {
                Log.w(TAG_RADIO_VOICE, "tts fallback rejected bulletin - resuming playback")
                finishNewsBreak()
            } else {
                armAnnouncementWatchdog()
            }
        }
    }

    private fun armAnnouncementWatchdog() {
        val token = ++announcementToken
        announcementWatchdogJob?.cancel()
        announcementWatchdogJob = viewModelScope.launch {
            delay(ANNOUNCEMENT_WATCHDOG_TIMEOUT_MS)
            if (token != announcementToken) return@launch
            if (!speakingNews && !pendingVinheta) return@launch
            Log.w(TAG_RADIO_VOICE, "announcement watchdog fired - forcing playback resume")
            runCatching { announcementPlayer?.release() }
            announcementPlayer = null
            runCatching { textToSpeech?.stop() }
            if (pendingVinheta) finishVinhetas(activeRadioName) else finishNewsBreak()
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean =
        (getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) == true

    fun createBatteryOptimizationExemptionIntent(): Intent? {
        val app = getApplication<Application>()
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        if (powerManager.isIgnoringBatteryOptimizations(app.packageName)) return null
        return Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
    }

    private fun finishNewsBreak() {
        if (!speakingNews) return
        speakingNews = false
        currentNewsHeadline = ""
        if (resumeAfterNews) {
            resumeAfterNews = false
            viewModelScope.launch {
                controller?.play()
            }
        }
        controller?.let { updatePlayerState(it) }
    }

    private fun playAnnouncementFile(file: java.io.File, onFinished: () -> Unit) {
        announcementPlayer?.release()
        announcementPlayer = null
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (announcementPlayer === it) announcementPlayer = null
                    file.delete()
                    onFinished()
                }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    if (announcementPlayer === player) announcementPlayer = null
                    file.delete()
                    onFinished()
                    true
                }
                prepare()
                start()
                announcementPlayer = this
            }
        }.onFailure {
            file.delete()
            onFinished()
        }
    }

    private suspend fun synthesizeLocalVoiceSafely(
        script: RadioScript,
        timeoutMs: Long = LOCAL_VOICE_TIMEOUT_MS,
    ): File? = requestLocalVoiceSynthesis(script, timeoutMs)?.file

    private suspend fun requestLocalVoiceSynthesis(
        script: RadioScript,
        timeoutMs: Long,
    ): LocalVoiceSynthesisResult? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val context = getApplication<Application>()
                val startedAt = System.currentTimeMillis()
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (!continuation.isActive) return
                        val elapsedMs = resultData
                            ?.getLong(RadioVoiceSynthesisService.EXTRA_ELAPSED_MS, 0L)
                            ?.takeIf { it > 0L }
                            ?: (System.currentTimeMillis() - startedAt)
                        val detail = resultData
                            ?.getString(RadioVoiceSynthesisService.EXTRA_DETAIL)
                            .orEmpty()
                        val file = resultData
                            ?.getString(RadioVoiceSynthesisService.EXTRA_OUTPUT_PATH)
                            ?.let(::File)
                            ?.takeIf { it.exists() && it.length() > 44 }
                        val result = if (resultCode == RadioVoiceSynthesisService.RESULT_OK && file != null) {
                            LocalVoiceSynthesisResult(
                                file = file,
                                detail = detail.ifBlank { "Audio local gerado." },
                                elapsedMs = elapsedMs,
                            )
                        } else {
                            LocalVoiceSynthesisResult(
                                file = null,
                                detail = detail.ifBlank { "Servico de voz nao gerou audio." },
                                elapsedMs = elapsedMs,
                            )
                        }
                        Log.d(
                            TAG_RADIO_VOICE,
                            "voice result code=$resultCode detail='${result.detail}' elapsed=${result.elapsedMs}ms file=${file?.length() ?: 0}",
                        )
                        continuation.resume(result)
                    }
                }
                Log.d(TAG_RADIO_VOICE, "request voice lines=${script.lines.size} timeout=${timeoutMs}ms")
                continuation.invokeOnCancellation {
                    Log.w(TAG_RADIO_VOICE, "voice request cancelled after ${System.currentTimeMillis() - startedAt}ms")
                }
                runCatching {
                    context.startService(RadioVoiceSynthesisService.intent(context, script, receiver))
                }.onFailure {
                    Log.e(TAG_RADIO_VOICE, "failed to start local voice service", it)
                    if (continuation.isActive) {
                        continuation.resume(
                            LocalVoiceSynthesisResult(
                                file = null,
                                detail = "Nao consegui iniciar o servico de voz local: ${it.message.orEmpty()}",
                                elapsedMs = System.currentTimeMillis() - startedAt,
                            ),
                        )
                    }
                }
            }
        }

    private fun String.toSingleLineScript(): RadioScript =
        RadioScript(
            story = com.pailer.localtune.data.NewsStory(title = this, source = "Pailer Player"),
            source = RadioScriptSource.Fallback,
            lines = listOf(RadioScriptLine(RadioSpeaker.Female, this)),
        )

    private fun connectToPlaybackService() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controller = future.get().also {
                    it.addListener(playerListener)
                    updatePlayerState(it)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun updatePlayerState(player: Player) {
        val metadata = player.currentMediaItem?.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        persistLastPlayback(player)
        val queueKey = "${player.currentMediaItemIndex}:${player.mediaItemCount}:${player.currentMediaItem?.mediaId.orEmpty()}"
        if (queueKey != lastUpcomingQueueKey) {
            lastUpcomingQueueKey = queueKey
            cachedUpcomingTracks = player.upcomingTracks()
        }
        playerState.value = PlayerUiState(
            songId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            album = metadata?.albumTitle?.toString().orEmpty(),
            artworkUri = metadata?.artworkUri,
            playbackSource = playbackSource,
            activeRadioName = activeRadioName,
            currentNewsHeadline = currentNewsHeadline,
            upcomingTracks = cachedUpcomingTracks,
            isPlaying = player.isPlaying,
            hasMedia = player.mediaItemCount > 0,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueIndex = player.currentMediaItemIndex + 1,
            queueSize = player.mediaItemCount,
        )
    }

    private fun Player.upcomingTracks(): List<String> =
        ((currentMediaItemIndex + 1) until mediaItemCount)
            .take(5)
            .mapNotNull { index ->
                val metadata = getMediaItemAt(index).mediaMetadata
                val title = metadata.title?.toString().orEmpty()
                val artist = metadata.artist?.toString().orEmpty()
                if (title.isBlank()) null else "$title${artist.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}"
            }

    private fun recordCurrentSong(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (mediaId == lastRecordedMediaId) return
        lastRecordedMediaId = mediaId
        mediaId.toLongOrNull()?.let(::recordPlayback)
    }

    private fun recordPlayback(songId: Long) {
        val updated = (listOf(songId) + libraryState.value.historyIds)
            .distinct()
            .take(MAX_HISTORY_ITEMS)
        libraryState.value = libraryState.value.copy(historyIds = updated, lastSongId = songId)
        historyPrefs.edit()
            .putString(KEY_HISTORY_IDS, updated.joinToString(","))
            .putLong(KEY_LAST_SONG_ID, songId)
            .apply()
    }

    private fun persistLastPlayback(player: Player) {
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val now = System.currentTimeMillis()
        if (now - lastPositionPersistedAtMs < POSITION_SAVE_INTERVAL_MS) return
        lastPositionPersistedAtMs = now
        val position = player.currentPosition.coerceAtLeast(0L)
        libraryState.value = libraryState.value.copy(
            lastSongId = songId,
            lastPositionMs = position,
        )
        historyPrefs.edit()
            .putLong(KEY_LAST_SONG_ID, songId)
            .putLong(KEY_LAST_POSITION_MS, position)
            .apply()
    }

    private fun loadHistoryIds(): List<Long> =
        historyPrefs.getString(KEY_HISTORY_IDS, null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.take(MAX_HISTORY_ITEMS)
            ?: emptyList()

    private fun loadFavoriteAlbumKeys(): Set<String> =
        favoritePrefs.getStringSet(KEY_FAVORITE_ALBUMS, emptySet())?.toSet() ?: emptySet()

    private fun loadFavoriteSongIds(): Set<Long> =
        favoritePrefs.getStringSet(KEY_FAVORITE_SONGS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    private fun loadFavoriteArtistKeys(): Set<String> =
        favoritePrefs.getStringSet(KEY_FAVORITE_ARTISTS, emptySet())?.toSet() ?: emptySet()

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        announcementPlayer?.release()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        controller = null
        super.onCleared()
    }

    private companion object {
        const val KEY_HISTORY_IDS = "history_ids"
        const val KEY_LAST_SONG_ID = "last_song_id"
        const val KEY_LAST_POSITION_MS = "last_position_ms"
        const val KEY_FAVORITE_ALBUMS = "favorite_album_keys"
        const val KEY_FAVORITE_SONGS = "favorite_song_ids"
        const val KEY_FAVORITE_ARTISTS = "favorite_artist_keys"
        const val KEY_RADIO_BULLETIN_MODE = "radio_bulletin_mode"
        const val KEY_RADIO_BULLETIN_DURATION = "radio_bulletin_duration"
        const val KEY_RADIO_BULLETIN_LOCAL_WRITER = "radio_bulletin_local_writer"
        const val KEY_RADIO_VOICE_ENABLED = "radio_voice_enabled"
        const val MAX_HISTORY_ITEMS = 80
        const val POSITION_SAVE_INTERVAL_MS = 3000L
        const val RADIO_BULLETIN_DEFAULT_INTERVAL = 3
        const val TTS_READY_WAIT_STEPS = 10
        const val TTS_READY_WAIT_INTERVAL_MS = 180L
        const val LOCAL_VOICE_TIMEOUT_MS = 12_000L
        // Pre-geracao roda em segundo plano durante a musica anterior ao boletim (minutos
        // disponiveis), entao pode esperar bem mais que o timeout usado na hora H do boletim.
        // Medido em campo: dialogo de 3 falas (fem->masc->fem) leva ~60-65s porque cada troca
        // de locutor recarrega um engine sherpa inteiro do disco (~16-18s cada); 45s cortava o
        // processo poucos segundos antes de terminar.
        const val BULLETIN_PREP_TIMEOUT_MS = 120_000L
        const val ANNOUNCEMENT_WATCHDOG_TIMEOUT_MS = 90_000L
        const val LOCAL_VOICE_TEST_TIMEOUT_MS = 35_000L
        const val TAG_RADIO_VOICE = "PailerRadioVoice"
        const val NEWS_UTTERANCE_ID = "pailer_player_news_break"

        // Chave = nome da radio normalizado (lowercase, so letras/digitos) — ver
        // normalizeRadioKey(). Nomes vem de MusicLibraryRepository (GENRE_DISPLAY_NAMES e
        // RADIO_PROFILES). "Indie" e "Indie / psicodelico" dividem o mesmo complemento, assim
        // como "Punk" e "Post-punk / cold wave" — a pedido do usuario ao gravar as vinhetas.
        val VINHETA_BY_RADIO_KEY: Map<String, Int> = mapOf(
            "grunge" to R.raw.vinheta_grunge,
            "rock" to R.raw.vinheta_rock,
            "punk" to R.raw.vinheta_punk_coldwave,
            "postpunkcoldwave" to R.raw.vinheta_punk_coldwave,
            "indie" to R.raw.vinheta_indie_psicodelico,
            "indiepsicodelico" to R.raw.vinheta_indie_psicodelico,
            "mpb" to R.raw.vinheta_mpb,
            "hiphoprap" to R.raw.vinheta_hip_hop,
            "rapnacional" to R.raw.vinheta_rap,
        )
    }
}
