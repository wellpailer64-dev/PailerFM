package com.pailer.localtune.player

import android.app.PendingIntent
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.ResultReceiver
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
import com.pailer.localtune.data.AppFolderRepository
import com.pailer.localtune.data.ArtistNewsCard
import com.pailer.localtune.data.ArtistNewsRepository
import com.pailer.localtune.data.BackupRepository
import com.pailer.localtune.data.BackupScheduler
import com.pailer.localtune.data.DuplicateArtistGroup
import com.pailer.localtune.data.LocalAlbum
import com.pailer.localtune.data.LocalArtist
import com.pailer.localtune.data.LocalRadio
import com.pailer.localtune.data.LocalSong
import com.pailer.localtune.data.MusicLibraryRepository
import com.pailer.localtune.data.ArtworkCandidate
import com.pailer.localtune.data.PendingTagChange
import com.pailer.localtune.data.RadioLastPlayedTrack
import com.pailer.localtune.data.RadioBulletinMode
import com.pailer.localtune.data.RadioBulletinRepository
import com.pailer.localtune.data.RadioBulletinSettings
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioScriptLine
import com.pailer.localtune.data.RadioScriptSource
import com.pailer.localtune.data.RadioSpeaker
import com.pailer.localtune.data.RadioVoicePackageRepository
import com.pailer.localtune.data.RadioWriterPackageRepository
import com.pailer.localtune.data.withLastPlayedIntro
import com.pailer.localtune.data.withPhilosophicalCloser
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

// Pasta oficial do Pailer FM (ver AppFolderRepository) - onde o app organiza Backup, Logs,
// Redator Local e Pacote de Vozes. folderName nulo = usuario ainda nao escolheu nenhuma.
data class AppFolderUiState(
    val folderName: String? = null,
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
    val localWriterInstalled: Boolean = false,
    val localWriterImporting: Boolean = false,
    val localWriterName: String = "Redator local",
    val localWriterDetail: String = "Pacote de LLM ainda nao instalado",
    val localWriterMessage: String? = null,
    // true quando ha uma chave de API do Gemini salva localmente (GeminiWriterSettings) -
    // pedido do usuario (03/09/2026): escrever o boletim via Gemini (nuvem, rapido) em vez do
    // redator local (Qwen3 4B no aparelho, ver ADR-020), so a sintese de voz continua local.
    val geminiConfigured: Boolean = false,
    // true quando ha uma chave de API do OpenRouter salva localmente (OpenRouterWriterSettings) -
    // pedido do usuario (05/09/2026): 2a opcao de nuvem, so tentada se o Gemini falhar (ver
    // RadioBulletinRepository.enhanceScript), pra cobrir pico de demanda momentaneo de um dos
    // dois sem cair direto pro redator local.
    val openRouterConfigured: Boolean = false,
)

// Estado do buffer de boletins prontos (bulletinBuffer/refillBulletinBuffer, ver ADR-019),
// exposto pra UI acompanhar o preparo em segundo plano em tempo real - pedido do usuario
// (03/09/2026): quer ver quantos boletins ja estao prontos, quantos estao sendo escritos agora,
// alem de poder pausar/resetar o preparo.
data class RadioBulletinBufferUiState(
    val readyCount: Int = 0,
    val targetCount: Int = 3,
    val isPreparing: Boolean = false,
    val isPaused: Boolean = false,
    val statusMessage: String? = null,
    // Percentual real: na escrita (redator local Qwen3) vem do callback nativo por token
    // (LlamaProgressListener, tokens gerados/teto); na sintese de voz vem do
    // RadioVoiceSynthesisService (index/total de fala). Nunca estimado (pedido do usuario
    // 03/09/2026 foi "acompanhamento em porcentagem", nao "estimativa").
    val progressPercent: Int? = null,
    // Pedido do usuario (03/09/2026): botao de reproduzir o primeiro boletim pronto do buffer,
    // sem precisar entrar numa radio pra testar - ver playReadyBufferedBulletin().
    val isPlayingPreview: Boolean = false,
    // Nivel 1 (coreBuffer/prewarmCoreBuffer) tem pelo menos um item com audio do miolo pronto -
    // usado como fallback do botao de reproduzir quando o Nivel 2 (bulletinBuffer, que exige
    // radio tocando) ainda esta vazio (achado 03/09/2026: sem isso o botao nunca acendia so de
    // abrir o app, contrariando a promessa original de "sem precisar entrar numa radio").
    val hasCorePreview: Boolean = false,
    // true so quando existe audio de verdade pra tocar (nao so texto pronto) - readyCount conta
    // qualquer item com SCRIPT pronto, inclusive roteiro padrao de fallback (que nunca tem
    // audio, ver prewarmCoreBuffer). Sem esse campo separado o botao ficava habilitado mas mudo
    // ao tocar num item sem audio (achado 03/09/2026, usuario relatou "reproduzir previa nao
    // esta funcionando").
    val hasPlayableAudio: Boolean = false,
    // Posicoes (dentre as bolinhas preenchidas, 0..readyCount-1) cujo roteiro caiu no fallback
    // deterministico (RadioScriptSource.Fallback - Gemini E redator local falharam os dois, ou
    // nenhum dos dois esta disponivel no momento) - pintadas de amarelo na UI em vez da cor
    // normal, pra identificar de relance quando o boletim que vai tocar e o roteiro
    // generico/pobre em vez do bate-bola de verdade (pedido do usuario 05/09/2026). Ver
    // fixFallbackBulletins()/scheduleFallbackAutoFix().
    val fallbackSlots: Set<Int> = emptySet(),
    val hasFallback: Boolean = false,
)

data class RadioVoiceUiState(
    val isInstalled: Boolean = false,
    val isImporting: Boolean = false,
    val isTesting: Boolean = false,
    val isTestingAndroidVoice: Boolean = false,
    val isEnabled: Boolean = false,
    val packageName: String = "Voz local",
    val femaleSpeaker: String = "",
    val maleSpeaker: String = "",
    val detail: String = "Nenhum pacote de voz instalado",
    val message: String? = null,
    // Existe um audio de teste (voz ou boletim) ainda no disco pra tocar de novo sem gerar
    // outra vez - ver playTestAudio()/replayLastTestAudio().
    val canReplayTest: Boolean = false,
)

private data class LocalVoiceSynthesisResult(
    val file: File?,
    val detail: String,
    val elapsedMs: Long,
)

// Um item do buffer de boletins (ver bulletinBuffer/refillBulletinBuffer). `file` fica nulo
// quando a voz local estava desativada ou a sintese falhou/estourou o timeout - nesse caso
// speakNextNewsBreak cai pro TTS do Android usando script.spokenText.
// `sourceCore` (06/09/2026, pedido do usuario): guarda o nucleo ORIGINAL (Nivel 1, agnostico de
// radio) de onde esse item veio, se veio de um - null quando o item foi gerado direto do zero
// (coreBuffer estava vazio nessa hora). So existe pra clearBulletinBuffer() poder DEVOLVER o
// nucleo pro coreBuffer em vez de descartar, quando o boletim nunca chegou a tocar (usuario saiu
// da radio ou trocou antes) - o boletim so e "gasto" de verdade quando toca (removido via
// speakNextNewsBreak), nunca so por sair da tela/trocar de radio.
private data class PreparedBulletin(
    val script: RadioScript,
    val file: File?,
    val sourceCore: PreparedNewsCore? = null,
)

// Um "nucleo" pre-aquecido (ver coreBuffer/prewarmCoreBuffer, pedido do usuario 03/09/2026:
// buffer deve encher mesmo sem radio nenhuma tocando, sem prender o trabalho pesado a uma
// radio especifica). `script` tem 6 falas mas SEM faixa/radio reais (hasTrackContext=false,
// ver RadioBulletin.kt) - so a analise da noticia (falas 2-5) e reaproveitavel entre radios.
// `coreFile` e o WAV "seco" (falas 2-5, sem musica de fundo) que refillBulletinBuffer() usa em
// spliceEdges() pra colar as pontas (fala 1/6) com contexto real, sem re-sintetizar tudo.
// `introFile` e SO pra previa manual (playReadyBufferedBulletin) - a fala 1 sozinha, sintetizada
// com o texto generico que o LLM ja escreve quando nao ha faixa real (ver buildPrompt: "faixa
// desconhecida - nao cite nome nenhum, so emenda direto pra noticia"). NUNCA usado por
// spliceEdges/refillBulletinBuffer (Nivel 2 sempre gera SUA PROPRIA fala 1 com faixa real e
// audio novo) - achado 03/09/2026: sem isso a previa comecava direto na fala do Nico, sem
// nenhum contexto de qual noticia estava sendo discutida ("fica sem pe nem cabeca").
private data class PreparedNewsCore(
    val script: RadioScript,
    val coreFile: File?,
    val introFile: File? = null,
)

class LocalTuneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicLibraryRepository(application)
    private val backupRepository = BackupRepository(application)
    private val appFolderRepository = AppFolderRepository(application)
    private val bulletinRepository = RadioBulletinRepository(application)
    private val artistNewsRepository = ArtistNewsRepository(application)
    private val voicePackageRepository = RadioVoicePackageRepository(application)
    private val writerPackageRepository = RadioWriterPackageRepository(application)
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
    private var bulletinReloadInFlight = false
    private var nextBulletinIndex = 0
    private var speakingNews = false
    private var currentNewsHeadline = ""
    private var resumeAfterNews = false
    private var currentRadioTrack: RadioLastPlayedTrack? = null
    private var pendingVinheta = false
    // Alterna entre as duas passagens curtas (musica > passagem > boletim > passagem > musica) -
    // ver playPassagem(). Incrementa a cada uso, nao reseta entre boletins.
    private var nextPassagemIndex = 0
    // Guarda de reentrancia de finishNewsBreak(): agora ela dispara a passagem final e so
    // zera speakingNews no fim disso (pra manter o watchdog cobrindo a passagem), entao o
    // guard antigo (`if (!speakingNews) return`) sozinho nao bastava mais - uma segunda
    // chamada durante a passagem tocaria a passagem duas vezes.
    private var newsBreakEnding = false
    private var announcementPlayer: MediaPlayer? = null
    private var lastTestAudioFile: File? = null
    // Player dedicado pro "Reproduzir" do card de buffer (playReadyBufferedBulletin) - separado
    // de announcementPlayer/lastTestAudioFile de proposito: aquele mecanismo APAGA o arquivo
    // anterior a cada novo teste, e o arquivo de um boletim do buffer ainda pertence ao
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
    // Buffer de boletins prontos (roteiro ja decorado + WAV ja sintetizado, se a voz local
    // estiver ativa) - ver refillBulletinBuffer()/ADR-019. Mantido em BULLETIN_BUFFER_TARGET
    // itens; consumido em FIFO por speakNextNewsBreak(), reabastecido logo em seguida.
    private val bulletinBuffer = ArrayDeque<PreparedBulletin>()
    // Buffer do "nucleo" (Nivel 1, ver PreparedNewsCore/prewarmCoreBuffer) - roda independente
    // de radioNewsEnabled, sobrevive a troca de radio (so bulletinBuffer, especifico da radio
    // ativa, e limpo em startRadioNewsMode). refillBulletinBuffer() consome daqui primeiro.
    private val coreBuffer = ArrayDeque<PreparedNewsCore>()
    private var corePrepJob: Job? = null
    // Nivel 1 (prewarmCoreBuffer) e Nivel 2 (refillBulletinBuffer) agora rodam em coroutines
    // INDEPENDENTES e podem se sobrepor (radio tocando enquanto o nucleo de outra materia ainda
    // esta sendo preparado) - sem isso, dois generate()/synthesize() concorrentes no mesmo
    // processo arriscam o mesmo tipo de crash que ja preocupou este projeto (redator local sem
    // isolamento de processo, ver ADR-002; "nunca dois engines de voz carregados ao mesmo
    // tempo", ver ADR-003). Os dois mutexes serializam cada motor nativo entre os dois niveis
    // sem bloquear um motor por causa do outro.
    private val llmGenerationMutex = Mutex()
    private val voiceSynthesisMutex = Mutex()
    // Job da correcao automatica de fallback (ver scheduleFallbackAutoFix/fixFallbackBulletins) -
    // guarda contra agendar mais de uma correcao sobreposta; roda com um pequeno delay pra dar
    // tempo de uma falha transitoria (rede, Gemini fora do ar) se resolver antes de tentar de
    // novo, em vez de bater na mesma falha em loop apertado.
    private var fallbackAutoFixJob: Job? = null
    // Pausa manual do preparo em segundo plano (pauseBulletinPreparation/resumeBulletinPreparation,
    // pedido do usuario 03/09/2026) - refillBulletinBuffer()/prewarmCoreBuffer() viram no-op
    // enquanto isso for true, em TODOS os pontos de chamada (troca de faixa, fim de boletim,
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

    var albumArtworkState = androidx.compose.runtime.mutableStateOf(AlbumArtworkUiState())
        private set

    var artistPhotoState = androidx.compose.runtime.mutableStateOf(ArtistPhotoUiState())
        private set

    var backupState = androidx.compose.runtime.mutableStateOf(
        BackupUiState(
            hasDestination = backupRepository.hasDestination(),
            usingOfficialFolder = backupRepository.usingOfficialFolder(),
            destinationDescription = backupRepository.destinationDescription(),
            lastBackupAtMillis = backupRepository.lastBackupAt(),
        ),
    )
        private set

    var appFolderState = androidx.compose.runtime.mutableStateOf(
        AppFolderUiState(folderName = appFolderRepository.folderDisplayName()),
    )
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

    var radioVoiceState = androidx.compose.runtime.mutableStateOf(loadRadioVoiceUiState())
        private set

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlayerState(player)
            if (player.isPlaying) recordCurrentSong(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val completedTrack = currentRadioTrack
            currentRadioTrack = mediaItem?.toRadioLastPlayedTrack()
            if (radioNewsEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                completedRadioSongs += 1
                val interval = radioBulletinState.value.settings.songsBetweenBulletins.coerceAtLeast(1)
                if (completedRadioSongs % interval == 0) {
                    speakNextNewsBreak(completedTrack)
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
        viewModelScope.launch {
            while (true) {
                controller?.let { updatePlayerState(it) }
                delay(if ((controller?.mediaItemCount ?: 0) > 0) 1_500 else 3_000)
            }
        }
        // Pre-aquecimento do nucleo do boletim comeca na abertura do app, SEM esperar o usuario
        // tocar uma radio (pedido do usuario 03/09/2026: "todos os dias, ele tem que fazer esse
        // buffer mesmo sem eu tocar na radio"). Ver prewarmCoreBuffer/PreparedNewsCore.
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
            prewarmCoreBuffer()
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

    fun setRadioBulletinPreferLocalWriter(preferLocalWriter: Boolean) {
        updateRadioBulletinSettings(radioBulletinState.value.settings.copy(preferLocalWriter = preferLocalWriter))
    }

    // Chave geral Gemini+OpenRouter (06/09/2026, pedido do usuario) - ver
    // RadioBulletinSettings.cloudWriterEnabled/RadioBulletinRepository.enhanceScript. As chaves
    // de API continuam salvas, so param de ser tentadas enquanto isso estiver desligado.
    fun setRadioBulletinCloudWriterEnabled(enabled: Boolean) {
        updateRadioBulletinSettings(radioBulletinState.value.settings.copy(cloudWriterEnabled = enabled))
    }

    fun importRadioWriterPackage(uri: Uri) {
        radioBulletinState.value = radioBulletinState.value.copy(
            localWriterImporting = true,
            localWriterMessage = "Importando redator local...",
        )
        viewModelScope.launch {
            runCatching { writerPackageRepository.importPackage(uri) }
                .onSuccess {
                    radioBulletinState.value = loadRadioBulletinUiState().copy(
                        localWriterMessage = "Redator local importado. Ele será usado no próximo boletim preparado.",
                    )
                    showToast("Redator local importado.")
                }
                .onFailure { error ->
                    val message = error.message ?: "Não consegui importar o redator local."
                    radioBulletinState.value = loadRadioBulletinUiState().copy(localWriterMessage = message)
                    showToast(message)
                }
        }
    }

    fun clearRadioWriterPackage() {
        writerPackageRepository.clearPackage()
        radioBulletinState.value = loadRadioBulletinUiState().copy(localWriterMessage = "Redator local removido.")
        showToast("Redator local removido.")
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
        // Pedido do usuario (02/09/2026): virar a chavinha no meio de uma radio ja tocando nao
        // mudava nada nos boletins que ja estavam prontos no buffer (preparados com o valor
        // ANTIGO de isEnabled - refillBulletinBuffer() so reconsulta a chavinha quando prepara
        // um item novo). Descarta o que tinha e prepara de novo do zero com o valor atual, pra
        // nao ter que esperar os boletins antigos (BULLETIN_BUFFER_TARGET) acabarem antes da voz
        // nova (ou a volta pro Android) valer de verdade.
        clearBulletinBuffer()
        refillBulletinBuffer()
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
                    source = "Pailer FM",
                ),
                source = RadioScriptSource.Fallback,
                lines = listOf(
                    RadioScriptLine(
                        speaker = RadioSpeaker.Female,
                        text = "Aqui é a Fran testando a voz local do Pailer FM.",
                    ),
                ),
            )
            val result = requestLocalVoiceSynthesis(script, LOCAL_VOICE_TEST_TIMEOUT_MS)
            if (result?.file != null) {
                radioPrefs.edit().putBoolean(KEY_RADIO_VOICE_ENABLED, true).apply()
                radioVoiceState.value = loadRadioVoiceUiState().copy(
                    isTesting = false,
                    message = "Teste OK em ${"%.1f".format(result.elapsedMs / 1000.0)}s. Voz local ativada.",
                    canReplayTest = true,
                )
                playTestAudio(result.file)
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

    fun testAndroidVoiceBulletin() {
        radioVoiceState.value = radioVoiceState.value.copy(
            isTestingAndroidVoice = true,
            message = "Testando voz do Android (bate-bola)...",
        )
        setupTextToSpeech(getApplication())
        viewModelScope.launch {
            var attempts = 0
            while (!ttsReady && attempts < TTS_READY_WAIT_STEPS) {
                attempts += 1
                delay(TTS_READY_WAIT_INTERVAL_MS)
            }
            val tts = textToSpeech
            if (!ttsReady || tts == null) {
                radioVoiceState.value = radioVoiceState.value.copy(
                    isTestingAndroidVoice = false,
                    message = "Voz do Android nao iniciou.",
                )
                return@launch
            }
            val offlineVoices = tts.voices.orEmpty()
                .filter { it.locale.language == "pt" && !it.isNetworkConnectionRequired }
                .sortedBy { it.name }
            Log.d(TAG_RADIO_VOICE, "android tts voices pt (offline)=${offlineVoices.map { it.name }}")
            val femaleVoice = offlineVoices.getOrNull(0) ?: tts.voice
            val maleVoice = offlineVoices.getOrNull(1) ?: tts.voice

            val lines = listOf(
                RadioSpeaker.Female to "Notícia rápida: cientistas brasileiros desenvolveram uma técnica de reciclagem " +
                    "de plástico usando bactérias marinhas.",
                RadioSpeaker.Male to "O estudo foi publicado essa semana e promete reduzir o descarte de garrafas " +
                    "PET em até setenta por cento nos próximos anos.",
                RadioSpeaker.Female to "Fica a dica, e agora voltamos para a nossa programação normal.",
            )
            val startedAt = System.currentTimeMillis()
            val ok = speakAndroidDialogue(tts, lines, femaleVoice, maleVoice)
            val elapsed = System.currentTimeMillis() - startedAt
            radioVoiceState.value = radioVoiceState.value.copy(
                isTestingAndroidVoice = false,
                message = if (ok) {
                    "Voz do Android OK em ${"%.1f".format(elapsed / 1000.0)}s (2 locutores, " +
                        "${if (offlineVoices.size >= 2) "vozes distintas" else "mesma voz, tom diferente"})."
                } else {
                    "Voz do Android nao respondeu em ${ANDROID_VOICE_TEST_TIMEOUT_MS / 1000}s."
                },
            )
        }
    }

    private fun productionUtteranceListener(): UtteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            finishNewsBreak()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            finishNewsBreak()
        }
    }

    private suspend fun speakAndroidDialogue(
        tts: TextToSpeech,
        lines: List<Pair<RadioSpeaker, String>>,
        femaleVoice: Voice?,
        maleVoice: Voice?,
    ): Boolean = withTimeoutOrNull(ANDROID_VOICE_TEST_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            var index = 0
            fun speakNext() {
                if (index >= lines.size) {
                    tts.setOnUtteranceProgressListener(productionUtteranceListener())
                    if (continuation.isActive) continuation.resume(true)
                    return
                }
                val (speaker, text) = lines[index]
                val voice = if (speaker == RadioSpeaker.Female) femaleVoice else maleVoice
                voice?.let { runCatching { tts.voice = it } }
                tts.setPitch(if (speaker == RadioSpeaker.Female) 1.05f else 0.82f)
                tts.setSpeechRate(0.98f)
                val utteranceId = "android_dialogue_${index++}"
                val accepted = runCatching {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                }.getOrDefault(TextToSpeech.ERROR)
                if (accepted != TextToSpeech.SUCCESS) {
                    tts.setOnUtteranceProgressListener(productionUtteranceListener())
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = speakNext()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    tts.setOnUtteranceProgressListener(productionUtteranceListener())
                    if (continuation.isActive) continuation.resume(false)
                }
            })
            continuation.invokeOnCancellation {
                runCatching { tts.stop() }
                tts.setOnUtteranceProgressListener(productionUtteranceListener())
            }
            speakNext()
        }
    } ?: false

    private fun updateRadioBulletinSettings(settings: RadioBulletinSettings) {
        radioPrefs.edit()
            .putString(KEY_RADIO_BULLETIN_MODE, settings.mode.name)
            .putBoolean(KEY_RADIO_BULLETIN_LOCAL_WRITER, settings.preferLocalWriter)
            .putBoolean(KEY_RADIO_BULLETIN_CLOUD_WRITER, settings.cloudWriterEnabled)
            .apply()
        radioBulletinState.value = loadRadioBulletinUiState()
    }

    private fun loadRadioBulletinUiState(): RadioBulletinUiState {
        // Modo nao e mais escolha do usuario - sempre "conversa dos locutores" (Dialogue), a
        // UI de selecao foi removida. Ignora qualquer valor antigo salvo em
        // KEY_RADIO_BULLETIN_MODE (ex.: Off/Headlines de uma instalacao anterior).
        // Duracao do boletim tambem nao e mais preferencia do usuario - cada materia escolhe
        // sozinha (ver RadioBulletinRepository.pickDuration) de acordo com o quanto de
        // conteudo real tem pra render.
        val settings = RadioBulletinSettings(
            mode = RadioBulletinMode.Dialogue,
            songsBetweenBulletins = RADIO_BULLETIN_DEFAULT_INTERVAL,
            preferLocalWriter = radioPrefs.getBoolean(KEY_RADIO_BULLETIN_LOCAL_WRITER, true),
            cloudWriterEnabled = radioPrefs.getBoolean(KEY_RADIO_BULLETIN_CLOUD_WRITER, true),
        )
        val status = bulletinRepository.localWriterStatus()
        return RadioBulletinUiState(
            settings = settings,
            localWriterInstalled = status.isInstalled,
            localWriterName = status.modelName,
            localWriterDetail = status.detail,
            geminiConfigured = bulletinRepository.geminiSettings().apiKey() != null,
            openRouterConfigured = bulletinRepository.openRouterSettings().apiKey() != null,
        )
    }

    // Salva/remove a chave de API do Gemini (GeminiWriterSettings, fica so no SharedPreferences
    // do aparelho) e atualiza a UI - pedido do usuario (03/09/2026): campo direto nas
    // Configuracoes, a chave nunca passa por fora do proprio celular.
    fun saveGeminiApiKey(key: String) {
        if (key.isBlank()) return
        bulletinRepository.geminiSettings().setApiKey(key)
        radioBulletinState.value = radioBulletinState.value.copy(geminiConfigured = true)
        showToast("Chave do Gemini salva.")
    }

    fun clearGeminiApiKey() {
        bulletinRepository.geminiSettings().clearApiKey()
        radioBulletinState.value = radioBulletinState.value.copy(geminiConfigured = false)
        showToast("Chave do Gemini removida.")
    }

    // Mesma logica do Gemini acima (OpenRouterWriterSettings, mesmo SharedPreferences local) -
    // pedido do usuario (05/09/2026): 2a chave de nuvem, so tentada se o Gemini falhar.
    fun saveOpenRouterApiKey(key: String) {
        if (key.isBlank()) return
        bulletinRepository.openRouterSettings().setApiKey(key)
        radioBulletinState.value = radioBulletinState.value.copy(openRouterConfigured = true)
        showToast("Chave do OpenRouter salva.")
    }

    fun clearOpenRouterApiKey() {
        bulletinRepository.openRouterSettings().clearApiKey()
        radioBulletinState.value = radioBulletinState.value.copy(openRouterConfigured = false)
        showToast("Chave do OpenRouter removida.")
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
            if (ok) refreshLibrary()
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
        currentRadioTrack = songs.getOrNull(startIndex)?.let { RadioLastPlayedTrack(it.title, it.artist) }
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

    // "Sair da radio" - ao contrario de so fechar a tela (que deixa a radio tocando em
    // segundo plano, ver openActiveRadio em LocalTuneApp.kt), isso para a reproducao de
    // verdade e esvazia a fila: mediaItemCount some, hasMedia fica false, mini player some.
    fun stopRadio() {
        stopRadioNewsMode()
        playbackSource = ""
        controller?.apply {
            stop()
            clearMediaItems()
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
            setOnUtteranceProgressListener(productionUtteranceListener())
        }
    }

    private fun startRadioNewsMode(radioName: String) {
        setupTextToSpeech(getApplication())
        radioNewsEnabled = true
        completedRadioSongs = 0
        // NAO limpa newsBulletins/coreBuffer (Nivel 1, ver PreparedNewsCore) nem reseta
        // nextBulletinIndex - o pre-aquecimento (prewarmCoreBuffer, roda desde a abertura do
        // app) e radio-agnostico de proposito (pedido do usuario 03/09/2026: "posso entrar em
        // qualquer radio e ainda assim o boletim funcionar"), entao o trabalho pesado ja feito
        // (RSS + redator local) sobrevive a troca de radio. So bulletinBuffer (Nivel 2, com
        // audio especifico dessa rádio - nome dela entra na fala 6) e limpo abaixo.
        clearBulletinBuffer()
        speakingNews = false
        newsBreakEnding = false
        currentNewsHeadline = ""
        resumeAfterNews = false
        currentRadioTrack = controller?.currentMediaItem?.toRadioLastPlayedTrack()
        pendingVinheta = false
        activeRadioName = radioName
        playbackSource = "Rádio $radioName"

        viewModelScope.launch {
            ensureNewsBulletinsLoaded()
            // Carga inicial terminou (ou ja tinha sido feita pelo pre-aquecimento) - comeca a
            // encher o buffer de boletins prontos (3 por padrao, ver ADR-019) em segundo plano,
            // sem esperar a primeira musica acabar. refillBulletinBuffer() reaproveita o que o
            // coreBuffer ja tiver pronto.
            refillBulletinBuffer()
        }
    }

    // Carrega newsBulletins (historias RSS convertidas em roteiro base) se ainda nao tiver -
    // usado tanto pelo pre-aquecimento (prewarmCoreBuffer, sem radio nenhuma) quanto por
    // startRadioNewsMode (reaproveita se o pre-aquecimento ja carregou). radioName so entra no
    // contexto por compatibilidade de assinatura - loadScripts()/buildDialogueLines() no modo
    // Dialogue (unico usado hoje) nunca colocam nome de radio na fala em si.
    private suspend fun ensureNewsBulletinsLoaded(radioName: String = "") {
        if (newsBulletins.isNotEmpty()) return
        val settings = radioBulletinState.value.settings
        newsBulletins = runCatching {
            bulletinRepository.loadScripts(settings, radioName)
        }.getOrDefault(emptyList())
    }

    private fun reloadNewsBulletinsIfNeeded() {
        if (bulletinReloadInFlight) return
        bulletinReloadInFlight = true
        val settings = radioBulletinState.value.settings
        val radioName = activeRadioName
        viewModelScope.launch {
            val reloaded = runCatching {
                bulletinRepository.loadScripts(settings, radioName)
            }.getOrDefault(emptyList())
            if (reloaded.isNotEmpty()) {
                newsBulletins = reloaded
                Log.d(TAG_RADIO_VOICE, "boletim: recarga recuperou ${reloaded.size} historias")
                prewarmCoreBuffer()
                if (radioNewsEnabled) refillBulletinBuffer()
            }
            bulletinReloadInFlight = false
        }
    }

    // Dispara reload de RSS sempre que o round-robin (nextBulletinIndex) completa uma volta
    // inteira pelas newsBulletins - chamado logo apos incrementar nextBulletinIndex nos 3 pontos
    // que consomem uma materia base (refillBulletinBuffer, prewarmCoreBuffer, boletim ao vivo de
    // emergencia). Sem isso, newsBulletins so carregava UMA vez por sessao (ver
    // ensureNewsBulletinsLoaded) e a mesma lista de ~16 materias ficava se repetindo palavra por
    // palavra indefinidamente enquanto a radio tocasse por horas - pedido do usuario
    // (05/09/2026): noticia do Sirius (ciencia aberta) repetindo varias vezes na mesma sessao.
    private fun reloadNewsBulletinsIfCycleComplete() {
        if (newsBulletins.isNotEmpty() && nextBulletinIndex % newsBulletins.size == 0) {
            Log.d(TAG_RADIO_VOICE, "boletim: completou uma volta pelas ${newsBulletins.size} materias - recarregando RSS")
            reloadNewsBulletinsIfNeeded()
        }
    }

    private fun stopRadioNewsMode() {
        radioNewsEnabled = false
        completedRadioSongs = 0
        // coreBuffer/corePrepJob (Nivel 1) NAO param aqui - continuam pre-aquecendo mesmo sem
        // radio tocando (pedido do usuario 03/09/2026), so bulletinBuffer (Nivel 2) e encerrado.
        clearBulletinBuffer()
        speakingNews = false
        newsBreakEnding = false
        currentNewsHeadline = ""
        resumeAfterNews = false
        currentRadioTrack = null
        pendingVinheta = false
        activeRadioName = ""
        announcementPlayer?.release()
        announcementPlayer = null
        textToSpeech?.stop()
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
        player.setVolume(volume, volume)
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

    // Mantem bulletinBuffer com BULLETIN_BUFFER_TARGET itens prontos (roteiro decorado + WAV
    // sintetizado, se a voz local estiver ativa) - ver ADR-019. Um item de cada vez, nunca dois
    // engines de voz carregados ao mesmo tempo (mesma cautela de TTS.md/ADR-003). Chamada nao
    // bloqueia: se ja tem um preparo rodando (bulletinPrepJob ativo) ou o buffer ja esta cheio,
    // e um no-op - seguro chamar de qualquer lugar (transicao de faixa, fim de boletim, reload
    // de feed).
    private fun refillBulletinBuffer() {
        if (!radioNewsEnabled) return
        if (bulletinPrepPaused) return
        if (bulletinPrepJob?.isActive == true) return
        if (bulletinBuffer.size >= BULLETIN_BUFFER_TARGET) return
        if (coreBuffer.isEmpty() && newsBulletins.isEmpty()) {
            reloadNewsBulletinsIfNeeded()
            return
        }
        val radioName = activeRadioName
        // So puxa 1 nucleo por chamada, nao ate encher o BULLETIN_BUFFER_TARGET de uma vez -
        // pedido do usuario (06/09/2026): entrar numa radio (ou trocar de radio) puxava ate 5
        // nucleos do buffer agnostico (coreBuffer) de uma so vez, encaixando faixa/nome da radio
        // atual - se o usuario saisse ou trocasse de radio antes do 1o boletim tocar, esses itens
        // eram descartados (clearBulletinBuffer) E cada um ja tinha disparado prewarmCoreBuffer()
        // pra repor o nucleo consumido (trabalho caro de verdade: redator local/nuvem), nao so o
        // encaixe barato. Agora cada chamada preenche so 1 vaga; o resto do buffer volta a
        // encher sozinho conforme boletins realmente tocam e abrem vaga nova (speakNextNewsBreak
        // ja chama refillBulletinBuffer() a cada consumo real).
        val targetForThisCall = (bulletinBuffer.size + 1).coerceAtMost(BULLETIN_BUFFER_TARGET)
        bulletinPrepJob = viewModelScope.launch {
            try {
                while (bulletinBuffer.size < targetForThisCall && radioNewsEnabled &&
                    activeRadioName == radioName && !bulletinPrepPaused
                ) {
                    if (coreBuffer.isEmpty() && newsBulletins.isEmpty()) {
                        reloadNewsBulletinsIfNeeded()
                        break
                    }
                    val bufferPositionBeforeThisItem = bulletinBuffer.size
                    syncBulletinBufferState(
                        isPreparing = true,
                        statusMessage = "Preparando boletim ${bufferPositionBeforeThisItem + 1}/$BULLETIN_BUFFER_TARGET...",
                    )
                    // Catch-all defensivo: nada aqui deveria lancar sem ja logar (enhanceScript
                    // encapsula o redator local em runCatching, requestLocalVoiceSynthesis
                    // encapsula a chamada ao servico de voz), mas se algo inesperado escapar
                    // mesmo assim, melhor logar com stacktrace completo e parar essa rodada de
                    // preparo do que deixar a excecao subir e derrubar o job silenciosamente -
                    // pedido do usuario (03/09/2026): "pra caso algo der erro a gente saiba o
                    // que". CancellationException segue pro finally normalmente (nao e erro).
                    try {
                        val settings = radioBulletinState.value.settings
                        val interval = settings.songsBetweenBulletins.coerceAtLeast(1)

                        // Previsao de qual musica "acaba de tocar" (intro) e qual "vem a seguir"
                        // (fechamento filosofico) quando ESSE item do buffer realmente for ao ar -
                        // calculada andando na fila fixa da sessao pelo numero de musicas que ainda
                        // faltam ate a posicao dele (1 intervalo por item de buffer a frente). So fica
                        // imprecisa se o usuario pular musica manualmente entre o preparo e a hora de
                        // tocar - degrada pra frase generica sem citar artista (ver
                        // buildFranCloser), nunca quebra o boletim (ver ADR-019).
                        val player = controller
                        val songsUntilFire = run {
                            val remainder = completedRadioSongs % interval
                            val toNextBoundary = if (remainder == 0) interval else interval - remainder
                            toNextBoundary + bufferPositionBeforeThisItem * interval
                        }
                        val lastPlayedTrack = player?.let { p ->
                            val idx = p.currentMediaItemIndex + songsUntilFire - 1
                            if (idx in 0 until p.mediaItemCount) p.getMediaItemAt(idx).toRadioLastPlayedTrack() else null
                        }
                        val upcomingTrack = player?.let { p ->
                            val idx = p.currentMediaItemIndex + songsUntilFire
                            if (idx in 0 until p.mediaItemCount) p.getMediaItemAt(idx).toUpcomingTrack() else null
                        }

                        // Nucleo pre-aquecido (Nivel 1, prewarmCoreBuffer) primeiro - so cai pra
                        // gerar do zero (rede de seguranca) se o pre-aquecimento nao alcancou a
                        // tempo. Ver PreparedNewsCore/spliceEdges.
                        val core = coreBuffer.removeFirstOrNull()
                        val script: RadioScript
                        val file: File?
                        if (core != null) {
                            Log.d(TAG_RADIO_VOICE, "boletim: usando nucleo pre-aquecido (restam ${coreBuffer.size} no nucleo)")
                            script = core.script.withLastPlayedIntro(lastPlayedTrack).withPhilosophicalCloser(radioName, upcomingTrack)
                            file = if (radioVoiceState.value.isEnabled) {
                                if (core.coreFile != null && core.script.lines.size == 6) {
                                    syncBulletinBufferState(statusMessage = "Encaixando abertura/fechamento do boletim ${bufferPositionBeforeThisItem + 1}/$BULLETIN_BUFFER_TARGET...")
                                    val spliced = voiceSynthesisMutex.withLock {
                                        requestSpliceSynthesis(
                                            core.coreFile, script.lines.first(), script.lines.last(), BULLETIN_PREP_TIMEOUT_MS,
                                            onProgress = { message -> syncBulletinBufferState(statusMessage = message) },
                                            onProgressPercent = { percent -> syncBulletinBufferState(progressPercent = percent) },
                                        )
                                    }
                                    spliced ?: run {
                                        Log.w(TAG_RADIO_VOICE, "boletim: encaixe falhou; sintetizando boletim inteiro como rede de seguranca")
                                        voiceSynthesisMutex.withLock {
                                            synthesizeLocalVoiceSafely(
                                                script, BULLETIN_PREP_TIMEOUT_MS,
                                                onProgress = { message -> syncBulletinBufferState(statusMessage = message) },
                                                onProgressPercent = { percent -> syncBulletinBufferState(progressPercent = percent) },
                                            )
                                        }
                                    }
                                } else {
                                    syncBulletinBufferState(statusMessage = "Sintetizando voz do boletim ${bufferPositionBeforeThisItem + 1}/$BULLETIN_BUFFER_TARGET...")
                                    voiceSynthesisMutex.withLock {
                                        synthesizeLocalVoiceSafely(
                                            script, BULLETIN_PREP_TIMEOUT_MS,
                                            onProgress = { message -> syncBulletinBufferState(statusMessage = message) },
                                            onProgressPercent = { percent -> syncBulletinBufferState(progressPercent = percent) },
                                        )
                                    }
                                }
                            } else {
                                null
                            }
                            // core.coreFile ja foi lido (splice/synthesize acima) e o item ja saiu
                            // do coreBuffer - regrava o manifest agora, que tambem varre e apaga
                            // esse .wav do nucleo (nao referenciado por nenhum item restante).
                            saveCoreBufferManifest()
                            // Repoe o nucleo consumido, em paralelo (nao espera terminar).
                            prewarmCoreBuffer()
                        } else {
                            val index = nextBulletinIndex % newsBulletins.size
                            val baseScript = newsBulletins[index]
                            nextBulletinIndex += 1
                            reloadNewsBulletinsIfCycleComplete()
                            Log.d(
                                TAG_RADIO_VOICE,
                                "boletim: preparando do zero (nucleo vazio) index=$index posicao=$bufferPositionBeforeThisItem redator=${settings.preferLocalWriter} vozLocal=${radioVoiceState.value.isEnabled}",
                            )
                            val enhanced = llmGenerationMutex.withLock {
                                bulletinRepository.enhanceScript(
                                    baseScript, settings, radioName, lastPlayedTrack, upcomingTrack,
                                    onProgress = { message -> syncBulletinBufferState(statusMessage = message) },
                                    onProgressPercent = { percent ->
                                        syncBulletinBufferState(statusMessage = radioBulletinBufferState.value.statusMessage, progressPercent = percent)
                                    },
                                )
                            }
                            script = enhanced.withLastPlayedIntro(lastPlayedTrack).withPhilosophicalCloser(radioName, upcomingTrack)
                            file = if (radioVoiceState.value.isEnabled) {
                                syncBulletinBufferState(statusMessage = "Sintetizando voz do boletim ${bufferPositionBeforeThisItem + 1}/$BULLETIN_BUFFER_TARGET...")
                                voiceSynthesisMutex.withLock {
                                    synthesizeLocalVoiceSafely(
                                        script, BULLETIN_PREP_TIMEOUT_MS,
                                        onProgress = { message -> syncBulletinBufferState(statusMessage = message) },
                                        onProgressPercent = { percent -> syncBulletinBufferState(progressPercent = percent) },
                                    )
                                }
                            } else {
                                null
                            }
                        }
                        if (!radioNewsEnabled || activeRadioName != radioName) {
                            file?.delete()
                            break
                        }
                        if (file == null) {
                            Log.w(TAG_RADIO_VOICE, "boletim: voz local nao preparou audio; buffer guarda so o roteiro")
                        }
                        bulletinBuffer.addLast(PreparedBulletin(script, file, sourceCore = core))
                        Log.d(TAG_RADIO_VOICE, "boletim: buffer agora com ${bulletinBuffer.size}/$BULLETIN_BUFFER_TARGET (audio=${file != null})")
                        if (script.source == RadioScriptSource.Fallback) scheduleFallbackAutoFix()
                        syncBulletinBufferState(isPreparing = false, statusMessage = null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.e(TAG_RADIO_VOICE, "boletim: erro inesperado preparando item $bufferPositionBeforeThisItem do buffer", e)
                        syncBulletinBufferState(
                            isPreparing = false,
                            statusMessage = "Erro ao preparar boletim: ${e.message ?: e::class.simpleName} (ver logcat, tag $TAG_RADIO_VOICE).",
                        )
                        break
                    }
                }
            } finally {
                // Roda tambem em cancelamento (pauseBulletinPreparation/resetBulletinBuffer) -
                // garante que a UI nunca fique presa mostrando "escrevendo" depois que o job
                // parou. Cancelamento de coroutine e cooperativo: se o preparo estiver travado
                // numa chamada nativa bloqueante (JNI do redator local/voz), o cancel() so
                // encerra o job assim que essa chamada devolver o controle - risco ja conhecido
                // de falta de isolamento de processo do redator local (ver ADR-002).
                syncBulletinBufferState(isPreparing = false, statusMessage = null)
            }
        }
        syncBulletinBufferState(isPreparing = true)
    }

    // Chamada toda vez que a radio troca/reinicia (startRadioNewsMode) - antes descartava
    // (apagava arquivo + esquecia o roteiro) QUALQUER boletim que estivesse no buffer da radio
    // (Nivel 2), mesmo que nenhum tivesse tocado ainda. Pedido do usuario (06/09/2026): "nucleo
    // puxado pra radio so e gasto se ele for reproduzido, caso nao, volta pro buffer" - um
    // boletim so e "gasto" de verdade quando toca (removido em speakNextNewsBreak). Agora, item
    // que veio de um nucleo (sourceCore != null, ver PreparedBulletin) devolve esse nucleo pro
    // coreBuffer (Nivel 1, agnostico de radio) em vez de jogar fora o trabalho de escrita/sintese
    // ja feito - so o .wav "encaixado" com faixa/radio especificos (item.file) e descartado
    // mesmo, porque foi feito pra ESSA radio/faixa e nao serve pra outra. Item sem sourceCore
    // (gerado do zero, sem nucleo disponivel na hora) nao tem pra onde devolver - descartado como
    // antes, caso mais raro (so acontece com o Nivel 1 vazio).
    private fun clearBulletinBuffer() {
        bulletinPrepJob?.cancel()
        bulletinPrepJob = null
        var returnedAnyCore = false
        bulletinBuffer.forEach { item ->
            item.file?.delete()
            val core = item.sourceCore
            if (core != null) {
                coreBuffer.addFirst(core)
                returnedAnyCore = true
            }
        }
        bulletinBuffer.clear()
        if (returnedAnyCore) {
            Log.d(TAG_RADIO_VOICE, "boletim: ${coreBuffer.size} nucleo(s) devolvido(s) ao buffer agnostico (radio trocou antes de tocar)")
            saveCoreBufferManifest()
        }
        syncBulletinBufferState(isPreparing = false, statusMessage = null)
    }

    // Nivel 1: enche coreBuffer com "nucleos" (falas 2-5, radio/faixa-agnosticas, ver
    // PreparedNewsCore) mesmo sem radio nenhuma tocando - pedido do usuario (03/09/2026): "todos
    // os dias, ele tem que fazer esse buffer mesmo sem eu tocar na radio". Chamada nao bloqueia,
    // e um no-op se ja tem um preparo rodando ou o buffer ja esta cheio - seguro chamar de
    // qualquer lugar (init do app, recarga de feed, consumo de um nucleo por
    // refillBulletinBuffer).
    private fun prewarmCoreBuffer() {
        if (bulletinPrepPaused) return
        if (corePrepJob?.isActive == true) return
        if (coreBuffer.size >= BULLETIN_BUFFER_TARGET) return
        corePrepJob = viewModelScope.launch {
            try {
                if (newsBulletins.isEmpty()) ensureNewsBulletinsLoaded()
                while (coreBuffer.size < BULLETIN_BUFFER_TARGET && !bulletinPrepPaused) {
                    if (newsBulletins.isEmpty()) {
                        // RSS falhou ou ainda nao carregou - nada pra preparar agora; desiste
                        // sem erro (reloadNewsBulletinsIfNeeded/prewarmCoreBuffer tentam de novo
                        // no proximo gatilho).
                        break
                    }
                    try {
                        val settings = radioBulletinState.value.settings
                        val index = nextBulletinIndex % newsBulletins.size
                        val baseScript = newsBulletins[index]
                        nextBulletinIndex += 1
                        reloadNewsBulletinsIfCycleComplete()
                        syncCoreBufferStatus("Preparando núcleo do boletim (sem rádio ativa)...")
                        Log.d(TAG_RADIO_VOICE, "nucleo: preparando index=$index redator=${settings.preferLocalWriter}")
                        var usedCloudWriter = false
                        val enhanced = llmGenerationMutex.withLock {
                            bulletinRepository.enhanceScript(
                                baseScript, settings, radioName = "", lastPlayedTrack = null, upcomingTrack = null,
                                onProgress = { message -> syncCoreBufferStatus(message) },
                                onProgressPercent = { percent -> syncCoreBufferStatus(radioBulletinBufferState.value.statusMessage, percent) },
                                onWriterUsed = { usedCloudWriter = it },
                            )
                        }
                        // So cacheia audio do "miolo" quando o roteiro saiu com as 6 falas
                        // esperadas (redator local completo) - com menos falas (fallback
                        // deterministico ou LLM que nao emplacou) nao ha separacao clara entre
                        // "pontas" e "nucleo" pra encaixar depois, refillBulletinBuffer sintetiza
                        // esse item inteiro na hora, igual sempre foi.
                        val coreFile = if (enhanced.lines.size == 6 && radioVoiceState.value.isEnabled) {
                            syncCoreBufferStatus("Sintetizando núcleo do boletim...")
                            voiceSynthesisMutex.withLock {
                                requestCoreSynthesis(
                                    enhanced.lines.subList(1, 5), BULLETIN_PREP_TIMEOUT_MS,
                                    onProgress = { message -> syncCoreBufferStatus(message) },
                                    onProgressPercent = { percent -> syncCoreBufferStatus(radioBulletinBufferState.value.statusMessage, percent) },
                                )
                            }
                        } else {
                            null
                        }
                        // So pra previa manual (introFile, ver PreparedNewsCore) - fala 1 sozinha,
                        // ja generica porque foi gerada sem faixa real (lastPlayedTrack=null acima).
                        // Reaproveita o mesmo requestCoreSynthesis com 1 linha so, sem precisar de
                        // nenhum caminho novo no servico de voz.
                        val introFile = if (coreFile != null) {
                            syncCoreBufferStatus("Sintetizando abertura da prévia...")
                            voiceSynthesisMutex.withLock {
                                requestCoreSynthesis(
                                    listOf(enhanced.lines.first()), BULLETIN_PREP_TIMEOUT_MS,
                                    onProgress = { message -> syncCoreBufferStatus(message) },
                                    onProgressPercent = { percent -> syncCoreBufferStatus(radioBulletinBufferState.value.statusMessage, percent) },
                                )
                            }
                        } else {
                            null
                        }
                        coreBuffer.addLast(PreparedNewsCore(enhanced, coreFile, introFile))
                        saveCoreBufferManifest()
                        if (enhanced.source == RadioScriptSource.Fallback) scheduleFallbackAutoFix()
                        Log.d(TAG_RADIO_VOICE, "nucleo: buffer agora com ${coreBuffer.size}/$BULLETIN_BUFFER_TARGET (core=${coreFile != null}, falas=${enhanced.lines.size})")
                        syncCoreBufferStatus(null)
                        // Cooldown termico entre boletins consecutivos (pedido do usuario 03/09/2026,
                        // junto de subir threads pro maximo do aparelho/8): sessoes de geracao seguida
                        // mostraram o prefill do redator degradando de 7.3 pra 5.6 tok/s ao longo do
                        // dia (throttling termico, ja visto tambem na sintese de voz - 2t=410s/
                        // 4t=496s). Da um respiro pro SoC esfriar antes de emendar a proxima geracao
                        // pesada. So espera se ainda falta preparar mais item (senao fica parado 3min
                        // a toa depois do ultimo, sem nada que va se beneficiar do respiro).
                        // Pulado quando Gemini OU OpenRouter escreveram essa (usedCloudWriter) -
                        // pedido do usuario (04/09/2026, estendido 05/09/2026 pro OpenRouter): a
                        // escrita saiu da nuvem, so sobra a sintese de voz local sozinha (bem mais
                        // leve que LLM local + sintese juntos, a causa original do cooldown).
                        // Continua normal se caiu pro redator local Qwen3 (sem nenhuma chave
                        // configurada, ou os dois de nuvem falharam nessa hora) - ver ADR-021.
                        if (coreBuffer.size < BULLETIN_BUFFER_TARGET && !bulletinPrepPaused && !usedCloudWriter) {
                            val cooldownStart = System.currentTimeMillis()
                            while (System.currentTimeMillis() - cooldownStart < COOLDOWN_BETWEEN_BULLETINS_MS && !bulletinPrepPaused) {
                                val remainingS = ((COOLDOWN_BETWEEN_BULLETINS_MS - (System.currentTimeMillis() - cooldownStart)) / 1000)
                                    .coerceAtLeast(0)
                                syncCoreBufferStatus("Aguardando o aparelho esfriar antes do próximo boletim... ${remainingS}s")
                                delay(COOLDOWN_STATUS_TICK_MS)
                            }
                            syncCoreBufferStatus(null)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.e(TAG_RADIO_VOICE, "nucleo: erro inesperado preparando core", e)
                        syncCoreBufferStatus("Erro ao preparar núcleo: ${e.message ?: e::class.simpleName} (ver logcat, tag $TAG_RADIO_VOICE).")
                        break
                    }
                }
            } finally {
                syncCoreBufferStatus(null)
            }
        }
    }

    // So atualiza o texto/pulso de status quando o Nivel 2 (bulletinBuffer/refillBulletinBuffer)
    // nao estiver com uma mensagem propria em andamento - evita o preparo de fundo do nucleo
    // sobrescrever o progresso "de verdade" (pronto pra tocar) que o usuario esta acompanhando.
    // isPreparing=true enquanto tiver mensagem (pedido do usuario 03/09/2026: a bolinha nao
    // piscava durante o pre-aquecimento porque so o Nivel 2 ligava essa flag antes) - a bolinha
    // pisca na mesma posicao de sempre (proximo slot vazio do bulletinBuffer) mesmo sendo
    // trabalho do Nivel 1, so pra dar sinal de vida visual; nao muda o que fica tocavel.
    // Guarda contra bulletinPrepJob (job do Nivel 2), NAO contra radioBulletinBufferState.isPreparing
    // - essa flag e a MESMA que esta funcao liga no proprio primeiro sucesso, entao guardar contra
    // ela travava a si mesma: a 1a mensagem passava, ligava isPreparing=true, e da em diante TODA
    // atualizacao seguinte do Nivel 1 (inclusive o percentual por token e o syncCoreBufferStatus(null)
    // final) era descartada ate o Nivel 2 desligar a flag por fora - achado 03/09/2026 implementando
    // o percentual real do redator local, que dependia de chamadas repetidas chegarem na UI.
    private fun syncCoreBufferStatus(message: String?, percent: Int? = null) {
        if (bulletinPrepJob?.isActive == true) return
        syncBulletinBufferState(isPreparing = message != null, statusMessage = message, progressPercent = percent)
    }

    private fun clearCoreBuffer() {
        corePrepJob?.cancel()
        corePrepJob = null
        coreBuffer.forEach { it.coreFile?.delete() }
        coreBuffer.clear()
        saveCoreBufferManifest()
    }

    // Pasta privada do app em filesDir (NAO cacheDir) - sobrevive a "limpar cache" e a
    // reinicio/morte do processo, so some se o usuario fizer "Limpar dados" do app. Sem
    // permissao nenhuma envolvida (filesDir e sempre privado ao proprio app). Mesmo nome que
    // LocalRadioVoiceEngine usa pra escrever os .wav do nucleo (CORE_BUFFER_DIR_NAME).
    private val coreBufferDir: File
        get() = getApplication<Application>().filesDir
            .resolve(LocalRadioVoiceEngine.CORE_BUFFER_DIR_NAME).apply { mkdirs() }

    // Grava o estado atual do coreBuffer (Nivel 1) em disco - pedido do usuario (03/09/2026):
    // boletins ja escritos nao podiam sumir so porque o app reiniciou ou o Android limpou o
    // cache. So o Nivel 1 e persistido (nao o Nivel 2/bulletinBuffer): esse e especifico da radio
    // ativa e sempre limpo em startRadioNewsMode, entao nao sobreviveria a proxima sessao mesmo
    // se fosse salvo. Tambem varre a pasta e apaga .wav que sobrou de item ja consumido/removido
    // do coreBuffer (evita acumular lixo) - chamar sempre que coreBuffer mudar de conteudo.
    private fun saveCoreBufferManifest() {
        runCatching {
            val array = JSONArray()
            coreBuffer.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("title", item.script.story.title)
                        put("source", item.script.story.source)
                        put("summary", item.script.story.summary)
                        put("scriptSource", item.script.source.name)
                        put("duration", item.script.duration.name)
                        put("hasTrackContext", item.script.hasTrackContext)
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
                        put("coreFile", item.coreFile?.name)
                        put("introFile", item.introFile?.name)
                    },
                )
            }
            coreBufferDir.resolve(CORE_BUFFER_MANIFEST_FILE).writeText(array.toString())
            // Protege tambem os .wav "emprestados" pra um item do bulletinBuffer (Nivel 2) que
            // ainda nao tocou (ver PreparedBulletin.sourceCore, 06/09/2026) - sem isso, o proprio
            // ato de puxar um nucleo pro buffer da radio apagava o .wav original antes mesmo de
            // saber se o boletim ia tocar ou nao, impossibilitando devolver ele depois.
            val onLoan = bulletinBuffer.mapNotNull { it.sourceCore }
            val referenced = (
                coreBuffer.mapNotNull { it.coreFile?.name } + coreBuffer.mapNotNull { it.introFile?.name } +
                    onLoan.mapNotNull { it.coreFile?.name } + onLoan.mapNotNull { it.introFile?.name }
                ).toSet()
            coreBufferDir.listFiles { file -> file.name != CORE_BUFFER_MANIFEST_FILE && file.name !in referenced }
                ?.forEach { it.delete() }
        }.onFailure { Log.w(TAG_RADIO_VOICE, "nucleo: falha ao salvar manifest do buffer em disco", it) }
    }

    // Le o manifest salvo (se existir) e repopula coreBuffer - chamado uma vez na abertura do
    // app, antes do primeiro prewarmCoreBuffer(). Descarta silenciosamente qualquer entrada cujo
    // .wav nao exista mais em disco (sobrevive a "Limpar dados" parcial/corrupcao) em vez de
    // travar a restauracao inteira.
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
                        hasTrackContext = json.optBoolean("hasTrackContext", false),
                    )
                    val coreFile = json.optString("coreFile").takeIf { it.isNotBlank() }
                        ?.let { coreBufferDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    val introFile = json.optString("introFile").takeIf { it.isNotBlank() }
                        ?.let { coreBufferDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    PreparedNewsCore(script, coreFile, introFile)
                }.getOrNull()
            }
            coreBuffer.clear()
            coreBuffer.addAll(restored)
            Log.d(TAG_RADIO_VOICE, "nucleo: restaurados ${restored.size}/${array.length()} boletins do disco")
        }.onFailure { Log.w(TAG_RADIO_VOICE, "nucleo: falha ao restaurar buffer do disco", it) }
    }

    private fun syncBulletinBufferState(
        isPreparing: Boolean = radioBulletinBufferState.value.isPreparing,
        statusMessage: String? = radioBulletinBufferState.value.statusMessage,
        // Some sozinho quando a mensagem some junto (nova etapa/fim) - assim quem so passa
        // statusMessage=null pra "limpar" nao deixa um percentual velho grudado na tela.
        progressPercent: Int? = if (statusMessage == null) null else radioBulletinBufferState.value.progressPercent,
    ) {
        // Mesmo nivel que decide readyCount abaixo (bulletinBuffer se tiver algo, senao coreBuffer)
        // - fallbackSlots tem que vir da MESMA lista, senao os indices nao batem com as bolinhas
        // que a UI desenha (RadioBulletinBufferStatusCard, uma por posicao de readyCount).
        val activeSources = if (bulletinBuffer.isNotEmpty()) {
            bulletinBuffer.map { it.script.source }
        } else {
            coreBuffer.map { it.script.source }
        }
        radioBulletinBufferState.value = radioBulletinBufferState.value.copy(
            // Sem radio tocando o Nivel 2 (bulletinBuffer) nunca enche - cai pro Nivel 1
            // (coreBuffer) pras bolinhas refletirem o preparo automatico de verdade, em vez de
            // ficar preso em 0/3 (achado 03/09/2026 junto do botao de reproduzir por posicao).
            readyCount = if (bulletinBuffer.isNotEmpty()) bulletinBuffer.size else coreBuffer.size,
            isPreparing = isPreparing,
            isPaused = bulletinPrepPaused,
            statusMessage = statusMessage,
            progressPercent = progressPercent,
            // true quando o readyCount acima veio do Nivel 1 (nucleo, sem radio tocando) em vez
            // do Nivel 2 - usado so pra rotular o botao/bolinhas como "previa" (ver
            // RadioBulletinBufferStatusCard); cada bolinha checa seu proprio audio na hora de
            // tocar (playReadyBufferedBulletin(index)), nao precisa de lista pre-calculada aqui.
            hasCorePreview = bulletinBuffer.isEmpty() && coreBuffer.isNotEmpty(),
            hasPlayableAudio = bulletinBuffer.any { it.file != null } || coreBuffer.any { it.coreFile != null },
            fallbackSlots = activeSources.withIndex().filter { (_, source) -> source == RadioScriptSource.Fallback }
                .map { (index, _) -> index }.toSet(),
            hasFallback = activeSources.any { it == RadioScriptSource.Fallback },
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
        // Nivel 1 (nucleo) pausa junto - um botao so pro usuario, cobrindo os dois niveis do
        // pipeline (ver PreparedNewsCore).
        corePrepJob?.cancel()
        corePrepJob = null
        fallbackAutoFixJob?.cancel()
        fallbackAutoFixJob = null
        syncBulletinBufferState(isPreparing = false, statusMessage = null)
    }

    fun resumeBulletinPreparation() {
        if (!bulletinPrepPaused) return
        bulletinPrepPaused = false
        syncBulletinBufferState()
        prewarmCoreBuffer()
        if (radioNewsEnabled) refillBulletinBuffer()
    }

    // Apaga tudo que ja estava pronto nos dois niveis (nucleo + boletins prontos pra tocar) e
    // comeca de novo do zero (pedido do usuario 03/09/2026) - se estiver pausado, so limpa e
    // fica vazio ate o usuario retomar.
    fun resetBulletinBuffer() {
        clearBulletinBuffer()
        clearCoreBuffer()
        if (!bulletinPrepPaused) {
            prewarmCoreBuffer()
            if (radioNewsEnabled) refillBulletinBuffer()
        }
    }

    // Botao manual "Corrigir fallback" (pedido do usuario 05/09/2026): descarta so os itens que
    // cairam no roteiro generico (RadioScriptSource.Fallback - Gemini E redator local falharam
    // os dois, ou nenhum dos dois estava disponivel) dos dois niveis do buffer, e deixa o preparo
    // normal (prewarmCoreBuffer/refillBulletinBuffer) repor essas vagas do zero - o que tenta
    // Gemini primeiro de novo, com a mesma prioridade/retry de qualquer boletim novo. Nao mexe
    // nos itens que ja saíram com roteiro de LLM (Gemini ou redator local), so remove o que
    // precisa mesmo ser reescrito. Mesma funcao roda sozinha via scheduleFallbackAutoFix() assim
    // que um fallback novo entra no buffer, sem precisar o usuario apertar nada.
    fun fixFallbackBulletins() {
        val fallbackBulletins = bulletinBuffer.filter { it.script.source == RadioScriptSource.Fallback }
        if (fallbackBulletins.isNotEmpty()) {
            fallbackBulletins.forEach { it.file?.delete() }
            bulletinBuffer.removeAll(fallbackBulletins)
        }
        val fallbackCores = coreBuffer.filter { it.script.source == RadioScriptSource.Fallback }
        if (fallbackCores.isNotEmpty()) {
            fallbackCores.forEach { it.coreFile?.delete() }
            coreBuffer.removeAll(fallbackCores)
            saveCoreBufferManifest()
        }
        if (fallbackBulletins.isEmpty() && fallbackCores.isEmpty()) return
        Log.d(
            TAG_RADIO_VOICE,
            "boletim: corrigindo fallback - removidos ${fallbackBulletins.size} do buffer e ${fallbackCores.size} do nucleo pra regenerar",
        )
        syncBulletinBufferState()
        if (!bulletinPrepPaused) {
            prewarmCoreBuffer()
            if (radioNewsEnabled) refillBulletinBuffer()
        }
    }

    // Dispara fixFallbackBulletins() sozinho assim que o sistema percebe um item de fallback
    // novo em qualquer um dos 2 niveis (ver os 2 sites que chamam isso: bulletinBuffer.addLast em
    // refillBulletinBuffer e coreBuffer.addLast em prewarmCoreBuffer) - pedido do usuario
    // (05/09/2026): "que ele mesmo comece a rodar a correcao" sem precisar apertar o botao manual
    // toda vez. FALLBACK_AUTO_FIX_DELAY_MS de respiro antes de tentar, e o guard de job ativo
    // evita agendar em cima de uma correcao que ja esta rodando - se a causa raiz (sem internet,
    // Gemini fora do ar, chave invalida) ainda nao se resolveu, a proxima geracao tambem vai cair
    // em fallback e agendar de novo sozinha, criando um retry periodico (nao um loop apertado).
    private fun scheduleFallbackAutoFix() {
        if (fallbackAutoFixJob?.isActive == true) return
        fallbackAutoFixJob = viewModelScope.launch {
            delay(FALLBACK_AUTO_FIX_DELAY_MS)
            fixFallbackBulletins()
        }
    }

    private fun speakNextNewsBreak(lastPlayedTrack: RadioLastPlayedTrack? = currentRadioTrack) {
        if (!ttsReady || speakingNews) return
        val player = controller ?: return
        val prepared = bulletinBuffer.removeFirstOrNull()
        // Consumiu um item (ou tentou e o buffer estava vazio) - manda reabastecer ja, em
        // paralelo com a fala que vai comecar agora (ver ADR-019: buffer sempre com
        // BULLETIN_BUFFER_TARGET itens prontos, reposto assim que um sai).
        refillBulletinBuffer()

        val bulletin: RadioScript
        val readyFile: File?
        if (prepared != null) {
            bulletin = prepared.script
            readyFile = prepared.file?.takeIf { it.exists() }
            Log.d(TAG_RADIO_VOICE, "boletim: consumindo do buffer (audio=${readyFile != null}, restam ${bulletinBuffer.size})")
        } else {
            if (newsBulletins.isEmpty()) {
                // Feeds RSS podem ter falhado todos na carga inicial (rede instavel/DNS/feed
                // fora do ar - ver NewsBulletinRepository.loadStories, falha e ignorada
                // silenciosamente por feed) e a lista ficava vazia pro resto da sessao, sem log e
                // sem nova tentativa - o boletim simplesmente nunca mais tocava. Loga pra dar pra
                // diagnosticar; reloadNewsBulletinsIfNeeded ja tenta de novo em segundo plano.
                Log.w(TAG_RADIO_VOICE, "boletim: buffer e newsBulletins vazios - tentando recarregar")
                reloadNewsBulletinsIfNeeded()
                return
            }
            // Buffer vazio (sessao acabou de comecar, feed ainda carregando, ou consumo mais
            // rapido do que o preparo consegue acompanhar) - monta um boletim ao vivo com o
            // contexto real de agora (sem enhanceScript/redator local, que e lento demais pra
            // essa emergencia; so o fallback determinístico + decoracao).
            Log.w(TAG_RADIO_VOICE, "boletim: buffer vazio; montando boletim ao vivo")
            val index = nextBulletinIndex % newsBulletins.size
            val baseBulletin = newsBulletins[index]
            nextBulletinIndex += 1
            reloadNewsBulletinsIfCycleComplete()
            val upcomingTrack = player.currentMediaItem?.toUpcomingTrack()
            bulletin = baseBulletin
                .withLastPlayedIntro(lastPlayedTrack)
                .withPhilosophicalCloser(activeRadioName, upcomingTrack)
            readyFile = null
        }

        speakingNews = true
        currentNewsHeadline = bulletin.displayText
        controller?.let { updatePlayerState(it) }
        resumeAfterNews = player.isPlaying
        if (resumeAfterNews) player.pause()

        // Ponte curta antes do boletim (musica > passagem > boletim) - alterna entre as duas
        // faixas, ver playPassagem(). Cobre os dois caminhos abaixo (audio pronto e fallback
        // TTS Android) porque entra antes da bifurcacao.
        playPassagem {
            if (!speakingNews) return@playPassagem
            if (readyFile != null) {
                Log.d(TAG_RADIO_VOICE, "boletim: tocando audio do buffer")
                playAnnouncementFile(readyFile) { finishNewsBreak() }
                armAnnouncementWatchdog()
                return@playPassagem
            }

            Log.d(TAG_RADIO_VOICE, "boletim: sem audio pronto; usando TTS Android imediato")
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
                Log.d(TAG_RADIO_VOICE, "boletim: falando via TTS Android")
                armAnnouncementWatchdog()
            }
        }
    }

    // Passagem curta entre musica e boletim (e vice-versa) - roda em sequencia pelas faixas
    // de PASSAGEM_RESOURCES (res/raw, ver vinhetas/README.md) pra nao repetir sempre a mesma.
    // Reaproveita playVinhetaResource() (mesmo MediaPlayer.create de recurso, sem gap pra
    // tocar em seguida). Volume reduzido -8dB (pedido do usuario, tocava alto demais).
    private fun playPassagem(onFinished: () -> Unit) {
        val resId = PASSAGEM_RESOURCES[nextPassagemIndex % PASSAGEM_RESOURCES.size]
        nextPassagemIndex++
        playVinhetaResource(resId, volume = PASSAGEM_VOLUME, onFinished)
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
        if (!speakingNews || newsBreakEnding) return
        newsBreakEnding = true
        currentNewsHeadline = ""
        // Ponte curta depois do boletim (boletim > passagem > musica) antes de retomar.
        // speakingNews so vira false depois da passagem tocar - enquanto isso o watchdog
        // (armAnnouncementWatchdog, disparado por playVinhetaResource) continua cobrindo essa
        // janela, senao uma passagem travada nunca seria forcada a liberar a musica de volta.
        playPassagem {
            newsBreakEnding = false
            speakingNews = false
            if (resumeAfterNews) {
                resumeAfterNews = false
                viewModelScope.launch {
                    controller?.play()
                }
            }
            controller?.let { updatePlayerState(it) }
        }
    }

    // So pros botoes de teste em Configuracoes: ao contrario do playAnnouncementFile do boletim
    // ao vivo (que sempre apaga o WAV depois de tocar - ver RADIO_PIPELINE.md), este mantem o
    // arquivo no disco pra permitir repetir o mesmo teste sem sintetizar de novo (pedido do
    // usuario - sintese local pode levar bastante tempo, ver BULLETIN_PREP_TIMEOUT_MS).
    private fun playTestAudio(file: java.io.File, onFinished: () -> Unit = {}) {
        if (lastTestAudioFile != null && lastTestAudioFile != file) {
            lastTestAudioFile?.delete()
        }
        lastTestAudioFile = file
        announcementPlayer?.release()
        announcementPlayer = null
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (announcementPlayer === it) announcementPlayer = null
                    onFinished()
                }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    if (announcementPlayer === player) announcementPlayer = null
                    onFinished()
                    true
                }
                prepare()
                start()
                announcementPlayer = this
            }
        }.onFailure { onFinished() }
    }

    // Toca a passagem antes e depois, igual o boletim ao vivo (speakNextNewsBreak/
    // finishNewsBreak) - pedido do usuario pra poder ouvir o teste exatamente como sai na
    // radio de verdade, nao so a voz pelada.
    private fun playTestAudioWithPassagem(file: java.io.File, onFinished: () -> Unit = {}) {
        playPassagem {
            playTestAudio(file) {
                playPassagem { onFinished() }
            }
        }
    }

    fun replayLastTestAudio() {
        val file = lastTestAudioFile
        if (file == null || !file.exists()) {
            radioVoiceState.value = radioVoiceState.value.copy(
                message = "Nenhum audio de teste pra repetir, gere um teste primeiro.",
                canReplayTest = false,
            )
            return
        }
        playTestAudioWithPassagem(file)
    }

    // Toca o boletim pronto na posicao `index` do buffer (bulletinBuffer, Nivel 2) direto na tela
    // de Configuracoes, sem precisar entrar numa radio - pedido do usuario (03/09/2026): quer
    // ouvir o resultado do redator local assim que fica pronto. So espia (elementAtOrNull), nunca
    // remove do buffer - testar nao pode consumir o boletim que a radio de verdade ainda vai usar.
    // Sem radio nenhuma tocando o Nivel 2 nunca enche (refillBulletinBuffer exige radioNewsEnabled
    // - achado 03/09/2026), entao cai pro Nivel 1 (coreBuffer) como previa: so o miolo (falas 2-5),
    // sem abertura/fechamento reais porque nao ha faixa de contexto ainda. `index` deixa tocar
    // qualquer um dos ate BULLETIN_BUFFER_TARGET itens prontos, nao so o primeiro (pedido do
    // usuario 03/09/2026: "os outros que ficam pronto, nao sei como reproduzir eles") - ver
    // RadioBulletinBufferStatusCard, cada bolinha preenchida vira clicavel pra sua posicao.
    fun playReadyBufferedBulletin(index: Int = 0) {
        if (radioBulletinBufferState.value.isPlayingPreview) return
        val bulletinItem = bulletinBuffer.elementAtOrNull(index)
        val coreItem = coreBuffer.filter { it.coreFile != null }.getOrNull(index)
        val isCorePreview: Boolean
        val mainFile: File?
        val storyTitle: String?
        val introFile: File?
        if (bulletinItem?.file != null) {
            isCorePreview = false
            mainFile = bulletinItem.file
            storyTitle = bulletinItem.script.story.title
            introFile = null
        } else if (coreItem != null) {
            isCorePreview = true
            mainFile = coreItem.coreFile
            storyTitle = coreItem.script.story.title
            introFile = coreItem.introFile
        } else {
            // Achado 03/09/2026: sem isso, tocar numa posicao sem audio (ex.: item que caiu pro
            // roteiro padrao apos falha do redator - fallback nunca tem coreFile, ver
            // prewarmCoreBuffer) nao fazia NADA visivel - o botao parecia habilitado (readyCount
            // conta item "com texto pronto", nao "com audio pronto") mas ficava mudo ao tocar.
            radioBulletinBufferState.value = radioBulletinBufferState.value.copy(
                statusMessage = "Esse boletim não tem áudio pronto (o redator local falhou nessa matéria, ou a voz local está desligada).",
                progressPercent = null,
            )
            return
        }
        if (mainFile == null || !mainFile.exists()) {
            radioBulletinBufferState.value = radioBulletinBufferState.value.copy(
                statusMessage = "Esse boletim não tem áudio pronto (voz local desligada agora).",
                progressPercent = null,
            )
            return
        }
        radioBulletinBufferState.value = radioBulletinBufferState.value.copy(
            isPlayingPreview = true,
            statusMessage = if (isCorePreview) {
                "Tocando prévia — matéria: \"${storyTitle ?: "?"}\" (sem fechamento, que depende de rádio real tocando)..."
            } else {
                radioBulletinBufferState.value.statusMessage
            },
        )
        previewPlayer?.release()
        previewPlayer = null
        val leadIn = introFile?.takeIf { it.exists() }
        // Fala 1 (introFile) primeiro se existir - ela ja e generica (gerada sem faixa real),
        // sem ela a previa comecava direto na resposta do Nico, sem contexto nenhum de qual
        // noticia estava sendo discutida (achado 03/09/2026, feedback do usuario: "fica sem pe
        // nem cabeca pra entender do que se trata").
        if (leadIn != null) {
            playPreviewFile(leadIn) { playPreviewFile(mainFile) { finishPreview() } }
        } else {
            playPreviewFile(mainFile) { finishPreview() }
        }
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

    private fun playAnnouncementFile(file: java.io.File, onFinished: () -> Unit) {
        announcementPlayer?.release()
        announcementPlayer = null
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG_RADIO_VOICE, "announcement file completed bytes=${file.length()}")
                    it.release()
                    if (announcementPlayer === it) announcementPlayer = null
                    file.delete()
                    onFinished()
                }
                setOnErrorListener { player, what, extra ->
                    Log.w(TAG_RADIO_VOICE, "announcement file error what=$what extra=$extra bytes=${file.length()}")
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
            Log.e(TAG_RADIO_VOICE, "failed to play announcement file bytes=${file.length()}", it)
            file.delete()
            onFinished()
        }
    }

    private suspend fun synthesizeLocalVoiceSafely(
        script: RadioScript,
        timeoutMs: Long = LOCAL_VOICE_TIMEOUT_MS,
        onProgress: (String) -> Unit = {},
        onProgressPercent: (Int) -> Unit = {},
    ): File? = requestLocalVoiceSynthesis(script, timeoutMs, onProgress, onProgressPercent)?.file

    private suspend fun requestLocalVoiceSynthesis(
        script: RadioScript,
        timeoutMs: Long,
        onProgress: (String) -> Unit = {},
        onProgressPercent: (Int) -> Unit = {},
    ): LocalVoiceSynthesisResult? =
        requestVoiceSynthesis(timeoutMs, "full lines=${script.lines.size}", onProgress, onProgressPercent) { context, receiver ->
            RadioVoiceSynthesisService.intent(context, script, receiver)
        }

    // Nucleo pre-aquecido (ver PreparedNewsCore/prewarmCoreBuffer) - so as falas do meio, sem
    // musica de fundo (mixBackgroundMusic depende do tamanho final, so calculado no encaixe).
    private suspend fun requestCoreSynthesis(
        lines: List<RadioScriptLine>,
        timeoutMs: Long,
        onProgress: (String) -> Unit = {},
        onProgressPercent: (Int) -> Unit = {},
    ): File? =
        requestVoiceSynthesis(timeoutMs, "core lines=${lines.size}", onProgress, onProgressPercent) { context, receiver ->
            RadioVoiceSynthesisService.coreIntent(context, lines, receiver)
        }?.file

    // Encaixe das pontas (fala 1/6 com contexto real) num nucleo ja pronto - ver
    // LocalRadioVoiceEngine.spliceEdges. Chamado por refillBulletinBuffer() quando o boletim
    // entrando no bulletinBuffer veio de um PreparedNewsCore com coreFile != null.
    private suspend fun requestSpliceSynthesis(
        coreFile: File,
        introLine: RadioScriptLine,
        closerLine: RadioScriptLine,
        timeoutMs: Long,
        onProgress: (String) -> Unit = {},
        onProgressPercent: (Int) -> Unit = {},
    ): File? =
        requestVoiceSynthesis(timeoutMs, "splice core=${coreFile.name}", onProgress, onProgressPercent) { context, receiver ->
            RadioVoiceSynthesisService.spliceIntent(context, coreFile, introLine, closerLine, receiver)
        }?.file

    // Base compartilhada por synthesizeLocalVoiceSafely/requestCoreSynthesis/
    // requestSpliceSynthesis - so muda o Intent que dispara o RadioVoiceSynthesisService, o
    // protocolo de resposta (ResultReceiver, progresso, timeout) e o mesmo pros 3 modos.
    // onProgressPercent e separado de onProgress (nao muda a assinatura usada pelos testes de
    // Configuracoes) - so quem quiser o numero (refillBulletinBuffer/prewarmCoreBuffer, pedido
    // do usuario 03/09/2026) passa os dois; index/total por fala ja vem do
    // RadioVoiceSynthesisService (RESULT_PROGRESS), so nao ha stage sem numero real aqui.
    private suspend fun requestVoiceSynthesis(
        timeoutMs: Long,
        logLabel: String,
        onProgress: (String) -> Unit,
        onProgressPercent: (Int) -> Unit = {},
        buildIntent: (Context, ResultReceiver) -> Intent,
    ): LocalVoiceSynthesisResult? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val context = getApplication<Application>()
                val startedAt = System.currentTimeMillis()
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (!continuation.isActive) return
                        if (resultCode == RadioVoiceSynthesisService.RESULT_PROGRESS) {
                            val index = resultData?.getInt(RadioVoiceSynthesisService.EXTRA_PROGRESS_INDEX) ?: 0
                            val total = resultData?.getInt(RadioVoiceSynthesisService.EXTRA_PROGRESS_TOTAL) ?: 0
                            Log.d(TAG_RADIO_VOICE, "voice progress $index/$total")
                            onProgress("Sintetizando vozes: fala $index de $total...")
                            if (total > 0) onProgressPercent(index * 100 / total)
                            return
                        }
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
                Log.d(TAG_RADIO_VOICE, "request voice $logLabel timeout=${timeoutMs}ms")
                continuation.invokeOnCancellation {
                    Log.w(TAG_RADIO_VOICE, "voice request cancelled after ${System.currentTimeMillis() - startedAt}ms")
                }
                runCatching {
                    context.startService(buildIntent(context, receiver))
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
            story = com.pailer.localtune.data.NewsStory(title = this, source = "Pailer FM"),
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

    private fun MediaItem.toRadioLastPlayedTrack(): RadioLastPlayedTrack? {
        val metadata = mediaMetadata
        val title = metadata.title?.toString().orEmpty().trim()
        if (title.isBlank()) return null
        return RadioLastPlayedTrack(
            title = title,
            artist = metadata.artist?.toString().orEmpty().trim(),
        )
    }

    // Igual toRadioLastPlayedTrack, mas tambem busca genero/ano na biblioteca ja carregada pelo
    // id da faixa - usado so pra "proxima musica" do fechamento filosofico
    // (withPhilosophicalCloser/musicTrivia em RadioBulletin.kt), que precisa desses dois campos
    // pra escolher a curiosidade (a introducao "voce acaba de ouvir" nao precisa disso).
    private fun MediaItem.toUpcomingTrack(): RadioLastPlayedTrack? {
        val base = toRadioLastPlayedTrack() ?: return null
        val song = libraryState.value.songs.firstOrNull { it.id.toString() == mediaId }
        return base.copy(genre = song?.genre.orEmpty(), year = song?.year ?: 0)
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
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        announcementPlayer?.release()
        previewPlayer?.release()
        lastTestAudioFile?.delete()
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
        const val KEY_RADIO_BULLETIN_LOCAL_WRITER = "radio_bulletin_local_writer"
        const val KEY_RADIO_BULLETIN_CLOUD_WRITER = "radio_bulletin_cloud_writer"
        const val KEY_RADIO_VOICE_ENABLED = "radio_voice_enabled"
        const val CORE_BUFFER_MANIFEST_FILE = "manifest.json"
        // Pausa entre boletins consecutivos do preparo automatico (Nivel 1) pra deixar o SoC
        // esfriar - ver comentario em prewarmCoreBuffer. Status atualiza a cada
        // COOLDOWN_STATUS_TICK_MS pra usuario acompanhar a contagem regressiva.
        // Reduzido de 180s pra 60s (06/09/2026, pedido do usuario) - com o cache de prefixo do
        // redator local (ver LocalLlamaTextGenerator/ensurePrefixCacheNative), o prefill caiu de
        // ~230s pra ~30s por boletim, entao o mesmo throttling termico que motivou os 180s
        // originais (ADR-020, medido ANTES do cache existir) deve pesar menos agora. Ainda existe
        // risco de acumular calor em sessoes longas - se voltar a aparecer throttling visivel
        // (decode caindo bem abaixo de ~2 tok/s), subir esse valor de novo.
        const val COOLDOWN_BETWEEN_BULLETINS_MS = 60_000L
        const val COOLDOWN_STATUS_TICK_MS = 15_000L
        // Respiro antes de scheduleFallbackAutoFix() tentar de novo sozinho (ver fixFallbackBulletins)
        // - evita loop apertado batendo no Gemini/redator local sem parar quando a causa da falha
        // (sem internet, chave invalida, os dois writers fora do ar) ainda nao se resolveu; da
        // tempo real pra uma falha transitoria passar antes da proxima tentativa automatica.
        const val FALLBACK_AUTO_FIX_DELAY_MS = 45_000L
        const val MAX_HISTORY_ITEMS = 80
        const val POSITION_SAVE_INTERVAL_MS = 3000L
        const val RADIO_BULLETIN_DEFAULT_INTERVAL = 3
        const val TTS_READY_WAIT_STEPS = 10
        const val TTS_READY_WAIT_INTERVAL_MS = 180L
        const val LOCAL_VOICE_TIMEOUT_MS = 12_000L
        // Pre-geracao roda em segundo plano durante a musica anterior ao boletim (minutos
        // disponiveis), entao pode esperar bem mais que o timeout usado na hora H do boletim.
        // Historico Piper/Kokoro (obsoleto, motor trocado pra Supertonic): ~60-65s e ~152s
        // respectivamente - valores abaixo nao se aplicam mais.
        // Atualizacao (03/09/2026): com Supertonic o timeout de 175s estava CORTANDO a sintese
        // em segundo plano antes de terminar (log de campo mostrou 410s com numThreads=2 /
        // 496s com numThreads=4, RTF≈5,6 no aparelho) -
        // o boletim ao vivo caia sistematicamente pro fallback TTS do Android mesmo com buffer
        // (BULLETIN_BUFFER_TARGET) tendo musicas de sobra pra preparar, porque quem matava o job
        // era esse timeout, nao falta de tempo real. Subido pro mesmo teto do teste (600s), depois
        // pra 660s (11min, pedido do usuario 03/09/2026) pra dar mais folga ao redator local
        // terminar sem estourar e cair no fallback deterministico (que usa frase fixa - ver
        // FallbackRadioScriptWriter) - folga aqui nao custa nada, roda em segundo plano.
        const val BULLETIN_PREP_TIMEOUT_MS = 660_000L

        // Quantos boletins prontos (roteiro + audio, se a voz local estiver ligada) o app
        // mantem em reserva o tempo todo - ver refillBulletinBuffer()/ADR-019. Preparo roda um
        // de cada vez em segundo plano bem antes de precisar, entao BULLETIN_PREP_TIMEOUT_MS
        // acima deixou de ser um aperto real (so importa se o consumo for mais rapido que o
        // preparo consegue repor, ex.: songsBetweenBulletins = 1).
        // Subido de 3 pra 5 (pedido do usuario 04/09/2026, junto da diversificacao de feeds em
        // NewsBulletinRepository) - mais folga de boletins prontos pra cobrir a variedade nova
        // de temas sem esbarrar em timeout de preparo. Ver ADR-021.
        const val BULLETIN_BUFFER_TARGET = 5

        // -8dB em amplitude linear (10^(-8/20)) - passagem_1/2 tocavam alto demais no volume
        // original do arquivo (pedido do usuario, 02/09/2026).
        const val PASSAGEM_VOLUME = 0.398f
        val PASSAGEM_RESOURCES = listOf(R.raw.passagem_1, R.raw.passagem_2, R.raw.passagem_3)
        const val ANNOUNCEMENT_WATCHDOG_TIMEOUT_MS = 90_000L
        const val LOCAL_VOICE_TEST_TIMEOUT_MS = 35_000L
        const val ANDROID_VOICE_TEST_TIMEOUT_MS = 25_000L
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
            "jazz" to R.raw.vinheta_jazz,
            "anos2000" to R.raw.vinheta_anos_2000,
        )
    }
}
