# Wallpaper media

Wallpaper imports keep the selected file bytes unchanged in app-managed storage. The image preview uses the detected cover display aspect ratio and the cover renderer uses the same framing, rotation, dim, and blur transform. Images are rendered with Android `ImageDecoder`, which applies EXIF orientation, and sampled to a bounded working bitmap rather than recompressed or decoded at their full source size. The decoded image target is capped at 6 megapixels (about 24 MiB for ARGB pixels).

## Import limits

| Media | Accepted formats | Limits |
| --- | --- | --- |
| Still image | Formats supported by the device's Android image decoder | 64 MiB file, at most 8192 × 8192 pixels and 40 megapixels |
| Animated image | GIF87a/GIF89a | 64 MiB file, at most 2048 × 2048 pixels (4,194,304 pixels), at most 10 seconds, at most 300 frames, no frame delay shorter than 34 ms |
| Video | MP4 or WebM containers with H.264/AVC, HEVC, or VP9 video | 64 MiB file, at most 2048 × 2048 pixels (4,194,304 pixels), at most 10 seconds, at most 30 fps and 300 frames |

The byte limit is checked while copying, including for providers that do not report a size. The importer inspects file signatures and media metadata instead of relying on the provider MIME type or filename. It checks dimensions before decoding and bounds poster/image decoding to 6 megapixels. Animated files are not converted or recompressed. Video audio is muted. An import is staged and validated before settings change; failures preserve the previous wallpaper and delete the partial copy.

The 4,194,304-pixel animation canvas is at most 16 MiB for one ARGB frame; the 6-megapixel still-image target is at most 24 MiB. GIF playback uses Android's animated-image decoder rather than loading a list of decoded frames in the app. Video decoding is delegated to Android's media codec and is bounded by the same 4.19-megapixel frame ceiling, duration, rate, and sample count. Codec-internal buffer counts vary by device, so total decoder memory still needs measurement on supported cover hardware.

The wallpaper screen previews crop/fit, crop position, zoom, rotation, dim and blur. Selection and framing buttons wrap onto additional rows at narrow widths or larger font sizes. Crop offset is bounded so the transformed image cannot move beyond its available edges; zoom ranges from 1× to 3×, and rotation changes in 90-degree steps. These settings are included in settings export/import and reset with wallpaper customization. Older exported settings use centered framing at 1× zoom and zero rotation.

GIF and video playback run only while the launcher host is started and the screen is interactive. Playback pauses on screen-off/host-stop and releases its view/player when removed. Android battery-saver mode uses a still poster rather than animation. Image/video blur is applied before the dim layer, extending beyond image edges in fit mode but clipped to the wallpaper viewport below icons and other foreground UI.

## Verification status

The repository contains unit tests for byte-copy boundaries, dimension boundaries, malformed GIF input, animation duration/frame-rate checks, and wallpaper setting persistence/import validation. No supported cover device was available for this change. Frame pacing, device memory pressure, codec compatibility across supported devices, battery use, and display-off behavior still require on-device QA; no measured performance claim is made here.
