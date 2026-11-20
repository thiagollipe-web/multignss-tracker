package com.thigas.multignss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Constellation(val label: String, val country: String) {
    GLONASS("GLONASS", "Rússia"),
    GALILEO("Galileo", "União Europeia"),
    BEIDOU("BeiDou", "China"),
    GPS("GPS", "EUA (referência)");

    companion object {
        fun from(type: Int): Constellation? = when (type) {
            GnssStatus.CONSTELLATION_GLONASS -> GLONASS
            GnssStatus.CONSTELLATION_GALILEO -> GALILEO
            GnssStatus.CONSTELLATION_BEIDOU -> BEIDOU
            GnssStatus.CONSTELLATION_GPS -> GPS
            else -> null // QZSS, SBAS, IRNSS: fora do escopo
        }
    }
}

data class Satellite(
    val constellation: Constellation,
    val svid: Int,
    val cn0DbHz: Float,
    val elevationDeg: Float,
    val azimuthDeg: Float,
    val carrierHz: Float?,
    val usedInFix: Boolean,
)

data class Fix(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMs: Float?,
    val bearingDeg: Float?,
    val accuracyM: Float?,
    val timeMs: Long,
)

/** Lê satélites e posição direto do chip GNSS (sem Play Services, sem internet). */
class GnssRepository(context: Context) {
    private val app = context.applicationContext
    private val lm = app.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _satellites = MutableStateFlow<List<Satellite>>(emptyList())
    val satellites: StateFlow<List<Satellite>> = _satellites.asStateFlow()
    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var running = false
    var onFix: ((Fix) -> Unit)? = null

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            _satellites.value = (0 until status.satelliteCount).mapNotNull { i ->
                val c = Constellation.from(status.getConstellationType(i)) ?: return@mapNotNull null
                Satellite(
                    constellation = c,
                    svid = status.getSvid(i),
                    cn0DbHz = status.getCn0DbHz(i),
                    elevationDeg = status.getElevationDegrees(i),
                    azimuthDeg = status.getAzimuthDegrees(i),
                    carrierHz = carrier(status, i),
                    usedInFix = status.usedInFix(i),
                )
            }
        }
        override fun onStopped() { _satellites.value = emptyList() }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            val f = l.toFix()
            _fix.value = f
            onFix?.invoke(f)
        }
        override fun onProviderDisabled(provider: String) { _error.value = "GPS desligado nas configurações" }
        override fun onProviderEnabled(provider: String) { _error.value = null }
        @Deprecated("API < 29") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    }

    fun hasPermission() = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checado em hasPermission()
    fun start() {
        if (running || !hasPermission()) return
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) _error.value = "GPS desligado nas configurações"
            lm.registerGnssStatusCallback(statusCallback, main)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_MS, 0f, locationListener, Looper.getMainLooper())
            running = true
        } catch (e: SecurityException) {
            _error.value = "Permissão de localização negada"
        } catch (e: IllegalArgumentException) {
            _error.value = "Este aparelho não tem GPS"
        }
    }

    fun stop() {
        if (!running) return
        lm.unregisterGnssStatusCallback(statusCallback)
        lm.removeUpdates(locationListener)
        running = false
    }

    private fun carrier(s: GnssStatus, i: Int): Float? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && s.hasCarrierFrequencyHz(i)) s.getCarrierFrequencyHz(i) else null

    private fun Location.toFix() = Fix(
        lat = latitude, lon = longitude,
        altitudeM = if (hasAltitude()) altitude else null,
        speedMs = if (hasSpeed()) speed else null,
        bearingDeg = if (hasBearing()) bearing else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        timeMs = time,
    )

    companion object {
        private const val UPDATE_MS = 1000L

        /** Nome da banda a partir da frequência da portadora. */
        fun band(hz: Float?): String {
            if (hz == null) return "—"
            val mhz = hz / 1e6
            return when {
                mhz in 1559.0..1563.0 -> "B1I"
                mhz in 1574.0..1577.0 -> "L1/E1/B1C"
                mhz in 1597.0..1607.0 -> "G1"
                mhz in 1242.0..1252.0 -> "G2"
                mhz in 1175.0..1178.0 -> "L5/E5a/B2a"
                mhz in 1206.0..1209.0 -> "E5b/B2I"
                mhz in 1267.0..1270.0 -> "B3I"
                else -> "%.2f MHz".format(mhz)
            }
        }
    }
}
