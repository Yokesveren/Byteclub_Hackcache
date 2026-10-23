"""
Builds web/public/graph/H01.json: the indoor map of Chinar District Hospital (demo) used by the
patient app (QR waypoint navigation) and the dashboard (crowd map). Run from web/:

    python scripts/build_h01_graph.py

Coordinates are plan pixels on a 1000 x 560 canvas per floor; 10 px = 1 m. Both floors share the
same coordinates, so the lift and the stairs line up. Corridor points with "sticker": false are
routing helpers with no QR on the wall; everything else is a printed sticker.
"""
import json, math, os

H = "H01"
CORRIDOR_Y = 305


def wid(floor, n):
    return f"SS:{H}:F{floor}:W{n:03d}"


def vid(floor, x):
    return f"SS:{H}:F{floor}:V{x:03d}"


nodes, edges, areas = [], [], []


def node(id, floor, x, y, name, tags, landmark=None, sticker=True):
    nodes.append({"id": id, "floor": floor, "x": x, "y": y, "sticker": sticker, "tags": tags,
                  "name": {"en": name}, "landmark": {"en": landmark} if landmark else None})


def edge(a, b, **attrs):
    na = next(n for n in nodes if n["id"] == a)
    nb = next(n for n in nodes if n["id"] == b)
    if na["floor"] != nb["floor"]:
        dist = attrs.pop("distance_m")
    else:
        dist = round(math.hypot(na["x"] - nb["x"], na["y"] - nb["y"]) / 10, 1)
    edges.append({"from": a, "to": b, "distance_m": dist, "stairs": attrs.get("stairs", False),
                  "lift": attrs.get("lift", False), "ramp": attrs.get("ramp", False),
                  "crowd_prone": attrs.get("crowd_prone", False)})


def area(id, floor, label, kind, x, y, w, h, capacity, waypoints):
    areas.append({"id": id, "floor": floor, "label": label, "kind": kind, "x": x, "y": y, "w": w, "h": h,
                  "capacity": capacity, "waypoints": waypoints})


# ------------------------------------------------------------------ ground floor (F0)
F = 0
node(wid(F, 1), F, 70, 500, "Main gate", ["entrance", "exit"], "Under the green hospital sign")
node(wid(F, 5), F, 120, 200, "Emergency (Casualty)", ["emergency"], "Red doors, open all day and night")
node(wid(F, 2), F, 150, CORRIDOR_Y, "Entrance hall", ["junction"], "Beside the visitor notice board")
node(wid(F, 3), F, 300, 260, "Registration counter", ["registration"], "Windows 1 to 4")
node(wid(F, 4), F, 430, 260, "Help desk", ["help"], "Blue counter with the help sign")
node(wid(F, 12), F, 560, 260, "Pharmacy", ["pharmacy"], "Medicine window")
node(wid(F, 13), F, 720, 260, "Laboratory", ["lab"], "Sample collection")
node(wid(F, 14), F, 870, 260, "X-ray", ["xray"], "Yellow radiation sign")
node(wid(F, 20), F, 310, 350, "Waiting area, ground floor", ["waiting"], "Rows of green chairs")
node(wid(F, 16), F, 440, 350, "Lift, ground floor", ["lift"], "Steel doors")
node(wid(F, 17), F, 510, 350, "Stairs, ground floor", ["stairs"])
node(wid(F, 7), F, 590, 350, "Room 7 (Paediatrics)", ["room"])
node(wid(F, 8), F, 680, 350, "Room 8 (Paediatrics)", ["room"])
node(wid(F, 10), F, 770, 350, "Room 10 (Maternal Health)", ["room"])
node(wid(F, 11), F, 860, 350, "Room 11 (Maternal Health)", ["room"])
node(wid(F, 15), F, 935, 350, "Toilets, ground floor", ["toilet"])
# corridor: sticker junctions and wall-free routing points in front of each door
F0_STOPS = [(150, wid(F, 2)), (300, None), (310, None), (430, wid(F, 6)), (440, None), (510, None),
            (560, None), (590, None), (680, wid(F, 9)), (720, None), (770, None), (860, wid(F, 18)),
            (870, None), (935, None)]
for x, sticker in F0_STOPS:
    if sticker and sticker != wid(F, 2):
        node(sticker, F, x, CORRIDOR_Y, {6: "Corridor by the help desk", 9: "Corridor centre", 18: "Corridor east end"}[int(sticker[-3:])],
             ["junction"], {6: "Green notice board", 9: "Water cooler", 18: "Fire extinguisher"}[int(sticker[-3:])])
    elif not sticker:
        node(vid(F, x), F, x, CORRIDOR_Y, "Corridor", ["corridor"], sticker=False)
f0_chain = [s or vid(F, x) for x, s in F0_STOPS]
for a, b in zip(f0_chain, f0_chain[1:]):
    edge(a, b, crowd_prone=True)
edge(wid(F, 1), wid(F, 2))
edge(wid(F, 5), wid(F, 2))
for door, x in [(3, 300), (20, 310), (4, 430), (16, 440), (17, 510), (12, 560), (7, 590), (8, 680),
                (13, 720), (10, 770), (11, 860), (14, 870), (15, 935)]:
    corridor = next(s or vid(F, xx) for xx, s in F0_STOPS if xx == x)
    edge(wid(F, door), corridor, crowd_prone=door in (3, 20, 12))

area("F0-emergency", F, "Emergency", "emergency", 40, 60, 160, 130, 15, [wid(F, 5)])
area("F0-hall", F, "Entrance hall", "hall", 40, 200, 160, 340, 25, [wid(F, 1), wid(F, 2)])
area("F0-registration", F, "Registration", "service", 220, 60, 150, 190, 25, [wid(F, 3)])
area("F0-help", F, "Help desk", "service", 380, 60, 100, 190, 8, [wid(F, 4)])
area("F0-pharmacy", F, "Pharmacy", "service", 490, 60, 140, 190, 15, [wid(F, 12)])
area("F0-lab", F, "Laboratory", "service", 640, 60, 150, 190, 12, [wid(F, 13)])
area("F0-xray", F, "X-ray", "service", 800, 60, 160, 190, 8, [wid(F, 14)])
area("F0-corridor", F, "Main corridor", "corridor", 200, 280, 760, 50, 20,
     [wid(F, 6), wid(F, 9), wid(F, 18)] + [vid(F, x) for x, s in F0_STOPS if not s])
area("F0-waiting", F, "Waiting area", "waiting", 220, 340, 180, 190, 40, [wid(F, 20)])
area("F0-lift", F, "Lift", "lift", 410, 340, 60, 70, 6, [wid(F, 16)])
area("F0-stairs", F, "Stairs", "stairs", 480, 340, 60, 70, 6, [wid(F, 17)])
area("F0-room7", F, "Room 7", "room", 550, 340, 80, 190, 8, [wid(F, 7)])
area("F0-room8", F, "Room 8", "room", 640, 340, 80, 190, 8, [wid(F, 8)])
area("F0-room10", F, "Room 10", "room", 730, 340, 80, 190, 8, [wid(F, 10)])
area("F0-room11", F, "Room 11", "room", 820, 340, 80, 190, 8, [wid(F, 11)])
area("F0-toilets", F, "Toilets", "toilet", 910, 340, 50, 190, 6, [wid(F, 15)])

# ------------------------------------------------------------------ first floor (F1)
F = 1
node(wid(F, 16), F, 440, 350, "Lift, first floor", ["lift"], "Steel doors")
node(wid(F, 17), F, 510, 350, "Stairs, first floor", ["stairs"])
node(wid(F, 103), F, 300, 260, "Room 3 (General Medicine)", ["room"], "Next to the green notice board")
node(wid(F, 104), F, 560, 260, "Room 4 (General Medicine)", ["room"])
node(wid(F, 105), F, 720, 260, "Room 5 (General Medicine)", ["room"])
node(wid(F, 114), F, 870, 260, "Room 14 (Orthopaedics)", ["room"])
node(wid(F, 111), F, 310, 350, "Waiting area, first floor", ["waiting"], "Rows of blue chairs")
node(wid(F, 115), F, 680, 350, "Room 15 (Orthopaedics)", ["room"])
node(wid(F, 116), F, 935, 350, "Toilets, first floor", ["toilet"])
F1_STOPS = [(300, wid(F, 110)), (310, None), (440, None), (510, wid(F, 112)), (560, None), (680, None),
            (720, wid(F, 113)), (870, None), (935, None)]
for x, sticker in F1_STOPS:
    if sticker:
        node(sticker, F, x, CORRIDOR_Y, {110: "Corridor west, first floor", 112: "Corridor by the stairs", 113: "Corridor east, first floor"}[int(sticker[-3:])],
             ["junction"], {110: "Green notice board", 112: "Window over the courtyard", 113: "Water cooler"}[int(sticker[-3:])])
    else:
        node(vid(F, x), F, x, CORRIDOR_Y, "Corridor", ["corridor"], sticker=False)
f1_chain = [s or vid(F, x) for x, s in F1_STOPS]
for a, b in zip(f1_chain, f1_chain[1:]):
    edge(a, b, crowd_prone=True)
for door, x in [(103, 300), (111, 310), (16, 440), (17, 510), (104, 560), (115, 680), (105, 720), (114, 870), (116, 935)]:
    corridor = next(s or vid(F, xx) for xx, s in F1_STOPS if xx == x)
    edge(wid(F, door), corridor, crowd_prone=door == 111)

area("F1-room3", F, "Room 3", "room", 220, 60, 150, 190, 10, [wid(F, 103)])
area("F1-room4", F, "Room 4", "room", 490, 60, 140, 190, 10, [wid(F, 104)])
area("F1-room5", F, "Room 5", "room", 640, 60, 150, 190, 10, [wid(F, 105)])
area("F1-room14", F, "Room 14", "room", 800, 60, 160, 190, 8, [wid(F, 114)])
area("F1-corridor", F, "First-floor corridor", "corridor", 220, 280, 740, 50, 18,
     [wid(F, 110), wid(F, 112), wid(F, 113)] + [vid(F, x) for x, s in F1_STOPS if not s])
area("F1-waiting", F, "Waiting area", "waiting", 220, 340, 180, 190, 30, [wid(F, 111)])
area("F1-lift", F, "Lift", "lift", 410, 340, 60, 70, 6, [wid(F, 16)])
area("F1-stairs", F, "Stairs", "stairs", 480, 340, 60, 70, 6, [wid(F, 17)])
area("F1-room15", F, "Room 15", "room", 640, 340, 80, 190, 8, [wid(F, 115)])
area("F1-toilets", F, "Toilets", "toilet", 910, 340, 50, 190, 6, [wid(F, 116)])

# ------------------------------------------------------------------ between floors
edge(wid(0, 16), wid(1, 16), lift=True, distance_m=6)
edge(wid(0, 17), wid(1, 17), stairs=True, distance_m=8)

graph = {
    "hospital_id": H,
    "name": "Chinar District Hospital (Demo)",
    "version": 1,
    "units": "Plan pixels; 10 px = 1 m. Edges are walkable both ways.",
    "floors": [
        {"floor": 0, "name": "Ground floor", "width": 1000, "height": 560},
        {"floor": 1, "name": "First floor", "width": 1000, "height": 560},
    ],
    "entrance": wid(0, 1),
    "emergency": wid(0, 5),
    "nodes": nodes,
    "edges": edges,
    "areas": areas,
}

# sanity: ids unique, edges reference nodes, every sticker reachable from the gate
ids = [n["id"] for n in nodes]
assert len(ids) == len(set(ids)), "duplicate node id"
for e in edges:
    assert e["from"] in ids and e["to"] in ids, e
adj = {i: set() for i in ids}
for e in edges:
    adj[e["from"]].add(e["to"]); adj[e["to"]].add(e["from"])
seen, stack = set(), [graph["entrance"]]
while stack:
    n = stack.pop()
    if n in seen:
        continue
    seen.add(n); stack.extend(adj[n])
assert seen == set(ids), f"unreachable: {set(ids) - seen}"
for a in areas:
    for w in a["waypoints"]:
        assert w in ids, (a["id"], w)

out = os.path.join(os.path.dirname(__file__), "..", "public", "graph", "H01.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf8") as f:
    json.dump(graph, f, indent=1, ensure_ascii=False)
stickers = sum(1 for n in nodes if n["sticker"])
print(f"wrote {out}: {len(nodes)} nodes ({stickers} stickers), {len(edges)} edges, {len(areas)} areas")
