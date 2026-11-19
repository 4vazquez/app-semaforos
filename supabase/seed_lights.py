#!/usr/bin/env python3
"""Genera 002_seed_lights.sql desde el GeoJSON de OpenStreetMap.

La base semilla (1 579 semáforos del área metropolitana de Monterrey)
entra a Supabase como VERIFIED, con el ID de nodo de OSM como id
(estable entre exportaciones). Ejecutar:

    python3 supabase/seed_lights.py
    # luego pegar supabase/002_seed_lights.sql en el SQL Editor

El GeoJSON vive en android/app/src/main/assets/semaforos_mty.geojson
(el mismo que usa la app en modo local).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GEOJSON = ROOT / "android/app/src/main/assets/semaforos_mty.geojson"
OUT = Path(__file__).resolve().parent / "002_seed_lights.sql"


def esc(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def main() -> None:
    data = json.loads(GEOJSON.read_text())
    features = data.get("features", [])
    rows = []
    for i, f in enumerate(features):
        geom = f.get("geometry") or {}
        if geom.get("type") != "Point":
            continue
        coords = geom.get("coordinates") or []
        if len(coords) < 2:
            continue
        lon, lat = float(coords[0]), float(coords[1])
        if not (25.0 <= lat <= 26.5 and -101.0 <= lon <= -99.5):
            continue
        oid = f.get("id") or f"osm-{i}"
        rows.append(
            "  ({}, {:.6f}, {:.6f}, NULL, 'Monterrey', 'VERIFIED')".format(
                esc(str(oid)), lat, lon
            )
        )
    with open(OUT, "w") as fh:
        fh.write("-- Semilla de semáforos OSM ({} filas, VERIFIED).\n".format(len(rows)))
        fh.write("-- Generado por seed_lights.py; no editar a mano.\n")
        fh.write("insert into public.traffic_lights\n")
        fh.write("  (id, latitude, longitude, name, city, status)\nvalues\n")
        fh.write(",\n".join(rows))
        fh.write("\non conflict (id) do nothing;\n")
    print(f"{len(rows)} semáforos -> {OUT}")


if __name__ == "__main__":
    sys.exit(main())
