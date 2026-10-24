# Night mode: screenshots and colour swaps

Screenshots of the test app ("Shoe Step (test)") in Dark, taken on the Pixel 9 Pro on 24 Oct 2026 with
the sample pairs, before the "Balanced" night colours (the table below has the new values; retake pending).
The phone's status bar is cropped off. They are the app as built, for comparing with
the light mockups and for drawing night frames in Figma.

## Putting them in Figma
Drag the PNGs onto the page. They are 1280 px wide (the phone's full resolution, about 427 dp). To sit
beside the 390 × 844 mockup frames, select them, lock the aspect ratio and set the width to 390.

| File | Light mockup it matches |
| --- | --- |
| N01 Home · session running | 01 Home · session running |
| N02 Home · no session | (Home with pairs, no session) |
| N04 Pair · idle | 08 Pair · idle |
| N05 Pair · running | 09 Pair · running |
| N06 Pair · paused | 10 Pair · paused |
| N07 Pair · more menu | 11 Pair · more menu |
| N08 End this session | 16 End this session |
| N09 Delete a pair | 23 Delete a pair |
| N10 Settings, N10b Settings · bottom | 21 Settings (plus the Appearance and Test build cards) |
| N11 Add shoe | 05 Add shoe |
| N12 Edit shoe | 05 Add shoe (editing) |
| N13 Discard changes | 07 Discard changes |
| N14 Switch to this pair | 17 Switch to this pair |
| N15 Privacy statement | (not mocked up) |
| N16 Why we read your activity | 22 Why we read your activity |

Not captured: the NFC tag pop-ups (15, 18–20) need a real tag tap, and 02 first run needs an empty app.
The "Test build" card at the bottom of Settings exists only in the test app.

## Tablet (`tablet/`)
The same screens on a Galaxy Tab S7+ (Android 13, landscape, 2800 × 1752, status bar cropped), taken on
24 Oct 2026 **with the "Balanced" night colours**. Health Connect is not installed on that tablet, so
steps show "(counted later)". Known large-screen issue, left for later: content stretches the full
width in landscape (cards, buttons and long lines of text); pop-ups are already a sensible width.

## Turning a light frame into a night frame
Duplicate a light frame, select it, and use **Selection colors** in the right panel to swap each light
colour for its night value below. These are the exact values the app uses (`ui/theme/Color.kt`).

| Role | Light | Night |
| --- | --- | --- |
| Backdrop, top | `#D1DDEC` | `#1A253D` |
| Backdrop, 8% | `#E3E7ED` | `#161F36` |
| Backdrop, 15% | `#E0E3E8` | `#121A2F` |
| Backdrop, bottom | `#52749E` | `#050914` |
| Card | `#DEE2E9` | `#151F34` |
| Secondary button sheen (3 stops) | `#E5EBF6` → `#C9D0DC` → `#DCE3F0` | `#2B3650` → `#20293F` → `#28324A` |
| Pressed-in (inputs, chips, switch track) | `#BDC3CE` | `#0D1322` |
| Pop-up and menu (top → bottom) | `#E5E8ED` → `#D1D6DE` | `#212B42` → `#192136` |
| Text | `#1E2230` | `#E8EDF6` |
| Muted text | `#3F4658` | `#A7B3CA` |
| Accent text (steps, links) | `#59319A` | `#C4A6FF` |
| Danger text ("Delete", "Unlink tag") | `#A02334` | `#FF8A95` |
| Danger button fill | `#A02334` | `#C2394B` |
| Card top edge highlight | white 70% | white 10% (pop-ups 12%) |
| Divider | `#1E2230` at 10% | `#C8D7FF` at 9% |
| Page dimming behind pop-ups | `#1E2230` at 42% | `#020610` at 62% |
| Shadows (cards) | `#1E2230` at 22% / 12% | black 45% / `#3D68FF` 18% (faint blue edge) |

Unchanged in both: the accent gradient `#7942DE` → `#A03CD0` → `#C92E87`, white text on it, the
purple glow (`#9A4BDB`, 30% light / 35% night), and the eight shoe colours.
