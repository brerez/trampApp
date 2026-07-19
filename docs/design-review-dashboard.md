# Dashboard Design Review (U3 / R18)

**Scope:** Current and planned TramApp Dashboard, reviewed against the goals of
minimalism, slickness, clear feedback, and fit-to-requirements. This document
resolves the design forks in R19–R26 and is the cited input for U9 (row visual
budget), U10 (status/chrome), and U11 (map + contrast).

**Method:** Static review of the current UI against the one-second-glance budget
and outdoor-legibility target on a Samsung S22+ (default and enlarged font
scales). Files reviewed: `ui/DashboardScreen.kt`, `ui/components/TramRow.kt`,
`ui/components/StationGroupCard.kt`, `ui/components/StatusIndicator.kt`,
`ui/components/MapStyleConfig.kt`, `ui/theme/Color.kt`, plus KTD5–KTD6.

---

## Current-state findings

1. **Relevance is signalled three times** for a home/work/school-bound station:
   the `StationGroupCard` border becomes a 2dp colored gradient, an emoji
   `DestinationBadge` row ("🏠 HOME") appears under the title, and inside the
   card each `TramRow` recolors its line badge *and* prints a "Towards home"
   subtitle. Four cues for one fact. This is visual noise and directly violates
   R20; it also dilutes which cue the eye should trust.
2. **The line badge already carries a relevance color** (`accentColor` →
   `HomeGlow`/`WorkGlow`/`SchoolGlow`), so the strongest, most local cue is
   already present at the row level — the card-level gradient and emoji badges
   are redundant duplication of it.
3. **No amenity glyphs exist** on the row today; the right side is a favorite
   star + countdown. There is spare horizontal room for a small glyph cluster
   between the headsign block and the countdown without touching countdown size.
4. **The header shows a developer string** — `Debug: N API calls, M stations
   found` — in the primary chrome, and the settings entry point is a **star**
   icon, which collides conceptually with the per-line favorite star used inside
   rows. There is a fully-built but **unwired** `StatusIndicator`/
   `CompactStatusIndicator`/`AppStatus` primitive available.
5. **The map is a 200dp light-tan rectangle** pinned above the station list.
   Outdoors it is a glare slab that pushes the first real answer (the nearest
   station's departures) toward/below the fold, and its light palette clashes
   with the `DeepBlack` theme.
6. **Surface contrast leans on glass-blur.** `SurfaceGlass` on `DeepBlack` with
   a 1dp `GlassBorder` is elegant indoors but the low-alpha border is weak under
   simulated glare; card edges should favor crispness where legibility competes.

---

## Resolved direction (R19–R26 forks)

- **R19 — Row content budget.** Row commits to exactly: line badge (identity +
  relevance color), countdown (largest/primary, unchanged size/weight), and a
  small low-emphasis amenity glyph cluster. **Countdown keeps `fontSize = 16.sp`
  / `FontWeight.ExtraBold` — it never shrinks to make room for glyphs.**
  → Confirms KTD5. Applied in U9.

- **R20 — Single relevance cue.** **Primary cue = the colored line badge**
  (validated: it is the most local, already-implemented, and reads at a glance).
  **Allowed secondary = the "Towards home/work/school" subtitle**, kept because
  it disambiguates *which* destination without adding a competing color block.
  **Remove:** the `StationGroupCard` gradient border **and** the emoji
  `DestinationBadge` row. → Confirms KTD5. Applied in U9.

- **R21 — Amenity glyphs.** Small, **monochrome** (single low-emphasis tint,
  `TextSecondary`/white-alpha, *not* a relevance color), ~16dp, placed just left
  of the countdown. Use `material-icons-extended`: `Icons.Filled.Accessible`
  (wheelchair) and `Icons.Filled.AcUnit` (air-conditioning). They sit below the
  countdown in visual weight and must not adopt Home/Work/School colors.
  Rendered only when the corresponding flag is non-null (R9). Verify at default
  and enlarged font scale (screenshot). Applied in U9.

- **R22 — Freshness cue replaces debug counter.** Wire the existing
  `CompactStatusIndicator` fed by a real `AppStatus` into `HeaderSection`, shown
  as a muted **"Updated Xs ago"** that ticks. Remove the `Debug: N API calls…`
  string from the visible header; keep the API-call counter but **gate it behind
  `BuildConfig.DEBUG`** (owner opted keep-but-gate, not delete). → Confirms
  KTD6. Applied in U10.

- **R23 — Settings icon.** Replace `Icons.Default.Star` with
  `Icons.Default.Settings` (gear) for the settings entry point, removing the
  collision with the favorite star. Applied in U10.

- **R11 — State distinction.** `AppStatus.connection`: `LOADING` while a refresh
  is in flight, `ERROR` while throttled/errored (`throttleUntil` active),
  `ONLINE` otherwise. The muted "Updated Xs ago" (idle) must be visibly distinct
  from the loading and error treatments. Applied in U10.

- **R25 — Map treatment.** The map must **not** occupy prime vertical space by
  default. Direction: **collapsible, collapsed-by-default to a slim strip**
  (~64dp peek) that the user can expand; when expanded cap it lower than today's
  200dp is acceptable but the default state must leave the first `Ready` station
  card visible above the fold. Restyle to a **dark** map palette (see R26).
  Applied in U11.

- **R26 — Contrast + dark map.** Replace the light tan (`#ebe3cd`) inline map
  style with a **dark** style (dark geometry, muted water, low-key labels) so no
  bright rectangle remains. Validate `SurfaceGlass`/`GlassBorder` card edges
  against `DeepBlack` under simulated glare; **favor a crisper card border**
  (raise border alpha / drop reliance on blur) where legibility competes with
  the glass aesthetic. Adjust `Color.kt` tokens only if the glare check fails.
  Applied in U11.

- **R24 — Expansion stability (design note).** Auto-expand targets the first
  *settled* (Ready) station once, and is sticky after user interaction; it must
  not re-fire as the visible set trickles in. Applied in U5.

---

## Notes / overrides

- No KTD is overridden by this review. KTD5 (single colored-badge cue) and KTD6
  (reuse `StatusIndicator`, gate the counter) are both validated as the best
  reading on an S22+ outdoors.
- The "Towards …" subtitle is explicitly retained as the *single* permitted
  secondary cue under R20; U9 must not also reintroduce a card-level color.
