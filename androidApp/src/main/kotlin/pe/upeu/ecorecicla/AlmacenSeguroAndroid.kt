package pe.upeu.ecorecicla

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import pe.upeu.ecorecicla.data.local.AlmacenSeguro

/**
 * Token en preferencias cifradas: la llave maestra AES-256 vive en el Android Keystore
 * y nunca sale del hardware, asi que el archivo copiado del equipo no sirve sin ella.
 */
@Suppress("DEPRECATION") // security-crypto esta deprecado, pero es el almacen pedido por el curso
class AlmacenSeguroAndroid(contexto: Context) : AlmacenSeguro {

    private val llave = MasterKey.Builder(contexto)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        contexto, ARCHIVO, llave,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // commit() y no apply(): si la app muere justo despues, el cambio ya quedo escrito.
    override fun guardarToken(token: String) {
        prefs.edit().putString(CLAVE, token).commit()
    }

    override fun leerToken(): String? = prefs.getString(CLAVE, null)

    override fun borrarToken() {
        prefs.edit().remove(CLAVE).commit()
    }

    private companion object {
        const val ARCHIVO = "sesion_segura"
        const val CLAVE = "jwt"
    }
}
