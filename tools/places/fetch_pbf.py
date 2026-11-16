"""
Alternativa a fetch_osm.py cuando los servidores de Overpass no responden: lee el extracto de Colombia de Geofabrik
(un solo archivo .osm.pbf) y escribe los dos archivos que usa build_places.py, con el mismo formato que Overpass.

Uso:
  curl -L -o colombia.osm.pbf https://download.geofabrik.de/south-america/colombia-latest.osm.pbf
  pip install osmium shapely
  python tools/places/fetch_pbf.py colombia.osm.pbf osm_co.json osm_meet.json
  python tools/places/build_places.py osm_co.json osm_meet.json

Los datos de OSM están bajo la licencia ODbL: © colaboradores de OpenStreetMap.
"""
import json, re, sys, time

import osmium
import shapely
from osmium.filter import KeyFilter

# Los mismos filtros de fetch_osm.py (PLACES y MEET).
TOURISM = re.compile(r"^(attraction|viewpoint|museum|theme_park|zoo)$")
NATURAL = re.compile(r"^(waterfall|hot_spring|beach)$")
HISTORIC = re.compile(r"^(monument|ruins|archaeological_site|castle|monastery)$")


def is_place(t, node):
    if not t.get("name"):
        return False
    return bool(
        TOURISM.match(t.get("tourism", "")) or NATURAL.match(t.get("natural", "")) or t.get("waterway") == "waterfall"
        or t.get("boundary") in ("national_park", "protected_area") or t.get("leisure") == "nature_reserve"
        or HISTORIC.match(t.get("historic", ""))
        or (node and t.get("place") in ("city", "town", "village"))
    )


def is_meet(t, node):
    name = t.get("name")
    return bool(
        t.get("amenity") == "fuel" or t.get("barrier") == "toll_booth"
        or (name and (t.get("shop") in ("supermarket", "mall") or t.get("amenity") == "shopping_centre"))
        or (node and name and t.get("place") in ("suburb", "neighbourhood", "quarter"))
    )


def main(pbf, out_places, out_meet):
    t0 = time.time()
    depts, places, meet = [], [], []
    wkb = osmium.geom.WKBFactory()
    keys = ("tourism", "natural", "waterway", "boundary", "leisure", "historic", "place", "amenity", "shop", "barrier", "admin_level")
    fp = osmium.FileProcessor(pbf).with_locations().with_areas().with_filter(KeyFilter(*keys))
    for o in fp:
        t = dict(o.tags)
        if o.is_node():
            node, typ, oid = True, "node", o.id
            if not o.location.valid():
                continue
            lat, lon = o.location.lat, o.location.lon
        elif o.is_area():
            node, typ, oid = False, ("way" if o.from_way() else "relation"), o.orig_id()
            if typ == "relation" and t.get("admin_level") == "4" and t.get("boundary") == "administrative" \
                    and t.get("ISO3166-2", "").startswith("CO-"):
                try:
                    depts.append((t, shapely.from_wkb(bytes.fromhex(wkb.create_multipolygon(o)))))
                except Exception as e:  # geometría rota: se avisa y se sigue
                    print("departamento sin geometría:", t.get("name"), e)
                continue
            pts = [(n.lat, n.lon) for ring in o.outer_rings() for n in ring if n.location.valid()]
            if not pts:
                continue
            lat = (min(p[0] for p in pts) + max(p[0] for p in pts)) / 2
            lon = (min(p[1] for p in pts) + max(p[1] for p in pts)) / 2
        elif o.is_way() and not o.is_closed():  # las vías cerradas ya llegan como área
            node, typ, oid = False, "way", o.id
            pts = [(n.lat, n.lon) for n in o.nodes if n.location.valid()]
            if not pts:
                continue
            lat = (min(p[0] for p in pts) + max(p[0] for p in pts)) / 2
            lon = (min(p[1] for p in pts) + max(p[1] for p in pts)) / 2
        else:
            continue
        if is_place(t, node) or is_meet(t, node):
            e = {"type": typ, "id": oid, "tags": t}
            if node: e.update(lat=lat, lon=lon)
            else: e["center"] = {"lat": lat, "lon": lon}
            (places if is_place(t, node) else meet).append(e)
            if is_place(t, node) and is_meet(t, node): meet.append(e)
    print(f"{len(depts)} departamentos, {len(places)} lugares, {len(meet)} puntos de encuentro ({time.time() - t0:.0f} s)")

    # Cada elemento va al departamento que lo contiene; lo que cae fuera de Colombia se descarta.
    for out, items in ((out_places, places), (out_meet, meet)):
        xs = [e.get("lon", e.get("center", {}).get("lon")) for e in items]
        ys = [e.get("lat", e.get("center", {}).get("lat")) for e in items]
        taken, elements = [False] * len(items), []
        for tags, geom in sorted(depts, key=lambda d: d[0].get("name", "")):
            shapely.prepare(geom)
            inside = shapely.contains_xy(geom, xs, ys)
            elements.append({"type": "relation", "id": 0, "tags": {"admin_level": "4", "boundary": "administrative", "name": tags.get("name", "")}})
            n = 0
            for i, ok in enumerate(inside):
                if ok and not taken[i]:
                    taken[i] = True; elements.append(items[i]); n += 1
            print(f"   {tags.get('name')}: {n}")
        json.dump({"elements": elements}, open(out, "w", encoding="utf-8"), ensure_ascii=False)
        print(out, len(elements), "elementos;", taken.count(False), "fuera de Colombia")


if __name__ == "__main__":
    main(*sys.argv[1:4])
