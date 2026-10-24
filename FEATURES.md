# FEATURES.md - SevaSetu Tracked Features (F01 to F51)

FOUNDATION
- [x] F01 Project setup: dependencies (no networking), permissions (CAMERA, VIBRATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC), H01.json loaded from assets/graph, launcher icon.
- [x] F02 App shell: SevaScaffold used by every screen with the persistent EmergencyBar; a navigation graph with routes language, consent, home, register, slip, myturn, called, haveslip, navigate, scan, emergency, journey, settings, previsit, help, sms, readiness, demo, stickers; a global error screen; the theme from section 6 with four text scales and the high-contrast variant.
- [x] F03 Home screen as in section 7.
- [x] F50 Component library from section 6 (SevaScaffold, EmergencyBar, BigButton, PictureCard, StepHeader, StatusPill, InfoCard, ListenButton, LastUpdatedText, OfflineBanner, EmptyState, LoadingState, ErrorState), each with a Compose preview.
- [x] F51 Loading, empty, error and offline states on every screen, using the shared components.

DATA AND SIMULATION
- [x] F04 BackendGateway interface, DTOs from section 3, FakeBackend from section 4, one Hilt binding, NetworkException when offline is simulated.
- [x] F05 Room database: StoredVisit, PatientProfile, RegistrationDraft (save and resume), OutboxItem (id, function, args JSON, priority 0 to 4, created_at, attempts, state PENDING, SENDING, DONE or FAILED, last_error), CachedJourney, cached public tables, last MyTurn per visit, and the FakeBackend tables.
- [x] F06 Outbox: enqueue sends at once when online and otherwise stores; a unique WorkManager job flushes by priority then created_at when the simulated connection returns, when the app comes to the foreground and every 15 minutes; urgent flags are always attempted immediately.
- [x] F07 Clock and time helpers: the simulated clock, Asia/Kolkata formatting, the LastUpdatedText component.
- [x] F08 Demo panel (Settings, Simulation): Simulate no internet switch; clock speed 1x, 30x, 120x; Autopilot switch; for each doctor Call next and a doctor-status picker; for each visit Confirm Priority and Send to Emergency; Mark urgent flag seen; Add 5 patients; Load demo patient Kamla (stores her seeded visit on the phone); Reset demo data; a log screen of journey events and the outbox.

LANGUAGE AND SETTINGS
- [x] F09 Language screen at first launch (section 7); per-app locale; code ready to add Urdu (right to left), Kashmiri and Dogri.
- [x] F10 backendLanguage(): English gives en, Hindi gives hi, Tamil gives en; every patient.language goes through it.
- [x] F11 Consent screen (section 7): terms (required) and movement (optional; without it no journey events are sent but navigation still works).
- [x] F12 First-launch load of hospitals, departments and doctors from FakeBackend into Room with progress; works fully offline.
- [x] F13 Settings: language, text size (four levels), high contrast, Listen on or off, speech speed, Avoid stairs (default on for walking or wheelchair needs), alerts (sound, vibration, flash screen), consent toggles, offline-readiness link, Demo panel link.
- [x] F14 Listen helper: Android TextToSpeech when a voice for the language exists; otherwise recorded audio from res/raw using keys such as listen_emergency_ta when present; otherwise hide the Listen button for that language.
- [x] F15 Strings in English, Hindi and Tamil for every screen, plus TRANSLATION_REVIEW.md listing every Hindi and Tamil string for native-speaker review.

EMERGENCY AND SYMPTOM CHECK
- [x] F16 Emergency screen (no network needed) as in section 7: steps to Emergency from the last scanned sticker or the gate (upgraded to real routing by F40); Call 112 and Call 108 using ACTION_DIAL; readable warning signs; an "I need urgent help" button.
- [x] F17 Urgent flag lifecycle: create the flag (UUID, source patient_app, visit_id or hospital_id plus a random session_id, last scanned waypoint) and enqueue it at priority 0; show "Not yet received by staff. Please tell a staff member." while it is not delivered, "Sent to staff" once received_at is set, and "Staff have seen your request" with vibration when seen_at arrives (observe the visit and re-check getUrgentFlag every 10 seconds until seen).
- [x] F18 Symptom check engine with assets/symptom_rules_demo-1.json (ruleset_version demo-1): (1) warning signs in English, Hindi and Tamil: chest pain, difficulty breathing, fainting or not responding, fits, heavy bleeding, severe injury, bleeding or severe pain in pregnancy, baby not feeding or lips turning blue, fever with stiff neck or confusion, face drooping or slurred speech, suspected poisoning; (2) short follow-up questions; (3) rules mapping answers to department code (P infant or child, A pregnancy, O bone, joint or injury, otherwise M), suggested band (green or yellow only) and cluster from exactly: fever, respiratory, diarrhoea, skin, injury (or none); (4) a doctor_note builder of two to four plain lines such as "Cough 5 days, fever, no breathing difficulty reported, age 62". Never diagnose. Add a comment that clinicians must review the file. Warning signs are asked first; any yes gives Emergency: no visit is created, recordSymptomCheck is enqueued with outcome emergency, a flag with source symptom_check is raised when a hospital is known, and the Emergency screen opens.
- [x] F19 "Not sure?" outcome: recordSymptomCheck with outcome help_desk, then "Please ask at the help desk" with the way to Registration.

REGISTRATION AND SLIP
- [x] F20 Registration flow (section 7): one question per screen. First screen: the warning signs ("Do any of these apply?"). Then: who is this for (myself or someone else, which turns on assisted mode); ABHA (optional, demo values only); first name; age group with picture cards plus optional age in years; special needs with icons; area (list of J&K districts plus type-in); phone (optional, digits only); then the symptom check; then the review screen. Back keeps answers; progress is saved and resumable.
- [x] F21 Department picture cards (with "Not sure?"), hospital picker (default H01, plus H02 and H03).
- [x] F22 Submit: build registerVisit exactly as in section 3. Online: show the returned MyTurn. Offline: create a local StoredVisit with an offline token (department code, a dash, four random characters) marked "Waiting to be confirmed", enqueue registerVisit with offline_token and the original created_at, show a provisional slip; when the outbox confirms it, replace it with the real MyTurn and show the token_confirmed reason.
- [x] F23 OPD slip (section 7). Buttons: Listen, Save as image, Print, Share (QR image only, after a warning that anyone with the QR can see the queue position; never share visit_id as text), Large type, Open hospital in Google Maps (https://www.google.com/maps/dir/?api=1&destination=lat,lng), Take me to my room.

QUEUE AND ALERTS
- [x] F24 My Turn screen (section 7): show cached data instantly, then refresh; if estimate.paused say the doctor is on a break or away and hide the window; if estimate is null hide the numbers; offline shows "Queue position may be out of date. Last updated at HH:mm."; always show "Feeling worse? Press Urgent help".
- [x] F25 Reason chip: translate all ten reason_code values into the app language, with the English message as fallback.
- [x] F26 Status handling: called opens the Called screen; emergency switches to the Emergency screen at once; done or cancelled says so and offers Health Journey.
- [x] F27 Live updates: collect observeVisit and refetch getMyTurn on every signal, plus a refresh every 5 seconds while a queue screen is open (this mirrors the future real-time and polling behaviour).
- [x] F28 Background queue watcher: a foreground service (dataSync) with the ongoing notification "Watching your queue" while a visit is waiting or called; alert once when people_ahead drops to 3 or fewer (sound, vibration, high-priority notification, full-screen card when the app is open, an entry in the simulated SMS inbox) and again when called, with the room; with the hearing need also flash the screen and use a long vibration pattern; explain the notification permission and keep working if refused.
- [x] F29 I have my slip: type the token with a hospital picker and call linkSlip (null shows "We could not find this token today."), or scan a slip QR, parse SS1:SLIP:<hospital>:<visit_id>, call getMyTurn, store the visit and open My Turn; reject other QR codes politely. The Demo panel lists the seeded tokens so they can be tried.
- [x] F30 SMS mock screen: a clearly labelled "Simulated SMS" inbox with the messages the app would receive at 3 ahead and when called, and a Settings toggle to demonstrate the no-data step.

NAVIGATION
- [x] F31 GraphRouter: a Kotlin port of graph.ts with the section 3 costs, plus unit tests that check at least ten place pairs, with and without Avoid stairs, including floor changes; print the results as a table.
- [x] F32 Destinations: the doctor's room from the slip (doctor.room_waypoint_id), search over nodes tagged registration, pharmacy, lab, xray, toilet, help, lift, room, waiting, and Emergency.
- [x] F33 Guidance one step at a time (section 7): what to do (turn left, turn right, go straight, take the lift to floor N; left and right from node coordinates, x grows east, y grows south), the landmark (translated through F38), distance in metres, and "Scan the sticker at the end of this corridor"; Listen on every step.
- [x] F34 Floor plan on a Compose Canvas scaled to the screen width (10 px = 1 m), areas drawn and labelled, route highlighted, a "You are here" dot at the last scanned sticker, pinch to zoom, and a floor switch when the route changes floors.
- [x] F35 Sticker scanner (CameraX and ML Kit, offline) that accepts only ids matching SS:<hospital>:F<floor>:W<id> that exist in the map with sticker true; start from the last scan, or scan the nearest sticker, or choose Start at the main gate. The scanner also has a "Simulate scan" list of all sticker nodes (tap to act as if scanned) that works exactly like a real scan.
- [x] F36 Wrong turn and lost: a scanned sticker that is not the planned next one and has a greater remaining distance gives "No problem. Let's take a new route.", re-routes from it, and records a wrong_turn event with consent; no scan for about 90 seconds on an active route gives "Are you lost?" with Scan nearest sticker and Call a helper (route to the nearest node tagged help). If Avoid stairs finds no route, say so and offer a helper.
- [x] F37 Journey events with movement consent only, under a random session_id per visit (never the visit or patient id): entry (first scan at the gate), scan (each sticker), wrong_turn, room (destination sticker), exit (the gate afterwards); enqueue at priority 4 and show them in the Demo panel log.
- [x] F38 Map translations: assets/graph/H01_i18n_hi.json and H01_i18n_ta.json keyed by node and area id with name and landmark; drafts also listed in TRANSLATION_REVIEW.md; fall back to English when missing, never blank.
- [x] F39 Hospitals without a map (H02, H03): show "Ask at the help desk for directions" and the Google Maps link.
- [x] F40 Connect routing to the Emergency screen: route from the last scanned sticker (or the gate) to the emergency node using emergency routing, with written steps and the floor plan; works fully offline.

EXTRAS
- [x] F41 My Health Journey (section 7): getMyJourney cached in Room; read-only, oldest first, large type with Listen; next_follow_up with its state in kind words (due, done, defaulted); cached copy with last-updated time when offline. Works with the seeded patient Kamla.
- [x] F42 Before you visit: from cached public tables show hospital address with Open in Google Maps, which services are up or down, and "Is my doctor in?" with doctor status; show when it was last updated.
- [x] F43 Help cards: a few static offline cards (what to bring, how the token works, what Priority means, where the pharmacy is); no free chat; any card or question mentioning a warning sign hands off to Emergency.
- [x] F44 Offline-readiness screen: tick list of what is stored (map, symptom rules, hospitals, last queue, last journey, audio) and how to test offline mode with the Simulate no internet switch.

QUALITY
- [x] F45 TalkBack pass, labels and focus order, nothing clipped at 360dp and the largest text size; list the screens changed.
- [x] F46 SAFETY_CHECKLIST.md built from S1 to S10, each item marked pass or fail with how it was checked.
- [x] F47 Unit tests: no registration after a warning sign; outbox ordering; offline token confirmation; routing; reason_code mapping; FakeBackend queue order and estimate.
- [x] F48 Release: a signed release APK (the Demo panel stays in, labelled as simulation); app/README.md explaining how to demo it and that integration means replacing FakeBackend with a real BackendGateway.
- [x] F49 Sticker sheet screen (in the Demo panel): every sticker node shown as a large QR with its name and id, so it can be displayed on a second screen or printed and scanned for a real test.
