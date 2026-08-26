import React, { useEffect, useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet,
  ActivityIndicator, Alert, Modal, TextInput,
  KeyboardAvoidingView, Platform, FlatList,
} from 'react-native';
import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import { LOGO_BASE64 } from '../../assets/logoBase64';
import { api } from '../../api/client';
import {
  ApiResponse, ServiceJobDTO, ServiceJobLineDTO, ServiceJobPartDTO,
  ServiceItemDTO, SettleJobDTO, PaymentMethodDTO, ProductDTO, ProductSerialDTO,
  CustomerDTO, StaffDTO,
} from '../../types';

type SnWarrantyMap = Record<string, ProductSerialDTO>;
import { C } from '../../theme';
import { useAuth } from '../../context/AuthContext';
import ScannerModal from '../../components/ScannerModal';
import { fmtSerialWarranty } from '../../utils/warrantyFormat';

const STATUSES = ['RECEIVED', 'INSPECTING', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED', 'CANCELLED'];
const STATUS_COL: Record<string, { bg: string; text: string }> = {
  RECEIVED:    { bg: C.primaryLight, text: C.primary },
  INSPECTING:  { bg: C.warningBg,    text: C.warning },
  IN_PROGRESS: { bg: C.violetBg,     text: C.violet  },
  COMPLETED:   { bg: C.successBg,    text: C.success },
  DELIVERED:   { bg: C.successBg,    text: C.success },
  CANCELLED:   { bg: C.dangerBg,     text: C.danger  },
};

function Row({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null;
  return (
    <View style={st.row}>
      <Text style={st.rowKey}>{label}</Text>
      <Text style={st.rowVal}>{value}</Text>
    </View>
  );
}

function formatPartRequests(value?: string | null) {
  if (!value?.trim()) return '';
  try {
    const rows = JSON.parse(value);
    if (!Array.isArray(rows)) return value;
    return rows.filter((row: any) => String(row?.partName ?? '').trim()).map((row: any) => {
      const action = ({ REPLACE: 'လဲရန်', REPAIR: 'ပြုပြင်ရန်', CHECK: 'စစ်ဆေးရန်' } as Record<string, string>)[row.action] ?? row.action ?? '';
      const notice = String(row.notice ?? '').trim();
      return `${row.partName}${action ? ` — ${action}` : ''} × ${Number(row.qty || 1)}${notice ? ` (${notice})` : ''}`;
    }).join('\n');
  } catch {
    return value;
  }
}
// ── Settle Modal ──────────────────────────────────────────────────────────────
function SettleModal({ visible, job, onClose, onSettled }: {
  visible: boolean; job: ServiceJobDTO; onClose: () => void; onSettled: (j: ServiceJobDTO) => void;
}) {
  const [payMethods, setPayMethods] = useState<PaymentMethodDTO[]>([]);
  const [finalCost,  setFinalCost]  = useState(String(job.estimatedCost ?? 0));
  const [discount,   setDiscount]   = useState('0');
  const [foc,        setFoc]        = useState(false);
  const [paidAmt,    setPaidAmt]    = useState('');
  const [payMethod,  setPayMethod]  = useState<PaymentMethodDTO | null>(null);
  const [saving,     setSaving]     = useState(false);
  const [showPayPicker, setPayPicker] = useState(false);

  useEffect(() => {
    if (visible) {
      api.get<ApiResponse<PaymentMethodDTO[]>>('/payment-methods/active')
        .then(r => setPayMethods(r.data ?? [])).catch(() => {});
      setFinalCost(String(job.estimatedCost ?? 0));
    }
  }, [visible]);

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
      onSettled(res.data);
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
              <TouchableOpacity style={sm.picker} onPress={() => setPayPicker(true)}>
                <Text style={[sm.pickerVal, !payMethod && { color: C.textMuted }]}>{payMethod?.methodName ?? 'ရွေးပါ...'}</Text>
                <Text style={{ color: C.textMuted, fontSize: 18 }}>›</Text>
              </TouchableOpacity>
            </>
          )}
          <TouchableOpacity style={[sm.submitBtn, saving && { opacity: 0.6 }]} onPress={submit} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={sm.submitBtnText}>✓  Settle & Deliver</Text>}
          </TouchableOpacity>
        </ScrollView>
        <Modal visible={showPayPicker} animationType="slide" onRequestClose={() => setPayPicker(false)}>
          <View style={{ flex: 1, backgroundColor: C.bg }}>
            <View style={sm.header}>
              <Text style={sm.title}>Payment Method</Text>
              <TouchableOpacity onPress={() => setPayPicker(false)}><Text style={sm.close}>✕</Text></TouchableOpacity>
            </View>
            {payMethods.map(p => (
              <TouchableOpacity key={p.id} style={sm.pmItem} onPress={() => { setPayMethod(p); setPayPicker(false); }}>
                <Text style={sm.pmText}>{p.methodName}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </Modal>
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ── Edit Job Modal ─────────────────────────────────────────────────────────────
function EditJobModal({ visible, job, onClose, onSaved }: {
  visible: boolean; job: ServiceJobDTO; onClose: () => void; onSaved: (j: ServiceJobDTO) => void;
}) {
  const [customers,   setCustomers]   = useState<CustomerDTO[]>([]);
  const [staffList,   setStaffList]   = useState<StaffDTO[]>([]);
  const [saving,      setSaving]      = useState(false);

  const [selCustomer, setSelCustomer] = useState<CustomerDTO | null>(null);
  const [selStaff,    setSelStaff]    = useState<StaffDTO | null>(null);
  const [itemName,    setItemName]    = useState('');
  const [itemCond,    setItemCond]    = useState('');
  const [problemDesc, setProblemDesc] = useState('');
  const [diagNotes,   setDiagNotes]   = useState('');
  const [estComp,     setEstComp]     = useState('');
  const [remark,      setRemark]      = useState('');
  const [estimatedCost, setEstimatedCost] = useState('');

  const [showCustPicker, setCustPicker] = useState(false);
  const [showStaffPicker, setStaffPicker] = useState(false);
  const [custSearch,  setCustSearch]  = useState('');
  const [staffSearch, setStaffSearch] = useState('');

  useEffect(() => {
    if (visible) {
      setItemName(job.itemName ?? '');
      setItemCond(job.itemCondition ?? '');
      setProblemDesc(job.problemDesc ?? '');
      setDiagNotes(job.diagnosisNotes ?? '');
      setEstComp(job.estimatedCompletion ?? '');
      setRemark(job.remark ?? '');
      setEstimatedCost(String(job.estimatedCost ?? ''));
      setSelCustomer(job.customerId ? { id: job.customerId, name: job.customerName ?? '', phone: '', address: '' } : null);
      setSelStaff(job.assignedStaffId ? { id: job.assignedStaffId, name: job.assignedStaffName ?? '', role: '', active: true } : null);

      api.get<ApiResponse<CustomerDTO[]>>('/customers').then(r => setCustomers(r.data ?? [])).catch(() => {});
      api.get<ApiResponse<StaffDTO[]>>('/staff/active').then(r => setStaffList(r.data ?? [])).catch(() => {});
    }
  }, [visible]);

  const save = async () => {
    setSaving(true);
    try {
      const body: ServiceJobDTO = {
        ...job,
        customerId: selCustomer?.id ?? job.customerId,
        assignedStaffId: selStaff?.id ?? job.assignedStaffId,
        itemName: itemName.trim() || undefined,
        itemCondition: itemCond.trim() || undefined,
        problemDesc: problemDesc.trim() || undefined,
        diagnosisNotes: diagNotes.trim() || undefined,
        estimatedCompletion: estComp.trim() || undefined,
        remark: remark.trim() || undefined,
        estimatedCost: estimatedCost ? (Number(estimatedCost) || undefined) : undefined,
      };
      const res = await api.put<ApiResponse<ServiceJobDTO>>(`/service-jobs/${job.id}`, body);
      onSaved(res.data);
    } catch (e: any) { Alert.alert('Error', e?.message ?? 'Save failed'); }
    setSaving(false);
  };

  const filteredCust  = customers.filter(c => c.name.toLowerCase().includes(custSearch.toLowerCase()) || c.phone.includes(custSearch));
  const filteredStaff = staffList.filter(s => s.name.toLowerCase().includes(staffSearch.toLowerCase()));

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={sm.header}>
          <Text style={sm.title}>Edit Job — {job.jobNo ?? `#${job.id}`}</Text>
          <TouchableOpacity onPress={onClose}><Text style={sm.close}>✕</Text></TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          <Text style={sm.label}>Customer</Text>
          <TouchableOpacity style={sm.picker} onPress={() => setCustPicker(true)}>
            <Text style={[sm.pickerVal, !selCustomer && { color: C.textMuted }]}>{selCustomer?.name ?? 'Select customer...'}</Text>
            <Text style={{ color: C.textMuted, fontSize: 18 }}>›</Text>
          </TouchableOpacity>

          <Text style={sm.label}>Assigned Staff</Text>
          <TouchableOpacity style={sm.picker} onPress={() => setStaffPicker(true)}>
            <Text style={[sm.pickerVal, !selStaff && { color: C.textMuted }]}>{selStaff?.name ?? 'Select staff...'}</Text>
            <Text style={{ color: C.textMuted, fontSize: 18 }}>›</Text>
          </TouchableOpacity>

          <Text style={sm.label}>Item Name</Text>
          <TextInput style={sm.input} value={itemName} onChangeText={setItemName} placeholder="e.g. iPhone 14 Pro" placeholderTextColor={C.textMuted} />

          <Text style={sm.label}>Item Condition</Text>
          <TextInput style={sm.input} value={itemCond} onChangeText={setItemCond} placeholder="e.g. Screen cracked" placeholderTextColor={C.textMuted} />

          <Text style={sm.label}>Problem Description</Text>
          <TextInput style={[sm.input, { minHeight: 70, textAlignVertical: 'top' }]} value={problemDesc} onChangeText={setProblemDesc} multiline placeholder="Describe the problem..." placeholderTextColor={C.textMuted} />

          <Text style={sm.label}>Diagnosis Notes</Text>
          <TextInput style={[sm.input, { minHeight: 70, textAlignVertical: 'top' }]} value={diagNotes} onChangeText={setDiagNotes} multiline placeholder="Technician diagnosis..." placeholderTextColor={C.textMuted} />

          <Text style={sm.label}>Estimated Cost (Ks)</Text>
          <TextInput style={sm.input} value={estimatedCost} onChangeText={setEstimatedCost} keyboardType="numeric" placeholder="0" placeholderTextColor={C.textMuted} />

          <Text style={sm.label}>Estimated Completion Date</Text>
          <TextInput style={sm.input} value={estComp} onChangeText={setEstComp} placeholder="YYYY-MM-DD" placeholderTextColor={C.textMuted} />

          <Text style={sm.label}>Remark</Text>
          <TextInput style={[sm.input, { minHeight: 60, textAlignVertical: 'top' }]} value={remark} onChangeText={setRemark} multiline placeholder="Additional notes..." placeholderTextColor={C.textMuted} />

          <TouchableOpacity style={[sm.submitBtn, saving && { opacity: 0.6 }]} onPress={save} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={sm.submitBtnText}>💾  Save Changes</Text>}
          </TouchableOpacity>
        </ScrollView>

        {/* Customer picker */}
        <Modal visible={showCustPicker} animationType="slide" onRequestClose={() => setCustPicker(false)}>
          <View style={{ flex: 1, backgroundColor: C.bg }}>
            <View style={sm.header}>
              <Text style={sm.title}>Select Customer</Text>
              <TouchableOpacity onPress={() => setCustPicker(false)}><Text style={sm.close}>✕</Text></TouchableOpacity>
            </View>
            <TextInput style={sm.searchInput} placeholder="Search name / phone..." value={custSearch} onChangeText={setCustSearch} placeholderTextColor={C.textMuted} />
            <FlatList data={filteredCust} keyExtractor={c => String(c.id)} renderItem={({ item }) => (
              <TouchableOpacity style={sm.pmItem} onPress={() => { setSelCustomer(item); setCustPicker(false); setCustSearch(''); }}>
                <Text style={sm.pmText}>{item.name}</Text>
                {item.phone ? <Text style={{ fontSize: 11, color: C.textMuted, marginTop: 2 }}>{item.phone}</Text> : null}
              </TouchableOpacity>
            )} />
          </View>
        </Modal>

        {/* Staff picker */}
        <Modal visible={showStaffPicker} animationType="slide" onRequestClose={() => setStaffPicker(false)}>
          <View style={{ flex: 1, backgroundColor: C.bg }}>
            <View style={sm.header}>
              <Text style={sm.title}>Select Staff</Text>
              <TouchableOpacity onPress={() => setStaffPicker(false)}><Text style={sm.close}>✕</Text></TouchableOpacity>
            </View>
            <TextInput style={sm.searchInput} placeholder="Search..." value={staffSearch} onChangeText={setStaffSearch} placeholderTextColor={C.textMuted} />
            <FlatList data={filteredStaff} keyExtractor={s => String(s.id)} renderItem={({ item }) => (
              <TouchableOpacity style={sm.pmItem} onPress={() => { setSelStaff(item); setStaffPicker(false); setStaffSearch(''); }}>
                <Text style={sm.pmText}>{item.name}</Text>
                {item.role ? <Text style={{ fontSize: 11, color: C.textMuted, marginTop: 2 }}>{item.role}</Text> : null}
              </TouchableOpacity>
            )} />
          </View>
        </Modal>
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ── Edit Service Lines Modal ───────────────────────────────────────────────────
function EditLinesModal({ visible, job, onClose, onSaved }: {
  visible: boolean; job: ServiceJobDTO; onClose: () => void; onSaved: (j: ServiceJobDTO) => void;
}) {
  const [services, setServices] = useState<ServiceItemDTO[]>([]);
  const [lines, setLines] = useState<(ServiceJobLineDTO & { _name: string })[]>([]);
  const [saving, setSaving] = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const [showSvcPicker, setSvcPicker] = useState(false);
  const [selSvc, setSelSvc] = useState<ServiceItemDTO | null>(null);
  const [addDesc, setAddDesc] = useState('');
  const [addCost, setAddCost] = useState('');
  const [svcSearch, setSvcSearch] = useState('');

  useEffect(() => {
    if (visible) {
      setLines((job.lines ?? []).map(l => ({ ...l, _name: l.serviceItemName ?? l.subServiceTypeName ?? l.serviceTypeName ?? l.description ?? '-' })));
      api.get<ApiResponse<ServiceItemDTO[]>>('/services/active').then(r => setServices(r.data ?? [])).catch(() => {});
    }
  }, [visible]);

  const confirmAdd = () => {
    if (!selSvc) return;
    setLines(prev => [...prev, {
      serviceItemId: selSvc.id,
      serviceItemName: selSvc.item,
      qty: 1,
      price: Number(addCost) || selSvc.price || 0,
      description: addDesc.trim() || selSvc.item || '',
      _name: selSvc.item,
    }]);
    setSelSvc(null); setAddDesc(''); setAddCost(''); setShowAdd(false);
  };

  const save = async () => {
    setSaving(true);
    try {
      const body = { ...job, lines: lines.map(l => ({ serviceItemId: l.serviceItemId, qty: l.qty ?? 1, warrantyMonths: l.warrantyMonths ?? 0 })) };
      const res = await api.put<ApiResponse<ServiceJobDTO>>(`/service-jobs/${job.id}`, body);
      onSaved(res.data);
    } catch (e: any) { Alert.alert('Error', e?.message ?? 'Save failed'); }
    setSaving(false);
  };

  const filteredSvcs = services.filter(s => s.item.toLowerCase().includes(svcSearch.toLowerCase()) || (s.serviceTypeName ?? '').toLowerCase().includes(svcSearch.toLowerCase()));
  const total = lines.reduce((s, l) => s + (Number(l.price ?? l.cost) || 0), 0);

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={sm.header}>
          <Text style={sm.title}>Edit Service Lines</Text>
          <TouchableOpacity onPress={onClose}><Text style={sm.close}>✕</Text></TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          {lines.map((l, i) => (
            <View key={i} style={sm.lineRow}>
              <View style={{ flex: 1 }}>
                <Text style={sm.lineName}>{l._name || l.serviceItemName || l.description || '-'}</Text>
              </View>
              <Text style={sm.lineCost}>{(Number(l.price ?? l.cost) || 0).toLocaleString()} Ks</Text>
              <TouchableOpacity onPress={() => setLines(p => p.filter((_, j) => j !== i))} style={sm.removeBtn}>
                <Text style={sm.removeText}>✕</Text>
              </TouchableOpacity>
            </View>
          ))}
          {lines.length > 0 && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderTopWidth: 1, borderTopColor: C.border, marginBottom: 8 }}>
              <Text style={{ fontSize: 13, fontWeight: '700', color: C.textMuted }}>Est. Total</Text>
              <Text style={{ fontSize: 15, fontWeight: '800', color: C.primary }}>{total.toLocaleString()} Ks</Text>
            </View>
          )}
          <TouchableOpacity style={sm.addLineBtn} onPress={() => setShowAdd(true)}>
            <Text style={sm.addLineBtnText}>+ Add Service Line</Text>
          </TouchableOpacity>
          {showAdd && (
            <View style={sm.addBox}>
              <Text style={sm.label}>Service Type</Text>
              <TouchableOpacity style={sm.picker} onPress={() => setSvcPicker(true)}>
                <Text style={[sm.pickerVal, !selSvc && { color: C.textMuted }]}>{selSvc?.item ?? 'Select service...'}</Text>
                <Text style={{ color: C.textMuted, fontSize: 18 }}>›</Text>
              </TouchableOpacity>
              {selSvc?.price != null && <Text style={{ fontSize: 11, color: C.textMuted, marginTop: 4 }}>Default: {Number(selSvc.price).toLocaleString()} Ks</Text>}
              <Text style={sm.label}>Description (optional)</Text>
              <TextInput style={sm.input} value={addDesc} onChangeText={setAddDesc} placeholder="Custom description..." placeholderTextColor={C.textMuted} />
              <Text style={sm.label}>Cost (Ks)</Text>
              <TextInput style={sm.input} value={addCost} onChangeText={setAddCost} keyboardType="numeric" placeholder={selSvc?.price != null ? String(selSvc.price) : '0'} placeholderTextColor={C.textMuted} />
              <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
                <TouchableOpacity style={[sm.submitBtn, { flex: 1, backgroundColor: C.textMuted, marginTop: 0 }]} onPress={() => { setShowAdd(false); setSelSvc(null); setAddDesc(''); setAddCost(''); }}>
                  <Text style={sm.submitBtnText}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity style={[sm.submitBtn, { flex: 1, marginTop: 0 }]} onPress={confirmAdd}>
                  <Text style={sm.submitBtnText}>Add</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
          <TouchableOpacity style={[sm.submitBtn, { marginTop: 16 }, saving && { opacity: 0.6 }]} onPress={save} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={sm.submitBtnText}>💾  Save Changes</Text>}
          </TouchableOpacity>
        </ScrollView>
        <Modal visible={showSvcPicker} animationType="slide" onRequestClose={() => setSvcPicker(false)}>
          <View style={{ flex: 1, backgroundColor: C.bg }}>
            <View style={sm.header}>
              <Text style={sm.title}>Select Service</Text>
              <TouchableOpacity onPress={() => setSvcPicker(false)}><Text style={sm.close}>✕</Text></TouchableOpacity>
            </View>
            <TextInput style={sm.searchInput} placeholder="Search..." value={svcSearch} onChangeText={setSvcSearch} placeholderTextColor={C.textMuted} />
            <FlatList data={filteredSvcs} keyExtractor={i => String(i.id)} renderItem={({ item }) => (
              <TouchableOpacity style={sm.pmItem} onPress={() => { setSelSvc(item); if (item.price != null) setAddCost(String(item.price)); setSvcPicker(false); setSvcSearch(''); }}>
                <Text style={sm.pmText}>{item.item}</Text>
                {item.serviceTypeName ? <Text style={{ fontSize: 11, color: C.textMuted, marginTop: 2 }}>{item.serviceTypeName}</Text> : null}
              </TouchableOpacity>
            )} />
          </View>
        </Modal>
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ── Edit Parts Modal ───────────────────────────────────────────────────────────
interface PartEntry extends ServiceJobPartDTO { _productName: string; _hasSerial: boolean; }

function EditPartsModal({ visible, job, onClose, onSaved }: {
  visible: boolean; job: ServiceJobDTO; onClose: () => void; onSaved: (j: ServiceJobDTO) => void;
}) {
  const [parts,       setParts]       = useState<PartEntry[]>([]);
  const [products,    setProducts]    = useState<ProductDTO[]>([]);
  const [saving,      setSaving]      = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [selProduct,  setSelProduct]  = useState<ProductDTO | null>(null);
  const [addQty,      setAddQty]      = useState('1');
  const [addSerials,  setAddSerials]  = useState<string[]>([]);
  const [showProdPicker, setProdPicker] = useState(false);
  const [prodSearch,   setProdSearch]  = useState('');
  const [prodLoading,  setProdLoading] = useState(false);
  const [showScanner,  setShowScanner] = useState(false);
  const [scanMode,     setScanMode]    = useState<'product' | 'serial'>('product');
  const [availSerials, setAvailSerials] = useState<ProductSerialDTO[]>([]);
  const [serialsLoading, setSerialsLoading] = useState(false);
  const [manualSerial,   setManualSerial]   = useState('');

  useEffect(() => {
    if (visible) {
      setParts((job.productParts ?? []).map(p => ({
        ...p,
        _productName: p.productName ?? '',
        _hasSerial: (p.serialNumbers ?? []).length > 0 || false,
      })));
      api.get<ApiResponse<ProductDTO[]>>('/products').then(r => setProducts(r.data ?? [])).catch(() => {});
    }
  }, [visible]);

  const filteredProds = products.filter(p =>
    p.name.toLowerCase().includes(prodSearch.toLowerCase()) ||
    p.productCode.toLowerCase().includes(prodSearch.toLowerCase())
  );

  const loadAvailSerials = async (product: ProductDTO) => {
    if (!product.hasSerial) { setAvailSerials([]); return; }
    setSerialsLoading(true);
    try {
      const res = await api.get<ApiResponse<ProductSerialDTO[]>>(`/product-serials/by-product/${product.id}`);
      setAvailSerials((res.data ?? []).filter(s => s.status === 'Available'));
    } catch { setAvailSerials([]); }
    setSerialsLoading(false);
  };

  const selectAvailSerial = (sn: string) => {
    if (addSerials.includes(sn)) { Alert.alert('Duplicate', 'Serial already added'); return; }
    setAddSerials(prev => [...prev, sn]);
  };

  const addManualSerial = () => {
    const sn = manualSerial.trim();
    if (!sn) return;
    if (addSerials.includes(sn)) { Alert.alert('Duplicate', 'Serial already added'); setManualSerial(''); return; }
    setAddSerials(prev => [...prev, sn]);
    setManualSerial('');
  };

  const resolveProductBySerial = async (code: string): Promise<{ product: ProductDTO; serial: string } | null> => {
    try {
      const res = await api.get<ApiResponse<ProductSerialDTO>>(`/product-serials/by-serial/${encodeURIComponent(code)}`);
      if (res.data?.productId) {
        let prod = products.find(p => p.id === res.data.productId);
        if (!prod) {
          const pRes = await api.get<ApiResponse<ProductDTO>>(`/products/${res.data.productId}`);
          prod = pRes.data;
          if (prod) setProducts(prev => [...prev.filter(x => x.id !== prod!.id), prod!]);
        }
        if (prod) return { product: prod, serial: code };
      }
    } catch {}
    return null;
  };

  const handleScan = async (code: string) => {
    setShowScanner(false);
    if (scanMode === 'serial') {
      if (addSerials.includes(code)) { Alert.alert('Duplicate', 'Serial already added'); return; }
      setAddSerials(prev => [...prev, code]);
      return;
    }
    setProdLoading(true);
    const byCode = products.find(p => p.productCode.toUpperCase() === code.toUpperCase());
    if (byCode) {
      setSelProduct(byCode); setAddQty('1'); setAddSerials([]); setManualSerial('');
      setShowAddForm(true); setProdPicker(false); setProdLoading(false);
      loadAvailSerials(byCode);
      return;
    }
    const found = await resolveProductBySerial(code);
    if (found) {
      setSelProduct(found.product); setAddQty('1'); setManualSerial('');
      setAddSerials(found.product.hasSerial ? [found.serial] : []);
      setShowAddForm(true); setProdPicker(false);
      loadAvailSerials(found.product);
    } else {
      setProdSearch(code); setProdPicker(true);
    }
    setProdLoading(false);
  };

  const confirmAdd = () => {
    if (!selProduct) return;
    const qty = Math.max(1, parseInt(addQty) || 1);
    if (selProduct.hasSerial && addSerials.length !== qty) {
      Alert.alert('Serial Required', `Qty ${qty} — ${addSerials.length} serial(s) scanned. ${qty - addSerials.length} more needed.`);
      return;
    }
    setParts(prev => [...prev, {
      productId: selProduct.id,
      productName: selProduct.name,
      productCode: selProduct.productCode,
      qty, unitPrice: selProduct.sellingPrice,
      subtotal: selProduct.sellingPrice * qty,
      serialNumbers: addSerials,
      _productName: selProduct.name,
      _hasSerial: !!selProduct.hasSerial,
    }]);
    setSelProduct(null); setAddQty('1'); setAddSerials([]); setManualSerial('');
    setAvailSerials([]); setShowAddForm(false);
  };

  const save = async () => {
    setSaving(true);
    try {
      const body = {
        ...job,
        productParts: parts.map(p => ({
          id: p.id, productId: p.productId,
          qty: p.qty, unitPrice: p.unitPrice,
          serialNumbers: p.serialNumbers ?? [],
        })),
      };
      const res = await api.put<ApiResponse<ServiceJobDTO>>(`/service-jobs/${job.id}`, body);
      onSaved(res.data);
    } catch (e: any) { Alert.alert('Error', e?.message ?? 'Save failed'); }
    setSaving(false);
  };

  const partsTotal = parts.reduce((s, p) => s + (p.unitPrice * p.qty), 0);

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={sm.header}>
          <Text style={sm.title}>Inventory Parts Used</Text>
          <TouchableOpacity onPress={onClose}><Text style={sm.close}>✕</Text></TouchableOpacity>
        </View>

        <ScrollView contentContainerStyle={{ padding: 16 }}>
          {parts.length === 0 && !showAddForm && (
            <Text style={{ textAlign: 'center', color: C.textMuted, paddingVertical: 16, fontSize: 13 }}>No parts added</Text>
          )}
          {parts.map((p, i) => (
            <View key={i} style={sm.lineRow}>
              <View style={{ flex: 1 }}>
                <Text style={sm.lineName}>{p._productName}</Text>
                <Text style={sm.lineSub}>Qty: {p.qty}  ·  {p.unitPrice.toLocaleString()} Ks each</Text>
                {(p.serialNumbers ?? []).length > 0 && (
                  <Text style={[sm.lineSub, { color: C.primary }]}>S/N: {p.serialNumbers.join(', ')}</Text>
                )}
              </View>
              <Text style={sm.lineCost}>{(p.unitPrice * p.qty).toLocaleString()} Ks</Text>
              <TouchableOpacity onPress={() => setParts(prev => prev.filter((_, j) => j !== i))} style={sm.removeBtn}>
                <Text style={sm.removeText}>✕</Text>
              </TouchableOpacity>
            </View>
          ))}
          {parts.length > 0 && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderTopWidth: 1, borderTopColor: C.border, marginBottom: 8 }}>
              <Text style={{ fontSize: 13, fontWeight: '700', color: C.textMuted }}>Parts Total</Text>
              <Text style={{ fontSize: 15, fontWeight: '800', color: C.primary }}>{partsTotal.toLocaleString()} Ks</Text>
            </View>
          )}
          {!showAddForm && (
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
              <TouchableOpacity style={[sm.addLineBtn, { flex: 1 }]} onPress={() => { setScanMode('product'); setProdPicker(true); }}>
                <Text style={sm.addLineBtnText}>+ Select Product</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[sm.addLineBtn, { flex: 1, backgroundColor: C.violetBg }]} onPress={() => { setScanMode('product'); setShowScanner(true); }}>
                {prodLoading
                  ? <ActivityIndicator color={C.violet} size="small" />
                  : <Text style={[sm.addLineBtnText, { color: C.violet }]}>📷 Scan</Text>
                }
              </TouchableOpacity>
            </View>
          )}
          {showAddForm && selProduct && (
            <View style={sm.addBox}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 }}>
                <Text style={[sm.lineName, { flex: 1 }]}>{selProduct.name}</Text>
                <TouchableOpacity onPress={() => { setSelProduct(null); setAddQty('1'); setAddSerials([]); setManualSerial(''); setAvailSerials([]); setShowAddForm(false); }}>
                  <Text style={{ color: C.danger, fontWeight: '700' }}>Cancel</Text>
                </TouchableOpacity>
              </View>
              <Text style={sm.lineSub}>{selProduct.productCode}  ·  {selProduct.sellingPrice.toLocaleString()} Ks</Text>
              <Text style={sm.label}>Quantity</Text>
              <TextInput style={sm.input} value={addQty} onChangeText={setAddQty} keyboardType="numeric" placeholderTextColor={C.textMuted} />
              {selProduct.hasSerial && (
                <>
                  <Text style={sm.label}>Serial Numbers ({addSerials.length} / {addQty || 1})</Text>
                  {addSerials.map((sn, i) => (
                    <View key={i} style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <View style={{ flex: 1, backgroundColor: C.successBg, borderRadius: 6, paddingHorizontal: 10, paddingVertical: 6 }}>
                        <Text style={{ fontSize: 12, fontWeight: '700', color: C.success }}>{sn}</Text>
                      </View>
                      <TouchableOpacity onPress={() => setAddSerials(p => p.filter((_, j) => j !== i))} style={sm.removeBtn}>
                        <Text style={sm.removeText}>✕</Text>
                      </TouchableOpacity>
                    </View>
                  ))}
                  {/* Manual entry */}
                  <View style={{ flexDirection: 'row', gap: 8, marginTop: 6 }}>
                    <TextInput
                      style={[sm.input, { flex: 1, margin: 0 }]}
                      placeholder="Type serial number..."
                      placeholderTextColor={C.textMuted}
                      value={manualSerial}
                      onChangeText={setManualSerial}
                      onSubmitEditing={addManualSerial}
                      returnKeyType="done"
                    />
                    <TouchableOpacity style={[sm.addLineBtn, { margin: 0, paddingHorizontal: 14 }]} onPress={addManualSerial}>
                      <Text style={sm.addLineBtnText}>+ Add</Text>
                    </TouchableOpacity>
                  </View>
                  {/* Available serials from inventory */}
                  {serialsLoading && <ActivityIndicator color={C.primary} style={{ marginTop: 8 }} />}
                  {!serialsLoading && availSerials.length > 0 && (
                    <View style={{ marginTop: 8 }}>
                      <Text style={[sm.label, { marginBottom: 4 }]}>Available in stock (tap to select)</Text>
                      {availSerials.filter(s => !addSerials.includes(s.serialNumber)).map(s => (
                        <TouchableOpacity key={s.id} onPress={() => selectAvailSerial(s.serialNumber)}
                          style={{ backgroundColor: C.primaryLight, borderRadius: 6, paddingHorizontal: 10, paddingVertical: 7, marginBottom: 4 }}>
                          <Text style={{ fontSize: 12, fontWeight: '700', color: C.primary }}>{s.serialNumber}</Text>
                        </TouchableOpacity>
                      ))}
                    </View>
                  )}
                  <TouchableOpacity style={[sm.addLineBtn, { backgroundColor: C.violetBg, marginTop: 8 }]}
                    onPress={() => { setScanMode('serial'); setShowScanner(true); }}>
                    <Text style={[sm.addLineBtnText, { color: C.violet }]}>📷 Scan Serial Number</Text>
                  </TouchableOpacity>
                </>
              )}
              <TouchableOpacity style={[sm.submitBtn, { marginTop: 14 }]} onPress={confirmAdd}>
                <Text style={sm.submitBtnText}>+ Add to List</Text>
              </TouchableOpacity>
            </View>
          )}
          <TouchableOpacity style={[sm.submitBtn, { marginTop: 16 }, saving && { opacity: 0.6 }]} onPress={save} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={sm.submitBtnText}>💾  Save Parts</Text>}
          </TouchableOpacity>
        </ScrollView>

        <Modal visible={showProdPicker} animationType="slide" onRequestClose={() => setProdPicker(false)}>
          <View style={{ flex: 1, backgroundColor: C.bg }}>
            <View style={sm.header}>
              <Text style={sm.title}>Select Product</Text>
              <TouchableOpacity onPress={() => setProdPicker(false)}><Text style={sm.close}>✕</Text></TouchableOpacity>
            </View>
            <View style={{ flexDirection: 'row', gap: 8, margin: 12 }}>
              <TextInput style={[sm.searchInput, { flex: 1, margin: 0 }]} placeholder="Search name / code..." value={prodSearch} onChangeText={setProdSearch} placeholderTextColor={C.textMuted} />
              <TouchableOpacity style={[sm.addLineBtn, { margin: 0, paddingHorizontal: 14, backgroundColor: C.violetBg }]}
                onPress={() => { setProdPicker(false); setScanMode('product'); setShowScanner(true); }}>
                <Text style={[sm.addLineBtnText, { color: C.violet }]}>📷</Text>
              </TouchableOpacity>
            </View>
            <FlatList data={filteredProds} keyExtractor={p => String(p.id)} renderItem={({ item }) => (
              <TouchableOpacity style={sm.pmItem} onPress={() => {
                setSelProduct(item); setAddQty('1'); setAddSerials([]); setManualSerial('');
                setShowAddForm(true); setProdPicker(false); setProdSearch('');
                loadAvailSerials(item);
              }}>
                <Text style={sm.pmText}>{item.name}</Text>
                <Text style={{ fontSize: 11, color: C.textMuted, marginTop: 2 }}>
                  {item.productCode}  ·  {item.sellingPrice.toLocaleString()} Ks
                  {item.hasSerial ? '  · Serial tracked' : ''}
                </Text>
              </TouchableOpacity>
            )} />
          </View>
        </Modal>

        <ScannerModal
          visible={showScanner}
          onDetected={handleScan}
          onClose={() => setShowScanner(false)}
          title={scanMode === 'serial' ? 'Scan Serial Number' : 'Scan Product / Serial'}
        />
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ── Voucher HTML builder ───────────────────────────────────────────────────────
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
    ${job.partRequests ? `<div class="section-label">Possible Parts / Actions</div><div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:7px;padding:10px 12px;font-size:11px;color:#9a3412;white-space:pre-wrap">${esc(formatPartRequests(job.partRequests))}</div>` : ''}
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

const sm = StyleSheet.create({
  header:       { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.primary, paddingHorizontal: 16, paddingVertical: 14, paddingTop: 48 },
  title:        { color: '#fff', fontWeight: '700', fontSize: 15, flex: 1, marginRight: 8 },
  close:        { color: '#fff', fontSize: 18 },
  label:        { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', marginTop: 14, marginBottom: 5 },
  input:        { backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, color: C.text },
  focBtn:       { backgroundColor: C.primaryLight, borderRadius: 10, paddingVertical: 13, alignItems: 'center', marginBottom: 4 },
  focText:      { fontSize: 14, fontWeight: '700', color: C.primary },
  netRow:       { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.border, marginVertical: 4 },
  netLabel:     { fontSize: 13, fontWeight: '600', color: C.textMuted },
  netVal:       { fontSize: 16, fontWeight: '800', color: C.primary },
  picker:       { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 11 },
  pickerVal:    { fontSize: 14, fontWeight: '600', color: C.text },
  submitBtn:    { backgroundColor: C.success, borderRadius: 12, paddingVertical: 14, alignItems: 'center', marginTop: 24 },
  submitBtnText:{ color: '#fff', fontWeight: '800', fontSize: 15 },
  pmItem:       { paddingHorizontal: 16, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border },
  pmText:       { fontSize: 14, fontWeight: '600', color: C.text },
  lineRow:      { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.border, gap: 8 },
  lineName:     { fontSize: 13, fontWeight: '700', color: C.text },
  lineSub:      { fontSize: 11, color: C.textMuted, marginTop: 1 },
  lineCost:     { fontSize: 13, fontWeight: '700', color: C.primary },
  removeBtn:    { width: 28, height: 28, borderRadius: 6, backgroundColor: C.dangerBg, justifyContent: 'center', alignItems: 'center' },
  removeText:   { fontSize: 12, fontWeight: '700', color: C.danger },
  addLineBtn:   { backgroundColor: C.primaryLight, borderRadius: 10, paddingVertical: 12, alignItems: 'center', marginBottom: 8 },
  addLineBtnText:{ color: C.primary, fontWeight: '700', fontSize: 14 },
  addBox:       { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 8 },
  searchInput:  { margin: 12, backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
});

// ── Main Screen ───────────────────────────────────────────────────────────────
export default function ServiceJobDetailScreen({ route, navigation }: any) {
  const { jobId } = route.params;
  const { hasPermission } = useAuth();
  const [job,              setJob]             = useState<ServiceJobDTO | null>(null);
  const [loading,          setLoading]         = useState(true);
  const [updating,         setUpdating]        = useState(false);
  const [printing,         setPrinting]        = useState(false);
  const [showSettle,       setSettle]          = useState(false);
  const [showEditJob,      setEditJob]         = useState(false);
  const [showEditLines,    setEditLines]       = useState(false);
  const [showEditParts,    setEditParts]       = useState(false);
  const [serialWarrantyMap, setSerialWarrantyMap] = useState<SnWarrantyMap>({});

  const loadSerialWarranties = (j: ServiceJobDTO) => {
    const allSerials = (j.productParts ?? []).flatMap(p => p.serialNumbers ?? []);
    if (allSerials.length === 0) return;
    api.post<ApiResponse<ProductSerialDTO[]>>('/product-serials/by-serials', allSerials)
      .then(sr => {
        const m: SnWarrantyMap = {};
        (sr.data ?? []).forEach(s => { m[s.serialNumber] = s; });
        setSerialWarrantyMap(m);
      })
      .catch(() => {});
  };

  useEffect(() => {
    api.get<ApiResponse<ServiceJobDTO>>(`/service-jobs/${jobId}`)
      .then(r => { setJob(r.data); loadSerialWarranties(r.data); })
      .catch(() => Alert.alert('Error', 'Cannot load job'))
      .finally(() => setLoading(false));
  }, [jobId]);

  const updateStatus = async (status: string) => {
    if (!job) return;
    setUpdating(true);
    try {
      const res = await api.patch<ApiResponse<ServiceJobDTO>>(
        `/service-jobs/${job.id}/status?status=${encodeURIComponent(status)}`
      );
      setJob(res.data);
    } catch (e: any) { Alert.alert('Error', e?.message ?? 'Status update failed'); }
    setUpdating(false);
  };

  const handleDelete = () => {
    Alert.alert(
      'Delete Job',
      `Delete ${job?.jobNo ?? `#${job?.id}`}? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: async () => {
          try {
            await api.delete(`/service-jobs/${job!.id}`);
            navigation.goBack();
          } catch (e: any) { Alert.alert('Error', e?.message ?? 'Delete failed'); }
        }},
      ]
    );
  };

  const handlePrint = async () => {
    if (!job) return;
    setPrinting(true);
    try {
      const html = buildVoucherHtml(job, serialWarrantyMap);
      const { uri } = await Print.printToFileAsync({ html });
      await Sharing.shareAsync(uri, { mimeType: 'application/pdf', dialogTitle: 'Print / Save PDF' });
    } catch (e: any) {
      if (!String(e?.message ?? '').includes('cancel')) {
        Alert.alert('Print Error', e?.message ?? 'Failed to print');
      }
    }
    setPrinting(false);
  };

  const handleRework = () => {
    Alert.alert('Create Rework', 'Rework type ရွေးပါ', [
      { text: 'WARRANTY (ကုန်ကျစရိတ်မရှိ)', onPress: async () => {
        try {
          const res = await api.post<ApiResponse<ServiceJobDTO>>(`/service-jobs/${jobId}/rework`, { reworkType: 'WARRANTY', problemDesc: job?.problemDesc });
          navigation.replace('ServiceJobDetail', { jobId: res.data.id });
        } catch (e: any) { Alert.alert('Error', e?.message ?? 'Failed'); }
      }},
      { text: 'ADDITIONAL (ကုန်ကျစရိတ်ပေးရ)', onPress: async () => {
        try {
          const res = await api.post<ApiResponse<ServiceJobDTO>>(`/service-jobs/${jobId}/rework`, { reworkType: 'ADDITIONAL', problemDesc: job?.problemDesc });
          navigation.replace('ServiceJobDetail', { jobId: res.data.id });
        } catch (e: any) { Alert.alert('Error', e?.message ?? 'Failed'); }
      }},
      { text: 'Cancel', style: 'cancel' },
    ]);
  };

  if (loading) return <ActivityIndicator color={C.primary} style={{ marginTop: 80 }} size="large" />;
  if (!job) return <Text style={{ textAlign: 'center', marginTop: 40, color: C.textMuted }}>Not found</Text>;

  const col = STATUS_COL[job.status ?? 'RECEIVED'] ?? STATUS_COL.RECEIVED;
  const currency = (v?: number | null) => v != null ? `${Number(v).toLocaleString()} Ks` : null;
  const canSettle  = hasPermission('CAN_ACCESS_SERVICE_JOB_SETTLE') && job.status === 'COMPLETED' && !job.paymentStatus;
  const canDeliver = hasPermission('CAN_ACCESS_SERVICE_JOB_UPDATE') && job.status === 'COMPLETED' && !!job.paymentStatus;
  const canRework  = hasPermission('CAN_ACCESS_SERVICE_JOB_REWORK') && job.status === 'DELIVERED';
  const canEdit    = hasPermission('CAN_ACCESS_SERVICE_JOB_UPDATE') && job.status !== 'DELIVERED' && job.status !== 'CANCELLED';
  const canDelete = hasPermission('CAN_ACCESS_SERVICE_JOB_DELETE');

  return (
    <View style={{ flex: 1, backgroundColor: C.bg }}>
      <ScrollView contentContainerStyle={{ padding: 14, paddingBottom: 40 }}>

        {/* Header */}
        <View style={st.section}>
          <View style={st.headerRow}>
            <Text style={st.code}>{job.jobNo ?? `#${job.id}`}</Text>
            <View style={[st.badge, { backgroundColor: col.bg }]}>
              <Text style={[st.badgeText, { color: col.text }]}>{(job.status ?? '').replace(/_/g, ' ')}</Text>
            </View>
          </View>
          {job.rework && <Text style={st.reworkTag}>🔁 REWORK {job.parentJobNo ? `(from ${job.parentJobNo})` : ''}</Text>}
          <Row label="Customer"  value={job.customerName} />
          <Row label="Staff"     value={job.assignedStaffName} />
          <Row label="Booking"   value={job.bookingNo} />
          <Row label="Payment"   value={job.paymentMethodName} />
          <Row label="Received"  value={job.receivedDate} />
          <Row label="Est. Done" value={job.estimatedCompletion} />
          <Row label="Completed" value={job.completedDate} />
          <Row label="Delivered" value={job.deliveredDate} />

          {/* Action buttons row */}
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
            {canEdit && (
              <TouchableOpacity style={st.actionBtn} onPress={() => setEditJob(true)}>
                <Text style={st.actionBtnText}>✏️ Edit Job</Text>
              </TouchableOpacity>
            )}
            <TouchableOpacity style={[st.actionBtn, { backgroundColor: C.violetBg }]} onPress={handlePrint} disabled={printing}>
              {printing
                ? <ActivityIndicator color={C.violet} size="small" />
                : <Text style={[st.actionBtnText, { color: C.violet }]}>🖨️ Print</Text>
              }
            </TouchableOpacity>
            {canDelete && (
              <TouchableOpacity style={[st.actionBtn, { backgroundColor: C.dangerBg }]} onPress={handleDelete}>
                <Text style={[st.actionBtnText, { color: C.danger }]}>🗑️ Delete</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* Item */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>ITEM</Text>
          <Row label="Item"      value={job.itemName} />
          <Row label="Condition" value={job.itemCondition} />
          <Row label="Part Requests" value={formatPartRequests(job.partRequests)} />
          <Row label="Problem"   value={job.problemDesc} />
          <Row label="Diagnosis" value={job.diagnosisNotes} />
          <Row label="Remark"    value={job.remark} />
        </View>

        {/* Cost */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>COST</Text>
          <Row label="Estimated"  value={currency(job.estimatedCost)} />
          <Row label="Final Cost" value={currency(job.finalCost)} />
          <Row label="Discount"   value={currency(job.discountAmount)} />
          <Row label="Net Amount" value={currency(job.netAmount)} />
          <Row label="Paid"       value={currency(job.paidAmount)} />
          {(job.dueAmount ?? 0) > 0 && (
            <View style={st.row}>
              <Text style={st.rowKey}>Due</Text>
              <Text style={[st.rowVal, { color: C.danger, fontWeight: '800' }]}>{currency(job.dueAmount)}</Text>
            </View>
          )}
          {job.foc && <Text style={st.foc}>★ FREE OF CHARGE</Text>}
          <Row label="Pay Status" value={job.paymentStatus} />
        </View>

        {/* Service Lines */}
        <View style={st.section}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Text style={st.sectionTitle}>SERVICE LINES</Text>
            {canEdit && (
              <TouchableOpacity style={st.editBtn} onPress={() => setEditLines(true)}>
                <Text style={st.editBtnText}>✏️ Edit</Text>
              </TouchableOpacity>
            )}
          </View>
          {(job.lines ?? []).length === 0
            ? <Text style={st.emptyText}>No service lines</Text>
            : job.lines!.map((l, i) => (
              <View key={i} style={st.lineRow}>
                <View style={{ flex: 1 }}>
                  <Text style={st.lineName}>{l.serviceItemName ?? l.subServiceTypeName ?? l.serviceTypeName ?? l.description ?? '-'}</Text>
                  {l.description && <Text style={st.lineSub}>{l.description}</Text>}
                  {(l.warrantyMonths ?? 0) > 0 && (
                    <Text style={st.lineWarranty}>🛡 {fmtSerialWarranty(l.warrantyMonths)} အာမခံ</Text>
                  )}
                </View>
                {(l.price ?? l.cost) ? <Text style={st.lineCost}>{Number(l.price ?? l.cost).toLocaleString()} Ks</Text> : null}
              </View>
            ))
          }
        </View>

        {/* Inventory Parts Used */}
        <View style={st.section}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Text style={st.sectionTitle}>INVENTORY PARTS USED</Text>
            {canEdit && (
              <TouchableOpacity style={[st.editBtn, { backgroundColor: C.violetBg }]} onPress={() => setEditParts(true)}>
                <Text style={[st.editBtnText, { color: C.violet }]}>✏️ Edit</Text>
              </TouchableOpacity>
            )}
          </View>
          {(job.productParts ?? []).length === 0
            ? <Text style={st.emptyText}>No parts used</Text>
            : job.productParts!.map((p, i) => (
              <View key={i} style={st.lineRow}>
                <View style={{ flex: 1 }}>
                  <Text style={st.lineName}>{p.productName ?? '-'}</Text>
                  <Text style={st.lineSub}>Qty: {p.qty}  ·  {Number(p.unitPrice).toLocaleString()} Ks each</Text>
                  {(p.serialNumbers ?? []).map(sn => {
                    const info = serialWarrantyMap[sn];
                    const wLabel = info ? fmtSerialWarranty(info.warrantyMonths) : '';
                    return (
                      <View key={sn} style={{ marginTop: 1 }}>
                        <Text style={[st.lineSub, { color: C.primary }]}>S/N: {sn}</Text>
                        {wLabel ? <Text style={[st.lineSub, { color: '#0891B2' }]}>🛡 {wLabel}</Text> : null}
                      </View>
                    );
                  })}
                </View>
                <Text style={st.lineCost}>{(Number(p.unitPrice) * p.qty).toLocaleString()} Ks</Text>
              </View>
            ))
          }
        </View>

        {/* Status update */}
        {canEdit && (
          <View style={st.section}>
            <Text style={st.sectionTitle}>UPDATE STATUS</Text>
            {updating && <ActivityIndicator color={C.primary} style={{ marginBottom: 8 }} />}
            <View style={st.statusGrid}>
              {STATUSES.filter(s => s !== 'DELIVERED' && s !== 'CANCELLED').map(s => {
                const c = STATUS_COL[s] ?? STATUS_COL.RECEIVED;
                const active = job.status === s;
                return (
                  <TouchableOpacity key={s} style={[st.statusBtn, { backgroundColor: active ? c.text : c.bg }]}
                    onPress={() => !active && !updating && updateStatus(s)} disabled={active || updating}>
                    <Text style={[st.statusBtnText, { color: active ? '#fff' : c.text }]}>{s.replace(/_/g, ' ')}</Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>
        )}

        {/* Cancel button (only if not already delivered/cancelled) */}
        {canEdit && job.status !== 'CANCELLED' && (
          <TouchableOpacity
            style={[st.reworkBtn, { backgroundColor: C.dangerBg }]}
            onPress={() => Alert.alert('Cancel Job', 'Mark this job as CANCELLED?', [
              { text: 'No', style: 'cancel' },
              { text: 'Yes, Cancel', style: 'destructive', onPress: () => updateStatus('CANCELLED') },
            ])}
          >
            <Text style={[st.reworkBtnText, { color: C.danger }]}>✕  Cancel Job</Text>
          </TouchableOpacity>
        )}

        {canSettle && (
          <TouchableOpacity style={st.settleBtn} onPress={() => setSettle(true)}>
            <Text style={st.settleBtnText}>💰  Settle</Text>
          </TouchableOpacity>
        )}
        {canDeliver && (
          <TouchableOpacity
            style={[st.settleBtn, { backgroundColor: C.primary }]}
            onPress={async () => {
              try {
                const res = await api.post<ApiResponse<ServiceJobDTO>>(`/service-jobs/${job.id}/deliver`, {});
                setJob(res.data);
                Alert.alert('Done', 'Marked as Delivered');
              } catch (e: any) { Alert.alert('Error', e?.message ?? 'Failed'); }
            }}
          >
            <Text style={st.settleBtnText}>📦  Mark as Delivered</Text>
          </TouchableOpacity>
        )}
        {canRework && (
          <TouchableOpacity style={st.reworkBtn} onPress={handleRework}>
            <Text style={st.reworkBtnText}>🔁  Create Rework</Text>
          </TouchableOpacity>
        )}
      </ScrollView>

      {job && <SettleModal visible={showSettle} job={job} onClose={() => setSettle(false)} onSettled={u => { setJob(u); setSettle(false); }} />}
      {job && <EditJobModal visible={showEditJob} job={job} onClose={() => setEditJob(false)} onSaved={u => { setJob(u); setEditJob(false); }} />}
      {job && <EditLinesModal visible={showEditLines} job={job} onClose={() => setEditLines(false)} onSaved={u => { setJob(u); setEditLines(false); }} />}
      {job && <EditPartsModal visible={showEditParts} job={job} onClose={() => setEditParts(false)} onSaved={u => { setJob(u); setEditParts(false); }} />}
    </View>
  );
}

const st = StyleSheet.create({
  section:      { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  sectionTitle: { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 6 },
  headerRow:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  code:         { fontSize: 16, fontWeight: '800', color: C.primary },
  badge:        { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 },
  badgeText:    { fontSize: 11, fontWeight: '800' },
  reworkTag:    { fontSize: 11, color: C.danger, fontWeight: '700', marginBottom: 8 },
  row:          { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 5, borderBottomWidth: 1, borderBottomColor: C.border },
  rowKey:       { fontSize: 12, color: C.textMuted, fontWeight: '600' },
  rowVal:       { fontSize: 12, fontWeight: '700', color: C.text, maxWidth: '60%' as any, textAlign: 'right' },
  foc:          { fontSize: 12, fontWeight: '800', color: C.success, textAlign: 'center', marginTop: 6 },
  actionBtn:    { backgroundColor: C.primaryLight, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },
  actionBtnText:{ color: C.primary, fontWeight: '700', fontSize: 12 },
  editBtn:      { backgroundColor: C.primaryLight, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 7 },
  editBtnText:  { color: C.primary, fontWeight: '700', fontSize: 12 },
  emptyText:    { color: C.textMuted, fontSize: 13, textAlign: 'center', paddingVertical: 8 },
  lineRow:      { flexDirection: 'row', alignItems: 'flex-start', paddingVertical: 7, borderBottomWidth: 1, borderBottomColor: C.border, gap: 8 },
  lineName:     { fontSize: 13, fontWeight: '600', color: C.text },
  lineSub:      { fontSize: 11, color: C.textMuted, marginTop: 1 },
  lineCost:     { fontSize: 13, fontWeight: '700', color: C.primary },
  lineWarranty: { fontSize: 10, color: '#0891B2', fontWeight: '600', marginTop: 2 },
  statusGrid:   { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  statusBtn:    { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },
  statusBtnText:{ fontSize: 11, fontWeight: '700' },
  settleBtn:    { backgroundColor: C.success, borderRadius: 12, paddingVertical: 14, alignItems: 'center', marginBottom: 10 },
  settleBtnText:{ color: '#fff', fontWeight: '800', fontSize: 15 },
  reworkBtn:    { backgroundColor: C.warningBg, borderRadius: 12, paddingVertical: 14, alignItems: 'center', marginBottom: 10 },
  reworkBtnText:{ color: C.warning, fontWeight: '800', fontSize: 15 },
});
