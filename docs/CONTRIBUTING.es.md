<!-- Español / Spanish -->
# Reglamento sobre la Fusión Automática de Solicitudes de Extracción

**Repositorio:** any-pr ｜ **Instrumento de ejecución:** `.github/workflows/auto-merge.yml`

> Esta traducción es un resumen. El texto normativo es [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Artículo Primero — Disposiciones Generales
El presente Reglamento prescribe las condiciones bajo las cuales una Solicitud de Extracción será fusionada automáticamente. No se realizará revisión humana en ninguna etapa.

## Artículo Segundo — Fusión Automática
Toda Solicitud de Extracción no designada como borrador que satisfaga los requisitos del Artículo Tercero será fusionada de inmediato mediante un squash commit, y su rama de origen será eliminada. Las solicitudes en borrador no se fusionarán hasta que se retire tal designación.

## Artículo Tercero — Causas de Cierre Obligatorio
Será cerrada sin fusión toda solicitud que: (a) modifique `.github/`; (b) modifique un instrumento de licencia o el README; (c) introduzca archivos prohibidos; (d) introduzca enlaces simbólicos o submódulos; (e) introduzca contenido binario; (f) exceda los límites del Artículo Cuarto.

## Artículo Cuarto — Limitaciones de Tamaño
No más de veinte archivos modificados; no más de quinientas líneas modificadas en total; no más de trescientas líneas por archivo. Los archivos de bloqueo y el contenido de `vendor/` quedan exentos del cómputo de líneas.

## Artículo Quinto — Prohibición de Envíos Directos
Nadie podrá enviar commits directamente a la rama `main`. Todo cambio se enviará exclusivamente mediante Solicitudes de Extracción. Los commits infractores serán revertidos y el incidente quedará registrado.

## Artículo Sexto — Exención de Responsabilidad
Este repositorio es un experimento de gobernanza automatizada y no debe interpretarse como modelo de buena práctica de ingeniería. Su contenido no está revisado. Ningún código debe ser ejecutado, desplegado ni utilizado. Cada contribuyente es el único responsable de aquello que somete.
