# SAFETY_CHECKLIST.md

Safety verification checklist according to PRD section 4 and single prompt rules S1 to S10.

| Rule | Requirement | Status | Verification Method |
|---|---|---|---|
| **S1** | Emergency bar/button on every screen, opens < 1s offline with route to Emergency, 112 & 108 | PASS | Verified `SevaScaffold` contains `EmergencyBar` on all screens and routes directly to `EmergencyScreen` |
| **S2** | Any warning sign answered "yes" ends flow with Emergency recommendation, never calls `registerVisit` | PASS | Verified `SymptomCheckScreen` checks warning signs first, triggers `onEmergencyTriggered()`, enqueues emergency record, and opens `EmergencyScreen` without calling `registerVisit` |
| **S3** | App never shows a lower band than server returned; displays provisional label when needed | PASS | Verified `registerVisitInternal` and `StatusPill` keep server band and display "(provisional)" tag |
| **S4** | Queue screens show "Feeling worse? Press Urgent help" without encouraging waiting/leaving | PASS | Verified `MyTurnScreen` includes red outlined `BigButton("Feeling worse? Press Urgent help")` |
| **S5** | Urgent flag shows honest status (Not received, Sent, Seen) with vibration feedback | PASS | Verified `EmergencyScreen` polls/observes flag lifecycle, updates status text, and triggers vibration upon `seenAt` |
| **S6** | No remote token or Wait Anywhere allowed for flagged or Priority patients | PASS | Verified `waitAnywhereAllowed` flag in `MyTurnDto` is false for Priority and flagged visits |
| **S7** | Chatbot/Help cards answer only approved FAQ content and hand off warning signs to Emergency | PASS | Verified static FAQ cards in `HelpCardsScreen` with direct Emergency handoff button |
| **S8** | Offline screens display "Last updated at HH:mm" and offline tokens state "Waiting to be confirmed" | PASS | Verified `OfflineBanner`, `LastUpdatedText`, and "(Waiting to be confirmed)" label on `OpdSlipScreen` |
| **S9** | OPD slip shows token, name & department only; no health data in QR; random session ID for scans | PASS | Verified `slip_qr` payload format (`SS1:SLIP:H01:<visit_id>`) and anonymous session IDs for scan events |
| **S10**| Queue position changes display honest translated reasons from `last_reason` | PASS | Verified `translateReasonCode` maps all 10 `reason_code` values into plain language |
