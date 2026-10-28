package pe.upeu.ecorecicla.domain.model

data class Sesion(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
    val nombre: String = usuario
) {
    fun puede(privilegio: String) = privilegio in privilegios

    /** SC-B: sin ningun privilegio de escritura (p. ej. AUDITOR). Se deduce de los privilegios, nunca del rol. */
    val soloLectura: Boolean get() = privilegios.none { it in Privilegio.ESCRITURA }
}

/** Privilegios que la interfaz consulta para ofrecer acciones. El servicio los vuelve a verificar. */
object Privilegio {
    const val PESAJE_CREAR = "PESAJE_CREAR"
    const val PESAJE_EDITAR = "PESAJE_EDITAR"
    const val PESAJE_EDITAR_TODOS = "PESAJE_EDITAR_TODOS"
    const val PESAJE_ANULAR = "PESAJE_ANULAR"
    const val RUTA_CERRAR = "RUTA_CERRAR"
    const val PUNTO_GESTIONAR = "PUNTO_GESTIONAR"

    val ESCRITURA = setOf(PESAJE_CREAR, PESAJE_EDITAR, PESAJE_EDITAR_TODOS, PESAJE_ANULAR, RUTA_CERRAR, PUNTO_GESTIONAR)
}
