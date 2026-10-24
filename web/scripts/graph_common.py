"""
Shared pieces of the hospital map builders (build_h01_graph.py, build_h02_graph.py, build_h03_graph.py).

A map is a graph of QR-sticker waypoints and corridor points, plus floor-plan areas for drawing. Coordinates are
plan pixels (10 px = 1 m). Place names and landmarks carry English, Hindi and Urdu; Kashmiri is shown in Urdu
script and Dogri in Hindi until native-speaker translations exist (app/TRANSLATION_REVIEW.md).

Each build writes the same file to the website (web/public/graph) and the patient app (app/.../assets/graph).
"""
import json, math, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
OUTPUTS = [
    os.path.join(HERE, "..", "public", "graph"),
    os.path.join(HERE, "..", "..", "app", "androidApp", "src", "main", "assets", "graph"),
]

# English -> (Hindi, Urdu). Room names are built from these parts.
WORDS = {
    "Main gate": ("मुख्य द्वार", "مین گیٹ"),
    "Emergency (Casualty)": ("इमरजेंसी (कैजुअल्टी)", "ایمرجنسی (کیجولٹی)"),
    "Emergency": ("इमरजेंसी", "ایمرجنسی"),
    "Entrance hall": ("प्रवेश हॉल", "داخلی ہال"),
    "Registration counter": ("पंजीकरण काउंटर", "رجسٹریشن کاؤنٹر"),
    "Registration": ("पंजीकरण", "رجسٹریشن"),
    "Help desk": ("सहायता डेस्क", "ہیلپ ڈیسک"),
    "Pharmacy": ("दवाखाना", "فارمیسی"),
    "Laboratory": ("प्रयोगशाला", "لیبارٹری"),
    "X-ray": ("एक्स-रे", "ایکس رے"),
    "Waiting area": ("प्रतीक्षा क्षेत्र", "انتظار گاہ"),
    "Lift": ("लिफ़्ट", "لفٹ"),
    "Stairs": ("सीढ़ियाँ", "سیڑھیاں"),
    "Toilets": ("शौचालय", "بیت الخلا"),
    "Corridor": ("गलियारा", "راہداری"),
    "Main corridor": ("मुख्य गलियारा", "مرکزی راہداری"),
    "First-floor corridor": ("पहली मंज़िल का गलियारा", "پہلی منزل کی راہداری"),
    "Corridor by the help desk": ("सहायता डेस्क के पास गलियारा", "ہیلپ ڈیسک کے پاس راہداری"),
    "Corridor centre": ("गलियारे का बीच", "راہداری کا درمیان"),
    "Corridor east end": ("गलियारे का पूर्वी छोर", "راہداری کا مشرقی سرا"),
    "Corridor west, first floor": ("पश्चिमी गलियारा, पहली मंज़िल", "مغربی راہداری، پہلی منزل"),
    "Corridor by the stairs": ("सीढ़ियों के पास गलियारा", "سیڑھیوں کے پاس راہداری"),
    "Corridor east, first floor": ("पूर्वी गलियारा, पहली मंज़िल", "مشرقی راہداری، پہلی منزل"),
    "Courtyard": ("आँगन", "صحن"),
    "Verandah": ("बरामदा", "برآمدہ"),
    "ground floor": ("भूतल", "گراؤنڈ فلور"),
    "first floor": ("पहली मंज़िल", "پہلی منزل"),
    "Ground floor": ("भूतल", "گراؤنڈ فلور"),
    "First floor": ("पहली मंज़िल", "پہلی منزل"),
    # departments
    "General Medicine": ("सामान्य चिकित्सा", "جنرل میڈیسن"),
    "Paediatrics": ("बाल रोग", "بچوں کا شعبہ"),
    "Maternal Health": ("मातृ स्वास्थ्य", "زچہ و بچہ"),
    "Orthopaedics": ("हड्डी रोग", "ہڈیوں کا شعبہ"),
    # landmarks
    "Under the green hospital sign": ("हरे अस्पताल बोर्ड के नीचे", "سبز اسپتال بورڈ کے نیچے"),
    "Red doors, open all day and night": ("लाल दरवाज़े, दिन-रात खुले", "سرخ دروازے، دن رات کھلے"),
    "Beside the visitor notice board": ("आगंतुक सूचना बोर्ड के पास", "ملاقاتیوں کے نوٹس بورڈ کے پاس"),
    "Windows 1 to 4": ("खिड़की 1 से 4", "کھڑکی 1 سے 4"),
    "Blue counter with the help sign": ("सहायता चिह्न वाला नीला काउंटर", "مدد کے نشان والا نیلا کاؤنٹر"),
    "Medicine window": ("दवा की खिड़की", "دوائی کی کھڑکی"),
    "Sample collection": ("नमूना संग्रह", "نمونہ جمع کرنا"),
    "Yellow radiation sign": ("पीला विकिरण चिह्न", "پیلا تابکاری نشان"),
    "Rows of green chairs": ("हरी कुर्सियों की कतारें", "سبز کرسیوں کی قطاریں"),
    "Rows of blue chairs": ("नीली कुर्सियों की कतारें", "نیلی کرسیوں کی قطاریں"),
    "Steel doors": ("स्टील के दरवाज़े", "اسٹیل کے دروازے"),
    "Green notice board": ("हरा सूचना बोर्ड", "سبز نوٹس بورڈ"),
    "Water cooler": ("पानी का कूलर", "پانی کا کولر"),
    "Fire extinguisher": ("अग्निशामक यंत्र", "آگ بجھانے کا آلہ"),
    "Window over the courtyard": ("आँगन की ओर खिड़की", "صحن کی طرف کھڑکی"),
    "Next to the green notice board": ("हरे सूचना बोर्ड के बगल में", "سبز نوٹس بورڈ کے ساتھ"),
    "Chinar tree in the courtyard": ("आँगन में चिनार का पेड़", "صحن میں چنار کا درخت"),
    "Wooden bench under the clock": ("घड़ी के नीचे लकड़ी की बेंच", "گھڑی کے نیچے لکڑی کا بینچ"),
    "Blue door with the room number": ("कमरा नंबर वाला नीला दरवाज़ा", "کمرہ نمبر والا نیلا دروازہ"),
    "Ramp beside the steps": ("सीढ़ियों के बगल में रैंप", "سیڑھیوں کے ساتھ ریمپ"),
    "Notice board with the doctors' list": ("डॉक्टरों की सूची वाला सूचना बोर्ड", "ڈاکٹروں کی فہرست والا نوٹس بورڈ"),
    "Window with the red cross": ("लाल क्रॉस वाली खिड़की", "سرخ صلیب والی کھڑکی"),
}


def tr(text):
    """{'en','hi','ur'} for a place name or landmark. Handles 'Room 3 (General Medicine)', 'Lift, ground floor'."""
    if text in WORDS:
        hi, ur = WORDS[text]
        return {"en": text, "hi": hi, "ur": ur}
    m = re.fullmatch(r"(Room|OPD) (\d+)(?: \((.+)\))?", text)
    if m:
        kind, n, dept = m.groups()
        hi = ("कमरा " if kind == "Room" else "ओपीडी ") + n
        ur = ("کمرہ " if kind == "Room" else "او پی ڈی ") + n
        if dept:
            dhi, dur = WORDS[dept]
            hi += f" ({dhi})"; ur += f" ({dur})"
        return {"en": text, "hi": hi, "ur": ur}
    m = re.fullmatch(r"(.+), (ground floor|first floor)", text)
    if m:
        a, f = tr(m.group(1)), tr(m.group(2))
        return {"en": text, "hi": f"{a['hi']}, {f['hi']}", "ur": f"{a['ur']}، {f['ur']}"}
    raise KeyError(f"no translation for map text: {text!r}")


class Map:
    def __init__(self, hospital_id, name):
        self.h = hospital_id
        self.name = name
        self.nodes, self.edges, self.areas, self.floors = [], [], [], []

    def wid(self, floor, n):
        return f"SS:{self.h}:F{floor}:W{n:03d}"

    def vid(self, floor, x):
        return f"SS:{self.h}:F{floor}:V{x:03d}"

    def floor(self, floor, name, width=1000, height=560):
        self.floors.append({"floor": floor, "name": name, "name_i18n": tr(name), "width": width, "height": height})

    def node(self, id, floor, x, y, name, tags, landmark=None, sticker=True):
        self.nodes.append({"id": id, "floor": floor, "x": x, "y": y, "sticker": sticker, "tags": tags,
                           "name": tr(name), "landmark": tr(landmark) if landmark else None})

    def edge(self, a, b, **attrs):
        na = next(n for n in self.nodes if n["id"] == a)
        nb = next(n for n in self.nodes if n["id"] == b)
        if na["floor"] != nb["floor"]:
            dist = attrs.pop("distance_m")
        else:
            dist = round(math.hypot(na["x"] - nb["x"], na["y"] - nb["y"]) / 10, 1)
        self.edges.append({"from": a, "to": b, "distance_m": dist, "stairs": attrs.get("stairs", False),
                           "lift": attrs.get("lift", False), "ramp": attrs.get("ramp", False),
                           "crowd_prone": attrs.get("crowd_prone", False)})

    def area(self, id, floor, label, kind, x, y, w, h, capacity, waypoints):
        self.areas.append({"id": id, "floor": floor, "label": label, "label_i18n": tr(label), "kind": kind,
                           "x": x, "y": y, "w": w, "h": h, "capacity": capacity, "waypoints": waypoints})

    def corridor(self, floor, y, stops, crowd_prone=True):
        """A straight corridor at height y. stops: (x, sticker id or None, name, landmark); None makes a corridor point."""
        chain = []
        for x, sticker, name, landmark in stops:
            if sticker:
                if not any(n["id"] == sticker for n in self.nodes):
                    self.node(sticker, floor, x, y, name, ["junction"], landmark)
                chain.append(sticker)
            else:
                self.node(self.vid(floor, x), floor, x, y, "Corridor", ["corridor"], sticker=False)
                chain.append(self.vid(floor, x))
        for a, b in zip(chain, chain[1:]):
            self.edge(a, b, crowd_prone=crowd_prone)
        return {x: c for (x, *_), c in zip(stops, chain)}

    def write(self, entrance, emergency):
        graph = {
            "hospital_id": self.h,
            "name": self.name,
            "version": 2,
            "units": "Plan pixels; 10 px = 1 m. Edges are walkable both ways. Names carry en, hi and ur.",
            "floors": self.floors,
            "entrance": entrance,
            "emergency": emergency,
            "nodes": self.nodes,
            "edges": self.edges,
            "areas": self.areas,
        }
        ids = [n["id"] for n in self.nodes]
        assert len(ids) == len(set(ids)), "duplicate node id"
        for e in self.edges:
            assert e["from"] in ids and e["to"] in ids, e
        adj = {i: set() for i in ids}
        for e in self.edges:
            adj[e["from"]].add(e["to"]); adj[e["to"]].add(e["from"])
        seen, stack = set(), [entrance]
        while stack:
            n = stack.pop()
            if n not in seen:
                seen.add(n); stack.extend(adj[n])
        assert seen == set(ids), f"unreachable: {set(ids) - seen}"
        for a in self.areas:
            for w in a["waypoints"]:
                assert w in ids, (a["id"], w)
        assert emergency in ids and entrance in ids
        for out_dir in OUTPUTS:
            os.makedirs(out_dir, exist_ok=True)
            path = os.path.join(out_dir, f"{self.h}.json")
            with open(path, "w", encoding="utf8") as f:
                json.dump(graph, f, indent=1, ensure_ascii=False)
                f.write("\n")
        stickers = sum(1 for n in self.nodes if n["sticker"])
        print(f"{self.h}: {len(self.nodes)} nodes ({stickers} stickers), {len(self.edges)} edges, {len(self.areas)} areas")
