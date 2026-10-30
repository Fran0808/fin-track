# Evaluación de FinTrack como usuario final

Fecha: 25 de octubre de 2026. Evaluación de la sesión web local abierta por el
usuario, con su sesión autenticada. No se revisó código ni arquitectura para
emitir los hallazgos. No se modificaron registros, reglas, categorías, productos
ni conexiones, ni se solicitó una sincronización manual.

## Decisión de uso

No elegiría todavía FinTrack como mi única herramienta de control mensual.
Sí la usaría para consultar y revisar los movimientos que ha capturado. Pude
reconciliar los totales visibles, pero no completar el ciclo de registrar un
gasto omitido, resolver cualquier error y decidir cuánto puedo reservar para
obligaciones futuras.

Esta conclusión corresponde a la interfaz observada durante la sesión, no a
una certificación de integridad de los movimientos bancarios ni de una versión
desplegada. Había cambios concurrentes en el espacio de trabajo al cerrar la
evaluación; deben repetirse los escenarios afectados si cambia la interfaz.

## Recorrido y evidencia

| Tarea cotidiana | Resultado observado |
| --- | --- |
| Revisar gastos, entradas y flujo neto del mes | Inicio y Análisis coincidieron; la resta de entradas menos gastos coincidió con el flujo neto. |
| Conciliar todos los gastos del mes | Las tres páginas del filtro Gastos sumaron el total mensual. El número de gastos coincidió con el desglose por categorías y medios. |
| Conciliar entradas | Las cinco filas del filtro Entradas sumaron el total mostrado en Inicio. |
| Comprender un pago de tarjeta propia | El detalle consultado explicó su exclusión de gastos, entradas y flujo neto. El movimiento se conservó en el historial. |
| Investigar una categoría | El acceso desde Análisis conservó categoría, tipo gasto y fechas; los subtotales de las dos páginas coincidieron con el grupo de origen. |
| Investigar un comercio frecuente | El acceso desde el ranking mostró sus cinco compras, con fechas e importes que sumaban el grupo de origen. |
| Consultar meses anteriores | Se consultó setiembre desde el selector; los rangos de comparación se mostraron explícitamente. Octubre parcial se comparaba con los mismos días de setiembre; setiembre cerrado se comparaba con agosto completo. |
| Explorar el gráfico | Se alternaron Diario y Acumulado; la flecha izquierda mostró el día anterior y sus importes. |
| Buscar pequeñas compras repetidas | La búsqueda por comercio en Todo el historial encontró movimientos de distintos meses, con y sin categoría. |
| Corregir clasificación y automatizarla | Se abrieron el detalle y el formulario de regla contextual y se cancelaron. Se verificó la oferta y explicación del flujo, no su guardado. |
| Revisar productos y fuentes | Se consultaron Tarjetas y cuentas, el formulario de producto cancelado, Categorías, Reglas y Configuración. |

No se copian nombres de personas, direcciones de correo, números de productos,
comprobantes ni importes identificables en este informe.

## Problemas observados

### 1. No encuentro cómo completar un gasto que quedó fuera de la captura

**Situación:** Necesito incluir una compra en efectivo o un movimiento que no
llegó por correo o notificación.

**Comportamiento observado:** Movimientos ofrece consulta, filtros, detalles y
exportación. No encontré una acción para registrar un movimiento manualmente en
las pantallas recorridas. Configuración presenta la captura por Gmail y Android.

**Problema:** No puedo completar mi registro desde la experiencia visible. Un
total de movimientos capturados puede ser correcto y aun así omitir gastos reales.

**Expectativa:** Poder incorporar una omisión y distinguirla de un registro
capturado automáticamente.

**Impacto:** Alto.

**Propuesta:** Ofrecer un registro manual de gasto o entrada con importe, fecha,
medio y categoría, indicando su origen y permitiendo conciliarlo si después llega
el comprobante. Es una capacidad propuesta, no verificada en esta evaluación.

### 2. La corrección visible no resuelve errores de importe, fecha o tipo

**Situación:** Necesito corregir un registro cuyo importe, fecha o interpretación
financiera no corresponde a lo ocurrido.

**Comportamiento observado:** En los detalles de gasto y entrada revisados pude
ver categoría y asignación de tarjeta/cuenta como controles editables. Importe,
fecha y tipo se presentaban como información, sin una corrección general visible.

**Problema:** Clasificar un registro no basta para reparar un error que cambia
mis totales. Tampoco encontré allí una forma de resolver un posible duplicado.
No se demostró que los registros consultados tuvieran esos errores.

**Expectativa:** Un camino para corregir o reportar una discrepancia, conservando
qué llegó originalmente y qué se corrigió.

**Impacto:** Alto.

**Propuesta:** Añadir una acción de revisión con motivo y vista previa del efecto
en gastos, entradas y transferencias; mostrar el historial de correcciones.

### 3. La clasificación histórica exige demasiado trabajo individual

**Situación:** Quiero saber en qué categorías gasto y poner al día movimientos
repetidos que siguen sin clasificar.

**Comportamiento observado:** La mayor parte del gasto de octubre estaba sin
categoría y en setiembre todo el gasto figuraba en ese grupo. La búsqueda de un
comercio con regla activa mostró compras clasificadas y otras sin categoría.
Reglas explica que los movimientos anteriores conservan su clasificación. No
encontré selección múltiple ni una aplicación retrospectiva desde las vistas
recorridas.

**Problema:** Tengo un catálogo y reglas, pero el análisis histórico sigue sin
explicar el destino del dinero hasta que revise los registros por separado.
Esto describe el estado observado; no demuestra un fallo en las reglas futuras.

**Expectativa:** Resolver juntos los movimientos equivalentes tras revisarlos.

**Impacto:** Alto.

**Propuesta:** Permitir clasificar una selección y previsualizar qué movimientos
históricos sin categoría recibirían una regla, antes de confirmar el cambio.

### 4. El resumen del listado no responde al conjunto que estoy consultando

**Situación:** Quiero conocer el total de una categoría o de las entradas que
acabo de filtrar.

**Comportamiento observado:** Al paginar, «Gastos mostrados» cambió con la página;
el promedio sí decía «de esta página». El total de toda la categoría no permaneció
visible. Al seleccionar Entradas, el resumen mantuvo «Gastos mostrados» y un
promedio de cero aunque había entradas con importes positivos.

**Problema:** Debo sumar páginas o volver a Análisis para contestar cuánto
representa mi selección. El cero al consultar entradas no resume lo que busqué.
Los subtotales comprobados sí sumaron correctamente: no se observó un descuadre.

**Expectativa:** Ver el total del filtro y saber qué parte corresponde a la página.

**Impacto:** Medio.

**Propuesta:** Mostrar totales de gastos y entradas de todos los resultados,
separados del subtotal de página, y adaptar el resumen al tipo seleccionado.

### 5. Sé cuánto aumentó el gasto, pero no qué explica el aumento

**Situación:** Quiero entender por qué gasté más que en el período anterior.

**Comportamiento observado:** Análisis mostró ambos importes, sus fechas y la
diferencia. También mostró el mayor gasto y el día de mayor gasto, pero la
comparación no ofrecía un desglose de la diferencia por categoría o comercio.

**Problema:** Un gasto grande del mes no demuestra por sí solo qué cambió respecto
del anterior. Tengo que reconstruir esa explicación cambiando de período y
comparando por mi cuenta.

**Expectativa:** Identificar los aumentos y reducciones que componen la diferencia.

**Impacto:** Medio.

**Propuesta:** Desglosar la diferencia por categoría y comercio, con acceso a los
movimientos de cada período y una indicación de lo que sigue sin categorizar.

### 6. No puedo auditar el origen de una cifra desde su detalle

**Situación:** Quiero distinguir una compra legítima de una posible captura
duplicada o interpretar un nombre poco reconocible.

**Comportamiento observado:** Los detalles revisados mostraron contacto/comercio,
medio, fecha, importe y clasificación. No mostraron un comprobante o referencia
de origen, ni una explicación de cuándo y desde qué fuente llegó ese movimiento.

**Problema:** Puedo localizar el registro, pero no contrastarlo desde FinTrack con
la evidencia que lo originó. Nombres o importes parecidos no bastan para concluir
que hay duplicación; no se confirmó ningún duplicado.

**Expectativa:** Reconocer la fuente y contar con una referencia verificable.

**Impacto:** Alto.

**Propuesta:** Mostrar origen, momento de captura y referencia del comprobante,
con los datos personales protegidos, y un flujo para señalar una posible duplicación.

### 7. Un fallo de sincronización no me dice cómo recuperar la confianza

**Situación:** Necesito saber si puedo considerar actualizados los movimientos.

**Comportamiento observado:** Durante el recorrido apareció «Falló la última
sincronización» junto con la última correcta. No había una causa ni un siguiente
paso específico en ese aviso. Más tarde volvió a «Sincronizado» con una hora
posterior, sin que yo solicitara una sincronización manual. Configuración indicó
que no registra la última actividad del teléfono.

**Problema:** La recuperación del estado es útil, pero no me permite saber qué
fuente falló, si quedaron movimientos pendientes o si el teléfono sigue enviando
datos. Un estado de sincronización no prueba que todos mis gastos estén capturados.

**Expectativa:** Conocer el alcance del fallo y el estado separado de cada fuente.

**Impacto:** Alto.

**Propuesta:** Mostrar causa comprensible, fuente afectada, última recepción y
acción recomendada, distinguiendo actualización de correo y recepción móvil.

## Necesidades sin respuesta verificada e ideas de mejora

No encontré un flujo para obligaciones recurrentes, objetivos de ahorro ni una
proyección de compromisos futuros en las pantallas recorridas. No afirmo que
existan en otra versión ni que se haya probado su ausencia fuera de este recorrido.

Para decidir cuánto puedo ahorrar, necesitaría registrar obligaciones y fechas
y contrastarlas con los movimientos registrados. Ese cálculo debe explicar sus
supuestos: el flujo neto observado no es un saldo bancario ni dinero disponible.
FinTrack hace bien en no presentar estos totales como saldos.

Para descubrir compras pequeñas acumuladas, sería útil ordenar todos los comercios
por frecuencia y fijar un umbral de importe. La búsqueda actual ayuda cuando ya
sé qué comercio quiero investigar; el ranking por gasto total no resuelve por sí
solo ese descubrimiento.

## Valoración de las seis dimensiones

| Dimensión | Juicio como usuario |
| --- | --- |
| Utilidad financiera | Útil para consultar lo capturado; insuficiente como registro completo si no puedo incorporar omisiones. |
| Claridad | Rangos y flujo neto comprensibles; resumen del listado poco adecuado para entradas y conjuntos paginados. |
| Facilidad de uso | Consulta y accesos desde Análisis directos; saneamiento histórico repetitivo. |
| Confiabilidad | Totales conciliados en los escenarios revisados; integridad respecto de fuentes externas y resolución de errores sin verificar. |
| Capacidad de análisis | Permite localizar concentraciones y revisar días; exige trabajo manual para explicar variaciones y detectar hábitos. |
| Valor cotidiano | Regresaría para revisar movimientos; necesitaría otra herramienta para completar registros y planificar obligaciones. |

## Límites de la evaluación

- Se examinó la interfaz web en el tamaño disponible del navegador integrado.
  No hubo aceptación independiente en teléfono, otras resoluciones o navegadores.
- No se guardaron cambios ni se verificó la persistencia de una edición, regla o
  producto. Los formularios abiertos se cancelaron.
- No se importaron correos ni se generaron notificaciones. No se probó la captura
  de extremo a extremo, permisos Android, duplicación entre fuentes o recuperación
  de movimientos omitidos.
- No se contrastaron los movimientos con estados bancarios. No se comprobaron
  devoluciones, cambio de mes en tiempo real, otras monedas ni aislamiento entre
  usuarios.
- Se vio la opción Exportar CSV; no se descargó ni validó su contenido.
- No se detectó un problema crítico demostrado ni un error aritmético en las
  conciliaciones descritas. Esto no equivale a aprobar la integridad financiera.
- Al finalizar se dejó Inicio con octubre seleccionado, sin filtros del recorrido.

Mi condición para usar FinTrack como herramienta principal sería poder completar
omisiones, auditar y resolver discrepancias, y mantener la clasificación al día
sin revisar individualmente cada compra repetida. Después evaluaría la
planificación de obligaciones; añadir gráficos por sí solo no resuelve esas tareas.
