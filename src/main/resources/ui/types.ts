
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface PagedData<T> {
  content: T[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
}

export type AppLanguage = 'en' | 'my';
export type AppTheme = 'light' | 'dark';

export interface AuthResponse {
  accessToken: string;
  username: string;
  name?: string;
  phone?: string;
  staffId?: number;
  roles: string[];
  permissions: string[];
}

export interface User {
  username: string;
  name?: string;
  phone?: string;
  staffId?: number;
  staffName?: string;
  roles: string[];
  permissions: string[];
}

export interface PermissionDTO {
  id: number;
  name: string;
  description?: string;
}

export interface RoleDTO {
  id: number;
  name: string;
  description?: string;
  permissions: PermissionDTO[];
}

export interface UserDTO {
  id: number;
  username: string;
  email: string;
  authProvider: string;
  isActive: boolean;
  roles: string[];
  staffId?: number;
  staffName?: string;
}

export interface BrandDTO {
  id: number;
  name: string;
  isActive: boolean;
}

export type VideoAudience = 'TECHNICIAN' | 'CLIENT' | 'BOTH';
export type VideoAppType = 'TECHNICIAN' | 'CLIENT';

export interface VideoPlacementDTO {
  appType: VideoAppType;
  sortOrder: number;
  featured?: boolean;
  active?: boolean;
}

export interface VideoDTO {
  id: number;
  title: string;
  description?: string;
  provider?: string;
  providerVideoId?: string;
  sourceUrl?: string;
  youtubeUrl?: string;
  thumbnailUrl?: string;
  category?: string;
  targetAudience: VideoAudience;
  sortOrder?: number;
  featured?: boolean;
  placements?: VideoPlacementDTO[];
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface UnitDTO {
  id: number;
  unitName: string;
  description?: string;
}

export interface CategoryDTO {
  id: number;
  name: string;
  description?: string;
  active: boolean;
  parentId: number | null;
  parentName?: string;
  children?: CategoryDTO[];
}

export enum ProductType {
  NEW = 'New',
  SECOND = 'Second',
  SECOND_NEW = 'Second_New'
}

export interface ProductDTO {
  id: number;
  productCode: string;
  name: string;
  currentStock: number;
  hasSerial?: boolean;
  photoBase64?: string;
  imagePath?: string;
  thumbnailPath?: string;
  imageMimeType?: string;
  originalFileName?: string;
  imageWidth?: number;
  imageHeight?: number;
  stockQty?: number;
  quarantinedQty?: number;
  reorderLevel?: number;
  shortageQty?: number;
  productType: ProductType;
  sellingPrice: number;
  costPrice?: number;
  lastPurchaseCost?: number;
  warrantyMonths?: number;
  warrantyTerms?: string;
  remark?: string;
  archived?: boolean;
  warehouseName?: string;
  warehouseId?: number;
  openingQty?: number;
  openingBatch?: string;
  openingExpiry?: string;
  shelfLocation?: string;
  categoryId?: number;
  categoryName?: string;
  brandId?: number;
  brandName?: string;
  unitId?: number;
  unitName?: string;
  availableSerialCount?: number;
  unlinkedQty?: number;
}

export interface ReorderSuggestionDTO {
  productId: number;
  productCode: string;
  productName: string;
  currentStock: number;
  reorderLevel: number;
  suggestedQuantity: number;
  supplierId?: number;
  supplierName?: string;
  currentCost?: number;
}

export interface PriceHistoryDTO {
  purchaseId: number;
  purchaseCode: string;
  purchaseDate: string;
  supplierId?: number;
  supplierName?: string;
  quantity: number;
  unitCost: number;
  weightedAverageCost: number;
}

export interface ProductStockHistoryMovementDTO {
  id: number;
  productId?: number;
  productName?: string;
  productCode?: string;
  date: string;
  type: string;
  referenceId?: number;
  referenceNumber?: string;
  partyName?: string;
  quantityIn: number;
  quantityOut: number;
  balance: number;
}

export interface ProductStockHistoryDTO {
  productId?: number;
  productName: string;
  currentStock: number;
  openingBalance: number;
  totalIn: number;
  totalOut: number;
  closingBalance: number;
  page?: number;
  size?: number;
  totalPages?: number;
  totalElements?: number;
  movements: ProductStockHistoryMovementDTO[];
}

export enum AdjustmentType {
  DAMAGE = 'DAMAGE',
  LOSS = 'LOSS',
  FOUND = 'FOUND',
  CORRECTION = 'CORRECTION'
}

export interface StockAdjustmentDTO {
  id?: number;
  productId: number;
  productName?: string;
  productCode?: string;
  adjustmentType: AdjustmentType;
  qtyChange: number;
  qtyBefore?: number;
  qtyAfter?: number;
  serialNumbers?: string;
  reason?: string;
  staffId: number;
  staffName?: string;
  warehouseId?: number;
  warehouseName?: string;
  createdAt?: string;
}

export enum SerialStatus {
  AVAILABLE = 'Available',
  SOLD = 'Sold',
  CONSUMED_IN_MANUFACTURING = 'Consumed_In_Manufacturing',
  USED_IN_SERVICE = 'Used_In_Service',
  DAMAGED = 'Damaged',
  LOST = 'Lost'
}

export interface ProductSerialDTO {
  id: number;
  serialNumber: string;
  status: SerialStatus;
  productId: number;
  productName?: string;
  warrantyMonths?: number;
  warrantyStartDate?: string;
  warrantyEndDate?: string;
  purchaseId?: number;
  purchaseCode?: string;
  supplierName?: string;
  purchaseDate?: string;
  condition?: string;
  photoBase64?: string;
}

export interface SupplierDTO {
  id: number;
  code: string;
  name: string;
  phone?: string;
  address?: string;
  openingBalance: number;
  currentBalance: number;
  defaultCreditDays?: number;
  creditLimit?: number;
  advanceBalance?: number;
}

export interface CustomerGroupDTO {
  id: number;
  name: string;
  description?: string;
}

export interface CustomerDTO {
  id: number;
  name: string;
  phone: string;
  address: string;
  creditHold?: boolean;
  creditHoldReason?: string;
  blacklisted?: boolean;
  blacklistReason?: string;
  advanceBalance?: number;
  latitude?: number;
  longitude?: number;
  locationAccuracy?: number;
  locationCapturedAt?: string;
  locationSource?: string;
}

export interface StaffDTO {
  id: number;
  name: string;
  phone?: string;
  role: string;
  active: boolean;
  basicSalary?: number;
}

export enum AccountType {
  Asset = 'Asset',
  Liability = 'Liability',
  Equity = 'Equity',
  Income = 'Income',
  Expense = 'Expense'
}

export interface ChartOfAccountDTO {
  id: number;
  accountName: string;
  accountType: AccountType;
  code: string;
  parentId: number | null;
  parentName?: string;
  children?: ChartOfAccountDTO[];
}

export interface PaymentMethodDTO {
  id: number;
  methodName: string;
  active: boolean;
  accountId: number | null;
  accountName?: string;
}

export interface ExpenseDTO {
  id?: number;
  expenseCode?: string;
  expenseDate?: string;
  accountId: number;
  accountName?: string;
  paymentMethodId: number;
  paymentMethodName?: string;
  amount: number;
  description?: string;
  staffId: number;
  staffName?: string;
}

export interface IncomeDTO {
  id?: number;
  incomeCode?: string;
  incomeDate?: string;
  accountId: number;
  accountName?: string;
  paymentMethodId: number;
  paymentMethodName?: string;
  amount: number;
  description?: string;
  staffId: number;
  staffName?: string;
}

export interface DashboardStats {
  totalSales: number;
  totalPurchases: number;
  totalServices: number;
  totalCustomers: number;
  recentSales: SaleSummary[];
  // Today
  todaySalesAmount: number;
  todaySalesCount: number;
  periodServiceAmount: number;
  periodServiceCount: number;
  periodPurchaseAmount: number;
  periodPurchaseCount: number;
  // AR Alerts
  totalOverdueAR: number;
  overdueARCount: number;
  totalPendingAR: number;
  pendingARCount: number;
  // Operations
  pendingServiceJobs: number;
  receivedJobCount: number;
  inProgressJobCount: number;
  completedJobCount: number;
  pendingPaymentJobCount: number;
  pendingDeliveryJobCount: number;
  lowStockCount: number;
  lowStockProducts: string[];
  stockValue: number;
  supplierPayable: number;
  reworkCount: number;
  upgradeCount: number;
  refundCount: number;
  refundAmount: number;
  updatedAt: string;
  // System
  hasJournalEntries: boolean;
}

export interface SaleSummary {
  id: number;
  saleCode: string;
  customerName: string;
  amount: number;
  date: string;
  status: 'Paid' | 'Partial' | 'Pending';
}

export interface SaleDetailDTO {
  productId: number;
  productName?: string;
  qty: number;
  unitPrice: number;
  /** Optional display-only unit price used on the printed voucher. */
  customVoucherPrice?: number;
  /** Stored separately; actual sales totals continue to use subtotal/netAmount. */
  customerMargin?: number;
  subtotal: number;
  allocatedLandedCost?: number;
  discountAmount?: number;
  foc?: boolean;
  warrantyMonths?: number;
  warrantyExpiryDate?: string;
  serialNumbers: string[];
}

export interface SaleDTO {
  id?: number;
  saleCode?: string;
  customerId: number;
  customerName?: string;
  staffId: number;
  staffName?: string;
  saleDate?: string;
  dueDate?: string;
  totalAmount?: number;
  discountAmount?: number;
  taxAmount?: number;
  foc?: boolean;
  netAmount?: number;
  paidAmount?: number;
  dueAmount?: number;
  paymentStatus?: string;
  creditStatus?: string;
  voided?: boolean;
  voidReason?: string;
  voidedBy?: string;
  voidedAt?: string;
  remark?: string;
  paymentMethodId?: number;
  paymentAccountId?: number;
  arAccountId?: number;
  transactionNo?: string;
  payments?: PaymentTransactionDTO[];
  quotationId?: number;
  quotationCode?: string;
  warehouseName?: string;
  warehouseId?: number;
  details: SaleDetailDTO[];
}

export interface SalePaymentDTO {
  paidAmount: number;
  paymentMethodId?: number;
  paymentAccountId?: number;
  transactionNo?: string;
  arAccountId?: number;
  staffId?: number;
  note?: string;
  payments?: PaymentTransactionDTO[];
}

export interface SaleReturnDetailDTO {
  id?: number;
  returnId?: number;
  productId: number;
  productName?: string;
  qty: number;
  unitPrice: number;
  subtotal: number;
  serialNumber?: string;
  serialNumbers?: string[];
  reasonId?: number;
  reasonCode?: string;
  reasonName?: string;
  restock?: boolean;
}

export interface SaleReturnDTO {
  id?: number;
  saleId: number;
  saleCode?: string;
  customerId?: number;
  customerName?: string;
  staffId?: number;
  returnCode?: string;
  returnDate?: string;
  totalReturnAmount: number;
  refundAmount?: number;
  paymentMethodId?: number;
  paymentMethodName?: string;
  transactionNo?: string;
  payments?: PaymentTransactionDTO[];
  status?: string;
  warehouseName?: string;
  settlementType?: string;
  creditNoteNo?: string;
  creditPostedAmount?: number;
  voidedAt?: string;
  voidReason?: string;
  reason?: string;
  details: SaleReturnDetailDTO[];
}

export type AlertType = 'Overdue' | 'Due_Soon' | 'Credit_Limit_Exceeded' | 'Large_Credit_Sale';

export interface CreditAlertDTO {
  id?: number;
  customerId: number;
  saleId?: number;
  alertType: AlertType;
  alertDate?: string;
  resolved?: boolean;
  resolvedAt?: string;
  customerName?: string;
  saleCode?: string;
}

export interface CustomerCreditTermDTO {
  id?: number;
  customerId: number;
  creditLimit?: number;
  creditDays?: number;
  creditAllowed?: boolean;
  customerName?: string;
}

export interface CustomerCreditTermHistoryDTO {
  id?: number;
  customerId?: number;
  customerName?: string;
  oldCreditAllowed?: boolean | null;
  newCreditAllowed?: boolean | null;
  oldCreditDays?: number | null;
  newCreditDays?: number | null;
  oldCreditLimit?: number | null;
  newCreditLimit?: number | null;
  changedAt?: string;
  createdAt?: string;
  changedBy?: string;
  changedByStaffName?: string;
  staffName?: string;
}

export interface CustomerPaymentDTO {
  id?: number;
  customerId: number;
  saleId?: number;
  paymentDate?: string;
  amount: number;
  paymentMethodId: number;
  transactionNo?: string;
  note?: string;
  staffId?: number;
  customerName?: string;
  saleCode?: string;
  paymentMethodName?: string;
  staffName?: string;
  paymentNo?: string;
  allocatedAmount?: number;
  advanceAmount?: number;
  voided?: boolean;
  voidedAt?: string;
  voidedBy?: string;
  voidReason?: string;
  allocations?: {
    saleId: number;
    saleCode?: string;
    amount: number;
    remainingDue?: number;
  }[];
}

export interface SaleReturnReasonDTO {
  id?: number;
  code?: string;
  name: string;
  description?: string;
  active?: boolean;
}

export interface QuotationDTO {
  id?: number;
  quotationCode?: string;
  customerId: number;
  customerName?: string;
  quotationDate?: string;
  validUntil?: string;
  status?: string;
  totalAmount?: number;
  discountAmount?: number;
  netAmount?: number;
  terms?: string;
  remark?: string;
  convertedSaleId?: number;
  convertedBy?: string;
  convertedAt?: string;
  details: SaleDetailDTO[];
}

export interface SaleTimelineEventDTO {
  type?: string;
  at?: string;
  title?: string;
  detail?: string;
  refCode?: string;
  amount?: number;
}

export interface AccountBalanceDTO {
  id: number;
  accountId: number;
  accountName: string;
  fiscalYear: string;
  openingBalance: number;
  currentBalance: number;
  lastUpdated: string;
}

export interface PaymentTransactionDTO {
  id?: number;
  referenceId?: number;
  referenceType?: string;
  paymentMethodId: number;
  paymentMethodName?: string;
  amount: number;
  transactionNo?: string;
  paymentDate?: string;
  referenceCode?: string;
  entityName?: string;
  reversed?: boolean;
  reversedAt?: string;
  reversedBy?: string;
  reversalReason?: string;
}

export interface AccountTransferDTO {
  fromPaymentMethodId: number;
  toPaymentMethodId: number;
  amount: number;
  staffId?: number;
  transactionNo?: string;
  description?: string;
}

export interface JournalDetailDTO {
  accountId: number;
  accountName?: string;
  debit: number;
  credit: number;
}

export interface JournalEntryDTO {
  id?: number;
  entryDate?: string;
  referenceNo: string;
  description: string;
  staffId: number;
  staffName?: string;
  status?: string;
  reversalOfId?: number;
  reversedBy?: string;
  reversedAt?: string;
  reversalReason?: string;
  details: JournalDetailDTO[];
}

export interface PurchaseDetailDTO {
  id?: number;
  productId: number;
  productName?: string;
  qty: number;
  unitCost: number;
  subtotal: number;
  allocatedLandedCost?: number;
  batchNumber?: string;
  expiryDate?: string;
  warrantyMonths?: number;
  warrantyTerms?: string;
  itemWarranties?: number[];
  serialNumbers: string[];
  serialConditions?: string[];
  serialPhotos?: string[];
}

export interface PurchaseDTO {
  id?: number;
  purchaseCode?: string;
  supplierId: number;
  supplierName?: string;
  staffId: number;
  staffName?: string;
  purchaseDate?: string;
  dueDate?: string;
  paymentTermDays?: number;
  totalAmount: number;
  discountAmount?: number;
  paidAmount: number;
  returnAmount?: number;
  refundAmount?: number;
  netAmount?: number;
  supplierCreditAmount?: number;
  dueAmount: number;
  paymentStatus?: string;
  remark?: string;
  details: PurchaseDetailDTO[];
  paymentMethodId?: number;
  transactionNo?: string;
  payments?: PaymentTransactionDTO[];
  // DRAFT / CONFIRMED / CANCELLED (undefined = CONFIRMED legacy)
  status?: string;
  taxAmount?: number;
  taxMode?: 'EXCLUSIVE' | 'INCLUSIVE';
  taxRate?: number;
  withholdingTaxAmount?: number;
  otherCharges?: number;
  landedCostAllocationMethod?: 'VALUE' | 'QUANTITY' | 'MANUAL';
  warehouseName?: string;
  warehouseId?: number;
  currencyCode?: string;
  exchangeRate?: number;
  foreignNetAmount?: number;
  attachmentName?: string;
  attachmentData?: string;
  poId?: number;
  poCode?: string;
  supplierInvoiceNo?: string;
  cancelReason?: string;
  cancelledBy?: string;
  cancelledAt?: string;
  creditLimitOverride?: boolean;
  creditOverrideReason?: string;
  creditOverrideBy?: string;
  creditOverrideAt?: string;
  budgetWarnings?: string[];
}

export interface PurchaseBudgetCheck {
  warnings?: string[];
  blocks?: string[];
  blocked?: boolean;
}

export interface ReorderSuggestionDTO {
  productId: number;
  productName: string;
  productCode: string;
  hasSerial?: boolean;
  stockQty: number;
  reorderLevel: number;
  suggestedQty: number;
  lastCost?: number;
}

export interface PurchaseOrderDetailDTO {
  id?: number;
  productId: number;
  productName?: string;
  hasSerial?: boolean;
  qty: number;
  receivedQty?: number;
  damagedQty?: number;
  rejectedQty?: number;
  unitCost: number;
  subtotal: number;
  itemWarranties?: number[];
  warrantyMonths?: number;
  serialNumbers?: string[];
  serialConditions?: string[];
  serialPhotos?: string[];
}

export interface PurchaseOrderDTO {
  id?: number;
  poCode?: string;
  supplierId: number;
  supplierName?: string;
  staffId: number;
  staffName?: string;
  orderDate?: string;
  expectedDate?: string;
  status?: string; // PENDING_APPROVAL / APPROVED / PARTIAL / RECEIVED / REJECTED / CANCELLED
  approvedBy?: string;
  approvedAt?: string;
  rejectedBy?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  totalAmount: number;
  remark?: string;
  details: PurchaseOrderDetailDTO[];
}

export interface PurchaseOrderReceiveLine {
  detailId: number;
  qty: number;
  damagedQty?: number;
  rejectedQty?: number;
  invoiceUnitCost?: number;
  warrantyMonths?: number;
  itemWarranties?: number[];
  serialNumbers?: string[];
  serialConditions?: string[];
  serialPhotos?: string[];
  batchNumber?: string;
  expiryDate?: string;
}

export interface PurchaseOrderReceivePayload {
  staffId: number;
  lines?: PurchaseOrderReceiveLine[];
  dueDate?: string;
  discountAmount?: number;
  taxAmount?: number;
  otherCharges?: number;
  remark?: string;
  supplierInvoiceNo?: string;
  varianceReason?: string;
  paymentMethodId?: number;
  transactionNo?: string;
  payments?: PaymentTransactionDTO[];
}

export interface GoodsReceiptDTO {
  id: number;
  grnCode: string;
  purchaseOrderId: number;
  poCode: string;
  purchaseId?: number;
  supplierInvoiceNo?: string;
  receivedAt: string;
  receivedBy?: string;
  matchStatus: 'MATCHED' | 'VARIANCE';
  varianceReason?: string;
  lines: Array<{
    productId: number; productName: string; orderedQty: number;
    acceptedQty: number; damagedQty: number; rejectedQty: number;
    poUnitCost: number; invoiceUnitCost: number; priceVariance: number;
  }>;
}

export interface PurchaseReturnDetailDTO {
  id?: number;
  returnId?: number;
  productId: number;
  productName?: string;
  qty: number;
  unitPrice: number;
  subtotal: number;
  allocatedShippingCost?: number;
  serialNumbers: string[];
  reasonId?: number;
  reasonCode?: string;
  reasonName?: string;
  quarantinedQty?: number;
  dispatchedQty?: number;
}

export interface PurchaseReturnReasonDTO {
  id?: number;
  code: string;
  name: string;
  description?: string;
  active: boolean;
}

export interface PurchaseReturnDTO {
  id?: number;
  status?: string;
  version?: number;
  purchaseId: number;
  returnNo?: string;
  returnDate?: string;
  totalReturnAmount?: number;
  refundAmount?: number;
  paymentMethodId?: number;
  paymentMethodName?: string;
  transactionNo?: string;
  payments?: PaymentTransactionDTO[];
  reason?: string;
  submittedBy?: string;
  submittedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  approvalNote?: string;
  rejectedBy?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  resolutionType?: 'REFUND' | 'REPLACEMENT' | 'REPAIR' | 'SUPPLIER_CREDIT' | 'PRICE_ADJUSTMENT' | 'WRONG_DELIVERY';
  rmaNumber?: string;
  claimDate?: string;
  expectedResolutionDate?: string;
  supplierContact?: string;
  claimStatus?: string;
  replacementExpectedQty?: number;
  replacementReceivedQty?: number;
  goodsReceiptId?: number;
  activities?: Array<{ id?: number; eventType: string; fromStatus?: string; toStatus?: string; note?: string; actor?: string; occurredAt?: string }>;
  attachments?: Array<{ id?: number; attachmentType: string; fileName: string; contentType?: string; dataUrl: string; uploadedBy?: string; uploadedAt?: string }>;
  carrier?: string;
  trackingNo?: string;
  dispatchedAt?: string;
  supplierReceivedAt?: string;
  deliveryProof?: string;
  shippingCostAmount?: number;
  shippingPayerResponsibility?: 'COMPANY' | 'SUPPLIER' | 'SHARED';
  companyShippingPortion?: number;
  supplierShippingPortion?: number;
  shippingAllocationMethod?: 'VALUE' | 'QUANTITY' | 'MANUAL';
  shippingPaymentMethodId?: number;
  shippingPaymentMethodName?: string;
  shippingTransactionReference?: string;
  shippingPostedAt?: string;
  shippingPaymentTransaction?: PaymentTransactionDTO;
  settlementType?: 'REFUND' | 'CREDIT_NOTE' | 'REPLACEMENT' | 'OFFSET' | 'SPLIT';
  expectedCreditAmount?: number;
  supplierCreditNoteNo?: string;
  supplierCreditNoteAmount?: number;
  creditVariance?: number;
  creditVarianceReason?: string;
  settledAt?: string;
  settlementReference?: string;
  supplierName?: string;
  purchaseCode?: string;
  details: PurchaseReturnDetailDTO[];
}

export interface ProfitLossLineItem {
  accountCode: string;
  accountName: string;
  amount: number;
}

export interface ProfitLossDTO {
  from: string;
  to: string;
  // Revenue
  grossSales: number;
  serviceRevenue: number;
  salesReturns: number;
  netRevenue: number;
  // Purchases / COGS
  purchases: number;
  purchaseReturns: number;
  netPurchases: number;
  cogs: number;
  // Gross Profit
  grossProfit: number;
  // Other Income
  otherIncomeItems: ProfitLossLineItem[];
  totalOtherIncome: number;
  // Expenses
  expenseItems: ProfitLossLineItem[];
  totalExpenses: number;
  // Bottom line
  netProfit: number;
}

export interface TrialBalanceLineItem {
  accountCode: string;
  accountName: string;
  accountType: string;
  totalDebit: number;
  totalCredit: number;
}

export interface TrialBalanceDTO {
  asOf: string;
  lines: TrialBalanceLineItem[];
  grandTotalDebit: number;
  grandTotalCredit: number;
  balanced: boolean;
}

export interface BalanceSheetLineItem {
  accountCode: string;
  accountName: string;
  balance: number;
}

export interface BalanceSheetDTO {
  asOf: string;
  assets: BalanceSheetLineItem[];
  totalAssets: number;
  liabilities: BalanceSheetLineItem[];
  totalLiabilities: number;
  equityItems: BalanceSheetLineItem[];
  currentYearPnL: number;
  totalEquity: number;
  totalLiabilitiesAndEquity: number;
  balanced: boolean;
}

export interface AgingLineItem {
  partyId?: number;
  referenceNo: string;
  partyName: string;
  invoiceDate: string;
  dueDate: string;
  originalAmount?: number;
  paidAmount?: number;
  dueAmount: number;
  daysPastDue: number;
  daysToDue?: number;
  bucket: string;
}

export interface AgingReportDTO {
  asOf: string;
  lines: AgingLineItem[];
  bucketCurrent?: number;
  bucket0To30: number;
  bucket31To60: number;
  bucket61To90: number;
  bucketOver90: number;
  totalOutstanding: number;
  totalInvoices?: number;
  totalParties?: number;
}


export interface ServiceJobDTO {
  id?: number;
  jobNo?: string;
  customerId: number;
  customerName?: string;
  serviceMode?: 'INDOOR' | 'OUTDOOR';
  assignedStaffId?: number;
  assignedStaffName?: string;
  itemName?: string;
  itemCondition?: string;
  problemDesc?: string;
  diagnosisNotes?: string;
  estimatedCost?: number;
  finalCost?: number;
  discountAmount?: number;
  foc?: boolean;
  netAmount?: number;
  paidAmount?: number;
  dueAmount?: number;
  paymentDiscountAmount?: number;
  paymentDiscountApprovedBy?: string;
  paymentDiscountApprovedAt?: string;
  paymentDiscountApprovalNote?: string;
  dueDeliveryApprovedBy?: string;
  dueDeliveryApprovedAt?: string;
  dueDeliveryApprovalReason?: string;
  voided?: boolean;
  voidReason?: string;
  voidedBy?: string;
  voidedAt?: string;
  dueDate?: string;
  paymentStatus?: string;
  creditStatus?: string;
  receivedDate?: string;
  estimatedCompletion?: string;
  completedDate?: string;
  deliveredDate?: string;
  status?: string;
  paymentMethodId?: number;
  paymentMethodName?: string;
  bookingId?: number;
  bookingNo?: string;
  customerPhone?: string;
  color?: string;
  serialNo?: string;
  accessories?: string;
  saleId?: number;
  rework?: boolean;
  parentJobId?: number;
  parentJobNo?: string;
  reworkType?: string;
  remark?: string;
  lines?: any[];
  productParts?: any[];
}

export interface SettleDTO {
  finalCost?: number;
  discountAmount?: number;
  foc?: boolean;
  paidAmount?: number;
  paymentDiscountAmount?: number;
  paymentDiscountApprovalNote?: string;
  dueDate?: string;
  paymentMethodId?: number;
  paymentAccountId?: number;
  transactionNo?: string;
  payments?: PaymentTransactionDTO[];
}

export interface AuditLogDTO {
  id: number;
  actor: string;
  actorRole?: string;
  action: string;
  module: string;
  resourceId?: string;
  description?: string;
  ipAddress?: string;
  deviceType?: string;
  createdAt: string;
}

export interface ShelfLocationDTO {
  id?: number;
  code: string;
  label?: string;
  active: boolean;
}

export enum AppRoute {
  LOGIN = '/login',
  DASHBOARD = '/',
  USERS = '/rbac/users',
  ROLES = '/rbac/roles',
  PERMISSIONS = '/rbac/permissions',
  PRODUCTS = '/inventory/products',
  PRODUCT_LABELS = '/inventory/barcode-labels',
  PRODUCT_SERIALS = '/inventory/serials',
  BRANDS = '/inventory/brands',
  CATEGORIES = '/inventory/categories',
  UNITS = '/inventory/units',
  SUPPLIERS = '/procurement/suppliers',
  SALES = '/crm/sales',
  QUOTATIONS = '/crm/quotations',
  CREDIT = '/crm/credit-management',
  CUSTOMERS = '/crm/customers',
  STAFF = '/hr/staff',
  COA = '/accounting/coa',
  PAYMENT_METHODS = '/accounting/payment-methods',
  ACCOUNTING_DASHBOARD = '/accounting/dashboard',
  JOURNAL_ENTRIES = '/accounting/journal-entries',
  EXPENSE_INCOME = '/accounting/expense-income',
  PURCHASES = '/procurement/purchases',
  PURCHASE_RETURNS = '/procurement/purchase-returns',
  PURCHASE_ORDERS = '/procurement/purchase-orders',
  SALE_RETURNS = '/sale-returns',
  STOCK_ADJUSTMENTS = '/inventory/stock-adjustments',
  PROFIT_LOSS = '/reports/profit-loss',
  TRIAL_BALANCE = '/reports/trial-balance',
  BALANCE_SHEET = '/reports/balance-sheet',
  AR_AGING = '/reports/ar-aging',
  AP_AGING = '/reports/ap-aging',
  BOOKINGS = '/bookings',
  SERVICES = '/services',
  SERVICE_JOBS = '/service-jobs',
  SERVICE_HELP = '/help/service-workflow',
  BACKUP = '/settings/backup',
  COMPANY_SETTINGS = '/settings/company',
  LABEL_DESIGNER = '/inventory/label-designer',
  AUDIT_LOGS = '/security/audit-logs',
  INCOME_REPORT     = '/reports/income',
  SALES_RANKING     = '/reports/sales-ranking',
  SALES_SUMMARY     = '/reports/sales-summary',
  PURCHASE_SUMMARY  = '/reports/purchase-summary',
  SERVICE_SUMMARY   = '/reports/service-summary',
  CUSTOMER_HISTORY  = '/reports/customer-history',
  STAFF_PERFORMANCE = '/reports/staff-performance',
  STOCK_REPORT      = '/reports/stock',
  VOUCHER_SETTINGS     = '/settings/voucher',
  APP_VERSION_SETTINGS = '/settings/app-version',
  SHELF_LOCATIONS      = '/services/shelf-locations',
  OUTDOOR_TRACKING     = '/services/outdoor-tracking',
  VIDEOS               = '/videos',
  OPENING_BALANCE          = '/accounting/opening-balance',
  OPENING_STOCK            = '/inventory/opening-stock',
  PAYMENT_TRANSACTIONS     = '/accounting/payment-transactions',
  DAILY_SNAPSHOT           = '/reports/daily-snapshot'
}
