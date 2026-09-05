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
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import kotlin.math.abs
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import com.pailer.localtune.data.ArtworkCandidate
import com.pailer.localtune.data.PendingTagChange
import com.pailer.localtune.data.capitalizeGenreTag
import com.pailer.localtune.data.joinGenreTags
import com.pailer.localtune.data.splitGenreTags
import com.pailer.localtune.player.LocalTuneViewModel
import com.pailer.localtune.player.AlbumArtworkUiState
import com.pailer.localtune.player.MetadataUiState
import com.pailer.localtune.player.PlayerUiState
import com.pailer.localtune.player.RadioBulletinBufferUiState
import com.pailer.localtune.player.RadioBulletinUiState
import com.pailer.localtune.player.RadioVoiceUiState
import com.pailer.localtune.ui.theme.LocalTuneTheme
import com.pailer.localtune.ui.theme.PailerCharcoal
import com.pailer.localtune.ui.theme.PailerGunmetal
import com.pailer.localtune.ui.theme.PailerRed
import com.pailer.localtune.ui.theme.PailerSilver
import com.pailer.localtune.ui.theme.PailerSurface
import com.pailer.localtune.ui.theme.PailerSurfaceHigh
import com.pailer.localtune.ui.theme.PailerSurfaceHighest
import com.pailer.localtune.util.DayPeriod
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

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
    AlbumArtwork,
}

// Fling mais suave/macio pra rolagem do app inteiro (pedido do usuario 04/09/2026) - o default
// do Compose (ScrollableDefaults.flingBehavior(), spline-based) desacelera rapido e para meio
// seco. exponentialDecay com frictionMultiplier bem abaixo do padrao real do sistema (~4-5 numa
// tela normal) deixa a lista "flutuar" mais depois do gesto, sem perder o controle (nao e
// fisica livre, so um atrito menor). Implementacao manual de FlingBehavior (em vez de reusar
// DefaultFlingBehavior, que e `internal` no compose-foundation desta versao - nao compila fora
// do modulo deles) copiando o mesmo padrao documentado pelo Google pra fling customizado.
// Usado em todo LazyColumn/LazyVerticalGrid/verticalScroll do arquivo pra nao ficar
// inconsistente (uma tela macia, outra seca).
@Composable
private fun rememberSoftFlingBehavior(): FlingBehavior {
    val flingDecay = exponentialDecay<Float>(frictionMultiplier = 1.4f)
    return remember(flingDecay) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val animationState = AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
                var lastValue = 0f
                animationState.animateDecay(flingDecay) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    if (abs(delta - consumed) > 0.5f) cancelAnimation()
                }
                return animationState.velocity
            }
        }
    }
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
                text = "Pailer FM",
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
    // Estados de scroll hoistados aqui (fora das telas de lista/grade) pra sobreviver a
    // navegacao entre abas e telas de detalhe - sem isso, cada `when` troca de branch destroi
    // e recria o LazyVerticalGrid/LazyColumn da tela anterior, voltando pro topo sempre que o
    // usuario aperta "voltar". As telas de detalhe (artista/album/genero/radio) usam a chave
    // da entidade aberta pra resetar o scroll quando o usuario abre uma entidade DIFERENTE,
    // mas preservar quando volta pra mesma (ex.: abriu um album de dentro do artista e voltou).
    val artistsScrollState = rememberScrollState()
    val albumsScrollState = rememberScrollState()
    val songsListState = rememberLazyGridState()
    val genresListState = rememberLazyGridState()
    val radiosListState = rememberLazyGridState()
    val artistDetailListState = remember(selectedArtist?.key) { LazyGridState() }
    val albumDetailListState = remember(selectedAlbum?.key) { LazyListState() }
    val genreDetailListState = remember(selectedGenre?.name) { LazyGridState() }
    val radioDetailListState = remember(selectedRadio?.name) { LazyListState() }
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
    var pendingArtworkAlbum by remember { mutableStateOf<LocalAlbum?>(null) }
    val artworkWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val album = pendingArtworkAlbum
        pendingArtworkAlbum = null
        if (result.resultCode == Activity.RESULT_OK && album != null) {
            viewModel.applyPendingArtwork(album)
        } else {
            viewModel.cancelArtworkApply()
        }
    }
    val requestApplyArtwork = { album: LocalAlbum ->
        pendingArtworkAlbum = album
        val request = viewModel.createArtworkApplyRequest(album)
        if (request != null) {
            artworkWriteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            pendingArtworkAlbum = null
            viewModel.applyPendingArtwork(album)
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
    val writerPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importRadioWriterPackage)
    }
    val library = viewModel.libraryState.value
    val content = viewModel.libraryContentState.value
    val player = viewModel.playerState.value
    val metadata = viewModel.metadataState.value
    val albumArtwork = viewModel.albumArtworkState.value
    LaunchedEffect(albumArtwork.appliedVersion) {
        // A Uri de albumart nao muda quando a capa embutida no arquivo muda - sem isso o app
        // continuaria mostrando o bitmap antigo (ou o placeholder) ate reiniciar.
        if (albumArtwork.appliedVersion > 0) artworkMemoryCache.evictAll()
    }
    val radioBulletins = viewModel.radioBulletinState.value
    val radioBulletinBuffer = viewModel.radioBulletinBufferState.value
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
            selectedRadio != null -> selectedRadio = null
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
                        containerColor = PailerSurface.copy(alpha = 0.94f),
                    ) {
                        LibraryTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedAlbum = null
                                    selectedArtist = null
                                    // radioSession NAO e limpa aqui de proposito - trocar de
                                    // aba so fecha a tela de detalhe, a radio pode continuar
                                    // tocando em segundo plano e o usuario pode voltar pra ela
                                    // clicando no card "ao vivo" (ver openActiveRadio).
                                    selectedRadio = null
                                    selectedGenre = null
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
                    query = library.query,
                    isLoading = library.isLoading,
                    onQueryChange = viewModel::setQuery,
                    onSettings = {
                        settingsPage = SettingsPage.Main
                        showSettings = true
                    },
                )

                val openedAlbum = selectedAlbum
                val openedArtist = selectedArtist
                val openedRadio = selectedRadio
                val openedGenre = selectedGenre
                // Clique no card "ao vivo" (gif) fora da tela da radio - volta pra tela padrao
                // da radio (RadioDetailScreen) em vez do player cheio, e sem mexer em
                // radioSession (a radio ja esta tocando, a fila ao vivo continua valida).
                val openActiveRadio: () -> Unit = {
                    val active = radios.firstOrNull { it.name == player.activeRadioName }
                    if (active != null) selectedRadio = active
                }
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
                        onCreateRadio = { viewModel.createRadioFromAlbum(openedAlbum) },
                        albumArtwork = albumArtwork,
                        onOpenArtworkSearch = { viewModel.openArtworkSearch(openedAlbum) },
                        onCloseArtworkSearch = viewModel::closeArtworkSearch,
                        onSelectArtworkCandidate = viewModel::selectArtworkCandidate,
                        onApplyArtwork = requestApplyArtwork,
                        currentlyPlayingSongId = player.songId,
                        listState = albumDetailListState,
                    )
                    openedArtist != null -> ArtistDetailScreen(
                        artist = openedArtist,
                        albums = openedArtistAlbums,
                        isFavorite = viewModel.isArtistFavorite(openedArtist),
                        onBack = { selectedArtist = null },
                        onToggleFavorite = { viewModel.toggleArtistFavorite(openedArtist) },
                        onOpenAlbum = { selectedAlbum = it },
                        onPlayArtist = { viewModel.playSongs(openedArtist.songs, source = "Artista ${openedArtist.name}") },
                        onShuffleArtist = { viewModel.playSongs(openedArtist.songs, shuffle = true, source = "Mix do artista ${openedArtist.name}") },
                        availableGenres = availableGenres,
                        onSaveMetadata = { artistName, edits ->
                            selectedArtist = viewModel.saveArtistMetadataEdits(openedArtist, artistName, edits)
                            requestRecentMetadataEditWrite()
                        },
                        onCreateRadio = { viewModel.createRadioFromArtist(openedArtist) },
                        listState = artistDetailListState,
                    )
                    openedRadio != null -> {
                    // Fontes extras (adicionadas via "+") da radio aberta, resolvidas contra as
                    // listas artists/albums ja carregadas - recalculado a cada recomposicao (nao
                    // memoizado), entao some/aparece sozinho ao adicionar/remover fonte.
                    val openedRadioExtraSourceIds = viewModel.radioExtraSourceIds(openedRadio)
                    val openedRadioExtraArtists = artists.filter { "artist:${it.key}" in openedRadioExtraSourceIds }
                    val openedRadioExtraAlbums = albums.filter { "album:${it.id}" in openedRadioExtraSourceIds }
                    RadioDetailScreen(
                        radio = openedRadio,
                        player = player,
                        sessionSongs = radioSession,
                        songsBetweenBulletins = radioBulletins.settings.songsBetweenBulletins,
                        isGenerating = isGeneratingRadio,
                        onBack = {
                            // So chamado pelo botao "Sair da radio" agora (a tela nao tem mais
                            // seta de voltar - o back do sistema fecha via BackHandler, que so
                            // fecha a tela e deixa a radio tocando). Esse botao e uma saida de
                            // verdade: para a reproducao e esvazia a fila.
                            viewModel.stopRadio()
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
                        onDeleteRadio = {
                            viewModel.deleteRadio(openedRadio)
                            radioSession = emptyList()
                            selectedRadio = null
                            isGeneratingRadio = false
                        },
                        artists = artists,
                        albums = albums,
                        // Chamada direta na thread principal, sem Dispatchers.Default - igual
                        // onSaveMetadata/onCreateRadio logo acima. addArtistToRadio/addAlbumToRadio
                        // chamam showToast() internamente, que exige a main thread (Toast.makeText
                        // sem Looper.prepare() em background derruba o app - visto em teste real).
                        onAddArtist = { artist ->
                            viewModel.addArtistToRadio(openedRadio, artist)?.let { selectedRadio = it }
                        },
                        onAddAlbum = { album ->
                            viewModel.addAlbumToRadio(openedRadio, album)?.let { selectedRadio = it }
                        },
                        extraArtists = openedRadioExtraArtists,
                        extraAlbums = openedRadioExtraAlbums,
                        onRemoveArtist = { artist ->
                            viewModel.removeArtistFromRadio(openedRadio, artist)?.let { selectedRadio = it }
                        },
                        onRemoveAlbum = { album ->
                            viewModel.removeAlbumFromRadio(openedRadio, album)?.let { selectedRadio = it }
                        },
                        onRenameRadio = { newName ->
                            viewModel.renameRadio(openedRadio, newName)?.let { selectedRadio = it }
                        },
                        listState = radioDetailListState,
                    )
                    }
                    openedGenre != null -> GenreDetailScreen(
                        genre = openedGenre,
                        artists = openedGenreArtists,
                        onOpenArtist = { selectedArtist = it },
                        onPlayGenre = { viewModel.playSongs(openedGenre.songs, source = "Categoria ${openedGenre.name}") },
                        onShuffleGenre = { viewModel.playSongs(openedGenre.songs, shuffle = true, source = "Mix da categoria ${openedGenre.name}") },
                        onCreateRadio = { viewModel.createRadioFromGenre(openedGenre) },
                        listState = genreDetailListState,
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
                        onOpenPlayer = openActiveRadio,
                        onPlay = { list, index, shuffle -> viewModel.playSongs(list, index, shuffle, source = if (shuffle) "Misturar tudo" else "Biblioteca") },
                    )
                    selectedTab == LibraryTab.Artists -> ArtistsScreen(
                        artists = artists,
                        onOpenArtist = { selectedArtist = it },
                        onDeleteArtist = { pendingDeleteArtist = it },
                        favoriteArtistKeys = library.favoriteArtistKeys,
                        scrollState = artistsScrollState,
                    )
                    selectedTab == LibraryTab.Albums -> AlbumsScreen(
                        albums = albums,
                        onOpenAlbum = { selectedAlbum = it },
                        onDeleteAlbum = { pendingDeleteAlbum = it },
                        favoriteAlbumKeys = library.favoriteAlbumKeys,
                        scrollState = albumsScrollState,
                    )
                    selectedTab == LibraryTab.Songs -> SongsScreen(
                        songs = songs,
                        isSongFavorite = viewModel::isSongFavorite,
                        onToggleSongFavorite = viewModel::toggleSongFavorite,
                        onPlay = { index -> viewModel.playSongs(songs, index, source = "Músicas") },
                        onDeleteSong = { pendingDeleteSong = it },
                        listState = songsListState,
                    )
                    selectedTab == LibraryTab.Genres -> PlaylistsScreen(
                        radios = genreRadios,
                        player = player,
                        onOpenRadio = {
                            radioSession = emptyList()
                            selectedGenre = it
                        },
                        onOpenPlayer = openActiveRadio,
                        showGifBanner = false,
                        listState = genresListState,
                    )
                    selectedTab == LibraryTab.Playlists -> PlaylistsScreen(
                        radios = radios,
                        player = player,
                        onOpenRadio = {
                            radioSession = emptyList()
                            selectedRadio = it
                        },
                        onOpenPlayer = openActiveRadio,
                        listState = radiosListState,
                    )
                }
            }
        }

        val activityContext = LocalContext.current
        SettingsDrawer(
            visible = showSettings,
            page = settingsPage,
            metadata = metadata,
            albumArtwork = albumArtwork,
            radioBulletins = radioBulletins,
            radioBulletinBuffer = radioBulletinBuffer,
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
            onScanAlbumArtwork = viewModel::scanAlbumsWithoutArtwork,
            onOpenArtworkSearch = viewModel::openArtworkSearch,
            onCloseArtworkSearch = viewModel::closeArtworkSearch,
            onSelectArtworkCandidate = viewModel::selectArtworkCandidate,
            onApplyArtwork = requestApplyArtwork,
            onSetRadioBulletinPreferLocalWriter = viewModel::setRadioBulletinPreferLocalWriter,
            onImportRadioWriterPackage = { writerPackageLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onClearRadioWriterPackage = viewModel::clearRadioWriterPackage,
            onSaveGeminiApiKey = viewModel::saveGeminiApiKey,
            onClearGeminiApiKey = viewModel::clearGeminiApiKey,
            onImportRadioVoicePackage = { voicePackageLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onClearRadioVoicePackage = viewModel::clearRadioVoicePackage,
            onSetRadioVoiceEnabled = viewModel::setRadioVoiceEnabled,
            onTestRadioVoicePackage = viewModel::testRadioVoicePackage,
            onReplayTestAudio = viewModel::replayLastTestAudio,
            onTestAndroidVoice = viewModel::testAndroidVoiceBulletin,
            onPauseBulletinPreparation = viewModel::pauseBulletinPreparation,
            onResumeBulletinPreparation = viewModel::resumeBulletinPreparation,
            onResetBulletinBuffer = viewModel::resetBulletinBuffer,
            onPlayReadyBulletin = viewModel::playReadyBufferedBulletin,
            hasHiddenRadios = viewModel.hasHiddenRadios(),
            onRestoreHiddenRadios = viewModel::restoreHiddenRadios,
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
            val playingAlbum = remember(player.songId, songs, albums) {
                songs.firstOrNull { it.id == player.songId }
                    ?.let { song -> albums.firstOrNull { it.id == song.albumId } }
            }
            val playingArtist = remember(player.artist, artists) {
                artists.firstOrNull { it.key == player.artist.trim().lowercase() }
            }
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
                albumSongs = playingAlbum?.songs.orEmpty(),
                onOpenAlbum = playingAlbum?.let { album ->
                    { showFullPlayer = false; selectedAlbum = album }
                },
                onOpenArtist = playingArtist?.let { artist ->
                    { showFullPlayer = false; selectedArtist = artist }
                },
                isAlbumSongFavorite = viewModel::isSongFavorite,
                onToggleAlbumSongFavorite = viewModel::toggleSongFavorite,
                onPlayAlbumSong = { song ->
                    playingAlbum?.let { album ->
                        viewModel.playSongs(album.songs, album.songs.indexOf(song), source = "Álbum ${album.title}")
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    showSearch: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = FontFamily.Cursive)) { append("Pailer ") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("FM") }
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        if (showSearch) {
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Buscar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onSettings,
            enabled = !isLoading,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Configuracoes",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsDrawer(
    visible: Boolean,
    page: SettingsPage,
    metadata: MetadataUiState,
    albumArtwork: AlbumArtworkUiState,
    radioBulletins: RadioBulletinUiState,
    radioBulletinBuffer: RadioBulletinBufferUiState,
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
    onScanAlbumArtwork: () -> Unit,
    onOpenArtworkSearch: (LocalAlbum) -> Unit,
    onCloseArtworkSearch: () -> Unit,
    onSelectArtworkCandidate: (ArtworkCandidate) -> Unit,
    onApplyArtwork: (LocalAlbum) -> Unit,
    onSetRadioBulletinPreferLocalWriter: (Boolean) -> Unit,
    onImportRadioWriterPackage: () -> Unit,
    onClearRadioWriterPackage: () -> Unit,
    onSaveGeminiApiKey: (String) -> Unit,
    onClearGeminiApiKey: () -> Unit,
    onImportRadioVoicePackage: () -> Unit,
    onClearRadioVoicePackage: () -> Unit,
    onSetRadioVoiceEnabled: (Boolean) -> Unit,
    onTestRadioVoicePackage: () -> Unit,
    onReplayTestAudio: () -> Unit,
    onTestAndroidVoice: () -> Unit,
    onPauseBulletinPreparation: () -> Unit,
    onResumeBulletinPreparation: () -> Unit,
    onResetBulletinBuffer: () -> Unit,
    onPlayReadyBulletin: (Int) -> Unit,
    hasHiddenRadios: Boolean = false,
    onRestoreHiddenRadios: () -> Unit = {},
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
                    color = PailerSurfaceHigh,
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
                            onOpenAlbumArtwork = {
                                onPageChange(SettingsPage.AlbumArtwork)
                                onScanAlbumArtwork()
                            },
                            hasHiddenRadios = hasHiddenRadios,
                            onRestoreHiddenRadios = onRestoreHiddenRadios,
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
                            bulletinBuffer = radioBulletinBuffer,
                            radioVoice = radioVoice,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onSetPreferLocalWriter = onSetRadioBulletinPreferLocalWriter,
                            onImportWriterPackage = onImportRadioWriterPackage,
                            onClearWriterPackage = onClearRadioWriterPackage,
                            onSaveGeminiApiKey = onSaveGeminiApiKey,
                            onClearGeminiApiKey = onClearGeminiApiKey,
                            onImportVoicePackage = onImportRadioVoicePackage,
                            onClearVoicePackage = onClearRadioVoicePackage,
                            onSetVoiceEnabled = onSetRadioVoiceEnabled,
                            onTestVoicePackage = onTestRadioVoicePackage,
                            onReplayTestAudio = onReplayTestAudio,
                            onTestAndroidVoice = onTestAndroidVoice,
                            onPauseBulletinPreparation = onPauseBulletinPreparation,
                            onResumeBulletinPreparation = onResumeBulletinPreparation,
                            onResetBulletinBuffer = onResetBulletinBuffer,
                            onPlayReadyBulletin = onPlayReadyBulletin,
                        )
                        SettingsPage.AlbumArtwork -> AlbumArtworkSettingsPanel(
                            state = albumArtwork,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onScan = onScanAlbumArtwork,
                            onOpenSearch = onOpenArtworkSearch,
                            onCloseSearch = onCloseArtworkSearch,
                            onSelectCandidate = onSelectArtworkCandidate,
                            onApply = onApplyArtwork,
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
    onOpenAlbumArtwork: () -> Unit,
    hasHiddenRadios: Boolean = false,
    onRestoreHiddenRadios: () -> Unit = {},
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
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Corrigir capas",
            subtitle = "Buscar capa na internet pros albuns sem imagem",
            icon = Icons.Filled.Image,
            onClick = onOpenAlbumArtwork,
        )
        if (hasHiddenRadios) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SettingsActionRow(
                title = "Restaurar radios ocultas",
                subtitle = "Traz de volta as radios de perfil/genero que voce apagou",
                icon = Icons.Filled.Sync,
                onClick = onRestoreHiddenRadios,
            )
        }
    }
}

@Composable
private fun RadioBulletinSettingsPanel(
    radioBulletins: RadioBulletinUiState,
    bulletinBuffer: RadioBulletinBufferUiState,
    radioVoice: RadioVoiceUiState,
    onBack: () -> Unit,
    onSetPreferLocalWriter: (Boolean) -> Unit,
    onImportWriterPackage: () -> Unit,
    onClearWriterPackage: () -> Unit,
    onSaveGeminiApiKey: (String) -> Unit,
    onClearGeminiApiKey: () -> Unit,
    onImportVoicePackage: () -> Unit,
    onClearVoicePackage: () -> Unit,
    onSetVoiceEnabled: (Boolean) -> Unit,
    onTestVoicePackage: () -> Unit,
    onReplayTestAudio: () -> Unit,
    onTestAndroidVoice: () -> Unit,
    onPauseBulletinPreparation: () -> Unit,
    onResumeBulletinPreparation: () -> Unit,
    onResetBulletinBuffer: () -> Unit,
    onPlayReadyBulletin: (Int) -> Unit,
) {
    val settings = radioBulletins.settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState(), flingBehavior = rememberSoftFlingBehavior())
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

        RadioBulletinBufferStatusCard(
            bulletinBuffer = bulletinBuffer,
            onPause = onPauseBulletinPreparation,
            onResume = onResumeBulletinPreparation,
            onReset = onResetBulletinBuffer,
            onPlay = onPlayReadyBulletin,
        )
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))

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
        radioBulletins.localWriterMessage?.let { message ->
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
                onClick = onImportWriterPackage,
                enabled = !radioBulletins.localWriterImporting,
            ) {
                if (radioBulletins.localWriterImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (radioBulletins.localWriterImporting) "Importando..." else "Importar redator")
            }
            if (radioBulletins.localWriterInstalled) {
                TextButton(onClick = onClearWriterPackage) {
                    Text("Remover", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))
        Text("Redator via Gemini (nuvem)", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (radioBulletins.geminiConfigured) {
                "Chave salva: o Gemini escreve o boletim primeiro (mais rapido), e o redator local so entra se ele falhar."
            } else {
                "Opcional: cole sua chave de API gratuita do Gemini pra escrever o boletim na nuvem em vez do redator local. Precisa de internet; a sintetizacao de voz continua sempre no aparelho."
            },
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        if (radioBulletins.geminiConfigured) {
            TextButton(onClick = onClearGeminiApiKey) {
                Text("Remover chave do Gemini", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            var geminiKeyInput by remember { mutableStateOf("") }
            MetadataTextField(
                value = geminiKeyInput,
                onValueChange = { geminiKeyInput = it },
                label = "Chave de API do Gemini",
                placeholder = "AIza...",
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onSaveGeminiApiKey(geminiKeyInput)
                    geminiKeyInput = ""
                },
                enabled = geminiKeyInput.isNotBlank(),
            ) {
                Text("Salvar chave")
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))
        Text("Voz local", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PailerSurface.copy(alpha = 0.9f),
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
                                "${radioVoice.femaleSpeaker.ifBlank { "Locutora" }} / ${radioVoice.maleSpeaker.ifBlank { "Locutor" }}"
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
                        enabled = !radioVoice.isImporting && !radioVoice.isTesting,
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
                            enabled = !radioVoice.isTesting,
                        ) {
                            if (radioVoice.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (radioVoice.isTesting) "Testando..." else "Testar ${radioVoice.packageName}")
                        }
                    }
                }
                if (radioVoice.isInstalled) {
                    Spacer(Modifier.height(8.dp))
                    // Testar o redator local/boletim acontece pelo card do buffer (ver
                    // RadioBulletinBufferStatusCard "Reproduzir boletim pronto") - pedido do
                    // usuario (03/09/2026): um caminho de teste so, ja que o preparo automatico
                    // do buffer serve como teste em si (e evita duas geracoes concorrentes
                    // disputando CPU, uma pelo botao manual e outra pelo preparo de fundo).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onClearVoicePackage) {
                            Text("Remover", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (radioVoice.canReplayTest) {
                            TextButton(onClick = onReplayTestAudio) {
                                Text("Tocar de novo", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onTestAndroidVoice,
            enabled = !radioVoice.isTestingAndroidVoice,
        ) {
            if (radioVoice.isTestingAndroidVoice) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (radioVoice.isTestingAndroidVoice) "Testando..." else "Testar voz do Android")
        }
        Text(
            text = "Por seguranca, pacote importado nao liga sozinho. Se algo falhar, deixe desligado e a radio usa a voz do Android.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// Status do buffer de boletins prontos (ADR-019) em tempo real - pedido do usuario
// (03/09/2026): acompanhar quantos boletins ja estao prontos/sendo escritos, alem de poder
// pausar (cancela o preparo e nao deixa comecar outro) e resetar (apaga o que tem e comeca de
// novo) o preparo em segundo plano.
@Composable
private fun RadioBulletinBufferStatusCard(
    bulletinBuffer: RadioBulletinBufferUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    // Pulso lento (1400ms, mais devagar que o "radioLivePulse"/"playerLivePulse" de 820ms usados
    // em "ao vivo" pela UI) na bolinha que esta sendo escrita agora - pedido do usuario
    // (03/09/2026): quer ver visualmente qual boletim esta em andamento, piscando devagar.
    val pulseTransition = rememberInfiniteTransition(label = "bulletinBufferPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bulletinBufferPulseAlpha",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PailerSurface.copy(alpha = 0.9f),
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
                        "Boletins em buffer",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${bulletinBuffer.readyCount}/${bulletinBuffer.targetCount} prontos" +
                            if (bulletinBuffer.isPaused) " · pausado" else "",
                        modifier = Modifier.padding(top = 3.dp),
                        color = if (bulletinBuffer.isPaused) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (bulletinBuffer.isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Um "ponto" por vaga do buffer: preenchido = pronto (clicavel, toca aquele boletim
            // especifico), translucido = sendo escrito agora, vazio = ainda por fazer - da pra
            // acompanhar de relance sem ler numero. Antes so o botao abaixo tocava o primeiro item
            // - pedido do usuario (03/09/2026): "os outros que ficam pronto, nao sei como
            // reproduzir eles" - agora cada bolinha preenchida toca a posicao correspondente.
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(bulletinBuffer.targetCount) { slot ->
                    val filled = slot < bulletinBuffer.readyCount
                    val preparing = !filled && slot == bulletinBuffer.readyCount && bulletinBuffer.isPreparing
                    val clickable = filled && !bulletinBuffer.isPlayingPreview
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .then(if (preparing) Modifier.alpha(pulse) else Modifier)
                            .clip(CircleShape)
                            .then(if (clickable) Modifier.clickable { onPlay(slot) } else Modifier)
                            .background(
                                when {
                                    filled -> MaterialTheme.colorScheme.primary
                                    preparing -> MaterialTheme.colorScheme.primary
                                    else -> Color.White.copy(alpha = 0.14f)
                                },
                            ),
                    )
                }
            }
            bulletinBuffer.statusMessage?.let { message ->
                // Mensagens de erro (RadioBulletinRepository.enhanceScript ou o catch generico
                // de refillBulletinBuffer) comecam com "Erro"/"falhou" de proposito - destaca em
                // vermelho pra ficar visivel na hora, sem precisar abrir o logcat.
                val isError = message.startsWith("Erro", ignoreCase = true) || message.contains("falhou", ignoreCase = true)
                // Percentual real - na escrita vem do callback nativo por token do redator local
                // (ver LlamaProgressListener), na sintese de voz vem do servico (ver
                // RadioBulletinBufferUiState.progressPercent).
                val text = bulletinBuffer.progressPercent?.let { percent -> "$message ($percent%)" } ?: message
                Text(
                    text = text,
                    modifier = Modifier.padding(top = 8.dp),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bulletinBuffer.isPaused) {
                    Button(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retomar")
                    }
                } else {
                    Button(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pausar")
                    }
                }
                TextButton(onClick = onReset) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Resetar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Reproduz o primeiro boletim ja pronto direto aqui, sem precisar entrar numa radio
            // pra testar (pedido do usuario 03/09/2026) - linha propria (nao cabia junto de
            // Pausar/Resetar sem estourar a largura da tela, empurrando o Resetar pra fora - bug
            // visto ao vivo 03/09/2026) - so acende quando tem pelo menos 1 pronto. Virou o
            // caminho PRINCIPAL de teste do redator local (03/09/2026): substituiu o antigo botao
            // "Testar noticia real", que gerava sob demanda sem passar pelo llmGenerationMutex e
            // podia rodar concorrente com o preparo de fundo, disputando CPU/threads.
            OutlinedButton(
                onClick = { onPlay(0) },
                enabled = bulletinBuffer.hasPlayableAudio && !bulletinBuffer.isPlayingPreview,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            ) {
                if (bulletinBuffer.isPlayingPreview) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        bulletinBuffer.isPlayingPreview -> "Tocando..."
                        // Nivel 2 (bulletinBuffer) vazio mas Nivel 1 (nucleo) tem algo pronto -
                        // deixa claro que e so previa do miolo, sem abertura/fechamento (que
                        // dependem de radio real tocando - ver playReadyBufferedBulletin).
                        bulletinBuffer.hasCorePreview -> "Reproduzir prévia (só o miolo)"
                        else -> "Reproduzir boletim pronto"
                    },
                )
            }
        }
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
private fun AlbumArtworkSettingsPanel(
    state: AlbumArtworkUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onOpenSearch: (LocalAlbum) -> Unit,
    onCloseSearch: () -> Unit,
    onSelectCandidate: (ArtworkCandidate) -> Unit,
    onApply: (LocalAlbum) -> Unit,
) {
    val activeAlbum = state.albumsWithoutArtwork.firstOrNull { it.key == state.activeAlbumKey }
    Box(Modifier.fillMaxSize()) {
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
                    "Corrigir capas",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                state.message ?: "${state.albumsWithoutArtwork.size} albuns sem capa",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onScan,
                enabled = !state.isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isScanning) "Procurando..." else "Varrer albuns")
            }
            Spacer(Modifier.height(16.dp))
            if (state.albumsWithoutArtwork.isEmpty() && !state.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum album sem capa.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    flingBehavior = rememberSoftFlingBehavior(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.albumsWithoutArtwork, key = { it.key }) { album ->
                        RowItem(
                            title = album.title,
                            subtitle = album.artist,
                            icon = { ArtworkBox(album.artworkUri, Modifier.size(52.dp)) },
                            onClick = { onOpenSearch(album) },
                            trailing = {
                                TextButton(onClick = { onOpenSearch(album) }) {
                                    Text("Corrigir")
                                }
                            },
                        )
                    }
                }
            }
        }

        if (activeAlbum != null) {
            ArtworkSearchOverlay(
                album = activeAlbum,
                state = state,
                onCancel = onCloseSearch,
                onSelectCandidate = onSelectCandidate,
                onApply = { onApply(activeAlbum) },
            )
        }
    }
}

@Composable
private fun ArtworkSearchOverlay(
    album: LocalAlbum,
    state: AlbumArtworkUiState,
    onCancel: () -> Unit,
    onSelectCandidate: (ArtworkCandidate) -> Unit,
    onApply: () -> Unit,
) {
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
            color = PailerSurfaceHigh,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        album.title,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
                when {
                    state.isSearching -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    state.candidates.isEmpty() -> Text(
                        "Nenhuma capa encontrada pra essa busca.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> LazyRow(
                        flingBehavior = rememberSoftFlingBehavior(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.candidates, key = { it.previewUrl }) { candidate ->
                            val selected = candidate == state.selectedCandidate
                            Column(
                                modifier = Modifier
                                    .width(84.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectCandidate(candidate) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (selected) {
                                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(if (selected) 3.dp else 0.dp),
                                ) {
                                    NetworkThumbnail(url = candidate.previewUrl, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    candidate.label,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                if (state.isDownloadingSelection) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Baixando capa escolhida...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = onApply,
                        enabled = state.selectedCandidate != null && !state.isDownloadingSelection && !state.isApplying,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isApplying) "Gravando..." else "Aplicar")
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkThumbnail(url: String, modifier: Modifier = Modifier) {
    var image by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val bitmap = withContext(Dispatchers.IO) { loadNetworkThumbnail(url) }
        if (bitmap != null) image = bitmap else failed = true
    }
    val currentImage = image
    when {
        currentImage != null -> Image(
            bitmap = currentImage,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        failed -> ArtworkPlaceholder(modifier = modifier, iconModifier = Modifier.size(20.dp))
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

private fun loadNetworkThumbnail(url: String): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

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
            flingBehavior = rememberSoftFlingBehavior(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PailerGunmetal.copy(alpha = 0.6f))
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
            flingBehavior = rememberSoftFlingBehavior(),
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
            .background(PailerSurface.copy(alpha = 0.9f))
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
            .background(PailerSurface.copy(alpha = 0.9f))
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
                    color = MaterialTheme.colorScheme.primary,
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
            .background(PailerSurface.copy(alpha = 0.9f))
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
            .background(PailerSurface.copy(alpha = 0.9f))
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
            flingBehavior = rememberSoftFlingBehavior(),
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
    val radioIsActive = player.activeRadioName.isNotBlank() && player.hasMedia

    LazyColumn(
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (radioIsActive) {
            item {
                LiveNowRadioCard(
                    player = player,
                    onClick = onOpenPlayer,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
        }
        // So mostra "Continuar ouvindo" quando nao ha radio tocando - com a radio ativa o
        // card "ao vivo" acima ja ocupa esse espaco (pedido do usuario).
        if (continueSong != null && !radioIsActive) {
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
            val recentAlbumsListState = rememberLazyListState()
            // Carrossel automatico bem lento, avancando uma capa por vez - pausa longa entre
            // cada passo em vez de animacao de deslize lenta (LazyListState.animateScrollToItem
            // nao expoe duracao customizavel), da o mesmo efeito de "passeando" pelas capas.
            LaunchedEffect(homeRecentAlbums) {
                if (homeRecentAlbums.size > 1) {
                    while (true) {
                        delay(4500)
                        if (!recentAlbumsListState.isScrollInProgress) {
                            val next = (recentAlbumsListState.firstVisibleItemIndex + 1) % homeRecentAlbums.size
                            recentAlbumsListState.animateScrollToItem(next)
                        }
                    }
                }
            }
            LazyRow(
                state = recentAlbumsListState,
                flingBehavior = rememberSoftFlingBehavior(),
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
                flingBehavior = rememberSoftFlingBehavior(),
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
                flingBehavior = rememberSoftFlingBehavior(),
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
                    flingBehavior = rememberSoftFlingBehavior(),
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
    favoriteArtistKeys: Set<String> = emptySet(),
    scrollState: ScrollState = rememberScrollState(),
) {
    // Bento: favoritos ocupam bloco 2x2 de verdade, os demais fluem 1x1 ao redor - por isso
    // isso nao e mais LazyVerticalGrid (GridItemSpan so estica largura dentro da MESMA linha,
    // nao da pra reservar altura extra com vizinhos preenchendo do lado). Ver BentoGrid.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState, flingBehavior = rememberSoftFlingBehavior())
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        BentoGrid(
            items = artists,
            isFavorite = { it.key in favoriteArtistKeys },
        ) { artist ->
            ArtistGridCard(
                artist = artist,
                onClick = { onOpenArtist(artist) },
                onLongClick = { onDeleteArtist(artist) },
            )
        }
    }
}

// Placement (linha, coluna, quantas colunas/linhas de lado) de um item na grade bento.
private data class BentoPlacement(val row: Int, val col: Int, val span: Int)

// Empacotamento "first-fit dense" (mesma ideia do `grid-auto-flow: dense` do CSS Grid):
// varre da esquerda pra direita, de cima pra baixo, e encaixa cada item no primeiro espaco
// livre grande o suficiente - itens 1x1 preenchem os buracos ao redor de um bloco 2x2 sem
// deixar lacunas. `isBig` na ordem de `artists` decide o tamanho (2x2 pros favoritos).
private fun packBentoGrid(isBig: List<Boolean>, columns: Int): List<BentoPlacement> {
    val occupied = mutableListOf<BooleanArray>()
    fun ensureRow(row: Int) {
        while (occupied.size <= row) occupied.add(BooleanArray(columns))
    }
    fun fits(row: Int, col: Int, span: Int): Boolean {
        if (col + span > columns) return false
        for (dr in 0 until span) {
            ensureRow(row + dr)
            for (dc in 0 until span) {
                if (occupied[row + dr][col + dc]) return false
            }
        }
        return true
    }
    fun occupy(row: Int, col: Int, span: Int) {
        for (dr in 0 until span) {
            ensureRow(row + dr)
            for (dc in 0 until span) {
                occupied[row + dr][col + dc] = true
            }
        }
    }
    val placements = ArrayList<BentoPlacement>(isBig.size)
    for (big in isBig) {
        val span = if (big) 2 else 1
        var row = 0
        var placed = false
        while (!placed) {
            ensureRow(row)
            for (col in 0..(columns - span)) {
                if (fits(row, col, span)) {
                    occupy(row, col, span)
                    placements.add(BentoPlacement(row, col, span))
                    placed = true
                    break
                }
            }
            row++
        }
    }
    return placements
}

// Grade bento generica: favoritos (isFavorite) ganham um bloco 2x2, os demais ficam 1x1 - usada
// tanto por artistas (ArtistGridCard) quanto por albuns (AlbumGridCard, pedido do usuario
// 04/09/2026 pra ter a mesma sensacao de "capa maior pro favorito" nos dois lugares), os dois
// escalam capa/texto proporcionalmente porque o card recebido em `itemContent` e sempre
// fillMaxWidth(). Layout customizado porque LazyVerticalGrid nao suporta item ocupando varias
// LINHAS com vizinhos preenchendo ao redor (so estica coluna na mesma linha) - ver
// packBentoGrid(). Nao e lazy: mede todos os cards de uma vez (aceitavel pro tamanho tipico de
// biblioteca pessoal; numa biblioteca MUITO maior, valeria a pena revisar).
@Composable
private fun <T> BentoGrid(
    items: List<T>,
    isFavorite: (T) -> Boolean,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    gap: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    val bigFlags = items.map(isFavorite)
    val placements = remember(items, bigFlags) { packBentoGrid(bigFlags, columns) }
    val rowCount = placements.maxOfOrNull { it.row + it.span } ?: 0

    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val gapPx = gap.roundToPx()
        val totalWidth = constraints.maxWidth
        val cellWidth = (totalWidth - gapPx * (columns - 1)) / columns

        // Card tipico nao e quadrado (capa 1:1 + linhas de texto embaixo) - pra saber a altura
        // de 1 celula sem cravar um numero de dp fixo (que quebraria com fonte do sistema
        // maior), mede um card de amostra a parte pela subcomposicao "probe", nao colocado na
        // tela. Compose so deixa medir cada Measurable uma vez, entao a grade de verdade abaixo
        // usa uma subcomposicao separada da sonda.
        val cellHeight = items.firstOrNull()?.let { sampleItem ->
            subcompose("bento-probe") {
                itemContent(sampleItem)
            }.first().measure(Constraints(minWidth = cellWidth, maxWidth = cellWidth)).height
        } ?: cellWidth

        val placedChildren = subcompose("bento-grid") {
            items.forEach { item -> itemContent(item) }
        }.mapIndexed { index, measurable ->
            val placement = placements[index]
            val width = (cellWidth * placement.span + gapPx * (placement.span - 1)).coerceAtLeast(0)
            val height = (cellHeight * placement.span + gapPx * (placement.span - 1)).coerceAtLeast(0)
            measurable.measure(Constraints.fixed(width, height)) to placement
        }

        val totalHeight = if (rowCount == 0) 0 else cellHeight * rowCount + gapPx * (rowCount - 1)

        layout(totalWidth, totalHeight) {
            placedChildren.forEach { (placeable, placement) ->
                val x: Int = placement.col * (cellWidth + gapPx)
                val y: Int = placement.row * (cellHeight + gapPx)
                placeable.place(x = x, y = y)
            }
        }
    }
}

@Composable
private fun GenreDetailScreen(
    genre: LocalRadio,
    artists: List<LocalArtist>,
    onOpenArtist: (LocalArtist) -> Unit,
    onPlayGenre: () -> Unit,
    onShuffleGenre: () -> Unit,
    onCreateRadio: () -> Unit = {},
    listState: LazyGridState = rememberLazyGridState(),
) {
    var showCreateRadioConfirm by rememberSaveable(genre.name) { mutableStateOf(false) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPlayGenre,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Tocar categoria",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = onShuffleGenre,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Misturar categoria",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = { showCreateRadioConfirm = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            Icons.Filled.PlaylistPlay,
                            contentDescription = "Criar radio desta categoria",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
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
    if (showCreateRadioConfirm) {
        AlertDialog(
            onDismissRequest = { showCreateRadioConfirm = false },
            icon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
            title = { Text("Criar radio") },
            text = { Text("Criar uma radio a partir da categoria \"${genre.name}\"?") },
            confirmButton = {
                TextButton(onClick = { showCreateRadioConfirm = false; onCreateRadio() }) {
                    Text("Sim")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRadioConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
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
    onPlayArtist: () -> Unit,
    onShuffleArtist: () -> Unit,
    availableGenres: List<String>,
    onSaveMetadata: (String, List<AlbumMetadataEdit>) -> Unit,
    onCreateRadio: () -> Unit = {},
    listState: LazyGridState = rememberLazyGridState(),
) {
    val artworkSong = artist.songs.firstOrNull { it.artworkUri != null }
    var showEditor by rememberSaveable(artist.key) { mutableStateOf(false) }
    var showCreateRadioConfirm by rememberSaveable(artist.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = listState,
            flingBehavior = rememberSoftFlingBehavior(),
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtworkBox(
                        uri = artworkSong?.artworkUri,
                        embeddedSourceUri = artworkSong?.contentUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        iconModifier = Modifier.size(84.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onPlayArtist,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Tocar artista",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                        IconButton(
                            onClick = onShuffleArtist,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PailerGunmetal.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Misturar artista",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                        IconButton(
                            onClick = { showCreateRadioConfirm = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PailerGunmetal.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Filled.PlaylistPlay,
                                contentDescription = "Criar radio deste artista",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
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
        if (showCreateRadioConfirm) {
            AlertDialog(
                onDismissRequest = { showCreateRadioConfirm = false },
                icon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                title = { Text("Criar radio") },
                text = { Text("Criar uma radio a partir do artista \"${artist.name}\"?") },
                confirmButton = {
                    TextButton(onClick = { showCreateRadioConfirm = false; onCreateRadio() }) {
                        Text("Sim")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateRadioConfirm = false }) {
                        Text("Cancelar")
                    }
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
            color = PailerSurfaceHigh,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 10.dp,
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                flingBehavior = rememberSoftFlingBehavior(),
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
                            .background(PailerSurface.copy(alpha = 0.9f))
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
                        GenreTagField(
                            value = draft.genre,
                            onValueChange = { value -> drafts = drafts + (album.key to draft.copy(genre = value)) },
                            suggestions = availableGenres,
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
            focusedContainerColor = PailerSurfaceHighest,
            unfocusedContainerColor = PailerSurfaceHighest,
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

// Campo de genero com tags multi-select + autocomplete - substitui o TextField livre de genero
// nos editores de album/artista. Guarda o valor como string unica delimitada (ver
// GENRE_TAG_SEPARATOR/splitGenreTags/joinGenreTags em GenreTags.kt) porque o campo real gravado
// no ID3 do arquivo (FieldKey.GENRE, ver MusicLibraryRepository.writeTagsToAudioFile) e uma
// string simples - nao precisou mudar tipo em nenhum lugar da cadeia de persistencia.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreTagField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String = "Gênero",
) {
    var input by remember { mutableStateOf("") }
    val tags = remember(value) { splitGenreTags(value) }

    fun commitTag(raw: String) {
        val trimmed = capitalizeGenreTag(raw.trim().trim(';', ',', '/'))
        if (trimmed.isBlank() || tags.any { it.equals(trimmed, ignoreCase = true) }) {
            input = ""
            return
        }
        onValueChange(joinGenreTags(tags + trimmed))
        input = ""
    }

    val filteredSuggestions = remember(input, suggestions, tags) {
        if (input.isBlank()) {
            emptyList()
        } else {
            suggestions.filter { suggestion ->
                suggestion.contains(input, ignoreCase = true) &&
                    tags.none { it.equals(suggestion, ignoreCase = true) }
            }.take(6)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(PailerSurfaceHighest)
                            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tag,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        IconButton(
                            onClick = { onValueChange(joinGenreTags(tags.filterNot { it.equals(tag, ignoreCase = true) })) },
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remover tag $tag",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        TextField(
            value = input,
            onValueChange = { text ->
                if (text.endsWith(";") || text.endsWith(",")) {
                    commitTag(text.dropLast(1))
                } else {
                    input = text
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text("Digite pra buscar ou criar uma tag", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitTag(input) }),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PailerSurfaceHighest,
                unfocusedContainerColor = PailerSurfaceHighest,
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
        if (filteredSuggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = PailerSurfaceHighest,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column {
                    filteredSuggestions.forEach { suggestion ->
                        Text(
                            suggestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { commitTag(suggestion) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsScreen(
    albums: List<LocalAlbum>,
    onOpenAlbum: (LocalAlbum) -> Unit,
    onDeleteAlbum: (LocalAlbum) -> Unit = {},
    favoriteAlbumKeys: Set<String> = emptySet(),
    scrollState: ScrollState = rememberScrollState(),
) {
    // Mesmo bento de ArtistsScreen (ver BentoGrid) - pedido do usuario (04/09/2026): favoritar
    // um album tambem deve dar capa maior, igual ja acontecia so pra artista.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState, flingBehavior = rememberSoftFlingBehavior())
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        BentoGrid(
            items = albums,
            isFavorite = { it.key in favoriteAlbumKeys },
        ) { album ->
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
    onCreateRadio: () -> Unit = {},
    albumArtwork: AlbumArtworkUiState = AlbumArtworkUiState(),
    onOpenArtworkSearch: () -> Unit = {},
    onCloseArtworkSearch: () -> Unit = {},
    onSelectArtworkCandidate: (ArtworkCandidate) -> Unit = {},
    onApplyArtwork: (LocalAlbum) -> Unit = {},
    currentlyPlayingSongId: Long? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    var showEditor by rememberSaveable(album.key) { mutableStateOf(false) }
    var showCreateRadioConfirm by rememberSaveable(album.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSoftFlingBehavior(),
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        if (album.isVariousArtists) {
                            AlbumCoverMosaic(
                                songs = album.songs,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            ArtworkBox(
                                uri = album.artworkUri,
                                modifier = Modifier.fillMaxSize(),
                                iconModifier = Modifier.size(64.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(0.22f)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            PailerCharcoal.copy(alpha = 0.55f),
                                        ),
                                    ),
                                ),
                        )
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPlayAlbum,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Tocar album",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = onShuffleAlbum,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Misturar album",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = { showCreateRadioConfirm = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            Icons.Filled.PlaylistPlay,
                            contentDescription = "Criar radio deste album",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
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
                    isPlaying = song.id == currentlyPlayingSongId,
                    showArtwork = album.isVariousArtists,
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
                onFixArtwork = {
                    showEditor = false
                    onOpenArtworkSearch()
                },
            )
        }
        if (albumArtwork.activeAlbumKey == album.key) {
            ArtworkSearchOverlay(
                album = album,
                state = albumArtwork,
                onCancel = onCloseArtworkSearch,
                onSelectCandidate = onSelectArtworkCandidate,
                onApply = { onApplyArtwork(album) },
            )
        }
        if (showCreateRadioConfirm) {
            AlertDialog(
                onDismissRequest = { showCreateRadioConfirm = false },
                icon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                title = { Text("Criar radio") },
                text = { Text("Criar uma radio a partir do album \"${album.title}\"?") },
                confirmButton = {
                    TextButton(onClick = { showCreateRadioConfirm = false; onCreateRadio() }) {
                        Text("Sim")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateRadioConfirm = false }) {
                        Text("Cancelar")
                    }
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
    onFixArtwork: () -> Unit = {},
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
            color = PailerSurfaceHigh,
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
                GenreTagField(
                    value = genre,
                    onValueChange = { genre = it },
                    suggestions = availableGenres,
                )
                OutlinedButton(onClick = onFixArtwork, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Corrigir capa")
                }
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
    isPlaying: Boolean = false,
    // So pra albuns "various artists" (LocalAlbum.isVariousArtists) - mostra a capa
    // EMBUTIDA de cada faixa em vez do numero, porque o album inteiro compartilha uma unica
    // capa de MediaStore que nao representa a faixa individual.
    showArtwork: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isPlaying) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            ArtworkBox(
                uri = song.artworkUri,
                embeddedSourceUri = song.contentUri,
                modifier = Modifier.size(40.dp),
                iconModifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
        } else if (isPlaying) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = "Tocando agora",
                modifier = Modifier.width(34.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                trackNumber.toString(),
                modifier = Modifier.width(34.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
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
    listState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        gridItems(songs, key = { it.id }) { song ->
            SongGridCard(
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
    showGifBanner: Boolean = true,
    listState: LazyGridState = rememberLazyGridState(),
) {
    val radioIsActive = player.activeRadioName.isNotBlank() && player.hasMedia
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // O banner ambiente some quando ha radio tocando pra nao duplicar o gif junto do
        // LiveNowRadioCard, que ja tem seu proprio fundo animado.
        if (showGifBanner) {
            if (radioIsActive) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LiveNowRadioCard(player = player, onClick = onOpenPlayer)
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DayPeriodGifBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
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
    // Mesmo default de RadioBulletinSettings.songsBetweenBulletins (RadioBulletin.kt) - so usado
    // se algum chamador nao passar o valor real das configuracoes de boletim.
    songsBetweenBulletins: Int = 3,
    isGenerating: Boolean,
    onBack: () -> Unit,
    onEnterRadio: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
    onDeleteRadio: () -> Unit = {},
    artists: List<LocalArtist> = emptyList(),
    albums: List<LocalAlbum> = emptyList(),
    onAddArtist: (LocalArtist) -> Unit = {},
    onAddAlbum: (LocalAlbum) -> Unit = {},
    extraArtists: List<LocalArtist> = emptyList(),
    extraAlbums: List<LocalAlbum> = emptyList(),
    onRemoveArtist: (LocalArtist) -> Unit = {},
    onRemoveAlbum: (LocalAlbum) -> Unit = {},
    onRenameRadio: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    var showDeleteConfirm by rememberSaveable(radio.customId) { mutableStateOf(false) }
    // So radio personalizada (isCustom) tem definicao persistida pra estender com mais
    // artista/album - ver MusicLibraryRepository.addSourceToCustomRadio.
    var showAddSource by rememberSaveable(radio.customId) { mutableStateOf(false) }
    var showManageSources by rememberSaveable(radio.customId) { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable(radio.customId) { mutableStateOf(false) }
    // Radio criada de categoria (customId "genre:...") tambem e isCustom - so nao pode renomear,
    // ver MusicLibraryRepository.renameCustomRadio.
    val canRename = radio.isCustom && radio.customId?.startsWith("genre:") != true
    val canManageSources = radio.isCustom && (extraArtists.isNotEmpty() || extraAlbums.isNotEmpty())
    LazyColumn(
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Sem seta de voltar aqui de proposito - o gesto/botao de voltar do proprio Android
        // já cobre isso; a lixeira desceu pra ficar do lado do botao principal (Sair/Entrar).
        val isInSession = player.activeRadioName == radio.name && player.hasMedia
        if (isInSession) {
            item {
                LiveNowRadioCard(player = player, onClick = onOpenPlayer)
            }
            // Ja mostrando a rádio ao vivo no card acima (nome, faixa atual, capa) - repetir
            // mosaico/nome/descricao aqui embaixo seria redundante. So o botao pra sair.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sair da rádio")
                    }
                    if (radio.isCustom) {
                        IconButton(
                            onClick = { showAddSource = true },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PailerGunmetal.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Adicionar artista ou álbum a esta rádio",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    if (canManageSources) {
                        IconButton(
                            onClick = { showManageSources = true },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PailerGunmetal.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Filled.Remove,
                                contentDescription = "Remover artista ou álbum desta rádio",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    if (canRename) {
                        IconButton(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PailerGunmetal.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Renomear rádio",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remover radio",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = PailerGunmetal.copy(alpha = 0.9f)),
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = onEnterRadio,
                                enabled = !isGenerating,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
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
                            if (radio.isCustom) {
                                IconButton(
                                    onClick = { showAddSource = true },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PailerCharcoal.copy(alpha = 0.6f)),
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Adicionar artista ou álbum a esta rádio",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            if (canManageSources) {
                                IconButton(
                                    onClick = { showManageSources = true },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PailerCharcoal.copy(alpha = 0.6f)),
                                ) {
                                    Icon(
                                        Icons.Filled.Remove,
                                        contentDescription = "Remover artista ou álbum desta rádio",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            if (canRename) {
                                IconButton(
                                    onClick = { showRenameDialog = true },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PailerCharcoal.copy(alpha = 0.6f)),
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Renomear rádio",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PailerCharcoal.copy(alpha = 0.6f)),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remover radio",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (sessionSongs.isNotEmpty()) {
            item {
                SectionTitle("Sequencia ao vivo")
            }
            // Boletim nunca vira item de fila de verdade no ExoPlayer (toca por cima via
            // announcementPlayer, ver LocalTuneViewModel) - o indice de sessionSongs bate direto
            // com player.currentMediaItemIndex/queueIndex. Card "Noticia" aqui e so uma marcacao
            // visual de ONDE o boletim entra (a cada `interval` musicas, mesmo calculo de
            // completedRadioSongs/songsBetweenBulletins no ViewModel), pedido do usuario
            // (04/09/2026) pra visualizar tocando/ja tocada/proximo boletim sem abrir o player.
            val interval = songsBetweenBulletins.coerceAtLeast(1)
            sessionSongs.forEachIndexed { index, song ->
                val position = index + 1
                item(key = song.id) {
                    RadioTrackRow(
                        index = position,
                        song = song,
                        isPlaying = isInSession && position == player.queueIndex,
                        isPlayed = isInSession && position < player.queueIndex,
                        onClick = { onPlaySong(index) },
                    )
                }
                if (position % interval == 0) {
                    item(key = "news_break_$position") {
                        RadioNewsBreakCard()
                    }
                }
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

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = "Remover radio?",
            message = "\"${radio.name}\" sai da lista de radios. As musicas continuam no aparelho.",
            onConfirm = {
                showDeleteConfirm = false
                onDeleteRadio()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showAddSource) {
        AddSourceToRadioDialog(
            artists = artists,
            albums = albums,
            onPickArtist = { artist ->
                showAddSource = false
                onAddArtist(artist)
            },
            onPickAlbum = { album ->
                showAddSource = false
                onAddAlbum(album)
            },
            onDismiss = { showAddSource = false },
        )
    }

    if (showManageSources) {
        ManageRadioSourcesDialog(
            extraArtists = extraArtists,
            extraAlbums = extraAlbums,
            onRemoveArtist = onRemoveArtist,
            onRemoveAlbum = onRemoveAlbum,
            onDismiss = { showManageSources = false },
        )
    }

    if (showRenameDialog) {
        RenameRadioDialog(
            currentName = radio.name,
            onConfirm = { newName ->
                showRenameDialog = false
                onRenameRadio(newName)
            },
            onDismiss = { showRenameDialog = false },
        )
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
                .background(MaterialTheme.colorScheme.primary),
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
private fun RadioTrackRow(
    index: Int,
    song: LocalSong,
    onClick: () -> Unit,
    isPlaying: Boolean = false,
    isPlayed: Boolean = false,
) {
    // Contraste em 3 niveis pedido pelo usuario (04/09/2026): tocando agora em destaque, ja
    // tocada apagada/cinza, proxima no tom normal - mesmo padrao visual de AlbumTrackRow
    // (icone GraphicEq + cor primaria quando toca), so com o estado "ja tocada" novo aqui.
    val contentAlpha = if (isPlayed) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isPlaying) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPlaying) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = "Tocando agora",
                modifier = Modifier.width(30.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                index.toString(),
                modifier = Modifier.width(30.dp).alpha(contentAlpha),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ArtworkBox(
            uri = song.artworkUri,
            embeddedSourceUri = song.contentUri,
            modifier = Modifier.size(48.dp).alpha(contentAlpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
            Text(
                song.title,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            formatDuration(song.durationMs),
            modifier = Modifier.alpha(contentAlpha),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// Marca visual de onde o boletim entra na "Sequencia ao vivo" (a cada `songsBetweenBulletins`
// musicas, ver RadioDetailScreen) - pedido do usuario (04/09/2026): "3 musicas, um card de
// noticia, 3 musicas, um card de noticia" pra demarcar os intervalos sem precisar abrir o
// player. Nao e clicavel (o boletim so toca no momento certo pela radio ao vivo de verdade).
@Composable
private fun RadioNewsBreakCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PailerSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Campaign,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = PailerRed,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Notícia",
            color = PailerRed,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LiveNowRadioCard(player: PlayerUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PailerSurface),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveRadioBadge(isActive = true)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatDuration(player.positionMs)} / ${formatDuration(player.durationMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Proporcao real dos gifs (720x406~414) - fundo inteiro, sem cortar topo/base.
                    .aspectRatio(16f / 9f),
            ) {
                DayPeriodGifBackground(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, PailerCharcoal.copy(alpha = 0.92f)),
                            )
                        )
                        .padding(top = 18.dp, start = 10.dp, end = 10.dp, bottom = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtworkBox(
                            uri = player.artworkUri,
                            embeddedSourceUri = player.artworkSourceUri,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                player.playbackSource.ifBlank { "Rádio" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                player.title.ifBlank { "Tocando agora" },
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(player.artist, player.album).filter { it.isNotBlank() }.joinToString(" • "),
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
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
                            .height(3.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                }
            }
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
        icon = {
            ArtworkBox(
                uri = song.artworkUri,
                embeddedSourceUri = song.contentUri,
                modifier = Modifier.size(52.dp),
            )
        },
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
        icon = {
            if (album.isVariousArtists) {
                AlbumCoverMosaic(songs = album.songs, modifier = Modifier.size(58.dp))
            } else {
                ArtworkBox(album.artworkUri, Modifier.size(58.dp))
            }
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistGridCard(artist: LocalArtist, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val artworkSong = artist.songs.firstOrNull { it.artworkUri != null }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ArtworkBox(
            uri = artworkSong?.artworkUri,
            embeddedSourceUri = artworkSong?.contentUri,
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
        if (album.isVariousArtists) {
            AlbumCoverMosaic(
                songs = album.songs,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        } else {
            ArtworkBox(
                uri = album.artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                iconModifier = Modifier.size(38.dp),
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongGridCard(
    song: LocalSong,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            ArtworkBox(
                uri = song.artworkUri,
                embeddedSourceUri = song.contentUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                iconModifier = Modifier.size(38.dp),
            )
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Curtir faixa",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            song.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            song.artist,
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

// Picker de artista/album pra adicionar como fonte extra numa radio personalizada (botao "+" em
// RadioDetailScreen) - so radio personalizada tem definicao persistida pra estender (ver
// MusicLibraryRepository.addSourceToCustomRadio). Lista simples com busca porque nao ha um
// picker generico reaproveitavel no app ainda.
@Composable
private fun AddSourceToRadioDialog(
    artists: List<LocalArtist>,
    albums: List<LocalAlbum>,
    onPickArtist: (LocalArtist) -> Unit,
    onPickAlbum: (LocalAlbum) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAlbums by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredArtists = remember(artists, query) {
        if (query.isBlank()) artists else artists.filter { it.name.contains(query, ignoreCase = true) }
    }
    val filteredAlbums = remember(albums, query) {
        if (query.isBlank()) albums else albums.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        title = { Text("Adicionar à rádio") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showAlbums) {
                        OutlinedButton(onClick = { showAlbums = false }, modifier = Modifier.weight(1f)) {
                            Text("Artistas")
                        }
                        Button(onClick = {}, modifier = Modifier.weight(1f)) {
                            Text("Álbuns")
                        }
                    } else {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) {
                            Text("Artistas")
                        }
                        OutlinedButton(onClick = { showAlbums = true }, modifier = Modifier.weight(1f)) {
                            Text("Álbuns")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MetadataTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Buscar",
                    placeholder = if (showAlbums) "Nome do álbum ou artista" else "Nome do artista",
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    flingBehavior = rememberSoftFlingBehavior(),
                ) {
                    if (!showAlbums) {
                        items(filteredArtists, key = { it.key }) { artist ->
                            RowItem(
                                title = artist.name,
                                subtitle = "${artist.albumCount} álbuns · ${artist.songs.size} faixas",
                                icon = {
                                    ArtworkBox(
                                        artist.songs.firstOrNull { it.artworkUri != null }?.artworkUri,
                                        Modifier.size(48.dp),
                                    )
                                },
                                onClick = { onPickArtist(artist) },
                            )
                        }
                        if (filteredArtists.isEmpty()) {
                            item {
                                Text(
                                    "Nenhum artista encontrado.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    } else {
                        items(filteredAlbums, key = { it.id }) { album ->
                            RowItem(
                                title = album.title,
                                subtitle = "${album.artist} · ${album.songs.size} faixas",
                                icon = { ArtworkBox(album.artworkUri, Modifier.size(48.dp)) },
                                onClick = { onPickAlbum(album) },
                            )
                        }
                        if (filteredAlbums.isEmpty()) {
                            item {
                                Text(
                                    "Nenhum álbum encontrado.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
    )
}

// Lista de artistas/albuns adicionados depois (botao "-" em RadioDetailScreen) com botao de
// remover em cada linha - so mostra fontes extras (ver MusicLibraryRepository.removeSourceFromCustomRadio),
// a fonte original que criou a radio nao aparece aqui porque nao pode ser removida.
@Composable
private fun ManageRadioSourcesDialog(
    extraArtists: List<LocalArtist>,
    extraAlbums: List<LocalAlbum>,
    onRemoveArtist: (LocalArtist) -> Unit,
    onRemoveAlbum: (LocalAlbum) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Remove, contentDescription = null) },
        title = { Text("Remover da rádio") },
        text = {
            if (extraArtists.isEmpty() && extraAlbums.isEmpty()) {
                Text(
                    "Nada pra remover.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    flingBehavior = rememberSoftFlingBehavior(),
                ) {
                    items(extraArtists, key = { "artist:${it.key}" }) { artist ->
                        RowItem(
                            title = artist.name,
                            subtitle = "${artist.albumCount} álbuns · ${artist.songs.size} faixas",
                            icon = {
                                ArtworkBox(
                                    artist.songs.firstOrNull { it.artworkUri != null }?.artworkUri,
                                    Modifier.size(48.dp),
                                )
                            },
                            onClick = { onRemoveArtist(artist) },
                            trailing = {
                                IconButton(onClick = { onRemoveArtist(artist) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remover ${artist.name} desta rádio",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                    items(extraAlbums, key = { "album:${it.id}" }) { album ->
                        RowItem(
                            title = album.title,
                            subtitle = "${album.artist} · ${album.songs.size} faixas",
                            icon = { ArtworkBox(album.artworkUri, Modifier.size(48.dp)) },
                            onClick = { onRemoveAlbum(album) },
                            trailing = {
                                IconButton(onClick = { onRemoveAlbum(album) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remover ${album.title} desta rádio",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
    )
}

// Diálogo simples de renomear rádio personalizada - so aparece pra radio de album/artista, nao
// de categoria (ver RadioDetailScreen.canRename / MusicLibraryRepository.renameCustomRadio).
@Composable
private fun RenameRadioDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        title = { Text("Renomear rádio") },
        text = {
            MetadataTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nome da rádio",
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Salvar")
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
    // Faixas de uma radio "album" personalizada (LocalAlbum.isVariousArtists) compartilham o
    // mesmo ALBUM_ID - distinctBy{albumId} sozinho colapsaria pra 1 capa so, entao complementa
    // com diversidade por artista.
    val byAlbum = songs.distinctBy { it.albumId }
    val covers = (byAlbum + songs.distinctBy { it.artist } + songs).distinct().take(4)
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
                                embeddedSourceUri = song.contentUri,
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

// Mosaico de capas pra albuns "various artists" (compilacoes montadas a mao) - as faixas
// compartilham o mesmo ALBUM_ID no MediaStore, entao nao da pra usar `distinctBy { albumId }`
// como no RadioCoverMosaic. Prioriza diversidade por artista e usa a capa EMBUTIDA de cada
// faixa (nao a do MediaStore, que seria a mesma pra todas) - ver LocalAlbum.isVariousArtists.
@Composable
private fun AlbumCoverMosaic(songs: List<LocalSong>, modifier: Modifier = Modifier) {
    val covers = (songs.distinctBy { it.artist } + songs).distinct().take(4)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        repeat(2) { rowIndex ->
            Row(Modifier.weight(1f)) {
                repeat(2) { columnIndex ->
                    val song = covers.getOrNull(rowIndex * 2 + columnIndex)
                    if (song != null) {
                        ArtworkBox(
                            uri = song.artworkUri,
                            embeddedSourceUri = song.contentUri,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            iconModifier = Modifier.size(20.dp),
                        )
                    } else {
                        ArtworkPlaceholder(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            iconModifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = PailerGunmetal.copy(alpha = 0.9f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ArtworkBox(
                    uri = song.artworkUri,
                    embeddedSourceUri = song.contentUri,
                    modifier = Modifier.size(154.dp),
                    iconModifier = Modifier.size(46.dp),
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(PailerCharcoal.copy(alpha = 0.8f)),
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
            embeddedSourceUri = song.contentUri,
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
        if (album.isVariousArtists) {
            AlbumCoverMosaic(
                songs = album.songs,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        } else {
            ArtworkBox(
                uri = album.artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                iconModifier = Modifier.size(48.dp),
            )
        }
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
    Surface(color = PailerSurface.copy(alpha = 0.94f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBox(
                uri = player.artworkUri,
                embeddedSourceUri = player.artworkSourceUri,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (player.playbackSource.isNotBlank()) {
                    Text(
                        player.playbackSource,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
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
    albumSongs: List<LocalSong> = emptyList(),
    onOpenAlbum: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    isAlbumSongFavorite: (LocalSong) -> Boolean = { false },
    onToggleAlbumSongFavorite: (LocalSong) -> Unit = {},
    onPlayAlbumSong: (LocalSong) -> Unit = {},
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
                .verticalScroll(rememberScrollState(), flingBehavior = rememberSoftFlingBehavior())
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
                embeddedSourceUri = player.artworkSourceUri,
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
                modifier = Modifier
                    .padding(top = 4.dp)
                    .then(
                        if (onOpenArtist != null) Modifier.clickable(onClick = onOpenArtist) else Modifier
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (player.album.isNotBlank()) {
                Text(
                    text = player.album,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .then(
                            if (onOpenAlbum != null) Modifier.clickable(onClick = onOpenAlbum) else Modifier
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (player.currentNewsHeadline.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = PailerGunmetal.copy(alpha = 0.6f)),
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
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
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
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Anterior",
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
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
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Proxima",
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
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
                AlbumSongsSection(
                    albumTitle = player.album,
                    songs = albumSongs,
                    currentSongId = player.songId,
                    isFavorite = isAlbumSongFavorite,
                    onToggleFavorite = onToggleAlbumSongFavorite,
                    onSongClick = onPlayAlbumSong,
                )
            }
        }
    }
}

@Composable
private fun AlbumSongsSection(
    albumTitle: String,
    songs: List<LocalSong>,
    currentSongId: Long?,
    isFavorite: (LocalSong) -> Boolean,
    onToggleFavorite: (LocalSong) -> Unit,
    onSongClick: (LocalSong) -> Unit,
) {
    val otherSongs = songs.filter { it.id != currentSongId }
    if (otherSongs.isEmpty()) return
    Spacer(Modifier.height(22.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PailerGunmetal.copy(alpha = 0.4f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Mais de ${albumTitle.ifBlank { "este álbum" }}",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        otherSongs.forEach { song ->
            AlbumTrackRow(
                trackNumber = song.trackNumber.takeIf { it > 0 } ?: (songs.indexOf(song) + 1),
                song = song,
                isFavorite = isFavorite(song),
                onClick = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) },
            )
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
                    .background(MaterialTheme.colorScheme.primary),
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
            .background(PailerGunmetal.copy(alpha = 0.4f))
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
                    color = MaterialTheme.colorScheme.primary,
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
    // Uri do proprio arquivo de audio (LocalSong.contentUri) - quando presente, tenta a capa
    // embutida NELE primeiro (MediaMetadataRetriever) antes de cair pro `uri` (capa
    // compartilhada do MediaStore por ALBUM_ID). So usado onde uma faixa precisa mostrar a
    // capa dela mesma em vez da capa do album inteiro (ex.: compilacoes com varios artistas).
    embeddedSourceUri: Uri? = null,
) {
    val context = LocalContext.current
    var image by remember(uri, embeddedSourceUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(uri, embeddedSourceUri) {
        image = withContext(Dispatchers.IO) {
            embeddedSourceUri?.let { loadEmbeddedArtwork(context, it) }
                ?: uri?.let { loadScaledArtwork(context, it) }
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

// Le a capa embutida NO ARQUIVO (ID3 APIC/FLAC PICTURE/etc via MediaMetadataRetriever) em vez
// da capa compartilhada por ALBUM_ID do MediaStore. Mais custoso (abre o arquivo por faixa),
// entao so e chamado onde uma compilacao de varios artistas precisa da capa por-faixa de
// verdade - ver `embeddedSourceUri` em ArtworkBox e LocalAlbum.isVariousArtists.
private fun loadEmbeddedArtwork(context: android.content.Context, contentUri: Uri): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val cacheKey = "embedded:$contentUri"
        artworkMemoryCache.get(cacheKey)?.let { return@runCatching it }
        val retriever = android.media.MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(context, contentUri)
            val bytes = retriever.embeddedPicture ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sampleSize = calculateArtworkSampleSize(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } finally {
            retriever.release()
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
                        MaterialTheme.colorScheme.primary,
                        PailerGunmetal,
                        PailerSilver,
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
        lerp(PailerSurfaceHigh, PailerRed, 0.07f),
        lerp(PailerSurface, PailerRed, 0.04f),
        lerp(MaterialTheme.colorScheme.background, PailerRed, 0.05f),
        lerp(PailerCharcoal, PailerRed, 0.09f),
    )
)

@Composable
private fun rememberCurrentDayPeriod(): DayPeriod {
    var period by remember { mutableStateOf(DayPeriod.current()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            period = DayPeriod.current()
        }
    }
    return period
}

@Composable
private fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

@Composable
private fun DayPeriodGifBackground(modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val period = rememberCurrentDayPeriod()
    AsyncImage(
        model = period.gifRes,
        imageLoader = rememberGifImageLoader(),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
