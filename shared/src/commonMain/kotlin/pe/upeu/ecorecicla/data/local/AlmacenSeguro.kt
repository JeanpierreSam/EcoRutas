package pe.upeu.ecorecicla.data.local

/** Donde vive el token. En Android lo implementa AlmacenSeguroAndroid (preferencias cifradas + Keystore). */
interface AlmacenSeguro {
    fun guardarToken(token: String)
    fun leerToken(): String?
    fun borrarToken()
}
