import React, { useEffect, useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet,
  ActivityIndicator, Alert, TextInput, Modal,
} from 'react-native';
import { api } from '../../api/client';
import { ApiResponse, BookingDTO } from '../../types';
import { C } from '../../theme';
import { useAuth } from '../../context/AuthContext';
import ScannerModal from '../../components/ScannerModal';

const STATUSES = ['Pending', 'Confirmed', 'IN_STORAGE', 'Completed', 'Cancelled'];
const STATUS_COL: Record<string, { bg: string; text: string }> = {
  Pending:    { bg: C.warningBg,    text: C.warning  },
  Confirmed:  { bg: C.primaryLight, text: C.primary  },
  IN_STORAGE: { bg: '#ccfbf1',      text: '#0f766e'  },
  Converted:  { bg: C.violetBg,     text: C.violet   },
  Completed:  { bg: C.successBg,    text: C.success  },
  Cancelled:  { bg: C.dangerBg,     text: C.danger   },
};

function Row({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <View style={st.row}>
      <Text style={st.rowKey}>{label}</Text>
      <Text style={st.rowVal}>{value}</Text>
    </View>
  );
}

export default function BookingDetailScreen({ route, navigation }: any) {
  const { bookingId } = route.params;
  const { hasPermission } = useAuth();
  const [booking,  setBooking]  = useState<BookingDTO | null>(null);
  const [loading,  setLoading]  = useState(true);
  const [updating, setUpdating] = useState(false);
  const [showStorageModal, setShowStorageModal] = useState(false);
  const [shelfInput, setShelfInput] = useState('');
  const [showScanner, setShowScanner] = useState(false);

  const load = () => {
    api.get<ApiResponse<BookingDTO>>(`/bookings/${bookingId}`)
      .then(r => setBooking(r.data))
      .catch(() => Alert.alert('Error', 'Cannot load booking'))
      .finally(() => setLoading(false));
  };

  useEffect(load, [bookingId]);

  const updateStatus = async (status: string) => {
    setUpdating(true);
    try {
      const res = await api.put<ApiResponse<BookingDTO>>(
        `/bookings/${bookingId}`, { ...booking, status }
      );
      setBooking(res.data);
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Failed to update status');
    }
    setUpdating(false);
  };

  const convertToJob = async () => {
    Alert.alert('Convert to Job', 'Service Job အဖြစ် ပြောင်းမလား?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Convert',
        onPress: async () => {
          setUpdating(true);
          try {
            await api.post(`/bookings/${bookingId}/convert-to-job`);
            Alert.alert('Success', 'Service Job ဖန်တီးပြီးပါပြီ', [
              { text: 'OK', onPress: () => navigation.goBack() },
            ]);
          } catch (e: any) {
            Alert.alert('Error', e?.message ?? 'Failed');
          }
          setUpdating(false);
        },
      },
    ]);
  };

  const moveToStorage = async () => {
    if (!shelfInput.trim()) { Alert.alert('Error', 'Shelf location ထည့်ပါ'); return; }
    setUpdating(true);
    try {
      const res = await api.put<ApiResponse<BookingDTO>>(
        `/bookings/${bookingId}`, { ...booking, status: 'IN_STORAGE', shelfLocation: shelfInput.trim() }
      );
      setBooking(res.data);
      setShowStorageModal(false);
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Failed');
    }
    setUpdating(false);
  };

  const handleScan = async (data: string) => {
    setShowScanner(false);
    try {
      const res = await api.get<ApiResponse<BookingDTO>>(`/bookings/scan/${encodeURIComponent(data)}`);
      if (res.data?.id) {
        navigation.replace('BookingDetail', { bookingId: res.data.id });
      } else {
        Alert.alert('Not found', `No booking found for: ${data}`);
      }
    } catch {
      Alert.alert('Not found', `No booking found for: ${data}`);
    }
  };

  if (loading) return <ActivityIndicator color={C.primary} style={{ marginTop: 80 }} size="large" />;
  if (!booking) return <Text style={{ textAlign: 'center', marginTop: 40, color: C.textMuted }}>Not found</Text>;

  const col = STATUS_COL[booking.status ?? 'Pending'] ?? STATUS_COL.Pending;
  const fmt = (d?: string) => d ? new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : '-';

  return (
    <View style={{ flex: 1, backgroundColor: C.bg }}>
      <ScrollView contentContainerStyle={{ padding: 14, paddingBottom: 100 }}>
        {/* Header */}
        <View style={st.section}>
          <View style={st.headerRow}>
            <Text style={st.code}>{booking.invoiceNo ?? `#${booking.id}`}</Text>
            <View style={[st.badge, { backgroundColor: col.bg }]}>
              <Text style={[st.badgeText, { color: col.text }]}>{(booking.status ?? '').replace('_', ' ')}</Text>
            </View>
          </View>
          <Row label="Customer"    value={booking.customerName} />
          <Row label="Phone"       value={booking.customerPhone} />
          <Row label="Staff"       value={booking.staffName} />
          <Row label="Booked"      value={fmt(booking.bookingDate)} />
          <Row label="Appointment" value={fmt(booking.appointmentDate)} />
          <Row label="Payment"     value={booking.paymentMethodName} />
          {booking.totalAmount ? <Row label="Amount" value={`${Number(booking.totalAmount).toLocaleString()} Ks`} /> : null}
          <Row label="Remark"      value={booking.remark} />
          {booking.shelfLocation ? (
            <View style={st.shelfBadge}>
              <Text style={st.shelfBadgeText}>📦 Shelf: {booking.shelfLocation}</Text>
            </View>
          ) : null}
        </View>

        {/* Devices (multiple) */}
        {(booking.devices && booking.devices.length > 0) ? (
          booking.devices.map((dev: any, i: number) => (
            <View key={i} style={st.section}>
              <Text style={st.sectionTitle}>{booking.devices!.length > 1 ? `DEVICE ${i + 1}` : 'DEVICE'}</Text>
              <Row label="Type"        value={dev.deviceType} />
              <Row label="Brand"       value={dev.brand} />
              <Row label="Model"       value={dev.model} />
              <Row label="Serial"      value={dev.serialNumber} />
              <Row label="Color"       value={dev.color} />
              <Row label="Accessories" value={dev.accessories} />
            </View>
          ))
        ) : (booking.brand || booking.model || booking.serialNumber) ? (
          <View style={st.section}>
            <Text style={st.sectionTitle}>DEVICE</Text>
            <Row label="Type"        value={booking.deviceType} />
            <Row label="Brand"       value={booking.brand} />
            <Row label="Model"       value={booking.model} />
            <Row label="Serial"      value={booking.serialNumber} />
            <Row label="Color"       value={booking.color} />
            <Row label="Accessories" value={booking.accessories} />
          </View>
        ) : null}

        {/* Services */}
        {(booking.details ?? []).length > 0 && (
          <View style={st.section}>
            <Text style={st.sectionTitle}>SERVICES</Text>
            {booking.details!.map((d, i) => (
              <View key={i} style={st.serviceRow}>
                <Text style={st.serviceName}>{d.subServiceTypeName ?? d.serviceTypeName}</Text>
                {d.estimatedCost ? <Text style={st.serviceCost}>{Number(d.estimatedCost).toLocaleString()} Ks</Text> : null}
              </View>
            ))}
          </View>
        )}

        {/* Status update */}
        {hasPermission('CAN_ACCESS_BOOKING_UPDATE') && (
          <View style={st.section}>
            <Text style={st.sectionTitle}>UPDATE STATUS</Text>
            <View style={st.statusGrid}>
              {STATUSES.map(s => {
                const c = STATUS_COL[s];
                const active = booking.status === s;
                return (
                  <TouchableOpacity
                    key={s}
                    style={[st.statusBtn, { backgroundColor: active ? c.text : c.bg }]}
                    onPress={() => !active && updateStatus(s)}
                    disabled={active || updating}
                  >
                    <Text style={[st.statusBtnText, { color: active ? '#fff' : c.text }]}>
                      {s.replace('_', ' ')}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>
        )}
      </ScrollView>

      {(hasPermission('CAN_ACCESS_BOOKING_UPDATE') || hasPermission('CAN_ACCESS_BOOKING_CONVERT_JOB')) &&
        booking.status !== 'Cancelled' && booking.status !== 'Converted' && booking.status !== 'Completed' && (
        <View style={st.footer}>
          {updating && <ActivityIndicator color={C.primary} style={{ marginBottom: 8 }} />}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {hasPermission('CAN_ACCESS_BOOKING_UPDATE') && booking.status !== 'IN_STORAGE' && (
              <TouchableOpacity
                style={[st.storageBtn, { flex: 1 }]}
                onPress={() => { setShelfInput(booking.shelfLocation ?? ''); setShowStorageModal(true); }}
                disabled={updating}
              >
                <Text style={st.storageBtnText}>📦  Move to Storage</Text>
              </TouchableOpacity>
            )}
            {hasPermission('CAN_ACCESS_BOOKING_CONVERT_JOB') && (
              <TouchableOpacity style={[st.convertBtn, { flex: 1 }]} onPress={convertToJob} disabled={updating}>
                <Text style={st.convertBtnText}>🔧  To Job</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      )}

      {/* Scan QR button (floating) */}
      <TouchableOpacity style={st.scanFab} onPress={() => setShowScanner(true)}>
        <Text style={st.scanFabText}>📷</Text>
      </TouchableOpacity>

      {/* Move to Storage Modal */}
      <Modal visible={showStorageModal} animationType="slide" transparent onRequestClose={() => setShowStorageModal(false)}>
        <View style={st.modalOverlay}>
          <View style={st.modalBox}>
            <Text style={st.modalTitle}>Move to Storage</Text>
            <Text style={st.modalLabel}>Shelf / Location</Text>
            <TextInput
              style={st.modalInput}
              value={shelfInput}
              onChangeText={setShelfInput}
              placeholder="e.g. A-01, Shelf-3"
              placeholderTextColor={C.textMuted}
              autoFocus
            />
            <View style={{ flexDirection: 'row', gap: 8, marginTop: 16 }}>
              <TouchableOpacity style={[st.modalBtn, { backgroundColor: C.border }]} onPress={() => setShowStorageModal(false)}>
                <Text style={{ fontWeight: '700', color: C.text }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[st.modalBtn, { backgroundColor: '#0f766e', flex: 1 }]} onPress={moveToStorage} disabled={updating}>
                <Text style={{ fontWeight: '700', color: '#fff' }}>Save</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {showScanner && (
        <ScannerModal
          visible={showScanner}
          onDetected={handleScan}
          onClose={() => setShowScanner(false)}
          title="Scan Booking QR"
        />
      )}
    </View>
  );
}

const st = StyleSheet.create({
  section:      { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  sectionTitle: { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 8 },
  headerRow:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  code:         { fontSize: 16, fontWeight: '800', color: C.primary },
  badge:        { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 },
  badgeText:    { fontSize: 12, fontWeight: '800' },
  row:          { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4, borderBottomWidth: 1, borderBottomColor: C.border },
  rowKey:       { fontSize: 12, color: C.textMuted, fontWeight: '600' },
  rowVal:       { fontSize: 12, fontWeight: '700', color: C.text, maxWidth: '60%' as any, textAlign: 'right' },
  serviceRow:   { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
  serviceName:  { fontSize: 13, fontWeight: '600', color: C.text },
  serviceCost:  { fontSize: 13, fontWeight: '700', color: C.primary },
  statusGrid:   { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  statusBtn:    { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },
  statusBtnText:{ fontSize: 12, fontWeight: '700' },
  footer:         { padding: 14, backgroundColor: C.card, borderTopWidth: 1, borderTopColor: C.border },
  convertBtn:     { backgroundColor: C.violet, borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  convertBtnText: { color: '#fff', fontWeight: '800', fontSize: 14 },
  storageBtn:     { backgroundColor: '#0f766e', borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  storageBtnText: { color: '#fff', fontWeight: '800', fontSize: 14 },
  shelfBadge:     { marginTop: 8, backgroundColor: '#ccfbf1', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6, alignSelf: 'flex-start' },
  shelfBadgeText: { fontSize: 12, fontWeight: '700', color: '#0f766e' },
  scanFab:        { position: 'absolute', right: 18, bottom: 90, width: 50, height: 50, borderRadius: 25, backgroundColor: C.primary, justifyContent: 'center', alignItems: 'center', elevation: 5 },
  scanFabText:    { fontSize: 22 },
  modalOverlay:   { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  modalBox:       { backgroundColor: C.card, borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 24, paddingBottom: 40 },
  modalTitle:     { fontSize: 16, fontWeight: '800', color: C.text, marginBottom: 16 },
  modalLabel:     { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', marginBottom: 6 },
  modalInput:     { backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 11, fontSize: 15, color: C.text },
  modalBtn:       { borderRadius: 10, paddingVertical: 13, alignItems: 'center', paddingHorizontal: 20 },
});
