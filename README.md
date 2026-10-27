# MessOff para Android

App **nativa** de [MessOff](https://github.com/mkandreum/MessOff) en Kotlin + Jetpack Compose. Copia el diseño de la web: papel cálido, tinta, Instrument Serif/Sans, un único bermellón y los mismos iconos Lucide. Usa la misma API y la misma cuenta.

## Qué incluye

- **Acceso**: entrar y crear cuenta, con la dirección de tu servidor (el de Coolify) configurable en la propia pantalla.
- **Hoy**: estadísticas del día, la agenda como línea de tiempo, los huecos libres, la tarjeta oscura "Ahora mismo", las tareas pendientes y el ritmo semanal.
- **Calendario**: vistas día, semana y mes. La hora queda fija a la izquierda, se marcan la columna de hoy y la línea de "ahora". Mantén pulsado un evento para arrastrarlo: cada franja da un tic háptico y al soltar notas un "snap", con opción de deshacer. Toca un hueco para crear un evento y desliza para cambiar de día o de mes.
- **Detalle del evento**: título, fecha, hora, duración, mover a otro día, repetir (día siguiente, resto de la semana o 4 semanas), categoría, lugar, personas, subtareas y notas. Todo se guarda automáticamente. Puedes duplicar o eliminar el evento, con deshacer.
- **Nuevo evento** en lenguaje natural ("Café con Lucía mañana 09:30"), con el mismo analizador que la web.
- **Tareas**: desliza a la derecha para completar y a la izquierda para eliminar. Toca una tarea para editar su título, fecha y etiqueta. El botón de programar busca el primer hueco libre.
- **Enfoque**: temporizador de 25, 50 o 90 minutos con un anillo animado. Sigue corriendo en segundo plano y, al terminar, avisa con una notificación y una vibración de logro. Mientras corre, una píldora flotante lo muestra en las demás pantallas.
- **Búsqueda** de eventos, tareas, secciones y acciones.
- **Cuenta**: nombre, tema papel o noche, vibración (con patrones de prueba), contraseña, cerrar sesión en todos los dispositivos y eliminar la cuenta.

## Hápticas

En Android 11 o superior, si el motor de vibración lo permite, MessOff usa `VibrationEffect.Composition` con primitivas: clic, tic, golpe seco y subida. En móviles más antiguos usa formas de onda con amplitud.

| Gesto | Sensación |
| --- | --- |
| Navegar / seleccionar | tic |
| Empezar a arrastrar | clic medio |
| Cruzar una franja arrastrando | tic seco |
| Soltar en su sitio | golpe + tic |
| Completar | doble clic |
| Empezar el enfoque | latido |
| Foco completado / bandeja vacía | subida + clics |
| Eliminar | golpe + dos clics |

## Compilar

Necesitas Android Studio (JDK 17+) y el SDK 37.

```bash
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. Si no defines una keystore, se firma con la clave de depuración para que puedas instalarlo directamente. Para firmar con la tuya, define `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.

Para dejar fijado el servidor por defecto:

```bash
./gradlew assembleRelease -PapiBase=https://messoff.tu-dominio.com
```

## Cómo se conecta

La app usa la misma API que la web. Al iniciar sesión guarda el token de sesión (`pulso_session`) y lo envía como `Authorization: Bearer`, que el servidor ya acepta. Por eso no hace falta tocar nada del backend. Se permite `http` para servidores en la red local o el emulador (`http://10.0.2.2:3001`).

## Estructura

```
app/src/main/java/com/messoff/pulso/
  AppViewModel.kt     estado, acciones optimistas con deshacer, temporizador
  data/               modelos, cliente HTTP (OkHttp + kotlinx.serialization), sesión
  haptics/            motor háptico
  focus/              alarma y notificación de fin de enfoque
  util/               fechas en español y analizador de lenguaje natural
  ui/theme/           colores papel/noche y tipografía
  ui/icons/           iconos Lucide como vectores nativos
  ui/components/      panel, chips, nota, interruptor, logo…
  ui/screens/         acceso, hoy, calendario, tareas, enfoque, hojas
```

Fuentes Instrument Serif e Instrument Sans bajo licencia SIL OFL 1.1 (ver `OFL-*.txt`). Iconos de Lucide (ISC).
