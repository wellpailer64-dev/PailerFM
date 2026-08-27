package com.pailer.localtune.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.pailer.localtune.R
import com.pailer.localtune.data.AlbumMetadataEdit
import com.pailer.localtune.data.DuplicateArtistGroup
import com.pailer.localtune.data.LocalAlbum
import com.pailer.localtune.data.LocalArtist
import com.pailer.localtune.data.LocalRadio
import com.pailer.localtune.data.LocalSong
import com.pailer.localtune.data.PendingTagChange
import com.pailer.localtune.data.RadioBulletinDuration
import com.pailer.localtune.data.RadioBulletinMode
import com.pailer.localtune.player.LocalTuneViewModel
import com.pailer.localtune.player.MetadataUiState
import com.pailer.localtune.player.PlayerUiState
import com.pailer.localtune.player.RadioBulletinUiState
import com.pailer.localtune.player.RadioVoiceUiState
import com.pailer.localtune.ui.theme.LocalTuneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LibraryTab(val label: String) {
    Home("Inicio"),
    Artists("Artistas"),
    Albums("Albuns"),
    Songs("Musicas"),
    Genres("Categorias"),
    Playlists("Radio"),
}

private enum class SettingsPage {
    Main,
    Metadata,
    AlbumArtists,
    TagWriter,
    Artists,
    RadioBulletins,
}

@Composable
fun LocalTuneApp(viewModel: LocalTuneViewModel = viewModel()) {
    LocalTuneTheme {
        val context = LocalContext.current
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val requestedPermissions = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }

        var hasAudioPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            hasAudioPermission = result[audioPermission] == true ||
                ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
            if (hasAudioPermission) viewModel.loadLibrary()
        }

        LaunchedEffect(hasAudioPermission) {
            if (hasAudioPermission) viewModel.loadLibrary()
        }

        if (hasAudioPermission) {
            LibraryShell(viewModel = viewModel)
        } else {
            PermissionGate(onGrant = { permissionLauncher.launch(requestedPermissions.toTypedArray()) })
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.pailer_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Pailer Player",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Um player pessoal para suas musicas salvas no celular.",
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onGrant) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ler musicas locais")
            }
        }
    }
}

@Composable
private fun LibraryShell(viewModel: LocalTuneViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.Home) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    var selectedAlbum by remember { mutableStateOf<LocalAlbum?>(null) }
    var selectedArtist by remember { mutableStateOf<LocalArtist?>(null) }
    var selectedRadio by remember { mutableStateOf<LocalRadio?>(null) }
    var selectedGenre by remember { mutableStateOf<LocalRadio?>(null) }
    var radioSession by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var isGeneratingRadio by remember { mutableStateOf(false) }
    var pendingDeleteSong by remember { mutableStateOf<LocalSong?>(null) }
    var pendingDeleteAlbum by remember { mutableStateOf<LocalAlbum?>(null) }
    var pendingDeleteArtist by remember { mutableStateOf<LocalArtist?>(null) }
    val scope = rememberCoroutineScope()
    val tagWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.writePendingTagsToMediaStore()
        } else {
            viewModel.cancelPendingTagWrite()
        }
    }
    val requestRecentMetadataEditWrite = {
        val request = viewModel.createRecentMetadataEditWriteRequest()
        if (request != null) {
            tagWriteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            viewModel.writePendingTagsToMediaStore()
        }
    }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.confirmDeleteCompleted()
        } else {
            viewModel.cancelPendingDelete()
        }
    }
    val requestDelete = { songsToDelete: List<LocalSong> ->
        val request = viewModel.createDeleteRequest(songsToDelete)
        if (request != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            viewModel.deletePendingSongsDirectly()
        }
    }
    val voicePackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importRadioVoicePackage)
    }
    val library = viewModel.libraryState.value
    val content = viewModel.libraryContentState.value
    val player = viewModel.playerState.value
    val metadata = viewModel.metadataState.value
    val radioBulletins = viewModel.radioBulletinState.value
    val radioVoice = viewModel.radioVoiceState.value
    val songs = content.songs
    val albums = content.albums
    val artists = content.artists
    val radios = content.radios
    val genreRadios = content.genreRadios
    val availableGenres = content.availableGenres
    val recentlyAddedAlbums = content.recentlyAddedAlbums
    val listenAgain = content.listenAgain
    val suggestedAlbums = content.suggestedAlbums
    val favoriteAlbums = content.favoriteAlbums
    val continueSong = content.continueSong
    val openedArtistAlbums = remember(selectedArtist) {
        selectedArtist?.let { viewModel.albums(it.songs) } ?: emptyList()
    }
    val openedGenreArtists = remember(selectedGenre) {
        selectedGenre?.let { viewModel.artists(it.songs) } ?: emptyList()
    }
    val canHandleBack = showFullPlayer ||
        showSettings ||
        selectedAlbum != null ||
        selectedArtist != null ||
        selectedRadio != null ||
        selectedGenre != null ||
        library.query.isNotBlank() ||
        selectedTab != LibraryTab.Home

    BackHandler(enabled = canHandleBack) {
        when {
            showFullPlayer -> showFullPlayer = false
            showSettings -> {
                if (settingsPage == SettingsPage.Main) {
                    showSettings = false
                } else {
                    settingsPage = SettingsPage.Main
                }
            }
            selectedAlbum != null -> selectedAlbum = null
            selectedRadio != null -> {
                radioSession = emptyList()
                selectedRadio = null
            }
            selectedArtist != null -> selectedArtist = null
            selectedGenre != null -> selectedGenre = null
            library.query.isNotBlank() -> viewModel.setQuery("")
            selectedTab != LibraryTab.Home -> selectedTab = LibraryTab.Home
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(appBackgroundBrush())
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    if (player.hasMedia) {
                        MiniPlayer(
                            player = player,
                            onOpen = { showFullPlayer = true },
                            onToggle = viewModel::togglePlayPause,
                            onNext = viewModel::skipNext,
                        )
                    }
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = Color(0xF015100C),
                    ) {
                        LibraryTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedRadio = null
                                    selectedGenre = null
                                    radioSession = emptyList()
                                    isGeneratingRadio = false
                                    selectedTab = tab
                                },
                                icon = { Icon(tab.icon(), contentDescription = tab.label) },
                                label = {
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        fontSize = 10.sp,
                                    )
                                },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                LibraryHeader(
                    selectedTab = selectedTab,
                    titleOverride = when {
                        selectedAlbum != null -> "Album"
                        selectedArtist != null -> "Artista"
                        selectedRadio != null -> "Radio"
                        selectedGenre != null -> "Categoria"
                        else -> null
                    },
                    query = library.query,
                    isLoading = library.isLoading,
                    onQueryChange = viewModel::setQuery,
                    onSettings = {
                        settingsPage = SettingsPage.Main
                        showSettings = true
                    },
                    showSearch = selectedTab != LibraryTab.Playlists,
                )

                val openedAlbum = selectedAlbum
                val openedArtist = selectedArtist
                val openedRadio = selectedRadio
                val openedGenre = selectedGenre
                when {
                    library.isLoading -> LoadingLibrary()
                    library.error != null -> ErrorLibrary(library.error, onRefresh = viewModel::refreshLibrary)
                    songs.isEmpty() -> EmptyLibrary(hasQuery = library.query.isNotBlank())
                    openedAlbum != null -> AlbumDetailScreen(
                        album = openedAlbum,
                        isFavorite = viewModel.isAlbumFavorite(openedAlbum),
                        onBack = { selectedAlbum = null },
                        onToggleFavorite = { viewModel.toggleAlbumFavorite(openedAlbum) },
                        onPlayAlbum = { viewModel.playSongs(openedAlbum.songs, source = "Álbum ${openedAlbum.title}") },
                        onShuffleAlbum = { viewModel.playSongs(openedAlbum.songs, shuffle = true, source = "Mix do álbum ${openedAlbum.title}") },
                        onPlaySong = { index -> viewModel.playSongs(openedAlbum.songs, index, source = "Álbum ${openedAlbum.title}") },
                        isSongFavorite = viewModel::isSongFavorite,
                        onToggleSongFavorite = viewModel::toggleSongFavorite,
                        availableGenres = availableGenres,
                        onSaveMetadata = { title, artistName, genre ->
                            selectedAlbum = viewModel.saveAlbumMetadataEdit(openedAlbum, title, artistName, genre)
                            requestRecentMetadataEditWrite()
                        },
                    )
                    openedArtist != null -> ArtistDetailScreen(
                        artist = openedArtist,
                        albums = openedArtistAlbums,
                        isFavorite = viewModel.isArtistFavorite(openedArtist),
                        onBack = { selectedArtist = null },
                        onToggleFavorite = { viewModel.toggleArtistFavorite(openedArtist) },
                        onOpenAlbum = { selectedAlbum = it },
                        onShuffleArtist = { viewModel.playSongs(openedArtist.songs, shuffle = true, source = "Mix do artista ${openedArtist.name}") },
                        availableGenres = availableGenres,
                        onSaveMetadata = { artistName, edits ->
                            selectedArtist = viewModel.saveArtistMetadataEdits(openedArtist, artistName, edits)
                            requestRecentMetadataEditWrite()
                        },
                    )
                    openedRadio != null -> RadioDetailScreen(
                        radio = openedRadio,
                        player = player,
                        sessionSongs = radioSession,
                        isGenerating = isGeneratingRadio,
                        onBack = {
                            radioSession = emptyList()
                            selectedRadio = null
                            isGeneratingRadio = false
                        },
                        onEnterRadio = {
                            if (!isGeneratingRadio) {
                                isGeneratingRadio = true
                                scope.launch {
                                    val session = withContext(Dispatchers.Default) {
                                        viewModel.createRadioSession(openedRadio)
                                    }
                                    radioSession = session
                                    isGeneratingRadio = false
                                    viewModel.playRadioSession(session, openedRadio.name)
                                }
                            }
                        },
                        onPlaySong = { index -> viewModel.playRadioSession(radioSession, openedRadio.name, startIndex = index) },
                        onOpenPlayer = { showFullPlayer = true },
                    )
                    openedGenre != null -> GenreDetailScreen(
                        genre = openedGenre,
                        artists = openedGenreArtists,
                        onOpenArtist = { selectedArtist = it },
                        onShuffleGenre = { viewModel.playSongs(openedGenre.songs, shuffle = true, source = "Mix da categoria ${openedGenre.name}") },
                    )
                    selectedTab == LibraryTab.Home -> HomeScreen(
                        songs = songs,
                        continueSong = continueSong,
                        continuePositionMs = viewModel.continuePositionMs(),
                        recentlyAddedAlbums = recentlyAddedAlbums,
                        listenAgain = listenAgain,
                        suggestedAlbums = suggestedAlbums,
                        favoriteAlbums = favoriteAlbums,
                        radios = radios,
                        player = player,
                        onContinue = { viewModel.continuePlayback(songs) },
                        onOpenAlbum = { selectedAlbum = it },
                        onOpenRadio = {
                            radioSession = emptyList()
                            selectedRadio = it
                        },
                        onOpenPlayer = { showFullPlayer = true },
                        onPlay = { list, index, shuffle -> viewModel.playSongs(list, index, shuffle, source = if (shuffle) "Misturar tudo" else "Biblioteca") },
                    )
                    selectedTab == LibraryTab.Artists -> ArtistsScreen(
                        artists = artists,
                        onOpenArtist = { selectedArtist = it },
                        onDeleteArtist = { pendingDeleteArtist = it },
                    )
                    selectedTab == LibraryTab.Albums -> AlbumsScreen(
                        albums = albums,
                        onOpenAlbum = { selectedAlbum = it },
                        onDeleteAlbum = { pendingDeleteAlbum = it },
                    )
                    selectedTab == LibraryTab.Songs -> SongsScreen(
                        songs = songs,
                        isSongFavorite = viewModel::isSongFavorite,
                        onToggleSongFavorite = viewModel::toggleSongFavorite,
                        onPlay = { index -> viewModel.playSongs(songs, index, source = "Músicas") },
                        onDeleteSong = { pendingDeleteSong = it },
                    )
                    selectedTab == LibraryTab.Genres -> PlaylistsScreen(
                        radios = genreRadios,
                        player = player,
                        onOpenRadio = {
                            radioSession = emptyList()
                            selectedGenre = it
                        },
                        onOpenPlayer = { showFullPlayer = true },
                    )
                    selectedTab == LibraryTab.Playlists -> PlaylistsScreen(
                        radios = radios,
                        player = player,
                        onOpenRadio = {
                            radioSession = emptyList()
                            selectedRadio = it
                        },
                        onOpenPlayer = { showFullPlayer = true },
                    )
                }
            }
        }

        val activityContext = LocalContext.current
        SettingsDrawer(
            visible = showSettings,
            page = settingsPage,
            metadata = metadata,
            radioBulletins = radioBulletins,
            radioVoice = radioVoice,
            batteryProtected = viewModel.isIgnoringBatteryOptimizations(),
            onRequestBatteryExemption = {
                viewModel.createBatteryOptimizationExemptionIntent()?.let { intent ->
                    activityContext.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onClose = { showSettings = false },
            onPageChange = { settingsPage = it },
            onSync = viewModel::refreshLibrary,
            onScanMetadata = viewModel::scanMissingGenres,
            onSuggestGenres = viewModel::suggestCurrentAlbumGenre,
            onApproveGenre = viewModel::approveAlbumGenre,
            onScanMissingArtists = viewModel::scanMissingArtists,
            onApproveAlbumArtist = viewModel::approveAlbumArtist,
            onScanPendingTagWrites = viewModel::scanPendingTagWrites,
            onScanArtists = viewModel::scanDuplicateArtists,
            onUnifyArtist = viewModel::unifyArtistGroup,
            onSetRadioBulletinMode = viewModel::setRadioBulletinMode,
            onSetRadioBulletinDuration = viewModel::setRadioBulletinDuration,
            onSetRadioBulletinPreferLocalWriter = viewModel::setRadioBulletinPreferLocalWriter,
            onImportRadioVoicePackage = { voicePackageLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onClearRadioVoicePackage = viewModel::clearRadioVoicePackage,
            onSetRadioVoiceEnabled = viewModel::setRadioVoiceEnabled,
            onTestRadioVoicePackage = viewModel::testRadioVoicePackage,
            onTestRadioBulletin = viewModel::testRadioBulletin,
        )

        pendingDeleteSong?.let { song ->
            DeleteConfirmDialog(
                title = "Apagar musica",
                message = "\"${song.title}\" sera apagada do aparelho para sempre. Essa acao nao pode ser desfeita.",
                onConfirm = {
                    pendingDeleteSong = null
                    requestDelete(listOf(song))
                },
                onDismiss = { pendingDeleteSong = null },
            )
        }
        pendingDeleteAlbum?.let { album ->
            DeleteConfirmDialog(
                title = "Apagar album",
                message = "\"${album.title}\" e as ${album.songs.size} musicas dele serao apagadas do aparelho para sempre. Essa acao nao pode ser desfeita.",
                onConfirm = {
                    pendingDeleteAlbum = null
                    requestDelete(album.songs)
                },
                onDismiss = { pendingDeleteAlbum = null },
            )
        }
        pendingDeleteArtist?.let { artist ->
            DeleteConfirmDialog(
                title = "Apagar artista",
                message = "\"${artist.name}\" e todos os albuns e musicas dele (${artist.songs.size} faixas) serao apagados do aparelho para sempre. Essa acao nao pode ser desfeita.",
                onConfirm = {
                    pendingDeleteArtist = null
                    requestDelete(artist.songs)
                },
                onDismiss = { pendingDeleteArtist = null },
            )
        }

        if (showFullPlayer) {
            FullPlayer(
                player = player,
                isFavorite = viewModel.isCurrentSongFavorite(),
                onClose = { showFullPlayer = false },
                onToggleFavorite = viewModel::toggleCurrentSongFavorite,
                onToggle = viewModel::togglePlayPause,
                onNext = viewModel::skipNext,
                onPrevious = viewModel::skipPrevious,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::cycleRepeat,
                onSeek = viewModel::seekTo,
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    selectedTab: LibraryTab,
    titleOverride: String? = null,
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    showSearch: Boolean = true,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.pailer_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Pailer Player",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = titleOverride ?: selectedTab.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onSettings, enabled = !isLoading) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Configuracoes",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (showSearch) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Buscar musicas, artistas e albuns") },
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun SettingsDrawer(
    visible: Boolean,
    page: SettingsPage,
    metadata: MetadataUiState,
    radioBulletins: RadioBulletinUiState,
    radioVoice: RadioVoiceUiState,
    batteryProtected: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onClose: () -> Unit,
    onPageChange: (SettingsPage) -> Unit,
    onSync: () -> Unit,
    onScanMetadata: () -> Unit,
    onSuggestGenres: () -> Unit,
    onApproveGenre: (LocalAlbum, String) -> Unit,
    onScanMissingArtists: () -> Unit,
    onApproveAlbumArtist: (LocalAlbum, String) -> Unit,
    onScanPendingTagWrites: () -> Unit,
    onScanArtists: () -> Unit,
    onUnifyArtist: (DuplicateArtistGroup) -> Unit,
    onSetRadioBulletinMode: (RadioBulletinMode) -> Unit,
    onSetRadioBulletinDuration: (RadioBulletinDuration) -> Unit,
    onSetRadioBulletinPreferLocalWriter: (Boolean) -> Unit,
    onImportRadioVoicePackage: () -> Unit,
    onClearRadioVoicePackage: () -> Unit,
    onSetRadioVoiceEnabled: (Boolean) -> Unit,
    onTestRadioVoicePackage: () -> Unit,
    onTestRadioBulletin: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(onClick = onClose),
            )
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 320.dp, max = 390.dp),
                    color = Color(0xFF120D09),
                    tonalElevation = 8.dp,
                ) {
                    when (page) {
                        SettingsPage.Main -> SettingsMainPanel(
                            onClose = onClose,
                            onSync = onSync,
                            batteryProtected = batteryProtected,
                            onRequestBatteryExemption = onRequestBatteryExemption,
                            onOpenMetadata = {
                                onPageChange(SettingsPage.Metadata)
                                onScanMetadata()
                            },
                            onOpenAlbumArtists = {
                                onPageChange(SettingsPage.AlbumArtists)
                                onScanMissingArtists()
                            },
                            onOpenTagWriter = {
                                onPageChange(SettingsPage.TagWriter)
                                onScanPendingTagWrites()
                            },
                            onOpenArtists = {
                                onPageChange(SettingsPage.Artists)
                                onScanArtists()
                            },
                            onOpenRadioBulletins = {
                                onPageChange(SettingsPage.RadioBulletins)
                            },
                        )
                        SettingsPage.Metadata -> MetadataSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onScan = onScanMetadata,
                            onSuggest = onSuggestGenres,
                            onApproveGenre = onApproveGenre,
                        )
                        SettingsPage.AlbumArtists -> AlbumArtistCleanupSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onScan = onScanMissingArtists,
                            onApproveArtist = onApproveAlbumArtist,
                        )
                        SettingsPage.TagWriter -> TagWriterSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onScan = onScanPendingTagWrites,
                        )
                        SettingsPage.Artists -> ArtistCleanupSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onScan = onScanArtists,
                            onUnifyArtist = onUnifyArtist,
                        )
                        SettingsPage.RadioBulletins -> RadioBulletinSettingsPanel(
                            radioBulletins = radioBulletins,
                            radioVoice = radioVoice,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onSetMode = onSetRadioBulletinMode,
                            onSetDuration = onSetRadioBulletinDuration,
                            onSetPreferLocalWriter = onSetRadioBulletinPreferLocalWriter,
                            onImportVoicePackage = onImportRadioVoicePackage,
                            onClearVoicePackage = onClearRadioVoicePackage,
                            onSetVoiceEnabled = onSetRadioVoiceEnabled,
                            onTestVoicePackage = onTestRadioVoicePackage,
                            onTestRadioBulletin = onTestRadioBulletin,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMainPanel(
    onClose: () -> Unit,
    onSync: () -> Unit,
    batteryProtected: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onOpenMetadata: () -> Unit,
    onOpenAlbumArtists: () -> Unit,
    onOpenTagWriter: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenRadioBulletins: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Configuracoes",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingsActionRow(
            title = "Sincronizar biblioteca",
            subtitle = "Ler novas faixas salvas no celular",
            icon = Icons.Filled.Sync,
            onClick = onSync,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        if (!batteryProtected) {
            SettingsActionRow(
                title = "Manter radio viva em segundo plano",
                subtitle = "Retirar o app da otimizacao de bateria do Android",
                icon = Icons.Filled.BatteryAlert,
                onClick = onRequestBatteryExemption,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }
        SettingsActionRow(
            title = "Boletins da radio",
            subtitle = "Configurar manchetes, dialogo e redator local",
            icon = Icons.Filled.GraphicEq,
            onClick = onOpenRadioBulletins,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Metadados",
            subtitle = "Encontrar albuns sem genero e sugerir categorias",
            icon = Icons.Filled.Tune,
            onClick = onOpenMetadata,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Albuns sem artista",
            subtitle = "Corrigir artista desconhecido album por album",
            icon = Icons.Filled.Person,
            onClick = onOpenAlbumArtists,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Tags pendentes",
            subtitle = "Ver o que ainda falta sincronizar",
            icon = Icons.Filled.Check,
            onClick = onOpenTagWriter,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Artistas duplicados",
            subtitle = "Unificar nomes parecidos na biblioteca",
            icon = Icons.Filled.Person,
            onClick = onOpenArtists,
        )
    }
}

@Composable
private fun RadioBulletinSettingsPanel(
    radioBulletins: RadioBulletinUiState,
    radioVoice: RadioVoiceUiState,
    onBack: () -> Unit,
    onSetMode: (RadioBulletinMode) -> Unit,
    onSetDuration: (RadioBulletinDuration) -> Unit,
    onSetPreferLocalWriter: (Boolean) -> Unit,
    onImportVoicePackage: () -> Unit,
    onClearVoicePackage: () -> Unit,
    onSetVoiceEnabled: (Boolean) -> Unit,
    onTestVoicePackage: () -> Unit,
    onTestRadioBulletin: () -> Unit,
) {
    val settings = radioBulletins.settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Boletins da radio",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "A cada ${settings.songsBetweenBulletins} musicas",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "A radio prepara a noticia enquanto a programacao toca e entra com um bloco curto entre as faixas.",
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Modo", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        RadioBulletinChoiceRow(
            title = "Desativado",
            subtitle = "A radio toca sem noticias",
            selected = settings.mode == RadioBulletinMode.Off,
            onClick = { onSetMode(RadioBulletinMode.Off) },
        )
        RadioBulletinChoiceRow(
            title = "So manchetes",
            subtitle = "Entrada curta usando a voz atual do Android",
            selected = settings.mode == RadioBulletinMode.Headlines,
            onClick = { onSetMode(RadioBulletinMode.Headlines) },
        )
        RadioBulletinChoiceRow(
            title = "Conversa dos locutores",
            subtitle = "Roteiro em bate-bola, preparado para o redator local",
            selected = settings.mode == RadioBulletinMode.Dialogue,
            onClick = { onSetMode(RadioBulletinMode.Dialogue) },
        )

        Spacer(Modifier.height(18.dp))
        Text("Duracao", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            RadioBulletinDuration.entries.forEach { duration ->
                TextButton(
                    onClick = { onSetDuration(duration) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = duration.radioLabel(),
                        color = if (settings.duration == duration) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (settings.duration == duration) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Redator local", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        RadioBulletinChoiceRow(
            title = radioBulletins.localWriterName,
            subtitle = radioBulletins.localWriterDetail,
            selected = settings.preferLocalWriter,
            onClick = { onSetPreferLocalWriter(!settings.preferLocalWriter) },
        )
        Text(
            text = if (radioBulletins.localWriterInstalled) {
                "Quando o pacote estiver ativo, ele escreve o dialogo curto no aparelho antes do boletim tocar."
            } else {
                "Enquanto o pacote nao existir, o app usa um roteiro leve de seguranca e continua tocando normalmente."
            },
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))
        Text("Voz local", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xE51B120C),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (radioVoice.isInstalled) radioVoice.packageName else "Voz padrao do Android",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (radioVoice.isInstalled) {
                                "${radioVoice.engine.ifBlank { "Motor local" }} · ${radioVoice.femaleSpeaker.ifBlank { "Locutora" }} / ${radioVoice.maleSpeaker.ifBlank { "Locutor" }}"
                            } else {
                                radioVoice.detail
                            },
                            modifier = Modifier.padding(top = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (radioVoice.isInstalled) {
                        Switch(
                            checked = radioVoice.isEnabled,
                            onCheckedChange = onSetVoiceEnabled,
                        )
                    }
                }
                radioVoice.message?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onImportVoicePackage,
                        enabled = !radioVoice.isImporting && !radioVoice.isTesting && !radioVoice.isTestingBulletin,
                    ) {
                        if (radioVoice.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (radioVoice.isImporting) "Importando..." else "Importar pacote")
                    }
                    if (radioVoice.isInstalled) {
                        Button(
                            onClick = onTestVoicePackage,
                            enabled = !radioVoice.isTesting && !radioVoice.isTestingBulletin,
                        ) {
                            if (radioVoice.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (radioVoice.isTesting) "Testando..." else "Testar voz")
                        }
                    }
                }
                if (radioVoice.isInstalled) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onTestRadioBulletin,
                            enabled = !radioVoice.isTesting && !radioVoice.isTestingBulletin,
                        ) {
                            if (radioVoice.isTestingBulletin) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (radioVoice.isTestingBulletin) "Testando..." else "Testar boletim")
                        }
                        TextButton(onClick = onClearVoicePackage) {
                            Text("Remover", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text(
            text = "Por seguranca, pacote importado nao liga sozinho. Se algo falhar, deixe desligado e a radio usa a voz do Android.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RadioBulletinChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun RadioBulletinDuration.radioLabel(): String = when (this) {
    RadioBulletinDuration.Short -> "Curta"
    RadioBulletinDuration.Normal -> "Normal"
    RadioBulletinDuration.Long -> "Longa"
}

@Composable
private fun MetadataSettingsPanel(
    metadata: MetadataUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSuggest: () -> Unit,
    onApproveGenre: (LocalAlbum, String) -> Unit,
) {
    val currentAlbum = metadata.missingGenreAlbums.firstOrNull()
    val suggestion = currentAlbum?.let { metadata.suggestions[it.key] }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Metadados",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            metadata.message ?: "${metadata.missingGenreAlbums.size} albuns para revisar",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onScan,
            enabled = !metadata.isScanning && !metadata.isSuggesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Varrer albuns")
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            if (currentAlbum == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum album pendente.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                MetadataReviewCard(
                    album = currentAlbum,
                    remainingCount = metadata.missingGenreAlbums.size,
                    availableGenres = metadata.availableGenres,
                    suggestion = suggestion,
                    isSuggesting = metadata.isSuggesting,
                    onSuggest = onSuggest,
                    onApproveGenre = { genre -> onApproveGenre(currentAlbum, genre) },
                )
            }
        }
    }
}

@Composable
private fun AlbumArtistCleanupSettingsPanel(
    metadata: MetadataUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onApproveArtist: (LocalAlbum, String) -> Unit,
) {
    val currentAlbum = metadata.missingArtistAlbums.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Albuns sem artista",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            metadata.message ?: "${metadata.missingArtistAlbums.size} albuns encontrados",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onScan,
            enabled = !metadata.isScanning && !metadata.isSuggesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Varrer albuns")
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f)) {
            if (currentAlbum == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum album pendente.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                AlbumArtistReviewCard(
                    album = currentAlbum,
                    remainingCount = metadata.missingArtistAlbums.size,
                    availableArtists = metadata.availableArtists,
                    onApproveArtist = { artist -> onApproveArtist(currentAlbum, artist) },
                )
            }
        }
    }
}

@Composable
private fun TagWriterSettingsPanel(
    metadata: MetadataUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Gravar tags",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            metadata.message ?: "${metadata.pendingTagChanges.size} alteracoes pendentes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onScan,
            enabled = !metadata.isScanning && !metadata.isSuggesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Atualizar lista")
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x9922160F))
                        .padding(12.dp),
                ) {
                    Text(
                        "Revisao antes da escrita",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Esta tela mostra o que ainda esta pendente. Para evitar pedidos enormes de permissao, a gravacao agora acontece direto ao salvar edicoes no artista ou album.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (metadata.pendingTagChanges.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Nenhuma tag pendente.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(metadata.pendingTagChanges, key = { it.id }) { change ->
                    PendingTagChangeRow(change = change)
                }
            }
        }
    }
}

@Composable
private fun ArtistCleanupSettingsPanel(
    metadata: MetadataUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onUnifyArtist: (DuplicateArtistGroup) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Artistas duplicados",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            metadata.message ?: "${metadata.duplicateArtistGroups.size} grupos encontrados",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onScan,
            enabled = !metadata.isScanning && !metadata.isSuggesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Varrer artistas")
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(metadata.duplicateArtistGroups, key = { it.canonicalName + it.variants.joinToString() }) { group ->
                DuplicateArtistRow(group = group, onUnify = { onUnifyArtist(group) })
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DuplicateArtistRow(group: DuplicateArtistGroup, onUnify: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE51B120C))
            .padding(12.dp),
    ) {
        Text(
            group.canonicalName,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${group.variants.size} nomes, ${group.totalSongs} faixas",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            group.variants.joinToString("  /  "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onUnify, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Unificar")
        }
    }
}

@Composable
private fun PendingTagChangeRow(change: PendingTagChange) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE51B120C))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(change.artworkUri, Modifier.size(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                change.albumTitle,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${change.artistName} • ${change.songCount} faixas",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            change.fields.forEach { field ->
                Text(
                    field,
                    color = Color(0xFFFF7A1A),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlbumArtistReviewCard(
    album: LocalAlbum,
    remainingCount: Int,
    availableArtists: List<String>,
    onApproveArtist: (String) -> Unit,
) {
    var artistInput by remember(album.key) { mutableStateOf("") }
    val cleanInput = artistInput.trim()
    val suggestions = remember(cleanInput, availableArtists) {
        if (cleanInput.isBlank()) {
            availableArtists.take(6)
        } else {
            availableArtists
                .filter { it.contains(cleanInput, ignoreCase = true) }
                .take(6)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE51B120C))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkBox(
            album.artworkUri,
            Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
            iconModifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            album.title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$remainingCount pendentes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))
        TextField(
            value = artistInput,
            onValueChange = { artistInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Digite o artista") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suggestions.forEach { artist ->
                    TextButton(
                        onClick = { artistInput = artist },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            artist,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onApproveArtist(cleanInput) },
            enabled = cleanInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (suggestions.any { it.equals(cleanInput, ignoreCase = true) }) "Salvar artista" else "Salvar como novo")
        }
    }
}

@Composable
private fun MetadataReviewCard(
    album: LocalAlbum,
    remainingCount: Int,
    availableGenres: List<String>,
    suggestion: String?,
    isSuggesting: Boolean,
    onSuggest: () -> Unit,
    onApproveGenre: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE51B120C))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkBox(
            album.artworkUri,
            Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
            iconModifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            album.title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$remainingCount pendentes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSuggest,
            enabled = !isSuggesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSuggesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Buscar sugestao")
            }
        }
        if (!suggestion.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onApproveGenre(suggestion) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Usar $suggestion")
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 6.dp),
        ) {
            gridItems(availableGenres, key = { it }) { genre ->
                TextButton(
                    onClick = { onApproveGenre(genre) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        genre,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    songs: List<LocalSong>,
    continueSong: LocalSong?,
    continuePositionMs: Long,
    recentlyAddedAlbums: List<LocalAlbum>,
    listenAgain: List<LocalSong>,
    suggestedAlbums: List<LocalAlbum>,
    favoriteAlbums: List<LocalAlbum>,
    radios: List<LocalRadio>,
    player: PlayerUiState,
    onContinue: () -> Unit,
    onOpenAlbum: (LocalAlbum) -> Unit,
    onOpenRadio: (LocalRadio) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlay: (List<LocalSong>, Int, Boolean) -> Unit,
) {
    val homeRecentAlbums = remember(recentlyAddedAlbums) { recentlyAddedAlbums.take(8) }
    val homeListenAgain = remember(listenAgain) { listenAgain.take(8) }
    val homeSuggestedAlbums = remember(suggestedAlbums) { suggestedAlbums.take(6) }
    val homeFavoriteAlbums = remember(favoriteAlbums) { favoriteAlbums.take(6) }
    val homeRadios = remember(radios) { radios.take(5) }

    LazyColumn(contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (player.activeRadioName.isNotBlank() && player.hasMedia) {
            item {
                LiveNowRadioCard(
                    player = player,
                    onClick = onOpenPlayer,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
        }
        if (continueSong != null) {
            item {
                ContinueListeningCard(
                    song = continueSong,
                    positionMs = continuePositionMs,
                    onClick = onContinue,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
        }
        item {
            SectionTitle("Albuns adicionados recentemente", modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(homeRecentAlbums, key = { it.key }) { album ->
                    AlbumCoverCard(album = album, onClick = { onOpenAlbum(album) })
                }
            }
        }
        item {
            SectionTitle("Ouvir de novo", modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(homeListenAgain, key = { it.id }) { song ->
                    SongCoverCard(
                        song = song,
                        onClick = { onPlay(homeListenAgain, homeListenAgain.indexOf(song), false) },
                    )
                }
            }
        }
        item {
            SectionTitle("Albuns sugeridos para voce", modifier = Modifier.padding(horizontal = 18.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(homeSuggestedAlbums, key = { it.key }) { album ->
                    AlbumCoverCard(album = album, onClick = { onOpenAlbum(album) })
                }
            }
        }
        if (homeFavoriteAlbums.isNotEmpty()) {
            item {
                SectionTitle("Albuns curtidos", modifier = Modifier.padding(horizontal = 18.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(homeFavoriteAlbums, key = { it.key }) { album ->
                        AlbumCoverCard(album = album, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }
        item {
            SectionTitle("Atalhos", modifier = Modifier.padding(horizontal = 18.dp))
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionTile(
                    title = "Tocar tudo",
                    subtitle = "${songs.size} faixas",
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.weight(1f),
                    onClick = { onPlay(songs, 0, false) },
                )
                ActionTile(
                    title = "Misturar",
                    subtitle = "Fila aleatoria",
                    icon = Icons.Filled.Shuffle,
                    modifier = Modifier.weight(1f),
                    onClick = { onPlay(songs, 0, true) },
                )
            }
        }
        item {
            SectionTitle("Radios rapidas", modifier = Modifier.padding(horizontal = 18.dp))
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                homeRadios.forEach { radio ->
                    RadioRow(radio = radio, onClick = { onOpenRadio(radio) })
                }
            }
        }
        item { SectionTitle("Novas no aparelho", modifier = Modifier.padding(horizontal = 18.dp)) }
        items(songs.take(8), key = { it.id }) { song ->
            Box(Modifier.padding(horizontal = 10.dp)) {
                SongRow(song = song, onClick = { onPlay(songs, songs.indexOf(song), false) })
            }
        }
    }
}

@Composable
private fun ArtistsScreen(
    artists: List<LocalArtist>,
    onOpenArtist: (LocalArtist) -> Unit,
    onDeleteArtist: (LocalArtist) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        gridItems(artists, key = { it.name }) { artist ->
            ArtistGridCard(
                artist = artist,
                onClick = { onOpenArtist(artist) },
                onLongClick = { onDeleteArtist(artist) },
            )
        }
    }
}

@Composable
private fun GenreDetailScreen(
    genre: LocalRadio,
    artists: List<LocalArtist>,
    onOpenArtist: (LocalArtist) -> Unit,
    onShuffleGenre: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    genre.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${genre.songs.size} musicas, ${artists.size} artistas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onShuffleGenre, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Misturar categoria")
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        gridItems(artists, key = { it.name }) { artist ->
            ArtistGridCard(
                artist = artist,
                onClick = { onOpenArtist(artist) },
            )
        }
    }
}

@Composable
private fun ArtistDetailScreen(
    artist: LocalArtist,
    albums: List<LocalAlbum>,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAlbum: (LocalAlbum) -> Unit,
    onShuffleArtist: () -> Unit,
    availableGenres: List<String>,
    onSaveMetadata: (String, List<AlbumMetadataEdit>) -> Unit,
) {
    val artworkUri = artist.songs.firstOrNull { it.artworkUri != null }?.artworkUri
    var showEditor by rememberSaveable(artist.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showEditor = true }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar metadados",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Curtir artista",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtworkBox(
                        uri = artworkUri,
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .aspectRatio(1f),
                        iconModifier = Modifier.size(58.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        artist.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${albums.size} albuns, ${artist.songs.size} faixas",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onShuffleArtist, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Misturar artista")
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Albuns")
            }
            gridItems(albums, key = { it.key }) { album ->
                AlbumGridCard(album = album, onClick = { onOpenAlbum(album) })
            }
        }

        if (showEditor) {
            ArtistMetadataEditorOverlay(
                artist = artist,
                albums = albums,
                availableGenres = availableGenres,
                onCancel = { showEditor = false },
                onSave = { artistName, edits ->
                    onSaveMetadata(artistName, edits)
                    showEditor = false
                },
            )
        }
    }
}

private data class AlbumMetadataDraft(
    val albumId: Long,
    val originalTitle: String,
    val title: String,
    val genre: String,
)

@Composable
private fun ArtistMetadataEditorOverlay(
    artist: LocalArtist,
    albums: List<LocalAlbum>,
    availableGenres: List<String>,
    onCancel: () -> Unit,
    onSave: (String, List<AlbumMetadataEdit>) -> Unit,
) {
    var artistName by remember(artist.key) { mutableStateOf(artist.name) }
    var drafts by remember(albums) {
        mutableStateOf(
            albums.associate { album ->
                album.key to AlbumMetadataDraft(
                    albumId = album.id,
                    originalTitle = album.title,
                    title = album.title,
                    genre = album.genre,
                )
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f)),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(18.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            color = Color(0xFF140E0A),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 10.dp,
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Editar metadados",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
                item {
                    MetadataTextField(
                        value = artistName,
                        onValueChange = { artistName = it },
                        label = "Nome do artista",
                    )
                }
                item {
                    Text(
                        "Albuns",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(albums, key = { it.key }) { album ->
                    val draft = drafts[album.key] ?: AlbumMetadataDraft(album.id, album.title, album.title, album.genre)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE51B120C))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtworkBox(album.artworkUri, Modifier.size(54.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    draft.title.ifBlank { album.title },
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${album.songs.size} faixas",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        MetadataTextField(
                            value = draft.title,
                            onValueChange = { value -> drafts = drafts + (album.key to draft.copy(title = value)) },
                            label = "Nome do album",
                        )
                        Spacer(Modifier.height(8.dp))
                        MetadataTextField(
                            value = draft.genre,
                            onValueChange = { value -> drafts = drafts + (album.key to draft.copy(genre = value)) },
                            label = "Genero",
                            placeholder = availableGenres.take(4).joinToString(" / "),
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                onSave(
                                    artistName,
                                    drafts.values.map {
                                        AlbumMetadataEdit(
                                            albumId = it.albumId,
                                            originalTitle = it.originalTitle,
                                            title = it.title,
                                            genre = it.genre,
                                        )
                                    },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = if (placeholder.isBlank()) null else {
            { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF24170F),
            unfocusedContainerColor = Color(0xFF24170F),
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun AlbumsScreen(
    albums: List<LocalAlbum>,
    onOpenAlbum: (LocalAlbum) -> Unit,
    onDeleteAlbum: (LocalAlbum) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        gridItems(albums, key = { it.key }) { album ->
            AlbumGridCard(
                album = album,
                onClick = { onOpenAlbum(album) },
                onLongClick = { onDeleteAlbum(album) },
            )
        }
    }
}

@Composable
private fun AlbumDetailScreen(
    album: LocalAlbum,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onPlaySong: (Int) -> Unit,
    isSongFavorite: (LocalSong) -> Boolean,
    onToggleSongFavorite: (LocalSong) -> Unit,
    availableGenres: List<String>,
    onSaveMetadata: (String, String, String) -> Unit,
) {
    var showEditor by rememberSaveable(album.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showEditor = true }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar metadados",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Curtir album",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtworkBox(
                        uri = album.artworkUri,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f),
                        iconModifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        album.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        album.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${album.songs.size} faixas",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlayAlbum,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tocar")
                    }
                    Button(
                        onClick = onShuffleAlbum,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Misturar")
                    }
                }
            }
            items(album.songs, key = { it.id }) { song ->
                val index = album.songs.indexOf(song)
                AlbumTrackRow(
                    trackNumber = index + 1,
                    song = song,
                    isFavorite = isSongFavorite(song),
                    onClick = { onPlaySong(index) },
                    onToggleFavorite = { onToggleSongFavorite(song) },
                )
            }
        }

        if (showEditor) {
            AlbumMetadataEditorOverlay(
                album = album,
                availableGenres = availableGenres,
                onCancel = { showEditor = false },
                onSave = { title, artistName, genre ->
                    onSaveMetadata(title, artistName, genre)
                    showEditor = false
                },
            )
        }
    }
}

@Composable
private fun AlbumMetadataEditorOverlay(
    album: LocalAlbum,
    availableGenres: List<String>,
    onCancel: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var title by remember(album.key) { mutableStateOf(album.title) }
    var artist by remember(album.key) { mutableStateOf(album.artist) }
    var genre by remember(album.key) { mutableStateOf(album.genre) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f)),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(18.dp)
                .fillMaxWidth(),
            color = Color(0xFF140E0A),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Editar album",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
                MetadataTextField(value = title, onValueChange = { title = it }, label = "Nome do album")
                MetadataTextField(value = artist, onValueChange = { artist = it }, label = "Nome do artista")
                MetadataTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = "Genero",
                    placeholder = availableGenres.take(4).joinToString(" / "),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = { onSave(title, artist, genre) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    trackNumber: Int,
    song: LocalSong,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            trackNumber.toString(),
            modifier = Modifier.width(34.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Normal,
            )
            Text(
                formatDuration(song.durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Curtir faixa",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SongsScreen(
    songs: List<LocalSong>,
    isSongFavorite: (LocalSong) -> Boolean,
    onToggleSongFavorite: (LocalSong) -> Unit,
    onPlay: (Int) -> Unit,
    onDeleteSong: (LocalSong) -> Unit = {},
) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isFavorite = isSongFavorite(song),
                onClick = { onPlay(songs.indexOf(song)) },
                onLongClick = { onDeleteSong(song) },
                onToggleFavorite = { onToggleSongFavorite(song) },
            )
        }
    }
}

@Composable
private fun PlaylistsScreen(
    radios: List<LocalRadio>,
    player: PlayerUiState,
    onOpenRadio: (LocalRadio) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (player.activeRadioName.isNotBlank() && player.hasMedia) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LiveNowRadioCard(player = player, onClick = onOpenPlayer)
            }
        }
        gridItems(radios, key = { it.name }) { radio ->
            RadioGridCard(radio = radio, onClick = { onOpenRadio(radio) })
        }
    }
}

@Composable
private fun RadioDetailScreen(
    radio: LocalRadio,
    player: PlayerUiState,
    sessionSongs: List<LocalSong>,
    isGenerating: Boolean,
    onBack: () -> Unit,
    onEnterRadio: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        if (player.activeRadioName == radio.name && player.hasMedia) {
            item {
                LiveNowRadioCard(player = player, onClick = onOpenPlayer)
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xE522160F)),
            ) {
                Column(Modifier.padding(18.dp)) {
                    RadioCoverMosaic(
                        songs = radio.coverSongs,
                        modifier = Modifier.size(104.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        radio.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        radio.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    LiveRadioBadge(isActive = sessionSongs.isNotEmpty())
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onEnterRadio,
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isGenerating) "Criando..." else "Entrar")
                        }
                    }
                }
            }
        }
        if (sessionSongs.isNotEmpty()) {
            item {
                SectionTitle("Sequencia ao vivo")
            }
            items(sessionSongs, key = { it.id }) { song ->
                val index = sessionSongs.indexOf(song)
                RadioTrackRow(
                    index = index + 1,
                    song = song,
                    onClick = { onPlaySong(index) },
                )
            }
        } else {
            item {
                Text(
                    "Toque em Entrar para criar uma sequencia nova agora.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveRadioBadge(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "radioLivePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radioLiveAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(if (isActive) 10.dp else 8.dp)
                .alpha(if (isActive) pulse else 0.55f)
                .clip(CircleShape)
                .background(Color(0xFFFF6A1A)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isActive) "ao vivo, sequencia criada agora" else "pronta para criar ao vivo",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun RadioTrackRow(index: Int, song: LocalSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            modifier = Modifier.width(30.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ArtworkBox(song.artworkUri, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Normal,
            )
            Text(
                song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LiveNowRadioCard(player: PlayerUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE522160F)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveRadioBadge(isActive = true)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatDuration(player.positionMs)} / ${formatDuration(player.durationMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkBox(player.artworkUri, Modifier.size(92.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        player.playbackSource.ifBlank { "Rádio" },
                        color = Color(0xFFFF7A1A),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        player.title.ifBlank { "Tocando agora" },
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        player.currentNewsHeadline.ifBlank { player.artist },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = {
                    if (player.durationMs > 0) {
                        (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = Color(0xFFFF7A1A),
                trackColor = Color(0x33FFFFFF),
            )
        }
    }
}

@Composable
private fun SongRow(
    song: LocalSong,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
) {
    RowItem(
        title = song.title,
        subtitle = "${song.artist} • ${formatDuration(song.durationMs)}",
        icon = { ArtworkBox(song.artworkUri, Modifier.size(52.dp)) },
        onClick = onClick,
        onLongClick = onLongClick,
        trailing = onToggleFavorite?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Curtir faixa",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun AlbumRow(album: LocalAlbum, onClick: () -> Unit) {
    RowItem(
        title = album.title,
        subtitle = "${album.artist} • ${album.songs.size} musicas",
        icon = { ArtworkBox(album.artworkUri, Modifier.size(58.dp)) },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistGridCard(artist: LocalArtist, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val artworkUri = artist.songs.firstOrNull { it.artworkUri != null }?.artworkUri
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ArtworkBox(
            uri = artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            iconModifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            artist.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${artist.albumCount} albuns",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridCard(album: LocalAlbum, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ArtworkBox(
            uri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            iconModifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            album.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            album.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Apagar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun RadioRow(radio: LocalRadio, onClick: () -> Unit) {
    RowItem(
        title = radio.name,
        subtitle = radio.description,
        icon = {
            RadioCoverMosaic(
                songs = radio.coverSongs,
                modifier = Modifier.size(62.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun RadioCoverMosaic(songs: List<LocalSong>, modifier: Modifier = Modifier) {
    val covers = songs.distinctBy { it.albumId }.take(4)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(2) { rowIndex ->
                Row(Modifier.weight(1f)) {
                    repeat(2) { columnIndex ->
                        val song = covers.getOrNull(rowIndex * 2 + columnIndex)
                        if (song != null) {
                            ArtworkBox(
                                uri = song.artworkUri,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                iconModifier = Modifier.size(14.dp),
                            )
                        } else {
                            ArtworkPlaceholder(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                iconModifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        Icon(
            Icons.Filled.PlaylistPlay,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp),
        )
    }
}

@Composable
private fun RadioGridCard(radio: LocalRadio, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        RadioCoverMosaic(
            songs = radio.coverSongs,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            radio.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            radio.description,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ContinueListeningCard(
    song: LocalSong,
    positionMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE522160F)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ArtworkBox(
                    uri = song.artworkUri,
                    modifier = Modifier.size(154.dp),
                    iconModifier = Modifier.size(46.dp),
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC080604)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Continuar ouvindo",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    song.album,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    song.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Retomar em ${formatDuration(positionMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SongCoverCard(song: LocalSong, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkBox(
            uri = song.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            iconModifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            song.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AlbumCoverCard(album: LocalAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(158.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkBox(
            uri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            iconModifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(9.dp))
        Text(
            album.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            album.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MiniPlayer(
    player: PlayerUiState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(color = Color(0xF015100C)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBox(player.artworkUri, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (player.playbackSource.isNotBlank()) {
                    Text(
                        player.playbackSource,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFFFF7A1A),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    player.title.ifBlank { "Tocando agora" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    player.currentNewsHeadline.ifBlank { player.artist },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (player.activeRadioName.isBlank()) {
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Proxima",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun FullPlayer(
    player: PlayerUiState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val isRadio = player.activeRadioName.isNotBlank()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(appBackgroundBrush())
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveSourceLabel(
                    text = player.playbackSource.ifBlank { "${player.queueIndex}/${player.queueSize}" },
                    isLive = isRadio,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = player.songId != null,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Curtir faixa",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Fechar player",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            ArtworkBox(
                uri = player.artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                iconModifier = Modifier.size(84.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = player.title.ifBlank { "Sem faixa selecionada" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = player.artist,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (player.currentNewsHeadline.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x9922160F)),
                ) {
                    Text(
                        text = "Notícia rápida: ${player.currentNewsHeadline}",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (isRadio) {
                LinearProgressIndicator(
                    progress = {
                        if (player.durationMs > 0) {
                            (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = Color(0xFFFF7A1A),
                    trackColor = Color(0x33FFFFFF),
                )
            } else {
                Slider(
                    value = player.positionMs.toFloat().coerceIn(0f, player.durationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..player.durationMs.coerceAtLeast(1L).toFloat(),
                )
            }
            Row {
                Text(formatDuration(player.positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(player.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(22.dp))
            if (isRadio) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Pausar radio",
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                RadioUpcomingQueue(player.upcomingTracks)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (player.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(38.dp))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Proxima", modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = onRepeat) {
                        Icon(
                            imageVector = if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Filled.RepeatOne
                            } else {
                                Icons.Filled.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (player.repeatMode != Player.REPEAT_MODE_OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSourceLabel(text: String, isLive: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isLive) {
            val transition = rememberInfiniteTransition(label = "playerLivePulse")
            val pulse by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 820),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "playerLiveAlpha",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(pulse)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6A1A)),
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RadioUpcomingQueue(upcomingTracks: List<String>) {
    if (upcomingTracks.isEmpty()) return
    Spacer(Modifier.height(22.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x6622160F))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "A seguir",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        upcomingTracks.take(5).forEachIndexed { index, track ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    modifier = Modifier.width(28.dp),
                    color = Color(0xFFFF7A1A),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    track,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtworkBox(
    uri: Uri?,
    modifier: Modifier,
    iconModifier: Modifier = Modifier.size(26.dp),
) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        image = withContext(Dispatchers.IO) {
            uri?.let { loadScaledArtwork(context, it) }
        }
    }

    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        ArtworkPlaceholder(modifier = modifier, iconModifier = iconModifier)
    }
}

private fun loadScaledArtwork(context: android.content.Context, uri: Uri): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val cacheKey = uri.toString()
        artworkMemoryCache.get(cacheKey)?.let { return@runCatching it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        val sampleSize = calculateArtworkSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
        if (bitmap != null) artworkMemoryCache.put(cacheKey, bitmap)
        bitmap
    }.getOrNull()

private val artworkMemoryCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(96)

private fun calculateArtworkSampleSize(width: Int, height: Int): Int {
    if (width <= ARTWORK_DECODE_TARGET_PX && height <= ARTWORK_DECODE_TARGET_PX) return 1
    var sampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while ((halfWidth / sampleSize) >= ARTWORK_DECODE_TARGET_PX && (halfHeight / sampleSize) >= ARTWORK_DECODE_TARGET_PX) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private const val ARTWORK_DECODE_TARGET_PX = 320

@Composable
private fun ArtworkPlaceholder(
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp = 8.dp,
    iconModifier: Modifier = Modifier.size(26.dp),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFF7A00),
                        Color(0xFF2A1B10),
                        Color(0xFFFFB25A),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = iconModifier, tint = Color.White)
    }
}

@Composable
private fun ArtistAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun LoadingLibrary() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text("Lendo sua biblioteca local...", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun ErrorLibrary(message: String, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tentar de novo")
            }
        }
    }
}

@Composable
private fun EmptyLibrary(hasQuery: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasQuery) "Nada encontrado" else "Nenhuma musica local encontrada",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (hasQuery) {
                    "Tenta buscar por outro artista, album ou faixa."
                } else {
                    "Coloque MP3, FLAC, OGG ou M4A no aparelho e atualize a biblioteca."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LibraryTab.icon() = when (this) {
    LibraryTab.Home -> Icons.Filled.Home
    LibraryTab.Artists -> Icons.Filled.Person
    LibraryTab.Albums -> Icons.Filled.Album
    LibraryTab.Songs -> Icons.Filled.QueueMusic
    LibraryTab.Genres -> Icons.Filled.Category
    LibraryTab.Playlists -> Icons.Filled.PlaylistPlay
}

@Composable
private fun appBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2A1204),
        Color(0xFF120B06),
        MaterialTheme.colorScheme.background,
        Color(0xFF080604),
    )
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
