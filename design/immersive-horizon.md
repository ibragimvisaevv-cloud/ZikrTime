# ZikrTime 0.8.9 — immersive Living Horizon

One full-window portrait scene, a real next-prayer clock, and one native vertical scroll owner. The warm foreground sheet rises over the scene. Parallax runs in the graphics layer, dimming in draw, and the compact header crossfades after the large hero disappears. No bitmap allocation, blur, second draggable sheet, or continuous decorative animation. Android animation scale zero disables parallax; native scroll and immediate feedback remain.

Home retains city-zone schedule, countdown, marks and widget updates. City and manual theme controls remain accessible. Timetable uses open rows, current label, next label with bronze rail, and checkbox semantics. Light/dark surfaces, navigation, reading, catalogue and More use the shared palette. One partner fills the width; large text switches product layout to one column.

## Generated assets
Built-in imagegen, separate generation/edit calls. Real interface text is never baked into artwork. Original images 941×1672, converted without resize to offline WebP quality86.
- `app/src/main/res/drawable-nodpi/horizon_portrait_day.webp` — 238392 bytes.
- `app/src/main/res/drawable-nodpi/horizon_portrait_night.webp` — 165622 bytes.
Other separate scenes from0.8.8 remain in their appropriate destinations.

Day prompt: Photorealistic vertical Islamic architectural landscape for a full-screen Android background. Pale warm limestone mosque with central dome and two minarets in lower-right middle, central75% safe width, minaret tips near40%, dome53%, base65%. Upper40% calm blue-teal sky for real white prayer typography. Morning sun fromright, layered sage/emerald mountains and valley, warm stone terrace below. Natural depth and believable Islamic architecture. No jungle, people, text, UI, frame, fantasy glow or collage.

Night edit prompt: Keep exact day image camera, geometry, mosque, terrace, mountains and lake. Change only exposure/lighting to indigo/teal night, sparse stars, small natural crescent upperright, blue moonlight on limestone and restrained warm arch lights. Calm upperleft text area. No added buildings, text, UI or neon.

## Verification
`tools/ci/horizon_interaction.py` exercises real Android touch input starting on the scene and continuing on the sheet; verifies rise, compact state and return in both themes. Captures real scrolling MP4, image-disabled composition,320dp screen at200% text, Azerbaijani/Turkish long labels, and reduced-motion scroll. Existing upgrade/theme/learning/progress checks remain in the workflow. Screenshots use synthetic emulator settings and real prayer calculations, not hardcoded times. Emulator performance is not a physical-device benchmark.
