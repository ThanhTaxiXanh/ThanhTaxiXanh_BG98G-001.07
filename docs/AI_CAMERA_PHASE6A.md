# TTX AI Camera · Phase 6A (experimental)

## Status
- 70mai A500S Plus supports **Wi-Fi preview and downloading through the official 70mai app**. No verified public RTSP/API is assumed.
- Import photo/video via Android Storage Access Framework, analyse offline on a background worker.
- Inference needs a **user-supplied, correctly licensed Vietnamese traffic-sign YOLOv8 raw ONNX model** and an exact class-order **labels.txt**. Neither is included. A general COCO model is not a traffic-sign model.
- Video scan samples one synchronized frame about every 2 s, up to 3 min and 60 candidate observations per import. Not a production-grade full coverage scanner.
- ONNX model must use float32 NCHW RGB input [1,3,S,S], raw [1,4+C,N] or [1,N,4+C] output **without built-in NMS**. TTX runs NMS and records evidence.
- Label P-127 or another generic speed-sign category does not encode the number. **The user must read and choose the actual value** (20–120 in steps of 10) from the evidence. No model confidence alone turns a suggestion into legal speed.
- No model installed => no automatic recognition. This is explicit in the UI.

## GPS / road attribution
The 70mai downloaded MP4 cannot be assumed to contain embedded GPS. Some A500S recordings keep a separate .GPS/GPSData000001.txt track on microSD. Sidecar parsing and time alignment are **not implemented in Phase 6A**. Importing a video will NOT attach the phone's live location to an earlier sign. Reviewer must supply verified coordinates; optional OSM way ID must be checked independently.

Approved items are kept in private storage and can be exported as personal GeoJSON. They are **not automatically injected into nationwide OSM, not claimed to be official speed limits, and do not override safety-critical map speed values** until a separate way-direction/applicability validation pipeline is implemented.

## Lane / road classification
Camera-based lane segmentation is a separate model task (YOLOv8-seg/road segmentation). A lane count, road surface or perceived road class alone **does not determine the applicable legal speed**. Phase 6A lets the user record a road-class observation for review; it does not claim AI lane segmentation is trained or ready.

## Test plan
1. Load VN traffic sign ONNX and matching labels. Import an image; verify candidate class + confidence and review before approval.
2. Import a video exported from the 70mai app. Confirm candidate timestamps are relative to the clip, but GPS stays unset.
3. Confirm approval requires valid manually verified coordinates; only evidence and personal GeoJSON are saved.
4. Reject a candidate; verify it is excluded from exported GeoJSON.
5. Verify the Android Auto UI remains unchanged and the phone AI module never prompts in-motion tapping.

## Next steps
(1) Test actual A500S video and original GPSData sidecar to implement timestamp-aligned coordinates; (2) train/benchmark Viet Nam speed-sign value OCR and lane segmentation; (3) match reviewed signs to directional OSM way segments with applicability/end sign rules before using them for on-map legal speed displays; (4) optional live bridge only if firmware/API compatibility is independently verified.


## VTSR preset and xADAS interoperability (November 1, 2026)
- User-confirmed download of VTSR INT8 and mapping from liamxdev/vtsr on Hugging Face (AGPL-3.0).
- Model remains private to device. Validate raw 56-class output and ONNX metadata class order before activation. Never infer class order from unordered descriptions.
- Warm ONNX session is reused during offline video sampling. Real device testing is still required.
- Current Google Play xADAS package com.kaibatech.drivedeck.xadas advertises Wi-Fi dashcams including 70mai, but does not publish an A500S-specific raw-video API.
- AppPage lists xADAS v0.2.5 with a Pro setting to share lead distance and too-close warnings, not raw video.
- Third-party mirrored APK was only v0.1.11; too old for current Wi-Fi interoperability. No current APK has been acquired or debugged.
- TTX offers a button opening the installed xADAS app for camera-source configuration. No undocumented IPC is called.
- Pilot is ambiguous; no autonomous-driving/vehicle-control integration is claimed or added. Identify exact Pilot model/app first.
