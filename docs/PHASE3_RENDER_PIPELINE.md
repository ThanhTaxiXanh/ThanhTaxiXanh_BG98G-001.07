# Phase 3 — Android Auto map render pipeline

Phase 3 removes full-frame RGBA capture work from the Android main thread and
reduces unnecessary pixel bandwidth while keeping the same dashboard contract.

## Capture architecture

```
MapLibre / VirtualDisplay
        |
        v
ImageReader
        |
        v
TTX-MapCapture HandlerThread
        |
        +-- acquireLatestImage()
        +-- adaptive capture throttle
        +-- copyPixelsFromBuffer() into back buffer
        |
        v
short frame-lock swap
        |
        v
published bitmap
        |
        v
Android Auto / phone renderer
```

The renderer holds a frame lease for one draw pass. Capture writes only into the
back buffer. The expensive pixel copy occurs outside the frame lock; publication
is only a short pointer swap. `stop()` serializes with capture before readers
or bitmaps are closed/recycled.

Frame notifications are coalesced on the main thread. Stale notifications are
rejected using a capture-session token after stop, resize or restart.

## Adaptive capture cadence

The capture callback follows the existing map cadence:

- interactive: about 30 fps
- moving: about 24 fps
- stationary with GPS: about 12.5 fps
- waiting for GPS: about 8 fps

`acquireLatestImage()` and intentional throttling discard stale/intermediate
frames instead of creating a backlog.

## Adaptive render resolution

Requested dashboard size and internal capture size are tracked separately.
This prevents the resize detector from restarting MapLibre just because the
capture surface is intentionally smaller.

Pixel policy:

- up to 700,000 requested pixels: 100%
- 700,001–1,300,000 pixels: 90%
- above 1,300,000 pixels: 82%

VirtualDisplay DPI is scaled by the same factor so MapLibre text and symbol
sizes remain visually close to native rendering. GPS matching, route geometry,
speed limits, signs and warnings are unaffected because only raster output
resolution changes.

## Debug performance metrics

Every debug sampling window reports aggregate-only metrics:

- delivered frame FPS
- deliberately dropped capture frames
- average / maximum bitmap copy time
- average / maximum frame-lock wait
- average / maximum Android Auto host draw time
- GPS processing time
- map step time
- Java heap usage

No GPS coordinates or trip history are included in the performance log.

On the phone dashboard, tap the status line to open the current Phase 3
performance summary. This is intended to make field testing possible without a
computer.

## Field acceptance targets

These are diagnostic targets rather than hard build gates:

- frame-lock wait average should normally stay below 1 ms
- bitmap copy must not execute on the main thread
- host draw average should remain comfortably below the moving-frame interval
  (42 ms), preferably below 20 ms
- no continuous MapLibre restart when adaptive capture resolution is active
- no recycled-bitmap or concurrent-modification crashes during resize,
  pause/resume or menu transitions
