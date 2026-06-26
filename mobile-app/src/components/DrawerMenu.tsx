import React, { useRef, useEffect } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, Modal,
  Animated, Dimensions, TouchableWithoutFeedback, Image, Platform,
  StatusBar as RNStatusBar,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { C } from '../theme';
import { useAuth } from '../context/AuthContext';

const LOGO   = require('../assets/logo.png');
const DRAWER_W = Dimensions.get('window').width * 0.76;
const HEADER_TOP = Platform.OS === 'android' ? (RNStatusBar.currentHeight ?? 24) : 0;

interface Props {
  visible: boolean;
  onClose: () => void;
  onNavigate: (screen: string) => void;
}

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const MENU_ITEMS: { icon: IoniconName; label: string; screen: string }[] = [
  { icon: 'wallet-outline',             label: 'ကုန်ကျစရိတ်များ',          screen: 'Expense'        },
  { icon: 'chatbubbles-outline',        label: 'အဖွဲ့ Chat',               screen: 'Chat'           },
  { icon: 'bar-chart-outline',          label: 'ဝန်ထမ်းစွမ်းဆောင်ရည်',   screen: 'StaffReport'    },
  { icon: 'trophy-outline',             label: 'ရောင်းအကောင်းဆုံးစာရင်း', screen: 'SalesRanking'   },
  { icon: 'shield-checkmark-outline',   label: 'Audit မှတ်တမ်းများ',       screen: 'AuditLog'       },
  { icon: 'person-circle-outline',      label: 'အကောင့်သတ်မှတ်ချက်',      screen: 'AccountSettings'},
  { icon: 'information-circle-outline', label: 'အကြောင်းအရာ',              screen: 'About'          },
];

export default function DrawerMenu({ visible, onClose, onNavigate }: Props) {
  const { username, name, logout } = useAuth() as any;
  const slideAnim = useRef(new Animated.Value(-DRAWER_W)).current;
  const bgAnim    = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.spring(slideAnim, { toValue: 0, useNativeDriver: true, tension: 80, friction: 10 }),
        Animated.timing(bgAnim, { toValue: 1, duration: 200, useNativeDriver: true }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(slideAnim, { toValue: -DRAWER_W, duration: 180, useNativeDriver: true }),
        Animated.timing(bgAnim, { toValue: 0, duration: 180, useNativeDriver: true }),
      ]).start();
    }
  }, [visible]);

  const bgOpacity = bgAnim.interpolate({ inputRange: [0, 1], outputRange: [0, 0.5] });
  const initial   = (username?.[0] ?? 'U').toUpperCase();

  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onClose}>
      <View style={{ flex: 1 }}>
        <TouchableWithoutFeedback onPress={onClose}>
          <Animated.View style={[st.overlay, { opacity: bgOpacity }]} />
        </TouchableWithoutFeedback>

        <Animated.View style={[st.drawer, { transform: [{ translateX: slideAnim }] }]}>
          {/* Header */}
          <View style={[st.header, { paddingTop: HEADER_TOP + 24 }]}>
            <View style={st.deco1} />
            <View style={st.deco2} />

            <Image source={LOGO} style={st.logo} resizeMode="contain" />
            <Text style={st.appName}>SSPD Manager</Text>
            <Text style={st.appSub}>IT Solution Center</Text>

            <View style={st.profileRow}>
              <View style={st.avatarCircle}>
                <Text style={st.avatarText}>{initial}</Text>
              </View>
              <View>
                <Text style={st.profileName}>{name || username || 'User'}</Text>
                <Text style={st.profileRole}>{username}</Text>
              </View>
            </View>
          </View>

          {/* Menu items */}
          <View style={{ flex: 1, paddingTop: 8 }}>
            {MENU_ITEMS.map(item => (
              <TouchableOpacity
                key={item.screen}
                style={st.item}
                onPress={() => { onNavigate(item.screen); onClose(); }}
                activeOpacity={0.7}
              >
                <View style={st.itemIconBox}>
                  <Ionicons name={item.icon} size={20} color={C.primary} />
                </View>
                <Text style={st.itemLabel}>{item.label}</Text>
                <Ionicons name="chevron-forward" size={16} color={C.border} />
              </TouchableOpacity>
            ))}
          </View>

          {/* Footer */}
          <View style={st.footer}>
            <View style={st.divider} />
            <TouchableOpacity style={st.logoutRow} onPress={() => { onClose(); logout?.(); }} activeOpacity={0.75}>
              <View style={[st.itemIconBox, { backgroundColor: '#FFF1F2' }]}>
                <Ionicons name="log-out-outline" size={20} color={C.danger} />
              </View>
              <Text style={[st.itemLabel, { color: C.danger }]}>ထွက်ရန်</Text>
            </TouchableOpacity>
            <Text style={st.footerNote}>SSPD Management System</Text>
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
}

const st = StyleSheet.create({
  overlay: { ...StyleSheet.absoluteFillObject, backgroundColor: '#000' },
  drawer: {
    position: 'absolute', top: 0, left: 0, bottom: 0, width: DRAWER_W,
    backgroundColor: C.card,
    elevation: 20,
    shadowColor: '#000', shadowOpacity: 0.25, shadowRadius: 20,
  },

  // Header
  header: {
    backgroundColor: C.primary,
    paddingHorizontal: 20,
    paddingBottom: 24,
    overflow: 'hidden',
  },
  deco1: { position: 'absolute', width: 130, height: 130, borderRadius: 65, backgroundColor: 'rgba(255,255,255,0.07)', top: -30, right: -20 },
  deco2: { position: 'absolute', width: 80,  height: 80,  borderRadius: 40, backgroundColor: 'rgba(255,255,255,0.05)', bottom: -20, right: 60 },
  logo:     { width: 42, height: 42, borderRadius: 10, backgroundColor: '#fff', marginBottom: 10 },
  appName:  { fontSize: 17, fontWeight: '800', color: '#fff' },
  appSub:   { fontSize: 11, color: 'rgba(255,255,255,0.65)', marginTop: 2, marginBottom: 18 },
  profileRow:   { flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 4 },
  avatarCircle: { width: 38, height: 38, borderRadius: 19, backgroundColor: 'rgba(255,255,255,0.2)', justifyContent: 'center', alignItems: 'center', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.4)' },
  avatarText:   { fontSize: 16, fontWeight: '800', color: '#fff' },
  profileName:  { fontSize: 14, fontWeight: '700', color: '#fff' },
  profileRole:  { fontSize: 11, color: 'rgba(255,255,255,0.6)', marginTop: 1 },

  // Items
  item: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    paddingHorizontal: 16, paddingVertical: 13,
    marginHorizontal: 8, borderRadius: 10, marginBottom: 2,
  },
  itemIconBox: {
    width: 36, height: 36, borderRadius: 9,
    backgroundColor: C.primaryLight,
    justifyContent: 'center', alignItems: 'center',
  },
  itemLabel: { flex: 1, fontSize: 14, fontWeight: '600', color: C.text },

  // Footer
  footer:    { paddingBottom: 24 },
  divider:   { height: 1, backgroundColor: C.border, marginHorizontal: 16, marginBottom: 8 },
  logoutRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 13, marginHorizontal: 8, borderRadius: 10 },
  footerNote:{ textAlign: 'center', fontSize: 10, color: C.textMuted, marginTop: 8 },
});
