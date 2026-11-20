package com.thigas.multignss

import android.location.GnssStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreTest {
    @Test fun `classifica so as constelacoes do escopo`() {
        assertEquals(Constellation.GLONASS, Constellation.from(GnssStatus.CONSTELLATION_GLONASS))
        assertEquals(Constellation.GALILEO, Constellation.from(GnssStatus.CONSTELLATION_GALILEO))
        assertEquals(Constellation.BEIDOU, Constellation.from(GnssStatus.CONSTELLATION_BEIDOU))
        assertEquals(Constellation.GPS, Constellation.from(GnssStatus.CONSTELLATION_GPS))
        assertNull(Constellation.from(GnssStatus.CONSTELLATION_QZSS))
    }

    @Test fun `banda pela frequencia`() {
        assertEquals("L1/E1/B1C", GnssRepository.band(1575.42e6f))
        assertEquals("G1", GnssRepository.band(1602.5625e6f))
        assertEquals("B1I", GnssRepository.band(1561.098e6f))
        assertEquals("L5/E5a/B2a", GnssRepository.band(1176.45e6f))
        assertEquals("—", GnssRepository.band(null))
    }

    @Test fun `gpx ida e volta`() {
        val pts = listOf(TrackPoint(-23.55, -46.63, 760.5, 1_700_000_000_000), TrackPoint(-23.56, -46.64, null, null))
        val back = Gpx.read(Gpx.write("a <b> & c", pts).byteInputStream())
        assertEquals(pts, back)
    }

    @Test fun `gpx ignora coordenada invalida e le rtept`() {
        val xml = """<gpx><rte><rtept lat="10" lon="20"/><rtept lat="999" lon="0"/><rtept lat="x" lon="1"/></rte></gpx>"""
        assertEquals(listOf(TrackPoint(10.0, 20.0, null, null)), Gpx.read(xml.byteInputStream()))
    }

    @Test(expected = Exception::class) fun `gpx com doctype e recusado`() {
        Gpx.read("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><gpx>&e;</gpx>""".byteInputStream())
    }

    @Test fun `skyplot zenite no centro e norte no topo`() {
        val (x0, y0) = skyplotXY(90f, 0f, 100f)
        assertEquals(0f, x0, 0.01f); assertEquals(0f, y0, 0.01f)
        val (x1, y1) = skyplotXY(0f, 0f, 100f)
        assertEquals(0f, x1, 0.01f); assertEquals(-100f, y1, 0.01f)
        val (x2, _) = skyplotXY(0f, 90f, 100f)
        assertEquals(100f, x2, 0.01f)
    }

    @Test fun `linha da trilha em disco`() {
        assertEquals(TrackPoint(1.0, 2.0, null, 5), OfflineMapManager.parseLine("1.0,2.0,,5"))
        assertNull(OfflineMapManager.parseLine("lixo"))
    }
}
