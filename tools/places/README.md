# Catálogo de lugares y rodadas (Explorar)

Genera `app/src/main/assets/places/colombia.json`, que la app lee en **Rutas → Explorar**.

| Archivo | Qué hace |
|---|---|
| `curated.py` | Lista curada de ApexMoto: lugares famosos (con descripción y consejo para moto) y rodadas conocidas. **Aquí se agregan o corrigen lugares y rodadas.** |
| `fetch_osm.py` | Descarga de OpenStreetMap (Overpass) los lugares turísticos y las poblaciones de Colombia, por departamento. |
| `build_places.py` | Une las dos fuentes, corrige las coordenadas curadas con las de OSM cuando encuentra el mismo lugar, quita repetidos y escribe el JSON. |

## Actualizar

```bash
python tools/places/fetch_osm.py osm_co.json      # 2 a 15 minutos; el archivo crudo NO se versiona
python tools/places/build_places.py osm_co.json
```

Después compila y revisa Explorar. Haz commit solo de `curated.py` y del JSON generado.

## Licencia

Los datos de OpenStreetMap son © colaboradores de OpenStreetMap, bajo la [ODbL](https://opendatacommons.org/licenses/odbl/1-0/).
El JSON generado es una base de datos derivada y queda bajo ODbL; la app muestra el crédito en Explorar. Ver `THIRD_PARTY_NOTICES.md`.

## Criterios de la lista curada

- Lugares reales y conocidos; nada presentado como aliado o comercio de ApexMoto.
- Textos cortos y sin promesas que no podamos verificar (horarios, precios, estado de la vía).
- Consejos de seguridad concretos (niebla, destapado, orden público). La app recuerda revisar la vía (#767) antes de salir.

## Estado del catálogo (2026-11-15)

El `colombia.json` actual trae los datos de OpenStreetMap de **22 de 33** departamentos. La lista curada (lugares famosos y rodadas) sí cubre todo el país.

Faltan por descargar de OSM: Arauca, Bogotá D.C., Boyacá, Casanare, Chocó, Guaviare, Norte de Santander, Quindío, Risaralda, Santander y Vaupés.

Para completarlos, cuando los servidores de Overpass respondan: `python tools/places/fetch_osm.py <salida.json>` (retoma solo lo que falta si se conserva la carpeta `<salida.json>.partes`), luego `--meet` y `build_places.py`.
