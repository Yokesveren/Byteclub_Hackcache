# TRANSLATION_REVIEW.md

Every patient-facing line of the app exists in English, Hindi and Urdu. **The Hindi and Urdu are first drafts written
for the demo, not by native speakers.** Before any real patient uses the app, a native speaker of each language must
review them, and Kashmiri and Dogri need their own translations.

## Where the text lives

| What | File | Languages |
|---|---|---|
| Screen text, directions, alerts (about 360 lines) | `app/i18n/strings.py` | en (key), hi, ur |
| Place names and landmarks on the indoor maps | `web/scripts/graph_common.py` (`WORDS`) | en, hi, ur |
| Department names | `supabase/seed.sql` (`name_i18n`) | en, hi, ur |
| Warning signs in the symptom check | `app/androidApp/src/main/assets/symptom_rules_demo-1.json` | en, hi, ur |

The warning signs are also clinical content: a clinician reviews their meaning, a native speaker their wording.

## How fallbacks work

- Kashmiri (`ks`) is shown in Urdu, right to left. Dogri (`doi`) is shown in Hindi.
- Any line with no translation shows in English, never blank.
- Place-holders such as `{token}` or `{m}` must stay exactly as written; `python app/i18n/build_strings.py` refuses a
  table where they differ, and lists English text in the code that has no translation yet.

## What to check

1. **Plain words.** Patients may have little schooling. Prefer the words people say in the OPD (टोकन, डॉक्टर,
   ٹوکن, ڈاکٹر) over formal ones.
2. **Safety lines first.** These must be unmistakable: "Go to Emergency now", "You do not need a token",
   "I need urgent help", "Not yet received by staff", "Staff have seen your request", the 11 warning signs.
3. **Directions.** Left and right, "walk about {m} m", floor names, and the map's place names and landmarks.
4. **Length.** Buttons are written in capitals in English; check that the Hindi and Urdu fit on a small phone at the
   largest text size.
5. **Kashmiri and Dogri.** Add `ks` and `doi` columns: extend `I18n.base()` in
   `ui/i18n/I18n.kt` to use them instead of the Urdu and Hindi fallbacks.

## Review log

| Language | Reviewer | Date | Status |
|---|---|---|---|
| Hindi | | | draft, not reviewed |
| Urdu | | | draft, not reviewed |
| Kashmiri | | | not started (shows Urdu) |
| Dogri | | | not started (shows Hindi) |
