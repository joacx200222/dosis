# dosis

Diario personal de medicinas para **Android (Galaxy S23 + Tab S11)**, **Wear OS (Galaxy Watch4)** y **web**.
Un solo núcleo en Kotlin Multiplatform; cada plataforma tiene su propia interfaz.

## Estructura

```
shared/      Núcleo común (Android, JVM y Wasm)
  model/         Medicine, Dose, Treatment, Schedule, Stock, PillAppearance
  interactions/  InteractionEngine + reglas de avisos
  schedule/      Cálculo de horarios y próxima toma
  search/        Sugerencias de "¿Qué tomaste?" (sin importar acentos)
  journal/       Estado del diario (registrar, botiquín, notas)
  ui/            Paleta risograph compartida
  data/          Carga del archivo de interacciones y nombres en español/marcas (DrugLexicon)
  demo/          Diario de ejemplo de los mockups
androidApp/  Celular + tableta (Jetpack Compose, diseño adaptable a partir de 600 dp)
wearApp/     Reloj (Compose for Wear OS, Material 3)
webApp/      Web (Compose Multiplatform → WebAssembly)
tools/       Script que arma los datos + tablas curadas (editables)
data/generated/interactions.tsv   Archivo que va dentro de las 3 apps (~1.9 MB; no se publica, ver data/README.md y NOTICE.md)
```

## Datos de interacciones

| Fuente | Qué aporta |
|---|---|
| **DDInter 2.0** (CSV, CC BY-NC-SA 4.0) | 160 mil pares medicamento–medicamento y 1,944 medicinas. También aporta alcohol (etanol), cafeína, nicotina, poppers, ketamina, GHB, óxido nitroso y hierbas, porque DDInter las trata como medicamentos. |
| **TripSit** (`combos.json`) | Combinaciones con drogas: MDMA, LSD, hongos, DMT, ketamina, GHB, 2C-x, NBOMe, opioides, benzodiacepinas, etc. Se cruzan con grupos de medicamentos (ISRS, IMAO, opioides, litio…) definidos en `tools/tripsit_classes.tsv`. |
| `tools/curated.tsv` | Tabla propia: toronja, lácteos, cafeína, tabaco, ayahuasca, poppers, San Pedro y suplementos. |
| `tools/curated_pairs.tsv` | Reglas entre grupos de medicamentos. Hacen falta porque las descargas públicas de DDInter solo traen los grupos ATC A, B, D, H, L, P, R y V, y faltan pares como sertralina + tramadol o sildenafilo + nitratos. |
| **CIMA (AEMPS, España)** | Lista ATC oficial con los nombres en español de los principios activos. Le pone nombre en español a ~1,300 medicinas de DDInter, suma ~2,300 medicinas que DDInter no trae (sin pares de interacción; salen como "al menos moderada") y registra las combinaciones (p. ej. "Valsartan, amlodipino e hidroclorotiazida") como un solo producto. |
| **INVIMA (Colombia, datos.gov.co)** | Registro de medicamentos (vigentes, en renovación, vencidos y otros estados) con su principio activo y código ATC: la mayor fuente de marcas latinoamericanas. |
| **CIMA productos (España)** | 25,521 medicamentos autorizados con su principio activo. |
| **RxNorm (EE. UU., contenido prescribible, sin licencia)** | Principios activos y marcas de EE. UU. con sus ingredientes. |
| `tools/aliases_es.tsv` | Nombres y marcas de Perú y Latinoamérica escritos a mano (Apronax, Panadol, Augmentin, Buscapina Compositum…). Tienen prioridad sobre CIMA. |

"Ten en cuenta siempre" muestra **31 cosas** en cada medicina: alcohol, tabaco, 25 drogas, toronja, lácteos, cafeína y suplementos. Cada una aparece como mínimo en nivel moderado. Las que no tienen datos se juntan en una sola línea por grupo, para que la lista no sea eterna.

En total la búsqueda tiene **7,684 principios activos y 20,700 marcas o productos** (4,806 de ellos combinaciones, que se registran como un solo producto con todos sus ingredientes). Los genéricos ("Ibuprofeno Cinfa 400 mg") cuentan como el propio principio activo, no como marca; los extractos para pruebas de alergia de RxNorm se dejan fuera. DIGEMID (Perú) no publica su registro como datos abiertos, así que las marcas peruanas se agregan a mano en `aliases_es.tsv`.

Para regenerar los datos, descarga los CSV de DDInter, `combos.json`, las páginas de CIMA (ATC y productos), INVIMA y RxNorm (los enlaces están en `tools/build_interactions.py`) en `tools/raw/` y corre:

```bash
python -I tools/build_interactions.py tools/raw data/generated/interactions.tsv
```

## Reglas de avisos (implementadas en `InteractionEngine`)

1. **Con tu botiquín:** la medicina se compara con *todas* las del botiquín, sin importar cuándo las tomaste. Se mantiene la gravedad real.
2. **Ten en cuenta siempre:** alcohol, cannabis, cocaína, tabaco, toronja, lácteos, cafeína y suplementos aparecen *siempre*, con nivel mínimo **moderada**.
3. **Sin datos:** igual se muestran como moderadas, con el texto "Sin datos específicos… por precaución". Una lista vacía nunca debe parecer "seguro".
4. Los avisos salen **antes de confirmar** el registro, en todas las plataformas. El historial no muestra avisos.

## Recordatorios (Android)

- **Dónde se configuran:** en Botiquín, en cada medicina, con **"+ Agregar recordatorio"**. Hay horarios diario, días específicos, cada X horas, cíclico y "cuando lo necesite". También eliges entre aviso suave o fuerte, si quieres que repita a los 30 min, y si el tratamiento tiene fecha de fin.
- **Aviso suave:** una notificación normal.
- **Aviso fuerte:** una alarma a pantalla completa sobre la pantalla de bloqueo. Suena por el canal de alarma, así que se oye aunque el teléfono esté en silencio, y deja de sonar sola a los 3 minutos.
- **Botones:** **Tomada**, **Posponer 10 min** y **Omitir**, en la notificación y en la alarma. La notificación también muestra las interacciones graves, para que registrar desde ahí no se salte los avisos.
- **Repetición:** si a los 30 min la toma no está registrada (tomada u omitida), vuelve a avisar.
- **Reloj:** el Galaxy Watch muestra las notificaciones del teléfono con los mismos botones.
- **Cómo funciona por dentro:** siempre hay **una sola alarma** programada, la próxima. El plan se calcula en el núcleo compartido (`ReminderPlanner`) y se recalcula cada vez que cambia el diario, al reiniciar el teléfono o si cambia la hora.
- **Permisos:** si falta alguno, Hoy muestra la tarjeta amarilla "Activa tus recordatorios": notificaciones, alarmas exactas, pantalla completa y No molestar.
- **Guardado:** el diario se guarda en el teléfono, en `files/journal.json`. La app empieza vacía; los datos de ejemplo solo los usan la web y las pruebas.

## Cómo correrlo

Requiere el JDK de Android Studio y el SDK 36. Abre la carpeta en Android Studio o usa:

```bash
./gradlew :shared:jvmTest                     # pruebas del núcleo
./gradlew :androidApp:installDebug            # al S23 / Tab S11 por USB o Wi‑Fi
./gradlew :wearApp:installDebug               # al Watch4 (depuración por Wi‑Fi activada)
./gradlew :webApp:wasmJsBrowserDevelopmentRun # web en localhost
```

> En esta máquina, Gradle está configurado con 2 GB de memoria (`gradle.properties`) porque con 4 GB se cerraba.
> La compilación web *de producción* necesita una ruta corta (Windows limita las rutas a 260 caracteres); la de desarrollo funciona en cualquier ruta.

## Estado

| Parte | Estado |
|---|---|
| Núcleo: modelo, motor de interacciones, horarios, recordatorios, búsqueda, diario | ✅ con 30 pruebas |
| Recordatorios en Android: suave, fuerte con alarma, repetir a los 30 min, posponer, editor de horarios | ✅ |
| Diario guardado en el teléfono (JSON) | ✅ |
| Datos reales: DDInter + TripSit + tablas propias, nombres en español y marcas | ✅ en las 3 apps |
| Android: Hoy, registro con avisos antes de confirmar, Historial, Botiquín, diseño para tableta | ✅ base |
| Wear OS: registrar desde recientes, avisos cortos, Hoy | ✅ base |
| Web: Historial (sin avisos), registro con avisos, Botiquín | ✅ base |

## Próximos pasos

1. **Completar pares que faltan:** sumar el texto de interacciones de las etiquetas de openFDA para los grupos ATC que DDInter no publica (C, N, J, G, M, S). Ampliar `aliases_es.tsv`.
2. **Persistencia en el reloj y la web:** el teléfono ya guarda en JSON; falta IndexedDB en la web y el guardado en el reloj (junto con el Data Layer).
3. **Recordatorios en el reloj:** tile y complicación con la próxima toma (las notificaciones ya llegan desde el teléfono).
4. **Reloj 24/7:** Wear Data Layer entre el S23 y el Watch4; tile y complicaciones.
5. **Nube:** Supabase gratis, con cifrado de extremo a extremo (clave secreta), sincronizando al abrir la app.
6. **Pantallas que faltan:** apariencia de la pastilla, nota con S Pen, exportar PDF, ajustes, bienvenida y desbloqueo.
7. **Diseño:** incluir la tipografía grotesca definitiva y unificar la interfaz de Android y la web en un módulo de Compose Multiplatform.
8. **Fase 2:** compartir la lista con Health Connect (API experimental, Android 16).

**Aviso:** dosis no reemplaza a un médico ni a un farmacéutico.
