import React, { useEffect, useState, useMemo } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl, TextInput, ScrollView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../api/client';
import { ApiResponse, ServiceJobDTO } from '../../types';
import { C } from '../../theme';
import { useWsTopic } from '../../hooks/useWsTopic';

const fmtDate = (v?: string) => {
  if (!v) return '';
  try {
    const d = new Date(v);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch { return v; }
};

const STATUS_COL: Record<string, { bg: string; text: string }> = {
  RECEIVED:    { bg: C.primaryLight, text: C.primary  },
  INSPECTING:  { bg: C.warningBg,    text: C.warning  },
  IN_PROGRESS: { bg: C.violetBg,     text: C.violet   },
  COMPLETED:   { bg: C.successBg,    text: C.success  },
  DELIVERED:   { bg: C.successBg,    text: C.success  },
  CANCELLED:   { bg: C.dangerBg,     text: C.danger   },
};

const ALL_STATUSES = ['RECEIVED', 'INSPECTING', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED', 'CANCELLED'];

export default function ServiceJobListScreen({ navigation }: any) {
  const [items,       setItems]       = useState<ServiceJobDTO[]>([]);
  const [loading,     setLoading]     = useState(true);
  const [refreshing,  setRefreshing]  = useState(false);
  const [search,      setSearch]      = useState('');
  const [statusFilter,setStatusFilter]= useState('');

  const load = async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<ServiceJobDTO[]>>('/service-jobs?page=0&size=100');
      setItems(Array.isArray(res.data) ? res.data : (res.data as any)?.content ?? []);
    } catch {}
    setLoading(false);
    setRefreshing(false);
  };

  useEffect(() => { load(); }, []);
  useWsTopic('/topic/service-jobs', () => load(true));

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return items.filter(j =>
      (!statusFilter || j.status === statusFilter) &&
      (!q || (j.jobNo ?? '').toLowerCase().includes(q) ||
             (j.customerName ?? '').toLowerCase().includes(q) ||
             (j.itemName ?? '').toLowerCase().includes(q))
    );
  }, [items, search, statusFilter]);

  const countByStatus = (s: string) => items.filter(j => j.status === s).length;

  const renderItem = ({ item }: { item: ServiceJobDTO }) => {
    const col = STATUS_COL[item.status ?? 'RECEIVED'] ?? STATUS_COL.RECEIVED;
    return (
      <TouchableOpacity
        style={st.card}
        onPress={() => navigation.navigate('ServiceJobDetail', { jobId: item.id })}
        activeOpacity={0.75}
      >
        <View style={st.cardTop}>
          <Text style={st.code}>{item.jobNo ?? `#${item.id}`}</Text>
          <View style={[st.badge, { backgroundColor: col.bg }]}>
            <Text style={[st.badgeText, { color: col.text }]}>{(item.status ?? '').replace(/_/g, ' ')}</Text>
          </View>
        </View>
        <Text style={st.customer}>{item.customerName ?? '-'}</Text>
        {item.itemName && <Text style={st.item}>{item.itemName}</Text>}
        {item.problemDesc && <Text style={st.problem} numberOfLines={1}>{item.problemDesc}</Text>}
        {/* Date chips */}
        <View style={st.dateRow}>
          {item.receivedDate ? (
            <View style={st.dateChip}>
              <Ionicons name="calendar-outline" size={11} color={C.textMuted} />
              <Text style={st.dateChipText}>Rcvd: {fmtDate(item.receivedDate)}</Text>
            </View>
          ) : null}
          {item.estimatedCompletion ? (
            <View style={[st.dateChip, { backgroundColor: C.violetBg }]}>
              <Ionicons name="time-outline" size={11} color={C.violet} />
              <Text style={[st.dateChipText, { color: C.violet }]}>Est: {fmtDate(item.estimatedCompletion)}</Text>
            </View>
          ) : null}
          {item.dueDate ? (
            <View style={[st.dateChip, { backgroundColor: C.warningBg }]}>
              <Ionicons name="alert-circle-outline" size={11} color={C.warning} />
              <Text style={[st.dateChipText, { color: C.warning }]}>Due: {fmtDate(item.dueDate)}</Text>
            </View>
          ) : null}
        </View>
        <View style={st.cardBottom}>
          {item.netAmount ? <Text style={st.amount}>{Number(item.netAmount).toLocaleString()} Ks</Text> : <View />}
        </View>
        {item.rework && (
          <View style={st.reworkBadge}>
            <Text style={st.reworkText}>REWORK</Text>
          </View>
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
          placeholder="Search job no / customer / item..."
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

      {/* Status filter chips */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={st.filterScroll} contentContainerStyle={{ paddingHorizontal: 12, gap: 6, paddingVertical: 8 }}>
        <TouchableOpacity
          style={[st.chip, !statusFilter && st.chipActive]}
          onPress={() => setStatusFilter('')}
        >
          <Text style={[st.chipText, !statusFilter && st.chipActiveText]}>All ({items.length})</Text>
        </TouchableOpacity>
        {ALL_STATUSES.map(s => {
          const col = STATUS_COL[s];
          const cnt = countByStatus(s);
          const active = statusFilter === s;
          return (
            <TouchableOpacity
              key={s}
              style={[st.chip, { backgroundColor: active ? col.text : col.bg }]}
              onPress={() => setStatusFilter(prev => prev === s ? '' : s)}
            >
              <Text style={[st.chipText, { color: active ? '#fff' : col.text }]}>
                {s.replace(/_/g, ' ')} ({cnt})
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {/* FAB */}
      <TouchableOpacity style={st.fab} onPress={() => navigation.navigate('NewServiceJob')}>
        <Text style={st.fabText}>+ New Job</Text>
      </TouchableOpacity>

      {loading
        ? <ActivityIndicator color={C.primary} style={{ marginTop: 40 }} size="large" />
        : (
          <FlatList
            data={filtered}
            keyExtractor={i => String(i.id)}
            renderItem={renderItem}
            contentContainerStyle={{ padding: 12, paddingBottom: 100 }}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={() => { setRefreshing(true); load(true); }}
                colors={[C.primary]}
                tintColor={C.primary}
              />
            }
            ListEmptyComponent={<Text style={st.empty}>No service jobs found</Text>}
          />
        )
      }
    </View>
  );
}

const st = StyleSheet.create({
  root:        { flex: 1, backgroundColor: C.bg },
  searchWrap:  { flexDirection: 'row', alignItems: 'center', backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border, paddingHorizontal: 12, paddingVertical: 8 },
  searchInput: { flex: 1, fontSize: 14, color: C.text, paddingVertical: 6 },
  clearBtn:    { padding: 4 },
  clearText:   { fontSize: 13, color: C.textMuted },
  filterScroll:{ flexGrow: 0, backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border },
  chip:        { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, backgroundColor: C.border },
  chipActive:  { backgroundColor: C.primary },
  chipText:    { fontSize: 11, fontWeight: '700', color: C.textMuted },
  chipActiveText: { color: '#fff' },
  fab:         { position: 'absolute', bottom: 20, right: 16, zIndex: 10, backgroundColor: C.primary, borderRadius: 28, paddingHorizontal: 20, paddingVertical: 12, elevation: 6, shadowColor: C.primary, shadowOpacity: 0.4, shadowRadius: 8 },
  fabText:     { color: '#fff', fontWeight: '700', fontSize: 14 },
  card:        { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 8 },
  cardTop:     { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 },
  code:        { fontSize: 14, fontWeight: '800', color: C.primary },
  badge:       { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  badgeText:   { fontSize: 10, fontWeight: '700' },
  customer:    { fontSize: 14, fontWeight: '600', color: C.text, marginBottom: 2 },
  item:        { fontSize: 12, color: C.textMuted, marginBottom: 2 },
  problem:     { fontSize: 12, color: C.textMuted, fontStyle: 'italic', marginBottom: 6 },
  dateRow:     { flexDirection: 'row', flexWrap: 'wrap', gap: 5, marginBottom: 8 },
  dateChip:    { flexDirection: 'row', alignItems: 'center', gap: 3, backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 6, paddingHorizontal: 7, paddingVertical: 3 },
  dateChipText:{ fontSize: 11, color: C.textMuted, fontWeight: '600' },
  cardBottom:  { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  amount:      { fontSize: 14, fontWeight: '800', color: C.text },
  reworkBadge: { position: 'absolute' as any, top: 10, right: 10, backgroundColor: C.dangerBg, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  reworkText:  { fontSize: 9, fontWeight: '800', color: C.danger },
  empty:       { textAlign: 'center', color: C.textMuted, marginTop: 40, fontSize: 14 },
});
