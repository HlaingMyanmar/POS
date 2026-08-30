
import React, { useState, useEffect } from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import UserManagement from './pages/UserManagement';
import RoleManagement from './pages/RoleManagement';
import PermissionManagement from './pages/PermissionManagement';
import ProductManagement from './pages/ProductManagement';
import LabelDesigner from './pages/LabelDesigner';
import ProductSerialManagement from './pages/ProductSerialManagement';
import BrandManagement from './pages/BrandManagement';
import CategoryManagement from './pages/CategoryManagement';
import UnitManagement from './pages/UnitManagement';
import SupplierManagement from './pages/SupplierManagement';
import CustomerManagement from './pages/CustomerManagement';
import StaffManagement from './pages/StaffManagement';
import ChartOfAccountManagement from './pages/ChartOfAccountManagement';
import PaymentMethodManagement from './pages/PaymentMethodManagement';
import AccountingDashboard from './pages/AccountingDashboard';
import PaymentTransactionManagement from './pages/PaymentTransactionManagement';
import JournalEntryManagement from './pages/JournalEntryManagement';
import PurchaseManagement from './pages/PurchaseManagement';
import PurchaseReturnManagement from './pages/PurchaseReturnManagement';
import PurchaseOrderManagement from './pages/PurchaseOrderManagement';
import WarehouseManagement from './pages/WarehouseManagement';
import SaleManagement from './pages/SaleManagement';
import SaleReturnManagement from './pages/SaleReturnManagement';
import QuotationManagement from './pages/QuotationManagement';
import CreditManagement from './pages/CreditManagement';
import StockAdjustmentManagement from './pages/StockAdjustmentManagement';
import ExpenseIncomeManagement from './pages/ExpenseIncomeManagement';
import ProfitLossReport from './pages/ProfitLossReport';
import TrialBalanceReport from './pages/TrialBalanceReport';
import BalanceSheetReport from './pages/BalanceSheetReport';
import AgingReportPage from './pages/AgingReportPage';
import BackupSettings from './pages/BackupSettings';
import CompanySettingsPage from './pages/CompanySettingsPage';
import VoucherSettingsPage from './pages/VoucherSettingsPage';
import ServiceManagement from './pages/ServiceManagement';
import BookingManagement from './pages/BookingManagement';
import ServiceJobManagement from './pages/ServiceJobManagement';
import ServiceHelpPage from './pages/ServiceHelpPage';
import ShelfLocationManagement from './pages/ShelfLocationManagement';
import AuditLogManagement from './pages/AuditLogManagement';
import SalesRankingPage from './pages/SalesRankingPage';
import DailyReport from './pages/reports/DailyReport';
import DailySnapshotReport from './pages/reports/DailySnapshotReport';
import SalesSummaryReport from './pages/reports/SalesSummaryReport';
import PurchaseSummaryReport from './pages/reports/PurchaseSummaryReport';
import ServiceSummaryReport from './pages/reports/ServiceSummaryReport';
import StockReport from './pages/reports/StockReport';
import StaffPerformanceReport from './pages/reports/StaffPerformanceReport';
import CustomerHistoryReport from './pages/reports/CustomerHistoryReport';
import SetupWizardPage from './pages/SetupWizardPage';
import InitialAdminPage from './pages/InitialAdminPage';
import ScanPage from './pages/ScanPage';
import OpeningBalancePage from './pages/OpeningBalancePage';
import OpeningStockPage from './pages/OpeningStockPage';
import AppVersionSettingsPage from './pages/AppVersionSettingsPage';
import ManufacturingManagement from './pages/ManufacturingManagement';
import OutdoorTracking from './pages/OutdoorTracking';
import VideoManagement from './pages/VideoManagement';
import Layout from './components/Layout';
import { User, AppLanguage, AppRoute, AppTheme } from './types';
import { getFromSession } from './utils/storageHelper';
import { authService, setAccessToken, setupService } from './services/api';
import { getCompanySettings } from './utils/companySettings';
import { applyDocumentLanguage, resolveInitialLanguage, saveLanguagePreference } from './utils/language';
import { initDomLanguageTranslator, setDomLanguage } from './utils/domLanguageTranslator';

type RequiredPermission = string | readonly string[];

const canAccess = (user: User, permission?: RequiredPermission): boolean => {
  if (!permission) return true;
  if (user.roles.some(r => r === 'ADMINISTRATOR' || r === 'ROLE_ADMINISTRATOR')) return true;
  const required = Array.isArray(permission) ? permission : [permission];
  return required.every(item => (user.permissions || []).includes(item));
};

const CUSTOMER_HISTORY_PERMISSIONS = [
  'CAN_ACCESS_CUSTOMER_READ',
  'CAN_ACCESS_SALE_READ',
  'CAN_ACCESS_BOOKING_READ',
  'CAN_ACCESS_SERVICE_JOB_READ'
] as const;

const THEME_STORAGE_KEY = 'sspd_theme';
const INVISIBLE_ROUTE_CHARS = /[\u200B-\u200D\uFEFF]/g;

const resolveInitialTheme = (): AppTheme => {
  if (typeof window === 'undefined') return 'light';
  try {
    const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);
    return savedTheme === 'dark' ? 'dark' : 'light';
  } catch (_error) {
    return 'light';
  }
};

const App: React.FC = () => {
  const [user, setUser]             = useState<User | null>(null);
  const [loading, setLoading]       = useState(true);
  const [needsSetup, setNeedsSetup] = useState(false);
  const [needsInitialAdmin, setNeedsInitialAdmin] = useState(false);
  const [language, setLanguage]     = useState<AppLanguage>(resolveInitialLanguage);
  const [theme, setTheme]           = useState<AppTheme>(resolveInitialTheme);

  useEffect(() => {
    const cleanHash = () => {
      const clean = window.location.hash.replace(INVISIBLE_ROUTE_CHARS, '');
      if (clean !== window.location.hash) {
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}${clean}`);
      }
    };

    cleanHash();
    window.addEventListener('hashchange', cleanHash);
    return () => window.removeEventListener('hashchange', cleanHash);
  }, []);

  const checkSetup = async () => {
    try {
      const status = await setupService.getStatus();
      setNeedsInitialAdmin(!!status.needsInitialAdmin);
      setNeedsSetup(!status.complete);
    } catch {
      setNeedsInitialAdmin(false);
      setNeedsSetup(false);
    }
  };

  useEffect(() => {
    const initializeAuth = async () => {
      let initialAdminNeeded = false;
      try {
        const status = await setupService.getStatus();
        initialAdminNeeded = !!status.needsInitialAdmin;
        setNeedsInitialAdmin(initialAdminNeeded);
        setNeedsSetup(!status.complete);
      } catch {
        setNeedsInitialAdmin(false);
        setNeedsSetup(false);
      }

      const savedUser = getFromSession('sspd_user');
      const refreshToken = getFromSession('sspd_refresh');

      if (!initialAdminNeeded && savedUser && refreshToken) {
        try {
          const res = await authService.refresh();
          if (res.success) {
            setAccessToken(res.data.accessToken);
            setUser(JSON.parse(savedUser));
            void getCompanySettings(true);
            void checkSetup();
          } else {
            throw new Error("Refresh failed");
          }
        } catch (e) {
          authService.logout();
        }
      }
      setLoading(false);
    };

    initializeAuth();
  }, []);

  useEffect(() => {
    applyDocumentLanguage(language);
    setDomLanguage(language);
    saveLanguagePreference(language);
  }, [language]);

  useEffect(() => {
    document.body.classList.remove('theme-light', 'theme-dark');
    document.body.classList.add(theme === 'dark' ? 'theme-dark' : 'theme-light');
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch (_error) {
      // Keep the selected theme for this page when storage is unavailable.
    }
  }, [theme]);

  useEffect(() => {
    const dispose = initDomLanguageTranslator(language);
    return () => dispose();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleLoginSuccess = (userData: User, _token: string) => {
    setUser(userData);
    void getCompanySettings(true);
    void checkSetup();
  };

  const handleLogout = () => {
    authService.logout();
    setUser(null);
  };

  // Permission guard for child routes (no Layout — Layout is the parent)
  const guard = (element: React.ReactNode, perm?: RequiredPermission) => {
    if (!user) return <Navigate to={AppRoute.LOGIN} replace />;
    if (perm && !canAccess(user, perm)) return <Navigate to={AppRoute.DASHBOARD} replace />;
    return <>{element}</>;
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-indigo-600"></div>
      </div>
    );
  }

  if (needsInitialAdmin) {
    return (
      <InitialAdminPage onComplete={() => {
        setNeedsInitialAdmin(false);
      }} />
    );
  }

  if (user && needsSetup) {
    return (
      <SetupWizardPage onComplete={() => {
        setNeedsSetup(false);
        void getCompanySettings(true);
      }} />
    );
  }

  // The Layout element for the parent route — rendered ONCE; child routes fill <Outlet>
  const layoutElement = user
    ? <Layout user={user} onLogout={handleLogout} language={language} onLanguageChange={setLanguage} theme={theme} onThemeChange={setTheme} />
    : <Navigate to={AppRoute.LOGIN} replace />;

  return (
    <HashRouter>
      <Routes>
        <Route path="/scan" element={<ScanPage />} />
        <Route
          path={AppRoute.LOGIN}
          element={!user ? <Login onLoginSuccess={handleLoginSuccess} language={language} onLanguageChange={setLanguage} /> : <Navigate to={AppRoute.DASHBOARD} />}
        />

        {/* ONE Layout wraps all protected pages — keeps components mounted across tab switches */}
        <Route element={layoutElement}>
          <Route path={AppRoute.DASHBOARD}           element={guard(<Dashboard />)} />
          <Route path={AppRoute.USERS}               element={guard(<UserManagement />,              'CAN_ACCESS_USERS_READ')} />
          <Route path={AppRoute.ROLES}               element={guard(<RoleManagement />,              'CAN_ACCESS_ROLES_READ')} />
          <Route path={AppRoute.PERMISSIONS}         element={guard(<PermissionManagement />,        'CAN_ACCESS_PERMISSIONS_READ')} />
          <Route path={AppRoute.PRODUCTS}            element={guard(<ProductManagement />,           'CAN_ACCESS_PRODUCT_READ')} />
          <Route path={AppRoute.PRODUCT_LABELS}      element={<Navigate to={AppRoute.LABEL_DESIGNER} replace />} />
          <Route path={AppRoute.LABEL_DESIGNER}      element={guard(<LabelDesigner />,              'CAN_ACCESS_PRODUCT_READ')} />
          <Route path={AppRoute.STOCK_ADJUSTMENTS}   element={guard(<StockAdjustmentManagement />,  'CAN_ACCESS_STOCK_ADJUSTMENT_READ')} />
          <Route path={AppRoute.PRODUCT_SERIALS}     element={guard(<ProductSerialManagement />,    'CAN_ACCESS_PRODUCT_SERIAL_READ')} />
          <Route path={AppRoute.BRANDS}              element={guard(<BrandManagement />,            'CAN_ACCESS_BRAND_READ')} />
          <Route path={AppRoute.CATEGORIES}          element={guard(<CategoryManagement />,         'CAN_ACCESS_CATEGORY_READ')} />
          <Route path={AppRoute.UNITS}               element={guard(<UnitManagement />,             'CAN_ACCESS_UNIT_READ')} />
          <Route path={AppRoute.SUPPLIERS}           element={guard(<SupplierManagement />,         'CAN_ACCESS_SUPPLIER_READ')} />
          <Route path={AppRoute.CUSTOMERS}           element={guard(<CustomerManagement />,         'CAN_ACCESS_CUSTOMER_READ')} />
          <Route path={AppRoute.STAFF}               element={guard(<StaffManagement />,            'CAN_ACCESS_STAFF_READ')} />
          <Route path={AppRoute.COA}                 element={guard(<ChartOfAccountManagement />,   'CAN_ACCESS_COA_READ')} />
          <Route path={AppRoute.PAYMENT_METHODS}     element={guard(<PaymentMethodManagement />,    'CAN_ACCESS_PAYMENT_METHOD_READ')} />
          <Route path={AppRoute.ACCOUNTING_DASHBOARD} element={guard(<AccountingDashboard />,       'CAN_ACCESS_COA_READ')} />
          <Route path={AppRoute.JOURNAL_ENTRIES}     element={guard(<JournalEntryManagement />,     'CAN_ACCESS_JOURNAL_READ')} />
          <Route path={AppRoute.EXPENSE_INCOME}      element={guard(<ExpenseIncomeManagement
            canBackdateExpense={user ? canAccess(user, 'CAN_ACCESS_EXPENSE_BACKDATE') : false}
            canFutureDateExpense={user ? canAccess(user, 'CAN_ACCESS_EXPENSE_FUTUREDATE') : false}
            canBackdateIncome={user ? canAccess(user, 'CAN_ACCESS_INCOME_BACKDATE') : false}
            canFutureDateIncome={user ? canAccess(user, 'CAN_ACCESS_INCOME_FUTUREDATE') : false}
          />, 'CAN_ACCESS_EXPENSE_READ')} />
          <Route path={AppRoute.OPENING_BALANCE}     element={guard(<OpeningBalancePage />,         'CAN_ACCESS_COA_READ')} />
          <Route path={AppRoute.PAYMENT_TRANSACTIONS} element={guard(<PaymentTransactionManagement />, 'CAN_ACCESS_COA_READ')} />
          <Route path={AppRoute.OPENING_STOCK}       element={guard(<OpeningStockPage />,           'CAN_ACCESS_PRODUCT_READ')} />
          <Route path={AppRoute.MANUFACTURING}       element={guard(<ManufacturingManagement />,    'CAN_ACCESS_PRODUCT_READ')} />
          <Route path={AppRoute.PURCHASES}           element={guard(<PurchaseManagement />,         'CAN_ACCESS_PURCHASE_READ')} />
          <Route path={AppRoute.PURCHASE_ORDERS}     element={guard(<PurchaseOrderManagement />,    'CAN_ACCESS_PURCHASE_ORDER_READ')} />
          <Route path={AppRoute.WAREHOUSES}          element={guard(<WarehouseManagement />,        'CAN_ACCESS_PURCHASE_WAREHOUSE')} />
          <Route path={AppRoute.PURCHASE_RETURNS}    element={guard(<PurchaseReturnManagement />,   'CAN_ACCESS_PURCHASE_RETURN_READ')} />
          <Route path={AppRoute.SALES}               element={guard(<SaleManagement />,             'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.QUOTATIONS}          element={guard(<QuotationManagement />,        'CAN_ACCESS_QUOTATION_READ')} />
          <Route path={AppRoute.SALE_RETURNS}        element={guard(<SaleReturnManagement />,       'CAN_ACCESS_SALE_RETURN_READ')} />
          <Route path={AppRoute.CREDIT}              element={guard(<CreditManagement />,           'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.PROFIT_LOSS}         element={guard(<ProfitLossReport />,           'CAN_ACCESS_REPORT_READ')} />
          <Route path={AppRoute.TRIAL_BALANCE}       element={guard(<TrialBalanceReport />,         'CAN_ACCESS_REPORT_READ')} />
          <Route path={AppRoute.BALANCE_SHEET}       element={guard(<BalanceSheetReport />,         'CAN_ACCESS_REPORT_READ')} />
          <Route path={AppRoute.AR_AGING}            element={guard(<AgingReportPage type="ar" />,  'CAN_ACCESS_REPORT_READ')} />
          <Route path={AppRoute.AP_AGING}            element={guard(<AgingReportPage type="ap" />,  'CAN_ACCESS_REPORT_READ')} />
          <Route path={AppRoute.BOOKINGS}            element={guard(<BookingManagement />,          'CAN_ACCESS_BOOKING_READ')} />
          <Route path={AppRoute.SERVICES}            element={guard(<ServiceManagement />,          'CAN_ACCESS_SERVICE_READ')} />
          <Route path={AppRoute.SERVICE_JOBS}        element={guard(<ServiceJobManagement />,       'CAN_ACCESS_SERVICE_JOB_READ')} />
          <Route path={AppRoute.SERVICE_HELP}        element={guard(<ServiceHelpPage />)} />
          <Route path={AppRoute.VIDEOS}              element={guard(<VideoManagement />,            'CAN_ACCESS_VIDEO_READ')} />
          <Route path={AppRoute.SHELF_LOCATIONS}     element={guard(<ShelfLocationManagement />,    'CAN_ACCESS_SHELF_LOCATION_READ')} />
          <Route path={AppRoute.OUTDOOR_TRACKING}    element={guard(<OutdoorTracking />,            'CAN_ACCESS_TECHNICIAN_LOCATION_READ')} />
          <Route path={AppRoute.BACKUP}              element={guard(<BackupSettings />,             'CAN_ACCESS_BACKUP_SETTINGS_READ')} />
          <Route path={AppRoute.COMPANY_SETTINGS}    element={guard(<CompanySettingsPage />)} />
          <Route path={AppRoute.VOUCHER_SETTINGS}    element={guard(<VoucherSettingsPage />)} />
          <Route path={AppRoute.APP_VERSION_SETTINGS} element={guard(<AppVersionSettingsPage />,   'CAN_ACCESS_USERS_READ')} />
          <Route path={AppRoute.AUDIT_LOGS}          element={guard(<AuditLogManagement />,         'CAN_ACCESS_AUDIT_LOG_READ')} />
          <Route path={AppRoute.INCOME_REPORT}       element={guard(<DailyReport />,                'CAN_ACCESS_REPORT_READ')} />
          <Route path={AppRoute.DAILY_SNAPSHOT}     element={guard(<DailySnapshotReport />,        'CAN_ACCESS_REPORT_READ')} />
          <Route path={AppRoute.SALES_RANKING}       element={guard(<SalesRankingPage />,           'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.SALES_SUMMARY}       element={guard(<SalesSummaryReport />,         'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.PURCHASE_SUMMARY}    element={guard(<PurchaseSummaryReport />,      'CAN_ACCESS_PURCHASE_READ')} />
          <Route path={AppRoute.SERVICE_SUMMARY}     element={guard(<ServiceSummaryReport />,       'CAN_ACCESS_SERVICE_JOB_READ')} />
          <Route path={AppRoute.CUSTOMER_HISTORY}    element={guard(<CustomerHistoryReport />,      CUSTOMER_HISTORY_PERMISSIONS)} />
          <Route path={AppRoute.STAFF_PERFORMANCE}   element={guard(<StaffPerformanceReport />,     'CAN_ACCESS_STAFF_READ')} />
          <Route path={AppRoute.STOCK_REPORT}        element={guard(<StockReport />,                'CAN_ACCESS_PRODUCT_READ')} />
          <Route path="*" element={<Navigate to={AppRoute.DASHBOARD} />} />
        </Route>
      </Routes>
    </HashRouter>
  );
};

export default App;
