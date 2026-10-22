---
title: Shoe Step Tracker – Design System (Silver Matte)
status: draft v0.5 (Milestone 2 target, with night mode; style measured from the 01 Shoes export; screens from the v0.1.0 app plus M2 additions, 19 Oct 2026)
source_of_truth: Figma file "Shoe Step Tracker – Silver Matte UI" (key 0x9L0fYJHJxl7Y2p3UR1Yq)
tags: [shoe-step-tracker, design, tokens]
---

# DESIGN.md – Silver Matte

Put this file at `docs/DESIGN.md` in the repo. Claude Code must read it before touching any UI.
Reference images live in `docs/design/` (the 30 PNGs from `shoe-step-mockups-png.zip`, numbered 01–30; they replace the four earlier Figma exports). **If this file and the images disagree, the images win; tell the user.**

> Values marked **(measured)** were sampled from exported PNGs, not read from Figma, so they can be 1–2 steps off. Values marked **(confirm)** are carried over from the earlier draft and not re-checked. The Figma variables/styles in the file are still the *old* silver set; they were not updated (Figma tool limit reached). Update them from this file.

## 1. Direction

Matte silver metal over a pale-sky-to-steel-blue backdrop. Surfaces are lightly raised (soft shadow, bright top edge). Inputs and unselected controls are pressed in. One accent: a purple-to-pink gradient for primary actions and progress. **No glass, no blur, no backdrop effects.** Not Material Design: Material components may be used under the hood, but the look comes from this file. Dynamic colour is off.

**Night mode (M2):** the same matte metal in gunmetal over a deep steel-navy backdrop, with the same accent gradient and shoe colours. Settings → Appearance → Theme: Light, Dark or System (default: System, which follows the phone). Every colour below has a light and a night value; components only ever use tokens, never literal colours.

## 2. Font

- **Chakra Petch** (Google Fonts, SIL Open Font License). Replaces Inter.
- Must be **bundled** in `app/src/main/res/font/` (Regular, Medium, SemiBold, Bold). No downloadable fonts: the app has no INTERNET permission by design (SEC rule). Keep the OFL licence text in the repo (`licenses/ChakraPetch-OFL.txt`).
- Weights used in the mockups: titles and numbers Bold; button labels SemiBold; body and notes Regular **(confirm weights)**.

| Style | Size / line height | Weight | Tracking | Use |
| --- | --- | --- | --- | --- |
| Display | 42 / 46 | Bold | −1.7 px **(confirm: tuned for Inter, re-check for Chakra Petch)** | Screen titles, big numbers |
| Section | 17 / 24 | Bold | 0 | Shoe name, sheet title |
| Button | 15 / 20 | SemiBold | 0 | Button text, row values |
| Body | 14 / 21 | Regular | 0 | Subtitles, inputs |
| Label | 12 / 16 | SemiBold | +0.2 px | Small caps-style labels, chips, tabs |
| Note | 12 / 16 | Regular | 0 | Helper text. **Minimum text size is 12.** |

Text must scale with the system font size (use `sp`). Test at 200%.

## 3. Colour

### Surfaces and gradients
| Token | Value | Notes |
| --- | --- | --- |
| `bg` | vertical gradient: 0% `#D1DDEC` → 8% `#E3E7ED` → 15% `#E0E3E8` → 100% `#52749E` | (measured) the screen backdrop |
| `card` | `#DEE2E9` | (measured) flat; look comes from shadow + edge |
| `card-register` | `#DADEE4` | (measured) form card, slightly darker |
| `sheet` | vertical `#E5E8ED` → `#CCD1DA` | (measured) bottom sheet, radius 32 |
| `button-secondary` | vertical `#E4E7ED` → `#CBD0D9` | (measured) |
| `tabbar` | horizontal sheen: edges `#E5EBF6` / `#DCE3F0`, centre `#BDC4D0` | (measured) floating bar |
| `inset` | `#BDC3CE` | inputs, unselected chips (pressed look) |
| `scrim` | `#1E2230` at 35% | behind the sheet |
| `edge-highlight` | white, ~70% | 1 px stroke on raised items |

### Text and states
| Token | Value |
| --- | --- |
| `text-primary` | `#1E2230` |
| `text-muted` | `#3F4658` |
| `text-on-accent` | `#FFFFFF` |
| `accent-ink` | `#59319A` (links, values, "Provisional") |
| `danger` | **`#A02334`** (was `#B72E40`; that failed contrast, see section 8) |

### Accent gradient (primary buttons, Add shoe, Running badge, selected chips)
Horizontal: `#7942DE` → `#A03CD0` → `#C92E87`. Glow under primary buttons: 0 / 7 / blur 20, `#9A4BDB` at 30%.

### Night palette (M2)
| Token | Light | Night |
| --- | --- | --- |
| `bg` | see above | vertical: 0% `#2A3345` → 8% `#222A38` → 15% `#1C2330` → 100% `#0B1018` |
| `card` | `#DEE2E9` | `#262D3A` |
| `sheen` (secondary buttons, icon buttons) | `#E5EBF6` → `#C9D0DC` → `#DCE3F0` | `#3B4456` → `#2C3341` → `#373F50` |
| `inset` | `#BDC3CE` | `#1A202B` |
| `dialog` | `#E5E8ED` → `#D1D6DE` | `#323A49` → `#272E3B` |
| `edge-highlight` | white 70% | white 8% |
| `divider` | `#1E2230` at 10% | white 8% |
| `scrim` | `#1E2230` at 42% | black 60% |
| `text-primary` | `#1E2230` | `#E8ECF4` |
| `text-muted` | `#3F4658` | `#A9B2C4` |
| `accent-ink` | `#59319A` | `#C4A6FF` |
| `danger` (text) | `#A02334` | `#FF8A95` |
| `danger-fill` (Delete button) | `#A02334` | `#C2394B` |
| Accent gradient, `text-on-accent`, shoe colours | unchanged | unchanged |

Night depth: Raised = drop 0/6/blur 16 black 45% + 0/1/blur 2 black 40% + inner top highlight white 8%. Floating = drop 0/16/blur 36 black 55% + inner highlight white 10%. Pressed = inner 0/2/blur 5 black 55% + inner 0/−1/blur 1 white 6%. Accent glow 35%.

### Shoe colours (user-assigned per pair)
Red `#FF6B6B`, Orange `#FF9F43`, Amber `#F7C548`, Green `#67B88D`, Teal `#2EC4B6`, Blue `#46A9E8`, Indigo `#5A67D8`, Pink `#EC4899`. Markers are **24 px circles**. Never rely on colour alone: the shoe name is always shown.

## 4. Depth (no blur)

| Name | Recipe | Use |
| --- | --- | --- |
| Raised | drop 0/6/blur 16 `#1E2230` 22% + drop 0/1/blur 2 12% + 1 px inner top highlight white 75% | cards, secondary buttons |
| Floating | drop 0/16/blur 36 30% + drop 0/2/blur 4 14% + inner top highlight white 80% | pop-ups, overflow menu |
| Pressed | inner 0/2/blur 5 `#1E2230` 25% + inner 0/−1/blur 1 white 50% | inputs, unselected chips, Paused badge, switch track |
| Accent glow | drop 0/7/blur 20 `#9A4BDB` 30% | primary buttons |

Compose: `Modifier.shadow(elevation, shape, ambientColor, spotColor)` for drop shadows (coloured shadows need API 28+, our minSdk), `drawWithContent` / `drawBehind` for the inner highlight and the pressed inset. Keep to **one raised surface per list row** and avoid nested shadows in long lists (measure scroll jank on a mid-range phone).

## 5. Shape and spacing

Panel radius 26 (sheet 32). Pills 100. Inputs 16. Screen padding 20; top inset 64 in the mockup frame (use real window insets in code). Gaps 4 / 8 / 16 / 24 / 32. **Touch targets ≥ 48 dp.** Mockup frame is 390 × 844.

## 6. Components

- **Primary button:** accent gradient, white SemiBold 15, pill, min height 48, glow.
- **Secondary button:** raised, `text-primary`.
- **Destructive text button:** `danger` text, no fill (for example "Unlink tag"), still 48 dp tall. Permanent deletes use a filled `danger` button ("Delete").
- **Chip:** selected = accent gradient + white label; unselected = pressed on `inset`.
- **Input:** pressed on `inset`, radius 16, min height 48, label above in Label style.
- **Shoe row card:** raised card, 24 px colour circle, name (Section), type (Note), total steps right-aligned (Button style, `accent-ink`), status badge above the steps when Running or Paused.
- **Icon button (top bar):** 48 dp circle with the tab-bar sheen and Raised shadow (Settings gear, Back, Close, More).
- **Add shoe button:** gradient pill with a plus icon, bottom right of Home, Accent glow.
- **Status badge:** small pill on a Home row. Running = accent gradient, Paused = pressed on `inset`.
- **Current session card:** label, colour dot + "Pair · running since 07:42" or "· paused", "About N so far", then Pause + End session (or Resume + End session).
- **Stats grid:** two columns: Total steps, Total distance, Sessions, Walking time, Average per session (steps, with distance underneath), Last worn, Cost per 1,000 steps, Cost per km (or per mile, following the unit setting).
- **History row:** "date, time · duration" on the left, steps on the right in `accent-ink`. States: In progress, About N (provisional for one day), N steps, "— (counted later)", "Ended by switching pairs". Pages of 50 with "Show N older sessions".
- **Dialog (all pop-ups):** centred, radius 28, Floating shadow, sheet gradient `#E5E8ED → #D1D6DE`, scrim `#1E2230` at 42%. Two choices sit side by side (text button + primary); three or more stack full width.
- **Overflow menu:** Floating panel under the More button: Edit, Archive, Delete (`danger`).
- **Colour swatches:** eight 30 px circles in one row; the selected one gets a white tick and ring, so colour is not the only cue.
- **NFC rings (Link a tag pop-up):** three concentric outlines in accent purple at 25/45/70% opacity around a raised 60 px "NFC" disc. "Lock the tag" checkbox with the permanent-lock warning on a light `danger` tint.
- **Switch:** "Show archived" and "Session notification" toggles. Off: pressed track, raised knob. On: accent-gradient track.
- **Distance line (M2):** under total steps on Home rows (Note size, `text-muted`, semibold); "About 2.4 km" under the live steps on session cards (`accent-ink`); second line under steps in History rows. "–" when there is no distance, never "0".
- **Segmented control (M2):** Settings → Units → Distance: Kilometres / Miles. Pressed track; selected segment uses the accent gradient. Each segment ≥ 48 dp tall including the track.
- **Permission cards (M2):** "Show distance" card on Home (Not now / Allow) when distance isn't allowed; "Show sessions in notifications?" dialog before the Android 13+ system prompt.
- **Theme switch (M2):** Settings → Appearance → Theme, a three-way segmented control: Light / Dark / System. Applies at once, no restart.
- **Add past session (M2):** "+ Add past session" text button in the History card header. Full-screen form: the pair (fixed), Date (picker, default today), Start and End times, then a preview card ("About 6,120 steps · 4.6 km", read from Health Connect for that time) on a light accent tint. Errors appear under the times (`danger`) and block Save: end before start, in the future, overlapping another session (name the pair and its times), older than Health Connect allows. Added sessions show "Added later" in History.
- **Session notification (M2):** drawn by Android, not by the app. The app sets: monochrome walking icon, accent colour `#7942DE`, title "Pair · running" or "· paused", running timer (paused shows "Paused"), body text, buttons Pause/Resume and End session. Private on the lock screen with a public version reading only "Session running".

There is **no tab bar** and **no step ring** in the app. They were in earlier Figma frames and have been removed.

## 7. Screens (Milestone 2 target, all mocked up)

Mockups: the "Shoe Step Mockups" artifact, with 30 frames and the PNGs in `docs/design/`. Existing text matches `strings.xml`; text on M2 frames is a draft. Frames are tagged "Changed in M2" or "New in M2".

| Area | Screens and pop-ups |
| --- | --- |
| Home | 01 session running · 02 first run · 03 distance not allowed yet (new) · 04 unknown tag tapped (New tag) |
| Add / edit shoe | 05 form (name, brand, model, 8 types, price, currency, purchase date, colour) · 06 Can't save yet · 07 Discard changes? (new) |
| A pair's page | 08 idle · 09 running · 10 paused · 11 more menu · 12 Add past session (new) · 13 Add past session, overlap error (new) |
| Session notification (new) | 14 ask before notifications · 15 notification running · 16 notification paused |
| Session pop-ups | 17 tag tapped while running (Pause / End session / Cancel) · 18 End this session? · 19 Switch to this pair? |
| NFC tag pop-ups | 20 Link a tag · 21 Tag linked · 22 Turn on NFC |
| Settings and privacy | 23 Settings (adds Appearance, Units and Session notification) · 24 Why we read your activity · 25 Delete a pair? |
| Night mode (new) | 26 Home · 27 pair page · 28 tag-tap pop-up · 29 Add past session · 30 Settings. Every other screen uses the same tokens. |

**Layout rules confirmed by the product owner (19 Oct):**
- Home's Current session card has Pause and End session buttons.
- Home rows show the colour dot, name, type and total steps, plus a status badge.
- On a pair's page: header (dot, name, type · brand model, price · purchase date), then **Stats**, then the **session controls** (Start session, or the running/paused card), then **NFC tag**, then **History**.
- Settings is a gear at the top of Home; "Add shoe" is the gradient button at the bottom right.
- **Cancel on the tag-tap pop-up only closes it.** It never discards a session.

**Not mocked up yet:** privacy statement, open-source licences, currency picker, date picker, Unlink tag and Archive confirmations, tag error messages, archived-pair note, Health Connect "not installed" and "unavailable" cards, archived list on Home.

## 8. Accessibility checks (WCAG 2.1 AA, computed from the measured values)

| Pair | Ratio | Result |
| --- | --- | --- |
| text-primary on card | 12.2 | pass |
| text-muted on card | 7.3 | pass |
| text-muted on `bg` near top | 6.9 | pass |
| text-muted on `bg` at ~50% height | 4.1 | **fail for 12 px text** |
| text-primary on `bg` at ~50% height | 7.9 | pass |
| accent-ink on card | 6.9 | pass |
| white on accent gradient (lightest/darkest stops) | 5.0–5.8 | pass |
| old danger `#B72E40` on sheet | 4.1 | **fail** → use `#A02334` (≈5.2) |
| text-muted on inset | 5.3 | pass |
| white on `bg` bottom `#52749E` | 4.8 | pass (but no text should sit there) |
| Night: text-primary on card | 11.7 | pass |
| Night: text-muted on card | 6.5 | pass |
| Night: accent-ink on card | 6.8 | pass |
| Night: danger on card / on dialog | 6.1 / 5.1 | pass |
| Night: text-muted on inset | 7.7 | pass |
| Night: white on danger-fill | 5.3 | pass |
| Night: text-muted on `bg` top / bottom | 5.9 / 8.9 | pass |

Rules that follow: put text on cards or sheets, not directly on the lower half of the backdrop; where text must sit on the backdrop (headings and the pair header near the top), keep it in the top third. Meaning is never carried by colour alone. Support 200% font scale and TalkBack (content descriptions on icon buttons, badges and colour circles). Add a "reduce shadows/effects" switch only if profiling shows jank.

## 9. Open items

- Confirm Chakra Petch weights and the Display tracking.
- Update the Figma variables/styles to this file (Figma reads were rate-limited on the Starter plan this month).
- Night mode: designed for the five key screens; check the rest on the phone in both themes.
- Copy on the M2 screens (permission cards, notification, Add past session) is a draft; confirm before Phase 6.
