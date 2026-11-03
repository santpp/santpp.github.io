# Rawapp

Rawapp es una cámara RAW personal para el **Google Pixel 10a**. Captura DNG directamente del sensor con Camera2 y (a partir de la Fase 2) los revela en GPU con un look cinematográfico propio. Funciona en local: sin red, sin cuentas y sin analítica.

## Estado

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Esqueleto, permiso, preview, captura RAW → DNG en `Pictures/Rawapp/`, diagnóstico, selector de lente | Hecha |
| 2 | Revelado GLES (pipeline + look) | Hecha, pendiente de verificar en el Pixel |
| 3 | Galería con edición en vivo, sidecar JSON | Pendiente |
| 4 | Exportación JPEG/HEIC, auto-exportar, compartir | Adelantado: "Guardar JPEG" en la pantalla Revelar. Pendientes HEIC, auto-exportar y compartir |

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
    Lens.kt                       Lentes con RAW (físicas dentro de la cámara lógica) y su preview
    ZoomLabel.kt                  Etiqueta "0,5×", "1×"… (función pura, testeada)
    CameraCapabilities.kt         Lectura de capacidades para el diagnóstico
    CaptureOrientation.kt         Rotación de la captura (función pura, testeada)
    RawCapture.kt                 Image RAW + resultado + características
  storage/
    DngWriter.kt                  DngCreator → MediaStore (Pictures/Rawapp/)
    JpegExporter.kt               JPEG revelado y ya girado → Pictures/Rawapp/Exports/
    RawFrameFactory.kt            Copia RAW16 + metadatos de una captura a un RawFrame
  develop/
    RawFrame.kt                   Datos Bayer + calibración, independiente de Camera2
    LookParams.kt                 Ajustes del usuario y carácter fijo del look (FilmLook)
    RawDeveloper.kt               Hilo GL dedicado; revela un RawFrame a Bitmap
    RawSampling.kt                Muestreo de verdes para la exposición automática
    math/                         Matemática pura con tests: Mat3, cromaticidad, matrices DNG,
                                  balance de blancos, curva tonal, LUT 3D, exposición automática
    gl/                           EGL off-screen, programas, render targets y pasadas
  ui/
    develop/                      Pantalla de revelado (Rawapp / Sin look) y su ViewModel
    RawappApp.kt                  Navegación mínima y control del permiso de cámara
    theme/Theme.kt                Colores, tipografía, tokens de cristal
    components/                   Superficie "glass", botones, pantalla de mensaje
    camera/                       Pantalla de cámara, ViewModel, SurfaceView, rotación física
    diagnostics/                  Pantalla y ViewModel de diagnóstico
app/src/main/assets/shaders/      GLSL: demosaico, halación (extracción + blur), look final
app/src/test/                     Tests unitarios de funciones puras
```

## Decisiones técnicas

- **Camera2, no CameraX.** Se necesita el stream `RAW_SENSOR` y el `TotalCaptureResult` exacto de cada disparo, que es lo que pide `DngCreator`.
- **El visor no muestra el look.** La preview es la del sistema y el revelado se verá en la galería. Así el visor va fluido y la Fase 2 no depende de él.
- **Guardado.** El DNG se escribe en un `OutputStream` de MediaStore con `IS_PENDING`. No hace falta ningún permiso de almacenamiento.
- **Lectura de DNG en la galería (Fase 3).** `ImageDecoder`/`BitmapFactory` devuelven el DNG ya revelado por Android, no los datos lineales del sensor, así que no sirven para nuestro pipeline. En lugar de LibRaw (NDK + CMake) se usará un **lector TIFF/DNG mínimo en Kotlin**: los DNG solo los escribe esta app con `DngCreator` (sin comprimir y con un formato conocido), así que basta con leer CFA, BlackLevel, WhiteLevel, ColorMatrix, ForwardMatrix y AsShotNeutral. Así no añadimos ninguna dependencia nativa.
- **Interfaz.** Fondo negro con controles de "cristal claro" translúcidos. Compose no puede desenfocar lo que hay detrás de un `SurfaceView` de cámara, así que el efecto se consigue con relleno translúcido, un brillo superior y un borde de luz.
- **Look (Fase 2).** Busca la estética del trabajo de Steve Yedlin (Chromagraph): espacio de trabajo de escena amplio, curva con hombro largo, desaturación de altas luces y sombras, separación cálido/frío, compresión suave de gamut, halación y grano fino. Su transformación no es pública: se reconstruye a partir de los principios que él ha explicado y se ajusta a ojo. No es una copia exacta.

## Pipeline de revelado (Fase 2)

Todo en GPU, OpenGL ES 3.0, en cuatro pasadas:

1. **Demosaico** (`demosaic.frag`). Sobre el RAW16 subido como textura entera hace la linealización con el black level por fotosito y el white level, la corrección de sombreado de lente, el balance de blancos y el demosaico Malvar-He-Cutler 5×5. Después aplica la matriz cámara → Rec.709 lineal y la exposición. El resultado se guarda en RGBA16F.
2. **Halación** (`halation_extract.frag` + `blur.frag` ×2). Extrae las altas luces a ¼ de resolución y las desenfoca con un Gaussiano separable.
3. **Look** (`look.frag`). Suma la halación teñida de rojo y naranja y comprime el gamut. Aplica la curva tonal por canal (una textura 1D horneada en Kotlin), luego la LUT 3D 33³ (generada en Kotlin, sin archivos) y por último el grano según la luminancia, en sRGB.
4. **Lectura** con `glReadPixels` a un `Bitmap`.

La matriz de color sigue la especificación DNG: interpola ColorMatrix, ForwardMatrix y Calibration entre los dos iluminantes en temperatura inversa, a partir del punto neutro de la toma. Las ganancias de balance de blancos se aplican antes del demosaico y se normalizan para que ningún canal pierda señal, así las altas luces recortadas quedan neutras.

**El look** busca la estética de Yedlin con estas piezas:
- una curva con negros ligeramente levantados y un hombro muy largo, que no recorta;
- desaturación en las altas luces y en las sombras;
- "densidad", es decir, los colores muy saturados se oscurecen;
- sombras ligeramente frías y altas luces cálidas;
- verdes hacia oliva y azules hacia teal;
- halación sutil y grano fino.

Todo son aproximaciones propias. Los valores están en `FilmLook`.

Calibración v2: comparando con fotos públicas de Chromagraph se midió lo siguiente:
- los negros son profundos;
- el blanco máximo se queda en ~0,9 y es crema (azul ~12 % por debajo del rojo);
- las sombras van hacia teal;
- la halación y el grano son visibles, y hay un viñeteo leve.

La curva tiene ahora un `peak` de 0,86 en lineal, los negros están casi sin levantar y el tinte teal se desvanece al acercarse a negro puro.

Calibración v3: se comparó una pareja de fotos de la misma escena, una de la cámara del móvil y otra de Chromagraph.
- **Cambios observados:** el cielo azul claro pasa a crema cálido, la mediana baja de 0,36 a 0,21 (un look denso) y los verdes van hacia oliva y más oscuros.
- **Ajustes:** contraste 1,6, gris medio en 0,12, exposición automática más baja, altas luces más cálidas y desaturadas, azules saturados hacia teal con menos densidad de azul, y verdes hacia oliva.

## Dependencias

- `androidx.activity:activity-compose`: `setContent`, el contrato de permisos y `BackHandler`.
- `androidx.lifecycle:lifecycle-viewmodel-compose`: ViewModels con `viewModel()`.
- `androidx.lifecycle:lifecycle-runtime-compose`: `collectAsStateWithLifecycle` y `LifecycleResumeEffect`, para abrir y cerrar la cámara según el ciclo de vida.
- `androidx.compose.ui`, `foundation`, `material3` (BOM): la interfaz. Material3 solo aporta tipografía, el indicador de progreso y los iconos.
- `androidx.compose.material:material-icons-core`: los iconos de información y de volver.
- `junit`: los tests unitarios.

La Fase 2 no añade dependencias: EGL y GLES vienen en el SDK.

## Limitaciones conocidas

- Selector de lente: 0,5× (ultra gran angular), 1× (principal) y frontal. Las traseras se abren como streams físicos de la cámara lógica, y el DNG lleva las matrices y el resultado de la lente física. Si el sistema no admite RAW en una lente física, se muestra el error con "Reintentar".
- La etiqueta de zoom se calcula a partir de la focal y el tamaño del sensor, así que es aproximada.
- La interfaz está fijada en vertical. La orientación de la foto se calcula con el sensor de orientación y se escribe en el DNG.
- El efecto cristal no desenfoca la preview, por la limitación de Compose ya explicada.
- La pantalla "Revelar" solo muestra la última foto de la sesión. La galería con todos los DNG llega en la Fase 3.
- La exposición automática del revelado (de −1 a +3 EV) compensa la subexposición del RAW del Pixel. Se puede ajustar en la Fase 3.
