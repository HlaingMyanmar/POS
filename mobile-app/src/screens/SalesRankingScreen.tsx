import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl, TextInput,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../api/client';
import { ApiResponse } from '../types';
import { C } from '../theme';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const MEDALS = ['🥇','🥈','🥉'];

type TabType = 'products' | 'monthly';

export default function SalesRankingScreen() {
  const [tab,      setTab]      = useState<TabType>('products');
  const [products, setProducts] = useState<any[]>([]);
  const [monthly,  setMonthly]  = useState<any[]>([]);
  const [loading,  setLoading]  = useState(false);
  const [refresh,  setRefresh]  = useState(false);
  const [from,     setFrom]     = useState('');
  const [to,       setTo]       = useState('');

  const loadProducts = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const params = new URLSearchParams();
      if (from) params.set('from', from);
      if (to)   params.set('to', to);
      const res = await api.get<ApiResponse<any[]>>(`/reports/sales-ranking/products?${params}`);
      setProducts(res.data ?? []);
    } catch {}
    setLoading(false);
    setRefresh(false);
  }, [from, to]);

  const loadMonthly = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<any[]>>('/reports/sales-ranking/monthly');
      setMonthly(res.data ?? []);
    } catch {}
    setLoading(false);
    setRefresh(false);
  }, []);

  useEffect(() => {
    if (tab === 'products') loadProducts();
    else loadMonthly();
  }, [tab]);

  const maxQty = products[0]?.totalQty ?? 1;
  const maxAmt = monthly.length ? Math.max(...monthly.map(m => Number(m.totalAmount))) : 1;

  const renderProduct = ({ item, index }: { item: any; index: number }) => {
    const pct = Math.round((Number(item.totalQty) / Number(maxQty)) * 100);
    return (
      <View style={[st.card, index < 3 && { backgroundColor: '#FFFBEB', borderColor: '#FDE68A' }]}>
        <View style={st.cardTop}>
          <Text style={st.medal}>{MEDALS[index] ?? <Text style={st.rank}>#{index + 1}</Text>}</Text>
          <View style={{ flex: 1 }}>
            <Text style={st.productName} numberOfLines={1}>{item.productName}</Text>
            <Text style={st.productCode}>{item.productCode}</Text>
          </View>
          <View style={st.qtyBadge}>
            <Text style={st.qtyText}>{Number(item.totalQty).toLocaleString()}</Text>
            <Text style={st.qtyLabel}>pcs</Text>
          </View>
        </View>
        <Text style={st.amount}>{Number(item.totalAmount).toLocaleString()} Ks</Text>
        <View style={st.barBg}>
          <View style={[st.barFill, { width: `${pct}%` as any, backgroundColor: index < 3 ? '#F59E0B' : C.primary }]} />
        </View>
      </View>
    );
  };

  const renderMonth = ({ item, index }: { item: any; index: number }) => {
    const pct = Math.round((Number(item.totalAmount) / maxAmt) * 100);
    return (
      <View style={[st.card, index < 3 && { backgroundColor: '#FFFBEB', borderColor: '#FDE68A' }]}>
        <View style={st.cardTop}>
          <Text style={st.medal}>{MEDALS[index] ?? ''}</Text>
          <View style={{ flex: 1 }}>
            <Text style={st.productName}>{MONTHS[item.month - 1]} {item.year}</Text>
            <Text style={st.productCode}>{Number(item.totalQty).toLocaleString()} units sold</Text>
          </View>
          <View style={[st.qtyBadge, { backgroundColor: C.successBg }]}>
            <Text style={[st.qtyText, { color: C.success }]}>{Number(item.totalAmount).toLocaleString()}</Text>
            <Text style={[st.qtyLabel, { color: C.success }]}>Ks</Text>
          </View>
        </View>
        <View style={[st.barBg, { marginTop: 8 }]}>
          <View style={[st.barFill, { width: `${pct}%` as any, backgroundColor: C.success }]} />
        </View>
      </View>
    );
  };

  return (
    <View style={st.root}>
      {/* Tab row */}
      <View style={st.tabRow}>
        <TouchableOpacity style={[st.tab, tab === 'products' && st.tabActive]} onPress={() => setTab('products')}>
          <Text style={[st.tabText, tab === 'products' && st.tabActiveText]}>🏆 Top Products</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[st.tab, tab === 'monthly' && st.tabActive]} onPress={() => setTab('monthly')}>
          <Text style={[st.tabText, tab === 'monthly' && st.tabActiveText]}>📅 By Month</Text>
        </TouchableOpacity>
      </View>

      {/* Product date filter */}
      {tab === 'products' && (
        <View style={st.filterRow}>
          <TextInput
            style={[st.filterInput, { flex: 1 }]}
            placeholder="From (YYYY-MM-DD)"
            placeholderTextColor={C.textMuted}
            value={from}
            onChangeText={setFrom}
          />
          <Text style={{ color: C.textMuted, marginHorizontal: 4 }}>—</Text>
          <TextInput
            style={[st.filterInput, { flex: 1 }]}
            placeholder="To (YYYY-MM-DD)"
            placeholderTextColor={C.textMuted}
            value={to}
            onChangeText={setTo}
          />
          <TouchableOpacity style={st.applyBtn} onPress={() => loadProducts()}>
            <Ionicons name="search" size={16} color="#fff" />
          </TouchableOpacity>
        </View>
      )}

      {loading ? (
        <ActivityIndicator color={C.primary} style={{ marginTop: 40 }} size="large" />
      ) : tab === 'products' ? (
        <FlatList
          data={products}
          keyExtractor={i => String(i.productId)}
          renderItem={renderProduct}
          contentContainerStyle={{ padding: 12, paddingBottom: 24 }}
          refreshControl={<RefreshControl refreshing={refresh} onRefresh={() => { setRefresh(true); loadProducts(true); }} colors={[C.primary]} />}
          ListEmptyComponent={<Text style={st.empty}>No sales data</Text>}
        />
      ) : (
        <FlatList
          data={monthly}
          keyExtractor={i => i.monthLabel}
          renderItem={renderMonth}
          contentContainerStyle={{ padding: 12, paddingBottom: 24 }}
          refreshControl={<RefreshControl refreshing={refresh} onRefresh={() => { setRefresh(true); loadMonthly(true); }} colors={[C.primary]} />}
          ListEmptyComponent={<Text style={st.empty}>No monthly data</Text>}
        />
      )}
    </View>
  );
}

const st = StyleSheet.create({
  root:         { flex: 1, backgroundColor: C.bg },
  tabRow:       { flexDirection: 'row', backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border },
  tab:          { flex: 1, paddingVertical: 12, alignItems: 'center' },
  tabActive:    { borderBottomWidth: 2, borderBottomColor: C.primary },
  tabText:      { fontSize: 13, fontWeight: '700', color: C.textMuted },
  tabActiveText:{ color: C.primary },
  filterRow:    { flexDirection: 'row', alignItems: 'center', padding: 10, backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border, gap: 6 },
  filterInput:  { borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 8, paddingVertical: 6, fontSize: 12, color: C.text, backgroundColor: C.bg },
  applyBtn:     { backgroundColor: C.primary, borderRadius: 8, padding: 8 },
  card:         { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 8 },
  cardTop:      { flexDirection: 'row', alignItems: 'center', gap: 10 },
  medal:        { fontSize: 22, width: 30 },
  rank:         { fontSize: 13, fontWeight: '800', color: C.textMuted },
  productName:  { fontSize: 14, fontWeight: '700', color: C.text },
  productCode:  { fontSize: 11, color: C.textMuted, marginTop: 1 },
  qtyBadge:     { backgroundColor: C.primaryLight, borderRadius: 8, paddingHorizontal: 10, paddingVertical: 5, alignItems: 'center' },
  qtyText:      { fontSize: 15, fontWeight: '800', color: C.primary },
  qtyLabel:     { fontSize: 9, fontWeight: '700', color: C.primary },
  amount:       { fontSize: 12, color: C.textMuted, marginTop: 6, marginLeft: 40 },
  barBg:        { height: 4, backgroundColor: C.border, borderRadius: 2, marginTop: 6, marginLeft: 40 },
  barFill:      { height: 4, borderRadius: 2 },
  empty:        { textAlign: 'center', color: C.textMuted, marginTop: 40, fontSize: 14 },
});
