"""
Builds the indoor map of Lidder Community Health Centre (demo), H03: a small single-storey centre with two OPD
rooms off one verandah. Run from web/:  python scripts/build_h03_graph.py
"""
from graph_common import Map

m = Map("H03", "Lidder Community Health Centre (Demo)")
w = m.wid
F, Y = 0, 300
m.floor(F, "Ground floor", width=800, height=560)

m.node(w(F, 1), F, 80, 500, "Main gate", ["entrance", "exit"], "Under the green hospital sign")
m.node(w(F, 2), F, 160, Y, "Entrance hall", ["junction"], "Ramp beside the steps")
m.node(w(F, 5), F, 140, 160, "Emergency (Casualty)", ["emergency"], "Red doors, open all day and night")
m.node(w(F, 3), F, 300, 230, "Registration counter", ["registration", "help"], "Window with the red cross")
m.node(w(F, 12), F, 440, 230, "Pharmacy", ["pharmacy"], "Medicine window")
m.node(w(F, 13), F, 580, 230, "Laboratory", ["lab"], "Sample collection")
m.node(w(F, 20), F, 300, 380, "Waiting area", ["waiting"], "Wooden bench under the clock")
m.node(w(F, 21), F, 460, 380, "OPD 1", ["room"], "Blue door with the room number")
m.node(w(F, 22), F, 620, 380, "OPD 2", ["room"], "Blue door with the room number")
m.node(w(F, 15), F, 720, 230, "Toilets", ["toilet"])
c = m.corridor(F, Y, [
    (160, w(F, 2), None, None), (300, None, None, None), (440, w(F, 6), "Verandah", "Chinar tree in the courtyard"),
    (460, None, None, None), (580, None, None, None), (620, None, None, None), (720, None, None, None),
])
m.edge(w(F, 1), w(F, 2), ramp=True)
m.edge(w(F, 5), w(F, 2))
for door, x in [(3, 300), (20, 300), (12, 440), (21, 460), (13, 580), (22, 620), (15, 720)]:
    m.edge(w(F, door), c[x], crowd_prone=door in (3, 20))

m.area("F0-emergency", F, "Emergency", "emergency", 40, 60, 200, 150, 8, [w(F, 5)])
m.area("F0-hall", F, "Entrance hall", "hall", 40, 220, 200, 320, 15, [w(F, 1), w(F, 2)])
m.area("F0-registration", F, "Registration", "service", 250, 60, 120, 200, 15, [w(F, 3)])
m.area("F0-pharmacy", F, "Pharmacy", "service", 380, 60, 130, 200, 10, [w(F, 12)])
m.area("F0-lab", F, "Laboratory", "service", 520, 60, 130, 200, 8, [w(F, 13)])
m.area("F0-toilets", F, "Toilets", "toilet", 660, 60, 100, 200, 4, [w(F, 15)])
m.area("F0-corridor", F, "Verandah", "corridor", 240, 275, 520, 50, 15, [w(F, 6)] + [x for x in c.values() if ":V" in x])
m.area("F0-waiting", F, "Waiting area", "waiting", 250, 340, 150, 190, 25, [w(F, 20)])
m.area("F0-opd1", F, "OPD 1", "room", 410, 340, 140, 190, 8, [w(F, 21)])
m.area("F0-opd2", F, "OPD 2", "room", 560, 340, 140, 190, 8, [w(F, 22)])

m.write(entrance=w(F, 1), emergency=w(F, 5))
