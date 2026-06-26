import React, { useEffect, useState, useMemo } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl, TextInput,
  Modal, KeyboardAvoidingView, Platform, ScrollView, Alert,
} from 'react-native';
import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import { Ionicons } from '@expo/vector-icons';
import { LOGO_BASE64 } from '../../assets/logoBase64';
import { api } from '../../api/client';
import { ApiResponse, ServiceJobDTO, SettleJobDTO, PaymentMethodDTO, ProductSerialDTO } from '../../types';

type SnWarrantyMap = Record<string, ProductSerialDTO>;
import { C } from '../../theme';
import { useWsTopic } from '../../hooks/useWsTopic';
import { useAuth } from '../../context/AuthContext';
import { fmtSerialWarranty } from '../../utils/warrantyFormat';

const fmtDate = (v?: string) => {
  if (!v) return '';
  try {
    const d = new Date(v);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch { return v; }
};

const STATUS_COL: Record<string, { bg: string; text: string }> = {
  COMPLETED: { bg: C.warningBg, text: C.warning },
  DELIVERED: { bg: C.successBg, text: C.success },
};

// ── Voucher builder (matches ServiceJobDetailScreen) ──────────────────────────
function buildVoucherHtml(job: ServiceJobDTO, snWarMap: SnWarrantyMap = {}): string {
  const esc = (v?: string | number | null) =>
    String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const money = (v?: number | null) =>
    Number(v || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const lines = (job.lines ?? []).map((l, i) => {
    const _dur = fmtSerialWarranty(l.warrantyMonths); const warTxt = _dur ? `${_dur} အာမခံ` : '';
    return `
      <tr>
        <td class="center">${i + 1}</td>
        <td>
          <div>${esc(l.serviceItemName ?? l.subServiceTypeName ?? l.serviceTypeName ?? l.description ?? '-')}</div>
          ${warTxt ? `<div style="font-size:10px;color:#0891b2;margin-top:2px;">&#128737; ${esc(warTxt)}</div>` : ''}
        </td>
        <td>${esc(l.description ?? '')}</td>
        <td class="num">${money(l.price ?? l.cost)}</td>
      </tr>`;
  }).join('') || '<tr><td colspan="4" class="center muted">No services recorded</td></tr>';

  const condLabel = (t?: string | null) => {
    if (t === 'NEW') return 'New';
    if (t === 'SECOND') return 'Used';
    if (t === 'SECOND_NEW') return 'Like New';
    return t ?? '-';
  };
  const condStyle = (t?: string | null) => {
    if (t === 'NEW') return 'background:#dcfce7;color:#166534';
    if (t === 'SECOND') return 'background:#fef9c3;color:#854d0e';
    if (t === 'SECOND_NEW') return 'background:#ede9fe;color:#5b21b6';
    return 'background:#f1f5f9;color:#475569';
  };

  const parts = (job.productParts ?? []).map((p, i) => {
    const snHtml = (p.serialNumbers ?? []).map(sn => {
      const info = snWarMap[sn];
      const wLabel = info ? fmtSerialWarranty(info.warrantyMonths) : '';
      return `<div style="font-size:10px;color:#1d4ed8;margin-top:1px;">S/N: ${esc(sn)}${wLabel ? ` &nbsp;&#128737; ${esc(wLabel)}` : ''}</div>`;
    }).join('');
    return `
    <tr>
      <td class="center">${i + 1}</td>
      <td>
        <div>${esc(p.productName ?? '-')}</div>
        ${snHtml}
      </td>
      <td class="center"><span style="display:inline-block;padding:1px 7px;border-radius:20px;font-size:10px;font-weight:600;${condStyle(p.productType)}">${esc(condLabel(p.productType))}</span></td>
      <td class="num">${p.qty}</td>
      <td class="num">${money(p.unitPrice)}</td>
      <td class="num">${money(p.unitPrice * p.qty)}</td>
    </tr>`;
  }).join('') || '<tr><td colspan="6" class="center muted">No parts used</td></tr>';

  const finalCost = Number(job.finalCost) || 0;
  const discount  = Number(job.discountAmount) || 0;
  const net       = Number(job.netAmount) || Math.max(0, finalCost - discount);
  const paid      = Number(job.paidAmount) || 0;
  const due       = Number(job.dueAmount) || Math.max(0, net - paid);

  return `<!doctype html><html><head><meta charset="utf-8"/>
  <style>
    @page{size:A4 portrait;margin:10mm}
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:'Segoe UI',Arial,sans-serif;color:#1e293b;font-size:12px;line-height:1.5;background:#fff}
    .header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;padding:18px 20px 14px;background:#1e40af;border-radius:10px 10px 0 0}
    .brand-name{font-size:22px;font-weight:800;color:#fff}
    .brand-sub{font-size:10px;color:#94a3b8;margin-top:3px}
    .inv-box{text-align:right;flex-shrink:0}
    .inv-label{font-size:9px;text-transform:uppercase;letter-spacing:1px;color:#64748b}
    .inv-code{font-size:18px;font-weight:800;color:#fff;margin-top:2px}
    .inv-date{font-size:10px;color:#94a3b8;margin-top:2px}
    .body-wrap{border:1px solid #e2e8f0;border-top:none;border-radius:0 0 10px 10px;padding:14px 20px}
    .blocks{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:14px;padding-bottom:14px;border-bottom:1px dashed #e2e8f0}
    .block{padding:10px 12px;background:#f8fafc;border-radius:7px;border:1px solid #e2e8f0}
    .block-title{font-size:9px;text-transform:uppercase;letter-spacing:.8px;color:#64748b;margin-bottom:6px;padding-bottom:4px;border-bottom:1px solid #e2e8f0}
    .block-row{display:flex;justify-content:space-between;gap:8px;margin-top:4px}
    .bl{color:#64748b;font-size:10px}
    .bv{font-weight:600;font-size:11px;text-align:right;max-width:180px;word-break:break-word}
    .section-label{font-size:9.5px;text-transform:uppercase;letter-spacing:.7px;color:#64748b;font-weight:700;margin-bottom:6px;margin-top:14px}
    .table-wrap{border:1px solid #e2e8f0;border-radius:7px;overflow:hidden;margin-bottom:8px}
    table{width:100%;border-collapse:collapse}
    th{background:#f1f5f9;color:#475569;font-size:9.5px;text-transform:uppercase;letter-spacing:.6px;padding:6px 8px;border-bottom:1px solid #e2e8f0;font-weight:700}
    td{padding:6px 8px;border-bottom:1px solid #f1f5f9;vertical-align:top}
    tr:last-child td{border-bottom:none}
    tr:nth-child(even) td{background:#fafbfd}
    .num{text-align:right;white-space:nowrap}
    .center{text-align:center}
    .muted{padding:12px;color:#94a3b8}
    .summary-box{width:250px;border:1px solid #e2e8f0;border-radius:7px;overflow:hidden;margin-left:auto;margin-top:12px}
    .s-row{display:flex;justify-content:space-between;align-items:center;padding:6px 12px;border-bottom:1px solid #f1f5f9;font-size:12px}
    .s-row:last-child{border-bottom:none}
    .s-row.highlight{background:#1e40af;color:#fff;font-weight:700}
    .s-row.sub-highlight{background:#f1f5f9;font-weight:600}
    .s-val{font-weight:600}
    .footer-bar{margin-top:12px;padding-top:8px;border-top:1px dashed #e2e8f0;text-align:center;font-size:9.5px;color:#94a3b8}
    .signatures{margin-top:24px;display:grid;grid-template-columns:1fr 1fr;gap:24px}
    .sign{padding-top:30px;border-top:1px solid #cbd5e1;text-align:center;font-size:10px;color:#64748b}
  </style></head><body>
  <div class="header">
    <div style="display:flex;align-items:center;gap:10px;">
      <img src="${LOGO_BASE64}" alt="logo" style="height:44px;width:44px;object-fit:contain;background:#fff;border-radius:6px;padding:3px;" />
      <div class="brand-name">Service Voucher</div>
    </div>
    <div class="inv-box">
      <div class="inv-label">Job No</div>
      <div class="inv-code">${esc(job.jobNo ?? `#${job.id}`)}</div>
      <div class="inv-date">${esc(job.receivedDate ?? '')}</div>
    </div>
  </div>
  <div class="body-wrap">
    <div class="blocks">
      <div class="block">
        <div class="block-title">Device / Item</div>
        <div class="block-row"><span class="bl">Customer</span><span class="bv">${esc(job.customerName)}</span></div>
        <div class="block-row"><span class="bl">Item</span><span class="bv">${esc(job.itemName)}</span></div>
        <div class="block-row"><span class="bl">Condition</span><span class="bv">${esc(job.itemCondition)}</span></div>
        <div class="block-row"><span class="bl">Problem</span><span class="bv">${esc(job.problemDesc)}</span></div>
      </div>
      <div class="block">
        <div class="block-title">Technician / Status</div>
        <div class="block-row"><span class="bl">Technician</span><span class="bv">${esc(job.assignedStaffName)}</span></div>
        <div class="block-row"><span class="bl">Status</span><span class="bv">${esc((job.status ?? '').replace(/_/g, ' '))}</span></div>
        <div class="block-row"><span class="bl">Received</span><span class="bv">${esc(job.receivedDate)}</span></div>
        <div class="block-row"><span class="bl">Payment</span><span class="bv">${esc(job.paymentStatus)}</span></div>
      </div>
    </div>
    ${job.diagnosisNotes ? `<div class="section-label">Diagnosis Notes</div><div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:7px;padding:10px 12px;font-size:11px;color:#475569;white-space:pre-wrap">${esc(job.diagnosisNotes)}</div>` : ''}
    <div class="section-label">Services</div>
    <div class="table-wrap"><table>
      <thead><tr><th style="width:5%" class="center">#</th><th style="width:40%">Service</th><th style="width:35%">Notes</th><th style="width:20%" class="num">Price</th></tr></thead>
      <tbody>${lines}</tbody>
    </table></div>
    <div class="section-label">Parts Used</div>
    <div class="table-wrap"><table>
      <thead><tr><th style="width:5%" class="center">#</th><th style="width:32%">Part Name</th><th style="width:15%" class="center">Condition</th><th style="width:10%" class="num">Qty</th><th style="width:18%" class="num">Unit Price</th><th style="width:20%" class="num">Subtotal</th></tr></thead>
      <tbody>${parts}</tbody>
    </table></div>
    <div class="summary-box">
      <div class="s-row"><span>Payment Summary</span></div>
      <div class="s-row"><span>Estimated</span><span class="s-val">${money(job.estimatedCost)}</span></div>
      <div class="s-row"><span>Final Cost</span><span class="s-val">${money(finalCost)}</span></div>
      <div class="s-row"><span>Discount</span><span class="s-val">${money(discount)}</span></div>
      <div class="s-row sub-highlight"><span>Net Amount</span><span class="s-val">${money(net)}</span></div>
      <div class="s-row"><span>Paid</span><span class="s-val">${money(paid)}</span></div>
      <div class="s-row highlight"><span>Balance Due</span><span class="s-val">${money(due)}</span></div>
    </div>
    ${job.remark ? `<div class="section-label">Remark</div><div style="border:1px solid #e2e8f0;border-radius:7px;padding:8px 10px;font-size:11px;color:#475569;margin-top:4px">${esc(job.remark)}</div>` : ''}
    <div class="signatures">
      <div class="sign">Technician Signature</div>
      <div class="sign">Customer Signature</div>
    </div>
    <div class="footer-bar">Thank you for your business · ${esc(job.jobNo ?? `#${job.id}`)}</div>
  </div>
</body></html>`;
}

// ── Settle Modal ──────────────────────────────────────────────────────────────
function SettleModal({ visible, job, onClose, onSettled }: {
  visible: boolean; job: ServiceJobDTO | null; onClose: () => void; onSettled: (j: ServiceJobDTO) => Promise<void>;
}) {
  const [payMethods,    setPayMethods]    = useState<PaymentMethodDTO[]>([]);
  const [finalCost,     setFinalCost]     = useState('0');
  const [discount,      setDiscount]      = useState('0');
  const [foc,           setFoc]           = useState(false);
  const [paidAmt,       setPaidAmt]       = useState('');
  const [payMethod,     setPayMethod]     = useState<PaymentMethodDTO | null>(null);
  const [saving,        setSaving]        = useState(false);
  const [showPayPicker, setShowPayPicker] = useState(false);

  useEffect(() => {
    if (visible && job) {
      api.get<ApiResponse<PaymentMethodDTO[]>>('/payment-methods/active')
        .then(r => setPayMethods(r.data ?? [])).catch(() => {});
      setFinalCost(String(job.estimatedCost ?? 0));
      setDiscount('0');
      setFoc(false);
      setPaidAmt('');
      setPayMethod(null);
    }
  }, [visible, job]);

  if (!job) return null;

  const net = foc ? 0 : Math.max(0, (Number(finalCost) || 0) - (Number(discount) || 0));

  const submit = async () => {
    setSaving(true);
    try {
      const body: SettleJobDTO = {
        finalCost: foc ? 0 : (Number(finalCost) || 0),
        discountAmount: Number(discount) || 0,
        foc,
        paidAmount: foc ? 0 : (Number(paidAmt) || net),
        paymentMethodId: payMethod?.id,
      };
      const res = await api.post<ApiResponse<ServiceJobDTO>>(`/service-jobs/${job.id}/settle`, body);
      await onSettled(res.data);
    } catch (e: any) { Alert.alert('Error', e?.message ?? 'Settle failed'); }
    setSaving(false);
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={sm.header}>
          <Text style={sm.title}>Settle & Deliver — {job.jobNo ?? `#${job.id}`}</Text>
          <TouchableOpacity onPress={onClose}><Text style={sm.close}>✕</Text></TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          <TouchableOpacity style={[sm.focBtn, foc && { backgroundColor: C.success }]} onPress={() => setFoc(!foc)}>
            <Text style={[sm.focText, foc && { color: '#fff' }]}>{foc ? '★ FREE OF CHARGE (ON)' : 'Free of Charge?'}</Text>
          </TouchableOpacity>
          {!foc && (
            <>
              <Text style={sm.label}>Final Cost (Ks)</Text>
              <TextInput style={sm.input} value={finalCost} onChangeText={setFinalCost} keyboardType="numeric" placeholderTextColor={C.textMuted} />
              <Text style={sm.label}>Discount (Ks)</Text>
              <TextInput style={sm.input} value={discount} onChangeText={setDiscount} keyboardType="numeric" placeholder="0" placeholderTextColor={C.textMuted} />
              <View style={sm.netRow}>
                <Text style={sm.netLabel}>Net Amount</Text>
                <Text style={sm.netVal}>{net.toLocaleString()} Ks</Text>
              </View>
              <Text style={sm.label}>Paid Amount (Ks)</Text>
              <TextInput style={sm.input} value={paidAmt} onChangeText={setPaidAmt} keyboardType="numeric" placeholder={String(net)} placeholderTextColor={C.textMuted} />
              <Text style={sm.label}>Payment Method</Text>
              <TouchableOpacity style={sm.picker} onPress={() => setShowPayPicker(true)}>
                <Text style={[sm.pickerVal, !payMethod && { color: C.textMuted }]}>{payMethod?.methodName ?? 'ရွေးပါ...'}</Text>
                <Text style={{ color: C.textMuted, fontSize: 18 }}>›</Text>
              </TouchableOpacity>
            </>
          )}
          <TouchableOpacity style={[sm.submitBtn, saving && { opacity: 0.6 }]} onPress={submit} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={sm.submitBtnText}>✓  Settle & Deliver</Text>}
          </TouchableOpacity>
        </ScrollView>

        <Modal visible={showPayPicker} animationType="slide" onRequestClose={() => setShowPayPicker(false)}>
          <View style={{ flex: 1, backgroundColor: C.bg }}>
            <View style={sm.header}>
              <Text style={sm.title}>Payment Method</Text>
              <TouchableOpacity onPress={() => setShowPayPicker(false)}><Text style={sm.close}>✕</Text></TouchableOpacity>
            </View>
            {payMethods.map(p => (
              <TouchableOpacity key={p.id} style={sm.pmItem} onPress={() => { setPayMethod(p); setShowPayPicker(false); }}>
                <Text style={sm.pmText}>{p.methodName}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </Modal>
      </KeyboardAvoidingView>
    </Modal>
  );
}

export default function DoneServiceJobsScreen({ navigation }: any) {
  const { hasPermission } = useAuth() as any;
  const [items,      setItems]      = useState<ServiceJobDTO[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search,     setSearch]     = useState('');
  const [tab,        setTab]        = useState<'COMPLETED' | 'DELIVERED'>('COMPLETED');
  const [settleJob,  setSettleJob]  = useState<ServiceJobDTO | null>(null);
  const [printing,   setPrinting]   = useState<number | null>(null);

  const canSettle = hasPermission?.('CAN_ACCESS_SERVICE_JOB_SETTLE') ?? false;

  const load = async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<ServiceJobDTO[]>>(
        '/service-jobs?page=0&size=200&status=COMPLETED,DELIVERED'
      );
      const data: ServiceJobDTO[] = Array.isArray(res.data) ? res.data : (res.data as any)?.content ?? [];
      setItems(data);
    } catch {}
    setLoading(false);
    setRefreshing(false);
  };

  useEffect(() => { load(); }, []);
  useWsTopic('/topic/service-jobs', () => load(true));

  const printJob = async (jobId: number) => {
    setPrinting(jobId);
    try {
      const res = await api.get<ApiResponse<ServiceJobDTO>>(`/service-jobs/${jobId}`);
      const job = res.data;
      const allSerials = (job.productParts ?? []).flatMap(p => p.serialNumbers ?? []);
      let snWarMap: SnWarrantyMap = {};
      if (allSerials.length > 0) {
        try {
          const snRes = await api.post<ApiResponse<ProductSerialDTO[]>>('/product-serials/by-serials', allSerials);
          (snRes.data ?? []).forEach(s => { snWarMap[s.serialNumber] = s; });
        } catch {}
      }
      const html = buildVoucherHtml(job, snWarMap);
      const { uri } = await Print.printToFileAsync({ html });
      await Sharing.shareAsync(uri, { mimeType: 'application/pdf', dialogTitle: 'Print / Save PDF' });
    } catch (e: any) {
      if (!String(e?.message ?? '').includes('cancel')) {
        Alert.alert('Error', e?.message ?? 'Failed to generate voucher');
      }
    }
    setPrinting(null);
  };

  const handleCardPress = (item: ServiceJobDTO) => {
    const needsSettle = canSettle && item.status === 'COMPLETED' && !item.paymentStatus;
    if (needsSettle) {
      setSettleJob(item);
    } else {
      printJob(item.id!);
    }
  };

  const handleSettled = async (updated: ServiceJobDTO) => {
    setSettleJob(null);
    try {
      const deliverRes = await api.post<ApiResponse<ServiceJobDTO>>(`/service-jobs/${updated.id}/deliver`);
      const delivered = deliverRes.data;
      setItems(prev => prev.map(j => j.id === delivered.id ? delivered : j));
      await printJob(delivered.id!);
    } catch {
      setItems(prev => prev.map(j => j.id === updated.id ? updated : j));
      await printJob(updated.id!);
    }
  };

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return items.filter(j =>
      j.status === tab &&
      (!q || (j.jobNo ?? '').toLowerCase().includes(q) ||
             (j.customerName ?? '').toLowerCase().includes(q) ||
             (j.itemName ?? '').toLowerCase().includes(q))
    );
  }, [items, search, tab]);

  const completedCount = items.filter(j => j.status === 'COMPLETED').length;
  const deliveredCount = items.filter(j => j.status === 'DELIVERED').length;

  const renderItem = ({ item }: { item: ServiceJobDTO }) => {
    const col = STATUS_COL[item.status ?? 'DELIVERED'] ?? STATUS_COL.DELIVERED;
    const needsSettle = canSettle && item.status === 'COMPLETED' && !item.paymentStatus;
    const isPrinting  = printing === item.id;
    return (
      <TouchableOpacity
        style={[st.card, needsSettle && st.cardPending]}
        onPress={() => handleCardPress(item)}
        activeOpacity={0.75}
        disabled={isPrinting}
      >
        <View style={st.cardTop}>
          <Text style={st.code}>{item.jobNo ?? `#${item.id}`}</Text>
          <View style={[st.badge, { backgroundColor: col.bg }]}>
            <Text style={[st.badgeText, { color: col.text }]}>{(item.status ?? '').replace(/_/g, ' ')}</Text>
          </View>
        </View>
        <Text style={st.customer}>{item.customerName ?? '-'}</Text>
        {item.itemName && <Text style={st.item}>{item.itemName}</Text>}
        <View style={st.dateRow}>
          {item.receivedDate ? (
            <View style={st.dateChip}>
              <Ionicons name="calendar-outline" size={11} color={C.textMuted} />
              <Text style={st.dateChipText}>Rcvd: {fmtDate(item.receivedDate)}</Text>
            </View>
          ) : null}
          {item.completedDate ? (
            <View style={[st.dateChip, { backgroundColor: C.warningBg }]}>
              <Ionicons name="checkmark-circle-outline" size={11} color={C.warning} />
              <Text style={[st.dateChipText, { color: C.warning }]}>Done: {fmtDate(item.completedDate)}</Text>
            </View>
          ) : null}
          {item.deliveredDate ? (
            <View style={[st.dateChip, { backgroundColor: C.successBg }]}>
              <Ionicons name="checkmark-done-circle-outline" size={11} color={C.success} />
              <Text style={[st.dateChipText, { color: C.success }]}>Delivered: {fmtDate(item.deliveredDate)}</Text>
            </View>
          ) : null}
        </View>
        <View style={st.cardBottom}>
          {item.netAmount
            ? <Text style={st.amount}>{Number(item.netAmount).toLocaleString()} Ks</Text>
            : <View />
          }
          {isPrinting
            ? <ActivityIndicator size="small" color={C.primary} />
            : needsSettle
              ? <View style={st.settleHint}><Text style={st.settleHintText}>💰 Tap to Settle</Text></View>
              : <View style={st.voucherHint}><Ionicons name="document-text-outline" size={13} color={C.violet} /><Text style={st.voucherHintText}>Voucher</Text></View>
          }
        </View>
        {item.rework && (
          <View style={st.reworkBadge}><Text style={st.reworkText}>REWORK</Text></View>
        )}
        {item.paymentStatus && (
          <View style={st.paidBadge}><Text style={st.paidText}>PAID</Text></View>
        )}
      </TouchableOpacity>
    );
  };

  return (
    <View style={st.root}>
      {/* Search */}
      <View style={st.searchWrap}>
        <TextInput
          style={st.searchInput}
          placeholder="Search job / customer / item..."
          placeholderTextColor={C.textMuted}
          value={search}
          onChangeText={setSearch}
        />
        {search.length > 0 && (
          <TouchableOpacity onPress={() => setSearch('')} style={st.clearBtn}>
            <Text style={st.clearText}>✕</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Tabs */}
      <View style={st.tabRow}>
        <TouchableOpacity
          style={[st.tab, tab === 'COMPLETED' && { ...st.tabActive, backgroundColor: C.warningBg, borderBottomColor: C.warning }]}
          onPress={() => setTab('COMPLETED')}
        >
          <Text style={[st.tabText, tab === 'COMPLETED' && { color: C.warning }]}>
            Completed ({completedCount})
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[st.tab, tab === 'DELIVERED' && st.tabActive]}
          onPress={() => setTab('DELIVERED')}
        >
          <Text style={[st.tabText, tab === 'DELIVERED' && st.tabActiveText]}>
            Delivered ({deliveredCount})
          </Text>
        </TouchableOpacity>
      </View>

      {loading
        ? <ActivityIndicator color={C.primary} style={{ marginTop: 40 }} size="large" />
        : (
          <FlatList
            data={filtered}
            keyExtractor={i => String(i.id)}
            renderItem={renderItem}
            contentContainerStyle={{ padding: 12, paddingBottom: 24 }}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={() => { setRefreshing(true); load(true); }}
                colors={[C.primary]}
                tintColor={C.primary}
              />
            }
            ListEmptyComponent={
              <Text style={st.empty}>
                {tab === 'DELIVERED' ? 'No delivered jobs' : 'No completed jobs'}
              </Text>
            }
          />
        )
      }

      <SettleModal
        visible={!!settleJob}
        job={settleJob}
        onClose={() => setSettleJob(null)}
        onSettled={handleSettled}
      />
    </View>
  );
}

const st = StyleSheet.create({
  root:          { flex: 1, backgroundColor: C.bg },
  searchWrap:    { flexDirection: 'row', alignItems: 'center', backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border, paddingHorizontal: 12, paddingVertical: 8 },
  searchInput:   { flex: 1, fontSize: 14, color: C.text, paddingVertical: 6 },
  clearBtn:      { padding: 4 },
  clearText:     { fontSize: 13, color: C.textMuted },
  tabRow:        { flexDirection: 'row', backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border },
  tab:           { flex: 1, paddingVertical: 11, alignItems: 'center' },
  tabActive:     { backgroundColor: C.successBg, borderBottomWidth: 2, borderBottomColor: C.success },
  tabText:       { fontSize: 13, fontWeight: '700', color: C.textMuted },
  tabActiveText: { color: C.success },
  card:          { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 8 },
  cardPending:   { borderColor: C.warning, borderWidth: 1.5 },
  cardTop:       { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 },
  code:          { fontSize: 14, fontWeight: '800', color: C.primary },
  badge:         { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  badgeText:     { fontSize: 10, fontWeight: '700' },
  customer:      { fontSize: 14, fontWeight: '600', color: C.text, marginBottom: 2 },
  item:          { fontSize: 12, color: C.textMuted, marginBottom: 6 },
  dateRow:       { flexDirection: 'row', flexWrap: 'wrap', gap: 5, marginBottom: 8 },
  dateChip:      { flexDirection: 'row', alignItems: 'center', gap: 3, backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 6, paddingHorizontal: 7, paddingVertical: 3 },
  dateChipText:  { fontSize: 11, color: C.textMuted, fontWeight: '600' },
  cardBottom:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  amount:        { fontSize: 14, fontWeight: '800', color: C.text },
  settleHint:    { backgroundColor: C.warningBg, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8 },
  settleHintText:{ fontSize: 11, fontWeight: '800', color: C.warning },
  voucherHint:   { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: C.violetBg, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8 },
  voucherHintText:{ fontSize: 11, fontWeight: '700', color: C.violet },
  paidBadge:     { position: 'absolute' as any, top: 10, right: 10, backgroundColor: C.successBg, paddingHorizontal: 7, paddingVertical: 2, borderRadius: 4 },
  paidText:      { fontSize: 9, fontWeight: '800', color: C.success },
  reworkBadge:   { position: 'absolute' as any, bottom: 10, right: 10, backgroundColor: C.dangerBg, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  reworkText:    { fontSize: 9, fontWeight: '800', color: C.danger },
  empty:         { textAlign: 'center', color: C.textMuted, marginTop: 40, fontSize: 14 },
});

const sm = StyleSheet.create({
  header:        { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border },
  title:         { fontSize: 16, fontWeight: '800', color: C.text, flex: 1 },
  close:         { fontSize: 20, color: C.textMuted, paddingHorizontal: 8 },
  label:         { fontSize: 12, fontWeight: '700', color: C.textMuted, marginTop: 16, marginBottom: 6, textTransform: 'uppercase' },
  input:         { backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, fontSize: 15, color: C.text },
  focBtn:        { padding: 14, borderRadius: 10, backgroundColor: C.border, alignItems: 'center', marginBottom: 8 },
  focText:       { fontSize: 14, fontWeight: '700', color: C.textMuted },
  netRow:        { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 12, padding: 12, backgroundColor: C.primaryLight, borderRadius: 10 },
  netLabel:      { fontSize: 13, fontWeight: '700', color: C.primary },
  netVal:        { fontSize: 18, fontWeight: '900', color: C.primary },
  picker:        { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12 },
  pickerVal:     { fontSize: 15, color: C.text },
  submitBtn:     { marginTop: 24, backgroundColor: C.success, padding: 16, borderRadius: 12, alignItems: 'center' },
  submitBtnText: { color: '#fff', fontWeight: '800', fontSize: 16 },
  pmItem:        { padding: 16, borderBottomWidth: 1, borderBottomColor: C.border },
  pmText:        { fontSize: 15, color: C.text, fontWeight: '600' },
});
