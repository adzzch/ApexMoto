"""
Genera app/src/main/assets/places/colombia.json: lugares para rodar y rodadas famosas de Colombia.

Une dos fuentes:
  1. curated.py  · lista curada de ApexMoto (lugares famosos con descripción y consejo, y rodadas).
  2. OpenStreetMap · todos los lugares turísticos con nombre (miradores, cascadas, museos, parques, reservas…) y las
     poblaciones (para saber en qué ciudad está la persona). Se descargan con fetch_osm.py.

Uso:
  python tools/places/fetch_osm.py osm_co.json
  python tools/places/fetch_osm.py --meet osm_meet.json
  python tools/places/build_places.py osm_co.json [osm_meet.json]

Los datos de OpenStreetMap están bajo la licencia ODbL: © colaboradores de OpenStreetMap. El archivo generado es una
base de datos derivada y queda bajo ODbL (ver THIRD_PARTY_NOTICES.md).
"""
import json, math, os, re, sys, unicodedata, datetime

sys.path.insert(0, os.path.dirname(__file__))
from curated import PLACES, RIDES  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "places", "colombia.json")

DEPT_FIX = {
    "Bogotá, Distrito Capital": "Bogotá", "Bogotá Distrito Capital": "Bogotá", "Bogotá D.C.": "Bogotá",
    "Archipiélago de San Andrés, Providencia y Santa Catalina": "San Andrés y Providencia",
    "San Andrés, Providencia y Santa Catalina": "San Andrés y Providencia",
}
KINDS = {"mirador", "cascada", "pueblo", "naturaleza", "cultura", "agua", "termales", "playa", "parque", "religioso", "desierto", "atractivo"}


def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9 ]+", " ", s).strip()


STOPWORDS = {"de", "del", "la", "las", "el", "los", "y", "parque", "nacional", "natural", "reserva", "municipal"}


def key(s):
    return " ".join(w for w in norm(s).split() if w not in STOPWORDS)


def km(a_lat, a_lng, b_lat, b_lng):
    r = 6371.0
    p1, p2 = math.radians(a_lat), math.radians(b_lat)
    dp, dl = p2 - p1, math.radians(b_lng - a_lng)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


class Grid:
    """Índice espacial simple (celdas de ~0,25°) para buscar lo más cercano rápido."""
    def __init__(self, items, cell=0.25):
        self.cell, self.cells = cell, {}
        for it in items:
            self.cells.setdefault(self._c(it["lat"], it["lng"]), []).append(it)

    def _c(self, lat, lng):
        return (int(math.floor(lat / self.cell)), int(math.floor(lng / self.cell)))

    def near(self, lat, lng, radius_km):
        ci, cj = self._c(lat, lng)
        r = int(math.ceil(radius_km / (111 * self.cell))) + 1
        out = []
        for i in range(ci - r, ci + r + 1):
            for j in range(cj - r, cj + r + 1):
                for it in self.cells.get((i, j), ()):
                    d = km(lat, lng, it["lat"], it["lng"])
                    if d <= radius_km:
                        out.append((d, it))
        out.sort(key=lambda x: x[0])
        return out


def classify(t):
    tour, nat, hist = t.get("tourism"), t.get("natural"), t.get("historic")
    if nat == "waterfall": return "cascada"
    if nat == "hot_spring": return "termales"
    if nat == "beach": return "playa"
    if tour == "viewpoint": return "mirador"
    if tour == "museum" or hist: return "cultura"
    if tour in ("theme_park", "zoo"): return "parque"
    if t.get("boundary") in ("national_park", "protected_area") or t.get("leisure") == "nature_reserve": return "naturaleza"
    if t.get("amenity") == "place_of_worship" or t.get("building") in ("church", "cathedral", "chapel"): return "religioso"
    if tour == "attraction": return "atractivo"
    return None


def short_desc(t):
    d = t.get("description:es") or t.get("description") or ""
    d = re.sub(r"\s+", " ", d).strip()
    return d if 0 < len(d) <= 220 else ""


def load_osm(path):
    raw = json.load(open(path, encoding="utf-8"))
    dept, seen, towns, villages, pois = None, set(), [], [], []
    for e in raw["elements"]:
        t = e.get("tags", {})
        if e["type"] == "relation" and t.get("admin_level") == "4" and t.get("boundary") == "administrative" and "center" not in e:
            dept = DEPT_FIX.get(t.get("name", ""), t.get("name", ""))
            continue
        uid = e["type"][0] + str(e["id"])
        if uid in seen:
            continue
        seen.add(uid)
        lat = e.get("lat", e.get("center", {}).get("lat"))
        lng = e.get("lon", e.get("center", {}).get("lon"))
        name = (t.get("name:es") or t.get("name") or "").strip()
        if lat is None or not name or len(name) < 3:
            continue
        item = {"id": "o" + uid, "name": name, "lat": round(lat, 5), "lng": round(lng, 5), "dept": dept or ""}
        place = t.get("place")
        if place in ("city", "town", "village"):
            pop = re.sub(r"[^0-9]", "", t.get("population", "")) or "0"
            level = {"city": 2, "town": 1}.get(place, 0)
            (towns if level else villages).append({**item, "pop": int(pop[:9]), "level": level})
        else:
            k = classify(t)
            if k:
                pois.append({**item, "kind": k, "desc": short_desc(t)})
    return towns, villages, pois


SUPER_NAME = re.compile(r"(d1|ara|exito|éxito|olimpica|olímpica|jumbo|carulla|metro|la 14|makro|pricesmart|super ?inter|mercaldas|colsubsidio|"
                        r"comfandi|alkosto|surtimax|merkepaisa|euro|consumo|cooratiendas|la vaquita|mercamio|super ?a)", re.I)


def load_meet(path, town_grid):
    """Puntos de encuentro seguros: estaciones de servicio, supermercados de cadena, centros comerciales y peajes."""
    raw = json.load(open(path, encoding="utf-8"))
    dept, seen, areas, out = None, set(), [], []
    for e in raw["elements"]:
        t = e.get("tags", {})
        if e["type"] == "relation" and t.get("admin_level") == "4" and "center" not in e:
            dept = DEPT_FIX.get(t.get("name", ""), t.get("name", "")); continue
        uid = e["type"][0] + str(e["id"])
        if uid in seen: continue
        seen.add(uid)
        lat = e.get("lat", e.get("center", {}).get("lat")); lng = e.get("lon", e.get("center", {}).get("lon"))
        if lat is None: continue
        name = (t.get("name") or "").strip(); brand = (t.get("brand") or "").strip()
        if t.get("place") in ("suburb", "neighbourhood", "quarter"):
            if name: areas.append({"name": name, "lat": lat, "lng": lng})
            continue
        if t.get("amenity") == "fuel":
            kind = "bomba"
            if not name: name = (brand + " " if brand else "") + "Estación de servicio"
            elif brand and brand.lower() not in name.lower(): name = f"{brand} {name}"
        elif t.get("shop") == "supermarket":
            if not (SUPER_NAME.search(name) or SUPER_NAME.search(brand)): continue
            kind = "super"
        elif t.get("shop") == "mall" or t.get("amenity") == "shopping_centre":
            kind = "centro"
        elif t.get("barrier") == "toll_booth":
            kind = "peaje"
            if not name: continue
            if "peaje" not in name.lower(): name = "Peaje " + name
        else:
            continue
        out.append({"id": "m" + uid, "name": name[:80], "kind": kind, "dept": dept or "", "lat": round(lat, 5), "lng": round(lng, 5)})
    area_grid = Grid(areas, cell=0.05)
    for m in out:
        a = area_grid.near(m["lat"], m["lng"], 1.5)
        m["area"] = a[0][1]["name"] if a else ""
        t = town_grid.near(m["lat"], m["lng"], 15)
        m["town"] = t[0][1]["name"] if t else ""
    # Repetidos (mismo nombre a menos de 150 m): queda uno.
    g, kept = Grid([], cell=0.05), []
    for m in out:
        if not any(q["name"] == m["name"] for _, q in g.near(m["lat"], m["lng"], 0.15)):
            kept.append(m); g.cells.setdefault(g._c(m["lat"], m["lng"]), []).append(m)
    return kept


def main(osm_path, meet_path=None):
    towns, villages, pois = load_osm(osm_path)
    print(f"OSM: {len(towns)} ciudades/pueblos, {len(villages)} veredas/caseríos, {len(pois)} lugares")

    # Lugares de OSM repetidos (mismo nombre a menos de 2 km): queda el primero.
    g, kept = Grid([]), []
    for p in pois:
        dup = any(key(q["name"]) == key(p["name"]) for _, q in g.near(p["lat"], p["lng"], 2))
        if not dup:
            kept.append(p)
            g.cells.setdefault(g._c(p["lat"], p["lng"]), []).append(p)
    pois = kept

    poi_grid, town_grid = Grid(pois), Grid(towns + villages)
    named = Grid(towns)

    # Lugares curados: se corrigen con la coordenada de OSM si aparece el mismo lugar cerca.
    used, curated = set(), []
    for i, (name, kind, dept, lat, lng, desc, tip) in enumerate(PLACES):
        assert kind in KINDS, (name, kind)
        k = key(name)
        cands = town_grid.near(lat, lng, 20) if kind == "pueblo" else poi_grid.near(lat, lng, 20)
        hit = next((it for _, it in cands if k and (key(it["name"]) == k or (len(k) >= 5 and (k in key(it["name"]) or key(it["name"]) in k) and len(key(it["name"])) >= 5))), None)
        if hit:
            lat, lng = hit["lat"], hit["lng"]
            used.add(hit["id"])
        curated.append({"id": f"c{i}", "name": name, "kind": kind, "dept": dept, "lat": lat, "lng": lng, "desc": desc, "tip": tip, "famous": 1, "snap": 1 if hit else 0})
    print(f"Curados: {len(curated)} ({sum(c['snap'] for c in curated)} con coordenada de OSM)")
    # Lugares de OSM que repiten uno curado (mismo nombre a menos de 5 km) también salen.
    cur_grid = Grid(curated)
    def repeats_curated(p):
        return any(key(c["name"]) == key(p["name"]) or (len(key(p["name"])) >= 6 and key(p["name"]) in key(c["name"])) for _, c in cur_grid.near(p["lat"], p["lng"], 5))
    pois = [p for p in pois if p["id"] not in used and not repeats_curated(p)]

    # Cada lugar dice cerca de qué población está.
    def town_of(lat, lng):
        n = town_grid.near(lat, lng, 12)
        if n: return n[0][1]["name"]
        n = named.near(lat, lng, 60)
        return n[0][1]["name"] if n else ""
    for p in curated + pois:
        p["town"] = town_of(p["lat"], p["lng"])

    # Rodadas: cada punto toma la coordenada del lugar curado del mismo nombre o de la población de OSM.
    by_name = {key(c["name"]): c for c in curated}
    rides = []
    for i, r in enumerate(RIDES):
        assert r["level"] in ("facil", "media", "exigente") and r["road"] in ("pavimentada", "mixta", "destapada"), r["name"]
        pts = []
        for (n, lat, lng) in r["pts"]:
            c = by_name.get(key(n))
            if c and km(lat, lng, c["lat"], c["lng"]) < 25:
                lat, lng = c["lat"], c["lng"]
            else:
                hit = next((it for _, it in named.near(lat, lng, 25) if key(it["name"]) == key(n)), None)
                if hit: lat, lng = hit["lat"], hit["lng"]
            pts.append([n, round(lat, 5), round(lng, 5)])
        rides.append({"id": f"r{i}", "name": r["name"], "dept": r["dept"], "level": r["level"], "road": r["road"], "km": r["km"],
                      "hours": r["hours"], "desc": r["desc"], "tip": r["tip"], "pts": pts})

    places = sorted(curated, key=lambda p: p["id"]) + sorted(pois, key=lambda p: (p["dept"], p["name"]))
    meet = load_meet(meet_path, Grid(towns, cell=0.25)) if meet_path else []
    if meet_path:
        mk = {}
        for m in meet: mk[m["kind"]] = mk.get(m["kind"], 0) + 1
        print(f"Puntos de encuentro: {len(meet)} {mk}")
    out = {
        "v": 1,
        "generated": datetime.date.today().isoformat(),
        "attribution": "Lugares: lista de ApexMoto y © colaboradores de OpenStreetMap (ODbL).",
        # Poblaciones: [nombre, departamento, lat, lng, habitantes, nivel (2 ciudad, 1 municipio/pueblo, 0 vereda o caserío)]
        "towns": [[t["name"], t["dept"], t["lat"], t["lng"], t["pop"], t["level"]] for t in sorted(towns + villages, key=lambda t: (t["dept"], t["name"]))],
        # Lugares: [id, nombre, tipo, departamento, cerca de, lat, lng, descripción, consejo, famoso]
        "places": [[p["id"], p["name"], p["kind"], p["dept"], p["town"], p["lat"], p["lng"], p.get("desc", ""), p.get("tip", ""), p.get("famous", 0)] for p in places],
        "rides": rides,
        # Puntos de encuentro: [id, nombre, tipo, departamento, municipio, barrio, lat, lng]
        "meet": [[m["id"], m["name"], m["kind"], m["dept"], m["town"], m["area"], m["lat"], m["lng"]] for m in sorted(meet, key=lambda m: (m["dept"], m["name"]))],
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    by_kind = {}
    for p in places: by_kind[p["kind"]] = by_kind.get(p["kind"], 0) + 1
    print(f"Escrito {OUT}: {len(out['towns'])} poblaciones, {len(places)} lugares {by_kind}, {len(rides)} rodadas, {os.path.getsize(OUT) // 1024} KB")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
