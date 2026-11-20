package com.thigas.multignss

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class TrackPoint(val lat: Double, val lon: Double, val ele: Double?, val timeMs: Long?)

/** GPX 1.1 mínimo: um <trk> com um <trkseg>. */
object Gpx {
    private fun iso() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun write(name: String, points: List<TrackPoint>): String = buildString {
        val fmt = iso()
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="MultiGNSS Tracker" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("<trk><name>").append(escape(name)).append("</name><trkseg>\n")
        for (p in points) {
            append("""<trkpt lat="${p.lat}" lon="${p.lon}">""")
            p.ele?.let { append("<ele>").append(it).append("</ele>") }
            p.timeMs?.let { append("<time>").append(fmt.format(Date(it))).append("</time>") }
            append("</trkpt>\n")
        }
        append("</trkseg></trk>\n</gpx>\n")
    }

    /** Lê trkpt e rtept; ignora pontos com coordenada inválida. */
    fun read(input: InputStream): List<TrackPoint> {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            // bloqueia XXE em arquivos vindos de fora
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = f.newDocumentBuilder().parse(input)
        val fmt = iso()
        return listOf("trkpt", "rtept").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).mapNotNull { i ->
                val e = nodes.item(i) as Element
                val lat = e.getAttribute("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lon = e.getAttribute("lon").toDoubleOrNull() ?: return@mapNotNull null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                TrackPoint(
                    lat, lon,
                    e.child("ele")?.toDoubleOrNull(),
                    e.child("time")?.let { runCatching { fmt.parse(it.take(19) + "Z")?.time }.getOrNull() },
                )
            }
        }
    }

    private fun Element.child(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/** Projeção do Skyplot: elevação 90° no centro, 0° na borda; azimute 0° = norte (topo). */
fun skyplotXY(elevationDeg: Float, azimuthDeg: Float, radius: Float): Pair<Float, Float> {
    val r = radius * (90f - elevationDeg.coerceIn(0f, 90f)) / 90f
    val a = Math.toRadians(azimuthDeg.toDouble())
    return (r * Math.sin(a)).toFloat() to (-r * Math.cos(a)).toFloat()
}
