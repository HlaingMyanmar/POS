import React, { useState, useEffect } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, TextInput, StyleSheet,
  KeyboardAvoidingView, Platform, Alert, ActivityIndicator,
} from 'react-native';
import { api } from '../../api/client';
import { ApiResponse, SaleDTO, SaleReturnDTO, PaymentMethodDTO } from '../../types';
import { C } from '../../theme';

export default function NewSaleReturnScreen({ route, navigation }: any) {
  const { sale }: { sale: SaleDTO } = route.params;
  const [payMethods, setPayMethods] = useState<PaymentMethodDTO[]>([]);
  const [reason,     setReason]     = useState('');
  const [selectedPay, setPay]       = useState<PaymentMethodDTO | null>(null);
  const [txnNo,      setTxnNo]      = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Return qty per item
  const [returnQtys, setReturnQtys] = useState<Record<number, number>>(() => {
    const m: Record<number, number> = {};
    (sale.details ?? []).forEach(d => { m[d.productId] = 0; });
    return m;
  });

  useEffect(() => {
    api.get<ApiResponse<PaymentMethodDTO[]>>('/payment-methods/active')
      .then(r => setPayMethods(r.data ?? []));
  }, []);

  const setQty = (productId: number, delta: number) => {
    setReturnQtys(prev => {
      const detail = sale.details.find(d => d.productId === productId);
      const max = detail?.qty ?? 0;
      const next = Math.max(0, Math.min(max, (prev[productId] ?? 0) + delta));
      return { ...prev, [productId]: next };
    });
  };

  const totalReturn = (sale.details ?? []).reduce((s, d) => {
    return s + (returnQtys[d.productId] ?? 0) * (d.unitPrice ?? 0);
  }, 0);

  const handleSubmit = async () => {
    const selected = (sale.details ?? []).filter(d => (returnQtys[d.productId] ?? 0) > 0);
    if (selected.length === 0) { Alert.alert('Error', 'Return item တစ်ခုမျှ မရွေးရသေးပါ'); return; }
    if (!reason.trim()) { Alert.alert('Error', 'Reason ထည့်ပါ'); return; }

    setSubmitting(true);
    const body: SaleReturnDTO = {
      saleId: sale.id!,
      staffId: sale.staffId,
      reason: reason.trim(),
      totalReturnAmount: totalReturn,
      refundAmount: totalReturn,
      paymentMethodId: selectedPay?.id,
      transactionNo: txnNo.trim() || undefined,
      details: selected.map(d => ({
        productId:  d.productId,
        productName: d.productName,
        qty:        returnQtys[d.productId],
        unitPrice:  d.unitPrice,
        subtotal:   returnQtys[d.productId] * (d.unitPrice ?? 0),
        serialNumbers: [],
      })),
    };
    try {
      await api.post<ApiResponse<SaleReturnDTO>>('/sale-returns', body);
      Alert.alert('Success', 'Sale Return ထည့်ပြီးပါပြီ', [
        { text: 'OK', onPress: () => navigation.popToTop() },
      ]);
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Failed');
    }
    setSubmitting(false);
  };

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView style={st.root} contentContainerStyle={{ padding: 14, paddingBottom: 40 }}>
        {/* Sale ref */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>ORIGINAL SALE</Text>
          <Text style={st.saleRef}>{sale.saleCode ?? `#${sale.id}`} · {sale.customerName}</Text>
        </View>

        {/* Items to return */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>SELECT ITEMS TO RETURN</Text>
          {(sale.details ?? []).map((d, i) => (
            <View key={i} style={st.itemRow}>
              <View style={{ flex: 1 }}>
                <Text style={st.itemName} numberOfLines={1}>{d.productName}</Text>
                <Text style={st.itemMeta}>Max: {d.qty} · {(d.unitPrice ?? 0).toLocaleString()} Ks each</Text>
              </View>
              <View style={st.qtyRow}>
                <TouchableOpacity style={st.qBtn} onPress={() => setQty(d.productId, -1)}>
                  <Text style={st.qBtnTxt}>−</Text>
                </TouchableOpacity>
                <Text style={[st.qty, { color: returnQtys[d.productId] > 0 ? C.danger : C.textMuted }]}>
                  {returnQtys[d.productId] ?? 0}
                </Text>
                <TouchableOpacity style={st.qBtn} onPress={() => setQty(d.productId, 1)}>
                  <Text style={st.qBtnTxt}>+</Text>
                </TouchableOpacity>
              </View>
            </View>
          ))}
        </View>

        {/* Payment */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>REFUND INFO</Text>
          <Text style={st.totalReturn}>Total Refund: <Text style={{ color: C.danger }}>{totalReturn.toLocaleString()} Ks</Text></Text>

          <Text style={st.label}>Refund Method</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 10 }}>
            {payMethods.map(m => (
              <TouchableOpacity
                key={m.id}
                style={[st.payChip, selectedPay?.id === m.id && st.payChipActive]}
                onPress={() => setPay(m)}
              >
                <Text style={[st.payChipText, selectedPay?.id === m.id && { color: '#fff' }]}>{m.methodName}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          <Text style={st.label}>Transaction No (optional)</Text>
          <TextInput style={st.input} value={txnNo} onChangeText={setTxnNo} placeholder="Transaction number" placeholderTextColor={C.textMuted} />

          <Text style={st.label}>Reason *</Text>
          <TextInput
            style={[st.input, { height: 70, textAlignVertical: 'top' }]}
            value={reason} onChangeText={setReason}
            placeholder="Return reason" placeholderTextColor={C.textMuted} multiline
          />
        </View>

        <TouchableOpacity style={[st.submitBtn, submitting && { opacity: 0.7 }]} onPress={handleSubmit} disabled={submitting}>
          {submitting ? <ActivityIndicator color="#fff" /> : <Text style={st.submitBtnText}>↩  Submit Return</Text>}
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  root:         { flex: 1, backgroundColor: C.bg },
  section:      { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  sectionTitle: { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 8 },
  saleRef:      { fontSize: 14, fontWeight: '700', color: C.primary },
  itemRow:      { flexDirection: 'row', alignItems: 'center', paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: C.border, gap: 10 },
  itemName:     { fontSize: 13, fontWeight: '700', color: C.text },
  itemMeta:     { fontSize: 11, color: C.textMuted, marginTop: 2 },
  qtyRow:       { flexDirection: 'row', alignItems: 'center', gap: 6 },
  qBtn:         { width: 28, height: 28, borderRadius: 8, backgroundColor: C.primaryLight, justifyContent: 'center', alignItems: 'center' },
  qBtnTxt:      { fontSize: 16, fontWeight: '700', color: C.primary },
  qty:          { fontSize: 16, fontWeight: '800', minWidth: 24, textAlign: 'center' },
  totalReturn:  { fontSize: 14, fontWeight: '700', color: C.text, marginBottom: 12 },
  label:        { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', marginBottom: 6, marginTop: 10 },
  input:        { backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
  payChip:      { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20, borderWidth: 1, borderColor: C.border, marginRight: 8, backgroundColor: C.card },
  payChipActive:{ backgroundColor: C.primary, borderColor: C.primary },
  payChipText:  { fontSize: 13, fontWeight: '600', color: C.text },
  submitBtn:    { backgroundColor: C.danger, borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  submitBtnText:{ color: '#fff', fontWeight: '800', fontSize: 15 },
});
