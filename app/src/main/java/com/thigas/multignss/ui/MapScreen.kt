package com.thigas.multignss.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thigas.multignss.GnssViewModel
import com.thigas.multignss.TrackPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private const val START_ZOOM = 16.0

@Composable
fun MapScreen(vm: GnssViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    val fix by vm.fix.collectAsStateWithLifecycle()
    val trail by vm.trail.collectAsStateWithLifecycle()
    val imported by vm.imported.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val offlineOnly by vm.offlineOnly.collectAsStateWithLifecycle()
    val status by vm.mapStatus.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val topo by vm.topo.collectAsStateWithLifecycle()
    var centered by remember { mutableStateOf(false) }

    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            controller.setZoom(START_ZOOM)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            overlays.add(CopyrightOverlay(ctx))
        }
    }
    val accuracy = remember { Polygon(map).apply { fillPaint.color = 0x33FFC400; outlinePaint.color = 0xFFFFC400.toInt(); outlinePaint.strokeWidth = 2f } }
    val track = remember { Polyline(map).apply { outlinePaint.color = Color(0xFFFFB300).toArgb(); outlinePaint.strokeWidth = 8f } }
    val importedLine = remember { Polyline(map).apply { outlinePaint.color = Color(0xFFFF7043).toArgb(); outlinePaint.strokeWidth = 6f } }
    val me = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "Você" } }
    remember { map.overlays.addAll(listOf(importedLine, track, accuracy)); true }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> { map.onResume(); vm.refreshOnline() }
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        map.onResume()
        onDispose { owner.lifecycle.removeObserver(obs); map.onPause(); map.onDetach() }
    }

    LaunchedEffect(imported) {
        imported.firstOrNull()?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon)) }
    }

    val exportGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        uri?.let(vm::exportGpx)
    }
    val importGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importGpx)
    }

    Column(modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            val archives = remember(status) { vm.maps.offlineArchives() } // relê só quando o status muda
            Text(
                (if (online) "● Online" else "○ Sem internet") + "  ·  " +
                    (if (archives.isEmpty()) "nenhum .mbtiles/.zip" else "offline: ${archives.joinToString()}"),
                fontSize = 12.sp, color = if (online) MaterialTheme.colorScheme.primary else Color(0xFFFF8A65),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Modo Somente Offline", Modifier.weight(1f))
                Switch(checked = offlineOnly, onCheckedChange = vm::setOfflineOnly)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Estilo Topo (permite baixar offline)", Modifier.weight(1f))
                Switch(checked = topo, onCheckedChange = vm::setTopo)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { vm.downloadVisible(map) }) { Text("Baixar Área Offline") }
                Button(onClick = { vm.setRecording(!recording) }) { Text(if (recording) "■ Parar trilha" else "● Gravar trilha") }
                OutlinedButton(onClick = { exportGpx.launch("trilha.gpx") }, enabled = trail.isNotEmpty()) { Text("Exportar GPX") }
                OutlinedButton(onClick = { importGpx.launch(arrayOf("*/*")) }) { Text("Importar GPX") }
                OutlinedButton(onClick = vm::clearTrail, enabled = trail.isNotEmpty()) { Text("Limpar") }
                OutlinedButton(onClick = { fix?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon)) } }) { Text("Centralizar") }
            }
            val info = "Trilha: ${trail.size} pts" + if (status.isNotEmpty()) "  ·  $status" else ""
            Text(info, fontSize = 12.sp)
        }

        AndroidView(
            factory = { map },
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            update = { m ->
                vm.maps.tileSource(topo).let { if (m.tileProvider.tileSource.name() != it.name()) m.setTileSource(it) }
                val night = com.thigas.multignss.isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
                m.overlayManager.tilesOverlay.setColorFilter(if (night) org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS else null)
                m.setUseDataConnection(!offlineOnly)
                track.setPoints(trail.toGeo())
                importedLine.setPoints(imported.toGeo())
                fix?.let { f ->
                    val p = GeoPoint(f.lat, f.lon)
                    me.position = p
                    if (me !in m.overlays) m.overlays.add(me)
                    accuracy.points = Polygon.pointsAsCircle(p, (f.accuracyM ?: 0f).toDouble())
                    if (!centered) { m.controller.setCenter(p); centered = true }
                }
                m.invalidate()
            },
        )
    }
}

private fun List<TrackPoint>.toGeo() = map { GeoPoint(it.lat, it.lon) }
