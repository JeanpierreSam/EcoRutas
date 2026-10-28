package pe.upeu.ecorecicla.data.remote

/** Direccion base y tiempos de espera en un solo lugar. */
object ApiConfig {
    /** El emulador ve al PC anfitrion como 10.0.2.2; EcoAPI escucha en el 8080. */
    const val URL_BASE_EMULADOR = "http://10.0.2.2:8080/api/v1/"
    const val TIEMPO_CONEXION_MS = 8_000L
    const val TIEMPO_PETICION_MS = 15_000L
    const val TAMANIO_PAGINA = 50
}
