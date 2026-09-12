import React, { useEffect, useRef, useState } from 'react';
import { BellRing, Volume2, VolumeX } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { AppRoute } from '../types';
import { subscribeTopic, isWsConnected } from '../services/wsClient';
import { parseCustomerOrderEvent } from '../hooks/useCustomerOrderLiveSync';
import { getCachedCompanySettings, getCompanySettings } from '../utils/companySettings';

type Order = { id: number; orderNo: string; customerName?: string; status: string };
const message = 'Customer Order ရှိပါတယ်ခင်ဗျာ';

export default function CustomerOrderNotification({ isDark }: { isDark: boolean }) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [open, setOpen] = useState(false);
  const [sound, setSound] = useState(false);
  const [notice, setNotice] = useState(false);
  const [failed, setFailed] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const audio = useRef<AudioContext | null>(null);
  const soundEnabled = useRef(false);
  const customClip = useRef<HTMLAudioElement | null>(null);

  const playDefaultBeep = () => {
    const context = audio.current;
    if (!context || context.state !== 'running') return;
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.connect(gain);
    gain.connect(context.destination);
    oscillator.frequency.setValueAtTime(880, context.currentTime);
    oscillator.frequency.setValueAtTime(660, context.currentTime + 0.15);
    gain.gain.setValueAtTime(0.12, context.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + 0.5);
    oscillator.start();
    oscillator.stop(context.currentTime + 0.5);
  };

  const playNotificationSound = () => {
    const dataUrl = getCachedCompanySettings().notificationSoundBase64?.trim();
    if (dataUrl?.startsWith('data:audio')) {
      try {
        if (customClip.current) {
          customClip.current.pause();
          customClip.current.currentTime = 0;
        }
        const clip = new Audio(dataUrl);
        customClip.current = clip;
        void clip.play().catch(() => playDefaultBeep());
        return;
      } catch {
        /* fall through to beep */
      }
    }
    playDefaultBeep();
  };

  const speakMyanmarIfAvailable = () => {
    if (!('speechSynthesis' in window)) return;
    const voice = window.speechSynthesis.getVoices().find(v => /^my([_-]|$)/i.test(v.lang));
    if (voice && !window.speechSynthesis.speaking) {
      const utterance = new SpeechSynthesisUtterance(message);
      utterance.voice = voice;
      utterance.lang = voice.lang;
      window.speechSynthesis.speak(utterance);
    }
  };

  const announce = () => {
    if (!soundEnabled.current) return;
    playNotificationSound();
    speakMyanmarIfAvailable();
  };

  useEffect(() => {
    void getCompanySettings();
    const onUpdated = () => { /* cache already refreshed by setter */ };
    window.addEventListener('company-settings-updated', onUpdated);
    return () => window.removeEventListener('company-settings-updated', onUpdated);
  }, []);

  useEffect(() => {
    let stopped = false;
    let seen: Set<number> | null = null;
    let running = false;
    let dirty = false;
    const refresh = async () => {
      if (running) { dirty = true; return; }
      running = true;
      try {
        do {
          dirty = false;
          const response = await api.get<Order[]>('/v1/customer-orders');
          if (stopped) return;
          if (!Array.isArray(response.data)) throw new Error('Invalid order response');
          const rows = response.data;
          const fresh = seen !== null && rows.some(order => !seen!.has(order.id));
          seen = new Set(rows.map(order => order.id));
          setOrders(rows.filter(order => order.status === 'PENDING'));
          setFailed(false);
          if (fresh) { setNotice(true); announce(); }
        } while (dirty && !stopped);
      } catch {
        if (!stopped) setFailed(true);
      } finally {
        running = false;
      }
    };
    const unsubscribe = subscribeTopic('/topic/customer-order', body => {
      const event = parseCustomerOrderEvent(body);
      if (seen === null && event.type === 'CUSTOMER_ORDER_CREATED') { setNotice(true); announce(); }
      void refresh();
      window.dispatchEvent(new Event('customer-orders-updated'));
    });
    const connected = () => { void refresh(); window.dispatchEvent(new Event('customer-orders-updated')); };
    const disconnected = () => setFailed(true);
    const onVisibility = () => {
      if (document.visibilityState === 'visible') connected();
    };
    window.addEventListener('ws-connected', connected);
    window.addEventListener('ws-disconnected', disconnected);
    window.addEventListener('focus', connected);
    document.addEventListener('visibilitychange', onVisibility);
    void refresh();
    const poll = window.setInterval(() => {
      if (isWsConnected()) return;
      if (document.visibilityState !== 'visible') return;
      void refresh();
    }, 15000);
    return () => {
      stopped = true;
      window.clearInterval(poll);
      unsubscribe();
      window.removeEventListener('ws-connected', connected);
      window.removeEventListener('ws-disconnected', disconnected);
      window.removeEventListener('focus', connected);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, []);

  useEffect(() => {
    const dismiss = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', dismiss);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('mousedown', dismiss);
      document.removeEventListener('keydown', escape);
      void audio.current?.close();
      customClip.current?.pause();
    };
  }, []);

  const toggleSound = async () => {
    if (soundEnabled.current) {
      soundEnabled.current = false;
      setSound(false);
      customClip.current?.pause();
      return;
    }
    try {
      audio.current ??= new AudioContext();
      await audio.current.resume();
      await getCompanySettings(true);
      soundEnabled.current = true;
      setSound(true);
      announce();
    } catch {
      soundEnabled.current = false;
      setSound(false);
    }
  };

  return (
    <div ref={root} className="relative">
      <button type="button" onClick={() => { setOpen(v => !v); setNotice(false); }}
        aria-label={`Customer orders: ${orders.length} pending`} aria-expanded={open}
        title="Customer App Orders"
        className={`relative p-1.5 rounded-lg ${isDark ? 'text-slate-300 hover:bg-slate-800' : 'text-slate-500 hover:bg-slate-100'}`}>
        <BellRing size={18} className={notice ? 'text-rose-500 animate-pulse' : ''} />
        {orders.length > 0 && <span className="absolute -top-1 -right-1 min-w-4 px-1 rounded-full bg-rose-500 text-white text-[10px] font-bold">{orders.length > 99 ? '99+' : orders.length}</span>}
      </button>
      <span role="status" className="sr-only">{notice ? message : ''}</span>
      {open && <div className={`absolute right-0 top-full mt-2 w-72 max-w-[85vw] rounded-xl border shadow-xl z-50 p-3 ${isDark ? 'bg-slate-900 border-slate-700 text-slate-200' : 'bg-white border-slate-200 text-slate-700'}`}>
        <p className="text-sm font-bold mb-2">Customer App Orders ({orders.length})</p>
        {failed && <p className="text-xs text-amber-600 mb-2">Order စစ်မရသေးပါ။ ပြန်လည်ချိတ်ဆက်နေပါသည်။</p>}
        <button type="button" onClick={() => void toggleSound()} aria-pressed={sound} className="flex items-center gap-2 text-xs text-indigo-500 py-2">
          {sound ? <Volume2 size={16} /> : <VolumeX size={16} />}
          {sound ? 'အသံပိတ်မည်' : 'အသံဖွင့်မည်'}
        </button>
        <p className="text-[11px] mb-2">
          မြန်မာ voice ရှိပါက စကားပြောအသံ ထပ်ထွက်ပါမည်။ Notification သံသည် Company Settings မှာ upload ထားသော အသံ (မရှိပါက မူရင်း beep) ဖြစ်သည်။
        </p>
        <div className="max-h-60 overflow-auto">
          {orders.length === 0 && <p className="text-xs py-3">Pending order မရှိပါ။</p>}
          {orders.map(order => <Link key={order.id} to={AppRoute.CUSTOMER_APP_ORDERS} onClick={() => setOpen(false)} className="block border-t border-slate-500/20 py-2 text-xs">
            <b>{order.orderNo}</b><span className="block">{order.customerName}</span>
          </Link>)}
        </div>
        <Link to={AppRoute.CUSTOMER_APP_ORDERS} onClick={() => setOpen(false)} className="block pt-2 text-xs font-bold text-indigo-500">Order အားလုံးကြည့်မည် →</Link>
      </div>}
    </div>
  );
}
