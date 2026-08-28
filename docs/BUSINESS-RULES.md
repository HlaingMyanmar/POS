# Business rules

Rules below are taken from service methods. If a path is not described, it was **not** confirmed for this document.

All mutating services listed use `@Transactional`. An uncaught exception rolls the Spring transaction back. Journal/stock helpers are called in the same transaction unless noted.

---

## Sale

**Trigger:** `POST /api/v1/sales` → `SaleService.save`.

**Validation**

- `details` non-empty; `customerId` required; customer must exist.
- Staff: without `CAN_ACCESS_SALE_STAFF_OVERRIDE`, staff must match the logged-in user’s linked staff.
- Date: past needs `CAN_ACCESS_SALE_BACKDATE`; future needs `CAN_ACCESS_SALE_FUTUREDATE`.
- Tax ≥ 0. Paid cannot exceed net (except internal service-job sales).
- Discount capped (cashier 5%, manager 20%) unless `CAN_ACCESS_SALE_DISCOUNT_OVERRIDE` / related flags in `validateDiscountLimit`.
- If due > 0 and customer `creditHold` or `blacklisted` → reject (not for `serviceJobSale`).
- Credit limit via `enforceCreditLimitWithOverride` (manager override fields on DTO).

**Database**

- Insert `sales` + `sale_details`. Temporary `sale_code` `PENDING`, then generated from id.
- FOC flag stored. Payment/credit status derived from net vs paid vs due date.

**Stock / payment**

- `stockLotService.allocateSale` (lots).
- `recordStockMovements` always (reduce inventory).
- Perpetual inventory journal: DR COGS / CR Inventory (`createInventoryValuationJournal`).
- If **not** `serviceJobSale`: payment transactions, AR/cash journal, `recordCustomerPayment`, credit alerts.

**Related:** WebSocket `/topic/sales` `SALE_CREATED`.

**Failure:** Runtime/AccessDenied → transaction rollback; no partial sale.

### Pay due

**Trigger:** `POST /api/v1/sales/{id}/pay`.

Rejects voided sales, due ≤ 0, paid ≤ 0, paid > due. Updates paid/due/status; payment txn + journal; customer payment; alerts.

### Update

**Trigger:** PUT. **Cannot change line details** (`Sale detail updates are not supported.`). Header/discount/tax/paid only. Payment increase/decrease posts adjustment journals; refund decrease validates payment-method balance.

### Void

**Trigger:** DELETE `/{id}?reason=` (`CAN_ACCESS_SALE_VOID` or `SALE_DELETE`).

Reason required. Restores lots (`restoreSaleVoid`), reverses stock, cash refund recording, deletes sale payment transactions, reverses journals by `saleCode`, `saleCode-COGS`, `-PAY`, `-ADJ`. Sets voided metadata. Does not “un-delete” the row.

---

## Purchase

**Trigger create:** `POST /api/v1/purchases` → `PurchaseService.save`.

**Validation**

- Supplier and staff exist; staff override rule like sales (`CAN_ACCESS_PURCHASE_STAFF_OVERRIDE`).
- Non-draft: `periodGuard.assertOpen` on purchase date; supplier invoice uniqueness; 15-second duplicate guard (same supplier+staff+total).
- Serial products: on confirm/non-draft, serial count = qty; serials unique globally.
- Non-serial products must not send serials on confirm.
- Tax/charges validated; budget warnings from `purchaseBudgetService.validate` (non-draft).

**Draft (`status=DRAFT`)**

- Header + details saved. **No** stock, serials, lots, payments, journals until confirm.

**Confirmed create / confirm draft**

**Trigger confirm:** `POST /api/v1/purchases/{id}/confirm` (`CAN_ACCESS_PURCHASE_UPDATE`). Only drafts.

- Serials created `Available` with warranty dates from purchase date + months.
- Non-serial: `product.stockQty` increased; average cost / last purchase cost updated.
- `stock_movements` type `IN`, reference `Purchase`.
- `stockLotService.receivePurchase`.
- Supplier balance sync; payment transactions; purchase journal.
- Paid cannot exceed net. Payment status Paid/Partial/Pending.

**Cancel** (`DELETE`, `CAN_ACCESS_PURCHASE_DELETE`)

- Reason required. Period must be open for original date and for reversal posting.
- **Draft:** status CANCELLED, due zeroed — comment in code says hard delete for draft but implementation **soft-cancels** the draft row.
- **Confirmed:** blocked if purchase returns exist. `stockLotService.cancelPurchase`; stock/serial reversal; reversing journal; supplier re-sync.

**Update:** general PUT is restricted to **draft** (throws if not draft).

---

## Purchase order / goods receipt

**Trigger:** `PurchaseOrderController` approve / reject / receive / close → `PurchaseOrderService`.

Receive creates goods receipt lines and (from prior review) a purchase. Receiver staff: authenticated user’s staff unless `STAFF_OVERRIDE`. Exact approve two-step flags: `CAN_ACCESS_PURCHASE_ORDER_APPROVE` and `CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE`.

**Unknown / Needs Confirmation:** full PO state machine (all statuses) — read `PurchaseOrderService` before changing workflow.

---

## Sale return

**Trigger:** `POST /api/v1/sale-returns` → `SaleReturnService.save`.

**Validation**

- Details and `saleId` required. Sale must exist.
- Cannot return more qty/serials than sold minus already returned (voided returns excluded in qty maps as implemented).
- Full-return guard when nothing left to return.

**Effects (create)**

- Header status `COMPLETED`. Restock: serials back to Available (or stock qty +) when restock flag allows; `MovementType.RETURN`; lot restore via `stockLotService`.
- Adjusts original sale paid/due/credit; optional cash refund vs customer credit (`creditPostedAmount`); journals; payment transactions; cash drawer if cash refund.

**Void:** restores lots (`reverseSaleReturn`), serials back to Sold, stock OUT if restocked, reverses sale adjustments and customer advance, reverses journal, marks payment txns reversed, status `VOIDED` / `deleted=true`.

PUT exists; detail of what may change: **Needs Confirmation** (read `update` method before documenting field-level rules).

---

## Purchase return

**Trigger:** create then workflow methods on `PurchaseReturnService`.

Flow in controller: save → submit → approve/reject → dispatch → supplier-received → settle; void; attachments.

Stock leaves on dispatch/approve (see `dispatch` / approve implementations). Settle posts supplier credit/refund journals. Void reverses lots/journals (regression tests exist under `src/test`).

**Unknown / Needs Confirmation:** exact status names and which step first decrements lots — confirm in `PurchaseReturnService.dispatch` / `approve` before ops training.

---

## Stock movement

Not a user “create movement” API for sales. Movements are **written by other services**.

`MovementType`: `IN`, `OUT`, `RETURN`, `ADJUST`.

Controllers are read-only (`CAN_ACCESS_STOCK_READ`).

---

## Stock adjustment

**Trigger:** `POST /api/v1/stock-adjustments` or `/physical-count`.

**Validation:** product + staff exist. If serials listed, `|qtyChange|` must equal serial count.

| Type | Serial | Non-serial |
|---|---|---|
| DAMAGE / LOSS | Available → Damaged/Lost | qtyChange must be negative; stock cannot go below 0 |
| FOUND | Damaged/Lost → Available | qtyChange must be positive |
| CORRECTION | Cannot correct Sold or Consumed_In_Manufacturing | qty applied to `stockQty` |

Then `stock_movements` `ADJUST` and inventory/expense journal via `JournalWriter`.

Physical-count endpoint calls the same `save`.

---

## Payment (sales / jobs / AP)

- Sale create/pay: `PaymentTransaction` `ReferenceType.Sale` + journals (cash/bank DR, AR CR or revenue).
- Service job settle: journal for net (cash + AR); payment txn only for amount actually paid. Parts create an **internal sale** (`serviceJobSale=true`) so stock moves without a second customer payment.
- Pay-due on job: DR cash, CR AR (`jobNo-PAY`).
- Purchase confirm: AP/cash journals + payment rows.
- `PaymentBalanceValidator` used when refunding through a payment method.
- `POST /api/v1/payment-transactions/transfer` and `/pay-purchase-debt` — dedicated transfer/AP pay (see `PaymentTransactionService`).

---

## Customer

**Create:** unique phone; new customers get `creditHold=true` and reason `"New customer – pending credit review"` (`CustomerService.save`).

Credit hold / blacklist block **credit** sales and job settlements with due > 0.

Credit terms drive `resolveDueDate`. Nightly `CreditCronService` (02:00 Asia/Rangoon) evaluates overdue alerts for Active/Overdue sales with due > 0.

---

## Supplier

Supplier balance is recalculated on purchase confirm/cancel (`syncSupplierBalance`). Payments allocate to purchases (`SupplierPaymentService`). Cannot cancel a confirmed purchase that already has returns.

---

## Service job

**Settle:** credit checks if due > 0; stamps billed prices; status `COMPLETED`; if parts exist, creates sale via `SaleService.save` with `serviceJobSale`; journals if not FOC; payment txns if paid > 0; may apply booking deposit.

**Pay due:** due must be > 0; payment method or payments required.

**Deliver:** status must be `COMPLETED`; due must be 0 or FOC. Then `DELIVERED`. May complete parent booking.

**Void settlement:** `voidSettlement` — reverses settlement (read method before training; serial mapping test exists).

**Rework:** `rework` + `ReworkRequestDTO` (warranty credit field exists on DTO).

**Technician assign:** without `CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN`, technician roles cannot pick another technician (`RoleSeeder` strips that permission from TECH* roles).

---

## Booking → job

`POST /api/v1/bookings/{id}/convert-to-job` copies customer, devices, service lines (warranty months from service item, FOC flag) into jobs (`BookingService`). Tests: `BookingServiceConversionTest`.

---

## Warranty

There is **no** standalone warranty document module. Months/dates live on:

- `products.warranty_months` / `warranty_terms`
- `product_serials` start/end
- `sale_details` months/expiry
- `purchase_details` / `purchase_detail_warranties`
- `services.warranty_months`
- PO line `warrantyMonths` copied on receive

Sale/purchase/serial assign paths copy months onto serials. Job rework DTO includes `warrantyCredit` — usage **Needs Confirmation** in `rework` implementation.

---

## Journal / period lock

`JournalWriter.write` / `reverseByReferenceNo` used by sales, purchases, returns, adjustments, jobs.

`periodGuard.assertOpen` blocks purchase confirm/cancel (and similar) when the accounting period is locked.

Manual journals: `POST /api/v1/journal-entries`. Backfill: `POST /api/v1/admin/backfill-journals`.

---

## Manufacturing

Complete/cancel on `/api/v1/manufacturing/{id}/complete|cancel` consume/produce stock and serials (`Consumed_In_Manufacturing`). **No controller permission.** Treat as authenticated-user capable until a permission is added.

---

## Documentation findings (rules)

- Purchase draft cancel comment vs implementation (soft cancel, not hard delete).
- Sale void does not remove the `sales` row.
- OCR “preview” is regex on text files only (`PurchaseOcrService`).
