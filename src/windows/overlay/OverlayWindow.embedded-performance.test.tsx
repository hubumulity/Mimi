// @vitest-environment jsdom
import { act, useSyncExternalStore } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { effectiveUiLanguage, I18N, setStoredUiLanguage, subscribeUiLanguage } from "../../lib/i18n";
import type { DesktopShortcutCommands, OverlayControlMode } from "../../lib/ipc";
import { shareUnchangedSubtitleHistory } from "../../lib/sessionSnapshot";
import { useStore } from "../../lib/store";
import { subtitleDisplayShortcut } from "../../lib/subtitleDisplay";
import { TARGET_LANGUAGE_DISPLAY_NAMES } from "../../lib/types";
import { overlayControlPanelModel } from "../overlay-control/overlayControlModel";
import { capsuleLabels } from "../overlay-control/capsuleLabels";
import { overlaySessionChromeLayout } from "./overlayChromeLayout";
import { OverlayWindow } from "./OverlayWindow";

const native = vi.hoisted(() => ({
  mode: "hidden" as OverlayControlMode,
  listeners: new Set<(mode: OverlayControlMode) => void>(),
  shortcuts: vi.fn(), width: vi.fn(), height: vi.fn(), bounds: vi.fn(),
}));
vi.mock("../../lib/ipc", async original => ({
  ...await original<typeof import("../../lib/ipc")>(),
  isTauri: true,
  hasEmbeddedOverlayControls: true,
  overlayControlGetState: async () => native.mode,
  listenOverlayControlMode: async (listener: (mode: OverlayControlMode) => void) => {
    native.listeners.add(listener);
    return () => native.listeners.delete(listener);
  },
  listenOverlayPointerMotion: async () => () => {},
  appDesktopShortcutCommands: native.shortcuts,
  overlayControlSetIslandWidth: native.width,
  overlayControlSetPanelHeight: native.height,
  overlayControlSetPopupBounds: native.bounds,
}));
// Count the real control-window work; keep its panel, capsule, hooks, option
// construction and the parent OverlayWindow/Timeline implementation intact.
vi.mock("../overlay-control/overlayControlModel", async original => {
  const actual = await original<typeof import("../overlay-control/overlayControlModel")>();
  return { ...actual, overlayControlPanelModel: vi.fn(actual.overlayControlPanelModel) };
});
vi.mock("./overlayChromeLayout", async original => {
  const actual = await original<typeof import("./overlayChromeLayout")>();
  return { ...actual, overlaySessionChromeLayout: vi.fn(actual.overlaySessionChromeLayout) };
});

const original = useStore.getState();
const scrollDescriptors = ["scrollTo", "scrollIntoView"].map(key => [key, Object.getOwnPropertyDescriptor(Element.prototype, key)] as const);
let host: HTMLDivElement, root: Root;
const resizeObservers = new Map<Element, Set<() => void>>();
let islandWidth: number;

// App subscribes to language changes above the actual overlay. Keep that
// inherited render too, so a memo boundary cannot silently freeze its locale.
function OverlayRoot() {
  useSyncExternalStore(subscribeUiLanguage, effectiveUiLanguage, effectiveUiLanguage);
  return <OverlayWindow />;
}
function publishMode(mode: OverlayControlMode) {
  native.mode = mode;
  native.listeners.forEach(listener => listener(mode));
}
async function mount(mode: OverlayControlMode) {
  native.mode = mode;
  await act(async () => root.render(<OverlayRoot />));
}
function picker(label: string) {
  return host.querySelector<HTMLButtonElement>(`[role="combobox"][aria-label="${label}"]`)!;
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
  vi.stubGlobal("matchMedia", () => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
  vi.stubGlobal("ResizeObserver", class {
    targets = new Set<Element>();
    callback: () => void;
    constructor(callback: () => void) { this.callback = callback; }
    observe(target: Element) {
      this.targets.add(target);
      const callbacks = resizeObservers.get(target) ?? new Set<() => void>();
      callbacks.add(this.callback); resizeObservers.set(target, callbacks);
    }
    disconnect() {
      this.targets.forEach(target => resizeObservers.get(target)?.delete(this.callback));
    }
  });
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
    const width = this.classList.contains("overlay-control-island") ? islandWidth : 180;
    return { x: 18, y: 16, top: 16, left: 18, width, height: 24, right: 18 + width, bottom: 40 } as DOMRect;
  });
  for (const [key] of scrollDescriptors) Object.defineProperty(Element.prototype, key, { configurable: true, value: vi.fn() });
  native.mode = "hidden"; native.listeners.clear();
  native.shortcuts.mockReset().mockResolvedValue(null);
  for (const call of [native.width, native.height, native.bounds]) call.mockReset().mockResolvedValue(undefined);
  resizeObservers.clear(); islandWidth = 180;
  setStoredUiLanguage("en");
  useStore.setState({ ...original,
    settings: { ...original.settings,
      profiles: [{ id: "synthetic", name: "Synthetic Gemini", provider: "googleGeminiLive", credentialState: "present" }],
      activeProfileId: "synthetic", sourceLanguage: "en", targetLanguage: "zh", subtitleDisplayMode: "original",
      pulseStyle: "syllable", pulseAnimation: false, subtitleAnimation: false, subtitleBlendsWithBackground: false, isOverlayLocked: true,
    },
    session: { ...original.session, status: { kind: "listening" }, isActive: true, isPaused: false,
      detectedLanguage: "en", isTranslationPending: true,
      subtitles: { source: { text: "Synthetic source draft", isFinal: false, utteranceId: "synthetic-live" },
        translation: { text: "Synthetic translated draft", isFinal: false, utteranceId: "synthetic-live" },
        history: [{ source: "Synthetic confirmed source", translation: "Synthetic confirmed translation", createdAt: 1 }] },
    },
  }, true);
  host = document.createElement("div"); document.body.append(host); root = createRoot(host);
});
afterEach(async () => {
  await act(async () => root.unmount());
  expect(native.listeners.size).toBe(0);
  host.remove(); useStore.setState(original, true); setStoredUiLanguage("system");
  for (const [key, descriptor] of scrollDescriptors) {
    if (descriptor) Object.defineProperty(Element.prototype, key, descriptor);
    else Reflect.deleteProperty(Element.prototype, key);
  }
  vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers();
});

it("updates a real embedded panel when its shortcut discovery resolves without a parent or settings update", async () => {
  let resolve!: (commands: DesktopShortcutCommands | null) => void;
  native.shortcuts.mockImplementation(() => new Promise<DesktopShortcutCommands | null>(done => { resolve = done; }));
  await mount("panel");
  const control = picker(I18N.settings.subtitleDisplay).closest<HTMLElement>(".overlay-control-picker")!;
  expect(control.title).toBe("");
  vi.mocked(overlayControlPanelModel).mockClear();
  await act(async () => resolve(null));
  expect(control.title).toBe(subtitleDisplayShortcut());
  expect(overlayControlPanelModel).not.toHaveBeenCalled();
});

it.each(["hidden", "island", "panel"] as const)("does not rebuild embedded controls for 50 real parent subtitle updates in %s mode", async mode => {
  await mount(mode);
  const surface = host.querySelector(".overlay-control-embedded")!;
  const island = host.querySelector(".overlay-control-island");
  const panel = host.querySelector(".overlay-control-panel");
  const display = mode === "panel" ? picker(I18N.settings.subtitleDisplay) : null;
  if (display) await act(async () => display.click());
  const menu = document.querySelector(".mimi-select__menu");
  if (display) expect(menu).not.toBeNull();
  vi.mocked(overlayControlPanelModel).mockClear();
  vi.mocked(overlaySessionChromeLayout).mockClear();
  for (let index = 0; index < 50; index++) {
    const incoming = JSON.parse(JSON.stringify(useStore.getState().session)) as typeof original.session;
    incoming.subtitles.source.text = `Synthetic source draft ${index}`;
    incoming.subtitles.translation.text = `Synthetic translated draft ${index}`;
    incoming.apiLatencyMs = index;
    incoming.translationLatencyMs = index + 10;
    await act(async () => useStore.setState(state => ({ session: shareUnchangedSubtitleHistory(state.session, incoming) })));
  }
  // Every snapshot really renders the parent. Its existing text stabilizer
  // remains active, then publishes the latest draft without touching controls.
  expect(overlaySessionChromeLayout).toHaveBeenCalledTimes(50);
  expect(overlayControlPanelModel).toHaveBeenCalledTimes(0);
  expect(document.querySelector(".mimi-select__menu")).toBe(menu);
  if (display) await act(async () => display.click());
  await act(async () => { await vi.advanceTimersByTimeAsync(800); });
  expect(host.querySelector('[data-utterance-id="live-utterance-synthetic-live"]')?.textContent).toContain("Synthetic source draft 49");
  expect(overlayControlPanelModel).toHaveBeenCalledTimes(0);
  expect(host.querySelector(".overlay-control-embedded")).toBe(surface);
  expect(surface.getAttribute("data-mode")).toBe(mode);
  expect(host.querySelector(".overlay-control-island")).toBe(island);
  expect(host.querySelector(".overlay-control-panel")).toBe(panel);
  if (display) expect(picker(I18N.settings.subtitleDisplay)).toBe(display);
});

it("keeps locale, settings, lifecycle, native mode and resize subscriptions live behind the parent render boundary", async () => {
  await mount("panel");
  const surface = host.querySelector(".overlay-control-embedded")!;
  const island = host.querySelector<HTMLButtonElement>(".overlay-control-island")!;
  const panel = host.querySelector(".overlay-control-panel")!;
  const settings = useStore.getState().settings;
  for (const language of ["zh", "zh-TW", "ja", "ko", "fr", "de", "th", "en"] as const) {
    await act(async () => setStoredUiLanguage(language));
    expect(useStore.getState().settings).toBe(settings);
    expect(host.querySelector(".overlay-control-panel")).toBe(panel);
    expect(panel.getAttribute("aria-label")).toBe(I18N.overlay.controlPanel);
    expect(island.title).toContain(I18N.overlay.openControls);
    expect(picker(I18N.settings.subtitleDisplay)).not.toBeNull();
  }
  vi.spyOn(navigator, "language", "get").mockReturnValue("fr-CA");
  await act(async () => setStoredUiLanguage("system"));
  expect(effectiveUiLanguage()).toBe("fr");
  expect(panel.getAttribute("aria-label")).toBe(I18N.overlay.controlPanel);
  expect(island.title).toContain(I18N.overlay.openControls);
  await act(async () => setStoredUiLanguage("en"));
  await act(async () => useStore.setState(state => ({ settings: { ...state.settings, targetLanguage: "ja", pulseStyle: "ribbon" } })));
  expect(picker(I18N.settings.translateTo).textContent).toContain(TARGET_LANGUAGE_DISPLAY_NAMES.ja);
  expect(island.querySelector<HTMLElement>("[data-pulse-style]")?.dataset.pulseStyle).toBe("ribbon");
  await act(async () => useStore.setState(state => ({ session: { ...state.session, isPaused: true } })));
  expect(island.querySelector(".overlay-control-island__phase")?.textContent).toBe(I18N.overlay.paused);
  await act(async () => useStore.setState(state => ({ session: { ...state.session, isPaused: false, status: { kind: "stopping" } } })));
  expect(island.querySelector(".overlay-control-island__phase")?.textContent).toBe(capsuleLabels(useStore.getState().settings, "stopping").phase);
  expect(picker(I18N.overlay.sourceLanguage).disabled).toBe(true);
  await act(async () => publishMode("island"));
  expect(surface.getAttribute("data-mode")).toBe("island");
  expect(host.querySelector(".overlay-control-panel")).toBeNull();
  await act(async () => setStoredUiLanguage("zh"));
  expect(island.title).toContain(I18N.overlay.openControls);
  expect(island.querySelector(".overlay-control-island__phase")?.textContent).toBe(capsuleLabels(useStore.getState().settings, "stopping").phase);
  await act(async () => setStoredUiLanguage("en"));
  native.width.mockClear(); islandWidth = 230;
  await act(async () => resizeObservers.get(island)?.forEach(callback => callback()));
  expect(native.width).toHaveBeenLastCalledWith(230);
  await act(async () => publishMode("panel"));
  expect(host.querySelector(".overlay-control-panel")).not.toBeNull();
  await act(async () => publishMode("hidden"));
  expect(surface.getAttribute("data-mode")).toBe("hidden");
  expect(host.querySelector(".overlay-control-panel")).toBeNull();
  expect(native.bounds).toHaveBeenLastCalledWith(null);
});
