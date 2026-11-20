package com.thigas.multignss

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Destino, rota, navegação passo a passo com voz e recálculo ao sair da rota. */
class RouteGuide(context: Context, private val scope: CoroutineScope, private val online: () -> Boolean) {
    private val app = context.applicationContext
    private val ua = "${app.packageName}/0.2 (Android)"

    private val _destination = MutableStateFlow<Place?>(null)
    val destination: StateFlow<Place?> = _destination.asStateFlow()
    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()
    private val _nav = MutableStateFlow<NavState?>(null)
    val nav: StateFlow<NavState?> = _nav.asStateFlow()
    private val _navigating = MutableStateFlow(false)
    val navigating: StateFlow<Boolean> = _navigating.asStateFlow()
    private val _results = MutableStateFlow<List<Place>>(emptyList())
    val results: StateFlow<List<Place>> = _results.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _mode = MutableStateFlow(TravelMode.CAR)
    val mode: StateFlow<TravelMode> = _mode.asStateFlow()

    private val _limit = MutableStateFlow<Int?>(null)
    val limit: StateFlow<Int?> = _limit.asStateFlow()
    private val _camera = MutableStateFlow<Pair<Camera, Double>?>(null)
    val camera: StateFlow<Pair<Camera, Double>?> = _camera.asStateFlow()
    private var road: RoadData? = null
    private var roadFor: Place? = null
    private val passed = mutableSetOf<Camera>()
    private var warnedCamera: Camera? = null

    private val _arrived = MutableStateFlow(false)
    val arrived: StateFlow<Boolean> = _arrived.asStateFlow()
    fun dismissArrived() { _arrived.value = false }

    private var here: LatLon? = null
    private var lastReroute = 0L
    private var spokenStep = -1
    private var warnedStep = -1
    private var routeJob: Job? = null

    private var ttsReady = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(app) { ok ->
            ttsReady = ok == TextToSpeech.SUCCESS
            if (ttsReady) tts.language = Locale("pt", "BR")
        }
    }

    fun setMode(m: TravelMode) {
        _mode.value = m
        _destination.value?.let { setDestination(it) }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        if (!online()) { _status.value = "Sem internet para buscar endereço. Segure o dedo no mapa."; return }
        _status.value = "Buscando…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Routing.search(query, here, ua) } }
            r.onSuccess { _results.value = it; _status.value = if (it.isEmpty()) "Nada encontrado" else "" }
                .onFailure { _status.value = "Erro na busca: ${it.message}" }
        }
    }

    fun setDestination(p: Place) {
        _results.value = emptyList(); _arrived.value = false
        _destination.value = p
        computeRoute()
    }

    fun computeRoute() {
        val from = here ?: run { _status.value = "Esperando sua posição GNSS…"; return }
        val to = _destination.value?.at ?: return
        if (!online()) { _status.value = "Sem internet: não dá para calcular a rota agora"; return }
        routeJob?.cancel() // destino/modo novo vence o pedido antigo
        _status.value = "Calculando rota…"
        routeJob = scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Routing.route(from, to, _mode.value, ua) } }
            r.onSuccess {
                _route.value = it
                spokenStep = -1; warnedStep = -1
                _nav.value = Navigator.update(it, from, 0)
                _status.value = "${Navigator.formatDist(it.distanceM)} · ${Navigator.formatTime(it.durationS)}"
                loadRoad(it)
            }.onFailure { _status.value = "Erro na rota: ${it.message}" }
        }
    }

    /** Radares e limites: uma consulta por destino (recálculo reaproveita). */
    private fun loadRoad(r: Route) {
        val d = _destination.value
        if (road != null && roadFor == d) return
        road = null; roadFor = d; passed.clear()
        if (r.distanceM > RoadInfo.MAX_ROUTE_M) { _status.value += " · rota longa: sem radares"; return }
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { RoadInfo.fetch(r, ua) } }
            if (roadFor != _destination.value) return@launch // destino mudou durante a busca
            res.onSuccess {
                road = it
                _status.value += " · ${it.cameras.size} radares"
            }.onFailure { _status.value += " · radares indisponíveis" }
        }
    }

    fun start() {
        if (_route.value == null) return
        _navigating.value = true
        spokenStep = -1
        _nav.value?.let { announce(it) }
    }

    fun stop() { _navigating.value = false; tts.stop() }

    fun clear() {
        stop()
        _route.value = null; _nav.value = null; _destination.value = null; _status.value = ""
        road = null; roadFor = null; _limit.value = null; _camera.value = null; _arrived.value = false
    }

    fun onFix(f: Fix) {
        val p = LatLon(f.lat, f.lon)
        here = p
        var spokeCamera = false
        road?.let { rd ->
            _limit.value = RoadInfo.limitAt(p, rd.ways)
            val cam = RoadInfo.nextCamera(p, rd.cameras, passed, CAMERA_RANGE_M)
            if (cam != null && cam.second < CAMERA_PASSED_M) passed += cam.first
            _camera.value = cam?.takeIf { it.second >= CAMERA_PASSED_M }
            val c = _camera.value
            if (_navigating.value && c != null && warnedCamera != c.first) {
                warnedCamera = c.first
                speak("Radar em ${Navigator.formatDist(c.second)}" + (c.first.limitKmh?.let { ", limite $it" } ?: ""))
                spokeCamera = true
            }
        }
        val r = _route.value ?: return
        val s = Navigator.update(r, p, _nav.value?.stepIndex ?: 0, (f.accuracyM ?: 0f).toDouble(), (f.speedMs ?: 0f).toDouble())
        _nav.value = s
        if (!_navigating.value) return
        if (s.arrived) { speak("Você chegou ao destino"); _arrived.value = true; stop(); return }
        if (s.offRoute) {
            val now = System.currentTimeMillis()
            if (now - lastReroute > REROUTE_MS && online()) {
                lastReroute = now
                speak("Recalculando rota")
                computeRoute()
            }
            return
        }
        if (!spokeCamera) announce(s)
    }

    private fun announce(s: NavState) {
        val r = _route.value ?: return
        val next = r.steps.getOrNull(s.stepIndex + 1) ?: return
        if (spokenStep != s.stepIndex) {
            spokenStep = s.stepIndex
            speak("Em ${Navigator.formatDist(s.toNextM)}, ${next.text.lowercase()}")
        } else if (s.toNextM < WARN_M && warnedStep != s.stepIndex + 1) {
            warnedStep = s.stepIndex + 1
            speak(next.text)
        }
    }

    private fun speak(t: String) {
        if (ttsReady) tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    fun shutdown() { tts.shutdown() }

    companion object {
        private const val REROUTE_MS = 15_000L
        private const val WARN_M = 60.0
        private const val CAMERA_RANGE_M = 400.0
        private const val CAMERA_PASSED_M = 30.0
    }
}
