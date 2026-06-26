import React, { useEffect, useRef, useState } from 'react';
import { Barcode, CheckCircle2, Send, Trash2, Wifi } from 'lucide-react';

interface ScanRecord { code: string; time: string; ok: boolean }

const API = `${window.location.origin}/api/v1/scan`;

const ScanPage: React.FC = () => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [input, setInput] = useState('');
  const [history, setHistory] = useState<ScanRecord[]>([]);
  const [status, setStatus] = useState<'idle' | 'sending' | 'ok' | 'err'>('idle');

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const send = async (code: string) => {
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;
    setStatus('sending');
    try {
      const res = await fetch(API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ barcode: trimmed }),
      });
      const ok = res.ok;
      setStatus(ok ? 'ok' : 'err');
      setHistory(prev => [{ code: trimmed, time: new Date().toLocaleTimeString(), ok }, ...prev.slice(0, 29)]);
      setTimeout(() => setStatus('idle'), 1200);
    } catch {
      setStatus('err');
      setTimeout(() => setStatus('idle'), 1500);
    }
    setInput('');
    inputRef.current?.focus();
  };

  const handleKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') { e.preventDefault(); send(input); }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center px-4 pt-10 pb-6 gap-6">

      {/* Header */}
      <div className="text-center space-y-1">
        <div className="flex items-center justify-center gap-2">
          <Barcode size={28} className="text-violet-400" />
          <h1 className="text-xl font-black text-white uppercase tracking-tight">Barcode Scanner</h1>
        </div>
        <p className="text-[11px] text-slate-400 font-semibold">
          Barcode ကို scan လုပ်ပါ — Sale form သို့ auto-send ဖြစ်မည်
        </p>
        <div className="flex items-center justify-center gap-1.5 mt-1">
          <Wifi size={11} className="text-emerald-400" />
          <span className="text-[10px] text-emerald-400 font-bold">{window.location.host}</span>
        </div>
      </div>

      {/* Status flash */}
      <div className={`w-full max-w-sm h-2 rounded-full transition-all duration-300 ${
        status === 'ok' ? 'bg-emerald-500' :
        status === 'err' ? 'bg-rose-500' :
        status === 'sending' ? 'bg-violet-500 animate-pulse' :
        'bg-slate-700'
      }`} />

      {/* Input area */}
      <div className="w-full max-w-sm space-y-3">
        <div className="relative">
          <Barcode className="absolute left-4 top-1/2 -translate-y-1/2 text-violet-400" size={20} />
          <input
            ref={inputRef}
            type="text"
            inputMode="text"
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="characters"
            value={input}
            onChange={e => setInput(e.target.value.toUpperCase())}
            onKeyDown={handleKey}
            placeholder="Barcode ရိုက်ပါ သို့ Scanner ဖြင့် scan..."
            className="w-full pl-12 pr-4 py-5 bg-slate-800 border-2 border-violet-600 rounded-2xl text-white text-[15px] font-black outline-none focus:border-violet-400 placeholder:text-slate-600 tracking-widest"
          />
        </div>

        <button
          onClick={() => send(input)}
          disabled={!input.trim() || status === 'sending'}
          className="w-full py-4 bg-violet-600 hover:bg-violet-500 disabled:opacity-40 text-white rounded-2xl text-[13px] font-black uppercase tracking-widest flex items-center justify-center gap-2 transition-all active:scale-95"
        >
          {status === 'sending' ? (
            <span className="animate-pulse">Sending...</span>
          ) : status === 'ok' ? (
            <><CheckCircle2 size={16} /> Sent!</>
          ) : (
            <><Send size={16} /> Send to Sale</>
          )}
        </button>
      </div>

      {/* Tips */}
      <div className="w-full max-w-sm bg-slate-800/60 rounded-2xl px-4 py-3 space-y-1.5 border border-slate-700">
        <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">အသုံးပြုနည်း</p>
        {[
          'Bluetooth barcode scanner ကို ဖုန်းနဲ့ connect ပြီး scan လုပ်ပါ',
          'Scanner app (keyboard wedge mode) သုံးပြီး ဤ input field ထဲ ပို့ပါ',
          'Manual ရိုက်ပြီးလည်း Enter နှိပ်ကာ send လုပ်နိုင်သည်',
          'Sale form ဖွင့်ထားပါက barcode ရောက်တာနဲ့ auto-add ဖြစ်မည်',
        ].map((tip, i) => (
          <div key={i} className="flex items-start gap-2">
            <span className="text-violet-500 font-black text-[11px] shrink-0 mt-0.5">{i + 1}.</span>
            <p className="text-[11px] text-slate-400">{tip}</p>
          </div>
        ))}
      </div>

      {/* Scan history */}
      {history.length > 0 && (
        <div className="w-full max-w-sm space-y-2">
          <div className="flex items-center justify-between">
            <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Scan History</p>
            <button onClick={() => setHistory([])} className="flex items-center gap-1 text-[10px] text-slate-600 hover:text-rose-400 transition-colors">
              <Trash2 size={11} /> Clear
            </button>
          </div>
          <div className="space-y-1.5 max-h-56 overflow-y-auto">
            {history.map((r, i) => (
              <div key={i} className={`flex items-center gap-3 px-3 py-2 rounded-xl border ${r.ok ? 'bg-slate-800 border-slate-700' : 'bg-rose-950/40 border-rose-800/50'}`}>
                <CheckCircle2 size={13} className={r.ok ? 'text-emerald-500 shrink-0' : 'text-rose-500 shrink-0'} />
                <span className="font-black text-white text-[12px] flex-1 tracking-widest truncate">{r.code}</span>
                <span className="text-[10px] text-slate-500 shrink-0">{r.time}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default ScanPage;
