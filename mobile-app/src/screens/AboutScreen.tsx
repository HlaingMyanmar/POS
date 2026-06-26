import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { C } from '../theme';

export default function AboutScreen() {
  return (
    <ScrollView style={st.root} contentContainerStyle={{ padding: 24 }}>
      <View style={st.logoBox}>
        <View style={st.logo}>
          <Text style={st.logoText}>S</Text>
        </View>
        <Text style={st.appName}>SSPD Manager</Text>
        <Text style={st.version}>Version 1.0.0</Text>
      </View>

      <View style={st.card}>
        <Text style={st.sectionTitle}>ABOUT THIS APP</Text>
        <Text style={st.body}>
          SSPD Manager is a comprehensive shop management system for handling sales, inventory,
          service jobs, bookings, and customer management.
        </Text>
      </View>

      <View style={st.card}>
        <Text style={st.sectionTitle}>FEATURES</Text>
        {[
          '📦  Inventory & Product Management',
          '🧾  Sales & Voucher',
          '🔁  Sale Returns',
          '📅  Bookings',
          '🔧  Service Job Orders',
          '👥  Customer Management',
          '📡  Real-time WebSocket Updates',
        ].map(f => (
          <Text key={f} style={st.feature}>{f}</Text>
        ))}
      </View>

      <View style={st.card}>
        <Text style={st.sectionTitle}>DEVELOPED BY</Text>
        <Text style={st.body}>SSPD Development Team</Text>
      </View>

      <Text style={st.footer}>© 2026 SSPD Management System. All rights reserved.</Text>
    </ScrollView>
  );
}

const st = StyleSheet.create({
  root:         { flex: 1, backgroundColor: C.bg },
  logoBox:      { alignItems: 'center', marginBottom: 28 },
  logo:         { width: 72, height: 72, borderRadius: 18, backgroundColor: C.primary, justifyContent: 'center', alignItems: 'center', marginBottom: 12, elevation: 4 },
  logoText:     { fontSize: 36, fontWeight: '900', color: '#fff' },
  appName:      { fontSize: 22, fontWeight: '800', color: C.text, marginBottom: 4 },
  version:      { fontSize: 13, color: C.textMuted, fontWeight: '600' },
  card:         { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 16, marginBottom: 14 },
  sectionTitle: { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 10 },
  body:         { fontSize: 14, color: C.text, lineHeight: 22 },
  feature:      { fontSize: 14, color: C.text, paddingVertical: 5, borderBottomWidth: 1, borderBottomColor: C.border },
  footer:       { textAlign: 'center', color: C.textMuted, fontSize: 11, marginTop: 8, marginBottom: 20 },
});
