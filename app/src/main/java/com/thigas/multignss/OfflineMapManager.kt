package com.thigas.multignss

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.IOException
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicyException
import org.osmdroid.views.MapView

/**
 * Cache de tiles, arquivos offline (.mbtiles/.zip/.sqlite), pré-download e trilha gravada em disco.
 * Os arquivos em [baseDir] são lidos sozinhos pelo MapTileFileArchiveProvider padrão do osmdroid.
 */
class OfflineMapManager(context: Context) {
    private val app = context.applicationContext
    val baseDir: File = File(app.getExternalFilesDir(null) ?: app.filesDir, "osmdroid")
    private val trailFile = File(app.filesDir, "trail.csv")

    // Limpo = OSM padrão (sem chave), mas a política dele proíbe pré-download em massa.
    // Topo = OpenTopoMap, usado para baixar áreas offline.
    fun tileSource(topo: Boolean): ITileSource = if (topo) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK

    init {
        baseDir.mkdirs()
        Configuration.getInstance().apply {
            load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = app.packageName
            osmdroidBasePath = baseDir
            osmdroidTileCache = File(baseDir, "tiles")
        }
    }

    fun offlineArchives(): List<String> =
        baseDir.listFiles()?.filter { it.extension.lowercase() in ARCHIVE_EXT }?.map { it.name }.orEmpty()

    fun isOnline(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Baixa a área visível do zoom atual até +2, sem passar de [MAX_TILES]. */
    fun downloadVisible(map: MapView, onStatus: (String) -> Unit) {
        val box = map.boundingBox
        val zMin = map.zoomLevelDouble.toInt().coerceIn(1, MAX_ZOOM)
        val cm = try { CacheManager(map) } catch (e: TileSourcePolicyException) {
            onStatus("Esta fonte de mapa não permite download"); return
        }
        var zMax = (zMin + 2).coerceAtMost(MAX_ZOOM)
        while (zMax > zMin && cm.possibleTilesInArea(box, zMin, zMax) > MAX_TILES) zMax--
        val total = cm.possibleTilesInArea(box, zMin, zMax)
        if (total > MAX_TILES) { onStatus("Área grande demais ($total tiles). Aproxime o zoom."); return }

        try { cm.downloadAreaAsyncNoUI(app, box, zMin, zMax, object : CacheManager.CacheManagerCallback {
            override fun downloadStarted() = onStatus("Baixando $total tiles (zoom $zMin–$zMax)…")
            override fun setPossibleTilesInArea(total: Int) {}
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) =
                onStatus("Baixando: $progress/$total tiles")
            override fun onTaskComplete() = onStatus("Área salva: $total tiles disponíveis offline")
            override fun onTaskFailed(errors: Int) = onStatus("Download terminou com $errors erros (sem rede?)")
        }) } catch (e: TileSourcePolicyException) {
            onStatus("Este estilo de mapa não permite baixar área. Ligue o Estilo Topo.")
        }
    }

    // --- Trilha: cada ponto vai para o disco na hora (sobrevive ao app ser fechado) ---

    fun appendTrail(p: TrackPoint) {
        try {
            trailFile.appendText("${p.lat},${p.lon},${p.ele ?: ""},${p.timeMs ?: ""}\n")
        } catch (e: IOException) {
            // ponytail: falha de disco só perde o ponto; upgrade: avisar na UI se repetir
        }
    }

    fun loadTrail(): List<TrackPoint> =
        if (!trailFile.exists()) emptyList() else trailFile.readLines().mapNotNull(::parseLine)

    fun clearTrail() { trailFile.delete() }

    companion object {
        const val MAX_TILES = 2000
        const val MAX_ZOOM = 17 // teto do pré-download (zooms maiores multiplicam os tiles)
        private val ARCHIVE_EXT = setOf("mbtiles", "zip", "sqlite", "gemf")

        fun parseLine(line: String): TrackPoint? {
            val c = line.split(',')
            if (c.size < 4) return null
            val lat = c[0].toDoubleOrNull() ?: return null
            val lon = c[1].toDoubleOrNull() ?: return null
            return TrackPoint(lat, lon, c[2].toDoubleOrNull(), c[3].toLongOrNull())
        }
    }
}
