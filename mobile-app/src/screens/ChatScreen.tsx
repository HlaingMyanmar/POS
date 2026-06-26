import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  View, Text, FlatList, TextInput, TouchableOpacity,
  StyleSheet, KeyboardAvoidingView, Platform, ActivityIndicator,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../api/client';
import { ApiResponse, ChatMessageDTO } from '../types';
import { C } from '../theme';
import { useAuth } from '../context/AuthContext';
import { subscribeTopic } from '../services/wsClient';

function formatTime(iso?: string) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso?: string) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
}

function isSameDay(a?: string, b?: string) {
  if (!a || !b) return false;
  return a.substring(0, 10) === b.substring(0, 10);
}

export default function ChatScreen() {
  const { username } = useAuth() as any;
  const [messages, setMessages] = useState<ChatMessageDTO[]>([]);
  const [text,     setText]     = useState('');
  const [loading,  setLoading]  = useState(true);
  const [sending,  setSending]  = useState(false);
  const listRef = useRef<FlatList>(null);

  const load = useCallback(async () => {
    try {
      const res = await api.get<ApiResponse<ChatMessageDTO[]>>('/chat/messages');
      setMessages(res.data ?? []);
    } catch {}
    setLoading(false);
  }, []);

  useEffect(() => { load(); }, []);

  useEffect(() => {
    const unsub = subscribeTopic('/topic/chat', (raw) => {
      try {
        const msg: ChatMessageDTO = JSON.parse(raw.body);
        setMessages(prev => [...prev, msg]);
        setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 80);
      } catch {}
    });
    return unsub;
  }, []);

  useEffect(() => {
    if (!loading && messages.length > 0) {
      setTimeout(() => listRef.current?.scrollToEnd({ animated: false }), 100);
    }
  }, [loading]);

  const send = async () => {
    const content = text.trim();
    if (!content || sending) return;
    setText('');
    setSending(true);
    try {
      await api.post('/chat/send', { content });
    } catch {
      setText(content); // restore on error
    }
    setSending(false);
  };

  const renderItem = ({ item, index }: { item: ChatMessageDTO; index: number }) => {
    const isMine = item.senderUsername === username;
    const prev   = messages[index - 1];
    const showDate = !isSameDay(prev?.sentAt, item.sentAt);
    const showName = !isMine && (index === 0 || prev?.senderUsername !== item.senderUsername);

    return (
      <>
        {showDate && (
          <View style={st.dateSep}>
            <Text style={st.dateSepText}>{formatDate(item.sentAt)}</Text>
          </View>
        )}
        <View style={[st.msgRow, isMine && st.msgRowMine]}>
          {!isMine && (
            <View style={st.avatar}>
              <Text style={st.avatarTxt}>{(item.senderName || item.senderUsername || '?')[0].toUpperCase()}</Text>
            </View>
          )}
          <View style={[st.bubble, isMine ? st.bubbleMine : st.bubbleOther]}>
            {showName && (
              <Text style={st.senderName}>
                {item.senderName || item.senderUsername}
                {item.senderRole ? <Text style={st.senderRole}>  {item.senderRole}</Text> : null}
              </Text>
            )}
            <Text style={[st.msgText, isMine && st.msgTextMine]}>{item.content}</Text>
            <Text style={[st.time, isMine && st.timeMine]}>{formatTime(item.sentAt)}</Text>
          </View>
        </View>
      </>
    );
  };

  if (loading) return (
    <View style={st.center}>
      <ActivityIndicator color={C.primary} size="large" />
    </View>
  );

  return (
    <KeyboardAvoidingView
      style={st.root}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 88 : 0}
    >
      <FlatList
        ref={listRef}
        data={messages}
        keyExtractor={(m, i) => String(m.id ?? i)}
        renderItem={renderItem}
        contentContainerStyle={{ padding: 12, paddingBottom: 8 }}
        ListEmptyComponent={
          <Text style={st.empty}>No messages yet. Say hello! 👋</Text>
        }
        onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: false })}
      />

      <View style={st.inputRow}>
        <TextInput
          style={st.input}
          value={text}
          onChangeText={setText}
          placeholder="Type a message…"
          placeholderTextColor={C.textMuted}
          multiline
          maxLength={500}
          returnKeyType="send"
          onSubmitEditing={send}
          blurOnSubmit={false}
        />
        <TouchableOpacity
          style={[st.sendBtn, (!text.trim() || sending) && { opacity: 0.4 }]}
          onPress={send}
          disabled={!text.trim() || sending}
        >
          {sending
            ? <ActivityIndicator color="#fff" size="small" />
            : <Ionicons name="send" size={18} color="#fff" />
          }
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  root:        { flex: 1, backgroundColor: C.bg },
  center:      { flex: 1, justifyContent: 'center', alignItems: 'center' },
  empty:       { textAlign: 'center', color: C.textMuted, marginTop: 60, fontSize: 14 },

  dateSep:     { alignItems: 'center', marginVertical: 10 },
  dateSepText: { fontSize: 11, color: C.textMuted, backgroundColor: C.border, paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10 },

  msgRow:      { flexDirection: 'row', marginBottom: 6, alignItems: 'flex-end', gap: 6 },
  msgRowMine:  { justifyContent: 'flex-end' },

  avatar:      { width: 30, height: 30, borderRadius: 15, backgroundColor: C.primary, justifyContent: 'center', alignItems: 'center', marginBottom: 2 },
  avatarTxt:   { color: '#fff', fontSize: 13, fontWeight: '700' },

  bubble:      { maxWidth: '75%', borderRadius: 14, paddingHorizontal: 12, paddingVertical: 8 },
  bubbleOther: { backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderBottomLeftRadius: 4 },
  bubbleMine:  { backgroundColor: C.primary, borderBottomRightRadius: 4 },

  senderName:  { fontSize: 11, fontWeight: '700', color: C.primary, marginBottom: 2 },
  senderRole:  { fontSize: 10, color: C.textMuted, fontWeight: '400' },
  msgText:     { fontSize: 14, color: C.text, lineHeight: 20 },
  msgTextMine: { color: '#fff' },
  time:        { fontSize: 10, color: C.textMuted, marginTop: 3, textAlign: 'right' },
  timeMine:    { color: 'rgba(255,255,255,0.65)' },

  inputRow:    { flexDirection: 'row', gap: 8, padding: 10, backgroundColor: C.card, borderTopWidth: 1, borderTopColor: C.border, alignItems: 'flex-end' },
  input:       { flex: 1, backgroundColor: C.bg, borderWidth: 1.5, borderColor: C.border, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 10, fontSize: 14, color: C.text, maxHeight: 100 },
  sendBtn:     { width: 42, height: 42, borderRadius: 21, backgroundColor: C.primary, justifyContent: 'center', alignItems: 'center' },
});
