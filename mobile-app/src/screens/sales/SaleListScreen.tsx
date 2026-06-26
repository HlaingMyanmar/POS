import React, { useEffect, useState } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl,
} from 'react-native';
import { api } from '../../api/client';
import { ApiResponse, SaleDTO } from '../../types';
import { C } from '../../theme';
import { useWsTopic } from '../../hooks/useWsTopic';

const STATUS_COLOR: Record<string, { bg: string; text: string }> = {
  Paid:    { bg: C.successBg,   text: C.success  },
  Partial: { bg: C.warningBg,   text: C.warning  },
  Pending: { bg: C.dangerBg,    text: C.danger   },
};

export default function SaleListScreen({ navigation }: any) {
  const [sales,     setSales]     = useState<SaleDTO[]>([]);
  const [loading,   setLoading]   = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<any>>('/sales?page=0&size=50');
      const list = res.data?.content ?? (Array.isArray(res.data) ? res.data : []);
      setSales(list);
    } catch {}
    setLoading(false);
    setRefreshing(false);
  };

  useEffect(() => { load(); }, []);
  useWsTopic('/topic/sales', () => load(true));

  const renderItem = ({ item: s }: { item: SaleDTO }) => {
    const status = s.paymentStatus ?? 'Pending';
    const col = STATUS_COLOR[status] ?? STATUS_COLOR['Pending'];
    return (
      <TouchableOpacity style={st.card} onPress={() => navigation.navigate('SaleDetail', { saleId: s.id })} activeOpacity={0.75}>
        <View style={st.cardHeader}>
          <Text style={st.saleCode}>{s.saleCode ?? `#${s.id}`}</Text>
          <View style={[st.badge, { backgroundColor: col.bg }]}>
            <Text style={[st.badgeText, { color: col.text }]}>{status}</Text>
          </View>
        </View>
        <Text style={st.customer}>{s.customerName ?? 'Customer'}</Text>
        <View style={st.amounts}>
          <View>
            <Text style={st.amtLabel}>Net Amount</Text>
            <Text style={st.amtValue}>{(s.netAmount ?? 0).toLocaleString()} Ks</Text>
          </View>
          {(s.dueAmount ?? 0) > 0 && (
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={st.amtLabel}>Due</Text>
              <Text style={[st.amtValue, { color: C.danger }]}>
                {(s.dueAmount ?? 0).toLocaleString()} Ks
              </Text>
            </View>
          )}
        </View>
        {s.saleDate && (
          <Text style={st.date}>{new Date(s.saleDate).toLocaleDateString()}</Text>
        )}
      </TouchableOpacity>
    );
  };

  return (
    <View style={st.root}>
      {/* New Sale FAB */}
      <TouchableOpacity style={st.fab} onPress={() => navigation.navigate('NewSale')}>
        <Text style={st.fabText}>+ New Sale</Text>
      </TouchableOpacity>

      {loading
        ? <ActivityIndicator color={C.primary} style={{ marginTop: 40 }} size="large" />
        : (
          <FlatList
            data={sales}
            keyExtractor={s => String(s.id)}
            renderItem={renderItem}
            contentContainerStyle={{ padding: 12, paddingBottom: 80 }}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={() => { setRefreshing(true); load(true); }}
                colors={[C.primary]}
              />
            }
            ListEmptyComponent={
              <Text style={st.empty}>No sales found</Text>
            }
          />
        )
      }
    </View>
  );
}

const st = StyleSheet.create({
  root:       { flex: 1, backgroundColor: C.bg },
  fab:        { position: 'absolute', bottom: 20, right: 16, zIndex: 10, backgroundColor: C.primary, borderRadius: 28, paddingHorizontal: 20, paddingVertical: 12, elevation: 6, shadowColor: C.primary, shadowOpacity: 0.4, shadowRadius: 8 },
  fabText:    { color: '#fff', fontWeight: '700', fontSize: 14 },
  card:       { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 8 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  saleCode:   { fontSize: 14, fontWeight: '800', color: C.primary },
  badge:      { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  badgeText:  { fontSize: 11, fontWeight: '700' },
  customer:   { fontSize: 14, fontWeight: '600', color: C.text, marginBottom: 10 },
  amounts:    { flexDirection: 'row', justifyContent: 'space-between' },
  amtLabel:   { fontSize: 11, color: C.textMuted, fontWeight: '600', marginBottom: 2 },
  amtValue:   { fontSize: 15, fontWeight: '800', color: C.text },
  date:       { fontSize: 11, color: C.textMuted, marginTop: 8 },
  empty:      { textAlign: 'center', color: C.textMuted, marginTop: 40, fontSize: 14 },
});
