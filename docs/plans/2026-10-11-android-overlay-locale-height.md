# Android waiting overlay language and spacing

The reported compact waiting card is too short, and its English waiting text can
disagree with the Simplified Chinese settings page after overlay/settings actions.

All service-owned strings resolve the app-selected locale explicitly, including
API 33+ where Android persists application locales. A display/window/service
resource context is not authoritative. Following System resolves actual system
locales rather than retaining a previous app-language context. Preserve other
configuration attributes, subtitle state and existing native windows.
Refresh the existing provider label and its accessibility description on locale
changes too; do not leave its startup-language name beside updated controls.

The non-immersive status-only card uses a 56dp minimum height, centered content
and 16dp vertical padding. Visible captions and immersive mode retain their
existing compact padding and have no added minimum height. Multiline and large
font text determines height naturally; no fixed height or ellipsis is introduced.

Native regression uses a foreign-locale context against the chosen app language,
then the actual empty service overlay. Check expansion/collapse and returning
from all main sections, text bounds, caption/immersive restoration and saved
placement. The fixture starts no capture or providers and restores preferences.
Cover every supported locale and System through the existing native language
suite, with service notification/help/error strings as well as overlay labels.
