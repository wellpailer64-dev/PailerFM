package com.pailer.localtune.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.ScrollScope
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.ViewCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.pailer.localtune.R
import com.pailer.localtune.data.AlbumMetadataEdit
import com.pailer.localtune.data.ArtistNewsCard
import com.pailer.localtune.data.BulletinTtsProvider
import com.pailer.localtune.data.DuplicateArtistGroup
import com.pailer.localtune.data.GeminiTtsModel
import com.pailer.localtune.data.LocalAlbum
import com.pailer.localtune.data.LocalArtist
import com.pailer.localtune.data.LocalRadio
import com.pailer.localtune.data.LocalSong
import com.pailer.localtune.data.LrcParser
import com.pailer.localtune.data.LyricsSource
import com.pailer.localtune.data.ArtworkCandidate
import com.pailer.localtune.data.PendingTagChange
import com.pailer.localtune.data.capitalizeGenreTag
import com.pailer.localtune.data.joinGenreTags
import com.pailer.localtune.data.splitGenreTags
import com.pailer.localtune.player.LocalTuneViewModel
import com.pailer.localtune.player.LyricsUiState
import com.pailer.localtune.player.AlbumArtworkUiState
import com.pailer.localtune.player.ArtistPhotoUiState
import com.pailer.localtune.player.ArtistNewsUiState
import com.pailer.localtune.player.AppFolderUiState
import com.pailer.localtune.player.BackupUiState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

// Reestruturado 10/09/2026 (pedido do usuario): eram 6 abas na barra inferior (Inicio,
// Artistas, Albuns, Musicas, Categorias, Radio) - virou 3 (MainTab), com a Radio em destaque
// no meio como funcao principal do app, e Artistas/Albuns/Musicas/Categorias viraram filtros
// (LibrarySection) dentro da aba Biblioteca, nao abas proprias.
private enum class MainTab(val label: String) {
    Home("Início"),
    Radio("Rádio"),
    Library("Biblioteca"),
}

private enum class LibrarySection(val label: String) {
    Artists("Artistas"),
    Albums("Álbuns"),
    Songs("Músicas"),
    Genres("Categorias"),
}

private enum class SettingsPage {
    Main,
    Metadata,
    AlbumArtists,
    TagWriter,
    Artists,
    RadioBulletins,
    GeminiApiKeys,
    AlbumArtwork,
    Backup,
    RadioDislikedSongs,
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
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var librarySection by rememberSaveable { mutableStateOf(LibrarySection.Artists) }
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
    // Alvo do popover de acoes (abrir/favoritar/ocultar/excluir) aberto ao segurar num artista
    // ou album na grade - ver LibraryItemActionsSheet. Excluir reusa pendingDeleteAlbum/
    // pendingDeleteArtist acima (o popover so abre o dialogo de confirmacao existente).
    var albumActionsTarget by remember { mutableStateOf<LocalAlbum?>(null) }
    var artistActionsTarget by remember { mutableStateOf<LocalArtist?>(null) }
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
    val backupCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(viewModel::chooseBackupDestination)
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::restoreBackup)
    }
    val appFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(viewModel::chooseAppFolder)
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
    val artistPhoto = viewModel.artistPhotoState.value
    LaunchedEffect(artistPhoto.appliedVersion) {
        // Mesmo motivo do albumArtwork acima: o arquivo local de foto do artista pode trocar de
        // conteudo sem trocar de nome/Uri.
        if (artistPhoto.appliedVersion > 0) artworkMemoryCache.evictAll()
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
    val artistNews = viewModel.artistNewsState.value
    val favoriteArtists = remember(artists, library.favoriteArtistKeys) {
        artists.filter { it.key in library.favoriteArtistKeys }
    }
    val newsContext = LocalContext.current
    val onOpenNewsLink: (String) -> Unit = { link ->
        runCatching {
            newsContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }
    }
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
        selectedTab != MainTab.Home

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
            // Dentro da Biblioteca com um filtro diferente do padrao, o back volta o filtro pro
            // padrao primeiro (mesma logica de "fechar 1 nivel por vez" dos casos acima) antes de
            // sair pra Inicio no proximo back.
            selectedTab == MainTab.Library && librarySection != LibrarySection.Artists ->
                librarySection = LibrarySection.Artists
            selectedTab != MainTab.Home -> selectedTab = MainTab.Home
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
                            isFavorite = viewModel.isCurrentSongFavorite(),
                            onOpen = { showFullPlayer = true },
                            onToggleFavorite = viewModel::toggleCurrentSongFavorite,
                            onToggle = viewModel::togglePlayPause,
                            onPrevious = viewModel::skipPrevious,
                            onNext = viewModel::skipNext,
                            onToggleMute = viewModel::toggleRadioMute,
                            onExitRadio = viewModel::stopRadio,
                            onDislike = {
                                // dislikeCurrentRadioSong() so troca a fila de verdade do
                                // ExoPlayer (controller.replaceMediaItem) - radioSession e um
                                // snapshot separado de UI (ver RadioDetailScreen/sessionSongs,
                                // "Sequencia ao vivo") que precisa ser corrigido manualmente na
                                // MESMA posicao, senao a faixa antiga continuava aparecendo la
                                // mesmo com o player ja tocando a substituta (achado ao vivo
                                // 11/09/2026).
                                viewModel.dislikeCurrentRadioSong()?.let { result ->
                                    if (result.index in radioSession.indices) {
                                        radioSession = radioSession.toMutableList()
                                            .apply { this[result.index] = result.replacement }
                                    }
                                }
                            },
                        )
                    }
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = PailerSurface.copy(alpha = 0.94f),
                    ) {
                        MainTab.entries.forEach { tab ->
                            val isRadio = tab == MainTab.Radio
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
                                icon = {
                                    if (isRadio) {
                                        // Radio em destaque no meio da barra (pedido do usuario
                                        // 10/09/2026: "a radio no meio em destaque como funcao
                                        // principal do app") - selo circular na cor de destaque,
                                        // sempre colorido (selecionado ou nao), maior que os
                                        // outros icones pra chamar mais atencao.
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selectedTab == tab) PailerRed else PailerRed.copy(alpha = 0.75f),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.Radio,
                                                contentDescription = tab.label,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    } else {
                                        Icon(tab.icon(), contentDescription = tab.label)
                                    }
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        fontSize = 10.sp,
                                        fontWeight = if (isRadio) FontWeight.Bold else FontWeight.Normal,
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
                    // Pedido do usuario (10/09/2026): busca só faz sentido em Biblioteca/Rádio -
                    // a aba Início não tem lista pra filtrar, então a barra sumia sem função. Logo
                    // centraliza sozinha quando a busca some (ver LibraryHeader).
                    showSearch = selectedTab != MainTab.Home,
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
                        photoOverrideUri = viewModel.artistPhotoUri(openedArtist),
                        photoState = artistPhoto,
                        onOpenPhotoSearch = { viewModel.openArtistPhotoSearch(openedArtist) },
                        onClosePhotoSearch = viewModel::closeArtistPhotoSearch,
                        onSelectPhotoCandidate = viewModel::selectArtistPhotoCandidate,
                        onApplyPhoto = { viewModel.applyArtistPhoto(openedArtist) },
                        onRemovePhoto = { viewModel.removeArtistPhoto(openedArtist) },
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
                        photoUriFor = viewModel::artistPhotoUri,
                        listState = genreDetailListState,
                    )
                    selectedTab == MainTab.Home -> HomeNewsDrawer(
                        favoriteArtists = favoriteArtists,
                        newsState = artistNews,
                        onRequestLoad = { viewModel.loadArtistNewsIfNeeded(favoriteArtists) },
                        onOpenLink = onOpenNewsLink,
                    ) {
                        HomeScreen(
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
                            onOpenCurrentPlayer = { showFullPlayer = true },
                            onOpenPlayer = openActiveRadio,
                            onPlay = { list, index, shuffle -> viewModel.playSongs(list, index, shuffle, source = if (shuffle) "Misturar tudo" else "Biblioteca") },
                        )
                    }
                    selectedTab == MainTab.Radio -> PlaylistsScreen(
                        radios = radios,
                        player = player,
                        onOpenRadio = {
                            radioSession = emptyList()
                            selectedRadio = it
                        },
                        onOpenPlayer = openActiveRadio,
                        listState = radiosListState,
                    )
                    selectedTab == MainTab.Library -> Column(Modifier.fillMaxSize()) {
                        LibrarySectionTabs(
                            selected = librarySection,
                            onSelect = { librarySection = it },
                        )
                        when (librarySection) {
                            LibrarySection.Artists -> ArtistsScreen(
                                artists = artists,
                                onOpenArtist = { selectedArtist = it },
                                onLongPressArtist = { artistActionsTarget = it },
                                favoriteArtistKeys = library.favoriteArtistKeys,
                                photoUriFor = viewModel::artistPhotoUri,
                                scrollState = artistsScrollState,
                            )
                            LibrarySection.Albums -> AlbumsScreen(
                                albums = albums,
                                onOpenAlbum = { selectedAlbum = it },
                                onLongPressAlbum = { albumActionsTarget = it },
                                favoriteAlbumKeys = library.favoriteAlbumKeys,
                                scrollState = albumsScrollState,
                            )
                            LibrarySection.Songs -> SongsScreen(
                                songs = songs,
                                isSongFavorite = viewModel::isSongFavorite,
                                onToggleSongFavorite = viewModel::toggleSongFavorite,
                                onPlay = { index -> viewModel.playSongs(songs, index, source = "Músicas") },
                                onDeleteSong = { pendingDeleteSong = it },
                                listState = songsListState,
                            )
                            LibrarySection.Genres -> PlaylistsScreen(
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
                        }
                    }
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
            onSetRadioBulletinCloudWriterEnabled = viewModel::setRadioBulletinCloudWriterEnabled,
            onSetRadioBulletinTtsProvider = viewModel::setRadioBulletinTtsProvider,
            onSetRadioBulletinTtsModel = viewModel::setRadioBulletinTtsModel,
            onTestGeminiFlashTtsVoices = viewModel::testGeminiFlashTtsVoices,
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
            onFixFallbackBulletins = viewModel::fixFallbackBulletins,
            onPlayReadyBulletin = viewModel::playReadyBufferedBulletin,
            hasHiddenRadios = viewModel.hasHiddenRadios(),
            onRestoreHiddenRadios = viewModel::restoreHiddenRadios,
            hasHiddenLibraryItems = viewModel.hasHiddenArtistsOrAlbums(),
            onRestoreHiddenLibraryItems = viewModel::restoreHiddenArtistsAndAlbums,
            radioDislikedSongs = viewModel.radioDislikedSongs(),
            onUndislikeSong = viewModel::undislikeSongForRadio,
            backup = viewModel.backupState.value,
            onChooseBackupDestination = { backupCreateLauncher.launch("pailer_fm_backup.json") },
            onBackupNow = viewModel::performBackupNow,
            onClearBackupDestination = viewModel::clearBackupDestination,
            onRestoreBackup = { backupRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
            appFolder = viewModel.appFolderState.value,
            onChooseAppFolder = { appFolderLauncher.launch(null) },
            onClearAppFolder = viewModel::clearAppFolder,
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

        albumActionsTarget?.let { album ->
            LibraryItemActionsSheet(
                title = album.title,
                subtitle = album.artist,
                isFavorite = viewModel.isAlbumFavorite(album),
                onOpen = {
                    albumActionsTarget = null
                    selectedAlbum = album
                },
                onToggleFavorite = { viewModel.toggleAlbumFavorite(album) },
                onHide = {
                    albumActionsTarget = null
                    viewModel.hideAlbum(album)
                },
                onDelete = {
                    albumActionsTarget = null
                    pendingDeleteAlbum = album
                },
                onDismiss = { albumActionsTarget = null },
            )
        }
        artistActionsTarget?.let { artist ->
            LibraryItemActionsSheet(
                title = artist.name,
                subtitle = "${artist.songs.size} faixas",
                isFavorite = viewModel.isArtistFavorite(artist),
                onOpen = {
                    artistActionsTarget = null
                    selectedArtist = artist
                },
                onToggleFavorite = { viewModel.toggleArtistFavorite(artist) },
                onHide = {
                    artistActionsTarget = null
                    viewModel.hideArtist(artist)
                },
                onDelete = {
                    artistActionsTarget = null
                    pendingDeleteArtist = artist
                },
                onDismiss = { artistActionsTarget = null },
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
            val lyrics = viewModel.lyricsState.value
            LaunchedEffect(player.songId, player.activeRadioName) {
                if (player.activeRadioName.isBlank()) viewModel.loadLyricsFor(player.songId)
            }
            FullPlayer(
                player = player,
                isFavorite = viewModel.isCurrentSongFavorite(),
                lyrics = lyrics,
                onFetchLyrics = { player.songId?.let(viewModel::fetchLyricsOnline) },
                onEditLyrics = viewModel::openLyricsEditor,
                onRemoveLyrics = { player.songId?.let(viewModel::removeLyrics) },
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
            if (lyrics.editorOpen) {
                LyricsEditorDialog(
                    draft = lyrics.editorDraft,
                    startedEmpty = lyrics.lyrics.isEmpty,
                    onDraftChange = viewModel::updateLyricsDraft,
                    onSave = { player.songId?.let(viewModel::saveLyrics) },
                    onRemove = { player.songId?.let(viewModel::removeLyrics) },
                    onDismiss = viewModel::dismissLyricsEditor,
                )
            }
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
    // Sem busca (aba Início) a logo centraliza no lugar de ficar encostada a esquerda com um
    // espaco vazio grande do lado - Box com align() em vez do Row de sempre, engrenagem continua
    // no canto direito (mesma posicao das outras abas, pedido do usuario 10/09/2026).
    if (!showSearch) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            HeaderLogo(modifier = Modifier.align(Alignment.Center))
            HeaderSettingsButton(
                onSettings = onSettings,
                isLoading = isLoading,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderLogo()
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
        Spacer(Modifier.width(4.dp))
        HeaderSettingsButton(onSettings = onSettings, isLoading = isLoading)
    }
}

@Composable
private fun HeaderLogo(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = FontFamily.Cursive)) { append("Pailer ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("FM") }
        },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun HeaderSettingsButton(onSettings: () -> Unit, isLoading: Boolean, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onSettings,
        enabled = !isLoading,
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Configuracoes",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
    }
}

// Filtros internos da aba Biblioteca (pedido do usuario 10/09/2026: Artistas/Albuns/Musicas/
// Categorias deixaram de ser abas proprias na barra inferior e viraram um seletor DENTRO da
// Biblioteca). Pilulas simples, no mesmo estilo de superficie arredondada ja usado em outros
// cards do app (ver PailerSurfaceHigh/RoundedCornerShape em RadioBulletinBufferStatusCard).
@Composable
private fun LibrarySectionTabs(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LibrarySection.entries.forEach { section ->
            val isSelected = section == selected
            Surface(
                onClick = { onSelect(section) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary else PailerSurfaceHigh,
            ) {
                Text(
                    section.label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    onSetRadioBulletinCloudWriterEnabled: (Boolean) -> Unit,
    onSetRadioBulletinTtsProvider: (BulletinTtsProvider) -> Unit,
    onSetRadioBulletinTtsModel: (GeminiTtsModel) -> Unit,
    onTestGeminiFlashTtsVoices: () -> Unit,
    onImportRadioWriterPackage: () -> Unit,
    onClearRadioWriterPackage: () -> Unit,
    onSaveGeminiApiKey: (Int, String) -> Unit,
    onClearGeminiApiKey: (Int) -> Unit,
    onImportRadioVoicePackage: () -> Unit,
    onClearRadioVoicePackage: () -> Unit,
    onSetRadioVoiceEnabled: (Boolean) -> Unit,
    onTestRadioVoicePackage: () -> Unit,
    onReplayTestAudio: () -> Unit,
    onTestAndroidVoice: () -> Unit,
    onPauseBulletinPreparation: () -> Unit,
    onResumeBulletinPreparation: () -> Unit,
    onResetBulletinBuffer: () -> Unit,
    onFixFallbackBulletins: () -> Unit,
    onPlayReadyBulletin: (Int) -> Unit,
    hasHiddenRadios: Boolean = false,
    onRestoreHiddenRadios: () -> Unit = {},
    hasHiddenLibraryItems: Boolean = false,
    onRestoreHiddenLibraryItems: () -> Unit = {},
    backup: BackupUiState = BackupUiState(),
    onChooseBackupDestination: () -> Unit = {},
    onBackupNow: () -> Unit = {},
    onClearBackupDestination: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    appFolder: AppFolderUiState = AppFolderUiState(),
    onChooseAppFolder: () -> Unit = {},
    onClearAppFolder: () -> Unit = {},
    radioDislikedSongs: List<LocalSong> = emptyList(),
    onUndislikeSong: (LocalSong) -> Unit = {},
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
                            onOpenBackup = { onPageChange(SettingsPage.Backup) },
                            hasHiddenRadios = hasHiddenRadios,
                            onRestoreHiddenRadios = onRestoreHiddenRadios,
                            hasHiddenLibraryItems = hasHiddenLibraryItems,
                            onRestoreHiddenLibraryItems = onRestoreHiddenLibraryItems,
                            hasRadioDislikedSongs = radioDislikedSongs.isNotEmpty(),
                            onOpenRadioDislikedSongs = { onPageChange(SettingsPage.RadioDislikedSongs) },
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
                            onSetCloudWriterEnabled = onSetRadioBulletinCloudWriterEnabled,
                            onSetTtsProvider = onSetRadioBulletinTtsProvider,
                            onSetTtsModel = onSetRadioBulletinTtsModel,
                            onTestGeminiVoice = onTestGeminiFlashTtsVoices,
                            onImportWriterPackage = onImportRadioWriterPackage,
                            onClearWriterPackage = onClearRadioWriterPackage,
                            onOpenGeminiApiKeys = { onPageChange(SettingsPage.GeminiApiKeys) },
                            onImportVoicePackage = onImportRadioVoicePackage,
                            onClearVoicePackage = onClearRadioVoicePackage,
                            onSetVoiceEnabled = onSetRadioVoiceEnabled,
                            onTestVoicePackage = onTestRadioVoicePackage,
                            onReplayTestAudio = onReplayTestAudio,
                            onTestAndroidVoice = onTestAndroidVoice,
                            onPauseBulletinPreparation = onPauseBulletinPreparation,
                            onResumeBulletinPreparation = onResumeBulletinPreparation,
                            onResetBulletinBuffer = onResetBulletinBuffer,
                            onFixFallbackBulletins = onFixFallbackBulletins,
                            onPlayReadyBulletin = onPlayReadyBulletin,
                        )
                        SettingsPage.GeminiApiKeys -> GeminiApiKeysSettingsPanel(
                            slotsFilled = radioBulletins.geminiApiKeySlotsFilled,
                            onBack = { onPageChange(SettingsPage.RadioBulletins) },
                            onSaveKey = onSaveGeminiApiKey,
                            onClearKey = onClearGeminiApiKey,
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
                        SettingsPage.Backup -> BackupSettingsPanel(
                            state = backup,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onChooseDestination = onChooseBackupDestination,
                            onBackupNow = onBackupNow,
                            onClearDestination = onClearBackupDestination,
                            onRestore = onRestoreBackup,
                            appFolder = appFolder,
                            onChooseAppFolder = onChooseAppFolder,
                            onClearAppFolder = onClearAppFolder,
                        )
                        SettingsPage.RadioDislikedSongs -> RadioDislikedSongsSettingsPanel(
                            songs = radioDislikedSongs,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onUndislike = onUndislikeSong,
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
    onOpenBackup: () -> Unit = {},
    hasHiddenRadios: Boolean = false,
    onRestoreHiddenRadios: () -> Unit = {},
    hasHiddenLibraryItems: Boolean = false,
    onRestoreHiddenLibraryItems: () -> Unit = {},
    hasRadioDislikedSongs: Boolean = false,
    onOpenRadioDislikedSongs: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState(), flingBehavior = rememberSoftFlingBehavior())
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
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Backup",
            subtitle = "Guardar favoritos e fotos de artista num arquivo seu",
            icon = Icons.Filled.CloudUpload,
            onClick = onOpenBackup,
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
        if (hasHiddenLibraryItems) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SettingsActionRow(
                title = "Restaurar artistas/albuns ocultos",
                subtitle = "Traz de volta o que voce ocultou (nao apaga nada do aparelho)",
                icon = Icons.Filled.VisibilityOff,
                onClick = onRestoreHiddenLibraryItems,
            )
        }
        if (hasRadioDislikedSongs) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SettingsActionRow(
                title = "Faixas deslikadas na radio",
                subtitle = "Faixas banidas do shuffle da radio pelo botao de deslike",
                icon = Icons.Filled.ThumbDown,
                onClick = onOpenRadioDislikedSongs,
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
    onSetCloudWriterEnabled: (Boolean) -> Unit,
    onSetTtsProvider: (BulletinTtsProvider) -> Unit,
    onSetTtsModel: (GeminiTtsModel) -> Unit,
    onTestGeminiVoice: () -> Unit,
    onImportWriterPackage: () -> Unit,
    onClearWriterPackage: () -> Unit,
    onOpenGeminiApiKeys: () -> Unit,
    onImportVoicePackage: () -> Unit,
    onClearVoicePackage: () -> Unit,
    onSetVoiceEnabled: (Boolean) -> Unit,
    onTestVoicePackage: () -> Unit,
    onReplayTestAudio: () -> Unit,
    onTestAndroidVoice: () -> Unit,
    onPauseBulletinPreparation: () -> Unit,
    onResumeBulletinPreparation: () -> Unit,
    onResetBulletinBuffer: () -> Unit,
    onFixFallbackBulletins: () -> Unit,
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
            onFixFallback = onFixFallbackBulletins,
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
        // Chave geral do Gemini (06/09/2026, pedido do usuario): desligada, o app ignora as
        // chaves salvas e so o redator local escreve - liga/desliga sem precisar apagar chave
        // nenhuma. Ver RadioBulletinSettings.cloudWriterEnabled/RadioBulletinRepository.
        // enhanceScript.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PailerSurface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Redação em nuvem",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (radioBulletins.settings.cloudWriterEnabled) {
                            "Ligado: o Gemini escreve primeiro (mais rapido), redator local so entra se ele falhar."
                        } else {
                            "Desligado: so o redator local escreve, mesmo com chave salva. As chaves continuam guardadas."
                        },
                        modifier = Modifier.padding(top = 3.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = radioBulletins.settings.cloudWriterEnabled,
                    onCheckedChange = onSetCloudWriterEnabled,
                )
            }
        }

        if (radioBulletins.settings.cloudWriterEnabled) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))
            // Campo de chave movido pra pagina propria (pedido do usuario 10/09/2026: "o campo de
            // chaves... tem que ser uma página a parte") - suporta ate GeminiApiKeySettings.
            // MAX_KEYS (8) chaves testadas em cadeia, ver GeminiApiKeysSettingsPanel. Mesmo pool
            // usado pelo redator (aqui) e pelo Gemini Flash TTS experimental (secao abaixo).
            val filledKeyCount = radioBulletins.geminiApiKeySlotsFilled.count { it }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PailerSurface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                onClick = onOpenGeminiApiKeys,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Chaves do Gemini",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (radioBulletins.geminiConfigured) {
                                "$filledKeyCount de ${radioBulletins.geminiApiKeySlotsFilled.size} salvas - escreve o boletim e (se ligado) sintetiza a voz. Se uma falhar, tenta a próxima em ordem."
                            } else {
                                "Opcional: cole até ${radioBulletins.geminiApiKeySlotsFilled.size} chaves de API gratuitas do Gemini. Sem chave, usa o redator local direto."
                            },
                            modifier = Modifier.padding(top = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))
        // Motor de SINTESE DE VOZ do boletim (Fran/Nico) - diferente das secoes acima, que sao
        // sobre quem ESCREVE o roteiro. Experimental (10/09/2026, pedido do usuario): coexiste
        // com o motor local de sempre, nunca o substitui - qualquer falha do Gemini cai pro
        // sistema atual sozinha (ver LocalTuneViewModel.trySynthesizeWithGeminiFlash).
        Text("Voz dos boletins", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        RadioBulletinChoiceRow(
            title = "Sistema atual",
            subtitle = "Motor local de sempre (Supertonic) - nada muda.",
            selected = settings.ttsProvider == BulletinTtsProvider.CURRENT,
            onClick = { onSetTtsProvider(BulletinTtsProvider.CURRENT) },
        )
        RadioBulletinChoiceRow(
            title = "Gemini Flash TTS · Experimental",
            subtitle = "Sintetiza Fran e Nico na nuvem via Gemini pra avaliar a qualidade. " +
                "Se falhar, o boletim cai pro sistema atual sozinho.",
            selected = settings.ttsProvider == BulletinTtsProvider.GEMINI_FLASH,
            onClick = { onSetTtsProvider(BulletinTtsProvider.GEMINI_FLASH) },
        )
        if (settings.ttsProvider == BulletinTtsProvider.GEMINI_FLASH) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Modelo",
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            GeminiTtsModel.entries.forEach { model ->
                RadioBulletinChoiceRow(
                    title = model.label,
                    subtitle = model.modelId,
                    selected = settings.ttsModel == model,
                    onClick = { onSetTtsModel(model) },
                )
            }
            if (!radioBulletins.geminiConfigured) {
                Text(
                    text = "Precisa de pelo menos 1 chave de API do Gemini salva (seção \"Chaves do Gemini\" acima) pra funcionar. Sem ela, o boletim usa o sistema atual direto.",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            radioBulletins.geminiVoiceTestMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onTestGeminiVoice,
                enabled = !radioBulletins.isTestingGeminiVoice,
            ) {
                if (radioBulletins.isTestingGeminiVoice) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (radioBulletins.isTestingGeminiVoice) "Testando..." else "Testar vozes")
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

// Pagina propria pras chaves de API do Gemini (pedido do usuario 10/09/2026: "o campo de
// chaves... tem que ser uma página a parte, clicar e ir pra página de chaves") - ate
// GeminiApiKeySettings.MAX_KEYS (8) chaves, testadas em cadeia por generateWithRetry (RadioBulletin.kt)
// e GeminiFlashTtsEngine.generateLineWithRetry: se a 1a salva falhar, tenta a proxima em ordem.
// UM pool so pro redator e pro Gemini Flash TTS experimental (mesma secao "Voz dos boletins" na
// tela anterior). Nunca mostra a chave de volta depois de salva (mesmo padrao ja usado pro campo
// unico antigo) - so "salva"/"remover" por slot.
@Composable
private fun GeminiApiKeysSettingsPanel(
    slotsFilled: List<Boolean>,
    onBack: () -> Unit,
    onSaveKey: (Int, String) -> Unit,
    onClearKey: (Int) -> Unit,
) {
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
                "Chaves do Gemini",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Usada pra escrever o roteiro do boletim e, se ligado, pra sintetizar a voz (Gemini " +
                "Flash TTS). Se a 1ª chave falhar (ex.: cota esgotada), a próxima salva é " +
                "tentada automaticamente, na ordem abaixo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(20.dp))
        slotsFilled.forEachIndexed { index, filled ->
            if (index > 0) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))
            }
            Text("Chave ${index + 1}", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (filled) {
                Text(
                    "Salva.",
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onClearKey(index) }) {
                    Text("Remover", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                var keyInput by remember { mutableStateOf("") }
                MetadataTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = "Chave de API do Gemini",
                    placeholder = "AIza...",
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSaveKey(index, keyInput)
                        keyInput = ""
                    },
                    enabled = keyInput.isNotBlank(),
                ) {
                    Text("Salvar chave")
                }
            }
        }
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
    onFixFallback: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    // Amarelo por convencao (nem erro/vermelho, nem normal/primary) pra bolinha de fallback -
    // roteiro generico/repetitivo (RadioScriptSource.Fallback, ver LocalTuneViewModel.
    // syncBulletinBufferState) em vez do bate-bola de verdade escrito por LLM. Pedido do usuario
    // (05/09/2026): "deixamos a bolinha na cor amarela" pra identificar de relance.
    val fallbackColor = Color(0xFFFFC107)
    // Verde SO quando script E voz vieram 100% do Gemini (fullGeminiSlots, ver
    // LocalTuneViewModel.syncBulletinBufferState) - pedido do usuario 10/09/2026: "bolinha verde
    // pra 100% Gemini, azul pra parcial com voz local". Distingue tambem de vermelho piscando
    // (bolinha em preparo) - antes as duas usavam a mesma cor primary, so a pulsacao de alpha
    // diferenciava; agora a cor sozinha ja diz "pronto" vs "escrevendo agora" de relance.
    val geminiColor = Color(0xFF4CAF50)
    // Azul e o estado NORMAL de "pronto" agora (fallback a parte) - cobre desde quem nunca ligou
    // nenhum recurso Gemini ate quem so conseguiu parte (script OU voz, nao os dois) na nuvem.
    // So fica verde quando os dois pedacos vieram do Gemini (ver geminiColor acima).
    val partialColor = Color(0xFF2196F3)
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
                            (if (bulletinBuffer.hasFallback) " · ${bulletinBuffer.fallbackSlots.size} em fallback" else "") +
                            if (bulletinBuffer.isPaused) " · pausado" else "",
                        modifier = Modifier.padding(top = 3.dp),
                        color = when {
                            bulletinBuffer.isPaused -> MaterialTheme.colorScheme.error
                            bulletinBuffer.hasFallback -> fallbackColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                    val isFallback = filled && slot in bulletinBuffer.fallbackSlots
                    val isFullGemini = filled && slot in bulletinBuffer.fullGeminiSlots
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .then(if (preparing) Modifier.alpha(pulse) else Modifier)
                            .clip(CircleShape)
                            .then(if (clickable) Modifier.clickable { onPlay(slot) } else Modifier)
                            .background(
                                when {
                                    isFallback -> fallbackColor
                                    isFullGemini -> geminiColor
                                    filled -> partialColor
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
            // Botoes compactos (padding/icone/texto reduzidos) dentro de um Row com scroll
            // horizontal - com os 3 (Pausar/Retomar, Resetar, Corrigir fallback) no tamanho
            // padrao do Material3 a soma nao cabia na largura da tela e o texto do 3o botao
            // quebrava letra por linha (achado 05/09/2026, ao vivo no aparelho). O scroll e so
            // rede de seguranca pra telas ainda mais estreitas/fonte grande - com o tamanho
            // reduzido os 3 cabem normalmente sem precisar rolar.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val compactPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                if (bulletinBuffer.isPaused) {
                    Button(onClick = onResume, contentPadding = compactPadding) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retomar", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(onClick = onPause, contentPadding = compactPadding) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pausar", style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(onClick = onReset, contentPadding = compactPadding) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Resetar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                // So acende quando ha pelo menos 1 bolinha amarela (ver fallbackColor acima) -
                // remove so os itens em fallback dos buffers e deixa o preparo normal repor essas
                // vagas priorizando o Gemini de novo (ver LocalTuneViewModel.fixFallbackBulletins).
                // A mesma correcao roda sozinha em segundo plano assim que um fallback novo entra
                // no buffer (scheduleFallbackAutoFix) - este botao e so o atalho manual, pra nao
                // esperar o ciclo automatico.
                if (bulletinBuffer.hasFallback) {
                    TextButton(onClick = onFixFallback, contentPadding = compactPadding) {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = fallbackColor,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Corrigir", color = fallbackColor, style = MaterialTheme.typography.labelMedium)
                    }
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
                Text(if (bulletinBuffer.isPlayingPreview) "Tocando..." else "Reproduzir boletim pronto")
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

// Backup manual/automatico de favoritos, overrides de album/artista e fotos de artista (ver
// BackupRepository/BackupScheduler) - pedido do usuario 07/09/2026 depois de perder esses dados
// num "adb uninstall" usado pra debug. Duas formas de escolher o destino: a pasta oficial do
// Pailer FM (ver AppFolderRepository - preferida, tambem organiza Logs/Redator Local/Pacote de
// Vozes) ou um arquivo avulso escolhido na mao (caminho antigo, mantido pra quem configurou
// antes da pasta oficial existir). Com pasta oficial configurada, o backup sempre usa ela -
// os controles de "arquivo avulso" ficam ocultos pra nao confundir com dois destinos possiveis.
@Composable
private fun BackupSettingsPanel(
    state: BackupUiState,
    onBack: () -> Unit,
    onChooseDestination: () -> Unit,
    onBackupNow: () -> Unit,
    onClearDestination: () -> Unit,
    onRestore: () -> Unit,
    appFolder: AppFolderUiState,
    onChooseAppFolder: () -> Unit,
    onClearAppFolder: () -> Unit,
) {
    var pendingRestoreConfirm by remember { mutableStateOf(false) }
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
                "Backup",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Pasta oficial do Pailer FM", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Text(
            "Uma pasta que voce escolhe uma vez - o app organiza dentro dela as subpastas Backup, Logs, Redator Local e Pacote de Vozes. Os modelos continuam rodando de dentro do app (exigencia tecnica), mas ficam com uma copia de referencia la.",
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (appFolder.folderName != null) {
            Text(
                "Pasta atual: ${appFolder.folderName}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onChooseAppFolder, modifier = Modifier.fillMaxWidth()) {
                Text("Trocar pasta")
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onClearAppFolder, modifier = Modifier.fillMaxWidth()) {
                Text("Remover pasta oficial")
            }
        } else {
            Button(onClick = onChooseAppFolder, modifier = Modifier.fillMaxWidth()) {
                Text("Escolher pasta oficial")
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(20.dp))

        Text("Backup", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Text(
            if (state.usingOfficialFolder) {
                "Guarda favoritos, fotos de artista e correcoes de album/artista dentro da pasta oficial acima."
            } else {
                "Guarda favoritos, fotos de artista e correcoes de album/artista num arquivo que voce escolhe (ex.: no seu Google Drive)."
            },
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        if (state.hasDestination) {
            Text(
                "Backup automatico ativo",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            state.destinationDescription?.let { description ->
                Text(
                    "Local: $description",
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (state.lastBackupAtMillis > 0L) {
                    "Ultimo backup: ${formatBackupTimestamp(state.lastBackupAtMillis)}"
                } else {
                    "Ainda nao rodou nenhum backup nesse destino."
                },
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onBackupNow,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isWorking) "Salvando..." else "Fazer backup agora")
            }
            // Controles de arquivo avulso ficam ocultos com pasta oficial ativa - so um destino
            // por vez pra nao confundir (a pasta sempre tem prioridade, ver BackupRepository).
            if (!state.usingOfficialFolder) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onChooseDestination, modifier = Modifier.fillMaxWidth()) {
                    Text("Trocar arquivo de backup")
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onClearDestination, modifier = Modifier.fillMaxWidth()) {
                    Text("Desligar backup automatico")
                }
            }
        } else {
            Button(
                onClick = onChooseDestination,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Escolher arquivo de backup")
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))
        Text("Restaurar", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Text(
            "Escolhe um arquivo de backup salvo antes e substitui os dados atuais do app por ele.",
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { pendingRestoreConfirm = true },
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restaurar de um arquivo")
        }

        state.message?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (pendingRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { pendingRestoreConfirm = false },
            icon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
            title = { Text("Restaurar backup") },
            text = { Text("Isso vai substituir os favoritos, fotos de artista e correcoes atuais pelos do arquivo escolhido. Essa acao nao pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreConfirm = false
                    onRestore()
                }) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private fun formatBackupTimestamp(millis: Long): String =
    java.text.SimpleDateFormat("dd/MM/yyyy 'as' HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(millis))

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

// Faixas marcadas como "deslike" na radio (pedido do usuario 11/09/2026, ver
// LocalTuneViewModel.dislikeCurrentRadioSong/radioDislikedSongs) - lista o que esta banido do
// shuffle de qualquer radio hoje, com botao por faixa pra desfazer (volta a poder ser
// escolhida).
@Composable
private fun RadioDislikedSongsSettingsPanel(
    songs: List<LocalSong>,
    onBack: () -> Unit,
    onUndislike: (LocalSong) -> Unit,
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
                "Faixas deslikadas na rádio",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Nunca mais escolhidas no shuffle de nenhuma rádio - o resto do álbum continua tocando normal.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nenhuma faixa deslikada ainda.",
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
                items(songs, key = { it.id }) { song ->
                    RowItem(
                        title = song.title,
                        subtitle = "${song.artist} • ${song.album}",
                        icon = {
                            ArtworkBox(
                                uri = song.artworkUri,
                                embeddedSourceUri = song.contentUri,
                                modifier = Modifier.size(52.dp),
                            )
                        },
                        onClick = {},
                        trailing = {
                            IconButton(onClick = { onUndislike(song) }) {
                                Icon(
                                    Icons.Filled.ThumbUp,
                                    contentDescription = "Desfazer deslike",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
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
    onOpenCurrentPlayer: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlay: (List<LocalSong>, Int, Boolean) -> Unit,
) {
    val homeRecentAlbums = remember(recentlyAddedAlbums) { recentlyAddedAlbums.take(8) }
    // 2a fileira mostra os PROXIMOS recentes (nao os mesmos 8 de cima) - pedido do usuario
    // 07/09/2026: as duas fileiras repetiam exatamente os mesmos albuns, so espelhado. Cai de
    // volta pros mesmos 8 apenas se a biblioteca nao tiver mais que isso de albuns recentes.
    val homeRecentAlbumsRow2 = remember(recentlyAddedAlbums) {
        recentlyAddedAlbums.drop(8).take(8).ifEmpty { recentlyAddedAlbums.take(8) }
    }
    val homeListenAgain = remember(listenAgain) { listenAgain.take(8) }
    val homeSuggestedAlbums = remember(suggestedAlbums) { suggestedAlbums.take(6) }
    val homeFavoriteAlbums = remember(favoriteAlbums) { favoriteAlbums.take(6) }
    val radioIsActive = player.activeRadioName.isNotBlank() && player.hasMedia
    val currentPlayerSong = remember(player.songId, songs) {
        player.songId?.let { id -> songs.firstOrNull { it.id == id } }
    }
    val homePlaybackContent = when {
        player.hasMedia && !radioIsActive -> HomePlaybackCardContent(
            title = player.title.ifBlank { currentPlayerSong?.title.orEmpty() },
            artist = player.artist.ifBlank { currentPlayerSong?.artist.orEmpty() },
            album = player.album.ifBlank { currentPlayerSong?.album.orEmpty() },
            artworkUri = player.artworkUri ?: currentPlayerSong?.artworkUri,
            artworkSourceUri = player.artworkSourceUri ?: currentPlayerSong?.contentUri,
            positionMs = player.positionMs,
            durationMs = player.durationMs.takeIf { it > 0 } ?: currentPlayerSong?.durationMs ?: 0L,
            isCurrentMedia = true,
        )
        continueSong != null && !radioIsActive -> HomePlaybackCardContent(
            title = continueSong.title,
            artist = continueSong.artist,
            album = continueSong.album,
            artworkUri = continueSong.artworkUri,
            artworkSourceUri = continueSong.contentUri,
            positionMs = continuePositionMs,
            durationMs = continueSong.durationMs,
            isCurrentMedia = false,
        )
        else -> null
    }

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
        // So mostra "Tocando agora"/"Continuar ouvindo" quando nao ha radio tocando - com a radio ativa o
        // card "ao vivo" acima ja ocupa esse espaco (pedido do usuario).
        if (homePlaybackContent != null) {
            item {
                ContinueListeningCard(
                    content = homePlaybackContent,
                    isPlaying = player.hasMedia && player.isPlaying,
                    onClick = if (homePlaybackContent.isCurrentMedia) onOpenCurrentPlayer else onContinue,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
        }
        item {
            SectionTitle("Albuns adicionados recentemente", modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
            ContinuousAlbumRow(albums = homeRecentAlbums, reverse = true, onOpenAlbum = onOpenAlbum)
            Spacer(Modifier.height(14.dp))
            ContinuousAlbumRow(albums = homeRecentAlbumsRow2, reverse = false, onOpenAlbum = onOpenAlbum)
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
    }
}

// Fileira de capas que desliza sozinha, sem parar, bem devagar (pedido do usuario 07/09/2026 -
// substitui o carrossel antigo que avancava aos saltos com animateScrollToItem a cada poucos
// segundos, um efeito "travado"). LazyRow com espaco de indice "infinito" (Int.MAX_VALUE) dando a
// volta nas mesmas capas via modulo - mesma tecnica da esteira de capas da tela de radio
// (RadioCoverTicker). Chegou a ser reescrita sem LazyRow (Row manual + graphicsLayer) pra tentar
// resolver o bug de clique abaixo, mas essa versao teve problemas serios de renderizacao (capas
// sumindo, layout quebrado) - voltou pro LazyRow, que sempre renderizou certinho; o bug de
// clique foi resolvido de outro jeito (ver comentario no LaunchedEffect abaixo).
//
// BUG DE CLIQUE (07/09/2026): tocar num album da fileira nao abria nada. Causa: o auto-scroll
// rodava dentro de UM UNICO listState.scroll(MutatePriority.Default) segurado para sempre (loop
// infinito por dentro do bloco). Qualquer toque na fileira faz o proprio Compose "parar o fling"
// interceptando o gesto ANTES do clickable do item receber o evento - com o mutex sempre ocupado
// pelo auto-scroll, TODO toque colidia com isso, entao o clique nunca disparava. Confirmado
// desligando o auto-scroll: sem ele, o clique funciona perfeitamente.
// Correcao: em vez de UM scroll() segurado por todos os frames, cada frame faz sua PROPRIA
// chamada curta e independente (listState.scrollBy) que adquire e solta o mutex na hora - o
// mutex fica livre durante toda a espera entre frames (a maior parte do tempo real), entao um
// toque quase nunca coincide com o instante exato de uma chamada em andamento. Rolagem manual
// continua funcionando (arrastar usa MutatePriority.UserInput, que ainda ganha prioridade se por
// acaso colidir com uma chamada do auto-scroll).
@Composable
private fun ContinuousAlbumRow(
    albums: List<LocalAlbum>,
    reverse: Boolean,
    onOpenAlbum: (LocalAlbum) -> Unit,
) {
    if (albums.isEmpty()) return
    val density = LocalDensity.current
    val startIndex = remember(albums.size) {
        val half = Int.MAX_VALUE / 2
        half - half % albums.size
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    LaunchedEffect(listState, reverse) {
        val sign = if (reverse) -1f else 1f
        // Bem mais devagar que a esteira de capas da radio (28dp/s) - aqui e so um fundo vivo,
        // sutil, atras de uma tela que o usuario passa a maior parte do tempo olhando parado.
        val pxPerSecond = sign * with(density) { 6.dp.toPx() }
        var lastFrameNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                // Chamada curta: pega e solta o mutex na hora, nao mantem segurado ate o
                // proximo frame (isso e o que resolve o bug de clique - ver comentario acima).
                // Se colidir com um arrasto do usuario (prioridade maior), so essa chamada e
                // cancelada - ignora e tenta de novo no proximo frame.
                runCatching { listState.scrollBy(pxPerSecond * deltaSeconds) }
            }
            lastFrameNanos = frameNanos
        }
    }
    LazyRow(
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(count = Int.MAX_VALUE) { index ->
            val album = albums[index % albums.size]
            AlbumCoverCard(album = album, onClick = { onOpenAlbum(album) })
        }
    }
}

// Painel de noticias/curiosidades dos artistas favoritados, revelado arrastando a Home pra
// direita (pedido do usuario 06/09/2026 - so na Home, ver LocalTuneApp.kt onde e usado). A zona
// de deteccao do gesto fica so numa faixa fina na borda esquerda pra nao brigar com os carrosseis
// horizontais da propria Home (Albuns recentes, Ouvir de novo etc.) - uma vez aberto, o proprio
// painel e o scrim tambem aceitam arrastar/tocar pra fechar.
@Composable
private fun HomeNewsDrawer(
    favoriteArtists: List<LocalArtist>,
    newsState: ArtistNewsUiState,
    onRequestLoad: () -> Unit,
    onOpenLink: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Cobre quase a tela toda, deixando so um filete da Home visivel na ponta (pedido do usuario)
    // - 32dp de sobra em vez da largura cheia, pra ainda dar pra perceber que tem conteudo atras.
    val panelWidthDp = (configuration.screenWidthDp.dp - 32.dp).coerceAtLeast(240.dp)
    val panelWidthPx = with(density) { panelWidthDp.toPx() }
    val offsetX = remember { Animatable(-panelWidthPx) }
    val scope = rememberCoroutineScope()
    val isOpen = offsetX.value > -panelWidthPx / 2
    val progress = ((offsetX.value + panelWidthPx) / panelWidthPx).coerceIn(0f, 1f)

    LaunchedEffect(isOpen) {
        if (isOpen) onRequestLoad()
    }
    BackHandler(enabled = progress > 0f) {
        scope.launch { offsetX.animateTo(-panelWidthPx) }
    }

    // O gesto de voltar do sistema (navegacao por gestos, Android 10+) tambem escuta a borda
    // esquerda da tela - sem excluir essa faixa, o Android intercepta o arrasto como "voltar" e
    // sai do app antes do nosso proprio detector de arrasto receber o toque.
    val view = LocalView.current
    var edgeZoneBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    DisposableEffect(view, edgeZoneBounds) {
        edgeZoneBounds?.let { rect -> ViewCompat.setSystemGestureExclusionRects(view, listOf(rect)) }
        onDispose { ViewCompat.setSystemGestureExclusionRects(view, emptyList()) }
    }

    val dragModifier = Modifier.pointerInput(panelWidthPx) {
        detectHorizontalDragGestures(
            onDragEnd = {
                scope.launch {
                    offsetX.animateTo(if (offsetX.value > -panelWidthPx / 2) 0f else -panelWidthPx)
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-panelWidthPx, 0f)) }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(28.dp)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    edgeZoneBounds = android.graphics.Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt(),
                    )
                }
                .then(dragModifier),
        )
        // Alca visual da gaveta de noticias - sem ela o usuario nao tem como saber que aquela
        // faixa fina na borda esquerda arrasta pra abrir (pedido do usuario 10/09/2026). E so
        // indicacao visual (nao tem pointerInput proprio), entao o toque atravessa pra Box de
        // deteccao de arrasto logo acima; ela mesma some assim que o painel comeca a cobri-la.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 24.dp)
                .alpha((1f - progress * 4f).coerceIn(0f, 1f))
                .width(16.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                .background(PailerRed),
            contentAlignment = Alignment.Center,
        ) {
            // rotate() sozinho nao basta: o Box pai mede o Text ANTES de rotacionar, entao com
            // maxWidth=16dp a palavra ficava truncada em "NE". O Modifier.layout mede o texto sem
            // limite de largura (na horizontal, como se nao fosse rotacionar) e devolve pro pai um
            // tamanho com largura/altura invertidas - o que a rotacao de 90 graus realmente ocupa.
            Text(
                "NEWS",
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                x = -(placeable.width - placeable.height) / 2,
                                y = -(placeable.height - placeable.width) / 2,
                            )
                        }
                    }
                    .rotate(-90f),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * progress))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { scope.launch { offsetX.animateTo(-panelWidthPx) } }
                    .then(dragModifier),
            )
        }
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .width(panelWidthDp)
                .fillMaxHeight()
                .background(PailerCharcoal)
                .then(dragModifier)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(vertical = 18.dp),
        ) {
            Text(
                "Novidades dos seus favoritos",
                modifier = Modifier.padding(horizontal = 18.dp),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(4.dp))
            when {
                favoriteArtists.isEmpty() -> Text(
                    "Curta um artista pra ver noticias e curiosidades dele aqui.",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                newsState.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                newsState.hasLoaded && newsState.cards.isEmpty() -> Text(
                    "Nao encontrei noticias recentes dos seus favoritos agora.",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(newsState.cards, key = { it.link }) { card ->
                        val artist = favoriteArtists.firstOrNull { it.key == card.artistName.lowercase().trim() }
                        val coverSong = artist?.songs?.firstOrNull { it.artworkUri != null }
                        ArtistNewsCardView(
                            card = card,
                            artworkUri = coverSong?.artworkUri,
                            embeddedSourceUri = coverSong?.contentUri,
                            onClick = { onOpenLink(card.link) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistNewsCardView(
    card: ArtistNewsCard,
    artworkUri: Uri?,
    embeddedSourceUri: Uri?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PailerGunmetal.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(bottom = 14.dp),
    ) {
        if (card.imageUrl.isNotBlank()) {
            NetworkThumbnail(
                url = card.imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        } else {
            ArtworkBox(
                uri = artworkUri,
                embeddedSourceUri = embeddedSourceUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                iconModifier = Modifier.size(44.dp),
            )
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    ArtworkBox(
                        uri = artworkUri,
                        embeddedSourceUri = embeddedSourceUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        iconModifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    card.artistName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                card.headline,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                card.source,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val dateLabel = formatNewsDate(card.publishedAtMillis)
            if (dateLabel.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    dateLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ArtistsScreen(
    artists: List<LocalArtist>,
    onOpenArtist: (LocalArtist) -> Unit,
    onLongPressArtist: (LocalArtist) -> Unit = {},
    favoriteArtistKeys: Set<String> = emptySet(),
    photoUriFor: (LocalArtist) -> Uri? = { null },
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
                onLongClick = { onLongPressArtist(artist) },
                photoUri = photoUriFor(artist),
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
    photoUriFor: (LocalArtist) -> Uri? = { null },
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
                photoUri = photoUriFor(artist),
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
    onToggleFavorite: () -> Unit,
    onOpenAlbum: (LocalAlbum) -> Unit,
    onPlayArtist: () -> Unit,
    onShuffleArtist: () -> Unit,
    availableGenres: List<String>,
    onSaveMetadata: (String, List<AlbumMetadataEdit>) -> Unit,
    onCreateRadio: () -> Unit = {},
    photoOverrideUri: Uri? = null,
    photoState: ArtistPhotoUiState = ArtistPhotoUiState(),
    onOpenPhotoSearch: () -> Unit = {},
    onClosePhotoSearch: () -> Unit = {},
    onSelectPhotoCandidate: (ArtworkCandidate) -> Unit = {},
    onApplyPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    listState: LazyGridState = rememberLazyGridState(),
) {
    // Foto escolhida pelo usuario (busca na web) tem prioridade sobre a capa do primeiro album -
    // ver LocalTuneViewModel.artistPhotoUri/LocalArtist nao ter foto propria no data model.
    val artworkSong = artist.songs.firstOrNull { it.artworkUri != null }
    val displayUri = photoOverrideUri ?: artworkSong?.artworkUri
    val displayEmbeddedSource = if (photoOverrideUri != null) null else artworkSong?.contentUri
    var showEditor by rememberSaveable(artist.key) { mutableStateOf(false) }
    var showCreateRadioConfirm by rememberSaveable(artist.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AlbumArtBackdrop(uri = displayUri, embeddedSourceUri = displayEmbeddedSource)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = listState,
            flingBehavior = rememberSoftFlingBehavior(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ArtworkBox(
                            uri = displayUri,
                            embeddedSourceUri = displayEmbeddedSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            iconModifier = Modifier.size(84.dp),
                        )
                        IconButton(
                            onClick = onOpenPhotoSearch,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PailerGunmetal.copy(alpha = 0.75f)),
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Buscar foto do artista na web",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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
                        Spacer(Modifier.width(20.dp))
                        IconButton(
                            onClick = { showEditor = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PailerGunmetal.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Editar metadados",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PailerGunmetal.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Curtir artista",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
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
        if (photoState.activeArtistKey == artist.key) {
            ArtistPhotoSearchOverlay(
                artist = artist,
                state = photoState,
                hasOverride = photoOverrideUri != null,
                onCancel = onClosePhotoSearch,
                onSelectCandidate = onSelectPhotoCandidate,
                onApply = onApplyPhoto,
                onRemove = { onRemovePhoto(); onClosePhotoSearch() },
            )
        }
    }
}

@Composable
private fun ArtistPhotoSearchOverlay(
    artist: LocalArtist,
    state: ArtistPhotoUiState,
    hasOverride: Boolean,
    onCancel: () -> Unit,
    onSelectCandidate: (ArtworkCandidate) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
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
                        "Foto de ${artist.name}",
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
                        state.message ?: "Nenhuma foto encontrada pra essa busca.",
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
                            "Baixando foto escolhida...",
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
                        Text(if (state.isApplying) "Salvando..." else "Aplicar")
                    }
                }
                if (hasOverride) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                        Text("Voltar a usar capa de album")
                    }
                }
            }
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
    onLongPressAlbum: (LocalAlbum) -> Unit = {},
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
                onLongClick = { onLongPressAlbum(album) },
            )
        }
    }
}

// Compartilha o arquivo de audio de verdade (Uri do MediaStore) via ACTION_SEND - o app que
// recebe (WhatsApp, Bluetooth, Drive, etc.) ganha a faixa tocavel direto, sem passar por zip.
private fun shareSong(context: Context, uri: Uri?, title: String) {
    if (uri == null) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar musica"))
}

// Compartilha varias faixas de uma vez (ex.: album inteiro) via ACTION_SEND_MULTIPLE - preferido
// a zipar os arquivos: chega no destinatario como faixas tocaveis direto (a maioria dos apps que
// recebem midia aceita varios anexos de audio), sem o custo de gerar/copiar um zip temporario nem
// depender do destinatario saber descompactar.
private fun shareSongs(context: Context, songs: List<LocalSong>, subject: String) {
    if (songs.isEmpty()) return
    if (songs.size == 1) {
        shareSong(context, songs.first().contentUri, songs.first().title)
        return
    }
    val uris = songs.map { it.contentUri }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        // ClipData com todas as Uris - e o que faz o FLAG_GRANT_READ_URI_PERMISSION valer pra
        // cada arquivo (o app que recebe so consegue ler o que estiver listado aqui).
        clipData = ClipData(subject, arrayOf("audio/*"), ClipData.Item(uris.first())).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar album"))
}

@Composable
private fun AlbumDetailScreen(
    album: LocalAlbum,
    isFavorite: Boolean,
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
    val context = LocalContext.current
    var showEditor by rememberSaveable(album.key) { mutableStateOf(false) }
    var showCreateRadioConfirm by rememberSaveable(album.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (!album.isVariousArtists) {
            AlbumArtBackdrop(uri = album.artworkUri)
        }
        LazyColumn(
            state = listState,
            flingBehavior = rememberSoftFlingBehavior(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
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
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = { showEditor = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar metadados",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Curtir album",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = { shareSongs(context, album.songs, album.title) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PailerGunmetal.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Compartilhar album",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            items(album.songs, key = { it.id }) { song ->
                val index = album.songs.indexOf(song)
                Box(Modifier.padding(horizontal = 18.dp)) {
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
                    Column(Modifier.padding(vertical = 18.dp)) {
                        val radioAlbumSongs = remember(radio.songs) {
                            radio.songs.distinctBy { it.albumId }.filter { it.albumId > 0 }
                        }
                        val radioAlbumNames = remember(radioAlbumSongs) {
                            radioAlbumSongs.map { "${it.artist} – ${it.album}" }
                        }
                        RadioCoverTicker(songs = radio.songs)
                        Spacer(Modifier.height(10.dp))
                        RadioNameTicker(names = radioAlbumNames)
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.padding(horizontal = 18.dp)) {
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
private fun ArtistGridCard(artist: LocalArtist, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, photoUri: Uri? = null) {
    val artworkSong = artist.songs.firstOrNull { it.artworkUri != null }
    val displayUri = photoUri ?: artworkSong?.artworkUri
    val displayEmbeddedSource = if (photoUri != null) null else artworkSong?.contentUri
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ArtworkBox(
            uri = displayUri,
            embeddedSourceUri = displayEmbeddedSource,
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

// Popover de acoes ao segurar num artista ou album na grade (ver ArtistsScreen/AlbumsScreen) -
// pedido do usuario (09/09/2026): antes o long-press ia direto pro dialogo de excluir; agora
// abre isso primeiro com abrir/favoritar/ocultar/excluir. Reaproveitado pelos dois tipos (artista
// e album) - so troca titulo/subtitulo/callbacks. Ocultar nao apaga nada do aparelho, so tira da
// biblioteca (ver MusicLibraryRepository.hideArtist/hideAlbum) - pensado pra faixas que nao sao
// musica de verdade (audio de WhatsApp, gravacoes soltas etc.) sem precisar apagar o arquivo.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryItemActionsSheet(
    title: String,
    subtitle: String,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PailerSurface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            ActionSheetRow(
                icon = Icons.Filled.OpenInNew,
                label = "Abrir",
                onClick = onOpen,
            )
            ActionSheetRow(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isFavorite) "Desfavoritar" else "Favoritar",
                tint = if (isFavorite) PailerRed else null,
                onClick = onToggleFavorite,
            )
            ActionSheetRow(
                icon = Icons.Filled.VisibilityOff,
                label = "Ocultar",
                onClick = onHide,
            )
            ActionSheetRow(
                icon = Icons.Filled.Delete,
                label = "Excluir",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
    }
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

// Carrega a capa embutida de VARIAS faixas reaproveitando a MESMA instancia de
// MediaMetadataRetriever (troca de dataSource via setDataSource, sem recriar) - ver comentario em
// RadioCoverTicker. Criar/descartar um MediaMetadataRetriever novo por faixa em sequencia rapida
// (loadEmbeddedArtwork isolado, um por faixa) devolvia bitmaps "validos" (nao-null, dimensoes
// certas) mas com pixels corrompidos pra todas menos a primeira faixa - reaproveitar a mesma
// instancia evita esse problema de recursos nativos do MediaMetadataRetriever.
private fun loadEmbeddedArtworkBatch(context: Context, songs: List<LocalSong>): List<androidx.compose.ui.graphics.ImageBitmap?> {
    val retriever = android.media.MediaMetadataRetriever()
    try {
        return songs.map { song ->
            runCatching {
                val cacheKey = "embedded:${song.contentUri}"
                artworkMemoryCache.get(cacheKey)?.let { return@runCatching it }
                retriever.setDataSource(context, song.contentUri)
                val bytes = retriever.embeddedPicture ?: return@runCatching song.artworkUri?.let { loadScaledArtwork(context, it) }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateArtworkSampleSize(bounds.outWidth, bounds.outHeight)
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                if (bitmap != null) artworkMemoryCache.put(cacheKey, bitmap)
                bitmap
            }.getOrNull()
        }
    } finally {
        retriever.release()
    }
}

// Usa LazyRow (virtualizada) em vez de uma Row gigante com N copias lado a lado. Uma Row comum
// com muitas capas de 156dp (radios com muitos albuns) chegava a dezenas de milhares de pixels de
// largura total - isso estourava o limite de textura da GPU do aparelho, corrompendo o desenho de
// forma intermitente (as vezes aparecia certo, as vezes ficava em branco, dependendo da posicao de
// scroll e do momento). LazyRow so compoe/desenha o que esta visivel + uma pequena margem, entao a
// largura real desenhada nunca cresce com a quantidade de capas - o indice "infinito"
// (Int.MAX_VALUE itens) com `% covers.size` da a volta pras mesmas capas indefinidamente, e um
// scroll continuo por frame (em vez de Modifier.offset animado) avanca a lista pra sempre.
@Composable
private fun RadioCoverTicker(songs: List<LocalSong>, modifier: Modifier = Modifier) {
    val covers = remember(songs) { songs.distinctBy { it.albumId }.filter { it.albumId > 0 } }
    if (covers.isEmpty()) return
    val context = LocalContext.current
    var images by remember(covers) {
        mutableStateOf<List<androidx.compose.ui.graphics.ImageBitmap?>>(covers.map { null })
    }
    LaunchedEffect(covers) {
        images = withContext(Dispatchers.IO) { loadEmbeddedArtworkBatch(context, covers) }
    }
    val itemSize = 156.dp
    val spacing = 3.dp
    val density = LocalDensity.current
    val startIndex = remember(covers) {
        val half = Int.MAX_VALUE / 2
        half - half % covers.size
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    LaunchedEffect(listState) {
        val pxPerSecond = with(density) { 28.dp.toPx() }
        listState.scroll(MutatePriority.PreventUserInput) {
            var lastFrameNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    if (lastFrameNanos != 0L) {
                        val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                        scrollBy(pxPerSecond * deltaSeconds)
                    }
                    lastFrameNanos = frameNanos
                }
            }
        }
    }
    LazyRow(
        state = listState,
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxWidth()
            .height(itemSize)
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(count = Int.MAX_VALUE) { index ->
            val coverIndex = index % covers.size
            val bitmap = images.getOrNull(coverIndex)
            val tileModifier = Modifier
                .size(itemSize)
                .clip(RoundedCornerShape(8.dp))
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = tileModifier,
                )
            } else {
                ArtworkPlaceholder(modifier = tileModifier, iconModifier = Modifier.size(32.dp))
            }
        }
    }
}

// Filete de texto com o nome de todos os artistas/albuns da radio, rolando da direita pra
// esquerda junto com RadioCoverTicker acima. Largura do texto so se sabe em tempo de execucao
// (fontes/nomes variam), entao mede 1 copia via onSizeChanged e desloca por essa largura medida -
// mesma logica de loop sem emenda do ticker de capas, só que com medida em vez de calculo fixo.
@Composable
private fun RadioNameTicker(names: List<String>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val segment = remember(names) { names.joinToString("   •   ") + "   •   " }
    var segmentWidthPx by remember(names) { mutableStateOf(0) }
    val speedDpPerSecond = 55f
    val density = LocalDensity.current
    val durationMillis = remember(segmentWidthPx, density) {
        if (segmentWidthPx <= 0) {
            4000
        } else {
            (with(density) { segmentWidthPx.toDp().value } / speedDpPerSecond * 1000).toInt().coerceAtLeast(1000)
        }
    }
    val transition = rememberInfiniteTransition(label = "radio-name-ticker")
    val offsetPx by transition.animateFloat(
        initialValue = 0f,
        targetValue = -segmentWidthPx.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radio-name-ticker-offset",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        Row(modifier = Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) }) {
            Text(
                segment,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.onSizeChanged { segmentWidthPx = it.width },
            )
            Text(
                segment,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
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

private data class HomePlaybackCardContent(
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: Uri?,
    val artworkSourceUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val isCurrentMedia: Boolean,
)

@Composable
private fun ContinueListeningCard(
    content: HomePlaybackCardContent,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(if (isPlaying) "Tocando agora" else "Continuar ouvindo")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                SpinningVinylArtwork(
                    uri = content.artworkUri,
                    embeddedSourceUri = content.artworkSourceUri,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PailerCharcoal.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                // Barra de progresso tipo "linha do tempo" no rodape da capa, igual a marca de
                // progresso das thumbnails do YouTube - pedido do usuario pra dar mais cara de
                // player pro card de "continuar ouvindo".
                val progress = if (content.durationMs > 0) {
                    (content.positionMs.toFloat() / content.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                content.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(content.artist, content.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (isPlaying && content.durationMs > 0) {
                    "${formatDuration(content.positionMs)} de ${formatDuration(content.durationMs)}"
                } else if (isPlaying) {
                    "Tocando agora"
                } else {
                    "Retomar em ${formatDuration(content.positionMs)}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SpinningVinylArtwork(
    uri: Uri?,
    embeddedSourceUri: Uri?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var rotation by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val elapsedMs = (frame - lastFrame) / 1_000_000f
            lastFrame = frame
            rotation = (rotation + elapsedMs * 360f / 28_000f) % 360f
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(PailerCharcoal),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.96f)
                .rotate(rotation)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2B2D33),
                            Color(0xFF0F1014),
                            Color(0xFF050506),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(20.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .padding(46.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .padding(72.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(0.58f)
                    .clip(CircleShape),
            ) {
                ArtworkBox(
                    uri = uri,
                    embeddedSourceUri = embeddedSourceUri,
                    modifier = Modifier.fillMaxSize(),
                    iconModifier = Modifier.size(64.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PailerCharcoal)
                    .border(2.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            )
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
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleMute: () -> Unit,
    onExitRadio: () -> Unit,
    onDislike: () -> Unit,
) {
    val isRadio = player.activeRadioName.isNotBlank()
    val statusLabel = when {
        isRadio -> player.playbackSource.ifBlank { "Rádio" }
        player.isPlaying -> "Tocando agora"
        else -> player.playbackSource.ifBlank { "Continuar ouvindo" }
    }
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
                Text(
                    statusLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
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
            // So em modo radio (pedido do usuario 11/09/2026): faixa que caiu na radio mas nao e
            // musica de verdade pra ali (intro/outro/transicao) - troca na hora por outra do
            // mesmo album e nunca mais entra no shuffle de nenhuma radio (ver
            // dislikeCurrentRadioSong). Fica a esquerda do coracaozinho.
            if (isRadio) {
                IconButton(onClick = onDislike, enabled = player.songId != null) {
                    Icon(
                        Icons.Filled.ThumbDown,
                        contentDescription = "Nao curti essa faixa na radio",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite, enabled = player.songId != null) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Curtir faixa",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
            }
            // Modo radio: nao tem anterior/proxima (fila ao vivo nao suporta pular livremente,
            // ver ADR) e o play/pause vira mute (a radio "ao vivo" continua avancando, so
            // silencia - ver toggleRadioMute) + um botao pra sair de vez (power off).
            if (isRadio) {
                IconButton(onClick = onToggleMute) {
                    Icon(
                        if (player.isRadioMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (player.isRadioMuted) "Ativar som" else "Mutar",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = onExitRadio) {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        contentDescription = "Sair da radio",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
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

@OptIn(ExperimentalFoundationApi::class)
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
    lyrics: LyricsUiState = LyricsUiState(),
    onFetchLyrics: () -> Unit = {},
    onEditLyrics: () -> Unit = {},
    onRemoveLyrics: () -> Unit = {},
    albumSongs: List<LocalSong> = emptyList(),
    onOpenAlbum: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    isAlbumSongFavorite: (LocalSong) -> Boolean = { false },
    onToggleAlbumSongFavorite: (LocalSong) -> Unit = {},
    onPlayAlbumSong: (LocalSong) -> Unit = {},
) {
    val context = LocalContext.current
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
                IconButton(
                    onClick = { shareSong(context, player.artworkSourceUri, player.title) },
                    enabled = player.songId != null,
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Compartilhar musica",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (!isRadio) {
                    LyricsOverflowMenu(
                        hasLyrics = lyrics.lyrics.source != LyricsSource.NONE,
                        enabled = player.songId != null,
                        onEdit = onEditLyrics,
                        onFetch = onFetchLyrics,
                        onRemove = onRemoveLyrics,
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
            if (isRadio) {
                ArtworkBox(
                    uri = player.artworkUri,
                    embeddedSourceUri = player.artworkSourceUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    iconModifier = Modifier.size(84.dp),
                )
            } else {
                // Arrasta pro lado: pagina 0 = capa (chamada identica de ArtworkBox), pagina 1 = letra.
                // Travado na MESMA caixa fillMaxWidth().aspectRatio(1f) que a capa ocupava, entao
                // titulo/seek/controles/"Mais de <album>" ficam na posicao exata de sempre.
                val pagerState = rememberPagerState(pageCount = { 2 })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    if (page == 0) {
                        ArtworkBox(
                            uri = player.artworkUri,
                            embeddedSourceUri = player.artworkSourceUri,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            iconModifier = Modifier.size(84.dp),
                        )
                    } else {
                        LyricsPage(
                            lyrics = lyrics,
                            positionMs = player.positionMs,
                            onFetch = onFetchLyrics,
                            onEdit = onEditLyrics,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(PailerGunmetal.copy(alpha = 0.35f)),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                PagerDots(count = 2, selected = pagerState.currentPage)
            }
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
private fun LyricsOverflowMenu(
    hasLyrics: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onFetch: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Opções de letra",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (hasLyrics) "Editar letra" else "Colar letra") },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Buscar letra online") },
                onClick = { expanded = false; onFetch() },
            )
            if (hasLyrics) {
                DropdownMenuItem(
                    text = { Text("Remover letra") },
                    onClick = { expanded = false; onRemove() },
                )
            }
        }
    }
}

// Pagina de letra do player (arrasta pro lado a partir da capa). Estados: carregando / vazio
// (com botoes buscar/colar) / buscando / com letra sincronizada (destaca e rola a linha atual
// usando player.positionMs, que ja pulsa pelo polling existente - sem timer novo) / letra sem
// sincronia (texto rolavel).
@Composable
private fun LyricsPage(
    lyrics: LyricsUiState,
    positionMs: Long,
    onFetch: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            lyrics.isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

            lyrics.isFetching -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Buscando letra…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            lyrics.lyrics.isEmpty -> Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Sem letra para esta música",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                )
                lyrics.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFetch) { Text("Buscar online") }
                    OutlinedButton(onClick = onEdit) { Text("Colar letra") }
                }
            }

            else -> {
                val lines = lyrics.lyrics.lines
                val current = if (lyrics.lyrics.synced) LrcParser.currentLineIndex(lines, positionMs) else -1
                Column(Modifier.fillMaxSize()) {
                    if (lyrics.lyrics.synced) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(current) {
                            if (current >= 0) runCatching { listState.animateScrollToItem(current) }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            flingBehavior = rememberSoftFlingBehavior(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(lines) { index, line ->
                                Text(
                                    text = line.text.ifBlank { " " },
                                    color = if (index == current) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    },
                                    fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState(), flingBehavior = rememberSoftFlingBehavior())
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                        ) {
                            Text(
                                lyrics.lyrics.plainText,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (lyrics.lyrics.source) {
                                LyricsSource.EMBEDDED -> "Letra do arquivo"
                                LyricsSource.MANUAL -> "Colada por você"
                                LyricsSource.LRCLIB -> "LRCLIB"
                                LyricsSource.NONE -> ""
                            },
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Editar letra",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}

@Composable
private fun LyricsEditorDialog(
    draft: String,
    startedEmpty: Boolean,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        title = { Text("Letra da música") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                minLines = 8,
                placeholder = { Text("Cole a letra aqui (aceita formato .lrc com tempos).") },
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (!startedEmpty) {
                    TextButton(onClick = onRemove) {
                        Text("Remover", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
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
    // Valor inicial busca o cache em memoria NA HORA (sincrono, sem coroutine) - se a capa ja foi
    // decodificada antes (ex.: voltando pra uma tela ja visitada), aparece na primeira composicao
    // sem o "pisca" do placeholder enquanto o LaunchedEffect da volta ate a thread de IO so pra
    // achar o mesmo bitmap que ja estava em memoria.
    var image by remember(uri, embeddedSourceUri) {
        mutableStateOf(peekCachedArtwork(uri, embeddedSourceUri))
    }

    LaunchedEffect(uri, embeddedSourceUri) {
        if (image == null) {
            image = withContext(Dispatchers.IO) {
                embeddedSourceUri?.let { loadEmbeddedArtwork(context, it) }
                    ?: uri?.let { loadScaledArtwork(context, it) }
            }
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

// Checagem sincrona do cache (sem I/O) - ver uso em ArtworkBox.
private fun peekCachedArtwork(uri: Uri?, embeddedSourceUri: Uri?): androidx.compose.ui.graphics.ImageBitmap? {
    embeddedSourceUri?.let { artworkMemoryCache.get("embedded:$it") }?.let { return it }
    uri?.let { artworkMemoryCache.get(it.toString()) }?.let { return it }
    return null
}

// 96 entradas era pouco pra uma biblioteca de verdade (bibliotecas com centenas de albuns
// estouravam o cache so navegando entre Artistas/Albuns, forcando redecodificar a mesma capa
// de novo a cada visita) - pedido do usuario (07/09/2026): capas recarregando toda vez que volta
// pra uma tela ja visitada. Cada entrada e no maximo ~200KB (RGB_565, ate 320x320 - ver
// ARTWORK_DECODE_TARGET_PX), entao 400 entradas ficam bem abaixo do limite de heap tipico.
private val artworkMemoryCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(400)

// Fundo personalizado da tela de album/artista: a propria capa, bem reduzida e desfocada,
// atras do conteudo. Decodifica num tamanho minusculo (BACKDROP_DECODE_TARGET_PX) de proposito -
// o upscale ja produz um efeito suave por si so, e sobra pouco trabalho pra Modifier.blur() (que
// so tem efeito real em API 31+; abaixo disso o proprio bitmap pequeno ja resolve visualmente).
private val backdropArtworkCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24)
private const val BACKDROP_DECODE_TARGET_PX = 48

private fun loadBackdropArtwork(context: android.content.Context, uri: Uri): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val cacheKey = uri.toString()
        backdropArtworkCache.get(cacheKey)?.let { return@runCatching it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateBackdropSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
        if (bitmap != null) backdropArtworkCache.put(cacheKey, bitmap)
        bitmap
    }.getOrNull()

private fun loadEmbeddedBackdropArtwork(context: android.content.Context, contentUri: Uri): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val cacheKey = "embedded:$contentUri"
        backdropArtworkCache.get(cacheKey)?.let { return@runCatching it }
        val retriever = android.media.MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(context, contentUri)
            val bytes = retriever.embeddedPicture ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateBackdropSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } finally {
            retriever.release()
        }
        if (bitmap != null) backdropArtworkCache.put(cacheKey, bitmap)
        bitmap
    }.getOrNull()

private fun calculateBackdropSampleSize(width: Int, height: Int): Int {
    if (width <= BACKDROP_DECODE_TARGET_PX && height <= BACKDROP_DECODE_TARGET_PX) return 1
    var sampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while ((halfWidth / sampleSize) >= BACKDROP_DECODE_TARGET_PX && (halfHeight / sampleSize) >= BACKDROP_DECODE_TARGET_PX) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

// Camada de fundo personalizada por album/artista: a capa em si, super reduzida, desfocada e com
// baixa opacidade sobre a cor de fundo do tema - uma "marca dagua" leve que da identidade a tela
// sem prejudicar a leitura do conteudo por cima. Colocar como primeiro filho do Box da tela (antes
// da lista), pra tudo o mais desenhar por cima.
@Composable
private fun AlbumArtBackdrop(
    uri: Uri?,
    embeddedSourceUri: Uri? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var image by remember(uri, embeddedSourceUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(uri, embeddedSourceUri) {
        image = withContext(Dispatchers.IO) {
            embeddedSourceUri?.let { loadEmbeddedBackdropArtwork(context, it) }
                ?: uri?.let { loadBackdropArtwork(context, it) }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.24f,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier,
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor.copy(alpha = 0.55f)),
            )
        }
    }
}

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

private fun MainTab.icon() = when (this) {
    MainTab.Home -> Icons.Filled.Home
    MainTab.Radio -> Icons.Filled.Radio // nao usado de fato (Radio ganha selo circular custom
        // no NavigationBar acima), so aqui pra when ficar exaustivo.
    MainTab.Library -> Icons.Filled.LibraryMusic
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

private val newsDateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

// card.publishedAtMillis vem do <pubDate> do RSS (ver ArtistNewsRepository.parsePubDate) - 0
// quando o item nao trouxe data, exibida como "" pra ArtistNewsCardView simplesmente omitir a
// linha.
private fun formatNewsDate(publishedAtMillis: Long): String {
    if (publishedAtMillis <= 0L) return ""
    return runCatching {
        java.time.Instant.ofEpochMilli(publishedAtMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(newsDateFormatter)
    }.getOrDefault("")
}
