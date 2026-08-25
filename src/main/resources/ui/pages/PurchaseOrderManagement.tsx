import React, { useState, useEffect, useCallback, useLayoutEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { purchaseOrderApiService, type PurchaseOrderPage } from '../services/purchaseorderapiservice';
import { supplierService } from '../services/supplierapiservice';
import { staffService } from '../services/staffapiservice';
import { productService } from '../services/productapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { PurchaseOrderDTO, SupplierDTO, StaffDTO, ProductDTO, PaymentMethodDTO, PaymentTransactionDTO, GoodsReceiptDTO } from '../types';
import { Plus, Trash2, Save, X, RefreshCw, Eye, FileText, Search, Calendar, Loader2, Ban, PackageCheck, AlertTriangle } from 'lucide-react';
import Swal from 'sweetalert2';

type PODetailForm = {
  detailId?: number;
  productId: number;
  productSearch: string;
  productOpen: boolean;
  qty: number;
  receivedQty: number;
  unitCost: number;
  subtotal: number;
};

const money = (n: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(n || 0);
const suggestedCost = (p?: ProductDTO | null) => Number(p?.lastPurchaseCost ?? p?.costPrice ?? 0) || 0;
const generateSerialNumbers = (qty: number, existingAll: string[] = []): string[] => {
  const now = new Date();
  const p = (n: number, len = 2) => String(n).padStart(len, '0');
  const prefix = `${now.getFullYear()}${p(now.getMonth() + 1)}${p(now.getDate())}${p(now.getHours())}${p(now.getMinutes())}${p(now.getSeconds())}`;
  const used = new Set(existingAll);
  const result: string[] = [];
  while (result.length < qty) {
    const candidate = `${prefix}${String(Math.floor(Math.random() * 1_000)).padStart(3, '0')}`;
    if (!used.has(candidate)) { used.add(candidate); result.push(candidate); }
  }
  return result;
};
const resizeSerials = (serials: string[] = [], qty: number) => {
  const n = Math.max(0, qty || 0);
  const next = [...serials];
  if (next.length > n) return next.slice(0, n);
  if (next.length < n) return [...next, ...Array(n - next.length).fill('')];
  return next;
};

const POTableRow: React.FC<{
  row: PODetailForm;
  idx: number;
  products: ProductDTO[];
  handleRowChange: (idx: number, patch: Partial<PODetailForm>) => void;
  money: (n: number) => string;
  onDelete: (idx: number) => void;
}> = ({ row, idx, products, handleRowChange, money, onDelete }) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const filteredProducts = products.filter((p) =>
    row.productSearch === '' || p.name.toLowerCase().includes(row.productSearch.toLowerCase()) ||
    (p.productCode || '').toLowerCase().includes(row.productSearch.toLowerCase())
  );
  const unitCostFor = (p: ProductDTO) => Number(p.costPrice ?? 0) || row.unitCost;
  const productChoices = filteredProducts.slice(0, 80);

  useLayoutEffect(() => {
    if (!row.productOpen || !inputRef.current) {
      setMenuPos(null);
      return;
    }
    const place = () => {
      if (!inputRef.current) return;
      const rect = inputRef.current.getBoundingClientRect();
      const maxH = 224;
      const width = Math.min(Math.max(rect.width, 260), window.innerWidth - 16);
      const spaceBelow = window.innerHeight - rect.bottom - 8;
      const openUp = spaceBelow < 160 && rect.top > spaceBelow;
      const height = Math.min(maxH, openUp ? Math.max(120, rect.top - 8) : Math.max(120, spaceBelow));
      setMenuPos({
        top: openUp ? Math.max(8, rect.top - height - 4) : rect.bottom + 4,
        left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
        width,
      });
    };
    place();
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [row.productOpen, row.productSearch, idx]);

  return (
    <tr>
      <td className="px-3 py-2 align-top">
        <div className="relative">
          <input
            ref={inputRef}
            value={row.productSearch && row.productSearch.length > 0 ? row.productSearch : products.find((p) => p.id === row.productId)?.name || ''}
            onChange={(e) => handleRowChange(idx, { productSearch: e.target.value, productOpen: true })}
            onFocus={() => handleRowChange(idx, { productOpen: true })}
            onBlur={() => setTimeout(() => handleRowChange(idx, { productOpen: false }), 150)}
            placeholder="ပစ္စည်း ရှာရန်..."
            className="h-9 w-full rounded-lg border border-slate-200 bg-white px-2.5 text-xs focus:border-indigo-400 focus:outline-none"
          />
          {row.productOpen && menuPos && createPortal(
            <div
              style={{ position: 'fixed', top: menuPos.top, left: menuPos.left, width: menuPos.width, zIndex: 9999 }}
              className="max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-xl"
            >
              {productChoices.length > 0 ? (
                <>
                  {productChoices.map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      onMouseDown={() => handleRowChange(idx, { productId: p.id, productSearch: p.name, productOpen: false, unitCost: unitCostFor(p) })}
                      className={`w-full px-3 py-2 text-left hover:bg-indigo-50 ${row.productId === p.id ? 'bg-indigo-50' : ''}`}
                    >
                      <p className="text-xs font-semibold text-slate-800">{p.name}</p>
                      <p className="text-[10px] text-slate-400">{p.productCode} · ဈေး {money(unitCostFor(p))}</p>
                    </button>
                  ))}
                  {filteredProducts.length > productChoices.length && (
                    <p className="sticky bottom-0 border-t border-slate-100 bg-slate-50 px-3 py-1.5 text-[10px] text-slate-500">
                      {filteredProducts.length} ခုထဲမှ {productChoices.length} ခု — ပိုရှာရန် စာရိုက်ပါ
                    </p>
                  )}
                </>
              ) : (
                <p className="p-2 text-center text-xs text-slate-400">ပစ္စည်း မတွေ့ပါ</p>
              )}
            </div>,
            document.body
          )}
        </div>
      </td>
      <td className="px-3 py-2">
        <input type="number" min="1" value={row.qty || ''} onChange={(e) => handleRowChange(idx, { qty: parseInt(e.target.value) || 0 })} className="w-full rounded border border-slate-200 px-2 py-1.5 text-right text-xs focus:border-indigo-400 focus:outline-none" />
      </td>
      <td className="px-3 py-2">
        <input type="number" min="0" step="0.01" value={row.unitCost || ''} onChange={(e) => handleRowChange(idx, { unitCost: parseFloat(e.target.value) || 0 })} placeholder="0.00" className="w-full rounded border border-slate-200 px-2 py-1.5 text-right text-xs focus:border-indigo-400 focus:outline-none" />
      </td>
      <td className="px-3 py-2 text-right font-bold tabular-nums text-slate-700">{money(row.subtotal)}</td>
      <td className="px-3 py-2 text-center">
        <button onClick={() => onDelete(idx)} className="rounded-md p-1.5 text-rose-500 hover:bg-rose-50"><Trash2 size={15} /></button>
      </td>
    </tr>
  );
};

type ReceiveLineForm = {
  detailId: number;
  productId: number;
  productName: string;
  pending: number;
  qty: number;
  damagedQty: number;
  rejectedQty: number;
  unitCost: number;
  invoiceUnitCost: number;
  hasSerial: boolean;
  serialNumbers: string[];
  batchNumber: string;
  expiryDate: string;
};
const dateInput = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const statusBadge: Record<string, string> = {
  PENDING_APPROVAL: 'bg-violet-100 text-violet-700 border border-violet-200',
  PENDING_FINAL_APPROVAL: 'bg-fuchsia-100 text-fuchsia-700 border border-fuchsia-200',
  APPROVED: 'bg-blue-100 text-blue-700 border border-blue-200',
  REJECTED: 'bg-rose-100 text-rose-700 border border-rose-200',
  OPEN: 'bg-sky-100 text-sky-700 border border-sky-200',
  PARTIAL: 'bg-amber-100 text-amber-700 border border-amber-200',
  RECEIVED: 'bg-emerald-100 text-emerald-700 border border-emerald-200',
  CLOSED: 'bg-slate-200 text-slate-600 border border-slate-300',
  CANCELLED: 'bg-slate-200 text-slate-500 border border-slate-300 line-through'
};
const statusLabel: Record<string, string> = {
  PENDING_APPROVAL: 'Pending Approval',
  PENDING_FINAL_APPROVAL: 'Pending Final Approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  OPEN: 'အော်ဒါဖွင့်',
  PARTIAL: 'တစ်ဝက်ရရှိ',
  RECEIVED: 'ရရှိပြီး',
  CLOSED: 'Closed',
  CANCELLED: 'ပယ်ဖျက်'
};

const emptyRow = (): PODetailForm => ({ productId: 0, productSearch: '', productOpen: false, qty: 1, receivedQty: 0, unitCost: 0, subtotal: 0 });

const PurchaseOrderManagement: React.FC = () => {
  const navigate = useNavigate();
  const sessionUser = (() => {
    try { return JSON.parse(sessionStorage.getItem('sspd_user') || '{}') as { staffId?: number; roles?: string[]; permissions?: string[] }; }
    catch { return {} as { staffId?: number; roles?: string[]; permissions?: string[] }; }
  })();
  const canApprove = (sessionUser.roles || []).some((role) => ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(role))
    || (sessionUser.permissions || []).includes('CAN_ACCESS_PURCHASE_ORDER_APPROVE');
  const isAdmin = (sessionUser.roles || []).some((role) => ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(role));
  const canFinalApprove = isAdmin
    || (sessionUser.permissions || []).includes('CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE');
  const canCancelDraftPo = isAdmin || (sessionUser.permissions || []).includes('CAN_ACCESS_PURCHASE_ORDER_DELETE');
  const canCancelApprovedPo = isAdmin || (sessionUser.permissions || []).includes('CAN_ACCESS_PURCHASE_ORDER_CANCEL_APPROVED');
  const canCreatePo = isAdmin || (sessionUser.permissions || []).includes('CAN_ACCESS_PURCHASE_ORDER_CREATE');
  const canUpdatePo = isAdmin || (sessionUser.permissions || []).includes('CAN_ACCESS_PURCHASE_ORDER_UPDATE');
  const canReceive = isAdmin || (sessionUser.permissions || []).includes('CAN_ACCESS_PURCHASE_ORDER_RECEIVE');
  const canCancelPo = (status?: string) => {
    const st = (status || '').toUpperCase();
    if (st === 'APPROVED') return canCancelApprovedPo;
    if (st === 'PENDING_APPROVAL' || st === 'PENDING_FINAL_APPROVAL' || st === 'OPEN') return canCancelDraftPo;
    return false;
  };
  const canReceivePo = (status?: string) => {
    const st = (status || '').toUpperCase();
    return canReceive && (st === 'APPROVED' || st === 'PARTIAL');
  };
  const hasReceiveProgress = (po: PurchaseOrderDTO) =>
    (po.details || []).some((d) => (d.receivedQty || 0) + (d.damagedQty || 0) + (d.rejectedQty || 0) > 0);
  const canClosePo = (po: PurchaseOrderDTO) => {
    const st = (po.status || '').toUpperCase();
    if (st === 'PARTIAL') return true;
    if (st === 'APPROVED' && hasReceiveProgress(po)) return true;
    return false;
  };
  const [orders, setOrders] = useState<PurchaseOrderDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [viewOrder, setViewOrder] = useState<PurchaseOrderDTO | null>(null);
  const [viewReceipts, setViewReceipts] = useState<GoodsReceiptDTO[]>([]);

  // form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [suppliers, setSuppliers] = useState<SupplierDTO[]>([]);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodDTO[]>([]);
  const [supplierId, setSupplierId] = useState(0);
  const [supplierSearch, setSupplierSearch] = useState('');
  const [supplierOpen, setSupplierOpen] = useState(false);
  const [staffId, setStaffId] = useState(0);
  const [staffSearch, setStaffSearch] = useState('');
  const [staffOpen, setStaffOpen] = useState(false);
  const [orderDate, setOrderDate] = useState(dateInput(new Date()));
  const [expectedDate, setExpectedDate] = useState('');
  const [remarkText, setRemarkText] = useState('');
  const [rows, setRows] = useState<PODetailForm[]>([emptyRow()]);
  const [saving, setSaving] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);

  // receive state
  const [receiveOrder, setReceiveOrder] = useState<PurchaseOrderDTO | null>(null);
  const [receiveLines, setReceiveLines] = useState<ReceiveLineForm[]>([]);
  const [receivePaidAmount, setReceivePaidAmount] = useState<number>(0);
  const [receiveMethodId, setReceiveMethodId] = useState(0);
  const [receiveInvoiceNo, setReceiveInvoiceNo] = useState('');
  const [receiveVarianceReason, setReceiveVarianceReason] = useState('');
  const [receiving, setReceiving] = useState(false);
  const [lateOrders, setLateOrders] = useState<PurchaseOrderDTO[]>([]);

  const fetchOrders = useCallback(async (pg: number, size: number, search: string) => {
    setLoading(true);
    try {
      const result: PurchaseOrderPage = await purchaseOrderApiService.getAllPaged(pg, size, search);
      setOrders(result.content);
      setTotalElements(result.totalElements);
      setTotalPages(result.totalPages);
      setLateOrders(await purchaseOrderApiService.getLate().catch(() => []));
    } catch (e) {
      console.error('Failed to load purchase orders', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const t = setTimeout(() => { setPage(0); setDebouncedSearch(searchTerm.trim()); }, 400);
    return () => clearTimeout(t);
  }, [searchTerm]);

  useEffect(() => { fetchOrders(page, pageSize, debouncedSearch); }, [fetchOrders, page, pageSize, debouncedSearch]);

  useEffect(() => {
    (async () => {
      try {
        const [supRes, staffRes, prodRes, payRes] = await Promise.all([
          supplierService.getAll(),
          staffService.getAll(),
          productService.getAll(),
          paymentMethodService.getAllActive()
        ]);
        setSuppliers(supRes);
        setStaffs(staffRes);
        setProducts(prodRes);
        setPaymentMethods(payRes);
        const me = JSON.parse(sessionStorage.getItem('sspd_user') || '{}') as { staffId?: number };
        const linked = staffRes.find((s) => s.id === me.staffId);
        setStaffId((prev) => prev || linked?.id || staffRes[0]?.id || 0);
      } catch (e) {
        console.error('Failed to load master data', e);
      }
    })();
  }, []);

  const filteredSuppliers = suppliers.filter((s) =>
    supplierSearch === '' || s.name.toLowerCase().includes(supplierSearch.toLowerCase()) ||
    s.code?.toLowerCase().includes(supplierSearch.toLowerCase()) ||
    s.phone?.toLowerCase().includes(supplierSearch.toLowerCase())
  );
  const filteredStaffs = staffs.filter((s) =>
    staffSearch === '' || s.name.toLowerCase().includes(staffSearch.toLowerCase()) ||
    s.role?.toLowerCase().includes(staffSearch.toLowerCase())
  );

  const totalAmount = rows.reduce((sum, r) => sum + r.subtotal, 0);
  const formValid = supplierId > 0 && staffId > 0 && rows.every((r) => r.productId > 0 && r.qty > 0 && r.unitCost >= 0);

  const handleOpenForm = () => {
    if (!canCreatePo) {
      Swal.fire({ icon: 'warning', title: 'ခွင့်ပြုချက် မရှိပါ', text: 'PO ဖန်တီးရန် CAN_ACCESS_PURCHASE_ORDER_CREATE လိုအပ်သည်။' });
      return;
    }
    setEditingId(null);
    setSupplierId(0);
    setSupplierSearch('');
    setSupplierOpen(false);
    setStaffSearch('');
    setStaffOpen(false);
    setStaffId(staffId);
    setOrderDate(dateInput(new Date()));
    setExpectedDate('');
    setRemarkText('');
    setRows([emptyRow()]);
    setShowForm(true);
  };

  const openViewOrder = async (po: PurchaseOrderDTO) => {
    setViewOrder(po);
    setViewReceipts([]);
    if (!po.id) return;
    try { setViewReceipts(await purchaseOrderApiService.getGoodsReceipts(po.id)); }
    catch { setViewReceipts([]); }
  };

  const handleEdit = (po: PurchaseOrderDTO) => {
    if (!canUpdatePo) {
      Swal.fire({ icon: 'warning', title: 'ခွင့်ပြုချက် မရှိပါ', text: 'PO ပြင်ဆင်ရန် CAN_ACCESS_PURCHASE_ORDER_UPDATE လိုအပ်သည်။' });
      return;
    }
    if (!['PENDING_APPROVAL', 'OPEN'].includes((po.status || 'PENDING_APPROVAL').toUpperCase())) {
      Swal.fire({ icon: 'warning', title: 'Cannot edit', text: 'Only orders awaiting approval can be edited.' });
      return;
    }
    setEditingId(po.id!);
    setSupplierId(po.supplierId);
    setStaffId(po.staffId);
    setOrderDate(po.orderDate ? po.orderDate.slice(0, 10) : dateInput(new Date()));
    setExpectedDate(po.expectedDate ? po.expectedDate.slice(0, 10) : '');
    setRemarkText(po.remark || '');
    setRows((po.details || []).map((d) => ({
      detailId: d.id,
      productId: d.productId,
      productSearch: d.productName ? `${d.productName}` : '',
      productOpen: false,
      qty: d.qty,
      receivedQty: d.receivedQty || 0,
      unitCost: d.unitCost,
      subtotal: d.subtotal
    })));
    setShowForm(true);
  };

  const handleRowChange = (idx: number, patch: Partial<PODetailForm>) => {
    setRows((prev) => {
      const next = [...prev];
      next[idx] = { ...next[idx], ...patch };
      next[idx].subtotal = (Number(next[idx].qty) || 0) * (Number(next[idx].unitCost) || 0);
      return next;
    });
  };

  const handleSave = async () => {
    if (!formValid || saving) return;
    if ((editingId && !canUpdatePo) || (!editingId && !canCreatePo)) {
      Swal.fire({ icon: 'warning', title: 'ခွင့်ပြုချက် မရှိပါ', text: editingId ? 'PO ပြင်ဆင်ခွင့် လိုအပ်သည်။' : 'PO ဖန်တီးခွင့် လိုအပ်သည်။' });
      return;
    }
    setSaving(true);
    try {
      const dto: PurchaseOrderDTO = {
        id: editingId ?? undefined,
        supplierId,
        staffId,
        orderDate: `${orderDate}T00:00:00`,
        expectedDate: expectedDate ? `${expectedDate}T00:00:00` : undefined,
        remark: remarkText.trim() || undefined,
        totalAmount,
        details: rows.map((r) => ({
          id: r.detailId,
          productId: r.productId,
          qty: Number(r.qty),
          unitCost: Number(r.unitCost),
          subtotal: r.subtotal
        }))
      };
      if (editingId) await purchaseOrderApiService.update(editingId, dto);
      else await purchaseOrderApiService.create(dto);
      Swal.fire({ icon: 'success', title: editingId ? 'Update ပြီး' : 'အော်ဒါ ဖန်တီးပြီး', timer: 1600, showConfirmButton: false });
      setShowForm(false);
      fetchOrders(page, pageSize, debouncedSearch);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: e.message || 'Failed to save purchase order' });
    } finally {
      setSaving(false);
    }
  };

  const handleCancelPO = async (po: PurchaseOrderDTO) => {
    if (actionBusy) return;
    const st = (po.status || '').toUpperCase();
    if (!canCancelPo(st)) {
      Swal.fire({
        icon: 'warning',
        title: 'ခွင့်ပြုချက် မရှိပါ',
        text: st === 'APPROVED'
          ? 'Approved PO ပယ်ဖျက်ရန် CAN_ACCESS_PURCHASE_ORDER_CANCEL_APPROVED လိုအပ်သည်။'
          : 'PO ပယ်ဖျက်ရန် CAN_ACCESS_PURCHASE_ORDER_DELETE လိုအပ်သည်။'
      });
      return;
    }
    setActionBusy(true);
    try {
      const res = await Swal.fire({
        icon: 'warning', title: 'အော်ဒါ ပယ်ဖျက်မည်လား?',
        html: `<b>${po.poCode || `#${po.id}`}</b>${st === 'APPROVED' ? '<br/><span style="font-size:12px;color:#64748b">Approved ပြီးသားဖြစ်သော်လည်း မလက်ခံရသေးပါက ပယ်ဖျက်နိုင်သည်။</span>' : ''}`,
        showCancelButton: true, confirmButtonText: 'ပယ်ဖျက်', cancelButtonText: 'မလုပ်တော့', confirmButtonColor: '#dc2626'
      });
      if (!res.isConfirmed) return;
      await purchaseOrderApiService.cancel(po.id!);
      Swal.fire({ icon: 'success', title: 'ပယ်ဖျက်ပြီး', timer: 1400, showConfirmButton: false });
      fetchOrders(page, pageSize, debouncedSearch);
      if (viewOrder?.id === po.id) setViewOrder(null);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: e.message || 'Failed to cancel order' });
    } finally {
      setActionBusy(false);
    }
  };

  const handleApprovePO = async (po: PurchaseOrderDTO) => {
    if (!po.id || actionBusy) return;
    setActionBusy(true);
    try {
      const result = await Swal.fire({
        icon: 'question', title: 'Approve Purchase Order?',
        text: `${po.poCode || '#' + po.id} can be received after approval.`,
        showCancelButton: true, confirmButtonText: 'Approve', confirmButtonColor: '#2563eb'
      });
      if (!result.isConfirmed) return;
      await purchaseOrderApiService.approve(po.id);
      setViewOrder(null);
      await fetchOrders(page, pageSize, debouncedSearch);
      Swal.fire({ icon: 'success', title: 'Approved', timer: 1200, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Approval failed', text: e.message || 'Unable to approve PO' });
    } finally {
      setActionBusy(false);
    }
  };

  const handleRejectPO = async (po: PurchaseOrderDTO) => {
    if (!po.id || actionBusy) return;
    setActionBusy(true);
    try {
      const result = await Swal.fire({
        icon: 'warning', title: 'Reject Purchase Order',
        input: 'textarea', inputLabel: 'Rejection reason', inputPlaceholder: 'Reason is required',
        showCancelButton: true, confirmButtonText: 'Reject', confirmButtonColor: '#e11d48',
        inputValidator: (value) => value?.trim() ? undefined : 'Rejection reason is required'
      });
      if (!result.isConfirmed) return;
      await purchaseOrderApiService.reject(po.id, String(result.value).trim());
      setViewOrder(null);
      await fetchOrders(page, pageSize, debouncedSearch);
      Swal.fire({ icon: 'success', title: 'Rejected', timer: 1200, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Rejection failed', text: e.message || 'Unable to reject PO' });
    } finally {
      setActionBusy(false);
    }
  };

  const handleClosePO = async (po: PurchaseOrderDTO) => {
    if (!po.id || !canClosePo(po) || actionBusy) return;
    setActionBusy(true);
    try {
      const result = await Swal.fire({
        icon: 'question',
        title: 'Close Purchase Order?',
        text: `${po.poCode || '#' + po.id} — remaining qty will not be received.`,
        input: 'textarea',
        inputLabel: 'Close reason (optional)',
        inputPlaceholder: 'e.g. Supplier short-shipped',
        showCancelButton: true,
        confirmButtonText: 'Close PO',
        confirmButtonColor: '#475569'
      });
      if (!result.isConfirmed) return;
      await purchaseOrderApiService.close(po.id, String(result.value || '').trim() || undefined);
      setViewOrder(null);
      await fetchOrders(page, pageSize, debouncedSearch);
      Swal.fire({ icon: 'success', title: 'Closed', timer: 1200, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Close failed', text: e.message || 'Unable to close PO' });
    } finally {
      setActionBusy(false);
    }
  };

  const openReceive = async (po: PurchaseOrderDTO) => {
    if (!canReceivePo(po.status)) {
      Swal.fire({ icon: 'warning', title: 'လက်ခံမရပါ', text: 'Approved/Partial PO နှင့် RECEIVE permission လိုအပ်သည်။' });
      return;
    }
    try {
      const full = await purchaseOrderApiService.getById(po.id!);
      const lines = (full.details || []).map((d) => {
        const prod = products.find((p) => p.id === d.productId);
        return {
          detailId: d.id!,
          productId: d.productId,
          productName: d.productName || prod?.name || `#${d.productId}`,
          pending: Math.max(0, d.qty - (d.receivedQty || 0) - (d.damagedQty || 0) - (d.rejectedQty || 0)),
          qty: Math.max(0, d.qty - (d.receivedQty || 0) - (d.damagedQty || 0) - (d.rejectedQty || 0)),
          damagedQty: 0,
          rejectedQty: 0,
          unitCost: d.unitCost,
          invoiceUnitCost: d.unitCost,
          hasSerial: d.hasSerial ?? prod?.hasSerial ?? false,
          serialNumbers: Array(Math.max(0, d.qty - (d.receivedQty || 0))).fill(''),
          batchNumber: '',
          expiryDate: ''
        };
      }).filter((l) => l.pending > 0);
      if (lines.length === 0) {
        Swal.fire({ icon: 'info', title: 'ပြီးစီးပြီး', text: 'ဤအော်ဒါ၏ ပစ္စည်းအားလုံး လက်ခံရရှိပြီးဖြစ်သည်။' });
        return;
      }
      setReceiveLines(lines.map((l) => ({ ...l, qty: l.pending, serialNumbers: resizeSerials(l.serialNumbers, l.pending) })));
      setReceivePaidAmount(0);
      setReceiveMethodId(0);
      setReceiveInvoiceNo('');
      setReceiveVarianceReason('');
      setReceiveOrder(full);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: e.message || 'Failed to load order' });
    }
  };

  const receiveTotal = receiveLines.reduce((s, l) => s + Math.max(0, Number(l.qty) || 0) * Number(l.invoiceUnitCost || 0), 0);
  const hasReceiveVariance = receiveLines.some((l) => Number(l.invoiceUnitCost) !== Number(l.unitCost));
  const serialIncomplete = receiveLines.some((l) => {
    if (!l.hasSerial || l.qty <= 0) return false;
    return (l.serialNumbers || []).map((s) => s.trim()).filter(Boolean).length !== Math.floor(Number(l.qty) || 0);
  });

  const handleReceive = async () => {
    if (!receiveOrder || receiving || !canReceivePo(receiveOrder.status)) return;
    if (serialIncomplete) {
      Swal.fire({ icon: 'warning', title: 'Serial လိုအပ်သည်', text: 'Serial ပစ္စည်းတိုင်းအတွက် Qty အရေအတွက်အတိုင်း Serial ထည့်ပါ။' });
      return;
    }
    const invalidQualityQty = receiveLines.some((l) => Number(l.qty) + Number(l.damagedQty) + Number(l.rejectedQty) > l.pending);
    if (invalidQualityQty) {
      Swal.fire({ icon: 'warning', title: 'Quantity exceeds pending', text: 'Accepted + damaged + rejected must not exceed pending quantity.' });
      return;
    }
    if (hasReceiveVariance && !receiveVarianceReason.trim()) {
      Swal.fire({ icon: 'warning', title: 'Variance reason required', text: 'Explain why supplier invoice price differs from the PO price.' });
      return;
    }
    const lines = receiveLines.filter((l) => Number(l.qty) + Number(l.damagedQty) + Number(l.rejectedQty) > 0).map((l) => ({
      detailId: l.detailId,
      qty: Math.floor(Number(l.qty)),
      damagedQty: Math.floor(Number(l.damagedQty)),
      rejectedQty: Math.floor(Number(l.rejectedQty)),
      invoiceUnitCost: Number(l.invoiceUnitCost),
      serialNumbers: l.hasSerial ? (l.serialNumbers || []).map((s) => s.trim()).filter(Boolean) : undefined,
      batchNumber: l.batchNumber.trim() || undefined,
      expiryDate: l.expiryDate || undefined
    }));
    if (lines.length === 0) {
      Swal.fire({ icon: 'warning', title: 'Quantities ထည့်ပါ', text: 'လက်ခံရရှိမည့် အရေအတွက်ကို ထည့်ပါ။' });
      return;
    }
    const payments: PaymentTransactionDTO[] =
      receivePaidAmount > 0 && receiveMethodId > 0
        ? [{ paymentMethodId: receiveMethodId, amount: receivePaidAmount }]
        : [];
    setReceiving(true);
    try {
      const created = await purchaseOrderApiService.receive(receiveOrder.id!, {
        // Backend resolves the authenticated receiver; this value is only honored with staff-override permission.
        staffId: sessionUser.staffId || 0,
        lines,
        dueDate: undefined,
        discountAmount: 0,
        taxAmount: 0,
        otherCharges: 0,
        remark: `Received from PO ${receiveOrder.poCode || receiveOrder.id}`,
        supplierInvoiceNo: receiveInvoiceNo.trim() || undefined,
        varianceReason: receiveVarianceReason.trim() || undefined,
        paymentMethodId: payments.length > 0 ? payments[0].paymentMethodId : undefined,
        transactionNo: undefined,
        payments: payments.length > 0 ? payments : undefined
      });
      Swal.fire({
        icon: 'success', title: 'Goods Received!',
        html: `GRN <b>${created?.goodsReceipt?.grnCode || '-'}</b> created.<br/>Matching: <b>${created?.goodsReceipt?.matchStatus || '-'}</b><br/>Voucher: <b>${created?.purchase?.purchaseCode || 'No accepted stock'}</b>`
      });
      setReceiveOrder(null);
      fetchOrders(page, pageSize, debouncedSearch);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'လက်ခံမရပါ', text: e.message || 'ပစ္စည်းလက်ခံရာတွင် အမှားရှိပါသည်။' });
    } finally {
      setReceiving(false);
    }
  };

  return (
    <div className="w-full max-w-none space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-xl font-black text-slate-800"><FileText size={22} className="text-indigo-500" /> အဝယ်အော်ဒါ (Purchase Orders)</h2>
          <p className="text-xs text-slate-500 mt-0.5">Supplier ဆီ အရင်မှာယူ — ပစ္စည်းရောက်မှ ဝယ်ယူမှုဘောင်ချာ ဖန်တီးမည်</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => fetchOrders(page, pageSize, debouncedSearch)} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> ပြန်ဖတ်ရန်
          </button>
          {canCreatePo && (
            <button onClick={handleOpenForm} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-bold text-white hover:bg-indigo-700">
              <Plus size={16} /> အော်ဒါအသစ်
            </button>
          )}
        </div>
      </div>

      {lateOrders.length > 0 && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-800">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" />
          <div>
            <p className="text-sm font-black">ရောက်မည့်ရက်ကျော်နေသော PO {lateOrders.length} ခု</p>
            <p className="mt-0.5 text-xs">{lateOrders.slice(0, 4).map((o) => o.poCode || `#${o.id}`).join(' · ')}{lateOrders.length > 4 ? ' …' : ''}</p>
          </div>
        </div>
      )}

      {/* Search */}
      <div className="rounded-xl border border-slate-200 bg-white p-3">
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="PO Code / Supplier ရှာပါ..."
            className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm font-medium outline-none focus:border-indigo-400 focus:bg-white"
          />
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-auto max-h-[62vh] custom-scrollbar">
          {loading ? (
            <div className="p-10 text-center text-slate-400">ဖတ်နေသည်...</div>
          ) : (
            <table className="w-full min-w-[900px] table-auto text-sm">
              <thead className="sticky top-0 z-10 bg-slate-50">
                <tr className="text-[11px] font-bold uppercase tracking-wide text-slate-500">
                  <th className="px-4 py-3 text-left">PO No</th>
                  <th className="px-4 py-3 text-left">ပေးသွင်းသူ</th>
                  <th className="px-4 py-3 text-left">အော်ဒါရက်</th>
                  <th className="px-4 py-3 text-left">ရောက်မည့်ရက်</th>
                  <th className="px-4 py-3 text-right">စုစုပေါင်း</th>
                  <th className="px-4 py-3 text-center">အခြေအနေ</th>
                  <th className="px-4 py-3 text-right">လုပ်ဆောင်ချက်</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {orders.length === 0 ? (
                  <tr><td colSpan={7} className="px-4 py-10 text-center text-slate-400">အဝယ်အော်ဒါ မရှိသေးပါ။</td></tr>
                ) : orders.map((po) => {
                  const st = (po.status || 'OPEN').toUpperCase();
                  const late = lateOrders.some((o) => o.id === po.id);
                  return (
                    <tr key={po.id} className={`hover:bg-slate-50/80 ${st === 'CANCELLED' ? 'opacity-60' : late ? 'bg-amber-50/80' : ''}`}>
                      <td className="px-4 py-3 font-mono text-xs font-bold text-slate-800">{po.poCode || `#${po.id}`}</td>
                      <td className="px-4 py-3 font-semibold text-slate-700">{po.supplierName || '-'}</td>
                      <td className="px-4 py-3 text-xs text-slate-500">{po.orderDate ? new Date(po.orderDate).toLocaleDateString() : '-'}</td>
                      <td className="px-4 py-3 text-xs text-slate-500">
                        {po.expectedDate ? new Date(po.expectedDate).toLocaleDateString() : '-'}
                        {late && <span className="ml-1 font-black text-amber-700">ကျော်</span>}
                      </td>
                      <td className="px-4 py-3 text-right font-semibold tabular-nums">{money(po.totalAmount)}</td>
                      <td className="px-4 py-3 text-center">
                        <span className={`inline-flex min-w-[86px] justify-center rounded-md px-2.5 py-1 text-[10px] font-black ${statusBadge[st] || ''}`}>{statusLabel[st] || st}</span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex items-center justify-end gap-1.5 whitespace-nowrap">
                          {canReceivePo(st) && (
                            <button onClick={() => openReceive(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-emerald-200 bg-emerald-600 px-2.5 text-xs font-bold text-white hover:bg-emerald-700" title="ပစ္စည်းလက်ခံမည်">
                              <PackageCheck size={14} /> Receive
                            </button>
                          )}
                          <button onClick={() => void openViewOrder(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-indigo-200 px-2.5 text-xs font-bold text-indigo-700 hover:bg-indigo-50">
                            <Eye size={14} /> ကြည့်မည်
                          </button>
                          {canApprove && (st === 'PENDING_APPROVAL' || st === 'OPEN') && (
                            <>
                              <button onClick={() => handleApprovePO(po)} className="inline-flex h-8 items-center rounded-md bg-blue-600 px-2.5 text-xs font-bold text-white hover:bg-blue-700">Approve</button>
                              <button onClick={() => handleRejectPO(po)} className="inline-flex h-8 items-center rounded-md border border-rose-200 px-2.5 text-xs font-bold text-rose-600 hover:bg-rose-50">Reject</button>
                            </>
                          )}
                          {canFinalApprove && st === 'PENDING_FINAL_APPROVAL' && (
                            <>
                              <button onClick={() => handleApprovePO(po)} className="inline-flex h-8 items-center rounded-md bg-fuchsia-600 px-2.5 text-xs font-bold text-white hover:bg-fuchsia-700">Final Approve</button>
                              <button onClick={() => handleRejectPO(po)} className="inline-flex h-8 items-center rounded-md border border-rose-200 px-2.5 text-xs font-bold text-rose-600 hover:bg-rose-50">Reject</button>
                            </>
                          )}
                          {canClosePo(po) && canApprove && (
                            <button onClick={() => handleClosePO(po)} className="inline-flex h-8 items-center rounded-md border border-slate-300 px-2.5 text-xs font-bold text-slate-600 hover:bg-slate-100" title="Short-close remaining">Close</button>
                          )}
                          {(st === 'PENDING_APPROVAL' || st === 'PENDING_FINAL_APPROVAL' || st === 'OPEN' || st === 'APPROVED') && (
                            <>
                              {canUpdatePo && (st === 'PENDING_APPROVAL' || st === 'OPEN') && (
                                <button onClick={() => handleEdit(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-amber-200 px-2.5 text-xs font-bold text-amber-700 hover:bg-amber-50">ပြင်</button>
                              )}
                              {canCancelPo(st) && (
                                <button onClick={() => handleCancelPO(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-300 px-2.5 text-xs font-bold text-slate-500 hover:bg-slate-100" title={st === 'APPROVED' ? 'Approved PO ပယ်ဖျက်' : 'ပယ်ဖျက်'}><Ban size={14} /></button>
                              )}
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
        {!loading && totalPages > 0 && (
          <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-3">
            <p className="text-xs text-slate-500">{page * pageSize + 1} မှ {Math.min((page + 1) * pageSize, totalElements)} / {totalElements.toLocaleString()} ခု</p>
            <div className="flex items-center gap-1.5">
              <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }} className="rounded-md border border-slate-200 px-2 py-1 text-xs">
                {[10, 20, 50, 100].map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="rounded-md border border-slate-200 px-3 py-1 text-xs font-bold disabled:opacity-40">နောက်ပြန်</button>
              <span className="text-xs font-bold text-slate-600">{page + 1}/{totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} className="rounded-md border border-slate-200 px-3 py-1 text-xs font-bold disabled:opacity-40">ရှေ့သို့</button>
            </div>
          </div>
        )}
      </div>

      {/* Create/Edit Form Modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-900/60 p-4">
          <div className="w-full max-w-4xl rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Purchase Order</p>
                <h3 className="font-black text-slate-800 text-sm mt-0.5">{editingId ? 'အော်ဒါ ပြင်ဆင်' : 'အဝယ်အော်ဒါ အသစ်ဖန်တီး'}</h3>
              </div>
              <button onClick={() => setShowForm(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X size={18} /></button>
            </div>
            <div className="space-y-4 p-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <label className="space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">ပေးသွင်းသူ *</span>
                  <div className="relative">
                    <input
                      value={supplierSearch && supplierSearch.length > 0 ? supplierSearch : filteredSuppliers.find((s) => s.id === supplierId)?.name || ''}
                      onChange={(e) => { setSupplierSearch(e.target.value); setSupplierOpen(true); }}
                      onFocus={() => setSupplierOpen(true)}
                      onBlur={() => setTimeout(() => setSupplierOpen(false), 120)}
                      placeholder="Search by name / code / phone..."
                      className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none"
                    />
                    {supplierOpen && (
                      <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                        {filteredSuppliers.length > 0 ? filteredSuppliers.map((s) => (
                          <button
                            key={s.id}
                            type="button"
                            onMouseDown={() => { setSupplierId(s.id); setSupplierSearch(s.name); setSupplierOpen(false); }}
                            className={`w-full px-3 py-2.5 text-left hover:bg-indigo-50 ${supplierId === s.id ? 'bg-indigo-50' : ''}`}
                          >
                            <p className="text-sm font-semibold text-slate-800">{s.name}</p>
                            <p className="text-xs text-slate-400">{s.code || '-'} {s.phone ? `· ${s.phone}` : ''}</p>
                          </button>
                        )) : <p className="p-3 text-center text-xs text-slate-400">No suppliers found</p>}
                      </div>
                    )}
                  </div>
                </label>
                <label className="space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">တာဝန်ခံ *</span>
                  <div className="relative">
                    <input
                      value={staffSearch && staffSearch.length > 0 ? staffSearch : filteredStaffs.find((s) => s.id === staffId)?.name || ''}
                      onChange={(e) => { setStaffSearch(e.target.value); setStaffOpen(true); }}
                      onFocus={() => setStaffOpen(true)}
                      onBlur={() => setTimeout(() => setStaffOpen(false), 120)}
                      placeholder="Search staff..."
                      className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none"
                    />
                    {staffOpen && (
                      <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                        {filteredStaffs.length > 0 ? filteredStaffs.map((s) => (
                          <button
                            key={s.id}
                            type="button"
                            onMouseDown={() => { setStaffId(s.id); setStaffSearch(s.name); setStaffOpen(false); }}
                            className={`w-full px-3 py-2.5 text-left hover:bg-indigo-50 ${staffId === s.id ? 'bg-indigo-50' : ''}`}
                          >
                            <p className="text-sm font-semibold text-slate-800">{s.name}</p>
                            <p className="text-xs text-slate-400">{s.role || '-'}</p>
                          </button>
                        )) : <p className="p-3 text-center text-xs text-slate-400">No staff found</p>}
                      </div>
                    )}
                  </div>
                </label>
                <label className="space-y-1.5">
                  <span className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-slate-400"><Calendar size={11} /> အော်ဒါရက်</span>
                  <input type="date" value={orderDate} onChange={(e) => setOrderDate(e.target.value)} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none" />
                </label>
                <label className="space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">ရောက်မည့်ရက်</span>
                  <input type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none" />
                </label>
              </div>

              <div className="rounded-xl border border-slate-200">
                <table className="w-full min-w-[680px] text-left text-sm">
                  <thead><tr className="bg-slate-50 text-[10px] font-black uppercase tracking-wide text-slate-500">
                    <th className="px-3 py-2.5">ပစ္စည်း</th>
                    <th className="px-3 py-2.5 w-24 text-right">အရေအတွက်</th>
                    <th className="px-3 py-2.5 w-32 text-right">ခန့်မှန်းဈေး</th>
                    <th className="px-3 py-2.5 w-32 text-right">စုစုပေါင်း</th>
                    <th className="w-12"></th>
                  </tr></thead>
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((row, idx) => (
                      <POTableRow
                        key={idx}
                        row={row}
                        idx={idx}
                        products={products}
                        handleRowChange={handleRowChange}
                        money={money}
                        onDelete={(i) => { if (rows.length > 1) setRows(rows.filter((_, j) => j !== i)); }}
                      />
                    ))}
                  </tbody>
                </table>
                <div className="border-t border-slate-100 p-2">
                  <button onClick={() => setRows([...rows, emptyRow()])} className="inline-flex items-center gap-1.5 rounded-lg border border-dashed border-indigo-300 px-3 py-1.5 text-xs font-bold text-indigo-600 hover:bg-indigo-50">
                    <Plus size={13} /> Row ထပ်ထည့်
                  </button>
                </div>
              </div>

              <label className="block space-y-1.5">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">မှတ်ချက်</span>
                <textarea value={remarkText} onChange={(e) => setRemarkText(e.target.value)} rows={2} placeholder="Optional notes..." className="w-full resize-none rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none" />
              </label>

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-3">
                <div className="text-sm"><span className="text-slate-500">စုစုပေါင်း:</span> <b className="ml-1 text-lg text-indigo-700">{money(totalAmount)}</b></div>
                <div className="flex gap-2">
                  <button onClick={() => setShowForm(false)} className="rounded-lg px-4 py-2.5 text-sm font-bold text-slate-600 hover:bg-slate-100">မလုပ်တော့ပါ</button>
                  <button onClick={handleSave} disabled={!formValid || saving} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-700 disabled:bg-slate-300 disabled:cursor-not-allowed">
                    <Save size={16} /> {saving ? 'သိမ်းနေသည်...' : editingId ? 'Update မည်' : 'သိမ်းမည်'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* View Modal */}
      {viewOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
          <div className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-slate-800">PO: {viewOrder.poCode || `#${viewOrder.id}`}</h3>
                <span className={`rounded-md px-2 py-0.5 text-[10px] font-black ${statusBadge[(viewOrder.status || 'OPEN').toUpperCase()] || ''}`}>
                  {statusLabel[(viewOrder.status || 'OPEN').toUpperCase()] || viewOrder.status}
                </span>
              </div>
              <button onClick={() => { setViewOrder(null); setViewReceipts([]); }} className="rounded-lg p-1.5 text-slate-400 hover:text-slate-700"><X size={18} /></button>
            </div>
            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
                <p className="text-slate-600"><span className="text-slate-500">ပေးသွင်းသူ:</span> {viewOrder.supplierName}</p>
                <p className="text-slate-600"><span className="text-slate-500">တာဝန်ခံ:</span> {viewOrder.staffName || '-'}</p>
                <p className="text-slate-600"><span className="text-slate-500">အော်ဒါရက်:</span> {viewOrder.orderDate ? new Date(viewOrder.orderDate).toLocaleDateString() : '-'}</p>
                <p className="text-slate-600"><span className="text-slate-500">ရောက်မည့်ရက်:</span> {viewOrder.expectedDate ? new Date(viewOrder.expectedDate).toLocaleDateString() : '-'}</p>
                <p className="text-slate-600"><span className="text-slate-500">စုစုပေါင်း:</span> <b>{money(viewOrder.totalAmount)}</b></p>
              </div>
              {viewOrder.remark && <p className="text-sm text-slate-600"><span className="text-slate-500">မှတ်ချက်:</span> {viewOrder.remark}</p>}
              <table className="w-full text-left text-sm border-collapse">
                <thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500">
                  <th className="border-b px-3 py-2">ပစ္စည်း</th>
                  <th className="border-b px-3 py-2 text-right">မှာယူ</th>
                  <th className="border-b px-3 py-2 text-right">ရရှိ</th>
                  <th className="border-b px-3 py-2 text-right">ဈေး</th>
                  <th className="border-b px-3 py-2 text-right">စုစုပေါင်း</th>
                </tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {(viewOrder.details || []).map((d, i) => (
                    <tr key={i}>
                      <td className="px-3 py-2">{d.productName || d.productId}</td>
                      <td className="px-3 py-2 text-right">{d.qty}</td>
                      <td className={`px-3 py-2 text-right font-bold ${(d.receivedQty || 0) >= d.qty ? 'text-emerald-600' : 'text-amber-600'}`}>{d.receivedQty || 0}</td>
                      <td className="px-3 py-2 text-right">{money(d.unitCost)}</td>
                      <td className="px-3 py-2 text-right font-medium">{money(d.subtotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div>
                <h4 className="mb-2 text-xs font-black uppercase tracking-wider text-slate-500">GRN History</h4>
                {viewReceipts.length === 0 ? <p className="text-xs text-slate-400">No goods receipts yet.</p> : (
                  <div className="space-y-2">{viewReceipts.map(grn => (
                    <div key={grn.id} className="rounded-lg border border-slate-200 p-3 text-xs">
                      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
                        <b className="font-mono">{grn.grnCode}</b>
                        <span className={`rounded px-2 py-0.5 text-[10px] font-black ${grn.matchStatus === 'VARIANCE' ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700'}`}>{grn.matchStatus}</span>
                      </div>
                      <p className="text-slate-500">{grn.receivedAt ? new Date(grn.receivedAt).toLocaleString() : '-'} · {grn.receivedBy || '-'}{grn.supplierInvoiceNo ? ` · Inv ${grn.supplierInvoiceNo}` : ''}</p>
                      {grn.varianceReason && <p className="mt-1 text-rose-600">{grn.varianceReason}</p>}
                      <table className="mt-2 w-full"><thead><tr className="text-[10px] uppercase text-slate-400"><th className="py-1 text-left">Item</th><th className="py-1 text-right">Accepted</th><th className="py-1 text-right">Damaged</th><th className="py-1 text-right">Rejected</th></tr></thead>
                      <tbody>{(grn.lines||[]).map((line,i)=><tr key={i}><td className="py-1">{line.productName}</td><td className="py-1 text-right">{line.acceptedQty}</td><td className="py-1 text-right">{line.damagedQty}</td><td className="py-1 text-right">{line.rejectedQty}</td></tr>)}</tbody></table>
                    </div>
                  ))}</div>
                )}
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 border-t border-slate-100 p-4">
              {['PENDING_APPROVAL', 'PENDING_FINAL_APPROVAL', 'OPEN', 'APPROVED', 'PARTIAL'].includes((viewOrder.status || 'OPEN').toUpperCase()) && (
                <>
                  {canCancelPo(viewOrder.status) && (
                    <button onClick={() => { handleCancelPO(viewOrder); }} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-bold text-slate-600 hover:bg-slate-100"><Ban size={13} /> ပယ်ဖျက်</button>
                  )}
                  {['PENDING_APPROVAL', 'OPEN'].includes((viewOrder.status || '').toUpperCase()) && (
                    <button onClick={() => { handleEdit(viewOrder); setViewOrder(null); }} className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-bold text-amber-700 hover:bg-amber-50">ပြင်ဆင်</button>
                  )}
                  {canApprove && ['PENDING_APPROVAL', 'OPEN'].includes((viewOrder.status || '').toUpperCase()) && (
                    <>
                      <button onClick={() => handleApprovePO(viewOrder)} className="rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-blue-700">Approve</button>
                      <button onClick={() => handleRejectPO(viewOrder)} className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-bold text-rose-600 hover:bg-rose-50">Reject</button>
                    </>
                  )}
                  {canFinalApprove && (viewOrder.status || '').toUpperCase() === 'PENDING_FINAL_APPROVAL' && (
                    <>
                      <button onClick={() => handleApprovePO(viewOrder)} className="rounded-lg bg-fuchsia-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-fuchsia-700">Final Approve</button>
                      <button onClick={() => handleRejectPO(viewOrder)} className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-bold text-rose-600 hover:bg-rose-50">Reject</button>
                    </>
                  )}
                  {canClosePo(viewOrder) && canApprove && (
                    <button onClick={() => handleClosePO(viewOrder)} className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-bold text-slate-600 hover:bg-slate-100">Close</button>
                  )}
                </>
              )}
              {canReceivePo(viewOrder.status) && (
                <button onClick={() => { openReceive(viewOrder); setViewOrder(null); }} className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-1.5 text-xs font-bold text-white hover:bg-emerald-700"><PackageCheck size={13} /> Receive</button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Receive Modal */}
      {receiveOrder && (() => {
        const paidAmt = Number(receivePaidAmount) || 0;
        const due = Math.max(0, receiveTotal - paidAmt);
        return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 p-4">
          <div className="flex max-h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-emerald-500">Goods Receipt</p>
                <h3 className="flex items-center gap-2 text-sm font-black text-slate-800"><PackageCheck size={16} className="text-emerald-500" /> {receiveOrder.poCode || `#${receiveOrder.id}`} — ပစ္စည်းလက်ခံ</h3>
              </div>
              <button onClick={() => setReceiveOrder(null)} className="rounded-lg p-1.5 text-slate-400 hover:text-slate-700"><X size={18} /></button>
            </div>
            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              <table className="w-full text-left text-sm">
                <thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500">
                  <th className="border-b px-3 py-2">ပစ္စည်း</th>
                  <th className="border-b px-3 py-2 text-right">ကျန်</th>
                  <th className="border-b px-3 py-2 text-right">လက်ခံ</th>
                  <th className="border-b px-3 py-2 text-right">Damaged</th>
                  <th className="border-b px-3 py-2 text-right">Rejected</th>
                  <th className="border-b px-3 py-2 text-right">PO / Invoice Cost</th>
                </tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {receiveLines.map((line, idx) => (
                    <tr key={line.detailId}>
                      <td className="px-3 py-2 align-top">
                        <p className="font-semibold text-slate-800">
                          {line.productName}
                          {line.hasSerial && line.qty > 0 && (
                            <span className="ml-1.5 inline-block rounded bg-violet-100 px-1.5 py-0.5 text-[9px] font-black text-violet-700">SERIAL</span>
                          )}
                        </p>
                        <div className="mt-1.5 grid grid-cols-2 gap-1.5">
                          <input value={line.batchNumber} onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => i === idx ? { ...l, batchNumber: e.target.value } : l))} placeholder="Batch" className="rounded border border-slate-200 px-2 py-1 text-[11px]" />
                          <input type="date" value={line.expiryDate} onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => i === idx ? { ...l, expiryDate: e.target.value } : l))} className="rounded border border-slate-200 px-2 py-1 text-[11px]" />
                        </div>
                        {line.hasSerial && line.qty > 0 && (
                          <div className="mt-2 space-y-1">
                            <div className="flex items-center justify-between">
                              <span className="text-[10px] font-bold uppercase text-violet-600">Serials ({line.qty})</span>
                              <button type="button" onClick={() => setReceiveLines((prev) => prev.map((l, i) => i === idx ? { ...l, serialNumbers: generateSerialNumbers(l.qty) } : l))} className="text-[10px] font-bold text-indigo-600 hover:underline">အလိုအလျောက်</button>
                            </div>
                            {resizeSerials(line.serialNumbers, line.qty).map((sn, sIdx) => (
                              <input
                                key={sIdx}
                                value={sn}
                                onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => {
                                  if (i !== idx) return l;
                                  const next = resizeSerials(l.serialNumbers, l.qty);
                                  next[sIdx] = e.target.value;
                                  return { ...l, serialNumbers: next };
                                }))}
                                placeholder={`Serial ${sIdx + 1}`}
                                className="w-full rounded border border-violet-200 bg-violet-50/40 px-2 py-1 font-mono text-[11px]"
                              />
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="px-3 py-2 text-right text-slate-500 align-top">{line.pending}</td>
                      <td className="px-3 py-2 text-right align-top">
                        <input
                          type="number"
                          min="0"
                          max={line.pending}
                          value={line.qty || ''}
                          onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => {
                            if (i !== idx) return l;
                            const qty = Math.min(l.pending, Math.max(0, parseFloat(e.target.value) || 0));
                            return { ...l, qty, serialNumbers: l.hasSerial ? resizeSerials(l.serialNumbers, qty) : [] };
                          }))}
                          className="w-20 rounded border border-slate-200 px-2 py-1 text-right text-xs font-bold focus:border-emerald-400 focus:outline-none"
                        />
                      </td>
                      <td className="px-3 py-2 text-right align-top">
                        <input type="number" min="0" max={line.pending} value={line.damagedQty || ''} onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => i === idx ? { ...l, damagedQty: Math.max(0, Number(e.target.value) || 0) } : l))} className="w-16 rounded border border-amber-200 px-2 py-1 text-right text-xs" />
                      </td>
                      <td className="px-3 py-2 text-right align-top">
                        <input type="number" min="0" max={line.pending} value={line.rejectedQty || ''} onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => i === idx ? { ...l, rejectedQty: Math.max(0, Number(e.target.value) || 0) } : l))} className="w-16 rounded border border-rose-200 px-2 py-1 text-right text-xs" />
                      </td>
                      <td className="px-3 py-2 text-right align-top">
                        <p className="text-[10px] text-slate-400">PO {money(line.unitCost)}</p>
                        <input type="number" min="0" step="0.01" value={line.invoiceUnitCost} onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => i === idx ? { ...l, invoiceUnitCost: Math.max(0, Number(e.target.value) || 0) } : l))} className={`mt-1 w-24 rounded border px-2 py-1 text-right text-xs font-bold ${line.invoiceUnitCost !== line.unitCost ? 'border-rose-300 text-rose-600' : 'border-slate-200'}`} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {serialIncomplete && (
                <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-semibold text-amber-700">
                  Serial ပစ္စည်းတိုင်း Qty အရေအတွက်အတိုင်း Serial ထည့်ပါ။
                </p>
              )}

              <div className="space-y-3 rounded-xl border border-slate-100 bg-slate-50/60 p-3">
                <label className="block space-y-1">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Supplier Invoice No.</span>
                  <input value={receiveInvoiceNo} onChange={(e) => setReceiveInvoiceNo(e.target.value)} placeholder="Optional" className="w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs focus:border-indigo-400 focus:outline-none" />
                </label>
                {hasReceiveVariance && (
                  <label className="block space-y-1">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-rose-500">Invoice price variance reason *</span>
                    <textarea value={receiveVarianceReason} onChange={(e) => setReceiveVarianceReason(e.target.value)} rows={2} placeholder="Explain PO vs invoice price difference" className="w-full rounded-lg border border-rose-200 bg-white px-2 py-2 text-xs focus:border-rose-400 focus:outline-none" />
                  </label>
                )}
                <div className="flex items-center justify-between text-sm"><span className="text-slate-500">Receive စုစုပေါင်း</span><b className="text-indigo-700">{money(receiveTotal)}</b></div>
                <div className="grid grid-cols-2 gap-2">
                  <label className="space-y-1 block">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Payment Method</span>
                    <select value={receiveMethodId} onChange={(e) => setReceiveMethodId(Number(e.target.value))} className="w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs focus:border-indigo-400 focus:outline-none">
                      <option value={0}>Credit (အကြွေး)</option>
                      {paymentMethods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                    </select>
                  </label>
                  <label className="space-y-1 block">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Advance Paid</span>
                    <input type="number" min="0" max={receiveTotal || undefined} value={receivePaidAmount || ''} onChange={(e) => setReceivePaidAmount(Math.min(receiveTotal, Math.max(0, parseFloat(e.target.value) || 0)))} placeholder="0.00" className="w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs font-bold text-emerald-600 focus:border-indigo-400 focus:outline-none" />
                  </label>
                </div>
                <div className="flex items-center justify-between text-sm border-t border-slate-200 pt-2">
                  <span className="text-slate-500">ကျန်ငွေ (အကြွေး)</span>
                  <b className={due > 0 ? 'text-rose-600' : 'text-emerald-600'}>{money(due)}</b>
                </div>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 border-t border-slate-100 p-4">
              <button onClick={() => setReceiveOrder(null)} className="rounded-lg px-4 py-2 text-sm font-bold text-slate-600 hover:bg-slate-100">မလုပ်တော့ပါ</button>
              <button onClick={handleReceive} disabled={receiving || serialIncomplete} className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-emerald-700 disabled:bg-slate-300 disabled:cursor-not-allowed">
                {receiving ? <Loader2 size={16} className="animate-spin" /> : <PackageCheck size={16} />}
                {receiving ? 'Processing...' : 'လက်ခံ + Voucher ဖန်တီး'}
              </button>
            </div>
          </div>
        </div>
        );
      })()}
    </div>
  );
};

export default PurchaseOrderManagement;
