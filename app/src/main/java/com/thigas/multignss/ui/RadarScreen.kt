package com.thigas.multignss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thigas.multignss.Constellation
import com.thigas.multignss.Fix
import com.thigas.multignss.GnssRepository
import com.thigas.multignss.GnssViewModel
import com.thigas.multignss.Satellite
import com.thigas.multignss.skyplotXY

private const val CN0_MAX = 50f

fun Constellation.color() = when (this) {
    Constellation.GLONASS -> Color(0xFFFF5A5A)
    Constellation.GALILEO -> Color(0xFFB388FF)
    Constellation.BEIDOU -> Color(0xFF39FF88)
    Constellation.GPS -> Color(0xFF8A9BA8)
}

@Composable
fun RadarScreen(vm: GnssViewModel, modifier: Modifier) {
    val sats by vm.satellites.collectAsStateWithLifecycle()
    val all by vm.allSatellites.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val fix by vm.fix.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LazyColumn(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Constellation.entries.forEach { c ->
                    FilterChip(selected = c in filter, onClick = { vm.toggle(c) }, label = { Text("${c.label} · ${c.country}") })
                }
            }
        }
        item { Summary(all) }
        item { FixCard(fix, error) }
        item { Skyplot(sats) }
        if (sats.isEmpty()) item { Text("Procurando satélites… vá para céu aberto.", color = Color.Gray) }
        items(sats, key = { "${it.constellation}-${it.svid}-${it.carrierHz}" }) { SatRow(it) }
    }
}

@Composable
private fun Summary(all: List<Satellite>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Constellation.entries.forEach { c ->
            val s = all.filter { it.constellation == c }
            val avg = s.map { it.cn0DbHz }.average().takeIf { !it.isNaN() }
            Column(
                Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(8.dp)
                    .width(78.dp),
            ) {
                Text(c.label, color = c.color(), fontSize = 13.sp)
                Text("${s.count { it.usedInFix }}/${s.size}", fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                Text("fix/visíveis", fontSize = 10.sp, color = Color.Gray)
                Text(avg?.let { "%.0f dB-Hz".format(it) } ?: "—", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun FixCard(fix: Fix?, error: String?) {
    val mono = FontFamily.Monospace
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).padding(10.dp)) {
        if (error != null) Text("⚠ $error", color = Color(0xFFFF8A65))
        if (fix == null) { Text("Sem posição ainda", fontFamily = mono); return }
        Text("LAT %.6f  LON %.6f".format(fix.lat, fix.lon), fontFamily = mono)
        Text(
            "ALT ${fix.altitudeM?.let { "%.1f m".format(it) } ?: "—"}  " +
                "VEL ${fix.speedMs?.let { "%.1f km/h".format(it * 3.6f) } ?: "—"}  " +
                "RUMO ${fix.bearingDeg?.let { "%.0f°".format(it) } ?: "—"}",
            fontFamily = mono, fontSize = 13.sp,
        )
        Text("PRECISÃO ${fix.accuracyM?.let { "±%.1f m".format(it) } ?: "—"}", fontFamily = mono, fontSize = 13.sp)
    }
}

@Composable
private fun Skyplot(sats: List<Satellite>) {
    val grid = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val label = MaterialTheme.colorScheme.primary.toArgb()
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)
            .semantics { contentDescription = "Skyplot com ${sats.size} satélites; centro é o zênite, topo é o norte" },
    ) {
        val r = size.minDimension / 2
        val c = center
        listOf(1f, 2f / 3, 1f / 3).forEach { drawCircle(grid, r * it, c, style = Stroke(2f)) }
        drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y))
        val paint = android.graphics.Paint().apply { color = label; textSize = 34f; isAntiAlias = true }
        drawContext.canvas.nativeCanvas.apply {
            drawText("N", c.x - 10, c.y - r - 8, paint)
            drawText("S", c.x - 10, c.y + r + 34, paint)
            drawText("L", c.x + r + 6, c.y + 12, paint)
            drawText("O", c.x - r - 30, c.y + 12, paint)
        }
        sats.forEach { s ->
            val (x, y) = skyplotXY(s.elevationDeg, s.azimuthDeg, r)
            val p = Offset(c.x + x, c.y + y)
            val dot = 8f + 10f * (s.cn0DbHz / CN0_MAX).coerceIn(0f, 1f)
            if (s.usedInFix) drawCircle(s.constellation.color(), dot, p)
            else drawCircle(s.constellation.color(), dot, p, style = Stroke(3f))
        }
    }
}

@Composable
private fun SatRow(s: Satellite) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${s.constellation.label} #${s.svid}", color = s.constellation.color())
            Text(if (s.usedInFix) "NO FIX" else "visível", fontSize = 12.sp, color = if (s.usedInFix) MaterialTheme.colorScheme.primary else Color.Gray)
        }
        Text(
            "CN0 %.1f dB-Hz  EL %.0f°  AZ %.0f°  %s".format(s.cn0DbHz, s.elevationDeg, s.azimuthDeg, GnssRepository.band(s.carrierHz)),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
        )
        LinearProgressIndicator(
            progress = { (s.cn0DbHz / CN0_MAX).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = s.constellation.color(),
        )
    }
}
