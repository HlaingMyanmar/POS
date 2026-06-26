
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
import SaleManagement from './pages/SaleManagement';
import SaleReturnManagement from './pages/SaleReturnManagement';
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
import ShelfLocationManagement from './pages/ShelfLocationManagement';
import AuditLogManagement from './pages/AuditLogManagement';
import SalesRankingPage from './pages/SalesRankingPage';
import DailyReport from './pages/reports/DailyReport';
import SalesSummaryReport from './pages/reports/SalesSummaryReport';
import PurchaseSummaryReport from './pages/reports/PurchaseSummaryReport';
import ServiceSummaryReport from './pages/reports/ServiceSummaryReport';
import StockReport from './pages/reports/StockReport';
import StaffPerformanceReport from './pages/reports/StaffPerformanceReport';
import SetupWizardPage from './pages/SetupWizardPage';
import ScanPage from './pages/ScanPage';
import OpeningBalancePage from './pages/OpeningBalancePage';
import OpeningStockPage from './pages/OpeningStockPage';
import AppVersionSettingsPage from './pages/AppVersionSettingsPage';
import Layout from './components/Layout';
import { User, AppLanguage, AppRoute, AppTheme } from './types';
import { getFromSession } from './utils/storageHelper';
import { authService, setAccessToken, setupService } from './services/api';
import { getCompanySettings } from './utils/companySettings';
import { applyDocumentLanguage, resolveInitialLanguage, saveLanguagePreference } from './utils/language';
import { initDomLanguageTranslator, setDomLanguage } from './utils/domLanguageTranslator';

const canAccess = (user: User, permission?: string): boolean => {
  if (!permission) return true;
  if (user.roles.some(r => r === 'ADMINISTRATOR' || r === 'ROLE_ADMINISTRATOR')) return true;
  return (user.permissions || []).includes(permission);
};

const THEME_STORAGE_KEY = 'sspd_theme';

const resolveInitialTheme = (): AppTheme => {
  if (typeof window === 'undefined') return 'light';
  const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);
  return savedTheme === 'dark' ? 'dark' : 'light';
};

const App: React.FC = () => {
  const [user, setUser]             = useState<User | null>(null);
  const [loading, setLoading]       = useState(true);
  const [needsSetup, setNeedsSetup] = useState(false);
  const [language, setLanguage]     = useState<AppLanguage>(resolveInitialLanguage);
  const [theme, setTheme]           = useState<AppTheme>(resolveInitialTheme);

  const checkSetup = async () => {
    try {
      const status = await setupService.getStatus();
      setNeedsSetup(!status.complete);
    } catch {
      setNeedsSetup(false);
    }
  };

  useEffect(() => {
    const initializeAuth = async () => {
      const savedUser = getFromSession('sspd_user');
      const refreshToken = getFromSession('sspd_refresh');
      
      if (savedUser && refreshToken) {
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
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  useEffect(() => {
    const dispose = initDomLanguageTranslator(language);
    return () => dispose();
    // Keep translator mounted once for live DOM updates across all routes.
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

  const renderProtected = (page: React.ReactNode, requiredPermission?: string) => {
    if (!user) return <Navigate to={AppRoute.LOGIN} />;
    if (!canAccess(user, requiredPermission)) return <Navigate to={AppRoute.DASHBOARD} replace />;

    return (
      <Layout
        user={user}
        onLogout={handleLogout}
        language={language}
        onLanguageChange={setLanguage}
        theme={theme}
        onThemeChange={setTheme}
      >
        {page}
      </Layout>
    );
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-indigo-600"></div>
      </div>
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

  return (
    <HashRouter>
      <Routes>
        <Route path="/scan" element={<ScanPage />} />
        <Route
          path={AppRoute.LOGIN}
          element={!user ? <Login onLoginSuccess={handleLoginSuccess} language={language} onLanguageChange={setLanguage} /> : <Navigate to={AppRoute.DASHBOARD} />}
        />
        <Route 
          path={AppRoute.DASHBOARD} 
          element={renderProtected(<Dashboard />)} 
        />
        <Route
          path={AppRoute.USERS}
          element={renderProtected(<UserManagement />, 'CAN_ACCESS_USERS_READ')}
        />
        <Route
          path={AppRoute.ROLES}
          element={renderProtected(<RoleManagement />, 'CAN_ACCESS_ROLES_READ')}
        />
        <Route
          path={AppRoute.PERMISSIONS}
          element={renderProtected(<PermissionManagement />, 'CAN_ACCESS_PERMISSIONS_READ')}
        />
        <Route
          path={AppRoute.PRODUCTS}
          element={renderProtected(<ProductManagement />, 'CAN_ACCESS_PRODUCT_READ')}
        />
        <Route
          path={AppRoute.PRODUCT_LABELS}
          element={<Navigate to={AppRoute.LABEL_DESIGNER} replace />}
        />
        <Route
          path={AppRoute.LABEL_DESIGNER}
          element={renderProtected(<LabelDesigner />, 'CAN_ACCESS_PRODUCT_READ')}
        />
        <Route
          path={AppRoute.STOCK_ADJUSTMENTS}
          element={renderProtected(<StockAdjustmentManagement />, 'CAN_ACCESS_STOCK_ADJUSTMENT_READ')}
        />
        <Route
          path={AppRoute.PRODUCT_SERIALS}
          element={renderProtected(<ProductSerialManagement />, 'CAN_ACCESS_PRODUCT_SERIAL_READ')}
        />
        <Route
          path={AppRoute.BRANDS}
          element={renderProtected(<BrandManagement />, 'CAN_ACCESS_BRAND_READ')}
        />
        <Route
          path={AppRoute.CATEGORIES}
          element={renderProtected(<CategoryManagement />, 'CAN_ACCESS_CATEGORY_READ')}
        />
        <Route
          path={AppRoute.UNITS}
          element={renderProtected(<UnitManagement />, 'CAN_ACCESS_UNIT_READ')}
        />
        <Route
          path={AppRoute.SUPPLIERS}
          element={renderProtected(<SupplierManagement />, 'CAN_ACCESS_SUPPLIER_READ')}
        />
        <Route
          path={AppRoute.CUSTOMERS}
          element={renderProtected(<CustomerManagement />, 'CAN_ACCESS_CUSTOMER_READ')}
        />
        <Route
          path={AppRoute.STAFF}
          element={renderProtected(<StaffManagement />, 'CAN_ACCESS_STAFF_READ')}
        />
        <Route
          path={AppRoute.COA}
          element={renderProtected(<ChartOfAccountManagement />, 'CAN_ACCESS_COA_READ')}
        />
        <Route
          path={AppRoute.PAYMENT_METHODS}
          element={renderProtected(<PaymentMethodManagement />, 'CAN_ACCESS_PAYMENT_METHOD_READ')}
        />
        <Route
          path={AppRoute.ACCOUNTING_DASHBOARD}
          element={renderProtected(<AccountingDashboard />, 'CAN_ACCESS_COA_READ')}
        />
        <Route
          path={AppRoute.JOURNAL_ENTRIES}
          element={renderProtected(<JournalEntryManagement />, 'CAN_ACCESS_JOURNAL_READ')}
        />
        <Route
          path={AppRoute.EXPENSE_INCOME}
          element={renderProtected(<ExpenseIncomeManagement />, 'CAN_ACCESS_EXPENSE_READ')}
        />
        <Route
          path={AppRoute.OPENING_BALANCE}
          element={renderProtected(<OpeningBalancePage />, 'CAN_ACCESS_COA_READ')}
        />
        <Route
          path={AppRoute.PAYMENT_TRANSACTIONS}
          element={renderProtected(<PaymentTransactionManagement />, 'CAN_ACCESS_COA_READ')}
        />
        <Route
          path={AppRoute.OPENING_STOCK}
          element={renderProtected(<OpeningStockPage />, 'CAN_ACCESS_PRODUCT_READ')}
        />
        <Route
          path={AppRoute.PURCHASES}
          element={renderProtected(<PurchaseManagement />, 'CAN_ACCESS_PURCHASE_READ')}
        />
        <Route
          path={AppRoute.PURCHASE_RETURNS}
          element={renderProtected(<PurchaseReturnManagement />, 'CAN_ACCESS_PURCHASE_RETURN_READ')}
        />
        <Route
          path={AppRoute.SALES}
          element={renderProtected(<SaleManagement />, 'CAN_ACCESS_SALE_READ')}
        />
        <Route
          path={AppRoute.SALE_RETURNS}
          element={renderProtected(<SaleReturnManagement />, 'CAN_ACCESS_SALE_RETURN_READ')}
        />
        <Route
          path={AppRoute.CREDIT}
          element={<Navigate to={AppRoute.CUSTOMERS} replace />}
        />
        <Route
          path={AppRoute.PROFIT_LOSS}
          element={renderProtected(<ProfitLossReport />, 'CAN_ACCESS_REPORT_READ')}
        />
        <Route
          path={AppRoute.TRIAL_BALANCE}
          element={renderProtected(<TrialBalanceReport />, 'CAN_ACCESS_REPORT_READ')}
        />
        <Route
          path={AppRoute.BALANCE_SHEET}
          element={renderProtected(<BalanceSheetReport />, 'CAN_ACCESS_REPORT_READ')}
        />
        <Route
          path={AppRoute.AR_AGING}
          element={renderProtected(<AgingReportPage type="ar" />, 'CAN_ACCESS_REPORT_READ')}
        />
        <Route
          path={AppRoute.AP_AGING}
          element={renderProtected(<AgingReportPage type="ap" />, 'CAN_ACCESS_REPORT_READ')}
        />
        <Route
          path={AppRoute.BOOKINGS}
          element={renderProtected(<BookingManagement />, 'CAN_ACCESS_BOOKING_READ')}
        />
        <Route
          path={AppRoute.SERVICES}
          element={renderProtected(<ServiceManagement />, 'CAN_ACCESS_SERVICE_READ')}
        />
        <Route
          path={AppRoute.SERVICE_JOBS}
          element={renderProtected(<ServiceJobManagement />, 'CAN_ACCESS_SERVICE_JOB_READ')}
        />
        <Route
          path={AppRoute.SHELF_LOCATIONS}
          element={renderProtected(<ShelfLocationManagement />, 'CAN_ACCESS_SHELF_LOCATION_READ')}
        />
        <Route
          path={AppRoute.BACKUP}
          element={renderProtected(<BackupSettings />, 'CAN_ACCESS_BACKUP_SETTINGS_READ')}
        />
        <Route
          path={AppRoute.COMPANY_SETTINGS}
          element={renderProtected(<CompanySettingsPage />)}
        />
        <Route
          path={AppRoute.VOUCHER_SETTINGS}
          element={renderProtected(<VoucherSettingsPage />)}
        />
        <Route
          path={AppRoute.APP_VERSION_SETTINGS}
          element={renderProtected(<AppVersionSettingsPage />, 'CAN_ACCESS_USERS_READ')}
        />
        <Route
          path={AppRoute.AUDIT_LOGS}
          element={renderProtected(<AuditLogManagement />, 'CAN_ACCESS_AUDIT_LOG_READ')}
        />
        <Route path={AppRoute.INCOME_REPORT}    element={renderProtected(<DailyReport />,            'CAN_ACCESS_REPORT_READ')}   />
        <Route path={AppRoute.SALES_RANKING}    element={renderProtected(<SalesRankingPage />,       'CAN_ACCESS_SALE_READ')}     />
        <Route path={AppRoute.SALES_SUMMARY}    element={renderProtected(<SalesSummaryReport />,    'CAN_ACCESS_SALE_READ')}     />
        <Route path={AppRoute.PURCHASE_SUMMARY} element={renderProtected(<PurchaseSummaryReport />, 'CAN_ACCESS_PURCHASE_READ')} />
        <Route path={AppRoute.SERVICE_SUMMARY}    element={renderProtected(<ServiceSummaryReport />,    'CAN_ACCESS_SERVICE_JOB_READ')} />
        <Route path={AppRoute.STAFF_PERFORMANCE}  element={renderProtected(<StaffPerformanceReport />, 'CAN_ACCESS_STAFF_READ')} />
        <Route path={AppRoute.STOCK_REPORT}     element={renderProtected(<StockReport />,           'CAN_ACCESS_PRODUCT_READ')}  />
        <Route path="*" element={<Navigate to={AppRoute.DASHBOARD} />} />
      </Routes>
    </HashRouter>
  );
};

export default App;
