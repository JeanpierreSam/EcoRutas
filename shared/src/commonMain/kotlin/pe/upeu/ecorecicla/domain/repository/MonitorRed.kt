package pe.upeu.ecorecicla.domain.repository

import kotlinx.coroutines.flow.Flow

/** true mientras haya red. Android lo implementa con ConnectivityManager. */
interface MonitorRed {
    val conectado: Flow<Boolean>
}
