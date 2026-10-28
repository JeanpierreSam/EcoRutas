# EcoRutas — app Android de pesajes en ruta (EcoRecicla)

Aplicación Kotlin Multiplatform (objetivo Android) que usa el operario durante el recorrido. Consume **EcoAPI**
(repositorio aparte) y funciona sin conexión: la interfaz siempre lee de la base local y la red solo la actualiza.

Kotlin 2.4 · Compose Multiplatform 1.11 · Ktor 3.6 · SQLDelight 2.4 · Koin 4.2 · security-crypto.

## Ejecución

1. Levantar el backend: en `EcoAPI/` ejecutar `docker compose up --build` (API en `http://localhost:8080`).
2. Abrir este proyecto en Android Studio y ejecutar `androidApp` en un emulador. La app llama a `http://10.0.2.2:8080/api/v1/`
   (alias del PC anfitrión; ver `ApiConfig.kt` y `res/xml/network_security_config.xml`).
3. Usuarios: `operario1`, `supervisor1`, `admin` — contraseña `Eco2026*`.

Línea de comandos: `./gradlew :androidApp:assembleDebug` y pruebas con `./gradlew :shared:testAndroidHostTest`.

## Estructura de paquetes

```
shared/src/commonMain/kotlin/pe/upeu/ecorecicla/
├── domain/
│   ├── model/        PuntoAcopio, EstadoPesaje, Pesaje, Sesion (+ Privilegio), Ruta, ErrorApi
│   ├── repository/   SesionRepository, RutaRepository, PesajeRepository, MonitorRed (solo contratos)
│   └── usecase/      Sesion, Ruta y Pesaje UseCases, SincronizarUseCase
├── data/
│   ├── remote/       ClienteHttp, ApiConfig, MapeoErrores (EjecutorApi), EcoApi
│   │   └── dto/      DTO, PaginaDto, ErrorDto y funciones aDominio()
│   ├── local/        AlmacenSeguro (interfaz), BaseLocal (driver + DatosLocales)
│   └── repository/   SesionRepositorioImpl, RutaRepositorioOffline, PesajeRepositorioOffline
├── presentation/     sesion/, listado/, detalle/, perfil/, navigation/, theme/ y Mensajes.kt
└── di/               redModule, dataModule, domainModule, presentationModule (platformModule en androidApp)
shared/src/commonMain/sqldelight/…/db/   Ruta.sq, Punto.sq, Pesaje.sq, Sincronizacion.sq
androidApp/src/main/kotlin/pe/upeu/ecorecicla/
├── AlmacenSeguroAndroid.kt     EncryptedSharedPreferences + MasterKey en el Android Keystore
├── BaseLocalAndroid.kt         AndroidSqliteDriver
├── PlatformModule.android.kt   motor OkHttp, URL base, monitor de red (ConnectivityManager)
└── MainActivity.kt             MainApplication (Koin) y MainActivity
```

## Contrato del API consumido

| Uso en la app | Endpoint |
|---|---|
| Inicio de sesión | `POST auth/login` |
| Ruta del día (sincronización) | `GET rutas/del-dia` |
| Pesajes de cada punto (sincronización) | `GET puntos/{id}/pesajes?pagina=0&tamanio=50` (envoltorio `PaginaDto`) |
| Registrar pesaje | `POST pesajes` → 201 |
| Corregir pesaje | `PUT pesajes/{id}` → 200 |
| Anular pesaje | `DELETE pesajes/{id}?motivo=` → 204 (solo se comprueba el código) |
| Cerrar ruta | `POST rutas/{id}/cierre` |

El detalle completo (códigos y formato de error) está en el README de EcoAPI.

## Roles y privilegios en la interfaz

La sesión trae los privilegios del token. La interfaz solo **ofrece** lo que el usuario puede hacer, pero quien decide es el servicio:

| Acción | Se muestra si | Lo verifica el servicio con |
|---|---|---|
| «Registrar pesaje» | PESAJE_CREAR y ruta abierta | PESAJE_CREAR (403) |
| «Corregir» | PESAJE_EDITAR_TODOS, o PESAJE_EDITAR y el pesaje es propio | PESAJE_EDITAR + RN-02 (403) |
| «Anular» | PESAJE_ANULAR | PESAJE_ANULAR (403) |
| «Cerrar ruta» | RUTA_CERRAR | RUTA_CERRAR (403) + RN-04 (409) |

## Decisiones de arquitectura

- **Capas**: `domain` no conoce Ktor, SQLDelight ni `@Serializable`; `data` implementa los repositorios; `presentation` usa ViewModel + `StateFlow` de un `UiState` por pantalla. Koin inyecta todo.
- **Cliente HTTP único** (`crearClienteHttp`, `single` en `redModule`): negociación JSON tolerante a claves desconocidas, URL base centralizada en `ApiConfig`, tiempos de espera y log de cabeceras solo en depuración (el token se oculta).
- **Cabecera de autorización centralizada**: el plugin `TokenJwt` de `ClienteHttp.kt` agrega `Bearer <token>` a cada petición leyendo el almacén seguro; ninguna función la agrega a mano.
- **Errores tipados**: `MapeoErrores.kt` traduce cada falla a `ErrorApi` (Validacion, NoAutenticado, SinPrivilegio, NoEncontrado, Conflicto, Servidor, SinConexion, TiempoAgotado). Conducta: 400 → mensaje bajo el campo; 401 → se borra el token y se vuelve al login; 403 → aviso, la sesión sigue; 404 → aviso y se refresca; 409 → motivo del servidor; 500/sin red → aviso con «Reintentar».
- **Sesión**: el token se guarda en `EncryptedSharedPreferences`; al abrir la app se lee su `exp` y, si sigue vigente, se entra directo. Cerrar sesión borra el token y la base local (equipo limpio para el siguiente turno; si hay pendientes se pide confirmación).
- **Offline-first**: la pantalla principal observa `listarPuntos` (Flow de SQLDelight). Un pesaje sin red se guarda con `pendiente = 1` e id provisional negativo (`siguienteIdLocal`: `MIN(0, COALESCE(MIN(id), 0)) - 1`, válido con tabla vacía); el servicio solo emite ids positivos.
- **Sincronización** (`SincronizarUseCase`): primero `enviarPendientes()` y después `descargar()`. La fila provisional se reemplaza por la definitiva en una transacción (`confirmarEnvio`), así no se duplica ni se pierde. Se dispara a mano («Sincronizar»), al iniciar sesión y al volver la conectividad (`MonitorRed`), con barra de progreso y resumen «Enviados / Recibidos».

## Pruebas (commonTest)

`./gradlew :shared:testAndroidHostTest` — motor HTTP simulado (`MockEngine`) y SQLite en memoria (`JdbcSqliteDriver`):

1. `conversionDeDtoAlDominio` — envoltorio de paginación, clave desconocida y anulado → `EstadoPesaje.Anulado`.
2. `traduccionDeErroresHttp` — 400 con detalle por campo, 403 mantiene la sesión, 401 la expira.
3. `registroSinConexionQuedaPendiente` — sin red se guarda con ids -1, -2 y marca de pendiente.
4. `sincronizacionSubeAntesDeBajar` — el POST va antes del GET y la fila -1 queda como id 100, sin duplicados.
5. `siguienteIdLocalConTablaVacia` — el primer id provisional es -1.
