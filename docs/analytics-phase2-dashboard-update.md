# Analytics Plan – Vehicle History Phase 2

**Owner:** Data & Analytics – Samira H.  
**Prepared by:** Agent B (2024-07-16)  
**Dashboard Target:** Looker – `Vehicle History Experience` folder (`vh_phase2_usage`)

## Objectives
- Track engagement with the new recall module, usage badges, and odometer breakdown.  
- Ensure `PvChange` enum migration does not break downstream event pipelines.  
- Provide day-0 adoption readouts for Product, QA, and Support teams.

## Event Instrumentation Summary
| UI Interaction | Event Name | Properties | Notes |
| --- | --- | --- | --- |
| Recall expansion | `vehicle_history_recall_expand` | `recall_id`, `status`, `source` | Fire on expand/collapse (boolean field `expanded`) |
| Recall CTA tap | `vehicle_history_recall_cta` | `recall_id`, `cta_type` | Distinguish dealer call vs. schedule service |
| Usage badge tap | `vehicle_history_usage_badge_tap` | `badge_type`, `has_multi_usage` | Fire once per session per badge |
| Odometer expand | `vehicle_history_odometer_expand` | `vin`, `odometer_state` | Include discrepancy flag |
| VIN auto-detection | `vehicle_history_vin_autodetect` | `vin`, `elapsed_ms` | Emitted when VIN populated via PV change |

## Dashboard Updates
- **New Tiles**
  - Phase 2 Overview: conversion funnel combining unique sessions, recall interactions, usage badge engagement, odometer expansions.  
  - Recall Detail Heatmap: open vs. completed recall engagement by make/model.  
  - Usage Badge Summary: stacked bar chart with commercial/rental/fleet distribution.  
  - Odometer Integrity Monitor: % of VINs with discrepancies week-over-week.
- **Existing Tile Adjustments**
  - Update filters to include Phase 2 feature flags (`vehicle_history_recalls`, `vehicle_history_usage`, `vehicle_history_odometer`).  
  - Add drill links to QA TestRail run IDs TR-1893/TR-1894 for easy traceability.  
  - Refresh KPI thresholds for “Healthy” state (≥35% recall module engagement in first week).

## Data Pipeline Actions
- Publish updated event schema to analytics repo (`analytics/events/vehicle_history_phase2.yaml`).  
- Deploy ETL changes in Airflow DAG `vh_feature_events` (add enum-to-string mapping for `PvChange` types).  
- Backfill 30 days of historical data to populate baseline comparison tiles.

## Validation & Rollout
- Pre-launch validation via Looker dev workspace (dashboard snapshot scheduled for 2024-07-24).  
- QA to run logcat + Snowplow stream verification during staging rollout.  
- Production dashboard go-live targeted for 2024-07-30 (aligned with feature flag ramp to 10%).  
- Add post-launch alert (PagerDuty) for event ingestion failures >15 minutes.

## Owners & Follow-Up
- **Dashboard Build:** Data viz (Samira H.) – ensure tile queries merged by 2024-07-22.  
- **Schema Review:** Analytics Engineering (Miguel R.) – sign-off on new fields by 2024-07-19.  
- **Product Review:** Vehicle History PM (Dana T.) – dashboard walkthrough scheduled 2024-07-31.  
- **Support Enablement:** Provide weekly digest to Support Ops summarizing recall interaction metrics during ramp.
