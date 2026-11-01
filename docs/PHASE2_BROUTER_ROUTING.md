# Phase 2 — BRouter offline routing integration

TTX AALauncher uses BRouter's exported Android service instead of embedding a
second copy of the routing engine. BRouter is MIT licensed.

The user installs the official `btools.routingapp` app and downloads the RD5
segments needed for the driving area. TTX binds explicitly to
`btools.routingapp.BRouterService` with the upstream AIDL contract.

Requests use current GPS + destination, `v=motorcar`, `fast=1`,
`trackFormat=json`, `timode=9`, and GPS heading when available.

BRouter GeoJSON is parsed into a bounded `RoutePlan`. The LineString is used
for the route polyline and BRouter voicehints become real maneuver objects.
No turn is inferred from the current street name.

The active route is persisted in app-private storage so phone and Android Auto
can consume the same route without computing it twice.

Upstream project: abrensch/brouter — MIT License.


## Phase 2D — destination lookup

The phone route dialog accepts either direct latitude/longitude or a place /
address query. `DestinationSearch` uses Android Geocoder on a worker thread,
first biased around the current GPS position and then falling back to a wider
Vietnam search. Routing remains BRouter/RD5 offline after coordinates are
resolved.

## Phase 2E — Vietnamese voice guidance

Android Auto uses system `TextToSpeech` with Vietnamese locale and navigation
audio usage. Prompts are distance-stage based and speed-aware. Repeated GPS
updates do not repeat the same prompt. Roundabout exit numbers are spoken from
BRouter voice-hint data. Off-route speech is rate limited.

## Phase 2F — stable progress matching

Route progress now projects GPS onto route segments rather than picking only
the nearest route vertex. Matching uses a bounded local window, coarse/fine
rejoin search, heading penalties and a backward-progress penalty. Off-route is
latched after three bad samples and requires two good samples to rejoin, which
reduces false reroutes on parallel roads, junctions and noisy GPS.

The visual MapLibre route is independently reduced to a bounded coordinate
count; full BRouter geometry remains available to the matcher.
