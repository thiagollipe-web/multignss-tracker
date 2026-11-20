package com.thigas.multignss

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.ceil
import org.json.JSONObject

data class Camera(val at: LatLon, val limitKmh: Int?)
data class SpeedWay(val points: List<LatLon>, val limitKmh: Int)
data class RoadData(val cameras: List<Camera>, val ways: List<SpeedWay>)

/**
 * Radares (highway=speed_camera) e limites (maxspeed) do OpenStreetMap ao longo da rota, via Overpass.
 * ponytail: servidores públicos do Overpass às vezes caem; sem resposta = sem radar/limite (a rota segue normal);
 * upgrade: baixar os radares do país uma vez e guardar offline.
 */
object RoadInfo {
    private val MIRRORS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    private const val TIMEOUT_MS = 30000
    private const val MAX_POLY_POINTS = 120
    const val SNAP_M = 25.0
    const val MAX_ROUTE_M = 200_000.0 // acima disso a consulta Overpass estoura o tempo

    fun fetch(route: Route, ua: String): RoadData {
        val body = "data=" + URLEncoder.encode(query(route.points), "UTF-8")
        var last: Exception? = null
        for (m in MIRRORS) {
            try { return parse(post(m, body, ua)) } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("Overpass indisponível")
    }

    fun query(points: List<LatLon>): String {
        val step = ceil(points.size / MAX_POLY_POINTS.toDouble()).toInt().coerceAtLeast(1)
        val sample = points.filterIndexed { i, _ -> i % step == 0 }.let { if (it.last() != points.last()) it + points.last() else it }
        val poly = sample.joinToString(",") { "%.5f,%.5f".format(java.util.Locale.US, it.lat, it.lon) }
        return "[out:json][timeout:25];(node(around:40,$poly)[\"highway\"=\"speed_camera\"];" +
            "way(around:15,$poly)[\"maxspeed\"];);out geom;"
    }

    private fun post(url: String, body: String, ua: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = TIMEOUT_MS; c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode != 200) error("Overpass HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(json: String): RoadData {
        val els = JSONObject(json).getJSONArray("elements")
        val cams = mutableListOf<Camera>()
        val ways = mutableListOf<SpeedWay>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val limit = parseMaxspeed(e.optJSONObject("tags")?.optString("maxspeed").orEmpty())
            when (e.optString("type")) {
                "node" -> cams += Camera(LatLon(e.getDouble("lat"), e.getDouble("lon")), limit)
                "way" -> {
                    val g = e.optJSONArray("geometry") ?: continue
                    if (limit == null) continue
                    ways += SpeedWay((0 until g.length()).map { LatLon(g.getJSONObject(it).getDouble("lat"), g.getJSONObject(it).getDouble("lon")) }, limit)
                }
            }
        }
        return RoadData(cams, ways)
    }

    /** "60" → 60, "40 mph" → 64; "BR:urban", "none", "signals" → null. */
    fun parseMaxspeed(v: String): Int? {
        val n = v.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        return if (v.contains("mph")) (n * 1.609).toInt() else n
    }

    /** Limite da via mais próxima (até [SNAP_M] m), ou null. */
    fun limitAt(here: LatLon, ways: List<SpeedWay>): Int? =
        ways.map { it to Geo.distToLine(here, it.points) }.filter { it.second <= SNAP_M }.minByOrNull { it.second }?.first?.limitKmh

    /** Radar mais perto à frente, dentro de [rangeM], ignorando os já passados. */
    fun nextCamera(here: LatLon, cams: List<Camera>, passed: Set<Camera>, rangeM: Double): Pair<Camera, Double>? =
        cams.filter { it !in passed }.map { it to Geo.dist(here, it.at) }.filter { it.second <= rangeM }.minByOrNull { it.second }
}

/** Noite = 18h às 6h (ponytail: hora fixa; upgrade: calcular nascer/pôr do sol pela posição). */
fun isNight(hour: Int) = hour >= 18 || hour < 6
