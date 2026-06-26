import React, { useEffect, useRef, useState, useCallback } from 'react';
import { scanImageData } from '@undecaf/zbar-wasm';
import { X, Camera, RefreshCw, ZoomIn, ImagePlus, ShieldAlert, ScanLine, FlipHorizontal, Zap, Cpu } from 'lucide-react';

interface Props {
  onDetected: (code: string) => void;
  onClose: () => void;
}

type PermState = 'checking' | 'requesting' | 'streaming' | 'denied' | 'unavailable' | 'http';
type ScanEngine = 'native' | 'zbar';

const DETECTED_DUPLICATE_WINDOW_MS = 900;
const SUCCESS_FLASH_MS = 250;

const NATIVE_FORMATS = [
  'qr_code', 'code_128', 'code_39', 'ean_13', 'ean_8',
  'upc_a', 'upc_e', 'itf', 'codabar',
];

// ── ZBar WASM file decode ─────────────────────────────────────────────────────
async function decodeFromFile(file: File): Promise<string> {
  const img = new Image();
  const url = URL.createObjectURL(file);
  try {
    img.src = url;
    await new Promise<void>((res, rej) => {
      img.onload = () => res();
      img.onerror = () => rej(new Error('Image load failed'));
    });
    const canvas = document.createElement('canvas');
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
    ctx.drawImage(img, 0, 0);
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const results = await scanImageData(imageData);
    if (results.length === 0) throw new Error('No barcode found');
    return results[0].decode();
  } finally {
    URL.revokeObjectURL(url);
  }
}

const nativeSupported = typeof window !== 'undefined' && 'BarcodeDetector' in window;

// ── Main component ─────────────────────────────────────────────────────────────
const BarcodeScannerCamera: React.FC<Props> = ({ onDetected, onClose }) => {
  const isSecure = typeof window !== 'undefined' && window.isSecureContext;

  const videoRef          = useRef<HTMLVideoElement>(null);
  // Native engine refs
  const nativeStreamRef   = useRef<MediaStream | null>(null);
  const nativeRafRef      = useRef<number>(0);
  // ZBar WASM engine refs
  const zbarStreamRef     = useRef<MediaStream | null>(null);
  const zbarRafRef        = useRef<number>(0);
  const zbarCanvasRef     = useRef<HTMLCanvasElement | null>(null);
  const zbarCtxRef        = useRef<CanvasRenderingContext2D | null>(null);
  const zbarScanningRef   = useRef(false);
  // Common refs
  const lastDetectedCodeRef = useRef('');
  const lastDetectedAtRef   = useRef(0);
  const fileInputRef        = useRef<HTMLInputElement>(null);
  const scanEngineRef       = useRef<ScanEngine>(nativeSupported ? 'native' : 'zbar');

  const [permState,      setPermState]      = useState<PermState>('checking');
  const [cameras,        setCameras]        = useState<MediaDeviceInfo[]>([]);
  const [selectedCamera, setSelectedCamera] = useState<string>('');
  const [facingMode,     setFacingMode]     = useState<'environment' | 'user'>('environment');
  const [streamError,    setStreamError]    = useState('');
  const [lastCode,       setLastCode]       = useState('');
  const [flash,          setFlash]          = useState(false);
  const [decoding,       setDecoding]       = useState(false);
  const [scanCount,      setScanCount]      = useState(0);
  const [scanEngine,     setScanEngine]     = useState<ScanEngine>(nativeSupported ? 'native' : 'zbar');

  // Pre-warm ZBar WASM on mount to avoid first-scan delay
  useEffect(() => {
    scanImageData(new ImageData(1, 1)).catch(() => {});
  }, []);

  const markDetected = useCallback((code: string) => {
    const now = Date.now();
    if (
      lastDetectedCodeRef.current === code &&
      now - lastDetectedAtRef.current < DETECTED_DUPLICATE_WINDOW_MS
    ) return false;
    lastDetectedCodeRef.current = code;
    lastDetectedAtRef.current = now;
    setLastCode(code);
    setFlash(true);
    setScanCount(n => n + 1);
    setTimeout(() => setFlash(false), SUCCESS_FLASH_MS);
    onDetected(code);
    return true;
  }, [onDetected]);

  // ── Stop helpers ──────────────────────────────────────────────────────────────
  const stopNative = useCallback(() => {
    cancelAnimationFrame(nativeRafRef.current);
    nativeStreamRef.current?.getTracks().forEach(t => t.stop());
    nativeStreamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  }, []);

  const stopZbar = useCallback(() => {
    cancelAnimationFrame(zbarRafRef.current);
    zbarScanningRef.current = false;
    zbarStreamRef.current?.getTracks().forEach(t => t.stop());
    zbarStreamRef.current = null;
    zbarCanvasRef.current = null;
    zbarCtxRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  }, []);

  const stopAll = useCallback(() => {
    stopNative();
    stopZbar();
  }, [stopNative, stopZbar]);

  // ── Native BarcodeDetector (OS hardware) ─────────────────────────────────────
  const startNative = useCallback(async (deviceId?: string, facing: 'environment' | 'user' = 'environment') => {
    if (!videoRef.current) return;
    stopAll();
    setStreamError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          ...(deviceId ? { deviceId: { exact: deviceId } } : { facingMode: { ideal: facing } }),
          width: { ideal: 640 }, height: { ideal: 640 },
          frameRate: { ideal: 30, max: 60 },
        },
      });
      nativeStreamRef.current = stream;
      videoRef.current.srcObject = stream;
      await videoRef.current.play().catch(() => {});

      const detector = new (window as any).BarcodeDetector({ formats: NATIVE_FORMATS });

      const doScan = async () => {
        const v = videoRef.current;
        if (v && v.readyState >= 2 && !v.paused) {
          try {
            const barcodes = await detector.detect(v);
            if (barcodes.length > 0) markDetected(barcodes[0].rawValue);
          } catch { /* ignore */ }
        }
        nativeRafRef.current = requestAnimationFrame(doScan);
      };
      nativeRafRef.current = requestAnimationFrame(doScan);
      setPermState('streaming');
    } catch (e: any) {
      const msg = (e?.message || '').toLowerCase();
      if (msg.includes('permission') || msg.includes('notallowed')) setPermState('denied');
      else if (msg.includes('notfound') || msg.includes('devicesnotfound')) { setStreamError('Camera မတွေ့ပါ။'); setPermState('unavailable'); }
      else { setStreamError(e?.message || 'Camera ခေါ်၍မရပါ။'); setPermState('unavailable'); }
    }
  }, [markDetected, stopAll]);

  // ── ZBar WASM (C++ compiled, near-native speed) ───────────────────────────────
  const startZbar = useCallback(async (deviceId?: string, facing: 'environment' | 'user' = 'environment') => {
    if (!videoRef.current) return;
    stopAll();
    setStreamError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          ...(deviceId ? { deviceId: { exact: deviceId } } : { facingMode: { ideal: facing } }),
          width: { ideal: 640 }, height: { ideal: 640 },
          frameRate: { ideal: 30, max: 60 },
        },
      });
      zbarStreamRef.current = stream;
      videoRef.current.srcObject = stream;
      await videoRef.current.play().catch(() => {});

      const canvas = document.createElement('canvas');
      canvas.width = 640;
      canvas.height = 640;
      zbarCanvasRef.current = canvas;
      zbarCtxRef.current = canvas.getContext('2d', { willReadFrequently: true });
      zbarScanningRef.current = false;

      const doScan = async () => {
        const v   = videoRef.current;
        const ctx = zbarCtxRef.current;
        const cvs = zbarCanvasRef.current;
        if (!zbarScanningRef.current && v && ctx && cvs && v.readyState >= 2 && !v.paused) {
          zbarScanningRef.current = true;
          try {
            ctx.drawImage(v, 0, 0, cvs.width, cvs.height);
            const imageData = ctx.getImageData(0, 0, cvs.width, cvs.height);
            const results = await scanImageData(imageData);
            if (results.length > 0) markDetected(results[0].decode());
          } catch { /* ignore */ }
          zbarScanningRef.current = false;
        }
        zbarRafRef.current = requestAnimationFrame(doScan);
      };
      zbarRafRef.current = requestAnimationFrame(doScan);
      setPermState('streaming');
    } catch (e: any) {
      const msg = (e?.message || '').toLowerCase();
      if (msg.includes('permission') || msg.includes('notallowed')) setPermState('denied');
      else if (msg.includes('notfound') || msg.includes('devicesnotfound')) { setStreamError('Camera မတွေ့ပါ။'); setPermState('unavailable'); }
      else { setStreamError(e?.message || 'Camera ခေါ်၍မရပါ။'); setPermState('unavailable'); }
    }
  }, [markDetected, stopAll]);

  const startWithEngine = useCallback(async (
    engine: ScanEngine, deviceId?: string, facing: 'environment' | 'user' = 'environment'
  ) => {
    if (engine === 'native') await startNative(deviceId, facing);
    else await startZbar(deviceId, facing);
  }, [startNative, startZbar]);

  // ── Camera enumeration ────────────────────────────────────────────────────────
  const initCameras = useCallback(async () => {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const videoDevices = devices.filter(d => d.kind === 'videoinput');
      setCameras(videoDevices);
      const back = videoDevices.find(d => /back|rear|environment/i.test(d.label));
      const id = back?.deviceId || videoDevices[0]?.deviceId || '';
      setSelectedCamera(id);
      await startWithEngine(scanEngineRef.current, id || undefined, 'environment');
    } catch {
      await startWithEngine(scanEngineRef.current, undefined, 'environment');
    }
  }, [startWithEngine]);

  // ── On mount ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (!isSecure) { setPermState('http'); return; }
    let permStatus: PermissionStatus | null = null;
    (async () => {
      try {
        const perm = await navigator.permissions.query({ name: 'camera' as PermissionName });
        permStatus = perm;
        if (perm.state === 'granted') await initCameras();
        else if (perm.state === 'denied') setPermState('denied');
        else setPermState('requesting');
        perm.onchange = async () => {
          if (perm.state === 'granted') await initCameras();
          else if (perm.state === 'denied') { stopAll(); setPermState('denied'); }
        };
      } catch { setPermState('requesting'); }
    })();
    return () => { stopAll(); if (permStatus) permStatus.onchange = null; };
  }, [isSecure, initCameras, stopAll]);

  // ── Handlers ──────────────────────────────────────────────────────────────────
  const requestCameraAccess = async () => {
    setPermState('checking');
    setStreamError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } } });
      stream.getTracks().forEach(t => t.stop());
      await initCameras();
    } catch (e: any) {
      const msg = (e?.message || '').toLowerCase();
      if (msg.includes('notallowed') || msg.includes('permission')) setPermState('denied');
      else { setStreamError(e?.message || 'Camera access မရပါ။'); setPermState('unavailable'); }
    }
  };

  const handleCameraSwitch = async (deviceId: string) => {
    setSelectedCamera(deviceId);
    await startWithEngine(scanEngineRef.current, deviceId, facingMode);
  };

  const handleFlipCamera = async () => {
    const next: 'environment' | 'user' = facingMode === 'environment' ? 'user' : 'environment';
    setFacingMode(next);
    setSelectedCamera('');
    await startWithEngine(scanEngineRef.current, undefined, next);
  };

  const handleSwitchEngine = async (newEngine: ScanEngine) => {
    scanEngineRef.current = newEngine;
    setScanEngine(newEngine);
    if (permState === 'streaming') {
      await startWithEngine(newEngine, selectedCamera || undefined, facingMode);
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setDecoding(true);
    setStreamError('');
    try {
      const code = await decodeFromFile(file);
      markDetected(code);
    } catch {
      setStreamError('Barcode မတွေ့ပါ။ ပိုနီးကပ်ပြီး ဓာတ်ပုံရိုက်ကာ ထပ်ကြိုးစားပါ။');
    } finally {
      setDecoding(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  // ── Render: non-streaming states ──────────────────────────────────────────────
  const renderContent = () => {
    switch (permState) {
      case 'checking':
        return (
          <div className="flex flex-col items-center justify-center gap-4 px-8 py-10">
            <div className="w-14 h-14 rounded-2xl bg-violet-100 flex items-center justify-center">
              <RefreshCw size={24} className="text-violet-600 animate-spin" />
            </div>
            <p className="text-[12px] font-semibold text-slate-600">Camera ကို ဖွင့်နေသည်…</p>
          </div>
        );

      case 'requesting':
        return (
          <div className="flex flex-col items-center justify-center gap-5 px-8 py-10 bg-gradient-to-b from-violet-50 to-white">
            <div className="relative">
              <div className="w-20 h-20 rounded-3xl bg-violet-100 flex items-center justify-center">
                <Camera size={38} className="text-violet-600" />
              </div>
              <div className="absolute -top-1 -right-1 w-6 h-6 bg-amber-400 rounded-full flex items-center justify-center shadow">
                <span className="text-white text-[10px] font-black">!</span>
              </div>
            </div>
            <div className="text-center space-y-1.5">
              <p className="text-[14px] font-black text-slate-800">Camera Access လိုအပ်သည်</p>
              <p className="text-[11px] text-slate-500 leading-relaxed max-w-[220px]">
                QR / Barcode scan လုပ်ရန် camera ကို access လုပ်ရပါမည်
              </p>
            </div>
            <button
              onClick={requestCameraAccess}
              className="w-full flex items-center justify-center gap-2.5 py-3.5 bg-violet-600 hover:bg-violet-700 active:scale-[0.98] text-white rounded-2xl text-[12px] font-black uppercase tracking-widest transition-all shadow-lg shadow-violet-200"
            >
              <Camera size={16} /> Camera ကို ခွင့်ပြုမည်
            </button>
            <p className="text-[10px] text-slate-400 text-center">
              Browser permission popup ပေါ်လာပါက <strong>Allow</strong> ကို နှိပ်ပါ
            </p>
          </div>
        );

      case 'denied':
        return (
          <div className="flex flex-col items-center justify-center gap-5 px-8 py-10 bg-gradient-to-b from-red-50 to-white">
            <div className="w-20 h-20 rounded-3xl bg-red-100 flex items-center justify-center">
              <ShieldAlert size={36} className="text-red-500" />
            </div>
            <div className="text-center space-y-2">
              <p className="text-[13px] font-black text-slate-800">Camera ခွင့်ပြုချက် ငြင်းပယ်ခံရသည်</p>
              <p className="text-[11px] text-slate-500 leading-relaxed max-w-[230px]">
                Browser address bar ရှိ 🔒 icon ကို click/tap ပြီး Camera → <strong>Allow</strong> သို့ ပြောင်းပါ
              </p>
            </div>
            <div className="w-full space-y-2">
              <button onClick={requestCameraAccess} className="w-full flex items-center justify-center gap-2 py-3 bg-violet-600 hover:bg-violet-700 text-white rounded-xl text-[11px] font-black uppercase tracking-wide transition-all">
                <RefreshCw size={13} /> ထပ်ကြိုးစား
              </button>
              <button onClick={() => fileInputRef.current?.click()} className="w-full flex items-center justify-center gap-2 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-[11px] font-semibold hover:bg-slate-50 transition-all">
                <ImagePlus size={13} /> ဓာတ်ပုံဖြင့် Scan
              </button>
            </div>
          </div>
        );

      case 'unavailable':
        return (
          <div className="flex flex-col items-center justify-center gap-4 px-8 py-10 bg-slate-50">
            <div className="w-16 h-16 rounded-2xl bg-slate-200 flex items-center justify-center">
              <Camera size={28} className="text-slate-400" />
            </div>
            <div className="text-center">
              <p className="text-[13px] font-black text-slate-700 mb-1">Camera မရရှိပါ</p>
              <p className="text-[11px] text-red-500 font-semibold leading-snug">{streamError}</p>
            </div>
            <div className="w-full space-y-2">
              <button onClick={requestCameraAccess} className="w-full flex items-center justify-center gap-2 py-2.5 bg-violet-600 text-white rounded-xl text-[11px] font-black hover:bg-violet-700">
                <RefreshCw size={12} /> ထပ်ကြိုးစား
              </button>
              <button onClick={() => fileInputRef.current?.click()} className="w-full flex items-center justify-center gap-2 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-[11px] font-semibold hover:bg-slate-50">
                <ImagePlus size={13} /> ဓာတ်ပုံဖြင့် Scan
              </button>
            </div>
          </div>
        );

      case 'http':
        return (
          <div className="flex flex-col items-center justify-center gap-5 px-8 py-10 bg-gradient-to-b from-amber-50 to-white">
            <div className="w-20 h-20 rounded-3xl bg-amber-100 flex items-center justify-center">
              <ImagePlus size={36} className="text-amber-500" />
            </div>
            <div className="text-center space-y-1.5">
              <p className="text-[13px] font-black text-slate-800">Photo Scan Mode</p>
              <p className="text-[11px] text-slate-500 leading-relaxed max-w-[220px]">
                Real-time camera သည် HTTPS တွင်သာ အလုပ်လုပ်သည်။<br />
                ဓာတ်ပုံဖြင့် barcode / QR scan လုပ်နိုင်သည်
              </p>
            </div>
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={decoding}
              className="w-full flex items-center justify-center gap-2.5 py-3.5 bg-violet-600 hover:bg-violet-700 disabled:opacity-60 text-white rounded-2xl text-[12px] font-black uppercase tracking-widest transition-all shadow-lg shadow-violet-200"
            >
              {decoding ? <RefreshCw size={15} className="animate-spin" /> : <Camera size={15} />}
              {decoding ? 'Decoding…' : 'Camera ဖြင့် ဓာတ်ပုံရိုက်ပါ'}
            </button>
            {streamError && <p className="text-[11px] text-rose-500 font-semibold text-center">{streamError}</p>}
          </div>
        );

      default:
        return null;
    }
  };

  // ── Main render ────────────────────────────────────────────────────────────────
  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div className="bg-white rounded-3xl shadow-2xl w-full max-w-md overflow-hidden flex flex-col">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 bg-slate-50 border-b border-slate-100 shrink-0">
          <div className="flex items-center gap-2">
            <ScanLine size={16} className="text-violet-600" />
            <span className="text-[13px] font-black text-slate-800 uppercase tracking-tight">
              Barcode / QR Scanner
            </span>
            {permState === 'streaming' && (
              <span className={`px-2 py-0.5 text-[9px] font-black rounded-full flex items-center gap-1 ${
                scanEngine === 'native'
                  ? 'bg-emerald-100 text-emerald-700'
                  : 'bg-orange-100 text-orange-700'
              }`}>
                <span className={`w-1.5 h-1.5 rounded-full animate-pulse ${
                  scanEngine === 'native' ? 'bg-emerald-500' : 'bg-orange-500'
                }`} />
                {scanEngine === 'native' ? 'Native' : 'ZBar'} · Live
              </span>
            )}
            {permState === 'http' && (
              <span className="px-2 py-0.5 bg-amber-100 text-amber-700 text-[9px] font-black rounded-full uppercase">Photo</span>
            )}
          </div>
          <button onClick={onClose} className="p-1.5 hover:bg-slate-200 rounded-xl transition-all">
            <X size={18} className="text-slate-500" />
          </button>
        </div>

        {/* Video area */}
        {isSecure && (
          <div
            className="relative bg-black overflow-hidden"
            style={permState === 'streaming' ? { aspectRatio: '1/1' } : { height: 0 }}
          >
            <video ref={videoRef} className="w-full h-full object-cover" playsInline muted autoPlay />

            {permState === 'streaming' && (
              <>
                <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                  <div className="absolute inset-0 bg-black/40" />
                  <div className={`relative w-4/5 aspect-square z-10 transition-opacity duration-300 ${flash ? 'opacity-0' : 'opacity-100'}`}>
                    <div className="absolute inset-0 bg-transparent" style={{ boxShadow: '0 0 0 9999px rgba(0,0,0,0.45)' }} />
                    {[
                      'top-0 left-0 border-t-[3px] border-l-[3px]',
                      'top-0 right-0 border-t-[3px] border-r-[3px]',
                      'bottom-0 left-0 border-b-[3px] border-l-[3px]',
                      'bottom-0 right-0 border-b-[3px] border-r-[3px]',
                    ].map((cls, i) => (
                      <span key={i} className={`absolute w-8 h-8 border-violet-400 rounded-sm ${cls}`} />
                    ))}
                    <div className="absolute left-4 right-4 top-1/2 h-0.5 bg-gradient-to-r from-transparent via-violet-400 to-transparent animate-pulse" />
                  </div>
                </div>

                {flash && <div className="absolute inset-0 bg-violet-400/30 pointer-events-none" />}

                <div className="absolute bottom-2 left-0 right-0 flex items-center justify-between px-3">
                  <span className="px-3 py-1 bg-black/50 text-white/80 text-[10px] font-semibold rounded-full backdrop-blur-sm">
                    Barcode / QR ကို frame အတွင်း ထားပါ
                  </span>
                  <button
                    onClick={handleFlipCamera}
                    title={facingMode === 'environment' ? 'Front camera သို့ ပြောင်း' : 'Back camera သို့ ပြောင်း'}
                    className="p-2 bg-black/50 hover:bg-black/70 text-white rounded-full backdrop-blur-sm transition-all active:scale-95"
                  >
                    <FlipHorizontal size={18} />
                  </button>
                </div>

                {streamError && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/75 px-6 text-center gap-3">
                    <Camera size={30} className="text-slate-400" />
                    <p className="text-white text-[12px] font-semibold leading-snug">{streamError}</p>
                    <button
                      onClick={() => { setStreamError(''); startWithEngine(scanEngineRef.current, selectedCamera || undefined, facingMode); }}
                      className="px-4 py-2 bg-violet-600 text-white rounded-xl text-[11px] font-black flex items-center gap-1.5 hover:bg-violet-700"
                    >
                      <RefreshCw size={12} /> ထပ်ကြိုးစား
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {renderContent()}

        {/* Bottom bar */}
        <div className="px-5 py-4 space-y-3 shrink-0 border-t border-slate-100">

          {/* Engine toggle — only when native is available */}
          {nativeSupported && permState === 'streaming' && (
            <div className="flex items-center gap-2 p-1 bg-slate-100 rounded-2xl">
              <button
                onClick={() => handleSwitchEngine('native')}
                className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl text-[11px] font-black transition-all ${
                  scanEngine === 'native'
                    ? 'bg-emerald-500 text-white shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                <Zap size={12} />
                Native (မြန်ဆုံး)
              </button>
              <button
                onClick={() => handleSwitchEngine('zbar')}
                className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl text-[11px] font-black transition-all ${
                  scanEngine === 'zbar'
                    ? 'bg-orange-500 text-white shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                <Cpu size={12} />
                ZBar WASM
              </button>
            </div>
          )}

          {/* Last scanned code */}
          {lastCode && (
            <div className="flex items-center gap-2 px-3 py-2.5 bg-emerald-50 border border-emerald-200 rounded-xl">
              <ZoomIn size={13} className="text-emerald-600 shrink-0" />
              <span className="text-[11px] font-black text-emerald-700 truncate flex-1">{lastCode}</span>
              <span className="text-[9px] text-emerald-500 font-bold uppercase shrink-0">
                ✓{scanCount > 1 ? ` ×${scanCount}` : ''}
              </span>
            </div>
          )}

          {/* Camera selector */}
          {permState === 'streaming' && cameras.length > 1 && (
            <div className="flex items-center gap-2">
              <Camera size={13} className="text-slate-400 shrink-0" />
              <select
                value={selectedCamera}
                onChange={e => handleCameraSwitch(e.target.value)}
                className="flex-1 text-xs py-1.5 px-2.5 border border-slate-200 rounded-xl bg-white outline-none focus:border-violet-400"
              >
                {cameras.map(c => (
                  <option key={c.deviceId} value={c.deviceId}>
                    {c.label || `Camera ${c.deviceId.slice(0, 8)}`}
                  </option>
                ))}
              </select>
            </div>
          )}

          {permState === 'streaming' && !streamError && (
            <p className="text-[10px] text-slate-400 text-center">
              {scanEngine === 'native'
                ? 'OS Hardware Scanner · အမြန်ဆုံး'
                : 'ZBar C++ WASM · JS ထက် 3–5x မြန်'}
            </p>
          )}

          <button
            onClick={onClose}
            className="w-full py-2.5 rounded-xl bg-slate-100 text-slate-600 text-[11px] font-black uppercase tracking-widest hover:bg-slate-200 transition-all"
          >
            ပိတ်မည်
          </button>
        </div>

        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          className="hidden"
          onChange={handleFileChange}
        />
      </div>
    </div>
  );
};

export default BarcodeScannerCamera;
