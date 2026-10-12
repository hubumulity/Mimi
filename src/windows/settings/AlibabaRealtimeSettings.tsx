import { useRef, useState } from "react";
import { effectiveUiLanguage, I18N } from "../../lib/i18n";
import type { AlibabaRealtimeMode, ServiceProfile } from "../../lib/types";
import { Icon } from "../../components/Icon";
import { ConfigInput } from "./ConfigInput";
import { SettingsHelp } from "./SettingsHelp";
import { InlineFeedback, SettingsSelect } from "./SettingsPrimitives";

const copy = {
  zh: { saved: "已保存测试模式。", save: "保存", label: "识别与翻译", chain: "现有 ASR + MT", test: "测试", workspace: "百炼北京业务空间 ID", help: "测试模式将音频发给单个实时模型，跳过独立文本翻译。当前仅提供北京业务空间、自动识别和中英目标语言；原文模式只显示识别结果。Omni 按翻译指令生成文本，可能回答或遗漏语音内容。停止字幕后可切换。", workspaceHelp: "填写已获授权使用对应实时模型的北京业务空间 ID。不会创建业务空间或更改 API Key。", failed: "测试模式保存失败，请检查业务空间 ID 后重试。" },
  "zh-TW": { saved: "已儲存測試模式。", save: "儲存", label: "辨識與翻譯", chain: "現有 ASR + MT", test: "測試", workspace: "百煉北京業務空間 ID", help: "測試模式將音訊傳給單一即時模型，略過獨立文字翻譯。目前僅提供北京業務空間、自動辨識及中英目標語言；原文模式只顯示辨識結果。Omni 依翻譯指令產生文字，可能回答或遺漏語音內容。停止字幕後可切換。", workspaceHelp: "填寫已獲授權使用對應即時模型的北京業務空間 ID。不會建立業務空間或變更 API Key。", failed: "測試模式儲存失敗，請檢查業務空間 ID 後重試。" },
  en: { saved: "Test mode saved.", save: "Save", label: "Recognition and translation", chain: "Current ASR + MT", test: "test", workspace: "Model Studio Beijing workspace ID", help: "Test modes send audio to one realtime model and skip separate text translation. This experiment supports Beijing workspaces, automatic recognition, and Chinese or English targets. Original displays recognition only. Omni follows a translation instruction and may answer or omit speech. Stop subtitles before switching.", workspaceHelp: "Enter a Beijing workspace ID already authorized for the selected model. This does not create a workspace or change the API key.", failed: "Could not save the test mode. Check the workspace ID and retry." },
  ja: { saved: "テストモードを保存しました。", save: "保存", label: "認識と翻訳", chain: "現在の ASR + MT", test: "テスト", workspace: "Model Studio 北京ワークスペース ID", help: "音声を単一のリアルタイムモデルへ送り、独立したテキスト翻訳を省きます。北京ワークスペース、自動認識、中国語・英語への翻訳のみ対応。原文は認識結果のみ表示。Omni が音声に回答したり省略したりする可能性があります。切り替える前に字幕を停止してください。", workspaceHelp: "選択したモデルの利用権限がある北京ワークスペース ID を入力します。ワークスペース作成や API キー変更は行いません。", failed: "保存できませんでした。ワークスペース ID を確認して再試行してください。" },
  de: { saved: "Testmodus gespeichert.", save: "Speichern", label: "Erkennung und Übersetzung", chain: "Aktuelle ASR + MT", test: "Test", workspace: "Model Studio Beijing Workspace-ID", help: "Testmodi senden Audio an ein Echtzeitmodell und überspringen die separate Textübersetzung. Nur Beijing, automatische Erkennung und Chinesisch/Englisch als Ziel sind verfügbar. Original zeigt nur die Erkennung. Omni kann auf Sprache antworten oder Inhalte auslassen. Untertitel vor dem Wechsel stoppen.", workspaceHelp: "Beijing Workspace-ID mit bestehender Berechtigung für das Modell eingeben. Es werden weder Workspace noch API-Schlüssel erstellt.", failed: "Testmodus konnte nicht gespeichert werden. Workspace-ID prüfen und erneut versuchen." },
  ko: { saved: "테스트 모드를 저장했습니다.", save: "저장", label: "인식 및 번역", chain: "기존 ASR + MT", test: "테스트", workspace: "Model Studio 베이징 워크스페이스 ID", help: "테스트 모드는 음성을 하나의 실시간 모델로 전송하고 별도의 텍스트 번역을 건너뜁니다. 베이징, 자동 인식, 중국어·영어 대상만 지원합니다. 원문은 인식 결과만 표시합니다. Omni는 음성에 답하거나 내용을 생략할 수 있습니다. 변경 전에 자막을 중지하세요.", workspaceHelp: "모델 사용 권한이 있는 베이징 워크스페이스 ID를 입력하세요. 워크스페이스 생성이나 API 키 변경은 하지 않습니다.", failed: "저장하지 못했습니다. 워크스페이스 ID를 확인한 후 다시 시도하세요." },
  fr: { saved: "Mode de test enregistré.", save: "Enregistrer", label: "Reconnaissance et traduction", chain: "ASR + MT actuel", test: "test", workspace: "ID d’espace Model Studio Beijing", help: "Ces modes envoient l’audio à un modèle temps réel et ignorent la traduction de texte séparée. Beijing, reconnaissance automatique et cibles chinois/anglais uniquement. Original affiche la reconnaissance seule. Omni peut répondre à la parole ou omettre du contenu. Arrêtez les sous-titres avant de changer.", workspaceHelp: "Saisissez l’ID d’un espace Beijing déjà autorisé pour le modèle. Aucun espace ni clé API n’est créé.", failed: "Échec de l’enregistrement. Vérifiez l’ID d’espace et réessayez." },
  th: { saved: "บันทึกโหมดทดสอบแล้ว", save: "บันทึก", label: "การรู้จำและแปล", chain: "ASR + MT ปัจจุบัน", test: "ทดสอบ", workspace: "ID เวิร์กสเปซ Model Studio ปักกิ่ง", help: "โหมดทดสอบส่งเสียงไปยังโมเดลเรียลไทม์เดียวโดยข้ามการแปลข้อความแยก รองรับเฉพาะปักกิ่ง การรู้จำอัตโนมัติ และภาษาปลายทางจีนหรืออังกฤษ โหมดต้นฉบับแสดงผลรู้จำเท่านั้น Omni อาจตอบคำพูดหรือละเนื้อหา หยุดคำบรรยายก่อนเปลี่ยน", workspaceHelp: "กรอก ID เวิร์กสเปซปักกิ่งที่มีสิทธิ์ใช้โมเดลนี้อยู่แล้ว ระบบจะไม่สร้างเวิร์กสเปซหรือเปลี่ยน API Key", failed: "บันทึกไม่ได้ ตรวจสอบ ID เวิร์กสเปซแล้วลองอีกครั้ง" },
};

export function AlibabaRealtimeSettings({ profile, disabled, onSave }: {
  profile: ServiceProfile;
  disabled: boolean;
  onSave: (mode: AlibabaRealtimeMode, workspaceId: string, savedMessage: string) => Promise<unknown>;
}) {
  const strings = copy[effectiveUiLanguage()];
  const savedMode = profile.alibabaRealtimeMode ?? "disabled";
  const savedWorkspace = profile.alibabaWorkspaceId ?? "";
  const [mode, setMode] = useState(savedMode);
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
      <span className="service-stage__name-help">{strings.label}<SettingsHelp text={strings.help} label={I18N.settings.helpLabel} /></span>
      <SettingsSelect label={strings.label} value={mode} disabled={disabled || busy}
        options={[{ value: "disabled", label: strings.chain }, { value: "liveTranslate", label: `Qwen LiveTranslate (${strings.test})` }, { value: "omni", label: `Qwen Omni (${strings.test})` }]}
        onChange={value => { setMode(value as AlibabaRealtimeMode); if (value === "disabled") setWorkspace(savedWorkspace); setError(false); }} />
    </div>
    {mode !== "disabled" && <label className="settings-field" htmlFor={`profile-workspace-${profile.id}`}>
      <span className="service-stage__name-help">{strings.workspace}<SettingsHelp text={strings.workspaceHelp} label={I18N.settings.helpLabel} /></span>
      <ConfigInput id={`profile-workspace-${profile.id}`} type="text" autoComplete="off" spellCheck={false} required
        disabled={disabled || busy} value={workspace} onValueChange={value => { setWorkspace(value); setError(false); }} />
    </label>}
    {error && <InlineFeedback tone="error">{strings.failed}</InlineFeedback>}
    {dirty && <span className="credential-form__actions">
      <button type="button" className="settings-button settings-button--quiet settings-button--compact" disabled={disabled || busy}
        onClick={() => { setMode(savedMode); setWorkspace(savedWorkspace); setError(false); }}>{I18N.settings.cancel}</button>
      <button type="button" className="settings-button settings-button--primary settings-button--compact" disabled={disabled || busy || (mode !== "disabled" && !validWorkspace)}
        aria-busy={busy || undefined} onClick={() => void save()}><Icon name="checkmark" />{strings.save}</button>
    </span>}
  </section>;
}
