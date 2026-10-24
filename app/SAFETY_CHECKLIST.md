# SAFETY_CHECKLIST.md

PRD section 4, rules S1 to S10, as the app meets them. The backend enforces what it can on its own (a red band is
never queued, bands are never lowered); this is the app's part. Re-check before every demo.

| Rule | Requirement | How the app meets it | Check |
|---|---|---|---|
| **S1** | Emergency on every screen, opens in under a second with no signal, with the route, 112 and 108 | `SevaScaffold` puts the red Emergency bar on every screen (language and consent included); Called has its own urgent-help button. The Emergency screen needs no network: the route to Casualty comes from the map on the phone, from the last scanned sticker or the gate. | Airplane mode, tap Emergency from each screen |
| **S2** | Any warning sign ends the flow at Emergency, no token | The 11 warning signs come first. A yes opens Emergency at once, records the outcome (anonymous) and raises an urgent flag with source `symptom_check`; `register_visit` is never called. | Symptom check, tap a warning sign; dashboard shows the flag |
| **S3** | Never show a lower band than the server | The app shows the server's `band` and `band_label`; while unconfirmed the chip has a dashed edge and "unconfirmed". | Register as elderly; chip shows Priority |
| **S4** | Queue screens say "Feeling worse? Press Urgent help"; nothing says the patient can wait | My Turn and Called carry the red button; the wording never suggests leaving. | Read My Turn and Called |
| **S5** | The urgent flag shows its honest state | "Not yet received by staff" (queued, with "please tell a staff member"), "Sent to staff", "Staff have seen your request" with vibration and speech when staff tap Seen. Live over Realtime, checked every 3 s as a fallback. | Press urgent help; tap Seen on the dashboard |
| **S6** | No Wait Anywhere or remote token for flagged or Priority patients | The app offers no Wait Anywhere at all. | |
| **S7** | Help answers only approved content and hands warning signs to Emergency | Help cards are fixed text with an Emergency hand-off; there is no chatbot. | |
| **S8** | Offline screens say when the information is from; offline tokens say "Waiting to be confirmed" | Offline banner with "updated at"; "Last updated at" on the slip; offline tickets are marked until the hospital confirms them. | Airplane mode, register |
| **S9** | The slip shows token, first name and department only; no health data in the QR; scans are anonymous | Slip QR is `SS1:SLIP:<hospital>:<visit_id>`; scan events go under a random session id, reset for each visit, and only with consent. | Scan a slip QR with any reader |
| **S10** | When the place in the queue changes, the patient sees why | `last_reason` is shown on My Turn as "Why did my place change?" in the patient's language; unknown codes show the server's message. | Confirm Priority for someone ahead on the dashboard |

Also: every colour-coded status carries a word and an icon (status chips, flag states, the floor-plan legend).
