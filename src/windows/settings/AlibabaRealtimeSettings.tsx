import { useRef, useState } from "react";
import { effectiveUiLanguage, I18N } from "../../lib/i18n";
import type { AlibabaRealtimeMode, ServiceProfile } from "../../lib/types";
import { AlibabaSetupLink } from "./AlibabaSetupLink";
import { Icon } from "../../components/Icon";
import { ConfigInput } from "./ConfigInput";
import { SettingsHelp } from "./SettingsHelp";
import { InlineFeedback, SettingsSelect } from "./SettingsPrimitives";

const copy = {
  zh: { chainDescription: "先识别再翻译，翻译服务可更换。", liveTranslateDescription: "边听边译，同时输出原文和译文。", saved: "已保存测试模式。", save: "保存", label: "识别与翻译", chain: "现有 ASR + MT", simultaneous: "同声传译", workspace: "百炼北京业务空间 ID", help: "测试功能，仅北京业务空间；自动识别，中英翻译。停止字幕后切换。", workspaceHelp: "复制业务空间 ID；API Host 只填 llm-xxx 部分。", failed: "测试模式保存失败，请检查业务空间 ID 后重试。" },
  "zh-TW": { chainDescription: "先辨識再翻譯，翻譯服務可更換。", liveTranslateDescription: "邊聽邊譯，同時輸出原文和譯文。", saved: "已儲存測試模式。", save: "儲存", label: "辨識與翻譯", chain: "現有 ASR + MT", simultaneous: "同聲傳譯", workspace: "百煉北京業務空間 ID", help: "測試功能，僅北京業務空間；自動辨識，中英翻譯。停止字幕後切換。", workspaceHelp: "複製業務空間 ID；API Host 只填 llm-xxx 部分。", failed: "測試模式儲存失敗，請檢查業務空間 ID 後重試。" },
  en: { chainDescription: "Recognizes speech, then translates. The translation service can be changed.", liveTranslateDescription: "Translates incoming audio, returning original text and translation together.", saved: "Test mode saved.", save: "Save", label: "Recognition and translation", chain: "Current ASR + MT", simultaneous: "simultaneous", workspace: "Model Studio Beijing workspace ID", help: "Experimental: Beijing workspace, automatic recognition, Chinese/English translation. Stop subtitles before switching.", workspaceHelp: "Copy the workspace ID; for API Host, use only llm-xxx.", failed: "Could not save the test mode. Check the workspace ID and retry." },
  ja: { chainDescription: "音声認識後に翻訳。翻訳サービスは変更可能。", liveTranslateDescription: "聞きながら翻訳し、原文と訳文を同時に出力。", saved: "テストモードを保存しました。", save: "保存", label: "認識と翻訳", chain: "現在の ASR + MT", simultaneous: "同時通訳", workspace: "Model Studio 北京ワークスペース ID", help: "テスト機能。北京ワークスペース、自動認識、中英翻訳のみ。字幕を停止してから切り替え。", workspaceHelp: "ワークスペース ID をコピー。API Host は llm-xxx の部分のみ。", failed: "保存できませんでした。ワークスペース ID を確認して再試行してください。" },
  de: { chainDescription: "Erst erkennen, dann übersetzen. Der Übersetzungsdienst ist wählbar.", liveTranslateDescription: "Übersetzt Audio laufend, mit Originaltext und Übersetzung zugleich.", saved: "Testmodus gespeichert.", save: "Speichern", label: "Erkennung und Übersetzung", chain: "Aktuelle ASR + MT", simultaneous: "Simultan", workspace: "Model Studio Beijing Workspace-ID", help: "Testfunktion: Beijing, automatische Erkennung, Chinesisch/Englisch. Untertitel vor dem Wechsel stoppen.", workspaceHelp: "Workspace-ID kopieren; bei API Host nur den Teil llm-xxx verwenden.", failed: "Testmodus konnte nicht gespeichert werden. Workspace-ID prüfen und erneut versuchen." },
  ko: { chainDescription: "먼저 인식한 뒤 번역. 번역 서비스 변경 가능.", liveTranslateDescription: "들으면서 번역하고 원문과 번역문을 함께 출력.", saved: "테스트 모드를 저장했습니다.", save: "저장", label: "인식 및 번역", chain: "기존 ASR + MT", simultaneous: "동시통역", workspace: "Model Studio 베이징 워크스페이스 ID", help: "테스트 기능: 베이징, 자동 인식, 중·영 번역. 변경 전 자막을 중지하세요.", workspaceHelp: "워크스페이스 ID를 복사하세요. API Host는 llm-xxx 부분만 입력하세요.", failed: "저장하지 못했습니다. 워크스페이스 ID를 확인한 후 다시 시도하세요." },
  fr: { chainDescription: "Reconnaissance puis traduction. Service de traduction modifiable.", liveTranslateDescription: "Traduit l’audio en continu, avec original et traduction ensemble.", saved: "Mode de test enregistré.", save: "Enregistrer", label: "Reconnaissance et traduction", chain: "ASR + MT actuel", simultaneous: "simultané", workspace: "ID d’espace Model Studio Beijing", help: "Expérimental : Beijing, reconnaissance automatique, chinois/anglais. Arrêtez les sous-titres avant de changer.", workspaceHelp: "Copiez l’ID d’espace ; pour API Host, utilisez seulement llm-xxx.", failed: "Échec de l’enregistrement. Vérifiez l’ID d’espace et réessayez." },
  th: { chainDescription: "รู้จำเสียงก่อนแปล เปลี่ยนบริการแปลได้", liveTranslateDescription: "ฟังและแปล พร้อมส่งต้นฉบับและคำแปล", saved: "บันทึกโหมดทดสอบแล้ว", save: "บันทึก", label: "การรู้จำและแปล", chain: "ASR + MT ปัจจุบัน", simultaneous: "แปลพร้อมเสียง", workspace: "ID เวิร์กสเปซ Model Studio ปักกิ่ง", help: "ฟีเจอร์ทดสอบ: ปักกิ่ง รู้จำอัตโนมัติ แปลจีน/อังกฤษ หยุดคำบรรยายก่อนเปลี่ยน", workspaceHelp: "คัดลอก ID เวิร์กสเปซ หากเป็น API Host ให้ใช้เฉพาะส่วน llm-xxx", failed: "บันทึกไม่ได้ ตรวจสอบ ID เวิร์กสเปซแล้วลองอีกครั้ง" },
};

export function AlibabaRealtimeSettings({ profile, disabled, onSave }: {
  profile: ServiceProfile;
  disabled: boolean;
  onSave: (mode: AlibabaRealtimeMode, workspaceId: string, savedMessage: string) => Promise<unknown>;
}) {
  const strings = copy[effectiveUiLanguage()];
  const savedMode: AlibabaRealtimeMode = profile.alibabaRealtimeMode === "liveTranslate" ? "liveTranslate" : "disabled";
  const savedWorkspace = profile.alibabaWorkspaceId ?? "";
  const [mode, setMode] = useState<AlibabaRealtimeMode>(savedMode);
  const [workspace, setWorkspace] = useState(savedWorkspace);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const dirty = mode !== savedMode || workspace.trim() !== savedWorkspace;
  const validWorkspace = workspace.trim().length <= 63 && /^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$/.test(workspace.trim());
  const save = async () => {
    if (disabled || inFlight.current || !dirty || (mode !== "disabled" && !validWorkspace)) return;
    inFlight.current = true;
    setBusy(true); setError(false);
    try { await onSave(mode, workspace.trim(), strings.saved); }
    catch { setError(true); }
    finally { inFlight.current = false; setBusy(false); }
  };
  return <section className="service-stage alibaba-realtime-settings">
    <div className="settings-field service-stage__selector">
      <span className="service-stage__field-label alibaba-setup-field-label">{strings.label}<SettingsHelp text={strings.help} label={I18N.settings.helpLabel} /><AlibabaSetupLink page="guide" disabled={disabled || busy} /></span>
      <SettingsSelect label={strings.label} value={mode} disabled={disabled || busy}
        options={[{ value: "liveTranslate", label: `LiveTranslate (${strings.simultaneous})`, description: strings.liveTranslateDescription }, { value: "disabled", label: strings.chain, description: strings.chainDescription }]}
        onChange={value => { setMode(value as AlibabaRealtimeMode); if (value === "disabled") setWorkspace(savedWorkspace); setError(false); }} />
    </div>
    {mode !== "disabled" && <div className="settings-field">
      <span className="service-stage__field-label alibaba-setup-field-label"><label htmlFor={`profile-workspace-${profile.id}`}>{strings.workspace}</label><SettingsHelp text={strings.workspaceHelp} label={I18N.settings.helpLabel} /><AlibabaSetupLink page="workspace" disabled={disabled || busy} /></span>
      <ConfigInput id={`profile-workspace-${profile.id}`} type="text" autoComplete="off" spellCheck={false} required
        disabled={disabled || busy} value={workspace} onValueChange={value => { setWorkspace(value); setError(false); }} />
    </div>}
    {error && <InlineFeedback tone="error">{strings.failed}</InlineFeedback>}
    {dirty && <span className="credential-form__actions">
      <button type="button" className="settings-button settings-button--quiet settings-button--compact" disabled={disabled || busy}
        onClick={() => { setMode(savedMode); setWorkspace(savedWorkspace); setError(false); }}>{I18N.settings.cancel}</button>
      <button type="button" className="settings-button settings-button--primary settings-button--compact" disabled={disabled || busy || (mode !== "disabled" && !validWorkspace)}
        aria-busy={busy || undefined} onClick={() => void save()}><Icon name="checkmark" />{strings.save}</button>
    </span>}
  </section>;
}
