# Revisión del frontend de FinTrack

Fecha: 23 de octubre de 2026. Responsable: chat de revisión del frontend.
Estado de la revisión original: análisis estático y automatizado completado.
El diagnóstico siguiente conserva la evidencia previa a las correcciones; sus
líneas de código corresponden a esa revisión. El seguimiento de implementación
está al final de este documento.

## Hallazgos prioritarios

### 1. P1: respuestas antiguas sobrescriben búsquedas o períodos nuevos

[useFinance.ts](../../ui/src/hooks/useFinance.ts), líneas 49–76.
Las cargas no cancelan peticiones ni comprueban su vigencia. Si una búsqueda A
tarda más que una posterior B, A puede sobrescribir el resultado de B. Las filas
dejan de corresponder al filtro visible. La analítica mensual usa el mismo patrón
y puede mostrarse bajo el encabezado de otro mes.
Reproducido con promesas controladas en un test del hook para transacciones.
Descartar respuestas superadas y asociar carga/error/resultado a la consulta actual.

### 2. P1: asignar una tarjeta borra ediciones sin guardar

[TransactionDetailModal.tsx](../../ui/src/components/transactions/TransactionDetailModal.tsx),
líneas 37–46 y 245.
Abrir un movimiento, escribir una nota y confirmar una tarjeta sugerida. La
asignación actualiza el objeto transaction; el efecto dependiente de todo el
objeto restaura categoría, notas y etiquetas desde el servidor, perdiendo el
borrador sin avisar. Reproducido con componentes reales y una respuesta simulada.
Reiniciar el borrador al cambiar de movimiento y preservar las ediciones de cada
sección cuando se guarda otra sección.

### 3. P1: CSV incompleto sin advertencia

[TransactionsView.tsx](../../ui/src/components/views/TransactionsView.tsx), líneas 31–73.
Si falla la exportación del servidor, se descarga silenciosamente solo la página
actual. Una búsqueda de varias páginas produce un archivo parcial con el mismo
nombre, sin avisar del fallo. Reproducido forzando el rechazo del endpoint.
Mostrar el error y reintentar; ofrecer una exportación parcial solo con alcance
explícito. Además, el escape local únicamente duplica comillas: textos que
empiezan con `=` pueden convertirse en fórmulas al abrir el archivo en una hoja
de cálculo. Este último riesgo se identificó por código, sin ejecutar fórmulas.

### 4. P2: selectores personalizados no funcionan con teclado

[CustomSelect.tsx](../../ui/src/components/common/CustomSelect.tsx), líneas 62–135.
El botón abre la lista, pero las opciones son elementos li con onClick, sin foco
ni gestión de flechas y Enter. El select nativo está fuera del orden de Tab.
Reproducido: flecha abajo y Enter no invocaron onChange. Afecta filtros,
categorías, formularios y asignación. También se repiten IDs como option- entre
selectores y algunos botones anuncian solo el valor, sin el nombre del campo.
Usar select nativo visible o implementar el patrón accesible completo.

### 5. P2: errores que permanecen después de recuperarse

[useFinance.ts](../../ui/src/hooks/useFinance.ts), líneas 49–76.
Una carga fallida escribe el error global, pero una carga posterior correcta no
lo limpia. Reproducido: fallo inicial seguido de refreshAll correcto, con datos
actualizados y el error antiguo todavía presente. Separar errores por recurso y
limpiarlos al completarse correctamente su petición vigente.

### 6. P2: Inicio hereda filtros y paginación de Movimientos

[App.tsx](../../ui/src/App.tsx), línea 146, y
[HomeView.tsx](../../ui/src/components/views/HomeView.tsx).
Inicio utiliza el mismo transactionsPage que el historial. Filtrar por comercio,
elegir un rango de otro mes o ir a otra página y volver a Inicio hace que
«Movimientos recientes» tome cinco registros de esa consulta. Sus textos y
totales siguen describiendo el mes global. Incluso puede anunciar que no hay
movimientos por una búsqueda sin resultados. Confirmado por flujo de props y
estado; pendiente de recorrido en navegador. Cargar recientes con una consulta
independiente del historial, restringida al mes y primera página.

### 7. P2: categorías del detalle quedan desactualizadas

[useCategories.ts](../../ui/src/hooks/useCategories.ts), líneas 11–30, y
[TransactionDetailModal.tsx](../../ui/src/components/transactions/TransactionDetailModal.tsx), línea 19.
El modal permanece montado en App, incluso sin movimiento abierto. Su catálogo se
carga al montar el dashboard; crear, renombrar o desactivar categorías no lo
invalida ni se recarga al abrir el detalle. Una categoría nueva puede no aparecer
hasta recargar la aplicación y una antigua seguir ofreciéndose. Confirmado por
dependencias y montaje, sin reproducción visual. Compartir e invalidar el catálogo
o actualizarlo al abrir. Distinguir fallo de carga de catálogo vacío: hoy ambos
se reemplazan silenciosamente por categorías predefinidas.

## Navegación y claridad

- La división entre resumen, análisis, historial y organización es razonable.
  Inicio y Mi dinero repiten métricas; «Análisis» diferenciaría mejor la segunda.
- Las vistas dependen de useState en App, sin URL propia. No se pueden enlazar
  directamente; recargar vuelve a Inicio y Atrás/Adelante no recorre secciones.
  Limitación confirmada por código. Añadir rutas y conservar filtros relevantes.
- El menú móvil cerrado solo se desplaza fuera de pantalla: no tiene inert ni
  ocultación accesible, de modo que sus botones pueden recibir Tab fuera de vista.
  Al abrir no hay gestión de foco o Escape. Requiere aceptación con teclado real.
- La barra lateral tiene nueve destinos y carece de desplazamiento propio. Existe
  riesgo de destinos inaccesibles en pantallas bajas/móvil horizontal; pendiente
  de verificación visual, no un desbordamiento observado.
- La tabla tiene mínimo 700 px y scroll horizontal. En móvil exige desplazarse
  para ver importe/detalle; evaluar una presentación que priorice ambos.
- «Gastos mostrados» y «Promedio» se calculan sobre la página. Aclarar «de esta
  página» en el promedio evita confundirlo con el promedio del período.
- Presupuestos está señalado como Próximamente, aunque ocupa espacio como destino.

## Aspectos correctamente planteados

- Flujo neto como entradas menos gastos; transferencias propias separadas y sin
  prometer saldos bancarios.
- Fechas inclusivas, rechazo de rangos incompletos/invertidos y pruebas existentes
  de paginación y restauración del mes.
- Pendientes explica su alcance histórico y corrige su página al reducirse los
  resultados; Reglas explica prioridad y aplicación a movimientos nuevos.
- Productos conserva historial al archivar y exige confirmar sugerencias.
- La sesión protege frente a un 401 tardío de otro token y tiene tests.
- Diálogos nativos en varios flujos, formato monetario compartido y CSS para
  movimiento reducido. Esto no certifica accesibilidad o seguridad completas.

## Evidencia y límites

- npm test: 9 archivos y 46 tests aprobados. El primer intento falló por ENOENT en
  temporales del sandbox; pasó al configurar TEMP/TMP, solo para ese proceso,
  hacia ui/node_modules/.review-tmp.
- npm run lint: completado con 12 advertencias; predominan estados en efectos,
  más Fast Refresh y una advertencia de inmutabilidad en CategoryDonutChart.
  No equivalen por sí solas a bugs confirmados.
- npm run build: compilación TypeScript y Vite correcta.
- Cinco tests temporales de caracterización con datos ficticios confirmaron los
  fallos 1–5. Se retiraron tras la revisión: no son cobertura permanente ni
  significan que los fallos estén corregidos.
- Vite de revisión arrancó en 127.0.0.1:5178, pero el navegador integrado devolvió
  ERR_CONNECTION_TIMED_OUT. Se detuvo el proceso al terminar. No se pudo verificar
  apariencia real, contraste, adaptación móvil, foco real o recorrido autenticado.
  El timeout no se atribuye a un defecto del frontend.
- Sin backend real, OAuth real, importación de correos, cambios financieros,
  rotación de credenciales ni instalación Android.

Siguiente acción: corregir primero respuestas superadas, borradores y CSV; después
selectores, errores y coherencia del dashboard. Incorporar regresiones de
comportamiento esperado y completar revisión visual con datos ficticios en móvil,
escritorio y altura reducida. P1 indica prioridad por información engañosa o
pérdida de trabajo; P2 indica fallo funcional o de accesibilidad.

## Seguimiento de implementación

El usuario autorizó corregir los hallazgos. Se implementaron los siete fallos:

- Recursos asíncronos ligados a su consulta, con descarte de resultados y errores
  superados, estados de carga independientes y limpieza de errores al recuperarse.
  Incluye protección al cambiar de usuario y rechazo de lecturas financieras sin
  usuario autenticado. La analítica mensual ya no depende del resumen histórico.
- Consulta independiente de cinco movimientos recientes del mes global para Inicio.
- Borrador de clasificación vinculado al ID del movimiento; asignar un producto
  conserva notas, categoría y etiquetas sin guardar. El detalle se monta al abrir
  y recarga categorías; errores/vacíos no se sustituyen por categorías inventadas.
- Exportación exclusivamente del resultado completo del servidor. Al fallar muestra
  un error y permite reintentar; se eliminó también el generador CSV local que
  escapaba texto sin proteger posibles fórmulas.
- Selectores nativos visibles, con nombre de campo y foco normal de teclado.
- Vistas con parámetro `vista`, enlaces directos y navegación Atrás/Adelante.
  El nombre visible de Mi dinero cambió a Análisis; su identificador se conserva.
  Filtros/mes se conservan entre secciones en memoria, no tras recargar la página.
- Menú móvil con diálogo nativo y cierre al navegar, Escape o pasar a escritorio;
  barra lateral con desplazamiento vertical. Tabla móvil con contacto, importe y
  detalle visibles; fecha y medio permanecen disponibles en el detalle.
- Promedio identificado como propio de la página y contador de filtros corregido
  para incluir límites de importe de cero.

Verificación final: 60 tests aprobados en 12 archivos (14 regresiones nuevas),
compilación TypeScript/Vite correcta, lint completado con 8 advertencias restantes
y comprobaciones de diff y enlaces correctas. Los tests de componentes usan jsdom:
no sustituyen la aceptación de teclado, foco o distribución en navegador real.

La evidencia automatizada final y los límites se registran en el checkpoint de
[progreso](../PROGRESS.md#frontend-review-checkpoint). La aceptación visual sigue
pendiente: el navegador integrado volvió a fallar al acceder a Vite local con
ERR_CONNECTION_TIMED_OUT. No se ejecutaron operaciones financieras, OAuth real,
rotación de tokens, migraciones ni cambios en backend o Android.

### Grupos de commit propuestos

Ejecutar desde la raíz únicamente cuando el usuario quiera guardar los cambios.
El agente no ejecutó estos comandos. El segundo grupo depende del primero, y la
documentación final describe ambos.

1. Integridad de datos y edición, con sus regresiones:

```powershell
git add ui/src/hooks/useQueryResource.ts ui/src/hooks/useFinance.ts ui/src/hooks/useCategories.ts ui/src/components/transactions/TransactionDetailModal.tsx ui/src/components/views/TransactionsView.tsx ui/tests/finance-reliability.test.tsx ui/tests/dashboard-interactions.test.tsx ui/tests/transactions-features.test.tsx
git commit -m "fix(ui): preserve current queries and transaction drafts"
```

2. Navegación, accesibilidad y claridad del dashboard:

```powershell
git add ui/src/App.tsx ui/src/hooks/useDashboardNavigation.ts ui/src/components/layout/MobileNavigation.tsx ui/src/components/layout/SidebarNavigation.tsx ui/src/components/common/CustomSelect.tsx ui/src/components/views/MyMoneyView.tsx ui/src/components/transactions/TransactionFilters.tsx ui/src/components/transactions/TransactionTable.tsx ui/tests/navigation-accessibility.test.tsx
git commit -m "fix(ui): support browser navigation and accessible controls"
```

3. Documentación del comportamiento y evidencia:

```powershell
git add ui/README.md docs/ui/frontend-review-2026-10-23.md docs/PROGRESS.md
git commit -m "docs(ui): record dashboard audit fixes and verification"
```
