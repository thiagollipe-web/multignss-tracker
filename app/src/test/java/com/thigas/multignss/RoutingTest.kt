package com.thigas.multignss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingTest {
    // rota reta ~1,1 km para o norte, com uma manobra no meio
    private val osrm = """
    {"code":"Ok","routes":[{"distance":1112.0,"duration":120.0,
      "geometry":{"coordinates":[[0.0,0.0],[0.0,0.005],[0.0,0.01]]},
      "legs":[{"steps":[
        {"name":"Rua A","distance":556.0,"maneuver":{"type":"depart","location":[0.0,0.0]}},
        {"name":"Rua B","distance":556.0,"maneuver":{"type":"turn","modifier":"right","location":[0.0,0.005]}},
        {"name":"","distance":0.0,"maneuver":{"type":"arrive","location":[0.0,0.01]}}]}]}]}
    """

    @Test fun `le rota do OSRM com instrucoes em portugues`() {
        val r = Routing.parseOsrm(osrm)
        assertEquals(3, r.points.size)
        assertEquals(LatLon(0.005, 0.0), r.steps[1].at)
        assertEquals("Siga pela Rua A", r.steps[0].text)
        assertEquals("Vire à direita na Rua B", r.steps[1].text)
        assertEquals(Turn.RIGHT, r.steps[1].turn)
        assertEquals(Turn.ARRIVE, r.steps[2].turn)
        assertEquals("Você chegou ao destino", r.steps[2].text)
    }

    @Test(expected = IllegalStateException::class) fun `sem rota da erro claro`() {
        Routing.parseOsrm("""{"code":"NoRoute","message":"Impossible route"}""")
    }

    @Test fun `rotatoria com saida`() {
        assertEquals("Na rotatória, pegue a 2ª saída na Av. X", Routing.instruction("roundabout", "", "Av. X", 2))
    }

    @Test fun `nominatim ignora item sem coordenada`() {
        val p = Routing.parseNominatim("""[{"display_name":"Praça","lat":"-23.5","lon":"-46.6"},{"display_name":"x"}]""")
        assertEquals(listOf(Place("Praça", LatLon(-23.5, -46.6))), p)
    }

    @Test fun `navegador avanca passo, detecta saida da rota e chegada`() {
        val r = Routing.parseOsrm(osrm)
        val start = Navigator.update(r, LatLon(0.0, 0.0), 0)
        assertEquals(0, start.stepIndex)
        assertEquals(1112.0, start.remainingM, 15.0)
        assertFalse(start.offRoute)

        val atTurn = Navigator.update(r, LatLon(0.00501, 0.0), 0)
        assertEquals(1, atTurn.stepIndex)

        assertTrue(Navigator.update(r, LatLon(0.005, 0.002), 1).offRoute) // ~220 m ao lado
        assertTrue(Navigator.update(r, LatLon(0.0099, 0.0), 1).arrived)
    }

    @Test(expected = IllegalStateException::class) fun `rota vazia vira erro e nao crash`() {
        Routing.parseOsrm("""{"code":"Ok","routes":[{"distance":0,"duration":0,"geometry":{"coordinates":[]},"legs":[]}]}""")
    }

    @Test fun `em alta velocidade nao trava na manobra pulada`() {
        val r = Routing.parseOsrm(osrm)
        // 40 m depois da curva: fora do raio fixo de 25 m
        assertEquals(0, Navigator.update(r, LatLon(0.00536, 0.0), 0).stepIndex)
        assertEquals(1, Navigator.update(r, LatLon(0.00536, 0.0), 0, speedMs = 30.0).stepIndex)
    }

    @Test fun `distancia ate a linha`() {
        val d = Geo.distToLine(LatLon(0.005, 0.001), listOf(LatLon(0.0, 0.0), LatLon(0.01, 0.0)))
        assertEquals(111.0, d, 2.0)
    }

    @Test fun `formatos`() {
        assertEquals("1,5 km", Navigator.formatDist(1500.0))
        assertEquals("120 m", Navigator.formatDist(123.0))
        assertEquals("1 h 5 min", Navigator.formatTime(3900.0))
    }
}
