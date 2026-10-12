// Actual local Timeline replay. Synthetic text only; no provider or capture.
export async function verifyCaptionReading(page) {
  await page.waitForFunction(() => window.readingReady === true);
  const observations = [];
  const failures = [];
  const cases = [
    { width: 700, height: 144, fontSize: 20, timestamps: false },
    { width: 700, height: 400, fontSize: 16, timestamps: false },
    { width: 700, height: 144, fontSize: 20, timestamps: true },
    { width: 520, height: 100, fontSize: 17, timestamps: false },
    { width: 320, height: 120, fontSize: 20, timestamps: true },
  ];
  for (const options of cases) {
    const rows = [];
    for (const id of ["before", "after", "short", "long", "continuation", "revision", "confirmed", "next", "clear"]) {
      await page.evaluate(({ id, options }) => window.readingFixture.render(id, { ...options, mode: "bilingual" }), { id, options });
      await page.waitForFunction(id => window.readingFixture.state().sceneId === id, id);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      const geometry = await page.evaluate(() => {
        const stage = document.getElementById("caption-stage");
        const stageRect = stage.getBoundingClientRect();
        const row = stage.querySelector('[data-utterance-id="next-owner"]') || stage.querySelector('[data-utterance-id="sample-owner"]');
        return { scene: window.readingFixture.state().sceneId, stageHeight: stageRect.height,
          lanes: [...(row?.querySelectorAll('[data-debug-lane]') || [])].map(lane => {
            const view = lane.getBoundingClientRect();
            const text = lane.querySelector("span");
            const node = text?.firstChild;
            let last = null;
            let first = null;
            if (node?.nodeType === Node.TEXT_NODE && node.textContent.length) {
              const range = document.createRange();
              range.setStart(node, node.textContent.length - 1); range.setEnd(node, node.textContent.length);
              const rect = range.getBoundingClientRect();
              last = { x: rect.x - stageRect.x, y: rect.y - stageRect.y, bottom: rect.bottom - stageRect.y, height: rect.height };
              range.setStart(node, 0); range.setEnd(node, 1);
              const start = range.getBoundingClientRect();
              first = { x: start.x - stageRect.x, y: start.y - stageRect.y };
            }
            return { kind: lane.dataset.debugLane, top: view.top - stageRect.top, height: view.height, last, first,
              fits: text?.getBoundingClientRect().height <= view.height + 0.5,
              characters: lane.textContent.length, accessibleCharacters: lane.getAttribute("aria-label")?.length || 0 };
          }), slots: row?.querySelectorAll('[data-subtitle-slot]').length || 0 };
      });
      rows.push(geometry);
      if (geometry.stageHeight !== options.height) failures.push({ options, id, reason: "viewport_changed" });
      for (const lane of geometry.lanes) {
        if (!lane.last || lane.last.bottom > options.height + 0.5 || lane.last.y < -0.5) failures.push({ options, id, lane: lane.kind, reason: "latest_glyph_outside_viewport" });
        if (lane.characters !== lane.accessibleCharacters) failures.push({ options, id, lane: lane.kind, reason: "full_text_not_accessible" });
      }
    }
    const sourceY = rows[0].lanes.find(lane => lane.kind === "source")?.last?.y;
    for (const row of rows.filter(row => row.scene !== "clear")) {
      const lane = row.lanes.find(lane => lane.kind === "source");
      if (row.slots !== 2) failures.push({ options, id: row.scene, reason: "missing_reserved_slot" });
      if (Math.abs(lane?.last?.y - sourceY) > 0.5) failures.push({ options, id: row.scene, reason: "source_reading_edge_moved" });
    }
    const translatedY = rows.flatMap(row => row.lanes).find(lane => lane.kind === "translation" && lane.fits)?.first?.y;
    for (const row of rows) {
      const lane = row.lanes.find(lane => lane.kind === "translation");
      if (lane?.fits && Math.abs(lane.first?.y - translatedY) > 0.5) failures.push({ options, id: row.scene, reason: "translation_start_moved_while_fitting" });
    }
    observations.push({ options, rows });
  }
  for (const id of ["short", "history-3", "history-4"]) {
    const options = { width: 600, height: id === "short" ? 660 : 255, fontSize: 16, timestamps: true, mode: "bilingual" };
    await page.evaluate(({ id, options }) => window.readingFixture.render(id, options), { id, options });
    await page.waitForFunction(id => window.readingFixture.state().sceneId === id, id);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const geometry = await page.evaluate(() => {
      const stage = document.getElementById("caption-stage").getBoundingClientRect();
      const row = document.querySelector('[data-utterance-id="sample-owner"]');
      const lanes = [...row.querySelectorAll('[data-debug-lane]')].map(lane => {
        const text = lane.querySelector("span");
        const height = lane.getBoundingClientRect().height;
        const textHeight = text.getBoundingClientRect().height;
        const lineHeight = parseFloat(getComputedStyle(text).lineHeight);
        return { kind: lane.dataset.debugLane, height, lineHeight, blankLines: Math.max(0, height - textHeight) / lineHeight };
      });
      const previous = [...document.querySelectorAll('[data-utterance-id^="prior-"]')];
      return { currentHeight: row.getBoundingClientRect().height, lanes,
        previousCount: previous.length, previousReserved: previous.filter(block => block.querySelector('[data-subtitle-slot]')).length,
        previousFullyVisible: previous.filter(block => { const rect = block.getBoundingClientRect(); return rect.top >= stage.top - 0.5 && rect.bottom <= stage.bottom + 0.5; }).length };
    });
    for (const lane of geometry.lanes) {
      if (lane.height > 3 * lane.lineHeight + 0.5 || lane.blankLines > 2.05) failures.push({ options, id, lane: lane.kind, reason: "current_slot_wastes_tall_viewport" });
    }
    if (geometry.currentHeight >= 180) failures.push({ options, id, reason: "current_pair_full_screen_card" });
    if (id.startsWith("history-") && (geometry.previousReserved !== 0 || geometry.previousFullyVisible < 1)) failures.push({ options, id, reason: "current_pair_pushes_all_history_out" });
    observations.push({ options, scene: id, geometry });
  }
  for (const mode of ["original", "translation"]) {
    await page.evaluate(mode => window.readingFixture.render("long", { mode, width: 700, height: 144, fontSize: 20, timestamps: false }), mode);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const row = await page.evaluate(() => ({ slots: document.querySelectorAll("[data-subtitle-slot]").length,
      lanes: [...document.querySelectorAll("[data-debug-lane]")].map(lane => lane.dataset.debugLane) }));
    if (row.slots !== 0 || row.lanes.length !== 1 || row.lanes[0] !== (mode === "original" ? "source" : "translation")) failures.push({ mode, reason: "single_mode_regression" });
  }
  return { scope: "Actual Timeline synthetic browser replay, no provider/capture/native acceptance", observations, failures };
}
