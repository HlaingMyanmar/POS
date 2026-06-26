<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://github.com/user-attachments/assets/0aa67016-6eaf-458a-adb2-6e31a0763ed6" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/d8e11300-3500-4110-b2e9-a8d3e3be0a73

## Run Locally

**Prerequisites:**  Node.js


1. Install dependencies:
   `npm install`
2. Set the `GEMINI_API_KEY` in [.env.local](.env.local) to your Gemini API key
   - Optional for network access:
     - `VITE_API_BASE_URL=http://<your-server-ip>:8080/api`
     - `VITE_WS_URL=http://<your-server-ip>:8080/ws-clinic`
     - `VITE_BACKEND_PORT=8080` (used when API/WS URLs are not explicitly set)
   - Optional for dev proxy (recommended for LAN testing):
     - `VITE_DEV_PROXY_TARGET=http://localhost:8080`
     - With default settings in dev mode, frontend calls `/api` and `/ws-clinic`, then Vite forwards to this target.
3. Run the app:
   `npm run dev`

## Access From Another Computer (LAN)

1. Start frontend on host machine:
   - `npm run dev:lan`
2. Open from another computer:
   - `http://<host-ip>:3000`
3. If blocked, allow Windows Firewall inbound for Node.js (or port `3000`).
