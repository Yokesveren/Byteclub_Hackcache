"""
Builds the indoor map of Jhelum Sub-District Hospital (demo), H02: one floor, an L of rooms off a verandah
corridor, entered by a ramp from the gate. Run from web/:  python scripts/build_h02_graph.py
"""
from graph_common import Map

m = Map("H02", "Jhelum Sub-District Hospital (Demo)")
w = m.wid
F, Y = 0, 300
m.floor(F, "Ground floor")

m.node(w(F, 1), F, 80, 500, "Main gate", ["entrance", "exit"], "Under the green hospital sign")
m.node(w(F, 2), F, 160, Y, "Entrance hall", ["junction"], "Ramp beside the steps")
m.node(w(F, 5), F, 140, 160, "Emergency (Casualty)", ["emergency"], "Red doors, open all day and night")
m.node(w(F, 3), F, 300, 230, "Registration counter", ["registration"], "Window with the red cross")
m.node(w(F, 4), F, 420, 230, "Help desk", ["help"], "Notice board with the doctors' list")
m.node(w(F, 12), F, 560, 230, "Pharmacy", ["pharmacy"], "Medicine window")
m.node(w(F, 13), F, 700, 230, "Laboratory", ["lab"], "Sample collection")
m.node(w(F, 20), F, 300, 380, "Waiting area", ["waiting"], "Wooden bench under the clock")
m.node(w(F, 21), F, 460, 380, "Room 1 (General Medicine)", ["room"], "Blue door with the room number")
m.node(w(F, 22), F, 600, 380, "Room 2 (General Medicine)", ["room"], "Blue door with the room number")
m.node(w(F, 23), F, 740, 380, "Room 3 (Paediatrics)", ["room"], "Blue door with the room number")
m.node(w(F, 24), F, 860, 230, "Room 4 (Maternal Health)", ["room"], "Blue door with the room number")
m.node(w(F, 15), F, 880, 380, "Toilets", ["toilet"])
c = m.corridor(F, Y, [
    (160, w(F, 2), None, None), (300, None, None, None), (420, w(F, 6), "Verandah", "Chinar tree in the courtyard"),
    (460, None, None, None), (560, None, None, None), (600, None, None, None), (700, w(F, 9), "Corridor centre", "Water cooler"),
    (740, None, None, None), (860, None, None, None), (880, None, None, None),
])
m.edge(w(F, 1), w(F, 2), ramp=True)
m.edge(w(F, 5), w(F, 2))
for door, x in [(3, 300), (20, 300), (4, 420), (21, 460), (12, 560), (22, 600), (13, 700), (23, 740), (24, 860), (15, 880)]:
    m.edge(w(F, door), c[x], crowd_prone=door in (3, 20))

m.area("F0-emergency", F, "Emergency", "emergency", 40, 60, 200, 150, 12, [w(F, 5)])
m.area("F0-hall", F, "Entrance hall", "hall", 40, 220, 200, 320, 20, [w(F, 1), w(F, 2)])
m.area("F0-registration", F, "Registration", "service", 250, 60, 110, 200, 20, [w(F, 3)])
m.area("F0-help", F, "Help desk", "service", 370, 60, 110, 200, 6, [w(F, 4)])
m.area("F0-pharmacy", F, "Pharmacy", "service", 490, 60, 130, 200, 12, [w(F, 12)])
m.area("F0-lab", F, "Laboratory", "service", 630, 60, 140, 200, 10, [w(F, 13)])
m.area("F0-room4", F, "Room 4", "room", 780, 60, 180, 200, 8, [w(F, 24)])
m.area("F0-corridor", F, "Verandah", "corridor", 240, 275, 720, 50, 20, [w(F, 6), w(F, 9)] + [x for x in c.values() if ":V" in x])
m.area("F0-waiting", F, "Waiting area", "waiting", 250, 340, 150, 190, 30, [w(F, 20)])
m.area("F0-room1", F, "Room 1", "room", 410, 340, 110, 190, 8, [w(F, 21)])
m.area("F0-room2", F, "Room 2", "room", 530, 340, 120, 190, 8, [w(F, 22)])
m.area("F0-room3", F, "Room 3", "room", 660, 340, 160, 190, 8, [w(F, 23)])
m.area("F0-toilets", F, "Toilets", "toilet", 830, 340, 130, 190, 6, [w(F, 15)])

m.write(entrance=w(F, 1), emergency=w(F, 5))
