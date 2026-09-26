
import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import {
  AccountingDashboard, AdminQueryPage, AgingReportPage, AppVersionSettingsPage,
  AuditLogManagement, BackupSettings, BalanceSheetReport, BookingManagement,
  ServiceBookingSettingsPage,
  BrandManagement, CashDrawerManagement, CategoryManagement, ChartOfAccountManagement,
  CompanySettingsPage, CreditManagement, CustomerAppAccountsPage, CustomerAppOrdersPage, CustomerChatInboxPage,
  CustomerHistoryReport, CustomerManagement, CustomerPasswordResetPage, CustomerPromoCodesPage,
  CustomerShopPage, DailyReport, DailySnapshotReport, Dashboard, DeliveryChargesPage,
  ExpenseIncomeManagement, InitialAdminPage, JournalEntryManagement, LabelDesigner, Login, PublicWebsite,
  OpeningBalancePage, OpeningStockPage, OutdoorTracking, PaymentMethodManagement,
  PaymentTransactionManagement, PermissionManagement, ProductManagement, ProductSerialManagement,
  ProfitLossReport, PurchaseManagement, PurchaseOrderManagement, PurchaseReturnManagement,
  PurchaseSummaryReport, QuotationManagement, RoleManagement, SaleManagement, SaleReturnManagement,
  SalesRankingPage, SalesSummaryReport, ScanPage, ServiceHelpPage, ServiceJobManagement,
  ServiceManagement, ServiceSummaryReport, SetupWizardPage, ShelfLocationManagement,
  StaffManagement, StaffPerformanceReport, StockAdjustmentManagement, StockReport,
  SupplierManagement, TrialBalanceReport, UnitManagement, UserManagement, VideoManagement,
  VoucherSettingsPage, WarrantyLookupPage
} from './routeModules';
import Layout from './components/Layout';
import RouteLoadBoundary from './components/RouteLoadBoundary';
import SessionLockOverlay from './components/SessionLockOverlay';
import { User, AppLanguage, AppRoute, AppTheme } from './types';
import { getFromSession } from './utils/storageHelper';
import {
  authService,
  setAccessToken,
  setupService,
  SESSION_USER_EVENT,
  SESSION_LOCK_EVENT,
  SESSION_UNLOCK_EVENT,
  CLIENT_IDLE_TIMEOUT_MS,
  dispatchSessionLock,
  userFromAuthResponse,
} from './services/api';
import { disconnectWs, ensureWsConnected } from './services/wsClient';
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

const canAccessAny = (user: User, permissions: readonly string[]): boolean => {
  if (user.roles.some(r => r === 'ADMINISTRATOR' || r === 'ROLE_ADMINISTRATOR')) return true;
  return permissions.some(item => (user.permissions || []).includes(item));
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
  const [sessionLocked, setSessionLocked] = useState(false);

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

      // Cookie may outlive tab sessionStorage — try refresh whenever past initial-admin.
      if (!initialAdminNeeded) {
        try {
          const res = await authService.refresh();
          if (res.success) {
            setAccessToken(res.data.accessToken);
            // Always prefer refresh response profile (roles/permissions may have changed).
            setUser(userFromAuthResponse(res.data));
            ensureWsConnected();
            void getCompanySettings(true);
            void checkSetup();
          } else if (savedUser) {
            throw new Error("Refresh failed");
          }
        } catch (e: any) {
          if (e?.error === 'SESSION_IDLE' && savedUser) {
            try {
              setUser(JSON.parse(savedUser));
            } catch {
              // ignore
            }
            setAccessToken(null);
            setSessionLocked(true);
          } else if (savedUser) {
            // No cookie / expired session: stay logged out without noisy logout when nothing was cached.
            await authService.logout({ forceClearLocal: true });
          } else {
            authService.clearLocalSession();
          }
        }
      }
      setLoading(false);
    };

    initializeAuth();
  }, []);

  useEffect(() => {
    const onSessionUser = (event: Event) => {
      const next = (event as CustomEvent<User>).detail;
      if (next?.username) {
        setUser(next);
      }
    };
    window.addEventListener(SESSION_USER_EVENT, onSessionUser);
    return () => window.removeEventListener(SESSION_USER_EVENT, onSessionUser);
  }, []);

  useEffect(() => {
    const onLock = () => setSessionLocked(true);
    const onUnlock = () => setSessionLocked(false);
    window.addEventListener(SESSION_LOCK_EVENT, onLock);
    window.addEventListener(SESSION_UNLOCK_EVENT, onUnlock);
    return () => {
      window.removeEventListener(SESSION_LOCK_EVENT, onLock);
      window.removeEventListener(SESSION_UNLOCK_EVENT, onUnlock);
    };
  }, []);

  // Client-side idle hint (server still enforces on refresh). Absolute timeout is server-only.
  useEffect(() => {
    if (!user || sessionLocked) return;

    let lastActivity = Date.now();
    const mark = () => { lastActivity = Date.now(); };
    const events: (keyof WindowEventMap)[] = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll'];
    events.forEach(evt => window.addEventListener(evt, mark, { passive: true }));
    const onVisibility = () => {
      if (document.visibilityState === 'visible') mark();
    };
    document.addEventListener('visibilitychange', onVisibility);

    const timer = window.setInterval(() => {
      if (Date.now() - lastActivity >= CLIENT_IDLE_TIMEOUT_MS) {
        setAccessToken(null);
        dispatchSessionLock();
      }
    }, 15_000);

    return () => {
      events.forEach(evt => window.removeEventListener(evt, mark));
      document.removeEventListener('visibilitychange', onVisibility);
      window.clearInterval(timer);
    };
  }, [user, sessionLocked]);

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

  useEffect(() => {
    if (user) {
      ensureWsConnected();
    } else {
      disconnectWs();
    }
  }, [user]);

  const handleLoginSuccess = (userData: User, _token: string) => {
    setSessionLocked(false);
    setUser(userData);
    ensureWsConnected();
    void getCompanySettings(true);
    void checkSetup();
  };

  const handleLogout = () => {
    void authService.logout().then((cleared) => {
      if (!cleared) return;
      disconnectWs();
      setSessionLocked(false);
      setUser(null);
    });
  };

  const handleUnlock = async (password: string) => {
    try {
      const res = await authService.unlock(password);
      if (!res.success) {
        throw new Error(res.message || 'Unlock failed');
      }
      setUser(userFromAuthResponse(res.data));
      setSessionLocked(false);
      ensureWsConnected();
    } catch (err: any) {
      const data = err?.response?.data || err;
      if (data?.error === 'SESSION_ABSOLUTE') {
        setSessionLocked(false);
        await authService.logout({ forceClearLocal: true });
        setUser(null);
        window.location.href = AppRoute.LOGIN;
        return;
      }
      throw new Error(data?.message || err?.message || 'Unlock failed');
    }
  };

  // Permission guard for child routes (no Layout — Layout is the parent)
  const guard = (element: React.ReactNode, perm?: RequiredPermission) => {
    if (!user) return <Navigate to={AppRoute.LOGIN} replace />;
    if (perm && !canAccess(user, perm)) return <Navigate to={AppRoute.DASHBOARD} replace />;
    return <RouteLoadBoundary>{element}</RouteLoadBoundary>;
  };

  const guardAny = (element: React.ReactNode, perms: readonly string[]) => {
    if (!user) return <Navigate to={AppRoute.LOGIN} replace />;
    if (!canAccessAny(user, perms)) return <Navigate to={AppRoute.DASHBOARD} replace />;
    return <RouteLoadBoundary>{element}</RouteLoadBoundary>;
  };

  const publicCustomerRoutes = (
    <>
      <Route path="/" element={<RouteLoadBoundary><PublicWebsite /></RouteLoadBoundary>} />
      <Route path="/login" element={<Navigate to={AppRoute.LOGIN} replace />} />
      <Route path="/pos" element={<Navigate to={AppRoute.LOGIN} replace />} />
      <Route path="/courses/*" element={<Navigate to="/" replace />} />
      <Route path="/services" element={<RouteLoadBoundary><PublicWebsite /></RouteLoadBoundary>} />
      <Route path="/book-service" element={<RouteLoadBoundary><PublicWebsite /></RouteLoadBoundary>} />
      <Route path="/tracking" element={<RouteLoadBoundary><PublicWebsite /></RouteLoadBoundary>} />
      <Route path="/student" element={<RouteLoadBoundary><PublicWebsite /></RouteLoadBoundary>} />
      <Route path="/about" element={<RouteLoadBoundary><PublicWebsite /></RouteLoadBoundary>} />
      <Route path="/contact" element={<RouteLoadBoundary><PublicWebsite /></RouteLoadBoundary>} />
      <Route path={AppRoute.CUSTOMER_SHOP} element={<RouteLoadBoundary><CustomerShopPage /></RouteLoadBoundary>} />
      <Route path={AppRoute.CUSTOMER_PASSWORD_RESET} element={<RouteLoadBoundary><CustomerPasswordResetPage /></RouteLoadBoundary>} />
    </>
  );

  if (loading) {
    return (
      <BrowserRouter>
        <Routes>
          {publicCustomerRoutes}
          <Route path="*" element={
            <div className="min-h-screen flex items-center justify-center bg-slate-50">
              <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-indigo-600"></div>
            </div>
          } />
        </Routes>
      </BrowserRouter>
    );
  }

  if (needsInitialAdmin) {
    return (
      <BrowserRouter>
        <Routes>
          {publicCustomerRoutes}
          <Route path="*" element={<RouteLoadBoundary><InitialAdminPage onComplete={() => { setNeedsInitialAdmin(false); }} /></RouteLoadBoundary>} />
        </Routes>
      </BrowserRouter>
    );
  }

  if (user && needsSetup) {
    return (
      <BrowserRouter>
        <Routes>
          {publicCustomerRoutes}
          <Route path="*" element={<RouteLoadBoundary>
            <SetupWizardPage onComplete={() => {
              setNeedsSetup(false);
              void getCompanySettings(true);
            }} />
          </RouteLoadBoundary>} />
        </Routes>
      </BrowserRouter>
    );
  }

  // The Layout element for the parent route — rendered ONCE; child routes fill <Outlet>
  const layoutElement = user
    ? <Layout user={user} onLogout={handleLogout} language={language} onLanguageChange={setLanguage} theme={theme} onThemeChange={setTheme} />
    : <Navigate to={AppRoute.LOGIN} replace />;

  return (
    <>
    <BrowserRouter>
      <Routes>
        {publicCustomerRoutes}
        <Route path="/scan" element={<RouteLoadBoundary><ScanPage /></RouteLoadBoundary>} />
        <Route
          path={AppRoute.LOGIN}
          element={!user
            ? <RouteLoadBoundary><Login onLoginSuccess={handleLoginSuccess} language={language} onLanguageChange={setLanguage} /></RouteLoadBoundary>
            : <Navigate to={AppRoute.DASHBOARD} />}
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
          <Route path={AppRoute.CASH_DRAWER}         element={guard(<CashDrawerManagement />,       'CAN_ACCESS_CASH_DRAWER_READ')} />
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
          <Route path={AppRoute.PURCHASES}           element={guard(<PurchaseManagement />,         'CAN_ACCESS_PURCHASE_READ')} />
          <Route path={AppRoute.PURCHASE_ORDERS}     element={guard(<PurchaseOrderManagement />,    'CAN_ACCESS_PURCHASE_ORDER_READ')} />
          <Route path={AppRoute.PURCHASE_RETURNS}    element={guard(<PurchaseReturnManagement />,   'CAN_ACCESS_PURCHASE_RETURN_READ')} />
          <Route path={AppRoute.SALES}               element={guard(<SaleManagement />,             'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.WARRANTIES}          element={guard(<WarrantyLookupPage />,         'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.QUOTATIONS}          element={guard(<QuotationManagement />,        'CAN_ACCESS_QUOTATION_READ')} />
          <Route path={AppRoute.CUSTOMER_APP_ORDERS} element={guard(<CustomerAppOrdersPage />,      'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.CUSTOMER_PROMO_CODES} element={guard(<CustomerPromoCodesPage />,    'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.DELIVERY_CHARGES} element={guard(<DeliveryChargesPage />,          'CAN_ACCESS_SALE_READ')} />
          <Route path={AppRoute.CUSTOMER_APP_ACCOUNTS} element={guard(<CustomerAppAccountsPage />,  'CAN_ACCESS_CUSTOMER_READ')} />
          <Route path={AppRoute.CUSTOMER_CHAT_INBOX} element={guard(<CustomerChatInboxPage />, 'CAN_ACCESS_CUSTOMER_READ')} />
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
          <Route path={AppRoute.ADMIN_QUERIES}      element={guardAny(<AdminQueryPage />, ['CAN_ACCESS_ADMIN_QUERY_READ', 'CAN_ACCESS_ADMIN_QUERY_WRITE'])} />
          <Route path={AppRoute.COMPANY_SETTINGS}    element={guard(<CompanySettingsPage />)} />
          <Route path={AppRoute.VOUCHER_SETTINGS}    element={guard(<VoucherSettingsPage />)} />
          <Route path={AppRoute.SERVICE_BOOKING_SETTINGS} element={guardAny(<ServiceBookingSettingsPage />, ['CAN_ACCESS_BOOKING_READ', 'CAN_ACCESS_SERVICE_JOB_READ', 'CAN_ACCESS_SERVICE_READ'])} />
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
    </BrowserRouter>
    {user && sessionLocked && (
      <SessionLockOverlay
        userName={user.name || user.username}
        onUnlock={handleUnlock}
        onSignOut={handleLogout}
      />
    )}
    </>
  );
};

export default App;
