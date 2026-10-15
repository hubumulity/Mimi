# Optional Alibaba LiveTranslate experiment

Keep existing Alibaba profiles on their saved route. New desktop Alibaba
configurations default to LiveTranslate; ASR + MT remains selectable. This experiment adds an explicit profile choice for
`qwen3.8-livetranslate-flash-realtime` without restoring the old translation-mode
picker or changing existing profiles.

The shared runtime owns the protocol. The first iteration is audio-only,
16 kHz mono PCM16, text-only output, Beijing workspaces, automatic source
recognition, and Original and all 60 documented text translation targets. Independent MT settings stay
saved and return when the user selects ASR + MT again. LiveTranslate requires an
existing Workspace ID and a matching saved Alibaba key; missing IDs fail before
network access. No credentials are copied when changing the mode.

LiveTranslate 3.8 accumulates source `delta` and translated `response.text.delta`.
Text completion is a preview until `response.done` confirms completed status.
Source/translation identities must remain linked before accepting a final pair.
LiveTranslate ends with `session.finish` → `session.finished`. Original mode suppresses the displayed
translation, but the model can still generate billable translation output.

The desktop selector lists **LiveTranslate (simultaneous)** first, while
keeping existing profiles on ASR + MT until explicitly
changed. An unfinished new profile may retain LiveTranslate with an empty workspace ID;
a valid ID is still required before starting a session or a connection check. Both options have
short descriptions: ASR + MT recognizes first and uses a separately selectable
text translator; LiveTranslate produces source and translated text in one model
session as audio arrives. Its help identifies the experimental limits. Fixed
Beijing console links beside the fields open API Key and workspace management.
The user flow has three steps: get an existing workspace ID and matching key;
select LiveTranslate, fill and save in Mimi; choose automatic recognition and
a supported translation target, then start a short subtitle test. The existing key may
be reused when it belongs to that workspace and has model-call access. The
workspace ID is copied from an existing workspace or the `llm-xxx` prefix of its
API Host; it is never inferred from a key or confused with a model ID. Mimi does
not create workspaces, keys, service activations, or permissions. User setup is
documented in [English](../provider-setup.md#alibaba-livetranslate) and
[简体中文](../provider-setup.zh-CN.md#alibaba-livetranslate).

The macOS manual probe reuses the production facade, the existing public-fixture
manifest validator and PCM pacing. A test-only read-only credential loader
prevents migration, normalization writes, and permission repair. It defaults to
one baseline arm; the LiveTranslate arm requires explicit selection and a current
budget gate. It sends at most one 20-second clip plus the same two-second silence
tail. Audio3 idle PCM is disabled for this comparison. No capture or native app
launch occurs. Provider acceptance, wire events, semantic quality and actual
visible subtitles remain separate evidence boundaries.

Current official references:
[LiveTranslate](https://help.aliyun.com/zh/model-studio/qwen3-5-livetranslate-flash-realtime),
[LiveTranslate server events](https://www.alibabacloud.com/help/en/model-studio/live-translator-server-events),
[Workspace hosts and regions](https://help.aliyun.com/zh/model-studio/regions),
[API Key management](https://help.aliyun.com/zh/model-studio/get-api-key).

The draft does not establish better subtitle quality, long-session stability,
Android selection UI, or Windows/Linux native acceptance. It does not authorize
merging, releasing, recording private audio, or additional paid batches.
