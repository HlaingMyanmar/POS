import React, { useEffect, useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet,
  ActivityIndicator, Alert,
} from 'react-native';
import { api } from '../../api/client';
import { ApiResponse, BookingDTO } from '../../types';
import { C } from '../../theme';
import { useAuth } from '../../context/AuthContext';

const STATUS_COL: Record<string, { bg: string; text: string }> = {
  CONFIRMED: { bg: C.primaryLight, text: C.primary },
  ARRIVED:   { bg: '#ccfbf1', text: '#0f766e' },
  CANCELED:  { bg: C.dangerBg, text: C.danger },
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
  const [booking, setBooking] = useState<BookingDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);

  const load = () => {
    api.get<ApiResponse<BookingDTO>>(`/bookings/${bookingId}`)
      .then(response => setBooking(response.data))
      .catch((error: any) => Alert.alert('Error', error?.message ?? 'Cannot load booking'))
      .finally(() => setLoading(false));
  };

  useEffect(load, [bookingId]);

  const runAction = async (
    title: string,
    message: string,
    request: () => Promise<ApiResponse<BookingDTO>>,
  ) => {
    Alert.alert(title, message, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Continue',
        onPress: async () => {
          setUpdating(true);
          try {
            const response = await request();
            setBooking(response.data);
          } catch (error: any) {
            Alert.alert('Error', error?.message ?? 'Action failed');
          } finally {
            setUpdating(false);
          }
        },
      },
    ]);
  };

  if (loading) return <ActivityIndicator color={C.primary} style={{ marginTop: 80 }} size="large" />;
  if (!booking) return <Text style={st.empty}>Booking not found</Text>;

  const color = STATUS_COL[booking.status ?? 'CONFIRMED'] ?? STATUS_COL.CONFIRMED;
  const formatDate = (value?: string) => value ? new Date(value).toLocaleString() : '-';
  const hasLinkedJobs = (booking.linkedJobs?.length ?? 0) > 0;

  return (
    <View style={st.root}>
      <ScrollView contentContainerStyle={{ padding: 14, paddingBottom: 120 }}>
        <View style={st.section}>
          <View style={st.headerRow}>
            <Text style={st.code}>{booking.bookingNo ?? `#${booking.id}`}</Text>
            <View style={[st.badge, { backgroundColor: color.bg }]}>
              <Text style={[st.badgeText, { color: color.text }]}>{booking.status}</Text>
            </View>
          </View>
          <Row label="Customer" value={booking.customerName} />
          <Row label="Phone" value={booking.customerPhone} />
          <Row label="Booking date" value={formatDate(booking.bookingDate)} />
          <Row label="Appointment" value={formatDate(booking.appointmentDate)} />
          <Row label="Complaint" value={booking.complaintNote} />
          <Row label="Remark" value={booking.remark} />
        </View>

        {(booking.items ?? []).map((item, index) => (
          <View key={item.id ?? index} style={st.section}>
            <View style={st.headerRow}>
              <Text style={st.sectionTitle}>ITEM {index + 1}</Text>
              <Text style={item.convertedJobId ? st.converted : st.pending}>
                {item.convertedJobId ? 'JOB CREATED' : 'PENDING'}
              </Text>
            </View>
            <Row label="Item" value={item.itemName} />
            <Row label="Device type" value={item.deviceType} />
            <Row label="Serial / IMEI" value={item.serialNo} />
            <Row label="Color" value={item.color} />
            <Row label="Accessories" value={item.accessories} />
            <Row label="Problem" value={item.problemDesc} />
            <Row label="Condition" value={item.itemCondition} />
            <Row label="Noticed" value={item.noticed} />
          </View>
        ))}

        {(booking.linkedJobs ?? []).length > 0 && (
          <View style={st.section}>
            <Text style={st.sectionTitle}>LINKED SERVICE JOBS</Text>
            {booking.linkedJobs!.map(job => (
              <TouchableOpacity
                key={job.id}
                style={st.jobRow}
                onPress={() => navigation.navigate('ServiceJobDetail', { jobId: job.id })}
              >
                <View>
                  <Text style={st.jobNo}>{job.jobNo}</Text>
                  <Text style={st.jobItem}>{job.itemName ?? '-'}</Text>
                </View>
                <Text style={st.jobStatus}>{job.status}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}
      </ScrollView>

      {booking.status !== 'CANCELED' && (
        <View style={st.footer}>
          {updating && <ActivityIndicator color={C.primary} style={{ marginBottom: 8 }} />}
          <View style={st.footerRow}>
            {hasPermission('CAN_ACCESS_BOOKING_CONVERT_JOB') && booking.status === 'CONFIRMED' && !hasLinkedJobs && (
              <TouchableOpacity
                style={[st.actionBtn, { backgroundColor: C.violet }]}
                disabled={updating}
                onPress={() => runAction(
                  'Outdoor service',
                  'Create one outdoor service job for this booking?',
                  () => api.post<ApiResponse<BookingDTO>>(`/bookings/${bookingId}/convert-outdoor`, {}),
                )}
              >
                <Text style={st.actionText}>Outdoor Job</Text>
              </TouchableOpacity>
            )}
            {hasPermission('CAN_ACCESS_BOOKING_CONVERT_JOB') && booking.status === 'ARRIVED' && (booking.unconvertedItemCount ?? 0) > 0 && (
              <TouchableOpacity
                style={[st.actionBtn, { backgroundColor: C.success }]}
                disabled={updating}
                onPress={() => runAction(
                  'Indoor service',
                  'Create one service job for every pending item?',
                  () => api.post<ApiResponse<BookingDTO>>(`/bookings/${bookingId}/convert-indoor`, {}),
                )}
              >
                <Text style={st.actionText}>Indoor Job(s)</Text>
              </TouchableOpacity>
            )}
            {hasPermission('CAN_ACCESS_BOOKING_UPDATE') && !hasLinkedJobs && (
              <TouchableOpacity
                style={[st.actionBtn, { backgroundColor: C.danger }]}
                disabled={updating}
                onPress={() => runAction(
                  'Cancel booking',
                  'Cancel this booking?',
                  () => api.patch<ApiResponse<BookingDTO>>(`/bookings/${bookingId}/status?status=CANCELED`, {}),
                )}
              >
                <Text style={st.actionText}>Cancel</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      )}
    </View>
  );
}

const st = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.bg },
  section: { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  sectionTitle: { fontSize: 11, fontWeight: '800', color: C.textMuted, letterSpacing: 0.6 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  code: { fontSize: 16, fontWeight: '800', color: C.primary },
  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 },
  badgeText: { fontSize: 12, fontWeight: '800' },
  row: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 5, borderBottomWidth: 1, borderBottomColor: C.border },
  rowKey: { fontSize: 12, color: C.textMuted, fontWeight: '600' },
  rowVal: { fontSize: 12, fontWeight: '700', color: C.text, maxWidth: '62%' as any, textAlign: 'right' },
  converted: { color: C.success, fontSize: 10, fontWeight: '800' },
  pending: { color: C.warning, fontSize: 10, fontWeight: '800' },
  jobRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.border },
  jobNo: { color: C.primary, fontWeight: '800', fontSize: 13 },
  jobItem: { color: C.textMuted, fontSize: 12, marginTop: 2 },
  jobStatus: { color: C.text, fontWeight: '700', fontSize: 11 },
  footer: { padding: 14, backgroundColor: C.card, borderTopWidth: 1, borderTopColor: C.border },
  footerRow: { flexDirection: 'row', gap: 8 },
  actionBtn: { flex: 1, borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  actionText: { color: '#fff', fontWeight: '800', fontSize: 13 },
  empty: { textAlign: 'center', marginTop: 40, color: C.textMuted },
});
