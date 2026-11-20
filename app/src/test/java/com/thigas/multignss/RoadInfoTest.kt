package com.thigas.multignss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.junit.Test

class RoadInfoTest {
    private val overpass = """{"elements":[
      {"type":"node","id":1,"lat":0.003,"lon":0.0,"tags":{"highway":"speed_camera","maxspeed":"60"}},
      {"type":"node","id":2,"lat":0.009,"lon":0.0,"tags":{"highway":"speed_camera"}},
      {"type":"way","id":3,"tags":{"maxspeed":"50"},"geometry":[{"lat":0.0,"lon":0.0},{"lat":0.01,"lon":0.0}]},
      {"type":"way","id":4,"tags":{"maxspeed":"BR:urban"},"geometry":[{"lat":0.0,"lon":0.0},{"lat":0.0,"lon":0.01}]}]}"""

    @Test fun `le radares e vias com limite numerico`() {
        val d = RoadInfo.parse(overpass)
        assertEquals(2, d.cameras.size)
        assertEquals(60, d.cameras[0].limitKmh)
        assertNull(d.cameras[1].limitKmh)
        assertEquals(1, d.ways.size) // BR:urban sem número fica de fora
    }

    @Test fun `limite em kmh e mph`() {
        assertEquals(60, RoadInfo.parseMaxspeed("60"))
        assertEquals(64, RoadInfo.parseMaxspeed("40 mph"))
        assertNull(RoadInfo.parseMaxspeed("none"))
        assertNull(RoadInfo.parseMaxspeed(""))
    }

    @Test fun `limite da via mais proxima`() {
        val d = RoadInfo.parse(overpass)
        assertEquals(50, RoadInfo.limitAt(LatLon(0.005, 0.0001), d.ways)) // ~11 m da via
        assertNull(RoadInfo.limitAt(LatLon(0.005, 0.01), d.ways)) // longe
    }

    @Test fun `proximo radar ignora os ja passados e os longe`() {
        val d = RoadInfo.parse(overpass)
        val (c, dist) = RoadInfo.nextCamera(LatLon(0.0, 0.0), d.cameras, emptySet(), 400.0)!!
        assertEquals(60, c.limitKmh)
        assertEquals(333.0, dist, 5.0)
        assertNull(RoadInfo.nextCamera(LatLon(0.0, 0.0), d.cameras, setOf(c), 400.0)) // o outro está a 1 km
    }

    @Test fun `consulta amostra a rota e inclui o fim`() {
        val pts = (0..500).map { LatLon(it * 0.0001, 0.0) }
        val q = RoadInfo.query(pts)
        assertTrue(q.contains("0.05000,0.00000")) // último ponto
        assertTrue(q.split(",").size / 2 <= 2 * 130) // a rota aparece 2x: radares e vias
        assertTrue(q.contains("speed_camera") && q.contains("maxspeed"))
    }

    @Test fun `faixas do OSRM`() {
        val l = Routing.lanes(JSONArray("""[{"valid":false,"indications":["left"]},{"valid":true,"indications":["none"]},{"valid":true,"indications":["straight","right"]}]"""))
        assertEquals(listOf(Lane(false, Turn.LEFT), Lane(true, Turn.STRAIGHT), Lane(true, Turn.STRAIGHT)), l)
        assertTrue(Routing.lanes(null).isEmpty())
    }

    @Test fun `noite das 18h as 6h`() {
        assertTrue(isNight(18)); assertTrue(isNight(2)); assertFalse(isNight(6)); assertFalse(isNight(12))
    }
}
