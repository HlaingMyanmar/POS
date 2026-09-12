import { CheckoutFlow, DeliverySelection } from './CheckoutFlow';
import { CustomerOrderCard } from './CustomerOrderPayment';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  History, Loader2, LogOut, Package, ShoppingCart, UserRound, Search, Wrench,
} from 'lucide-react';
import { AppRoute } from '../../types';
import { resolveAssetUrl } from '../../services/api';
import { useCustomerPortalOrderSync } from '../../hooks/useCustomerPortalOrderSync';
import {
  CartLine,
  CatalogProduct,
  CatalogOption,
  CatalogService,
  CustomerBooking,
  CustomerJob,
  CustomerOrder,
  CustomerPurchase,
  CustomerSession,
  clearCustomerSession,
  customerPortalService,
  getCustomerSession,
  loadCart,
  saveCart,
  saveCustomerSession,
} from '../../services/customerPortalApi';

type Tab = 'products' | 'services' | 'cart' | 'history' | 'account';

const money = (n?: number | null) => `${Number(n || 0).toLocaleString()} Ks`;

const applyAuth = (data: CustomerSession, fallback?: Partial<CustomerSession>): CustomerSession => {
  const session: CustomerSession = {
    accessToken: data.accessToken,
    customerId: data.customerId,
    name: data.name || fallback?.name,
    phone: data.phone || fallback?.phone,
    address: data.address || fallback?.address,
    email: data.email || fallback?.email,
    needsProfile: data.needsProfile,
  };
  saveCustomerSession(session);
  return session;
};

const CustomerShopPage: React.FC = () => {
  const [shopName, setShopName] = useState('SSPD');
  const [pickupDepositPercent, setPickupDepositPercent] = useState(30);
  const [deliveryEnabled, setDeliveryEnabled] = useState(true);
  const [session, setSession] = useState<CustomerSession | null>(() => getCustomerSession());
  const [tab, setTab] = useState<Tab>('products');
  const [products, setProducts] = useState<CatalogProduct[]>([]);
  const [services, setServices] = useState<CatalogService[]>([]);
  const [orders, setOrders] = useState<CustomerOrder[]>([]);
  const [bookings, setBookings] = useState<CustomerBooking[]>([]);
  const [purchases, setPurchases] = useState<CustomerPurchase[]>([]);
  const [jobs, setJobs] = useState<CustomerJob[]>([]);
  const [cart, setCart] = useState<CartLine[]>(() => loadCart());
  const [loading, setLoading] = useState(false);
  const [mineLoading, setMineLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [categories, setCategories] = useState<CatalogOption[]>([]);
  const [brands, setBrands] = useState<CatalogOption[]>([]);
  const [categoryId, setCategoryId] = useState('');
  const [brandId, setBrandId] = useState('');
  const [sort, setSort] = useState('name');
  const [page, setPage] = useState(0);
  const [totalProducts, setTotalProducts] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [catalogRetry, setCatalogRetry] = useState(0);
  const [selectedService, setSelectedService] = useState<CatalogService | null>(null);
  const [device, setDevice] = useState('');
  const [problem, setProblem] = useState('');
  const [orderNote, setOrderNote] = useState('');
  const [delivery, setDelivery] = useState<DeliverySelection>({
    orderType: 'PICKUP',
    deliveryLocationMode: 'PROFILE',
    paymentChoice: 'TRANSFER',
  });
  const [placing, setPlacing] = useState(false);
  const checkoutAttempt = useRef<{ stamp: string; key: string } | null>(null);

  const persistCart = (next: CartLine[]) => {
    setCart(next);
    saveCart(next);
  };

  useEffect(() => {
    void customerPortalService.services().then(r => setServices(r.data || [])).catch(() => setMessage('Service စာရင်း ဖတ်မရပါ'));
    void customerPortalService.categories().then(r => setCategories(r.data || [])).catch(() => setMessage('Category စာရင်း ဖတ်မရပါ'));
    void customerPortalService.brands().then(r => setBrands(r.data || [])).catch(() => setMessage('Brand စာရင်း ဖတ်မရပါ'));
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setCatalogError(null);
    setProducts([]);
    setHasNext(false);
    const timer = window.setTimeout(async () => {
      try {
        const response = await customerPortalService.productPage({
          page, size: 24, q: query.trim() || undefined,
          categoryId: categoryId ? Number(categoryId) : undefined,
          brandId: brandId ? Number(brandId) : undefined, sort,
        }, controller.signal);
        if (!active) return;
        if (!response.success || !response.data) throw new Error(response.message || 'Catalog ဖတ်မရပါ');
        setProducts(response.data.content);
        setTotalProducts(response.data.totalElements);
        setHasNext(response.data.hasNext);
      } catch (error: any) {
        if (active) setCatalogError(error?.message || 'Catalog ဖတ်မရပါ');
      } finally {
        if (active) setLoading(false);
      }
    }, 300);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [query, categoryId, brandId, sort, page, catalogRetry]);

  const loadMine = useCallback(async (silent = false) => {
    if (!getCustomerSession()?.accessToken) return;
    if (!silent) setMineLoading(true);
    const results = await Promise.allSettled([
      customerPortalService.myOrders(),
      customerPortalService.myBookings(),
      customerPortalService.myPurchases(),
      customerPortalService.myJobs(),
    ]);
    const [o, b, p, j] = results;
    if (o.status === 'fulfilled') setOrders(o.value.data || []);
    if (b.status === 'fulfilled') setBookings(b.value.data || []);
    if (p.status === 'fulfilled') setPurchases(p.value.data || []);
    if (j.status === 'fulfilled') setJobs(j.value.data || []);
    const failed = results.filter(result => result.status === 'rejected').length;
    if (failed) setMessage(`မှတ်တမ်း ${failed} မျိုး ဖတ်မရပါ — ပြန်ကြိုးစားမည်`);
    if (!silent) setMineLoading(false);
  }, []);

  useEffect(() => {
    customerPortalService.branding()
      .then(res => {
        if (res.success && res.data) {
          if (res.data.companyName) setShopName(res.data.companyName);
          const pct = Number(res.data.pickupDepositPercent);
          if (pct >= 1 && pct <= 100) setPickupDepositPercent(pct);
          const enabled = res.data.deliveryEnabled !== false;
          setDeliveryEnabled(enabled);
          if (!enabled) {
            setDelivery(prev => ({ ...prev, orderType: 'PICKUP', paymentChoice: 'TRANSFER' }));
          }
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (session?.accessToken) void loadMine();
  }, [session?.accessToken, loadMine]);

  useEffect(() => {
    const ended = () => {
      setSession(null);
      setMessage('Session ကုန်သွားပါသည် — ပြန်ဝင်ပါ');
      setTab('account');
    };
    window.addEventListener('customer-session-ended', ended);
    return () => window.removeEventListener('customer-session-ended', ended);
  }, []);

  const filtered = products;
  useCustomerPortalOrderSync(!!session?.accessToken, () => loadMine(true));
  useEffect(() => {
    if (!session?.accessToken) return;
    const waiting = orders.some((o) => {
      const ship = (o.shippingState || '').toUpperCase();
      const pay = (o.paymentState || 'NONE').toUpperCase();
      return (o.orderType === 'DELIVERY' && ['AWAITING_SHOP', 'NEEDS_QUOTE'].includes(ship))
        || ['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(pay)
        || (pay === 'NONE' && o.status === 'PENDING');
    });
    if (!waiting) return;
    const t = window.setInterval(() => { void loadMine(true); }, 2500);
    return () => window.clearInterval(t);
  }, [session?.accessToken, orders, loadMine]);
  const cartCount = cart.reduce((n, l) => n + l.qty, 0);

  const addToCart = (product: CatalogProduct) => {
    if (product.inStock === false) {
      setMessage('ဤပစ္စည်း ကုန်နေပါသည်');
      return;
    }
    const existing = cart.find(l => l.product.id === product.id);
    persistCart(existing
      ? cart.map(l => l.product.id === product.id ? { ...l, qty: l.qty + 1 } : l)
      : [...cart, { product, qty: 1 }]);
    setMessage(`${product.name} ခြင်းထည့်ပြီး`);
  };

  const checkout = async (promoCode?: string) => {
    if (placing) return;
    if (!session) { setTab('account'); setMessage('အရင် ဝင်ပါ'); return; }
    if (session.needsProfile) { setTab('account'); setMessage('ဖုန်းနှင့် လိပ်စာ ဖြည့်ပါ'); return; }
    if (!cart.length) { setMessage('ခြင်းတောင်း ဗလာဖြစ်နေသည်'); return; }
    if (delivery.orderType === 'DELIVERY') {
      if (!deliveryEnabled) { setMessage('သွားပို့ ယာယီပိတ်ထားသည်။ ဆိုင်မှာလာယူ ရွေးပါ'); return; }
      if (!delivery.requestedDeliveryAt) { setMessage('ပို့မည့် ရက်နှင့် အချိန် ရွေးပါ'); return; }
      if (new Date(delivery.requestedDeliveryAt).getTime() <= Date.now()) {
        setMessage('ပို့မည့်အချိန်သည် ယခုအချိန် နောက်မှ ဖြစ်ရမည်'); return;
      }
      if (!delivery.wardId) { setMessage('ပို့မည့် ရပ်ကွက် ရွေးပါ'); return; }
      if (delivery.deliveryLocationMode === 'PROFILE' && !session.address) {
        setTab('account'); setMessage('Profile လိပ်စာ ဖြည့်ပါ'); return;
      }
      if (delivery.deliveryLocationMode === 'OTHER' && (!delivery.deliveryPhone?.trim() || !delivery.deliveryAddress?.trim())) {
        setMessage('ပို့မည့် ဖုန်းနှင့် လိပ်စာ ထည့်ပါ'); return;
      }
    }
    const stamp = cart.map(l => `${l.product.id}:${l.qty}`).join('|');
    const key = checkoutAttempt.current?.stamp === stamp
      ? checkoutAttempt.current.key
      : (typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`);
    checkoutAttempt.current = { stamp, key };
    setPlacing(true);
    try {
      const pickup = delivery.orderType === 'PICKUP';
      const res = await customerPortalService.placeOrder({
        orderType: delivery.orderType,
        paymentChoice: pickup ? 'TRANSFER' : undefined,
        requestedDeliveryAt: pickup ? undefined : delivery.requestedDeliveryAt,
        wardId: pickup ? undefined : delivery.wardId,
        townshipId: pickup ? undefined : delivery.townshipId,
        deliveryLocationMode: pickup ? undefined : delivery.deliveryLocationMode,
        deliveryAddress: !pickup && delivery.deliveryLocationMode === 'OTHER' ? delivery.deliveryAddress?.trim() : undefined,
        deliveryPhone: !pickup && delivery.deliveryLocationMode === 'OTHER' ? delivery.deliveryPhone?.trim() : undefined,
        note: orderNote.trim() || undefined,
        idempotencyKey: key,
        lines: cart.map(l => ({ productId: l.product.id, qty: l.qty })),
        promoCode: promoCode || undefined,
      });
      if (res.success) {
        checkoutAttempt.current = null;
        persistCart([]);
        setOrderNote('');
        setMessage(pickup
          ? 'အော်ဒါ တင်ပြီးပါပြီ — စရံကြိုလွှဲပါ'
          : 'အော်ဒါ တင်ပြီးပါပြီ — ဆိုင်က ပို့ချိန်နှင့် ပို့ပုံ အတည်ပြုပါမည်');
        setTab('history');
        await loadMine();
      }
    } catch (e: any) {
      setMessage(e?.message || 'အော်ဒါ မတင်နိုင်ပါ');
    } finally {
      setPlacing(false);
    }
  };

  const requestService = async () => {
    if (!session) { setTab('account'); setMessage('အရင် ဝင်ပါ'); return; }
    if (session.needsProfile) { setTab('account'); setMessage('ဖုန်းနှင့် လိပ်စာ ဖြည့်ပါ'); return; }
    if (!selectedService && !problem.trim()) {
      setMessage('ဝန်ဆောင်မှု သို့မဟုတ် ပြဿနာ ရွေးပါ');
      return;
    }
    try {
      const res = await customerPortalService.requestService({
        serviceName: selectedService?.name,
        deviceName: device.trim() || undefined,
        problem: problem.trim() || undefined,
      });
      if (res.success) {
        setProblem('');
        setDevice('');
        setMessage('Service ခေါ်ပြီးပါပြီ');
        setTab('history');
        await loadMine();
      }
    } catch (e: any) {
      setMessage(e?.message || 'Service မခေါ်နိုင်ပါ');
    }
  };

  const logout = () => {
    clearCustomerSession();
    persistCart([]);
    setSession(null);
    setOrders([]);
    setBookings([]);
    setTab('products');
  };

  const shopTitle = shopName;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      <header className="sticky top-0 z-20 border-b bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
          <div>
            <div className="text-sm font-black tracking-tight">{shopTitle}</div>
            <div className="text-[11px] text-slate-500">ဖောက်သည်ဆိုင် — ပစ္စည်းမှာ / Service ခေါ်ရန်</div>
          </div>
          <Link to={AppRoute.LOGIN} className="text-[11px] font-semibold text-slate-400 hover:text-slate-600">ဆိုင်ဝန်ထမ်း</Link>
        </div>
      </header>

      {message && (
        <div className="mx-auto max-w-5xl px-4 pt-3">
          <button type="button" onClick={() => setMessage(null)} className="w-full rounded-xl border border-indigo-100 bg-indigo-50 px-3 py-2 text-left text-sm text-indigo-800">
            {message}
          </button>
        </div>
      )}

      <main className="mx-auto max-w-5xl px-4 py-4 pb-24">
        {tab === 'products' && (
          <section className="space-y-3">
            <div className="relative">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input value={query} maxLength={120} onChange={e => { setQuery(e.target.value); setPage(0); }} placeholder="ပစ္စည်းရှာမည်" className="w-full rounded-xl border bg-white py-2.5 pl-9 pr-3 text-sm" />
            </div>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
              <select aria-label="Category" value={categoryId} onChange={e => { setCategoryId(e.target.value); setPage(0); }} className="rounded-xl border p-2">
                <option value="">Category အားလုံး</option>
                {categories.map(c => <option key={c.id} value={c.id}>{c.parentName ? c.parentName + ' / ' : ''}{c.name}</option>)}
              </select>
              <select aria-label="Brand" value={brandId} onChange={e => { setBrandId(e.target.value); setPage(0); }} className="rounded-xl border p-2">
                <option value="">Brand အားလုံး</option>
                {brands.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
              <select aria-label="Sort products" value={sort} onChange={e => { setSort(e.target.value); setPage(0); }} className="rounded-xl border p-2">
                <option value="name">အမည်အလိုက်</option><option value="price_asc">ဈေးနည်းမှများ</option>
                <option value="price_desc">ဈေးများမှနည်း</option><option value="newest">အသစ်တင်ထားသော</option>
              </select>
            </div>
            {catalogError && <div role="alert" className="rounded-xl border p-3 text-rose-600">{catalogError} <button type="button" onClick={() => setCatalogRetry(n => n + 1)} className="underline">ပြန်ကြိုးစားမည်</button></div>}
            {loading && <div className="flex justify-center py-10 text-slate-400"><Loader2 className="animate-spin" /></div>}
            <div className="grid gap-3 sm:grid-cols-2">
              {filtered.map(p => {
                const photo = resolveAssetUrl(p.thumbnailUrl || p.photoUrls?.[0]);
                return (
                  <article key={p.id} className="overflow-hidden rounded-2xl border bg-white">
                    {photo ? <img src={photo} alt={p.name} loading="lazy" decoding="async" width={480} height={288} className="h-36 w-full object-cover bg-slate-100" /> : <div className="flex h-28 items-center justify-center bg-slate-100 text-slate-300"><Package size={28} /></div>}
                    <div className="space-y-1 p-3">
                      <div className="font-bold">{p.name}</div>
                      <div className="text-[11px] text-slate-500">{[p.brandName, p.categoryName].filter(Boolean).join(' · ') || '—'}</div>
                      <div className="flex items-center justify-between">
                        <span className="font-black text-indigo-600">{money(p.sellingPrice)}</span>
                        <span className={`text-[10px] font-bold ${p.inStock === false ? 'text-rose-500' : 'text-emerald-600'}`}>{p.inStock === false ? 'ကုန်' : 'ရှိသည်'}</span>
                      </div>
                      {(p.warrantyMonths || 0) > 0 && <div className="text-[11px] text-slate-500">Warranty {p.warrantyMonths} လ</div>}
                      <button type="button" onClick={() => addToCart(p)} className="mt-1 w-full rounded-lg bg-indigo-600 py-2 text-xs font-bold text-white">ခြင်းထည့်</button>
                    </div>
                  </article>
                );
              })}
            </div>
            {!loading && !catalogError && !filtered.length && <div className="py-12 text-center text-sm text-slate-400">ပစ္စည်း မရှိသေးပါ</div>}
            {!catalogError && <div className="flex items-center justify-between gap-2">
              <button type="button" disabled={loading || page === 0} onClick={() => setPage(p => p - 1)} className="rounded-xl border px-3 py-2 disabled:opacity-40">ရှေ့စာမျက်နှာ</button>
              <span aria-live="polite" className="text-xs text-slate-500">{loading ? 'ဖတ်နေသည်…' : `${totalProducts} မျိုး · စာမျက်နှာ ${page + 1}`}</span>
              <button type="button" disabled={loading || !hasNext} onClick={() => setPage(p => p + 1)} className="rounded-xl border px-3 py-2 disabled:opacity-40">နောက်စာမျက်နှာ</button>
            </div>}
          </section>
        )}

        {tab === 'services' && (
          <section className="space-y-3">
            <h2 className="font-bold">Service ခေါ်ရန်</h2>
            <div className="grid gap-2">
              {services.map(s => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => setSelectedService(s)}
                  className={`rounded-xl border p-3 text-left ${selectedService?.id === s.id ? 'border-indigo-400 bg-indigo-50' : 'bg-white'}`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="font-bold">{s.name}</div>
                      <div className="text-[11px] text-slate-500">{s.serviceTypeName || 'Service'}</div>
                    </div>
                    <div className="text-sm font-black text-indigo-600">{money(s.price)}</div>
                  </div>
                  {s.description && <p className="mt-1 text-xs text-slate-500">{s.description}</p>}
                </button>
              ))}
            </div>
            <input value={device} onChange={e => setDevice(e.target.value)} placeholder="ကိရိယာ (ဥပမာ Laptop)" className="w-full rounded-xl border bg-white px-3 py-2.5 text-sm" />
            <textarea value={problem} onChange={e => setProblem(e.target.value)} placeholder="ပြဿနာ ဖော်ပြရန်" rows={3} className="w-full rounded-xl border bg-white px-3 py-2.5 text-sm" />
            <button type="button" onClick={() => void requestService()} className="w-full rounded-xl bg-indigo-600 py-3 text-sm font-bold text-white">
              {selectedService ? `${selectedService.name} ခေါ်မည်` : 'Service ခေါ်မည်'}
            </button>
          </section>
        )}

        {tab === 'cart' && (
          <section className="space-y-3">
            <h2 className="font-bold">ခြင်းတောင်း</h2>
            <CheckoutFlow
              cart={cart}
              persistCart={persistCart}
              session={session}
              value={delivery}
              onChange={setDelivery}
              orderNote={orderNote}
              onNoteChange={setOrderNote}
              placing={placing}
              pickupDepositPercent={pickupDepositPercent}
              deliveryEnabled={deliveryEnabled}
              onPlace={(promoCode) => void checkout(promoCode)}
              onNeedAuth={() => { setTab('account'); setMessage('အရင် ဝင်ပါ'); }}
              onBrowse={() => setTab('products')}
            />
          </section>
        )}

        {tab === 'history' && (
          <section className="space-y-4">
            {!session && <div className="rounded-2xl border py-10 text-center text-sm text-slate-500">မှတ်တမ်းကြည့်ရန် ဝင်ပါ</div>}
            {mineLoading && <div className="flex justify-center text-slate-400"><Loader2 className="animate-spin" /></div>}
            {session && (
              <>
                <div>
                  <h2 className="mb-2 font-bold">ဝယ်ယူမှတ်တမ်း</h2>
                  {!purchases.length ? <div className="text-sm text-slate-400">ဆိုင်က ဝယ်ထားတာ မရှိသေးပါ</div> : purchases.map(p => (
                    <div key={p.id} className="mb-2 rounded-xl border bg-white p-3">
                      <div className="flex justify-between gap-2">
                        <b className="text-indigo-700">{p.saleCode}</b>
                        <span className="text-[10px] font-black text-slate-500">{p.paymentStatus || '—'}</span>
                      </div>
                      <div className="text-xs text-slate-500">{(p.lines || []).map(l => `${l.productName} × ${l.qty}`).join(', ')}</div>
                      {(p.lines || []).filter(l => (l.warrantyMonths || 0) > 0).map((l, i) => (
                        <div key={i} className="mt-1 text-[11px] text-slate-500">
                          Warranty {l.warrantyMonths} လ
                          {l.serialNumber ? ` · ${l.serialNumber}` : ''}
                          {l.warrantyStartDate ? ` · ${l.warrantyStartDate}` : ''}
                          {l.warrantyExpiryDate ? ` → ${l.warrantyExpiryDate}` : ''}
                          {l.warrantyStatus === 'ACTIVE' ? ` · ကျန် ${l.warrantyDaysRemaining ?? 0} ရက်` : l.warrantyStatus === 'EXPIRED' ? ' · သက်တမ်းကုန်' : ''}
                        </div>
                      ))}
                      <div className="mt-1 flex items-center justify-between gap-2">
                        <div className="text-sm font-bold">{money(p.netAmount)}</div>
                        <button
                          type="button"
                          className="text-xs font-semibold text-indigo-600"
                          onClick={async () => {
                            try {
                              const blob = await customerPortalService.downloadPurchaseInvoice(p.id);
                              const url = URL.createObjectURL(blob);
                              const a = document.createElement('a');
                              a.href = url;
                              a.download = `invoice-${p.saleCode || p.id}.pdf`;
                              a.click();
                              window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
                            } catch (e: any) {
                              setMessage(e?.message || 'Invoice မရနိုင်သေးပါ');
                            }
                          }}
                        >
                          Invoice
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
                <div>
                  <h2 className="mb-2 font-bold">Service မှတ်တမ်း</h2>
                  {!jobs.length ? <div className="text-sm text-slate-400">Service job မရှိသေးပါ</div> : jobs.map(j => (
                    <div key={j.id} className="mb-2 rounded-xl border bg-white p-3">
                      <div className="flex justify-between gap-2">
                        <b className="text-indigo-700">{j.jobNo}</b>
                        <span className="text-[10px] font-black text-slate-500">{j.status || '—'}</span>
                      </div>
                      <div className="text-xs text-slate-600">{[j.itemName, j.deviceType].filter(Boolean).join(' · ') || '—'}</div>
                      {j.problemDesc && <p className="whitespace-pre-wrap text-xs text-slate-600">{j.problemDesc}</p>}
                      <div className="text-xs text-slate-500">{(j.services || []).map(s => s.name).filter(Boolean).join(', ')}</div>
                      <div className="mt-1 text-sm font-bold">{money(j.netAmount)}</div>
                    </div>
                  ))}
                </div>
                <div>
                  <h2 className="mb-2 font-bold">App အော်ဒါ</h2>
                  {!orders.length ? <div className="text-sm text-slate-400">မရှိသေးပါ</div> : orders.map(o => (
                    <CustomerOrderCard
                      key={o.id}
                      order={o}
                      onUpdated={(next) => {
                        if (next) setOrders(prev => prev.map(row => row.id === next.id ? next : row));
                        else void loadMine();
                      }}
                      onReorder={(order) => {
                        const next = [...cart];
                        for (const line of order.lines || []) {
                          const id = line.productId;
                          const qty = Number(line.qty || 0);
                          if (!id || qty <= 0) continue;
                          const product = products.find(p => p.id === id) || {
                            id,
                            name: line.productName,
                            sellingPrice: line.unitPrice,
                            stockQty: qty,
                            inStock: true,
                          } as CatalogProduct;
                          const idx = next.findIndex(l => l.product.id === id);
                          if (idx >= 0) next[idx] = { ...next[idx], qty, product };
                          else next.push({ product, qty });
                        }
                        persistCart(next);
                        setTab('cart');
                        setMessage('ခြင်းတောင်းထဲ ထည့်ပြီးပါပြီ။ အော်ဒါပြန်တင်ပါ။');
                      }}
                    />
                  ))}
                </div>
                <div>
                  <h2 className="mb-2 font-bold">Service တောင်းချက်</h2>
                  {!bookings.length ? <div className="text-sm text-slate-400">မရှိသေးပါ</div> : bookings.map(b => (
                    <div key={b.id} className="mb-2 rounded-xl border bg-white p-3">
                      <div className="flex justify-between gap-2">
                        <b className="text-indigo-700">{b.bookingNo}</b>
                        <span className="text-[10px] font-black text-slate-500">{b.status}</span>
                      </div>
                      <p className="whitespace-pre-wrap text-xs text-slate-600">{b.complaintNote || '—'}</p>
                    </div>
                  ))}
                </div>
              </>
            )}
          </section>
        )}

        {tab === 'account' && (
          <AccountPanel session={session} onSession={setSession} onLogout={logout} />
        )}
      </main>

      <nav className="fixed bottom-0 left-0 right-0 border-t bg-white">
        <div className="mx-auto grid max-w-5xl grid-cols-5 text-[11px] font-semibold text-slate-500">
          {([
            ['products', 'ပစ္စည်း', Package],
            ['services', 'Service', Wrench],
            ['cart', 'ခြင်း', ShoppingCart],
            ['history', 'မှတ်တမ်း', History],
            ['account', 'ကျွန်ုပ်', UserRound],
          ] as const).map(([id, label, Icon]) => (
            <button key={id} type="button" onClick={() => setTab(id)} className={`relative flex flex-col items-center gap-0.5 py-2 ${tab === id ? 'text-indigo-600' : ''}`}>
              <Icon size={18} />
              {id === 'cart' && cartCount > 0 && (
                <span className="absolute right-[18%] top-1 rounded-full bg-rose-500 px-1.5 text-[9px] font-black text-white">{cartCount}</span>
              )}
              {label}
            </button>
          ))}
        </div>
      </nav>
    </div>
  );
};

const AccountPanel: React.FC<{
  session: CustomerSession | null;
  onSession: (s: CustomerSession | null) => void;
  onLogout: () => void;
}> = ({ session, onSession, onLogout }) => {
  const [mode, setMode] = useState<'login' | 'register' | 'forgot'>('login');
  const [name, setName] = useState(session?.name || '');
  const [phone, setPhone] = useState(session?.phone || '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [address, setAddress] = useState(session?.address || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    setName(session?.name || '');
    setPhone(session?.phone || '');
    setAddress(session?.address || '');
  }, [session]);

  const submitAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setNotice(null);
    if (mode === 'register') {
      const strong =
        password.length >= 8 &&
        /[A-Za-z]/.test(password) &&
        /\d/.test(password) &&
        /[^A-Za-z0-9\s]/.test(password);
      if (!strong) {
        setError('အနည်းဆုံး ၈ လုံး — အက္ခရာ၊ ဂဏန်း နှင့် အထူးအက္ခရာ (!@#$…) ရောထည့်ပါ');
        return;
      }
    }
    setBusy(true);
    try {
      if (mode === 'forgot') {
        const res = await customerPortalService.forgotPassword({ email });
        setNotice(res.message || 'Password ပြန်သတ်မှတ်ရန် link ကို email သို့ ပို့ပြီးပါပြီ');
        return;
      }
      const res = mode === 'register'
        ? await customerPortalService.register({ name, phone, email, password, address })
        : await customerPortalService.login({ phone, login: phone, password });
      if (res.data?.resetSent) {
        setNotice(res.message || 'ဤ email ဖြင့် အကောင့်ရှိပြီးသား။ Password ပြန်သတ်မှတ်ရန် link ကို ပို့ပြီးပါပြီ');
        setMode('login');
        return;
      }
      if (res.success && res.data?.accessToken) {
        onSession(applyAuth(res.data as CustomerSession, { name, phone, address, email }));
      } else {
        setError(res.message || 'မအောင်မြင်ပါ');
      }
    } catch (err: any) {
      setError(err?.message || 'မအောင်မြင်ပါ');
    } finally {
      setBusy(false);
    }
  };

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await customerPortalService.updateProfile({ name, phone, address });
      if (res.success && res.data?.accessToken) {
        onSession(applyAuth(res.data, { name, phone, address }));
      } else if (res.success && res.data) {
        const current = getCustomerSession();
        if (current) onSession(applyAuth({ ...current, ...res.data, needsProfile: false }, { name, phone, address }));
      }
    } catch (err: any) {
      setError(err?.message || 'သိမ်းမရပါ');
    } finally {
      setBusy(false);
    }
  };

  if (!session) {
    return (
      <form onSubmit={submitAuth} className="space-y-3 rounded-2xl border bg-white p-4">
        <h2 className="font-bold">{mode === 'register' ? 'အကောင့်ဖွင့်ရန်' : mode === 'forgot' ? 'Password မေ့နေပါသလား' : 'ဝင်ရန်'}</h2>
        {mode === 'register' && (
          <input value={name} onChange={e => setName(e.target.value)} placeholder="အမည် (POS မှာရှိပြီးသားဆို ချန်လို့ရ)" className="w-full rounded-xl border px-3 py-2.5 text-sm" />
        )}
        {mode !== 'forgot' && (
          <input
            required
            value={phone}
            onChange={e => setPhone(e.target.value)}
            placeholder={mode === 'login' ? 'ဖုန်း / Email / အမည်' : 'ဖုန်း'}
            className="w-full rounded-xl border px-3 py-2.5 text-sm"
          />
        )}
        {(mode === 'register' || mode === 'forgot') && (
          <input required type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="Email (မဖြစ်မနေ)" className="w-full rounded-xl border px-3 py-2.5 text-sm" />
        )}
        {mode !== 'forgot' && (
          <input required type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder={mode === 'register' ? 'ဥပမာ MyPass@12 (၈+ အက္ခရာ+ဂဏန်း+အထူး)' : 'စကားဝှက်'} className="w-full rounded-xl border px-3 py-2.5 text-sm" />
        )}
        {mode === 'register' && (
          <input value={address} onChange={e => setAddress(e.target.value)} placeholder="လိပ်စာ (optional)" className="w-full rounded-xl border px-3 py-2.5 text-sm" />
        )}
        {notice && <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{notice}</p>}
        {error && <p className="text-sm text-rose-600">{error}</p>}
        <button disabled={busy} type="submit" className="flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 py-3 text-sm font-bold text-white disabled:opacity-60">
          {busy && <Loader2 size={16} className="animate-spin" />}
          {mode === 'register' ? 'ဖွင့်မည်' : mode === 'forgot' ? 'Reset link ပို့မည်' : 'ဝင်မည်'}
        </button>
        {mode === 'login' && (
          <button type="button" className="w-full text-xs font-semibold text-indigo-600" onClick={() => { setMode('forgot'); setError(null); setNotice(null); }}>
            Password မေ့နေပါသလား?
          </button>
        )}
        <button type="button" className="w-full text-xs font-semibold text-indigo-600" onClick={() => { setMode(mode === 'register' ? 'login' : 'register'); setError(null); setNotice(null); }}>
          {mode === 'register' ? 'အကောင့်ရှိပြီး? ဝင်ရန်' : 'အကောင့်မရှိသေး? ဖွင့်ရန်'}
        </button>
      </form>
    );
  }

  return (
    <form onSubmit={saveProfile} className="space-y-3 rounded-2xl border bg-white p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="font-bold">{session.name || 'ဖောက်သည်'}</h2>
          <div className="text-xs text-slate-500">{session.phone || session.email}</div>
        </div>
        <button type="button" onClick={onLogout} className="inline-flex items-center gap-1 text-xs font-bold text-rose-600"><LogOut size={14} /> ထွက်မည်</button>
      </div>
      {session.needsProfile && <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">မှာယူ/Service ခေါ်ရန် ဖုန်းနှင့် လိပ်စာ ဖြည့်ပါ။</p>}
      <input value={name} onChange={e => setName(e.target.value)} placeholder="အမည်" className="w-full rounded-xl border px-3 py-2.5 text-sm" />
      <input required value={phone} onChange={e => setPhone(e.target.value)} placeholder="ဖုန်း" className="w-full rounded-xl border px-3 py-2.5 text-sm" />
      <input value={address} onChange={e => setAddress(e.target.value)} placeholder="လိပ်စာ" className="w-full rounded-xl border px-3 py-2.5 text-sm" />
      {error && <p className="text-sm text-rose-600">{error}</p>}
      <button disabled={busy} type="submit" className="w-full rounded-xl bg-indigo-600 py-3 text-sm font-bold text-white disabled:opacity-60">သိမ်းမည်</button>
    </form>
  );
};

export default CustomerShopPage;

