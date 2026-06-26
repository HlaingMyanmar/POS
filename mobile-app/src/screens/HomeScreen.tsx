import React, { useEffect, useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, ScrollView,
  ActivityIndicator, Platform, StatusBar as RNStatusBar,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { ApiResponse } from '../types';
import { C } from '../theme';
import DrawerMenu from '../components/DrawerMenu';
import { useWsTopic } from '../hooks/useWsTopic';

interface Stats {
  totalSalesCount?: number;
  todaySalesAmount?: number;
  todaySalesCount?: number;
  lowStockCount?: number;
  pendingServiceJobs?: number;
}

const HERO_TOP = Platform.OS === 'android' ? (RNStatusBar.currentHeight ?? 24) + 12 : 54;

const getGreeting = () => {
  const h = new Date().getHours();
  if (h < 12) return 'မင်္ဂလာနံနက်ခင်းပါ';
  if (h < 17) return 'မင်္ဂလာနေ့လည်ပိုင်းခင်းပါ';
  return 'မင်္ဂလာညနေခင်းပါ';
};

const fmtDate = () => {
  const d = new Date();
  const days   = ['တနင်္ဂနွေ','တနင်္လာ','အင်္ဂါ','ဗုဒ္ဓဟူး','ကြာသပတေး','သောကြာ','စနေ'];
  const months = ['ဇန်နဝါရီ','ဖေဖော်ဝါရီ','မတ်','ဧပြီ','မေ','ဇွန်','ဇူလိုင်','သြဂုတ်','စက်တင်ဘာ','အောက်တိုဘာ','နိုဝင်ဘာ','ဒီဇင်ဘာ'];
  return `${days[d.getDay()]}၊ ${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
};

export default function HomeScreen({ navigation }: any) {
  const { username, logout, hasPermission } = useAuth() as any;
  const [stats,      setStats]      = useState<Stats>({});
  const [loading,    setLoading]    = useState(true);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    navigation.setOptions({ headerShown: false });
  }, [navigation]);

  const loadStats = () => {
    setLoading(true);
    api.get<ApiResponse<any>>('/dashboard/stats')
      .then(r => setStats(r.data ?? {}))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadStats(); }, []);

  useWsTopic('/topic/sales',        () => loadStats());
  useWsTopic('/topic/service-jobs', () => loadStats());

  const statTiles: {
    label: string; value: string;
    icon: React.ComponentProps<typeof Ionicons>['name'];
    color: string; bg: string; tab?: string;
  }[] = [
    { label: 'ယနေ့ ရောင်းရငွေ',       value: (stats.todaySalesAmount ?? 0).toLocaleString() + ' Ks', icon: 'cash-outline',      color: C.primary, bg: C.primaryLight, tab: 'Sales'     },
    { label: 'ရောင်းချမှုအရေအတွက်',   value: (stats.todaySalesCount  ?? 0) + ' ခု',                 icon: 'receipt-outline',   color: C.success, bg: C.successBg,    tab: 'Sales'     },
    { label: 'ကုန်သိုလှောင် နည်းပါး', value: (stats.lowStockCount    ?? 0) + ' မျိုး',              icon: 'warning-outline',   color: C.warning, bg: C.warningBg,    tab: 'Inventory' },
    { label: 'ဆိုင်ခင်းအလုပ်',        value: String(stats.pendingServiceJobs ?? 0),                  icon: 'construct-outline', color: C.violet,  bg: C.violetBg,     tab: 'Service'   },
  ];

  type ActionItem = {
    label: string;
    icon: React.ComponentProps<typeof Ionicons>['name'];
    color: string; bg: string;
    onPress: () => void;
    perm?: string;
  };

  const actions: ActionItem[] = ([
    { label: 'ရောင်းချမှုအသစ်',        icon: 'add-circle',  color: '#fff', bg: C.primary, onPress: () => navigation.navigate('NewSale'),     perm: 'CAN_ACCESS_SALE_CREATE'        },
    { label: 'ကုန်ပစ္စည်းများ',         icon: 'cube',        color: '#fff', bg: '#0891B2', onPress: () => navigation.navigate('Inventory'),   perm: 'CAN_ACCESS_PRODUCT_READ'       },
    { label: 'Booking အသစ်',            icon: 'calendar',    color: '#fff', bg: C.violet,  onPress: () => navigation.navigate('Bookings'),    perm: 'CAN_ACCESS_BOOKING_CREATE'     },
    { label: 'Job အသစ်',               icon: 'construct',   color: '#fff', bg: '#059669', onPress: () => navigation.navigate('Service'),     perm: 'CAN_ACCESS_SERVICE_JOB_CREATE' },
    { label: 'ဝန်ထမ်းစွမ်းဆောင်ရည်',  icon: 'bar-chart',   color: '#fff', bg: '#7C3AED', onPress: () => navigation.navigate('StaffReport')                                       },
  ] as unknown as ActionItem[]).filter(a => !a.perm || !hasPermission || hasPermission(a.perm));

  const initial = (username?.[0] ?? 'U').toUpperCase();

  return (
    <>
      <RNStatusBar barStyle="light-content" backgroundColor={C.primary} />

      <ScrollView
        style={st.root}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 40 }}
      >
        {/* ── Hero Banner ── */}
        <View style={[st.hero, { paddingTop: HERO_TOP }]}>
          <View style={st.deco1} />
          <View style={st.deco2} />
          <View style={st.deco3} />

          <View style={st.heroTop}>
            <TouchableOpacity onPress={() => setDrawerOpen(true)} style={st.iconBtn} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Ionicons name="menu" size={26} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity onPress={loadStats} style={st.iconBtn} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Ionicons name="refresh-outline" size={22} color="rgba(255,255,255,0.8)" />
            </TouchableOpacity>
          </View>

          <View style={st.heroContent}>
            <View style={st.avatar}>
              <Text style={st.avatarText}>{initial}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={st.greetLabel}>{getGreeting()},</Text>
              <Text style={st.greetName}>{username ?? 'User'}</Text>
              <Text style={st.greetDate}>{fmtDate()}</Text>
            </View>
          </View>
        </View>

        <View style={st.body}>

          {/* Stats */}
          <View style={st.sectionRow}>
            <Text style={st.sectionTitle}>ယနေ့ ခြုံငုံသုံးသပ်ချက်</Text>
            {loading && <ActivityIndicator color={C.primary} size="small" />}
          </View>

          <View style={st.statsGrid}>
            {statTiles.map(t => (
              <TouchableOpacity
                key={t.label}
                style={st.statCard}
                onPress={() => t.tab && navigation.navigate(t.tab)}
                activeOpacity={0.75}
              >
                <View style={[st.statIconRing, { backgroundColor: t.bg }]}>
                  <Ionicons name={t.icon} size={20} color={t.color} />
                </View>
                <Text style={[st.statValue, { color: t.color }]}>{t.value}</Text>
                <Text style={st.statLabel}>{t.label}</Text>
              </TouchableOpacity>
            ))}
          </View>

          {/* Quick Actions */}
          <Text style={[st.sectionTitle, { marginTop: 8 }]}>အမြန် လုပ်ဆောင်ချက်များ</Text>
          <View style={st.actionsGrid}>
            {actions.map(a => (
              <TouchableOpacity
                key={a.label}
                style={st.actionCard}
                onPress={a.onPress}
                activeOpacity={0.8}
              >
                <View style={[st.actionIconBox, { backgroundColor: a.bg }]}>
                  <Ionicons name={a.icon} size={26} color={a.color} />
                </View>
                <Text style={st.actionLabel}>{a.label}</Text>
                <Ionicons name="chevron-forward" size={14} color={C.border} style={{ position: 'absolute', right: 12, top: '50%' as any }} />
              </TouchableOpacity>
            ))}
          </View>

        </View>
      </ScrollView>

      <DrawerMenu
        visible={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onNavigate={screen => navigation.navigate(screen)}
      />
    </>
  );
}

const st = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.bg },

  hero: {
    backgroundColor: C.primary,
    paddingHorizontal: 20,
    paddingBottom: 30,
    borderBottomLeftRadius: 28,
    borderBottomRightRadius: 28,
    overflow: 'hidden',
  },
  heroTop:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 },
  heroContent:{ flexDirection: 'row', alignItems: 'center', gap: 14 },
  iconBtn:    { padding: 4 },
  avatar:     { width: 50, height: 50, borderRadius: 25, backgroundColor: 'rgba(255,255,255,0.22)', justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: 'rgba(255,255,255,0.45)' },
  avatarText: { fontSize: 22, fontWeight: '800', color: '#fff' },
  greetLabel: { fontSize: 12, color: 'rgba(255,255,255,0.72)', fontWeight: '600' },
  greetName:  { fontSize: 21, fontWeight: '800', color: '#fff', marginTop: 1 },
  greetDate:  { fontSize: 11, color: 'rgba(255,255,255,0.58)', marginTop: 4 },
  deco1: { position: 'absolute', width: 140, height: 140, borderRadius: 70, backgroundColor: 'rgba(255,255,255,0.07)', top: -30, right: -30 },
  deco2: { position: 'absolute', width: 90,  height: 90,  borderRadius: 45, backgroundColor: 'rgba(255,255,255,0.05)', bottom: 0,  right: 50 },
  deco3: { position: 'absolute', width: 60,  height: 60,  borderRadius: 30, backgroundColor: 'rgba(255,255,255,0.06)', top: 20,    right: 90 },

  body: { paddingHorizontal: 16, paddingTop: 22 },

  sectionRow:  { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  sectionTitle:{ fontSize: 12, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.9 },

  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 22 },
  statCard: {
    width: '48%',
    backgroundColor: C.card,
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: C.border,
    elevation: 2,
    shadowColor: '#64748B',
    shadowOpacity: 0.07,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
  },
  statIconRing:{ width: 40, height: 40, borderRadius: 10, justifyContent: 'center', alignItems: 'center', marginBottom: 10 },
  statValue:   { fontSize: 16, fontWeight: '800', marginBottom: 4, letterSpacing: -0.3 },
  statLabel:   { fontSize: 11, fontWeight: '600', color: C.textMuted },

  actionsGrid: { gap: 8 },
  actionCard: {
    backgroundColor: C.card,
    borderRadius: 14,
    paddingVertical: 16,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    borderWidth: 1,
    borderColor: C.border,
    elevation: 1,
    shadowColor: '#64748B',
    shadowOpacity: 0.05,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 1 },
  },
  actionIconBox: { width: 46, height: 46, borderRadius: 12, justifyContent: 'center', alignItems: 'center' },
  actionLabel:   { fontSize: 14, fontWeight: '700', color: C.text, flex: 1 },
});
