
import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

console.log("System Initializing...");

// Global error listener to help debug white screens
window.onerror = function(message, source, lineno, colno, error) {
  console.error("Critical System Error:", message, "at", source, lineno, colno, error);
  const rootElement = document.getElementById('root');
  if (rootElement && rootElement.innerHTML === '') {
    rootElement.innerHTML = `
      <div style="padding: 32px; font-family: 'Inter', sans-serif; color: #991b1b; background: #fef2f2; border: 1px solid #fee2e2; border-radius: 16px; margin: 60px auto; max-width: 600px; text-align: center; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
        <div style="background: #ef4444; color: white; width: 48px; height: 48px; border-radius: 50%; display: flex; items-center; justify-content: center; margin: 0 auto 16px; font-size: 24px; font-weight: bold;">!</div>
        <h2 style="margin-top: 0; font-weight: 800; font-size: 20px;">Initialization Failure</h2>
        <p style="font-size: 14px; color: #7f1d1d; margin-bottom: 20px;">The ERP system failed to load. This is usually caused by network issues or blocked external resources.</p>
        <div style="text-align: left; background: #0f172a; color: #38bdf8; padding: 16px; border-radius: 8px; font-family: 'Courier New', monospace; font-size: 12px; margin: 16px 0; overflow-x: auto; border: 1px solid #1e293b;">
          <strong>Error:</strong> ${message}<br/>
          <strong>Source:</strong> ${source || 'unknown'}:${lineno || '0'}
        </div>
        <button onclick="window.location.reload()" style="padding: 12px 24px; background: #ef4444; color: white; border: none; border-radius: 10px; cursor: pointer; font-weight: 700; font-size: 14px; width: 100%; transition: background 0.2s;">Force Reload System</button>
      </div>
    `;
  }
};

const container = document.getElementById('root');
if (container) {
  try {
    const root = createRoot(container);
    root.render(
      <React.StrictMode>
        <App />
      </React.StrictMode>
    );
    console.log("System Mounted Successfully.");
  } catch (err) {
    console.error("System Mount Failed:", err);
  }
} else {
  console.error("Fatal: Root element not found.");
}
