# REST and WebSocket API

There is **no Swagger/OpenAPI** module in this repository. This catalog is generated from `*Controller.java` mappings.

Base URL: `https://<host>:8080`  
Most REST paths: **`/api/v1/...`**  
Exception: backup history is **`/api/backups`**.

Frontend Axios uses `baseURL` `/api` (dev) then paths like `/v1/sales`.

## Conventions

### Envelope

Most JSON endpoints return:

```json
{ "success": true, "message": "...", "data": {} }
```

Class: `org.sspd.servicemgmt.api.ApiResponse<T>`.

Paged lists often use `PageResponse<T>` inside `data`.

Binary endpoints (Excel, PDF) return raw bytes with `Content-Disposition`.

### Authentication

Send:

```http
Authorization: Bearer <accessToken>
```

Unless listed as public in [SECURITY.md](SECURITY.md). Missing/invalid JWT typically yields **401**. Failed `@PreAuthorize` yields **403** (`ErrorResponse` or Spring default). Business failures often **400** with `ErrorResponse { status, message, timestamp }` or `ApiResponse` with `success: false`. `ResourceNotFoundException` → **404**. Validation (`@Valid`) → **400** field map (not `ApiResponse`).

Login success wraps `AuthResponse` in `ApiResponse`.

### Errors (handler)

`GlobalExceptionHandler`:

| Exception | HTTP |
|---|---|
| `BadCredentialsException` | 401 + ApiResponse message |
| `MethodArgumentNotValidException` | 400 `{ field: message }` |
| `ResourceNotFoundException` | 404 ErrorResponse |
| `IllegalStateException` / `IllegalArgumentException` | 400 ErrorResponse |
| `AccessDeniedException` | 403 ErrorResponse |
| Other `RuntimeException` | 400 (or 500 if NPE/index/class-cast/arithmetic) |

---

## Auth

| Method | URL | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public (`/api/v1/auth/**`) | Authenticate |

**Request** (`AuthRequest`): `{ "usernameOremail": string, "password": string }`

**Response data** (`AuthResponse`): `accessToken`, `refreshToken`, `username`, `name`, `phone`, `staffId`, `roles[]`, `permissions[]`

**Side effects:** increments `users.token_version` (invalidates other sessions); sets HttpOnly cookie `refreshToken` (7 days, Secure, SameSite Lax); writes LOGIN audit log.

**Errors:** 401 wrong password.

**Not implemented:** `POST /api/v1/auth/refresh` (frontend still calls it).

---

## Setup and public-ish

| Method | URL | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/setup/status` | Public GET | Whether first-run setup is complete |
| POST | `/api/v1/setup/initialize` | Authenticated `/api/**` | Write company + payment methods (`SetupInitDTO`) |
| GET | `/api/v1/company-settings` | Public GET | Company profile |
| POST | `/api/v1/company-settings` | JWT only, **no permission** | Save settings |
| GET | `/api/v1/app/version` | Public GET | POS Manager version + `/app/servicemgmt.apk` URL if file exists |
| GET | `/api/v1/app/technician/version` | Public GET | Technician version + `/app/technician.apk` URL if file exists |
| POST | `/api/v1/scan` | Public POST | Broadcast barcode to `/topic/barcode-scan` `{ "barcode": "..." }` |

---

## Users, roles, permissions

Prefix `/api/v1/user` — permission `CAN_ACCESS_USER*` / `USERS_READ`.

| Method | URL | Purpose |
|---|---|---|
| GET | `/` | List |
| GET | `/{id}` | Get |
| POST | `/` | Create `UserDTO` |
| PUT | `/{id}` | Update |
| DELETE | `/{id}` | Delete |
| PUT | `/{userId}/role` | Assign roles — body `Set<Long>` role ids |
| DELETE | `/{userId}/role/{roleId}` | Remove role |
| GET | `/me` | Current profile (JWT, no extra permission) |
| PUT | `/profile` | Update own profile (JWT only) |

Prefix `/api/v1/roles`:

| Method | URL | Permission |
|---|---|---|
| GET `/`, GET `/{id}` | Read | `CAN_ACCESS_ROLES_READ` |
| POST `/` | Create | `CAN_ACCESS_ROLE_CREATE` |
| PUT `/{id}` | Update | `CAN_ACCESS_ROLE_UPDATE` |
| DELETE `/{id}` | Delete | `CAN_ACCESS_ROLE_DELETE` |
| PUT `/{roleId}/permissions` | **Replace** permission set (`Set<Long>` ids) | ADMINISTRATOR **or** `CAN_ACCESS_ROLE_ASSIGN_PERMISSIONS` |
| DELETE `/{roleId}/permissions/{permissionId}` | Remove one | ADMINISTRATOR **or** `CAN_ACCESS_ROLE_REMOVE_PERMISSIONS` |

Prefix `/api/v1/permissions` — `CAN_ACCESS_PERMISSIONS_READ` / create-update-delete also allow `hasRole('ADMINISTRATOR')`.

---

## Sales

Prefix `/api/v1/sales`. Auth: `CAN_ACCESS_SALE_*`.

| Method | URL | Permission | Purpose |
|---|---|---|---|
| GET | `/` | READ | Page: `page`, `size`, `search`, `dateFrom`, `dateTo` |
| GET | `/stats` | READ | Stats |
| GET | `/{id}` | READ | Detail |
| GET | `/{id}/timeline` | READ | Timeline |
| GET | `/export/excel` | READ | XLSX bytes |
| POST | `/` | CREATE | Create `SaleDTO` → 201 |
| PUT | `/{id}` | UPDATE | Header/payment update; **detail updates rejected** |
| POST | `/{id}/pay` | UPDATE | Body `SalePaymentDTO` |
| DELETE | `/{id}?reason=` | VOID or DELETE | Void sale |

**Create validation (service):** details required; customer required; tax ≥ 0; paid ≤ net; credit hold/blacklist block credit sales; staff override permission; backdate/futuredate permissions; discount limits (cashier 5% / manager 20% unless override flags). Void requires non-blank `reason`.

**Pay body:** `paidAmount`, `paymentMethodId`, `paymentAccountId`, `transactionNo`, `arAccountId`, `staffId`, `note`, `payments[]`.

---

## Sale returns

`/api/v1/sale-returns` — `CAN_ACCESS_SALE_RETURN_*`

GET `/`, GET `/by-sale/{saleId}`, GET `/{id}`, POST `/`, PUT `/{id}`, DELETE `/{id}?reason=`, POST `/{id}/void`.

`/api/v1/sale-return-reasons` — GET/POST/PUT (permission on **service** layer).

---

## Quotations

`/api/v1/quotations`

GET all, GET `/{id}`, POST, PUT `/{id}`, PATCH `/{id}/status?status=`, POST `/{id}/convert-to-sale` (needs `QUOTATION_CONVERT_TO_SALE` **and** `SALE_CREATE`).

---

## Cash drawer

`/api/v1/cash-drawers` — `CASH_DRAWER_READ` / `MANAGE`

GET `/`, GET `/{id}/movements`, POST `/open`, POST `/{id}/cash-in`, `cash-out`, `close`.

---

## Purchases

`/api/v1/purchases` — `CAN_ACCESS_PURCHASE_*` (analytics/import/reorder have extra flags).

| Method | URL | Notes |
|---|---|---|
| GET | `/` | Paged search + dates |
| GET | `/stats`, `/trend`, `/analytics`, `/overdue`, `/top-suppliers`, `/reorder-suggestions` | |
| POST | `/budget-check` | Body `PurchaseDTO` |
| GET | `/{id}`, `/{id}/timeline` | |
| GET | `/export/excel` | |
| POST | `/` | Create; `status=DRAFT` skips stock |
| PUT | `/{id}` | Draft-only general update (service) |
| PUT | `/{id}/attachment` | |
| POST | `/{id}/confirm` | Confirm draft |
| DELETE | `/{id}` | Cancel/void + reason |
| POST | `/import/preview`, GET `/import/template` | Excel |
| POST | `/ocr/preview` | Multipart file; **text/csv parsed**; images not OCR’d |

`/api/v1/purchase-orders` — list, overdue, `/{id}`, goods-receipts, CRUD, approve, reject, receive, close.

`/api/v1/purchase-returns` — list (many filters), by-purchase, CRUD, void, submit, approve, reject, attachments, dispatch, supplier-received, settle.

`/api/v1/purchase-return-details` — CRUD with `PURCHASE_RETURN_DETAIL_*`.

`/api/v1/purchase-return-reasons` — GET/POST/PUT (auth via service).

`/api/v1/purchase-budgets` — list/save/update/toggle/delete (`CAN_ACCESS_PURCHASE_BUDGET`).

`/api/v1/supplier-payments` — POST pay, GET supplier payables/credit-summary, apply-credit, void.

---

## Products and stock

`/api/v1/products`

GET `/`, `/low-stock`, `/reorder-suggestions`, `/{id}`, `/{id}/price-history` (PRICE_HISTORY_READ), `/{id}/next-serial-seq`, `/export`, `/import-template`, `/stock-history`, `/{productId}/stock-history`.

POST `/`, POST `/import` (multipart), POST `/{id}/assign-serials`.

PUT `/{id}`, `/{id}/archive?archived=`, `/{id}/photo` `{ photoBase64 }`.

DELETE `/{id}` archives (does not hard-delete).

`/api/v1/product-serials` — list, by-product, by-serial, CRUD, photo, `POST /by-serials`, delete by serial.

`/api/v1/brands`, `/api/v1/category` (tree, sub, filter, CRUD), `/api/v1/units` — standard CRUD + READ permissions.

`/api/v1/stock-adjustments` GET page, GET `/{id}`, POST `/`, POST `/physical-count` (`CAN_ACCESS_PHYSICAL_STOCK_COUNT`).

`/api/v1/stock-movements` GET list / by product / range / `movement-type`.

`/api/v1/stock-lots/expiring`, `/warehouse-balances`.

`/api/v1/warehouses` GET/POST/PUT, POST `/transfers`, GET `/transfers`.

`/api/v1/shelf-locations` CRUD + `/active`.

`/api/v1/manufacturing` GET/POST/PUT, `/{id}/complete`, `/{id}/cancel`, DELETE — **no @PreAuthorize on controller**.

`/api/v1/manufacturing/formulas` CRUD — **no controller PreAuthorize found**.

---

## Customers, suppliers, staff

`/api/v1/customers` CRUD — permission on **CustomerService**.

`/api/v1/suppliers` GET (paged), `/all`, `/search`, GET `/{id}`, POST/PUT/DELETE.

`/api/v1/staffs` GET, `/active`, POST/PUT/DELETE — permission on **StaffService**.

---

## Credit

`/api/v1/credit-terms` GET, GET `/customer/{customerId}`, POST, PUT.

`/api/v1/credit-alerts` GET, GET `/customer/{id}`, POST `/{alertId}/resolve`.

`/api/v1/customer-payments` POST, `/allocate`, `/apply-credit`, `/{id}/void`, GET by customer / receivables / credit-summary / sale.

---

## Bookings and jobs

`/api/v1/bookings`

| Method | Path | Notes |
|---|---|---|
| GET | `/` | paged (`page`, `size`, `search`, `dateFrom`, `dateTo`) |
| GET | `/{id}` | detail (+ linked jobs when loaded) |
| POST | `/` | create → status `CONFIRMED` |
| PUT | `/{id}` | update (not when canceled / fully converted) |
| PATCH | `/{id}/status?status=CANCELED` | **cancel only** — other statuses rejected |
| POST | `/{id}/items` | receive indoor items → status `ARRIVED` |
| DELETE | `/{id}/items/{itemId}` | remove unconverted item |
| POST | `/{id}/convert-outdoor` | `CONFIRMED` → one outdoor job |
| POST | `/{id}/convert-indoor` | `ARRIVED` → one job per unconverted item |
| DELETE | `/{id}` | `CONFIRMED` only, no items / linked jobs |

Booking statuses: `CONFIRMED`, `ARRIVED`, `CANCELED`. There is **no** `/scan`, `/upcoming`, or `convert-to-job` endpoint.

`/api/v1/service-types`, `/api/v1/sub-service-types`, `/api/v1/services` — catalog CRUD; services include `/active`, `/by-type/{id}`, `/{id}/price-history`.

`/api/v1/service-jobs`

GET `/` (page, search, dates), `/{id}`, `/by-booking/{id}`, `/customer/{customerId}`, `/status/{status}`, `/unpaid`, `/overdue`, `/used-serial-numbers`, `/pending-handovers/mine`, `/handovers/sent/mine`.

POST `/`, PUT `/{id}`, PATCH `/{id}/status`, POST settle, pay-due, deliver, approve-due-delivery, rework, void, approve-estimate, hold-estimate, reject-estimate, approve-final, lead-final-check, return-final, notify, attachments, DELETE.

Settle/pay body: `SettleDTO` (finalCost, discount, foc, paidAmount, payments, paymentMethodId, dueDate, …).

`POST /{id}/notify` — persists notification history only; outbound SMS/Viber/Telegram requires a `CustomerNotifier` provider bean (default logs only).

Job numbers are assigned from the persisted ID (`SJ-000123`) after insert to avoid concurrent collisions.

---

## Accounting

`/api/v1/chart-of-accounts` GET, `/tree`, `/{id}`, POST/PUT/DELETE.

`/api/v1/journal-entries` GET, GET `/{id}`, POST.

`/api/v1/payment-methods` GET, `/active`, `/{id}`, POST/PUT/DELETE.

`/api/v1/payment-transactions` GET, `/report`, `/reference/{refId}?type=`, POST, POST `/transfer`, POST `/pay-purchase-debt`.

`/api/v1/account-balances` GET, `/account/{accountId}`, `/filter?accountId&fiscalYear`, POST `/set-opening`.

`/api/v1/expenses` GET, GET `/{id}`, POST (no PUT/DELETE on controller).

`/api/v1/incomes` GET, GET `/{id}`, POST.

`/api/v1/accounting-period-locks` GET, POST lock, POST `/{id}/unlock` (permission on service).

`POST /api/v1/admin/backfill-journals` — `CAN_ACCESS_JOURNAL_CREATE`.

---

## Reports

Prefix `/api/v1/reports` unless noted.

| Method | URL | Typical permission |
|---|---|---|
| GET | `/api/v1/dashboard/stats` | JWT only (no @PreAuthorize) |
| GET | `/profit-loss` | REPORT_READ |
| GET | `/trial-balance`, `/balance-sheet`, `/ar-aging`, `/ap-aging` | REPORT_READ |
| GET | `/sales-summary`, `/purchase-summary`, `/service-summary`, `/daily-summary`, `/yearly-summary` | see controller |
| GET | `/staff`, `/staff/performance` | STAFF_READ / report |
| GET | `/sales-ranking/products`, `/sales-ranking/monthly` | SALE_READ |
| GET | `/sale/{saleId}`, `/sale/{saleId}/pos`, `/booking/{bookingId}`, `/service-job/{jobId}` | PDF |

Query params for summaries: `from`, `to`, `year` as in controllers.

---

## Print, export, barcode, backup, chat, audit

`/api/v1/print` — POST `/pdf`, POST `/preview`, GET `/pdf/sale/{id}`, `/pdf/service-job/{id}`, `/pdf/booking/{id}` (+ preview variants). JWT only, **no permission annotation**.

`/api/v1/voucher-settings` GET `/`, GET `/{type}`, PUT `/{type}`, POST `/{type}/reset`. JWT only.

`/api/v1/export/bookings`, `/services` — XLSX, JWT only.

`/api/v1/barcode-label-settings` GET/POST. `/api/v1/barcode-label-presets` CRUD. JWT only on controllers.

`/api/v1/backup` — settings GET/POST, POST `/run-now`, GET `/list`, POST `/import` (multipart). Permissions `BACKUP_*`.

`/api/backups` — POST create, GET history, POST `/{id}/restore`, DELETE `/{id}`.

`/api/v1/chat/messages` GET, POST `/send`. STOMP `@MessageMapping("/chat.send")`. JWT only, no chat permission.

`/api/v1/audit-logs` GET — `CAN_ACCESS_AUDIT_LOG_READ`.

`/api/v1/app-version-settings` GET/POST, POST `/upload-apk`, GET `/apk-exists`. JWT only on controller.

---

## WebSocket

| Endpoint | Clients |
|---|---|
| `/ws-clinic` | SockJS (web) |
| `/ws-native` | Native WS |

Subscribe to `/topic/...`. Send chat to `/app/chat.send`.

`POST /api/v1/scan` is public and publishes `/topic/barcode-scan`.

---

## Documentation findings (API)

- No OpenAPI spec.
- Frontend `POST /v1/auth/refresh` has no backend mapping.
- Several mutating routes are JWT-authenticated without a permission flag (company save, print, manufacturing, export, chat, voucher settings, barcode, app-version-settings).
- Expense/income controllers expose create+read only; update/delete if they exist are **not** on these controllers (check `ExpenseService` before assuming they are missing entirely).
