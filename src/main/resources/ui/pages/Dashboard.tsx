import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  BadgeCheck,
  BarChart3,
  Boxes,
  ChevronRight,
  CircleDollarSign,
  ClipboardCheck,
  Clock3,
  Factory,
  Filter,
  Layers3,
  PackageCheck,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  ReceiptText,
  Search,
  ShieldCheck,
  ShoppingCart,
  Truck,
} from 'lucide-react';
import { AppRoute } from '../types';

type ErpModuleKey = 'purchase' | 'supplier' | 'stock' | 'sale';
type ModuleTone = 'blue' | 'emerald' | 'amber' | 'rose';

interface ModuleConfig {
  key: ErpModuleKey;
  label: string;
  eyebrow: string;
  icon: React.ReactNode;
  route: AppRoute;
  tone: ModuleTone;
  metric: string;
  metricLabel: string;
  delta: string;
  summary: string;
  action: string;
  rows: Array<{ id: string; title: string; meta: string; amount: string; status: string }>;
  detail: Array<{ label: string; value: string }>;
  pipeline: Array<{ label: string; value: number }>;
}

const toneStyles: Record<ModuleTone, { active: string; soft: string; text: string; bar: string; chip: string; button: string }> = {
  blue: {
    active: 'bg-blue-600 text-white shadow-lg shadow-blue-500/25',
    soft: 'bg-blue-50 border-blue-100',
    text: 'text-blue-700',
    bar: 'bg-blue-500',
    chip: 'bg-blue-50 text-blue-700 border-blue-100',
    button: 'bg-blue-600 hover:bg-blue-700 text-white',
  },
  emerald: {
    active: 'bg-emerald-600 text-white shadow-lg shadow-emerald-500/25',
    soft: 'bg-emerald-50 border-emerald-100',
    text: 'text-emerald-700',
    bar: 'bg-emerald-500',
    chip: 'bg-emerald-50 text-emerald-700 border-emerald-100',
    button: 'bg-emerald-600 hover:bg-emerald-700 text-white',
  },
  amber: {
    active: 'bg-amber-500 text-white shadow-lg shadow-amber-500/25',
    soft: 'bg-amber-50 border-amber-100',
    text: 'text-amber-700',
    bar: 'bg-amber-500',
    chip: 'bg-amber-50 text-amber-700 border-amber-100',
    button: 'bg-amber-500 hover:bg-amber-600 text-white',
  },
  rose: {
    active: 'bg-rose-600 text-white shadow-lg shadow-rose-500/25',
    soft: 'bg-rose-50 border-rose-100',
    text: 'text-rose-700',
    bar: 'bg-rose-500',
    chip: 'bg-rose-50 text-rose-700 border-rose-100',
    button: 'bg-rose-600 hover:bg-rose-700 text-white',
  },
};

const modules: ModuleConfig[] = [
  {
    key: 'purchase',
    label: 'Purchase',
    eyebrow: 'Procurement control',
    icon: <ShoppingCart size={19} />,
    route: AppRoute.PURCHASES,
    tone: 'blue',
    metric: '128.4M',
    metricLabel: 'Open purchase value',
    delta: '+12.8% vs last month',
    summary: 'Purchase orders, goods receipt, and vendor bills are grouped into one flow for quick review.',
    action: 'Create PO',
    rows: [
      { id: 'PO-24061', title: 'Laptop accessories replenishment', meta: 'Awaiting supplier confirmation', amount: '18.6M', status: 'Pending' },
      { id: 'PO-24058', title: 'Mobile display batch', meta: 'Goods receipt due today', amount: '42.0M', status: 'Due' },
      { id: 'PO-24052', title: 'Repair tools restock', meta: 'Invoice matching required', amount: '7.4M', status: 'Review' },
    ],
    detail: [
      { label: 'Open POs', value: '34' },
      { label: 'Pending receipts', value: '9' },
      { label: 'Avg cycle', value: '3.2 days' },
    ],
    pipeline: [
      { label: 'Request', value: 88 },
      { label: 'Ordered', value: 64 },
      { label: 'Received', value: 42 },
    ],
  },
  {
    key: 'supplier',
    label: 'Supplier',
    eyebrow: 'Vendor performance',
    icon: <Truck size={19} />,
    route: AppRoute.SUPPLIERS,
    tone: 'emerald',
    metric: '76',
    metricLabel: 'Active suppliers',
    delta: '94% on-time score',
    summary: 'Supplier risk, outstanding balances, and delivery reliability are visible from the same workspace.',
    action: 'Add supplier',
    rows: [
      { id: 'SUP-018', title: 'Yangon Tech Parts', meta: 'Preferred electronics supplier', amount: '96%', status: 'Trusted' },
      { id: 'SUP-044', title: 'Mandalay Mobile Hub', meta: '2 delayed shipments this month', amount: '82%', status: 'Watch' },
      { id: 'SUP-057', title: 'North Star Tools', meta: 'New onboarding documents ready', amount: 'New', status: 'Draft' },
    ],
    detail: [
      { label: 'Payables', value: '31.8M' },
      { label: 'Due this week', value: '6' },
      { label: 'Quality score', value: '91%' },
    ],
    pipeline: [
      { label: 'Approved', value: 76 },
      { label: 'Review', value: 18 },
      { label: 'Blocked', value: 4 },
    ],
  },
  {
    key: 'stock',
    label: 'Stock',
    eyebrow: 'Inventory health',
    icon: <Boxes size={19} />,
    route: AppRoute.PRODUCTS,
    tone: 'amber',
    metric: '312.7M',
    metricLabel: 'Inventory value',
    delta: '18 low-stock SKUs',
    summary: 'Serial and quantity inventory are monitored together with reorder pressure and movement signals.',
    action: 'Stock adjust',
    rows: [
      { id: 'SKU-8821', title: 'iPhone 13 display assembly', meta: 'Serial stock, 5 available', amount: 'Low', status: 'Reorder' },
      { id: 'SKU-4403', title: 'USB-C charging port', meta: 'Qty stock, 132 available', amount: 'Stable', status: 'OK' },
      { id: 'SKU-1190', title: 'Samsung battery pack', meta: 'Serial stock, 14 available', amount: 'Fast', status: 'Moving' },
    ],
    detail: [
      { label: 'Available units', value: '4,820' },
      { label: 'Serial items', value: '1,248' },
      { label: 'Stock alerts', value: '18' },
    ],
    pipeline: [
      { label: 'Healthy', value: 72 },
      { label: 'Low', value: 18 },
      { label: 'Blocked', value: 6 },
    ],
  },
  {
    key: 'sale',
    label: 'Sale',
    eyebrow: 'Revenue operations',
    icon: <ReceiptText size={19} />,
    route: AppRoute.SALES,
    tone: 'rose',
    metric: '54.2M',
    metricLabel: 'Month-to-date sales',
    delta: '+8.4% conversion',
    summary: 'Sales orders, payment status, and margin signals are arranged for quick cashier and manager decisions.',
    action: 'New sale',
    rows: [
      { id: 'INV-9031', title: 'Aung Mobile Service', meta: 'Paid, delivered today', amount: '2.4M', status: 'Paid' },
      { id: 'INV-9027', title: 'Walk-in customer', meta: 'Partial payment remains', amount: '680K', status: 'Partial' },
      { id: 'INV-9019', title: 'Corporate repair batch', meta: 'Invoice approval pending', amount: '6.2M', status: 'Pending' },
    ],
    detail: [
      { label: 'Invoices', value: '418' },
      { label: 'Gross margin', value: '28%' },
      { label: 'Receivables', value: '17.3M' },
    ],
    pipeline: [
      { label: 'Quoted', value: 58 },
      { label: 'Invoiced', value: 84 },
      { label: 'Paid', value: 69 },
    ],
  },
];

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [activeKey, setActiveKey] = useState<ErpModuleKey>('purchase');
  const [collapsed, setCollapsed] = useState(false);

  const activeModule = useMemo(() => modules.find((module) => module.key === activeKey) || modules[0], [activeKey]);
  const tone = toneStyles[activeModule.tone];
  const maxPipelineValue = Math.max(...activeModule.pipeline.map((item) => item.value), 1);

  return (
    <div className="h-full min-h-[calc(100vh-7rem)] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="flex h-full min-h-[calc(100vh-7rem)] bg-slate-50/80">
        <aside className={(collapsed ? 'w-[76px]' : 'w-[264px]') + ' hidden shrink-0 border-r border-slate-200 bg-white transition-all duration-300 lg:flex lg:flex-col'}>
          <div className="flex h-16 items-center justify-between border-b border-slate-100 px-4">
            {!collapsed && (
              <div className="min-w-0">
                <p className="text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">ERP Admin</p>
                <h2 className="truncate text-sm font-black text-slate-900">Operations Hub</h2>
              </div>
            )}
            <button
              type="button"
              onClick={() => setCollapsed((value) => !value)}
              className="grid h-9 w-9 place-items-center rounded-lg border border-slate-200 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-900"
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            >
              {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
            </button>
          </div>

          <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
            {modules.map((module) => {
              const selected = module.key === activeKey;
              const moduleTone = toneStyles[module.tone];
              const iconClass = selected ? 'bg-white/20 text-white' : moduleTone.soft + ' ' + moduleTone.text;
              return (
                <button
                  key={module.key}
                  type="button"
                  onClick={() => setActiveKey(module.key)}
                  className={'group flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left transition-all duration-200 ' + (selected ? moduleTone.active : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950')}
                  title={collapsed ? module.label : undefined}
                >
                  <span className={'grid h-9 w-9 shrink-0 place-items-center rounded-lg ' + iconClass}>{module.icon}</span>
                  {!collapsed && (
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-black">{module.label}</span>
                      <span className={'block truncate text-[10px] font-bold ' + (selected ? 'text-white/70' : 'text-slate-400')}>{module.eyebrow}</span>
                    </span>
                  )}
                  {!collapsed && <ChevronRight size={15} className={selected ? 'text-white/70' : 'text-slate-300 group-hover:text-slate-500'} />}
                </button>
              );
            })}
          </nav>

          {!collapsed && (
            <div className="border-t border-slate-100 p-4">
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                <div className="flex items-center gap-2 text-[11px] font-black uppercase tracking-widest text-slate-400">
                  <ShieldCheck size={14} /> Live Control
                </div>
                <p className="mt-2 text-xs font-semibold leading-5 text-slate-600">Four core modules synced into one admin workspace.</p>
              </div>
            </div>
          )}
        </aside>

        <main className="min-w-0 flex-1 overflow-y-auto">
          <div className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 px-4 py-3 backdrop-blur lg:hidden">
            <div className="flex gap-2 overflow-x-auto">
              {modules.map((module) => (
                <button
                  key={module.key}
                  type="button"
                  onClick={() => setActiveKey(module.key)}
                  className={'flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-black transition ' + (module.key === activeKey ? toneStyles[module.tone].active : 'border-slate-200 bg-white text-slate-600')}
                >
                  {module.icon}{module.label}
                </button>
              ))}
            </div>
          </div>

          <section key={activeModule.key} className="animate-[erpSlideFade_280ms_ease-out] p-4 sm:p-5 xl:p-6">
            <div className="mb-5 flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
              <div className="min-w-0">
                <div className={'mb-2 inline-flex items-center gap-2 rounded-lg border px-2.5 py-1 text-[11px] font-black uppercase tracking-widest ' + tone.chip}>
                  {activeModule.icon}
                  {activeModule.eyebrow}
                </div>
                <h1 className="text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">{activeModule.label} Workspace</h1>
                <p className="mt-1 max-w-2xl text-sm font-medium leading-6 text-slate-500">{activeModule.summary}</p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <button type="button" className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600 shadow-sm transition hover:bg-slate-50">
                  <Filter size={15} /> Filter
                </button>
                <button type="button" className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-600 shadow-sm transition hover:bg-slate-50">
                  <Search size={15} /> Search
                </button>
                <button type="button" onClick={() => navigate(activeModule.route)} className={'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-xs font-black shadow-sm transition ' + tone.button}>
                  <Plus size={15} /> {activeModule.action}
                </button>
              </div>
            </div>

            <div className="grid gap-4 xl:grid-cols-[1.35fr_0.65fr]">
              <div className="min-w-0 space-y-4">
                <div className="grid gap-3 sm:grid-cols-3">
                  <div className={'rounded-xl border p-4 ' + tone.soft}>
                    <p className="text-[11px] font-black uppercase tracking-widest text-slate-500">{activeModule.metricLabel}</p>
                    <p className={'mt-2 text-3xl font-black tabular-nums ' + tone.text}>{activeModule.metric}</p>
                    <p className="mt-1 text-xs font-bold text-slate-500">{activeModule.delta}</p>
                  </div>
                  {activeModule.detail.map((item, index) => (
                    <div key={item.label} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                      <div className="flex items-center justify-between gap-3">
                        <p className="text-[11px] font-black uppercase tracking-widest text-slate-400">{item.label}</p>
                        {[<ClipboardCheck size={16} />, <Clock3 size={16} />, <BadgeCheck size={16} />][index]}
                      </div>
                      <p className="mt-2 text-2xl font-black text-slate-900 tabular-nums">{item.value}</p>
                    </div>
                  ))}
                </div>

                <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
                  <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                    <div>
                      <h3 className="text-sm font-black text-slate-900">Operational Queue</h3>
                      <p className="text-[11px] font-semibold text-slate-400">Master list for the selected module</p>
                    </div>
                    <button type="button" onClick={() => navigate(activeModule.route)} className={'inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-[11px] font-black ' + tone.chip}>
                      Open module <ArrowRight size={13} />
                    </button>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[720px] text-left">
                      <thead className="bg-slate-50 text-[10px] font-black uppercase tracking-widest text-slate-400">
                        <tr>
                          <th className="px-4 py-3">Reference</th>
                          <th className="px-4 py-3">Record</th>
                          <th className="px-4 py-3">Signal</th>
                          <th className="px-4 py-3 text-right">Value</th>
                          <th className="px-4 py-3 text-right">State</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {activeModule.rows.map((row) => (
                          <tr key={row.id} className="transition hover:bg-slate-50/80">
                            <td className="px-4 py-4 text-xs font-black text-slate-700">{row.id}</td>
                            <td className="px-4 py-4">
                              <p className="text-sm font-black text-slate-900">{row.title}</p>
                              <p className="mt-0.5 text-xs font-medium text-slate-400">{row.meta}</p>
                            </td>
                            <td className="px-4 py-4"><span className={'inline-flex rounded-md border px-2 py-1 text-[10px] font-black uppercase tracking-wide ' + tone.chip}>{row.status}</span></td>
                            <td className="px-4 py-4 text-right text-sm font-black text-slate-900 tabular-nums">{row.amount}</td>
                            <td className="px-4 py-4 text-right"><button className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-900"><ChevronRight size={16} /></button></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              <aside className="min-w-0 space-y-4">
                <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div className="mb-4 flex items-center justify-between">
                    <div>
                      <h3 className="text-sm font-black text-slate-900">Module Flow</h3>
                      <p className="text-[11px] font-semibold text-slate-400">Stage distribution</p>
                    </div>
                    <span className={'grid h-10 w-10 place-items-center rounded-xl border ' + tone.soft + ' ' + tone.text}>{activeModule.icon}</span>
                  </div>
                  <div className="space-y-4">
                    {activeModule.pipeline.map((item) => (
                      <div key={item.label}>
                        <div className="mb-1.5 flex items-center justify-between text-xs font-bold">
                          <span className="text-slate-500">{item.label}</span>
                          <span className="text-slate-900">{item.value}</span>
                        </div>
                        <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                          <div className={'h-full rounded-full transition-all duration-500 ' + tone.bar} style={{ width: String(Math.max(12, (item.value / maxPipelineValue) * 100)) + '%' }} />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                  <h3 className="text-sm font-black text-slate-900">Detail Snapshot</h3>
                  <div className="mt-4 space-y-3">
                    {[
                      { icon: <Layers3 size={16} />, label: 'Document control', value: 'Approval rules active' },
                      { icon: <CircleDollarSign size={16} />, label: 'Financial sync', value: 'Ledger ready' },
                      { icon: <PackageCheck size={16} />, label: 'Inventory link', value: 'Stock movement tracked' },
                      { icon: <Factory size={16} />, label: 'Branch scope', value: 'Main warehouse' },
                    ].map((item) => (
                      <div key={item.label} className="flex items-center gap-3 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2.5">
                        <span className={tone.text}>{item.icon}</span>
                        <div className="min-w-0">
                          <p className="truncate text-xs font-black text-slate-700">{item.label}</p>
                          <p className="truncate text-[11px] font-medium text-slate-400">{item.value}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-xl border border-slate-200 bg-slate-950 p-4 text-white shadow-sm">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-[11px] font-black uppercase tracking-widest text-white/40">Next review</p>
                      <h3 className="mt-1 text-lg font-black">09:30 AM</h3>
                      <p className="mt-1 text-xs font-medium leading-5 text-white/55">Manager approval queue and daily stock exception review.</p>
                    </div>
                    <BarChart3 size={22} className="text-white/50" />
                  </div>
                </div>
              </aside>
            </div>
          </section>
        </main>
      </div>

      <style>{'@keyframes erpSlideFade { from { opacity: 0; transform: translateX(18px); } to { opacity: 1; transform: translateX(0); } }'}</style>
    </div>
  );
};

export default Dashboard;
