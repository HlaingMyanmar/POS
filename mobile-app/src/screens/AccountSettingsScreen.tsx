import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  ActivityIndicator, Alert, ScrollView, KeyboardAvoidingView, Platform,
} from 'react-native';
import { api } from '../api/client';
import { ApiResponse } from '../types';
import { C } from '../theme';
import { useAuth } from '../context/AuthContext';

interface ProfileDTO {
  name?: string;
  phone?: string;
}

export default function AccountSettingsScreen({ navigation }: any) {
  const { username, name: storedName, phone: storedPhone, updateProfile } = useAuth() as any;
  const [name,    setName]    = useState<string>(storedName  ?? '');
  const [phone,   setPhone]   = useState<string>(storedPhone ?? '');
  const [loading, setLoading] = useState(false);

  const save = async () => {
    setLoading(true);
    try {
      const res = await api.put<ApiResponse<ProfileDTO>>('/user/profile', { name: name.trim(), phone: phone.trim() });
      if (res.success !== false) {
        updateProfile(name.trim(), phone.trim());
        Alert.alert('Saved', 'Profile updated successfully.', [
          { text: 'OK', onPress: () => navigation.goBack() },
        ]);
      } else {
        Alert.alert('Error', 'Could not update profile.');
      }
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Cannot connect to server');
    }
    setLoading(false);
  };

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: C.bg }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={st.container}>
        {/* Avatar */}
        <View style={st.avatarWrap}>
          <View style={st.avatarCircle}>
            <Text style={st.avatarText}>
              {(name || username || 'U')[0].toUpperCase()}
            </Text>
          </View>
          <Text style={st.usernameLabel}>{username}</Text>
        </View>

        {/* Form card */}
        <View style={st.card}>
          <Text style={st.sectionLabel}>PROFILE INFO</Text>

          <Text style={st.label}>Display Name</Text>
          <TextInput
            style={st.input}
            value={name}
            onChangeText={setName}
            placeholder="Enter your name"
            placeholderTextColor={C.textMuted}
            autoCorrect={false}
          />

          <Text style={st.label}>Phone Number</Text>
          <TextInput
            style={st.input}
            value={phone}
            onChangeText={setPhone}
            placeholder="Enter your phone number"
            placeholderTextColor={C.textMuted}
            keyboardType="phone-pad"
            autoCorrect={false}
          />
        </View>

        <TouchableOpacity style={[st.saveBtn, loading && { opacity: 0.6 }]} onPress={save} disabled={loading}>
          {loading
            ? <ActivityIndicator color="#fff" size="small" />
            : <Text style={st.saveBtnText}>Save Changes</Text>
          }
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  container:    { padding: 20, paddingBottom: 40 },
  avatarWrap:   { alignItems: 'center', marginBottom: 24, marginTop: 8 },
  avatarCircle: { width: 72, height: 72, borderRadius: 36, backgroundColor: C.primary, justifyContent: 'center', alignItems: 'center', marginBottom: 10, elevation: 4, shadowColor: C.primary, shadowOpacity: 0.35, shadowRadius: 10 },
  avatarText:   { fontSize: 30, fontWeight: '800', color: '#fff' },
  usernameLabel:{ fontSize: 14, color: C.textMuted, fontWeight: '600' },
  card:         { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 16, marginBottom: 20 },
  sectionLabel: { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 14 },
  label:        { fontSize: 12, fontWeight: '700', color: C.text, marginBottom: 6 },
  input:        { backgroundColor: C.bg, borderWidth: 1.5, borderColor: C.border, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 11, fontSize: 14, color: C.text, marginBottom: 14 },
  saveBtn:      { backgroundColor: C.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center' },
  saveBtnText:  { color: '#fff', fontWeight: '800', fontSize: 15 },
});
