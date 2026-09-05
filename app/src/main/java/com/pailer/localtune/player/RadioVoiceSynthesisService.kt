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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class RadioVoiceSynthesisService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedAt = System.currentTimeMillis()
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        val texts = intent?.getStringArrayListExtra(EXTRA_TEXTS).orEmpty()
        val speakers = intent?.getIntArrayExtra(EXTRA_SPEAKERS) ?: intArrayOf()
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_FULL
        val coreFilePath = intent?.getStringExtra(EXTRA_CORE_FILE_PATH)
        if (receiver == null || texts.isEmpty() || (mode == MODE_SPLICE && coreFilePath.isNullOrBlank())) {
            Log.w(TAG, "invalid voice request receiver=${receiver != null} texts=${texts.size} mode=$mode")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            Log.d(TAG, "start synthesis mode=$mode lines=${texts.size} chars=${texts.sumOf { it.length }}")
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
                    val file = when (mode) {
                        MODE_CORE -> engine.synthesizeCore(lines, onLineDone)
                        MODE_SPLICE -> {
                            // texts/speakers carrega exatamente 2 falas nessa ordem: [0]=intro
                            // (fala 1, cola ANTES do nucleo), [1]=closer (fala 6, cola DEPOIS) -
                            // ver LocalTuneViewModel.requestSpliceSynthesis/spliceIntent.
                            engine.spliceEdges(File(coreFilePath!!), lines[0], lines[1])
                        }
                        else -> {
                            val script = RadioScript(
                                story = NewsStory(title = texts.first(), source = "Pailer FM"),
                                source = RadioScriptSource.Fallback,
                                lines = lines,
                            )
                            engine.synthesize(script, onLineDone)
                        }
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
        // EXTRA_MODE: "full" (default, comportamento de sempre - script inteiro, com musica de
        // fundo), "core" (so sintetiza, sem musica de fundo - ver
        // LocalRadioVoiceEngine.synthesizeCore, usado no pre-aquecimento radio-agnostico) ou
        // "splice" (encaixa 2 falas de ponta [intro, closer] em volta de um nucleo ja pronto -
        // ver LocalRadioVoiceEngine.spliceEdges, usado quando entra numa radio de verdade).
        const val EXTRA_MODE = "mode"
        const val MODE_FULL = "full"
        const val MODE_CORE = "core"
        const val MODE_SPLICE = "splice"
        const val EXTRA_CORE_FILE_PATH = "core_file_path"
        private const val TAG = "PailerRadioVoice"

        fun intent(context: Context, script: RadioScript, receiver: ResultReceiver): Intent =
            linesIntent(context, script.lines, receiver)

        // Nucleo pre-aquecido: so as falas do meio (radio-agnosticas), sem musica de fundo.
        fun coreIntent(context: Context, lines: List<RadioScriptLine>, receiver: ResultReceiver): Intent =
            linesIntent(context, lines, receiver).apply { putExtra(EXTRA_MODE, MODE_CORE) }

        // Encaixe: coreFile e o WAV "seco" ja sintetizado por coreIntent, introLine/closerLine
        // sao as 2 falas com contexto real (radio/faixa) recem-decoradas (ver
        // RadioScript.withLastPlayedIntro/withPhilosophicalCloser).
        fun spliceIntent(
            context: Context,
            coreFile: File,
            introLine: RadioScriptLine,
            closerLine: RadioScriptLine,
            receiver: ResultReceiver,
        ): Intent =
            linesIntent(context, listOf(introLine, closerLine), receiver).apply {
                putExtra(EXTRA_MODE, MODE_SPLICE)
                putExtra(EXTRA_CORE_FILE_PATH, coreFile.absolutePath)
            }

        private fun linesIntent(context: Context, lines: List<RadioScriptLine>, receiver: ResultReceiver): Intent =
            Intent(context, RadioVoiceSynthesisService::class.java).apply {
                putExtra(EXTRA_RECEIVER, receiver)
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
