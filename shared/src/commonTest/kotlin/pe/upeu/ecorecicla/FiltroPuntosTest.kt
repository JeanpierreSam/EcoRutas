package pe.upeu.ecorecicla

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.upeu.ecorecicla.domain.model.FiltroPuntos
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import kotlin.test.Test
import kotlin.test.assertEquals

/** SC-A: el filtro se resuelve en la consulta local, sin red (no hay motor HTTP en estas pruebas). */
class FiltroPuntosTest {

    private fun punto(id: Long, nombre: String, zona: String, secuencia: Int) =
        PuntoAcopio(id, nombre, "Av. $nombre", zona, "CARTON", secuencia, atendido = false)

    private fun baseConRuta() = baseEnMemoria().also {
        it.reemplazar(
            ruta = Ruta(2, "2026-10-28", cerrada = false),
            puntos = listOf(punto(7, "Ferreteria", "CENTRO", 1), punto(10, "Restaurante", "CENTRO", 2), punto(11, "Colegio", "SUR", 3)),
            omitidos = emptySet(),
            pesajes = listOf(pesajeNuevo(puntoId = 7).copy(id = 50, pendiente = false)),
            instante = 1L
        )
    }

    @Test
    fun soloPendientesExcluyeLosPuntosPesados() = runTest {
        val local = baseConRuta()
        assertEquals(listOf(10L, 11L), local.observarPuntos(FiltroPuntos(soloPendientes = true)).first().map { it.id })
    }

    @Test
    fun pesajeSinConexionSacaAlPuntoDeLosPendientes() = runTest {
        val local = baseConRuta()
        local.guardarPendiente(pesajeNuevo(puntoId = 10))           // registrado en modo avion
        assertEquals(listOf(11L), local.observarPuntos(FiltroPuntos(soloPendientes = true)).first().map { it.id })
    }

    @Test
    fun filtroSeCombinaConElBuscador() = runTest {
        val local = baseConRuta()
        assertEquals(listOf(7L, 10L), local.observarPuntos(FiltroPuntos(texto = "centro")).first().map { it.id })
        assertEquals(listOf(10L), local.observarPuntos(FiltroPuntos("centro", soloPendientes = true)).first().map { it.id })
        assertEquals(3, local.observarPuntos().first().size)
    }
}
