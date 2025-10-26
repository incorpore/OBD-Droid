# OBD-Droid Documentation Hub

Welcome to the OBD-Droid documentation center. This index provides quick access to all planning documents, roadmaps, case studies, and reference materials.

---

## 📚 Documentation Map

### Core Plans & Engineering Backlogs

Central planning documents for the OBD-Droid platform:

- **[docs-master-todo.md](./docs-master-todo.md)** – Master engineering backlog across all features (diagnostics, telemetry, gauges, AI, dealer tools)
- **[obd-unified-plan.md](./obd-unified-plan.md)** – Focused sequencing of near-term ship goals and stretch items
- **[platform-enhancement.md](./platform-enhancement.md)** – Cross-team platform upgrades, build system, and release coordination
- **[telemetry-and-history.md](./telemetry-and-history.md)** – Process-variable refactor and Vehicle History roadmap

---

### Dealer Diagnostics & ECU Expansion

Professional-grade diagnostic tools and multi-ECU support:

#### Planning & Roadmaps
- **[dealer-diagnostics-roadmap.md](./dealer-diagnostics-roadmap.md)** – Dealer-grade diagnostics roadmap and feature backlog
- **[multi-ecu-discovery-plan.md](./multi-ecu-discovery-plan.md)** – Discovery and addressing plan for multi-ECU vehicles
- **[ecu-module-refactor.md](./ecu-module-refactor.md)** – ECU abstraction strategy and architecture
- **[ecu-conversion-abstractions.md](./ecu-conversion-abstractions.md)** – ECU data conversion and unit handling

#### Diagnostic Reports & Case Studies
- **[dealer-diag-reports/](./dealer-diag-reports/)** – In-house dealer shop diagnostic reports
  - **[README.md](./dealer-diag-reports/README.md)** – Quick start guide for diagnostic workflows
  - **[2022_GMC_Canyon_P0302_Cylinder2_Misfire.md](./dealer-diag-reports/2022_GMC_Canyon_P0302_Cylinder2_Misfire.md)** – P0302 misfire diagnosis with MAP sensor analysis
  - **[2014_Ford_F150_PSCM_Lockout_No_Power_Steering.md](./dealer-diag-reports/2014_Ford_F150_PSCM_Lockout_No_Power_Steering.md)** – PSCM lockout diagnostic case study

#### Vehicle Baseline Profiles
- **[vehicle-profiles/](./vehicle-profiles/)** – Complete OBD baseline scans and diagnostic records
  - **[README.md](./vehicle-profiles/README.md)** – Profile system overview and template
  - **[2017_Nissan_Frontier_VIN778459.md](./vehicle-profiles/2017_Nissan_Frontier_VIN778459.md)** – Baseline scan example (healthy vehicle)
  - **[2022_GMC_Canyon_VIN107528.md](./vehicle-profiles/2022_GMC_Canyon_VIN107528.md)** – Diagnostic profile example (active fault code)

---

### Vehicle Intelligence & AI

AI-powered diagnostic assistance and intelligent scanning:

- **[vehicle-intelligence-suite-plan.md](./vehicle-intelligence-suite-plan.md)** – Complete unified plan (64% implemented) including:
  - Implementation status and roadmap
  - Agents API architecture (core AI strategy)
  - CoPilot world-class UI/UX (Lottie, Markwon, Material 3, voice input)
  - Full Vehicle Scan Orchestrator design
  - AI Diagnostic Analyzer integration
  - Phased rollout plan with priorities

---

### Launch, Growth & Store Assets

Play Store launch planning and go-to-market strategy:

- **[launch-plan.md](./launch-plan.md)** – Play Store launch workflow and go-to-market checklist
- **[store-assets-checklist.md](./store-assets-checklist.md)** – Asset production checklist (copy, visuals, screenshots, ASO)

---

## 🎯 Quick Navigation

**Starting a new diagnostic?**
→ [dealer-diag-reports/README.md](./dealer-diag-reports/README.md)

**Creating a vehicle baseline?**
→ [vehicle-profiles/README.md](./vehicle-profiles/README.md)

**Looking for the master task list?**
→ [docs-master-todo.md](./docs-master-todo.md)

**Planning next sprint?**
→ [obd-unified-plan.md](./obd-unified-plan.md)

**Need CoPilot or AI integration specs?**
→ [vehicle-intelligence-suite-plan.md](./vehicle-intelligence-suite-plan.md)

**Preparing for launch?**
→ [launch-plan.md](./launch-plan.md)

---

## 📁 Directory Structure

```
docs/
├── index.md                              ← You are here
│
├── Core Planning
│   ├── docs-master-todo.md
│   ├── obd-unified-plan.md
│   ├── platform-enhancement.md
│   └── telemetry-and-history.md
│
├── Dealer Diagnostics & ECU
│   ├── dealer-diagnostics-roadmap.md
│   ├── multi-ecu-discovery-plan.md
│   ├── ecu-module-refactor.md
│   ├── ecu-conversion-abstractions.md
│   ├── dealer-diag-reports/
│   │   ├── README.md
│   │   ├── 2022_GMC_Canyon_P0302_Cylinder2_Misfire.md
│   │   └── 2014_Ford_F150_PSCM_Lockout_No_Power_Steering.md
│   └── vehicle-profiles/
│       ├── README.md
│       ├── 2017_Nissan_Frontier_VIN778459.md
│       └── 2022_GMC_Canyon_VIN107528.md
│
├── AI & Intelligence
│   └── vehicle-intelligence-suite-plan.md
│
└── Launch & Growth
    ├── launch-plan.md
    └── store-assets-checklist.md
```

---

## 🔄 Document Status Legend

- **Roadmap** – Strategic planning document
- **Backlog** – Active task list / engineering queue
- **Case Study** – Real-world diagnostic example
- **Baseline** – Vehicle health snapshot
- **Template** – Copy and customize for new work
- **Archive** – Historical reference (not actively maintained)

---

*For build instructions, codebase navigation, and contributor guidelines, see [CLAUDE.md](../CLAUDE.md) and the main project README.*
