import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { getCachedCompanySettings, getCompanySettings, CompanySettings } from '../utils/companySettings';
import { Link, useLocation } from 'react-router-dom';
import {
  Activity,
  BarChart2,
  BarChart3,
  Bell,
  Building2,
  CalendarDays,
  Barcode,
  Scale,
  BookMarked,
  BookOpen,
  FileText,
  Box,
  CalendarClock,
  CreditCard,
  Database,
  Hash,
  Key,
  Keyboard,
  Languages,
  Layers,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Package,
  PanelLeftClose,
  PanelLeftOpen,
  RotateCcw,
  Ruler,
  Scissors,
  Settings,
  ClipboardList,
  ShieldCheck,
  ShoppingCart,
  Sun,
  TrendingUp,
  Truck,
  UserCircle,
  UserPlus,
  Users,
  Wallet,
  Wrench,
  X,
  ChevronRight,
  Smartphone
} from 'lucide-react';
import { AppLanguage, AppRoute, AppTheme, User } from '../types';
import { creditAlertService } from '../services/creditalertapiservice';
import { useWebsocket } from '../hooks/useWebsocket';
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts';
import KeyboardShortcutsHelp from './KeyboardShortcutsHelp';

const isAdministrator = (user: User) =>
  user.roles.some(r => r === 'ADMINISTRATOR' || r === 'ROLE_ADMINISTRATOR');

const hasMenuAccess = (user: User, permission?: string): boolean => {
  if (!permission) return true;
  if (isAdministrator(user)) return true;
  return (user.permissions || []).includes(permission);
};

interface LayoutProps {
  children: React.ReactNode;
  user: User;
  onLogout: () => void;
  language: AppLanguage;
  onLanguageChange: (language: AppLanguage) => void;
  theme: AppTheme;
  onThemeChange: (theme: AppTheme) => void;
}

const Layout: React.FC<LayoutProps> = ({
  children,
  user,
  onLogout,
  language,
  onLanguageChange,
  theme,
  onThemeChange
}) => {
  const [isSidebarOpen, setSidebarOpen] = useState(false);
  const [alertCount, setAlertCount] = useState(0);
  const [showShortcuts, setShowShortcuts] = useState(false);
  const location = useLocation();
  const [selectedGroup, setSelectedGroup] = useState<string>('အထွေထွေ');
  const [openGroup, setOpenGroup] = useState<string | null>(null);
  const [isSidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [company, setCompany] = useState<CompanySettings>(() => getCachedCompanySettings());

  useKeyboardShortcuts(() => setShowShortcuts(true));

  useEffect(() => {
    void getCompanySettings().then(setCompany);
    const handler = (e: Event) => setCompany((e as CustomEvent).detail);
    window.addEventListener('company-settings-updated', handler);
    return () => window.removeEventListener('company-settings-updated', handler);
  }, []);

  const menuItems = useMemo(
    () => [
      { name: 'ခွဲခြမ်းစိတ်ဖြာ', icon: <LayoutDashboard size={18} />, path: AppRoute.DASHBOARD, group: 'အထွေထွေ' },
      { name: 'ဝန်ထမ်းများ', icon: <UserCircle size={18} />, path: AppRoute.STAFF, group: 'ဝန်ထမ်းရေးရာ', permission: 'CAN_ACCESS_STAFF_READ' },
      { name: 'ကုန်ပစ္စည်းများ', icon: <Box size={18} />, path: AppRoute.PRODUCTS, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_PRODUCT_READ' },
      { name: 'လေဘယ်ဒီဇိုင်း', icon: <Barcode size={18} />, path: AppRoute.LABEL_DESIGNER, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_PRODUCT_READ' },
      { name: 'ကနဦး ကုန်လက်ကျန်', icon: <Package size={18} />, path: AppRoute.OPENING_STOCK, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_PRODUCT_READ' },
      { name: 'ထုတ်လုပ်ရေး', icon: <Layers size={18} />, path: AppRoute.MANUFACTURING, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_PRODUCT_READ' },
      { name: 'လက်ကျန်ညှိမှု', icon: <Wrench size={18} />, path: AppRoute.STOCK_ADJUSTMENTS, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_STOCK_ADJUSTMENT_READ' },
      { name: 'စီရီနံပါတ်', icon: <Hash size={18} />, path: AppRoute.PRODUCT_SERIALS, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_PRODUCT_SERIAL_READ' },
      { name: 'အမှတ်တံဆိပ်', icon: <Package size={18} />, path: AppRoute.BRANDS, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_BRAND_READ' },
      { name: 'အမျိုးအစား', icon: <Layers size={18} />, path: AppRoute.CATEGORIES, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_CATEGORY_READ' },
      { name: 'ယူနစ်', icon: <Ruler size={18} />, path: AppRoute.UNITS, group: 'ကုန်ပစ္စည်း', permission: 'CAN_ACCESS_UNIT_READ' },
      { name: 'ဝယ်ယူမှု', icon: <ShoppingCart size={18} />, path: AppRoute.PURCHASES, group: 'ဝယ်ယူရေး', permission: 'CAN_ACCESS_PURCHASE_READ' },
      { name: 'ဝယ်ပြန်ပို့', icon: <RotateCcw size={18} />, path: AppRoute.PURCHASE_RETURNS, group: 'ဝယ်ယူရေး', permission: 'CAN_ACCESS_PURCHASE_RETURN_READ' },
      { name: 'ပေးသွင်းသူ', icon: <Truck size={18} />, path: AppRoute.SUPPLIERS, group: 'ဝယ်ယူရေး', permission: 'CAN_ACCESS_SUPPLIER_READ' },
      { name: 'ရောင်းချမှု', icon: <TrendingUp size={18} />, path: AppRoute.SALES, group: 'ရောင်းချရေး', permission: 'CAN_ACCESS_SALE_READ' },
      { name: 'ရောင်းပြန်ပို့', icon: <RotateCcw size={18} />, path: AppRoute.SALE_RETURNS, group: 'ရောင်းချရေး', permission: 'CAN_ACCESS_SALE_RETURN_READ' },
      { name: 'ဖောက်သည်များ', icon: <Users size={18} />, path: AppRoute.CUSTOMERS, group: 'ရောင်းချရေး', permission: 'CAN_ACCESS_CUSTOMER_READ' },
      { name: 'စာရင်းပင်မ', icon: <BarChart3 size={18} />, path: AppRoute.ACCOUNTING_DASHBOARD, group: 'စာရင်းကိုင်', permission: 'CAN_ACCESS_COA_READ' },
      { name: 'စာရင်းဇယား', icon: <BookOpen size={18} />, path: AppRoute.COA, group: 'စာရင်းကိုင်', permission: 'CAN_ACCESS_COA_READ' },
      { name: 'ဂျာနယ်မှတ်တမ်း', icon: <BookMarked size={18} />, path: AppRoute.JOURNAL_ENTRIES, group: 'စာရင်းကိုင်', permission: 'CAN_ACCESS_JOURNAL_READ' },
      { name: 'ဝင်ငွေ/ထွက်ငွေ', icon: <Wallet size={18} />, path: AppRoute.EXPENSE_INCOME, group: 'စာရင်းကိုင်', permission: 'CAN_ACCESS_EXPENSE_READ' },
      { name: 'ငွေပေးချေနည်း', icon: <CreditCard size={18} />, path: AppRoute.PAYMENT_METHODS, group: 'စာရင်းကိုင်', permission: 'CAN_ACCESS_PAYMENT_METHOD_READ' },
      { name: 'ငွေပေးချေမှတ်တမ်း', icon: <Wallet size={18} />, path: AppRoute.PAYMENT_TRANSACTIONS, group: 'စာရင်းကိုင်', permission: 'CAN_ACCESS_COA_READ' },
      { name: 'Opening Balance', icon: <Building2 size={18} />, path: AppRoute.OPENING_BALANCE, group: 'စာရင်းကိုင်', permission: 'CAN_ACCESS_COA_READ' },
      { name: 'ဝင်ငွေနှင့်အမြတ်', icon: <CalendarDays size={18} />, path: AppRoute.INCOME_REPORT, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_REPORT_READ' },
      { name: 'ရောင်းအားအကျဉ်း', icon: <TrendingUp size={18} />, path: AppRoute.SALES_SUMMARY, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_SALE_READ' },
      { name: 'ရောင်းအားအဆင့်', icon: <BarChart3 size={18} />, path: AppRoute.SALES_RANKING, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_SALE_READ' },
      { name: 'ဝယ်ယူမှုအကျဉ်း', icon: <Truck size={18} />, path: AppRoute.PURCHASE_SUMMARY, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_PURCHASE_READ' },
      { name: 'ဝန်ဆောင်မှုအကျဉ်း', icon: <Wrench size={18} />, path: AppRoute.SERVICE_SUMMARY, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_SERVICE_JOB_READ' },
      { name: 'ဝန်ထမ်းစွမ်းဆောင်ရည်', icon: <Activity size={18} />, path: AppRoute.STAFF_PERFORMANCE, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_STAFF_READ' },
      { name: 'လက်ကျန်အစီရင်ခံ', icon: <Package size={18} />, path: AppRoute.STOCK_REPORT, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_PRODUCT_READ' },
      { name: 'အမြတ်/အရှုံး', icon: <FileText size={18} />, path: AppRoute.PROFIT_LOSS, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_REPORT_READ' },
      { name: 'စမ်းသပ်လက်ကျန်', icon: <Scale size={18} />, path: AppRoute.TRIAL_BALANCE, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_REPORT_READ' },
      { name: 'လက်ကျန်ရှင်းတမ်း', icon: <BarChart2 size={18} />, path: AppRoute.BALANCE_SHEET, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_REPORT_READ' },
      { name: 'အရောင်းကြွေးသက်တမ်း', icon: <FileText size={18} />, path: AppRoute.AR_AGING, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_REPORT_READ' },
      { name: 'အဝယ်ကြွေးသက်တမ်း', icon: <FileText size={18} />, path: AppRoute.AP_AGING, group: 'အစီရင်ခံစာ', permission: 'CAN_ACCESS_REPORT_READ' },
      { name: 'အသုံးပြုသူ', icon: <UserPlus size={18} />, path: AppRoute.USERS, group: 'လုံခြုံရေး', permission: 'CAN_ACCESS_USERS_READ' },
      { name: 'ရာထူးအဆင့်', icon: <ShieldCheck size={18} />, path: AppRoute.ROLES, group: 'လုံခြုံရေး', permission: 'CAN_ACCESS_ROLES_READ' },
      { name: 'ခွင့်ပြုချက်', icon: <Key size={18} />, path: AppRoute.PERMISSIONS, group: 'လုံခြုံရေး', permission: 'CAN_ACCESS_PERMISSIONS_READ' },
      { name: 'စစ်ဆေးမှတ်တမ်း', icon: <ClipboardList size={18} />, path: AppRoute.AUDIT_LOGS, group: 'လုံခြုံရေး', permission: 'CAN_ACCESS_AUDIT_LOG_READ' },
      { name: 'ပစ္စည်းလက်ခံ', icon: <CalendarClock size={18} />, path: AppRoute.BOOKINGS, group: 'ဝန်ဆောင်မှု', permission: 'CAN_ACCESS_BOOKING_READ' },
      { name: 'ဝန်ဆောင်မှုလုပ်ငန်း', icon: <Wrench size={18} />, path: AppRoute.SERVICE_JOBS, group: 'ဝန်ဆောင်မှု', permission: 'CAN_ACCESS_SERVICE_JOB_READ' },
      { name: 'ကန့်တည်နေရာ', icon: <Package size={18} />, path: AppRoute.SHELF_LOCATIONS, group: 'ဝန်ဆောင်မှု', permission: 'CAN_ACCESS_SHELF_LOCATION_READ' },
      { name: 'ဝန်ဆောင်မှုစာရင်း', icon: <Scissors size={18} />, path: AppRoute.SERVICES, group: 'ဝန်ဆောင်မှု', permission: 'CAN_ACCESS_SERVICE_READ' },
      { name: 'အရန်သိမ်းဆည်း', icon: <Database size={18} />, path: AppRoute.BACKUP, group: 'ဆက်တင်', permission: 'CAN_ACCESS_BACKUP_SETTINGS_READ' },
      { name: 'ကုမ္ပဏီဆက်တင်', icon: <Settings size={18} />, path: AppRoute.COMPANY_SETTINGS, group: 'ဆက်တင်' },
      { name: 'ပရင့်ဒီဇိုင်း', icon: <FileText size={18} />, path: AppRoute.VOUCHER_SETTINGS, group: 'ဆက်တင်' },
      { name: 'App Version', icon: <Smartphone size={18} />, path: AppRoute.APP_VERSION_SETTINGS, group: 'ဆက်တင်', permission: 'CAN_ACCESS_USERS_READ' }
    ],
    []
  );

  const menuGroups = useMemo(() => {
    const groupOrder = ['အထွေထွေ', 'ဝန်ထမ်းရေးရာ', 'ကုန်ပစ္စည်း', 'ဝယ်ယူရေး', 'ရောင်းချရေး', 'စာရင်းကိုင်', 'အစီရင်ခံစာ', 'ဝန်ဆောင်မှု', 'လုံခြုံရေး', 'ဆက်တင်'];
    const groupIcons: Record<string, React.ReactNode> = {
      'အထွေထွေ': <LayoutDashboard size={18} />,
      'ဝန်ထမ်းရေးရာ': <UserCircle size={18} />,
      'ကုန်ပစ္စည်း': <Box size={18} />,
      'ဝယ်ယူရေး': <ShoppingCart size={18} />,
      'ရောင်းချရေး': <TrendingUp size={18} />,
      'စာရင်းကိုင်': <BookMarked size={18} />,
      'အစီရင်ခံစာ': <BarChart3 size={18} />,
      'ဝန်ဆောင်မှု': <CalendarClock size={18} />,
      'လုံခြုံရေး': <ShieldCheck size={18} />,
      'ဆက်တင်': <Settings size={18} />
    };
    const groupDescriptions: Record<string, string> = {
      'အထွေထွေ': 'စနစ်ခြုံငုံသုံးသပ်ချက်',
      'ဝန်ထမ်းရေးရာ': 'ဝန်ထမ်းနှင့် လူ့စွမ်းအား',
      'ကုန်ပစ္စည်း': 'ပစ္စည်း၊ စီရီနှင့် လက်ကျန်',
      'ဝယ်ယူရေး': 'ဝယ်ယူမှုနှင့် ပေးသွင်းသူ',
      'ရောင်းချရေး': 'ရောင်းချမှု၊ ဖောက်သည်နှင့် အကြွေး',
      'စာရင်းကိုင်': 'ငွေစာရင်းနှင့် ဂျာနယ်',
      'အစီရင်ခံစာ': 'စိတ်ဖြာမှု၊ အဆင့်နှင့် အကျဉ်း',
      'ဝန်ဆောင်မှု': 'ပစ္စည်းလက်ခံနှင့် ဝန်ဆောင်မှု',
      'လုံခြုံရေး': 'အသုံးပြုသူ၊ ရာထူးနှင့် ခွင့်ပြုချက်',
      'ဆက်တင်': 'စနစ်ပြင်ဆင်မှုနှင့် အရန်သိမ်း'
    };

    return groupOrder
      .map((group) => ({
        name: group,
        icon: groupIcons[group],
        description: groupDescriptions[group],
        items: menuItems
          .filter((item) => item.group === group)
          .filter((item) => hasMenuAccess(user, item.permission))
      }))
      .filter((group) => group.items.length > 0);
  }, [menuItems, user]);

  const activeGroupName = useMemo(() => {
    const activeItem = menuItems.find((item) => item.path === location.pathname);
    return activeItem?.group || 'အထွေထွေ';
  }, [location.pathname, menuItems]);

  useEffect(() => {
    setSelectedGroup(activeGroupName);
  }, [activeGroupName]);

  const currentPathName = menuItems.find((item) => item.path === location.pathname)?.name || 'စီမံခန့်ခွဲမှုစနစ်';
  const primaryRole = useMemo(
    () => (user.roles || []).map((role) => role.replace('ROLE_', '')).join(', ') || 'User',
    [user.roles]
  );
  const isDark = theme === 'dark';

  const loadAlertCount = useCallback(async () => {
    try {
      const alerts = await creditAlertService.getAllUnresolved();
      setAlertCount((alerts || []).length);
    } catch {
      setAlertCount(0);
    }
  }, []);

  useEffect(() => {
    void loadAlertCount();
  }, [loadAlertCount]);

  useWebsocket('/topic/credit-alerts', () => {
    void loadAlertCount();
  });

  return (
    <div className={`h-screen overflow-x-hidden flex ${isDark ? 'bg-slate-950 text-slate-100' : 'bg-slate-50 text-slate-800'}`}>
      {/* Sidebar */}
      <div className={`hidden lg:flex flex-col h-screen ${isSidebarCollapsed ? 'w-20' : 'w-72'} ${isDark ? 'bg-slate-900 border-r border-slate-800' : 'bg-white border-r border-slate-200'} shadow-xl overflow-hidden transition-all duration-200`}>
        {/* Header — indigo gradient matching mobile DrawerMenu */}
        <div className="relative bg-indigo-600 overflow-hidden flex-shrink-0">
          {/* Decorative circles */}
          <div className="absolute w-32 h-32 rounded-full bg-white/[0.07] -top-8 -right-6 pointer-events-none" />
          <div className="absolute w-20 h-20 rounded-full bg-white/[0.05] -bottom-5 right-14 pointer-events-none" />

          {isSidebarCollapsed ? (
            <div className="flex flex-col items-center py-4 gap-3 relative z-10">
              {company.logoBase64
                ? <img src={company.logoBase64} alt="logo" className="w-9 h-9 object-contain rounded-lg bg-white/20" />
                : <div className="w-9 h-9 rounded-lg bg-white/20 flex items-center justify-center text-white font-bold text-sm">S</div>
              }
              <button
                type="button"
                onClick={() => { setSidebarCollapsed(false); setOpenGroup(null); }}
                className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 flex items-center justify-center text-white transition-colors"
                title="Expand sidebar"
              >
                <PanelLeftOpen size={16} />
              </button>
            </div>
          ) : (
            <div className="px-5 pt-5 pb-4 relative z-10">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3 min-w-0">
                  {company.logoBase64
                    ? <img src={company.logoBase64} alt="logo" className="w-10 h-10 object-contain rounded-xl bg-white/20 shrink-0" />
                    : <div className="w-10 h-10 rounded-xl bg-white/20 flex items-center justify-center text-white font-bold text-lg shrink-0">S</div>
                  }
                  <div className="min-w-0">
                    <h1 className="text-sm font-extrabold text-white truncate leading-tight">{company.companyName || 'SSPD Manager'}</h1>
                    <p className="text-[11px] text-white/60 mt-0.5 truncate">{company.taglineMm || 'Management System'}</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => { setSidebarCollapsed(true); setOpenGroup(null); }}
                  className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 flex items-center justify-center text-white transition-colors shrink-0 ml-2"
                  title="Collapse sidebar"
                >
                  <PanelLeftClose size={16} />
                </button>
              </div>

            </div>
          )}
        </div>
        
        <nav className={`flex-1 overflow-y-auto custom-scrollbar ${isSidebarCollapsed ? 'p-2' : 'p-3'} space-y-1`}>
          {menuGroups.map((group) => (
            <div key={group.name}>
              <button
                onClick={() => {
                  setSelectedGroup(group.name);
                  if (isSidebarCollapsed) {
                    setSidebarCollapsed(false);
                    setOpenGroup(group.name);
                    return;
                  }
                  setOpenGroup(group.name === openGroup ? null : group.name);
                }}
                className={`w-full flex items-center ${isSidebarCollapsed ? 'justify-center px-0 py-3' : 'justify-between px-4 py-3'} rounded-lg text-sm font-semibold transition-all ${
                  selectedGroup === group.name
                    ? 'bg-indigo-600 text-white'
                    : isDark
                    ? 'text-slate-300 hover:bg-slate-800'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                <span className="flex items-center gap-3">
                  <div className={`w-8 h-8 flex items-center justify-center rounded-lg ${
                    selectedGroup === group.name
                      ? 'bg-white/20'
                      : isDark
                      ? 'bg-slate-800'
                      : 'bg-slate-100'
                  }`}>
                    {group.icon}
                  </div>
                  {!isSidebarCollapsed && group.name}
                </span>
                {!isSidebarCollapsed && (
                  <ChevronRight size={16} className={`transition-transform ${selectedGroup === group.name && openGroup === group.name ? 'rotate-90' : ''}`} />
                )}
              </button>
              
              {!isSidebarCollapsed && openGroup === group.name && (
                <div className="pl-2 mt-1 space-y-1 border-l-2 border-indigo-200 ml-4">
                  {group.items.map((item) => {
                    const isActive = location.pathname === item.path;
                    return (
                      <Link
                        key={item.path}
                        to={item.path}
                        onClick={() => setSidebarOpen(false)}
                        className={`flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm transition-all ${
                          isActive
                            ? 'bg-indigo-50 text-indigo-600 font-semibold'
                            : isDark
                            ? 'text-slate-300 hover:bg-slate-800'
                            : 'text-slate-600 hover:bg-slate-50'
                        }`}
                      >
                        <div className={`w-6 h-6 flex items-center justify-center ${isActive ? 'text-indigo-600' : ''}`}>
                          {item.icon}
                        </div>
                        {item.name}
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </nav>

        <div className={`border-t ${isSidebarCollapsed ? 'p-2' : 'p-4'} ${isDark ? 'border-slate-800 bg-slate-950/50' : 'border-slate-200 bg-slate-50/50'}`}>
          {!isSidebarCollapsed && (
            <div className={`rounded-lg px-4 py-3 ${isDark ? 'bg-slate-800' : 'bg-slate-100'}`}>
              <p className="text-xs uppercase tracking-widest text-slate-500 font-bold">ဝင်ရောက်ထား</p>
              <p className="text-sm font-semibold mt-2 text-slate-800 truncate">{user.username}</p>
              <p className="text-xs uppercase tracking-widest text-slate-500 font-bold mt-1">{primaryRole}</p>
            </div>
          )}
          <button
            onClick={onLogout}
            className={`w-full flex items-center ${isSidebarCollapsed ? 'justify-center px-0 py-3 mt-0' : 'gap-2 px-4 py-2 mt-3'} rounded-lg text-sm font-semibold transition-all ${isDark ? 'text-rose-400 hover:bg-slate-800' : 'text-rose-600 hover:bg-rose-50'}`}
            title="ထွက်မည်"
          >
            <LogOut size={16} />
            {!isSidebarCollapsed && 'ထွက်မည်'}
          </button>
        </div>
      </div>

      {/* Mobile Sidebar */}
      {isSidebarOpen && (
        <>
          <div className="fixed inset-0 bg-slate-900/60 z-40 lg:hidden" onClick={() => setSidebarOpen(false)} />
          <div className={`fixed inset-y-0 left-0 w-72 z-50 lg:hidden ${isDark ? 'bg-slate-900' : 'bg-white'} shadow-xl flex flex-col`}>
            <div className={`px-6 py-4 border-b flex items-center justify-between ${isDark ? 'border-slate-800 bg-slate-950/50' : 'border-slate-200 bg-slate-50/50'}`}>
              <h1 className="text-lg font-bold text-slate-800">မီနူး</h1>
              <button onClick={() => setSidebarOpen(false)} className={`p-2 rounded-lg ${isDark ? 'hover:bg-slate-800' : 'hover:bg-slate-100'}`}>
                <X size={20} />
              </button>
            </div>
            
            <nav className="flex-1 overflow-y-auto custom-scrollbar p-3 space-y-1">
              {menuGroups.map((group) => (
                <div key={group.name}>
                  <button
                    onClick={() => {
                      setSelectedGroup(group.name);
                      setOpenGroup(group.name === openGroup ? null : group.name);
                    }}
                    className={`w-full flex items-center justify-between px-4 py-3 rounded-lg text-sm font-semibold transition-all ${
                      selectedGroup === group.name
                        ? 'bg-indigo-600 text-white'
                        : isDark
                        ? 'text-slate-300 hover:bg-slate-800'
                        : 'text-slate-700 hover:bg-slate-100'
                    }`}
                  >
                    <span className="flex items-center gap-3">
                      <div className={`w-8 h-8 flex items-center justify-center rounded-lg ${
                        selectedGroup === group.name
                          ? 'bg-white/20'
                          : isDark
                          ? 'bg-slate-800'
                          : 'bg-slate-100'
                      }`}>
                        {group.icon}
                      </div>
                      {group.name}
                    </span>
                    <ChevronRight size={16} className={`transition-transform ${selectedGroup === group.name && openGroup === group.name ? 'rotate-90' : ''}`} />
                  </button>
                  
                  {openGroup === group.name && (
                    <div className="pl-2 mt-1 space-y-1 border-l-2 border-indigo-200 ml-4">
                      {group.items.map((item) => {
                        const isActive = location.pathname === item.path;
                        return (
                          <Link
                            key={item.path}
                            to={item.path}
                            onClick={() => setSidebarOpen(false)}
                            className={`flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm transition-all ${
                              isActive
                                ? 'bg-indigo-50 text-indigo-600 font-semibold'
                                : isDark
                                ? 'text-slate-300 hover:bg-slate-800'
                                : 'text-slate-600 hover:bg-slate-50'
                            }`}
                          >
                            <div className={`w-6 h-6 flex items-center justify-center ${isActive ? 'text-indigo-600' : ''}`}>
                              {item.icon}
                            </div>
                            {item.name}
                          </Link>
                        );
                      })}
                    </div>
                  )}
                </div>
              ))}
            </nav>

            <div className={`border-t p-4 ${isDark ? 'border-slate-800 bg-slate-950/50' : 'border-slate-200 bg-slate-50/50'}`}>
              <div className={`rounded-lg px-4 py-3 ${isDark ? 'bg-slate-800' : 'bg-slate-100'}`}>
                <p className="text-xs uppercase tracking-widest text-slate-500 font-bold">ဝင်ရောက်ထား</p>
                <p className="text-sm font-semibold mt-2 text-slate-800 truncate">{user.username}</p>
                <p className="text-xs uppercase tracking-widest text-slate-500 font-bold mt-1">{primaryRole}</p>
              </div>
              <button
                onClick={onLogout}
                className={`w-full flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold mt-3 transition-all ${isDark ? 'text-rose-400 hover:bg-slate-800' : 'text-rose-600 hover:bg-rose-50'}`}
              >
                <LogOut size={16} />
                ထွက်မည်
              </button>
            </div>
          </div>
        </>
      )}

      {/* Main Content */}
      <div className="flex-1 h-screen overflow-x-hidden flex flex-col">
        <div className="h-full overflow-x-hidden">
          <div className="h-full flex flex-col min-w-0 overflow-x-hidden">
            <header className={`min-h-12 shrink-0 backdrop-blur flex items-center justify-between gap-3 px-3 sm:px-4 py-2 sticky top-0 z-30 ${isDark ? 'bg-slate-950/95 border-b border-slate-700' : 'bg-white/95 border-b border-slate-200'}`}>
              <div className="flex min-w-0 items-center gap-2 sm:gap-3">
                <button className="p-1.5 lg:hidden" onClick={() => setSidebarOpen((prev) => !prev)}>
                  <Menu size={18} />
                </button>
                <h1 className="truncate text-sm font-bold text-slate-800">{currentPathName}</h1>
              </div>

              <div className="flex items-center gap-1.5 sm:gap-3">
                <div data-no-translate="true" className={`hidden sm:flex items-center gap-1 rounded-lg p-0.5 ${isDark ? 'border border-slate-700 bg-slate-900' : 'border border-slate-200 bg-slate-50'}`}>
                  <button
                    type="button"
                    onClick={() => onThemeChange(theme === 'dark' ? 'light' : 'dark')}
                    className={`px-2 py-1 text-[10px] font-bold rounded ${isDark ? 'text-slate-300 hover:bg-slate-800' : 'text-slate-600 hover:bg-slate-100'}`}
                    aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
                  >
                    <span className="inline-flex items-center gap-1">
                      {theme === 'dark' ? <Sun size={12} /> : <Moon size={12} />}
                      {theme === 'dark' ? 'Light' : 'Dark'}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => onLanguageChange('en')}
                    className={`px-2 py-1 text-[10px] font-bold rounded ${language === 'en' ? 'bg-indigo-600 text-white' : isDark ? 'text-slate-300 hover:bg-slate-800' : 'text-slate-600 hover:bg-slate-100'}`}
                    aria-label="Switch to English"
                  >
                    <span className="inline-flex items-center gap-1">
                      <Languages size={12} /> ENG
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => onLanguageChange('my')}
                    className={`px-2 py-1 text-[10px] font-bold rounded ${language === 'my' ? 'bg-indigo-600 text-white' : isDark ? 'text-slate-300 hover:bg-slate-800' : 'text-slate-600 hover:bg-slate-100'}`}
                    aria-label="Switch to Myanmar"
                  >
                    MY
                  </button>
                </div>

                <button
                  type="button"
                  onClick={() => onThemeChange(theme === 'dark' ? 'light' : 'dark')}
                  className={`sm:hidden p-1.5 rounded-lg ${isDark ? 'text-slate-300 hover:bg-slate-800' : 'text-slate-600 hover:bg-slate-100'}`}
                  aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
                >
                  {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
                </button>

                <button
                  type="button"
                  onClick={() => setShowShortcuts(true)}
                  className={`hidden sm:flex p-1.5 rounded-lg items-center justify-center ${isDark ? 'text-slate-400 hover:bg-slate-800' : 'text-slate-500 hover:bg-slate-100'}`}
                  aria-label="Keyboard shortcuts"
                  title="Keyboard shortcuts (?)"
                >
                  <Keyboard size={16} />
                </button>

                <button className={`p-1.5 rounded-lg relative ${isDark ? 'text-slate-400 hover:bg-slate-800' : 'text-slate-500 hover:bg-slate-100'}`}>
                  <Bell size={18} />
                  {alertCount > 0 && (
                    <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 bg-rose-500 text-white text-[8px] font-bold rounded-full border border-white flex items-center justify-center leading-none">
                      {alertCount > 9 ? '9+' : alertCount}
                    </span>
                  )}
                </button>

                <div className={`flex items-center gap-2 p-1 pr-2 rounded-lg min-w-0 ${isDark ? 'bg-slate-900 border border-slate-700' : 'bg-slate-50 border border-slate-100'}`}>
                  <div className="w-6 h-6 rounded bg-indigo-600 text-white flex items-center justify-center text-[10px] font-bold">
                    {user.username.charAt(0)}
                  </div>
                  <div className="hidden sm:block text-left">
                    <p className="text-[10px] font-bold text-slate-800 leading-none">{user.username}</p>
                    <p className="text-[8px] text-slate-400 uppercase font-bold mt-0.5">{primaryRole}</p>
                  </div>
                </div>
              </div>
            </header>

            <main className={`min-w-0 px-3 py-3 sm:p-4 pb-32 sm:pb-36 lg:pb-8 ${isDark ? 'bg-slate-950/60' : 'bg-slate-50/50'}`}>
              {children}
            </main>
          </div>
        </div>
      </div>

      {isSidebarOpen && <div className="fixed inset-0 bg-slate-900/60 z-40 lg:hidden" onClick={() => setSidebarOpen(false)} />}

      {showShortcuts && (
        <KeyboardShortcutsHelp isDark={isDark} onClose={() => setShowShortcuts(false)} />
      )}
    </div>
  );
};

export default Layout;
