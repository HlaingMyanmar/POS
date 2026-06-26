import React, { useEffect, useState } from 'react';
import {
  View, Text, TextInput, ScrollView, TouchableOpacity, StyleSheet,
  Modal, FlatList, ActivityIndicator, Alert, KeyboardAvoidingView, Platform,
} from 'react-native';
import { api } from '../../api/client';
import {
  ApiResponse, CustomerDTO, StaffDTO, ServiceItemDTO, BookingDTO, BookingDetailDTO,
} from '../../types';
import { C } from '../../theme';

// ── Generic Picker Modal ──────────────────────────────────────────────────────
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
function NewCustomerModal({ visible, onClose, onCreated }: {
  visible: boolean; onClose: () => void; onCreated: (c: CustomerDTO) => void;
}) {
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
          <Text style={st.label}>Name *</Text>
          <TextInput style={st.input} value={name} onChangeText={setName} placeholder="Full name" placeholderTextColor={C.textMuted} />
          <Text style={st.label}>Phone</Text>
          <TextInput style={st.input} value={phone} onChangeText={setPhone} placeholder="09xxxxxxx" keyboardType="phone-pad" placeholderTextColor={C.textMuted} />
          <Text style={st.label}>Address</Text>
          <TextInput style={st.input} value={address} onChangeText={setAddress} placeholder="Address..." placeholderTextColor={C.textMuted} />
          <TouchableOpacity style={[st.submitBtn, saving && { opacity: 0.6 }]} onPress={submit} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={st.submitBtnText}>Save Customer</Text>}
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>
    </Modal>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────
export default function NewBookingScreen({ navigation }: any) {
  const [customers,  setCustomers]  = useState<CustomerDTO[]>([]);
  const [staffList,  setStaffList]  = useState<StaffDTO[]>([]);
  const [services,   setServices]   = useState<ServiceItemDTO[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Core fields
  const [customer,    setCustomer]    = useState<CustomerDTO | null>(null);
  const [staff,       setStaff]       = useState<StaffDTO | null>(null);
  const [appointDate, setAppointDate] = useState('');
  const [remark,      setRemark]      = useState('');

  // Devices (multiple)
  const [devices, setDevices] = useState<{
    deviceType: string; brand: string; model: string;
    serialNo: string; color: string; accessories: string;
  }[]>([]);

  // Service details
  const [details, setDetails] = useState<(BookingDetailDTO & { _name: string })[]>([]);

  // Pickers
  const [showCustPicker,  setCustPicker]  = useState(false);
  const [showStaffPicker, setStaffPicker] = useState(false);
  const [showSvcPicker,   setSvcPicker]   = useState(false);
  const [showNewCust,     setNewCust]     = useState(false);

  useEffect(() => {
    Promise.all([
      api.get<ApiResponse<CustomerDTO[]>>('/customers'),
      api.get<ApiResponse<StaffDTO[]>>('/staffs/active'),
      api.get<ApiResponse<ServiceItemDTO[]>>('/services/active'),
    ]).then(([c, s, sv]) => {
      setCustomers(c.data ?? []);
      setStaffList(s.data ?? []);
      setServices(sv.data ?? []);
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const addService = (svc: ServiceItemDTO) => {
    setDetails(prev => [...prev, {
      subServiceTypeId: svc.id,
      subServiceTypeName: svc.item,
      serviceTypeId: svc.serviceTypeId,
      serviceTypeName: svc.serviceTypeName,
      estimatedCost: svc.price ?? 0,
      _name: svc.item,
    }]);
  };

  const removeDetail = (idx: number) => setDetails(prev => prev.filter((_, i) => i !== idx));

  const estimatedTotal = details.reduce((s, d) => s + (Number(d.estimatedCost) || 0), 0);

  const handleSubmit = async () => {
    if (!customer) { Alert.alert('Error', 'Customer ရွေးပါ'); return; }
    setSubmitting(true);
    const body: BookingDTO = {
      customerId:      customer.id,
      staffId:         staff?.id,
      appointmentDate: appointDate.trim() ? `${appointDate.trim()}T00:00:00` : undefined,
      remark:          remark.trim() || undefined,
      devices: devices.map(d => ({
        deviceType:   d.deviceType.trim() || undefined,
        brand:        d.brand.trim() || undefined,
        model:        d.model.trim() || undefined,
        serialNumber: d.serialNo.trim() || undefined,
        color:        d.color.trim() || undefined,
        accessories:  d.accessories.trim() || undefined,
      })),
      details: details.map(d => ({
        subServiceTypeId:  d.subServiceTypeId,
        serviceTypeId:     d.serviceTypeId,
        description:       d.description,
        estimatedCost:     d.estimatedCost,
      })),
    };
    try {
      const res = await api.post<ApiResponse<BookingDTO>>('/bookings', body);
      setSubmitting(false);
      navigation.replace('BookingDetail', { bookingId: res.data.id });
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Failed to create booking');
      setSubmitting(false);
    }
  };

  if (loading) return <ActivityIndicator color={C.primary} style={{ marginTop: 80 }} size="large" />;

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView style={st.root} contentContainerStyle={{ padding: 14, paddingBottom: 40 }}>

        {/* Booking Info */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>BOOKING INFO</Text>

          <TouchableOpacity style={st.row} onPress={() => setCustPicker(true)}>
            <Text style={st.rowKey}>Customer *</Text>
            <Text style={[st.rowVal, !customer && { color: C.textMuted }]}>{customer?.name ?? 'ရွေးပါ...'}</Text>
          </TouchableOpacity>
          <TouchableOpacity style={st.row} onPress={() => setStaffPicker(true)}>
            <Text style={st.rowKey}>Staff</Text>
            <Text style={[st.rowVal, !staff && { color: C.textMuted }]}>{staff?.name ?? 'ရွေးပါ...'}</Text>
          </TouchableOpacity>

          <Text style={st.label}>Appointment Date</Text>
          <TextInput style={st.input} value={appointDate} onChangeText={setAppointDate} placeholder="YYYY-MM-DD" placeholderTextColor={C.textMuted} />

          <Text style={st.label}>Remark</Text>
          <TextInput style={[st.input, { height: 60, textAlignVertical: 'top' }]} value={remark} onChangeText={setRemark} multiline placeholder="Notes..." placeholderTextColor={C.textMuted} />
        </View>

        {/* Devices (multiple) */}
        <View style={st.section}>
          <View style={st.sectionHeaderRow}>
            <Text style={st.sectionTitle}>DEVICES</Text>
            <TouchableOpacity style={st.addBtn} onPress={() => setDevices(prev => [...prev, { deviceType: '', brand: '', model: '', serialNo: '', color: '', accessories: '' }])}>
              <Text style={st.addBtnText}>+ Add</Text>
            </TouchableOpacity>
          </View>

          {devices.length === 0 && (
            <Text style={st.empty}>Device မထည့်ရသေး — Add နှိပ်ပါ</Text>
          )}

          {devices.map((dev, idx) => (
            <View key={idx} style={st.deviceCard}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <Text style={st.deviceCardTitle}>Device {idx + 1}</Text>
                <TouchableOpacity onPress={() => setDevices(prev => prev.filter((_, i) => i !== idx))}>
                  <Text style={{ color: C.danger, fontWeight: '700', fontSize: 12 }}>✕ Remove</Text>
                </TouchableOpacity>
              </View>
              <Text style={st.label}>Device Type</Text>
              <TextInput style={st.input} value={dev.deviceType}
                onChangeText={v => setDevices(prev => { const d = [...prev]; d[idx] = { ...d[idx], deviceType: v }; return d; })}
                placeholder="Phone, Laptop, TV..." placeholderTextColor={C.textMuted} />
              <Text style={st.label}>Brand</Text>
              <TextInput style={st.input} value={dev.brand}
                onChangeText={v => setDevices(prev => { const d = [...prev]; d[idx] = { ...d[idx], brand: v }; return d; })}
                placeholder="Samsung, Apple..." placeholderTextColor={C.textMuted} />
              <Text style={st.label}>Model</Text>
              <TextInput style={st.input} value={dev.model}
                onChangeText={v => setDevices(prev => { const d = [...prev]; d[idx] = { ...d[idx], model: v }; return d; })}
                placeholder="Galaxy S23, iPhone 15..." placeholderTextColor={C.textMuted} />
              <Text style={st.label}>Serial / IMEI</Text>
              <TextInput style={st.input} value={dev.serialNo}
                onChangeText={v => setDevices(prev => { const d = [...prev]; d[idx] = { ...d[idx], serialNo: v }; return d; })}
                placeholder="Optional" placeholderTextColor={C.textMuted} />
              <Text style={st.label}>Color</Text>
              <TextInput style={st.input} value={dev.color}
                onChangeText={v => setDevices(prev => { const d = [...prev]; d[idx] = { ...d[idx], color: v }; return d; })}
                placeholder="Black, White..." placeholderTextColor={C.textMuted} />
              <Text style={st.label}>Accessories</Text>
              <TextInput style={[st.input, { height: 60, textAlignVertical: 'top' }]} value={dev.accessories}
                onChangeText={v => setDevices(prev => { const d = [...prev]; d[idx] = { ...d[idx], accessories: v }; return d; })}
                multiline placeholder="Charger, case..." placeholderTextColor={C.textMuted} />
            </View>
          ))}
        </View>

        {/* Service Lines */}
        <View style={st.section}>
          <View style={st.sectionHeaderRow}>
            <Text style={st.sectionTitle}>SERVICES</Text>
            <TouchableOpacity style={st.addBtn} onPress={() => setSvcPicker(true)}>
              <Text style={st.addBtnText}>+ Add</Text>
            </TouchableOpacity>
          </View>

          {details.length === 0
            ? <Text style={st.empty}>No services added</Text>
            : details.map((d, i) => (
              <View key={i} style={st.lineRow}>
                <View style={{ flex: 1 }}>
                  <Text style={st.lineName}>{d._name ?? d.subServiceTypeName ?? '-'}</Text>
                </View>
                {d.estimatedCost ? (
                  <Text style={st.lineCost}>{Number(d.estimatedCost).toLocaleString()} Ks</Text>
                ) : null}
                <TouchableOpacity onPress={() => removeDetail(i)} style={st.lineRemove}>
                  <Text style={st.lineRemoveText}>✕</Text>
                </TouchableOpacity>
              </View>
            ))
          }

          {details.length > 0 && (
            <View style={st.totalRow}>
              <Text style={st.totalLabel}>Est. Total</Text>
              <Text style={st.totalVal}>{estimatedTotal.toLocaleString()} Ks</Text>
            </View>
          )}
        </View>

        <TouchableOpacity style={[st.submitBtn, submitting && { opacity: 0.6 }]} onPress={handleSubmit} disabled={submitting}>
          {submitting ? <ActivityIndicator color="#fff" /> : <Text style={st.submitBtnText}>📅  Create Booking</Text>}
        </TouchableOpacity>
      </ScrollView>

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
        subKey="role"
        onSelect={setStaff}
        onClose={() => setStaffPicker(false)}
      />
      <PickerModal
        visible={showSvcPicker}
        title="Add Service"
        items={services}
        labelKey="item"
        subKey="serviceTypeName"
        onSelect={addService}
        onClose={() => setSvcPicker(false)}
      />
      <NewCustomerModal
        visible={showNewCust}
        onClose={() => setNewCust(false)}
        onCreated={c => { setCustomers(prev => [...prev, c]); setCustomer(c); }}
      />
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  root:             { flex: 1, backgroundColor: C.bg },
  section:          { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  sectionTitle:     { fontSize: 11, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 8 },
  sectionHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  row:              { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 11, borderBottomWidth: 1, borderBottomColor: C.border },
  rowKey:           { fontSize: 13, fontWeight: '600', color: C.textMuted },
  rowVal:           { fontSize: 14, fontWeight: '700', color: C.text },
  label:            { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', marginBottom: 5, marginTop: 12 },
  input:            { backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
  addBtn:           { backgroundColor: C.primary, paddingHorizontal: 14, paddingVertical: 7, borderRadius: 8 },
  addBtnText:       { color: '#fff', fontWeight: '700', fontSize: 12 },
  empty:            { textAlign: 'center', color: C.textMuted, paddingVertical: 12, fontSize: 13 },
  lineRow:          { flexDirection: 'row', alignItems: 'center', paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: C.border, gap: 8 },
  lineName:         { fontSize: 13, fontWeight: '700', color: C.text },
  lineCost:         { fontSize: 13, fontWeight: '700', color: C.primary },
  lineRemove:       { width: 26, height: 26, borderRadius: 6, backgroundColor: C.dangerBg, justifyContent: 'center', alignItems: 'center' },
  lineRemoveText:   { fontSize: 12, fontWeight: '700', color: C.danger },
  totalRow:         { flexDirection: 'row', justifyContent: 'space-between', paddingTop: 10, marginTop: 4 },
  totalLabel:       { fontSize: 13, fontWeight: '700', color: C.textMuted },
  totalVal:         { fontSize: 15, fontWeight: '800', color: C.primary },
  submitBtn:       { backgroundColor: C.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center' },
  submitBtnText:   { color: '#fff', fontWeight: '800', fontSize: 16 },
  deviceCard:      { borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, marginBottom: 12, backgroundColor: C.bg },
  deviceCardTitle: { fontSize: 11, fontWeight: '800', color: C.primary, textTransform: 'uppercase' },
});
