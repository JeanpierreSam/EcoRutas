package pe.upeu.ecorecicla

import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.data.remote.dto.aPeticion
import pe.upeu.ecorecicla.data.repository.PesajeRepositorioOffline
import pe.upeu.ecorecicla.domain.usecase.validarPesaje
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SC-C: la humedad recorre DTO -> dominio -> base local y vuelve al servicio. */
class HumedadEstimadaTest {

    @Test
    fun dtoTraeLaHumedadAlDominioYLaPeticionLaEnvia() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<PesajeDto>(
            """{"id":1,"rutaId":2,"puntoId":7,"fechaHora":"2026-10-28T08:05","material":"METAL","pesoBruto":75.0,
            "tara":8.0,"pesoNeto":67.0,"operario":"operario1","humedadEstimada":0.5}"""
        )
        val pesaje = dto.aDominio()
        assertEquals(0.5, pesaje.humedadEstimada)

        val enviado = Json.encodeToJsonElement(PesajeRequestDto.serializer(), pesaje.aPeticion()).jsonObject
        assertEquals("0.5", enviado["humedadEstimada"].toString())
        assertFalse("pesoNeto" in enviado, "el neto nunca viaja al servicio")
    }

    @Test
    fun registroSinConexionGuardaLaHumedadEnLaBaseLocal() = runTest {
        val local = baseEnMemoria()
        val repo = PesajeRepositorioOffline(local, apiSimulada { throw IOException("modo avion") })
        val guardado = repo.registrar(pesajeNuevo().copy(humedadEstimada = 12.5))
        assertEquals(12.5, local.obtenerPesaje(guardado.id)?.humedadEstimada)
    }

    @Test
    fun humedadFueraDeRangoSeRechazaBajoSuCampo() {
        assertTrue("humedadEstimada" in validarPesaje(pesajeNuevo().copy(humedadEstimada = 120.0)))
        assertTrue("humedadEstimada" !in validarPesaje(pesajeNuevo().copy(humedadEstimada = 100.0)))
    }
}
