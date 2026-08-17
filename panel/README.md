# SMS Control Panel

Next.js App Router panel for the SMS Android app. Deploy this `panel/` directory as the Vercel project root.

## Required environment variables

- `UPSTASH_REDIS_REST_URL`
- `UPSTASH_REDIS_REST_TOKEN`
- `ADMIN_PASSWORD` — panel login password
- `SESSION_SECRET` — long random secret used to sign the admin session
- `DEVICE_API_TOKEN` — shared secret used by the Android APK to authenticate device events

Use the Vercel Marketplace Upstash Redis integration to provision Redis and inject its credentials into the project.

## Android build secrets

GitHub Actions expects:

- `PANEL_API_URL` — deployed panel URL, e.g. `https://your-panel.vercel.app`
- `PANEL_DEVICE_TOKEN` — same value as `DEVICE_API_TOKEN`
- Existing signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

The Android app sends notification events to `/api/device/event`, heartbeats to `/api/device/heartbeat`, and polls `/api/device/poll` for control commands.

## Control

The panel currently supports `forwarding_enabled` per device. The Android listener polls every 15 seconds and applies the setting locally.
