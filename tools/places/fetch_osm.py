"""
Descarga de OpenStreetMap (Overpass) los lugares turísticos y poblaciones de Colombia, departamento por departamento
(una sola consulta para todo el país se pasa del tiempo máximo del servidor).
Uso:  python tools/places/fetch_osm.py <salida.json>            lugares turísticos y poblaciones
      python tools/places/fetch_osm.py --meet <salida.json>    puntos de encuentro (bombas, supermercados, centros comerciales,
                                                                peajes) y barrios
Los datos de OSM están bajo la licencia ODbL: © colaboradores de OpenStreetMap.
"""
import json, sys, time, urllib.parse, urllib.request

# Servidores públicos de Overpass: si uno no responde se prueba el siguiente.
URLS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
        "https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter", "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]
UA = {"User-Agent": "apexmoto-places-script"}

DEPTS = r"""
[out:json][timeout:120];
area["ISO3166-1"="CO"][admin_level=2]->.co;
rel(area.co)["admin_level"="4"]["boundary"="administrative"];
out tags;
"""

PLACES = r"""
[out:json][timeout:600];
area(%d)->.a;
(
  nwr["tourism"~"^(attraction|viewpoint|museum|theme_park|zoo)$"]["name"](area.a);
  nwr["natural"~"^(waterfall|hot_spring|beach)$"]["name"](area.a);
  nwr["waterway"="waterfall"]["name"](area.a);
  nwr["boundary"="national_park"]["name"](area.a);
  nwr["boundary"="protected_area"]["name"](area.a);
  nwr["leisure"="nature_reserve"]["name"](area.a);
  nwr["historic"~"^(monument|ruins|archaeological_site|castle|monastery)$"]["name"](area.a);
  node["place"~"^(city|town|village)$"]["name"](area.a);
);
out center tags;
"""

MEET = r"""
[out:json][timeout:600];
area(%d)->.a;
(
  nwr["amenity"="fuel"](area.a);
  nwr["shop"~"^(supermarket|mall)$"]["name"](area.a);
  nwr["amenity"="shopping_centre"]["name"](area.a);
  nwr["barrier"="toll_booth"](area.a);
  node["place"~"^(suburb|neighbourhood|quarter)$"]["name"](area.a);
);
out center tags;
"""


def overpass(q, tries=40):
    data = urllib.parse.urlencode({"data": q}).encode()
    for attempt in range(tries):
        url = URLS[attempt % len(URLS)]
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=UA), timeout=240) as r:
                body = json.loads(r.read())
            if "runtime error" in (body.get("remark") or ""):
                raise RuntimeError(body["remark"])
            return body
        except Exception as e:
            print("   intento", attempt + 1, url.split("/")[2], "falló:", str(e)[:120], flush=True)
            time.sleep(20)
    raise SystemExit("Overpass no respondió")


def main(out, query=PLACES):
    import os
    depts = [e for e in overpass(DEPTS)["elements"] if e["type"] == "relation"]
    print(len(depts), "departamentos", flush=True)
    # Cada departamento se guarda apenas llega, para retomar si se corta (carpeta <salida>.partes).
    parts = out + ".partes"  # noqa
    os.makedirs(parts, exist_ok=True)
    elements = []
    for i, d in enumerate(depts):
        name = d["tags"].get("name", "")
        part = os.path.join(parts, f"{d['id']}.json")
        if os.path.exists(part):
            res = json.load(open(part, encoding="utf-8"))
        else:
            # Dos consultas livianas (puntos; vías y relaciones) en vez de una pesada: los servidores públicos cortan a los ~3 min.
            area = 3600000000 + d["id"]
            res = {"elements": []}
            for kind in ("node", "wr"):
                q = (query % area).replace("[timeout:600]", "[timeout:200]")
                q = q.replace("nwr[", kind + "[") if kind == "node" else q.replace("nwr[", "nw_r[")
                if kind == "wr":
                    # Vías y relaciones: se repite cada filtro para way y relation; los nodos de población no aplican.
                    lines = [l for l in q.splitlines() if not l.strip().startswith("node[")]
                    out_lines = []
                    for l in lines:
                        if l.strip().startswith("nw_r["):
                            out_lines += [l.replace("nw_r[", "way["), l.replace("nw_r[", "relation[")]
                        else:
                            out_lines.append(l)
                    q = "\n".join(out_lines)
                res["elements"] += overpass(q)["elements"]
            json.dump(res, open(part, "w", encoding="utf-8"), ensure_ascii=False)
            time.sleep(5)
        # Mismo formato de antes: el departamento (sin center) seguido de sus elementos.
        elements.append({"type": "relation", "id": d["id"], "tags": d["tags"]})
        elements.extend(res["elements"])
        print(f"{i + 1}/{len(depts)} {name}: {len(res['elements'])}", flush=True)
    json.dump({"elements": elements}, open(out, "w", encoding="utf-8"), ensure_ascii=False)
    print("ok", len(elements), "elementos")


if __name__ == "__main__":
    if sys.argv[1] == "--meet":
        main(sys.argv[2], MEET)
    else:
        main(sys.argv[1])
