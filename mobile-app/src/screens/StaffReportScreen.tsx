import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../api/client';
import { ApiResponse, StaffReportDTO } from '../types';
import { C } from '../theme';

function fmt(n?: number) {
  return (n ?? 0).toLocaleString();
}

function prevMonth(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y, m - 2, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function nextMonth(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y, m, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function nowYM() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

const MM_MONTHS = ['ဇန်နဝါရီ','ဖေဖော်ဝါရီ','မတ်','ဧပြီ','မေ','ဇွန်','ဇူလိုင်','သြဂုတ်','စက်တင်ဘာ','အောက်တိုဘာ','နိုဝင်ဘာ','ဒီဇင်ဘာ'];

function displayMonth(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  return `${MM_MONTHS[m - 1]} ${y}`;
}

const ROLE_COLOR: Record<string, { bg: string; text: string }> = {
  TECHNICIAN: { bg: C.violetBg,     text: C.violet   },
  Technician: { bg: C.violetBg,     text: C.violet   },
  CASHIER:    { bg: C.successBg,    text: C.success  },
  Cashier:    { bg: C.successBg,    text: C.success  },
  MANAGER:    { bg: C.primaryLight, text: C.primary  },
  Manager:    { bg: C.primaryLight, text: C.primary  },
  ADMIN:      { bg: C.warningBg,    text: C.warning  },
  Admin:      { bg: C.warningBg,    text: C.warning  },
};
const DEFAULT_ROLE_COLOR = { bg: C.border, text: C.textMuted };

const ROLE_MM: Record<string, string> = {
  TECHNICIAN: 'နည်းပညာဆရာ',
  Technician: 'နည်းပညာဆရာ',
  CASHIER:    'ငွေကောက်',
  Cashier:    'ငွေကောက်',
  MANAGER:    'မန်နေဂျာ',
  Manager:    'မန်နေဂျာ',
  ADMIN:      'Admin',
  Admin:      'Admin',
};

export default function StaffReportScreen() {
  const [month,     setMonth]     = useState(nowYM());
  const [reports,   setReports]   = useState<StaffReportDTO[]>([]);
  const [loading,   setLoading]   = useState(true);
  const [refreshing,setRefreshing]= useState(false);

  const load = useCallback(async (m: string, quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<StaffReportDTO[]>>(`/reports/staff?month=${m}`);
      setReports(res.data ?? []);
    } catch {}
    setLoading(false);
    setRefreshing(false);
  }, []);

  useEffect(() => { load(month); }, [month]);

  const canNext = month < nowYM();

  const totalSalesAmt   = reports.reduce((s, r) => s + (r.salesAmount ?? 0), 0);
  const totalServiceAmt = reports.reduce((s, r) => s + (r.serviceJobsAmount ?? 0), 0);

  return (
    <View style={st.root}>
      {/* Month navigator */}
      <View style={st.monthBar}>
        <TouchableOpacity onPress={() => setMonth(prevMonth(month))} style={st.arrowBtn}>
          <Ionicons name="chevron-back" size={20} color={C.primary} />
        </TouchableOpacity>
        <Text style={st.monthLabel}>{displayMonth(month)}</Text>
        <TouchableOpacity onPress={() => canNext && setMonth(nextMonth(month))} style={[st.arrowBtn, !canNext && { opacity: 0.25 }]} disabled={!canNext}>
          <Ionicons name="chevron-forward" size={20} color={C.primary} />
        </TouchableOpacity>
      </View>

      {/* Summary totals */}
      <View style={st.summaryRow}>
        <View style={[st.summaryCard, { borderColor: C.primary }]}>
          <Text style={st.summaryLabel}>စုစုပေါင်း ရောင်းချမှု</Text>
          <Text style={[st.summaryValue, { color: C.primary }]}>{fmt(totalSalesAmt)} Ks</Text>
        </View>
        <View style={[st.summaryCard, { borderColor: C.violet }]}>
          <Text style={st.summaryLabel}>ဝန်ဆောင်မှု ဝင်ငွေ</Text>
          <Text style={[st.summaryValue, { color: C.violet }]}>{fmt(totalServiceAmt)} Ks</Text>
        </View>
      </View>

      {loading
        ? <ActivityIndicator color={C.primary} style={{ marginTop: 60 }} size="large" />
        : (
          <ScrollView
            contentContainerStyle={{ padding: 14, paddingBottom: 40 }}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(month, true); }} />}
          >
            {reports.length === 0
              ? <Text style={st.empty}>ဤလအတွက် ဒေတာမရှိပါ</Text>
              : reports.map(r => <StaffCard key={r.staffId} report={r} />)
            }
          </ScrollView>
        )
      }
    </View>
  );
}

function StaffCard({ report: r }: { report: StaffReportDTO }) {
  const roleKey  = r.staffRole ?? '';
  const roleCol  = ROLE_COLOR[roleKey] ?? DEFAULT_ROLE_COLOR;
  const roleMM   = ROLE_MM[roleKey] ?? (roleKey || 'ဝန်ထမ်း');
  const hasSales = r.salesCount > 0;
  const hasJobs  = r.serviceJobsCount > 0;

  return (
    <View style={st.card}>
      {/* Staff header */}
      <View style={st.cardHeader}>
        <View style={st.staffAvatar}>
          <Text style={st.staffAvatarTxt}>{(r.staffName || '?')[0].toUpperCase()}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={st.staffName}>{r.staffName}</Text>
          <View style={[st.roleBadge, { backgroundColor: roleCol.bg }]}>
            <Text style={[st.roleText, { color: roleCol.text }]}>{roleMM}</Text>
          </View>
        </View>
      </View>

      <View style={st.divider} />

      {/* Sales section */}
      {hasSales && (
        <View style={st.section}>
          <View style={st.sectionHeader}>
            <Ionicons name="receipt-outline" size={14} color={C.primary} />
            <Text style={[st.sectionTitle, { color: C.primary }]}>ရောင်းချမှု</Text>
          </View>
          <View style={st.statsRow}>
            <Stat label="အရေအတွက်" value={String(r.salesCount)} />
            <Stat label="ရောင်းရငွေ" value={`${fmt(r.salesAmount)} Ks`} bold />
          </View>
        </View>
      )}

      {/* Service section */}
      {hasJobs && (
        <View style={[st.section, hasSales && { marginTop: 10 }]}>
          <View style={st.sectionHeader}>
            <Ionicons name="construct-outline" size={14} color={C.violet} />
            <Text style={[st.sectionTitle, { color: C.violet }]}>ဝန်ဆောင်မှု Jobs</Text>
          </View>
          <View style={st.statsRow}>
            <Stat label="စုစုပေါင်း"    value={String(r.serviceJobsCount)} />
            <Stat label="ပြီးဆုံး"       value={String(r.completedJobsCount)} color={C.success} />
            <Stat label="လုပ်ဆဲ"         value={String(r.inProgressJobsCount ?? 0)} color={C.violet} />
          </View>
          <View style={[st.statsRow, { marginTop: 6 }]}>
            <Stat label="ပယ်ဖျက်"       value={String(r.cancelledJobsCount ?? 0)} color={C.danger} />
            <Stat label="ပြန်ပြင်"       value={String(r.reworkJobsCount ?? 0)} color={C.warning} />
            <Stat label="ဝင်ငွေ"         value={`${fmt(r.serviceJobsAmount)} Ks`} bold />
          </View>
          {/* Completion rate bar */}
          <View style={st.rateRow}>
            <Text style={st.rateLabel}>ပြီးနှုန်း</Text>
            <View style={st.rateBarBg}>
              <View style={[st.rateBarFill, { width: `${Math.min(r.completionRate ?? 0, 100)}%` as any }]} />
            </View>
            <Text style={st.ratePct}>{(r.completionRate ?? 0).toFixed(1)}%</Text>
          </View>
        </View>
      )}

      {!hasSales && !hasJobs && (
        <Text style={st.noActivity}>ဤလတွင် လုပ်ဆောင်မှုမရှိပါ</Text>
      )}
    </View>
  );
}

function Stat({ label, value, bold, color }: { label: string; value: string; bold?: boolean; color?: string }) {
  return (
    <View style={st.statItem}>
      <Text style={st.statLabel}>{label}</Text>
      <Text style={[st.statValue, bold && { color: C.text, fontWeight: '800' }, color ? { color } : {}]}>{value}</Text>
    </View>
  );
}

const st = StyleSheet.create({
  root:        { flex: 1, backgroundColor: C.bg },

  monthBar:    { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: C.card, paddingHorizontal: 12, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.border },
  arrowBtn:    { padding: 6 },
  monthLabel:  { fontSize: 15, fontWeight: '800', color: C.text },

  summaryRow:  { flexDirection: 'row', gap: 10, padding: 12, paddingBottom: 2 },
  summaryCard: { flex: 1, backgroundColor: C.card, borderRadius: 10, borderWidth: 1.5, padding: 12, alignItems: 'center' },
  summaryLabel:{ fontSize: 10, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', marginBottom: 4 },
  summaryValue:{ fontSize: 15, fontWeight: '900' },

  empty:       { textAlign: 'center', color: C.textMuted, marginTop: 60, fontSize: 14 },

  card:        { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  cardHeader:  { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 10 },
  staffAvatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: C.primary, justifyContent: 'center', alignItems: 'center' },
  staffAvatarTxt: { color: '#fff', fontSize: 16, fontWeight: '800' },
  staffName:   { fontSize: 15, fontWeight: '800', color: C.text, marginBottom: 4 },
  roleBadge:   { alignSelf: 'flex-start', paddingHorizontal: 8, paddingVertical: 2, borderRadius: 6 },
  roleText:    { fontSize: 10, fontWeight: '800' },
  divider:     { height: 1, backgroundColor: C.border, marginBottom: 10 },

  section:     {},
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: 5, marginBottom: 8 },
  sectionTitle:  { fontSize: 11, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.5 },
  statsRow:    { flexDirection: 'row', gap: 8 },
  statItem:    { flex: 1, backgroundColor: C.bg, borderRadius: 8, padding: 8, alignItems: 'center' },
  statLabel:   { fontSize: 10, color: C.textMuted, fontWeight: '600', marginBottom: 3 },
  statValue:   { fontSize: 13, fontWeight: '700', color: C.textMuted },

  noActivity:  { fontSize: 12, color: C.textMuted, textAlign: 'center', paddingVertical: 6 },

  rateRow:     { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 8 },
  rateLabel:   { fontSize: 10, color: C.textMuted, fontWeight: '700', width: 50 },
  rateBarBg:   { flex: 1, height: 6, backgroundColor: C.border, borderRadius: 3, overflow: 'hidden' },
  rateBarFill: { height: '100%', backgroundColor: C.success, borderRadius: 3 },
  ratePct:     { fontSize: 11, fontWeight: '800', color: C.success, width: 40, textAlign: 'right' },
});
