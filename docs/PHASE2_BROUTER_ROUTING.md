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
