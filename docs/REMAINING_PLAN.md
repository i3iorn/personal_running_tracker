# Personal Running Tracker — Remaining Work Plan

Reference: original MVP spec v1.0 (Phases 1–6). Phases 1–3 complete (foundation,
tracking UI, sensor-assisted fusion). Phase 6 items 1–3 (battery benchmarking,
accuracy benchmarking, algorithm tuning) are baked in and modular. Live clock and
CSV export (runs / diagnostics / tuning) shipped. Below is everything left.

## Phase A — Polish & small gaps (1 session)

1. **Notification bar padding**: top app padding must account for the status bar /
   system notification area so "Personal Running Tracker" never overlaps system
   notifications. Use `WindowInsets` / `fitsSystemWindows` or add top padding via
   `WindowCompat` edge-to-edge handling.
2. **Custom launcher + notification icon**: vector drawable running-shoe/runner icon
   in `res/drawable/`; adaptive launcher icon (`mipmap-anydpi-v26`), and set it on
   the tracking foreground notification (`setSmallIcon`).
3. **Discard run**: service has `ACTION_DISCARD_RUN` but no UI button — add Discard
   control next to Pause/Finish, only enabled while a run is active.
4. **Battery level realism**: `getBatteryLevel()` currently a placeholder — wire
   `BatteryManager.BATTERY_PROPERTY_CAPACITY` so battery diagnostics record real
   percentages.
5. **Run summary screen**: after Finish, show the run's summary (distance, durations,
   pace, elevation, steps, diagnostics rating) per spec §6.1.

## Phase B — Splits & summaries (spec §5, §6)

1. **Splits table + entity** (§6.3): Room `SplitEntity` (run_id, split_number,
   distance, duration, avg pace, avg speed, elevation change); computed at finish
   for every configured unit (km default, mile option); arbitrary split distances
   supported in the statistics engine later.
2. **Pace improvements** (§5.2): smoothed current pace, moving-average pace, split
   pace; suppress instantaneous pace when GPS confidence is poor (use existing
   confidence/uncertainty from fusion).
3. **Route map** (§6.2): MapLibre map of recorded route, colouring segments by
   reliability (GPS / sensor-assisted / poor confidence) using the estimated-position
   records we already store.

## Phase C — Statistics engine (spec §8, Phase 4)

Pure-Kotlin statistics module, no Android deps, TDD with synthetic datasets:
1. Basic totals (§8.1): distance, time, runs, averages, longest/fastest.
2. Activity stats (§8.2): daily/weekly/monthly/yearly volumes, frequency, streaks.
3. Performance (§8.3): pace/speed/distance trends, personal records (best 1/3/5/10 km,
   HM, marathon + arbitrary distances) computed from GPS observations, PR progression.
4. Rolling stats (§8.4): 7/30/90/365-day rolling distance, pace, frequency.
5. Comparisons (§8.5): week/previous week, month/year, custom ranges — absolute and
   percentage deltas clearly distinguished.
6. UI: dedicated Statistics section (§9.3) with line/bar charts, histograms,
   calendar heatmap (Compose-compatible charting), time-range selector, tap-through
   to underlying runs.

## Phase D — Data management (spec §12, Phase 5)

1. **GPX export** (route + timestamps + elevation) — parser already exists for import;
   add the writer.
2. **GPX import UI** — importer exists; add file-picker flow, mark runs as imported,
   dedupe, include in statistics.
3. **CSV splits/statistics export** — extend existing exporter.
4. **Full backup & restore** (§12.3): export complete DB + metadata + manifest as a
   single archive; restore without cloud/account.
5. **Manual run corrections** (§7.2): edit name/notes/distance; store original and
   adjusted values (`user_adjusted` flag already in schema); recalculate derived stats.

## Phase E — Reliability & optimization (spec §14, Phase 6 remainder)

1. **State persistence**: survive process recreation — persist run state
   (accumulated time/distance, current run id) to DB frequently so an interrupted run
   resumes instead of silently disappearing.
2. **Database optimization** (§11.7): batch writes, transactions, indexed queries;
   keep raw GPS observations forever, sample/summarize high-frequency sensor data.
3. **Error handling**: GPS loss, sensor unavailability, permission revocation, low
   battery, storage exhaustion — surface degraded-tracking state to the user
   (already partially modelled in fusion states).
4. **UI refinement** (§3.2, §9): History screen with search/filter/sort and route
   thumbnails; Settings screen for units, profile, auto-pause, export (§9.4) —
   replacing the current single-screen layout.
5. **Reliability & battery testing** (§15): on-device battery benchmarks across
   profiles using the run_diagnostics data we now export; accuracy vs continuous-GPS
   reference in open/urban/forest conditions; data-integrity tests (backup/restore,
   crash recovery, migrations).

## Out of scope (unchanged, spec §17)

No social, no cloud sync, no training plans/coaching, no smartwatch, no ads,
no subscriptions. Recording must stay fully offline.

## Sequencing

A → B are quick wins that make the app usable for real runs.
C is the largest block (statistics is a core product feature per spec).
D/E can interleave: export/backup before statistics if data ownership matters more;
otherwise statistics first, data management second, hardening last.

## MVP acceptance checklist (spec §18) — status

- Tracking: start/pause/resume/finish ✅, screen-off recording ✅ (foreground service),
  offline ✅, live clock ✅ (fixed), discard UI ⬜ (A3)
- Sensor-assisted: anchors ✅, fusion ✅, uncertainty ✅, profiles ✅
- Statistics: ⬜ (Phase C)
- Data management: CSV export ✅, GPX ⬜ (D1–D2), backup ⬜ (D4), corrections ⬜ (D5)
- Quality: battery benchmarking ✅ (data collection + export), device benchmarks ⬜ (E5),
  DB integrity tests ⬜ (E5)
