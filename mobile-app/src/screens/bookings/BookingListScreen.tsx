import React, { useEffect, useState } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../api/client';
import { ApiResponse, BookingDTO } from '../../types';
import { C } from '../../theme';
import { useWsTopic } from '../../hooks/useWsTopic';
import { useLayoutEffect } from 'react';

const fmtDate = (v?: string) => {
  if (!v) return '';
  try {
    const d = new Date(v);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch { return v; }
};

const STATUS_COL: Record<string, { bg: string; text: string }> = {
  Pending:    { bg: C.warningBg,    text: C.warning  },
  Confirmed:  { bg: C.primaryLight, text: C.primary  },
  IN_STORAGE: { bg: '#ccfbf1',      text: '#0f766e'  },
  Converted:  { bg: C.violetBg,     text: C.violet   },
  Completed:  { bg: C.successBg,    text: C.success  },
  Cancelled:  { bg: C.dangerBg,     text: C.danger   },
};

export default function BookingListScreen({ navigation }: any) {
  const [items,     setItems]     = useState<BookingDTO[]>([]);
  const [loading,   setLoading]   = useState(true);
  const [refreshing,setRefreshing]= useState(false);

  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <TouchableOpacity
          onPress={() => navigation.navigate('DoneServiceJobs')}
          style={{ marginRight: 12, backgroundColor: 'rgba(255,255,255,0.15)', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8 }}
        >
          <Text style={{ color: '#fff', fontWeight: '700', fontSize: 12 }}>✓ Done Jobs</Text>
        </TouchableOpacity>
      ),
    });
  }, [navigation]);

  const load = async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<BookingDTO[]>>('/bookings?page=0&size=50');
      setItems(Array.isArray(res.data) ? res.data : (res.data as any)?.content ?? []);
    } catch {}
    setLoading(false);
    setRefreshing(false);
  };

  useEffect(() => { load(); }, []);
  useWsTopic('/topic/booking', () => load(true));

  const renderItem = ({ item }: { item: BookingDTO }) => {
    const col = STATUS_COL[item.status ?? 'Pending'] ?? STATUS_COL.Pending;
    return (
      <TouchableOpacity style={st.card} onPress={() => navigation.navigate('BookingDetail', { bookingId: item.id })} activeOpacity={0.75}>
        <View style={st.cardTop}>
          <Text style={st.code}>{item.invoiceNo ?? `#${item.id}`}</Text>
          <View style={[st.badge, { backgroundColor: col.bg }]}>
            <Text style={[st.badgeText, { color: col.text }]}>{(item.status ?? '').replace('_', ' ')}</Text>
          </View>
        </View>
        <Text style={st.customer}>{item.customerName ?? '-'}</Text>
        {item.brand || item.model ? (
          <Text style={st.device}>{[item.brand, item.model, item.deviceType].filter(Boolean).join(' · ')}</Text>
        ) : null}
        {/* Date row */}
        <View style={st.dateRow}>
          {item.bookingDate ? (
            <View style={st.dateChip}>
              <Ionicons name="calendar-outline" size={11} color={C.textMuted} />
              <Text style={st.dateChipText}>Rcvd: {fmtDate(item.bookingDate)}</Text>
            </View>
          ) : null}
          {item.appointmentDate ? (
            <View style={[st.dateChip, { backgroundColor: C.primaryLight }]}>
              <Ionicons name="time-outline" size={11} color={C.primary} />
              <Text style={[st.dateChipText, { color: C.primary }]}>Appt: {fmtDate(item.appointmentDate)}</Text>
            </View>
          ) : null}
        </View>
        <View style={st.cardBottom}>
          {item.totalAmount ? <Text style={st.amount}>{Number(item.totalAmount).toLocaleString()} Ks</Text> : <View />}
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={st.root}>
      {loading
        ? <ActivityIndicator color={C.primary} style={{ marginTop: 40 }} size="large" />
        : (
          <FlatList
            data={items}
            keyExtractor={i => String(i.id)}
            renderItem={renderItem}
            contentContainerStyle={{ padding: 12, paddingBottom: 100 }}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(true); }} colors={[C.primary]} />}
            ListEmptyComponent={<Text style={st.empty}>No bookings found</Text>}
          />
        )
      }
      <TouchableOpacity style={st.fab} onPress={() => navigation.navigate('NewBooking')}>
        <Text style={st.fabText}>+ New Booking</Text>
      </TouchableOpacity>
    </View>
  );
}

const st = StyleSheet.create({
  root:       { flex: 1, backgroundColor: C.bg },
  card:       { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 8 },
  cardTop:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  code:       { fontSize: 14, fontWeight: '800', color: C.primary },
  badge:      { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  badgeText:  { fontSize: 11, fontWeight: '700' },
  customer:   { fontSize: 14, fontWeight: '600', color: C.text, marginBottom: 3 },
  device:     { fontSize: 12, color: C.textMuted, marginBottom: 6 },
  dateRow:    { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 8 },
  dateChip:   { flexDirection: 'row', alignItems: 'center', gap: 3, backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 6, paddingHorizontal: 7, paddingVertical: 3 },
  dateChipText:{ fontSize: 11, color: C.textMuted, fontWeight: '600' },
  cardBottom: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  amount:     { fontSize: 14, fontWeight: '800', color: C.text },
  empty:      { textAlign: 'center', color: C.textMuted, marginTop: 40, fontSize: 14 },
  fab:        { position: 'absolute' as any, bottom: 20, right: 16, zIndex: 10, backgroundColor: C.primary, borderRadius: 28, paddingHorizontal: 20, paddingVertical: 12, elevation: 6, shadowColor: C.primary, shadowOpacity: 0.4, shadowRadius: 8 },
  fabText:    { color: '#fff', fontWeight: '700', fontSize: 14 },
});
