package com.pailer.localtune.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import com.pailer.localtune.util.DayPeriod

// RemoteViews (widget de tela inicial) nao anima gif - aqui so tiramos um quadro estatico
// e representativo do gif do periodo pra usar como fundo do widget quando a radio esta ao
// vivo. Cacheia em memoria por periodo pra nao redecodificar o gif inteiro a cada
// atualizacao do widget.
internal object RadioGifFrameCache {
    private const val TARGET_WIDTH = 480

    @Volatile
    private var cachedPeriod: DayPeriod? = null

    @Volatile
    private var cachedFrame: Bitmap? = null

    fun frameFor(context: Context, period: DayPeriod): Bitmap? {
        cachedFrame?.let { if (cachedPeriod == period) return it }
        val frame = decode(context, period.gifRes)
        cachedPeriod = period
        cachedFrame = frame
        return frame
    }

    private fun decode(context: Context, resId: Int): Bitmap? = runCatching {
        val movie = context.resources.openRawResource(resId).use(Movie::decodeStream) ?: return null
        if (movie.width() <= 0 || movie.height() <= 0) return null
        val scale = TARGET_WIDTH.toFloat() / movie.width()
        val targetHeight = (movie.height() * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(TARGET_WIDTH, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            scale(scale, scale)
            // Quadro do meio da animacao costuma representar melhor o gif que o primeiro.
            movie.setTime(movie.duration() / 2)
            movie.draw(this, 0f, 0f)
        }
        bitmap
    }.getOrNull()
}
