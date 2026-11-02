# Rawapp

Rawapp es una cámara RAW personal para el **Google Pixel 10a**. Captura DNG directamente del sensor con Camera2 y (a partir de la Fase 2) los revela en GPU con un look cinematográfico propio. Funciona en local: sin red, sin cuentas y sin analítica.

## Estado

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Esqueleto, permiso, preview, captura RAW → DNG en `Pictures/Rawapp/`, diagnóstico | Hecha |
| 2 | Revelado GLES (pipeline + look) | Pendiente |
| 3 | Galería con edición en vivo, sidecar JSON | Pendiente |
| 4 | Exportación JPEG/HEIC, auto-exportar, compartir | Pendiente |

## Requisitos

- Android Studio (Narwhal o posterior) con Android SDK 36 instalado.
- JDK 17 (el que trae Android Studio sirve).
- Pixel 10a con *Opciones de desarrollador → Depuración USB* activada.

## Compilar e instalar por USB

```bash
cd Rawapp
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # tests unitarios
./gradlew installDebug           # con el Pixel conectado y autorizado
```

También puedes abrir la carpeta `Rawapp/` en Android Studio y pulsar *Run*.

## Estructura

```
app/src/main/java/app/rawapp/
  MainActivity.kt                 Activity única, edge-to-edge
  camera/
    CameraController.kt           CameraDevice + sesión (preview + RAW_SENSOR), captura, liberación
    CameraSelection.kt            Elige la cámara trasera con RAW y el tamaño de preview
    CameraCapabilities.kt         Lectura de capacidades para el diagnóstico
    CaptureOrientation.kt         Rotación de la captura (función pura, testeada)
    RawCapture.kt                 Image RAW + resultado + características
  storage/
    DngWriter.kt                  DngCreator → MediaStore (Pictures/Rawapp/)
  ui/
    RawappApp.kt                  Navegación mínima y control del permiso de cámara
    theme/Theme.kt                Colores, tipografía, tokens de cristal
    components/                   Superficie "glass", botones, pantalla de mensaje
    camera/                       Pantalla de cámara, ViewModel, SurfaceView, rotación física
    diagnostics/                  Pantalla y ViewModel de diagnóstico
app/src/test/                     Tests unitarios de funciones puras
```

## Decisiones técnicas

- **Camera2, no CameraX.** Se necesita el stream `RAW_SENSOR` y el `TotalCaptureResult` exacto de cada disparo, que es lo que pide `DngCreator`.
- **El visor no muestra el look.** La preview es la del sistema y el revelado se verá en la galería. Así el visor va fluido y la Fase 2 no depende de él.
- **Guardado.** El DNG se escribe en un `OutputStream` de MediaStore con `IS_PENDING`. No hace falta ningún permiso de almacenamiento.
- **Lectura de DNG en la galería (Fase 3).** `ImageDecoder`/`BitmapFactory` devuelven el DNG ya revelado por Android, no los datos lineales del sensor, así que no sirven para nuestro pipeline. En lugar de LibRaw (NDK + CMake) se usará un **lector TIFF/DNG mínimo en Kotlin**: los DNG solo los escribe esta app con `DngCreator` (sin comprimir y con un formato conocido), así que basta con leer CFA, BlackLevel, WhiteLevel, ColorMatrix, ForwardMatrix y AsShotNeutral. Así no añadimos ninguna dependencia nativa.
- **Interfaz.** Fondo negro con controles de "cristal claro" translúcidos. Compose no puede desenfocar lo que hay detrás de un `SurfaceView` de cámara, así que el efecto se consigue con relleno translúcido, un brillo superior y un borde de luz.
- **Look (Fase 2).** Busca la estética del trabajo de Steve Yedlin (Chromagraph): espacio de trabajo de escena amplio, curva con hombro largo, desaturación de altas luces y sombras, separación cálido/frío, compresión suave de gamut, halación y grano fino. Su transformación no es pública: se reconstruye a partir de los principios que él ha explicado y se ajusta a ojo. No es una copia exacta.

## Dependencias

- `androidx.activity:activity-compose`: `setContent`, el contrato de permisos y `BackHandler`.
- `androidx.lifecycle:lifecycle-viewmodel-compose`: ViewModels con `viewModel()`.
- `androidx.lifecycle:lifecycle-runtime-compose`: `collectAsStateWithLifecycle` y `LifecycleResumeEffect`, para abrir y cerrar la cámara según el ciclo de vida.
- `androidx.compose.ui`, `foundation`, `material3` (BOM): la interfaz. Material3 solo aporta tipografía, el indicador de progreso y los iconos.
- `androidx.compose.material:material-icons-core`: los iconos de información y de volver.
- `junit`: los tests unitarios.

## Limitaciones conocidas

- Solo se usa la cámara trasera lógica principal. La ultra gran angular y la frontal aparecen en el diagnóstico, pero no se pueden seleccionar.
- La interfaz está fijada en vertical. La orientación de la foto se calcula con el sensor de orientación y se escribe en el DNG.
- El efecto cristal no desenfoca la preview, por la limitación de Compose ya explicada.
