import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl, TextInput, Modal,
  ScrollView, Alert, KeyboardAvoidingView, Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../api/client';
import { ApiResponse, ExpenseDTO } from '../types';
import { C } from '../theme';
import { useAuth } from '../context/AuthContext';

interface AccountDTO { id: number; accountName: string; accountType: string; }
interface PaymentMethodDTO { id: number; methodName: string; }

const todayStr = () => new Date().toISOString().slice(0, 10);

const fmtDate = (v?: string) => {
  if (!v) return '';
  try {
    const d = new Date(v);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch { return v; }
};

const fmtAmt = (v?: number) => (v ?? 0).toLocaleString();

export default function ExpenseScreen() {
  const { hasPermission } = useAuth() as any;
  const canCreate = hasPermission?.('CAN_ACCESS_EXPENSE_CREATE') ?? true;

  const [items,      setItems]      = useState<ExpenseDTO[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showForm,   setShowForm]   = useState(false);
  const [saving,     setSaving]     = useState(false);

  // Selects for form
  const [accounts,  setAccounts]  = useState<AccountDTO[]>([]);
  const [methods,   setMethods]   = useState<PaymentMethodDTO[]>([]);

  // Form state
  const [date,      setDate]      = useState(todayStr());
  const [amount,    setAmount]    = useState('');
  const [desc,      setDesc]      = useState('');
  const [accountId, setAccountId] = useState<number>(0);
  const [methodId,  setMethodId]  = useState<number>(0);
  const [acctOpen,  setAcctOpen]  = useState(false);
  const [methOpen,  setMethOpen]  = useState(false);

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<ExpenseDTO[]>>('/expenses');
      const data = Array.isArray(res.data) ? res.data : [];
      setItems(data.sort((a, b) => (b.expenseDate ?? '').localeCompare(a.expenseDate ?? '')));
    } catch {}
    setLoading(false);
    setRefreshing(false);
  }, []);

  const loadSelects = useCallback(async () => {
    try {
      const [accRes, pmRes] = await Promise.all([
        api.get<ApiResponse<AccountDTO[]>>('/accounts'),
        api.get<ApiResponse<PaymentMethodDTO[]>>('/payment-methods'),
      ]);
      const accs = (Array.isArray(accRes.data) ? accRes.data : []) as AccountDTO[];
      setAccounts(accs.filter(a => a.accountType === 'EXPENSE' || a.accountType === 'expense' || !a.accountType));
      setMethods(Array.isArray(pmRes.data) ? pmRes.data : []);
    } catch {}
  }, []);

  useEffect(() => { load(); loadSelects(); }, [load, loadSelects]);

  const resetForm = () => {
    setDate(todayStr());
    setAmount('');
    setDesc('');
    setAccountId(accounts[0]?.id ?? 0);
    setMethodId(0);
  };

  const openForm = () => {
    resetForm();
    setShowForm(true);
  };

  const submit = async () => {
    const amt = parseFloat(amount);
    if (!amt || amt <= 0) { Alert.alert('Error', 'Enter a valid amount'); return; }
    if (!accountId)        { Alert.alert('Error', 'Select an account'); return; }
    setSaving(true);
    try {
      const body: ExpenseDTO = {
        expenseDate: date,
        accountId,
        paymentMethodId: methodId || undefined,
        amount: amt,
        description: desc.trim() || undefined,
      };
      const res = await api.post<ApiResponse<ExpenseDTO>>('/expenses', body);
      if ((res as any).success !== false) {
        setShowForm(false);
        load(true);
      } else {
        Alert.alert('Error', (res as any).message ?? 'Could not save expense');
      }
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Could not connect to server');
    }
    setSaving(false);
  };

  const selAcct = accounts.find(a => a.id === accountId);
  const selMeth = methods.find(m => m.id === methodId);

  const renderItem = ({ item }: { item: ExpenseDTO }) => (
    <View style={st.card}>
      <View style={st.cardTop}>
        <View style={st.codeWrap}>
          <Ionicons name="wallet-outline" size={14} color={C.danger} style={{ marginRight: 5 }} />
          <Text style={st.code}>{item.expenseCode ?? `#${item.id}`}</Text>
        </View>
        <Text style={st.dateText}>{fmtDate(item.expenseDate)}</Text>
      </View>
      {item.description ? <Text style={st.desc}>{item.description}</Text> : null}
      <View style={st.cardBottom}>
        <Text style={st.acctName}>{item.accountName ?? '—'}</Text>
        <Text style={st.amount}>{fmtAmt(item.amount)} Ks</Text>
      </View>
      {item.paymentMethodName ? (
        <Text style={st.method}>{item.paymentMethodName}</Text>
      ) : null}
    </View>
  );

  return (
    <View style={st.root}>
      {loading
        ? <ActivityIndicator color={C.primary} style={{ marginTop: 40 }} size="large" />
        : (
          <FlatList
            data={items}
            keyExtractor={i => String(i.id)}
            renderItem={renderItem}
            contentContainerStyle={{ padding: 12, paddingBottom: 100 }}
            refreshControl={
              <RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(true); }} colors={[C.danger]} tintColor={C.danger} />
            }
            ListEmptyComponent={
              <View style={st.emptyWrap}>
                <Ionicons name="wallet-outline" size={42} color={C.border} />
                <Text style={st.empty}>No expenses recorded</Text>
              </View>
            }
          />
        )
      }

      {canCreate && (
        <TouchableOpacity style={st.fab} onPress={openForm}>
          <Ionicons name="add" size={22} color="#fff" />
          <Text style={st.fabText}>Add Expense</Text>
        </TouchableOpacity>
      )}

      {/* ── Add Expense Modal ── */}
      <Modal visible={showForm} animationType="slide" transparent onRequestClose={() => setShowForm(false)}>
        <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <View style={st.modalOverlay}>
            <View style={st.modalSheet}>
              {/* Header */}
              <View style={st.modalHeader}>
                <Text style={st.modalTitle}>New Expense</Text>
                <TouchableOpacity onPress={() => setShowForm(false)}>
                  <Ionicons name="close" size={22} color={C.text} />
                </TouchableOpacity>
              </View>

              <ScrollView contentContainerStyle={{ paddingBottom: 24 }} keyboardShouldPersistTaps="handled">

                {/* Date */}
                <Text style={st.label}>Date</Text>
                <TextInput
                  style={st.input}
                  value={date}
                  onChangeText={setDate}
                  placeholder="YYYY-MM-DD"
                  placeholderTextColor={C.textMuted}
                  keyboardType="numbers-and-punctuation"
                />

                {/* Amount */}
                <Text style={st.label}>Amount (Ks) *</Text>
                <TextInput
                  style={st.input}
                  value={amount}
                  onChangeText={setAmount}
                  placeholder="0"
                  placeholderTextColor={C.textMuted}
                  keyboardType="numeric"
                />

                {/* Description */}
                <Text style={st.label}>Description</Text>
                <TextInput
                  style={[st.input, { height: 72, textAlignVertical: 'top' }]}
                  value={desc}
                  onChangeText={setDesc}
                  placeholder="Expense details..."
                  placeholderTextColor={C.textMuted}
                  multiline
                />

                {/* Account */}
                <Text style={st.label}>Account *</Text>
                <TouchableOpacity style={st.select} onPress={() => setAcctOpen(v => !v)}>
                  <Text style={selAcct ? st.selectVal : st.selectPlaceholder}>
                    {selAcct ? selAcct.accountName : 'Select account...'}
                  </Text>
                  <Ionicons name={acctOpen ? 'chevron-up' : 'chevron-down'} size={16} color={C.textMuted} />
                </TouchableOpacity>
                {acctOpen && (
                  <View style={st.dropdown}>
                    <ScrollView style={{ maxHeight: 180 }} nestedScrollEnabled>
                      {accounts.map(a => (
                        <TouchableOpacity key={a.id} style={st.dropItem} onPress={() => { setAccountId(a.id); setAcctOpen(false); }}>
                          <Text style={[st.dropText, a.id === accountId && { color: C.primary, fontWeight: '700' }]}>{a.accountName}</Text>
                        </TouchableOpacity>
                      ))}
                      {accounts.length === 0 && <Text style={st.dropText}>No accounts found</Text>}
                    </ScrollView>
                  </View>
                )}

                {/* Payment Method */}
                <Text style={[st.label, { marginTop: 10 }]}>Payment Method</Text>
                <TouchableOpacity style={st.select} onPress={() => setMethOpen(v => !v)}>
                  <Text style={selMeth ? st.selectVal : st.selectPlaceholder}>
                    {selMeth ? selMeth.methodName : 'Select method (optional)...'}
                  </Text>
                  <Ionicons name={methOpen ? 'chevron-up' : 'chevron-down'} size={16} color={C.textMuted} />
                </TouchableOpacity>
                {methOpen && (
                  <View style={st.dropdown}>
                    <ScrollView style={{ maxHeight: 150 }} nestedScrollEnabled>
                      <TouchableOpacity style={st.dropItem} onPress={() => { setMethodId(0); setMethOpen(false); }}>
                        <Text style={st.dropText}>— None —</Text>
                      </TouchableOpacity>
                      {methods.map(m => (
                        <TouchableOpacity key={m.id} style={st.dropItem} onPress={() => { setMethodId(m.id); setMethOpen(false); }}>
                          <Text style={[st.dropText, m.id === methodId && { color: C.primary, fontWeight: '700' }]}>{m.methodName}</Text>
                        </TouchableOpacity>
                      ))}
                    </ScrollView>
                  </View>
                )}

              </ScrollView>

              {/* Save */}
              <TouchableOpacity style={[st.saveBtn, saving && { opacity: 0.6 }]} onPress={submit} disabled={saving}>
                {saving
                  ? <ActivityIndicator color="#fff" size="small" />
                  : <Text style={st.saveBtnText}>Save Expense</Text>
                }
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </View>
  );
}

const st = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.bg },

  card: {
    backgroundColor: C.card, borderRadius: 12, borderWidth: 1,
    borderColor: C.border, padding: 14, marginBottom: 8,
  },
  cardTop:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 },
  codeWrap:   { flexDirection: 'row', alignItems: 'center' },
  code:       { fontSize: 13, fontWeight: '800', color: C.danger },
  dateText:   { fontSize: 11, color: C.textMuted, fontWeight: '600' },
  desc:       { fontSize: 13, color: C.text, marginBottom: 8 },
  cardBottom: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  acctName:   { fontSize: 12, color: C.textMuted },
  amount:     { fontSize: 15, fontWeight: '800', color: C.danger },
  method:     { fontSize: 11, color: C.textMuted, marginTop: 4 },
  emptyWrap:  { alignItems: 'center', marginTop: 60, gap: 10 },
  empty:      { textAlign: 'center', color: C.textMuted, fontSize: 14 },

  fab: {
    position: 'absolute', bottom: 20, right: 16, zIndex: 10,
    backgroundColor: C.danger, borderRadius: 28,
    paddingHorizontal: 18, paddingVertical: 12,
    elevation: 6, shadowColor: C.danger, shadowOpacity: 0.4, shadowRadius: 8,
    flexDirection: 'row', alignItems: 'center', gap: 6,
  },
  fabText: { color: '#fff', fontWeight: '700', fontSize: 14 },

  modalOverlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' },
  modalSheet: {
    backgroundColor: C.card,
    borderTopLeftRadius: 22, borderTopRightRadius: 22,
    paddingHorizontal: 20, paddingTop: 16, paddingBottom: 12,
    maxHeight: '92%',
  },
  modalHeader:  { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 },
  modalTitle:   { fontSize: 17, fontWeight: '800', color: C.text },

  label: { fontSize: 12, fontWeight: '700', color: C.text, marginBottom: 6 },
  input: {
    backgroundColor: C.bg, borderWidth: 1.5, borderColor: C.border,
    borderRadius: 10, paddingHorizontal: 14, paddingVertical: 10,
    fontSize: 14, color: C.text, marginBottom: 12,
  },
  select: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    backgroundColor: C.bg, borderWidth: 1.5, borderColor: C.border,
    borderRadius: 10, paddingHorizontal: 14, paddingVertical: 11, marginBottom: 4,
  },
  selectVal:         { fontSize: 14, color: C.text },
  selectPlaceholder: { fontSize: 14, color: C.textMuted },
  dropdown:  { backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, marginBottom: 10 },
  dropItem:  { paddingHorizontal: 14, paddingVertical: 11, borderBottomWidth: 1, borderBottomColor: C.border },
  dropText:  { fontSize: 13, color: C.text },

  saveBtn:     { backgroundColor: C.danger, borderRadius: 12, paddingVertical: 14, alignItems: 'center', marginTop: 8 },
  saveBtnText: { color: '#fff', fontWeight: '800', fontSize: 15 },
});
