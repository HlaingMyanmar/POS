import React, { useEffect, useState, useMemo } from 'react';
import {
  View, Text, TextInput, FlatList, TouchableOpacity, StyleSheet,
  ScrollView, Modal, ActivityIndicator, Alert, KeyboardAvoidingView, Platform,
} from 'react-native';
import { api } from '../../api/client';
import {
  ApiResponse, ProductDTO, ProductSerialDTO, CustomerDTO,
  StaffDTO, PaymentMethodDTO, CartItem, SaleDTO,
} from '../../types';
import ScannerModal from '../../components/ScannerModal';
import { C } from '../../theme';

// ── Generic Picker Modal ──────────────────────────────────────────────────────
function PickerModal<T extends { id: number }>({
  visible, title, items, labelKey, onSelect, onClose, extraHeader,
}: {
  visible: boolean; title: string; items: T[];
  labelKey: keyof T; onSelect: (item: T) => void;
  onClose: () => void; extraHeader?: React.ReactNode;
}) {
  const [q, setQ] = useState('');
  const filtered = items.filter(i =>
    String(i[labelKey]).toLowerCase().includes(q.toLowerCase())
  );
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: C.bg }}>
        <View style={pm.header}>
          <Text style={pm.title}>{title}</Text>
          <TouchableOpacity onPress={onClose}><Text style={pm.close}>✕</Text></TouchableOpacity>
        </View>
        {extraHeader}
        <TextInput style={pm.search} placeholder="Search..." value={q} onChangeText={setQ} placeholderTextColor={C.textMuted} />
        <FlatList
          data={filtered}
          keyExtractor={i => String(i.id)}
          renderItem={({ item }) => (
            <TouchableOpacity style={pm.item} onPress={() => { onSelect(item); onClose(); setQ(''); }}>
              <Text style={pm.itemText}>{String(item[labelKey])}</Text>
              {(item as any).phone ? <Text style={pm.itemSub}>{(item as any).phone}</Text> : null}
            </TouchableOpacity>
          )}
        />
      </View>
    </Modal>
  );
}

const pm = StyleSheet.create({
  header:   { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.primary, paddingHorizontal: 16, paddingVertical: 14, paddingTop: 48 },
  title:    { color: '#fff', fontWeight: '700', fontSize: 16 },
  close:    { color: '#fff', fontSize: 18 },
  search:   { margin: 12, backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
  item:     { paddingHorizontal: 16, paddingVertical: 13, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.card },
  itemText: { fontSize: 14, fontWeight: '600', color: C.text },
  itemSub:  { fontSize: 12, color: C.textMuted, marginTop: 2 },
  newBtn:   { flexDirection: 'row', alignItems: 'center', gap: 8, margin: 12, backgroundColor: C.primaryLight, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 10 },
  newText:  { fontSize: 14, fontWeight: '700', color: C.primary },
});

// ── New Customer Modal ────────────────────────────────────────────────────────
function NewCustomerModal({
  visible, onClose, onCreated,
}: { visible: boolean; onClose: () => void; onCreated: (c: CustomerDTO) => void }) {
  const [name,    setName]    = useState('');
  const [phone,   setPhone]   = useState('');
  const [address, setAddress] = useState('');
  const [saving,  setSaving]  = useState(false);

  const submit = async () => {
    if (!name.trim()) { Alert.alert('Error', 'Customer name ထည့်ပါ'); return; }
    setSaving(true);
    try {
      const res = await api.post<ApiResponse<CustomerDTO>>('/customers', { name: name.trim(), phone: phone.trim(), address: address.trim() });
      onCreated(res.data);
      setName(''); setPhone(''); setAddress('');
      onClose();
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Failed to create customer');
    }
    setSaving(false);
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={nc.header}>
          <Text style={nc.title}>New Customer</Text>
          <TouchableOpacity onPress={onClose}><Text style={nc.close}>✕</Text></TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }}>
          <Text style={nc.label}>Name *</Text>
          <TextInput style={nc.input} value={name} onChangeText={setName} placeholder="Customer name" placeholderTextColor={C.textMuted} />
          <Text style={nc.label}>Phone</Text>
          <TextInput style={nc.input} value={phone} onChangeText={setPhone} placeholder="09xxxxxxxxx" placeholderTextColor={C.textMuted} keyboardType="phone-pad" />
          <Text style={nc.label}>Address</Text>
          <TextInput style={[nc.input, { height: 80, textAlignVertical: 'top' }]} value={address} onChangeText={setAddress} placeholder="Address" placeholderTextColor={C.textMuted} multiline />
          <TouchableOpacity style={[nc.btn, saving && { opacity: 0.7 }]} onPress={submit} disabled={saving}>
            {saving ? <ActivityIndicator color="#fff" /> : <Text style={nc.btnText}>Save Customer</Text>}
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const nc = StyleSheet.create({
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.primary, paddingHorizontal: 16, paddingVertical: 14, paddingTop: 48 },
  title:  { color: '#fff', fontWeight: '700', fontSize: 16 },
  close:  { color: '#fff', fontSize: 18 },
  label:  { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', marginBottom: 6, marginTop: 14 },
  input:  { backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, color: C.text },
  btn:    { backgroundColor: C.primary, borderRadius: 10, paddingVertical: 14, alignItems: 'center', marginTop: 24 },
  btnText:{ color: '#fff', fontWeight: '700', fontSize: 15 },
});

// ── Main Screen ───────────────────────────────────────────────────────────────
export default function NewSaleScreen({ route, navigation }: any) {
  const prefill: ProductDTO | undefined = route.params?.prefill;

  const [products,     setProducts]     = useState<ProductDTO[]>([]);
  const [customers,    setCustomers]    = useState<CustomerDTO[]>([]);
  const [staffList,    setStaffList]    = useState<StaffDTO[]>([]);
  const [payMethods,   setPayMethods]   = useState<PaymentMethodDTO[]>([]);
  const [loading,      setLoading]      = useState(true);

  const [cart,         setCart]         = useState<CartItem[]>([]);
  const [selectedCustomer, setCustomer] = useState<CustomerDTO | null>(null);
  const [selectedStaff,    setStaff]    = useState<StaffDTO | null>(null);
  const [selectedPayMethod,setPayMethod]= useState<PaymentMethodDTO | null>(null);
  const [paidAmount,   setPaidAmount]   = useState('');
  const [remark,       setRemark]       = useState('');
  const [discountTotal,setDiscountTotal]= useState('');
  const [submitting,   setSubmitting]   = useState(false);

  // Scanner state: null = scanning for product, number = scanning serial for cart[idx]
  const [showScanner,        setScanner]        = useState(false);
  const [serialScanIdx,      setSerialScanIdx]  = useState<number | null>(null);
  const [scanLoading,        setScanLoading]    = useState(false);
  const [showProductPicker,  setProductPicker]  = useState(false);
  const [showCustomerPicker, setCustomerPicker] = useState(false);
  const [showStaffPicker,    setStaffPicker]    = useState(false);
  const [showPayPicker,      setPayPicker]      = useState(false);
  const [showNewCustomer,    setNewCustomer]    = useState(false);


  useEffect(() => {
    Promise.all([
      api.get<ApiResponse<ProductDTO[]>>('/products'),
      api.get<ApiResponse<CustomerDTO[]>>('/customers'),
      api.get<ApiResponse<StaffDTO[]>>('/staffs/active'),
      api.get<ApiResponse<PaymentMethodDTO[]>>('/payment-methods/active'),
    ]).then(([p, c, s, pm]) => {
      setProducts(p.data ?? []);
      setCustomers(c.data ?? []);
      setStaffList(s.data ?? []);
      setPayMethods(pm.data ?? []);
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => { if (prefill) addToCart(prefill); }, [prefill]);

  const addToCart = (product: ProductDTO, serial?: string) => {
    setCart(prev => {
      const idx = prev.findIndex(i => i.product.id === product.id);
      if (idx >= 0) {
        const item = prev[idx];
        if (product.hasSerial) {
          // For serial-tracked: only add if serial provided and not duplicate
          if (serial && !item.serialNumbers.includes(serial)) {
            return prev.map((ci, j) => j === idx
              ? { ...ci, serialNumbers: [...ci.serialNumbers, serial], qty: ci.serialNumbers.length + 1 }
              : ci
            );
          }
          // No serial or duplicate — do nothing
          return prev;
        }
        // Non-serial: increment qty
        return prev.map((ci, j) => j === idx ? { ...ci, qty: ci.qty + 1 } : ci);
      }
      return [...prev, {
        product,
        qty: 1,
        unitPrice: product.sellingPrice,
        discountAmount: 0,
        serialNumbers: serial ? [serial] : [],
      }];
    });
  };

  const updateQty = (idx: number, delta: number) => {
    setCart(prev => {
      const item = prev[idx];
      if (item.product.hasSerial) return prev; // qty managed by serials
      const updated = [...prev];
      updated[idx] = { ...updated[idx], qty: Math.max(1, updated[idx].qty + delta) };
      return updated;
    });
  };

  const updateDiscount = (idx: number, value: string) => {
    const amt = Math.max(0, Number(value) || 0);
    setCart(prev => prev.map((ci, i) => i === idx ? { ...ci, discountAmount: amt } : ci));
  };

  const removeItem = (idx: number) => setCart(prev => prev.filter((_, i) => i !== idx));

  const removeSerial = (itemIdx: number, serial: string) => {
    setCart(prev => prev.map((ci, j) => {
      if (j !== itemIdx) return ci;
      const newSerials = ci.serialNumbers.filter(s => s !== serial);
      return { ...ci, serialNumbers: newSerials, qty: Math.max(1, newSerials.length) };
    }));
  };

  // Product scan (main scan button)
  const handleScan = async (code: string) => {
    setScanner(false);
    const byCode = products.find(p => p.productCode.toUpperCase() === code.toUpperCase());
    if (byCode) { addToCart(byCode); return; }
    setScanLoading(true);
    try {
      const res = await api.get<ApiResponse<ProductSerialDTO>>(`/product-serials/by-serial/${encodeURIComponent(code)}`);
      if (res.data?.productId) {
        const bySerial = products.find(p => p.id === res.data.productId);
        if (bySerial) {
          setScanLoading(false);
          addToCart(bySerial, code); // pass scanned serial
          return;
        }
      }
    } catch {}
    setScanLoading(false);
    Alert.alert('Not Found', `"${code}" မတွေ့ပါ`);
  };

  // Serial scan for a specific cart item
  const handleSerialScan = (code: string) => {
    if (serialScanIdx === null) return;
    const idx = serialScanIdx;
    setSerialScanIdx(null);
    const item = cart[idx];
    if (!item) return;
    if (item.serialNumbers.includes(code)) {
      Alert.alert('Duplicate', `Serial "${code}" ထပ်ပြီးရှိနေပြီ`);
      return;
    }
    setCart(prev => prev.map((ci, j) => j === idx
      ? { ...ci, serialNumbers: [...ci.serialNumbers, code], qty: ci.serialNumbers.length + 1 }
      : ci
    ));
  };

  const grossTotal      = useMemo(() => cart.reduce((s, i) => s + i.unitPrice * i.qty, 0), [cart]);
  const lineDiscountSum = useMemo(() => cart.reduce((s, i) => s + (i.discountAmount || 0), 0), [cart]);
  const subtotal        = Math.max(0, grossTotal - lineDiscountSum);
  const discountAmt     = Number(discountTotal) || 0;
  const netAmount       = Math.max(0, subtotal - discountAmt);
  const paid        = Number(paidAmount) || 0;
  const dueAmount   = Math.max(0, netAmount - paid);

  const handleSubmit = async () => {
    if (!selectedCustomer) { Alert.alert('Error', 'Customer ရွေးပါ'); return; }
    if (!selectedStaff)    { Alert.alert('Error', 'Staff ရွေးပါ'); return; }
    if (cart.length === 0) { Alert.alert('Error', 'Item တစ်ခုမျှ မထည့်ရသေးပါ'); return; }

    // Validate serial numbers for hasSerial products
    for (const item of cart) {
      if (item.product.hasSerial && item.serialNumbers.length !== item.qty) {
        Alert.alert(
          'Serial Required',
          `"${item.product.name}" အတွက် serial number ${item.qty} ခု လိုအပ်သည်\n(${item.serialNumbers.length}/${item.qty} ထည့်ပြီး)`
        );
        return;
      }
    }

    setSubmitting(true);
    const body: SaleDTO = {
      customerId:      selectedCustomer.id,
      staffId:         selectedStaff.id,
      totalAmount:     grossTotal,
      discountAmount:  discountAmt,
      netAmount,
      paidAmount:      paid,
      dueAmount,
      paymentMethodId: selectedPayMethod?.id,
      remark,
      details: cart.map(i => ({
        productId:      i.product.id,
        productName:    i.product.name,
        qty:            i.qty,
        unitPrice:      i.unitPrice,
        subtotal:       i.qty * i.unitPrice,
        discountAmount: i.discountAmount,
        serialNumbers:  i.serialNumbers,
      })),
    };
    try {
      const res = await api.post<ApiResponse<SaleDTO>>('/sales', body);
      setSubmitting(false);
      navigation.replace('Voucher', { sale: res.data, cart, customer: selectedCustomer, staff: selectedStaff, payMethod: selectedPayMethod });
    } catch (e: any) {
      Alert.alert('Error', e?.message ?? 'Failed to save sale');
      setSubmitting(false);
    }
  };

  if (loading) {
    return <ActivityIndicator color={C.primary} style={{ marginTop: 80 }} size="large" />;
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView style={st.root} contentContainerStyle={{ padding: 14, paddingBottom: 40 }}>

        {/* SALE INFO */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>SALE INFO</Text>

          <TouchableOpacity style={st.picker} onPress={() => setCustomerPicker(true)}>
            <View>
              <Text style={st.pickerLabel}>Customer</Text>
              {selectedCustomer?.phone ? <Text style={st.pickerSub}>{selectedCustomer.phone}</Text> : null}
            </View>
            <View style={st.pickerRight}>
              <Text style={[st.pickerValue, !selectedCustomer && { color: C.textMuted }]}>
                {selectedCustomer?.name ?? 'ရွေးပါ...'}
              </Text>
              <Text style={st.chevron}>›</Text>
            </View>
          </TouchableOpacity>

          <TouchableOpacity style={st.picker} onPress={() => setStaffPicker(true)}>
            <Text style={st.pickerLabel}>Staff</Text>
            <View style={st.pickerRight}>
              <Text style={[st.pickerValue, !selectedStaff && { color: C.textMuted }]}>
                {selectedStaff?.name ?? 'ရွေးပါ...'}
              </Text>
              <Text style={st.chevron}>›</Text>
            </View>
          </TouchableOpacity>
        </View>

        {/* ITEMS */}
        <View style={st.section}>
          <View style={st.sectionHeader}>
            <Text style={st.sectionTitle}>ITEMS {cart.length > 0 ? `(${cart.length})` : ''}</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <TouchableOpacity style={st.addBtn} onPress={() => setScanner(true)}>
                {scanLoading
                  ? <ActivityIndicator color="#fff" size="small" />
                  : <Text style={st.addBtnText}>📷 Scan</Text>
                }
              </TouchableOpacity>
              <TouchableOpacity style={[st.addBtn, { backgroundColor: C.primaryLight }]} onPress={() => setProductPicker(true)}>
                <Text style={[st.addBtnText, { color: C.primary }]}>+ Search</Text>
              </TouchableOpacity>
            </View>
          </View>

          {cart.length === 0
            ? (
              <View style={st.emptyCart}>
                <Text style={{ fontSize: 28 }}>🛒</Text>
                <Text style={st.emptyCartText}>Scan or search to add items</Text>
              </View>
            )
            : cart.map((item, idx) => (
              <View key={item.product.id} style={st.cartItem}>
                {/* Product info row */}
                <View style={{ flex: 1 }}>
                  <View style={st.itemHeaderRow}>
                    <Text style={st.itemName} numberOfLines={1}>{item.product.name}</Text>
                    <TouchableOpacity style={st.removeBtn} onPress={() => removeItem(idx)}>
                      <Text style={st.removeBtnText}>✕</Text>
                    </TouchableOpacity>
                  </View>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 }}>
                    <View style={st.codeChip}>
                      <Text style={st.codeText}>{item.product.productCode}</Text>
                    </View>
                    {item.product.hasSerial && (
                      <View style={[st.codeChip, { backgroundColor: C.violetBg }]}>
                        <Text style={[st.codeText, { color: C.violet }]}>Serial Tracked</Text>
                      </View>
                    )}
                  </View>
                  <Text style={st.itemPrice}>
                    {item.unitPrice.toLocaleString()} Ks × {item.qty} = <Text style={{ color: C.primary, fontWeight: '800' }}>{(item.unitPrice * item.qty).toLocaleString()} Ks</Text>
                  </Text>
                  {/* Per-line discount */}
                  <View style={st.lineDiscRow}>
                    <Text style={st.lineDiscLabel}>Discount (Ks)</Text>
                    <TextInput
                      style={st.lineDiscInput}
                      value={item.discountAmount ? String(item.discountAmount) : ''}
                      onChangeText={v => updateDiscount(idx, v)}
                      keyboardType="numeric"
                      placeholder="0"
                      placeholderTextColor={C.textMuted}
                    />
                  </View>
                  {item.discountAmount > 0 && (
                    <Text style={st.lineDiscNet}>
                      Net: <Text style={{ color: C.success, fontWeight: '800' }}>{Math.max(0, item.unitPrice * item.qty - item.discountAmount).toLocaleString()} Ks</Text>
                    </Text>
                  )}
                </View>

                {/* Qty controls — only for non-serial products */}
                {!item.product.hasSerial && (
                  <View style={st.qtyRow}>
                    <TouchableOpacity style={st.qtyBtn} onPress={() => updateQty(idx, -1)}>
                      <Text style={st.qtyBtnText}>−</Text>
                    </TouchableOpacity>
                    <Text style={st.qty}>{item.qty}</Text>
                    <TouchableOpacity style={st.qtyBtn} onPress={() => updateQty(idx, 1)}>
                      <Text style={st.qtyBtnText}>+</Text>
                    </TouchableOpacity>
                  </View>
                )}

                {/* Serial number section — only for hasSerial products */}
                {item.product.hasSerial && (
                  <View style={st.serialSection}>
                    <View style={st.serialHeaderRow}>
                      <Text style={st.serialLabel}>
                        SERIAL ({item.serialNumbers.length} ခု)
                      </Text>
                      {item.serialNumbers.length === 0 && (
                        <Text style={st.serialWarning}>⚠ Required</Text>
                      )}
                    </View>

                    {/* Serial chips */}
                    {item.serialNumbers.length > 0 && (
                      <View style={st.serialChips}>
                        {item.serialNumbers.map(sn => (
                          <View key={sn} style={st.serialChip}>
                            <Text style={st.serialChipText}>{sn}</Text>
                            <TouchableOpacity onPress={() => removeSerial(idx, sn)} hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}>
                              <Text style={st.serialChipRemove}>×</Text>
                            </TouchableOpacity>
                          </View>
                        ))}
                      </View>
                    )}

                    {/* Scan button only — no manual typing */}
                    <TouchableOpacity
                      style={st.serialScanBtn}
                      onPress={() => setSerialScanIdx(idx)}
                    >
                      <Text style={st.serialScanBtnText}>📷  Scan Serial Number</Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            ))
          }
        </View>

        {/* PAYMENT */}
        <View style={st.section}>
          <Text style={st.sectionTitle}>PAYMENT</Text>
          <TouchableOpacity style={st.picker} onPress={() => setPayPicker(true)}>
            <Text style={st.pickerLabel}>Payment Method</Text>
            <View style={st.pickerRight}>
              <Text style={[st.pickerValue, !selectedPayMethod && { color: C.textMuted }]}>
                {selectedPayMethod?.methodName ?? 'ရွေးပါ...'}
              </Text>
              <Text style={st.chevron}>›</Text>
            </View>
          </TouchableOpacity>

          <View style={st.inputRow}>
            <Text style={st.inputLabel}>Overall Discount (Ks)</Text>
            <TextInput style={st.numInput} value={discountTotal} onChangeText={setDiscountTotal}
              keyboardType="numeric" placeholder="0" placeholderTextColor={C.textMuted} />
          </View>
          <View style={st.inputRow}>
            <Text style={st.inputLabel}>Paid Amount (Ks)</Text>
            <TextInput style={st.numInput} value={paidAmount} onChangeText={setPaidAmount}
              keyboardType="numeric" placeholder={String(netAmount)} placeholderTextColor={C.textMuted} />
          </View>
          <TextInput style={[st.numInput, { marginTop: 10, textAlign: 'left' }]}
            value={remark} onChangeText={setRemark}
            placeholder="Remark (optional)" placeholderTextColor={C.textMuted} />
        </View>

        {/* TOTALS */}
        <View style={st.summary}>
          {([
            ['Gross Total',     `${grossTotal.toLocaleString()} Ks`,      false, false],
            ...(lineDiscountSum > 0 ? [['Line Discounts', `− ${lineDiscountSum.toLocaleString()} Ks`, false, false] as [string, string, boolean, boolean]] : []),
            ['Subtotal',        `${subtotal.toLocaleString()} Ks`,        false, false],
            ['Overall Discount',`− ${discountAmt.toLocaleString()} Ks`,   false, false],
            ['Net Amount',      `${netAmount.toLocaleString()} Ks`,       true,  false],
            ['Paid',            `${paid.toLocaleString()} Ks`,            false, false],
            ['Due',             `${dueAmount.toLocaleString()} Ks`,       false, dueAmount > 0],
          ] as [string, string, boolean, boolean][]).map(([label, value, bold, red]) => (
            <View key={label} style={st.summaryRow}>
              <Text style={bold ? st.summaryKeyBold : st.summaryKey}>{label}</Text>
              <Text style={[bold ? st.summaryValBold : st.summaryVal, red ? { color: C.danger } : {}]}>{value}</Text>
            </View>
          ))}
        </View>

        <TouchableOpacity style={[st.submitBtn, submitting && { opacity: 0.7 }]} onPress={handleSubmit} disabled={submitting}>
          {submitting ? <ActivityIndicator color="#fff" /> : <Text style={st.submitBtnText}>💾  Save Sale</Text>}
        </TouchableOpacity>
      </ScrollView>

      {/* Product scanner */}
      <ScannerModal
        visible={showScanner}
        onDetected={handleScan}
        onClose={() => setScanner(false)}
        title="Scan Product Barcode / Serial"
      />

      {/* Serial scanner for specific cart item */}
      <ScannerModal
        visible={serialScanIdx !== null}
        onDetected={handleSerialScan}
        onClose={() => setSerialScanIdx(null)}
        title={`Scan Serial — ${serialScanIdx !== null ? (cart[serialScanIdx]?.product.name ?? '') : ''}`}
      />

      <PickerModal visible={showProductPicker} title="Select Product" items={products}
        labelKey="name" onSelect={p => addToCart(p)} onClose={() => setProductPicker(false)} />

      <PickerModal
        visible={showCustomerPicker}
        title="Select Customer"
        items={customers}
        labelKey="name"
        onSelect={setCustomer}
        onClose={() => setCustomerPicker(false)}
        extraHeader={
          <TouchableOpacity style={pm.newBtn} onPress={() => { setCustomerPicker(false); setNewCustomer(true); }}>
            <Text style={{ fontSize: 18 }}>➕</Text>
            <Text style={pm.newText}>New Customer</Text>
          </TouchableOpacity>
        }
      />

      <PickerModal visible={showStaffPicker} title="Select Staff" items={staffList}
        labelKey="name" onSelect={setStaff} onClose={() => setStaffPicker(false)} />
      <PickerModal visible={showPayPicker} title="Payment Method" items={payMethods}
        labelKey="methodName" onSelect={setPayMethod} onClose={() => setPayPicker(false)} />

      <NewCustomerModal
        visible={showNewCustomer}
        onClose={() => setNewCustomer(false)}
        onCreated={c => {
          setCustomers(prev => [...prev, c]);
          setCustomer(c);
        }}
      />
    </KeyboardAvoidingView>
  );
}

const st = StyleSheet.create({
  root:            { flex: 1, backgroundColor: C.bg },
  section:         { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 12 },
  sectionHeader:   { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  sectionTitle:    { fontSize: 11, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 10 },
  picker:          { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: C.border },
  pickerLabel:     { fontSize: 13, fontWeight: '600', color: C.textMuted },
  pickerSub:       { fontSize: 11, color: C.textMuted, marginTop: 1 },
  pickerRight:     { flexDirection: 'row', alignItems: 'center', gap: 4 },
  pickerValue:     { fontSize: 14, fontWeight: '700', color: C.text },
  chevron:         { fontSize: 18, color: C.textMuted, lineHeight: 20 },
  addBtn:          { backgroundColor: C.primary, paddingHorizontal: 12, paddingVertical: 7, borderRadius: 8 },
  addBtnText:      { color: '#fff', fontWeight: '700', fontSize: 12 },
  emptyCart:       { alignItems: 'center', paddingVertical: 20, gap: 6 },
  emptyCartText:   { color: C.textMuted, fontSize: 13, fontWeight: '500' },
  cartItem:        { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.border },
  itemHeaderRow:   { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  itemName:        { fontSize: 13, fontWeight: '700', color: C.text, flex: 1, marginRight: 8 },
  codeChip:        { backgroundColor: C.primaryLight, borderRadius: 5, paddingHorizontal: 6, paddingVertical: 1 },
  codeText:        { fontSize: 10, fontWeight: '800', color: C.primary },
  itemPrice:       { fontSize: 12, color: C.textMuted, marginTop: 4 },
  qtyRow:          { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 8, justifyContent: 'flex-end' },
  qtyBtn:          { width: 28, height: 28, borderRadius: 8, backgroundColor: C.primaryLight, justifyContent: 'center', alignItems: 'center' },
  qtyBtnText:      { fontSize: 16, fontWeight: '700', color: C.primary },
  qty:             { fontSize: 15, fontWeight: '800', color: C.text, minWidth: 22, textAlign: 'center' },
  removeBtn:       { width: 26, height: 26, borderRadius: 6, backgroundColor: C.dangerBg, justifyContent: 'center', alignItems: 'center' },
  removeBtnText:   { fontSize: 12, fontWeight: '700', color: C.danger },
  // Serial section
  serialSection:   { marginTop: 8, backgroundColor: C.bg, borderRadius: 8, padding: 8, borderWidth: 1, borderColor: C.border },
  serialHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  serialLabel:     { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 },
  serialWarning:   { fontSize: 10, fontWeight: '700', color: C.danger },
  serialChips:     { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 8 },
  serialChip:      { flexDirection: 'row', alignItems: 'center', backgroundColor: C.successBg, borderRadius: 6, paddingHorizontal: 8, paddingVertical: 4, gap: 5 },
  serialChipText:  { fontSize: 11, fontWeight: '700', color: C.success },
  serialChipRemove:{ fontSize: 13, fontWeight: '800', color: C.danger },
  serialScanBtn:   { backgroundColor: C.primary, borderRadius: 8, paddingVertical: 9, alignItems: 'center' },
  serialScanBtnText:{ fontSize: 13, fontWeight: '700', color: '#fff' },
  lineDiscRow:     { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 6 },
  lineDiscLabel:   { fontSize: 11, fontWeight: '600', color: C.textMuted },
  lineDiscInput:   { backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 7, paddingHorizontal: 10, paddingVertical: 5, fontSize: 13, color: C.text, minWidth: 100, textAlign: 'right' },
  lineDiscNet:     { fontSize: 11, color: C.textMuted, textAlign: 'right', marginTop: 2 },
  // Payment / totals
  inputRow:        { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 10 },
  inputLabel:      { fontSize: 13, fontWeight: '600', color: C.textMuted },
  numInput:        { backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8, fontSize: 14, color: C.text, minWidth: 120, textAlign: 'right' },
  summary:         { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 14 },
  summaryRow:      { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 5 },
  summaryKey:      { fontSize: 13, color: C.textMuted, fontWeight: '600' },
  summaryVal:      { fontSize: 13, fontWeight: '700', color: C.text } as any,
  summaryKeyBold:  { fontSize: 14, fontWeight: '800', color: C.text },
  summaryValBold:  { fontSize: 16, fontWeight: '800', color: C.primary },
  submitBtn:       { backgroundColor: C.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center' },
  submitBtnText:   { color: '#fff', fontWeight: '800', fontSize: 16 },
});
