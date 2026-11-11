# Asset provenance

This inventory covers binary and externally sourced assets shipped in, or used
for Mhirex's public presentation. It is intentionally conservative: an asset is
not treated as freely licensed merely because it is present in the repository.

| Asset | Use | Provenance / licence status |
|---|---|---|
| `logo.png` | Android launcher icon source, and the source for the Play listing below | Supplied for the Mhirex rebrand by the project owner. Confirm the owner's distribution terms before release. |
| `app/src/main/res/mipmap-nodpi/logo.png` | Android launcher icon | Exact copy of `src/images/logo_readme_dark.png`, with the supplied white mark on a solid black background. |
| `app/src/main/res/drawable-nodpi/brand_logo.png` | About card, startup welcome, and sign-in dialog | Current Mhirex mark, supplied by the project owner as a 1254 px RGBA and cropped to its alpha bounds at 512x511. The dark tile launcher icon uses `logo_readme_dark.png`; in-app halo treatments continue to use this transparent mark. |
| `src/images/logo_readme_light.png`, `src/images/logo_readme_dark.png` | README wordmark | Derived from the mark above by this project: alpha-bbox cropped, scaled to 62.5% of a 512 px canvas. The light variant adds a `#111114` rounded tile (from `values/colors.xml` `colorSurface`) because the mark is white on transparent and would be invisible on GitHub's white page. No third-party artwork added. |
| `app/src/main/ic_launcher-playstore.png` | Play Store icon | Resized derivative of `logo.png`; no additional artwork. |
| `fastlane/metadata/android/en-US/images/icon.png` | Play Store icon | 512 px resized derivative of `logo.png`; no additional artwork. |
| `fastlane/metadata/android/en-US/images/featureGraphic.png` | Play feature graphic | Neutral background with a centered derivative of `logo.png`; created for this rebrand. |
| `src/images/ic_banner.png` | README/store banner asset | Resized derivative of `logo.png`; no additional artwork. |
| `app/src/main/assets/fonts/Roboto-Regular.ttf` | Bundled editor/export font | Upstream LibreCuts asset. Roboto is distributed under Apache License 2.0 by Google; retain the applicable font notices when redistributing. |
| `app/src/main/res/raw/film.json` | Loading-screen Lottie animation | Upstream LibreCuts asset. Its exact separate licence/provenance has not been independently verified; retain the upstream MIT notice and audit before release. |
| `app/src/main/res/drawable/filter_preview_*.jpg` | Filter picker previews | Origin and licence have not been established. **Release blocker:** regenerate or replace with project-owned artwork, or document the applicable licence and attribution. |
| `app/src/main/res/drawable/trans_preview_*.webp` | Transition picker previews | Origin and licence have not been established. **Release blocker:** regenerate or replace with project-owned artwork, or document the applicable licence and attribution. |
| `src/images/sc_*.png` and the remaining badge images | README/store presentation | Upstream LibreCuts assets. Retain upstream attribution and verify their redistribution terms before release. `badge_obtainium.png` and `badge_discord.png` were deleted with their README badges: both pointed at third-party/upstream destinations (an unaffiliated install redirect, and LibreCuts' own Discord) rather than anything Mhirex owns. |
| `app/src/main/res/drawable-nodpi/ig_idea.jpg` | About screen and startup welcome profile photo | Public Instagram profile image for `@_fivetriple.8_`, used at the profile owner's request; image rights remain with the profile owner. |
| `app/src/main/res/drawable-nodpi/ig_idea.jpg`, `website/assets/founder_fivetriple8.jpg` | App and landing-page creator profile photo for @_fivetriple.8_ | Supplied Instagram CDN profile image; image rights remain with the profile owner. Mehul Patel's separate `ig_mehul.jpg` image is unchanged. |
| `app/src/main/res/drawable/ic_instagram_24.xml` | Instagram link badge | Original vector recreation of the familiar Instagram camera outline; no copied raster artwork. |
| `app/src/main/res/drawable-nodpi/ar_goggles.png`, `ar_skull_mask.png` | Face-tracked editor accessories | Original Mhirex artwork generated for this project; no third-party image assets. |
| `app/src/main/res/drawable-nodpi/brand_youtube.png`, `brand_instagram.png` | Optional creator watermark badges | Original simplified project-made badge artwork for user-selected creator handles; not copied from the external image URLs in the feature request. |
| `app/src/main/res/raw/freeze_stinger.wav` | Freeze template sound cue | Original sound synthesized for Mhirex from generated oscillator/noise samples. |
| `app/src/main/res/drawable-nodpi/death_skull.png` | Freeze + Death template skull overlay | Supplied by the project owner from the linked PNGEgg thumbnail; checkerboard preview pixels were removed to create transparency. The source page does not establish redistribution rights; verify before a stable release. |
| `app/src/main/res/raw/birthday_chime.wav` | Birthday template sound effect | Original chime synthesized for Mhirex from generated oscillator samples. |
| `app/src/main/res/raw/freeze_impact.wav`, `shake_rattle.wav` | Freeze + Death template sound effects | Original Mhirex effects synthesized from oscillator, transient-noise, and rattle samples. |
| `app/src/main/res/raw/happy_birthday_melody.wav` | Birthday template music | New synthesized recording made for Mhirex. The underlying familiar birthday composition may have different rights by territory; the template adds a reminder to confirm local rights before sharing. |
| `app/src/main/assets/face_landmarker.task` | On-device face landmark tracking for AR accessories | Official MediaPipe Face Landmarker model, downloaded from Google AI Edge's model storage. SHA-256 `64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`. Model cards identify Apache License 2.0; the MediaPipe SDK is pinned in Gradle and its generated AboutLibraries notices remain part of release review. |
| `app/src/androidTest/assets/mhirex_test_fixture.mp4` | Instrumentation-only test media | Synthetic local fixture; test-only and not included in the production APK. |
| `website/assets/logo.png` | Website wordmark and the source for the tab icons below | The 1254 px RGBA owner-supplied mark, the same artwork `brand_logo.png` above was cropped from, but not byte-identical to it (that file is cropped to its alpha bounds at 512x511). Confirm the owner's distribution terms before release, as for the other derivatives. |
| `website/assets/favicon-16.png`, `favicon-32.png`, `favicon-48.png`, `favicon.png`, `favicon.ico` | Browser-tab icons | Derived by `tools/make-favicons.py` from `website/assets/logo.png`: the mark cropped to its alpha bounds, scaled to 88% of a square tile, every surviving pixel set to the single colour `#07080d` and the rest left fully transparent. No third-party artwork added. The `.ico` frames are PNG-encoded and assembled by that script rather than by Pillow, whose resampler turned the monochrome glyph into dozens of near-black shades. |
| `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` | Native media processing dependency | GPL-3.0. It is a dependency rather than a repository asset; its source, licence, and corresponding-source obligations must accompany distributed binaries. |

Pixabay search is user initiated. The app caches API responses for 24 hours,
shows creator names, and opens source pages for review. Pixabay's API does not
expose audio search, so Music and Sound Effects use the official Pixabay pages
inside the app; the app imports audio when the user taps Pixabay's Free Download
action. Users should retain any licence records required for their use.
Any future asset addition must be added to this table before release.
