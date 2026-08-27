package com.pailer.localtune.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class LocalWeather(
    val city: String,
    val temperatureCelsius: Int,
)

class WeatherRepository {
    suspend fun currentSaoPauloWeather(): LocalWeather? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(SAO_PAULO_WEATHER_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PailerPlayer/0.1")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val current = JSONObject(body).getJSONObject("current")
            LocalWeather(
                city = "Sao Paulo",
                temperatureCelsius = current.getDouble("temperature_2m").roundToInt(),
            )
        }.getOrNull()
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 2500
        const val SAO_PAULO_WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=-23.55&longitude=-46.63&current=temperature_2m&timezone=America%2FSao_Paulo"
    }
}
