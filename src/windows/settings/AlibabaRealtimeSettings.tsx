import { useRef, useState } from "react";
import { effectiveUiLanguage, I18N } from "../../lib/i18n";
import type { AlibabaRealtimeMode, ServiceProfile } from "../../lib/types";
import { AlibabaSetupLink } from "./AlibabaSetupLink";
import { Icon } from "../../components/Icon";
import { ConfigInput } from "./ConfigInput";
import { SettingsHelp } from "./SettingsHelp";
import { InlineFeedback, SettingsSelect } from "./SettingsPrimitives";

const copy = {
  zh: { chainDescription: "先识别再翻译，可单独更换文字翻译服务。", liveTranslateDescription: "同一模型边听边译，同时输出原文和译文。", saved: "已保存测试模式。", save: "保存", label: "识别与翻译", chain: "现有 ASR + MT", simultaneous: "同声传译", workspace: "百炼北京业务空间 ID", help: "同声传译仍为测试功能：音频发送到单个实时模型，跳过独立文本翻译。当前仅提供北京业务空间、自动识别和中英目标语言；原文模式只显示识别结果。停止字幕后可切换。", workspaceHelp: "填写已获授权使用对应实时模型的北京业务空间 ID。不会创建业务空间或更改 API Key。", failed: "测试模式保存失败，请检查业务空间 ID 后重试。" },
  "zh-TW": { chainDescription: "先辨識再翻譯，可單獨更換文字翻譯服務。", liveTranslateDescription: "同一模型邊聽邊譯，同時輸出原文和譯文。", saved: "已儲存測試模式。", save: "儲存", label: "辨識與翻譯", chain: "現有 ASR + MT", simultaneous: "同聲傳譯", workspace: "百煉北京業務空間 ID", help: "同聲傳譯仍為測試功能：音訊傳給單一即時模型，略過獨立文字翻譯。目前僅提供北京業務空間、自動辨識及中英目標語言；原文模式只顯示辨識結果。停止字幕後可切換。", workspaceHelp: "填寫已獲授權使用對應即時模型的北京業務空間 ID。不會建立業務空間或變更 API Key。", failed: "測試模式儲存失敗，請檢查業務空間 ID 後重試。" },
  en: { chainDescription: "Recognizes speech, then translates it. The text translation service can be changed separately.", liveTranslateDescription: "One model translates incoming audio, returning original text and translation.", saved: "Test mode saved.", save: "Save", label: "Recognition and translation", chain: "Current ASR + MT", simultaneous: "simultaneous", workspace: "Model Studio Beijing workspace ID", help: "LiveTranslate is experimental. It sends audio to one realtime model and skips separate text translation. This experiment supports Beijing workspaces, automatic recognition, and Chinese or English targets. Original displays recognition only. Stop subtitles before switching.", workspaceHelp: "Enter a Beijing workspace ID already authorized for the selected model. This does not create a workspace or change the API key.", failed: "Could not save the test mode. Check the workspace ID and retry." },
  ja: { chainDescription: "音声認識後に翻訳。テキスト翻訳サービスは個別に変更できます。", liveTranslateDescription: "同じモデルが音声を聞きながら翻訳し、原文と訳文を同時に出力します。", saved: "テストモードを保存しました。", save: "保存", label: "認識と翻訳", chain: "現在の ASR + MT", simultaneous: "同時通訳", workspace: "Model Studio 北京ワークスペース ID", help: "同時通訳はテスト機能です。音声を単一のリアルタイムモデルへ送り、独立したテキスト翻訳を省きます。北京ワークスペース、自動認識、中国語・英語への翻訳のみ対応。原文は認識結果のみ表示。切り替える前に字幕を停止してください。", workspaceHelp: "選択したモデルの利用権限がある北京ワークスペース ID を入力します。ワークスペース作成や API キー変更は行いません。", failed: "保存できませんでした。ワークスペース ID を確認して再試行してください。" },
  de: { chainDescription: "Erst Spracherkennung, dann Übersetzung. Der Textübersetzungsdienst ist separat wählbar.", liveTranslateDescription: "Ein Modell hört zu und übersetzt, mit Originaltext und Übersetzung zugleich.", saved: "Testmodus gespeichert.", save: "Speichern", label: "Erkennung und Übersetzung", chain: "Aktuelle ASR + MT", simultaneous: "Simultan", workspace: "Model Studio Beijing Workspace-ID", help: "LiveTranslate ist eine Testfunktion. Audio wird an ein Echtzeitmodell gesendet, ohne separate Textübersetzung. Nur Beijing, automatische Erkennung und Chinesisch/Englisch als Ziel sind verfügbar. Original zeigt nur die Erkennung. Untertitel vor dem Wechsel stoppen.", workspaceHelp: "Beijing Workspace-ID mit bestehender Berechtigung für das Modell eingeben. Es werden weder Workspace noch API-Schlüssel erstellt.", failed: "Testmodus konnte nicht gespeichert werden. Workspace-ID prüfen und erneut versuchen." },
  ko: { chainDescription: "먼저 음성을 인식한 다음 번역합니다. 텍스트 번역 서비스를 따로 변경할 수 있습니다.", liveTranslateDescription: "하나의 모델이 음성을 듣고 번역하며 원문과 번역문을 함께 출력합니다.", saved: "테스트 모드를 저장했습니다.", save: "저장", label: "인식 및 번역", chain: "기존 ASR + MT", simultaneous: "동시통역", workspace: "Model Studio 베이징 워크스페이스 ID", help: "LiveTranslate는 테스트 기능입니다. 음성을 하나의 실시간 모델로 전송하고 별도의 텍스트 번역을 건너뜁니다. 베이징, 자동 인식, 중국어·영어 대상만 지원합니다. 원문은 인식 결과만 표시합니다. 변경 전에 자막을 중지하세요.", workspaceHelp: "모델 사용 권한이 있는 베이징 워크스페이스 ID를 입력하세요. 워크스페이스 생성이나 API 키 변경은 하지 않습니다.", failed: "저장하지 못했습니다. 워크스페이스 ID를 확인한 후 다시 시도하세요." },
  fr: { chainDescription: "Reconnaissance puis traduction. Le service de traduction de texte peut être changé séparément.", liveTranslateDescription: "Un même modèle traduit pendant l’écoute et produit ensemble l’original et sa traduction.", saved: "Mode de test enregistré.", save: "Enregistrer", label: "Reconnaissance et traduction", chain: "ASR + MT actuel", simultaneous: "simultané", workspace: "ID d’espace Model Studio Beijing", help: "LiveTranslate est expérimental. L’audio est envoyé à un modèle temps réel sans traduction de texte séparée. Beijing, reconnaissance automatique et cibles chinois/anglais uniquement. Original affiche la reconnaissance seule. Arrêtez les sous-titres avant de changer.", workspaceHelp: "Saisissez l’ID d’un espace Beijing déjà autorisé pour le modèle. Aucun espace ni clé API n’est créé.", failed: "Échec de l’enregistrement. Vérifiez l’ID d’espace et réessayez." },
  th: { chainDescription: "รู้จำเสียงก่อนแปล เปลี่ยนบริการแปลข้อความแยกได้", liveTranslateDescription: "โมเดลเดียวฟังและแปล พร้อมส่งต้นฉบับและคำแปล", saved: "บันทึกโหมดทดสอบแล้ว", save: "บันทึก", label: "การรู้จำและแปล", chain: "ASR + MT ปัจจุบัน", simultaneous: "แปลพร้อมเสียง", workspace: "ID เวิร์กสเปซ Model Studio ปักกิ่ง", help: "LiveTranslate เป็นฟีเจอร์ทดสอบที่ส่งเสียงไปยังโมเดลเรียลไทม์เดียวโดยข้ามการแปลข้อความแยก รองรับเฉพาะปักกิ่ง การรู้จำอัตโนมัติ และภาษาปลายทางจีนหรืออังกฤษ โหมดต้นฉบับแสดงผลรู้จำเท่านั้น หยุดคำบรรยายก่อนเปลี่ยน", workspaceHelp: "กรอก ID เวิร์กสเปซปักกิ่งที่มีสิทธิ์ใช้โมเดลนี้อยู่แล้ว ระบบจะไม่สร้างเวิร์กสเปซหรือเปลี่ยน API Key", failed: "บันทึกไม่ได้ ตรวจสอบ ID เวิร์กสเปซแล้วลองอีกครั้ง" },
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
      <span className="service-stage__name-help">{strings.label}<SettingsHelp text={strings.help} label={I18N.settings.helpLabel} /></span>
      <SettingsSelect label={strings.label} value={mode} disabled={disabled || busy}
        options={[{ value: "disabled", label: strings.chain, description: strings.chainDescription }, { value: "liveTranslate", label: `LiveTranslate (${strings.simultaneous})`, description: strings.liveTranslateDescription }]}
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
