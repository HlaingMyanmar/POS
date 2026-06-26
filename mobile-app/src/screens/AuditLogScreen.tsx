import React, { useState, useCallback, useEffect } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, ActivityIndicator,
  StyleSheet, TextInput, RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { C } from '../theme';
import { api } from '../api/client';

interface AuditLog {
  id: number;
  actor: string;
  actorRole?: string;
  action: string;
  module: string;
  resourceId?: string;
  description?: string;
  ipAddress?: string;
  deviceType?: string;
  createdAt: string;
}

const ACTION_COLORS: Record<string, { bg: string; text: string }> = {
  LOGIN:  { bg: '#EFF6FF', text: '#1D4ED8' },
  CREATE: { bg: '#ECFDF5', text: '#065F46' },
  UPDATE: { bg: '#FFFBEB', text: '#92400E' },
  DELETE: { bg: '#FFF1F2', text: '#9F1239' },
  ACTION: { bg: '#F5F3FF', text: '#5B21B6' },
};

const ACTION_ICONS: Record<string, string> = {
  LOGIN:  'log-in-outline',
  CREATE: 'add-circle-outline',
  UPDATE: 'pencil-outline',
  DELETE: 'trash-outline',
  ACTION: 'flash-outline',
};

const fmtDate = (d: string) => {
  try {
    const dt = new Date(d);
    return dt.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
      + '  ' + dt.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  } catch { return d; }
};

const ALL_ACTIONS = ['', 'LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'ACTION'];

export default function AuditLogScreen() {
  const [logs, setLogs]           = useState<AuditLog[]>([]);
  const [loading, setLoading]     = useState(true);
  const [refreshing, setRefresh]  = useState(false);
  const [actor, setActor]         = useState('');
  const [action, setAction]       = useState('');
  const [page, setPage]           = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loadingMore, setLoadingMore] = useState(false);

  const fetchLogs = useCallback(async (pg = 0, reset = true) => {
    if (pg === 0) reset ? setLoading(true) : setRefresh(true);
    else setLoadingMore(true);
    try {
      const params = new URLSearchParams({ page: String(pg), size: '30', actor, action });
      const res = await api.get<any>(`/audit-logs?${params}`);
      const data = res.data;
      if (reset || pg === 0) setLogs(data.content ?? []);
      else setLogs(prev => [...prev, ...(data.content ?? [])]);
      setTotalPages(data.totalPages ?? 1);
      setPage(pg);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
      setRefresh(false);
      setLoadingMore(false);
    }
  }, [actor, action]);

  useEffect(() => { void fetchLogs(0, true); }, [fetchLogs]);

  const loadMore = () => {
    if (!loadingMore && page < totalPages - 1) fetchLogs(page + 1, false);
  };

  const renderItem = ({ item }: { item: AuditLog }) => {
    const colors = ACTION_COLORS[item.action] ?? { bg: '#F8FAFC', text: '#475569' };
    const iconName = (ACTION_ICONS[item.action] ?? 'ellipse-outline') as any;
    return (
      <View style={st.card}>
        <View style={st.cardHeader}>
          <View style={[st.actionBadge, { backgroundColor: colors.bg }]}>
            <Ionicons name={iconName} size={12} color={colors.text} />
            <Text style={[st.actionText, { color: colors.text }]}>{item.action}</Text>
          </View>
          <Text style={st.module}>{item.module}</Text>
          {item.resourceId ? <Text style={st.resId}>{item.resourceId}</Text> : null}
        </View>

        <View style={st.cardBody}>
          <View style={st.avatarRow}>
            <View style={st.avatar}>
              <Text style={st.avatarText}>{(item.actor || 'U')[0].toUpperCase()}</Text>
            </View>
            <View>
              <Text style={st.actor}>{item.actor}</Text>
              {item.actorRole ? <Text style={st.role}>{item.actorRole}</Text> : null}
            </View>
          </View>
          <View style={st.meta}>
            <View style={st.metaRow}>
              <Ionicons name="time-outline" size={11} color={C.textMuted} />
              <Text style={st.metaText}>{fmtDate(item.createdAt)}</Text>
            </View>
            {item.ipAddress ? (
              <View style={st.metaRow}>
                <Ionicons name="globe-outline" size={11} color={C.textMuted} />
                <Text style={st.metaText}>{item.ipAddress}</Text>
              </View>
            ) : null}
            {item.deviceType ? (
              <View style={st.metaRow}>
                <Ionicons name={item.deviceType === 'MOBILE' ? 'phone-portrait-outline' : 'desktop-outline'} size={11} color={C.textMuted} />
                <Text style={st.metaText}>{item.deviceType}</Text>
              </View>
            ) : null}
          </View>
        </View>
      </View>
    );
  };

  return (
    <View style={st.container}>
      {/* Search */}
      <View style={st.filterBar}>
        <View style={st.searchBox}>
          <Ionicons name="search-outline" size={15} color={C.textMuted} />
          <TextInput
            style={st.searchInput}
            placeholder="Search by username..."
            placeholderTextColor={C.textMuted}
            value={actor}
            onChangeText={setActor}
          />
          {actor ? (
            <TouchableOpacity onPress={() => setActor('')}>
              <Ionicons name="close-circle" size={15} color={C.textMuted} />
            </TouchableOpacity>
          ) : null}
        </View>
      </View>

      {/* Action filter pills */}
      <View style={st.pillRow}>
        {ALL_ACTIONS.map(a => (
          <TouchableOpacity
            key={a}
            onPress={() => setAction(a)}
            style={[st.pill, action === a && st.pillActive]}
          >
            <Text style={[st.pillText, action === a && st.pillTextActive]}>
              {a === '' ? 'All' : a}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {loading ? (
        <View style={st.center}>
          <ActivityIndicator size="large" color={C.primary} />
        </View>
      ) : (
        <FlatList
          data={logs}
          keyExtractor={i => String(i.id)}
          renderItem={renderItem}
          contentContainerStyle={{ paddingHorizontal: 12, paddingBottom: 32 }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => fetchLogs(0, true)} colors={[C.primary]} />}
          onEndReached={loadMore}
          onEndReachedThreshold={0.3}
          ListFooterComponent={loadingMore ? <ActivityIndicator size="small" color={C.primary} style={{ margin: 16 }} /> : null}
          ListEmptyComponent={
            <View style={st.center}>
              <Ionicons name="shield-outline" size={48} color={C.border} />
              <Text style={{ color: C.textMuted, marginTop: 12, fontSize: 14 }}>No audit records found</Text>
            </View>
          }
        />
      )}
    </View>
  );
}

const st = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.background },
  filterBar: { paddingHorizontal: 12, paddingTop: 12, paddingBottom: 6 },
  searchBox: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    backgroundColor: C.card, borderWidth: 1, borderColor: C.border,
    borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8,
  },
  searchInput: { flex: 1, fontSize: 13, color: C.text },
  pillRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, paddingHorizontal: 12, paddingBottom: 8 },
  pill: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 20, borderWidth: 1, borderColor: C.border, backgroundColor: C.card },
  pillActive: { backgroundColor: C.primary, borderColor: C.primary },
  pillText: { fontSize: 11, fontWeight: '600', color: C.textMuted },
  pillTextActive: { color: '#fff' },
  card: {
    backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border,
    marginBottom: 8, overflow: 'hidden',
  },
  cardHeader: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    paddingHorizontal: 12, paddingVertical: 8,
    backgroundColor: '#F8FAFC', borderBottomWidth: 1, borderBottomColor: C.border,
  },
  actionBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 20 },
  actionText: { fontSize: 10, fontWeight: '700' },
  module: { fontSize: 12, fontWeight: '700', color: C.text, flex: 1 },
  resId: { fontSize: 11, color: C.primary, fontFamily: 'monospace' },
  cardBody: { padding: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  avatarRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  avatar: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#EEF2FF', justifyContent: 'center', alignItems: 'center' },
  avatarText: { fontSize: 13, fontWeight: '800', color: C.primary },
  actor: { fontSize: 13, fontWeight: '700', color: C.text },
  role: { fontSize: 10, color: C.textMuted, fontWeight: '600', textTransform: 'uppercase', marginTop: 1 },
  meta: { alignItems: 'flex-end', gap: 3 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  metaText: { fontSize: 10, color: C.textMuted },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingTop: 60 },
});
