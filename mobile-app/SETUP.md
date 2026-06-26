# SSPD Manager — Mobile App

React Native (Expo) app for Inventory & Sales.

## Requirements
- Node.js 18+
- Expo CLI: `npm install -g expo-cli eas-cli`
- Android device / emulator (USB debugging on)

## Run (Development)

```bash
cd mobile-app
npm install
npx expo start --android
```

## Build APK (without Expo account — local)

```bash
cd mobile-app
npm install

# EAS account ရှိမှ
npx eas build -p android --profile preview
```

## Build APK (locally, no account needed)

```bash
npm install -g @expo/ngrok
npx expo run:android          # requires Android SDK
```

Or use **Expo Go** app for testing without building:
1. `npx expo start`
2. Android phone မှာ Expo Go install လုပ်
3. QR code scan ဖတ်ပြီး run

## Server URL Setup

Login screen မှာ Server URL ထည့်ပါ:
```
http://192.168.x.x:8080
```
(Spring Boot backend IP/port)

## Features

| Feature | Description |
|---|---|
| Login | JWT auth — same backend |
| Products | List, search, barcode scan |
| Product Detail | Stock, price, condition, serial |
| Sales List | Recent 50 sales with status |
| New Sale | Scan/search items, customer, staff, payment |

## Folder Structure

```
mobile-app/
├── App.tsx
├── src/
│   ├── api/client.ts          # axios + token storage
│   ├── context/AuthContext.tsx
│   ├── navigation/AppNavigator.tsx
│   ├── components/ScannerModal.tsx  # Native camera scanner
│   ├── screens/
│   │   ├── LoginScreen.tsx
│   │   ├── HomeScreen.tsx
│   │   ├── inventory/
│   │   │   ├── ProductListScreen.tsx
│   │   │   └── ProductDetailScreen.tsx
│   │   └── sales/
│   │       ├── SaleListScreen.tsx
│   │       └── NewSaleScreen.tsx
│   ├── theme.ts
│   └── types.ts
```
