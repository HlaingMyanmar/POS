import React, { useEffect, useState } from 'react';
import {
  View, Text, TextInput, ScrollView, TouchableOpacity, StyleSheet,
  Modal, FlatList, ActivityIndicator, Alert, KeyboardAvoidingView, Platform,
} from 'react-native';
import { api } from '../../api/client';
import { ApiResponse, CustomerDTO, StaffDTO, ServiceItemDTO, ServiceJobDTO, ServiceJobLineDTO } from '../../types';
import { C } from '../../theme';

// ── Picker Modal ──────────────────────────────────────────────────────────────
function PickerModal<T extends { id: number }>({
  visible, title, items, labelKey, subKey, onSelect, onClose, extraHeader,
}: {
  visible: boolean; title: string; items: T[]; labelKey: keyof T; subKey?: keyof T;
  onSelect: (item: T) => void; onClose: () => void; extraHeader?: React.ReactNode;
}) {
  const [q, setQ] = useState('');
  const filtered = items.filter(i => String(i[labelKey]).toLowerCase().includes(q.toLowerCase()));
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: C.bg }}>
        <View style={pm.header}>
          <Text style={pm.title}>{title}</Text>
          <TouchableOpacity onPress={onClose}><Text style={pm.close}>✕</Text></TouchableOpacity>
        </View>
        {extraHeader}
        <TextInput style={pm.search} placeholder="Search..." value={q} onChangeText={setQ} placeholderTextColor={C.textMuted} />
        <FlatList
          data={filtered}
          keyExtractor={i => String(i.id)}
          renderItem={({ item }) => (
            <TouchableOpacity style={pm.item} onPress={() => { onSelect(item); onClose(); setQ(''); }}>
              <Text style={pm.itemText}>{String(item[labelKey])}</Text>
              {subKey && (item as any)[subKey] ? <Text style={pm.itemSub}>{String((item as any)[subKey])}</Text> : null}
            </TouchableOpacity>
          )}
        />
      </View>
    </Modal>
  );
}
const pm = StyleSheet.create({
  header:   { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.primary, paddingHorizontal: 16, paddingVertical: 14, paddingTop: 48 },
  title:    { color: '#fff', fontWeight: '700', fontSize: 16 },
  close:    { color: '#fff', fontSize: 18 },
  search:   { margin: 12, backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
  item:     { paddingHorizontal: 16, paddingVertical: 13, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.card },
  itemText: { fontSize: 14, fontWeight: '600', color: C.text },
  itemSub:  { fontSize: 12, color: C.textMuted, marginTop: 2 },
  newBtn:   { flexDirection: 'row', alignItems: 'center', gap: 8, margin: 12, backgroundColor: C.primaryLight, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 10 },
  newText:  { fontSize: 14, fontWeight: '700', color: C.primary },
});

// ── New Customer Inline Modal ─────────────────────────────────────────────────
function NewCustomerModal({ visible, onClose, onCreated }: { visible: boolean; onClose: () => void; onCreated: (c: CustomerDTO) => void }) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!name.trim()) { Alert.alert('Error', 'Name ထည့်ပါ'); return; }
    setSaving(true);
    try {
      const res = await api.post<ApiResponse<CustomerDTO>>('/customers', { name: name.trim(), phone: phone.trim(), address: address.trim() });
      onCreated(res.data);
      setName(''); setPhone(''); setAddress('');
      onClose();
    } catch (e: any) { Alert.alert('Error', e?.message ?? 'Failed'); }
    setSaving(false);
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={pm.header}><Text style={pm.title}>New Customer</Text><TouchableOpacity onPress={onClose}><Text style={pm.close}>✕</Text></TouchableOpacity></View>
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          {[['Name *', name, setName, false], ['Phone', phone, setPhone, false], ['Address', address, setAddress, false]].map(([label, val, setter]) => (
            <View key={label as string}>
              <Text style={st.label}>{label as string}</Text>
              <TextInput style={st.input} value={val as string} onChangeText={setter as any} placeholderTextColor={C.textMuted} placeholder={label as string} />
            </View>
          ))}
          <TouchableOpacity style={[st.submitBtn, saving && { opacity: 0.6 }]} onPress={submit} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={st.submitBtnText}>Save Customer</Text>}
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ── Service Line editor Modal ─────────────────────────────────────────────────
function ServiceLineModal({
  visible, services, onAdd, onClose,
}: { visible: boolean; services: ServiceItemDTO[]; onAdd: (line: ServiceJobLineDTO & { _name: string }) => void; onClose: () => void }) {
  const [selectedSvc, setSelectedSvc] = useState<ServiceItemDTO | null>(null);
  const [desc, setDesc]   = useState('');
  const [cost, setCost]   = useState('');
  const [showPicker, setPicker] = useState(false);

  const reset = () => { setSelectedSvc(null); setDesc(''); setCost(''); };

  const confirm = () => {
    if (!selectedSvc) { Alert.alert('Error', 'Service ရွေးပါ'); return; }
    onAdd({
      serviceItemId: selectedSvc.id,
      serviceItemName: selectedSvc.item,
      qty: 1,
      price: Number(cost) || selectedSvc.price || 0,
      description: desc.trim() || selectedSvc.item || '',
      _name: selectedSvc.item,
    });
    reset();
    onClose();
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={() => { reset(); onClose(); }}>
      <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={pm.header}>
          <Text style={pm.title}>Add Service Line</Text>
          <TouchableOpacity onPress={() => { reset(); onClose(); }}><Text style={pm.close}>✕</Text></TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          <Text style={st.label}>Service Type</Text>
          <TouchableOpacity style={st.picker} onPress={() => setPicker(true)}>
            <Text style={[st.pickerVal, !selectedSvc && { color: C.textMuted }]}>
              {selectedSvc ? selectedSvc.item : 'Select service...'}
            </Text>
            <Text style={{ color: C.textMuted, fontSize: 18 }}>›</Text>
          </TouchableOpacity>
          {selectedSvc?.price != null && (
            <Text style={st.priceHint}>Default price: {Number(selectedSvc.price).toLocaleString()} Ks</Text>
          )}

          <Text style={st.label}>Description (optional)</Text>
          <TextInput style={[st.input, { height: 70, textAlignVertical: 'top' }]} value={desc} onChangeText={setDesc} multiline placeholder="Custom description..." placeholderTextColor={C.textMuted} />

          <Text style={st.label}>Cost (Ks)</Text>
          <TextInput style={st.input} value={cost} onChangeText={setCost} keyboardType="numeric" placeholder={selectedSvc?.price != null ? String(selectedSvc.price) : '0'} placeholderTextColor={C.textMuted} />

          <TouchableOpacity style={[st.submitBtn, { marginTop: 20 }]} onPress={confirm}>
            <Text style={st.submitBtnText}>Add Line</Text>
          </TouchableOpacity>
        </ScrollView>

        <PickerModal
          visible={showPicker}
          title="Select Service"
          items={services}
          labelKey="item"
          subKey="serviceTypeName"
          onSelect={s => { setSelectedSvc(s); if (s.price != null) setCost(String(s.price)); }}
          onClose={() => setPicker(false)}
        />
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────
export default function NewServiceJobScreen({ route, navigation }: any) {
  const prefillBooking = route.params?.booking;

  const [customers,  setCustomers]  = useState<CustomerDTO[]>([]);
  const [staffList,  setStaffList]  = useState<StaffDTO[]>([]);
  const [services,   setServices]   = useState<ServiceItemDTO[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [customer,   setCustomer]   = useState<CustomerDTO | null>(null);
  const [staff,      setStaff]      = useState<StaffDTO | null>(null);
  const [itemName,   setItemName]   = useState(prefillBooking?.deviceType ?? '');
  const [itemCond,   setItemCond]   = useState('');
  const [problemDesc,setProblemDesc]= useState(prefillBooking?.remark ?? '');
  const [diagNotes,  setDiagNotes]  = useState('');
  const [estCompl,   setEstCompl]   = useState('');
  const [remark,     setRemark]     = useState('');
  const [lines,      setLines]      = useState<(ServiceJobLineDTO & { _name: string })[]>([]);

  const [showCustPicker,  setCustPicker]  = useState(false);
  const [showStaffPicker, setStaffPicker] = useState(false);
  const [showNewCust,     setNewCust]     = useState(false);
  const [showLineMod,     setLineMod]     = useState(false);

  useEffect(() => {
    Promise.all([
      api.get<ApiResponse<CustomerDTO[]>>('/customers'),
      api.get<ApiResponse<StaffDTO[]>>('/staffs/active'),
      api.get<ApiResponse<ServiceItemDTO[]>>('/services/active'),
    ]).then(([c, s, sv]) => {
      setCustomers(c.data ?? []);
      setStaffList(s.data ?? []);
      setServices(sv.data ?? []);
      // Pre-fill from booking if provided
      if (prefillBooking?.customerId) {
        const found = (c.data ?? []).find((x: CustomerDTO) => x.id === prefillBooking.customerId);
        if (found) setCustomer(found);
      }
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const removeLine = (idx: number) => setLines(prev => prev.filter((_, i) => i !== idx));

  const estimatedTotal = lines.reduce((s, l) => s + (Number(l.price ?? l.cost) || 0), 0);

  const handleSubmit = async () => {
    if (!customer) { Alert.alert('Error', 'Customer ရွေးပါ'); return; }
    if (!itemName.trim()) { Alert.alert('Error', 'Item name ထည့်ပါ'); return; }
    setSubmitting(true);
    const body: ServiceJobDTO = {
      customerId:          customer.id,
      assignedStaffId:     staff?.id,
      itemName:            itemName.trim(),
      itemCondition:       itemCond.trim() || undefined,
      problemDesc:         problemDesc.trim() || undefined,
      diagnosisNotes:      diagNotes.trim() || undefined,
      estimatedCompletion: estCompl.trim() ? `${estCompl.trim()}T00:00:00` : undefined,
      remark:              remark.trim() || undefined,
      bookingId:           prefillBooking?.id,
      lines: lines.map(l => ({
        serviceItemId: l.serviceItemId,
        qty: l.qty ?? 1,
      })),
    };
    try {
      const res = await api.post<ApiResponse<ServiceJobDTO>>('/service-jobs', body);
      setSubmitting(false);
      navigation.replace('ServiceJobDetail', { jobId: res.data.id });
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Failed to create service job');
      setSubmitting(false);
    }
  };

  if (loading) return <ActivityIndicator color={C.primary} style={{ marginTop: 80 }} size="large" />;

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView style={st.root} contentContainerStyle={{ padding: 14, paddingBottom: 40 }}>

        {/* Customer & Staff */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>JOB INFO</Text>

          <TouchableOpacity style={st.row} onPress={() => setCustPicker(true)}>
            <Text style={st.rowKey}>Customer *</Text>
            <Text style={[st.rowVal, !customer && { color: C.textMuted }]}>{customer?.name ?? 'ရွေးပါ...'}</Text>
          </TouchableOpacity>
          <TouchableOpacity style={st.row} onPress={() => setStaffPicker(true)}>
            <Text style={st.rowKey}>Assigned Staff</Text>
            <Text style={[st.rowVal, !staff && { color: C.textMuted }]}>{staff?.name ?? 'ရွေးပါ...'}</Text>
          </TouchableOpacity>
        </View>

        {/* Item Details */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>DEVICE / ITEM</Text>
          <Text style={st.label}>Item Name *</Text>
          <TextInput style={st.input} value={itemName} onChangeText={setItemName} placeholder="e.g. iPhone 14, Laptop..." placeholderTextColor={C.textMuted} />
          <Text style={st.label}>Condition</Text>
          <TextInput style={st.input} value={itemCond} onChangeText={setItemCond} placeholder="e.g. Cracked screen, No power..." placeholderTextColor={C.textMuted} />
          <Text style={st.label}>Problem Description</Text>
          <TextInput style={[st.input, { height: 72, textAlignVertical: 'top' }]} value={problemDesc} onChangeText={setProblemDesc} multiline placeholder="Describe the issue..." placeholderTextColor={C.textMuted} />
          <Text style={st.label}>Diagnosis Notes</Text>
          <TextInput style={[st.input, { height: 72, textAlignVertical: 'top' }]} value={diagNotes} onChangeText={setDiagNotes} multiline placeholder="Technician notes..." placeholderTextColor={C.textMuted} />
          <Text style={st.label}>Est. Completion Date</Text>
          <TextInput style={st.input} value={estCompl} onChangeText={setEstCompl} placeholder="YYYY-MM-DD" placeholderTextColor={C.textMuted} />
          <Text style={st.label}>Remark</Text>
          <TextInput style={st.input} value={remark} onChangeText={setRemark} placeholder="Optional..." placeholderTextColor={C.textMuted} />
        </View>

        {/* Service Lines */}
        <View style={st.section}>
          <View style={st.sectionHeaderRow}>
            <Text style={st.sectionTitle}>SERVICE LINES</Text>
            <TouchableOpacity style={st.addBtn} onPress={() => setLineMod(true)}>
              <Text style={st.addBtnText}>+ Add</Text>
            </TouchableOpacity>
          </View>

          {lines.length === 0
            ? <Text style={st.empty}>No service lines added</Text>
            : lines.map((l, i) => (
              <View key={i} style={st.lineRow}>
                <View style={{ flex: 1 }}>
                  <Text style={st.lineName}>{l._name || l.serviceItemName || l.description || '-'}</Text>
                  {l.description && l.description !== l._name && <Text style={st.lineSub}>{l.description}</Text>}
                </View>
                <Text style={st.lineCost}>{(Number(l.price ?? l.cost) || 0).toLocaleString()} Ks</Text>
                <TouchableOpacity onPress={() => removeLine(i)} style={st.lineRemove}>
                  <Text style={st.lineRemoveText}>✕</Text>
                </TouchableOpacity>
              </View>
            ))
          }

          {lines.length > 0 && (
            <View style={st.totalRow}>
              <Text style={st.totalLabel}>Est. Total</Text>
              <Text style={st.totalVal}>{estimatedTotal.toLocaleString()} Ks</Text>
            </View>
          )}
        </View>

        <TouchableOpacity style={[st.submitBtn, submitting && { opacity: 0.6 }]} onPress={handleSubmit} disabled={submitting}>
          {submitting ? <ActivityIndicator color="#fff" /> : <Text style={st.submitBtnText}>💾  Create Service Job</Text>}
        </TouchableOpacity>
      </ScrollView>

      {/* Modals */}
      <PickerModal
        visible={showCustPicker}
        title="Select Customer"
        items={customers}
        labelKey="name"
        subKey="phone"
        onSelect={setCustomer}
        onClose={() => setCustPicker(false)}
        extraHeader={
          <TouchableOpacity style={pm.newBtn} onPress={() => { setCustPicker(false); setNewCust(true); }}>
            <Text style={{ fontSize: 18 }}>➕</Text>
            <Text style={pm.newText}>New Customer</Text>
          </TouchableOpacity>
        }
      />
      <PickerModal
        visible={showStaffPicker}
        title="Select Staff"
        items={staffList}
        labelKey="name"
        onSelect={setStaff}
        onClose={() => setStaffPicker(false)}
      />
      <NewCustomerModal
        visible={showNewCust}
        onClose={() => setNewCust(false)}
        onCreated={c => { setCustomers(prev => [...prev, c]); setCustomer(c); }}
      />
      <ServiceLineModal
        visible={showLineMod}
        services={services}
        onAdd={l => setLines(prev => [...prev, l])}
        onClose={() => setLineMod(false)}
      />
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  root:           { flex: 1, backgroundColor: C.bg },
  section:        { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  sectionTitle:   { fontSize: 11, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 8 },
  sectionHeaderRow:{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  row:            { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 11, borderBottomWidth: 1, borderBottomColor: C.border },
  rowKey:         { fontSize: 13, fontWeight: '600', color: C.textMuted },
  rowVal:         { fontSize: 14, fontWeight: '700', color: C.text },
  label:          { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', marginBottom: 5, marginTop: 12 },
  input:          { backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
  picker:         { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 11 },
  pickerVal:      { fontSize: 14, fontWeight: '600', color: C.text },
  priceHint:      { fontSize: 11, color: C.textMuted, marginTop: 4 },
  addBtn:         { backgroundColor: C.primary, paddingHorizontal: 14, paddingVertical: 7, borderRadius: 8 },
  addBtnText:     { color: '#fff', fontWeight: '700', fontSize: 12 },
  empty:          { textAlign: 'center', color: C.textMuted, paddingVertical: 12, fontSize: 13 },
  lineRow:        { flexDirection: 'row', alignItems: 'center', paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: C.border, gap: 8 },
  lineName:       { fontSize: 13, fontWeight: '700', color: C.text },
  lineSub:        { fontSize: 11, color: C.textMuted, marginTop: 1 },
  lineCost:       { fontSize: 13, fontWeight: '700', color: C.primary },
  lineRemove:     { width: 26, height: 26, borderRadius: 6, backgroundColor: C.dangerBg, justifyContent: 'center', alignItems: 'center' },
  lineRemoveText: { fontSize: 12, fontWeight: '700', color: C.danger },
  totalRow:       { flexDirection: 'row', justifyContent: 'space-between', paddingTop: 10, marginTop: 4 },
  totalLabel:     { fontSize: 13, fontWeight: '700', color: C.textMuted },
  totalVal:       { fontSize: 15, fontWeight: '800', color: C.primary },
  submitBtn:      { backgroundColor: C.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center' },
  submitBtnText:  { color: '#fff', fontWeight: '800', fontSize: 16 },
});
