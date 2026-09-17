package com.pailer.localtune.player

import android.app.PendingIntent
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pailer.localtune.R
import com.pailer.localtune.cast.CastMediaHttpServer
import com.pailer.localtune.cast.LocalWifiAddress
import com.pailer.localtune.dlna.DlnaDevice
import com.pailer.localtune.dlna.DlnaPlaybackBridge
import com.pailer.localtune.dlna.SsdpDiscovery
import com.pailer.localtune.player.cast.CastPlaybackBridge
import com.pailer.localtune.data.AlbumGenreSuggestionRepository
import com.pailer.localtune.data.AlbumMetadataEdit
import com.pailer.localtune.data.AppFolderRepository
import com.pailer.localtune.data.ArtistNewsCard
import com.pailer.localtune.data.ArtistNewsRepository
import com.pailer.localtune.data.BackupRepository
import com.pailer.localtune.data.BackupScheduler
import com.pailer.localtune.data.BroadcastFeedRepository
import com.pailer.localtune.data.DuplicateArtistGroup
import com.pailer.localtune.data.LocalAlbum
import com.pailer.localtune.data.LocalArtist
import com.pailer.localtune.data.LocalRadio
import com.pailer.localtune.data.LocalSong
import com.pailer.localtune.data.Lyrics
import com.pailer.localtune.data.LyricsRepository
import com.pailer.localtune.data.LyricsSource
import com.pailer.localtune.data.MusicLibraryRepository
import com.pailer.localtune.data.ArtworkCandidate
import com.pailer.localtune.data.PendingTagChange
import com.pailer.localtune.data.RadioBulletinMode
import com.pailer.localtune.data.RadioBulletinSettings
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioScriptLine
import com.pailer.localtune.data.RadioScriptSource
import com.pailer.localtune.data.RadioSpeaker
import com.pailer.localtune.data.LatestReleaseInfo
import com.pailer.localtune.data.UpdateCheckRepository
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
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
    // Uri do PROPRIO arquivo de audio tocando (nao a capa) - permite extrair a capa embutida
    // por faixa em vez da capa compartilhada por ALBUM_ID do MediaStore (`artworkUri` acima),
    // que da errado em compilacoes com varios artistas sob o mesmo nome de album. Ver
    // LocalAlbum.isVariousArtists e `embeddedSourceUri` em ArtworkBox (LocalTuneApp.kt).
    val artworkSourceUri: android.net.Uri? = null,
    val playbackSource: String = "",
    val activeRadioName: String = "",
    val currentNewsHeadline: String = "",
    val upcomingTracks: List<String> = emptyList(),
    val isPlaying: Boolean = false,
    val isRadioMuted: Boolean = false,
    val autoplayEnabled: Boolean = true,
    val hasMedia: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    // Aviso "voce ainda esta ai?" do timer de inatividade da radio (ver armSleepTimer/
    // triggerSleepCheck em LocalTuneViewModel) - true mostra o dialogo em LocalTuneApp.kt.
    val sleepCheckPending: Boolean = false,
)

// Um unico botao/seletor pra "transmitir pra TV" (ver HeaderCastButton em LocalTuneApp.kt) que
// lista os dois tipos de dispositivo juntos - Google Cast (Chromecast/Google TV) e UPnP/DLNA
// (TVs que nao falam Cast de verdade, ex.: LG webOS testada em 12/09/2026).
sealed class RemoteDeviceEntry {
    abstract val label: String

    data class Cast(val routeId: String, override val label: String) : RemoteDeviceEntry()
    data class Dlna(val device: DlnaDevice) : RemoteDeviceEntry() {
        override val label: String get() = device.friendlyName
    }
}

data class RemoteDeviceUiState(
    val isScanning: Boolean = false,
    val devices: List<RemoteDeviceEntry> = emptyList(),
    val connectedLabel: String? = null,
)

// Retorno de LocalTuneViewModel.dislikeCurrentRadioSong() - so preenchido quando a faixa
// tocando foi mesmo trocada por outra do album (null quando pulou pra proxima da fila por
// falta de substituto). A UI (LocalTuneApp.kt) usa isso pra corrigir a MESMA posicao no
// snapshot local `radioSession` ("Sequencia ao vivo") - controller.replaceMediaItem so muda a
// fila de verdade do ExoPlayer, radioSession e um estado de UI separado que nao sabe da troca
// sozinho.
data class RadioDislikeResult(val index: Int, val replacement: LocalSong)

// Letra da musica tocando, exibida na tela do player (arrasta pro lado: capa <-> letra). Ver
// LyricsRepository - offline primeiro (tag embutida / letra colada), busca online (LRCLIB) so
// sob demanda. Carregada pela UI via LaunchedEffect(player.songId), nunca do polling/init (ADR-020).
data class LyricsUiState(
    val songId: Long? = null,
    val lyrics: Lyrics = Lyrics.EMPTY,
    val isLoading: Boolean = false,
    val isFetching: Boolean = false,
    // "Letra nao encontrada online" ou erro - some no proximo load/fetch bem-sucedido.
    val message: String? = null,
    val editorOpen: Boolean = false,
    val editorDraft: String = "",
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

data class AlbumArtworkUiState(
    val albumsWithoutArtwork: List<LocalAlbum> = emptyList(),
    val isScanning: Boolean = false,
    val activeAlbumKey: String? = null,
    val isSearching: Boolean = false,
    val candidates: List<ArtworkCandidate> = emptyList(),
    val selectedCandidate: ArtworkCandidate? = null,
    val isDownloadingSelection: Boolean = false,
    val isApplying: Boolean = false,
    val message: String? = null,
    // Incrementado a cada capa gravada com sucesso - a UI observa isso pra saber quando invalidar
    // o cache de bitmaps em memoria (a Uri de albumart nao muda, so o que ela aponta por tras).
    val appliedVersion: Int = 0,
)

// Estado do buscador de foto de artista (Deezer, ver MusicLibraryRepository.searchArtistPhotoCandidates)
// - mesmo padrao do AlbumArtworkUiState, mas sem permissao de escrita no MediaStore: a foto fica
// so num arquivo do proprio app, entao nao ha etapa de "createWriteRequest" aqui.
data class ArtistPhotoUiState(
    val activeArtistKey: String? = null,
    val isSearching: Boolean = false,
    val candidates: List<ArtworkCandidate> = emptyList(),
    val selectedCandidate: ArtworkCandidate? = null,
    val isDownloadingSelection: Boolean = false,
    val isApplying: Boolean = false,
    val message: String? = null,
    // Incrementado a cada foto aplicada/removida - a UI usa isso pra invalidar o bitmap em cache
    // (a Uri do arquivo local nao muda de nome, so o conteudo por tras).
    val appliedVersion: Int = 0,
)

// Backup manual/automatico de favoritos, overrides de album/artista e fotos de artista (ver
// BackupRepository) - pedido do usuario 07/09/2026 depois de perder esses dados num
// "adb uninstall" usado pra debug. hasDestination indica se ja existe um arquivo escolhido pelo
// usuario (SAF) pro backup automatico diario escrever sozinho (ver BackupScheduler).
data class BackupUiState(
    val hasDestination: Boolean = false,
    val usingOfficialFolder: Boolean = false,
    val destinationDescription: String? = null,
    val lastBackupAtMillis: Long = 0L,
    val isWorking: Boolean = false,
    val message: String? = null,
)

// Auto-atualizacao fora da Play Store (pedido do usuario 15/09/2026, ver UpdateCheckRepository) -
// popup "Nova versao disponivel" ao abrir o app, com download + instalacao direto de dentro dele
// (nao so um link pro navegador). downloadedApkFile preenchido = pronto pra abrir o instalador
// (ver LocalTuneApp.kt, precisa de Context/FileProvider que o ViewModel nao tem acesso direto).
data class UpdateUiState(
    val isChecking: Boolean = false,
    val latestRelease: LatestReleaseInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkFile: File? = null,
    val dismissed: Boolean = false,
    val message: String? = null,
)

// Pasta oficial do Pailer FM (ver AppFolderRepository) - onde o app organiza Backup, Logs,
// Redator Local e Pacote de Vozes. folderName nulo = usuario ainda nao escolheu nenhuma.
data class AppFolderUiState(
    val folderName: String? = null,
)

data class UserProfileUiState(
    val name: String = "",
    val birthday: String = "",
    val photoUri: Uri? = null,
    val appliedVersion: Int = 0,
    val message: String? = null,
)

// Sessao "arraste pra revelar" da Home com noticias/curiosidades dos artistas favoritados (ver
// ArtistNewsRepository) - pedido do usuario 06/09/2026. hasLoaded fica true mesmo em caso de erro
// ou lista vazia, pra loadArtistNewsIfNeeded() nao ficar refazendo a busca toda vez que o painel
// abre de novo (so recarrega se favoriteArtistNames mudar OU o cache tiver ficado velho - ver
// loadedAtMillis e checagem em loadArtistNewsIfNeeded).
data class ArtistNewsUiState(
    val cards: List<ArtistNewsCard> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val loadedForArtistNames: List<String> = emptyList(),
    // Quando o cache atual foi carregado (System.currentTimeMillis()) - usado pra decidir se ja
    // passou o proximo "5 da manha" desde entao (pedido do usuario 07/09/2026: atualizar
    // diariamente as 5h). Nao e um agendamento de verdade rodando em segundo plano (o painel so
    // existe dentro do app, nao ha nada pra mostrar uma notificacao nova com o app fechado) - so
    // garante que a PRIMEIRA vez que o painel abre depois das 5h de um novo dia, busca de novo em
    // vez de servir o cache do dia anterior.
    val loadedAtMillis: Long = 0L,
)

data class RadioBulletinUiState(
    val settings: RadioBulletinSettings = RadioBulletinSettings(),
)

// Estado do buffer de boletins prontos (bulletinBuffer/refillBulletinBuffer, ver ADR-019),
// exposto pra UI acompanhar o preparo em segundo plano em tempo real - pedido do usuario
// (03/09/2026): quer ver quantos boletins ja estao prontos, quantos estao sendo escritos agora,
// alem de poder pausar/resetar o preparo.
data class RadioBulletinBufferUiState(
    val readyCount: Int = 0,
    val targetCount: Int = 3,
    val scriptReadyCount: Int = 0,
    val scriptTargetCount: Int = 0,
    val scriptRefillThreshold: Int = 0,
    val isPreparing: Boolean = false,
    val isPaused: Boolean = false,
    val statusMessage: String? = null,
    // So usado hoje pra sinalizar que a busca no feed remoto comecou (ver refillBulletinBuffer).
    val progressPercent: Int? = null,
    // Pedido do usuario (03/09/2026): botao de reproduzir o primeiro boletim pronto do buffer,
    // sem precisar entrar numa radio pra testar - ver playReadyBufferedBulletin().
    val isPlayingPreview: Boolean = false,
    // true so quando existe audio de verdade pra tocar (nao so texto pronto) - readyCount conta
    // qualquer item com SCRIPT pronto, inclusive roteiro padrao de fallback (que nunca tem
    // audio). Sem esse campo separado o botao ficava habilitado mas mudo ao tocar num item sem
    // audio (achado 03/09/2026, usuario relatou "reproduzir previa nao esta funcionando").
    val hasPlayableAudio: Boolean = false,
    // Posicoes (dentre as bolinhas preenchidas, 0..readyCount-1) cujo roteiro caiu no fallback
    // deterministico (RadioScriptSource.Fallback - Gemini E redator local falharam os dois, ou
    // nenhum dos dois esta disponivel no momento) - pintadas de amarelo na UI em vez da cor
    // normal, pra identificar de relance quando o boletim que vai tocar e o roteiro
    // generico/pobre em vez do bate-bola de verdade (pedido do usuario 05/09/2026). Ver
    // fixFallbackBulletins()/scheduleFallbackAutoFix().
    val fallbackSlots: Set<Int> = emptySet(),
    val hasFallback: Boolean = false,
    // Posicoes cujo roteiro E voz vieram 100% do Gemini (scriptFromGemini && voiceFromGemini em
    // PreparedBulletin) - pintadas de verde; qualquer outro caso pronto
    // (fallback a parte) fica azul, INCLUSIVE quando nenhum recurso Gemini esta ligado (pedido do
    // usuario 10/09/2026: "bolinha verde pra 100% Gemini, azul pra parcial com voz local" tomado
    // ao pe da letra - azul e o estado padrao/normal agora, verde e a excecao que so acende
    // quando os dois pedacos vieram da nuvem). fallbackSlots sempre vence sobre isso na UI
    // (RadioBulletinBufferStatusCard) - um boletim so entra em fallback quando Gemini E redator
    // local falharam os dois, entao fallback e sempre pior que "so nao usou Gemini".
    val fullGeminiSlots: Set<Int> = emptySet(),
)

// Um item do buffer UNICO de boletins (ver bulletinBuffer/refillBulletinBuffer). Todo item vem
// pronto (roteiro + audio) do feed remoto (BroadcastFeedRepository) - se o WAV nao existir/nao
// for tocavel, o intervalo e cancelado sem alternativa (ver speakNextNewsBreak). Campos
// scriptFromGemini/voiceFromGemini mantidos so por compatibilidade com manifests antigos salvos
// em disco (loadCoreBufferManifest) - sempre false pra itens novos desde a remocao do redator/
// voz local e Gemini (16/09/2026).
private data class PreparedBulletin(
    val script: RadioScript,
    val file: File?,
    val scriptFromGemini: Boolean = false,
    val voiceFromGemini: Boolean = false,
)

class LocalTuneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicLibraryRepository(application)
    private val backupRepository = BackupRepository(application)
    private val appFolderRepository = AppFolderRepository(application)
    private val broadcastFeedRepository = BroadcastFeedRepository(application)
    private val artistNewsRepository = ArtistNewsRepository(application)
    private val genreSuggestionRepository = AlbumGenreSuggestionRepository()
    private val updateCheckRepository = UpdateCheckRepository(application)
    private val lyricsRepository = LyricsRepository(application)
    private val historyPrefs = application.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
    private val favoritePrefs = application.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val radioPrefs = application.getSharedPreferences("radio_bulletins", Context.MODE_PRIVATE)
    private val profilePrefs = application.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    // Cast pra TV: o controller local (ExoPlayer/MediaController) continua tocando NORMALMENTE
    // (so mudo) enquanto uma sessao remota (Google Cast OU DLNA/UPnP - ver RemotePlaybackBridge)
    // estiver conectada - ele continua sendo a unica fonte de verdade de fila/posicao/
    // proximo-anterior de sempre. remoteBridge so ESPELHA (ver mirrorToRemoteIfNeeded, chamado em
    // playerListener.onEvents): toda vez que o controller local troca de faixa ou muda play/pause,
    // manda o mesmo comando pro destino remoto. Isso evita duplicar toda a logica de fila/radio so
    // pra falar com Cast/DLNA - ver docs/DECISIONS.md ADR-009 (motivo de nao arriscar reescrever o
    // caminho local de reproducao). DLNA existe porque nem toda "TV com cast" fala o protocolo
    // Google Cast de verdade (ex.: LG webOS testada em 12/09/2026 so respondia via UPnP).
    private var remoteHttpServer: CastMediaHttpServer? = null
    private var remoteBridge: RemotePlaybackBridge? = null
    private var remoteHttpBaseUrl: String? = null
    private var remoteMirroredMediaId: String? = null
    private var remoteMirroredIsPlaying: Boolean? = null
    var remoteDeviceState = androidx.compose.runtime.mutableStateOf(RemoteDeviceUiState())
        private set
    private val mediaRouter: MediaRouter by lazy { MediaRouter.getInstance(getApplication()) }
    private val castRouteSelector: MediaRouteSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    // So atualiza a lista quando o MediaRouter avisa de uma mudanca (rota Cast apareceu/sumiu/
    // mudou) - a descoberta em si (buscar na rede) e ligada/desligada por scanForRemoteDevices()/
    // stopScanningRemoteDevices() via CALLBACK_FLAG_REQUEST_DISCOVERY, nao aqui.
    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshCastRouteEntries()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshCastRouteEntries()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshCastRouteEntries()
    }
    private val castSessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onCastSessionActive(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onCastSessionActive(session)
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = onCastSessionInactive()
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    }
    private var lastRecordedMediaId: String? = null
    private var lastPositionPersistedAtMs: Long = 0L
    private var radioNewsEnabled = false
    // Mute do mini player em modo radio (pedido do usuario 11/09/2026: "nao temos avancar,
    // vamos ter mute no lugar de pausar" - radio "ao vivo" continua avancando, so silencia).
    // Cobre os 2 canais de audio que a radio usa (controller = musica, announcementPlayer =
    // vinheta/passagem/boletim) - ver radioVolume()/toggleRadioMute(). Reseta sozinho em
    // startRadioNewsMode() (cada sessao nova comeca sem mute).
    private var radioMuted = false
    // "Reproducao automatica" (botao ao lado da legenda na Home, pedido do usuario 15/09/2026,
    // imitando o autoplay do Youtube) - ligado por padrao. So atua fora do modo radio (a radio ja
    // tem sua propria continuacao via vinhetas/boletins) e so dispara quando a fila ACABA sozinha
    // (Player.STATE_ENDED) - ver onEvents/maybeAutoplayNextAlbum.
    private var autoplayEnabled = true
    // Evita disparar autoplayNextAlbumSongs() de novo a cada onEvents() enquanto o player fica
    // parado em STATE_ENDED (varios eventos chegam nesse estado) - reseta assim que a fila muda.
    private var autoplayHandledForEndedQueue = false
    private var completedRadioSongs = 0
    private val recentBulletinStoryKeys = ArrayDeque<String>().apply {
        radioPrefs.getString(KEY_RECENT_BULLETIN_STORY_KEYS, null)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(RECENT_BULLETIN_STORY_KEY_LIMIT)
            ?.forEach { addLast(it) }
    }
    private var speakingNews = false
    private var currentNewsHeadline = ""
    private var resumeAfterNews = false
    // Guardas do gatilho antecipado do boletim (ver checkForEarlyNewsBreak/fadeOutRadioVolume):
    // newsBreakFadeInProgress cobre a janela do fade (antes de speakingNews existir de verdade,
    // pra checkForEarlyNewsBreak nao disparar duas vezes pro mesmo fim de musica);
    // newsBreakSkipsAheadOnResume sinaliza pra finishNewsBreak pular pra proxima faixa em vez de
    // retomar os ultimos segundos (ja em fade/silenciados) da musica que terminou.
    private var newsBreakFadeInProgress = false
    private var newsBreakSkipsAheadOnResume = false
    private var pendingVinheta = false
    // Alterna entre as vinhetas de despedida (tchauzinho/ate mais) ao sair de uma radio - ver
    // playExitVinheta()/EXIT_VINHETA_RESOURCES. Incrementa a cada uso, nao reseta entre sessoes.
    private var nextExitVinhetaIndex = 0
    // Timer "voce ainda esta ai?" (pedido do usuario 15/09/2026): sleepTimerJob dispara o aviso
    // depois de SLEEP_TIMER_IDLE_MS (1h30) tocando a mesma sessao de radio; sleepAutoStopJob
    // conta SLEEP_TIMER_RESPONSE_MS (1min) a partir do aviso e desliga a radio sozinha
    // (stopRadio()) se ninguem confirmar - motivo: usuario dorme com a radio ligada e ela fica
    // gastando boletim/sintese de voz a noite toda sem ninguem escutando. Ver armSleepTimer/
    // triggerSleepCheck/confirmStillListening/cancelSleepTimer.
    private var sleepTimerJob: Job? = null
    private var sleepAutoStopJob: Job? = null
    private var sleepCheckPending = false
    // Guarda de reentrancia de finishNewsBreak(): agora ela dispara a passagem final e so
    // zera speakingNews no fim disso (pra manter o watchdog cobrindo a passagem), entao o
    // guard antigo (`if (!speakingNews) return`) sozinho nao bastava mais - uma segunda
    // chamada durante a passagem tocaria a passagem duas vezes.
    private var newsBreakEnding = false
    private var announcementPlayer: MediaPlayer? = null
    // Player dedicado pro "Reproduzir" do card de buffer (playReadyBufferedBulletin) - separado
    // de announcementPlayer de proposito: o arquivo de um boletim do buffer ainda pertence ao
    // bulletinBuffer (vai tocar de verdade dali a pouco) - reusar o mesmo player/apagamento
    // arriscaria apagar o audio de um boletim que a radio real ainda vai consumir.
    private var previewPlayer: MediaPlayer? = null
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
    // Buffer UNICO de boletins prontos (roteiro ja decorado + WAV ja sintetizado, se a voz
    // local/Gemini estiver ativa) - ver refillBulletinBuffer()/ADR-019. Roda o tempo todo,
    // independente de radio ativa ou nao (pedido do usuario 03/09/2026: "todos os dias, ele tem
    // que fazer esse buffer mesmo sem eu tocar na radio") e SOBREVIVE a troca de radio (pedido do
    // usuario 10/09/2026: "o boletim e independente, nao pode ser apagado so porque eu saí da
    // radio no meio da geracao"). Mantido em BULLETIN_BUFFER_TARGET itens; consumido em FIFO por
    // speakNextNewsBreak(), reabastecido logo em seguida.
    // Unificado em 10/09/2026: antes existiam DOIS buffers separados ("nucleo" agnostico de
    // radio + fila especifica da radio ativa) guardando literalmente o mesmo tipo de item, que
    // precisavam ficar sincronizados via promocao/devolucao a cada troca de radio - essa
    // duplicacao foi a causa raiz de 2 bugs achados ao vivo no mesmo dia: boletim apagado no
    // meio da troca de radio, e as duas gavetas enchendo ate BULLETIN_BUFFER_TARGET CADA UMA
    // (estoque real de ate 20) por uma nao saber da outra. Boletim e 100% radio-agnostico (ver
    // RadioBulletin.kt buildSystemInstructions) - nunca precisou de dois niveis pra comecar.
    private val bulletinBuffer = ArrayDeque<PreparedBulletin>()
    private var protectedBulletinPlaybackFileName: String? = null
    // Job da correcao automatica de fallback (ver scheduleFallbackAutoFix/fixFallbackBulletins) -
    // guarda contra agendar mais de uma correcao sobreposta; roda com um pequeno delay pra dar
    // tempo de uma falha transitoria (rede, Gemini fora do ar) se resolver antes de tentar de
    // novo, em vez de bater na mesma falha em loop apertado.
    private var fallbackAutoFixJob: Job? = null
    // Pausa manual do preparo em segundo plano (pauseBulletinPreparation/resumeBulletinPreparation,
    // pedido do usuario 03/09/2026) - refillBulletinBuffer() vira no-op enquanto isso for true,
    // em TODOS os pontos de chamada (troca de faixa, fim de boletim,
    // reload de feed), sem precisar guardar isso em cada um. So sai do estado pausado se o
    // usuario retomar explicitamente - nao e limpo sozinho ao trocar de radio nem ao
    // desligar/ligar o modo boletim de novo.
    private var bulletinPrepPaused = false

    var libraryState = androidx.compose.runtime.mutableStateOf(LibraryUiState())
        private set

    var libraryContentState = androidx.compose.runtime.mutableStateOf(LibraryContentUiState())
        private set

    var playerState = androidx.compose.runtime.mutableStateOf(PlayerUiState())
        private set

    var metadataState = androidx.compose.runtime.mutableStateOf(MetadataUiState())
        private set

    // Incrementado sozinho a cada album auto-taggeado (genero/ano) com mudanca pra gravar de
    // verdade no arquivo - LibraryShell observa isso e chama requestRecentMetadataEditWrite()
    // sozinha (ver markAutoTagPendingWrite/maybeAutoTagAlbumMetadata), pedido explicito do usuario
    // 15/09/2026 pra nao depender de visita manual a Configuracoes > Tags pendentes.
    var autoTagWriteRequestedVersion = androidx.compose.runtime.mutableStateOf(0)
        private set

    var albumArtworkState = androidx.compose.runtime.mutableStateOf(AlbumArtworkUiState())
        private set

    var artistPhotoState = androidx.compose.runtime.mutableStateOf(ArtistPhotoUiState())
        private set

    var lyricsState = androidx.compose.runtime.mutableStateOf(LyricsUiState())
        private set

    // Carregamento de letra da faixa atual - cancelado/substituido a cada troca de musica.
    private var lyricsJob: Job? = null

    var backupState = androidx.compose.runtime.mutableStateOf(
        BackupUiState(
            hasDestination = backupRepository.hasDestination(),
            usingOfficialFolder = backupRepository.usingOfficialFolder(),
            destinationDescription = backupRepository.destinationDescription(),
            lastBackupAtMillis = backupRepository.lastBackupAt(),
        ),
    )
        private set

    var updateState = androidx.compose.runtime.mutableStateOf(UpdateUiState())
        private set

    var appFolderState = androidx.compose.runtime.mutableStateOf(
        AppFolderUiState(folderName = appFolderRepository.folderDisplayName()),
    )
        private set

    var profileState = androidx.compose.runtime.mutableStateOf(loadProfile())
        private set

    var artistNewsState = androidx.compose.runtime.mutableStateOf(ArtistNewsUiState())
        private set

    // Carrega sob demanda (chamado quando o painel de noticias da Home abre) em vez de no start do
    // app - evita uma chamada de rede que a maioria das sessoes nem chega a ver. Refaz a busca se
    // a lista de artistas favoritados mudou desde a ultima vez (favoritar/desfavoritar um artista)
    // OU se o cache atual e de antes do ultimo "5 da manha" (ver isNewsCacheStale) - pedido do
    // usuario 07/09/2026 de atualizar as noticias todo dia as 5h. Senao mantem o que ja foi
    // carregado.
    fun loadArtistNewsIfNeeded(favoriteArtists: List<LocalArtist>) {
        val names = favoriteArtists.map { it.name }
        val current = artistNewsState.value
        if (current.isLoading) return
        if (current.hasLoaded && current.loadedForArtistNames == names && !isNewsCacheStale(current.loadedAtMillis)) return
        if (names.isEmpty()) {
            artistNewsState.value = ArtistNewsUiState(hasLoaded = true, loadedForArtistNames = names, loadedAtMillis = System.currentTimeMillis())
            return
        }
        artistNewsState.value = current.copy(isLoading = true)
        viewModelScope.launch {
            val cards = runCatching { artistNewsRepository.loadNewsForArtists(names) }.getOrDefault(emptyList())
            artistNewsState.value = ArtistNewsUiState(
                cards = cards,
                isLoading = false,
                hasLoaded = true,
                loadedForArtistNames = names,
                loadedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    // true quando `loadedAtMillis` e de ANTES do 5h mais recente (hoje as 5h, ou ontem as 5h se
    // ainda nao deu 5h hoje) - ou seja, o cache e de "ontem" pro efeito de atualizacao diaria.
    private fun isNewsCacheStale(loadedAtMillis: Long): Boolean {
        if (loadedAtMillis <= 0L) return true
        val now = java.time.ZonedDateTime.now()
        var boundary = now.withHour(5).withMinute(0).withSecond(0).withNano(0)
        if (now.isBefore(boundary)) boundary = boundary.minusDays(1)
        return loadedAtMillis < boundary.toInstant().toEpochMilli()
    }

    fun saveUserProfile(name: String, birthday: String) {
        profilePrefs.edit()
            .putString(KEY_PROFILE_NAME, name.trim())
            .putString(KEY_PROFILE_BIRTHDAY, birthday.trim())
            .apply()
        profileState.value = loadProfile(profileState.value.appliedVersion).copy(message = "Perfil salvo.")
    }

    fun importProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext false
                    val dir = File(getApplication<Application>().filesDir, "profile").apply { mkdirs() }
                    val file = File(dir, PROFILE_PHOTO_FILE_NAME)
                    file.writeBytes(bytes)
                    profilePrefs.edit().putString(KEY_PROFILE_PHOTO_PATH, file.absolutePath).apply()
                    true
                }.getOrDefault(false)
            }
            val nextVersion = profileState.value.appliedVersion + 1
            profileState.value = loadProfile(nextVersion).copy(
                message = if (ok) "Foto atualizada." else "Nao consegui usar essa imagem.",
            )
        }
    }

    private fun loadProfile(appliedVersion: Int = 0): UserProfileUiState {
        val path = profilePrefs.getString(KEY_PROFILE_PHOTO_PATH, null)
        val photoUri = path?.let { File(it) }?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
        return UserProfileUiState(
            name = profilePrefs.getString(KEY_PROFILE_NAME, "").orEmpty(),
            birthday = profilePrefs.getString(KEY_PROFILE_BIRTHDAY, "").orEmpty(),
            photoUri = photoUri,
            appliedVersion = appliedVersion,
        )
    }

    // Bytes da capa baixada pro candidato selecionado - fora do StateFlow/State de proposito
    // (ByteArray nao tem equals estrutural util pra Compose, e o dado so importa no momento de
    // aplicar). Fica nulo ate selectArtworkCandidate() terminar o download.
    private var pendingArtworkBytes: ByteArray? = null

    // Mesma ideia de pendingArtworkBytes, so que pra foto de artista (ver applyArtistPhoto) -
    // fora do State de proposito, ByteArray nao tem equals estrutural util pro Compose.
    private var pendingArtistPhotoBytes: ByteArray? = null

    var radioBulletinState = androidx.compose.runtime.mutableStateOf(loadRadioBulletinUiState())
        private set

    var radioBulletinBufferState = androidx.compose.runtime.mutableStateOf(
        RadioBulletinBufferUiState(targetCount = BULLETIN_BUFFER_TARGET),
    )
        private set

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlayerState(player)
            if (player.isPlaying) recordCurrentSong(player)
            mirrorToRemoteIfNeeded(player)
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                maybeAutoplayNextAlbum(player)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (radioNewsEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // Heuristica de afinidade da radio (ADR-028) - lastRecordedMediaId ainda e a
                // faixa que ACABOU de tocar aqui (recordCurrentSong so atualiza pra faixa nova
                // no onEvents seguinte, que roda depois deste callback especifico). So conta
                // faixa que terminou sozinha (reason AUTO) - pular manualmente ou deslikar nao
                // some pra afinidade.
                lastRecordedMediaId?.toLongOrNull()?.let(repository::recordRadioPlayThrough)
                completedRadioSongs += 1
                val interval = radioBulletinState.value.settings.songsBetweenBulletins.coerceAtLeast(1)
                if (completedRadioSongs % interval == 0) {
                    speakNextNewsBreak()
                }
                // Preparo em segundo plano nao depende mais de "1 musica antes" - o buffer
                // (bulletinBuffer/refillBulletinBuffer) e mantido cheio continuamente, entao so
                // precisa de um empurrao aqui se algo tiver esvaziado ele nesse meio tempo
                // (ex.: reload de feed que faltava).
                refillBulletinBuffer()
            }
        }
    }

    init {
        connectToPlaybackService()
        runCatching {
            CastContext.getSharedInstance(application).sessionManager
                .addSessionManagerListener(castSessionManagerListener, CastSession::class.java)
        }
        viewModelScope.launch {
            while (true) {
                controller?.let {
                    updatePlayerState(it)
                    checkForEarlyNewsBreak(it)
                }
                // Achado 11/09/2026 ao vivo: um item pode ficar "pronto" no buffer sem chance real
                // de tocar com voz - seja porque a sintese nunca gerou arquivo (file == null,
                // restaurado assim do manifest.json de uma sessao anterior, sem nenhum evento NESTA
                // sessao que dispare scheduleFallbackAutoFix sozinho) ou porque o arquivo existia e
                // sumiu depois (corrida com o sweep de orfaos em saveCoreBufferManifest). Os dois
                // casos so eram descobertos na hora de tocar (speakNextNewsBreak), tarde demais.
                // Este polling ja roda a cada 1.5-3s pra outras coisas; aproveita pra notar cedo.
                //
                // Achado 11/09/2026 (segunda rodada, ao vivo - usuario reportou "bolinhas verdes"
                // mas TTS Android tocando mesmo assim): o caso "arquivo sumiu depois" NAO PODE
                // esperar o FALLBACK_AUTO_FIX_DELAY_MS (45s) de scheduleFallbackAutoFix - essa folga
                // existe pra nao martelar Gemini/local quando os dois estao de fato falhando na
                // ESCRITA/SINTESE (throttle certo ali), mas remover uma referencia de arquivo que
                // ja nao existe mais e so leitura de disco local, sem custo nenhum de esperar. Sem
                // separar isso, um item "verdinho" (fullGeminiSlots) podia ficar ate 45s na frente
                // da fila com o audio ja sumido - se o boletim seguinte da radio caisse dentro dessa
                // janela, tocava TTS Android cru com a bolinha ainda mostrando verde na tela (ver
                // tambem hasPlayableAudio/fullGeminiSlots em syncBulletinBufferState, que agora
                // conferem it.file?.exists() de verdade em vez de confiar em flag congelada).
                // purgeVanishedBulletinAudio() roda TODO ciclo (sem guard de job, e barato) pra
                // fechar essa janela; scheduleFallbackAutoFix (throttled) fica so pro que realmente
                // precisa tentar sintetizar de novo (file == null ou roteiro em Fallback).
                purgeVanishedBulletinAudio()
                if (bulletinBuffer.any { it.file == null || it.script.source == RadioScriptSource.Fallback }) {
                    scheduleFallbackAutoFix()
                }
                delay(if ((controller?.mediaItemCount ?: 0) > 0) 1_500 else 3_000)
            }
        }
        // Preparo do buffer de boletins comeca na abertura do app, SEM esperar o usuario tocar
        // uma radio (pedido do usuario 03/09/2026: "todos os dias, ele tem que fazer esse buffer
        // mesmo sem eu tocar na radio"). Ver refillBulletinBuffer/PreparedBulletin.
        // Restaura primeiro o que ja tinha sido escrito numa sessao anterior (loadCoreBufferManifest,
        // ver comentario la - pedido do usuario 03/09/2026: boletins prontos nao podiam sumir so
        // por reiniciar o app) antes de decidir se precisa preparar mais.
        // NAO rodar em Dispatchers.Default: syncBulletinBufferState() le/escreve Compose State
        // (radioBulletinBufferState) criado no proprio construtor deste ViewModel - fazer essa
        // primeira leitura de uma thread diferente da que criou o State, antes de qualquer
        // snapshot global ter sido aplicado, derruba o app com IllegalStateException ("Reading a
        // state that was created after the snapshot was taken") em TODA abertura - crash visto em
        // campo 03/09/2026 assim que essa persistencia foi ligada. O manifest e pequeno (poucos
        // KB), ler direto aqui no thread padrao do viewModelScope (Main.immediate) nao trava nada
        // perceptivel na abertura.
        viewModelScope.launch {
            loadCoreBufferManifest()
            syncBulletinBufferState()
            // Pedido do usuario (17/09/2026, ADR-037): nao quer precisar clicar em "Resetar" -
            // verifica especial pendente TODA abertura do app, furando fila mesmo com o buffer
            // ja cheio de itens normais (refillBulletinBuffer() sozinho nao tentaria nada aqui,
            // ve bulletinBuffer.size >= BULLETIN_BUFFER_TARGET e desiste sem checar o feed).
            // Roda sequencial, ANTES de refillBulletinBuffer() e na MESMA corrotina (nao um
            // launch separado) - evita baixar o mesmo especial duas vezes em paralelo.
            checkForSpecialOnAppOpen()
            refillBulletinBuffer()
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

    private fun updateRadioBulletinSettings(settings: RadioBulletinSettings) {
        radioPrefs.edit()
            .putString(KEY_RADIO_BULLETIN_MODE, settings.mode.name)
            .apply()
        radioBulletinState.value = loadRadioBulletinUiState()
    }

    private fun loadRadioBulletinUiState(): RadioBulletinUiState {
        // Modo nao e mais escolha do usuario - sempre "conversa dos locutores" (Dialogue), a
        // UI de selecao foi removida. Ignora qualquer valor antigo salvo em
        // KEY_RADIO_BULLETIN_MODE (ex.: Off/Headlines de uma instalacao anterior). Voz/redator
        // removidos 16/09/2026 - o boletim vem pronto (roteiro + audio) do feed remoto
        // (BroadcastFeedRepository), sem nada mais pra configurar aqui.
        val settings = RadioBulletinSettings(
            mode = RadioBulletinMode.Dialogue,
            songsBetweenBulletins = RADIO_BULLETIN_DEFAULT_INTERVAL,
        )
        return RadioBulletinUiState(settings = settings)
    }

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
        // Log.d/Log.e adicionados 02/09/2026: antes disso, uma falha em loadSongs() (ou uma
        // consulta que devolvia a mesma contagem por metadado do MediaStore ainda pendente -
        // duration/is_music NULL logo apos um scan em massa via `content call scan_volume`, que
        // NAO extrai metadado como um scan por arquivo) ficava completamente silenciosa -
        // onFailure so seta error, nunca loga a excecao.
        runCatching { repository.loadSongs(includeDeviceGenres = includeDeviceGenres) }
            .onSuccess { songs ->
                Log.d("PailerLibrarySync", "scanLibrary sucesso: ${songs.size} faixas (antes: ${state.songs.size})")
                repository.saveCachedSongs(songs)
                applyLibrarySongs(
                    songs = songs,
                    isLoading = false,
                    hasLoaded = true,
                    query = libraryState.value.query,
                )
            }
            .onFailure { error ->
                Log.e("PailerLibrarySync", "scanLibrary falhou", error)
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
            lastSongId = historyPrefs.getLongSafe(KEY_LAST_SONG_ID, 0L).takeIf { it > 0L },
            lastPositionMs = historyPrefs.getLongSafe(KEY_LAST_POSITION_MS, 0L),
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
            // Artista/album ocultos (ver hideArtist/hideAlbum, popover de long-press na grade)
            // somem daqui em diante pra biblioteca inteira - musicas/albuns/radios/generos abaixo
            // sao todos derivados de visibleSongs, nao de `filtered`. Ocultar artista cascateia
            // pros albuns dele automaticamente (nenhum song sobra pra reconstruir o album); nao
            // precisa de logica separada pra isso.
            val hiddenArtistKeys = repository.hiddenArtistKeys()
            val hiddenAlbumKeys = repository.hiddenAlbumKeys()
            val visibleSongs = if (hiddenArtistKeys.isEmpty() && hiddenAlbumKeys.isEmpty()) {
                filtered
            } else {
                val hiddenSongIds = (
                    repository.artistsFrom(filtered).filter { it.key in hiddenArtistKeys }.flatMap { it.songs } +
                        repository.albumsFrom(filtered).filter { it.key in hiddenAlbumKeys }.flatMap { it.songs }
                    ).mapTo(mutableSetOf()) { it.id }
                filtered.filterNot { it.id in hiddenSongIds }
            }
            val albums = repository.albumsFrom(visibleSongs)
            val historyIds = state.historyIds
            val historyIdsSet = historyIds.toSet()
            val songsById = visibleSongs.associateBy { it.id }
            val artistsList = repository.artistsFrom(visibleSongs)
            val genreRadiosList = repository.allGenreRadios(visibleSongs)
            val radiosList = repository.radiosFrom(visibleSongs, precomputedGenreRadios = genreRadiosList)
            val availableGenresList = repository.availableGenres(visibleSongs)
            val content = LibraryContentUiState(
                songs = visibleSongs,
                albums = albums,
                artists = artistsList,
                radios = radiosList,
                genreRadios = genreRadiosList,
                availableGenres = availableGenresList,
                recentlyAddedAlbums = albums.sortedByDescending { it.dateAdded }.take(18),
                listenAgain = historyIds.mapNotNull { songsById[it] }.take(18)
                    .ifEmpty { visibleSongs.sortedByDescending { it.dateAdded }.take(18) },
                suggestedAlbums = albums.sortedWith(
                    compareByDescending<LocalAlbum> { album -> album.songs.count { it.id in historyIdsSet } }
                        .thenByDescending { album -> album.songs.maxOfOrNull { it.dateAdded } ?: 0L }
                        .thenBy { it.title.lowercase() }
                ).take(20),
                favoriteAlbums = albums
                    .filter { it.key in state.favoriteAlbumKeys }
                    .sortedBy { it.title.lowercase() },
                continueSong = visibleSongs.firstOrNull { it.id == state.lastSongId }
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

    // --- Faixas "deslike" na radio (tela de Configuracoes, ver dislikeCurrentRadioSong) ---

    fun radioDislikedSongs(): List<LocalSong> {
        val ids = repository.radioDislikedSongIds()
        return libraryState.value.songs.filter { it.id in ids }
    }

    fun undislikeSongForRadio(song: LocalSong) {
        repository.undislikeSongForRadio(song.id)
        rebuildLibraryContent()
    }

    // --- Letra da musica (tela do player) ---

    // Chamado pela UI num LaunchedEffect(player.songId) quando o player cheio esta aberto e NAO
    // e modo radio. Lancado no viewModelScope (Main.immediate) e faz o I/O em Dispatchers.IO -
    // nunca do init{} nem do polling de posicao (ADR-020). Early-return se ja carregou a mesma faixa.
    fun loadLyricsFor(songId: Long?) {
        if (songId == null) {
            lyricsJob?.cancel()
            lyricsState.value = LyricsUiState()
            return
        }
        val current = lyricsState.value
        if (current.songId == songId && (current.isLoading || !current.lyrics.isEmpty)) return

        val song = libraryState.value.songs.firstOrNull { it.id == songId }
        if (song == null) {
            lyricsState.value = LyricsUiState(songId = songId)
            return
        }
        lyricsJob?.cancel()
        lyricsState.value = LyricsUiState(songId = songId, isLoading = true)
        lyricsJob = viewModelScope.launch {
            val result = lyricsRepository.load(song)
            if (lyricsState.value.songId != songId) return@launch
            lyricsState.value = lyricsState.value.copy(lyrics = result, isLoading = false)
        }
    }

    fun fetchLyricsOnline(songId: Long) {
        if (lyricsState.value.isFetching) return
        val song = libraryState.value.songs.firstOrNull { it.id == songId } ?: return
        lyricsState.value = lyricsState.value.copy(isFetching = true, message = null)
        viewModelScope.launch {
            val fetched = lyricsRepository.fetchOnline(song)
            if (lyricsState.value.songId != songId) return@launch
            lyricsState.value = if (fetched != null) {
                lyricsState.value.copy(lyrics = fetched, isFetching = false, message = null)
            } else {
                lyricsState.value.copy(isFetching = false, message = "Letra não encontrada online.")
            }
        }
    }

    fun openLyricsEditor() {
        lyricsState.value = lyricsState.value.copy(
            editorOpen = true,
            editorDraft = lyricsState.value.lyrics.plainText,
        )
    }

    fun updateLyricsDraft(text: String) {
        lyricsState.value = lyricsState.value.copy(editorDraft = text)
    }

    fun dismissLyricsEditor() {
        lyricsState.value = lyricsState.value.copy(editorOpen = false)
    }

    fun saveLyrics(songId: Long) {
        val song = libraryState.value.songs.firstOrNull { it.id == songId } ?: return
        val draft = lyricsState.value.editorDraft
        viewModelScope.launch {
            val result = if (draft.isBlank()) {
                lyricsRepository.remove(songId)
                lyricsRepository.load(song)
            } else {
                lyricsRepository.save(song, draft, LyricsSource.MANUAL)
            }
            if (lyricsState.value.songId != songId) return@launch
            lyricsState.value = lyricsState.value.copy(
                lyrics = result,
                editorOpen = false,
                message = null,
            )
        }
    }

    fun removeLyrics(songId: Long) {
        val song = libraryState.value.songs.firstOrNull { it.id == songId } ?: return
        viewModelScope.launch {
            lyricsRepository.remove(songId)
            val reloaded = lyricsRepository.load(song) // pode reaparecer a letra embutida na tag
            if (lyricsState.value.songId != songId) return@launch
            lyricsState.value = lyricsState.value.copy(lyrics = reloaded, editorOpen = false)
        }
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

    // Ocultar artista/album (ver LibraryItemActionsSheet, popover de long-press na grade) - so
    // tira da biblioteca (rebuildLibraryContent filtra por repository.hiddenArtistKeys/
    // hiddenAlbumKeys, ver la), NAO apaga arquivo nenhum do aparelho, diferente de requestDelete.
    fun isArtistHidden(artist: LocalArtist): Boolean = artist.key in repository.hiddenArtistKeys()

    fun hideArtist(artist: LocalArtist) {
        repository.hideArtist(artist)
        rebuildLibraryContent()
        showToast("\"${artist.name}\" foi ocultado da biblioteca.")
    }

    fun isAlbumHidden(album: LocalAlbum): Boolean = album.key in repository.hiddenAlbumKeys()

    fun hideAlbum(album: LocalAlbum) {
        repository.hideAlbum(album)
        rebuildLibraryContent()
        showToast("\"${album.title}\" foi ocultado da biblioteca.")
    }

    fun hasHiddenArtistsOrAlbums(): Boolean =
        repository.hiddenArtistKeys().isNotEmpty() || repository.hiddenAlbumKeys().isNotEmpty()

    fun restoreHiddenArtistsAndAlbums() {
        repository.unhideAllArtists()
        repository.unhideAllAlbums()
        rebuildLibraryContent()
        showToast("Artistas e albuns ocultos foram restaurados.")
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

    // Ano de lancamento (pedido do usuario 15/09/2026) - mesmo padrao de approveAlbumGenre, usado
    // hoje so pela busca automatica (ver maybeAutoTagAlbumMetadata); nao ha tela manual de "albuns
    // sem ano" equivalente ainda.
    fun approveAlbumYear(album: LocalAlbum, year: Int) {
        repository.saveAlbumYearOverride(album, year)
        val updatedSongs = libraryState.value.songs.map { song ->
            if (song.albumId == album.id && song.album == album.title) song.copy(year = year) else song
        }
        libraryState.value = libraryState.value.copy(songs = updatedSongs)
        rebuildLibraryContent()
        viewModelScope.launch { repository.saveCachedSongs(updatedSongs) }
    }

    fun scanAlbumsWithoutArtwork() {
        albumArtworkState.value = albumArtworkState.value.copy(isScanning = true, message = null)
        viewModelScope.launch {
            val songs = libraryState.value.songs
            val missing = withContext(Dispatchers.Default) { repository.albumsWithoutArtwork(songs) }
            albumArtworkState.value = albumArtworkState.value.copy(
                albumsWithoutArtwork = missing,
                isScanning = false,
                message = if (missing.isEmpty()) "Todos os albuns ja tem capa." else null,
            )
        }
    }

    // --- Pasta oficial do Pailer FM (Backup/Logs/Redator Local/Pacote de Vozes - ver AppFolderRepository) ---

    fun chooseAppFolder(uri: Uri) {
        appFolderRepository.setFolder(uri)
        appFolderState.value = AppFolderUiState(folderName = appFolderRepository.folderDisplayName())
        // Pasta oficial vira a fonte do backup automaticamente (ver BackupRepository -
        // prioriza a pasta sobre um arquivo manual escolhido antes) - agenda e faz o primeiro
        // backup na hora, mesmo esquema de chooseBackupDestination abaixo.
        BackupScheduler.scheduleNextMidnight(getApplication())
        backupState.value = backupState.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            val ok = backupRepository.performBackup()
            backupState.value = backupState.value.copy(
                hasDestination = backupRepository.hasDestination(),
                usingOfficialFolder = backupRepository.usingOfficialFolder(),
                destinationDescription = backupRepository.destinationDescription(),
                lastBackupAtMillis = backupRepository.lastBackupAt(),
                isWorking = false,
                message = if (ok) "Pasta configurada e backup salvo com sucesso." else "Pasta configurada, mas o backup falhou.",
            )
        }
    }

    fun clearAppFolder() {
        appFolderRepository.clearFolder()
        appFolderState.value = AppFolderUiState()
        backupState.value = backupState.value.copy(
            hasDestination = backupRepository.hasDestination(),
            usingOfficialFolder = false,
            destinationDescription = backupRepository.destinationDescription(),
        )
        if (!backupState.value.hasDestination) {
            BackupScheduler.cancel(getApplication())
        }
    }

    // --- Backup manual/automatico (favoritos, overrides, fotos de artista - ver BackupRepository) ---
    // Caminho antigo (arquivo unico escolhido na mao) - continua funcionando pra quem configurou
    // antes da pasta oficial existir, mas fica em segundo plano: se uma pasta oficial estiver
    // configurada, BackupRepository sempre prioriza ela (ver hasDestination/usingOfficialFolder).

    fun chooseBackupDestination(uri: Uri) {
        backupRepository.saveBackupDestination(uri)
        BackupScheduler.scheduleNextMidnight(getApplication())
        backupState.value = backupState.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            val ok = backupRepository.performBackup()
            backupState.value = backupState.value.copy(
                hasDestination = backupRepository.hasDestination(),
                usingOfficialFolder = backupRepository.usingOfficialFolder(),
                destinationDescription = backupRepository.destinationDescription(),
                lastBackupAtMillis = backupRepository.lastBackupAt(),
                isWorking = false,
                message = if (ok) "Backup salvo com sucesso." else "Nao consegui salvar o backup nesse arquivo.",
            )
        }
    }

    fun performBackupNow() {
        if (!backupState.value.hasDestination) return
        backupState.value = backupState.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            val ok = backupRepository.performBackup()
            backupState.value = backupState.value.copy(
                lastBackupAtMillis = backupRepository.lastBackupAt(),
                isWorking = false,
                message = if (ok) "Backup atualizado agora." else "Nao consegui salvar o backup.",
            )
        }
    }

    fun clearBackupDestination() {
        backupRepository.clearBackupDestination()
        backupState.value = backupState.value.copy(
            hasDestination = backupRepository.hasDestination(),
            usingOfficialFolder = backupRepository.usingOfficialFolder(),
            destinationDescription = backupRepository.destinationDescription(),
        )
        if (!backupState.value.hasDestination) {
            BackupScheduler.cancel(getApplication())
        }
    }

    // Restaura favoritos/overrides/fotos de um arquivo de backup escolhido pelo usuario (SAF) e
    // recarrega a biblioteca - refreshLibrary() ja re-le todas as SharedPreferences do zero (ver
    // applyLibrarySongs), entao restaurar os arquivos e chamar ela de novo basta, sem precisar
    // reiniciar o app.
    fun restoreBackup(uri: Uri) {
        backupState.value = backupState.value.copy(isWorking = true, message = null)
        viewModelScope.launch {
            val ok = backupRepository.restoreBackup(uri)
            backupState.value = backupState.value.copy(
                isWorking = false,
                message = if (ok) "Backup restaurado." else "Nao consegui ler esse arquivo de backup.",
            )
            if (ok) {
                profileState.value = loadProfile(profileState.value.appliedVersion + 1)
                refreshLibrary()
            }
        }
    }

    // Prompt de "restaurar backup?" na primeira abertura com a biblioteca vazia de favoritos/
    // historico/perfil (pedido do usuario 15/09/2026, pra reduzir o atrito de reinstalar - ex.:
    // ao unificar a chave de assinatura de release). Chamado uma vez quando a library termina de
    // carregar (ver LaunchedEffect(library.hasLoaded) em LocalTuneApp.kt) - hasOfferedFreshRestorePrompt
    // garante que so acontece 1 vez por instalacao, nunca mais depois (nem incomoda quem comecou
    // do zero de verdade, nem fica reaparecendo pra sempre).
    var showFreshRestorePrompt = androidx.compose.runtime.mutableStateOf(false)
        private set

    fun maybeOfferFreshRestore() {
        if (backupRepository.hasOfferedFreshRestorePrompt()) return
        val lib = libraryState.value
        val looksFresh = lib.historyIds.isEmpty() &&
            lib.favoriteAlbumKeys.isEmpty() &&
            lib.favoriteSongIds.isEmpty() &&
            lib.favoriteArtistKeys.isEmpty() &&
            lib.lastSongId == null
        if (looksFresh) {
            showFreshRestorePrompt.value = true
        }
        backupRepository.markFreshRestorePromptOffered()
    }

    fun dismissFreshRestorePrompt() {
        showFreshRestorePrompt.value = false
    }

    // --- Auto-atualizacao fora da Play Store (pedido do usuario 15/09/2026, ver
    // UpdateCheckRepository/docs sobre build-apk.yml) ---

    // Chamado uma vez na abertura do app (ver LaunchedEffect em LocalTuneApp.kt). Silencioso em
    // build local (BuildConfig.RELEASE_TAG == "local-dev", ver isNewerThanCurrent) e em qualquer
    // falha de rede - checagem de atualizacao nunca deve incomodar nem travar o uso normal do app.
    fun checkForUpdate() {
        if (updateState.value.isChecking || updateState.value.latestRelease != null) return
        updateState.value = updateState.value.copy(isChecking = true)
        viewModelScope.launch {
            val release = runCatching { updateCheckRepository.fetchLatestRelease() }.getOrNull()
            val hasUpdate = release != null && updateCheckRepository.isNewerThanCurrent(release.tagName)
            updateState.value = updateState.value.copy(
                isChecking = false,
                latestRelease = if (hasUpdate) release else null,
            )
        }
    }

    fun dismissUpdatePrompt() {
        updateState.value = updateState.value.copy(dismissed = true)
    }

    // Baixa o APK da release (streaming, com progresso - ver UpdateCheckRepository.downloadApk) e
    // deixa pronto em downloadedApkFile; quem abre o instalador de verdade e a UI
    // (LocalTuneApp.kt), que tem o Context/FileProvider pra montar o content:// URI.
    fun downloadUpdate() {
        val release = updateState.value.latestRelease ?: return
        if (updateState.value.isDownloading) return
        updateState.value = updateState.value.copy(isDownloading = true, downloadProgress = 0f, message = null)
        viewModelScope.launch {
            val file = updateCheckRepository.downloadApk(release.apkDownloadUrl) { progress ->
                updateState.value = updateState.value.copy(downloadProgress = progress)
            }
            updateState.value = updateState.value.copy(
                isDownloading = false,
                downloadedApkFile = file,
                message = if (file == null) "Não consegui baixar a atualização. Tenta de novo." else null,
            )
        }
    }

    fun openArtworkSearch(album: LocalAlbum) {
        pendingArtworkBytes = null
        albumArtworkState.value = albumArtworkState.value.copy(
            activeAlbumKey = album.key,
            isSearching = true,
            candidates = emptyList(),
            selectedCandidate = null,
            message = null,
        )
        viewModelScope.launch {
            val results = repository.searchArtworkCandidates(album)
            if (albumArtworkState.value.activeAlbumKey != album.key) return@launch
            albumArtworkState.value = albumArtworkState.value.copy(
                isSearching = false,
                candidates = results,
                message = if (results.isEmpty()) "Nenhuma capa encontrada pra \"${album.title}\"." else null,
            )
        }
    }

    fun closeArtworkSearch() {
        pendingArtworkBytes = null
        albumArtworkState.value = albumArtworkState.value.copy(
            activeAlbumKey = null,
            candidates = emptyList(),
            selectedCandidate = null,
            isDownloadingSelection = false,
        )
    }

    fun selectArtworkCandidate(candidate: ArtworkCandidate) {
        pendingArtworkBytes = null
        albumArtworkState.value = albumArtworkState.value.copy(
            selectedCandidate = candidate,
            isDownloadingSelection = true,
            message = null,
        )
        viewModelScope.launch {
            val bytes = repository.downloadArtwork(candidate.fullUrl)
            if (albumArtworkState.value.selectedCandidate != candidate) return@launch
            pendingArtworkBytes = bytes
            albumArtworkState.value = albumArtworkState.value.copy(
                isDownloadingSelection = false,
                message = if (bytes == null) "Nao consegui baixar essa capa, tenta outra." else null,
            )
        }
    }

    // Sincrono de proposito (mesmo padrao de createRecentMetadataEditWriteRequest): so pede a
    // permissao de escrita se ja tiver os bytes da capa em mao, baixados em selectArtworkCandidate.
    fun createArtworkApplyRequest(album: LocalAlbum): PendingIntent? {
        if (pendingArtworkBytes == null) return null
        return runCatching { repository.createArtworkWriteRequest(album) }.getOrNull()
    }

    fun applyPendingArtwork(album: LocalAlbum) {
        val bytes = pendingArtworkBytes
        if (bytes == null) {
            albumArtworkState.value = albumArtworkState.value.copy(message = "Escolha uma capa antes de aplicar.")
            return
        }
        albumArtworkState.value = albumArtworkState.value.copy(isApplying = true)
        viewModelScope.launch {
            val updated = runCatching { repository.applyArtworkToAlbum(album, bytes) }.getOrDefault(0)
            pendingArtworkBytes = null
            val message = if (updated > 0) {
                "Capa aplicada em $updated faixas de \"${album.title}\"."
            } else {
                "Nao consegui gravar a capa em nenhuma faixa."
            }
            albumArtworkState.value = albumArtworkState.value.copy(
                isApplying = false,
                activeAlbumKey = null,
                candidates = emptyList(),
                selectedCandidate = null,
                albumsWithoutArtwork = if (updated > 0) {
                    albumArtworkState.value.albumsWithoutArtwork.filterNot { it.key == album.key }
                } else {
                    albumArtworkState.value.albumsWithoutArtwork
                },
                appliedVersion = if (updated > 0) albumArtworkState.value.appliedVersion + 1 else albumArtworkState.value.appliedVersion,
                message = message,
            )
            showToast(message)
        }
    }

    fun cancelArtworkApply() {
        pendingArtworkBytes = null
        albumArtworkState.value = albumArtworkState.value.copy(
            isApplying = false,
            message = "Permissao cancelada. Nada foi alterado.",
        )
    }

    // --- Foto de artista (busca na web, ver MusicLibraryRepository) ---

    fun artistPhotoUri(artist: LocalArtist): Uri? = repository.artistPhotoOverrideUri(artist.key)

    fun openArtistPhotoSearch(artist: LocalArtist) {
        pendingArtistPhotoBytes = null
        artistPhotoState.value = artistPhotoState.value.copy(
            activeArtistKey = artist.key,
            isSearching = true,
            candidates = emptyList(),
            selectedCandidate = null,
            message = null,
        )
        viewModelScope.launch {
            val results = repository.searchArtistPhotoCandidates(artist.name)
            if (artistPhotoState.value.activeArtistKey != artist.key) return@launch
            artistPhotoState.value = artistPhotoState.value.copy(
                isSearching = false,
                candidates = results,
                message = if (results.isEmpty()) "Nenhuma foto encontrada pra \"${artist.name}\"." else null,
            )
        }
    }

    fun closeArtistPhotoSearch() {
        pendingArtistPhotoBytes = null
        artistPhotoState.value = artistPhotoState.value.copy(
            activeArtistKey = null,
            candidates = emptyList(),
            selectedCandidate = null,
            isDownloadingSelection = false,
        )
    }

    fun selectArtistPhotoCandidate(candidate: ArtworkCandidate) {
        pendingArtistPhotoBytes = null
        artistPhotoState.value = artistPhotoState.value.copy(
            selectedCandidate = candidate,
            isDownloadingSelection = true,
            message = null,
        )
        viewModelScope.launch {
            val bytes = repository.downloadArtwork(candidate.fullUrl)
            if (artistPhotoState.value.selectedCandidate != candidate) return@launch
            pendingArtistPhotoBytes = bytes
            artistPhotoState.value = artistPhotoState.value.copy(
                isDownloadingSelection = false,
                message = if (bytes == null) "Nao consegui baixar essa foto, tenta outra." else null,
            )
        }
    }

    // Nao precisa de PendingIntent/permissao de escrita como a capa de album - a foto de artista
    // fica so num arquivo do proprio app (ver MusicLibraryRepository.applyArtistPhoto).
    fun applyArtistPhoto(artist: LocalArtist) {
        val bytes = pendingArtistPhotoBytes
        if (bytes == null) {
            artistPhotoState.value = artistPhotoState.value.copy(message = "Escolha uma foto antes de aplicar.")
            return
        }
        artistPhotoState.value = artistPhotoState.value.copy(isApplying = true)
        viewModelScope.launch {
            val ok = runCatching { repository.applyArtistPhoto(artist.key, bytes) }.getOrDefault(false)
            pendingArtistPhotoBytes = null
            val message = if (ok) "Foto de \"${artist.name}\" atualizada." else "Nao consegui salvar essa foto."
            artistPhotoState.value = artistPhotoState.value.copy(
                isApplying = false,
                activeArtistKey = null,
                candidates = emptyList(),
                selectedCandidate = null,
                appliedVersion = if (ok) artistPhotoState.value.appliedVersion + 1 else artistPhotoState.value.appliedVersion,
                message = message,
            )
            showToast(message)
        }
    }

    fun removeArtistPhoto(artist: LocalArtist) {
        repository.removeArtistPhotoOverride(artist.key)
        artistPhotoState.value = artistPhotoState.value.copy(
            appliedVersion = artistPhotoState.value.appliedVersion + 1,
            message = "Voltou a usar a capa de album como foto de \"${artist.name}\".",
        )
    }

    // Pedido do usuario 15/09/2026 (mesmo padrao do genero automatico ao tocar pela primeira
    // vez): assim que a pagina do artista abre sem foto propria ainda, busca sozinho na Deezer
    // (mesma fonte do buscador manual) e aplica o primeiro resultado - o nome mais relevante pra
    // essa busca, segundo a propria Deezer - sem precisar abrir o buscador. So tenta 1 vez por
    // artista (ache ou nao), pra nao bater na web de novo toda vez que a pagina abrir. O buscador
    // manual (agora dentro de "Editar metadados" > Foto do artista, ver ArtistMetadataEditorOverlay)
    // continua disponivel pra trocar a foto ou tentar de novo se essa automatica nao agradar.
    fun maybeAutoFetchArtistPhoto(artist: LocalArtist) {
        if (repository.artistPhotoOverrideUri(artist.key) != null) return
        if (repository.hasAutoPhotoLookupRun(artist.key)) return
        repository.markAutoPhotoLookupRun(artist.key)
        viewModelScope.launch {
            val candidate = runCatching { repository.searchArtistPhotoCandidates(artist.name) }
                .getOrDefault(emptyList())
                .firstOrNull() ?: return@launch
            val bytes = repository.downloadArtwork(candidate.fullUrl) ?: return@launch
            val ok = runCatching { repository.applyArtistPhoto(artist.key, bytes) }.getOrDefault(false)
            if (ok) {
                artistPhotoState.value = artistPhotoState.value.copy(
                    appliedVersion = artistPhotoState.value.appliedVersion + 1,
                )
            }
        }
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
        year: Int = album.year,
    ): LocalAlbum {
        val cleanTitle = title.trim().ifBlank { album.title }
        val cleanArtist = artistName.trim().ifBlank { album.artist }
        val cleanGenre = genre.trim()
        val cleanYear = year.takeIf { it > 0 } ?: 0
        val titleChanged = cleanTitle != album.title
        val artistChanged = cleanArtist != album.artist
        val genreChanged = cleanGenre != album.genre
        val yearChanged = cleanYear > 0 && cleanYear != album.year
        val albumSongIds = album.songs.map { it.id }.toSet()

        if (titleChanged) repository.saveAlbumTitleOverride(album, cleanTitle)
        if (artistChanged) repository.saveAlbumArtistOverride(album, cleanArtist)
        if (genreChanged) repository.saveAlbumGenreOverride(album, cleanGenre)
        if (yearChanged) repository.saveAlbumYearOverride(album, cleanYear)

        val updatedSongs = libraryState.value.songs.map { song ->
            if (song.id !in albumSongIds) {
                song
            } else {
                song.copy(
                    artist = if (artistChanged) cleanArtist else song.artist,
                    album = if (titleChanged) cleanTitle else song.album,
                    genre = if (genreChanged) cleanGenre else song.genre,
                    year = if (yearChanged) cleanYear else song.year,
                )
            }
        }

        libraryState.value = libraryState.value.copy(songs = updatedSongs)
        rebuildLibraryContent()
        viewModelScope.launch { repository.saveCachedSongs(updatedSongs) }
        val pending = repository.pendingTagChanges(updatedSongs)
        requestedTagWriteChanges = if (titleChanged || artistChanged || genreChanged || yearChanged) {
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
                        year = if (yearChanged) cleanYear else it.year,
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

    private suspend fun downloadGoogleDrivePackage(
        fileId: String,
        fileName: String,
        maxBytes: Long,
        progressPrefix: String,
        onProgress: suspend (String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val destination = getApplication<Application>()
            .cacheDir
            .resolve("package_downloads")
            .resolve(fileName)
        destination.parentFile?.mkdirs()
        destination.delete()

        var url = URL("https://drive.google.com/uc?export=download&id=$fileId")
        val cookies = linkedSetOf<String>()
        var attempt = 0
        while (attempt < 5) {
            attempt++
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "PailerFM/1.0")
                if (cookies.isNotEmpty()) {
                    setRequestProperty("Cookie", cookies.joinToString("; "))
                }
            }
            connection.headerFields["Set-Cookie"]
                ?.mapNotNull { it.substringBefore(";").takeIf(String::isNotBlank) }
                ?.let(cookies::addAll)

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location") ?: error("Download redirecionou sem destino.")
                url = URL(url, location)
                connection.disconnect()
                continue
            }
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText().take(160) }.orEmpty()
                connection.disconnect()
                error("Download falhou (${responseCode}). $errorText".trim())
            }

            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            val contentDisposition = connection.getHeaderField("Content-Disposition").orEmpty()
            Log.d(
                TAG_RADIO_VOICE,
                "drive download response file=$fileName code=$responseCode type=$contentType disposition=${contentDisposition.take(80)}",
            )
            if (contentDisposition.isBlank() && contentType.contains("text/html")) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val confirmedUrl = findGoogleDriveConfirmedDownloadUrl(html, fileId)
                    ?: error("Google Drive pediu confirmacao e nao liberou o arquivo automaticamente.")
                Log.d(TAG_RADIO_VOICE, "drive download confirmation resolved file=$fileName")
                url = URL(url, confirmedUrl)
                continue
            }

            val expectedBytes = connection.contentLengthLong.takeIf { it > 0L }
            var copied = 0L
            var lastProgressBytes = 0L
            var lastProgressAt = 0L
            withContext(Dispatchers.Main) {
                onProgress(formatDownloadProgress(progressPrefix, copied, expectedBytes))
            }
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > maxBytes) {
                            destination.delete()
                            error("Arquivo maior que o esperado.")
                        }
                        output.write(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (copied - lastProgressBytes >= DOWNLOAD_PROGRESS_STEP_BYTES ||
                            now - lastProgressAt >= DOWNLOAD_PROGRESS_STEP_MS
                        ) {
                            lastProgressBytes = copied
                            lastProgressAt = now
                            withContext(Dispatchers.Main) {
                                onProgress(formatDownloadProgress(progressPrefix, copied, expectedBytes))
                            }
                        }
                    }
                }
            }
            connection.disconnect()
            if (copied <= 0L) {
                destination.delete()
                error("Download veio vazio.")
            }
            withContext(Dispatchers.Main) {
                onProgress(formatDownloadProgress(progressPrefix, copied, expectedBytes))
            }
            Log.d(TAG_RADIO_VOICE, "drive download completed file=$fileName bytes=$copied")
            return@withContext destination
        }

        destination.delete()
        error("Nao consegui iniciar o download pelo Google Drive.")
    }

    private fun findGoogleDriveConfirmedDownloadUrl(html: String, fileId: String): String? {
        val normalizedHtml = html
            .replace("&amp;", "&")
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")

        val href = Regex("""href="([^"]*(?:/uc\?export=download|drive\.usercontent\.google\.com/download)[^"]*)"""")
            .find(normalizedHtml)
            ?.groupValues
            ?.getOrNull(1)
        if (!href.isNullOrBlank()) {
            return if (href.startsWith("http")) href else "https://drive.google.com$href"
        }

        findGoogleDriveDownloadFormUrl(normalizedHtml, fileId)?.let { return it }

        val confirm = Regex("""confirm=([0-9A-Za-z_\-]+)""")
            .find(normalizedHtml)
            ?.groupValues
            ?.getOrNull(1)
        return confirm?.let { "https://drive.google.com/uc?export=download&confirm=$it&id=$fileId" }
    }

    private fun findGoogleDriveDownloadFormUrl(html: String, fileId: String): String? {
        val formOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val form = Regex(
            """<form\b[^>]*\bid=["']download-form["'][^>]*>.*?</form>""",
            formOptions,
        ).find(html)?.value ?: Regex(
            """<form\b[^>]*\baction=["'][^"']*(?:drive\.usercontent\.google\.com/download|/download)[^"']*["'][^>]*>.*?</form>""",
            formOptions,
        ).find(html)?.value ?: return null

        val action = htmlAttribute(form, "action")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val params = linkedMapOf<String, String>()
        Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(form)
            .forEach { match ->
                val input = match.value
                val name = htmlAttribute(input, "name") ?: return@forEach
                params[name] = htmlAttribute(input, "value").orEmpty()
            }

        params["id"] = params["id"].takeUnless { it.isNullOrBlank() } ?: fileId
        params["export"] = params["export"].takeUnless { it.isNullOrBlank() } ?: "download"

        val baseUrl = when {
            action.startsWith("http", ignoreCase = true) -> action
            action.startsWith("/") -> "https://drive.usercontent.google.com$action"
            else -> "https://drive.usercontent.google.com/$action"
        }
        val query = params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator$query"
    }

    private fun htmlAttribute(tag: String, name: String): String? =
        Regex("""\b${Regex.escape(name)}=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("&amp;", "&")

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun formatDownloadProgress(prefix: String, downloadedBytes: Long, totalBytes: Long?): String {
        val downloaded = formatMegabytes(downloadedBytes)
        return if (totalBytes != null && totalBytes > 0L) {
            val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0L)
            "$prefix: $downloaded de ${formatMegabytes(totalBytes)} (${formatMegabytes(remaining)} faltando)"
        } else {
            "$prefix: $downloaded baixados"
        }
    }

    private fun formatMegabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

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

    fun createRadioFromAlbum(album: LocalAlbum) {
        repository.createRadioFromAlbum(album)
        rebuildLibraryContent()
        showToast("Radio \"${album.title}\" criada.")
    }

    fun createRadioFromArtist(artist: LocalArtist) {
        repository.createRadioFromArtist(artist)
        rebuildLibraryContent()
        showToast("Radio \"${artist.name}\" criada.")
    }

    fun createRadioFromGenre(genre: LocalRadio) {
        if (repository.createRadioFromGenre(genre) == null) {
            showToast("Categoria sem musicas pra criar radio.")
            return
        }
        rebuildLibraryContent()
        showToast("Radio \"${genre.name}\" criada.")
    }

    fun deleteCustomRadio(radio: LocalRadio) {
        val customId = radio.customId ?: return
        repository.deleteCustomRadio(customId)
        rebuildLibraryContent()
        showToast("Radio \"${radio.name}\" removida.")
    }

    // Adiciona um artista/album como fonte extra de uma radio personalizada ja existente (botao
    // "+" na tela da radio). Só radio personalizada (isCustom) tem definicao persistida pra
    // estender - radio de perfil/genero/fallback nao aceita. Devolve a radio ja atualizada
    // (recomputada na hora, igual saveAlbumMetadataEdit/saveArtistMetadataEdits) pra tela poder
    // trocar o estado local sem esperar o rebuildLibraryContent assincrono.
    fun addArtistToRadio(radio: LocalRadio, artist: LocalArtist): LocalRadio? {
        val customId = radio.customId ?: return null
        repository.addSourceToCustomRadio(customId, repository.sourceIdForArtist(artist))
        rebuildLibraryContent()
        showToast("${artist.name} adicionado a \"${radio.name}\".")
        return repository.radiosFrom(libraryState.value.songs).firstOrNull { it.customId == customId }
    }

    fun addAlbumToRadio(radio: LocalRadio, album: LocalAlbum): LocalRadio? {
        val customId = radio.customId ?: return null
        repository.addSourceToCustomRadio(customId, repository.sourceIdForAlbum(album))
        rebuildLibraryContent()
        showToast("${album.title} adicionado a \"${radio.name}\".")
        return repository.radiosFrom(libraryState.value.songs).firstOrNull { it.customId == customId }
    }

    // Remove um artista/album adicionado depois (botao "-" na tela da radio) - so afeta fontes
    // extras, nunca a fonte original que criou a radio (ver MusicLibraryRepository.removeSourceFromCustomRadio).
    fun removeArtistFromRadio(radio: LocalRadio, artist: LocalArtist): LocalRadio? {
        val customId = radio.customId ?: return null
        repository.removeSourceFromCustomRadio(customId, repository.sourceIdForArtist(artist))
        rebuildLibraryContent()
        showToast("${artist.name} removido de \"${radio.name}\".")
        return repository.radiosFrom(libraryState.value.songs).firstOrNull { it.customId == customId }
    }

    fun removeAlbumFromRadio(radio: LocalRadio, album: LocalAlbum): LocalRadio? {
        val customId = radio.customId ?: return null
        repository.removeSourceFromCustomRadio(customId, repository.sourceIdForAlbum(album))
        rebuildLibraryContent()
        showToast("${album.title} removido de \"${radio.name}\".")
        return repository.radiosFrom(libraryState.value.songs).firstOrNull { it.customId == customId }
    }

    // Lista as fontes extras (id prefixado "album:<id>"/"artist:<chave>", ver
    // MusicLibraryRepository.sourceIdForAlbum/sourceIdForArtist) de uma radio personalizada, pra
    // tela resolver nome/capa cruzando contra as listas artists/albums ja carregadas.
    fun radioExtraSourceIds(radio: LocalRadio): List<String> {
        val customId = radio.customId ?: return emptyList()
        return repository.customRadioDefinitions().firstOrNull { it.id == customId }?.extraSourceIds.orEmpty()
    }

    // Renomeia radio personalizada (botao de lapis) - nao se aplica a radio criada de categoria
    // (ver MusicLibraryRepository.renameCustomRadio, que recusa sourceType "genre" devolvendo null).
    fun renameRadio(radio: LocalRadio, newName: String): LocalRadio? {
        val customId = radio.customId ?: return null
        repository.renameCustomRadio(customId, newName) ?: return null
        rebuildLibraryContent()
        showToast("Radio renomeada.")
        return repository.radiosFrom(libraryState.value.songs).firstOrNull { it.customId == customId }
    }

    // Radios de perfil/genero (Grunge, Anos 2000, Jazz etc.) nao tem definicao persistida pra
    // apagar - sao recalculadas da biblioteca toda vez. "Apagar" aqui so tira da lista (ver
    // MusicLibraryRepository.hideRadio); a vinheta por genero continua chaveada pelo nome
    // (VINHETA_BY_RADIO_KEY abaixo), entao volta intacta se a radio for desocultada depois.
    fun deleteRadio(radio: LocalRadio) {
        if (radio.isCustom) {
            deleteCustomRadio(radio)
        } else {
            repository.hideRadio(radio)
            rebuildLibraryContent()
            showToast("Radio \"${radio.name}\" removida.")
        }
    }

    fun hasHiddenRadios(): Boolean = repository.hiddenRadioKeys().isNotEmpty()

    fun restoreHiddenRadios() {
        repository.unhideAllRadios()
        rebuildLibraryContent()
        showToast("Radios ocultas restauradas.")
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
            setMediaItems(songs.map { it.toMediaItem(radioName) }, index, startPositionMs.coerceAtLeast(0L))
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

    // Botao de "reproducao automatica" na Home (ao lado da legenda) - liga/desliga o autoplay de
    // album seguinte. Nao mexe na fila atual, so no que acontece quando ela acabar sozinha.
    fun toggleAutoplay() {
        autoplayEnabled = !autoplayEnabled
        controller?.let { updatePlayerState(it) }
    }

    // Dispara quando a fila termina sozinha (Player.STATE_ENDED) com autoplay ligado e fora do
    // modo radio - pedido do usuario 15/09/2026, imitando o "continuar reproduzindo" do Youtube:
    // toca o proximo album do MESMO artista; se nao tiver outro album, pula pro proximo artista da
    // mesma categoria (genero) da faixa que acabou de tocar.
    private fun maybeAutoplayNextAlbum(player: Player) {
        if (player.playbackState != Player.STATE_ENDED) {
            autoplayHandledForEndedQueue = false
            return
        }
        if (!autoplayEnabled || activeRadioName.isNotBlank() || autoplayHandledForEndedQueue) return
        autoplayHandledForEndedQueue = true
        val finishedSongId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val finishedSong = libraryContentState.value.songs.firstOrNull { it.id == finishedSongId } ?: return
        val nextSongs = autoplayNextAlbumSongs(finishedSong) ?: return
        playSongs(nextSongs, source = "Reprodução automática")
    }

    // Ver maybeAutoplayNextAlbum acima - primeiro tenta o proximo album (por ano, depois titulo)
    // do mesmo artista da faixa que terminou; sem outro album, cai pro proximo artista dentro da
    // MESMA radio de categoria/genero que essa faixa pertence (reusa genreRadios ja calculado em
    // vez de reclassificar genero na mao - ver dynamicGenreRadios/genreEntriesFor). Faixa sem
    // genero classificado (tag vazia e nenhum palpite por metadado - MUITO comum em bibliotecas
    // locais reais, achado ao vivo 15/09/2026: "Chuva"/Froid ficava sem musica seguinte por causa
    // disso) cai por ultimo pro proximo artista de TODA a biblioteca (ordem alfabetica) - sem essa
    // ultima rede de seguranca o autoplay simplesmente parava mudo em vez de continuar tocando
    // algo, o que ia contra o pedido do usuario. Retorna null so quando a biblioteca inteira e de
    // um unico artista (nao ha mesmo pra onde ir).
    private fun autoplayNextAlbumSongs(finishedSong: LocalSong): List<LocalSong>? {
        val content = libraryContentState.value
        if (content.albums.isEmpty()) return null

        fun sortedAlbumSongs(album: LocalAlbum): List<LocalSong> =
            album.songs.sortedWith(compareBy<LocalSong> { it.trackNumber }.thenBy { it.title.lowercase() })

        fun albumYearOrder(album: LocalAlbum): Int = if (album.year > 0) album.year else Int.MAX_VALUE

        fun firstAlbumSongsFor(artistName: String): List<LocalSong>? =
            content.albums
                .filter { album -> album.songs.any { it.artist.equals(artistName, ignoreCase = true) } }
                .sortedWith(compareBy<LocalAlbum>({ albumYearOrder(it) }).thenBy { it.title.lowercase() })
                .firstOrNull()
                ?.let(::sortedAlbumSongs)

        fun nextInList(names: List<String>): String? {
            if (names.size < 2) return null
            val currentIndex = names.indexOfFirst { it.equals(finishedSong.artist, ignoreCase = true) }
                .let { if (it >= 0) it else 0 }
            return (1 until names.size)
                .map { offset -> names[(currentIndex + offset) % names.size] }
                .firstOrNull { !it.equals(finishedSong.artist, ignoreCase = true) }
        }

        val artistAlbums = content.albums
            .filter { album -> album.songs.any { it.artist.equals(finishedSong.artist, ignoreCase = true) } }
            .sortedWith(compareBy<LocalAlbum>({ albumYearOrder(it) }).thenBy { it.title.lowercase() })
        val currentAlbumIndex = artistAlbums.indexOfFirst { it.id == finishedSong.albumId }
        if (currentAlbumIndex in artistAlbums.indices && currentAlbumIndex < artistAlbums.lastIndex) {
            return sortedAlbumSongs(artistAlbums[currentAlbumIndex + 1])
        }

        val genreRadio = content.genreRadios.firstOrNull { radio -> radio.songs.any { it.id == finishedSong.id } }
        if (genreRadio != null) {
            val artistsInGenre = genreRadio.songs.map { it.artist }.distinct().sortedBy { it.lowercase() }
            nextInList(artistsInGenre)?.let { nextArtist -> firstAlbumSongsFor(nextArtist)?.let { return it } }
        }

        val allArtists = content.artists.map { it.name }.distinct().sortedBy { it.lowercase() }
        val nextArtist = nextInList(allArtists) ?: return null
        return firstAlbumSongsFor(nextArtist)
    }

    // Volume "alvo" pros 2 canais de audio da radio (controller = musica, announcementPlayer =
    // vinheta/passagem/boletim) - 0 quando o usuario mutou pelo mini player, 1 normal. Os pontos
    // que restauram volume depois de um fade (cancelEarlyNewsBreakIfPending/speakNextNewsBreak)
    // usam isso em vez de 1f fixo, senao o boletim seguinte desmutava a radio sozinho.
    private fun radioVolume(): Float = if (radioMuted) 0f else 1f

    // Botao de mute do mini player em modo radio (substitui o play/pause ali - pedido do usuario
    // 11/09/2026: radio "ao vivo" continua avancando/contando tempo, so silencia, nao pausa de
    // verdade). Aplica nos 2 canais de audio na hora (musica + o que estiver tocando agora no
    // announcementPlayer, seja vinheta/passagem/boletim).
    fun toggleRadioMute() {
        if (activeRadioName.isBlank()) return
        radioMuted = !radioMuted
        val target = radioVolume()
        controller?.volume = target
        announcementPlayer?.setVolume(target, target)
        controller?.let { updatePlayerState(it) }
    }

    // Botao de deslike do mini player em modo radio (pedido do usuario 11/09/2026): faixas que
    // caem na radio mas nao sao musica de verdade pra aquele contexto (intro, outro, faixa de
    // transicao) - deslike na faixa TOCANDO AGORA faz duas coisas: troca ela na hora por outra
    // faixa do MESMO album (LocalSong.albumId), e marca o id em
    // MusicLibraryRepository.dislikeSongForRadio pra nunca mais ser escolhida no shuffle de
    // nenhuma radio (ver filtro em radioSessionFrom). So o resto do album continua tocando
    // normal - diferente de ocultar artista/album inteiro.
    fun dislikeCurrentRadioSong(): RadioDislikeResult? {
        if (activeRadioName.isBlank()) return null
        val player = controller ?: return null
        val songId = playerState.value.songId ?: return null
        val song = libraryState.value.songs.firstOrNull { it.id == songId } ?: return null
        repository.dislikeSongForRadio(songId)
        val disliked = repository.radioDislikedSongIds()
        // Prefere uma faixa do album que ainda nao esta na fila ao vivo (evita repetir a mesma
        // faixa 2x na mesma sessao) - se todas as opcoes ja estiverem na fila, tanto faz, usa
        // qualquer uma mesmo (melhor repetir que nao trocar).
        val queuedIds = (0 until player.mediaItemCount).mapNotNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull()
        }.toSet()
        val albumSongs = libraryState.value.songs.filter { it.albumId == song.albumId && it.id !in disliked }
        val replacement = albumSongs.filterNot { it.id in queuedIds }.ifEmpty { albumSongs }.randomOrNull()
        val index = player.currentMediaItemIndex
        val result = if (replacement != null) {
            player.replaceMediaItem(index, replacement.toMediaItem(activeRadioName))
            RadioDislikeResult(index, replacement)
        } else {
            // Nenhuma outra faixa sobrou nesse album (so tinha essa, ou as outras ja estavam
            // deslikadas) - pula pra proxima da fila em vez de deixar a faixa deslikada tocando.
            player.seekToNextMediaItem()
            null
        }
        updatePlayerState(player)
        showToast("Faixa removida da radio - nao vai mais tocar aqui.")
        return result
    }

    // "Sair da radio" - ao contrario de so fechar a tela (que deixa a radio tocando em
    // segundo plano, ver openActiveRadio em LocalTuneApp.kt), isso para a reproducao de
    // verdade e esvazia a fila: mediaItemCount some, hasMedia fica false, mini player some.
    // Toca uma vinheta de despedida por cima (tchauzinho/ate mais, alternando - pedido do
    // usuario 15/09/2026) antes de esvaziar a fila de verdade.
    fun stopRadio() {
        val hadRadio = activeRadioName.isNotBlank()
        stopRadioNewsMode()
        playbackSource = ""
        controller?.pause()
        if (hadRadio) {
            playExitVinheta()
        } else {
            controller?.apply {
                stop()
                clearMediaItems()
            }
        }
    }

    private fun playExitVinheta() {
        val resId = EXIT_VINHETA_RESOURCES[nextExitVinhetaIndex % EXIT_VINHETA_RESOURCES.size]
        nextExitVinhetaIndex++
        playVinhetaResource(resId) {
            controller?.apply {
                stop()
                clearMediaItems()
            }
        }
    }

    // (Re)inicia a contagem de SLEEP_TIMER_IDLE_MS do timer "voce ainda esta ai?" - chamado ao
    // entrar numa radio e de novo sempre que o usuario confirma que continua ouvindo
    // (confirmStillListening), pra recomecar do zero.
    private fun armSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = viewModelScope.launch {
            delay(SLEEP_TIMER_IDLE_MS)
            triggerSleepCheck()
        }
    }

    private fun triggerSleepCheck() {
        if (activeRadioName.isBlank()) return
        sleepCheckPending = true
        controller?.let { updatePlayerState(it) }
        sleepAutoStopJob = viewModelScope.launch {
            delay(SLEEP_TIMER_RESPONSE_MS)
            sleepCheckPending = false
            stopRadio()
        }
    }

    // Botao "Sim" do aviso de inatividade (LocalTuneApp.kt) - cancela o desligamento automatico
    // pendente e reinicia a contagem de 1h30 do zero.
    fun confirmStillListening() {
        if (!sleepCheckPending) return
        sleepAutoStopJob?.cancel()
        sleepAutoStopJob = null
        sleepCheckPending = false
        controller?.let { updatePlayerState(it) }
        armSleepTimer()
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepAutoStopJob?.cancel()
        sleepAutoStopJob = null
        sleepCheckPending = false
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

    private fun startRadioNewsMode(radioName: String) {
        radioNewsEnabled = true
        completedRadioSongs = 0
        // NAO limpa bulletinBuffer - o buffer (roda desde a abertura do app) e 100%
        // radio-agnostico de proposito (pedido do usuario 03/09/2026: "posso entrar em qualquer
        // radio e ainda assim o boletim funcionar"; boletim nunca cita musica/radio), entao o
        // trabalho ja baixado do feed remoto sobrevive a troca de radio e a fila continua
        // exatamente de onde estava (unificacao 10/09/2026 - antes cada radio "recomecava a fila
        // do zero" por simplicidade, o que na pratica descartava boletins prontos so por causa de
        // timing).
        speakingNews = false
        newsBreakEnding = false
        currentNewsHeadline = ""
        resumeAfterNews = false
        pendingVinheta = false
        activeRadioName = radioName
        playbackSource = "Rádio $radioName"
        armSleepTimer()

        // Buffer (100% alimentado pelo feed remoto, ver refillBulletinBuffer) roda desde a
        // abertura do app - no-op aqui se ja estiver cheio.
        refillBulletinBuffer()
    }

    private fun stopRadioNewsMode() {
        radioNewsEnabled = false
        completedRadioSongs = 0
        // bulletinBuffer/bulletinPrepJob NAO param aqui - continuam preparando mesmo sem radio
        // tocando (pedido do usuario 03/09/2026), o buffer e 100% independente de radio ativa.
        speakingNews = false
        newsBreakEnding = false
        currentNewsHeadline = ""
        resumeAfterNews = false
        pendingVinheta = false
        activeRadioName = ""
        cancelSleepTimer()
        announcementPlayer?.release()
        announcementPlayer = null
        // Radio pode ser desligada no meio do fade de checkForEarlyNewsBreak (ver la) - restaura
        // o volume senao a musica fica muda dependendo de onde o fade parou. Sempre 1f (nao
        // radioVolume()) e reseta o mute - cada sessao de radio nova comeca sem mute.
        newsBreakFadeInProgress = false
        newsBreakSkipsAheadOnResume = false
        radioMuted = false
        controller?.volume = 1f
    }

    private fun playRadioVinhetas(radioName: String) {
        pendingVinheta = true
        val complementRes = VINHETA_BY_RADIO_KEY[normalizeRadioKey(radioName)]
        Log.d(TAG_RADIO_VOICE, "vinheta: intro para '$radioName' (complemento=${complementRes != null})")
        playVinhetaResource(R.raw.radio_intro) {
            if (!pendingVinheta || activeRadioName != radioName) return@playVinhetaResource
            if (complementRes != null) {
                Log.d(TAG_RADIO_VOICE, "vinheta: complemento para '$radioName'")
                playVinhetaResource(complementRes) { finishVinhetas(radioName) }
            } else {
                finishVinhetas(radioName)
            }
        }
    }

    private fun finishVinhetas(radioName: String) {
        if (!pendingVinheta || activeRadioName != radioName) return
        pendingVinheta = false
        Log.d(TAG_RADIO_VOICE, "vinheta: fim, dando play em '$radioName'")
        viewModelScope.launch {
            controller?.play()
            controller?.let { updatePlayerState(it) }
        }
    }

    private fun playVinhetaResource(resId: Int, volume: Float = 1.0f, onFinished: () -> Unit) {
        announcementPlayer?.release()
        announcementPlayer = null
        val player = runCatching {
            MediaPlayer.create(getApplication(), resId)
        }.getOrNull()
        if (player == null) {
            Log.w(TAG_RADIO_VOICE, "vinheta: falhou ao criar MediaPlayer pro recurso $resId - pulando")
            onFinished()
            return
        }
        // Multiplica por radioVolume() (nao aplica o volume cru) - zera a vinheta junto quando
        // o usuario mutou no mini player (ver toggleRadioMute).
        val applied = volume * radioVolume()
        player.setVolume(applied, applied)
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

    private fun currentReservedBulletinStoryKeys(): MutableSet<String> {
        val reserved = recentBulletinStoryKeys.toMutableSet()
        bulletinBuffer.forEach { item -> item.script.newsReservationKey().takeIf { it.isNotBlank() }?.let(reserved::add) }
        return reserved
    }

    private fun RadioScript.newsReservationKey(): String =
        "${story.source.normalizedBulletinKeyPart()}|${story.title.normalizedBulletinKeyPart()}"

    private fun String.normalizedBulletinKeyPart(): String {
        val withoutArtifacts = replace(Regex("\\b(?:IMG|SI)_[\\p{Alnum}_-]+\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\bgettyimages-[a-z0-9-]+\\b", RegexOption.IGNORE_CASE), " ")
        val withoutAccents = Normalizer.normalize(withoutArtifacts, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun rememberRecentBulletinStory(script: RadioScript) {
        val key = script.newsReservationKey()
        if (key.isBlank()) return
        while (recentBulletinStoryKeys.remove(key)) Unit
        recentBulletinStoryKeys.addLast(key)
        while (recentBulletinStoryKeys.size > RECENT_BULLETIN_STORY_KEY_LIMIT) {
            recentBulletinStoryKeys.removeFirst()
        }
        radioPrefs.edit()
            .putString(KEY_RECENT_BULLETIN_STORY_KEYS, recentBulletinStoryKeys.joinToString("\n"))
            .apply()
    }

    // Verifica um boletim "especial" pendente na abertura do app e, se achar, fura a fila na
    // hora - ver ADR-037 em docs/DECISIONS.md. Chamada de proposito ANTES de
    // refillBulletinBuffer() (mesma corrotina, nunca em paralelo com ele) e ignora
    // BULLETIN_BUFFER_TARGET: mesmo com o buffer ja cheio de itens normais (refillBulletinBuffer
    // sozinho seria um no-op nesse caso, ve o teto batido e nem consulta o feed), este metodo
    // ainda baixa e insere o especial. Pode deixar bulletinBuffer com TARGET + 1 item
    // temporariamente - aceito de proposito, nunca descarta um item normal ja baixado pra abrir
    // espaco (mesma decisao consciente do resto do ADR-037); a fila assenta de volta no teto
    // sozinha assim que esse especial tocar. No-op rapido (sem tocar rede) se ja existe um
    // especial no buffer - nao empilha vários furando fila ao mesmo tempo.
    private suspend fun checkForSpecialOnAppOpen() {
        if (bulletinBuffer.any { it.script.isSpecial }) {
            Log.d(TAG_RADIO_VOICE, "boletim: checkForSpecialOnAppOpen - ja tem especial no buffer, pulando")
            return
        }
        Log.d(TAG_RADIO_VOICE, "boletim: checkForSpecialOnAppOpen - consultando feed")
        val remoteBulletin = withTimeoutOrNull(REMOTE_FEED_TIMEOUT_MS) {
            broadcastFeedRepository.downloadNextApprovedBulletin(
                targetDir = coreBufferDir,
                reservedKeys = currentReservedBulletinStoryKeys(),
                specialOnly = true,
            )
        }
        if (remoteBulletin == null) {
            Log.d(TAG_RADIO_VOICE, "boletim: checkForSpecialOnAppOpen - nenhum especial novo pra baixar")
            return
        }
        rememberRecentBulletinStory(remoteBulletin.script)
        bulletinBuffer.addFirst(
            PreparedBulletin(script = remoteBulletin.script, file = remoteBulletin.audioFile),
        )
        saveCoreBufferManifest()
        syncBulletinBufferState()
        Log.d(TAG_RADIO_VOICE, "boletim: especial furou fila na abertura do app id=${remoteBulletin.id}")
    }

    // Mantem bulletinBuffer com BULLETIN_BUFFER_TARGET itens prontos (roteiro decorado + WAV
    // sintetizado, se a voz local/Gemini estiver ativa) - roda o tempo todo, independente de
    // radio ativa ou nao (pedido do usuario 03/09/2026), e SOBREVIVE a troca de radio (pedido do
    // usuario 10/09/2026: "o boletim e independente" - trocar/sair de uma radio nunca cancela
    // nem descarta um preparo em andamento, ver comentario em bulletinBuffer). Um item de cada
    // vez, nunca dois engines de voz/LLM carregados ao mesmo tempo (mesma cautela de
    // TTS.md/ADR-002/ADR-003). Chamada nao bloqueia: se ja tem um preparo rodando
    // (bulletinPrepJob ativo), o buffer ja esta cheio, ou o preparo esta pausado, e um no-op -
    // seguro chamar de qualquer lugar (abertura do app, transicao de faixa, fim de boletim,
    // reload de feed).
    // Simplificado 16/09/2026 (ADR a documentar): o unico produtor de boletim agora e o feed
    // remoto (BroadcastFeedRepository) - a central de broadcast fora do app ja escreve e
    // sintetiza tudo. Sem boletim aprovado novo, a vaga fica vazia e a rodada para (sem RSS,
    // sem redator local/Gemini, sem sintese de voz local/Gemini local). Ver antiga versao desta
    // funcao no historico do git pra referencia do pipeline local que existia antes.
    private fun refillBulletinBuffer() {
        if (bulletinPrepPaused) return
        if (bulletinPrepJob?.isActive == true) return
        if (bulletinBuffer.size >= BULLETIN_BUFFER_TARGET) return
        bulletinPrepJob = viewModelScope.launch {
            try {
                while (bulletinBuffer.size < BULLETIN_BUFFER_TARGET && !bulletinPrepPaused) {
                    val bufferPositionBeforeThisItem = bulletinBuffer.size
                    // Ultimas BULLETIN_BUFFER_SPECIAL_RESERVED_SLOTS vagas sao reservadas pra
                    // especial (ver ADR-037): uma vez que o buffer ja tem
                    // BULLETIN_BUFFER_TARGET - BULLETIN_BUFFER_SPECIAL_RESERVED_SLOTS itens
                    // normais, para de aceitar normal ate a meta e so busca especial pras vagas
                    // que sobraram. Sem isso, um buffer que o app mantem sempre cheio de itens
                    // normais so buscaria um especial recem-aprovado depois de esvaziar tudo.
                    val normalCount = bulletinBuffer.count { !it.script.isSpecial }
                    val specialOnly = normalCount >= (BULLETIN_BUFFER_TARGET - BULLETIN_BUFFER_SPECIAL_RESERVED_SLOTS)
                    syncBulletinBufferState(
                        isPreparing = true,
                        statusMessage = if (specialOnly) {
                            "Vagas reservadas p/ especial: verificando feed Pailer FM..."
                        } else {
                            "Buscando boletim aprovado no feed Pailer FM..."
                        },
                        progressPercent = 5,
                    )
                    try {
                        Log.d(
                            TAG_RADIO_VOICE,
                            "boletim: buscando posicao=$bufferPositionBeforeThisItem specialOnly=$specialOnly",
                        )
                        // withTimeoutOrNull aqui (alem do timeout interno de conexao do
                        // BroadcastFeedRepository) porque hang de DNS/handshake em algumas
                        // redes Android nao respeita connectTimeout do HttpURLConnection -
                        // sem isso, uma rede ruim travava a rodada INTEIRA do buffer, descoberto
                        // ao vivo em teste 16/09/2026.
                        val remoteBulletin = withTimeoutOrNull(REMOTE_FEED_TIMEOUT_MS) {
                            broadcastFeedRepository.downloadNextApprovedBulletin(
                                targetDir = coreBufferDir,
                                reservedKeys = currentReservedBulletinStoryKeys(),
                                specialOnly = specialOnly,
                            )
                        }
                        if (remoteBulletin == null) {
                            // Feed sem boletim novo pra essa vaga (ou, em modo specialOnly, sem
                            // especial pendente agora). Desiste sem erro; os proximos gatilhos
                            // (abertura do app, troca de faixa, fim de boletim) tentam de novo -
                            // as vagas reservadas ficam livres ate um especial aparecer.
                            syncBulletinBufferState(isPreparing = false, statusMessage = null)
                            break
                        }
                        rememberRecentBulletinStory(remoteBulletin.script)
                        val preparedBulletin = PreparedBulletin(
                            script = remoteBulletin.script,
                            file = remoteBulletin.audioFile,
                            scriptFromGemini = false,
                            voiceFromGemini = false,
                        )
                        // Especial fura fila do buffer (ver ADR-037 em docs/DECISIONS.md): entra
                        // na FRENTE em vez do fim, pra ser o proximo escolhido em
                        // dequeueBufferedBulletinForPlayback() (removeFirst simples - nenhuma
                        // mudanca precisa la, so na ordem de insercao aqui).
                        if (remoteBulletin.script.isSpecial) {
                            bulletinBuffer.addFirst(preparedBulletin)
                        } else {
                            bulletinBuffer.addLast(preparedBulletin)
                        }
                        // Persiste AGORA que o item esta de verdade no buffer - sem isso, um
                        // processo morto logo depois (comum no Android, app em segundo plano)
                        // perdia boletins prontos que ainda nao tinham sido salvos em disco.
                        saveCoreBufferManifest()
                        Log.d(TAG_RADIO_VOICE, "boletim: baixado do feed aprovado id=${remoteBulletin.id} arquivo=${remoteBulletin.audioFile.name}")
                        Log.d(TAG_RADIO_VOICE, "boletim: buffer agora com ${bulletinBuffer.size}/$BULLETIN_BUFFER_TARGET")
                        syncBulletinBufferState(isPreparing = false, statusMessage = null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.e(TAG_RADIO_VOICE, "boletim: erro inesperado baixando item $bufferPositionBeforeThisItem do buffer", e)
                        syncBulletinBufferState(
                            isPreparing = false,
                            statusMessage = "Erro ao baixar boletim: ${e.message ?: e::class.simpleName} (ver logcat, tag $TAG_RADIO_VOICE).",
                        )
                        break
                    }
                }
            } finally {
                // Roda tambem em cancelamento (pauseBulletinPreparation/resetBulletinBuffer) -
                // garante que a UI nunca fique presa mostrando "buscando" depois que o job parou.
                syncBulletinBufferState(isPreparing = false, statusMessage = null)
            }
        }
        syncBulletinBufferState(isPreparing = true)
    }

    // Apaga tudo que ja estava pronto no buffer e cancela qualquer preparo em andamento - usado
    // SO pelo botao manual "Resetar" (resetBulletinBuffer, pedido do usuario 03/09/2026). Trocar
    // ou sair de uma radio NUNCA chama isso (unificacao 10/09/2026) - o buffer e 100%
    // independente de qual radio esta ativa, entao so o usuario pedindo explicitamente apaga
    // trabalho ja pronto.
    private fun clearBulletinBuffer() {
        bulletinPrepJob?.cancel()
        bulletinPrepJob = null
        bulletinBuffer.forEach { it.file?.delete() }
        bulletinBuffer.clear()
        saveCoreBufferManifest()
        syncBulletinBufferState(isPreparing = false, statusMessage = null)
    }

    // Pasta privada do app em filesDir (NAO cacheDir) - sobrevive a "limpar cache" e a
    // reinicio/morte do processo, so some se o usuario fizer "Limpar dados" do app. Sem
    // permissao nenhuma envolvida (filesDir e sempre privado ao proprio app).
    private val coreBufferDir: File
        get() = getApplication<Application>().filesDir
            .resolve(CORE_BUFFER_DIR_NAME).apply { mkdirs() }

    // Grava o estado atual do bulletinBuffer em disco - pedido do usuario (03/09/2026): boletins
    // ja escritos nao podiam sumir so porque o app reiniciou ou o Android limpou o cache. Tambem
    // varre a pasta e apaga .wav que sobrou de item ja consumido/removido do buffer (evita
    // acumular lixo) - chamar sempre que bulletinBuffer mudar de conteudo. Unificado em
    // 10/09/2026: antes salvava a uniao de DOIS buffers separados (cada um convertido pro
    // formato do outro pra caber no mesmo JSON); hoje e so o buffer unico, sem conversao. Chave
    // JSON "coreFile" mantida (em vez de renomear pra "file") de proposito - preserva
    // compatibilidade com o manifest ja gravado em disco pelas versoes anteriores, sem precisar
    // de nenhuma migracao.
    private fun saveCoreBufferManifest() {
        runCatching {
            val array = JSONArray()
            bulletinBuffer.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("title", item.script.story.title)
                        put("source", item.script.story.source)
                        put("summary", item.script.story.summary)
                        put("scriptSource", item.script.source.name)
                        put("duration", item.script.duration.name)
                        put("special", item.script.isSpecial)
                        put(
                            "lines",
                            JSONArray().apply {
                                item.script.lines.forEach { line ->
                                    put(
                                        JSONObject().apply {
                                            put("speaker", line.speaker.name)
                                            put("text", line.text)
                                        },
                                    )
                                }
                            },
                        )
                        put("coreFile", item.file?.name)
                        // Sem isso, todo restart de processo (comum no Android - app em segundo
                        // plano morto pelo sistema) perdia essa info e a bolinha verde/azul
                        // (RadioBulletinBufferStatusCard) voltava sempre azul pra qualquer item
                        // recarregado do manifest, mesmo tendo sido 100% Gemini de verdade
                        // (achado ao vivo 10/09/2026, logo apos reinstalar com essa feature nova).
                        put("scriptFromGemini", item.scriptFromGemini)
                        put("voiceFromGemini", item.voiceFromGemini)
                    },
                )
            }
            coreBufferDir.resolve(CORE_BUFFER_MANIFEST_FILE).writeText(array.toString())
            // Varre a pasta e apaga .wav orfao (ja consumido/removido do buffer, ou baixado mas
            // nunca registrado por alguma falha). ORPHAN_CLEANUP_GRACE_MS da folga suficiente pra
            // um download em andamento (BroadcastFeedRepository) terminar antes de ser considerado
            // lixo - bem menor hoje do que quando precisava cobrir sintese local/Gemini de varios
            // minutos (removida 16/09/2026).
            val referenced = (bulletinBuffer.mapNotNull { it.file?.name } +
                listOfNotNull(protectedBulletinPlaybackFileName)).toSet()
            val now = System.currentTimeMillis()
            coreBufferDir.listFiles { file ->
                file.name != CORE_BUFFER_MANIFEST_FILE &&
                    file.name !in referenced &&
                    (now - file.lastModified()) > ORPHAN_CLEANUP_GRACE_MS
            }?.forEach { it.delete() }
        }.onFailure { Log.w(TAG_RADIO_VOICE, "boletim: falha ao salvar manifest do buffer em disco", it) }
    }

    // Le o manifest salvo (se existir) e repopula bulletinBuffer - chamado uma vez na abertura do
    // app, antes do primeiro refillBulletinBuffer(). Descarta silenciosamente qualquer entrada cujo
    // .wav nao exista mais em disco (sobrevive a "Limpar dados" parcial/corrupcao) em vez de
    // travar a restauracao inteira. Nao trunca em BULLETIN_BUFFER_TARGET aqui de proposito -
    // trabalho ja pago (LLM+voz) nunca e descartado so por restaurar acima da meta (pode
    // acontecer uma vez so, restaurando de um manifest salvo por uma versao anterior com o bug
    // de estoque duplicado); refillBulletinBuffer() so para de PRODUZIR mais acima da meta, o que
    // ja existe fica e drena sozinho conforme toca.
    private fun loadCoreBufferManifest() {
        runCatching {
            val manifestFile = coreBufferDir.resolve(CORE_BUFFER_MANIFEST_FILE)
            if (!manifestFile.exists()) return
            val array = JSONArray(manifestFile.readText())
            val restored = (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val json = array.getJSONObject(index)
                    val story = com.pailer.localtune.data.NewsStory(
                        title = json.getString("title"),
                        source = json.getString("source"),
                        summary = json.optString("summary"),
                    )
                    val linesJson = json.getJSONArray("lines")
                    val lines = (0 until linesJson.length()).map { lineIndex ->
                        val lineJson = linesJson.getJSONObject(lineIndex)
                        RadioScriptLine(
                            speaker = RadioSpeaker.valueOf(lineJson.getString("speaker")),
                            text = lineJson.getString("text"),
                        )
                    }
                    val script = RadioScript(
                        story = story,
                        lines = lines,
                        source = RadioScriptSource.valueOf(json.getString("scriptSource")),
                        duration = com.pailer.localtune.data.RadioBulletinDuration.valueOf(json.getString("duration")),
                        // optBoolean(_, false): manifest gravado antes desse campo existir volta
                        // sem ele - trata como normal, nao especial (mesmo padrao de
                        // scriptFromGemini/voiceFromGemini logo abaixo).
                        isSpecial = json.optBoolean("special", false),
                    )
                    val file = json.optString("coreFile").takeIf { it.isNotBlank() }
                        ?.let { coreBufferDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    // optBoolean(_, false): manifest gravado ANTES desses 2 campos existirem volta
                    // sem eles - default false/"nao sei" e proposital, ver PreparedBulletin.
                    PreparedBulletin(
                        script, file,
                        scriptFromGemini = json.optBoolean("scriptFromGemini", false),
                        voiceFromGemini = json.optBoolean("voiceFromGemini", false),
                    )
                }.getOrNull()
            }
            bulletinBuffer.clear()
            bulletinBuffer.addAll(restored)
            Log.d(TAG_RADIO_VOICE, "boletim: restaurados ${bulletinBuffer.size}/${array.length()} boletins do disco")
        }.onFailure { Log.w(TAG_RADIO_VOICE, "boletim: falha ao restaurar buffer do disco", it) }
    }

    private fun syncBulletinBufferState(
        isPreparing: Boolean = radioBulletinBufferState.value.isPreparing,
        statusMessage: String? = radioBulletinBufferState.value.statusMessage,
        // Some sozinho quando a mensagem some junto (nova etapa/fim) - assim quem so passa
        // statusMessage=null pra "limpar" nao deixa um percentual velho grudado na tela.
        progressPercent: Int? = if (statusMessage == null) null else radioBulletinBufferState.value.progressPercent,
    ) {
        // Redator/roteiro em nuvem removido (16/09/2026) - todo item do buffer agora vem do feed
        // remoto (RadioScriptSource.BroadcastFeed), entao script/fallback/Gemini nunca mais
        // variam; os campos correspondentes de RadioBulletinBufferUiState ficam nos defaults ate
        // a tela "Boletins da radio" ser removida (estagio seguinte da limpeza).
        radioBulletinBufferState.value = radioBulletinBufferState.value.copy(
            readyCount = bulletinBuffer.size,
            isPreparing = isPreparing,
            isPaused = bulletinPrepPaused,
            statusMessage = statusMessage,
            progressPercent = progressPercent,
            hasPlayableAudio = bulletinBuffer.any { it.file?.exists() == true },
        )
    }

    // Pausa o preparo em segundo plano (pedido do usuario 03/09/2026): cancela o job em
    // andamento (ver ressalva de cancelamento cooperativo no finally de refillBulletinBuffer) e
    // bloqueia refillBulletinBuffer() de comecar outro ate resumeBulletinPreparation() ser
    // chamado - o buffer ja pronto continua disponivel, so para de crescer/repor.
    fun pauseBulletinPreparation() {
        if (bulletinPrepPaused) return
        bulletinPrepPaused = true
        bulletinPrepJob?.cancel()
        bulletinPrepJob = null
        fallbackAutoFixJob?.cancel()
        fallbackAutoFixJob = null
        syncBulletinBufferState(isPreparing = false, statusMessage = null)
    }

    fun resumeBulletinPreparation() {
        if (!bulletinPrepPaused) return
        bulletinPrepPaused = false
        syncBulletinBufferState()
        refillBulletinBuffer()
    }

    // Apaga tudo que ja estava pronto no buffer e comeca de novo do zero (pedido do usuario
    // 03/09/2026) - se estiver pausado, so limpa e fica vazio ate o usuario retomar.
    fun resetBulletinBuffer() {
        clearBulletinBuffer()
        if (!bulletinPrepPaused) refillBulletinBuffer()
    }

    // Achado 11/09/2026 (segunda rodada, ao vivo): separado de fixFallbackBulletins porque esse
    // caso NAO precisa do throttle de 45s (FALLBACK_AUTO_FIX_DELAY_MS) - remover uma referencia de
    // arquivo que ja sumiu do disco (corrida com o sweep de orfaos em saveCoreBufferManifest, ver
    // ORPHAN_CLEANUP_GRACE_MS) e so leitura local, nao gasta cota nem risco de martelar Gemini/
    // local. Rodar isso a cada poll (init{}) em vez de esperar o job throttled fecha a janela em
    // que um item "verdinho" (fullGeminiSlots, 100% Gemini) fica na frente da fila com o audio ja
    // sumido mas a bolinha ainda mostrando verde - se um boletim novo caisse dentro dos 45s de
    // espera do throttle antigo, tocava TTS Android cru mesmo com tudo "verde" na tela.
    // So mexe em item cujo `file` NAO E NULO mas nao existe mais (tinha audio, sumiu depois) -
    // item com `file == null` desde o começo (sintese nunca rolou) fica pro fixFallbackBulletins
    // throttled, que e quem de fato tenta gerar audio de novo (chamada de rede/servico).
    private fun purgeVanishedBulletinAudio() {
        val vanished = bulletinBuffer.filter { it.file != null && !it.file.exists() }
        if (vanished.isEmpty()) return
        bulletinBuffer.removeAll(vanished)
        saveCoreBufferManifest()
        Log.w(TAG_RADIO_VOICE, "boletim: removidos ${vanished.size} item(ns) com audio sumido do disco (arquivo nao existe mais) - refill vai repor")
        syncBulletinBufferState()
        if (!bulletinPrepPaused) refillBulletinBuffer()
    }

    // Botao manual "Corrigir fallback" (pedido do usuario 05/09/2026, estendido 11/09/2026):
    // descarta os itens SEM CHANCE de tocar com voz de verdade e deixa o preparo normal
    // (refillBulletinBuffer) repor essas vagas do zero - o que tenta Gemini primeiro de novo, com
    // a mesma prioridade/retry de qualquer boletim novo. Dois motivos pegam um item aqui:
    // (1) roteiro generico (RadioScriptSource.Fallback - Gemini E redator local falharam os dois
    // na ESCRITA), ou (2) audio ausente (it.file == null - a sintese de voz falhou pra esse
    // roteiro especifico). O caso "tinha arquivo e sumiu depois" agora e tratado sem throttle por
    // purgeVanishedBulletinAudio() (chamado a cada poll do init{}) - mas o `!it.file.exists()`
    // continua aqui tambem, de defesa, caso essa funcao rode antes do proximo poll.
    // Achado 11/09/2026 ao vivo: sem o motivo (2), um item ficava "pronto" pra sempre no buffer
    // (contando em readyCount, podendo ate ser fullGeminiSlots) mas SEM audio - speakNextNewsBreak
    // so descobre isso na hora de tocar (readyFile = prepared.file?.takeIf { it.exists() } null) e
    // cai pro TTS cru do Android, sem nunca tentar resintetizar esse item.
    // IMPORTANTE - protege os boletins 100% Gemini (fullGeminiSlots, os "verdinhos"): so remove
    // por (2) quando o arquivo comprovadamente NAO EXISTE MAIS (it.file == null ou
    // exists() == false) - nunca um item cujo audio ainda esta la, seja ele Gemini, local ou
    // misto. Um verdinho com arquivo intacto nunca cai nesse filtro.
    // Nao mexe nos itens que ja saíram com roteiro de LLM E audio de verdade, so remove o que
    // precisa mesmo ser reescrito/resintetizado. Mesma funcao roda sozinha via
    // scheduleFallbackAutoFix() assim que um item quebrado (por (1) ou (2)) entra no buffer, sem
    // precisar o usuario apertar nada.
    fun fixFallbackBulletins() {
        val broken = bulletinBuffer.filter {
            it.script.source == RadioScriptSource.Fallback || it.file == null || !it.file.exists()
        }
        if (broken.isEmpty()) return
        broken.forEach { it.file?.delete() }
        bulletinBuffer.removeAll(broken)
        saveCoreBufferManifest()
        Log.d(TAG_RADIO_VOICE, "boletim: corrigindo fallback - removidos ${broken.size} do buffer pra regenerar")
        syncBulletinBufferState()
        if (!bulletinPrepPaused) refillBulletinBuffer()
    }

    // Dispara fixFallbackBulletins() sozinho assim que o sistema percebe um item quebrado (roteiro
    // em fallback OU sem audio, ver fixFallbackBulletins) novo no buffer (ver bulletinBuffer.addLast
    // em refillBulletinBuffer) - pedido do usuario (05/09/2026): "que ele mesmo comece a rodar a
    // correcao" sem precisar apertar o botao manual toda vez. FALLBACK_AUTO_FIX_DELAY_MS de
    // respiro antes de tentar, e o guard de job ativo evita agendar em cima de uma correcao que ja
    // esta rodando - se a causa raiz (sem internet, Gemini fora do ar, chave invalida, voz local
    // desligada) ainda nao se resolveu, a proxima geracao tambem vai cair quebrada e agendar de
    // novo sozinha, criando um retry periodico (nao um loop apertado).
    private fun scheduleFallbackAutoFix() {
        if (fallbackAutoFixJob?.isActive == true) return
        fallbackAutoFixJob = viewModelScope.launch {
            delay(FALLBACK_AUTO_FIX_DELAY_MS)
            fixFallbackBulletins()
        }
    }

    // Chamado no polling de posicao (ver init{}) enquanto uma radio com boletins ligados esta
    // tocando. Pedido do usuario (09/09/2026): o boletim pode comecar 5s antes do fim da ultima
    // musica, pra nunca deixar a proxima musica comecar a tocar antes dele - do jeito antigo
    // (so no onMediaItemTransition, reason AUTO) a proxima faixa ja tinha comecado a tocar
    // quando o boletim pausava ela de volta. Detecta a faixa que vai completar o intervalo de
    // songsBetweenBulletins ANTES dela realmente terminar, e dispara o fade + boletim ali, sem
    // deixar o ExoPlayer chegar a avancar pra proxima faixa sozinho.
    private fun checkForEarlyNewsBreak(player: Player) {
        if (!radioNewsEnabled || speakingNews || newsBreakFadeInProgress || !player.isPlaying) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = duration - player.currentPosition
        if (remaining !in 0..NEWS_BREAK_LEAD_MS) return
        val interval = radioBulletinState.value.settings.songsBetweenBulletins.coerceAtLeast(1)
        if ((completedRadioSongs + 1) % interval != 0) return

        completedRadioSongs += 1
        newsBreakFadeInProgress = true
        newsBreakSkipsAheadOnResume = true
        viewModelScope.launch {
            fadeOutRadioVolume(player)
            newsBreakFadeInProgress = false
            speakNextNewsBreak()
        }
    }

    // Fade linear de volume da musica dentro da janela de NEWS_BREAK_LEAD_MS antes do boletim -
    // pedido do usuario pra a musica "entrar como uma especie de fade out" em vez de ser cortada
    // seca quando checkForEarlyNewsBreak pausa ela pra dar lugar ao boletim.
    private suspend fun fadeOutRadioVolume(player: Player) {
        val steps = 10
        val stepDelayMs = NEWS_BREAK_FADE_MS / steps
        for (step in steps downTo 1) {
            player.volume = step.toFloat() / steps
            delay(stepDelayMs)
        }
        player.volume = 0f
    }

    // Desfaz o fade/gatilho antecipado quando speakNextNewsBreak() acaba desistindo de falar
    // (buffer sem audio pronto, controller sumiu) - sem isso a musica ficava travada pausada ou
    // tocando em volume 0 pro resto da faixa.
    private fun cancelEarlyNewsBreakIfPending() {
        if (!newsBreakSkipsAheadOnResume) return
        newsBreakSkipsAheadOnResume = false
        controller?.volume = radioVolume()
    }

    private fun PreparedBulletin.playableAudioFile(): File? =
        file?.takeIf { it.exists() && it.length() > WAV_HEADER_SIZE }

    private fun dequeueBufferedBulletinForPlayback(): Pair<PreparedBulletin?, Int> {
        var skippedWithoutAudio = 0
        while (bulletinBuffer.isNotEmpty()) {
            val candidate = bulletinBuffer.removeFirst()
            if (candidate.playableAudioFile() != null) {
                return candidate to skippedWithoutAudio
            }
            skippedWithoutAudio += 1
            candidate.file?.takeIf { !it.exists() || it.length() <= WAV_HEADER_SIZE }?.delete()
        }
        return null to skippedWithoutAudio
    }

    private fun restoreBufferedBulletinToFront(item: PreparedBulletin, reason: String) {
        if (item.playableAudioFile() == null) return
        bulletinBuffer.addFirst(item)
        saveCoreBufferManifest()
        syncBulletinBufferState()
        Log.w(TAG_RADIO_VOICE, "boletim: devolvido para frente do buffer ($reason)")
    }

    private fun clearProtectedBulletinPlaybackFile(file: File?) {
        if (protectedBulletinPlaybackFileName == file?.name) {
            protectedBulletinPlaybackFileName = null
        }
    }

    private fun speakNextNewsBreak() {
        if (speakingNews) {
            cancelEarlyNewsBreakIfPending()
            return
        }
        val player = controller ?: run {
            cancelEarlyNewsBreakIfPending()
            return
        }
        val (prepared, skippedWithoutAudio) = dequeueBufferedBulletinForPlayback()
        val protectedReadyFile = prepared?.playableAudioFile()
        protectedBulletinPlaybackFileName = protectedReadyFile?.name
        // Regrava o manifest agora que o item saiu do bulletinBuffer de vez (vai tocar - "gasto"
        // de verdade) ou depois de pular itens sem WAV na frente da fila. Sem isso um processo
        // morto logo depois desse consumo ainda restauraria boletins ja falados/quebrados.
        if (prepared != null || skippedWithoutAudio > 0) {
            saveCoreBufferManifest()
        }
        if (skippedWithoutAudio > 0) {
            Log.w(
                TAG_RADIO_VOICE,
                "boletim: pulados $skippedWithoutAudio item(ns) sem audio na frente do buffer; procurando WAV pronto",
            )
            syncBulletinBufferState()
            scheduleFallbackAutoFix()
        }
        // Consumiu um item (ou tentou e o buffer estava vazio) - manda reabastecer ja, em
        // paralelo com a fala que vai comecar agora (ver ADR-019: buffer sempre com
        // BULLETIN_BUFFER_TARGET itens prontos, reposto assim que um sai).
        refillBulletinBuffer()

        // Sem alternativa: sem boletim aprovado pronto no buffer (baixado do feed remoto,
        // ver refillBulletinBuffer), a entrada simplesmente e cancelada e a musica segue -
        // sem redator/voz local, sem TTS Android, sem boletim "ao vivo" de emergencia
        // (simplificado 16/09/2026).
        if (prepared == null) {
            Log.w(TAG_RADIO_VOICE, "boletim: nenhum audio pronto no buffer; cancelando entrada")
            clearProtectedBulletinPlaybackFile(protectedReadyFile)
            cancelEarlyNewsBreakIfPending()
            return
        }
        val bulletin = prepared.script
        rememberRecentBulletinStory(bulletin)
        val readyFile = protectedReadyFile?.takeIf { it.exists() && it.length() > WAV_HEADER_SIZE }
        Log.d(
            TAG_RADIO_VOICE,
            "boletim: consumindo do buffer (audio=${readyFile != null}, arquivo=${readyFile?.name}, bytes=${readyFile?.length() ?: 0}, titulo='${bulletin.displayText}', restam ${bulletinBuffer.size})",
        )
        if (readyFile == null) {
            Log.w(TAG_RADIO_VOICE, "boletim: item do buffer sem audio tocavel; cancelando entrada")
            clearProtectedBulletinPlaybackFile(protectedReadyFile)
            cancelEarlyNewsBreakIfPending()
            return
        }

        speakingNews = true
        currentNewsHeadline = bulletin.displayText
        controller?.let { updatePlayerState(it) }
        resumeAfterNews = player.isPlaying
        if (resumeAfterNews) player.pause()
        // Volume pode ter ficado em 0 por causa do fade de checkForEarlyNewsBreak/
        // fadeOutRadioVolume - restaura aqui pra quando a musica (essa ou a proxima, ver
        // newsBreakSkipsAheadOnResume em finishNewsBreak) voltar a tocar depois do boletim.
        // radioVolume() (nao 1f fixo) - se o usuario mutou no mini player, o boletim seguinte
        // nao pode desmutar a radio sozinho.
        player.volume = radioVolume()

        // Sem passagem antes do boletim (pedido do usuario 16/09/2026): o audio baixado do
        // feed remoto ja vem com sua propria transicao embutida (produzida pela central de
        // broadcast) - tocar a passagem do app por cima duplicava a transicao.
        Log.d(TAG_RADIO_VOICE, "boletim: tocando audio do buffer arquivo=${readyFile.name} bytes=${readyFile.length()}")
        playAnnouncementFile(
            readyFile,
            onError = { restoreBufferedBulletinToFront(prepared, "erro no MediaPlayer") },
        ) {
            clearProtectedBulletinPlaybackFile(readyFile)
            finishNewsBreak()
        }
        armAnnouncementWatchdog()
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
        if (!speakingNews || newsBreakEnding) return
        newsBreakEnding = true
        currentNewsHeadline = ""
        // Sem passagem depois do boletim (pedido do usuario 16/09/2026, mesmo motivo do
        // lado de entrada em speakNextNewsBreak) - retoma a musica direto assim que o audio
        // do boletim termina.
        newsBreakEnding = false
        speakingNews = false
        if (resumeAfterNews) {
            resumeAfterNews = false
            // checkForEarlyNewsBreak() pausou a musica ~5s antes do fim dela pra dar lugar
            // ao boletim (ver la) - retomar ela agora so tocaria esses ultimos segundos (ja
            // silenciados pelo fade), entao pula direto pra proxima faixa da fila em vez de
            // dar play na que acabou de "terminar".
            val skipToNextTrack = newsBreakSkipsAheadOnResume
            newsBreakSkipsAheadOnResume = false
            viewModelScope.launch {
                if (skipToNextTrack) controller?.seekToNextMediaItem()
                controller?.play()
            }
        }
        controller?.let { updatePlayerState(it) }
    }

    // Toca o boletim pronto na posicao `index` do buffer direto na tela de Configuracoes, sem
    // precisar entrar numa radio - pedido do usuario (03/09/2026): quer ouvir o resultado assim
    // que fica pronto. So espia (elementAtOrNull), nunca remove do buffer - testar nao pode
    // consumir o boletim que a radio de verdade ainda vai usar. Buffer enche independente de
    // radio tocando ou nao (unificacao 10/09/2026), entao sempre reflete o preparo automatico de
    // verdade. `index` deixa tocar qualquer um dos ate BULLETIN_BUFFER_TARGET itens prontos, nao
    // so o primeiro (pedido do usuario 03/09/2026: "os outros que ficam pronto, nao sei como
    // reproduzir eles") - ver RadioBulletinBufferStatusCard, cada bolinha preenchida vira
    // clicavel pra sua posicao.
    fun playReadyBufferedBulletin(index: Int = 0) {
        if (radioBulletinBufferState.value.isPlayingPreview) return
        val selected = bulletinBuffer.elementAtOrNull(index)
            ?.takeIf { it.playableAudioFile() != null }
            ?: if (index == 0) bulletinBuffer.firstOrNull { it.playableAudioFile() != null } else null
        val mainFile = selected?.playableAudioFile()
        if (mainFile == null) {
            // Achado 03/09/2026: sem isso, tocar numa posicao sem audio (ex.: item que caiu pro
            // roteiro padrao apos falha do redator - fallback nunca tem audio) nao fazia NADA
            // visivel - o botao parecia habilitado (readyCount conta item "com texto pronto", nao
            // "com audio pronto") mas ficava mudo ao tocar.
            radioBulletinBufferState.value = radioBulletinBufferState.value.copy(
                statusMessage = "Esse boletim não tem áudio pronto (o redator local falhou nessa matéria, ou a voz local está desligada).",
                progressPercent = null,
            )
            return
        }
        if (!mainFile.exists()) {
            radioBulletinBufferState.value = radioBulletinBufferState.value.copy(
                statusMessage = "Esse boletim não tem áudio pronto (voz local desligada agora).",
                progressPercent = null,
            )
            return
        }
        radioBulletinBufferState.value = radioBulletinBufferState.value.copy(isPlayingPreview = true)
        previewPlayer?.release()
        previewPlayer = null
        playPreviewFile(mainFile) { finishPreview() }
    }

    private fun finishPreview() {
        radioBulletinBufferState.value = radioBulletinBufferState.value.copy(isPlayingPreview = false)
    }

    private fun playPreviewFile(file: File, onFinished: () -> Unit) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (previewPlayer === it) previewPlayer = null
                    onFinished()
                }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    if (previewPlayer === player) previewPlayer = null
                    onFinished()
                    true
                }
                prepare()
                start()
                previewPlayer = this
            }
        }.onFailure { onFinished() }
    }

    private fun playAnnouncementFile(
        file: java.io.File,
        deleteOnCompletion: Boolean = true,
        onError: () -> Unit = {},
        onFinished: () -> Unit,
    ) {
        announcementPlayer?.release()
        announcementPlayer = null
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                // Sem isso, o boletim ao vivo tocava no volume cheio (default do MediaPlayer)
                // mesmo com o usuario tendo mutado a radio no mini player (ver toggleRadioMute).
                setVolume(radioVolume(), radioVolume())
                setOnCompletionListener {
                    Log.d(TAG_RADIO_VOICE, "announcement file completed bytes=${file.length()}")
                    it.release()
                    if (announcementPlayer === it) announcementPlayer = null
                    if (deleteOnCompletion) file.delete()
                    onFinished()
                }
                setOnErrorListener { player, what, extra ->
                    Log.w(TAG_RADIO_VOICE, "announcement file error what=$what extra=$extra bytes=${file.length()}")
                    player.release()
                    if (announcementPlayer === player) announcementPlayer = null
                    onError()
                    onFinished()
                    true
                }
                prepare()
                start()
                announcementPlayer = this
            }
        }.onFailure {
            Log.e(TAG_RADIO_VOICE, "failed to play announcement file bytes=${file.length()}", it)
            onError()
            onFinished()
        }
    }

    private fun connectToPlaybackService() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connectedController = future.get()
                // Atribui o campo ANTES de restaurar a radio - startRadioNewsMode() le
                // controller?.currentMediaItem internamente, e "controller = X.also { ... }"
                // so atualiza o campo DEPOIS que o also{} termina, entao chamar a restauracao
                // de dentro do also{} leria o controller antigo (null na 1a conexao).
                controller = connectedController
                connectedController.addListener(playerListener)
                updatePlayerState(connectedController)
                restoreRadioSessionIfNeeded(connectedController)
            },
            MoreExecutors.directExecutor(),
        )
    }

    // Restaura o "modo radio" (boletins/buffer) quando o ViewModel e recriado do zero mas o
    // MediaSessionService sobrevive tocando uma radio - pedido do usuario (06/09/2026): "se eu
    // sair do app e abrir outro, a radio se desfaz sozinha, fica tocando as musicas mas fora da
    // radio". Causa raiz: o Android mata o PROCESSO do app em segundo plano pra liberar memoria
    // (app pesado - redator local carrega ate ~2,3GB de modelo), o que destroi este ViewModel e
    // reseta radioNewsEnabled/activeRadioName pro default (false/""). O MediaSessionService (
    // MusicPlaybackService) e um componente Android separado, sobrevive e continua tocando -
    // dai a musica seguir mas o boletim nunca mais falar. O nome da radio ja fica gravado em
    // MediaMetadata.station de cada item (ver LocalSong.toMediaItem) dentro do proprio servico,
    // entao da pra recuperar sem nenhuma persistencia propria nova: se o ViewModel acabou de
    // conectar (nunca esteve em modo radio nesta instancia) e a faixa atual tem station
    // preenchido, e porque uma radio real ja estava tocando antes deste ViewModel existir.
    // startRadioNewsMode() sozinho ja faz exatamente o que precisamos aqui (liga radioNewsEnabled,
    // reconstroi o buffer de boletins) sem mexer na fila/reproducao, que ja esta correta.
    private fun restoreRadioSessionIfNeeded(player: Player) {
        if (activeRadioName.isNotBlank()) return
        val stationName = player.currentMediaItem?.mediaMetadata?.station?.toString()
        if (stationName.isNullOrBlank()) return
        Log.d(TAG_RADIO_VOICE, "radio: restaurando sessao apos recriacao do processo - '$stationName'")
        startRadioNewsMode(stationName)
    }

    // Conectou/retomou uma sessao de Cast (usuario escolheu uma rota Cast em connectToRemoteDevice,
    // que chama mediaRouter.selectRoute - isso e o que dispara isso aqui de volta, de forma
    // assincrona, atraves do SDK do Cast).
    private fun onCastSessionActive(session: CastSession) {
        val remoteMediaClient = session.remoteMediaClient ?: return
        if (startRemoteSession(CastPlaybackBridge(remoteMediaClient))) {
            remoteDeviceState.value = remoteDeviceState.value.copy(
                connectedLabel = session.castDevice?.friendlyName ?: "TV",
            )
        } else {
            runCatching { CastContext.getSharedInstance(getApplication()).sessionManager.endCurrentSession(true) }
        }
    }

    // Sessao de Cast terminou (usuario desconectou ou a TV caiu). So encerra a sessao remota se
    // quem estiver ativo agora for mesmo essa sessao de Cast - se o usuario ja trocou pra DLNA
    // entre o Cast conectar e desconectar, endRemoteSession() ja rodou na hora da troca
    // (startRemoteSession sempre encerra o que tinha antes) e essa notificacao chegando depois
    // e so um eco tardio do SDK do Cast, nao deve derrubar a sessao DLNA que esta ativa agora.
    private fun onCastSessionInactive() {
        if (remoteBridge is CastPlaybackBridge) {
            endRemoteSession()
            remoteDeviceState.value = remoteDeviceState.value.copy(connectedLabel = null)
        }
    }

    // Chamado quando o botao unico de "transmitir pra TV" (HeaderCastButton) abre o seletor -
    // liga a descoberta dos dois protocolos ao mesmo tempo: rotas Cast (MediaRouter, atualiza
    // sozinho via mediaRouterCallback) e um scan SSDP pontual de dispositivos UPnP.
    fun scanForRemoteDevices() {
        if (remoteDeviceState.value.isScanning) return
        mediaRouter.addCallback(castRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        remoteDeviceState.value = remoteDeviceState.value.copy(isScanning = true, devices = currentCastRouteEntries())
        viewModelScope.launch {
            val dlnaDevices = runCatching { SsdpDiscovery.discover(getApplication()) }.getOrDefault(emptyList())
            remoteDeviceState.value = remoteDeviceState.value.copy(
                isScanning = false,
                devices = currentCastRouteEntries() + dlnaDevices.map { RemoteDeviceEntry.Dlna(it) },
            )
        }
    }

    // Seletor fechou - para de gastar bateria/rede escutando rotas Cast novas (o SSDP ja e
    // pontual, termina sozinho).
    fun stopScanningRemoteDevices() {
        mediaRouter.removeCallback(mediaRouterCallback)
    }

    private fun currentCastRouteEntries(): List<RemoteDeviceEntry.Cast> =
        mediaRouter.routes
            .filter { !it.isDefault && it.matchesSelector(castRouteSelector) }
            .map { RemoteDeviceEntry.Cast(routeId = it.id, label = it.name) }

    private fun refreshCastRouteEntries() {
        val dlnaEntries = remoteDeviceState.value.devices.filterIsInstance<RemoteDeviceEntry.Dlna>()
        remoteDeviceState.value = remoteDeviceState.value.copy(devices = currentCastRouteEntries() + dlnaEntries)
    }

    fun connectToRemoteDevice(entry: RemoteDeviceEntry) {
        when (entry) {
            is RemoteDeviceEntry.Cast -> {
                val route = mediaRouter.routes.firstOrNull { it.id == entry.routeId } ?: return
                mediaRouter.selectRoute(route)
            }
            is RemoteDeviceEntry.Dlna -> {
                if (remoteBridge is CastPlaybackBridge) {
                    runCatching {
                        CastContext.getSharedInstance(getApplication()).sessionManager.endCurrentSession(true)
                    }
                }
                Log.d(TAG_REMOTE_PLAYBACK, "Conectando DLNA em ${entry.device.friendlyName} -> ${entry.device.controlUrl}")
                if (startRemoteSession(DlnaPlaybackBridge(entry.device.controlUrl))) {
                    remoteDeviceState.value = remoteDeviceState.value.copy(connectedLabel = entry.device.friendlyName)
                }
            }
        }
    }

    fun disconnectRemote() {
        if (remoteDeviceState.value.connectedLabel == null) return
        if (remoteBridge is CastPlaybackBridge) {
            runCatching { CastContext.getSharedInstance(getApplication()).sessionManager.endCurrentSession(true) }
        } else {
            endRemoteSession()
        }
        remoteDeviceState.value = remoteDeviceState.value.copy(connectedLabel = null)
    }

    // Conectou numa TV (Cast OU DLNA, ver acima): silencia o controller local (continua tocando
    // de verdade, so mudo - ver comentario de remoteHttpServer acima) e sobe o servidor HTTP local
    // que serve os arquivos de musica (content:// do MediaStore) pra TV alcancar via rede Wi-Fi.
    // Sempre encerra qualquer sessao remota anterior primeiro (Cast OU DLNA) - so uma de cada vez.
    private fun startRemoteSession(bridge: RemotePlaybackBridge): Boolean {
        endRemoteSession()
        val context = getApplication<Application>()
        val player = controller
        val mediaItem = player?.currentMediaItem
        val songUri = mediaItem?.localConfiguration?.uri
        if (player == null || mediaItem == null || songUri == null) {
            showToast("Escolha uma musica antes de transmitir pra TV.")
            bridge.release()
            return false
        }
        val wifiAddress = LocalWifiAddress.find(context)
        if (wifiAddress == null) {
            showToast("Conecte o celular numa rede Wi-Fi pra transmitir pra TV.")
            bridge.release()
            return false
        }
        val server = CastMediaHttpServer(context, java.util.UUID.randomUUID().toString())
        if (!runCatching { server.start() }.isSuccess) {
            showToast("Nao consegui iniciar o servidor local pra transmitir pra TV.")
            bridge.release()
            return false
        }
        remoteHttpServer = server
        remoteHttpBaseUrl = "http://${wifiAddress.hostAddress}:${server.listeningPort}"
        remoteBridge = bridge
        remoteMirroredMediaId = null
        remoteMirroredIsPlaying = null
        controller?.volume = 0f
        Log.d(TAG_REMOTE_PLAYBACK, "Sessao remota pronta em $remoteHttpBaseUrl")
        mirrorToRemoteIfNeeded(player)
        return true
    }

    // Sessao remota terminou (Cast desconectou ou DLNA foi desconectado pelo usuario) - desliga o
    // servidor local e devolve o audio pro alto-falante do celular de onde a fila/posicao ja
    // estao (o controller local NUNCA parou de tocar, so estava mudo - ver comentario acima).
    private fun endRemoteSession() {
        remoteBridge?.release()
        remoteBridge = null
        remoteHttpServer?.stop()
        remoteHttpServer = null
        remoteHttpBaseUrl = null
        remoteMirroredMediaId = null
        remoteMirroredIsPlaying = null
        controller?.volume = radioVolume()
    }

    // So espelha (nunca decide sozinho) - controller local continua sendo a unica fonte de
    // verdade. Troca de faixa: registra o arquivo no servidor local e manda a TV (Cast ou DLNA)
    // carregar essa URL. Play/pause: repassa. Ver comentario de remoteHttpServer acima.
    private fun mirrorToRemoteIfNeeded(player: Player) {
        val bridge = remoteBridge ?: return
        val server = remoteHttpServer ?: return
        val baseUrl = remoteHttpBaseUrl ?: return
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId != remoteMirroredMediaId) {
            remoteMirroredMediaId = mediaId
            remoteMirroredIsPlaying = null
            val mediaItem = player.currentMediaItem
            val songUri = mediaItem?.localConfiguration?.uri
            if (mediaItem != null && songUri != null) {
                val token = server.registerSong(songUri)
                val remoteUrl = "$baseUrl${server.pathFor(token)}"
                val artworkUrl = mediaItem.mediaMetadata.artworkUri?.let { artworkUri ->
                    val artworkToken = server.registerArtwork(artworkUri)
                    "$baseUrl${server.artworkPathFor(artworkToken)}"
                }
                Log.d(
                    TAG_REMOTE_PLAYBACK,
                    "Espelhando midia para remoto: mediaId=$mediaId url=$remoteUrl artwork=$artworkUrl isPlaying=${player.isPlaying}",
                )
                bridge.playUrl(
                    url = remoteUrl,
                    mimeType = getApplication<Application>().contentResolver.getType(songUri) ?: "audio/*",
                    title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                    artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                    album = mediaItem.mediaMetadata.albumTitle?.toString().orEmpty(),
                    artworkUrl = artworkUrl,
                    startPositionMs = player.currentPosition.coerceAtLeast(0L),
                    autoplay = player.isPlaying,
                )
                remoteMirroredIsPlaying = player.isPlaying
            }
            return
        }
        if (player.isPlaying != remoteMirroredIsPlaying) {
            remoteMirroredIsPlaying = player.isPlaying
            Log.d(TAG_REMOTE_PLAYBACK, "Espelhando estado remoto: isPlaying=${player.isPlaying}")
            if (player.isPlaying) bridge.resume() else bridge.pause()
        }
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
            artworkSourceUri = player.currentMediaItem?.localConfiguration?.uri,
            playbackSource = playbackSource,
            activeRadioName = activeRadioName,
            currentNewsHeadline = currentNewsHeadline,
            upcomingTracks = cachedUpcomingTracks,
            isPlaying = player.isPlaying,
            isRadioMuted = radioMuted,
            autoplayEnabled = autoplayEnabled,
            hasMedia = player.mediaItemCount > 0,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueIndex = player.currentMediaItemIndex + 1,
            queueSize = player.mediaItemCount,
            sleepCheckPending = sleepCheckPending,
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

    // Pedido do usuario 15/09/2026 (revisado no mesmo dia - o gatilho original era "primeira
    // faixa do album que toca", mas o usuario queria algo mais previsivel: ABRIR a pagina do
    // album). Checa genero e ano SEPARADAMENTE (cada um so busca se estiver faltando) e aplica os
    // dois de uma vez so quando algum precisar - mesma AlbumGenreSuggestionRepository do fluxo
    // manual em Configuracoes > Albuns sem genero, genero(s) sempre do nosso catalogo padronizado
    // (GENRE_TAG_CATALOG via repository.availableGenres, suporta mais de 1 genero junto - ver
    // AlbumGenreSuggestionRepository.suggestMetadata). O usuario sempre pode revisar/corrigir
    // depois.
    //
    // De proposito SEM trava de "so tenta 1 vez pra sempre" (existiu antes, removida 15/09/2026):
    // o gatilho e simplesmente "o album ainda esta sem genero e/ou sem ano" (needsGenre/needsYear
    // abaixo), reavaliado toda vez que a pagina abre - achado ao vivo com o White Pony: uma
    // primeira tentativa que falhou (bug na busca, ja corrigido) ficava marcada como "ja tentei"
    // pra sempre e nunca mais tentava de novo, mesmo o album continuando sem os dados. Sem a
    // trava, reabrir a pagina de um album que realmente nao tem genero/ano em nenhuma fonte so
    // bate na web de novo (barato, 1-2 chamadas) - e ele naturalmente para de tentar sozinho assim
    // que os dois campos forem preenchidos (needsGenre/needsYear viram false).
    fun maybeAutoTagAlbumMetadata(album: LocalAlbum) {
        val needsGenre = album.genre.isBlank()
        val needsYear = album.year <= 0
        if (!needsGenre && !needsYear) return
        viewModelScope.launch {
            val availableGenres = repository.availableGenres(libraryState.value.songs)
            val suggestion = runCatching { genreSuggestionRepository.suggestMetadata(album, availableGenres) }
                .getOrNull() ?: return@launch
            var applied = false
            if (needsGenre && !suggestion.genre.isNullOrBlank()) {
                approveAlbumGenre(album, suggestion.genre)
                applied = true
            }
            if (needsYear && (suggestion.year ?: 0) > 0) {
                approveAlbumYear(album, suggestion.year!!)
                applied = true
            }
            if (applied) markAutoTagPendingWrite(album)
        }
    }

    // Dispara sozinho o pedido de permissao pra gravar as mudancas automaticas (genero/ano) DESSE
    // album de verdade no arquivo - escopado so as faixas desse album, mesmo padrao de
    // requestedTagWriteChanges usado por saveAlbumMetadataEdit/saveArtistMetadataEdits pra edicao
    // manual. A UI (LibraryShell) observa autoTagWriteRequestedVersion e chama
    // requestRecentMetadataEditWrite() sozinha quando ele muda.
    private fun markAutoTagPendingWrite(album: LocalAlbum) {
        val songs = libraryState.value.songs
        val albumSongIds = album.songs.map { it.id }.toSet()
        val scoped = repository.pendingTagChanges(songs)
            .filter { change -> change.songIds.any { it in albumSongIds } }
        if (scoped.isEmpty()) return
        requestedTagWriteChanges = scoped
        autoTagWriteRequestedVersion.value += 1
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

    // getLong() normal crasha (ClassCastException) se a chave foi gravada como Integer em vez de
    // Long - aconteceu de verdade em campo 07/09/2026 com um bug no restore de backup
    // (BackupRepository serializava Long sem marcar o tipo; org.json le de volta como Integer
    // quando o valor cabe num Int, e o codigo de restauracao gravava com putInt por engano,
    // travando o app pra sempre na proxima leitura). Corrigido no BackupRepository, mas um
    // usuario que ja tinha restaurado um backup com o bug antigo ficaria preso num crash-loop
    // sem conseguir nem abrir o app - esse fallback AUTO-REPARA removendo a chave corrompida em
    // vez de travar, sem exigir reinstalar/perder o resto dos dados.
    private fun SharedPreferences.getLongSafe(key: String, default: Long): Long =
        runCatching { getLong(key, default) }.getOrElse {
            edit().remove(key).apply()
            default
        }

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
        runCatching {
            CastContext.getSharedInstance(getApplication()).sessionManager
                .removeSessionManagerListener(castSessionManagerListener, CastSession::class.java)
        }
        endRemoteSession()
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        announcementPlayer?.release()
        previewPlayer?.release()
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
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_PROFILE_BIRTHDAY = "profile_birthday"
        const val KEY_PROFILE_PHOTO_PATH = "profile_photo_path"
        const val PROFILE_PHOTO_FILE_NAME = "profile.jpg"
        const val KEY_RADIO_BULLETIN_MODE = "radio_bulletin_mode"
        const val KEY_RECENT_BULLETIN_STORY_KEYS = "recent_bulletin_story_keys"
        const val DOWNLOAD_PROGRESS_STEP_BYTES = 2L * 1024L * 1024L
        const val DOWNLOAD_PROGRESS_STEP_MS = 700L
        const val CORE_BUFFER_MANIFEST_FILE = "manifest.json"
        // Movidos de LocalRadioVoiceEngine (removida 16/09/2026 junto da sintese de voz local) -
        // CORE_BUFFER_DIR_NAME ainda e o destino dos .wav baixados do feed remoto
        // (BroadcastFeedRepository), WAV_HEADER_SIZE ainda valida esses arquivos.
        const val CORE_BUFFER_DIR_NAME = "radio_bulletins_ready"
        const val WAV_HEADER_SIZE = 44
        // Respiro antes de scheduleFallbackAutoFix() tentar de novo sozinho (ver fixFallbackBulletins)
        // - evita loop apertado batendo no Gemini/redator local sem parar quando a causa da falha
        // (sem internet, chave invalida, os dois writers fora do ar) ainda nao se resolveu; da
        // tempo real pra uma falha transitoria passar antes da proxima tentativa automatica.
        const val FALLBACK_AUTO_FIX_DELAY_MS = 45_000L
        const val MAX_HISTORY_ITEMS = 80
        const val POSITION_SAVE_INTERVAL_MS = 3000L
        const val RADIO_BULLETIN_DEFAULT_INTERVAL = 3

        // Quantos boletins aprovados o app mantem baixados em reserva o tempo todo - ver
        // refillBulletinBuffer()/ADR-019. So importa se o consumo for mais rapido que o feed
        // remoto consegue repor (ex.: songsBetweenBulletins = 1).
        // Subido de 3 pra 5 (pedido do usuario 04/09/2026, junto da diversificacao de feeds em
        // NewsBulletinRepository) - mais folga de boletins prontos pra cobrir a variedade nova
        // de temas sem esbarrar em timeout de preparo. Ver ADR-021.
        // Subido de 5 pra 10 (09/09/2026, pedido do usuario): virou trivial de sustentar depois
        // do boletim ficar 100% radio-agnostico. Buffer UNICO (unificado 10/09/2026, ver
        // comentario em bulletinBuffer) - 10 e o estoque real, nao 10 por "nivel".
        const val BULLETIN_BUFFER_TARGET = 10
        // Pedido do usuario (17/09/2026, ADR-037): reserva as ultimas 3 vagas do buffer so pra
        // boletim "especial" - normal para de ser baixado em BULLETIN_BUFFER_TARGET -
        // BULLETIN_BUFFER_SPECIAL_RESERVED_SLOTS (7) itens, mesmo com vaga livre ate 10. Sem
        // isso, um buffer ja cheio de itens normais (comum - o app mantem ele sempre cheio)
        // so buscaria um especial recem-aprovado depois de esvaziar TUDO ate abrir vaga de
        // verdade, o que pode levar bastante tempo tocando radio. Com reserva, a vaga pra um
        // especial fica pronta assim que o buffer normal encolhe pra 7 (3 musicas/boletins
        // tocados), sem precisar esperar o buffer inteiro esvaziar.
        const val BULLETIN_BUFFER_SPECIAL_RESERVED_SLOTS = 3
        const val RECENT_BULLETIN_STORY_KEY_LIMIT = 80

        // Margem de seguranca da limpeza de .wav orfaos em saveCoreBufferManifest() - so precisa
        // cobrir um download do feed remoto em andamento (REMOTE_FEED_TIMEOUT_MS, 15s) com folga;
        // reduzido de 5min pra 1min em 16/09/2026 junto da remocao da sintese local/Gemini (que
        // podia levar ate 10min no pior caso).
        const val ORPHAN_CLEANUP_GRACE_MS = 60 * 1000L

        // Vinhetas de despedida ao sair de uma radio (pedido do usuario 15/09/2026) - alternam
        // em sequencia a cada uso, ver playExitVinheta()/nextExitVinhetaIndex. Tocam no volume
        // cheio (1.0, igual radio_intro/complemento de genero), nao no volume reduzido das
        // passagens.
        val EXIT_VINHETA_RESOURCES = listOf(R.raw.vinheta_tchauzinho, R.raw.vinheta_ate_mais)
        // Timer "voce ainda esta ai?" (pedido do usuario 15/09/2026) - ver armSleepTimer/
        // triggerSleepCheck.
        const val SLEEP_TIMER_IDLE_MS = 90 * 60 * 1000L
        const val SLEEP_TIMER_RESPONSE_MS = 60 * 1000L
        const val ANNOUNCEMENT_WATCHDOG_TIMEOUT_MS = 90_000L
        // Pedido do usuario (09/09/2026): o boletim pode comecar 5s antes do fim da ultima
        // musica, pra nunca deixar a proxima musica comecar a tocar antes dele (ver
        // checkForEarlyNewsBreak). NEWS_BREAK_FADE_MS e o fade de volume da musica dentro dessa
        // janela ate o boletim assumir - fica com folga de sobra pra nunca cruzar o fim real da
        // faixa mesmo com o jitter do polling de posicao (ver init{} no topo da classe).
        const val NEWS_BREAK_LEAD_MS = 5_000L
        const val NEWS_BREAK_FADE_MS = 2_500L
        // Rede folgada (Wi-Fi ruim, DNS lento) alem do timeout interno de 12s do
        // BroadcastFeedRepository - ver comentario no ponto de chamada em refillBulletinBuffer.
        const val REMOTE_FEED_TIMEOUT_MS = 15_000L
        const val TAG_RADIO_VOICE = "PailerRadioVoice"
        const val TAG_REMOTE_PLAYBACK = "PailerRemote"

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
            "jazz" to R.raw.vinheta_jazz,
            "anos2000" to R.raw.vinheta_anos_2000,
        )
    }
}
