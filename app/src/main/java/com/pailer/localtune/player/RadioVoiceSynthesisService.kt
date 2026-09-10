package com.pailer.localtune.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import com.pailer.localtune.data.NewsStory
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioScriptLine
import com.pailer.localtune.data.RadioScriptSource
import com.pailer.localtune.data.RadioSpeaker
import com.pailer.localtune.data.RadioVoicePackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class RadioVoiceSynthesisService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Um Job por requestId (ver EXTRA_REQUEST_ID/ACTION_CANCEL) - pedido do usuario (09/09/2026):
    // quando quem pediu a sintese desiste de esperar (timeout, boletim descartado), o processo
    // isolado continuava sintetizando TODAS as falas sozinho, disputando CPU com o proximo pedido
    // (achado em campo: um boletim cancelado ainda rodando quando o seguinte comecava, os dois
    // disputando as mesmas threads). Guardar o Job aqui deixa o ramo ACTION_CANCEL abaixo
    // interromper so ESSE pedido, sem afetar outros rodando ao mesmo tempo no mesmo processo.
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, NO_REQUEST_ID)
            val job = activeJobs.remove(requestId)
            Log.d(TAG, "cancel requested for requestId=$requestId found=${job != null}")
            job?.cancel()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val startedAt = System.currentTimeMillis()
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        val texts = intent?.getStringArrayListExtra(EXTRA_TEXTS).orEmpty()
        val speakers = intent?.getIntArrayExtra(EXTRA_SPEAKERS) ?: intArrayOf()
        val persistent = intent?.getBooleanExtra(EXTRA_PERSISTENT, false) ?: false
        val requestId = intent?.getLongExtra(EXTRA_REQUEST_ID, NO_REQUEST_ID) ?: NO_REQUEST_ID
        if (receiver == null || texts.isEmpty()) {
            Log.w(TAG, "invalid voice request receiver=${receiver != null} texts=${texts.size}")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val job = scope.launch {
            Log.d(TAG, "start synthesis lines=${texts.size} chars=${texts.sumOf { it.length }} persistent=$persistent requestId=$requestId")
            val result = runCatching {
                val lines = texts.mapIndexed { index, text ->
                    RadioScriptLine(
                        speaker = if (speakers.getOrNull(index) == SPEAKER_MALE) {
                            RadioSpeaker.Male
                        } else {
                            RadioSpeaker.Female
                        },
                        text = text,
                    )
                }
                Log.d(TAG, "loading voice engine")
                LocalRadioVoiceEngine(
                    context = applicationContext,
                    packageRepository = RadioVoicePackageRepository(applicationContext),
                ).use { engine ->
                    Log.d(TAG, "generating audio")
                    val onLineDone = { index: Int, total: Int ->
                        // So um "aviso de vida" pro chamador (tela de teste) saber que nao
                        // travou - RESULT_PROGRESS nunca resolve a coroutine que espera o
                        // resultado final, so RESULT_OK/RESULT_FAILED fazem isso (ver
                        // requestLocalVoiceSynthesis em LocalTuneViewModel.kt).
                        receiver.send(
                            RESULT_PROGRESS,
                            Bundle().apply {
                                putInt(EXTRA_PROGRESS_INDEX, index)
                                putInt(EXTRA_PROGRESS_TOTAL, total)
                            },
                        )
                        Unit
                    }
                    val file = if (persistent) {
                        // Buffer do boletim (ver LocalTuneViewModel.prewarmCoreBuffer) - precisa
                        // sobreviver a reinicio do app/processo, ver LocalRadioVoiceEngine.
                        // synthesizeCore.
                        engine.synthesizeCore(lines, onLineDone)
                    } else {
                        val script = RadioScript(
                            story = NewsStory(title = texts.first(), source = "Pailer FM"),
                            source = RadioScriptSource.Fallback,
                            lines = lines,
                        )
                        engine.synthesize(script, onLineDone)
                    }
                    Log.d(TAG, "audio result path=${file?.absolutePath.orEmpty()} bytes=${file?.length() ?: 0}")
                    file
                }
            }
            val outputFile = result.getOrNull()
            val elapsedMs = System.currentTimeMillis() - startedAt
            val detail = when {
                result.isFailure -> {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "synthesis failed after ${elapsedMs}ms", error)
                    "Falha na voz local: ${error?.message ?: error?.javaClass?.simpleName.orEmpty()}"
                }
                outputFile == null -> {
                    Log.w(TAG, "synthesis returned no file after ${elapsedMs}ms")
                    "Motor local carregou, mas nao gerou audio."
                }
                outputFile.length() <= 44 -> {
                    Log.w(TAG, "synthesis returned tiny file after ${elapsedMs}ms bytes=${outputFile.length()}")
                    "Audio gerado ficou vazio."
                }
                else -> {
                    Log.d(TAG, "synthesis ok after ${elapsedMs}ms bytes=${outputFile.length()}")
                    "Audio local gerado em ${elapsedMs}ms."
                }
            }

            receiver.send(
                if (outputFile != null && outputFile.length() > 44) RESULT_OK else RESULT_FAILED,
                Bundle().apply {
                    putString(EXTRA_OUTPUT_PATH, outputFile?.absolutePath)
                    putString(EXTRA_DETAIL, detail)
                    putLong(EXTRA_ELAPSED_MS, elapsedMs)
                },
            )
            stopSelfResult(startId)
        }
        if (requestId != NO_REQUEST_ID) {
            activeJobs[requestId] = job
            job.invokeOnCompletion { activeJobs.remove(requestId, job) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private inline fun <T : LocalRadioVoiceEngine, R> T.use(block: (T) -> R): R =
        try {
            block(this)
        } finally {
            release()
        }

    companion object {
        const val RESULT_OK = 1
        const val RESULT_FAILED = 0
        // Enviado 0+ vezes ANTES do resultado final (RESULT_OK/RESULT_FAILED), um por fala
        // sintetizada - so pra dar sinal de vida pra UI de teste, nunca resolve a espera.
        const val RESULT_PROGRESS = 2
        const val EXTRA_RECEIVER = "receiver"
        const val EXTRA_TEXTS = "texts"
        const val EXTRA_SPEAKERS = "speakers"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_ELAPSED_MS = "elapsed_ms"
        const val EXTRA_PROGRESS_INDEX = "progress_index"
        const val EXTRA_PROGRESS_TOTAL = "progress_total"
        const val SPEAKER_FEMALE = 0
        const val SPEAKER_MALE = 1
        // Buffer do boletim (ver LocalTuneViewModel.prewarmCoreBuffer/persistentIntent): quando
        // true, o audio vai pra filesDir (sobrevive a reinicio do app) em vez do cacheDir efemero
        // de sempre - ver LocalRadioVoiceEngine.synthesizeCore.
        const val EXTRA_PERSISTENT = "persistent"
        // Identifica um pedido de sintese pra poder cancela-lo individualmente depois (ver
        // ACTION_CANCEL/activeJobs) - gerado pelo chamador (LocalTuneViewModel.requestVoiceSynthesis),
        // unico por pedido. -1L (NO_REQUEST_ID) significa "nao rastreavel", mantem compatibilidade
        // com qualquer chamador que nao precise cancelar.
        const val EXTRA_REQUEST_ID = "request_id"
        const val NO_REQUEST_ID = -1L
        // Pedido do usuario (09/09/2026): cancela so o pedido com esse requestId, sem afetar
        // outros rodando ao mesmo tempo no mesmo processo isolado - ver activeJobs.
        const val ACTION_CANCEL = "com.pailer.localtune.action.CANCEL_VOICE_SYNTHESIS"
        private const val TAG = "PailerRadioVoice"

        fun intent(context: Context, script: RadioScript, receiver: ResultReceiver, requestId: Long = NO_REQUEST_ID): Intent =
            linesIntent(context, script.lines, receiver, requestId)

        fun persistentIntent(context: Context, script: RadioScript, receiver: ResultReceiver, requestId: Long = NO_REQUEST_ID): Intent =
            linesIntent(context, script.lines, receiver, requestId).apply { putExtra(EXTRA_PERSISTENT, true) }

        fun cancelIntent(context: Context, requestId: Long): Intent =
            Intent(context, RadioVoiceSynthesisService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_REQUEST_ID, requestId)
            }

        private fun linesIntent(context: Context, lines: List<RadioScriptLine>, receiver: ResultReceiver, requestId: Long): Intent =
            Intent(context, RadioVoiceSynthesisService::class.java).apply {
                putExtra(EXTRA_RECEIVER, receiver)
                putExtra(EXTRA_REQUEST_ID, requestId)
                putStringArrayListExtra(EXTRA_TEXTS, ArrayList(lines.map { it.text }))
                putExtra(
                    EXTRA_SPEAKERS,
                    lines.map {
                        if (it.speaker == RadioSpeaker.Male) SPEAKER_MALE else SPEAKER_FEMALE
                    }.toIntArray(),
                )
            }
    }
}
