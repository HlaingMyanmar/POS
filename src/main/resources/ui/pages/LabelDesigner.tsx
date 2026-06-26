import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft, Barcode, Bookmark, BookmarkCheck, Eye,
  Layers, Minus, Plus, Printer, QrCode, Settings, Trash2, Type, Usb, Wifi, X, ChevronDown, ChevronRight,
} from 'lucide-react';
import BarcodeLabel from '../components/BarcodeLabel';
import QrCodeLabel from '../components/QrCodeLabel';
import { productService } from '../services/productapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { AppRoute, ProductDTO, ProductSerialDTO, SerialStatus } from '../types';

// ─── Types ────────────────────────────────────────────────────────────────────

type DataField = 'NAME' | 'CODE' | 'PRICE' | 'SERIAL' | 'WARRANTY' | 'BRAND' | 'CATEGORY' | 'UNIT' | 'CUSTOM';

interface BaseEl  { id: string; x: number; y: number; w: number; h: number; }
interface BarcodeEl extends BaseEl {
  type: 'barcode'; field: DataField; customValue: string;
  barW: number; barH: number;
  codeTextSize: number; showCodeText: boolean;
  topLabel: DataField | ''; topFontSize: number; topAlign: 'left' | 'center' | 'right';
  botLabel: DataField | ''; botFontSize: number; botAlign: 'left' | 'center' | 'right';
}
interface QrEl extends BaseEl {
  type: 'qr'; field: DataField; customValue: string;
  qrPx: number;
  topLabel: DataField | ''; topFontSize: number; topAlign: 'left' | 'center' | 'right';
  botLabel: DataField | ''; botFontSize: number; botAlign: 'left' | 'center' | 'right';
}
interface TextEl   extends BaseEl {
  type: 'text'; field: DataField; customValue: string;
  fontSize: number; bold: boolean; italic: boolean;
  align: 'left' | 'center' | 'right'; color: string;
}
type DesignEl = BarcodeEl | QrEl | TextEl;
interface PrintEntry { product: ProductDTO; serial?: string; }

type PaperKey = 'A4' | '4x6in' | '4x4in' | '2x4in' | 'custom';

interface DesignerPreset {
  id: string; name: string;
  labelW: number; labelH: number;
  paperKey: PaperKey; customPaperW: number; customPaperH: number;
  marginTop: number; marginBottom: number; marginLeft: number; marginRight: number;
  gapH: number; gapV: number;
  useManualGrid: boolean; manualCols: number; manualRows: number;
  elements: DesignEl[];
}

interface PrinterPaper { label: string; w: number; h: number; }
interface PrinterProfile {
  vendorId: number;
  brand: string;
  model: string;
  protocol: 'ZPL' | 'TSPL' | 'EPL2' | 'CPCL' | 'GENERIC';
  dpi: number;
  maxWidthMm: number;
  defaultMarginMm: number;
  papers: PrinterPaper[];
}

type SizeUnit = 'mm' | 'in';

// ─── Constants ────────────────────────────────────────────────────────────────

const PAPER_DEFS: Record<Exclude<PaperKey,'custom'>, { label: string; w: number; h: number }> = {
  'A4':    { label: 'A4  (210 × 297 mm)',           w: 210,   h: 297   },
  '4x6in': { label: '4 × 6 in  (101.6 × 152.4 mm)', w: 101.6, h: 152.4 },
  '4x4in': { label: '4 × 4 in  (101.6 × 101.6 mm)', w: 101.6, h: 101.6 },
  '2x4in': { label: '2 × 4 in  (50.8 × 101.6 mm)',  w: 50.8,  h: 101.6 },
};

const LABEL_PRESETS = [
  { label: '40 × 30 mm',  w: 40,  h: 30 },
  { label: '40 × 33 mm',  w: 40,  h: 33 },
  { label: '50 × 25 mm',  w: 50,  h: 25 },
  { label: '50 × 30 mm',  w: 50,  h: 30 },
  { label: '60 × 40 mm',  w: 60,  h: 40 },
  { label: '100 × 50 mm', w: 100, h: 50 },
];

const ROLL_WIDTHS = [
  { label: '40 mm',  w: 40  },
  { label: '50 mm',  w: 50  },
  { label: '58 mm',  w: 58  },
  { label: '60 mm',  w: 60  },
  { label: '62 mm',  w: 62  },
  { label: '76 mm',  w: 76  },
  { label: '80 mm',  w: 80  },
  { label: '100 mm', w: 100 },
  { label: '110 mm', w: 110 },
];

const FIELD_OPTIONS: { value: DataField; label: string }[] = [
  { value: 'CODE',     label: 'Product Code'  },
  { value: 'NAME',     label: 'Product Name'  },
  { value: 'PRICE',    label: 'Selling Price' },
  { value: 'SERIAL',   label: 'Serial No.'    },
  { value: 'WARRANTY', label: 'Warranty'      },
  { value: 'BRAND',    label: 'Brand'         },
  { value: 'CATEGORY', label: 'Category'      },
  { value: 'UNIT',     label: 'Unit'          },
  { value: 'CUSTOM',   label: 'Custom Text'   },
];

const PRESET_KEY = 'sspd_label_designer_presets';
const loadPresets  = (): DesignerPreset[] => { try { return JSON.parse(localStorage.getItem(PRESET_KEY) || '[]'); } catch { return []; } };
const storePresets = (p: DesignerPreset[]) => localStorage.setItem(PRESET_KEY, JSON.stringify(p));

// ─── Printer Database ──────────────────────────────────────────────────────────

const PRINTER_DB: PrinterProfile[] = [
  {
    vendorId: 0x0A5F, brand: 'Zebra', model: 'ZD / GK / GX Series', protocol: 'ZPL', dpi: 203, maxWidthMm: 108, defaultMarginMm: 2,
    papers: [
      { label: '2" × 1"   (50.8 × 25.4 mm)',  w: 50.8,  h: 25.4  },
      { label: '2" × 1.5" (50.8 × 38.1 mm)',  w: 50.8,  h: 38.1  },
      { label: '2" × 2"   (50.8 × 50.8 mm)',  w: 50.8,  h: 50.8  },
      { label: '4" × 2"   (101.6 × 50.8 mm)', w: 101.6, h: 50.8  },
      { label: '4" × 3"   (101.6 × 76.2 mm)', w: 101.6, h: 76.2  },
      { label: '4" × 4"   (101.6 × 101.6 mm)',w: 101.6, h: 101.6 },
      { label: '4" × 6"   (101.6 × 152.4 mm)',w: 101.6, h: 152.4 },
    ],
  },
  {
    vendorId: 0x0922, brand: 'DYMO', model: 'LabelWriter 400 / 450 / 550', protocol: 'GENERIC', dpi: 300, maxWidthMm: 56, defaultMarginMm: 1,
    papers: [
      { label: 'Address Label   (89 × 28 mm)',  w: 89,  h: 28  },
      { label: 'Shipping Label  (54 × 101 mm)', w: 54,  h: 101 },
      { label: 'Large Label     (89 × 36 mm)',  w: 89,  h: 36  },
      { label: 'Name Badge      (89 × 41 mm)',  w: 89,  h: 41  },
      { label: '2½" × 2½"      (64 × 64 mm)',  w: 64,  h: 64  },
    ],
  },
  {
    vendorId: 0x04F9, brand: 'Brother', model: 'QL-700 / QL-800 / QL-1100', protocol: 'GENERIC', dpi: 300, maxWidthMm: 62, defaultMarginMm: 1,
    papers: [
      { label: '29 × 90 mm',   w: 29, h: 90  },
      { label: '38 × 90 mm',   w: 38, h: 90  },
      { label: '62 × 29 mm',   w: 62, h: 29  },
      { label: '62 × 100 mm',  w: 62, h: 100 },
      { label: '62 × 150 mm',  w: 62, h: 150 },
    ],
  },
  {
    vendorId: 0x1203, brand: 'TSC', model: 'TDP / TTP / DA Series', protocol: 'TSPL', dpi: 203, maxWidthMm: 108, defaultMarginMm: 2,
    papers: [
      { label: '40 × 30 mm',  w: 40,  h: 30  },
      { label: '50 × 25 mm',  w: 50,  h: 25  },
      { label: '50 × 30 mm',  w: 50,  h: 30  },
      { label: '60 × 40 mm',  w: 60,  h: 40  },
      { label: '100 × 50 mm', w: 100, h: 50  },
      { label: '4" × 6"  (101.6 × 152.4 mm)', w: 101.6, h: 152.4 },
    ],
  },
  {
    vendorId: 0x0FE6, brand: 'Xprinter', model: 'XP-365B / XP-420B / XP-460B', protocol: 'TSPL', dpi: 203, maxWidthMm: 108, defaultMarginMm: 2,
    papers: [
      { label: '40 × 30 mm',  w: 40,  h: 30  },
      { label: '50 × 25 mm',  w: 50,  h: 25  },
      { label: '60 × 40 mm',  w: 60,  h: 40  },
      { label: '80 × 40 mm',  w: 80,  h: 40  },
      { label: '100 × 50 mm', w: 100, h: 50  },
      { label: '4" × 6"  (101.6 × 152.4 mm)', w: 101.6, h: 152.4 },
    ],
  },
  {
    vendorId: 0x0DD4, brand: 'HPRT', model: 'N41 / HM-A300 / TP808', protocol: 'TSPL', dpi: 203, maxWidthMm: 108, defaultMarginMm: 2,
    papers: [
      { label: '40 × 30 mm',  w: 40,  h: 30  },
      { label: '50 × 25 mm',  w: 50,  h: 25  },
      { label: '60 × 40 mm',  w: 60,  h: 40  },
      { label: '100 × 50 mm', w: 100, h: 50  },
      { label: '4" × 6"  (101.6 × 152.4 mm)', w: 101.6, h: 152.4 },
    ],
  },
  {
    vendorId: 0x0525, brand: 'Rollo / Munbyn', model: 'X1038 / ITPP941', protocol: 'ZPL', dpi: 203, maxWidthMm: 108, defaultMarginMm: 2,
    papers: [
      { label: '4" × 6"  (101.6 × 152.4 mm)', w: 101.6, h: 152.4 },
      { label: '4" × 4"  (101.6 × 101.6 mm)', w: 101.6, h: 101.6 },
      { label: '4" × 2"  (101.6 × 50.8 mm)',  w: 101.6, h: 50.8  },
      { label: '2" × 4"  (50.8 × 101.6 mm)',  w: 50.8,  h: 101.6 },
    ],
  },
  {
    vendorId: 0x28E9, brand: 'Argox', model: 'OS / CP / IX Series', protocol: 'TSPL', dpi: 203, maxWidthMm: 108, defaultMarginMm: 2,
    papers: [
      { label: '40 × 30 mm',  w: 40,  h: 30  },
      { label: '50 × 25 mm',  w: 50,  h: 25  },
      { label: '60 × 40 mm',  w: 60,  h: 40  },
      { label: '100 × 50 mm', w: 100, h: 50  },
    ],
  },
];

const MANUAL_BRANDS = PRINTER_DB.map(p => p.brand);

// ─── Helpers ──────────────────────────────────────────────────────────────────

const uid = () => Math.random().toString(36).slice(2, 9);
const MM_PER_INCH = 25.4;
const roundMm = (v: number) => Math.round(v * 100) / 100;
const fmtDim = (v: number) => Number(v.toFixed(2)).toString();
const fmtIn = (mm: number) => fmtDim(mm / MM_PER_INCH);
const fmtMmIn = (mm: number) => `${fmtDim(mm)} mm (${fmtIn(mm)} in)`;
const fmtSizeMmIn = (w: number, h: number) => `${fmtDim(w)} × ${fmtDim(h)} mm (${fmtIn(w)} × ${fmtIn(h)} in)`;
const mmToIn = (mm: number) => mm / MM_PER_INCH;
const inToMm = (inch: number) => inch * MM_PER_INCH;
const fmtMoney    = (v: number) => new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(v || 0);
const fmtWarranty = (p: ProductDTO) => {
  const t = String(p.warrantyTerms || '').trim();
  if (t) return t;
  const m = Number(p.warrantyMonths || 0);
  return m > 0 ? `${m} mo` : 'No warranty';
};
const resolveField = (field: DataField, custom: string, product: ProductDTO, serial?: string): string => {
  switch (field) {
    case 'NAME':     return product.name;
    case 'CODE':     return product.productCode;
    case 'PRICE':    return fmtMoney(product.sellingPrice);
    case 'SERIAL':   return serial || product.productCode;
    case 'WARRANTY': return fmtWarranty(product);
    case 'BRAND':    return product.brandName  || '';
    case 'CATEGORY': return product.categoryName || '';
    case 'UNIT':     return product.unitName   || '';
    case 'CUSTOM':   return custom;
  }
};
const getStock = (p: ProductDTO, sers: ProductSerialDTO[]) =>
  p.hasSerial ? sers.filter(s => s.productId === p.id).length
              : Number(p.stockQty ?? p.currentStock) || 0;

// ─── NumField helper ──────────────────────────────────────────────────────────

const LABEL_PRESET_OPTIONS = LABEL_PRESETS.map(s => ({
  ...s,
  displayLabel: fmtSizeMmIn(s.w, s.h),
}));

const ROLL_WIDTH_OPTIONS = ROLL_WIDTHS.map(r => ({
  ...r,
  displayLabel: fmtMmIn(r.w),
}));

const NF = ({ lbl, v, fn, min = 0, max = 9999, step = 1 }: {
  lbl: string; v: number; fn: (n: number) => void; min?: number; max?: number; step?: number;
}) => (
  <label className="flex flex-col gap-0.5">
    <span className="text-slate-400 text-xs">{lbl}</span>
    <input type="number" className="border rounded px-1.5 py-0.5 text-xs w-full"
      value={v} min={min} max={max} step={step}
      onChange={e => fn(Number(e.target.value))} />
  </label>
);

const UF = ({ lbl, mm, fn, unit, minMm = 0, maxMm = 9999, stepMm = 1 }: {
  lbl: string; mm: number; fn: (n: number) => void; unit: SizeUnit; minMm?: number; maxMm?: number; stepMm?: number;
}) => {
  const value = unit === 'in' ? mmToIn(mm) : mm;
  const min = unit === 'in' ? mmToIn(minMm) : minMm;
  const max = unit === 'in' ? mmToIn(maxMm) : maxMm;
  const step = unit === 'in' ? mmToIn(stepMm) : stepMm;
  return (
    <label className="flex flex-col gap-0.5">
      <span className="text-slate-400 text-xs">{lbl} ({unit})</span>
      <input
        type="number"
        className="border rounded px-1.5 py-0.5 text-xs w-full"
        value={Number(value.toFixed(unit === 'in' ? 3 : 2))}
        min={min}
        max={max}
        step={step}
        onChange={e => fn(roundMm(unit === 'in' ? inToMm(Number(e.target.value)) : Number(e.target.value)))}
      />
    </label>
  );
};

// ─── Component ────────────────────────────────────────────────────────────────

const LabelDesigner: React.FC = () => {
  const navigate = useNavigate();

  const [products,  setProducts]  = useState<ProductDTO[]>([]);
  const [serials,   setSerials]   = useState<ProductSerialDTO[]>([]);
  const [loading,   setLoading]   = useState(true);
  const [search,    setSearch]    = useState('');

  const [labelW, setLabelW] = useState(50);
  const [labelH, setLabelH] = useState(30);

  const [paperKey,     setPaperKey]     = useState<PaperKey>('A4');
  const [customPaperW, setCustomPaperW] = useState(210);
  const [customPaperH, setCustomPaperH] = useState(297);
  const [marginTop,    setMarginTop]    = useState(5);
  const [marginBottom, setMarginBottom] = useState(5);
  const [marginLeft,   setMarginLeft]   = useState(5);
  const [marginRight,  setMarginRight]  = useState(5);
  const [gapH, setGapH] = useState(2);
  const [gapV, setGapV] = useState(2);
  const [useManualGrid, setUseManualGrid] = useState(false);

  // Roll mode
  const [paperType,   setPaperType]   = useState<'sheet' | 'roll'>('sheet');
  const [rollWidth,   setRollWidth]   = useState(50);   // mm
  const [rollGap,     setRollGap]     = useState(3);    // mm gap between labels on roll
  const [manualCols,    setManualCols]    = useState(3);
  const [manualRows,    setManualRows]    = useState(9);

  const [elements,   setElements]   = useState<DesignEl[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [previewProductId, setPreviewProductId] = useState<number | null>(null);
  const [previewSerial,    setPreviewSerial]    = useState('SERIAL-001');

  const [printIds,     setPrintIds]     = useState<Set<number>>(new Set());
  const [printCopies,  setPrintCopies]  = useState<Record<number, number>>({});
  // For hasSerial products: which specific serial numbers are checked for printing
  const [printSerials, setPrintSerials] = useState<Record<number, Set<string>>>({});

  const [presets,       setPresets]       = useState<DesignerPreset[]>(loadPresets);
  const [showSaveDlg,   setShowSaveDlg]   = useState(false);
  const [newPresetName, setNewPresetName] = useState('');

  // Zoom: 0.25 – 4.0
  const [zoom, setZoom] = useState(1.0);

  // Page navigation
  const [currentPage, setCurrentPage] = useState(1);

  // Printer connection
  const [printerProfile,   setPrinterProfile]   = useState<PrinterProfile | null>(null);
  const [printerConnected, setPrinterConnected] = useState(false);
  const [printerError,     setPrinterError]     = useState('');
  const [manualBrand,      setManualBrand]      = useState(PRINTER_DB[0].brand);
  const [printerMarginMm,  setPrinterMarginMm]  = useState(2);
  const [printerPaperInfo, setPrinterPaperInfo] = useState('');
  const [showPrinterSettings, setShowPrinterSettings] = useState(false);
  const [selectedPrinterPaper, setSelectedPrinterPaper] = useState<PrinterPaper | null>(null);
  const webUsbSupported = typeof navigator !== 'undefined' && 'usb' in navigator;
  
  // Persist printer settings
  const PRINTER_KEY = 'sspd_label_designer_printer';
  const savePrinterSettings = (profile: PrinterProfile | null, margin: number) => {
    if (profile) {
      localStorage.setItem(PRINTER_KEY, JSON.stringify({ brand: profile.brand, margin }));
    } else {
      localStorage.removeItem(PRINTER_KEY);
    }
  };
  const loadPrinterSettings = (): { brand: string; margin: number } | null => {
    try {
      const data = localStorage.getItem(PRINTER_KEY);
      return data ? JSON.parse(data) : null;
    } catch { return null; }
  };

  const [rightTab, setRightTab] = useState<'layout' | 'props' | 'printer'>('layout');
  const [printOpen, setPrintOpen] = useState(true);
  const [layersOpen, setLayersOpen] = useState(true);
  const [sizeUnit, setSizeUnit] = useState<SizeUnit>('in');

  const dragRef = useRef<{ elId: string; startX: number; startY: number; origX: number; origY: number; scale: number } | null>(null);
  const resizeRef = useRef<{ elId: string; handle: 'nw'|'ne'|'sw'|'se'; startX: number; startY: number; origEl: DesignEl; scale: number } | null>(null);
  const canvasRef = useRef<HTMLDivElement>(null);

  // ── Load ─────────────────────────────────────────────────────────────────
  useEffect(() => {
    void (async () => {
      try {
        const [prods, sers] = await Promise.all([productService.getAll(), productSerialService.getAll()]);
        setProducts(prods);
        setSerials(sers.filter(s => s.status === SerialStatus.AVAILABLE));
        if (prods.length > 0) setPreviewProductId(prods[0].id);
      } finally { setLoading(false); }
    })();
    // Load saved printer settings
    const saved = loadPrinterSettings();
    if (saved) {
      const profile = PRINTER_DB.find(p => p.brand === saved.brand);
      if (profile) {
        setPrinterProfile(profile);
        setPrinterMarginMm(saved.margin);
      }
    }
  }, []);

  // ── Derived ──────────────────────────────────────────────────────────────
  const ROLL_PREVIEW_COUNT = 3; // number of labels shown stacked in roll preview

  const activePaper = useMemo(() => {
    if (paperType === 'roll') return { label: `Roll ${fmtMmIn(rollWidth)}`, w: rollWidth, h: labelH * ROLL_PREVIEW_COUNT + rollGap * (ROLL_PREVIEW_COUNT - 1) };
    if (paperKey === 'custom') return { label: `Custom (${fmtSizeMmIn(customPaperW, customPaperH)})`, w: customPaperW, h: customPaperH };
    return PAPER_DEFS[paperKey as Exclude<PaperKey,'custom'>];
  }, [paperType, rollWidth, rollGap, labelH, paperKey, customPaperW, customPaperH]);

  // base scale to fit paper in ~400px wide (roll) or 540px (sheet), then multiply by zoom
  const baseScale  = useMemo(() => {
    if (paperType === 'roll') return Math.min(400 / activePaper.w, 600 / activePaper.h);
    return Math.min(540 / activePaper.w, 680 / activePaper.h);
  }, [paperType, activePaper]);
  const canvasScale = baseScale * zoom;

  const paperPxW = activePaper.w * canvasScale;
  const paperPxH = activePaper.h * canvasScale;

  // Roll mode: no margins; Sheet mode: use margin settings
  const originX  = paperType === 'roll' ? 0 : marginLeft * canvasScale;
  const originY  = paperType === 'roll' ? 0 : marginTop  * canvasScale;

  // Effective label width for bounds checking
  const activeW = paperType === 'roll' ? rollWidth : labelW;

  const labelsPerPage = useMemo(() => {
    if (paperType === 'roll') return { cols: 1, rows: 1, total: 1 };
    if (useManualGrid) return { cols: manualCols, rows: manualRows, total: manualCols * manualRows };
    const cw   = Math.max(0, activePaper.w - marginLeft - marginRight);
    const ch   = Math.max(0, activePaper.h - marginTop  - marginBottom);
    const cols = Math.max(1, Math.floor((cw + gapH) / (labelW + gapH)));
    const rows = Math.max(1, Math.floor((ch + gapV) / (labelH + gapV)));
    return { cols, rows, total: cols * rows };
  }, [paperType, useManualGrid, manualCols, manualRows, activePaper, marginLeft, marginRight, marginTop, marginBottom, gapH, gapV, labelW, labelH]);

  const stepX = (labelW + gapH) * canvasScale;
  const stepY = (labelH + gapV) * canvasScale;

  const previewProduct = useMemo(
    () => products.find(p => p.id === previewProductId) ?? products[0] ?? null,
    [products, previewProductId],
  );
  const selectedEl = elements.find(e => e.id === selectedId) ?? null;

  const filteredProducts = useMemo(() => {
    const q = search.toLowerCase();
    return products.filter(p => p.name.toLowerCase().includes(q) || p.productCode.toLowerCase().includes(q));
  }, [products, search]);

  const printEntries = useMemo<PrintEntry[]>(() => {
    const out: PrintEntry[] = [];
    for (const id of Array.from(printIds)) {
      const product = products.find(p => p.id === id);
      if (!product) continue;
      if (product.hasSerial) {
        // Use explicitly selected serials
        const selected = printSerials[id] ?? new Set<string>();
        selected.forEach(sn => out.push({ product, serial: sn }));
      } else {
        const maxQty = getStock(product, serials);
        const copies = Math.min(printCopies[id] || 1, maxQty || 1);
        for (let i = 0; i < copies; i++) out.push({ product });
      }
    }
    return out;
  }, [printIds, printCopies, printSerials, products, serials]);

  const totalPages = printEntries.length > 0
    ? Math.max(1, Math.ceil(printEntries.length / labelsPerPage.total))
    : 1;

  // Clamp currentPage when totalPages shrinks
  useEffect(() => {
    setCurrentPage(p => Math.min(p, totalPages));
  }, [totalPages]);

  const resizeLabelCanvas = (nextW: number, nextH: number, opts?: { syncRollWidth?: boolean }) => {
    const safeW = roundMm(Math.max(5, nextW));
    const safeH = roundMm(Math.max(5, nextH));
    const prevW = Math.max(labelW, 1);
    const prevH = Math.max(labelH, 1);
    const sx = safeW / prevW;
    const sy = safeH / prevH;
    const sf = Math.sqrt(sx * sy);

    setLabelW(safeW);
    setLabelH(safeH);
    if (opts?.syncRollWidth) setRollWidth(safeW);

    if (safeW === labelW && safeH === labelH) return;

    setElements(prev => prev.map(el => {
      const base = {
        ...el,
        x: roundMm(el.x * sx),
        y: roundMm(el.y * sy),
        w: roundMm(Math.max(3, el.w * sx)),
        h: roundMm(Math.max(3, el.h * sy)),
      };
      if (el.type === 'barcode') {
        return {
          ...base,
          barH: Math.max(10, Math.round(el.barH * sy)),
          codeTextSize: Math.max(5, Math.round((el.codeTextSize ?? 8) * sf)),
          topFontSize: Math.max(5, Math.round((el.topFontSize ?? 8) * sf)),
          botFontSize: Math.max(5, Math.round((el.botFontSize ?? 8) * sf)),
        } as DesignEl;
      }
      if (el.type === 'qr') {
        return {
          ...base,
          qrPx: Math.max(40, Math.round(el.qrPx * Math.max(sx, sy))),
          topFontSize: Math.max(5, Math.round((el.topFontSize ?? 8) * sf)),
          botFontSize: Math.max(5, Math.round((el.botFontSize ?? 8) * sf)),
        } as DesignEl;
      }
      return {
        ...base,
        fontSize: Math.max(6, Math.round(el.fontSize * sf)),
      } as DesignEl;
    }));
  };

  // ── Element ops ──────────────────────────────────────────────────────────
  const addElement = (type: DesignEl['type']) => {
    const id = uid();
    let el: DesignEl;
    if (type === 'barcode') {
      el = { id, type, x: 2, y: labelH/2-7, w: labelW-4, h: 14, field: 'CODE', customValue: '', barW: 1.2, barH: 30, codeTextSize: 8, showCodeText: true, topLabel: '', topFontSize: 8, topAlign: 'center', botLabel: 'NAME', botFontSize: 8, botAlign: 'center' };
    } else if (type === 'qr') {
      el = { id, type, x: labelW/2-10, y: labelH/2-10, w: 20, h: 20, field: 'CODE', customValue: '', qrPx: 80, topLabel: '', topFontSize: 8, topAlign: 'center', botLabel: 'NAME', botFontSize: 8, botAlign: 'center' };
    } else {
      el = { id, type, x: 2, y: labelH/2-3, w: labelW-4, h: 6, field: 'NAME', customValue: '', fontSize: 10, bold: false, italic: false, align: 'center', color: '#000000' };
    }
    setElements(prev => [...prev, el]);
    setSelectedId(id);
    setRightTab('props');
  };

  const updateEl = (id: string, patch: Partial<DesignEl>) =>
    setElements(prev => prev.map(e => e.id === id ? { ...e, ...patch } as DesignEl : e));
  const deleteEl = (id: string) => { setElements(prev => prev.filter(e => e.id !== id)); if (selectedId === id) setSelectedId(null); };

  // ── Preset ops ───────────────────────────────────────────────────────────
  const handleSavePreset = () => {
    const name = newPresetName.trim(); if (!name) return;
    const preset: DesignerPreset = { id: uid(), name, labelW, labelH, paperKey, customPaperW, customPaperH, marginTop, marginBottom, marginLeft, marginRight, gapH, gapV, useManualGrid, manualCols, manualRows, elements };
    const updated = [...presets, preset]; setPresets(updated); storePresets(updated);
    setShowSaveDlg(false); setNewPresetName('');
  };
  const handleLoadPreset = (p: DesignerPreset) => {
    resizeLabelCanvas(p.labelW, p.labelH, { syncRollWidth: paperType === 'roll' });
    setPaperKey(p.paperKey);
    setCustomPaperW(p.customPaperW); setCustomPaperH(p.customPaperH);
    setMarginTop(p.marginTop); setMarginBottom(p.marginBottom); setMarginLeft(p.marginLeft); setMarginRight(p.marginRight);
    setGapH(p.gapH); setGapV(p.gapV); setUseManualGrid(p.useManualGrid);
    setManualCols(p.manualCols); setManualRows(p.manualRows);
    setElements(p.elements); setSelectedId(null);
  };
  const handleDeletePreset = (id: string) => { const u = presets.filter(p => p.id !== id); setPresets(u); storePresets(u); };

  // ── Printer ops ───────────────────────────────────────────────────────────
  const applyPrinterPaper = (paper: PrinterPaper, profile: PrinterProfile) => {
    // Validate paper size against printer capabilities
    if (paper.w > profile.maxWidthMm) {
      setPrinterError(`Paper width ${paper.w}mm exceeds printer max width ${profile.maxWidthMm}mm`);
      return;
    }

    // If the paper width fits a standard roll width, switch to roll mode
    const isRollSize = profile.maxWidthMm <= 120 && paper.h <= 200;
    if (isRollSize) {
      setPaperType('roll');
      setRollWidth(paper.w);
      resizeLabelCanvas(paper.w, paper.h, { syncRollWidth: true });
      setRollGap(profile.defaultMarginMm);
      setPrinterMarginMm(profile.defaultMarginMm);
      setPrinterPaperInfo(`Roll: ${paper.label} · DPI: ${profile.dpi} · Margin: ${profile.defaultMarginMm}mm`);
      setSelectedPrinterPaper(paper);
      setPrinterError('');
      savePrinterSettings(profile, profile.defaultMarginMm);
    } else {
      // Sheet mode
      setPaperType('sheet');
      const m = profile.defaultMarginMm;
      setPaperKey('custom');
      setCustomPaperW(paper.w);
      setCustomPaperH(paper.h);
      setMarginTop(m);
      setMarginBottom(m);
      setMarginLeft(m);
      setMarginRight(m);
      setGapH(2);
      setGapV(2);
      setUseManualGrid(false);
      setPrinterMarginMm(m);
      const labelW = Math.max(5, Math.round((paper.w - m * 2) * 10) / 10);
      const labelH = Math.max(5, Math.round((paper.h - m * 2) * 10) / 10);
      resizeLabelCanvas(labelW, labelH);
      setPrinterPaperInfo(`Sheet: ${paper.label} · DPI: ${profile.dpi} · Margins: ${m}mm`);
      setSelectedPrinterPaper(paper);
      setPrinterError('');
      savePrinterSettings(profile, m);
    }
  };

  const connectUsbPrinter = async () => {
    setPrinterError('');
    try {
      const usb = (navigator as any).usb as USB;
      const device: USBDevice = await usb.requestDevice({ filters: [] });
      const profile = PRINTER_DB.find(p => p.vendorId === device.vendorId) ?? null;
      setPrinterProfile(profile);
      setPrinterConnected(true);
      if (profile) {
        setPrinterError('');
        savePrinterSettings(profile, printerMarginMm);
      } else {
        setPrinterError(`Unknown printer (VendorID: 0x${device.vendorId.toString(16).toUpperCase()}). Select manually below.`);
      }
    } catch (e: any) {
      if (e?.name !== 'NotFoundError') setPrinterError('Connection failed: ' + (e?.message || String(e)));
    }
  };

  const applyManualPrinter = () => {
    const profile = PRINTER_DB.find(p => p.brand === manualBrand);
    if (profile) {
      setPrinterProfile(profile);
      setPrinterConnected(false);
      setPrinterError('');
      setPrinterMarginMm(profile.defaultMarginMm);
      savePrinterSettings(profile, profile.defaultMarginMm);
    }
  };

  const removePrinterProfile = () => {
    setPrinterProfile(null);
    setPrinterConnected(false);
    setPrinterError('');
    setPrinterPaperInfo('');
    setSelectedPrinterPaper(null);
    savePrinterSettings(null, 0);
  };

  // ── Mouse ────────────────────────────────────────────────────────────────
  const onCanvasMouseDown = (e: React.MouseEvent) => { if (e.target === canvasRef.current) setSelectedId(null); };
  const onElMouseDown = (e: React.MouseEvent, elId: string) => {
    e.stopPropagation(); setSelectedId(elId); setRightTab('props');
    const el = elements.find(x => x.id === elId); if (!el) return;
    dragRef.current = { elId, startX: e.clientX, startY: e.clientY, origX: el.x, origY: el.y, scale: canvasScale };
  };
  const onHandleMouseDown = (e: React.MouseEvent, elId: string, handle: 'nw'|'ne'|'sw'|'se') => {
    e.stopPropagation(); e.preventDefault();
    const el = elements.find(x => x.id === elId); if (!el) return;
    resizeRef.current = { elId, handle, startX: e.clientX, startY: e.clientY, origEl: { ...el }, scale: canvasScale };
  };
  const onMouseMove = (e: React.MouseEvent) => {
    if (dragRef.current) {
      const { elId, startX, startY, origX, origY, scale } = dragRef.current;
      const dx = (e.clientX - startX) / scale, dy = (e.clientY - startY) / scale;
      setElements(prev => prev.map(el => el.id !== elId ? el : {
        ...el, x: Math.max(0, Math.min(activeW - el.w, origX + dx)), y: Math.max(0, Math.min(labelH - el.h, origY + dy)),
      }));
    } else if (resizeRef.current) {
      const { elId, handle, startX, startY, origEl, scale } = resizeRef.current;
      const dx = (e.clientX - startX) / scale, dy = (e.clientY - startY) / scale;
      setElements(prev => prev.map(el => {
        if (el.id !== elId) return el;
        let { x, y, w, h } = origEl;
        if (handle==='se') { w=Math.max(5,w+dx); h=Math.max(3,h+dy); }
        if (handle==='sw') { const nx=Math.min(origEl.x+origEl.w-5,origEl.x+dx); w=origEl.w-(nx-origEl.x); x=nx; h=Math.max(3,h+dy); }
        if (handle==='ne') { w=Math.max(5,w+dx); const ny=Math.min(origEl.y+origEl.h-3,origEl.y+dy); h=origEl.h-(ny-origEl.y); y=ny; }
        if (handle==='nw') { const nx=Math.min(origEl.x+origEl.w-5,origEl.x+dx); w=origEl.w-(nx-origEl.x); x=nx; const ny=Math.min(origEl.y+origEl.h-3,origEl.y+dy); h=origEl.h-(ny-origEl.y); y=ny; }
        return { ...el, x:Math.max(0,x), y:Math.max(0,y), w:Math.max(5,w), h:Math.max(3,h) };
      }));
    }
  };
  const onMouseUp = () => { dragRef.current = null; resizeRef.current = null; };

  const togglePrint = (id: number) => {
    const isRemoving = printIds.has(id);
    const product = products.find(p => p.id === id);
    setPrintIds(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n; });
    if (!isRemoving && product?.hasSerial) {
      // Pre-select all available serials for this product
      const avail = serials.filter(s => s.productId === id);
      setPrintSerials(prev => ({ ...prev, [id]: new Set(avail.map(s => s.serialNumber)) }));
    }
    setPrintCopies(prev => prev[id] ? prev : { ...prev, [id]: 1 });
  };

  // ── Canvas element renderer (inner content) ───────────────────────────────
  const elInner = (el: DesignEl, val: string, prod?: ProductDTO | null, ser?: string): React.ReactNode => {
    if (el.type === 'barcode') {
      const top = el.topLabel && prod ? resolveField(el.topLabel as DataField, '', prod, ser) : undefined;
      const bot = el.botLabel && prod ? resolveField(el.botLabel as DataField, '', prod, ser) : el.botLabel ? el.botLabel : undefined;
      return (
        <BarcodeLabel
          value={val || 'PREVIEW'}
          width={el.barW} height={el.barH}
          fontSize={el.codeTextSize ?? 8}
          showCodeText={el.showCodeText ?? true}
          label={top} subLabel={bot}
          labelFontSize={el.topFontSize ?? 8}
          subLabelFontSize={el.botFontSize ?? 8}
          labelAlign={el.topAlign ?? 'center'}
          subLabelAlign={el.botAlign ?? 'center'}
        />
      );
    }
    if (el.type === 'qr') {
      const top = el.topLabel && prod ? resolveField(el.topLabel as DataField, '', prod, ser) : undefined;
      const bot = el.botLabel && prod ? resolveField(el.botLabel as DataField, '', prod, ser) : el.botLabel ? el.botLabel : undefined;
      return (
        <QrCodeLabel
          value={val || 'PREVIEW'}
          pixelSize={el.qrPx}
          label={top} subLabel={bot}
          labelFontSize={el.topFontSize ?? 8}
          subLabelFontSize={el.botFontSize ?? 8}
          labelAlign={el.topAlign ?? 'center'}
          subLabelAlign={el.botAlign ?? 'center'}
        />
      );
    }
    return (
      <div style={{
        fontSize: el.fontSize, fontWeight: el.bold ? 'bold' : 'normal',
        fontStyle: el.italic ? 'italic' : 'normal', textAlign: el.align, color: el.color,
        width: '100%', height: '100%', display: 'flex', alignItems: 'center',
        padding: '0 2px', boxSizing: 'border-box', lineHeight: 1.1, overflow: 'hidden', userSelect: 'none',
      }}>{val || ' '}</div>
    );
  };

  // ── Print element renderer ────────────────────────────────────────────────
  const renderPrintEl = (el: DesignEl, product: ProductDTO, serial?: string) => {
    const val = resolveField(el.field, el.customValue, product, serial);
    const s: React.CSSProperties = { position:'absolute', left:`${el.x}mm`, top:`${el.y}mm`, width:`${el.w}mm`, height:`${el.h}mm`, overflow:'hidden', boxSizing:'border-box' };
    if (el.type==='barcode') {
      const top = el.topLabel ? resolveField(el.topLabel as DataField, '', product, serial) : undefined;
      const bot = el.botLabel ? resolveField(el.botLabel as DataField, '', product, serial) : undefined;
      return <div key={el.id} style={s}><BarcodeLabel value={val||product.productCode} width={el.barW} height={el.barH} fontSize={el.codeTextSize??8} showCodeText={el.showCodeText??true} label={top} subLabel={bot} labelFontSize={el.topFontSize??8} subLabelFontSize={el.botFontSize??8} labelAlign={el.topAlign??'center'} subLabelAlign={el.botAlign??'center'}/></div>;
    }
    if (el.type==='qr') {
      const top = el.topLabel ? resolveField(el.topLabel as DataField, '', product, serial) : undefined;
      const bot = el.botLabel ? resolveField(el.botLabel as DataField, '', product, serial) : undefined;
      return <div key={el.id} style={s}><QrCodeLabel value={val||product.productCode} pixelSize={el.qrPx} label={top} subLabel={bot} labelFontSize={el.topFontSize??8} subLabelFontSize={el.botFontSize??8} labelAlign={el.topAlign??'center'} subLabelAlign={el.botAlign??'center'}/></div>;
    }
    return <div key={el.id} style={{ ...s, fontSize:el.fontSize, fontWeight:el.bold?'bold':'normal', fontStyle:el.italic?'italic':'normal', textAlign:el.align, color:el.color, display:'flex', alignItems:'center', padding:'0 1mm', lineHeight:1.1 }}>{val}</div>;
  };

  // ── Render ALL label cells with full content ───────────────────────────────
  const renderAllCells = () => {
    const HS = 7;
    const nodes: React.ReactNode[] = [];

    const pageEntries = printEntries.length > 0
      ? printEntries.slice((currentPage - 1) * labelsPerPage.total, currentPage * labelsPerPage.total)
      : null;

    // ── ROLL MODE ─────────────────────────────────────────────────────────
    if (paperType === 'roll') {
      const lw = rollWidth * canvasScale;
      const lh = labelH   * canvasScale;
      const gapPx = rollGap * canvasScale;

      for (let i = 0; i < ROLL_PREVIEW_COUNT; i++) {
        const oy = i * (lh + gapPx);
        const isFirst = i === 0;
        const entry    = pageEntries ? pageEntries[i] : null;
        const cellProd = entry?.product ?? previewProduct;
        const cellSer  = entry?.serial  ?? (previewProduct?.hasSerial ? previewSerial : undefined);
        const isEmpty  = pageEntries !== null && entry === undefined;

        // Gap strip between labels (represents the sensor gap on roll)
        if (i > 0) nodes.push(
          <div key={`roll-gap-${i}`} style={{
            position:'absolute', left:0, top: oy - gapPx, width: lw, height: gapPx,
            background:'rgba(148,163,184,0.35)',
            display:'flex', alignItems:'center', justifyContent:'center',
            pointerEvents:'none', zIndex:1,
          }}>
            <div style={{ width:'70%', borderTop:'1px dashed #94a3b8' }}/>
          </div>
        );

        // Label border
        nodes.push(
          <div key={`roll-border-${i}`} style={{
            position:'absolute', left:0, top: oy, width: lw, height: lh,
            border: isFirst ? '1.5px solid #6366f1' : '1px dashed #94a3b8',
            background: isEmpty ? 'rgba(148,163,184,0.08)' : undefined,
            boxSizing:'border-box', pointerEvents:'none', zIndex:25,
          }} />
        );

        if (isEmpty) continue;

        elements.forEach(el => {
          const isSelected = selectedId === el.id;
          const val = cellProd ? resolveField(el.field, el.customValue, cellProd, cellSer) : el.customValue || 'PREVIEW';
          nodes.push(
            <div key={`roll-el-${i}-${el.id}`}
              style={{
                position:'absolute',
                left: el.x * canvasScale,
                top:  el.y * canvasScale + oy,
                width:  el.w * canvasScale,
                height: el.h * canvasScale,
                cursor:'move', boxSizing:'border-box', overflow:'hidden',
                outline: isSelected ? '1.5px solid #6366f1' : '1px dashed rgba(148,163,184,0.6)',
                zIndex: isSelected ? 20 : 10,
              }}
              onMouseDown={ev => onElMouseDown(ev, el.id)}
            >
              {elInner(el, val, cellProd, cellSer)}
              {isSelected && isFirst && (['nw','ne','sw','se'] as const).map(h => (
                <div key={h} style={{
                  position:'absolute', width:HS, height:HS,
                  background:'#6366f1', border:'1.5px solid white', borderRadius:1,
                  cursor:`${h}-resize`, zIndex:30,
                  ...(h.includes('n') ? { top:-HS/2 } : { bottom:-HS/2 }),
                  ...(h.includes('w') ? { left:-HS/2 } : { right:-HS/2 }),
                }} onMouseDown={ev => onHandleMouseDown(ev, el.id, h)} />
              ))}
            </div>
          );
        });
      }
      return nodes;
    }

    // ── SHEET MODE ────────────────────────────────────────────────────────
    let entryIdx = 0;
    for (let r = 0; r < labelsPerPage.rows; r++) {
      for (let c = 0; c < labelsPerPage.cols; c++) {
        const ox = originX + c * stepX;
        const oy = originY + r * stepY;
        const lw = labelW * canvasScale;
        const lh = labelH * canvasScale;
        const isFirst = r === 0 && c === 0;

        const entry    = pageEntries ? pageEntries[entryIdx] : null;
        const cellProd = entry?.product ?? previewProduct;
        const cellSer  = entry?.serial  ?? (previewProduct?.hasSerial ? previewSerial : undefined);
        const isEmpty  = pageEntries !== null && entry === undefined;
        entryIdx++;

        if (gapH > 0 && c < labelsPerPage.cols - 1) nodes.push(
          <div key={`gh-${r}-${c}`} style={{ position:'absolute', left:ox+lw, top:oy, width:gapH*canvasScale, height:lh, background:'rgba(148,163,184,0.25)', pointerEvents:'none', zIndex:1 }} />
        );
        if (gapV > 0 && r < labelsPerPage.rows - 1) nodes.push(
          <div key={`gv-${r}-${c}`} style={{ position:'absolute', left:ox, top:oy+lh, width:lw, height:gapV*canvasScale, background:'rgba(148,163,184,0.25)', pointerEvents:'none', zIndex:1 }} />
        );

        nodes.push(
          <div key={`border-${r}-${c}`} style={{
            position:'absolute', left:ox, top:oy, width:lw, height:lh,
            border: isFirst ? '1.5px solid #6366f1' : '1px dashed #94a3b8',
            background: isEmpty ? 'rgba(148,163,184,0.08)' : undefined,
            boxSizing:'border-box', pointerEvents:'none', zIndex:25,
          }} />
        );

        if (isEmpty) continue;

        elements.forEach(el => {
          const isSelected = selectedId === el.id;
          const val = cellProd ? resolveField(el.field, el.customValue, cellProd, cellSer) : el.customValue || 'PREVIEW';
          nodes.push(
            <div key={`el-${r}-${c}-${el.id}`}
              style={{
                position:'absolute',
                left: el.x * canvasScale + ox,
                top:  el.y * canvasScale + oy,
                width:  el.w * canvasScale,
                height: el.h * canvasScale,
                cursor: 'move', boxSizing:'border-box', overflow:'hidden',
                outline: isSelected ? '1.5px solid #6366f1' : '1px dashed rgba(148,163,184,0.6)',
                zIndex: isSelected ? 20 : 10,
              }}
              onMouseDown={ev => onElMouseDown(ev, el.id)}
            >
              {elInner(el, val, cellProd, cellSer)}
              {isSelected && isFirst && (['nw','ne','sw','se'] as const).map(h => (
                <div key={h} style={{
                  position:'absolute', width:HS, height:HS,
                  background:'#6366f1', border:'1.5px solid white', borderRadius:1,
                  cursor:`${h}-resize`, zIndex:30,
                  ...(h.includes('n') ? { top:-HS/2 } : { bottom:-HS/2 }),
                  ...(h.includes('w') ? { left:-HS/2 } : { right:-HS/2 }),
                }} onMouseDown={ev => onHandleMouseDown(ev, el.id, h)} />
              ))}
            </div>
          );
        });
      }
    }
    return nodes;
  };

  // ── Right panel ───────────────────────────────────────────────────────────
  const renderRightPanel = () => {
    const el = selectedEl;

    const renderLayoutTab = () => (
      <div className="flex-1 overflow-y-auto p-3 space-y-4 text-xs">
        {/* Label Size */}
        <div>
          <div className="flex items-center justify-between mb-2 gap-2">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Label Size</p>
            <div className="flex rounded border border-slate-200 overflow-hidden">
              {(['in', 'mm'] as const).map(unit => (
                <button
                  key={unit}
                  type="button"
                  onClick={() => setSizeUnit(unit)}
                  className={`px-2 py-0.5 text-[11px] uppercase ${sizeUnit === unit ? 'bg-indigo-600 text-white' : 'bg-white text-slate-500'}`}
                >
                  {unit}
                </button>
              ))}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-1.5 mb-1.5">
            <UF lbl="Width" mm={labelW} fn={n => resizeLabelCanvas(n, labelH, { syncRollWidth: paperType === 'roll' })} unit={sizeUnit} minMm={5} maxMm={500}/>
            <UF lbl="Height" mm={labelH} fn={n => resizeLabelCanvas(labelW, n, { syncRollWidth: paperType === 'roll' })} unit={sizeUnit} minMm={5} maxMm={500}/>
          </div>
          <p className="text-[11px] text-slate-400 mb-1.5">One sticker/label size: {fmtSizeMmIn(labelW, labelH)}</p>
          <select className="border rounded px-1.5 py-1 text-xs w-full bg-white"
            onChange={e => { const f=LABEL_PRESETS.find(s=>`${s.w}x${s.h}`===e.target.value); if(f){resizeLabelCanvas(f.w, f.h, { syncRollWidth: paperType === 'roll' });} }}>
            <option value="">Quick sizes…</option>
            {LABEL_PRESET_OPTIONS.map(s=><option key={s.label} value={`${s.w}x${s.h}`}>{s.displayLabel}</option>)}
          </select>
          <p className="text-[11px] text-slate-400 mt-1.5">`Label Size` က sticker တစ်ခုရဲ့ size ဖြစ်ပါတယ်။ `Paper Size` က sheet/roll media တစ်ရွက်လုံးဖြစ်ပါတယ်။</p>
        </div>

        <hr className="border-slate-100"/>

        {/* Paper Type */}
        <div>
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Paper Type</p>
          <div className="flex rounded-lg overflow-hidden border border-slate-200 mb-3">
            {(['sheet','roll'] as const).map(t => (
              <button key={t} onClick={() => setPaperType(t)}
                className={`flex-1 py-1.5 text-xs font-medium transition-colors
                  ${paperType === t ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                {t === 'sheet' ? '📄 Sheet' : '🖨️ Roll'}
              </button>
            ))}
          </div>

          {paperType === 'roll' && (
            <div className="space-y-2">
              <div className="flex gap-1.5">
                <select className="flex-1 border rounded px-1.5 py-1 text-xs"
                  value={rollWidth}
                  onChange={e => resizeLabelCanvas(Number(e.target.value), labelH, { syncRollWidth: true })}>
                  {ROLL_WIDTH_OPTIONS.map(r => <option key={r.w} value={r.w}>{r.displayLabel}</option>)}
                </select>
                <div className="w-16">
                  <UF lbl="Custom" mm={rollWidth} fn={v => resizeLabelCanvas(v, labelH, { syncRollWidth: true })} unit={sizeUnit} minMm={20} maxMm={200}/>
                </div>
              </div>
              <UF lbl="Label Height" mm={labelH} fn={n => resizeLabelCanvas(rollWidth, n, { syncRollWidth: true })} unit={sizeUnit} minMm={5} maxMm={500}/>
              <UF lbl="Gap between labels" mm={rollGap} fn={setRollGap} unit={sizeUnit} minMm={0} maxMm={20} stepMm={0.5}/>
              <div className="rounded-lg bg-indigo-50 border border-indigo-100 p-2.5">
                <p className="font-semibold text-indigo-700">Roll · {fmtSizeMmIn(rollWidth, labelH)}</p>
                <p className="text-indigo-400 mt-0.5">1 label/page · gap {fmtMmIn(rollGap)}</p>
              </div>
            </div>
          )}

          {paperType === 'sheet' && (
            <div className="space-y-3">
              <div>
                <p className="font-medium text-slate-600 mb-1">Paper Size</p>
                <select className="border rounded px-1.5 py-1 text-xs w-full mb-2" value={paperKey} onChange={e => setPaperKey(e.target.value as PaperKey)}>
                  {(Object.entries(PAPER_DEFS) as [Exclude<PaperKey,'custom'>,{label:string}][]).map(([k,v]) => <option key={k} value={k}>{v.label}</option>)}
                  <option value="custom">Custom</option>
                </select>
                {paperKey==='custom' && (
                  <div className="grid grid-cols-2 gap-1.5 mb-2">
                    <UF lbl="Width" mm={customPaperW} fn={setCustomPaperW} unit={sizeUnit} minMm={20} maxMm={1200}/>
                    <UF lbl="Height" mm={customPaperH} fn={setCustomPaperH} unit={sizeUnit} minMm={20} maxMm={1200}/>
                  </div>
                )}
                <p className="text-[11px] text-slate-400">Full paper/media size: {fmtSizeMmIn(activePaper.w, activePaper.h)}</p>
              </div>
              <div>
                <p className="font-medium text-slate-600 mb-1">Margins (mm)</p>
                <div className="grid grid-cols-2 gap-1.5">
                  <UF lbl="Top" mm={marginTop} fn={setMarginTop} unit={sizeUnit} minMm={0} maxMm={50} stepMm={0.5}/>
                  <UF lbl="Bottom" mm={marginBottom} fn={setMarginBottom} unit={sizeUnit} minMm={0} maxMm={50} stepMm={0.5}/>
                  <UF lbl="Left" mm={marginLeft} fn={setMarginLeft} unit={sizeUnit} minMm={0} maxMm={50} stepMm={0.5}/>
                  <UF lbl="Right" mm={marginRight} fn={setMarginRight} unit={sizeUnit} minMm={0} maxMm={50} stepMm={0.5}/>
                </div>
              </div>
              <div>
                <p className="font-medium text-slate-600 mb-1">Spacing (mm)</p>
                <div className="grid grid-cols-2 gap-1.5 mb-2">
                  <UF lbl="Gap H" mm={gapH} fn={setGapH} unit={sizeUnit} minMm={0} maxMm={20} stepMm={0.5}/>
                  <UF lbl="Gap V" mm={gapV} fn={setGapV} unit={sizeUnit} minMm={0} maxMm={20} stepMm={0.5}/>
                </div>
                <label className="flex items-center gap-2 mb-2 cursor-pointer select-none">
                  <input type="checkbox" className="accent-indigo-600" checked={useManualGrid} onChange={e => setUseManualGrid(e.target.checked)}/>
                  <span className="text-slate-700 font-medium">Manual Grid</span>
                </label>
                {useManualGrid && (
                  <div className="grid grid-cols-2 gap-1.5">
                    <NF lbl="Columns" v={manualCols} fn={n => setManualCols(Math.max(1,n))} min={1} max={50}/>
                    <NF lbl="Rows"    v={manualRows} fn={n => setManualRows(Math.max(1,n))} min={1} max={50}/>
                  </div>
                )}
              </div>
              <div className="rounded-lg bg-indigo-50 border border-indigo-100 p-2.5">
                <p className="font-semibold text-indigo-700">{labelsPerPage.cols} × {labelsPerPage.rows} = {labelsPerPage.total} labels/page</p>
                <p className="text-indigo-400 mt-0.5">{fmtSizeMmIn(labelW, labelH)} · gap {fmtDim(gapH)}×{fmtDim(gapV)} mm</p>
              </div>
            </div>
          )}
        </div>

        <hr className="border-slate-100"/>

        {/* Presets */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">My Presets</p>
            <button className="flex items-center gap-1 px-2 py-0.5 text-xs bg-indigo-600 text-white rounded hover:bg-indigo-700"
              onClick={() => { setNewPresetName(''); setShowSaveDlg(true); }}><Bookmark size={10}/> Save</button>
          </div>
          {showSaveDlg && (
            <div className="flex gap-1 mb-2">
              <input autoFocus className="flex-1 border rounded px-1.5 py-0.5 text-xs" placeholder="Preset name…"
                value={newPresetName} onChange={e => setNewPresetName(e.target.value)}
                onKeyDown={e => { if (e.key==='Enter') handleSavePreset(); if (e.key==='Escape') setShowSaveDlg(false); }} />
              <button className="px-2 py-0.5 text-xs bg-indigo-600 text-white rounded" onClick={handleSavePreset}>OK</button>
              <button className="px-2 py-0.5 text-xs border rounded text-slate-500" onClick={() => setShowSaveDlg(false)}>✕</button>
            </div>
          )}
          {presets.length === 0
            ? <p className="text-slate-400 italic">No presets saved.</p>
            : presets.map(p => (
              <div key={p.id} className="flex items-center gap-1 mb-1 group">
                <BookmarkCheck size={11} className="text-indigo-400 shrink-0"/>
                <span className="flex-1 truncate text-slate-700">{p.name}</span>
                <button className="text-xs text-indigo-600 hover:underline px-1 opacity-0 group-hover:opacity-100" onClick={() => handleLoadPreset(p)}>Load</button>
                <button className="text-slate-300 hover:text-red-500 opacity-0 group-hover:opacity-100" onClick={() => handleDeletePreset(p.id)}><X size={10}/></button>
              </div>
            ))
          }
        </div>
      </div>
    );

    const renderPropsTab = () => {
      if (!el) return (
        <div className="flex flex-col items-center justify-center flex-1 text-center px-4 py-12">
          <Settings size={28} className="text-slate-200 mb-3"/>
          <p className="text-xs text-slate-400 leading-relaxed">Click an element on the canvas to edit its properties</p>
        </div>
      );
      return (
        <div className="flex-1 overflow-y-auto p-3 space-y-3 text-xs">
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1.5">Position & Size</p>
            <div className="grid grid-cols-2 gap-1.5">
              <UF lbl="Left" mm={Math.round((el as any).x*10)/10} fn={n=>updateEl(el.id,{x:n} as any)} unit={sizeUnit} stepMm={0.5}/>
              <UF lbl="Top" mm={Math.round((el as any).y*10)/10} fn={n=>updateEl(el.id,{y:n} as any)} unit={sizeUnit} stepMm={0.5}/>
              <UF lbl="Width" mm={Math.round((el as any).w*10)/10} fn={n=>updateEl(el.id,{w:n} as any)} unit={sizeUnit} minMm={1} stepMm={0.5}/>
              <UF lbl="Height" mm={Math.round((el as any).h*10)/10} fn={n=>updateEl(el.id,{h:n} as any)} unit={sizeUnit} minMm={1} stepMm={0.5}/>
            </div>
            <p className="text-[11px] text-slate-400 mt-1.5">ဒီ `Position & Size` က QR/Barcode box ရဲ့ အမှန်တကယ် print size ဖြစ်ပါတယ်။</p>
          </div>
          <hr className="border-slate-100"/>
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">Data Field</p>
            <select className="border rounded px-1.5 py-0.5 text-xs w-full" value={el.field} onChange={e => updateEl(el.id,{field:e.target.value as DataField} as any)}>
              {FIELD_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
            {el.field==='CUSTOM' && <input type="text" placeholder="Custom text…" className="border rounded px-1.5 py-0.5 text-xs w-full mt-1.5" value={el.customValue} onChange={e=>updateEl(el.id,{customValue:e.target.value} as any)}/>}
          </div>
          {el.type==='barcode' && (
            <div className="space-y-2">
              <hr className="border-slate-100"/>
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Barcode</p>
              <div className="grid grid-cols-2 gap-1.5">
                <NF lbl="Render Height (px)" v={el.barH}            fn={n=>updateEl(el.id,{barH:n} as any)}            min={10}  max={120}/>
                <NF lbl="Bar Thickness"      v={el.barW}            fn={n=>updateEl(el.id,{barW:n} as any)}            min={0.5} max={4} step={0.1}/>
                <NF lbl="Code Text (px)"     v={el.codeTextSize??8} fn={n=>updateEl(el.id,{codeTextSize:n} as any)}    min={5}   max={32}/>
              </div>
              <p className="text-[11px] text-slate-400">ဒီအောက်က setting တွေက barcode render style ပဲဖြစ်ပြီး paper/label size မဟုတ်ပါဘူး။</p>
              <hr className="border-slate-100"/>
              <p className="font-medium text-slate-600">Top Label</p>
              <div className="flex gap-1.5">
                <select className="flex-1 border rounded px-1.5 py-0.5 text-xs"
                  value={el.topLabel} onChange={e=>updateEl(el.id,{topLabel:e.target.value} as any)}>
                  <option value="">— None —</option>
                  {FIELD_OPTIONS.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
                {el.topLabel && <div className="w-14"><NF lbl="px" v={el.topFontSize??8} fn={n=>updateEl(el.id,{topFontSize:n} as any)} min={5} max={32}/></div>}
              </div>
              {el.topLabel && (
                <div className="flex gap-1">
                  {(['left','center','right'] as const).map(a=>(
                    <button key={a} onClick={()=>updateEl(el.id,{topAlign:a} as any)}
                      className={`flex-1 py-0.5 rounded border text-xs ${(el.topAlign??'center')===a?'bg-indigo-100 border-indigo-400 text-indigo-700':'bg-white border-slate-200 text-slate-500'}`}>
                      {a==='left'?'⇐':a==='center'?'⇔':'⇒'}
                    </button>
                  ))}
                </div>
              )}
              <p className="font-medium text-slate-600">Bottom Label</p>
              <div className="flex gap-1.5">
                <select className="flex-1 border rounded px-1.5 py-0.5 text-xs"
                  value={el.botLabel} onChange={e=>updateEl(el.id,{botLabel:e.target.value} as any)}>
                  <option value="">— None —</option>
                  {FIELD_OPTIONS.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
                {el.botLabel && <div className="w-14"><NF lbl="px" v={el.botFontSize??8} fn={n=>updateEl(el.id,{botFontSize:n} as any)} min={5} max={32}/></div>}
              </div>
              {el.botLabel && (
                <div className="flex gap-1">
                  {(['left','center','right'] as const).map(a=>(
                    <button key={a} onClick={()=>updateEl(el.id,{botAlign:a} as any)}
                      className={`flex-1 py-0.5 rounded border text-xs ${(el.botAlign??'center')===a?'bg-indigo-100 border-indigo-400 text-indigo-700':'bg-white border-slate-200 text-slate-500'}`}>
                      {a==='left'?'⇐':a==='center'?'⇔':'⇒'}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
          {el.type==='qr' && (
            <div className="space-y-2">
              <hr className="border-slate-100"/>
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">QR Code</p>
              <NF lbl="Render Quality (px)" v={el.qrPx} fn={n=>updateEl(el.id,{qrPx:n} as any)} min={40} max={400} step={10}/>
              <p className="text-[11px] text-slate-400">QR code ရဲ့ အမှန်တကယ် print size က အပေါ်က `Width/Height` ဖြစ်ပါတယ်။ ဒီ setting က render quality ပဲဖြစ်ပါတယ်။</p>
              <hr className="border-slate-100"/>
              <p className="font-medium text-slate-600">Top Label</p>
              <div className="flex gap-1.5">
                <select className="flex-1 border rounded px-1.5 py-0.5 text-xs"
                  value={el.topLabel} onChange={e=>updateEl(el.id,{topLabel:e.target.value} as any)}>
                  <option value="">— None —</option>
                  {FIELD_OPTIONS.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
                {el.topLabel && <div className="w-14"><NF lbl="px" v={el.topFontSize??8} fn={n=>updateEl(el.id,{topFontSize:n} as any)} min={5} max={32}/></div>}
              </div>
              {el.topLabel && (
                <div className="flex gap-1">
                  {(['left','center','right'] as const).map(a=>(
                    <button key={a} onClick={()=>updateEl(el.id,{topAlign:a} as any)}
                      className={`flex-1 py-0.5 rounded border text-xs ${(el.topAlign??'center')===a?'bg-indigo-100 border-indigo-400 text-indigo-700':'bg-white border-slate-200 text-slate-500'}`}>
                      {a==='left'?'⇐':a==='center'?'⇔':'⇒'}
                    </button>
                  ))}
                </div>
              )}
              <p className="font-medium text-slate-600">Bottom Label</p>
              <div className="flex gap-1.5">
                <select className="flex-1 border rounded px-1.5 py-0.5 text-xs"
                  value={el.botLabel} onChange={e=>updateEl(el.id,{botLabel:e.target.value} as any)}>
                  <option value="">— None —</option>
                  {FIELD_OPTIONS.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
                {el.botLabel && <div className="w-14"><NF lbl="px" v={el.botFontSize??8} fn={n=>updateEl(el.id,{botFontSize:n} as any)} min={5} max={32}/></div>}
              </div>
              {el.botLabel && (
                <div className="flex gap-1">
                  {(['left','center','right'] as const).map(a=>(
                    <button key={a} onClick={()=>updateEl(el.id,{botAlign:a} as any)}
                      className={`flex-1 py-0.5 rounded border text-xs ${(el.botAlign??'center')===a?'bg-indigo-100 border-indigo-400 text-indigo-700':'bg-white border-slate-200 text-slate-500'}`}>
                      {a==='left'?'⇐':a==='center'?'⇔':'⇒'}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
          {el.type==='text' && (
            <div className="space-y-2">
              <hr className="border-slate-100"/>
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Text Style</p>
              <div className="flex items-center gap-1.5">
                <div className="flex-1"><NF lbl="Font Size (px)" v={el.fontSize} fn={n=>updateEl(el.id,{fontSize:n} as any)} min={6} max={72}/></div>
                <label className="flex flex-col gap-0.5"><span className="text-slate-400 text-xs">Color</span>
                  <input type="color" className="h-[26px] w-9 cursor-pointer border rounded" value={el.color} onChange={e=>updateEl(el.id,{color:e.target.value} as any)}/>
                </label>
              </div>
              <div className="flex gap-1 flex-wrap">
                {[{k:'bold',lbl:'B',cls:'font-bold'},{k:'italic',lbl:'I',cls:'italic'}].map(({k,lbl,cls})=>(
                  <button key={k} className={`px-2.5 py-0.5 rounded border text-xs ${cls} ${(el as any)[k]?'bg-indigo-100 border-indigo-400 text-indigo-700':'bg-white border-slate-200 text-slate-600'}`}
                    onClick={()=>updateEl(el.id,{[k]:!(el as any)[k]} as any)}>{lbl}</button>
                ))}
                {(['left','center','right'] as const).map(a=>(
                  <button key={a} className={`px-2 py-0.5 rounded border text-xs ${el.align===a?'bg-indigo-100 border-indigo-400 text-indigo-700':'bg-white border-slate-200 text-slate-600'}`}
                    onClick={()=>updateEl(el.id,{align:a} as any)}>
                    {a==='left'?'⇐':a==='center'?'⇔':'⇒'}
                  </button>
                ))}
              </div>
            </div>
          )}
          <hr className="border-slate-100"/>
          <button className="w-full text-xs text-red-600 border border-red-200 rounded px-2 py-1.5 hover:bg-red-50 flex items-center justify-center gap-1"
            onClick={()=>deleteEl(el.id)}><Trash2 size={12}/> Delete Element</button>
        </div>
      );
    };

    const renderPrinterTab = () => (
      <div className="flex-1 overflow-y-auto p-3 space-y-3 text-xs">
        {/* Connection section */}
        <div className="space-y-2">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Connect Printer</p>
          {webUsbSupported ? (
            <button
              className="flex items-center gap-1.5 w-full px-2.5 py-2 rounded-lg border border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 font-medium"
              onClick={connectUsbPrinter}
            >
              <Usb size={13}/> {printerConnected ? 'Reconnect USB Printer' : 'Connect USB Printer'}
              {printerConnected && <span className="ml-auto w-2 h-2 rounded-full bg-green-500"/>}
            </button>
          ) : (
            <div className="flex items-center gap-1.5 px-2.5 py-2 rounded-lg border border-amber-200 bg-amber-50 text-amber-700 text-xs">
              <Wifi size={13}/> <span>Use Chrome/Edge for USB support</span>
            </div>
          )}

          {printerError && (
            <div className="text-red-600 bg-red-50 border border-red-200 rounded p-2.5 text-xs leading-tight">
              <p className="font-semibold mb-1">⚠️ Error</p>
              <p>{printerError}</p>
            </div>
          )}
        </div>

        <hr className="border-slate-100"/>

        {/* Manual selection */}
        <div className="space-y-2">
          <p className="font-medium text-slate-600">Select Manually:</p>
          <div className="flex gap-1.5">
            <select className="flex-1 border rounded px-1.5 py-1 text-xs"
              value={manualBrand} onChange={e => setManualBrand(e.target.value)}>
              {MANUAL_BRANDS.map(b => <option key={b} value={b}>{b}</option>)}
            </select>
            <button className="px-2.5 py-1 text-xs bg-slate-600 text-white rounded hover:bg-slate-700 shrink-0 font-medium"
              onClick={applyManualPrinter}>Apply</button>
          </div>
        </div>

        <hr className="border-slate-100"/>

        {/* Printer profile info */}
        {printerProfile ? (
          <div className="space-y-2">
            <div className="rounded-lg bg-indigo-50 border border-indigo-200 p-2.5 space-y-2">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <p className="font-semibold text-indigo-900 leading-tight">{printerProfile.brand}</p>
                  <p className="text-indigo-400 text-xs leading-tight">{printerProfile.model}</p>
                </div>
                <button
                  className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-1"
                  onClick={removePrinterProfile}
                  title="Remove printer profile"
                >
                  ✕
                </button>
              </div>

              {/* Specs */}
              <div className="flex flex-wrap gap-1">
                <span className="bg-white border border-indigo-200 rounded px-1.5 py-0.5 text-indigo-700 font-mono text-[10px]">{printerProfile.protocol}</span>
                <span className="bg-white border border-indigo-200 rounded px-1.5 py-0.5 text-indigo-700 font-mono text-[10px]">{printerProfile.dpi} dpi</span>
                <span className="bg-white border border-indigo-200 rounded px-1.5 py-0.5 text-indigo-700 font-mono text-[10px]">Max {printerProfile.maxWidthMm}mm</span>
              </div>

              {/* Margin setting */}
              <div className="pt-1.5 border-t border-indigo-200">
                <label className="flex flex-col gap-0.5 mb-1.5">
                  <span className="text-indigo-700 font-medium">Default Margin</span>
                  <input
                    type="number"
                    className="border border-indigo-200 rounded px-1.5 py-0.5 text-xs"
                    value={printerMarginMm}
                    min={0}
                    max={20}
                    step={0.5}
                    onChange={(e) => {
                      const v = Number(e.target.value);
                      setPrinterMarginMm(v);
                      savePrinterSettings(printerProfile, v);
                    }}
                  />
                  <span className="text-indigo-400 text-[11px]">Applied margins on all sides</span>
                </label>
              </div>
            </div>

            {/* Paper sizes list */}
            <div>
              <p className="font-semibold text-slate-600 mb-2">Supported Label Sizes:</p>
              <div className="space-y-1 max-h-64 overflow-y-auto">
                {printerProfile.papers.map((paper, i) => {
                  const isSelected = selectedPrinterPaper?.label === paper.label;
                  const isCompatible = paper.w <= printerProfile.maxWidthMm;
                  return (
                    <div
                      key={i}
                      className={`flex items-center gap-1.5 p-1.5 rounded border transition-colors ${
                        isSelected
                          ? 'bg-indigo-50 border-indigo-300'
                          : isCompatible
                          ? 'bg-white border-slate-200 hover:bg-slate-50'
                          : 'bg-red-50 border-red-200 opacity-60'
                      }`}
                    >
                      {isSelected && <span className="text-indigo-600">✓</span>}
                      <span className="flex-1 text-slate-700 text-xs">
                        {paper.label}
                      </span>
                      {!isCompatible && (
                        <span className="text-red-600 text-[10px] font-medium">Too wide</span>
                      )}
                      {isCompatible && (
                        <button
                          className={`px-2 py-0.5 text-xs rounded shrink-0 transition-colors ${
                            isSelected
                              ? 'bg-indigo-200 text-indigo-700 font-medium'
                              : 'bg-slate-200 text-slate-600 hover:bg-indigo-100 hover:text-indigo-700'
                          }`}
                          onClick={() => applyPrinterPaper(paper, printerProfile)}
                        >
                          {isSelected ? 'Applied' : 'Apply'}
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Info about applied settings */}
            {printerPaperInfo && (
              <div className="bg-slate-50 border border-slate-200 rounded p-2 text-[11px] text-slate-600">
                <p className="font-mono">{printerPaperInfo}</p>
              </div>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center py-6 px-3 text-center">
            <Printer size={28} className="text-slate-300 mb-2" />
            <p className="text-xs text-slate-400">No printer selected</p>
            <p className="text-xs text-slate-300 mt-1">Connect via USB or select manually above</p>
          </div>
        )}

        <hr className="border-slate-100" />

        {/* Tips section */}
        <div className="bg-blue-50 border border-blue-200 rounded p-2 space-y-1 text-[11px] text-blue-700">
          <p className="font-semibold">💡 Tips:</p>
          <ul className="list-disc list-inside space-y-0.5 text-blue-600">
            <li>Select your printer model to see compatible label sizes</li>
            <li>Click "Apply" to auto-configure layout for that paper size</li>
            <li>Adjust margins to fine-tune print area</li>
            <li>Your printer settings are saved automatically</li>
          </ul>
        </div>
      </div>
    );

    const TABS = [
      { id: 'layout'  as const, label: 'Layout',  icon: <Layers   size={12}/> },
      { id: 'props'   as const, label: 'Props',   icon: <Settings size={12}/>, badge: !!el },
      { id: 'printer' as const, label: 'Printer', icon: <Printer  size={12}/>, dot: printerConnected },
    ];

    return (
      <div className="flex flex-col h-full overflow-hidden">
        {/* Tab bar */}
        <div className="flex border-b border-slate-100 shrink-0 bg-white">
          {TABS.map(tab => (
            <button key={tab.id}
              onClick={() => setRightTab(tab.id)}
              className={`flex flex-1 items-center justify-center gap-1 py-2.5 text-xs font-medium border-b-2 transition-colors relative
                ${rightTab === tab.id
                  ? 'border-indigo-600 text-indigo-700'
                  : 'border-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-50'}`}>
              {tab.icon}
              {tab.label}
              {'dot' in tab && tab.dot && <span className="absolute top-1.5 right-2 w-1.5 h-1.5 rounded-full bg-green-500"/>}
              {'badge' in tab && tab.badge && <span className="absolute top-1.5 right-2 w-1.5 h-1.5 rounded-full bg-indigo-500"/>}
            </button>
          ))}
        </div>
        {/* Tab content */}
        <div className="flex flex-1 flex-col overflow-hidden">
          {rightTab === 'layout'  && renderLayoutTab()}
          {rightTab === 'props'   && renderPropsTab()}
          {rightTab === 'printer' && renderPrinterTab()}
        </div>
      </div>
    );
  };

  // ── Render ────────────────────────────────────────────────────────────────
  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-indigo-600"/>
    </div>
  );

  const zoomPct = Math.round(zoom * 100);

  return (
    <div className="flex flex-col bg-slate-50" style={{ minHeight: 'calc(100vh - 64px)' }}>
      <style>{paperType === 'roll' ? `
        @media print {
          @page { margin: 0; size: ${rollWidth}mm ${labelH}mm; }
          body * { visibility: hidden !important; }
          #ld-print-area, #ld-print-area * { visibility: visible !important; }
          #ld-print-area { position: absolute !important; left: 0 !important; top: 0 !important; display: block !important; }
          .ld-label { width: ${rollWidth}mm !important; height: ${labelH}mm !important; position: relative !important; overflow: hidden !important; box-sizing: border-box !important; page-break-after: always !important; break-after: page !important; }
          .ld-print-hidden { display: none !important; }
        }
      ` : `
        @media print {
          @page { margin: 0; size: ${activePaper.w}mm ${activePaper.h}mm; }
          body * { visibility: hidden !important; }
          #ld-print-area, #ld-print-area * { visibility: visible !important; }
          #ld-print-area {
            position: absolute !important; left: 0 !important; top: 0 !important;
            width: ${activePaper.w}mm !important;
            padding: ${marginTop}mm ${marginRight}mm ${marginBottom}mm ${marginLeft}mm !important;
            box-sizing: border-box !important;
            display: grid !important;
            grid-template-columns: repeat(${labelsPerPage.cols}, ${labelW}mm) !important;
            gap: ${gapV}mm ${gapH}mm !important;
          }
          .ld-label { break-inside: avoid !important; }
          .ld-print-hidden { display: none !important; }
        }
      `}</style>

      {/* Hidden print area */}
      <div id="ld-print-area" style={{ display:'none' }}>
        {printEntries.map((entry, i) => (
          <div key={i} className="ld-label" style={{ position:'relative', width:`${paperType==='roll'?rollWidth:labelW}mm`, height:`${labelH}mm`, overflow:'hidden', boxSizing:'border-box' }}>
            {elements.map(el => renderPrintEl(el, entry.product, entry.serial))}
          </div>
        ))}
      </div>

      {/* Header */}
      <header className="ld-print-hidden bg-white border-b flex items-center gap-2 px-4 py-2.5 shrink-0">
        <button onClick={() => navigate(AppRoute.PRODUCTS)} className="text-slate-500 hover:text-indigo-600 shrink-0"><ArrowLeft size={18}/></button>
        <Layers size={17} className="text-indigo-600 shrink-0"/>
        <h1 className="font-bold text-slate-800 text-sm whitespace-nowrap">Label Designer</h1>
        <div className="h-4 w-px bg-slate-200 shrink-0"/>
        <div className="flex items-center gap-1.5 text-xs shrink-0">
          <input type="number" className="border rounded px-1.5 py-1 text-xs w-16" value={Number((sizeUnit === 'in' ? mmToIn(labelW) : labelW).toFixed(sizeUnit === 'in' ? 3 : 2))} min={sizeUnit === 'in' ? mmToIn(5) : 5} max={sizeUnit === 'in' ? mmToIn(500) : 500} step={sizeUnit === 'in' ? mmToIn(0.5) : 0.5} onChange={e=>resizeLabelCanvas(sizeUnit === 'in' ? inToMm(Number(e.target.value)) : Number(e.target.value), labelH, { syncRollWidth: paperType === 'roll' })}/>
          <span className="text-slate-400">×</span>
          <input type="number" className="border rounded px-1.5 py-1 text-xs w-16" value={Number((sizeUnit === 'in' ? mmToIn(labelH) : labelH).toFixed(sizeUnit === 'in' ? 3 : 2))} min={sizeUnit === 'in' ? mmToIn(5) : 5} max={sizeUnit === 'in' ? mmToIn(500) : 500} step={sizeUnit === 'in' ? mmToIn(0.5) : 0.5} onChange={e=>resizeLabelCanvas(labelW, sizeUnit === 'in' ? inToMm(Number(e.target.value)) : Number(e.target.value), { syncRollWidth: paperType === 'roll' })}/>
          <span className="text-slate-400">{sizeUnit}</span>
          <span className="text-slate-400 whitespace-nowrap">({fmtSizeMmIn(labelW, labelH)})</span>
        </div>
        <span className={`px-2 py-0.5 rounded-full text-xs font-medium shrink-0 ${paperType === 'roll' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'}`}>
          {paperType === 'roll' ? `🖨️ Roll ${fmtMmIn(rollWidth)}` : `📄 ${activePaper.label}`}
        </span>
        <div className="ml-auto flex items-center gap-2 shrink-0">
          {printerProfile && (
            <div className="flex items-center gap-1 px-2 py-1 rounded border border-slate-200 bg-slate-50 text-xs text-slate-600">
              <span className={printerConnected ? 'text-green-500' : 'text-amber-400'}>●</span>
              {printerProfile.brand} · {printerProfile.protocol}
            </div>
          )}
          {printIds.size > 0 && elements.length > 0 && (
            <span className="text-xs text-slate-400 whitespace-nowrap">{printEntries.length} labels</span>
          )}
          <button className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed whitespace-nowrap"
            disabled={printIds.size===0||elements.length===0||printEntries.length===0} onClick={()=>window.print()}>
            <Printer size={13}/> Print ({printEntries.length} labels)
          </button>
        </div>
      </header>

      {/* Body */}
      <div className="ld-print-hidden flex flex-1 overflow-hidden" style={{ height:'calc(100vh - 120px)' }}>

        {/* Left panel */}
        <aside className="w-56 bg-white border-r flex flex-col shrink-0 overflow-hidden">
          <div className="p-3 border-b shrink-0">
            <p className="text-xs font-semibold text-slate-600 mb-2">Add Element</p>
            <div className="grid grid-cols-3 gap-1.5">
              {([{t:'barcode' as const,Icon:Barcode,lbl:'Barcode'},{t:'qr' as const,Icon:QrCode,lbl:'QR Code'},{t:'text' as const,Icon:Type,lbl:'Text'}]).map(({t,Icon,lbl})=>(
                <button key={t} className="flex flex-col items-center gap-0.5 p-2 rounded border border-slate-200 hover:bg-indigo-50 hover:border-indigo-400 text-xs text-slate-600 hover:text-indigo-700 transition-colors"
                  onClick={()=>addElement(t)}><Icon size={15}/>{lbl}</button>
              ))}
            </div>
          </div>

          <div className="border-b shrink-0">
            <button className="flex items-center gap-1.5 w-full px-3 py-2 text-left hover:bg-slate-50 transition-colors" onClick={() => setLayersOpen(o=>!o)}>
              {layersOpen ? <ChevronDown size={11} className="text-slate-400"/> : <ChevronRight size={11} className="text-slate-400"/>}
              <p className="text-xs font-semibold text-slate-600 flex-1">Layers</p>
              <span className="text-xs text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded-full min-w-[20px] text-center">{elements.length}</span>
            </button>
            {layersOpen && (
              <div className="px-2 pb-2 max-h-36 overflow-y-auto">
                {elements.length===0
                  ? <p className="text-xs text-slate-400 italic px-1">No elements yet</p>
                  : [...elements].reverse().map(el=>(
                    <div key={el.id}
                      className={`flex items-center gap-1.5 px-2 py-1 rounded cursor-pointer text-xs mb-0.5 ${selectedId===el.id?'bg-indigo-100 text-indigo-700':'hover:bg-slate-50 text-slate-600'}`}
                      onClick={()=>{ setSelectedId(el.id); setRightTab('props'); }}>
                      {el.type==='barcode'?<Barcode size={11}/>:el.type==='qr'?<QrCode size={11}/>:<Type size={11}/>}
                      <span className="flex-1 truncate">{el.type==='text'?(el.customValue||FIELD_OPTIONS.find(f=>f.value===el.field)?.label||el.field):`${el.type.toUpperCase()} · ${el.field}`}</span>
                      <button className="text-slate-300 hover:text-red-500 shrink-0" onClick={ev=>{ev.stopPropagation();deleteEl(el.id);}}><X size={10}/></button>
                    </div>
                  ))
                }
              </div>
            )}
          </div>

          <div className="flex flex-col flex-1 overflow-hidden">
            <button className="flex items-center gap-1.5 w-full px-3 py-2 text-left hover:bg-slate-50 transition-colors shrink-0" onClick={() => setPrintOpen(o=>!o)}>
              {printOpen ? <ChevronDown size={11} className="text-slate-400"/> : <ChevronRight size={11} className="text-slate-400"/>}
              <p className="text-xs font-semibold text-slate-600 flex-1">Print Products</p>
              {printIds.size > 0 && (
                <span className="text-xs font-medium text-indigo-700 bg-indigo-100 px-1.5 py-0.5 rounded-full min-w-[20px] text-center">{printIds.size}</span>
              )}
            </button>
            {printOpen && (
              <div className="flex-1 overflow-y-auto px-3 pb-3">
                <div className="flex items-center gap-1 mb-2">
                  <button className="text-xs text-indigo-600 hover:underline"
                    onClick={()=>{ setPrintIds(new Set(filteredProducts.map(p=>p.id))); setPrintCopies(prev=>{const n={...prev};filteredProducts.forEach(p=>{if(!n[p.id])n[p.id]=1;});return n;}); }}>All</button>
                  <span className="text-slate-300 text-xs">|</span>
                  <button className="text-xs text-slate-500 hover:underline" onClick={()=>setPrintIds(new Set())}>None</button>
                </div>
                <input className="w-full border rounded px-2 py-1 text-xs mb-2" placeholder="Search…" value={search} onChange={e=>setSearch(e.target.value)}/>
                <div className="space-y-2">
                  {filteredProducts.map(p => {
                    const stock = getStock(p, serials);
                    const checked = printIds.has(p.id);
                    return (
                      <div key={p.id}>
                        <div className="flex items-center gap-1.5">
                          <input type="checkbox" id={`chk-${p.id}`} checked={checked} onChange={()=>togglePrint(p.id)} className="accent-indigo-600 shrink-0"/>
                          <label htmlFor={`chk-${p.id}`}
                            className={`flex-1 text-xs truncate cursor-pointer ${previewProductId===p.id?'font-semibold text-indigo-700':'text-slate-700'}`}
                            onClick={()=>setPreviewProductId(p.id)}>{p.name}</label>
                          <span className="text-xs text-slate-400 shrink-0">{stock}</span>
                        </div>
                        {checked && (
                          p.hasSerial ? (
                            <div className="ml-5 mt-1">
                              {serials.filter(s => s.productId === p.id).length === 0
                                ? <span className="text-xs text-slate-400 italic">No available serials</span>
                                : (
                                  <>
                                    <div className="flex items-center gap-2 mb-1">
                                      <button className="text-xs text-indigo-500 hover:underline"
                                        onClick={() => setPrintSerials(prev => ({ ...prev, [p.id]: new Set(serials.filter(s => s.productId === p.id).map(s => s.serialNumber)) }))}>All</button>
                                      <span className="text-slate-300 text-xs">|</span>
                                      <button className="text-xs text-slate-400 hover:underline"
                                        onClick={() => setPrintSerials(prev => ({ ...prev, [p.id]: new Set() }))}>None</button>
                                      <span className="text-xs text-slate-400 ml-auto">
                                        {printSerials[p.id]?.size ?? 0} selected
                                      </span>
                                    </div>
                                    <div className="space-y-0.5 max-h-36 overflow-y-auto pr-1">
                                      {serials.filter(s => s.productId === p.id).map(s => {
                                        const checked2 = printSerials[p.id]?.has(s.serialNumber) ?? false;
                                        return (
                                          <label key={s.serialNumber} className="flex items-center gap-1.5 cursor-pointer hover:bg-slate-50 rounded px-1 py-0.5">
                                            <input type="checkbox" className="accent-indigo-600 shrink-0" checked={checked2}
                                              onChange={() => setPrintSerials(prev => {
                                                const cur = new Set(prev[p.id] ?? []);
                                                cur.has(s.serialNumber) ? cur.delete(s.serialNumber) : cur.add(s.serialNumber);
                                                return { ...prev, [p.id]: cur };
                                              })}
                                            />
                                            <span className="text-xs font-mono text-slate-700">{s.serialNumber}</span>
                                          </label>
                                        );
                                      })}
                                    </div>
                                  </>
                                )
                              }
                            </div>
                          ) : (
                            <div className="flex items-center gap-1.5 ml-5 mt-0.5">
                              <input type="number" className="w-16 border rounded px-1.5 py-0.5 text-xs"
                                value={printCopies[p.id]||1} min={1} max={stock||1}
                                onChange={e=>setPrintCopies(prev=>({...prev,[p.id]:Math.min(stock||1,Math.max(1,Number(e.target.value)))}))}/>
                              <span className="text-xs text-slate-400">/ {stock} copies</span>
                            </div>
                          )
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </aside>

        {/* Canvas area */}
        <main className="flex-1 overflow-auto bg-slate-300 flex flex-col items-center py-6 px-4 gap-3">

          {/* Preview + Zoom bar */}
          <div className="flex items-center gap-3 text-xs bg-white rounded-lg px-3 py-1.5 shadow-sm shrink-0 flex-wrap">
            <Eye size={13} className="text-indigo-500"/>
            <select className="border rounded px-2 py-0.5 text-xs bg-white"
              value={previewProductId||''} onChange={e=>setPreviewProductId(Number(e.target.value))}>
              {products.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            {previewProduct?.hasSerial && (
              <input className="border rounded px-2 py-0.5 text-xs w-28" value={previewSerial} onChange={e=>setPreviewSerial(e.target.value)}/>
            )}
            {/* Page navigation — shown only when print entries span multiple pages */}
            {totalPages > 1 && (
              <>
                <div className="h-4 w-px bg-slate-200"/>
                <div className="flex items-center gap-1">
                  <button
                    className="w-6 h-6 flex items-center justify-center border rounded hover:bg-slate-50 disabled:opacity-40"
                    disabled={currentPage <= 1}
                    onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                  ><ArrowLeft size={11}/></button>
                  <span className="text-xs text-slate-600 font-mono w-16 text-center">
                    {currentPage} / {totalPages}
                  </span>
                  <button
                    className="w-6 h-6 flex items-center justify-center border rounded hover:bg-slate-50 disabled:opacity-40"
                    disabled={currentPage >= totalPages}
                    onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                  ><ArrowLeft size={11} style={{ transform: 'rotate(180deg)' }}/></button>
                </div>
              </>
            )}
            <div className="h-4 w-px bg-slate-200"/>
            {/* Zoom controls */}
            <div className="flex items-center gap-1">
              <button className="w-6 h-6 flex items-center justify-center border rounded hover:bg-slate-50"
                onClick={()=>setZoom(z=>Math.max(0.25,+(z-0.25).toFixed(2)))}><Minus size={11}/></button>
              <span className="text-xs text-slate-600 w-11 text-center font-mono">{zoomPct}%</span>
              <button className="w-6 h-6 flex items-center justify-center border rounded hover:bg-slate-50"
                onClick={()=>setZoom(z=>Math.min(4,+(z+0.25).toFixed(2)))}><Plus size={11}/></button>
              <button className="text-xs text-slate-400 hover:text-indigo-600 ml-1"
                onClick={()=>setZoom(1)}>Fit</button>
            </div>
          </div>

          {/* Paper canvas — overflow visible so larger zoom shows full paper */}
          <div style={{ position:'relative', flexShrink:0 }}>
            <div
              ref={canvasRef}
              style={{
                position:'relative', width:paperPxW, height:paperPxH,
                background:'white', boxShadow:'0 6px 32px rgba(0,0,0,0.22)',
                cursor:'default', userSelect:'none',
              }}
              onMouseDown={onCanvasMouseDown}
              onMouseMove={onMouseMove}
              onMouseUp={onMouseUp}
              onMouseLeave={onMouseUp}
            >
              {/* 5mm grid (subtle) */}
              <svg style={{ position:'absolute', inset:0, width:'100%', height:'100%', pointerEvents:'none', opacity:0.07 }}>
                {Array.from({length:Math.ceil(activePaper.w/5)+1},(_,i)=>(
                  <line key={`v${i}`} x1={i*5*canvasScale} y1={0} x2={i*5*canvasScale} y2={paperPxH} stroke="#6366f1" strokeWidth={0.7}/>
                ))}
                {Array.from({length:Math.ceil(activePaper.h/5)+1},(_,i)=>(
                  <line key={`h${i}`} x1={0} y1={i*5*canvasScale} x2={paperPxW} y2={i*5*canvasScale} stroke="#6366f1" strokeWidth={0.7}/>
                ))}
              </svg>

              {/* Margin shading */}
              {[
                {style:{left:0,top:0,width:paperPxW,height:originY}},
                {style:{left:0,bottom:0,width:paperPxW,height:marginBottom*canvasScale}},
                {style:{left:0,top:originY,width:originX,height:paperPxH-(marginTop+marginBottom)*canvasScale}},
                {style:{right:0,top:originY,width:marginRight*canvasScale,height:paperPxH-(marginTop+marginBottom)*canvasScale}},
              ].map((item,i)=>(
                <div key={i} style={{position:'absolute',background:'rgba(99,102,241,0.06)',pointerEvents:'none',...item.style}}/>
              ))}

              {/* Margin guide lines */}
              <svg style={{position:'absolute',inset:0,width:'100%',height:'100%',pointerEvents:'none',zIndex:2}}>
                <line x1={0} y1={originY} x2={paperPxW} y2={originY} stroke="#6366f1" strokeWidth={0.7} strokeDasharray="4,3" opacity={0.5}/>
                <line x1={0} y1={paperPxH-marginBottom*canvasScale} x2={paperPxW} y2={paperPxH-marginBottom*canvasScale} stroke="#6366f1" strokeWidth={0.7} strokeDasharray="4,3" opacity={0.5}/>
                <line x1={originX} y1={0} x2={originX} y2={paperPxH} stroke="#6366f1" strokeWidth={0.7} strokeDasharray="4,3" opacity={0.5}/>
                <line x1={paperPxW-marginRight*canvasScale} y1={0} x2={paperPxW-marginRight*canvasScale} y2={paperPxH} stroke="#6366f1" strokeWidth={0.7} strokeDasharray="4,3" opacity={0.5}/>
              </svg>

              {/* All cells with full real content */}
              {renderAllCells()}
            </div>
          </div>

          {/* Info strip */}
          <p className="text-xs text-slate-500 shrink-0">
            {paperType === 'roll'
              ? `Roll ${fmtSizeMmIn(rollWidth, labelH)} · gap ${fmtMmIn(rollGap)} · 1 label/page`
              : `${activePaper.label} · ${labelsPerPage.cols}×${labelsPerPage.rows} = ${labelsPerPage.total} labels/page`}
            {' · '}{zoomPct}%
            {totalPages > 1 ? ` · Page ${currentPage}/${totalPages} · ${printEntries.length} total` : ''}
            {selectedEl ? ` · (${Math.round(selectedEl.x*10)/10}, ${Math.round(selectedEl.y*10)/10}) mm` : ''}
          </p>
        </main>

        {/* Right panel */}
        <aside className="w-60 bg-white border-l flex flex-col shrink-0 overflow-hidden">
          {renderRightPanel()}
        </aside>
      </div>
    </div>
  );
};

export default LabelDesigner;
