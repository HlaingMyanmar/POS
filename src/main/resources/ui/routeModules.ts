import { lazy } from 'react';
import type { ComponentType, LazyExoticComponent } from 'react';
import { AppRoute } from './types';

type PageModule = { default: ComponentType<any> };
type PageLoader = () => Promise<PageModule>;
type LazyPage = LazyExoticComponent<ComponentType<any>> & { preload: PageLoader };

const page = (loader: PageLoader): LazyPage =>
  Object.assign(lazy(loader), { preload: loader });

export const Login = page(() => import('./pages/Login'));
export const Dashboard = page(() => import('./pages/Dashboard'));
export const UserManagement = page(() => import('./pages/UserManagement'));
export const RoleManagement = page(() => import('./pages/RoleManagement'));
export const PermissionManagement = page(() => import('./pages/PermissionManagement'));
export const ProductManagement = page(() => import('./pages/ProductManagement'));
export const LabelDesigner = page(() => import('./pages/LabelDesigner'));
export const ProductSerialManagement = page(() => import('./pages/ProductSerialManagement'));
export const BrandManagement = page(() => import('./pages/BrandManagement'));
export const CategoryManagement = page(() => import('./pages/CategoryManagement'));
export const UnitManagement = page(() => import('./pages/UnitManagement'));
export const SupplierManagement = page(() => import('./pages/SupplierManagement'));
export const CustomerManagement = page(() => import('./pages/CustomerManagement'));
export const StaffManagement = page(() => import('./pages/StaffManagement'));
export const ChartOfAccountManagement = page(() => import('./pages/ChartOfAccountManagement'));
export const PaymentMethodManagement = page(() => import('./pages/PaymentMethodManagement'));
export const CashDrawerManagement = page(() => import('./pages/CashDrawerManagement'));
export const AccountingDashboard = page(() => import('./pages/AccountingDashboard'));
export const PaymentTransactionManagement = page(() => import('./pages/PaymentTransactionManagement'));
export const JournalEntryManagement = page(() => import('./pages/JournalEntryManagement'));
export const PurchaseManagement = page(() => import('./pages/PurchaseManagement'));
export const PurchaseReturnManagement = page(() => import('./pages/PurchaseReturnManagement'));
export const PurchaseOrderManagement = page(() => import('./pages/PurchaseOrderManagement'));
export const SaleManagement = page(() => import('./pages/SaleManagement'));
export const WarrantyLookupPage = page(() => import('./pages/WarrantyLookupPage'));
export const QuotationManagement = page(() => import('./pages/QuotationManagement'));
export const SaleReturnManagement = page(() => import('./pages/SaleReturnManagement'));
export const CustomerAppOrdersPage = page(() => import('./pages/CustomerAppOrdersPage'));
export const CustomerPromoCodesPage = page(() => import('./pages/CustomerPromoCodesPage'));
export const DeliveryChargesPage = page(() => import('./pages/DeliveryChargesPage'));
export const CustomerAppAccountsPage = page(() => import('./pages/CustomerAppAccountsPage'));
export const CustomerShopPage = page(() => import('./pages/customer-shop/CustomerShopPage'));
export const CustomerPasswordResetPage = page(() => import('./pages/customer-shop/CustomerPasswordResetPage'));
export const CreditManagement = page(() => import('./pages/CreditManagement'));
export const StockAdjustmentManagement = page(() => import('./pages/StockAdjustmentManagement'));
export const ExpenseIncomeManagement = page(() => import('./pages/ExpenseIncomeManagement'));
export const ProfitLossReport = page(() => import('./pages/ProfitLossReport'));
export const TrialBalanceReport = page(() => import('./pages/TrialBalanceReport'));
export const BalanceSheetReport = page(() => import('./pages/BalanceSheetReport'));
export const AgingReportPage = page(() => import('./pages/AgingReportPage'));
export const BackupSettings = page(() => import('./pages/BackupSettings'));
export const AdminQueryPage = page(() => import('./pages/AdminQueryPage'));
export const CompanySettingsPage = page(() => import('./pages/CompanySettingsPage'));
export const VoucherSettingsPage = page(() => import('./pages/VoucherSettingsPage'));
export const ServiceManagement = page(() => import('./pages/ServiceManagement'));
export const BookingManagement = page(() => import('./pages/BookingManagement'));
export const ServiceJobManagement = page(() => import('./pages/ServiceJobManagement'));
export const ServiceHelpPage = page(() => import('./pages/ServiceHelpPage'));
export const ShelfLocationManagement = page(() => import('./pages/ShelfLocationManagement'));
export const AuditLogManagement = page(() => import('./pages/AuditLogManagement'));
export const SalesRankingPage = page(() => import('./pages/SalesRankingPage'));
export const DailyReport = page(() => import('./pages/reports/DailyReport'));
export const DailySnapshotReport = page(() => import('./pages/reports/DailySnapshotReport'));
export const SalesSummaryReport = page(() => import('./pages/reports/SalesSummaryReport'));
export const PurchaseSummaryReport = page(() => import('./pages/reports/PurchaseSummaryReport'));
export const ServiceSummaryReport = page(() => import('./pages/reports/ServiceSummaryReport'));
export const StockReport = page(() => import('./pages/reports/StockReport'));
export const StaffPerformanceReport = page(() => import('./pages/reports/StaffPerformanceReport'));
export const CustomerHistoryReport = page(() => import('./pages/reports/CustomerHistoryReport'));
export const SetupWizardPage = page(() => import('./pages/SetupWizardPage'));
export const InitialAdminPage = page(() => import('./pages/InitialAdminPage'));
export const ScanPage = page(() => import('./pages/ScanPage'));
export const OpeningBalancePage = page(() => import('./pages/OpeningBalancePage'));
export const OpeningStockPage = page(() => import('./pages/OpeningStockPage'));
export const AppVersionSettingsPage = page(() => import('./pages/AppVersionSettingsPage'));
export const OutdoorTracking = page(() => import('./pages/OutdoorTracking'));
export const VideoManagement = page(() => import('./pages/VideoManagement'));

const routePreloads = new Map<string, PageLoader>([
  [AppRoute.LOGIN, Login.preload],
  [AppRoute.CUSTOMER_SHOP, CustomerShopPage.preload],
  [AppRoute.CUSTOMER_PASSWORD_RESET, CustomerPasswordResetPage.preload],
  [AppRoute.DASHBOARD, Dashboard.preload],
  [AppRoute.USERS, UserManagement.preload],
  [AppRoute.ROLES, RoleManagement.preload],
  [AppRoute.PERMISSIONS, PermissionManagement.preload],
  [AppRoute.PRODUCTS, ProductManagement.preload],
  [AppRoute.LABEL_DESIGNER, LabelDesigner.preload],
  [AppRoute.STOCK_ADJUSTMENTS, StockAdjustmentManagement.preload],
  [AppRoute.PRODUCT_SERIALS, ProductSerialManagement.preload],
  [AppRoute.BRANDS, BrandManagement.preload],
  [AppRoute.CATEGORIES, CategoryManagement.preload],
  [AppRoute.UNITS, UnitManagement.preload],
  [AppRoute.SUPPLIERS, SupplierManagement.preload],
  [AppRoute.CUSTOMERS, CustomerManagement.preload],
  [AppRoute.STAFF, StaffManagement.preload],
  [AppRoute.COA, ChartOfAccountManagement.preload],
  [AppRoute.PAYMENT_METHODS, PaymentMethodManagement.preload],
  [AppRoute.CASH_DRAWER, CashDrawerManagement.preload],
  [AppRoute.ACCOUNTING_DASHBOARD, AccountingDashboard.preload],
  [AppRoute.JOURNAL_ENTRIES, JournalEntryManagement.preload],
  [AppRoute.EXPENSE_INCOME, ExpenseIncomeManagement.preload],
  [AppRoute.OPENING_BALANCE, OpeningBalancePage.preload],
  [AppRoute.PAYMENT_TRANSACTIONS, PaymentTransactionManagement.preload],
  [AppRoute.OPENING_STOCK, OpeningStockPage.preload],
  [AppRoute.PURCHASES, PurchaseManagement.preload],
  [AppRoute.PURCHASE_ORDERS, PurchaseOrderManagement.preload],
  [AppRoute.PURCHASE_RETURNS, PurchaseReturnManagement.preload],
  [AppRoute.SALES, SaleManagement.preload],
  [AppRoute.WARRANTIES, WarrantyLookupPage.preload],
  [AppRoute.QUOTATIONS, QuotationManagement.preload],
  [AppRoute.CUSTOMER_APP_ORDERS, CustomerAppOrdersPage.preload],
  [AppRoute.CUSTOMER_PROMO_CODES, CustomerPromoCodesPage.preload],
  [AppRoute.DELIVERY_CHARGES, DeliveryChargesPage.preload],
  [AppRoute.CUSTOMER_APP_ACCOUNTS, CustomerAppAccountsPage.preload],
  [AppRoute.SALE_RETURNS, SaleReturnManagement.preload],
  [AppRoute.CREDIT, CreditManagement.preload],
  [AppRoute.PROFIT_LOSS, ProfitLossReport.preload],
  [AppRoute.TRIAL_BALANCE, TrialBalanceReport.preload],
  [AppRoute.BALANCE_SHEET, BalanceSheetReport.preload],
  [AppRoute.AR_AGING, AgingReportPage.preload],
  [AppRoute.AP_AGING, AgingReportPage.preload],
  [AppRoute.BOOKINGS, BookingManagement.preload],
  [AppRoute.SERVICES, ServiceManagement.preload],
  [AppRoute.SERVICE_JOBS, ServiceJobManagement.preload],
  [AppRoute.SERVICE_HELP, ServiceHelpPage.preload],
  [AppRoute.VIDEOS, VideoManagement.preload],
  [AppRoute.SHELF_LOCATIONS, ShelfLocationManagement.preload],
  [AppRoute.OUTDOOR_TRACKING, OutdoorTracking.preload],
  [AppRoute.BACKUP, BackupSettings.preload],
  [AppRoute.ADMIN_QUERIES, AdminQueryPage.preload],
  [AppRoute.COMPANY_SETTINGS, CompanySettingsPage.preload],
  [AppRoute.VOUCHER_SETTINGS, VoucherSettingsPage.preload],
  [AppRoute.APP_VERSION_SETTINGS, AppVersionSettingsPage.preload],
  [AppRoute.AUDIT_LOGS, AuditLogManagement.preload],
  [AppRoute.INCOME_REPORT, DailyReport.preload],
  [AppRoute.DAILY_SNAPSHOT, DailySnapshotReport.preload],
  [AppRoute.SALES_RANKING, SalesRankingPage.preload],
  [AppRoute.SALES_SUMMARY, SalesSummaryReport.preload],
  [AppRoute.PURCHASE_SUMMARY, PurchaseSummaryReport.preload],
  [AppRoute.SERVICE_SUMMARY, ServiceSummaryReport.preload],
  [AppRoute.CUSTOMER_HISTORY, CustomerHistoryReport.preload],
  [AppRoute.STAFF_PERFORMANCE, StaffPerformanceReport.preload],
  [AppRoute.STOCK_REPORT, StockReport.preload]
]);

export const preloadRoute = (path: string): void => {
  const load = routePreloads.get(path);
  if (load) void load().catch(() => undefined);
};
