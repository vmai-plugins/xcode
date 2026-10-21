#!/usr/bin/env python3
"""
Evolution API <-> VPS Agent Loop & APK Dispatch Webhook Bridge
==============================================================
Runs on your VPS (or local server). Listens for incoming WhatsApp messages
from Evolution API at wa.vmstudio.digital, authenticates the sender, executes
coding tasks via Claude Code / OmniRoute, and sends APKs or results back to WhatsApp.

Requirements:
    pip install fastapi uvicorn requests

Run:
    uvicorn vps_whatsapp_bridge:app --host 0.0.0.0 --port 8765
"""

import os
import subprocess
import requests
from typing import Dict, Any, List
from fastapi import FastAPI, Request, BackgroundTasks

# Configuration
EVOLUTION_BASE_URL = os.getenv("EVOLUTION_BASE_URL", "https://wa.vmstudio.digital").rstrip("/")
EVOLUTION_API_KEY = os.getenv("EVOLUTION_API_KEY", "")
EVOLUTION_INSTANCE = os.getenv("EVOLUTION_INSTANCE", "main")
# Comma-separated list of authorized numbers with country code (e.g., "919876543210,1234567890")
ALLOWED_NUMBERS = [n.strip() for n in os.getenv("ALLOWED_NUMBERS", "").split(",") if n.strip()]
PROJECTS_ROOT = os.getenv("PROJECTS_ROOT", "/var/www")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")

app = FastAPI(title="X-Codes Evolution WhatsApp Agent Bridge")

def transcribe_voice_note(audio_bytes: bytes, filename: str = "voice.ogg") -> str:
    """Transcribes audio notes to text via OpenAI Whisper or local speech tool."""
    if OPENAI_API_KEY:
        try:
            resp = requests.post(
                "https://api.openai.com/v1/audio/transcriptions",
                headers={"Authorization": f"Bearer {OPENAI_API_KEY}"},
                files={"file": (filename, audio_bytes, "audio/ogg")},
                data={"model": "whisper-1"},
                timeout=30
            )
            if resp.status_code == 200:
                return resp.json().get("text", "").strip()
        except Exception as e:
            print(f"[Error] Whisper transcription failed: {e}")
    # Local fallback or return empty
    return ""

def send_whatsapp_text(recipient: str, message: str):
    """Sends a text message back via Evolution API."""
    url = f"{EVOLUTION_BASE_URL}/message/sendText/{EVOLUTION_INSTANCE}"
    headers = {"apikey": EVOLUTION_API_KEY, "Content-Type": "application/json"}
    payload = {"number": recipient, "text": message, "linkPreview": True}
    try:
        requests.post(url, json=payload, headers=headers, timeout=15)
    except Exception as e:
        print(f"[Error] Failed to send WhatsApp message: {e}")

def send_whatsapp_media(recipient: str, file_path: str, caption: str = ""):
    """Sends a file/APK back via Evolution API."""
    import base64
    url = f"{EVOLUTION_BASE_URL}/message/sendMedia/{EVOLUTION_INSTANCE}"
    headers = {"apikey": EVOLUTION_API_KEY, "Content-Type": "application/json"}
    try:
        with open(file_path, "rb") as f:
            encoded = base64.b64encode(f.read()).decode("utf-8")
        filename = os.path.basename(file_path)
        payload = {
            "number": recipient,
            "mediatype": "document",
            "mimetype": "application/vnd.android.package-archive" if filename.endswith(".apk") else "application/octet-stream",
            "caption": caption or f"Artifact: {filename}",
            "media": f"data:application/octet-stream;base64,{encoded}",
            "fileName": filename
        }
        requests.post(url, json=payload, headers=headers, timeout=60)
    except Exception as e:
        print(f"[Error] Failed to send media: {e}")

def handle_user_command(sender: str, text: str):
    """Parses incoming instructions and executes them on the VPS."""
    cmd = text.strip()

    # 1. /status command
    if cmd.lower() in ["/status", "status"]:
        mem = subprocess.getoutput("free -h | awk '/Mem:/ {print $3 \"/\" $2}'")
        disk = subprocess.getoutput("df -h / | awk 'NR==2 {print $3 \"/\" $2 \" (\" $5 \")\"}'")
        uptime = subprocess.getoutput("uptime -p")
        send_whatsapp_text(sender, f"🖥️ *VPS Status Summary*\n• Uptime: {uptime}\n• Memory: {mem}\n• Disk: {disk}\n• WhatsApp: wa.vmstudio.digital")
        return

    # 2. /projects command
    if cmd.lower() in ["/projects", "projects"]:
        if os.path.exists(PROJECTS_ROOT):
            dirs = [d for d in os.listdir(PROJECTS_ROOT) if os.path.isdir(os.path.join(PROJECTS_ROOT, d))]
            send_whatsapp_text(sender, f"📁 *Available Workspaces* ({len(dirs)}):\n" + "\n".join(f"• @{d}" for d in dirs))
        else:
            send_whatsapp_text(sender, "No projects directory found.")
        return

    # 3. /build command
    if cmd.lower().startswith("/build"):
        project_name = cmd.replace("/build", "").strip().lstrip("@")
        target_dir = os.path.join(PROJECTS_ROOT, project_name) if project_name else PROJECTS_ROOT
        send_whatsapp_text(sender, f"⚙️ *Starting release build for* `{project_name or 'current'}`…")
        
        # Look for gradlew
        gradlew = os.path.join(target_dir, "gradlew")
        if os.path.exists(gradlew):
            res = subprocess.run([gradlew, "assembleRelease", "-x", "lintVitalRelease"], cwd=target_dir, capture_output=True, text=True)
            if res.returncode == 0:
                # Find output APK
                apk_path = None
                for root, _, files in os.walk(os.path.join(target_dir, "app/build/outputs/apk/release")):
                    for file in files:
                        if file.endswith(".apk"):
                            apk_path = os.path.join(root, file)
                            break
                if apk_path:
                    send_whatsapp_text(sender, "✅ *Build Succeeded!* Uploading release APK to WhatsApp…")
                    send_whatsapp_media(sender, apk_path, f"Release APK: {os.path.basename(apk_path)}")
                else:
                    send_whatsapp_text(sender, "✅ Build succeeded, but no APK file was found.")
            else:
                send_whatsapp_text(sender, f"❌ *Build Failed*:\n```{res.stderr[-300:]}```")
        else:
            send_whatsapp_text(sender, f"No gradlew found in {target_dir}")
        return

    # 4. Natural language coding via Claude Code / Agent CLI
    send_whatsapp_text(sender, f"🤖 *Agent executing task:* `{cmd[:80]}…`")
    agent_cmd = ["claude", "--print", "--", cmd]
    try:
        res = subprocess.run(agent_cmd, cwd=PROJECTS_ROOT, capture_output=True, text=True, timeout=300)
        output = res.stdout if res.returncode == 0 else res.stderr
        snippet = output[-1500:] if len(output) > 1500 else output
        send_whatsapp_text(sender, f"📋 *Agent Result*:\n{snippet}")
    except subprocess.TimeoutExpired:
        send_whatsapp_text(sender, "⏳ Task timed out after 5 minutes.")
    except Exception as e:
        send_whatsapp_text(sender, f"❌ Execution error: {e}")

@app.post("/webhook/evolution")
async def evolution_webhook(request: Request, bg_tasks: BackgroundTasks):
    """Webhook listener configured in Evolution API."""
    try:
        body = await request.json()
    except Exception:
        return {"status": "ignored"}

    event = body.get("event")
    data = body.get("data", {})

    if event == "messages.upsert":
        key = data.get("key", {})
        if key.get("fromMe"):
            return {"status": "ignored_self"}

        remote_jid = key.get("remoteJid", "")
        sender_number = remote_jid.split("@")[0].replace("+", "")

        # Authenticate sender against ALLOWED_NUMBERS whitelist
        if ALLOWED_NUMBERS and sender_number not in ALLOWED_NUMBERS:
            print(f"[Warning] Unauthorized message from {sender_number}")
            return {"status": "unauthorized"}

        msg = data.get("message", {})
        text = msg.get("conversation") or msg.get("extendedTextMessage", {}).get("text")

        # Audio / Voice note handling (Voice-to-Code)
        if not text and (msg.get("audioMessage") or msg.get("voiceMessage")):
            import base64
            # Attempt to extract base64 media if provided by webhook, or download via Evolution API
            audio_base64 = data.get("base64")
            if audio_base64:
                try:
                    audio_bytes = base64.b64decode(audio_base64.split(",")[-1])
                    transcribed = transcribe_voice_note(audio_bytes)
                    if transcribed:
                        send_whatsapp_text(sender_number, f"🎙️ *Heard*: \"{transcribed}\"\n⚙️ Running task...")
                        text = transcribed
                except Exception as e:
                    print(f"[Error] Failed to process voice note: {e}")

        if text:
            bg_tasks.add_task(handle_user_command, sender_number, text)

    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8765)
