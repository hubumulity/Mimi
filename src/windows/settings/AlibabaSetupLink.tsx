import { Icon } from "../../components/Icon";
import { effectiveUiLanguage } from "../../lib/i18n";
import { openAlibabaSetupPage, type AlibabaSetupPage } from "../../lib/ipc";
import { useSettingsToast } from "./useSettingsToast";

const copy = {
  zh: { apiKey: "获取 API Key", workspace: "获取业务空间 ID", failed: "无法打开百炼设置页，请重试。" },
  "zh-TW": { apiKey: "取得 API Key", workspace: "取得業務空間 ID", failed: "無法開啟百煉設定頁，請重試。" },
  en: { apiKey: "Get API key", workspace: "Get workspace ID", failed: "Could not open the Model Studio setup page. Try again." },
  ja: { apiKey: "API キーを取得", workspace: "ワークスペース ID を取得", failed: "Model Studio 設定ページを開けませんでした。再試行してください。" },
  de: { apiKey: "API-Schlüssel abrufen", workspace: "Workspace-ID abrufen", failed: "Model Studio-Einrichtung konnte nicht geöffnet werden. Bitte erneut versuchen." },
  ko: { apiKey: "API 키 가져오기", workspace: "워크스페이스 ID 가져오기", failed: "Model Studio 설정 페이지를 열지 못했습니다. 다시 시도하세요." },
  fr: { apiKey: "Obtenir la clé API", workspace: "Obtenir l’ID d’espace", failed: "Impossible d’ouvrir la configuration Model Studio. Réessayez." },
  th: { apiKey: "รับ API Key", workspace: "รับ ID เวิร์กสเปซ", failed: "เปิดหน้าการตั้งค่า Model Studio ไม่ได้ ลองอีกครั้ง" },
};

/** Native code permits only these fixed public console destinations. */
export function AlibabaSetupLink({ page, disabled = false }: { page: AlibabaSetupPage; disabled?: boolean }) {
  const strings = copy[effectiveUiLanguage()];
  const { runWithToast } = useSettingsToast();
  return <button type="button" className="settings-link alibaba-setup-link" disabled={disabled}
    onClick={() => { void runWithToast(() => openAlibabaSetupPage(page), strings.failed); }}>
    {strings[page]}<Icon name="chevron-right" />
  </button>;
}
