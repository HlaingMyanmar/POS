import React, { useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert, Image, Modal,
} from 'react-native';

const LOGO = require('../../assets/logo.png');
import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import { C } from '../../theme';
import { CartItem, CustomerDTO, StaffDTO, PaymentMethodDTO, SaleDTO } from '../../types';
import { buildSaleVoucherHtml, PrintFormat, VoucherData } from '../../utils/saleVoucherHtml';

const STATUS_COLOR: Record<string, { bg: string; text: string }> = {
  PAID:    { bg: C.successBg, text: C.success },
  PARTIAL: { bg: C.warningBg, text: C.warning },
  PENDING: { bg: C.dangerBg,  text: C.danger  },
};

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <View style={st.row}>
      <Text style={bold ? st.rowKeyBold : st.rowKey}>{label}</Text>
      <Text style={bold ? st.rowValBold : st.rowVal}>{value}</Text>
    </View>
  );
}

function Divider() {
  return <View style={st.divider} />;
}

export default function VoucherScreen({ route, navigation }: any) {
  const { sale, cart, customer, staff, payMethod }: {
    sale: SaleDTO;
    cart: CartItem[];
    customer: CustomerDTO;
    staff: StaffDTO;
    payMethod: PaymentMethodDTO | null;
  } = route.params;

  const [fmtModal, setFmtModal] = useState(false);
  const [printing, setPrinting] = useState(false);

  const status = sale.paymentStatus ?? (
    (sale.dueAmount ?? 0) <= 0 ? 'PAID' : (sale.paidAmount ?? 0) > 0 ? 'PARTIAL' : 'PENDING'
  );
  const statusStyle = STATUS_COLOR[status] ?? STATUS_COLOR.PAID;

  const formatDate = (d?: string) => {
    if (!d) return new Date().toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    return new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  const doPrint = async (format: PrintFormat) => {
    setFmtModal(false);
    setPrinting(true);
    try {
      const data: VoucherData = {
        voucherNo: sale.saleCode ?? `#${sale.id}`,
        saleDate: sale.saleDate,
        customerName: customer.name,
        customerPhone: customer.phone,
        staffName: staff.name,
        paymentMethodName: payMethod?.methodName,
        paymentStatus: status,
        items: cart.map(item => ({
          name: item.product.name,
          qty: item.qty,
          unitPrice: item.unitPrice,
          subtotal: item.qty * item.unitPrice,
          serialNumbers: (item as any).serialNumbers,
          warrantyMonths: (item as any).warrantyMonths ?? item.product.warrantyMonths,
          warrantyExpiryDate: (item as any).warrantyExpiryDate,
        })),
        totalAmount: sale.totalAmount,
        discountAmount: sale.discountAmount,
        netAmount: sale.netAmount,
        paidAmount: sale.paidAmount,
        dueAmount: sale.dueAmount,
        remark: sale.remark,
      };
      const html = buildSaleVoucherHtml(data, format);
      const { uri } = await Print.printToFileAsync({ html });
      await Sharing.shareAsync(uri, { mimeType: 'application/pdf', dialogTitle: 'Print / Save PDF' });
    } catch (e: any) {
      if (!String(e?.message ?? '').includes('cancel')) {
        Alert.alert('Print Error', e?.message ?? 'Cannot print');
      }
    }
    setPrinting(false);
  };

  return (
    <View style={st.root}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32 }}>

        {/* Header */}
        <View style={st.voucherCard}>
          {/* Store header */}
          <View style={st.storeHeader}>
            <Image source={LOGO} style={st.storeLogo} resizeMode="contain" />
            <View>
              <Text style={st.storeName}>SSPD Manager</Text>
              <Text style={st.storeTagline}>Sales Receipt</Text>
            </View>
            <View style={[st.statusBadge, { backgroundColor: statusStyle.bg }]}>
              <Text style={[st.statusText, { color: statusStyle.text }]}>{status}</Text>
            </View>
          </View>

          <Divider />

          {/* Sale meta */}
          <Row label="Voucher No" value={sale.saleCode ?? `#${sale.id}`} bold />
          <Row label="Date"       value={formatDate(sale.saleDate)} />
          <Row label="Customer"   value={customer.name + (customer.phone ? ` · ${customer.phone}` : '')} />
          <Row label="Staff"      value={staff.name} />
          {payMethod && <Row label="Payment" value={payMethod.methodName} />}

          <Divider />

          {/* Items */}
          <Text style={st.itemsHeader}>ITEMS</Text>
          {cart.map((item, i) => (
            <View key={i} style={st.itemRow}>
              <View style={{ flex: 1 }}>
                <Text style={st.itemName} numberOfLines={2}>{item.product.name}</Text>
                <Text style={st.itemMeta}>{item.product.productCode} · {item.qty} × {item.unitPrice.toLocaleString()} Ks</Text>
              </View>
              <Text style={st.itemTotal}>{(item.qty * item.unitPrice).toLocaleString()}</Text>
            </View>
          ))}

          <Divider />

          {/* Totals */}
          <Row label="Subtotal"   value={`${(sale.totalAmount ?? 0).toLocaleString()} Ks`} />
          {(sale.discountAmount ?? 0) > 0 && (
            <Row label="Discount" value={`− ${(sale.discountAmount ?? 0).toLocaleString()} Ks`} />
          )}
          <Row label="Net Amount" value={`${(sale.netAmount ?? 0).toLocaleString()} Ks`} bold />
          <Row label="Paid"       value={`${(sale.paidAmount ?? 0).toLocaleString()} Ks`} />
          {(sale.dueAmount ?? 0) > 0 && (
            <View style={st.row}>
              <Text style={st.rowKey}>Due</Text>
              <Text style={[st.rowVal, { color: C.danger, fontWeight: '800' }]}>{(sale.dueAmount ?? 0).toLocaleString()} Ks</Text>
            </View>
          )}

          {sale.remark ? (
            <>
              <Divider />
              <Text style={st.remarkLabel}>REMARK</Text>
              <Text style={st.remarkText}>{sale.remark}</Text>
            </>
          ) : null}

          <Divider />
          <Text style={st.thanks}>Thank you for your purchase!</Text>
        </View>
      </ScrollView>

      {/* Format picker modal */}
      <Modal visible={fmtModal} transparent animationType="fade" onRequestClose={() => setFmtModal(false)}>
        <TouchableOpacity style={st.overlay} activeOpacity={1} onPress={() => setFmtModal(false)}>
          <View style={st.fmtBox}>
            <Text style={st.fmtTitle}>Select Print Format</Text>
            {([
              { fmt: 'POS' as PrintFormat, label: '🧾  POS (80mm)', sub: 'Thermal receipt printer' },
              { fmt: 'A5'  as PrintFormat, label: '📄  A5',          sub: 'Half-page invoice' },
              { fmt: 'A4'  as PrintFormat, label: '📋  A4',          sub: 'Full-page invoice' },
            ] as const).map(o => (
              <TouchableOpacity key={o.fmt} style={st.fmtRow} onPress={() => doPrint(o.fmt)}>
                <Text style={st.fmtLabel}>{o.label}</Text>
                <Text style={st.fmtSub}>{o.sub}</Text>
              </TouchableOpacity>
            ))}
            <TouchableOpacity style={st.fmtCancel} onPress={() => setFmtModal(false)}>
              <Text style={{ color: C.textMuted, fontWeight: '700' }}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </TouchableOpacity>
      </Modal>

      {/* Buttons */}
      <View style={st.footer}>
        <TouchableOpacity style={st.printBtn} onPress={() => setFmtModal(true)} disabled={printing}>
          <Text style={st.printBtnText}>🖨️  Print</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={st.newSaleBtn}
          onPress={() => navigation.replace('NewSale')}
        >
          <Text style={st.newSaleBtnText}>➕  New Sale</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={st.doneBtn}
          onPress={() => navigation.navigate('SaleList')}
        >
          <Text style={st.doneBtnText}>✓  Done</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const st = StyleSheet.create({
  root:          { flex: 1, backgroundColor: C.bg },
  voucherCard:   { backgroundColor: C.card, borderRadius: 16, borderWidth: 1, borderColor: C.border, padding: 18 },
  storeHeader:   { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 14 },
  storeLogo:     { width: 44, height: 44, borderRadius: 10, backgroundColor: '#fff', borderWidth: 1, borderColor: C.border },
  storeName:     { fontSize: 16, fontWeight: '800', color: C.text },
  storeTagline:  { fontSize: 11, color: C.textMuted, fontWeight: '600' },
  statusBadge:   { marginLeft: 'auto' as any, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 },
  statusText:    { fontSize: 12, fontWeight: '800' },
  divider:       { height: 1, backgroundColor: C.border, marginVertical: 12 },
  row:           { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
  rowKey:        { fontSize: 12, color: C.textMuted, fontWeight: '600', flex: 1 },
  rowVal:        { fontSize: 12, fontWeight: '700', color: C.text, maxWidth: '60%' as any, textAlign: 'right' },
  rowKeyBold:    { fontSize: 13, fontWeight: '800', color: C.text, flex: 1 },
  rowValBold:    { fontSize: 15, fontWeight: '900', color: C.primary, textAlign: 'right' },
  itemsHeader:   { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 8 },
  itemRow:       { flexDirection: 'row', alignItems: 'flex-start', paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: C.border, gap: 8 },
  itemName:      { fontSize: 13, fontWeight: '700', color: C.text },
  itemMeta:      { fontSize: 11, color: C.textMuted, marginTop: 2 },
  itemTotal:     { fontSize: 13, fontWeight: '800', color: C.text, minWidth: 80, textAlign: 'right' },
  remarkLabel:   { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', marginBottom: 4 },
  remarkText:    { fontSize: 13, color: C.text, fontStyle: 'italic' },
  thanks:        { textAlign: 'center', color: C.textMuted, fontSize: 12, fontStyle: 'italic' },
  overlay:       { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  fmtBox:        { backgroundColor: C.card, borderTopLeftRadius: 18, borderTopRightRadius: 18, padding: 20, paddingBottom: 32 },
  fmtTitle:      { fontSize: 15, fontWeight: '800', color: C.text, marginBottom: 14, textAlign: 'center' },
  fmtRow:        { paddingVertical: 14, paddingHorizontal: 8, borderBottomWidth: 1, borderBottomColor: C.border },
  fmtLabel:      { fontSize: 15, fontWeight: '700', color: C.text },
  fmtSub:        { fontSize: 11, color: C.textMuted, marginTop: 2 },
  fmtCancel:     { marginTop: 14, alignItems: 'center', paddingVertical: 10 },
  footer:        { flexDirection: 'row', gap: 8, padding: 14, backgroundColor: C.card, borderTopWidth: 1, borderTopColor: C.border },
  printBtn:      { flex: 1, backgroundColor: C.violetBg, borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  printBtnText:  { color: C.violet, fontWeight: '800', fontSize: 13 },
  newSaleBtn:    { flex: 1, backgroundColor: C.primaryLight, borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  newSaleBtnText:{ color: C.primary, fontWeight: '800', fontSize: 13 },
  doneBtn:       { flex: 1, backgroundColor: C.primary, borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  doneBtnText:   { color: '#fff', fontWeight: '800', fontSize: 13 },
});
