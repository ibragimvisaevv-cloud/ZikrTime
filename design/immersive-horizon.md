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

## Exact built-in image prompts

### Day — generate
Use case: photorealistic-natural. Asset type: production full-screen portrait background for the Islamic Android app ZikrTime, not an interface mockup. Create one coherent vertical 9:16 scene, ideally 1080x1920. A beautiful pale warm limestone mosque with a refined central dome and two slender minarets stands in the middle distance on a quiet stone terrace above a layered mountain valley. Mosque placed in the lower-right middle of the image: dome centered near x65%, y53%, minaret tips near y40%, mosque base near y65%. All architecture stays inside central 75% width for narrower screen crops. Upper 40% is expansive soft blue-teal sky with delicate high clouds, no bright sun disk; upper-left has calm subtly darker blue negative space for large white prayer-time typography later. Warm morning sunlight falls from right onto carved limestone and dome; natural sky blue, sage and muted emerald hills, warm sand, small restrained brass architectural accents. Foreground from bottom 30% is a quiet stone terrace and atmospheric depth, no prominent objects, because a real scrolling interface panel will cover it. Sophisticated believable architectural photography, serene fine atmospheric perspective, luminous natural colors, tactile materials, realistic Islamic architecture. No jungle, no lush tropical vegetation, no people, no text, no lettering, no UI, no frame, no buttons, no artificial fantasy glow, no collage. Background must extend to all edges. The mosque must remain visibly separated from the upper-left information area.

### Night — lighting edit of the day image
Use case: lighting-weather. Edit this exact production portrait background into its NIGHT exposure for ZikrTime. Keep identical camera, aspect ratio, architecture geometry and positioning, dome and both minarets, terrace, mountains, lake and every major compositional element. Change only time of day and illumination: deep indigo and teal night sky with a sparse delicate star field, tiny natural crescent in upper right, faint blue moonlight on the limestone, restrained warm lights inside mosque arches and a few distant valley lights. Foreground dark but with readable stone texture. Upper left must be a calm dark area for real white text later. Serene realistic architectural night photography, no neon, no fantasy glow, no additional buildings. Absolutely no text, UI, logos or border.
