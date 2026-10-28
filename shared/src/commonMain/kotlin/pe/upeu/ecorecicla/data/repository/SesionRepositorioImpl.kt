package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.DatosLocales
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.SesionRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SesionRepositorioImpl(
    private val api: EcoApi,
    private val almacen: AlmacenSeguro,
    private val local: DatosLocales,
    private val reloj: () -> Long
) : SesionRepository {

    private val actual = MutableStateFlow<Sesion?>(null)
    override val sesion: StateFlow<Sesion?> = actual

    override suspend fun iniciar(usuario: String, contrasena: String): Sesion {
        val r = api.login(usuario, contrasena)
        almacen.guardarToken(r.token)
        return Sesion(r.token, r.usuario, r.rol, r.privilegios.toSet(), r.nombre).also { actual.value = it }
    }

    override fun restaurar(): Sesion? {
        val token = almacen.leerToken() ?: return null
        val datos = runCatching { leerToken(token) }.getOrNull()
        if (datos == null || datos.second * 1000 <= reloj()) {   // vencido o ilegible: al login
            almacen.borrarToken()
            return null
        }
        return datos.first.also { actual.value = it }
    }

    override fun cerrar() {
        almacen.borrarToken()
        local.limpiarTodo()
        actual.value = null
    }

    override fun expirar() {
        almacen.borrarToken()
        actual.value = null
    }
}

/**
 * Lee el cuerpo del JWT (sin verificar la firma: eso lo hace el servicio en cada peticion)
 * para recuperar usuario, rol, privilegios y el vencimiento «exp» en segundos.
 */
@OptIn(ExperimentalEncodingApi::class)
fun leerToken(token: String): Pair<Sesion, Long> {
    val cuerpo = token.split(".")[1]
    val json = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(cuerpo).decodeToString()
    val claims = Json.parseToJsonElement(json).jsonObject
    val usuario = claims.getValue("sub").jsonPrimitive.content
    val sesion = Sesion(
        token = token,
        usuario = usuario,
        rol = claims["rol"]?.jsonPrimitive?.content.orEmpty(),
        privilegios = claims["privilegios"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
        nombre = claims["nombre"]?.jsonPrimitive?.content ?: usuario
    )
    return sesion to claims.getValue("exp").jsonPrimitive.long
}
