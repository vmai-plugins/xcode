#!/usr/bin/env bash
# Universal APK Publisher & Apps Hub Updater
# Runs on VPS 2 to publish APKs and update https://vmstudio.digital/apps/
set -euo pipefail

APP_SLUG="${1:-x-codes}"
APP_NAME="${2:-Project X-Codes}"
APP_DESC="${3:-Mobile AI Cloud IDE with live SFTP server file manager, persistent chat memory, and direct OmniRoute AI integration.}"
APK_PATH="${4:-/opt/vmstudio/builds/apk/xcodes-latest.apk}"
VERSION="${5:-v3.18}"

DEPLOY_HOST="${DEPLOY_HOST:-root@31.97.63.239}"
REMOTE_APPS_DIR="/home/vmstudio.digital/public_html/apps"
TARGET_DIR="${REMOTE_APPS_DIR}/${APP_SLUG}"
TARGET_APK="${APP_SLUG}-latest.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

SIZE_MB=$(du -h "$APK_PATH" | awk '{print $1}')

echo "==> [1/2] Uploading $APP_NAME ($SIZE_MB) to VPS 1..."
ssh -o StrictHostKeyChecking=accept-new "$DEPLOY_HOST" "mkdir -p $TARGET_DIR"
scp -q "$APK_PATH" "${DEPLOY_HOST}:${TARGET_DIR}/.${TARGET_APK}.uploading"
ssh "$DEPLOY_HOST" "cd $TARGET_DIR && mv -f .${TARGET_APK}.uploading ${TARGET_APK} && chown -R vmstu1627:vmstu1627 . && chmod 644 ${TARGET_APK}"

echo "==> [2/2] Refreshing Apps Hub index at https://vmstudio.digital/apps/..."
ssh "$DEPLOY_HOST" bash -c "'
python3 - << \"PYEOF\"
import os, re, glob

apps_dir = \"/home/vmstudio.digital/public_html/apps\"
index_file = os.path.join(apps_dir, \"index.html\")

apps = [
    {
        \"slug\": \"x-codes\",
        \"name\": \"Project X-Codes\",
        \"desc\": \"Mobile AI Cloud IDE with live SFTP server file manager, persistent chat memory, and direct OmniRoute AI integration.\",
        \"apk\": \"x-codes/xcodes-latest.apk\",
        \"badge\": \"Active Build\",
        \"version\": \"v3.18\"
    },
    {
        \"slug\": \"mynearby-shop\",
        \"name\": \"MyNearby Shop\",
        \"desc\": \"Multi-vendor local marketplace & ecommerce mobile app for customers.\",
        \"apk\": \"mynearby-shop/mynearby-shop-latest.apk\",
        \"badge\": \"Production\",
        \"version\": \"v2.4\"
    },
    {
        \"slug\": \"mynearby-vendor\",
        \"name\": \"MyNearby Vendor\",
        \"desc\": \"Store management, real-time orders, catalog and inventory management for shop owners.\",
        \"apk\": \"mynearby-vendor/mynearby-vendor-latest.apk\",
        \"badge\": \"Production\",
        \"version\": \"v2.1\"
    },
    {
        \"slug\": \"mynearby-deliveryman\",
        \"name\": \"MyNearby Delivery\",
        \"desc\": \"Delivery partner mobile app with live GPS tracking and order route fulfillment.\",
        \"apk\": \"mynearby-deliveryman/mynearby-deliveryman-latest.apk\",
        \"badge\": \"Active\",
        \"version\": \"v1.9\"
    }
]

cards_html = \"\"
for app in apps:
    apk_rel_path = app[\"apk\"]
    apk_full_path = os.path.join(apps_dir, apk_rel_path)
    if os.path.exists(apk_full_path):
        size_mb = os.path.getsize(apk_full_path) / (1024 * 1024)
        btn_text = f\"Download APK ({size_mb:.1f} MB)\"
    else:
        btn_text = \"Download APK (Latest)\"

    cards_html += f\"\"\"
    <div class=\"app-card\">
      <div class=\"app-header\">
        <div class=\"icon\">
          <svg viewBox=\"0 0 24 24\"><path d=\"M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4zM14.5 4.2l-5 15.6 1.9.6 5-15.6-1.9-.6z\"/></svg>
        </div>
        <div>
          <div class=\"app-title\">{app['name']}</div>
          <span style=\"font-size: 0.8rem; color: #10b981; font-weight: 600;\">● {app['badge']}</span>
        </div>
      </div>
      <p class=\"app-desc\">{app['desc']}</p>
      <div class=\"app-footer\">
        <a href=\"{app['apk']}\" download class=\"btn-dl\">{btn_text}</a>
        <a href=\"./{app['slug']}/\" class=\"btn-details\">Details →</a>
      </div>
    </div>
    \"\"\"

html = f\"\"\"<!DOCTYPE html>
<html lang=\"en\">
<head>
  <meta charset=\"UTF-8\">
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
  <title>VM Studio Apps Hub</title>
  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">
  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\">
  <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap\" rel=\"stylesheet\">
  <style>
    :root {{
      --bg: #090d16;
      --card-bg: rgba(22, 29, 47, 0.7);
      --card-border: rgba(99, 102, 241, 0.18);
      --primary: #6366f1;
      --primary-gradient: linear-gradient(135deg, #6366f1 0%, #a855f7 50%, #ec4899 100%);
      --text: #f8fafc;
      --text-muted: #94a3b8;
    }}
    * {{ box-sizing: border-box; margin: 0; padding: 0; }}
    body {{
      font-family: 'Outfit', -apple-system, BlinkMacSystemFont, sans-serif;
      background: var(--bg);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px 24px;
    }}
    .header {{ text-align: center; margin-bottom: 40px; }}
    .badge {{
      display: inline-block;
      padding: 6px 14px;
      border-radius: 9999px;
      background: rgba(99, 102, 241, 0.12);
      border: 1px solid rgba(99, 102, 241, 0.3);
      color: #818cf8;
      font-size: 0.82rem;
      font-weight: 600;
      text-transform: uppercase;
      margin-bottom: 12px;
    }}
    h1 {{
      font-size: 2.4rem;
      font-weight: 800;
      background: linear-gradient(135deg, #ffffff 0%, #cbd5e1 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-bottom: 8px;
    }}
    .sub {{ color: var(--text-muted); font-size: 1.05rem; }}
    .grid {{
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 24px;
      max-width: 1100px;
      width: 100%;
    }}
    .app-card {{
      background: var(--card-bg);
      backdrop-filter: blur(16px);
      border: 1px solid var(--card-border);
      border-radius: 20px;
      padding: 24px;
      color: inherit;
      display: flex;
      flex-direction: column;
      transition: all 0.25s ease;
    }}
    .app-card:hover {{
      transform: translateY(-4px);
      border-color: rgba(99, 102, 241, 0.4);
      box-shadow: 0 20px 40px -10px rgba(0,0,0,0.5);
    }}
    .app-header {{ display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }}
    .icon {{
      width: 52px; height: 52px; border-radius: 14px;
      background: var(--primary-gradient);
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    }}
    .icon svg {{ width: 28px; height: 28px; fill: #fff; }}
    .app-title {{ font-size: 1.25rem; font-weight: 700; color: #fff; }}
    .app-desc {{
      color: var(--text-muted); font-size: 0.95rem; line-height: 1.5; margin-bottom: 20px; flex-grow: 1;
    }}
    .app-footer {{
      display: flex; justify-content: space-between; align-items: center; gap: 12px;
    }}
    .btn-dl {{
      background: var(--primary-gradient); color: #fff; text-decoration: none;
      padding: 8px 18px; border-radius: 10px; font-weight: 700; font-size: 0.85rem;
      transition: all 0.2s ease;
    }}
    .btn-dl:hover {{ opacity: 0.9; transform: translateY(-1px); }}
    .btn-details {{
      color: #818cf8; text-decoration: none; font-size: 0.85rem; font-weight: 600;
    }}
  </style>
</head>
<body>
  <div class=\"header\">
    <div class=\"badge\">VM Studio Ecosystem</div>
    <h1>Applications Hub</h1>
    <p class=\"sub\">Central repository of software and mobile platforms developed by VM Studio.</p>
  </div>
  <div class=\"grid\">
    {cards_html}
  </div>
</body>
</html>\"\"\"

with open(index_file, \"w\") as f:
    f.write(html)
chown_cmd = f\"chown -R vmstu1627:vmstu1627 {index_file} && chmod 644 {index_file}\"
os.system(chown_cmd)
print(\"Apps Hub updated successfully.\")
PYEOF
'\"

echo \"===> ALL APKS ARE NOW LIVE AT: https://vmstudio.digital/apps/\"
