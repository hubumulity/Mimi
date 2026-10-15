import { effectiveUiLanguage } from "../../lib/i18n";

const copy = {
  zh: { voice: "声音与语言", models: "模型与服务", selection: "使用的配置", voiceHelp: "选择声音来源，以及识别和翻译语言。", modelsHelp: "管理模型、服务连接和凭证。" },
  "zh-TW": { voice: "聲音與語言", models: "模型與服務", selection: "使用的設定", voiceHelp: "選擇聲音來源，以及辨識和翻譯語言。", modelsHelp: "管理模型、服務連線和憑證。" },
  en: { voice: "Audio & Languages", models: "Models & Services", selection: "Active configuration", voiceHelp: "Choose audio sources and recognition and translation languages.", modelsHelp: "Manage models, service connections and credentials." },
  ja: { voice: "音声と言語", models: "モデルとサービス", selection: "使用する設定", voiceHelp: "音声入力と認識・翻訳言語を選択します。", modelsHelp: "モデル、サービス接続、認証情報を管理します。" },
  ko: { voice: "오디오 및 언어", models: "모델 및 서비스", selection: "사용할 구성", voiceHelp: "오디오 입력과 인식 및 번역 언어를 선택합니다.", modelsHelp: "모델, 서비스 연결 및 자격 증명을 관리합니다." },
  de: { voice: "Audio & Sprachen", models: "Modelle & Dienste", selection: "Aktive Konfiguration", voiceHelp: "Audioquellen sowie Erkennungs- und Übersetzungssprachen wählen.", modelsHelp: "Modelle, Dienstverbindungen und Zugangsdaten verwalten." },
  fr: { voice: "Audio et langues", models: "Modèles et services", selection: "Configuration active", voiceHelp: "Choisir les sources audio et les langues de reconnaissance et de traduction.", modelsHelp: "Gérer les modèles, les connexions et les identifiants." },
  th: { voice: "เสียงและภาษา", models: "โมเดลและบริการ", selection: "การตั้งค่าที่ใช้", voiceHelp: "เลือกแหล่งเสียง ภาษาที่รู้จำและภาษาแปล", modelsHelp: "จัดการโมเดล การเชื่อมต่อและข้อมูลรับรอง" },
};
export function settingsPageCopy() { return copy[effectiveUiLanguage()]; }
