package pe.upeu.ecorecicla

import pe.upeu.ecorecicla.data.repository.leerToken
import pe.upeu.ecorecicla.domain.model.Privilegio
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SC-B: la app trata al AUDITOR solo por sus privilegios; ningun codigo pregunta por el nombre del rol. */
class RolAuditorTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun token(rol: String, privilegios: List<String>): String {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val cuerpo = """{"sub":"x","rol":"$rol","privilegios":[${privilegios.joinToString { "\"$it\"" }}],"exp":9999999999}"""
        return "${b64.encode("{}".encodeToByteArray())}.${b64.encode(cuerpo.encodeToByteArray())}.firma"
    }

    @Test
    fun auditorQuedaEnSoloLecturaYSinAcciones() {
        val (sesion, _) = leerToken(token("AUDITOR", listOf("PUNTO_LEER", "PESAJE_LEER", "RUTA_LEER_TODAS")))
        assertEquals("AUDITOR", sesion.rol)
        assertTrue(sesion.soloLectura)
        assertTrue(Privilegio.ESCRITURA.none(sesion::puede))
    }

    @Test
    fun operarioNoEsSoloLectura() {
        val (sesion, _) = leerToken(token("OPERARIO", listOf("PUNTO_LEER", "PESAJE_LEER", "PESAJE_CREAR", "PESAJE_EDITAR")))
        assertFalse(sesion.soloLectura)
    }

    @Test
    fun cualquierRolNuevoSinEscrituraTambienEsSoloLectura() {
        assertTrue(leerToken(token("CONSULTOR", listOf("PUNTO_LEER"))).first.soloLectura)
    }
}
