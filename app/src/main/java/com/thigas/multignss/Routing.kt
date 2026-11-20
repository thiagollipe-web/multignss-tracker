package com.thigas.multignss

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

data class LatLon(val lat: Double, val lon: Double)
/** Tipo de manobra; o ângulo (0 = frente, negativo = esquerda) desenha a seta. */
enum class Turn(val angle: Float) {
    STRAIGHT(0f), SLIGHT_LEFT(-45f), LEFT(-90f), SHARP_LEFT(-135f),
    SLIGHT_RIGHT(45f), RIGHT(90f), SHARP_RIGHT(135f), UTURN(180f), ROUNDABOUT(0f), ARRIVE(0f)
}

data class Lane(val valid: Boolean, val turn: Turn)
data class Step(
    val at: LatLon, val text: String, val distanceM: Double,
    val turn: Turn = Turn.STRAIGHT, val lanes: List<Lane> = emptyList(),
)
data class Route(val points: List<LatLon>, val steps: List<Step>, val distanceM: Double, val durationS: Double)
data class Place(val name: String, val at: LatLon)

enum class TravelMode(val label: String, val server: String) {
    CAR("Carro", "routed-car"), BIKE("Bike", "routed-bike"), FOOT("A pé", "routed-foot")
}

/**
 * Rota pelo OSRM do FOSSGIS (routing.openstreetmap.de) e busca pelo Nominatim: grátis, sem chave.
 * ponytail: calcular a rota precisa de internet (depois de calculada, seguir funciona offline);
 * upgrade: roteador no aparelho (GraphHopper/BRouter com mapa .pbf baixado).
 */
object Routing {
    private const val TIMEOUT_MS = 15000

    fun route(from: LatLon, to: LatLon, mode: TravelMode, ua: String): Route {
        val url = "https://routing.openstreetmap.de/${mode.server}/route/v1/driving/" +
            "${from.lon},${from.lat};${to.lon},${to.lat}?overview=full&geometries=geojson&steps=true"
        return parseOsrm(get(url, ua))
    }

    fun search(query: String, near: LatLon?, ua: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val bias = near?.let { "&viewbox=${it.lon - 0.5},${it.lat + 0.5},${it.lon + 0.5},${it.lat - 0.5}" } ?: ""
        return parseNominatim(get("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q=$q$bias", ua))
    }

    private fun get(url: String, ua: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", ua) // exigido pelas políticas do Nominatim/OSRM
            c.setRequestProperty("Accept-Language", "pt-BR")
            if (c.responseCode != 200) error("servidor respondeu HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parseOsrm(json: String): Route {
        val o = JSONObject(json)
        if (o.optString("code") != "Ok") error("sem rota: ${o.optString("message", o.optString("code"))}")
        val r = o.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map { coords.getJSONArray(it).toLatLon() }
        if (points.size < 2) error("rota vazia")
        val steps = mutableListOf<Step>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val s = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until s.length()) {
                val st = s.getJSONObject(i)
                val m = st.getJSONObject("maneuver")
                steps += Step(
                    m.getJSONArray("location").toLatLon(),
                    instruction(m.optString("type"), m.optString("modifier"), st.optString("name"), m.optInt("exit", 0)),
                    st.optDouble("distance", 0.0),
                    turn(m.optString("type"), m.optString("modifier")),
                    lanes(st.optJSONArray("intersections")?.optJSONObject(0)?.optJSONArray("lanes")),
                )
            }
        }
        return Route(points, steps, r.getDouble("distance"), r.getDouble("duration"))
    }

    fun parseNominatim(json: String): List<Place> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull {
            val p = a.getJSONObject(it)
            val lat = p.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = p.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            Place(p.optString("display_name"), LatLon(lat, lon))
        }
    }

    private fun JSONArray.toLatLon() = LatLon(getDouble(1), getDouble(0))

    /** Faixas na manobra: indicação "none" = siga em frente. */
    fun lanes(a: JSONArray?): List<Lane> {
        if (a == null) return emptyList()
        return (0 until a.length()).map {
            val l = a.getJSONObject(it)
            val ind = l.optJSONArray("indications")?.optString(0).orEmpty()
            Lane(l.optBoolean("valid"), if (ind == "none" || ind == "straight") Turn.STRAIGHT else turn("turn", ind))
        }
    }

    fun turn(type: String, modifier: String): Turn = when {
        type == "arrive" -> Turn.ARRIVE
        type == "roundabout" || type == "rotary" -> Turn.ROUNDABOUT
        modifier == "uturn" -> Turn.UTURN
        modifier == "sharp left" -> Turn.SHARP_LEFT
        modifier == "left" -> Turn.LEFT
        modifier == "slight left" -> Turn.SLIGHT_LEFT
        modifier == "sharp right" -> Turn.SHARP_RIGHT
        modifier == "right" -> Turn.RIGHT
        modifier == "slight right" -> Turn.SLIGHT_RIGHT
        else -> Turn.STRAIGHT
    }

    fun instruction(type: String, modifier: String, name: String, exit: Int): String {
        val on = if (name.isNotBlank()) " na $name" else ""
        val turn = when (modifier) {
            "left" -> "Vire à esquerda"
            "right" -> "Vire à direita"
            "slight left" -> "Mantenha-se à esquerda"
            "slight right" -> "Mantenha-se à direita"
            "sharp left" -> "Vire acentuadamente à esquerda"
            "sharp right" -> "Vire acentuadamente à direita"
            "uturn" -> "Faça o retorno"
            else -> "Siga em frente"
        }
        return when (type) {
            "depart" -> if (name.isNotBlank()) "Siga pela $name" else "Comece a seguir a rota"
            "arrive" -> "Você chegou ao destino"
            "roundabout", "rotary" -> if (exit > 0) "Na rotatória, pegue a ${exit}ª saída$on" else "Entre na rotatória$on"
            "exit roundabout", "exit rotary" -> "Saia da rotatória$on"
            else -> turn + on
        }
    }
}

object Geo {
    private const val R = 6_371_000.0

    fun dist(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Distância (m) do ponto até a polyline, em projeção plana local (boa para dezenas de km). */
    fun distToLine(p: LatLon, line: List<LatLon>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        if (line.size == 1) return dist(p, line[0])
        val kx = cos(Math.toRadians(p.lat)) * R * Math.PI / 180
        val ky = R * Math.PI / 180
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val ax = (line[i].lon - p.lon) * kx; val ay = (line[i].lat - p.lat) * ky
            val bx = (line[i + 1].lon - p.lon) * kx; val by = (line[i + 1].lat - p.lat) * ky
            val dx = bx - ax; val dy = by - ay
            val len = dx * dx + dy * dy
            val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
            val x = ax + t * dx; val y = ay + t * dy
            best = minOf(best, sqrt(x * x + y * y))
        }
        return best
    }
}

/** Estado da navegação a cada posição nova. */
data class NavState(
    val stepIndex: Int,
    val toNextM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
)

object Navigator {
    const val STEP_REACHED_M = 25.0
    const val OFF_ROUTE_M = 50.0
    const val ARRIVED_M = 25.0
    private const val REACH_SECONDS = 2.0
    private const val LOOK_AHEAD = 3

    fun update(route: Route, here: LatLon, currentStep: Int, accuracyM: Double = 0.0, speedMs: Double = 0.0): NavState {
        var i = currentStep.coerceIn(0, route.steps.lastIndex.coerceAtLeast(0))
        // avança ao chegar perto de uma das próximas manobras; raio cresce com a velocidade
        // (a 30 m/s o GNSS anda 30 m entre leituras e poderia "pular" o raio fixo)
        val reach = maxOf(STEP_REACHED_M, speedMs * REACH_SECONDS)
        val lookAhead = minOf(route.steps.lastIndex, i + LOOK_AHEAD)
        for (j in lookAhead downTo i + 1) if (Geo.dist(here, route.steps[j].at) < reach) { i = j; break }
        val next = route.steps.getOrNull(i + 1)
        val toNext = next?.let { Geo.dist(here, it.at) } ?: Geo.dist(here, route.points.last())
        // distanceM de cada passo = trecho da manobra até a próxima
        val remaining = toNext + route.steps.drop(i + 1).sumOf { it.distanceM }
        val ratio = if (route.distanceM > 0) (remaining / route.distanceM).coerceIn(0.0, 1.0) else 0.0
        val tolerance = OFF_ROUTE_M + accuracyM.coerceAtMost(OFF_ROUTE_M)
        return NavState(
            stepIndex = i,
            toNextM = toNext,
            remainingM = remaining,
            remainingS = route.durationS * ratio,
            offRoute = Geo.distToLine(here, route.points) > tolerance,
            arrived = Geo.dist(here, route.points.last()) < ARRIVED_M,
        )
    }

    fun formatDist(m: Double): String = when {
        m >= 1000 -> "%.1f km".format(m / 1000).replace('.', ',')
        m >= 100 -> "${(m / 10).toInt() * 10} m"
        else -> "${m.toInt()} m"
    }

    fun formatTime(s: Double): String {
        val min = (s / 60).toInt()
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "${min.coerceAtLeast(1)} min"
    }
}
