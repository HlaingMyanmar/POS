# SSPD Service Management System

A full-stack business management system for service shops — covering sales, purchases, inventory, service jobs, accounting, and more.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5.8 (Java 17) |
| Database | MySQL 8 (`ser_db`) |
| Authentication | JWT (HS256) |
| Caching | Hibernate L2 Cache (Caffeine / JCache) |
| Frontend | React + Vite (served via Nginx) |
| WebSocket | STOMP over SockJS |
| Mobile | Native Android — Kotlin + Jetpack Compose |

---

## Features

### Sales & Invoicing
- Create and manage sales invoices with line items
- Apply discounts, credits, and partial payments
- Credit status tracking (PAID / CREDIT / DUE)
- Sale returns with stock reversal

### Purchase Management
- Purchase orders and purchase returns
- Supplier tracking and payment management

### Service Jobs
- Job cards with repair status lifecycle (`PENDING → IN_PROGRESS → DONE → SETTLED`)
- Service lines, parts used, and technician assignment
- Rework tracking and warranty management
- Credit / due settlement per service job

### Inventory & Stock
- Stock levels, shelf locations, and product serials
- Stock adjustments and full movement history
- Barcode label printing with custom presets
- ML Kit barcode scanning (mobile)

### Customer & Supplier Management
- Full CRM with credit limits and credit terms
- Customer payment history and credit alerts

### Staff Management
- Staff profiles, attendance, and performance reports

### RBAC
- Role-based access control with granular permissions per endpoint

### Accounting & Finance
- Chart of Accounts (COA) with account types
- Double-entry journal entries and journal backfill
- Income & expense tracking
- Payment methods and payment transaction ledger
- Account balance management and transfers
- Profit & Loss statement
- Financial statements

### Reports & Dashboard
- Dashboard summary statistics
- Sales ranking report
- Staff performance report
- Summary reports and voucher reports
- Excel export for all major reports

### Booking System
- Appointment and service booking with device info
- Booking status management and booking alerts

### Real-time Chat
- WebSocket-based chat (STOMP / SockJS)

### Backup
- Scheduled automatic database backups with configurable frequency

### Printing & Export
- PDF receipt and voucher printing
- Barcode label printing with layout presets
- Excel export

### Audit Logging
- AOP-based audit trail for all create / update / delete actions

### Company Settings
- Configurable company profile used across receipts and reports

### Mobile App (Android)
- Sales, sale list, and invoice printing
- Sale returns (create, list, detail)
- Service job management (create, list, detail, print, settle)
- Booking management (create, list, detail, print)
- Product catalogue with barcode scanning
- Shelf location management
- Expense recording
- Income & summary reports
- Staff report and sales ranking
- Real-time chat
- Audit log viewer
- Burmese-language UI with Myanmar date/time

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Maven | 3.8+ |
| MySQL | 8+ |
| Nginx | Any stable (for production) |
| Android Studio | Ladybug+ (for mobile development) |
| Android SDK | API 26+ (minSdk), API 35 (compileSdk) |

---

## Getting Started

### 1. Database Setup

```sql
CREATE DATABASE ser_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configure Application

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ser_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

Also update the CORS allowed origins to match your LAN IP:

```properties
app.cors.allowed-origins=https://localhost:8080,https://192.168.x.x:8080
```

### 3. Run Backend

```bash
./mvnw spring-boot:run
```

Backend starts at `https://localhost:8080` (HTTPS with self-signed PKCS12 keystore).

### 4. Production Deployment (Nginx)

```bash
# Build the frontend
cd src/main/resources/ui && npm run build:standalone

# Copy dist/ to web root
sudo cp -r dist/ /var/www/sspd/

# Copy and activate Nginx config
sudo cp deploy/nginx.conf /etc/nginx/conf.d/sspd.conf

# Edit server_name in nginx.conf to match your LAN IP
sudo nginx -t && sudo systemctl reload nginx
```

With Nginx in front, the browser sees everything on port 80 (same origin) — no CORS needed. Set `VITE_BACKEND_PORT=80` in `.env.standalone` before building.

---

## Mobile App (Android)

The Android app is a native Kotlin app built with **Jetpack Compose** and communicates with the backend over HTTPS.

### Tech Stack (Mobile)

| Library | Purpose |
|---|---|
| Jetpack Compose BOM 2024.12.01 | Declarative UI |
| Material 3 | Design system |
| Navigation Compose | Screen routing |
| Lifecycle ViewModel + Compose | State management |
| Retrofit 2 + Gson | REST API client |
| OkHttp 4 + Logging Interceptor | HTTP layer |
| CameraX 1.4 | Camera for barcode scanning |
| ML Kit Barcode Scanning | Barcode / QR decoding |
| Kotlin Coroutines | Async operations |

### Build & Run

```bash
# Open android-app/ in Android Studio
# Connect an Android device (API 26+) or start an emulator

# Build release APK (already signed with sspd-release.keystore)
./gradlew assembleRelease
```

### Change Backend URL

Edit `android-app/app/src/main/java/com/sspd/servicemgmt/api/ApiClient.kt` and update the base URL to point to your server's LAN IP.

---

## Project Structure

```
servicemgmt-master/
├── src/
│   └── main/
│       ├── java/org/sspd/servicemgmt/
│       │   ├── saleoptions/              # Sales, sale details, sale returns
│       │   ├── purchaseoptions/          # Purchases & purchase returns
│       │   ├── servicejoboptions/        # Service job tracking
│       │   ├── stockoptions/             # Inventory, products, serials, adjustments
│       │   ├── customeroptions/          # Customer CRM
│       │   ├── supplieroptions/          # Supplier management
│       │   ├── staffoptions/             # Staff profiles & attendance
│       │   ├── rbacoptions/              # Roles, permissions, users
│       │   ├── accountingoptions/        # COA, journal, income, expense, payments
│       │   ├── reportoptions/            # Dashboard, P&L, financial statements
│       │   ├── bookingoptions/           # Appointment booking
│       │   ├── chatoptions/              # Real-time chat (STOMP)
│       │   ├── backupoptions/            # Scheduled DB backup
│       │   ├── auditoptions/             # Audit log (AOP)
│       │   ├── barcodesettingoptions/    # Barcode label presets
│       │   ├── creditoptions/            # Credit terms & customer payments
│       │   ├── exportoptions/            # Excel export
│       │   ├── printingoptions/          # Invoice & voucher printing
│       │   ├── companysettingoptions/    # Company profile settings
│       │   ├── brandoptions/             # Product brands
│       │   ├── categoryoptions/          # Product categories
│       │   ├── unitsoptions/             # Units of measure
│       │   ├── shelflocationoptions/     # Shelf locations
│       │   ├── serviceoptions/           # Service types & items
│       │   ├── journaloption/            # Journal entries & backfill
│       │   └── authoption/               # JWT authentication
│       └── resources/
│           ├── application.properties
│           ├── keystore.p12              # HTTPS self-signed certificate
│           └── ui/                       # React + Vite frontend source
├── android-app/                          # Native Android (Kotlin + Compose)
│   └── app/src/main/java/com/sspd/servicemgmt/
│       ├── api/                          # Retrofit client & API interfaces
│       ├── navigation/                   # NavHost & screen routes
│       ├── ui/screens/                   # Composable screen files
│       ├── ui/viewmodel/                 # ViewModels per screen
│       └── ui/theme/                     # Colors, typography, shapes
├── deploy/
│   └── nginx.conf                        # Nginx reverse proxy config
└── pom.xml
```

---

## API

All REST endpoints are prefixed with `/api/`. Protected routes require a Bearer JWT token in the `Authorization` header.

**Authentication:**
```
POST /api/auth/login     → returns { token, username, roles, ... }
```

**Key endpoint groups:**

| Group | Prefix |
|---|---|
| Sales | `/api/sales` |
| Sale Returns | `/api/sale-returns` |
| Purchases | `/api/purchases` |
| Purchase Returns | `/api/purchase-returns` |
| Service Jobs | `/api/service-jobs` |
| Products | `/api/products` |
| Stock Adjustments | `/api/stock-adjustments` |
| Customers | `/api/customers` |
| Suppliers | `/api/suppliers` |
| Staff | `/api/staff` |
| Bookings | `/api/bookings` |
| Expenses | `/api/expenses` |
| Income | `/api/income` |
| Journal Entries | `/api/journal-entries` |
| Chart of Accounts | `/api/chart-of-accounts` |
| Payment Methods | `/api/payment-methods` |
| Reports (P&L, Dashboard, etc.) | `/api/reports/*` |
| Audit Logs | `/api/audit-logs` |
| Backup | `/api/backup` |

**WebSocket:**
```
/ws-clinic/    → STOMP endpoint (SockJS)
/topic/chat    → chat message broadcast
/topic/booking-alert → booking notifications
```

---

## Security

- HTTPS enforced via PKCS12 keystore (`keystore.p12`)
- JWT (HS256) with 24-hour expiry
- CORS restricted to explicitly listed LAN origins
- RBAC: every API endpoint is guarded by role/permission checks
- Audit logging captures all mutating operations with user and timestamp

---

## License

Private — SSPD Internal Use Only.
