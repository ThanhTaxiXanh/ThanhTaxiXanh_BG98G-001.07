# Personal Android Auto screen contract

This repository has a personal/debug Android Auto mode intended for the owner's
own head unit. The following layout is a product requirement and must not be
removed by performance refactors:

- One screen contains the live map, GPS speed, current explicit speed limit,
  traffic signs, cameras/road warnings and route/turn guidance.
- The map remains the dominant left-side surface.
- The right side uses two or three stacked utility rows. Fresh installs use
  the `stacked` three-row preset.
- When a route is active, turn guidance owns the top-right row. When no route
  is active, that row may fall back to the Vietnamese calendar/events.
- The remaining rows can show signs/warnings and QR/other personal utility
  information according to the selected layout.
- Route guidance must come from a real route engine. The renderer must never
  fabricate turn instructions from the current street name.

## Current implementation status

Phase 1 provides the multi-tile debug surface, adaptive render cadence,
warm map pause/resume, cached battery/event reads, distance-driven road
queries and the `RouteGuidanceState` UI contract.

A route engine is not yet connected. `RouteGuidanceState.EMPTY` therefore
keeps the guidance panel inactive until a later routing phase supplies
validated maneuver, distance and ETA data.

The production/release Android Auto path remains separately gated. This
personal-screen requirement must not be used as evidence of Google Play or
Android for Cars policy compliance.
