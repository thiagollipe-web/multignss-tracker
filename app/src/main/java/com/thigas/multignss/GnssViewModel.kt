package com.thigas.multignss

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.views.MapView

class GnssViewModel(app: Application) : AndroidViewModel(app) {
    private val gnss = GnssRepository(app)
    val maps = OfflineMapManager(app)

    val fix: StateFlow<Fix?> = gnss.fix
    val error: StateFlow<String?> = gnss.error

    private val _filter = MutableStateFlow(Constellation.entries.toSet())
    val filter: StateFlow<Set<Constellation>> = _filter.asStateFlow()

    val allSatellites: StateFlow<List<Satellite>> = gnss.satellites
    val satellites: StateFlow<List<Satellite>> = combine(gnss.satellites, _filter) { s, f ->
        s.filter { it.constellation in f }.sortedWith(compareBy({ it.constellation }, { -it.cn0DbHz }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _topo = MutableStateFlow(false)
    val topo: StateFlow<Boolean> = _topo.asStateFlow()
    fun setTopo(on: Boolean) { _topo.value = on }

    private val _guideSat = MutableStateFlow(Constellation.GLONASS)
    val guideSat: StateFlow<Constellation> = _guideSat.asStateFlow()
    fun setGuideSat(c: Constellation) { _guideSat.value = c }

    private val _trail = MutableStateFlow(maps.loadTrail())
    val trail: StateFlow<List<TrackPoint>> = _trail.asStateFlow()
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private val _imported = MutableStateFlow<List<TrackPoint>>(emptyList())
    val imported: StateFlow<List<TrackPoint>> = _imported.asStateFlow()

    private val _offlineOnly = MutableStateFlow(false)
    val offlineOnly: StateFlow<Boolean> = _offlineOnly.asStateFlow()
    private val _mapStatus = MutableStateFlow("")
    val mapStatus: StateFlow<String> = _mapStatus.asStateFlow()
    private val _online = MutableStateFlow(maps.isOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    // uma escrita por vez, na ordem: pontos da trilha não se embaralham e o "Limpar" não corre junto
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val diskQueue = Dispatchers.IO.limitedParallelism(1)

    val guide = RouteGuide(app, viewModelScope) { maps.isOnline() }

    init {
        gnss.onFix = { f ->
            guide.onFix(f)
            if (_recording.value) {
                val p = TrackPoint(f.lat, f.lon, f.altitudeM, f.timeMs)
                _trail.value = _trail.value + p
                viewModelScope.launch(diskQueue) { maps.appendTrail(p) }
            }
        }
    }

    fun hasPermission() = gnss.hasPermission()
    // ponytail: GNSS para no ON_STOP (tela apagada = trilha pausa); upgrade: foreground service de localização
    fun startGnss() { gnss.start(); refreshOnline() }
    fun stopGnss() = gnss.stop()

    fun toggle(c: Constellation) {
        _filter.value = _filter.value.let { if (c in it) it - c else it + c }
    }

    fun setRecording(on: Boolean) { _recording.value = on }

    fun clearTrail() {
        _recording.value = false
        _trail.value = emptyList()
        viewModelScope.launch(diskQueue) { maps.clearTrail() }
    }

    fun setOfflineOnly(on: Boolean) { _offlineOnly.value = on }
    fun refreshOnline() { _online.value = maps.isOnline() }

    fun downloadVisible(map: MapView) {
        refreshOnline()
        if (!_online.value) { _mapStatus.value = "Sem internet: conecte para baixar a área"; return }
        if (_offlineOnly.value) { _mapStatus.value = "Desligue o \"Somente offline\" para baixar"; return }
        if (!_topo.value) { _mapStatus.value = "O mapa limpo (OSM) não permite baixar área: ligue o Estilo Topo"; return }
        maps.downloadVisible(map) { _mapStatus.value = it }
    }

    fun exportGpx(uri: Uri) = io("Trilha exportada (${_trail.value.size} pontos)") {
        val text = Gpx.write("MultiGNSS ${System.currentTimeMillis()}", _trail.value)
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            ?: error("não abriu o arquivo")
    }

    fun importGpx(uri: Uri) = io("Trilha importada") {
        val pts = getApplication<Application>().contentResolver.openInputStream(uri)?.use(Gpx::read)
            ?: error("não abriu o arquivo")
        if (pts.isEmpty()) error("GPX sem pontos")
        _imported.value = pts
    }

    private fun io(ok: String, block: () -> Unit) = viewModelScope.launch {
        _mapStatus.value = withContext(Dispatchers.IO) {
            runCatching(block).fold({ ok }, { "Erro: ${it.message ?: "arquivo inválido"}" })
        }
    }

    override fun onCleared() { gnss.stop(); guide.shutdown() }
}
