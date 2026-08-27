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

class RadioVoiceSynthesisService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedAt = System.currentTimeMillis()
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        val texts = intent?.getStringArrayListExtra(EXTRA_TEXTS).orEmpty()
        val speakers = intent?.getIntArrayExtra(EXTRA_SPEAKERS) ?: intArrayOf()
        if (receiver == null || texts.isEmpty()) {
            Log.w(TAG, "invalid voice request receiver=${receiver != null} texts=${texts.size}")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            Log.d(TAG, "start synthesis lines=${texts.size} chars=${texts.sumOf { it.length }}")
            val result = runCatching {
                val script = RadioScript(
                    story = NewsStory(title = texts.first(), source = "Pailer Player"),
                    source = RadioScriptSource.Fallback,
                    lines = texts.mapIndexed { index, text ->
                        RadioScriptLine(
                            speaker = if (speakers.getOrNull(index) == SPEAKER_MALE) {
                                RadioSpeaker.Male
                            } else {
                                RadioSpeaker.Female
                            },
                            text = text,
                        )
                    },
                )
                Log.d(TAG, "loading voice engine")
                LocalRadioVoiceEngine(
                    context = applicationContext,
                    packageRepository = RadioVoicePackageRepository(applicationContext),
                ).use { engine ->
                    Log.d(TAG, "generating audio")
                    val file = engine.synthesize(script)
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
        const val EXTRA_RECEIVER = "receiver"
        const val EXTRA_TEXTS = "texts"
        const val EXTRA_SPEAKERS = "speakers"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_ELAPSED_MS = "elapsed_ms"
        const val SPEAKER_FEMALE = 0
        const val SPEAKER_MALE = 1
        private const val TAG = "PailerRadioVoice"

        fun intent(context: Context, script: RadioScript, receiver: ResultReceiver): Intent =
            Intent(context, RadioVoiceSynthesisService::class.java).apply {
                putExtra(EXTRA_RECEIVER, receiver)
                putStringArrayListExtra(
                    EXTRA_TEXTS,
                    ArrayList(script.lines.map { it.text }),
                )
                putExtra(
                    EXTRA_SPEAKERS,
                    script.lines.map {
                        if (it.speaker == RadioSpeaker.Male) SPEAKER_MALE else SPEAKER_FEMALE
                    }.toIntArray(),
                )
            }
    }
}
