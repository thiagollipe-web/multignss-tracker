package com.thigas.multignss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.thigas.multignss.isNight
import org.osmdroid.views.overlay.TilesOverlay
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.thigas.multignss.Turn
import org.osmdroid.views.overlay.milestones.MilestoneManager
import org.osmdroid.views.overlay.milestones.MilestonePathDisplayer
import org.osmdroid.views.overlay.milestones.MilestonePixelDistanceLister
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import com.thigas.multignss.Constellation
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thigas.multignss.GnssViewModel
import com.thigas.multignss.LatLon
import com.thigas.multignss.Navigator
import com.thigas.multignss.Place
import com.thigas.multignss.TravelMode
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val BANNER = Color(0xFFFFC400)
private val ON_BANNER = Color(0xFF1A1A1A)
private val WARN = Color(0xFFE53935)
private const val LANES_SHOW_M = 800.0
private const val THEN_SHOW_M = 300.0
private const val OVER_TOLERANCE_KMH = 5
private const val NAV_ZOOM = 18.0
private const val MIN_SPEED_ROTATE = 1.5f // m/s; abaixo disso o rumo do GNSS é ruído

@Composable
fun NavScreen(vm: GnssViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    val g = vm.guide
    val fix by vm.fix.collectAsStateWithLifecycle()
    val route by g.route.collectAsStateWithLifecycle()
    val nav by g.nav.collectAsStateWithLifecycle()
    val navigating by g.navigating.collectAsStateWithLifecycle()
    val dest by g.destination.collectAsStateWithLifecycle()
    val results by g.results.collectAsStateWithLifecycle()
    val status by g.status.collectAsStateWithLifecycle()
    val mode by g.mode.collectAsStateWithLifecycle()
    val offlineOnly by vm.offlineOnly.collectAsStateWithLifecycle()
    val topo by vm.topo.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var centered by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    // tela acesa enquanto navega (o GNSS para quando o app sai da tela)
    val view = LocalView.current
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }

    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // mapa limpo: zoom por pinça
            isTilesScaledToDpi = true
            overlays.add(CopyrightOverlay(ctx))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint) = false
                override fun longPressHelper(p: GeoPoint): Boolean {
                    g.setDestination(Place("Ponto no mapa (%.5f, %.5f)".format(p.latitude, p.longitude), LatLon(p.latitude, p.longitude)))
                    return true
                }
            }))
        }
    }
    // rota amarela com contorno escuro: aparece em qualquer fundo
    val casing = remember { Polyline(map).apply { outlinePaint.color = Color(0xFF1A1A1A).toArgb(); outlinePaint.strokeWidth = 22f; outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND } }
    val line = remember {
        Polyline(map).apply {
            outlinePaint.color = Color(0xFFFFC400).toArgb(); outlinePaint.strokeWidth = 13f; outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            // chevrons escuros a cada ~110 px, girando com o caminho
            val chevron = android.graphics.Path().apply { moveTo(-5f, -6f); lineTo(3f, 0f); lineTo(-5f, 6f) }
            val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE; strokeWidth = 3.5f; color = 0xFF1A1A1A.toInt()
                strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
            }
            setMilestoneManagers(listOf(MilestoneManager(MilestonePixelDistanceLister(55.0, 110.0), MilestonePathDisplayer(0.0, true, chevron, ink))))
        }
    }
    // posição = seta amarela que aponta para onde você vai
    val me = remember {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "Você"; isFlat = true
            icon = androidx.core.content.ContextCompat.getDrawable(ctx, com.thigas.multignss.R.drawable.ic_nav_arrow)
            setInfoWindow(null)
        }
    }
    val target = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "Destino" } }
    remember { map.overlays.add(casing); map.overlays.add(line); true }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        owner.lifecycle.addObserver(obs)
        map.onResume()
        onDispose { owner.lifecycle.removeObserver(obs); map.onPause(); map.onDetach() }
    }

    // quando a rota chega, enquadra ela inteira
    LaunchedEffect(route) {
        val pts = route?.points?.map { GeoPoint(it.lat, it.lon) }
        if (pts != null && pts.size > 1 && !navigating) map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, 120) }
    }
    val guideSat by vm.guideSat.collectAsStateWithLifecycle()
    val limit by g.limit.collectAsStateWithLifecycle()
    val arrived by g.arrived.collectAsStateWithLifecycle()
    val camera by g.camera.collectAsStateWithLifecycle()
    val night = isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    val cardBg = if (night) Color(0xFF1C1B17) else Color.White
    val cardFg = if (night) Color.White else Color.Black
    val cardSub = if (night) Color(0xFFBDB8A6) else Color.DarkGray
    val sats by vm.allSatellites.collectAsStateWithLifecycle()

    Column(modifier) {
        if (!navigating) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Para onde? (endereço ou lugar)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); g.search(query) }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TravelMode.entries.forEach { m -> FilterChip(m == mode, { g.setMode(m) }, { Text(m.label) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Satélite guia:", fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    Constellation.entries.forEach { c -> FilterChip(c == guideSat, { vm.setGuideSat(c) }, { Text(c.label) }) }
                }
                Text("Dica: segure o dedo no mapa para escolher o destino.", fontSize = 12.sp, color = Color.Gray)
                results.forEach { p ->
                    Text(
                        p.name, maxLines = 2, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { query = p.name.substringBefore(','); g.setDestination(p); map.controller.animateTo(GeoPoint(p.at.lat, p.at.lon)) }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(8.dp),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            AndroidView(
                factory = { map },
                modifier = Modifier.fillMaxSize(),
                update = { m ->
                    vm.maps.tileSource(topo).let { if (m.tileProvider.tileSource.name() != it.name()) m.setTileSource(it) }
                    m.overlayManager.tilesOverlay.setColorFilter(if (night) TilesOverlay.INVERT_COLORS else null)
                    m.setUseDataConnection(!offlineOnly)
                    casing.setPoints(route?.points?.map { GeoPoint(it.lat, it.lon) }.orEmpty())
                    line.setPoints(route?.points?.map { GeoPoint(it.lat, it.lon) }.orEmpty())
                    dest?.let {
                        target.position = GeoPoint(it.at.lat, it.at.lon)
                        if (target !in m.overlays) m.overlays.add(target)
                    } ?: m.overlays.remove(target)
                    fix?.let { f ->
                        val p = GeoPoint(f.lat, f.lon)
                        me.position = p
                        if (me !in m.overlays) m.overlays.add(me)
                        if (!centered) { m.controller.setCenter(p); centered = true }
                        if (navigating) {
                            if (m.zoomLevelDouble < NAV_ZOOM - 1) m.controller.setZoom(NAV_ZOOM)
                            m.controller.setCenter(p)
                            val b = f.bearingDeg
                            if (b != null && (f.speedMs ?: 0f) > MIN_SPEED_ROTATE) m.mapOrientation = -b
                        } else if (m.mapOrientation != 0f) m.mapOrientation = 0f
                        // Marker plano gira -rotation na tela: compensa o giro do mapa
                        f.bearingDeg?.let { b -> me.rotation = -(b + m.mapOrientation) }
                    }
                    m.invalidate()
                },
            )

            val r = route
            val s = nav
            // faixa de manobra no topo, estilo app de trânsito
            if (r != null && s != null) {
                val next = r.steps.getOrNull(s.stepIndex + 1)
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp)
                        .background(if (s.offRoute) WARN else BANNER, RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TurnIcon(next?.turn ?: Turn.ARRIVE, if (s.offRoute) Color.White else ON_BANNER, 64.dp, next?.text ?: "Destino")
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(Navigator.formatDist(s.toNextM), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (s.offRoute) Color.White else ON_BANNER)
                        Text(if (s.offRoute) "Fora da rota · recalculando" else next?.text ?: "Siga até o destino",
                            fontSize = 16.sp, color = if (s.offRoute) Color.White else ON_BANNER, maxLines = 2)
                        // faixas: as certas em destaque
                        val lanes = next?.lanes.orEmpty()
                        if (!s.offRoute && lanes.size >= 2 && s.toNextM < LANES_SHOW_M) {
                            Row(Modifier.padding(top = 4.dp).background(Color(0x22000000), RoundedCornerShape(8.dp)).padding(4.dp)) {
                                lanes.forEach { l -> TurnIcon(l.turn, if (l.valid) ON_BANNER else ON_BANNER.copy(alpha = 0.25f), 26.dp, if (l.valid) "faixa certa" else "faixa errada") }
                            }
                        }
                        // depois: manobra seguinte quando estão próximas
                        val after = r.steps.getOrNull(s.stepIndex + 2)
                        if (!s.offRoute && after != null && next != null && next.distanceM < THEN_SHOW_M) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text("depois ", fontSize = 13.sp, color = ON_BANNER)
                                TurnIcon(after.turn, ON_BANNER, 22.dp, after.text)
                            }
                        }
                    }
                }
            }
            // Orbi comemora a chegada
            if (arrived) {
                Column(
                    Modifier.align(Alignment.Center).background(cardBg, RoundedCornerShape(24.dp)).border(3.dp, BANNER, RoundedCornerShape(24.dp))
                        .clickable { g.dismissArrived() }.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Orbi(if (night) OrbiMood.SLEEP else OrbiMood.HAPPY, 110.dp, "Orbi comemorando")
                    Text(if (night) "Você chegou! Boa noite 🌙" else "Você chegou! 🎉", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cardFg)
                    dest?.let { Text(it.name.substringBefore(','), fontSize = 14.sp, color = cardSub, maxLines = 1) }
                    Text("toque para fechar", fontSize = 11.sp, color = cardSub)
                }
            }
            // aviso de radar
            camera?.let { (c, d) ->
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = if (r != null) 150.dp else 8.dp)
                        .background(WARN, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Orbi(OrbiMood.ALERT, 30.dp, "Orbi alerta")
                    Spacer(Modifier.width(6.dp))
                    Text("Radar em ${Navigator.formatDist(d)}" + (c.limitKmh?.let { " · $it km/h" } ?: ""),
                        color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            // satélite guia: quanto a constelação escolhida está ajudando agora
            val mine = sats.filter { it.constellation == guideSat }
            val used = mine.count { it.usedInFix }
            val cn0 = mine.filter { it.usedInFix }.map { it.cn0DbHz }.average().takeIf { !it.isNaN() }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp).background(Color(0xE6141210), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(guideSat.color(), CircleShape))
                Text(
                    "  ${guideSat.label}: $used guiando" + (cn0?.let { " · %.0f dB-Hz".format(it) } ?: "") +
                        if (used == 0) " (fora do fix)" else "",
                    fontSize = 12.sp, color = Color.White,
                )
            }
            // bolha de velocidade
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // placa de limite (estilo placa brasileira R-19)
                limit?.let { lim ->
                    Box(
                        Modifier.size(52.dp).background(Color.White, CircleShape).border(6.dp, Color(0xFFD32F2F), CircleShape)
                            .semantics { contentDescription = "Limite $lim quilômetros por hora" },
                        contentAlignment = Alignment.Center,
                    ) { Text("$lim", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
                    Spacer(Modifier.size(6.dp))
                }
                fix?.speedMs?.let { v ->
                    val kmh = (v * 3.6f).roundToInt()
                    val over = limit?.let { kmh > it + OVER_TOLERANCE_KMH } == true
                    Box(
                        Modifier.size(64.dp).background(if (over) WARN else Color.White, CircleShape).border(3.dp, if (over) WARN else BANNER, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$kmh", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (over) Color.White else Color.Black)
                            Text("km/h", fontSize = 10.sp, color = if (over) Color.White else Color.DarkGray)
                        }
                    }
                }
            }
        }

        // cartão de chegada
        Column(Modifier.fillMaxWidth().background(cardBg).padding(12.dp)) {
            val r = route
            val s = nav
            if (r != null && s != null) {
                val eta = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(System.currentTimeMillis() + (s.remainingS * 1000).toLong()))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(eta, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cardFg)
                    Text("  chegada", fontSize = 13.sp, color = cardSub)
                }
                Text("${Navigator.formatTime(s.remainingS)} · ${Navigator.formatDist(s.remainingM)}", fontSize = 15.sp, color = cardSub)
            }
            dest?.let { Text("📍 ${it.name}", fontSize = 14.sp, color = cardFg, maxLines = 2, fontWeight = FontWeight.Medium) }
            if (status.isNotEmpty() && !navigating) Text(status, fontSize = 12.sp, color = cardSub)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (navigating) Button(onClick = g::stop, colors = ButtonDefaults.buttonColors(containerColor = WARN)) { Text("■ Parar") }
                else Button(onClick = g::start, enabled = route != null, colors = ButtonDefaults.buttonColors(containerColor = BANNER, contentColor = ON_BANNER)) { Text("▶ Ir agora") }
                OutlinedButton(onClick = g::computeRoute, enabled = dest != null) { Text("Recalcular", color = cardFg) }
                OutlinedButton(onClick = g::clear, enabled = dest != null) { Text("Limpar", color = cardFg) }
            }
        }
    }
}
