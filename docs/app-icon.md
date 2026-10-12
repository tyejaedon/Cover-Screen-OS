# App icon

The launcher icon uses the supplied `assets/coveros-icon.jpeg`. The original was
found in the main checkout at `app/src/main/assets/coveros-icon.jpeg` and copied
unchanged into the source artwork folder. It is not bundled as a runtime asset.

The JPEG includes a checkerboard background. The generator extracts the rounded
tile and masks its outside corners rather than displaying that checkerboard.
It creates lossless legacy and round icons for all five Android densities, plus
a padded adaptive foreground. The adaptive background matches the dark tile.
A simple XML version of the Z/arrow mark provides Android's themed icon.

To regenerate, install Pillow in your development environment and run:

```sh
python3 tools/generate_app_icon.py
```

`drawable/ic_launcher_fallback.xml` retains the previous XML-only folding-phone
design. If the artwork cannot be packaged, point the foreground in both adaptive
icon XML files at `@drawable/ic_launcher_fallback`. Android does not automatically
fall back from a missing resource; this is an explicit build-time alternative.

Check the icon on circular, rounded-square, and themed launcher masks on a device.
