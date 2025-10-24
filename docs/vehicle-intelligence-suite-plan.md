# Vehicle Intelligence Suite – Unified Plan

_Last updated: October 2025_

## Purpose

Bring three previously parallel initiatives together into a single, phased
program for smarter diagnostics and technician assistance:

1. **Full Vehicle Scan Orchestrator** – automated, unattended mode sweep that
   produces a comprehensive dossier per vehicle.
2. **AI Diagnostic Analyzer** – GPT-powered reasoning engine that interprets
   the scan output and proposes repair paths.
3. **CoPilot Conversational UI** – Knight Rider–style assistant (animated face
   + wake word) that exposes insights and actions through natural language.

The combined system should let engineers connect to a vehicle, execute a
turn-key scan, receive structured + AI-enhanced reports, and interact with the
results conversationally (hands-free if desired).

## Solution Map

```
┌────────────────────┐      ┌────────────────────┐      ┌────────────────────┐
│ DiscoveryManager   │◄────►│ Full Scan           │◄────▶│ AI Diagnostic       │
│ (session + logs)   │      │ Orchestrator        │      │ Analyzer (GPT)      │
└──────▲─────────────┘      └──────────▲──────────┘      └───────▲────────────┘
       │                                │                           │
       │                                │                           │
       │                                ▼                           │
       │                     ReportBuilder + File Outputs           │
       │                                │                           │
       ▼                                ▼                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ CoPilot Controller  (wake word, animated face, command bridge, prompts)  │
└──────────────────────────────────────────────────────────────────────────┘
```

## Shared Foundations

- **DiscoveryManager Enhancements** – already logging ECU discovery and session
  metadata; reuse session IDs across scan, AI analysis, and CoPilot logs.
- **SecurePreferences** – persist OpenAI keys, wake-word toggles, model
  selection, and privacy controls.
- **Report & Logging Layout** – keep consolidated artifacts under
  `Android/data/com.obddroid/` with predictable subfolders:
  - `logs/discovery/<session>.jsonl`
  - `logs/copilot/<session>.jsonl`
  - `reports/<VIN>/<timestamp>/…`
- **CommandBridge** – shared utility that encapsulates privileged app actions
  (run scan, export data, open screens) so both Orchestrator automation and
  CoPilot voice commands call the same surface area.
- **Telemetry Providers** – single set of helpers that surface: vehicle info,
  DTC summaries, monitor states, live data anomalies, discovery notes, recent
  scans.
- **Agent API Conversation State** – lean on OpenAI’s Conversations API or
  `previous_response_id` threading to persist memory across CoPilot sessions
  without manual prompt concatenation. Conversation IDs should align with
  discovery session IDs so reconnects resume seamlessly.
- **Tool & Connector Registry** – central definition of command-bridge tools
  (scan orchestration, log export, stage replay, human handoff) so both Agent
  workflows and realtime models can invoke them safely with guardrails.

## 1. Full Vehicle Scan Orchestrator

### Objectives

- One-tap, unattended sweeping of OBD/UDS modes with structured capture.
- Deterministic state machine of stages; resilience to adapter quirks.
- Generate a rich dossier (Markdown + JSON + attachments) ready for AI analysis
  and human review.

### Flow

1. User picks **Full Vehicle Scan** from main screen overflow.
2. Confirmation dialog highlights runtime (≈2–4 min) and vehicle power needs.
3. Foreground service (`ScanOrchestrator`) launches, binding progress sheet.
4. Discovery snapshot recorded (adapter, VIN, ECU list, addresses).
5. Sequential stages execute (see table); each stage logs metadata, raw frames,
   parsed summary, errors.
6. `ReportBuilder` writes outputs; optional AI analyzer and CoPilot are invoked.
7. Completion UI offers share/export/AI explain actions.

### Stage Coverage

| # | Stage | Service | Notes |
|---|-------|---------|-------|
| 1 | Discovery Snapshot | — | Reuse `DiscoveryManager` session. |
| 2 | Live Data Baseline | Mode 01 | Snapshot supported PIDs + key metrics. |
| 3 | Freeze Frame | Mode 02 | Capture per-DTC frames if available. |
| 4 | Confirmed DTCs | Mode 03 | Include descriptions + severity. |
| 5 | Pending DTCs | Mode 07 | |
| 6 | Permanent DTCs | Mode 0A | |
| 7 | MIL Reset (optional) | Mode 04 | User confirmation required. |
| 8 | O2/Monitor Tests | Modes 05/06 | Summaries vs thresholds. |
| 9 | Component Tests | Mode 08 | Only if vehicle supports; fallback gracefully. |
|10 | Vehicle Info | Mode 09 | Seed `EcuManager` / VIN metadata. |
|11 | Extended UDS | Custom | OEM-specific (future packs). |
|12 | AI Analysis Trigger | — | Fire AI Diagnostic Analyzer when enabled. |

### Key Components

- `ScanStage` interface (`run()` returning `StageResult`).
- `ScanContext` with handles to `ElmProt`, session IDs, configuration, logging
  sinks.
- Progress sheet with cancel + “view log” controls.
- Zip export bundling report + raw stage JSON + optional attachments.

### Deliverables Checklist

- [ ] Stage engine + MVP stages (01/03/07/0A/09).
- [ ] Cancellation + recovery (resume vs restart behavior).
- [ ] Report writer (Markdown + JSON + zipped artifacts).
- [ ] Share sheet & quick link to AI explanation once finished.
- [ ] Verified on ≥3 vehicle platforms pre-pilot.

## 2. AI Diagnostic Analyzer

### Role

Interpret full scan output (including freeze frames and live metrics) using
OpenAI’s GPT models, producing actionable diagnosis, root-cause narratives, and
repair pathways.

### Architecture

- Extend `OpenAiService` with `analyzeFullScan(scanBundle, vehicleProfile)`.
- `buildFullScanPrompt()` compiles:
  - Vehicle metadata (VIN, make, model, engine, mileage).
  - DTC summaries (confirmed/pending/permanent) with textual descriptions.
  - Freeze frame values per DTC.
  - Live data anomalies (e.g., trims, sensor readings outside norms).
  - Monitor readiness status.
- Invoke GPT-5 (or GPT-4 Turbo / GPT-4o) requesting JSON output describing:
  - Root-cause hypotheses + confidence.
  - Supporting evidence.
  - Tiered repair options (time, parts, cost, success rate).
  - Post-repair verification checklist.
- Parse JSON into `DiagnosticReport` and write both Markdown section and
  machine-readable `ai-diagnosis.json`.

### Settings & Cost Controls

- Toggle: “Enable AI Assistant” (default OFF).
- Model picker: GPT-3.5 Turbo (cheap) vs GPT-4 Turbo / GPT-4o (accurate).
- Estimated cost banner before analysis (use token predictions).
- Usage tracking screen showing monthly spend/token counts.

### UX Integration

- Progress spinner during AI call (async, cancellable).
- On completion, integrate into report under “AI Diagnostic Analysis” with
  sections for root cause, recommended repairs (3 tiers), verification steps.
- CoPilot can surface or summarize these results when asked “What did the AI
  find?”

### Privacy Notes

- Clear warning that scan data (incl. VIN) is sent to OpenAI; user-provided API
  key required.
- No data routed through OBD-Droid servers.

### Development Checklist

- [ ] `OpenAiService.analyzeFullScan()` & prompt builder.
- [ ] `GptDiagnosticAnalyzer` parser with error handling.
- [ ] Markdown/JSON writers integrated into ReportBuilder.
- [ ] Settings UI for AI toggles, model choice, cost estimate, usage log.
- [ ] Unit tests covering representative DTC scenarios (misfire, EVAP leak, etc.).
- [ ] UX flow for retries / failures (e.g., network errors, API quota).

## 3. CoPilot Conversational Interface

### Vision

Knight Rider–inspired animated assistant that technicians can summon by voice
or tap, drawing on the same scan + AI data to explain issues, suggest actions,
and even launch commands.

### Core Capabilities

- **Animated Persona:** Compose/Lottie-driven face with idle motion, speech
  animation, and mood cues (listening, thinking, responding).
- **Wake Word:** On-device detection of “OBD Droid” (toggle-able, with mic
  indicator and privacy controls). Push-to-talk fallback.
- **Contextual Prompts:** PromptBuilder merges vehicle metadata, current scan
  results, AI analysis, recent commands, and discovery notes into structured
  context blocks (`vehicle`, `dtc_summary`, `live_metrics`, `recent_actions`).
- **Command Bridge:** CoPilot can call whitelisted actions
  (`runFullScan()`, `openScreen("emissions")`, `shareLatestReport()`, etc.) with
  optional confirmation dialogue.
- **Logs & Telemetry Visibility:** Ability to reference discovery logs, scan
  transcripts, AI results, and show excerpts on request.
- **Voice & Text Interaction:** Stream responses via GPT-5/4o; TTS playback and
  live subtitle display.

### Architecture Components

1. `CoPilotController` – orchestrates UI state, orchestrator status, context
   capture, and LLM requests.
2. `VoiceService` – wake word, mic capture, ambient noise metering, push-to-talk.
3. `PromptBuilder` – assembles system + user messages, including wake-word
   metadata and command intent hints.
4. `LLMClient` – streaming chat completions (GPT-5/GPT-4o), retry/backoff, model
   switching.
5. `ConversationStore` – rolling history with summarisation to stay within
   token limits.
6. `SecurityLayer` – consent, audio indicators, wake-word settings.
7. `CoPilotCommandBridge` – executes allowed app actions with guardrails.

### Roadmap Snapshot

| Phase | Focus | Key Deliverables |
|-------|-------|------------------|
| 0 | Foundations | Wireframes, prompt schema, latency goals, API quota review. |
| 1 | Backend wiring | `LLMClient`, SecurePreferences updates, telemetry providers, logging sink. |
| 2 | UI shell | Panel fragment, animated face, streaming renderer, wake/mute UI. |
| 3 | Context & commands | PromptBuilder v1, insight cards, command bridge, eval harness. |
| 3b | Voice enablement (pilot) | Wake word engine, mic capture, GPT-4o realtime/ASR, TTS + lip sync. |
| 4 | Pilot & feedback | Field testing, metrics, prompt tuning, voice rollout plan. |

### Safety & UX Considerations

- Visual confirmation when wake word triggers (LED ring / color shift).
- Quick “sleep” button to disable listening.
- Log every command execution for audit; require explicit confirmation for
  destructive operations (e.g., clear codes).
- Provide disclaimers when summarising AI analysis to avoid overstating
  certainty.

### Agent API & Conversation Memory Strategy

- Introduce OpenAI’s **Conversations API** for durable CoPilot threads. Store
  the `conversation_id` alongside each discovery session so reconnecting to the
  same VIN revives prior context automatically.
- When Conversations API is unavailable, fall back to `previous_response_id`
  threading but enforce summarisation to stay within model context windows.
- Register CoPilot commands as structured tools (JSON schema) in the Agent API
  so the model can call `runFullScan`, `openScreen`, `exportReport`, or escalate
  to a human with explicit confirmation gates.
- Capture tool invocations and responses in `logs/copilot/<session>.jsonl` for
  auditing and evaluation.
- Provide UI controls to purge, export, or transfer conversations for privacy
  compliance.

### Realtime Voice Architecture Plan

- **Primary Model:** `gpt-4o-realtime-preview` for low-latency speech-to-speech
  (S2S) conversations; retain chained fallback (`gpt-4o-transcribe` → `gpt-4.1`
  → `gpt-4o-mini-tts`) for transcript-centric use cases.
- **Transport Selection:**
  - **WebRTC** for on-device interactions (fast peer-to-peer audio, easier lip
    sync with the animated avatar).
- **SDK Usage:** Start with the TypeScript Realtime Agents SDK to prototype and
  study best practices, then port the handshake and session management into an
  Android-native WebRTC layer.
- **Wake-Word Pipeline:** Local detector (“OBD Droid”) triggers session start;
  status indicator + quick mute button keep technicians in control. Push-to-talk
  remains for noisy bays.
- **Prompting & Persona:** Use the voice-agent metaprompt template provided by
  OpenAI to encode identity, tone, filler usage, pacing, and guardrails so the
  assistant speaks consistently and repeats critical info (VIN, part numbers).
- **Tool Handoffs:** Implement `transferAgents`-style tools to escalate to
  specialised agents (parts advisor, human expert). Use `session.update` events
  to swap instructions mid-call.
- **Hybrid Tool Usage:** Allow the realtime assistant to call text-first models
  (o3 validation, AI Diagnostic Analyzer) as functions, piping the results back
  into the speech stream.
- **Output Rendering:** Begin with Android TTS for voice playback, later explore
  streaming audio from the model for richer expressiveness. Drive avatar mouth
  motion using prosody markers from the realtime response stream.

## Integrated Roadmap

1. **Discovery & Logging Baseline (in progress)**
   - Finish DiscoveryManager logging (passive + active) and session handling.
   - Ensure SecurePreferences covers OpenAI key, wake-word toggle, privacy.

2. **Scan Orchestrator MVP**
   - Implement core stages and reporting.
   - Produce machine-readable outputs consumable by AI + CoPilot.

3. **AI Diagnostic Analyzer (Beta)**
   - Integrate GPT analysis post-scan.
   - Surface results in reports; expose via simple UI tile.

4. **Agent API Integration**
   - Wire Conversations API persistence, align IDs with discovery sessions.
   - Publish command-bridge tools via Agent/Assistants schema.

5. **CoPilot Foundations**
   - Conversational UI (text mode), animated avatar, context prompts without
     voice.
   - Basic command bridge (open screens, read reports).

6. **Voice & Wake Word Pilot**
   - Add wake word + audio streaming with opt-in toggles.
   - Implement WebRTC sessions; fall back to chained ASR/TTS pipeline when
     latency or device constraints require.
   
7. **Unified Technician Experience**
   - From main screen: start full scan → watch progress → receive AI report →
     ask CoPilot “What’s the plan?” → optionally run recommended actions.

## Privacy, Cost & Compliance Snapshot

- **Data Handling:** All AI calls go straight from device to OpenAI (user key).
  Provide clear disclosure before enabling; no data is routed through
  OBD-Droid servers. Offer settings to redact VIN or purge stored conversations.
- **Cost Controls:** Use GPT-3.5 by default; allow GPT-4/5 as premium option.
  Show per-scan estimate and monthly usage log. Warn when hitting custom spend
  ceilings.
- **Voice Consent:** Wake-word toggle off by default; highlight mic usage with
  on-screen indicator. Store wake-word activation events locally only and allow
  quick “sleep” or push-to-talk modes.
- **Logging Footprint:** Document retention policy for discovery/copilot logs;
  add “export + purge” option post-session.

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| GPT latency or cost spikes | Model fallback (GPT-3.5), offline caching for common diagnoses, upfront cost estimate. |
| Wake-word false positives / noisy bays | Tunable sensitivity, push-to-talk fallback, visual confirmations |
| Tool misuse via Agent API | Strict JSON schema validation, confirmation dialogs, rate limiting, audit logs. |
| Token bloat in conversations | Conversations API summaries, periodic pruning, user-adjustable verbosity. |
| Privacy concerns | Explicit consent screens, easy data purge/export, on-device wake-word processing. |
| Adapter variance | Stage timeouts, adaptive retries, per-make heuristics, continuous telemetry logging. |

## Next 48 Hours – Action Items

1. Finalise DiscoveryManager event schema (`CAPABILITY`, `SCAN_STAGE`).
2. Stub `ScanStage` interface + skeleton orchestrator service.
3. Evaluate Agent/Conversations API usage for persistent CoPilot memory and
   draft tool schema for the command bridge.
4. Extend `OpenAiService` signature + create prompt template draft for full
   scan analysis.
5. Draft CoPilot command list + permission model.
6. Sketch animated avatar states (idle, listening, thinking, speaking).
7. Prototype WebRTC session flow (Android ↔ OpenAI) and outline chained ASR/TTS
   fallback.