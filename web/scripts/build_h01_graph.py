"""
Builds the indoor map of Chinar District Hospital (demo), H01, used by the patient app (QR waypoint navigation)
and the dashboard (crowd map). Run from web/:

    python scripts/build_h01_graph.py

Coordinates are plan pixels on a 1000 x 560 canvas per floor; 10 px = 1 m. Both floors share the
same coordinates, so the lift and the stairs line up. Corridor points with "sticker": false are
routing helpers with no QR on the wall; everything else is a printed sticker.
"""
from graph_common import Map

m = Map("H01", "Chinar District Hospital (Demo)")
w, v = m.wid, m.vid
CORRIDOR_Y = 305
m.floor(0, "Ground floor")
m.floor(1, "First floor")

# ------------------------------------------------------------------ ground floor (F0)
F = 0
m.node(w(F, 1), F, 70, 500, "Main gate", ["entrance", "exit"], "Under the green hospital sign")
m.node(w(F, 5), F, 120, 200, "Emergency (Casualty)", ["emergency"], "Red doors, open all day and night")
m.node(w(F, 2), F, 150, CORRIDOR_Y, "Entrance hall", ["junction"], "Beside the visitor notice board")
m.node(w(F, 3), F, 300, 260, "Registration counter", ["registration"], "Windows 1 to 4")
m.node(w(F, 4), F, 430, 260, "Help desk", ["help"], "Blue counter with the help sign")
m.node(w(F, 12), F, 560, 260, "Pharmacy", ["pharmacy"], "Medicine window")
m.node(w(F, 13), F, 720, 260, "Laboratory", ["lab"], "Sample collection")
m.node(w(F, 14), F, 870, 260, "X-ray", ["xray"], "Yellow radiation sign")
m.node(w(F, 20), F, 310, 350, "Waiting area, ground floor", ["waiting"], "Rows of green chairs")
m.node(w(F, 16), F, 440, 350, "Lift, ground floor", ["lift"], "Steel doors")
m.node(w(F, 17), F, 510, 350, "Stairs, ground floor", ["stairs"])
m.node(w(F, 7), F, 590, 350, "Room 7 (Paediatrics)", ["room"])
m.node(w(F, 8), F, 680, 350, "Room 8 (Paediatrics)", ["room"])
m.node(w(F, 10), F, 770, 350, "Room 10 (Maternal Health)", ["room"])
m.node(w(F, 11), F, 860, 350, "Room 11 (Maternal Health)", ["room"])
m.node(w(F, 15), F, 935, 350, "Toilets, ground floor", ["toilet"])
f0 = m.corridor(F, CORRIDOR_Y, [
    (150, w(F, 2), None, None), (300, None, None, None), (310, None, None, None),
    (430, w(F, 6), "Corridor by the help desk", "Green notice board"), (440, None, None, None), (510, None, None, None),
    (560, None, None, None), (590, None, None, None), (680, w(F, 9), "Corridor centre", "Water cooler"),
    (720, None, None, None), (770, None, None, None), (860, w(F, 18), "Corridor east end", "Fire extinguisher"),
    (870, None, None, None), (935, None, None, None),
])
m.edge(w(F, 1), w(F, 2))
m.edge(w(F, 5), w(F, 2))
for door, x in [(3, 300), (20, 310), (4, 430), (16, 440), (17, 510), (12, 560), (7, 590), (8, 680),
                (13, 720), (10, 770), (11, 860), (14, 870), (15, 935)]:
    m.edge(w(F, door), f0[x], crowd_prone=door in (3, 20, 12))

m.area("F0-emergency", F, "Emergency", "emergency", 40, 60, 160, 130, 15, [w(F, 5)])
m.area("F0-hall", F, "Entrance hall", "hall", 40, 200, 160, 340, 25, [w(F, 1), w(F, 2)])
m.area("F0-registration", F, "Registration", "service", 220, 60, 150, 190, 25, [w(F, 3)])
m.area("F0-help", F, "Help desk", "service", 380, 60, 100, 190, 8, [w(F, 4)])
m.area("F0-pharmacy", F, "Pharmacy", "service", 490, 60, 140, 190, 15, [w(F, 12)])
m.area("F0-lab", F, "Laboratory", "service", 640, 60, 150, 190, 12, [w(F, 13)])
m.area("F0-xray", F, "X-ray", "service", 800, 60, 160, 190, 8, [w(F, 14)])
m.area("F0-corridor", F, "Main corridor", "corridor", 200, 280, 760, 50, 20,
       [w(F, 6), w(F, 9), w(F, 18)] + [c for c in f0.values() if ":V" in c])
m.area("F0-waiting", F, "Waiting area", "waiting", 220, 340, 180, 190, 40, [w(F, 20)])
m.area("F0-lift", F, "Lift", "lift", 410, 340, 60, 70, 6, [w(F, 16)])
m.area("F0-stairs", F, "Stairs", "stairs", 480, 340, 60, 70, 6, [w(F, 17)])
m.area("F0-room7", F, "Room 7", "room", 550, 340, 80, 190, 8, [w(F, 7)])
m.area("F0-room8", F, "Room 8", "room", 640, 340, 80, 190, 8, [w(F, 8)])
m.area("F0-room10", F, "Room 10", "room", 730, 340, 80, 190, 8, [w(F, 10)])
m.area("F0-room11", F, "Room 11", "room", 820, 340, 80, 190, 8, [w(F, 11)])
m.area("F0-toilets", F, "Toilets", "toilet", 910, 340, 50, 190, 6, [w(F, 15)])

# ------------------------------------------------------------------ first floor (F1)
F = 1
m.node(w(F, 16), F, 440, 350, "Lift, first floor", ["lift"], "Steel doors")
m.node(w(F, 17), F, 510, 350, "Stairs, first floor", ["stairs"])
m.node(w(F, 103), F, 300, 260, "Room 3 (General Medicine)", ["room"], "Next to the green notice board")
m.node(w(F, 104), F, 560, 260, "Room 4 (General Medicine)", ["room"])
m.node(w(F, 105), F, 720, 260, "Room 5 (General Medicine)", ["room"])
m.node(w(F, 114), F, 870, 260, "Room 14 (Orthopaedics)", ["room"])
m.node(w(F, 111), F, 310, 350, "Waiting area, first floor", ["waiting"], "Rows of blue chairs")
m.node(w(F, 115), F, 680, 350, "Room 15 (Orthopaedics)", ["room"])
m.node(w(F, 116), F, 935, 350, "Toilets, first floor", ["toilet"])
f1 = m.corridor(F, CORRIDOR_Y, [
    (300, w(F, 110), "Corridor west, first floor", "Green notice board"), (310, None, None, None), (440, None, None, None),
    (510, w(F, 112), "Corridor by the stairs", "Window over the courtyard"), (560, None, None, None), (680, None, None, None),
    (720, w(F, 113), "Corridor east, first floor", "Water cooler"), (870, None, None, None), (935, None, None, None),
])
for door, x in [(103, 300), (111, 310), (16, 440), (17, 510), (104, 560), (115, 680), (105, 720), (114, 870), (116, 935)]:
    m.edge(w(F, door), f1[x], crowd_prone=door == 111)

m.area("F1-room3", F, "Room 3", "room", 220, 60, 150, 190, 10, [w(F, 103)])
m.area("F1-room4", F, "Room 4", "room", 490, 60, 140, 190, 10, [w(F, 104)])
m.area("F1-room5", F, "Room 5", "room", 640, 60, 150, 190, 10, [w(F, 105)])
m.area("F1-room14", F, "Room 14", "room", 800, 60, 160, 190, 8, [w(F, 114)])
m.area("F1-corridor", F, "First-floor corridor", "corridor", 220, 280, 740, 50, 18,
       [w(F, 110), w(F, 112), w(F, 113)] + [c for c in f1.values() if ":V" in c])
m.area("F1-waiting", F, "Waiting area", "waiting", 220, 340, 180, 190, 30, [w(F, 111)])
m.area("F1-lift", F, "Lift", "lift", 410, 340, 60, 70, 6, [w(F, 16)])
m.area("F1-stairs", F, "Stairs", "stairs", 480, 340, 60, 70, 6, [w(F, 17)])
m.area("F1-room15", F, "Room 15", "room", 640, 340, 80, 190, 8, [w(F, 115)])
m.area("F1-toilets", F, "Toilets", "toilet", 910, 340, 50, 190, 6, [w(F, 116)])

# ------------------------------------------------------------------ between floors
m.edge(w(0, 16), w(1, 16), lift=True, distance_m=6)
m.edge(w(0, 17), w(1, 17), stairs=True, distance_m=8)

m.write(entrance=w(0, 1), emergency=w(0, 5))
