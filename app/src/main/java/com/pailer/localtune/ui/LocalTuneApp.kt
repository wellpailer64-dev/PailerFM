package com.pailer.localtune.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.LruCache
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cast
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.pailer.localtune.BuildConfig
import com.pailer.localtune.R
import com.pailer.localtune.data.AlbumMetadataEdit
import com.pailer.localtune.data.ArtistNewsCard
import com.pailer.localtune.data.DuplicateArtistGroup
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
import com.pailer.localtune.player.RemoteDeviceEntry
import com.pailer.localtune.player.RemoteDeviceUiState
import com.pailer.localtune.player.LyricsUiState
import com.pailer.localtune.player.AlbumArtworkUiState
import com.pailer.localtune.player.ArtistPhotoUiState
import com.pailer.localtune.player.ArtistNewsUiState
import com.pailer.localtune.player.AppFolderUiState
import com.pailer.localtune.player.BackupUiState
import com.pailer.localtune.player.MetadataUiState
import com.pailer.localtune.player.PlayerUiState
import com.pailer.localtune.player.RadioAmbience
import com.pailer.localtune.player.UpcomingTrack
import com.pailer.localtune.player.RadioBulletinUiState
import com.pailer.localtune.player.ListeningStatsUiState
import com.pailer.localtune.player.UpdateUiState
import com.pailer.localtune.player.UserProfileUiState
import com.pailer.localtune.ui.theme.LocalTuneTheme
import com.pailer.localtune.ui.theme.PailerCharcoal
import com.pailer.localtune.ui.theme.PailerGunmetal
import com.pailer.localtune.ui.theme.PailerRed
import com.pailer.localtune.ui.theme.PailerSilver
import com.pailer.localtune.ui.theme.PailerSurface
import com.pailer.localtune.ui.theme.PailerSurfaceHigh
import com.pailer.localtune.ui.theme.PailerSurfaceHighest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    Profile,
    RadioSettings,
    LibraryMaintenance,
    Metadata,
    AlbumArtists,
    TagWriter,
    Artists,
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

        // Primeiro uso: boas-vindas (nome/foto/aniversario) antes de pedir permissao - ver
        // OnboardingFlow. Se a permissao ja estiver concedida (ex.: app atualizado), a biblioteca
        // ja vai carregando por baixo enquanto o usuario preenche.
        if (viewModel.needsOnboarding.value) {
            OnboardingFlow(viewModel)
        } else if (hasAudioPermission) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryShell(viewModel: LocalTuneViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var librarySection by rememberSaveable { mutableStateOf(LibrarySection.Artists) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var showTrackEditor by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    var selectedAlbum by remember { mutableStateOf<LocalAlbum?>(null) }
    var selectedArtist by remember { mutableStateOf<LocalArtist?>(null) }
    // rememberSaveable (nao remember simples) de proposito - pedido do usuario 17/09/2026: girar
    // a tela recria a Activity (sem android:configChanges no manifest), e um remember comum
    // reseta pra null nessa recriacao mesmo com uma radio ainda tocando (o ViewModel sobrevive,
    // so o estado local desta composable que se perde) - a tela caia de volta na lista de
    // "outras radios" na vertical. Salva so o NOME (String, serializavel de verdade no Bundle) e
    // resolve pro LocalRadio de verdade contra viewModel.libraryContentState (nao contra o `radios`
    // local mais abaixo - o Saver roda na primeira composicao, antes daquele val existir; o
    // ViewModel em si sobrevive rotacao, entao esse state ja vem populado mesmo recem-recriado).
    val selectedRadioSaver = remember {
        Saver<LocalRadio?, String>(
            save = { it?.name ?: "" },
            restore = { name ->
                name.takeIf { it.isNotEmpty() }
                    ?.let { savedName -> viewModel.libraryContentState.value.radios.firstOrNull { it.name == savedName } }
            },
        )
    }
    var selectedRadio by rememberSaveable(stateSaver = selectedRadioSaver) { mutableStateOf<LocalRadio?>(null) }
    var selectedGenre by remember { mutableStateOf<LocalRadio?>(null) }
    // Mini player some sozinho depois de 5s sem nenhum toque na tela e volta a aparecer no
    // proximo toque (pedido do usuario 15/09/2026, imitando players tipo Youtube Music) - so a
    // barra do mini player, a navegacao inferior (Inicio/Radio/Biblioteca) fica sempre visivel.
    // lastInteractionAt e atualizado pelo pointerInput global no Box raiz mais abaixo.
    var miniPlayerVisible by remember { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableStateOf(0L) }
    // Estados de scroll hoistados aqui (fora das telas de lista/grade) pra sobreviver a
    // navegacao entre abas e telas de detalhe - sem isso, cada `when` troca de branch destroi
    // e recria o LazyVerticalGrid/LazyColumn da tela anterior, voltando pro topo sempre que o
    // usuario aperta "voltar". As telas de detalhe (artista/album/genero/radio) usam a chave
    // da entidade aberta pra resetar o scroll quando o usuario abre uma entidade DIFERENTE,
    // mas preservar quando volta pra mesma (ex.: abriu um album de dentro do artista e voltou).
    val artistsScrollState = rememberLazyGridState()
    val albumsScrollState = rememberLazyGridState()
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
    // Popover de 3 pontinhos da aba Radio (pedido do usuario 22/09/2026: "faz um botão de 3
    // pontinhos no topo da tela canto direito, ao clicar, abre um pop over (criar nova rádio) e
    // radio settings") - ver RadioMenuButton/AddSourceToRadioDialog(title = "Criar nova rádio")
    // mais abaixo. "Radio Settings" reusa o MESMO SettingsDrawer/RadioSettingsPanel de
    // Configuracoes (so um atalho direto pra la, nao uma tela duplicada).
    var showCreateRadioDialog by remember { mutableStateOf(false) }
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
    // Genero/ano descobertos sozinhos (ver maybeAutoTagAlbumMetadata no ViewModel) disparam o
    // mesmo pedido de permissao acima sem precisar passar por Configuracoes > Tags pendentes -
    // pedido explicito do usuario 15/09/2026, mesmo sabendo que o dialogo do Android vai aparecer
    // sozinho de vez em quando (regra de escrita em arquivo de terceiros, nao da pra evitar).
    LaunchedEffect(viewModel.autoTagWriteRequestedVersion.value) {
        if (viewModel.autoTagWriteRequestedVersion.value > 0) {
            requestRecentMetadataEditWrite()
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
    val profilePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importProfilePhoto)
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
    // Oferece restaurar backup na primeira vez que a biblioteca carrega vazia de
    // favoritos/historico (ver maybeOfferFreshRestore) - so dispara 1 vez por instalacao.
    LaunchedEffect(library.hasLoaded) {
        if (library.hasLoaded) viewModel.maybeOfferFreshRestore()
    }

    // Auto-atualizacao fora da Play Store (pedido do usuario 15/09/2026, ver
    // UpdateCheckRepository) - checa 1 vez ao abrir o app; quando o download termina, abre o
    // instalador sozinho (ver installApkUpdate abaixo) em vez de so oferecer um botao "Instalar".
    val updateContext = LocalContext.current
    val updateState = viewModel.updateState.value
    LaunchedEffect(Unit) { viewModel.checkForUpdate() }
    LaunchedEffect(updateState.downloadedApkFile) {
        updateState.downloadedApkFile?.let { installApkUpdate(updateContext, it) }
    }
    LaunchedEffect(updateState.manualCheckMessage) {
        updateState.manualCheckMessage?.let {
            Toast.makeText(updateContext, it, Toast.LENGTH_LONG).show()
            viewModel.consumeManualCheckMessage()
        }
    }
    if (updateState.latestRelease != null && !updateState.dismissed) {
        UpdateAvailableDialog(
            state = updateState,
            onDownload = viewModel::downloadUpdate,
            onDismiss = viewModel::dismissUpdatePrompt,
        )
    }
    if (viewModel.showFreshRestorePrompt.value) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFreshRestorePrompt,
            icon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
            title = { Text("Já tem um backup?") },
            text = {
                Text(
                    "Se você já usou o Pailer FM antes (favoritos, histórico, correções de " +
                        "álbum/artista, fotos), pode restaurar tudo agora de um arquivo de backup " +
                        "antes de continuar.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissFreshRestorePrompt()
                    backupRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                }) {
                    Text("Restaurar backup")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFreshRestorePrompt) {
                    Text("Começar do zero")
                }
            },
        )
    }
    val content = viewModel.libraryContentState.value
    val player = viewModel.playerState.value
    // Reaparece na hora quando uma midia comeca a tocar (hasMedia false->true) e reinicia a
    // contagem de 5s a cada novo toque (lastInteractionAt) - ver Box raiz mais abaixo pro
    // detector de toque global e AnimatedVisibility no MiniPlayer.
    LaunchedEffect(lastInteractionAt, player.hasMedia) {
        if (!player.hasMedia) return@LaunchedEffect
        miniPlayerVisible = true
        delay(5_000)
        miniPlayerVisible = false
    }
    val lyrics = viewModel.lyricsState.value
    LaunchedEffect(player.activeRadioName, player.songId, lyrics.songId, lyrics.isLoading, lyrics.lyrics.synced, lyrics.message) {
        if (player.activeRadioName.isNotBlank() &&
            player.songId != null &&
            lyrics.songId == player.songId &&
            !lyrics.isLoading &&
            !lyrics.isFetching &&
            !lyrics.lyrics.synced &&
            lyrics.message == null
        ) {
            viewModel.autoUpgradeLyricsSyncIfNeeded(player.songId)
        }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val landscapeRailWidth = 64.dp
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
    val profile = viewModel.profileState.value
    LaunchedEffect(profile.appliedVersion) {
        if (profile.appliedVersion > 0) artworkMemoryCache.evictAll()
    }
    val radioBulletins = viewModel.radioBulletinState.value
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
    var showNewsDrawer by rememberSaveable { mutableStateOf(false) }
    var lastSeenNewsLoadedAtMillis by rememberSaveable { mutableStateOf(0L) }
    val hasNewArtistNews = artistNews.hasLoaded &&
        artistNews.cards.isNotEmpty() &&
        artistNews.loadedAtMillis > lastSeenNewsLoadedAtMillis
    LaunchedEffect(showNewsDrawer, artistNews.loadedAtMillis) {
        if (showNewsDrawer && artistNews.hasLoaded) {
            lastSeenNewsLoadedAtMillis = artistNews.loadedAtMillis
        }
    }
    LaunchedEffect(player.songId, songs.size) {
        viewModel.loadLyricsFor(player.songId)
    }
    val homeBackdropSong = remember(player.songId, player.activeRadioName, player.hasMedia, continueSong?.id, songs) {
        if (player.hasMedia && player.activeRadioName.isBlank()) {
            player.songId?.let { id -> songs.firstOrNull { it.id == id } }
        } else {
            continueSong
        }
    }
    val homeBackdropUri = when {
        player.hasMedia && player.activeRadioName.isBlank() -> player.artworkUri ?: homeBackdropSong?.artworkUri
        else -> continueSong?.artworkUri
    }
    val homeBackdropSourceUri = when {
        player.hasMedia && player.activeRadioName.isBlank() -> player.artworkSourceUri ?: homeBackdropSong?.contentUri
        else -> continueSong?.contentUri
    }
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
    // "Mais desse artista" no fim da tela de album (pedido do usuario 15/09/2026) - so pra
    // album de um unico artista (isVariousArtists=false), buscando pelas faixas com o mesmo
    // artist EXATO (album.artist ja e o valor cru da tag quando nao e various-artists, sem
    // precisar da normalizacao de primaryArtistKey que so existe dentro do repository).
    val openedAlbumOtherAlbums = remember(selectedAlbum) {
        val current = selectedAlbum
        if (current == null || current.isVariousArtists) {
            emptyList()
        } else {
            viewModel.albums(songs.filter { it.artist == current.artist }).filterNot { it.key == current.key }
        }
    }
    val openedGenreArtists = remember(selectedGenre) {
        selectedGenre?.let { viewModel.artists(it.songs) } ?: emptyList()
    }
    val canHandleBack = showFullPlayer ||
        showSettings ||
        showNewsDrawer ||
        selectedAlbum != null ||
        selectedArtist != null ||
        selectedRadio != null ||
        selectedGenre != null ||
        library.query.isNotBlank() ||
        selectedTab != MainTab.Home

    BackHandler(enabled = canHandleBack) {
        when {
            showTrackEditor -> showTrackEditor = false
            showFullPlayer -> showFullPlayer = false
            showSettings -> {
                when (settingsPage) {
                    SettingsPage.Main -> showSettings = false
                    SettingsPage.Profile -> settingsPage = SettingsPage.Main
                    SettingsPage.Metadata,
                    SettingsPage.AlbumArtists,
                    SettingsPage.TagWriter,
                    SettingsPage.Artists,
                    SettingsPage.AlbumArtwork -> settingsPage = SettingsPage.LibraryMaintenance
                    else -> settingsPage = SettingsPage.Main
                }
            }
            showNewsDrawer -> showNewsDrawer = false
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
            // Detector de toque global pro auto-hide do mini player (ver miniPlayerVisible acima)
            // - Initial e sem consumir o evento, entao nao atrapalha nenhum clique/scroll normal
            // da tela, so observa que um toque aconteceu em qualquer lugar.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // So conta toque de verdade (pressed) - sem esse filtro, eventos de
                        // hover/proximidade sem nenhum dedo na tela (achado ao vivo 15/09/2026)
                        // reiniciavam a contagem sem parar e o mini player nunca sumia sozinho.
                        if (event.changes.any { it.pressed }) {
                            lastInteractionAt = System.currentTimeMillis()
                        }
                    }
                }
            }
    ) {
        BackgroundGrainOverlay()
        val showHomeBackdrop = selectedTab == MainTab.Home &&
            selectedAlbum == null &&
            selectedArtist == null &&
            selectedRadio == null &&
            selectedGenre == null
        if (showHomeBackdrop) {
            AlbumArtBackdrop(uri = homeBackdropUri, embeddedSourceUri = homeBackdropSourceUri)
        }
        val isRadioActive = player.activeRadioName.isNotBlank() && player.hasMedia
        val isInsideActiveRadio = isRadioActive && (selectedTab == MainTab.Radio || selectedRadio != null)
        // "Modo cinema" da tela da radio (pedido do usuario 18/09/2026): barra de progresso some
        // e o header/navbar do proprio app ficam pretos depois de um tempo parado, tocar na tela
        // volta tudo ao normal. So ativo dentro da tela da radio com sessao rolando (ver
        // RadioDetailScreen.isInSession + LiveNowRadioCard(edgeToEdge = true) mais abaixo) -
        // hoisted aqui porque header e navbar sao irmaos da tela da radio, nao filhos dela.
        val radioCinematicIdle = rememberRadioCinematicIdleState(enabled = isInsideActiveRadio)
        // Tela sempre acesa + barras do sistema escondidas enquanto o usuario esta DENTRO de uma
        // radio tocando (pedido do usuario 20/09/2026) - mesma condicao do modo cinema acima.
        RadioImmersiveModeEffect(active = isInsideActiveRadio)
        // Video mudo de fundo + orquestrador de cenas da radio (ver comentario em
        // RadioAlbumMockupScene) - hoisted AQUI, e nao mais dentro de cada lugar que mostra a
        // cena, pelo mesmo motivo do modo cinema/immersive acima: header/navbar/FullPlayer sao
        // irmaos das telas que mostram a radio, nao filhos delas, e essa Box raiz e o unico ponto
        // que sobrevive a troca de aba (Home <-> Radio), abrir/fechar o FullPlayer, e o app ir pra
        // segundo plano e voltar (contanto que o processo nao morra) - exatamente as 3 situacoes
        // relatadas pelo usuario 22/09/2026 em que a cena reiniciava sozinha (sempre voltando pra
        // animacao "trocando disco" no meio da musica) e a transicao de fade/sincronia quebrava.
        // if/else (nao um remember incondicional) de proposito: enquanto isRadioActive for false
        // nem existe ExoPlayer nenhum rodando (sem gastar bateria/CPU a toa fora de uma sessao de
        // radio), e QUANDO uma sessao nova comeca de verdade (radio desligada -> ligada) o Compose
        // descarta o remember antigo e cria um objeto novo do zero - reset correto (nova sessao
        // pede uma nova animacao de abertura), diferente do reset ESPURIO que motivou essa mudanca
        // (mera navegacao entre telas com a MESMA sessao ainda tocando).
        val radioMockup = if (isRadioActive) {
            val radioMockupNearEnd = player.durationMs > 0 &&
                (player.durationMs - player.positionMs) in 0..RadioEndingCutoverMs
            val radioMockupDiscSwapImminent = player.durationMs > 0 &&
                (player.durationMs - player.positionMs) in 0..RadioDiscSwapLeadMs
            val radioMockupChorusWindows = remember(lyrics.lyrics) {
                if (lyrics.lyrics.synced) LrcParser.chorusWindows(lyrics.lyrics.lines) else emptyList()
            }
            val radioMockupChorusWindowRange = radioMockupChorusWindows.firstOrNull { player.positionMs in it }
            val radioMockupScrim = rememberRadioTransitionScrimState()
            val radioMockupSequencer = rememberRadioMockupSequencer(
                songId = player.songId,
                nearEnd = radioMockupNearEnd,
                discSwapImminent = radioMockupDiscSwapImminent,
                isBulletinPlaying = player.currentNewsHeadline.isNotBlank(),
                bulletinBreakUpcoming = player.bulletinBreakUpcoming,
                hasProfilePhoto = player.profilePhotoUri != null,
                chorusWindowRange = radioMockupChorusWindowRange,
                scrim = radioMockupScrim,
            )
            RadioMockupSharedState(radioMockupSequencer, radioMockupScrim)
        } else {
            null
        }
        val selectMainTab: (MainTab) -> Unit = { tab ->
            selectedAlbum = null
            selectedArtist = null
            // radioSession NAO e limpa aqui de proposito - trocar de aba so fecha a tela de
            // detalhe, a radio pode continuar tocando em segundo plano e o usuario pode voltar
            // pra ela clicando no card "ao vivo" (ver openActiveRadio) OU clicando de novo no
            // botao da radio abaixo.
            //
            // Pedido do usuario 17/09/2026: com uma radio ativa tocando, sair pra Inicio e depois
            // clicar no botao do meio (radio) tem que voltar PRA DENTRO da radio que esta tocando,
            // nao pra lista de outras radios - a lista so aparece se o usuario sair da radio atual
            // de verdade (stopRadio). Mesmo trecho de busca que openActiveRadio usa mais abaixo.
            selectedRadio = if (tab == MainTab.Radio) {
                radios.firstOrNull { it.name == player.activeRadioName }
            } else {
                null
            }
            selectedGenre = null
            isGeneratingRadio = false
            selectedTab = tab
        }
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column(
                    modifier = Modifier.padding(end = if (isLandscape) landscapeRailWidth else 0.dp),
                ) {
                    if (player.hasMedia && !isInsideActiveRadio) {
                        AnimatedVisibility(
                            visible = miniPlayerVisible,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        ) {
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
                    }
                    if (!isLandscape) {
                        BottomMainNavigationBar(
                            selectedTab = selectedTab,
                            onSelectTab = selectMainTab,
                            dimmed = radioCinematicIdle.dimSystemBars,
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = if (isLandscape) landscapeRailWidth else 0.dp),
                ) {
                LibraryHeader(
                    query = library.query,
                    isLoading = library.isLoading,
                    onQueryChange = viewModel::setQuery,
                    onSettings = {
                        settingsPage = SettingsPage.Main
                        showSettings = true
                    },
                    onNews = {
                        showNewsDrawer = true
                        viewModel.loadArtistNewsIfNeeded(favoriteArtists)
                    },
                    hasNewNews = hasNewArtistNews,
                    remoteDeviceState = viewModel.remoteDeviceState.value,
                    onScanRemote = viewModel::scanForRemoteDevices,
                    onStopScanRemote = viewModel::stopScanningRemoteDevices,
                    onConnectRemote = viewModel::connectToRemoteDevice,
                    onDisconnectRemote = viewModel::disconnectRemote,
                    // Busca so fica na Biblioteca. A radio e uma tela de experiencia/controle,
                    // sem lista textual pra filtrar ali.
                    showSearch = selectedTab == MainTab.Library,
                    dimmed = radioCinematicIdle.dimSystemBars,
                    extraAction = if (selectedTab == MainTab.Radio) {
                        {
                            RadioMenuButton(
                                onCreateRadio = { showCreateRadioDialog = true },
                                onOpenRadioSettings = {
                                    settingsPage = SettingsPage.RadioSettings
                                    showSettings = true
                                },
                            )
                        }
                    } else {
                        null
                    },
                )

                val openedAlbum = selectedAlbum
                // Mantem a tela do album aberta em dia com genero/ano recem-descobertos pela
                // busca automatica (ver AlbumDetailScreen.onAutoFetchMetadata) - sem isso,
                // approveAlbumGenre/approveAlbumYear atualizam `albums` mas o `openedAlbum`
                // continua apontando pro snapshot antigo ate o usuario sair e voltar na tela.
                LaunchedEffect(albums, openedAlbum?.key) {
                    val current = openedAlbum ?: return@LaunchedEffect
                    albums.firstOrNull { it.key == current.key }
                        ?.takeIf { it != current }
                        ?.let { selectedAlbum = it }
                }
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
                        onSaveMetadata = { title, artistName, genre, year ->
                            selectedAlbum = viewModel.saveAlbumMetadataEdit(openedAlbum, title, artistName, genre, year)
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
                        otherArtistAlbums = openedAlbumOtherAlbums,
                        onOpenAlbum = { selectedAlbum = it },
                        onAutoFetchMetadata = { viewModel.maybeAutoTagAlbumMetadata(openedAlbum) },
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
                        onAutoFetchPhoto = { viewModel.maybeAutoFetchArtistPhoto(openedArtist) },
                        listState = artistDetailListState,
                    )
                    openedRadio != null -> {
                    // Pedido do usuario 17/09/2026: saiu do app (ou girou a tela), quando volta a
                    // radio ativa aparece so com o card "ao vivo" e o bloco embaixo vazio - o
                    // estado local radioSession (Compose remember, nao sobrevive a Activity
                    // recriada) fica vazio mas a radio continua tocando de verdade no controller.
                    // Em vez de deixar vazio ou gerar sessao nova (reiniciaria a musica atual),
                    // reconstroi a lista a partir da fila real (ver currentRadioQueueSongs).
                    LaunchedEffect(openedRadio.name, player.activeRadioName, player.hasMedia) {
                        val isActiveSession = player.activeRadioName == openedRadio.name && player.hasMedia
                        if (isActiveSession && radioSession.isEmpty()) {
                            val recovered = viewModel.currentRadioQueueSongs()
                            if (recovered.isNotEmpty()) radioSession = recovered
                        }
                    }
                    // Fontes extras (adicionadas via "+") da radio aberta, resolvidas contra as
                    // listas artists/albums ja carregadas - recalculado a cada recomposicao (nao
                    // memoizado), entao some/aparece sozinho ao adicionar/remover fonte.
                    val openedRadioExtraSourceIds = viewModel.radioExtraSourceIds(openedRadio)
                    val openedRadioExtraArtists = artists.filter { "artist:${it.key}" in openedRadioExtraSourceIds }
                    val openedRadioExtraAlbums = albums.filter { "album:${it.id}" in openedRadioExtraSourceIds }
                    RadioDetailScreen(
                        radio = openedRadio,
                        player = player,
                        radioMockup = radioMockup,
                        radioVideoActive = !showFullPlayer,
                        lyrics = lyrics,
                        sessionSongs = radioSession,
                        songsBetweenBulletins = radioBulletins.settings.songsBetweenBulletins,
                        isGenerating = isGeneratingRadio,
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
                        onOpenPlayer = { showFullPlayer = true },
                        isFavorite = viewModel.isCurrentSongFavorite(),
                        onToggleFavorite = viewModel::toggleCurrentSongFavorite,
                        onToggleMute = viewModel::toggleRadioMute,
                        onToggleAmbience = viewModel::toggleAmbience,
                        onAmbienceVolumeChange = viewModel::setAmbienceVolume,
                        onExitRadio = {
                            viewModel.stopRadio()
                            radioSession = emptyList()
                            isGeneratingRadio = false
                        },
                        onDislike = {
                            viewModel.dislikeCurrentRadioSong()?.let { result ->
                                if (result.index in radioSession.indices) {
                                    radioSession = radioSession.toMutableList()
                                        .apply { this[result.index] = result.replacement }
                                }
                            }
                        },
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
                        cinematicIdle = radioCinematicIdle,
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
                    selectedTab == MainTab.Home -> {
                        HomeScreen(
                            songs = songs,
                            continueSong = continueSong,
                            continuePositionMs = viewModel.continuePositionMs(),
                            recentlyAddedAlbums = recentlyAddedAlbums,
                            listenAgain = listenAgain,
                            suggestedAlbums = suggestedAlbums,
                            favoriteAlbums = favoriteAlbums,
                            radios = radios,
                            artists = artists,
                            player = player,
                            radioMockup = radioMockup,
                            radioVideoActive = !showFullPlayer,
                            lyrics = lyrics,
                            onContinue = { viewModel.continuePlayback(songs) },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onAutoUpgradeLyrics = { player.songId?.let(viewModel::autoUpgradeLyricsSyncIfNeeded) },
                            onOpenAlbum = { selectedAlbum = it },
                            onOpenRadio = {
                                radioSession = emptyList()
                                selectedRadio = it
                            },
                            onOpenCurrentPlayer = { showFullPlayer = true },
                            onOpenPlayer = openActiveRadio,
                            onPlay = { list, index, shuffle -> viewModel.playSongs(list, index, shuffle, source = if (shuffle) "Misturar tudo" else "Biblioteca") },
                            onToggleAutoplay = viewModel::toggleAutoplay,
                            artistPhotoUriFor = viewModel::artistPhotoUri,
                            onOpenArtist = { selectedArtist = it },
                        )
                    }
                    selectedTab == MainTab.Radio -> PlaylistsScreen(
                        radios = radios,
                        player = player,
                        radioMockup = radioMockup,
                        radioVideoActive = !showFullPlayer,
                        lyrics = lyrics,
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
                        // Navegacao por gesto entre as sessoes da biblioteca (arrastar pro lado
                        // avanca/volta) - pedido do usuario 15/09/2026. HorizontalPager ja cuida
                        // da animacao leve e mantem sincronia nos 2 sentidos com os botoes acima.
                        val libraryPagerState = rememberPagerState(
                            initialPage = librarySection.ordinal,
                            pageCount = { LibrarySection.entries.size },
                        )
                        LaunchedEffect(libraryPagerState.currentPage) {
                            librarySection = LibrarySection.entries[libraryPagerState.currentPage]
                        }
                        LaunchedEffect(librarySection) {
                            if (libraryPagerState.currentPage != librarySection.ordinal) {
                                libraryPagerState.animateScrollToPage(librarySection.ordinal)
                            }
                        }
                        HorizontalPager(
                            state = libraryPagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            when (LibrarySection.entries[page]) {
                                LibrarySection.Artists -> ArtistsScreen(
                                    artists = artists,
                                    onOpenArtist = { selectedArtist = it },
                                    onLongPressArtist = { artistActionsTarget = it },
                                    photoUriFor = viewModel::artistPhotoUri,
                                    scrollState = artistsScrollState,
                                )
                                LibrarySection.Albums -> AlbumsScreen(
                                    albums = albums,
                                    onOpenAlbum = { selectedAlbum = it },
                                    onLongPressAlbum = { albumActionsTarget = it },
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
                                    radioMockup = radioMockup,
                                    lyrics = lyrics,
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
                if (isLandscape) {
                    LandscapeMainNavigationRail(
                        selectedTab = selectedTab,
                        onSelectTab = selectMainTab,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(landscapeRailWidth),
                    )
                }
            }
        }

        val activityContext = LocalContext.current
        ArtistNewsDrawer(
            visible = showNewsDrawer,
            favoriteArtists = favoriteArtists,
            newsState = artistNews,
            onRequestLoad = { viewModel.loadArtistNewsIfNeeded(favoriteArtists) },
            onOpenLink = onOpenNewsLink,
            onClose = { showNewsDrawer = false },
        )
        SettingsDrawer(
            visible = showSettings,
            page = settingsPage,
            metadata = metadata,
            albumArtwork = albumArtwork,
            profile = profile,
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
            onChooseProfilePhoto = { profilePhotoLauncher.launch(arrayOf("image/*")) },
            onSaveProfile = viewModel::saveUserProfile,
            hasHiddenRadios = viewModel.hasHiddenRadios(),
            onRestoreHiddenRadios = viewModel::restoreHiddenRadios,
            hasHiddenLibraryItems = viewModel.hasHiddenArtistsOrAlbums(),
            onRestoreHiddenLibraryItems = viewModel::restoreHiddenArtistsAndAlbums,
            radioDislikedSongs = viewModel.radioDislikedSongs(),
            onUndislikeSong = viewModel::undislikeSongForRadio,
            updateState = updateState,
            onCheckForUpdate = viewModel::checkForUpdateManually,
            listeningStats = viewModel.listeningStatsState.value,
            backup = viewModel.backupState.value,
            onChooseBackupDestination = { backupCreateLauncher.launch("pailer_fm_backup.json") },
            onBackupNow = viewModel::performBackupNow,
            onClearBackupDestination = viewModel::clearBackupDestination,
            onRestoreBackup = { backupRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
            appFolder = viewModel.appFolderState.value,
            onChooseAppFolder = { appFolderLauncher.launch(null) },
            onClearAppFolder = viewModel::clearAppFolder,
            songsBetweenBulletins = radioBulletins.settings.songsBetweenBulletins,
            onChangeSongsBetweenBulletins = viewModel::updateSongsBetweenBulletins,
        )

        // Pedido do usuario 22/09/2026 (popover de 3 pontinhos na aba Radio, ver RadioMenuButton/
        // LibraryHeader.extraAction acima) - reusa o MESMO dialogo de escolher artista/album
        // usado dentro de uma radio ja existente (AddSourceToRadioDialog), so com titulo
        // diferente e criando uma radio NOVA em vez de somar fonte numa que ja existe.
        if (showCreateRadioDialog) {
            AddSourceToRadioDialog(
                title = "Criar nova rádio",
                artists = artists,
                albums = albums,
                onPickArtist = { artist ->
                    showCreateRadioDialog = false
                    viewModel.createRadioFromArtist(artist)
                },
                onPickAlbum = { album ->
                    showCreateRadioDialog = false
                    viewModel.createRadioFromAlbum(album)
                },
                onDismiss = { showCreateRadioDialog = false },
            )
        }

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
            val playingSong = remember(player.songId, songs) {
                songs.firstOrNull { it.id == player.songId }
            }
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
                radioMockup = radioMockup,
                lyrics = lyrics,
                onFetchLyrics = { player.songId?.let(viewModel::fetchLyricsOnline) },
                onAutoUpgradeLyrics = { player.songId?.let(viewModel::autoUpgradeLyricsSyncIfNeeded) },
                onEditLyrics = viewModel::openLyricsEditor,
                onRemoveLyrics = { player.songId?.let(viewModel::removeLyrics) },
                onEditTrack = { showTrackEditor = true },
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
            if (showTrackEditor && playingSong != null) {
                TrackMetadataEditorOverlay(
                    song = playingSong,
                    availableGenres = availableGenres,
                    onCancel = { showTrackEditor = false },
                    onSave = { title, artistName, album, genre, year ->
                        viewModel.saveTrackMetadataEdit(playingSong, title, artistName, album, genre, year)
                        requestRecentMetadataEditWrite()
                        showTrackEditor = false
                    },
                )
            }
        }

        // Timer de inatividade da radio (pedido do usuario 15/09/2026, ver ADR-027) - sem
        // onDismissRequest de proposito, so sai com o botao "Sim" (toque fora nao conta como
        // "ainda estou aqui"); sem resposta em 1min a radio desliga sozinha.
        if (player.sleepCheckPending) {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null) },
                title = { Text("Você ainda está aí?") },
                text = { Text("A rádio vai desligar automaticamente em 1 minuto se você não responder.") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmStillListening) {
                        Text("Sim")
                    }
                },
            )
        }
    }
}

@Composable
private fun BottomMainNavigationBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    // "Modo cinema" da radio ativa (pedido do usuario 18/09/2026): fica preto depois de um tempo
    // parado, ver rememberRadioCinematicIdleState/LibraryShell.
    dimmed: Boolean = false,
) {
    val containerColor by animateColorAsState(
        targetValue = if (dimmed) Color.Black else PailerSurface.copy(alpha = 0.94f),
        animationSpec = tween(400),
        label = "bottomNavBackground",
    )
    // Icones/texto somem junto com o fundo escurecendo (pedido do usuario 18/09/2026) - o fundo
    // fica preto solido, so o CONTEUDO de cada item desaparece.
    val contentAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0f else 1f,
        animationSpec = tween(400),
        label = "bottomNavContentAlpha",
    )
    NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = containerColor,
    ) {
        MainTab.entries.forEach { tab ->
            val isRadio = tab == MainTab.Radio
            NavigationBarItem(
                modifier = Modifier.alpha(contentAlpha),
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    if (isRadio) {
                        RadioNavIcon(selected = selectedTab == tab, size = 40.dp, iconSize = 21.dp)
                    } else {
                        Icon(
                            tab.icon(),
                            contentDescription = tab.label,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                },
                label = {
                    Text(
                        tab.label,
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 9.sp,
                        fontWeight = if (isRadio) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun LandscapeMainNavigationRail(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(PailerSurface.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        MainTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val isRadio = tab == MainTab.Radio
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected && !isRadio) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center,
            ) {
                if (isRadio) {
                    RadioNavIcon(selected = selected, size = 40.dp, iconSize = 21.dp)
                } else {
                    Icon(
                        tab.icon(),
                        contentDescription = tab.label,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioNavIcon(
    selected: Boolean,
    size: Dp,
    iconSize: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) PailerRed else PailerRed.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Radio,
            contentDescription = MainTab.Radio.label,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun LibraryHeader(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    onNews: () -> Unit,
    hasNewNews: Boolean,
    remoteDeviceState: RemoteDeviceUiState,
    onScanRemote: () -> Unit,
    onStopScanRemote: () -> Unit,
    onConnectRemote: (RemoteDeviceEntry) -> Unit,
    onDisconnectRemote: () -> Unit,
    showSearch: Boolean = true,
    // "Modo cinema" da radio ativa (pedido do usuario 18/09/2026): fica preto depois de um tempo
    // parado, ver rememberRadioCinematicIdleState/LibraryShell.
    dimmed: Boolean = false,
    // Botao extra so na aba Radio (pedido do usuario 22/09/2026: "faz um botão de 3 pontinhos no
    // topo da tela canto direito" - abre popover com "Criar nova rádio"/"Radio Settings", ver
    // RadioMenuButton em LibraryShell) - null nas outras abas, sem espaco reservado.
    extraAction: (@Composable () -> Unit)? = null,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (dimmed) Color.Black else PailerSurface,
        animationSpec = tween(400),
        label = "libraryHeaderBackground",
    )
    // Icones/texto somem junto com o fundo escurecendo (pedido do usuario 18/09/2026) - o fundo
    // fica preto solido (nao transparente), so o CONTEUDO desaparece.
    val contentAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0f else 1f,
        animationSpec = tween(400),
        label = "libraryHeaderContentAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 10.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderSettingsButton(onSettings = onSettings, isLoading = isLoading)
            Spacer(Modifier.width(8.dp))
            HeaderLogo()
            if (showSearch) {
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color.White.copy(alpha = 0.68f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Buscar",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.58f),
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            cursorBrush = SolidColor(PailerRed),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(10.dp))
            HeaderCastButton(
                state = remoteDeviceState,
                onScan = onScanRemote,
                onStopScan = onStopScanRemote,
                onConnect = onConnectRemote,
                onDisconnect = onDisconnectRemote,
            )
            Spacer(Modifier.width(14.dp))
            HeaderNewsButton(onNews = onNews, hasNewNews = hasNewNews)
            if (extraAction != null) {
                Spacer(Modifier.width(14.dp))
                extraAction()
            }
        }
    }
}

@Composable
private fun HeaderLogo(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = FontFamily.Cursive)) { append("Pailer ") }
            withStyle(SpanStyle(color = PailerRed)) { append("FM") }
        },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun HeaderSettingsButton(onSettings: () -> Unit, isLoading: Boolean, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onSettings,
        enabled = !isLoading,
        modifier = modifier.size(30.dp),
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = "Configuracoes",
            tint = Color.White.copy(alpha = if (isLoading) 0.28f else 0.66f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun HeaderNewsButton(onNews: () -> Unit, hasNewNews: Boolean, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onNews,
        modifier = modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Noticias",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(21.dp),
            )
            if (hasNewNews) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(PailerRed),
                )
            }
        }
    }
}

// Botao de 3 pontinhos so na aba Radio (pedido do usuario 22/09/2026: "faz um botão de 3
// pontinhos no topo da tela canto direito, ao clicar, abre um pop over (criar nova rádio) e radio
// settings") - popover simples com 2 opcoes; "Radio Settings" so navega pro MESMO
// SettingsDrawer/RadioSettingsPanel que ja existe em Configuracoes (evita duplicar a tela).
@Composable
private fun RadioMenuButton(
    onCreateRadio: () -> Unit,
    onOpenRadioSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Mais opções da rádio",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(21.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Criar nova rádio") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCreateRadio()
                },
            )
            DropdownMenuItem(
                text = { Text("Radio Settings") },
                leadingIcon = { Icon(Icons.Filled.Radio, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenRadioSettings()
                },
            )
        }
    }
}

// Botao unico de "transmitir pra TV" - lista Google Cast (Chromecast/Google TV, via MediaRouter
// direto, ver LocalTuneViewModel.scanForRemoteDevices) e UPnP/DLNA (TVs que nao falam Cast de
// verdade, ex.: LG webOS testada em 12/09/2026, so via SSDP) juntos no mesmo seletor - pedido do
// usuario (12/09/2026): "um so icone, as duas funcoes la dentro".
@Composable
private fun HeaderCastButton(
    state: RemoteDeviceUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (RemoteDeviceEntry) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    if (Build.VERSION.SDK_INT >= 33) {
        var hasNearbyPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ) == PackageManager.PERMISSION_GRANTED,
            )
        }
        val nearbyPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> hasNearbyPermission = granted }
        LaunchedEffect(hasNearbyPermission) {
            if (!hasNearbyPermission) {
                nearbyPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }
    IconButton(
        onClick = {
            showSheet = true
            onScan()
        },
        modifier = modifier.size(30.dp),
    ) {
        Icon(
            Icons.Filled.Cast,
            contentDescription = "Transmitir pra TV",
            tint = if (state.connectedLabel != null) PailerRed else Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp),
        )
    }
    if (showSheet) {
        RemoteDeviceSheet(
            state = state,
            onConnect = { entry -> onConnect(entry); showSheet = false },
            onDisconnect = { onDisconnect(); showSheet = false },
            onDismiss = {
                showSheet = false
                onStopScan()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteDeviceSheet(
    state: RemoteDeviceUiState,
    onConnect: (RemoteDeviceEntry) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PailerSurface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Transmitir para TV",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Chromecast, Google TV ou TVs UPnP na sua rede Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            if (state.connectedLabel != null) {
                ActionSheetRow(
                    icon = Icons.Filled.Close,
                    label = "Desconectar de ${state.connectedLabel}",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDisconnect,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }
            when {
                state.devices.isEmpty() && state.isScanning -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PailerRed)
                }
                state.devices.isEmpty() -> Text(
                    text = "Nenhuma TV encontrada na rede Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> state.devices.forEach { entry ->
                    ActionSheetRow(
                        icon = Icons.Filled.Tv,
                        label = entry.label,
                        onClick = { onConnect(entry) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// Filtros internos da aba Biblioteca (pedido do usuario 10/09/2026: Artistas/Albuns/Musicas/
// Categorias deixaram de ser abas proprias na barra inferior e viraram um seletor DENTRO da
// Biblioteca). Pilulas simples, no mesmo estilo de superficie arredondada ja usado em outros
// cards do app (PailerSurfaceHigh/RoundedCornerShape).
@Composable
private fun LibrarySectionTabs(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = if (isLandscape) 8.dp else 10.dp),
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
                    modifier = Modifier.padding(vertical = if (isLandscape) 6.dp else 8.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    fontSize = if (isLandscape) 11.sp else 12.sp,
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
    profile: UserProfileUiState,
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
    onChooseProfilePhoto: () -> Unit,
    onSaveProfile: (String, String) -> Unit,
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
    updateState: UpdateUiState = UpdateUiState(),
    onCheckForUpdate: () -> Unit = {},
    listeningStats: ListeningStatsUiState = ListeningStatsUiState(),
    songsBetweenBulletins: Int = 3,
    onChangeSongsBetweenBulletins: (Int) -> Unit = {},
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
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.align(Alignment.CenterStart),
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
                            profile = profile,
                            onSync = onSync,
                            batteryProtected = batteryProtected,
                            onRequestBatteryExemption = onRequestBatteryExemption,
                            onOpenProfile = { onPageChange(SettingsPage.Profile) },
                            onOpenLibraryMaintenance = { onPageChange(SettingsPage.LibraryMaintenance) },
                            onOpenRadioSettings = { onPageChange(SettingsPage.RadioSettings) },
                            updateState = updateState,
                            onCheckForUpdate = onCheckForUpdate,
                        )
                        SettingsPage.Profile -> UserProfileSettingsPanel(
                            profile = profile,
                            listeningStats = listeningStats,
                            onBack = { onPageChange(SettingsPage.Main) },
                            onChoosePhoto = onChooseProfilePhoto,
                            onSave = onSaveProfile,
                            onOpenBackup = { onPageChange(SettingsPage.Backup) },
                        )
                        SettingsPage.RadioSettings -> RadioSettingsPanel(
                            onBack = { onPageChange(SettingsPage.Main) },
                            hasHiddenRadios = hasHiddenRadios,
                            onRestoreHiddenRadios = onRestoreHiddenRadios,
                            hasRadioDislikedSongs = radioDislikedSongs.isNotEmpty(),
                            onOpenRadioDislikedSongs = { onPageChange(SettingsPage.RadioDislikedSongs) },
                            songsBetweenBulletins = songsBetweenBulletins,
                            onChangeSongsBetweenBulletins = onChangeSongsBetweenBulletins,
                        )
                        SettingsPage.LibraryMaintenance -> LibraryMaintenanceSettingsPanel(
                            onBack = { onPageChange(SettingsPage.Main) },
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
                            onOpenAlbumArtwork = {
                                onPageChange(SettingsPage.AlbumArtwork)
                                onScanAlbumArtwork()
                            },
                            hasHiddenLibraryItems = hasHiddenLibraryItems,
                            onRestoreHiddenLibraryItems = onRestoreHiddenLibraryItems,
                        )
                        SettingsPage.Metadata -> MetadataSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.LibraryMaintenance) },
                            onScan = onScanMetadata,
                            onSuggest = onSuggestGenres,
                            onApproveGenre = onApproveGenre,
                        )
                        SettingsPage.AlbumArtists -> AlbumArtistCleanupSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.LibraryMaintenance) },
                            onScan = onScanMissingArtists,
                            onApproveArtist = onApproveAlbumArtist,
                        )
                        SettingsPage.TagWriter -> TagWriterSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.LibraryMaintenance) },
                            onScan = onScanPendingTagWrites,
                        )
                        SettingsPage.Artists -> ArtistCleanupSettingsPanel(
                            metadata = metadata,
                            onBack = { onPageChange(SettingsPage.LibraryMaintenance) },
                            onScan = onScanArtists,
                            onUnifyArtist = onUnifyArtist,
                        )
                        SettingsPage.AlbumArtwork -> AlbumArtworkSettingsPanel(
                            state = albumArtwork,
                            onBack = { onPageChange(SettingsPage.LibraryMaintenance) },
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
                            onBack = { onPageChange(SettingsPage.RadioSettings) },
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
    profile: UserProfileUiState,
    onSync: () -> Unit,
    batteryProtected: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLibraryMaintenance: () -> Unit,
    onOpenRadioSettings: () -> Unit,
    updateState: UpdateUiState = UpdateUiState(),
    onCheckForUpdate: () -> Unit = {},
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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState(), flingBehavior = rememberSoftFlingBehavior()),
        ) {
            Spacer(Modifier.height(16.dp))
            SettingsActionRow(
            title = "Sincronizar",
            subtitle = "Ler novas faixas do celular",
            icon = Icons.Filled.Sync,
            onClick = onSync,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        if (!batteryProtected) {
            SettingsActionRow(
                title = "Segundo plano",
                subtitle = "Evitar pausa pela bateria",
                icon = Icons.Filled.BatteryAlert,
                onClick = onRequestBatteryExemption,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }
            SettingsActionRow(
                title = "Radio Settings",
                subtitle = "Boletins, radios e bloqueios",
                icon = Icons.Filled.Radio,
                onClick = onOpenRadioSettings,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SettingsActionRow(
                title = "Biblioteca Settings",
                subtitle = "Metadados, capas e itens ocultos",
                icon = Icons.Filled.Tune,
                onClick = onOpenLibraryMaintenance,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            // Botao manual (pedido do usuario 21/09/2026) - o app ja checa sozinho ao abrir (ver
            // LocalTuneApp.kt/checkForUpdate), mas quem instalou um APK de ANTES desse sistema
            // existir nunca vai ganhar o popup automatico sozinho; esse botao da uma resposta na
            // hora (achou/nao achou/falhou) em vez do silencio de sempre.
            SettingsActionRow(
                title = "Buscar atualizações",
                subtitle = if (updateState.isChecking) "Checando..." else "Versão instalada: ${BuildConfig.RELEASE_TAG}",
                icon = Icons.Filled.CloudUpload,
                onClick = { if (!updateState.isChecking) onCheckForUpdate() },
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        ProfileFooter(profile = profile, onOpenProfile = onOpenProfile)
    }
}

@Composable
private fun RadioSettingsPanel(
    onBack: () -> Unit,
    hasHiddenRadios: Boolean,
    onRestoreHiddenRadios: () -> Unit,
    hasRadioDislikedSongs: Boolean,
    onOpenRadioDislikedSongs: () -> Unit,
    songsBetweenBulletins: Int = 3,
    onChangeSongsBetweenBulletins: (Int) -> Unit = {},
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
                "Radio Settings",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Ajustes da radio, noticias e o que fica fora da programacao.",
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        // Pedido do usuario 22/09/2026: "poder escolher a partir de quantas músicas toca uma
        // notícia, o padrão é 3, mas adicionar a opção pra escolher 4 e 5 músicas".
        Text(
            "Notícias a cada quantas músicas",
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(3, 4, 5).forEach { option ->
                val selected = songsBetweenBulletins == option
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else PailerSurfaceHigh)
                        .clickable { onChangeSongsBetweenBulletins(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$option",
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))
        if (hasHiddenRadios) {
            SettingsActionRow(
                title = "Radios ocultas",
                subtitle = "Restaurar radios apagadas",
                icon = Icons.Filled.VisibilityOff,
                onClick = onRestoreHiddenRadios,
            )
        }
        if (hasRadioDislikedSongs) {
            if (hasHiddenRadios) HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SettingsActionRow(
                title = "Faixas bloqueadas",
                subtitle = "Liberar musicas da radio",
                icon = Icons.Filled.ThumbDown,
                onClick = onOpenRadioDislikedSongs,
            )
        }
    }
}

@Composable
private fun ProfileFooter(
    profile: UserProfileUiState,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(profile.photoUri, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name.ifBlank { "Meu perfil" },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenProfile, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Meu perfil",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ProfileAvatar(photoUri: Uri?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(PailerGunmetal.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri != null) {
            ArtworkBox(
                uri = photoUri,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                iconModifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun UserProfileSettingsPanel(
    profile: UserProfileUiState,
    listeningStats: ListeningStatsUiState = ListeningStatsUiState(),
    onBack: () -> Unit,
    onChoosePhoto: () -> Unit,
    onSave: (String, String) -> Unit,
    onOpenBackup: () -> Unit,
) {
    var nameDraft by remember(profile.name) { mutableStateOf(profile.name) }
    var birthdayDraft by remember(profile.birthday) { mutableStateOf(profile.birthday) }

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
                "Meu perfil",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(profile.photoUri, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onChoosePhoto) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Escolher foto")
            }
        }
        Spacer(Modifier.height(18.dp))
        ListeningStatsCard(listeningStats)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            label = { Text("Nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = birthdayDraft,
            onValueChange = { birthdayDraft = it },
            label = { Text("Data de aniversario") },
            placeholder = { Text("dd/mm") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        profile.message?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onSave(nameDraft, birthdayDraft) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Salvar alteracoes")
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Backup",
            subtitle = "Favoritos, fotos e app",
            icon = Icons.Filled.CloudUpload,
            onClick = onOpenBackup,
        )
    }
}

// Level + horas ouvidas (pedido do usuario 21/09/2026: "podemos acompanhar essa pontuação dentro
// do meu perfil, onde fica minha foto e nome e niver"). Nivel 1 comeca em 0 musicas - a barra
// mostra o progresso DENTRO do nivel atual (songsIntoCurrentLevel de 50), nao o total acumulado.
@Composable
private fun ListeningStatsCard(stats: ListeningStatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PailerGunmetal.copy(alpha = 0.5f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Nível ${stats.level}",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${stats.songsCompleted} músicas ouvidas",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { stats.songsIntoCurrentLevel / 50f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Faltam ${stats.songsToNextLevel} músicas para o nível ${stats.level + 1}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatListeningHours(stats.totalListeningMs),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Tempo ouvido",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    formatListeningHours(stats.radioListeningMs),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Na rádio",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatListeningHours(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}min"
        else -> "${minutes}min"
    }
}

@Composable
private fun LibraryMaintenanceSettingsPanel(
    onBack: () -> Unit,
    onOpenMetadata: () -> Unit,
    onOpenAlbumArtists: () -> Unit,
    onOpenTagWriter: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbumArtwork: () -> Unit,
    hasHiddenLibraryItems: Boolean,
    onRestoreHiddenLibraryItems: () -> Unit,
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
                "Biblioteca Settings",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Ferramentas para organizar a colecao local.",
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsActionRow(
            title = "Metadados",
            subtitle = "Generos e categorias",
            icon = Icons.Filled.Tune,
            onClick = onOpenMetadata,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Albuns sem artista",
            subtitle = "Corrigir nomes ausentes",
            icon = Icons.Filled.Person,
            onClick = onOpenAlbumArtists,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Tags pendentes",
            subtitle = "Sincronizacoes restantes",
            icon = Icons.Filled.Check,
            onClick = onOpenTagWriter,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Duplicados",
            subtitle = "Unificar artistas parecidos",
            icon = Icons.Filled.Person,
            onClick = onOpenArtists,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SettingsActionRow(
            title = "Capas",
            subtitle = "Buscar imagens faltantes",
            icon = Icons.Filled.Image,
            onClick = onOpenAlbumArtwork,
        )
        if (hasHiddenLibraryItems) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SettingsActionRow(
                title = "Itens ocultos",
                subtitle = "Restaurar artistas e albuns",
                icon = Icons.Filled.VisibilityOff,
                onClick = onRestoreHiddenLibraryItems,
            )
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
                "Guarda favoritos, fotos de artista e correcoes de album/artista num arquivo que voce escolhe - normalmente dentro do seu Google Drive, com a conta que ja esta logada no aparelho."
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
                    Icon(
                        painterResource(R.drawable.ic_google_drive),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Trocar pasta do Google Drive")
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onClearDestination, modifier = Modifier.fillMaxWidth()) {
                    Text("Desligar backup automatico")
                }
            }
        } else {
            // Pedido do usuario 22/09/2026: "não estou achando o botão enviar backup pro google
            // drive, talvez devesse ser mais explícito, até ter o logo do drive" - texto generico
            // "Escolher arquivo de backup" virou explicito com o nome do Drive + o logo dele
            // (ic_google_drive, ver drawable) do lado. onChooseDestination continua abrindo o
            // seletor de documentos padrao do Android (SAF) - Google Drive so aparece como opcao
            // ali PORQUE o app do Drive ja esta instalado/logado no aparelho, o app em si nao fala
            // com a API do Drive diretamente.
            Button(
                onClick = onChooseDestination,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painterResource(R.drawable.ic_google_drive),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Enviar backup para o Google Drive")
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
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
                "${change.artistName} • ${change.songCount} ${if (change.songCount == 1) "faixa" else "faixas"}",
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
    artists: List<LocalArtist>,
    player: PlayerUiState,
    // Nulo quando nao ha radio ativa - so precisa ser nao-nulo quando radioIsActive for true
    // aqui dentro (mesma condicao usada em LibraryShell pra criar o valor).
    radioMockup: RadioMockupSharedState?,
    // false enquanto o FullPlayer estiver aberto por cima (mostrando a MESMA cena) - ver
    // comentario em RadioMockupVideoBackground.
    radioVideoActive: Boolean = true,
    lyrics: LyricsUiState = LyricsUiState(),
    onContinue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onAutoUpgradeLyrics: () -> Unit = {},
    onOpenAlbum: (LocalAlbum) -> Unit,
    onOpenRadio: (LocalRadio) -> Unit,
    onOpenCurrentPlayer: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlay: (List<LocalSong>, Int, Boolean) -> Unit,
    onToggleAutoplay: () -> Unit,
    artistPhotoUriFor: (LocalArtist) -> Uri? = { null },
    onOpenArtist: (LocalArtist) -> Unit = {},
) {
    // Legenda (letra sincronizada sobre o disco) na Home - toggle manual do usuario, independente
    // do auto-fetch ao arrastar pro lado no FullPlayer (ADR-023). Enquanto ligado, dispara a busca
    // sozinho sempre que a musica atual nao tiver letra SINCRONIZADA ainda (vazia OU so com
    // texto sem timestamp, ex. letra incorporada na tag do arquivo - pedido do usuario
    // 17/09/2026: a legenda nunca acompanhava a musica em varias faixas porque a letra
    // incorporada quase nunca tem sincronia, e antes so tentava buscar online quando nao
    // havia NENHUMA letra) - inclusive ao trocar de faixa com o toggle ja ativado (nao so no
    // clique que liga), senao a legenda fica "presa" na letra da musica anterior.
    // onAutoUpgradeLyrics() so troca a letra atual se achar uma versao sincronizada (ou se nao
    // havia nada antes) - nunca troca um texto sem sincronia por outro tambem sem sincronia,
    // ver comentario em autoUpgradeLyricsSyncIfNeeded() no ViewModel. lyrics.message == null
    // evita repetir uma busca que ja falhou pra essa musica (mesma trava do auto-fetch do
    // player).
    // Ligada por padrao (pedido do usuario 15/09/2026, mesma regra da reproducao automatica).
    var homeCaptionsEnabled by rememberSaveable { mutableStateOf(true) }
    val onToggleCaptions: () -> Unit = { homeCaptionsEnabled = !homeCaptionsEnabled }
    LaunchedEffect(homeCaptionsEnabled, player.songId, lyrics.songId, lyrics.isLoading, lyrics.lyrics.synced, lyrics.message) {
        if (homeCaptionsEnabled &&
            player.songId != null &&
            lyrics.songId == player.songId &&
            !lyrics.isLoading &&
            !lyrics.isFetching &&
            !lyrics.lyrics.synced &&
            lyrics.message == null
        ) {
            onAutoUpgradeLyrics()
        }
    }
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
            songId = player.songId,
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
            songId = continueSong.id,
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
    // Foto redonda do artista ao lado do titulo/artista/album (pedido do usuario 15/09/2026) -
    // resolve o LocalArtist pelo nome cru do card (mesmo padrao de "playingArtist" no FullPlayer)
    // pra poder abrir a pagina dele e puxar a foto (override da web ou capa de album como
    // fallback, ver HomeArtistAvatar). Fica null (sem avatar) quando o nome nao bate com nenhum
    // artista conhecido - ex.: "various artists" agregado.
    val homePlayingArtist = remember(homePlaybackContent?.artist, artists) {
        homePlaybackContent?.artist
            ?.trim()?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let { key -> artists.firstOrNull { it.key == key } }
    }
    val homePlayingArtistPhotoUri = homePlayingArtist?.let(artistPhotoUriFor)
    // Fallback de "A seguir" quando a fila real do player vem vazia (musica parada mostrando so a
    // previa de "continuar ouvindo", ainda nao carregada no ExoPlayer, ou faixa que e a ultima da
    // fila) - sem isso o espaco reservado pra previa ficava em branco (feio, pedido do usuario
    // 15/09/2026 pra sempre vir preenchido). Cai pro resto do mesmo album, depois mesmo artista,
    // depois "ouvir de novo" - so fica vazio mesmo se a biblioteca nao tiver mais nada pra sugerir.
    val homeUpNextPreview = remember(player.upcomingTracks, homePlaybackContent, songs, homeListenAgain) {
        val liveQueue = player.upcomingTracks
        if (liveQueue.isNotEmpty()) {
            liveQueue
        } else {
            val albumNumbers = homePlaybackContent?.album
                ?.takeIf { it.isNotBlank() }
                ?.let { album -> songs.filter { it.album == album } }
                .orEmpty()
                .sortedWith(compareBy<LocalSong> { it.trackNumber }.thenBy { it.title.lowercase() })
            val currentId = homePlaybackContent?.songId
            val sameAlbum = homePlaybackContent?.album
                ?.takeIf { it.isNotBlank() }
                ?.let { album -> songs.filter { it.album == album && it.id != currentId } }
                .orEmpty()
            val sameArtist = sameAlbum.ifEmpty {
                homePlaybackContent?.artist
                    ?.takeIf { it.isNotBlank() }
                    ?.let { artist -> songs.filter { it.artist == artist && it.id != currentId } }
                    .orEmpty()
            }
            val fallbackSongs = sameArtist.ifEmpty { homeListenAgain.filter { it.id != currentId } }
            fallbackSongs.take(4).map { song ->
                UpcomingTrack(
                    label = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" - "),
                    albumTrackNumber = albumNumbers.indexOfFirst { it.id == song.id }.takeIf { it >= 0 }?.plus(1),
                )
            }
        }
    }

    LazyColumn(
        flingBehavior = rememberSoftFlingBehavior(),
        // top = 12.dp (pedido do usuario 17/09/2026): sem padding nenhum no topo, o card do
        // disco (primeiro item, radio ao vivo ou tocando agora/continuar ouvindo) ficava colado
        // direto na status bar - so uma margem pequena, nao um header novo.
        contentPadding = PaddingValues(top = 12.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Sessao do disco (radio ao vivo OU tocando agora/continuar ouvindo) preenche a tela
        // inteira - pedido do usuario 15/09/2026: precisa rolar pra baixo pra chegar em "Albuns
        // adicionados recentemente". O espaco vazio sobrando (quando o card nao preenche tudo
        // sozinho) mostra a previa da fila (HomeUpNextPreview) encostada embaixo.
        if (radioIsActive || homePlaybackContent != null) {
            item {
                Column(modifier = Modifier.fillParentMaxHeight()) {
                    // radioMockup != null (nao so radioIsActive) de proposito: activeRadioName e
                    // hasMedia nem sempre atualizam no mesmo frame ao iniciar uma radio (o
                    // controller carrega a fila de forma assincrona) - sem essa checagem extra,
                    // uma janela rara entre os dois eventos derrubaria o app num !! nulo.
                    if (radioIsActive && radioMockup != null) {
                        LiveNowRadioCard(
                            player = player,
                            radioMockup = radioMockup,
                            lyrics = lyrics,
                            onClick = onOpenPlayer,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            videoActive = radioVideoActive,
                        )
                    } else if (homePlaybackContent != null) {
                        ContinueListeningCard(
                            content = homePlaybackContent,
                            isPlaying = player.hasMedia && player.isPlaying,
                            lyrics = if (homePlaybackContent.isCurrentMedia) lyrics else LyricsUiState(),
                            onClick = if (homePlaybackContent.isCurrentMedia) onOpenCurrentPlayer else onContinue,
                            onPlayPauseClick = if (homePlaybackContent.isCurrentMedia) onTogglePlayPause else onContinue,
                            captionsEnabled = homeCaptionsEnabled,
                            onToggleCaptions = onToggleCaptions,
                            autoplayEnabled = player.autoplayEnabled,
                            onToggleAutoplay = onToggleAutoplay,
                            artist = homePlayingArtist,
                            artistPhotoUri = homePlayingArtistPhotoUri,
                            onOpenArtist = { homePlayingArtist?.let(onOpenArtist) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                    }
                    // Espaco fixo (nao mais um Spacer com weight) - pedido do usuario 15/09/2026:
                    // a previa nao pode "descer" seguindo a altura do item quando o mini player
                    // esconde/aparece (o Scaffold da mais/menos espaco pro conteudo nessa hora,
                    // ver LibraryShell/miniPlayerVisible). Com altura fixa aqui, a previa fica
                    // sempre na mesma posicao logo abaixo do card, e a sobra de espaco (ex.: mini
                    // player escondido, ou pouca coisa em "A seguir") fica em branco no rodape.
                    Spacer(Modifier.height(18.dp))
                    if (homeUpNextPreview.isNotEmpty()) {
                        HomeUpNextPreview(
                            upcomingTracks = homeUpNextPreview,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                    }
                }
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

// Painel de noticias/curiosidades dos artistas favoritados, aberto pelo sino da navbar.
@Composable
private fun ArtistNewsDrawer(
    visible: Boolean,
    favoriteArtists: List<LocalArtist>,
    newsState: ArtistNewsUiState,
    onRequestLoad: () -> Unit,
    onOpenLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    // Cobre quase a tela toda, deixando so um filete da Home visivel na ponta (pedido do usuario)
    // - 32dp de sobra em vez da largura cheia, pra ainda dar pra perceber que tem conteudo atras.
    val panelWidthDp = (configuration.screenWidthDp.dp - 32.dp).coerceAtLeast(240.dp)

    LaunchedEffect(visible) {
        if (visible) onRequestLoad()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onClose),
            )
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Column(
                    modifier = Modifier
                        .width(panelWidthDp)
                        .fillMaxHeight()
                        .background(PailerCharcoal)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Fechar",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            "Novidades dos seus favoritos",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
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
    photoUriFor: (LocalArtist) -> Uri? = { null },
    scrollState: LazyGridState = rememberLazyGridState(),
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val columns = if (isLandscape) 4 else 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = scrollState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(
            horizontal = if (isLandscape) 14.dp else 18.dp,
            vertical = if (isLandscape) 8.dp else 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 18.dp),
    ) {
        gridItems(artists, key = { it.key }) { artist ->
            ArtistGridCard(
                artist = artist,
                onClick = { onOpenArtist(artist) },
                onLongClick = { onLongPressArtist(artist) },
                photoUri = photoUriFor(artist),
                compact = isLandscape,
            )
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
    onAutoFetchPhoto: () -> Unit = {},
    listState: LazyGridState = rememberLazyGridState(),
) {
    // Foto escolhida pelo usuario (busca na web) tem prioridade sobre a capa do primeiro album -
    // ver LocalTuneViewModel.artistPhotoUri/LocalArtist nao ter foto propria no data model.
    val artworkSong = artist.songs.firstOrNull { it.artworkUri != null }
    val displayUri = photoOverrideUri ?: artworkSong?.artworkUri
    val displayEmbeddedSource = if (photoOverrideUri != null) null else artworkSong?.contentUri
    // Busca automatica da foto (pedido do usuario 15/09/2026) - dispara sozinha ao abrir a pagina
    // do artista pela primeira vez, so 1 vez (ver maybeAutoFetchArtistPhoto/hasAutoPhotoLookupRun).
    LaunchedEffect(artist.key) { onAutoFetchPhoto() }
    var showEditor by rememberSaveable(artist.key) { mutableStateOf(false) }
    var showCreateRadioConfirm by rememberSaveable(artist.key) { mutableStateOf(false) }
    // Mais novo primeiro (pedido do usuario 15/09/2026) - album sem ano tageado (0) sempre por
    // ultimo, nunca misturado como se fosse o mais antigo de verdade.
    val sortedAlbums = remember(albums) {
        albums.sortedWith(
            compareByDescending<LocalAlbum> { it.year > 0 }
                .thenByDescending { it.year }
                .thenBy { it.title.lowercase() },
        )
    }
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
                    ArtworkBox(
                        uri = displayUri,
                        embeddedSourceUri = displayEmbeddedSource,
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
            gridItems(sortedAlbums, key = { it.key }) { album ->
                AlbumGridCard(
                    album = album,
                    onClick = { onOpenAlbum(album) },
                    subtitle = album.year.takeIf { it > 0 }?.toString(),
                    // Nome um pouco menor e ano ainda menor (pedido do usuario 15/09/2026) - so
                    // nesta tela, o grid geral (Biblioteca/Genero) mantem o tamanho padrao.
                    titleStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    subtitleStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                )
            }
        }

        if (showEditor) {
            ArtistMetadataEditorOverlay(
                artist = artist,
                albums = albums,
                availableGenres = availableGenres,
                photoUri = displayUri,
                photoEmbeddedSource = displayEmbeddedSource,
                onOpenPhotoSearch = onOpenPhotoSearch,
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
    photoUri: Uri?,
    photoEmbeddedSource: Uri?,
    onOpenPhotoSearch: () -> Unit,
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
                // Foto do artista (pedido do usuario 15/09/2026) - antes era um lapis solto por
                // cima da propria foto na tela do artista; juntar aqui deixa aquela tela mais
                // limpa e concentra toda edicao de metadados num lugar so.
                item {
                    Text(
                        "Foto do artista",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtworkBox(
                            uri = photoUri,
                            embeddedSourceUri = photoEmbeddedSource,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = onOpenPhotoSearch) {
                            Text("Buscar foto na web")
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
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
        keyboardOptions = keyboardOptions,
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
    scrollState: LazyGridState = rememberLazyGridState(),
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val columns = if (isLandscape) 4 else 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = scrollState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(
            horizontal = if (isLandscape) 14.dp else 18.dp,
            vertical = if (isLandscape) 8.dp else 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 18.dp),
    ) {
        gridItems(albums, key = { it.key }) { album ->
            AlbumGridCard(
                album = album,
                onClick = { onOpenAlbum(album) },
                onLongClick = { onLongPressAlbum(album) },
                compact = isLandscape,
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

// Nome de exibicao real do arquivo (com extensao certa) via MediaStore - LocalSong nao guarda
// isso, so o Uri. Cai pra um nome generico .mp3 se a consulta falhar (raro, mas contentUri pode
// ja ter sido apagado do MediaStore entre abrir o album e compartilhar).
private fun displayFileName(context: Context, song: LocalSong): String {
    val queried = runCatching {
        context.contentResolver.query(
            song.contentUri,
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
    return queried?.takeIf { it.isNotBlank() } ?: "${song.title}.mp3"
}

// Zipa as faixas do album num arquivo unico em cache/shared_zips (limpo antes de cada zip novo -
// so 1 por vez, pra nao acumular lixo), com todas dentro de uma PASTA com o nome do album no zip -
// e o que faz o destinatario receber "a pasta do album certinho" ao descompactar, diferente de
// shareSongs() (ACTION_SEND_MULTIPLE manda as faixas soltas, tocaveis na hora, mas o WhatsApp do
// destinatario nao recria pasta nenhuma - so joga tudo junto na pasta de midia dele). Publicado via
// FileProvider (androidx.core, ver AndroidManifest.xml/res/xml/file_paths.xml) porque um File cru
// de cache nao pode ser exposto por Uri content:// direto pra outro app.
private suspend fun buildAlbumZip(context: Context, songs: List<LocalSong>, albumTitle: String): Uri? =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_zips").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safeName = albumTitle.ifBlank { "Album" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val zipFile = File(dir, "$safeName.zip")
        val ok = runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                val usedNames = mutableSetOf<String>()
                songs.forEach { song ->
                    val baseName = displayFileName(context, song)
                    var entryName = "$safeName/$baseName"
                    var suffix = 1
                    while (!usedNames.add(entryName)) {
                        entryName = "$safeName/${suffix++}_$baseName"
                    }
                    context.contentResolver.openInputStream(song.contentUri)?.use { input ->
                        zipOut.putNextEntry(ZipEntry(entryName))
                        input.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                }
            }
        }.isSuccess
        if (!ok) return@withContext null
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile) }.getOrNull()
    }

// Compartilha o album como .zip (ver buildAlbumZip) - roda no scope da tela (nao viewModelScope,
// e um efeito colateral de UI pontual) pra nao travar a thread principal zipando arquivos grandes.
private fun shareAlbumAsZip(context: Context, scope: CoroutineScope, songs: List<LocalSong>, albumTitle: String) {
    if (songs.isEmpty()) return
    scope.launch {
        val uri = buildAlbumZip(context, songs, albumTitle) ?: run {
            Toast.makeText(context, "Não foi possível gerar o zip do álbum.", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, albumTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar album (zip)"))
    }
}

// Abre o instalador do Android direto pro APK ja baixado (ver UpdateCheckRepository.downloadApk/
// UpdateUiState.downloadedApkFile) - mesmo padrao de FileProvider do buildAlbumZip acima (um File
// cru de cache nao pode virar Uri content:// pra outro "app" - aqui o proprio PackageInstaller -
// sem passar pelo FileProvider). REQUEST_INSTALL_PACKAGES no manifesto faz o Android pedir
// "permitir que o Pailer FM instale outros apps?" sozinho na primeira vez, antes de prosseguir.
private fun installApkUpdate(context: Context, apkFile: File) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

// Popup "Nova versao disponivel" (pedido do usuario 15/09/2026) - substitui ter que mandar o APK
// manualmente pelo WhatsApp toda vez. "Agora nao" so fecha pra essa sessao (reaparece no proximo
// launch enquanto a versao instalada continuar desatualizada - pedido explicito era um popup, nao
// um botao escondido em Configuracoes).
@Composable
private fun UpdateAvailableDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = state.latestRelease ?: return
    AlertDialog(
        onDismissRequest = { if (!state.isDownloading) onDismiss() },
        icon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
        title = { Text("Nova versão disponível") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (release.notes.isNullOrBlank()) {
                    Text("A versão ${release.tagName} do Pailer FM já está disponível.")
                } else {
                    Text("Novidades da ${release.tagName}:", fontWeight = FontWeight.SemiBold)
                    Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.isDownloading) {
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Baixando... ${(state.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = !state.isDownloading) {
                Text(if (state.isDownloading) "Baixando..." else "Baixar e instalar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isDownloading) {
                Text("Agora não")
            }
        },
    )
}

@Composable
private fun AlbumShareMenu(songs: List<LocalSong>, albumTitle: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Enviar faixas separadas") },
                onClick = { expanded = false; shareSongs(context, songs, albumTitle) },
            )
            DropdownMenuItem(
                text = { Text("Enviar como .zip (mantém a pasta)") },
                onClick = { expanded = false; shareAlbumAsZip(context, scope, songs, albumTitle) },
            )
        }
    }
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
    onSaveMetadata: (String, String, String, Int) -> Unit,
    onCreateRadio: () -> Unit = {},
    albumArtwork: AlbumArtworkUiState = AlbumArtworkUiState(),
    onOpenArtworkSearch: () -> Unit = {},
    onCloseArtworkSearch: () -> Unit = {},
    onSelectArtworkCandidate: (ArtworkCandidate) -> Unit = {},
    onApplyArtwork: (LocalAlbum) -> Unit = {},
    currentlyPlayingSongId: Long? = null,
    listState: LazyListState = rememberLazyListState(),
    // "Mais desse artista" (pedido do usuario 15/09/2026) - outros albuns do MESMO artista,
    // pra navegar sem voltar pra tela do artista. Vazio pra albuns "various artists" (nao ha
    // "o artista" desse album pra buscar mais coisas dele).
    otherArtistAlbums: List<LocalAlbum> = emptyList(),
    onOpenAlbum: (LocalAlbum) -> Unit = {},
    onAutoFetchMetadata: () -> Unit = {},
) {
    // Busca automatica de genero/ano (pedido do usuario 15/09/2026) - dispara sozinha ao abrir a
    // pagina do album, reavaliada toda vez (sem trava de "so 1 vez pra sempre" - ver
    // maybeAutoTagAlbumMetadata) enquanto o album continuar sem genero e/ou sem ano.
    LaunchedEffect(album.key) { onAutoFetchMetadata() }
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
                            if (album.year > 0) "${album.songs.size} faixas • ${album.year}" else "${album.songs.size} faixas",
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
                    AlbumShareMenu(songs = album.songs, albumTitle = album.title)
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
            if (otherArtistAlbums.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.padding(horizontal = 18.dp)) {
                        SectionTitle("Mais desse artista")
                    }
                }
                item {
                    LazyRow(
                        flingBehavior = rememberSoftFlingBehavior(),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(otherArtistAlbums, key = { it.key }) { other ->
                            AlbumGridCard(
                                album = other,
                                onClick = { onOpenAlbum(other) },
                                subtitle = other.year.takeIf { it > 0 }?.toString(),
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
            }
        }

        if (showEditor) {
            AlbumMetadataEditorOverlay(
                album = album,
                availableGenres = availableGenres,
                onCancel = { showEditor = false },
                onSave = { title, artistName, genre, year ->
                    onSaveMetadata(title, artistName, genre, year)
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
    onSave: (String, String, String, Int) -> Unit,
    onFixArtwork: () -> Unit = {},
) {
    var title by remember(album.key) { mutableStateOf(album.title) }
    var artist by remember(album.key) { mutableStateOf(album.artist) }
    var genre by remember(album.key) { mutableStateOf(album.genre) }
    // Pedido do usuario 15/09/2026 - campo livre (o mesmo usado pra ano automatico via
    // approveAlbumYear/saveAlbumYearOverride), so digitos, vazio quando o album nao tem ano ainda.
    var year by remember(album.key) { mutableStateOf(album.year.takeIf { it > 0 }?.toString().orEmpty()) }

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
                MetadataTextField(
                    value = year,
                    onValueChange = { input -> year = input.filter { it.isDigit() }.take(4) },
                    label = "Ano de lançamento",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        onClick = { onSave(title, artist, genre, year.toIntOrNull() ?: 0) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

// Edicao de metadados de UMA faixa (pedido do usuario 17/09/2026) - aberta pelos 3 pontinhos ao
// lado do X do player cheio ("Editar faixa"). Cobre o caso de faixa recebida por app de mensagem
// com nome tipo "AUD1028389.mp3": da pra corrigir titulo/album/artista/genero/ano so dela, sem
// precisar editar o album inteiro (ver saveTrackMetadataEdit no ViewModel).
@Composable
private fun TrackMetadataEditorOverlay(
    song: LocalSong,
    availableGenres: List<String>,
    onCancel: () -> Unit,
    onSave: (String, String, String, String, Int) -> Unit,
) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album) }
    var genre by remember(song.id) { mutableStateOf(song.genre) }
    var year by remember(song.id) { mutableStateOf(song.year.takeIf { it > 0 }?.toString().orEmpty()) }

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
                        "Editar faixa",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
                MetadataTextField(value = title, onValueChange = { title = it }, label = "Nome da faixa")
                MetadataTextField(value = artist, onValueChange = { artist = it }, label = "Nome do artista")
                MetadataTextField(value = album, onValueChange = { album = it }, label = "Nome do album")
                GenreTagField(
                    value = genre,
                    onValueChange = { genre = it },
                    suggestions = availableGenres,
                )
                MetadataTextField(
                    value = year,
                    onValueChange = { input -> year = input.filter { it.isDigit() }.take(4) },
                    label = "Ano de lançamento",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = { onSave(title, artist, album, genre, year.toIntOrNull() ?: 0) },
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 4 else 3),
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(if (isLandscape) 14.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 18.dp),
    ) {
        gridItems(songs, key = { it.id }) { song ->
            SongGridCard(
                song = song,
                isFavorite = isSongFavorite(song),
                onClick = { onPlay(songs.indexOf(song)) },
                onLongClick = { onDeleteSong(song) },
                onToggleFavorite = { onToggleSongFavorite(song) },
                compact = isLandscape,
            )
        }
    }
}

@Composable
private fun PlaylistsScreen(
    radios: List<LocalRadio>,
    player: PlayerUiState,
    // Nulo quando nao ha radio ativa - so precisa ser nao-nulo quando radioIsActive for true
    // aqui dentro (mesma condicao usada em LibraryShell pra criar o valor).
    radioMockup: RadioMockupSharedState?,
    // false enquanto o FullPlayer estiver aberto por cima (mostrando a MESMA cena) - ver
    // comentario em RadioMockupVideoBackground.
    radioVideoActive: Boolean = true,
    lyrics: LyricsUiState = LyricsUiState(),
    onOpenRadio: (LocalRadio) -> Unit,
    onOpenPlayer: () -> Unit,
    showGifBanner: Boolean = true,
    listState: LazyGridState = rememberLazyGridState(),
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val radioIsActive = player.activeRadioName.isNotBlank() && player.hasMedia
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 4 else 3),
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(if (isLandscape) 14.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 18.dp),
    ) {
        // O banner ambiente some quando ha radio tocando pra nao duplicar o gif junto do
        // LiveNowRadioCard, que ja tem seu proprio fundo animado.
        if (showGifBanner) {
            // radioMockup != null (nao so radioIsActive): ver comentario equivalente em
            // HomeScreen - janela rara onde activeRadioName/hasMedia ainda nao atualizaram juntos.
            if (radioIsActive && radioMockup != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LiveNowRadioCard(
                        player = player,
                        radioMockup = radioMockup,
                        lyrics = lyrics,
                        onClick = onOpenPlayer,
                        videoActive = radioVideoActive,
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RadioHostsVideoBanner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
        }
        gridItems(radios, key = { it.name }) { radio ->
            MinimalRadioCard(radio = radio, onClick = { onOpenRadio(radio) })
        }
    }
}

@Composable
private fun RadioDetailScreen(
    radio: LocalRadio,
    player: PlayerUiState,
    // Nulo quando essa radio nao e a que esta tocando agora - so precisa ser nao-nulo quando
    // isInSession for true aqui dentro (implica player.activeRadioName nao-vazio, mesma condicao
    // usada em LibraryShell pra criar o valor).
    radioMockup: RadioMockupSharedState?,
    // false enquanto o FullPlayer estiver aberto por cima (mostrando a MESMA cena) - ver
    // comentario em RadioMockupVideoBackground.
    radioVideoActive: Boolean = true,
    lyrics: LyricsUiState = LyricsUiState(),
    sessionSongs: List<LocalSong>,
    // Mesmo default de RadioBulletinSettings.songsBetweenBulletins (RadioBulletin.kt) - so usado
    // se algum chamador nao passar o valor real das configuracoes de boletim.
    songsBetweenBulletins: Int = 3,
    isGenerating: Boolean,
    onEnterRadio: () -> Unit,
    onOpenPlayer: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleAmbience: (RadioAmbience) -> Unit = {},
    onAmbienceVolumeChange: (Float) -> Unit = {},
    onExitRadio: () -> Unit = {},
    onDislike: () -> Unit = {},
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
    cinematicIdle: RadioCinematicIdleState? = null,
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
    val isInSession = player.activeRadioName == radio.name && player.hasMedia
    LazyColumn(
        state = listState,
        flingBehavior = rememberSoftFlingBehavior(),
        contentPadding = PaddingValues(
            horizontal = if (isInSession) 0.dp else 18.dp,
            vertical = if (isInSession) 0.dp else 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Sem seta de voltar aqui de proposito - o gesto/botao de voltar do proprio Android
        // já cobre isso; a lixeira desceu pra ficar do lado do botao principal (Sair/Entrar).
        if (isInSession) {
            item {
                // radioMockup pode chegar nulo por 1 frame bem no instante em que a sessao esta
                // comecando (activeRadioName e hasMedia nem sempre atualizam juntos - ver
                // comentario equivalente em HomeScreen) - so entao esse card fica vazio por
                // instante, sem derrubar o app; os controles abaixo (RadioSessionControls) nao
                // dependem disso e continuam normais.
                radioMockup?.let { mockup ->
                    LiveNowRadioCard(
                        player = player,
                        radioMockup = mockup,
                        lyrics = lyrics,
                        onClick = onOpenPlayer,
                        edgeToEdge = true,
                        cinematicIdle = cinematicIdle,
                        videoActive = radioVideoActive,
                    )
                }
            }
            // Ja mostrando a rádio ao vivo no card acima (nome, faixa atual, capa) - repetir
            // mosaico/nome/descricao aqui embaixo seria redundante. Os controles essenciais ficam
            // aqui dentro da propria radio, deixando o mini player global escondido nessa tela.
            item {
                RadioSessionControls(
                    player = player,
                    isFavorite = isFavorite,
                    onDislike = onDislike,
                    onToggleFavorite = onToggleFavorite,
                    onToggleMute = onToggleMute,
                    onToggleAmbience = onToggleAmbience,
                    onAmbienceVolumeChange = onAmbienceVolumeChange,
                    onExitRadio = onExitRadio,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
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
                            // Radios fixas (Surprise Me/Radio recente/Musicas Curtidas, pedido do
                            // usuario 22/09/2026) nao podem ser removidas/ocultadas - deleteRadio
                            // chamaria hideRadio() nelas, mas radiosFrom nao filtra mais as fixas
                            // por hiddenRadioKeys (ver comentario la), entao "remover" nunca
                            // faria efeito nenhum e ainda mostraria um toast enganoso de sucesso.
                            if (!radio.isPinned) {
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
        }
        if (sessionSongs.isNotEmpty()) {
            item {
                // Titulo proprio (nao SectionTitle - reservado pras secoes da Home/Biblioteca,
                // fonte maior de proposito la) - fonte menor, pedido do usuario (17/09/2026).
                Text(
                    "Sequência ao vivo do bloco",
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            // Mostra so o BLOCO atual (interval musicas + o boletim que fecha ele), nao a
            // sequencia inteira - pedido do usuario (17/09/2026): mostrar tudo de uma vez
            // dava spoiler da programacao inteira; de 3 em 3 (ou o que songsBetweenBulletins
            // definir) mantem a sensacao de radio "infinita" com fator surpresa. Bloco atual =
            // o que contem player.queueIndex (1-based, mesmo indice de sessionSongs); fora da
            // sessao (isInSession == false, queueIndex nao serve de referencia), mostra sempre
            // o primeiro bloco. Revela o proximo bloco sozinho conforme queueIndex avanca (a UI
            // ja recompoe a cada posicao nova tocada, nao precisa de estado proprio aqui).
            //
            // Boletim nunca vira item de fila de verdade no ExoPlayer (toca por cima via
            // announcementPlayer, ver LocalTuneViewModel) - o indice de sessionSongs bate direto
            // com player.currentMediaItemIndex/queueIndex. Card "Noticia" aqui e so uma marcacao
            // visual de ONDE o boletim entra, pedido do usuario (04/09/2026) pra visualizar
            // tocando/ja tocada/proximo boletim sem abrir o player.
            //
            // Cards da sequencia NAO SAO MAIS clicaveis (pedido do usuario 17/09/2026): tocar
            // num card da proxima musica deixava "furar fila"/pular pra frente, o que nao era
            // combinado - RadioTrackRow perdeu o onClick de proposito.
            val interval = songsBetweenBulletins.coerceAtLeast(1)
            val currentPosition = if (isInSession) player.queueIndex.coerceAtLeast(1) else 1
            val blockStart = ((currentPosition - 1) / interval) * interval + 1
            val blockEnd = blockStart + interval - 1
            sessionSongs.withIndex()
                .filter { (i, _) -> (i + 1) in blockStart..blockEnd }
                .forEach { (index, song) ->
                    val position = index + 1
                    item(key = song.id) {
                        RadioTrackRow(
                            index = position,
                            song = song,
                            isPlaying = isInSession && position == player.queueIndex,
                            isPlayed = isInSession && position < player.queueIndex,
                        )
                    }
                }
            if (sessionSongs.size >= blockEnd) {
                item(key = "news_break_$blockEnd") {
                    RadioNewsBreakCard()
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
private fun RadioSessionControls(
    player: PlayerUiState,
    isFavorite: Boolean,
    onDislike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleAmbience: (RadioAmbience) -> Unit,
    onAmbienceVolumeChange: (Float) -> Unit,
    onExitRadio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAmbienceMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PailerSurface.copy(alpha = 0.78f))
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioControlButton(
            icon = Icons.Filled.ThumbDown,
            contentDescription = "Nao curti essa faixa na radio",
            enabled = player.songId != null,
            onClick = onDislike,
        )
        RadioControlButton(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "Curtir faixa",
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.9f),
            enabled = player.songId != null,
            onClick = onToggleFavorite,
        )
        RadioControlButton(
            icon = if (player.isRadioMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            contentDescription = if (player.isRadioMuted) "Ativar som" else "Mutar",
            onClick = onToggleMute,
        )
        // Ambiencia (pedido do usuario 24/09/2026): abre o popup de sons de fundo; verde
        // enquanto algum som de ambiencia estiver tocando.
        RadioControlButton(
            painter = painterResource(R.drawable.ic_ambience_rain),
            contentDescription = "Ambiencia",
            tint = if (player.activeAmbience != null) RadioAmbienceActiveGreen else Color.White.copy(alpha = 0.9f),
            onClick = { showAmbienceMenu = true },
        )
        RadioControlButton(
            icon = Icons.Filled.PowerSettingsNew,
            contentDescription = "Desligar radio",
            tint = Color(0xFFFFD6B0),
            onClick = onExitRadio,
        )
    }

    if (showAmbienceMenu) {
        RadioAmbienceDialog(
            activeAmbience = player.activeAmbience,
            volume = player.ambienceVolume,
            onToggleAmbience = onToggleAmbience,
            onVolumeChange = onAmbienceVolumeChange,
            onDismiss = { showAmbienceMenu = false },
        )
    }
}

private val RadioAmbienceActiveGreen = Color(0xFF4CD37A)

private val RadioAmbience.label: String
    get() = when (this) {
        RadioAmbience.Rain -> "Chuva"
        RadioAmbience.Fan -> "Ventilador"
        RadioAmbience.Fireplace -> "Lareira"
        RadioAmbience.BrownNoise -> "Ruído marrom"
        RadioAmbience.Bus -> "Ônibus"
        RadioAmbience.Sea -> "Mar"
    }

@Composable
private fun RadioAmbienceIcon(ambience: RadioAmbience, tint: Color, size: androidx.compose.ui.unit.Dp) {
    val modifier = Modifier.size(size)
    when (ambience) {
        RadioAmbience.Rain -> Icon(painterResource(R.drawable.ic_ambience_rain), null, modifier, tint)
        RadioAmbience.Fan -> Icon(Icons.Filled.Air, null, modifier, tint)
        RadioAmbience.Fireplace -> Icon(Icons.Filled.LocalFireDepartment, null, modifier, tint)
        RadioAmbience.BrownNoise -> Icon(Icons.Filled.GraphicEq, null, modifier, tint)
        RadioAmbience.Bus -> Icon(Icons.Filled.DirectionsBus, null, modifier, tint)
        RadioAmbience.Sea -> Icon(Icons.Filled.Waves, null, modifier, tint)
    }
}

@Composable
private fun RadioAmbienceDialog(
    activeAmbience: RadioAmbience?,
    volume: Float,
    onToggleAmbience: (RadioAmbience) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PailerSurface)
                .padding(20.dp),
        ) {
            Text(
                "Ambiencia",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
            // Uma ambiencia por vez - tocar em outra troca o som, tocar na ativa desliga.
            RadioAmbience.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    row.forEach { ambience ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            RadioAmbienceOption(
                                label = ambience.label,
                                active = activeAmbience == ambience,
                                onClick = { onToggleAmbience(ambience) },
                            ) { tint -> RadioAmbienceIcon(ambience, tint, 28.dp) }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                "Volume da ambiencia",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun RadioAmbienceOption(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val tint = if (active) RadioAmbienceActiveGreen else Color.White.copy(alpha = 0.9f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    if (active) RadioAmbienceActiveGreen.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.24f),
                )
                .border(
                    width = 1.5.dp,
                    color = if (active) RadioAmbienceActiveGreen else Color.Transparent,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            icon(tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

// 42dp/22dp (eram 48/26) - encolhidos pra caber o 5o botao (ambiencia) na mesma linha.
@Composable
private fun RadioControlButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    tint: Color = Color.White.copy(alpha = 0.9f),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.24f else 0.12f)),
    ) {
        val iconTint = if (enabled) tint else Color.White.copy(alpha = 0.32f)
        if (painter != null) {
            Icon(painter, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(22.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(22.dp))
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
    isPlaying: Boolean = false,
    isPlayed: Boolean = false,
) {
    // Contraste em 3 niveis pedido pelo usuario (04/09/2026): tocando agora em destaque, ja
    // tocada apagada/cinza, proxima no tom normal - mesmo padrao visual de AlbumTrackRow
    // (icone GraphicEq + cor primaria quando toca), so com o estado "ja tocada" novo aqui.
    // NAO clicavel de proposito (pedido do usuario 17/09/2026): tocar num card da sequencia
    // deixava pular pra frente na radio ao vivo, funcionalidade que nao era combinada - so
    // exibe, nunca interage. Padding/artwork reduzidos no mesmo pedido: card mais fino/
    // minimalista (era 48dp de capa + 8dp de padding vertical, ficou 38dp + 4dp).
    val contentAlpha = if (isPlayed) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isPlaying) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPlaying) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = "Tocando agora",
                modifier = Modifier.width(24.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                index.toString(),
                modifier = Modifier.width(24.dp).alpha(contentAlpha),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ArtworkBox(
            uri = song.artworkUri,
            embeddedSourceUri = song.contentUri,
            modifier = Modifier.size(38.dp).alpha(contentAlpha),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
            Text(
                song.title,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
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
private fun LiveNowRadioCard(
    player: PlayerUiState,
    // Nao-nulo sempre que esse card e de fato mostrado - todo chamador so renderiza
    // LiveNowRadioCard quando ja sabe que a radio esta ativa (ver isRadioActive em LibraryShell),
    // que e exatamente a mesma condicao que garante radioMockup != null la.
    radioMockup: RadioMockupSharedState,
    lyrics: LyricsUiState = LyricsUiState(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = false,
    cinematicIdle: RadioCinematicIdleState? = null,
    // false quando o FullPlayer (que mostra a MESMA cena, ver radioMockup) esta aberto por cima
    // desse card - evita as 2 instancias brigando pela superficie de video do player
    // compartilhado (ver comentario em RadioMockupVideoBackground).
    videoActive: Boolean = true,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(if (edgeToEdge) 0.dp else 8.dp),
        colors = CardDefaults.cardColors(containerColor = PailerSurface),
    ) {
        RadioAlbumMockupScene(
            player = player,
            lyrics = lyrics,
            radioMockup = radioMockup,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(RadioAlbumMockupAspectRatio),
            cinematicIdle = cinematicIdle,
            videoActive = videoActive,
        )
    }
}

private const val RadioAlbumMockupAspectRatio = 941f / 1672f

private val RadioAlbumMockupCorners = listOf(
    Offset(0.0210f, 0.3100f),
    Offset(0.5950f, 0.3360f),
    Offset(0.6900f, 0.7070f),
    Offset(0.1010f, 0.7630f),
)

// Quanto antes do fim da musica a cena corta pra radio_scene_disco_final (mesmo enquadramento
// da capa, take de encerramento) - musica e video terminam juntos. Alinhado a duracao desse
// video (12s). Ver rememberRadioMockupSequencer().
private const val RadioEndingCutoverMs = 12_000L

// Duracao real do video radio_scene_trocando_disco.mp4 (Fran trocando o disco na vitrola),
// medida via ffprobe - 6.5s. Usada como referencia pra alinhar o inicio da transicao (abaixo) e
// o tempo que ela fica visivel (RadioDiscSwapHoldMs) com a duracao de VERDADE do clipe, em vez de
// numeros arbitrarios - pedido do usuario 20/09/2026: "veja a duração do clipe atual... toque ele
// exatamente na quantidade que o clipe tem de segundos pra acabar junto com a quantidade de
// segundos que a musica tem pra acabar".
private const val RadioDiscSwapClipDurationMs = 6_500L

// Quanto antes do fim real da musica a transicao de trocar o disco comeca. Precisa ser o tamanho
// do proprio clipe (RadioDiscSwapClipDurationMs, 6.5s) MAIS a folga do fade de entrada da tela
// preta (450ms = RadioSceneTransitionFadeInMs la embaixo, so entao o video e trocado de verdade -
// ver flashThroughBlack; nao da pra referenciar a constante direto aqui, ela e declarada depois
// no arquivo e top-level const val nao aceita forward reference), senao o video comeca cedo demais
// mas so fica visivel tarde demais e acaba cortado no meio (era o caso antes, com 900ms fixos pra
// um clipe de 6.5s - a troca de disco mal comecava a aparecer e ja levava o corte pro ciclo
// normal, ou pior, so aparecia DEPOIS da musica acabar, parecendo a capa trocando sozinha). Com
// essa conta o clipe termina de tocar bem na hora que a musica antiga acaba, nao antes nem (pior
// ainda) so depois.
private const val RadioDiscSwapLeadMs = RadioDiscSwapClipDurationMs + 450L

@Composable
private fun RadioAlbumMockupScene(
    player: PlayerUiState,
    lyrics: LyricsUiState,
    // Video mudo de fundo + orquestrador de cenas (ExoPlayer, scrim de fade, qual take esta
    // ativo) - SEMPRE hoisted no chamador (LibraryShell, ver radioMockup la) agora, nunca mais
    // criado aqui dentro (era remember local, entao toda vez que essa composable saia de
    // composicao - trocar de aba, abrir/fechar o FullPlayer, o app ir pra segundo plano e voltar -
    // o ExoPlayer e todo o estado (isFirstSetup etc.) eram destruidos e recriados do zero na
    // proxima composicao, sempre caindo na animacao de abertura "trocando disco" no meio da
    // musica, como um reinicio - e pior, se isso acontecesse perto do fim de uma musica de
    // verdade, o efeito de "primeira composicao" e o de "disco/musica mudando" disparavam JUNTOS
    // na montagem nova, brigando pelo mesmo scrim/exoPlayer - relatado pelo usuario 22/09/2026:
    // "sempre fica repetindo a imagem da fran colocando o disco... perde o efeito de fade in fade
    // out, ou fica sem sincronia"). Agora e UM SO objeto compartilhado por todos os lugares que
    // mostram essa cena (Home, aba Radio, tela da radio, FullPlayer), vivo enquanto a radio
    // estiver ativa (ver isRadioActive em LibraryShell), independente de qual tela esta na frente.
    radioMockup: RadioMockupSharedState,
    modifier: Modifier = Modifier,
    // null no card pequeno da Home (barra de progresso sempre visivel, sem "modo cinema") - nas
    // telas de radio em tela cheia (RadioDetailScreen/FullPlayer) quem chama ja tem o estado
    // hoisted (LibraryShell/FullPlayer), porque o header e a navbar do app sao IRMAOS dessa
    // cena, nao filhos dela (pedido do usuario 18/09/2026).
    cinematicIdle: RadioCinematicIdleState? = null,
    // false quando outra instancia desta mesma cena (mesmo radioMockup) esta na frente - ver
    // comentario em RadioMockupVideoBackground/LiveNowRadioCard.
    videoActive: Boolean = true,
) {
    val scrim = radioMockup.scrim
    val sequencer = radioMockup.sequencer

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (cinematicIdle != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { cinematicIdle.onInteraction() })
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // Um pouco maior durante o take de ceu do refrao (pedido do usuario 21/09/2026, ver
        // RadioChorusSkyScale) - Box pai ja tem clipToBounds(), entao o zoom nunca vaza pra fora
        // da tela; RESIZE_MODE_ZOOM (RadioMockupVideoBackground) ja corta as bordas por conta
        // propria, esse scale so aumenta ainda mais em cima disso.
        val videoScale by animateFloatAsState(
            targetValue = if (sequencer.chorusCaptionActive) RadioChorusSkyScale else 1f,
            animationSpec = tween(600),
            label = "radioSkyVideoScale",
        )
        RadioMockupVideoBackground(
            sequencer.exoPlayer,
            Modifier.fillMaxSize().scale(videoScale),
            videoActive = videoActive,
        )
        if (sequencer.showAlbumArt) {
            PerspectiveAlbumArtwork(
                uri = player.artworkUri,
                embeddedSourceUri = player.artworkSourceUri,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (sequencer.showProfilePhotoBoard) {
            PerspectiveProfilePhotoOverlay(
                photoUri = player.profilePhotoUri,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (sequencer.showTvScreen) {
            // Sorteia de novo a cada ocorrencia desse take (nunca mais de 1 corte por take - ver
            // comentario em RadioTvCutsPool).
            val corteRes = remember(sequencer.currentOccurrence) { RadioTvCutsPool.random() }
            PerspectiveTvScreenVideo(
                rawRes = corteRes,
                corners = RadioTvScreenCorners,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.drawable.radio_scene_take_tv),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFC06A).copy(alpha = 0.13f),
                        Color(0xFFFF8A2F).copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.73f, size.height * 0.38f),
                    radius = size.width * 0.48f,
                ),
                radius = size.width * 0.48f,
                center = Offset(size.width * 0.73f, size.height * 0.38f),
            )
        }
        // Fade de transicao (pedido do usuario 18/09/2026: precisa cobrir a capa/video de
        // verdade, pra esconder o atraso da PerspectiveAlbumArtwork (des)aparecendo exatamente no
        // corte) - mas ATRAS do header/legenda/rodape agora (pedido do usuario 22/09/2026: "a
        // legenda tem que ficar sempre por cima de tudo, nunca em baixo do fade"), nao mais por
        // cima de tudo feito antes. Ainda cobre 100% do video/capa/TV/gradiente acima (essa e a
        // parte que precisa mesmo ficar escondida no corte); noise/VHS abaixo continuam por cima
        // do fade tambem, ja eram "sempre por cima de tudo, independente da cena" por definicao
        // propria deles.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = scrim.alphaValue)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(PailerCharcoal.copy(alpha = 0.86f), Color.Transparent),
                    )
                )
                .padding(start = 18.dp, top = 30.dp, end = 18.dp, bottom = 58.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveRadioBadge(isActive = true)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatDuration(player.positionMs)} / ${formatDuration(player.durationMs)}",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Miniatura da capa ao lado do titulo: so nas cenas de ambiente/refrao, onde a
                // capa NAO esta desenhada grande em perspectiva por cima do video (showAlbumArt)
                // - pedido do usuario 21/09/2026, "quando estamos vendo outras imagens... pode
                // fazer a capa aparecer ao lado esquerdo do nome". Some ao voltar pra cena da
                // capa/disco pra nao duplicar a mesma arte na tela.
                AnimatedVisibility(
                    visible = !sequencer.showAlbumArt,
                    enter = fadeIn(tween(280)) + expandHorizontally(tween(280)),
                    exit = fadeOut(tween(200)) + shrinkHorizontally(tween(200)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtworkBox(
                            uri = player.artworkUri,
                            embeddedSourceUri = player.artworkSourceUri,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        player.title.ifBlank { "Tocando agora" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(player.artist, player.album).filter { it.isNotBlank() }.joinToString(" • "),
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        player.playbackSource.ifBlank { "Rádio" },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, PailerCharcoal.copy(alpha = 0.95f)),
                    )
                )
                .padding(start = 18.dp, top = 62.dp, end = 18.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Durante o take de ceu do refrao a legenda normal vira a versao cinematografica
            // (maior, bold, caps, com animacao de entrada - pedido do usuario 20/09/2026); no take
            // do quadro de fotos (a foto do OUVINTE, nao da musica) a legenda vira nome+level/
            // plays/horas do ouvinte (pedido do usuario 21/09/2026: "naquela tela onde aparece a
            // foto de perfil do ouvinte... nome dele, embaixo do nome o level > plays > horas") -
            // lyrics da musica atual nao fazem sentido nenhum numa cena que e sobre o ouvinte, nao
            // sobre a faixa. Fora dos dois casos e a legenda de sempre.
            when {
                sequencer.showProfilePhotoBoard -> {
                    // "bem mais pra baixo" (pedido do usuario 22/09/2026) - empurra o bloco pra
                    // perto do rodape de verdade em vez de nascer logo no topo dessa area (onde a
                    // legenda normal/chorus comecam). Altura fixa (nao weight(1f) - essa Column e
                    // wrap-content, sem altura propria pra distribuir peso nenhum).
                    Spacer(Modifier.height(64.dp))
                    RadioListenerStatsFooter(
                        name = player.listenerName,
                        level = player.listenerLevel,
                        songsCompleted = player.listenerSongsCompleted,
                        listeningMs = player.listenerListeningMs,
                    )
                }
                sequencer.chorusCaptionActive -> {
                    RadioChorusCaption(
                        lyrics = lyrics,
                        songId = player.songId,
                        positionMs = player.positionMs,
                        isPlaying = player.isPlaying,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    HomeLyricsSubtitle(
                        lyrics = lyrics,
                        songId = player.songId,
                        positionMs = player.positionMs,
                        isPlaying = player.isPlaying,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // No player expandido, some depois de alguns segundos parado e volta ao tocar na tela
            // (pedido do usuario 18/09/2026) - no card pequeno da Home fica sempre visivel.
            val progressBarAlpha by animateFloatAsState(
                targetValue = if (cinematicIdle?.controlsVisible != false) 1f else 0f,
                animationSpec = tween(400),
                label = "radioProgressBarAlpha",
            )
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
                    .clip(CircleShape)
                    .alpha(progressBarAlpha),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
        RadioMockupNoiseOverlay(Modifier.matchParentSize())
        // Filtro VHS trocado pelo teste do CTR (pedido do usuario 22/09/2026) - ver
        // RadioCtrOverlayTest acima. Pra voltar ao VHS: trocar essa linha de volta por
        // RadioVhsFilterOverlay(Modifier.matchParentSize()).
        RadioCtrOverlayTest(Modifier.matchParentSize())
    }
}

// Par (orquestrador de cenas + scrim de fade) hoisted em LibraryShell e repassado por parametro
// ate RadioAlbumMockupScene (ver comentario la) - o MESMO objeto em todo lugar que mostra a cena
// da radio, pra nunca mais reiniciar so por causa de navegacao entre telas.
private class RadioMockupSharedState(
    val sequencer: RadioMockupSequencerState,
    val scrim: RadioTransitionScrimState,
)

private class RadioMockupSequencerState(
    val exoPlayer: ExoPlayer,
    val showAlbumArt: Boolean,
    val showProfilePhotoBoard: Boolean,
    val showTvScreen: Boolean,
    val currentOccurrence: Int,
    val currentMediaId: String,
    // true enquanto um dos takes de ceu (pedido do usuario 20/09/2026) esta tocando - a legenda
    // troca pro estilo cinematografico nesse momento (ver RadioChorusCaption).
    val chorusCaptionActive: Boolean,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// "Modo cinema" da radio ativa (pedido do usuario 18/09/2026): sem interacao, esconde a barra de
// progresso E escurece (fundo preto + icones/texto some) o header/navbar do app, tudo junto, no
// mesmo instante. Tocar na tela (onInteraction) reseta tudo pro estado normal e reinicia a
// contagem.
private const val RadioCinematicHideDelayMs = 5_000L

private class RadioCinematicIdleState(
    val controlsVisible: Boolean,
    val dimSystemBars: Boolean,
    val onInteraction: () -> Unit,
)

@Composable
private fun rememberRadioCinematicIdleState(enabled: Boolean): RadioCinematicIdleState {
    var lastInteractionAt by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var dimSystemBars by remember { mutableStateOf(false) }

    // Loop de polling em vez de LaunchedEffect(key = tick) que reinicia a cada toque: um efeito
    // so, que roda o tempo todo e reage a lastInteractionAt - mais a prova de bug do que
    // depender do efeito ser cancelado/recriado no timing certo a cada interacao.
    LaunchedEffect(enabled) {
        if (!enabled) {
            controlsVisible = true
            dimSystemBars = false
            return@LaunchedEffect
        }
        // Zera a contagem sempre que o modo cinema fica habilitado (ex.: entrar na tela da
        // radio) - sem isso o relogio vinha rodando desde a primeira composicao da tela
        // (as vezes desde a abertura do app), fazendo a barra sumir/escurecer na hora se o
        // usuario demorasse pra chegar na radio (achado ao vivo 18/09/2026).
        lastInteractionAt = SystemClock.uptimeMillis()
        while (isActive) {
            val idleMs = SystemClock.uptimeMillis() - lastInteractionAt
            val hide = idleMs >= RadioCinematicHideDelayMs
            controlsVisible = !hide
            dimSystemBars = hide
            delay(200)
        }
    }

    return RadioCinematicIdleState(
        controlsVisible = controlsVisible,
        dimSystemBars = dimSystemBars,
        onInteraction = { lastInteractionAt = SystemClock.uptimeMillis() },
    )
}

// Modo imersivo da radio ativa (pedido do usuario 20/09/2026): dentro da radio a tela nao pode
// apagar sozinha nem a barra de notificacao/status por cima atrapalhar a "apresentacao" - esconde
// as barras do sistema (feito igual video em fullscreen, reversivel com um swipe da borda - mesmo
// comportamento do imersive sticky do Android) e mantem a tela acesa enquanto a sessao durar.
// Restaura os dois (barras + tela pode apagar de novo) ao sair de verdade da radio.
@Composable
private fun RadioImmersiveModeEffect(active: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val window = activity.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        onDispose {
            view.keepScreenOn = false
            WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(window, view, active) {
        view.keepScreenOn = active
        val controller = WindowInsetsControllerCompat(window, view)
        if (active) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun RadioCinematicSystemBars(dim: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val window = activity.window
    DisposableEffect(window) {
        val originalStatusBarColor = window.statusBarColor
        val originalNavigationBarColor = window.navigationBarColor
        onDispose {
            window.statusBarColor = originalStatusBarColor
            window.navigationBarColor = originalNavigationBarColor
        }
    }
    LaunchedEffect(window, dim) {
        val color = if (dim) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
        window.statusBarColor = color
        window.navigationBarColor = color
    }
}

private fun radioMockupMediaItem(context: Context, rawRes: Int, mediaId: String): MediaItem =
    MediaItem.Builder()
        .setUri(Uri.parse("android.resource://${context.packageName}/$rawRes"))
        .setMediaId(mediaId)
        .build()

// Take de ceu do refrao SEM o "volta" espelhado (pedido do usuario 21/09/2026 - ver comentario
// nos pools sky*Pool) - forwardHalfMs e a metade real do arquivo (ffprobe), cortada via
// ClippingConfiguration em vez de reeditar o .mp4 (reversivel na hora, sem precisar reprocessar
// os 12 arquivos de video).
private fun radioMockupSkyMediaItem(context: Context, rawRes: Int, mediaId: String, forwardHalfMs: Long): MediaItem =
    MediaItem.Builder()
        .setUri(Uri.parse("android.resource://${context.packageName}/$rawRes"))
        .setMediaId(mediaId)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setEndPositionMs(forwardHalfMs)
                .build(),
        )
        .build()

// Compensa a metade cortada acima (pedido do usuario 21/09/2026: "diminuir a velocidade do vídeo
// pra durar mais e deixar um pouquinho maior"): mais devagar pra nao passar rapido demais so com
// a metade do conteudo, e um pouco maior (RadioMockupVideoBackground ja usa RESIZE_MODE_ZOOM/
// corta as bordas, entao um scale por cima nao deixa tarja nem estica proporcao).
private const val RadioChorusSkyPlaybackSpeed = 0.6f
private const val RadioChorusSkyScale = 1.08f

// Volume do efeito sonoro da vitrola por cima da musica (pedido do usuario 22/09/2026) - baixo o
// bastante pra ficar so como camada de ambiente, nunca competir com a musica que continua tocando
// no player de verdade (esse e um player extra so pra esse som, ver vitrolaSfxPlayer).
private const val RadioVitrolaSfxVolume = 0.7f

// Um "pedaco" do ciclo normal: capa em loop 2x + 1 ou 2 cenas de ambiente aleatorias (pedido do
// usuario 18/09/2026: "não precisa aparecer todos os takes em sequência de uma vez... em ordem
// aleatória, e no máximo 2 cenas no intervalo da cena oficial do disco"). Evita repetir a MESMA
// cena de ambiente duas vezes seguidas dentro do mesmo intervalo, so por variedade.
private fun buildRadioCycleChunk(environmentPool: List<MediaItem>, coverItem: MediaItem): List<MediaItem> {
    val sceneCount = (1..2).random()
    var previous: MediaItem? = null
    val scenes = List(sceneCount) {
        var pick = environmentPool.random()
        if (environmentPool.size > 1) {
            while (pick.mediaId == previous?.mediaId) pick = environmentPool.random()
        }
        previous = pick
        pick
    }
    return listOf(coverItem, coverItem) + scenes
}

// Pedido do usuario 20/09/2026: "faz as cenas durarem o refrão todo" - antes o hold era um
// numero fixo (10s) sem relacao com o refrao de verdade, entao um refrao mais longo cortava de
// volta pro disco no meio (voltava pra capa, tocava outro verso do refrao, tinha que trocar de
// novo) - ida-e-volta feia. Agora o hold e a duracao REAL da ocorrencia do refrao (fim - inicio
// do range de LrcParser.chorusWindows) - os clipes de ceu sao loops "ida e volta" tocados em
// sequencia dentro do POOL do horario (ver skyPoolForNow), entao qualquer duracao de hold funciona
// sem cortar feio no meio. RadioChorusSkyMinHoldMs (~1 clipe inteiro) evita entrar por uma
// ocorrencia curta demais pra valer a pena a transicao; o teto de 45s e so seguranca contra o
// heuristico "detectar" por acaso um refrao gigante.
private const val RadioChorusSkyMinHoldMs = 4_000L
private fun holdMsForChorusWindow(range: LongRange): Long =
    (range.last - range.first).coerceIn(RadioChorusSkyMinHoldMs, 45_000L)

// Escolhe o POOL de takes de ceu (varias opcoes por horario, pedido do usuario 20/09/2026: "pega
// 2 opções a mais de cada cena... faz eles tocar um após o outro") pelo RELOGIO REAL do aparelho -
// nao e sorteado nem depende da musica, e sempre o horario batendo com a hora local de quem esta
// ouvindo: madrugada 00h-07h (usuario pediu 00h-05h; o buraco 05h-07h ficou aqui por padrao, mais
// perto tematicamente de madrugada do que de manha), manha 07h-12h, tarde 12h-19h, noite 19h-00h.
private fun skyPoolForNow(
    madrugada: List<MediaItem>,
    manha: List<MediaItem>,
    tarde: List<MediaItem>,
    noite: List<MediaItem>,
): List<MediaItem> {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 7 -> madrugada
        hour < 12 -> manha
        hour < 19 -> tarde
        else -> noite
    }
}

// Quanto tempo o take de trocar o disco fica visivel de fato (depois do fade de entrada) antes
// de voltar pro ciclo normal - ver LaunchedEffect de songId abaixo. Igual a duracao real do
// proprio clipe (RadioDiscSwapClipDurationMs), nao um numero arbitrario menor/maior: o video toca
// UMA vez, do inicio ao fim (REPEAT_MODE_OFF, ver abaixo), e some exatamente quando termina -
// antes (5s fixos pra um clipe de 6.5s) ele era cortado no meio, no talho errado.
private const val RadioDiscSwapHoldMs = RadioDiscSwapClipDurationMs

// Sequencia de takes da radio (pedido do usuario 18/09/2026, refinado no mesmo dia): capa do
// disco em loop 2x, depois 1-2 cenas de ambiente aleatorias, volta pra capa - ciclo continuo com
// fila do ExoPlayer estendida dinamicamente aos poucos (ver watcher de extensao mais abaixo), nao
// um playlist fixo repetindo sempre a mesma ordem. So os takes "capa"/"disco" mostram a arte do
// album desenhada em perspectiva por cima (PerspectiveAlbumArtwork); os takes de ambiente sao so
// a filmagem, sem overlay de capa. Perto do fim da musica o ciclo e interrompido e trocamos pro
// take de encerramento (radio_scene_disco_final); quando a musica muda de verdade, entra a
// transicao de trocar o disco (radio_scene_trocando_disco) bem no limite entre uma musica e
// outra, e so depois volta pro ciclo normal - ja com a capa/arte da musica nova.
@Composable
private fun rememberRadioMockupSequencer(
    songId: Long?,
    nearEnd: Boolean,
    discSwapImminent: Boolean,
    isBulletinPlaying: Boolean,
    bulletinBreakUpcoming: Boolean,
    // true quando o usuario tem foto de perfil salva (UserProfileUiState.photoUri) - so entao o
    // take "quadro de fotos" entra no pool de cenas de ambiente (pedido do usuario 19/09/2026:
    // "esse take só aparece se tiver foto lá"). Muda o pool pra frente (proxima extensao da fila),
    // sem interromper o que ja esta tocando.
    hasProfilePhoto: Boolean,
    // Range (ms na musica atual) da ocorrencia do refrao que esta rolando AGORA, ou null fora de
    // refrao / sem letra sincronizada (pedido do usuario 20/09/2026, ver LrcParser.chorusWindows).
    // O INICIO do range muda a cada nova ocorrencia do refrao, usado como "chave" pra saber se
    // essa ocorrencia especifica ja mostrou o take de ceu ou nao; a duracao do range (fim-inicio)
    // vira o hold de verdade da cena de ceu (ver holdMsForChorusWindow) - pedido do usuario depois
    // de ver ao vivo: "faz as cenas durarem o refrão todo", sem hold fixo desalinhado da letra.
    chorusWindowRange: LongRange?,
    scrim: RadioTransitionScrimState,
): RadioMockupSequencerState {
    val context = LocalContext.current

    val coverItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_album_mockup_scene, "cover")
    }
    val takeDeCimaItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_take_de_cima, "take_de_cima")
    }
    val franItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_fran_escrevendo, "fran")
    }
    val dogItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_dog, "dog")
    }
    val cafeNicoItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_cafe_nico, "cafe_nico")
    }
    val discoGirandoItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_disco_girando, "disco_girando")
    }
    // Imagem estatica (sem nenhum movimento, de proposito - pedido do usuario 19/09/2026) do
    // mural de polaroids; a janela em branco do polaroid do meio ganha a foto do usuario via
    // PerspectiveProfilePhotoOverlay, desenhada por cima quando esse take esta ativo (mesma
    // logica de showAlbumArt/PerspectiveAlbumArtwork pra capa do disco).
    val quadroFotosItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_quadro_fotos, "quadro_fotos")
    }
    // Imagem estatica da estante com a TV (pedido do usuario 19/09/2026) - o visor e transparente
    // na arte original (RadioTvScreenCorners) e recebe, por cima, 1 dos cortes 1:1 de abertura de
    // serie escolhido na hora (nunca mais de 1 por take - ver RadioTvCutsPool/currentOccurrence).
    val tvItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_take_tv, "take_tv")
    }
    val discoItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_disco_final, "disco")
    }
    val trocandoDiscoItem = remember(context) {
        radioMockupMediaItem(context, R.raw.radio_scene_trocando_disco, "trocando_disco")
    }
    // Pedido do usuario 22/09/2026: "vamos manter [o fade], toda vez que for trocar pra tela da
    // capa do disco e sair da tela da capa do disco... e manter o fade quando aparece também a
    // tela com a foto do perfil do ouvinte, e quando sai dessa tela. o resto, corte seco." - so
    // essas 4 (capa/disco/trocando-disco, que sao a MESMA "familia" visual da capa - ver
    // showAlbumArt acima -, e o quadro de fotos) disparam o flash preto numa transicao; qualquer
    // corte que nao envolva nenhuma delas (nem como origem nem como destino) fica sem flash -
    // ver RadioTransitionScrimState.transition mais abaixo.
    val fadeFamilyMediaIds = remember(coverItem, discoItem, trocandoDiscoItem, quadroFotosItem) {
        setOf(coverItem.mediaId, discoItem.mediaId, trocandoDiscoItem.mediaId, quadroFotosItem.mediaId)
    }
    // Takes de ceu (pedido do usuario 20/09/2026) - so tocam durante um refrao (ver watcher de
    // refrao mais abaixo), NAO fazem parte do environmentPool sorteado do ciclo normal. Um POOL
    // de 3 clipes por horario do dia (pedido do usuario depois: "pega 2 opções a mais de cada
    // cena... faz eles tocar um após o outro" - ver skyPoolForNow), nao so 1 fixo.
    //
    // Cada arquivo e gravado em "ida e volta" (a mesma filmagem tocada pra frente e depois de
    // tras pra frente, pra fechar o loop sem salto - so percebido comparando frame a frame,
    // frame(t) ~= frame(duracao-t)); pedido do usuario 21/09/2026: "nos takes que aparecem no
    // refrão, vamos tirar o reverse? ao invés disso, pode diminuir a velocidade do vídeo pra
    // durar mais e deixar um pouquinho maior". radioMockupSkyMediaItem corta cada clipe na
    // METADE real dele via MediaItem.ClippingConfiguration (so a parte de ida, sem o "volta"
    // espelhado) - a metade fica mais devagar/maior compensando (ver RadioChorusSkyPlaybackSpeed/
    // RadioChorusSkyScale, aplicados no LaunchedEffect do refrao e em RadioAlbumMockupScene).
    // radio_scene_ceu_madrugada_2/_3 REMOVIDOS do pool (pedido do usuario 22/09/2026: "estão
    // aparecendo 2 imagens que claramente está de dia, deveria estar escuro o céu, estrelado,
    // lua, mas não de dia") - checado frame a frame: os dois sao na verdade nascer/por do sol
    // (sol visivel, ceu laranja vivo), nao madrugada de verdade. So radio_scene_ceu_madrugada.mp4
    // (ceu escuro estrelado) e correto pra essa faixa de horario; fica sozinho no pool ate ter um
    // clipe de madrugada de verdade pra substituir os outros dois (sem variedade de 3 por enquanto,
    // so repete o mesmo - melhor que mostrar sol ao meio da madrugada).
    val skyMadrugadaPool = remember(context) {
        listOf(
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_madrugada, "ceu_madrugada_1", 4_000L),
        )
    }
    val skyManhaPool = remember(context) {
        listOf(
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_manha, "ceu_manha_1", 4_000L),
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_manha_2, "ceu_manha_2", 4_000L),
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_manha_3, "ceu_manha_3", 4_000L),
        )
    }
    // Take antigo de radio_scene_ceu_tarde.mp4 foi SUBSTITUIDO (pedido do usuario 20/09/2026:
    // "esse [céu de tarde] está meio cara de savana, precisa ser algo urbano") - os 3 agora sao
    // todos novos, silhueta de skyline generica (nunca uma casa/ponto turistico reconhecivel, pra
    // servir de céu de QUALQUER cidade).
    val skyTardePool = remember(context) {
        listOf(
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_tarde, "ceu_tarde_1", 4_000L),
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_tarde_2, "ceu_tarde_2", 4_000L),
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_tarde_3, "ceu_tarde_3", 4_000L),
        )
    }
    val skyNoitePool = remember(context) {
        listOf(
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_noite, "ceu_noite_1", 4_000L),
            // Esse clipe especifico e mais curto que os outros 11 (4.004s vs 8s de duracao real,
            // ffprobe) - a metade dele e proporcionalmente menor (2002ms, nao 4000ms).
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_noite_2, "ceu_noite_2", 2_002L),
            radioMockupSkyMediaItem(context, R.raw.radio_scene_ceu_noite_3, "ceu_noite_3", 4_000L),
        )
    }
    val environmentPool = remember(takeDeCimaItem, franItem, dogItem, cafeNicoItem, discoGirandoItem, quadroFotosItem, tvItem, hasProfilePhoto) {
        val base = listOf(takeDeCimaItem, franItem, dogItem, cafeNicoItem, discoGirandoItem, tvItem)
        if (hasProfilePhoto) base + quadroFotosItem else base
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply { volume = 0f }
    }

    // Som ambiente da Fran ajeitando a vitrola (pedido do usuario 22/09/2026: "coloquei uma
    // trilha nova... pra gente tocar de background quando estiver no fim da música e começar o
    // take da fran colocando o disco na vitrola") - exoPlayer acima (o video) fica sempre mudo, o
    // audio de verdade da radio e outro player la no ViewModel; esse aqui e um 3o player, so pra
    // esse efeito sonoro, tocando por cima da musica como camada de ambiente (nao mudo, mas bem
    // mais baixo que a musica - mesma ideia das vinhetas/boletim, que tambem tocam num canal a
    // parte). Disparado 1x (sem loop) exatamente quando o take radio_scene_trocando_disco entra
    // (ver LaunchedEffect de songId abaixo) - os 5s do audio cabem dentro do RadioDiscSwapHoldMs
    // (6.5s) do clipe.
    val vitrolaSfxPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            volume = RadioVitrolaSfxVolume
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.radio_sfx_fran_ajeitando_vitrola}")))
            prepare()
        }
    }
    DisposableEffect(vitrolaSfxPlayer) {
        onDispose { vitrolaSfxPlayer.release() }
    }

    var currentMediaId by remember(exoPlayer) { mutableStateOf(coverItem.mediaId) }
    // Incrementa a cada troca de item - usado so pra sortear de novo qual corte 1:1 passa na TV
    // toda vez que o take "take_tv" volta a tocar (currentMediaId sozinho nao muda entre duas
    // ocorrencias desse MESMO take).
    var currentOccurrence by remember(exoPlayer) { mutableStateOf(0) }
    var discoActiveForSong by remember(exoPlayer) { mutableStateOf<Long?>(null) }
    var isFirstSetup by remember(exoPlayer) { mutableStateOf(true) }
    var hasHandledBulletinOnce by remember(exoPlayer) { mutableStateOf(false) }
    // Enquanto false, o watcher de extensao (mais abaixo) fica de fora - evita ele "completar"
    // uma fila que na verdade e um estado forcado de item unico (boletim/disco final/trocando
    // disco, todos REPEAT_MODE_ONE).
    var dynamicCycleEnabled by remember(exoPlayer) { mutableStateOf(true) }

    fun freshCyclePlaylist() = buildRadioCycleChunk(environmentPool, coverItem) +
        buildRadioCycleChunk(environmentPool, coverItem)

    val startFreshCycle: suspend () -> Unit = {
        dynamicCycleEnabled = true
        // Sempre volta pra velocidade normal aqui (nao so no fim do bloco de ceu que a ligou) -
        // e o unico ponto de retorno ao ciclo comum de TODAS as transicoes especiais (trocando
        // disco, boletim, disco final), entao cobre qualquer caminho de volta sem precisar
        // lembrar de resetar em cada um deles.
        exoPlayer.setPlaybackParameters(PlaybackParameters(1f))
        exoPlayer.setMediaItems(freshCyclePlaylist())
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaId = mediaItem?.mediaId ?: currentMediaId
                currentOccurrence++
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Cortes NATURAIS do playlist (teto->fran, fran->capa, etc.) - antecipados por polling, ver
    // RadioNaturalTransitionWatcher.
    RadioNaturalTransitionWatcher(exoPlayer, scrim, fadeFamilyMediaIds)

    // Fila dinamica: enquanto no ciclo normal, mantem sempre um pedaco de folga na frente
    // (nunca deixa o ExoPlayer ficar sem "proximo item" real, que faria REPEAT_MODE_OFF parar
    // sozinho) e poda o que ja tocou pra fila nao crescer pra sempre numa musica longa.
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (dynamicCycleEnabled) {
                val index = exoPlayer.currentMediaItemIndex
                val count = exoPlayer.mediaItemCount
                if (count - index <= 3) {
                    exoPlayer.addMediaItems(buildRadioCycleChunk(environmentPool, coverItem))
                }
                if (index > 1) {
                    exoPlayer.removeMediaItems(0, index)
                }
            }
            delay(300)
        }
    }

    // Primeira composicao (pedido do usuario 22/09/2026: "podemos começar a rádio com o take da
    // fran ajeitando a vitrola? já que ela está ajeitando o primeiro cd da primeira música" - so
    // pulava direto pra capa antes) - toca o MESMO take/som de troca de disco
    // (trocandoDiscoItem + vitrolaSfxPlayer, ver LaunchedEffect de songId mais abaixo) uma vez ao
    // abrir a tela. Sem flash preto NESSA entrada especifica (a tela esta abrindo agora, nao ha
    // nada por tras pra esconder - mesma logica de antes); a SAIDA pro ciclo normal, sim, ja passa
    // pelo scrim.transition normal (trocando_disco -> capa, os dois na fadeFamily, entao sempre
    // funde).
    LaunchedEffect(exoPlayer, environmentPool, coverItem, trocandoDiscoItem) {
        if (isFirstSetup) {
            isFirstSetup = false
            dynamicCycleEnabled = false
            exoPlayer.setMediaItem(trocandoDiscoItem)
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            vitrolaSfxPlayer.seekTo(0)
            vitrolaSfxPlayer.playWhenReady = true
            delay(RadioDiscSwapHoldMs)
            scrim.transition(
                sourceMediaId = trocandoDiscoItem.mediaId,
                destMediaId = coverItem.mediaId,
                fadeFamily = fadeFamilyMediaIds,
                onBlack = startFreshCycle,
            )
        }
    }

    // Transicao de trocar o disco (pedido do usuario 18/09/2026): dispara ANTES de songId mudar
    // de verdade, assim que discSwapImminent fica true (musica antiga terminando) - "pode começar
    // a transição só um pouco antes". guardActive evita disparar de novo por causa do proprio
    // songId mudando logo em seguida; se por algum motivo discSwapImminent nunca chegou a ficar
    // true pra essa transicao (skip manual, por exemplo), o proprio songId mudando dispara na
    // hora como fallback. Usa rememberUpdatedState pra ler os valores mais recentes de dentro de
    // um loop que NAO reinicia a cada recomposicao (evita reiniciar o timer de hold no meio).
    val currentSongId = rememberUpdatedState(songId)
    val currentDiscSwapImminent = rememberUpdatedState(discSwapImminent)
    val currentBulletinPlaying = rememberUpdatedState(isBulletinPlaying)
    val currentBulletinBreakUpcoming = rememberUpdatedState(bulletinBreakUpcoming)
    LaunchedEffect(exoPlayer, environmentPool, coverItem, trocandoDiscoItem) {
        var lastSongId = currentSongId.value
        var wasBulletinPlaying = currentBulletinPlaying.value
        while (isActive) {
            val bulletinNow = currentBulletinPlaying.value
            val bulletinJustEnded = wasBulletinPlaying && !bulletinNow
            wasBulletinPlaying = bulletinNow
            if (bulletinNow) {
                // Boletim no ar: a cena e do take de cima em loop (efeito do boletim abaixo) - a
                // musica esta pausada nos ultimos segundos, entao discSwapImminent fica true o
                // boletim inteiro; sem esse guard a troca de disco disparava de novo em loop por
                // cima do take de cima.
                lastSongId = currentSongId.value
            } else if (!isFirstSetup) {
                val songChanged = currentSongId.value != lastSongId
                // Pedido do usuario 24/09/2026: boletim -> take de cima -> fran arrumando a
                // vitrola -> capa. Musica que termina em boletim NAO troca o disco antes dele
                // (bulletinBreakUpcoming), so quando o boletim acaba (bulletinJustEnded).
                val swapBeforeEnd = currentDiscSwapImminent.value && !currentBulletinBreakUpcoming.value
                if (bulletinJustEnded || swapBeforeEnd || songChanged) {
                    lastSongId = currentSongId.value
                    // Saindo do boletim, a musica antiga ainda esta "perto do fim" ate o
                    // seekToNextMediaItem chegar - marca ela como ja encerrada pra o efeito do
                    // disco final nao disparar por cima da troca de disco.
                    discoActiveForSong = if (bulletinJustEnded) currentSongId.value else null
                    // dynamicCycleEnabled = false enquanto isso pra o watcher de extensao nao
                    // mexer na fila de item unico do trocando-disco.
                    dynamicCycleEnabled = false
                    scrim.transition(
                        sourceMediaId = currentMediaId,
                        destMediaId = trocandoDiscoItem.mediaId,
                        fadeFamily = fadeFamilyMediaIds,
                        onBlack = {
                            exoPlayer.setMediaItem(trocandoDiscoItem)
                            // REPEAT_MODE_OFF (era ONE) - agora o hold bate com a duracao real do
                            // clipe (RadioDiscSwapHoldMs = RadioDiscSwapClipDurationMs), entao o
                            // video so precisa tocar UMA vez do inicio ao fim; deixa-lo em loop so
                            // arriscava reiniciar bem quando o hold ia estourar, cortando o final
                            // da animacao.
                            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                            // Som da vitrola por cima (pedido do usuario 22/09/2026) - seekTo(0)
                            // pra sempre comecar do inicio, mesmo se o efeito ainda estivesse
                            // tocando de uma troca de disco anterior muito rapida.
                            vitrolaSfxPlayer.seekTo(0)
                            vitrolaSfxPlayer.playWhenReady = true
                        },
                    )
                    delay(RadioDiscSwapHoldMs)
                    // Boletim entrou no meio da troca (buffer atrasou o flag de "boletim vindo")
                    // - o take de cima ja assumiu, nao volta pra capa por cima dele.
                    if (!currentBulletinPlaying.value) {
                        scrim.transition(
                            sourceMediaId = trocandoDiscoItem.mediaId,
                            destMediaId = coverItem.mediaId,
                            fadeFamily = fadeFamilyMediaIds,
                            onBlack = startFreshCycle,
                        )
                    }
                    lastSongId = currentSongId.value
                }
            }
            delay(80)
        }
    }

    // Perto do fim da musica atual: corta pro take de encerramento (por trás do flash, pra
    // esconder o corte forcado) e trava nele ate a proxima musica comecar (o LaunchedEffect acima
    // reresolve quando songId mudar, incluindo a transicao de trocar o disco). Ignorado com
    // boletim tocando - o boletim manda nesse momento (ver efeito abaixo), a musica esta pausada
    // mesmo.
    LaunchedEffect(exoPlayer, songId, nearEnd, discoItem, isBulletinPlaying) {
        if (nearEnd && !isBulletinPlaying && discoActiveForSong != songId) {
            discoActiveForSong = songId
            dynamicCycleEnabled = false
            scrim.transition(
                sourceMediaId = currentMediaId,
                destMediaId = discoItem.mediaId,
                fadeFamily = fadeFamilyMediaIds,
                onBlack = {
                    exoPlayer.setMediaItem(discoItem)
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                },
            )
        }
    }

    // Boletim ao vivo (pedido do usuario 18/09/2026): enquanto toca, trava so no take de cima em
    // loop, sem capa. Quando termina, entra a fran arrumando a vitrola e depois a capa (pedido do
    // usuario 24/09/2026).
    LaunchedEffect(exoPlayer, isBulletinPlaying, takeDeCimaItem, environmentPool, coverItem) {
        if (!hasHandledBulletinOnce) {
            hasHandledBulletinOnce = true
            if (!isBulletinPlaying) return@LaunchedEffect
        }
        // A SAIDA do boletim (fran arrumando a vitrola -> capa) fica no loop de troca de disco
        // acima (bulletinJustEnded), que tambem absorve o songId mudando logo em seguida.
        if (isBulletinPlaying) {
            dynamicCycleEnabled = false
            scrim.transition(
                sourceMediaId = currentMediaId,
                destMediaId = takeDeCimaItem.mediaId,
                fadeFamily = fadeFamilyMediaIds,
                onBlack = {
                    exoPlayer.setMediaItem(takeDeCimaItem)
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                },
            )
        }
    }

    // Take de ceu no refrao (pedido do usuario 20/09/2026): entra so quando chorusWindowRange
    // aponta uma ocorrencia de refrao que essa musica ainda nao mostrou - guarda por
    // songId+startMs (chorusShownForSongId/chorusShownWindowStart) pra nao repetir a cada
    // recomposicao enquanto a musica continua dentro da MESMA ocorrencia (a janela dura varias
    // linhas, esse efeito roda em loop de polling). !nearEnd evita colidir com a transicao de
    // encerramento/troca de disco perto do fim da musica (dynamicCycleEnabled ja e a trava
    // compartilhada contra rodar 2 transicoes especiais ao mesmo tempo). O POOL inteiro do
    // horario entra na fila (embaralhado) em REPEAT_MODE_ALL - toca um clipe apos o outro
    // (pedido do usuario) pelo tempo INTEIRO da ocorrencia do refrao (holdMsForChorusWindow),
    // sem cortar de volta pro disco no meio.
    val currentChorusWindowRange = rememberUpdatedState(chorusWindowRange)
    var chorusShownForSongId by remember(exoPlayer) { mutableStateOf<Long?>(null) }
    var chorusShownWindowStart by remember(exoPlayer) { mutableStateOf<Long?>(null) }
    LaunchedEffect(exoPlayer, skyMadrugadaPool, skyManhaPool, skyTardePool, skyNoitePool) {
        while (isActive) {
            val window = currentChorusWindowRange.value
            val songNow = currentSongId.value
            if (songNow != chorusShownForSongId) {
                chorusShownForSongId = songNow
                chorusShownWindowStart = null
            }
            val alreadyShownThisOccurrence = window != null && window.first == chorusShownWindowStart
            if (!isFirstSetup && dynamicCycleEnabled && !nearEnd && window != null && !alreadyShownThisOccurrence) {
                chorusShownWindowStart = window.first
                val skyPool = skyPoolForNow(skyMadrugadaPool, skyManhaPool, skyTardePool, skyNoitePool)
                val shuffledSkyPool = skyPool.shuffled()
                dynamicCycleEnabled = false
                scrim.transition(
                    sourceMediaId = currentMediaId,
                    destMediaId = shuffledSkyPool.first().mediaId,
                    fadeFamily = fadeFamilyMediaIds,
                    onBlack = {
                        exoPlayer.setMediaItems(shuffledSkyPool)
                        // REPEAT_MODE_ALL (era ONE com 1 clipe so) - agora e um POOL de varios
                        // clipes, toca um apos o outro e recomeca do inicio se o refrao continuar
                        // mais que uma volta completa do pool.
                        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                        // Mais devagar (pedido do usuario 21/09/2026, ver
                        // RadioChorusSkyPlaybackSpeed e radioMockupSkyMediaItem) - compensa cada
                        // clipe agora so ter a metade "de ida" (sem o "volta" espelhado).
                        // startFreshCycle acima de volta pra 1f.
                        exoPlayer.setPlaybackParameters(PlaybackParameters(RadioChorusSkyPlaybackSpeed))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    },
                )
                delay(holdMsForChorusWindow(window))
                scrim.transition(
                    sourceMediaId = currentMediaId,
                    destMediaId = coverItem.mediaId,
                    fadeFamily = fadeFamilyMediaIds,
                    onBlack = startFreshCycle,
                )
            }
            delay(150)
        }
    }

    val showAlbumArt = currentMediaId == coverItem.mediaId || currentMediaId == discoItem.mediaId
    val showProfilePhotoBoard = currentMediaId == quadroFotosItem.mediaId
    val showTvScreen = currentMediaId == tvItem.mediaId
    // Todo mediaId de take de ceu comeca com "ceu_" (ver os 4 pools acima) - mais simples que
    // comparar contra as 12 opcoes uma a uma.
    val chorusCaptionActive = currentMediaId.startsWith("ceu_")
    return RadioMockupSequencerState(
        exoPlayer,
        showAlbumArt,
        showProfilePhotoBoard,
        showTvScreen,
        currentOccurrence,
        currentMediaId,
        chorusCaptionActive,
    )
}

// Cobre o corte entre takes com um flash preto ANTECIPADO (pedido do usuario 18/09/2026: o fade
// reagindo depois que o corte ja aconteceu (onMediaItemTransition) ficava sempre um passo atras
// do glitch, o que parecia uma piscada em vez de um fade - "o fade precisa entrar antes, ficar
// escuro no centro e voltar suave"). Fecha pro preto ANTES do corte previsto, segura escuro
// (cobrindo o instante exato da troca) e so entao abre de novo devagar.
private const val RadioSceneTransitionLeadMs = 450L
private const val RadioSceneTransitionHoldMs = 200L
private const val RadioSceneTransitionFadeInMs = 450
private const val RadioSceneTransitionFadeOutMs = 700
private const val RadioSceneTransitionRearmMarginMs = 500L

private class RadioTransitionScrimState(private val alpha: Animatable<Float, *>) {
    val alphaValue: Float get() = alpha.value

    suspend fun flashThroughBlack(onBlack: (suspend () -> Unit)? = null) {
        alpha.animateTo(1f, tween(RadioSceneTransitionFadeInMs))
        onBlack?.invoke()
        delay(RadioSceneTransitionHoldMs)
        alpha.animateTo(0f, tween(RadioSceneTransitionFadeOutMs))
    }

    // Decide fade vs corte seco pela FAMILIA da cena de origem/destino (pedido do usuario
    // 22/09/2026, ver fadeFamilyMediaIds em rememberRadioMockupSequencer) - fade so quando origem
    // OU destino for capa/disco/trocando-disco ou quadro de fotos; caso contrario so executa a
    // troca direto (onBlack), sem flash nenhum - a propria troca do ExoPlayer ja e um corte seco.
    suspend fun transition(
        sourceMediaId: String,
        destMediaId: String,
        fadeFamily: Set<String>,
        onBlack: suspend () -> Unit,
    ) {
        if (sourceMediaId in fadeFamily || destMediaId in fadeFamily) {
            flashThroughBlack(onBlack)
        } else {
            onBlack()
        }
    }
}

@Composable
private fun rememberRadioTransitionScrimState(): RadioTransitionScrimState {
    val alpha = remember { Animatable(0f) }
    return remember(alpha) { RadioTransitionScrimState(alpha) }
}

// Antecipa cortes NATURAIS do playlist (troca automatica de item no ExoPlayer, sem intervencao
// nossa - ex.: teto->fran, fran->volta pra capa) monitorando quanto falta pro fim do item atual,
// ja que todos os takes tem duracao fixa e conhecida. Cortes que NOS disparamos (disco no fim da
// musica, reset pra musica nova) sao cobertos a parte, orquestrados ao redor do proprio
// setMediaItem (ver rememberRadioMockupSequencer) - ai sabemos exatamente quando o corte
// acontece, nao precisa adivinhar por polling.
@Composable
private fun RadioNaturalTransitionWatcher(exoPlayer: ExoPlayer, scrim: RadioTransitionScrimState, fadeFamily: Set<String>) {
    LaunchedEffect(exoPlayer, scrim, fadeFamily) {
        var armed = true
        while (isActive) {
            val duration = exoPlayer.duration
            val remaining = duration - exoPlayer.currentPosition
            if (duration > 0) {
                // Pula o flash quando o proximo item e o MESMO take (ex.: capa->capa no loop de
                // 2x, ou o disco repetindo em REPEAT_MODE_ONE) - pedido do usuario 18/09/2026:
                // "não precisa [de fade], só de uma cena diferente pra outra diferente".
                val itemCount = exoPlayer.mediaItemCount
                val currentIndex = exoPlayer.currentMediaItemIndex
                // Fila e linear (nao da REPEAT_MODE_ALL) exceto nos estados de item unico
                // (REPEAT_MODE_ONE, onde currentIndex+1 nao existe mas o proprio item repete
                // sozinho - conta como "mesmo conteudo" tambem).
                val sameContentLoop = when {
                    itemCount == 1 -> true
                    currentIndex + 1 < itemCount ->
                        exoPlayer.getMediaItemAt(currentIndex).mediaId ==
                            exoPlayer.getMediaItemAt(currentIndex + 1).mediaId
                    else -> false
                }
                when {
                    armed && !sameContentLoop && remaining in 0..RadioSceneTransitionLeadMs -> {
                        armed = false
                        // Corte seco por padrao (pedido do usuario 22/09/2026: "o resto, corte
                        // seco") - so passa pelo flash quando a cena de origem OU destino for
                        // capa/disco/quadro de fotos (fadeFamily). Cobre TODOS os cortes naturais
                        // do ciclo comum: capa->ambiente, ambiente->capa, ambiente->ambiente
                        // (quando sceneCount=2) e clipe->clipe dentro do pool de ceu do refrao.
                        val nextIndex = if (currentIndex + 1 < itemCount) currentIndex + 1 else currentIndex
                        val sourceId = exoPlayer.getMediaItemAt(currentIndex).mediaId
                        val destId = exoPlayer.getMediaItemAt(nextIndex).mediaId
                        if (sourceId in fadeFamily || destId in fadeFamily) {
                            scrim.flashThroughBlack()
                        }
                    }
                    remaining > RadioSceneTransitionLeadMs + RadioSceneTransitionRearmMarginMs -> {
                        armed = true
                    }
                }
            }
            delay(50)
        }
    }
}

@Composable
private fun RadioMockupVideoBackground(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    // false quando essa instancia esta coberta por outra mostrando o MESMO exoPlayer compartilhado
    // (ex.: FullPlayer aberto por cima da aba Radio) - so entao ela e a UNICA marcada active=true,
    // senao as duas ficariam chamando setVideoTextureView em cada recomposicao, roubando a
    // superficie de video uma da outra (a visivel piscaria/congelaria). Pedido do usuario
    // 22/09/2026 (compartilhar o player entre telas) so vale a pena sem esse cuidado - antes cada
    // tela tinha seu PROPRIO ExoPlayer, entao essa disputa simplesmente nao existia.
    videoActive: Boolean = true,
) {
    // Preenche o quadro inteiro cortando as bordas (RESIZE_MODE_ZOOM) em vez de deixar tarja
    // preta - achado ao vivo 18/09/2026 com o take do dog, que tem proporcao um pouco diferente
    // dos outros takes e aparecia com letterbox sem isso.
    val aspectRatioFrame = remember { mutableStateOf<AspectRatioFrameLayout?>(null) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0 && videoSize.width > 0) {
                    val aspect = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
                    aspectRatioFrame.value?.setAspectRatio(aspect)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            AspectRatioFrameLayout(viewContext).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                val textureView = TextureView(viewContext)
                addView(
                    textureView,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                exoPlayer.setVideoTextureView(textureView)
                aspectRatioFrame.value = this
            }
        },
        update = { frame ->
            aspectRatioFrame.value = frame
            if (videoActive) {
                val textureView = frame.getChildAt(0) as TextureView
                exoPlayer.setVideoTextureView(textureView)
                if (!exoPlayer.isPlaying) exoPlayer.play()
            }
        },
    )
}

// Pedido do usuario 18/09/2026: filtro "old" trocado pelo VHS (radio_filter_vhs) - opacidade 35%,
// velocidade normal (sem necessidade de reduzir como o filtro anterior).
private const val RadioVhsFilterAlpha = 0.35f

@Composable
private fun RadioVhsFilterOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.radio_filter_vhs}")))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier.alpha(RadioVhsFilterAlpha),
        factory = { viewContext ->
            TextureView(viewContext).also { textureView ->
                textureView.isOpaque = false
                exoPlayer.setVideoTextureView(textureView)
            }
        },
        update = { textureView ->
            exoPlayer.setVideoTextureView(textureView)
            if (!exoPlayer.isPlaying) exoPlayer.play()
        },
    )
}

// TESTE (pedido do usuario 22/09/2026): "coloquei uma imagem chamada CTR overlay na pasta do
// projeto, vamos fazer um teste, substituir o filtro overlay que está atualmente por cima das
// imagens e colocar esse CTR, em 25% só pra ver como fica" - troca RadioVhsFilterOverlay (video)
// por essa textura ESTATICA (foto real de perto de uma tela CRT, grade de subpixel RGB) por cima
// das cenas. Ladrilhada (BitmapShader TileMode.REPEAT) em vez de esticada pra tela inteira - o
// padrao e bem fininho, esticar 1 imagem so ia borrar tudo; repetindo em mosaico mantem o
// pontilhado nitido em qualquer tamanho de tela. Comprimida de proposito (pedido do usuario:
// "tirar o peso da imagem comprimindo, deixar bem leve") - original 31.9MB/5472x3648, aqui
// 640x640/~250KB (radio_filter_ctr.jpg), denso o bastante pra nao perder o padrao de pontinhos.
// So um dos dois filtros fica ativo por vez (ver call site em RadioAlbumMockupScene) - e um teste
// visual, facil de voltar pro VHS se nao ficar bom.
// v2 (pedido do usuario 22/09/2026: "EU COLOQUEI UM CTR V2, vamos testar esse outro a 13%?") -
// arquivo original vinha em paisagem (612x400, linhas verticais); girado 90 graus pra retrato
// (400x612, pedido do usuario: "na vertical claro" - a tela do celular e vertical) antes de virar
// recurso. radio_filter_ctr (v1) continua no projeto, sem uso - so trocar a linha de volta pra
// comparar.
private const val RadioCtrOverlayAlpha = 0.18f

@Composable
private fun RadioCtrOverlayTest(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.radio_filter_ctr_v2)
    }
    Canvas(modifier.alpha(RadioCtrOverlayAlpha)) {
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

@Composable
private fun RadioMockupNoiseOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 24f
        val dot = 1.25f
        val columns = (size.width / step).toInt()
        val rows = (size.height / step).toInt()
        for (row in 0..rows) {
            for (column in 0..columns) {
                val hash = (column * 73_856_093) xor (row * 19_349_663)
                val grain = ((hash ushr 8) and 0xFF) / 255f
                if (grain > 0.38f) {
                    val alpha = if (grain > 0.72f) 0.045f else 0.022f
                    drawRect(
                        color = if (grain > 0.72f) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha),
                        topLeft = Offset(column * step, row * step),
                        size = Size(dot, dot),
                    )
                }
            }
        }
    }
}

@Composable
private fun PerspectiveAlbumArtwork(
    uri: Uri?,
    embeddedSourceUri: Uri?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var artwork by remember(uri, embeddedSourceUri) {
        mutableStateOf(peekCachedArtwork(uri, embeddedSourceUri))
    }

    LaunchedEffect(uri, embeddedSourceUri) {
        artwork = peekCachedArtwork(uri, embeddedSourceUri)
            ?: withContext(Dispatchers.IO) {
                embeddedSourceUri?.let { loadEmbeddedArtwork(context, it) }
                    ?: uri?.let { loadScaledArtwork(context, it) }
            }
    }

    val currentArtwork = artwork ?: return
    Canvas(modifier = modifier) {
        val bitmap = currentArtwork.asAndroidBitmap()
        val p1 = Offset(size.width * RadioAlbumMockupCorners[0].x, size.height * RadioAlbumMockupCorners[0].y)
        val p2 = Offset(size.width * RadioAlbumMockupCorners[1].x, size.height * RadioAlbumMockupCorners[1].y)
        val p3 = Offset(size.width * RadioAlbumMockupCorners[2].x, size.height * RadioAlbumMockupCorners[2].y)
        val p4 = Offset(size.width * RadioAlbumMockupCorners[3].x, size.height * RadioAlbumMockupCorners[3].y)
        val destination = floatArrayOf(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
        val cropSide = minOf(bitmap.width, bitmap.height).toFloat()
        val cropLeft = (bitmap.width - cropSide) / 2f
        val cropTop = (bitmap.height - cropSide) / 2f
        val source = floatArrayOf(
            cropLeft, cropTop,
            cropLeft + cropSide, cropTop,
            cropLeft + cropSide, cropTop + cropSide,
            cropLeft, cropTop + cropSide,
        )
        val matrix = Matrix().apply { setPolyToPoly(source, 0, destination, 0, 4) }
        val albumPath = Path().apply {
            val cornerRadius = cropSide * 0.02f
            addRoundRect(
                RectF(cropLeft, cropTop, cropLeft + cropSide, cropTop + cropSide),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW,
            )
            transform(matrix)
        }
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val saveCount = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
            nativeCanvas.clipPath(albumPath)
            nativeCanvas.drawBitmap(
                bitmap,
                matrix,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    alpha = 226
                },
            )
            nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(34, 0, 0, 0)
                },
            )
            nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        p1.x,
                        p1.y,
                        p3.x,
                        p3.y,
                        intArrayOf(
                            android.graphics.Color.argb(58, 20, 14, 10),
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.argb(34, 255, 178, 94),
                        ),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            nativeCanvas.drawPath(
                albumPath,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size.minDimension * 0.008f
                    color = android.graphics.Color.argb(34, 0, 0, 0)
                    maskFilter = BlurMaskFilter(size.minDimension * 0.0025f, BlurMaskFilter.Blur.NORMAL)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                },
            )
            nativeCanvas.restoreToCount(saveCount)
        }
    }
}

// Pedido do usuario 19/09/2026: novo take "quadro de fotos" (mural com polaroids penduradas por
// um barbante) - so entra na rotacao de takes de ambiente quando o usuario tem foto de perfil
// salva (ver UserProfileUiState.photoUri); a foto do usuario ocupa a janela do polaroid do meio,
// que fica em branco quando nao ha foto. Pontos calibrados em cima da imagem de referencia do take
// ("quadro para animar.webp", 941x1672 - mesma proporcao de RadioAlbumMockupAspectRatio). Usa
// exatamente a mesma tecnica/opacidade de PerspectiveAlbumArtwork (perspectiva via
// setPolyToPoly + alpha 226/255 + escurecida/gradiente quente) pra parecer uma foto real
// pendurada ali, nao um sticker colado por cima.
private val RadioProfilePhotoMockupCorners = listOf(
    Offset(0.3634f, 0.4026f), // janela do polaroid em branco: topo-esquerda
    Offset(0.6280f, 0.4008f), // topo-direita
    Offset(0.6280f, 0.5502f), // baixo-direita
    Offset(0.3634f, 0.5520f), // baixo-esquerda
)

@Composable
private fun PerspectiveProfilePhotoOverlay(
    photoUri: Uri?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var photo by remember(photoUri) { mutableStateOf(peekCachedArtwork(photoUri, null)) }

    LaunchedEffect(photoUri) {
        photo = if (photoUri == null) {
            null
        } else {
            peekCachedArtwork(photoUri, null)
                ?: withContext(Dispatchers.IO) { loadScaledArtwork(context, photoUri) }
        }
    }

    val currentPhoto = photo ?: return
    Canvas(modifier = modifier) {
        val bitmap = currentPhoto.asAndroidBitmap()
        val p1 = Offset(size.width * RadioProfilePhotoMockupCorners[0].x, size.height * RadioProfilePhotoMockupCorners[0].y)
        val p2 = Offset(size.width * RadioProfilePhotoMockupCorners[1].x, size.height * RadioProfilePhotoMockupCorners[1].y)
        val p3 = Offset(size.width * RadioProfilePhotoMockupCorners[2].x, size.height * RadioProfilePhotoMockupCorners[2].y)
        val p4 = Offset(size.width * RadioProfilePhotoMockupCorners[3].x, size.height * RadioProfilePhotoMockupCorners[3].y)
        val destination = floatArrayOf(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
        val cropSide = minOf(bitmap.width, bitmap.height).toFloat()
        val cropLeft = (bitmap.width - cropSide) / 2f
        val cropTop = (bitmap.height - cropSide) / 2f
        val source = floatArrayOf(
            cropLeft, cropTop,
            cropLeft + cropSide, cropTop,
            cropLeft + cropSide, cropTop + cropSide,
            cropLeft, cropTop + cropSide,
        )
        val matrix = Matrix().apply { setPolyToPoly(source, 0, destination, 0, 4) }
        val photoPath = Path().apply {
            val cornerRadius = cropSide * 0.02f
            addRoundRect(
                RectF(cropLeft, cropTop, cropLeft + cropSide, cropTop + cropSide),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW,
            )
            transform(matrix)
        }
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val saveCount = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
            nativeCanvas.clipPath(photoPath)
            nativeCanvas.drawBitmap(
                bitmap,
                matrix,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    // ~90% de opacidade (pedido do usuario: "tirar uns 10% de opacidade" pra nao
                    // parecer um sticker colado por cima, e sim uma foto real ali na moldura).
                    alpha = 226
                },
            )
            nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(34, 0, 0, 0)
                },
            )
            nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        p1.x,
                        p1.y,
                        p3.x,
                        p3.y,
                        intArrayOf(
                            android.graphics.Color.argb(58, 20, 14, 10),
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.argb(34, 255, 178, 94),
                        ),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            nativeCanvas.drawPath(
                photoPath,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size.minDimension * 0.008f
                    color = android.graphics.Color.argb(34, 0, 0, 0)
                    maskFilter = BlurMaskFilter(size.minDimension * 0.0025f, BlurMaskFilter.Blur.NORMAL)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                },
            )
            nativeCanvas.restoreToCount(saveCount)
        }
    }
}

// Pedido do usuario 19/09/2026: take da estante com a TV antiga - a arte (radio_scene_take_tv,
// drawable-nodpi) e uma foto estatica com o visor deixado por proposito semi-transparente
// (alpha ~102/255 so ali dentro, resto 100% opaco); pontos extraidos direto do canal alpha da
// PNG (nao estimados a olho como no quadro de polaroids). Por cima do video sorteado (perspectiva
// via setPolyToPoly, igual PerspectiveAlbumArtwork/PerspectiveProfilePhotoOverlay) desenhamos essa
// MESMA png com alpha normal: a moldura/estante opaca cobre qualquer sobra fora do visor, e o
// visor semi-transparente da aquele efeito de estatica/fosforo por cima do video, de graca.
private val RadioTvScreenCorners = listOf(
    Offset(225f / 941f, 556f / 1672f), // topo-esquerda
    Offset(581f / 941f, 544f / 1672f), // topo-direita
    Offset(571f / 941f, 901f / 1672f), // baixo-direita
    Offset(215f / 941f, 873f / 1672f), // baixo-esquerda
)

// Aberturas de serie cortadas em 1:1 (6s cada) - 1 sorteada por ocorrencia do take, nunca mais de
// uma ao mesmo tempo (pedido do usuario 19/09/2026: "vai passar 1 vídeo por take... nunca passar
// mais de um"). Ver sorteio em RadioAlbumMockupScene, com remember(currentOccurrence).
private val RadioTvCutsPool = listOf(
    R.raw.radio_tv_corte_better_call_saul,
    R.raw.radio_tv_corte_familia_addams,
    R.raw.radio_tv_corte_kenan_kel,
    R.raw.radio_tv_corte_maluco_no_pedaco,
    R.raw.radio_tv_corte_sopranos,
)

// Video "de verdade" (nao um bitmap estatico) dentro de uma moldura em perspectiva - diferente de
// PerspectiveAlbumArtwork/PerspectiveProfilePhotoOverlay (que so tem um bitmap pra desenhar), aqui
// precisamos capturar o frame atual de um TextureView pra poder aplicar o MESMO Matrix.setPolyToPoly
// usado nos outros overlays (TextureView.setTransform nao da suporte confiavel a perspectiva de
// verdade, so afim). O TextureView real fica escondido (alpha 0, tamanho pequeno so pra manter a
// decodificacao/textura vivas) e so o bitmap capturado e desenhado, na posicao certa.
private const val RadioTvFrameCaptureIntervalMs = 55L // ~18fps, mesmo frame rate dos cortes

@Composable
private fun PerspectiveTvScreenVideo(
    rawRes: Int,
    corners: List<Offset>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(rawRes) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$rawRes")))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    var textureView by remember(exoPlayer) { mutableStateOf<TextureView?>(null) }
    var frame by remember(exoPlayer) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(textureView) {
        val view = textureView ?: return@LaunchedEffect
        while (isActive) {
            if (view.isAvailable) {
                runCatching { view.bitmap }.getOrNull()?.let { frame = it }
            }
            delay(RadioTvFrameCaptureIntervalMs)
        }
    }

    Box(modifier) {
        AndroidView(
            // So precisa ser grande o bastante pra capturar com nitidez razoavel (fonte e 400x400)
            // - o tamanho aqui nao afeta o tamanho final na tela, que quem manda e o Canvas abaixo.
            modifier = Modifier.size(200.dp).alpha(0f),
            factory = { viewContext ->
                TextureView(viewContext).also {
                    exoPlayer.setVideoTextureView(it)
                    textureView = it
                }
            },
        )
        val bitmap = frame
        if (bitmap != null) {
            Canvas(Modifier.matchParentSize()) {
                val p1 = Offset(size.width * corners[0].x, size.height * corners[0].y)
                val p2 = Offset(size.width * corners[1].x, size.height * corners[1].y)
                val p3 = Offset(size.width * corners[2].x, size.height * corners[2].y)
                val p4 = Offset(size.width * corners[3].x, size.height * corners[3].y)
                val destination = floatArrayOf(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
                val source = floatArrayOf(
                    0f, 0f,
                    bitmap.width.toFloat(), 0f,
                    bitmap.width.toFloat(), bitmap.height.toFloat(),
                    0f, bitmap.height.toFloat(),
                )
                val matrix = Matrix().apply { setPolyToPoly(source, 0, destination, 0, 4) }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        matrix,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
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
private fun ArtistGridCard(
    artist: LocalArtist,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    photoUri: Uri? = null,
    compact: Boolean = false,
) {
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
                .aspectRatio(if (compact) 16f / 9f else 1f),
            iconModifier = Modifier.size(if (compact) 30.dp else 38.dp),
        )
        Spacer(Modifier.height(if (compact) 4.dp else 7.dp))
        Text(
            artist.name,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        )
        if (!compact) {
            Text(
                "${artist.albumCount} albuns",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridCard(
    album: LocalAlbum,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
    // Segunda linha do card - artista por padrao (telas com albuns de varios artistas
    // misturados), sobrescrito com o ano em ArtistDetailScreen (artista ja e o contexto ali,
    // mostrar de novo seria redundante).
    subtitle: String? = album.artist,
    // fillMaxWidth por padrao (grid) - "Mais desse artista" (AlbumDetailScreen) passa uma
    // largura fixa pra caber numa LazyRow horizontal.
    modifier: Modifier = Modifier.fillMaxWidth(),
    // Overrides opcionais de fonte (pedido do usuario 15/09/2026 - ArtistDetailScreen usa um
    // titulo/ano menores que o padrao do grid geral) - null mantem o estilo de sempre.
    titleStyle: TextStyle? = null,
    subtitleStyle: TextStyle? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (album.isVariousArtists) {
            AlbumCoverMosaic(
                songs = album.songs,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (compact) 16f / 9f else 1f),
            )
        } else {
            ArtworkBox(
                uri = album.artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (compact) 16f / 9f else 1f),
                iconModifier = Modifier.size(if (compact) 30.dp else 38.dp),
            )
        }
        Spacer(Modifier.height(if (compact) 4.dp else 7.dp))
        Text(
            album.title,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
            style = titleStyle ?: if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        )
        if (!compact && !subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = subtitleStyle ?: MaterialTheme.typography.labelSmall,
            )
        }
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
    compact: Boolean = false,
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
                    .aspectRatio(if (compact) 16f / 9f else 1f),
                iconModifier = Modifier.size(if (compact) 30.dp else 38.dp),
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
        Spacer(Modifier.height(if (compact) 4.dp else 7.dp))
        Text(
            song.title,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        )
        if (!compact) {
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
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
    // Mesmo dialogo de escolher artista/album reusado pra "Criar nova rádio" (pedido do usuario
    // 22/09/2026, ver popover do botao de 3 pontinhos na aba Radio) - so o titulo muda, o dialogo
    // em si (busca, lista, alternar artista/album) e identico.
    title: String = "Adicionar à rádio",
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
        title = { Text(title) },
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

// Card minimalista (pedido do usuario 22/09/2026: primeiro pras Categorias - "ao invés de mostrar
// várias capas em cada categoria, vamos fazer um card minimalista, estreito, e continuar de 3x3
// por fileira" -, depois estendido pras Radios de verdade - "na rádio, fazer a mesma coisa, deixar
// os cards de cada rádio minimalista, 3x3") - substituiu de vez o card antigo (RadioGridCard,
// removido) que mostrava um mosaico grande de capas (RadioCoverMosaic - ainda usada em
// RadioCoverTicker, so o card em si que sumiu) em cima do nome/descricao. Agora e so nome +
// contagem de musicas num retangulo baixo, sem imagem nenhuma. Paleta neutra igual o resto do app
// (PailerSurfaceHigh) - sem icone algum (pedido do usuario 22/09/2026: "pode tirar esse simbolo de
// nota musical vermelho do card" - era um toque de cor a mais, mas ficou poluindo o minimalismo).
@Composable
private fun MinimalRadioCard(radio: LocalRadio, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(PailerSurfaceHigh)
            // As 3 radios fixas (Surprise Me/Radio recente/Musicas Curtidas, pedido do usuario
            // 22/09/2026: "com cor vermelha no stroke bem fino") ganham um contorno fino na cor
            // primaria (PailerRed) pra se destacar como padrao/fixas das demais, sem precisar de
            // nenhum icone ou texto extra - o resto do card continua identico.
            .then(
                if (radio.isPinned) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // Fonte reduzida (pedido do usuario 22/09/2026: "pode diminuir o tamanho da fonte dos
        // cards da radio pra caber") - nomes mais longos (ex.: "Músicas Curtidas") estouravam o
        // card pequeno/estreito com bodyMedium; tamanho explicito em vez do style padrao pra
        // controlar exatamente o quanto cabe em 2 linhas sem cortar.
        Text(
            radio.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${radio.songs.size} músicas",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
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
    val songId: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: Uri?,
    val artworkSourceUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val isCurrentMedia: Boolean,
)

// Botao de legenda (letra sincronizada sobre o disco, ver HomeLyricsSubtitle) no espaco vago ao
// lado do titulo/artista/album/timecode na Home - toggle manual (ADR-031): liga/desliga
// HomeLyricsSubtitle, e o proprio clique (em ContinueListeningCard/LandscapeCard) dispara a busca
// online sozinho se a musica ainda nao tiver letra carregada.
@Composable
private fun CaptionsToggleButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else PailerCharcoal.copy(alpha = 0.6f),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
            contentDescription = if (enabled) "Ocultar legenda" else "Mostrar legenda",
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// Botao de "reproducao automatica" (imitando o autoplay do Youtube, pedido do usuario
// 15/09/2026) ao lado do botao de legenda - liga por padrao. Ligado, o app toca sozinho o proximo
// album do mesmo artista (ou o proximo artista da mesma categoria, sem outro album) quando a fila
// atual acaba - ver toggleAutoplay/maybeAutoplayNextAlbum no ViewModel.
@Composable
private fun AutoplayToggleButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else PailerCharcoal.copy(alpha = 0.6f),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (enabled) "Desligar reprodução automática" else "Ligar reprodução automática",
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// Foto redonda do artista ao lado do titulo/artista/album na Home (pedido do usuario
// 15/09/2026) - clique abre a pagina do artista. Prioriza a foto override (busca manual/
// automatica na Deezer, ver ArtistDetailScreen/maybeAutoFetchArtistPhoto) e cai pra capa do
// primeiro album do artista com capa quando nao ha override, mesmo fallback usado la.
@Composable
private fun HomeArtistAvatar(artist: LocalArtist, photoUri: Uri?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val artworkSong = remember(artist) { artist.songs.firstOrNull { it.artworkUri != null } }
    val displayUri = photoUri ?: artworkSong?.artworkUri
    val displayEmbeddedSource = if (photoUri != null) null else artworkSong?.contentUri
    ArtworkBox(
        uri = displayUri,
        embeddedSourceUri = displayEmbeddedSource,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        iconModifier = Modifier.size(20.dp),
    )
}

@Composable
private fun ContinueListeningCard(
    content: HomePlaybackCardContent,
    isPlaying: Boolean,
    lyrics: LyricsUiState = LyricsUiState(),
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    captionsEnabled: Boolean,
    onToggleCaptions: () -> Unit,
    autoplayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    artist: LocalArtist? = null,
    artistPhotoUri: Uri? = null,
    onOpenArtist: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    Column(modifier = modifier.fillMaxWidth()) {
        PlaybackStatusTitle(isPlaying = isPlaying)
        if (isLandscape) {
            ContinueListeningLandscapeCard(
                content = content,
                isPlaying = isPlaying,
                lyrics = lyrics,
                onClick = onClick,
                onPlayPauseClick = onPlayPauseClick,
                captionsEnabled = captionsEnabled,
                onToggleCaptions = onToggleCaptions,
                autoplayEnabled = autoplayEnabled,
                onToggleAutoplay = onToggleAutoplay,
                artist = artist,
                artistPhotoUri = artistPhotoUri,
                onOpenArtist = onOpenArtist,
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        ) {
            HomePlaybackArtwork(
                content = content,
                isPlaying = isPlaying,
                lyrics = lyrics,
                onPlayPauseClick = onPlayPauseClick,
                captionsEnabled = captionsEnabled,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artist != null) {
                    HomeArtistAvatar(artist = artist, photoUri = artistPhotoUri, onClick = onOpenArtist)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        content.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        // Um pouco menor que o padrao titleMedium (pedido do usuario 15/09/2026) -
                        // ao lado da foto redonda do artista, o titulo grande demais competia com ela.
                        style = MaterialTheme.typography.titleSmall,
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
                if (content.isCurrentMedia) {
                    Spacer(Modifier.width(8.dp))
                    AutoplayToggleButton(enabled = autoplayEnabled, onClick = onToggleAutoplay)
                    Spacer(Modifier.width(8.dp))
                    CaptionsToggleButton(enabled = captionsEnabled, onClick = onToggleCaptions)
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningLandscapeCard(
    content: HomePlaybackCardContent,
    isPlaying: Boolean,
    lyrics: LyricsUiState,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    captionsEnabled: Boolean,
    onToggleCaptions: () -> Unit,
    autoplayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    artist: LocalArtist? = null,
    artistPhotoUri: Uri? = null,
    onOpenArtist: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val compactLandscape = configuration.screenHeightDp < 420
    val cardHeight = if (compactLandscape) 194.dp else 232.dp
    val artworkSize = if (compactLandscape) 176.dp else 212.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(PailerSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            HomePlaybackArtwork(
                content = content,
                isPlaying = isPlaying,
                lyrics = lyrics,
                onPlayPauseClick = onPlayPauseClick,
                captionsEnabled = captionsEnabled,
                modifier = Modifier.size(artworkSize),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(
            modifier = Modifier.weight(0.54f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artist != null) {
                    HomeArtistAvatar(artist = artist, photoUri = artistPhotoUri, onClick = onOpenArtist)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    content.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (content.isCurrentMedia) {
                    Spacer(Modifier.width(8.dp))
                    AutoplayToggleButton(enabled = autoplayEnabled, onClick = onToggleAutoplay)
                    Spacer(Modifier.width(8.dp))
                    CaptionsToggleButton(enabled = captionsEnabled, onClick = onToggleCaptions)
                }
            }
            Text(
                listOf(content.artist, content.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isPlaying && content.durationMs > 0) {
                    "${formatDuration(content.positionMs)} de ${formatDuration(content.durationMs)}"
                } else if (isPlaying) {
                    "Tocando agora"
                } else {
                    "Retomar em ${formatDuration(content.positionMs)}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            if (content.durationMs > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = {
                        (content.positionMs.toFloat() / content.durationMs.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                )
            }
        }
    }
}

@Composable
private fun HomePlaybackArtwork(
    content: HomePlaybackCardContent,
    isPlaying: Boolean,
    lyrics: LyricsUiState,
    onPlayPauseClick: () -> Unit,
    captionsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Tracinho vertical na ponta da barra de progresso (pedido do usuario 15/09/2026): some depois
    // de 3s tocando pra nao poluir o disco, mas volta a aparecer a cada novo toque no disco
    // (passar o dedo/clicar-arrastar, detectado sem consumir o evento - pass = Initial - pra nao
    // atrapalhar o clique do card nem do botao de play por baixo) e fica sempre visivel pausado.
    var seekTickPing by remember { mutableStateOf(0) }
    var showSeekTick by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying, seekTickPing) {
        if (isPlaying) {
            showSeekTick = true
            delay(3_000)
            showSeekTick = false
        } else {
            showSeekTick = true
        }
    }
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                seekTickPing++
            }
        },
    ) {
        SpinningVinylArtwork(
            uri = content.artworkUri,
            embeddedSourceUri = content.artworkSourceUri,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize(),
        )
        HomeLyricsSubtitle(
            lyrics = lyrics,
            songId = content.songId,
            positionMs = content.positionMs,
            isPlaying = isPlaying,
            enabled = captionsEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 18.dp),
        )
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PailerCharcoal.copy(alpha = 0.75f))
                    .clickable(onClick = onPlayPauseClick),
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
        val progress = if (content.durationMs > 0) {
            (content.positionMs.toFloat() / content.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        // A barra inteira (trilho + preenchimento + tracinho) some junto com o "Tocando agora"
        // (PlaybackStatusTitle) - mesmo showSeekTick, nao so o tracinho isolado.
        AnimatedVisibility(
            visible = showSeekTick,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    // Borda branca fina em volta do vermelho - sem ela o tracinho fica "vermelho
                    // sobre vermelho" (mesma cor do preenchimento por baixo) e some de vista.
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White)
                            .padding(1.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeLyricsSubtitle(
    lyrics: LyricsUiState,
    songId: Long?,
    positionMs: Long,
    isPlaying: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (
        !enabled ||
        !isPlaying ||
        songId == null ||
        lyrics.songId != songId ||
        lyrics.lyrics.songId != songId ||
        lyrics.isLoading ||
        lyrics.isFetching ||
        lyrics.lyrics.isEmpty ||
        !lyrics.lyrics.synced
    ) return
    val lines = lyrics.lyrics.lines
    val currentIndex = LrcParser.currentLineIndex(lines, positionMs)
    val currentText = lines.getOrNull(currentIndex)?.text?.takeIf { it.isNotBlank() } ?: return

    Text(
        text = currentText,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

// Legenda "cinematografica" (pedido do usuario 20/09/2026), so usada enquanto um take de ceu do
// refrao esta tocando (ver RadioMockupSequencerState.chorusCaptionActive): maior, bold de peso,
// sem serifa, em CAPS, trocando de linha com uma animacao de entrada/saida (fade + escala) em vez
// de so aparecer/sumir seco - mesma fonte de linha atual de HomeLyricsSubtitle (LrcParser.
// currentLineIndex), so o estilo visual muda.
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun RadioChorusCaption(
    lyrics: LyricsUiState,
    songId: Long?,
    positionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    if (
        !isPlaying ||
        songId == null ||
        lyrics.songId != songId ||
        lyrics.lyrics.songId != songId ||
        lyrics.isLoading ||
        lyrics.isFetching ||
        lyrics.lyrics.isEmpty ||
        !lyrics.lyrics.synced
    ) return
    val lines = lyrics.lyrics.lines
    val currentIndex = LrcParser.currentLineIndex(lines, positionMs)
    val currentText = lines.getOrNull(currentIndex)?.text?.takeIf { it.isNotBlank() } ?: return

    AnimatedContent(
        targetState = currentText,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.82f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.1f))
        },
        label = "radioChorusCaption",
    ) { text ->
        Text(
            text = text.uppercase(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            // Maior e alinhada a esquerda (pedido do usuario 20/09/2026, ajuste depois de ver ao
            // vivo) - era 26sp/centralizada. Sem limite de linhas nem ellipsis (pedido do usuario
            // depois: "nunca cortar a letra... pode usar o espaço vertical, já que o horizontal
            // ficou limitado") - com a fonte maior e alinhada a esquerda uma frase grande pode
            // precisar de 3+ linhas, e isso e melhor do que cortar.
            fontSize = 38.sp,
            lineHeight = 42.sp,
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Start,
        )
    }
}

// Legenda do take "quadro de fotos" (a foto do OUVINTE, nao da musica - pedido do usuario
// 21/09/2026: "naquela tela onde aparece a foto de perfil do ouvinte... no rodapé, lá em baixo,
// vai aparecer nome dele, embaixo do nome o level > plays > horas"). Mesma identidade visual do
// resto do overlay da radio: titulo branco bold igual RadioAlbumMockupScene, level em destaque na
// cor primaria (mesma logica de ListeningStatsCard em Configuracoes > Meu perfil), stats
// secundarios em branco 76% opaco igual a linha de artista/album do header.
@Composable
private fun RadioListenerStatsFooter(
    name: String,
    level: Int,
    songsCompleted: Int,
    listeningMs: Long,
    modifier: Modifier = Modifier,
) {
    // Pedido do usuario 22/09/2026: "as informações de ouvinte na tela da foto de perfil, tem que
    // ficar bem mais pra baixo, e alinhadas a esquerda" - horizontalAlignment.Start (era
    // CenterHorizontally); a posicao mais pra baixo fica no CALL SITE (RadioAlbumMockupScene,
    // Spacer antes desse composable), nao aqui dentro.
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            name.ifBlank { "Ouvinte" },
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Nível $level",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$songsCompleted músicas tocadas • ${formatListeningHours(listeningMs)} ouvidas",
            color = Color.White.copy(alpha = 0.76f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val iconButtonSize = if (isLandscape) 40.dp else 48.dp
    val iconSize = if (isLandscape) 21.dp else 24.dp
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
                .padding(
                    horizontal = if (isLandscape) 12.dp else 14.dp,
                    vertical = if (isLandscape) 5.dp else 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBox(
                uri = player.artworkUri,
                embeddedSourceUri = player.artworkSourceUri,
                modifier = Modifier.size(if (isLandscape) 40.dp else 48.dp),
            )
            Spacer(Modifier.width(if (isLandscape) 10.dp else 12.dp))
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
                IconButton(onClick = onDislike, enabled = player.songId != null, modifier = Modifier.size(iconButtonSize)) {
                    Icon(
                        Icons.Filled.ThumbDown,
                        contentDescription = "Nao curti essa faixa na radio",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
            IconButton(onClick = onToggleFavorite, enabled = player.songId != null, modifier = Modifier.size(iconButtonSize)) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Curtir faixa",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(iconSize),
                )
            }
            // Modo radio: nao tem anterior/proxima (fila ao vivo nao suporta pular livremente,
            // ver ADR) e o play/pause vira mute (a radio "ao vivo" continua avancando, so
            // silencia - ver toggleRadioMute) + um botao pra sair de vez (power off).
            if (isRadio) {
                IconButton(onClick = onToggleMute, modifier = Modifier.size(iconButtonSize)) {
                    Icon(
                        if (player.isRadioMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (player.isRadioMuted) "Ativar som" else "Mutar",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(iconSize),
                    )
                }
                IconButton(onClick = onExitRadio, modifier = Modifier.size(iconButtonSize)) {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        contentDescription = "Sair da radio",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(iconSize),
                    )
                }
            } else {
                IconButton(onClick = onPrevious, modifier = Modifier.size(iconButtonSize)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(iconSize),
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(iconButtonSize)) {
                    Icon(
                        if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(iconSize),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(iconButtonSize)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Proxima",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(iconSize),
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
    // Nulo quando o player esta mostrando uma musica comum (nao radio), ou por 1 frame raro no
    // instante em que uma sessao de radio esta comecando - ver radioMockup?.let mais abaixo.
    radioMockup: RadioMockupSharedState?,
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
    onAutoUpgradeLyrics: () -> Unit = {},
    onEditLyrics: () -> Unit = {},
    onRemoveLyrics: () -> Unit = {},
    onEditTrack: () -> Unit = {},
    albumSongs: List<LocalSong> = emptyList(),
    onOpenAlbum: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    isAlbumSongFavorite: (LocalSong) -> Boolean = { false },
    onToggleAlbumSongFavorite: (LocalSong) -> Unit = {},
    onPlayAlbumSong: (LocalSong) -> Unit = {},
) {
    val context = LocalContext.current
    val isRadio = player.activeRadioName.isNotBlank()
    val cinematicIdle = rememberRadioCinematicIdleState(enabled = isRadio)
    if (isRadio) {
        RadioCinematicSystemBars(dim = cinematicIdle.dimSystemBars)
    }
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
                        onEditTrack = onEditTrack,
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
                // radioMockup pode chegar nulo por 1 frame bem no instante em que a sessao esta
                // comecando (activeRadioName e hasMedia nem sempre atualizam juntos - ver
                // comentario equivalente em HomeScreen); so entao essa cena fica vazia por
                // instante em vez de derrubar o app num !! nulo.
                radioMockup?.let { mockup ->
                    RadioAlbumMockupScene(
                        player = player,
                        lyrics = lyrics,
                        radioMockup = mockup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(RadioAlbumMockupAspectRatio)
                            .clip(RoundedCornerShape(8.dp)),
                        cinematicIdle = cinematicIdle,
                    )
                }
            } else {
                // Arrasta pro lado: pagina 0 = capa (chamada identica de ArtworkBox), pagina 1 = letra.
                // Travado na MESMA caixa fillMaxWidth().aspectRatio(1f) que a capa ocupava, entao
                // titulo/seek/controles/"Mais de <album>" ficam na posicao exata de sempre.
                val pagerState = rememberPagerState(pageCount = { 2 })
                // Arrastar ate a pagina da letra ja e um pedido implicito de ve-la - dispara a
                // busca online sozinho quando chega la sem letra SINCRONIZADA ainda (vazia OU so
                // com texto sem timestamp - pedido do usuario 17/09/2026, mesmo motivo do
                // LaunchedEffect equivalente em HomeScreen), sem precisar de botao "Buscar
                // online" (pedido do usuario 15/09/2026). onAutoUpgradeLyrics() so troca a letra
                // atual se achar uma versao sincronizada (ou se nao havia nada antes), ver
                // autoUpgradeLyricsSyncIfNeeded() no ViewModel. So tenta 1 vez por musica: se
                // falhar (ou nao achar nada com sincronia), lyrics.message so fica preenchido se
                // a letra estava vazia - com letra sem sincronia ja carregada, so para de tentar
                // sem mensagem de erro nenhuma (nao e uma falha, so nao tinha nada melhor).
                LaunchedEffect(pagerState.currentPage, player.songId, lyrics.songId, lyrics.isLoading, lyrics.lyrics.synced, lyrics.message) {
                    if (pagerState.currentPage == 1 &&
                        player.songId != null &&
                        lyrics.songId == player.songId &&
                        !lyrics.isLoading &&
                        !lyrics.isFetching &&
                        !lyrics.lyrics.synced &&
                        lyrics.message == null
                    ) {
                        onAutoUpgradeLyrics()
                    }
                }
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
    onEditTrack: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Mais opções",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (hasLyrics) "Editar letra" else "Adicionar letra") },
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
            DropdownMenuItem(
                text = { Text("Editar faixa") },
                onClick = { expanded = false; onEditTrack() },
            )
        }
    }
}

// Pagina de letra do player (arrasta pro lado a partir da capa). Estados: carregando / vazio (a
// busca online ja disparou sozinha ao chegar aqui - so mostra "Adicionar letra" se ela falhar, ver o
// LaunchedEffect em FullPlayer) / buscando / com letra sincronizada (destaca e rola a linha atual
// usando player.positionMs, que ja pulsa pelo polling existente - sem timer novo) / letra sem
// sincronia (texto rolavel).
@Composable
private fun LyricsPage(
    lyrics: LyricsUiState,
    positionMs: Long,
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
                Button(onClick = onEdit) { Text("Adicionar letra") }
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
                                LyricsSource.LYRICS_OVH -> "lyrics.ovh"
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
                placeholder = { Text("Cole ou escreva a letra aqui (aceita formato .lrc com tempos).") },
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
                trackNumber = songs.indexOf(song) + 1,
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

// Previa da fila ("A seguir") no espaco vazio sobrando embaixo do disco na Home - pedido do
// usuario 15/09/2026: fonte pequena e cards estreitos, so pra preencher o vao ate o fim da tela
// sem competir visualmente com o card do disco em cima.
@Composable
private fun HomeUpNextPreview(upcomingTracks: List<UpcomingTrack>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "A seguir",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        upcomingTracks.take(4).forEachIndexed { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PailerGunmetal.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (track.albumTrackNumber ?: (index + 1)).toString().padStart(2, '0'),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    track.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RadioUpcomingQueue(upcomingTracks: List<UpcomingTrack>) {
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
                    (track.albumTrackNumber ?: (index + 1)).toString().padStart(2, '0'),
                    modifier = Modifier.width(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    track.label,
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
private val backdropArtworkCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(96)
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

private fun peekCachedBackdropArtwork(uri: Uri?, embeddedSourceUri: Uri?): androidx.compose.ui.graphics.ImageBitmap? {
    embeddedSourceUri?.let { backdropArtworkCache.get("embedded:$it") }?.let { return it }
    uri?.let { backdropArtworkCache.get(it.toString()) }?.let { return it }
    return null
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
    var image by remember(uri, embeddedSourceUri) {
        mutableStateOf(peekCachedBackdropArtwork(uri, embeddedSourceUri))
    }

    LaunchedEffect(uri, embeddedSourceUri) {
        if (image == null) {
            image = withContext(Dispatchers.IO) {
                embeddedSourceUri?.let { loadEmbeddedBackdropArtwork(context, it) }
                    ?: uri?.let { loadBackdropArtwork(context, it) }
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.32f,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(16.dp) else Modifier,
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor.copy(alpha = 0.42f)),
            )
        }
    }
}

@Composable
private fun BackgroundGrainOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.018f,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { 7.dp.toPx() }
    val dotPx = with(density) { 0.8.dp.toPx() }.coerceAtLeast(1f)
    Canvas(modifier.fillMaxSize()) {
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else stepPx * 0.5f
            var column = 0
            while (x < size.width) {
                val seed = (row * 31 + column * 17) % 13
                if (seed < 7) {
                    val color = if (seed % 2 == 0) {
                        Color.White.copy(alpha = alpha)
                    } else {
                        Color.Black.copy(alpha = alpha * 0.65f)
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(dotPx, dotPx),
                    )
                }
                x += stepPx
                column++
            }
            y += stepPx
            row++
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
private fun PlaybackStatusTitle(isPlaying: Boolean, modifier: Modifier = Modifier) {
    // "Continuar ouvindo" fica fixo enquanto pausado. Ao dar play, "Tocando agora" entra com
    // animação, fica 3s e recolhe (some) - pedido do usuario 15/09/2026.
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            visible = true
            delay(3_000)
            visible = false
        } else {
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        SectionTitle(if (isPlaying) "Tocando agora" else "Continuar ouvindo")
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
internal fun appBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        lerp(PailerSurfaceHigh, PailerRed, 0.07f),
        lerp(PailerSurface, PailerRed, 0.04f),
        lerp(MaterialTheme.colorScheme.background, PailerRed, 0.05f),
        lerp(PailerCharcoal, PailerRed, 0.09f),
    )
)

// Banner do topo da aba Radio (pedido do usuario 22/09/2026: "o Gif que fica em cima, vamos
// trocar pra aquele gif que mostra o nico e a fran de cima, só que 1:1 e centralizando eles 2" -
// substitui de vez o antigo DayPeriodGifBackground, um .gif generico de ceu que trocava por
// horario do dia). radio_scene_take_de_cima.mp4 (720x1280, plano aereo dos dois na sala de vinil)
// e o MESMO clipe usado como um dos takes de ambiente da radio imersiva (rememberRadioMockupSequencer)
// - aqui toca sozinho, em loop, mudo, so como pano de fundo da lista. Mesmo padrao de
// ExoPlayer+TextureView+AspectRatioFrameLayout(RESIZE_MODE_ZOOM) de RadioMockupVideoBackground:
// o container de fora ja forca aspectRatio(1f) (ver PlaylistsScreen), e RESIZE_MODE_ZOOM enche
// esse quadrado cortando os excessos (aqui, as laterais - o video e mais alto que largo) SEM
// esticar, sempre centralizado - com os dois (Nico em cima escrevendo no laptop, Fran embaixo
// escrevendo no caderno) ocupando o miolo vertical do enquadramento original, o corte central ja
// deixa os dois dentro do quadro.
@Composable
private fun RadioHostsVideoBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.radio_scene_take_de_cima}")))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    val aspectRatioFrame = remember { mutableStateOf<AspectRatioFrameLayout?>(null) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0 && videoSize.width > 0) {
                    val aspect = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
                    aspectRatioFrame.value?.setAspectRatio(aspect)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            AspectRatioFrameLayout(viewContext).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                val textureView = TextureView(viewContext)
                addView(
                    textureView,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                exoPlayer.setVideoTextureView(textureView)
                aspectRatioFrame.value = this
            }
        },
        update = { frame ->
            aspectRatioFrame.value = frame
            val textureView = frame.getChildAt(0) as TextureView
            exoPlayer.setVideoTextureView(textureView)
            if (!exoPlayer.isPlaying) exoPlayer.play()
        },
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
