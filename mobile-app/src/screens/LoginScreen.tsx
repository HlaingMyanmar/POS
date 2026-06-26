import React, { useState, useEffect } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  KeyboardAvoidingView, Platform, ScrollView, ActivityIndicator,
  Modal, StatusBar as RNStatusBar,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../context/AuthContext';
import { loadServerUrl, saveServerUrl, setServerUrl } from '../api/client';
import { C } from '../theme';

export default function LoginScreen() {
  const { login } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  // Server IP settings
  const [savedServer,  setSavedServer]  = useState('');
  const [settingOpen,  setSettingOpen]  = useState(false);
  const [serverInput,  setServerInput]  = useState('');
  const [serverSaved,  setServerSaved]  = useState(false);

  // Load saved server URL on startup
  useEffect(() => {
    loadServerUrl().then(url => {
      if (url) {
        setSavedServer(url);
        setServerInput(url);
        setServerUrl(url);
      } else {
        // First time — open settings automatically
        setSettingOpen(true);
      }
    });
  }, []);

  const handleSaveServer = () => {
    const trimmed = serverInput.trim().replace(/\/+$/, '');
    if (!trimmed) return;
    saveServerUrl(trimmed);
    setServerUrl(trimmed);
    setSavedServer(trimmed);
    setServerSaved(true);
    setSettingOpen(false);
    setTimeout(() => setServerSaved(false), 2000);
  };

  const handleLogin = async () => {
    if (!savedServer) {
      setError('Server IP မသတ်မှတ်ရသေးပါ။ ⚙️ ကိုနှိပ်၍ ထည့်ပါ။');
      return;
    }
    if (!username.trim() || !password.trim()) {
      setError('အသုံးပြုသူနာမည်နှင့် စကားဝှက် ဖြည့်ပါ');
      return;
    }
    setLoading(true);
    setError('');
    const err = await login(savedServer, username, password);
    if (err) setError(err);
    setLoading(false);
  };

  const serverHost = savedServer
    ? savedServer.replace(/^https?:\/\//, '').split(':')[0]
    : null;

  return (
    <KeyboardAvoidingView style={st.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <RNStatusBar barStyle="light-content" backgroundColor={C.primary} />
      <ScrollView contentContainerStyle={st.scroll} keyboardShouldPersistTaps="handled">

        {/* Settings button — top right */}
        <TouchableOpacity style={st.settingBtn} onPress={() => setSettingOpen(true)} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
          <Ionicons name="settings-outline" size={22} color="rgba(255,255,255,0.85)" />
        </TouchableOpacity>

        {/* Logo */}
        <View style={st.logoWrap}>
          <View style={st.logo}>
            <Text style={st.logoText}>S</Text>
          </View>
          <Text style={st.appName}>SSPD Manager</Text>
          <Text style={st.subtitle}>ကုန်ပစ္စည်းနှင့် ရောင်းချမှုစနစ်</Text>

          {/* Server status chip */}
          <View style={[st.serverChip, savedServer ? st.serverChipOk : st.serverChipWarn]}>
            <Ionicons
              name={savedServer ? 'wifi' : 'wifi-outline'}
              size={12}
              color={savedServer ? '#fff' : 'rgba(255,255,255,0.7)'}
            />
            <Text style={[st.serverChipTxt, !savedServer && { opacity: 0.7 }]}>
              {savedServer ? serverHost : 'Server IP မသတ်မှတ်ရသေး'}
            </Text>
          </View>
        </View>

        {/* Login card */}
        <View style={st.card}>
          <Text style={st.cardTitle}>အကောင့်ဝင်ရောက်မည်</Text>

          <Text style={st.fieldLabel}>အသုံးပြုသူနာမည်</Text>
          <View style={st.inputWrap}>
            <Ionicons name="person-outline" size={17} color={C.textMuted} style={st.inputIcon} />
            <TextInput
              style={st.input}
              value={username}
              onChangeText={setUsername}
              placeholder="Username"
              placeholderTextColor={C.textMuted}
              autoCapitalize="none"
            />
          </View>

          <Text style={st.fieldLabel}>စကားဝှက်</Text>
          <View style={st.inputWrap}>
            <Ionicons name="lock-closed-outline" size={17} color={C.textMuted} style={st.inputIcon} />
            <TextInput
              style={st.input}
              value={password}
              onChangeText={setPassword}
              placeholder="Password"
              placeholderTextColor={C.textMuted}
              secureTextEntry
            />
          </View>

          {error ? <Text style={st.error}>{error}</Text> : null}

          <TouchableOpacity
            style={[st.btn, (loading || !savedServer) && { opacity: 0.65 }]}
            onPress={handleLogin}
            disabled={loading || !savedServer}
          >
            {loading
              ? <ActivityIndicator color="#fff" />
              : <Text style={st.btnText}>ဝင်ရောက်မည်</Text>
            }
          </TouchableOpacity>

          {!savedServer && (
            <TouchableOpacity style={st.setupHint} onPress={() => setSettingOpen(true)}>
              <Ionicons name="settings-outline" size={14} color={C.primary} />
              <Text style={st.setupHintTxt}>Server IP ထည့်ရန် နှိပ်ပါ</Text>
            </TouchableOpacity>
          )}
        </View>

        <Text style={st.footNote}>SSPD IT Solution Center</Text>
      </ScrollView>

      {/* ── Server Settings Modal ── */}
      <Modal visible={settingOpen} transparent animationType="slide" onRequestClose={() => savedServer && setSettingOpen(false)}>
        <View style={st.modalOverlay}>
          <View style={st.modalCard}>
            {/* Header */}
            <View style={st.modalHeader}>
              <View style={st.modalIconBox}>
                <Ionicons name="wifi" size={20} color={C.primary} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={st.modalTitle}>Server IP သတ်မှတ်ရန်</Text>
                <Text style={st.modalSub}>တူညီသော network ရှိ server IP ထည့်ပါ</Text>
              </View>
              {savedServer ? (
                <TouchableOpacity onPress={() => setSettingOpen(false)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                  <Ionicons name="close" size={22} color={C.textMuted} />
                </TouchableOpacity>
              ) : null}
            </View>

            {/* Instruction */}
            <View style={st.infoBox}>
              <Ionicons name="information-circle-outline" size={15} color={C.primary} />
              <Text style={st.infoTxt}>
                Server နှင့် phone တူညီသော Wi-Fi / Network ထဲမှာ ရှိရပါမည်။{'\n'}
                ဥပမာ: <Text style={{ fontWeight: '800' }}>http://192.168.1.100:8080</Text>
              </Text>
            </View>

            <Text style={st.fieldLabel}>Server Address</Text>
            <TextInput
              style={st.modalInput}
              value={serverInput}
              onChangeText={setServerInput}
              placeholder="http://192.168.x.x:8080"
              placeholderTextColor={C.textMuted}
              autoCapitalize="none"
              keyboardType="url"
              autoFocus={!savedServer}
            />

            {serverSaved && (
              <View style={st.savedBadge}>
                <Ionicons name="checkmark-circle" size={15} color={C.success} />
                <Text style={st.savedTxt}>သိမ်းပြီးပါပြီ</Text>
              </View>
            )}

            <TouchableOpacity
              style={[st.btn, { marginTop: 16 }]}
              onPress={handleSaveServer}
              disabled={!serverInput.trim()}
            >
              <Ionicons name="save-outline" size={16} color="#fff" style={{ marginRight: 6 }} />
              <Text style={st.btnText}>သိမ်းရန်</Text>
            </TouchableOpacity>

            <Text style={st.modalNote}>
              တစ်ကြိမ်သိမ်းလိုက်သည်နှင့် နောက်တစ်ကြိမ် ထပ်ထည့်စရာမလိုတော့ပါ
            </Text>
          </View>
        </View>
      </Modal>
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  root:    { flex: 1, backgroundColor: C.primary },
  scroll:  { flexGrow: 1, justifyContent: 'center', padding: 24, paddingTop: 56 },

  settingBtn: {
    position: 'absolute', top: 16, right: 24, zIndex: 10,
    padding: 6, borderRadius: 10,
    backgroundColor: 'rgba(255,255,255,0.15)',
  },

  // Logo
  logoWrap:  { alignItems: 'center', marginBottom: 28 },
  logo:      { width: 72, height: 72, borderRadius: 20, backgroundColor: 'rgba(255,255,255,0.2)', justifyContent: 'center', alignItems: 'center', marginBottom: 12 },
  logoText:  { fontSize: 32, fontWeight: '800', color: '#fff' },
  appName:   { fontSize: 22, fontWeight: '800', color: '#fff', marginBottom: 4 },
  subtitle:  { fontSize: 12, color: 'rgba(255,255,255,0.7)', fontWeight: '600', marginBottom: 12 },

  serverChip:    { flexDirection: 'row', alignItems: 'center', gap: 5, paddingHorizontal: 12, paddingVertical: 5, borderRadius: 20, borderWidth: 1, borderColor: 'rgba(255,255,255,0.3)' },
  serverChipOk:  { backgroundColor: 'rgba(255,255,255,0.18)' },
  serverChipWarn:{ backgroundColor: 'rgba(255,255,255,0.08)' },
  serverChipTxt: { fontSize: 11, color: '#fff', fontWeight: '700' },

  // Card
  card:      { backgroundColor: '#fff', borderRadius: 22, padding: 24, elevation: 8, shadowColor: '#000', shadowOpacity: 0.12, shadowRadius: 16 },
  cardTitle: { fontSize: 18, fontWeight: '800', color: C.text, marginBottom: 18, textAlign: 'center' },
  fieldLabel:{ fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 6, marginTop: 14 },
  inputWrap: { flexDirection: 'row', alignItems: 'center', backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 12 },
  inputIcon: { marginLeft: 12 },
  input:     { flex: 1, paddingHorizontal: 10, paddingVertical: 12, fontSize: 14, color: C.text },
  error:     { color: C.danger, fontSize: 12, marginTop: 10, textAlign: 'center', fontWeight: '600' },
  btn:       { backgroundColor: C.primary, borderRadius: 12, paddingVertical: 14, alignItems: 'center', marginTop: 20, flexDirection: 'row', justifyContent: 'center', gap: 6 },
  btnText:   { color: '#fff', fontWeight: '700', fontSize: 15 },

  setupHint: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 5, marginTop: 14 },
  setupHintTxt: { fontSize: 12, color: C.primary, fontWeight: '700' },

  footNote: { textAlign: 'center', color: 'rgba(255,255,255,0.45)', fontSize: 11, marginTop: 24, fontWeight: '600' },

  // Modal
  modalOverlay:{ flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  modalCard:   { backgroundColor: '#fff', borderTopLeftRadius: 26, borderTopRightRadius: 26, padding: 24, paddingBottom: 36 },
  modalHeader: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 16 },
  modalIconBox:{ width: 40, height: 40, borderRadius: 10, backgroundColor: C.primaryLight, justifyContent: 'center', alignItems: 'center' },
  modalTitle:  { fontSize: 16, fontWeight: '800', color: C.text },
  modalSub:    { fontSize: 11, color: C.textMuted, marginTop: 2 },

  infoBox:  { flexDirection: 'row', alignItems: 'flex-start', gap: 8, backgroundColor: C.primaryLight, borderRadius: 10, padding: 12, marginBottom: 16 },
  infoTxt:  { flex: 1, fontSize: 12, color: C.text, lineHeight: 18 },

  modalInput:{ backgroundColor: C.bg, borderWidth: 1.5, borderColor: C.border, borderRadius: 12, paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: C.text, fontWeight: '600' },

  savedBadge: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 10, justifyContent: 'center' },
  savedTxt:   { fontSize: 13, color: C.success, fontWeight: '700' },

  modalNote: { textAlign: 'center', fontSize: 11, color: C.textMuted, marginTop: 12, lineHeight: 16 },
});
