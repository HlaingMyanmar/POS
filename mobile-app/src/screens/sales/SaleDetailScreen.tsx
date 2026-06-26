import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, Image, Modal } from 'react-native';
import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import { api } from '../../api/client';
import { ApiResponse, SaleDTO, ProductSerialDTO } from '../../types';
import { C } from '../../theme';
import { useAuth } from '../../context/AuthContext';
import { buildSaleVoucherHtml, PrintFormat, VoucherData } from '../../utils/saleVoucherHtml';
import { fmtSerialWarranty } from '../../utils/warrantyFormat';

const LOGO = require('../../assets/logo.png');

const STATUS_COLOR: Record<string, { bg: string; text: string }> = {
  PAID:    { bg: C.successBg, text: C.success },
  Paid:    { bg: C.successBg, text: C.success },
  PARTIAL: { bg: C.warningBg, text: C.warning },
  Partial: { bg: C.warningBg, text: C.warning },
  PENDING: { bg: C.dangerBg,  text: C.danger  },
  Pending: { bg: C.dangerBg,  text: C.danger  },
};

function Divider() { return <View style={{ height: 1, backgroundColor: C.border, marginVertical: 10 }} />; }

export default function SaleDetailScreen({ route, navigation }: any) {
  const { saleId } = route.params;
  const { hasPermission } = useAuth();
  const [sale,               setSale]               = useState<SaleDTO | null>(null);
  const [loading,            setLoading]            = useState(true);
  const [printing,           setPrinting]           = useState(false);
  const [fmtModal,           setFmtModal]           = useState(false);
  const [serialWarrantyMap,  setSerialWarrantyMap]  = useState<Record<string, ProductSerialDTO>>({});

  useEffect(() => {
    api.get<ApiResponse<SaleDTO>>(`/sales/${saleId}`)
      .then(r => {
        setSale(r.data);
        const allSerials = (r.data.details ?? []).flatMap(d => d.serialNumbers ?? []);
        if (allSerials.length > 0) {
          api.post<ApiResponse<ProductSerialDTO[]>>('/product-serials/by-serials', allSerials)
            .then(sr => {
              const m: Record<string, ProductSerialDTO> = {};
              (sr.data ?? []).forEach(s => { m[s.serialNumber] = s; });
              setSerialWarrantyMap(m);
            })
            .catch(() => {});
        }
      })
      .catch(() => Alert.alert('Error', 'Cannot load sale'))
      .finally(() => setLoading(false));
  }, [saleId]);

  if (loading) return <ActivityIndicator color={C.primary} style={{ marginTop: 80 }} size="large" />;
  if (!sale) return <Text style={{ textAlign: 'center', marginTop: 40, color: C.textMuted }}>Sale not found</Text>;

  const status = sale.paymentStatus ?? 'Pending';
  const col = STATUS_COLOR[status] ?? STATUS_COLOR['Pending'];

  const fmt = (d?: string) => d ? new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-';

  const doPrint = async (format: PrintFormat) => {
    if (!sale) return;
    setFmtModal(false);
    setPrinting(true);
    try {
      const data: VoucherData = {
        voucherNo: sale.saleCode ?? `#${sale.id}`,
        saleDate: sale.saleDate,
        customerName: sale.customerName,
        staffName: sale.staffName,
        paymentMethodName: sale.paymentMethodName,
        paymentStatus: status,
        items: (sale.details ?? []).map(d => ({
          name: d.productName ?? '',
          qty: d.qty,
          unitPrice: d.unitPrice ?? 0,
          subtotal: (d.unitPrice ?? 0) * d.qty,
          serialNumbers: d.serialNumbers,
          serialWarranties: (d.serialNumbers ?? []).length > 0
            ? Object.fromEntries(
                (d.serialNumbers ?? []).map(sn => {
                  const info = serialWarrantyMap[sn];
                  return [sn, info ? fmtSerialWarranty(info.warrantyMonths) : ''];
                })
              )
            : undefined,
          warrantyMonths: d.warrantyMonths,
          warrantyExpiryDate: d.warrantyExpiryDate,
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
    <View style={{ flex: 1, backgroundColor: C.bg }}>
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

      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 100 }}>
        <View style={st.card}>
          {/* Header */}
          <View style={st.header}>
            <Image source={LOGO} style={st.logo} resizeMode="contain" />
            <View style={{ flex: 1 }}>
              <Text style={st.saleCode}>{sale.saleCode ?? `#${sale.id}`}</Text>
              <Text style={st.dateText}>{fmt(sale.saleDate)}</Text>
            </View>
            <View style={[st.badge, { backgroundColor: col.bg }]}>
              <Text style={[st.badgeText, { color: col.text }]}>{status}</Text>
            </View>
          </View>
          <Divider />

          <Row label="Customer" value={sale.customerName ?? '-'} />
          <Row label="Staff"    value={sale.staffName ?? '-'} />
          {sale.paymentMethodName && <Row label="Payment" value={sale.paymentMethodName} />}

          <Divider />
          <Text style={st.section}>ITEMS</Text>
          {(sale.details ?? []).map((d, i) => (
            <View key={i} style={st.itemRow}>
              <View style={{ flex: 1 }}>
                <Text style={st.itemName}>{d.productName}</Text>
                <Text style={st.itemMeta}>{d.qty} × {(d.unitPrice ?? 0).toLocaleString()} Ks</Text>
                {(d.serialNumbers ?? []).length > 0
                  ? d.serialNumbers.map(sn => {
                      const info = serialWarrantyMap[sn];
                      const wLabel = info ? fmtSerialWarranty(info.warrantyMonths) : '';
                      return (
                        <View key={sn} style={{ marginTop: 2 }}>
                          <Text style={st.serial}>S/N: {sn}</Text>
                          {wLabel ? <Text style={st.warranty}>🛡 {wLabel}</Text> : null}
                        </View>
                      );
                    })
                  : (d.warrantyMonths ?? 0) > 0
                      ? <Text style={st.warranty}>🛡 {fmtSerialWarranty(d.warrantyMonths)}</Text>
                      : null
                }
              </View>
              <Text style={st.itemTotal}>{((d.unitPrice ?? 0) * d.qty).toLocaleString()}</Text>
            </View>
          ))}

          <Divider />
          <Row label="Subtotal"   value={`${(sale.totalAmount ?? 0).toLocaleString()} Ks`} />
          {(sale.discountAmount ?? 0) > 0 && <Row label="Discount" value={`− ${(sale.discountAmount ?? 0).toLocaleString()} Ks`} />}
          <Row label="Net Amount" value={`${(sale.netAmount ?? 0).toLocaleString()} Ks`} bold />
          <Row label="Paid"       value={`${(sale.paidAmount ?? 0).toLocaleString()} Ks`} />
          {(sale.dueAmount ?? 0) > 0 && (
            <View style={st.row}>
              <Text style={st.rowKey}>Due</Text>
              <Text style={[st.rowVal, { color: C.danger, fontWeight: '800' }]}>{(sale.dueAmount ?? 0).toLocaleString()} Ks</Text>
            </View>
          )}
          {sale.remark ? (<><Divider /><Text style={st.remark}>"{sale.remark}"</Text></>) : null}
        </View>
      </ScrollView>

      <View style={st.footer}>
        <TouchableOpacity style={st.printBtn} onPress={() => setFmtModal(true)} disabled={printing}>
          {printing
            ? <ActivityIndicator color={C.violet} size="small" />
            : <Text style={st.printBtnText}>🖨️  Print</Text>
          }
        </TouchableOpacity>
        {hasPermission('CAN_ACCESS_SALE_RETURN_CREATE') && (
          <TouchableOpacity
            style={st.returnBtn}
            onPress={() => navigation.navigate('NewSaleReturn', { sale })}
          >
            <Text style={st.returnBtnText}>↩  Return</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <View style={st.row}>
      <Text style={bold ? st.rowKeyBold : st.rowKey}>{label}</Text>
      <Text style={bold ? st.rowValBold : st.rowVal}>{value}</Text>
    </View>
  );
}

const st = StyleSheet.create({
  card:        { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 16 },
  header:      { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 12 },
  logo:        { width: 44, height: 44, borderRadius: 10, backgroundColor: '#fff', borderWidth: 1, borderColor: C.border },
  saleCode:    { fontSize: 15, fontWeight: '800', color: C.primary },
  dateText:    { fontSize: 11, color: C.textMuted },
  badge:       { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 },
  badgeText:   { fontSize: 12, fontWeight: '800' },
  section:     { fontSize: 10, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 8 },
  row:         { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
  rowKey:      { fontSize: 12, color: C.textMuted, fontWeight: '600' },
  rowVal:      { fontSize: 12, fontWeight: '700', color: C.text },
  rowKeyBold:  { fontSize: 13, fontWeight: '800', color: C.text },
  rowValBold:  { fontSize: 15, fontWeight: '900', color: C.primary },
  itemRow:     { flexDirection: 'row', paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: C.border, gap: 8 },
  itemName:    { fontSize: 13, fontWeight: '700', color: C.text },
  itemMeta:    { fontSize: 11, color: C.textMuted, marginTop: 2 },
  serial:      { fontSize: 10, color: C.primary, marginTop: 2 },
  warranty:    { fontSize: 10, color: '#0891B2', marginTop: 2, fontWeight: '600' },
  itemTotal:   { fontSize: 13, fontWeight: '800', color: C.text, minWidth: 80, textAlign: 'right' },
  remark:      { fontSize: 13, color: C.textMuted, fontStyle: 'italic', textAlign: 'center' },
  overlay:      { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  fmtBox:       { backgroundColor: C.card, borderTopLeftRadius: 18, borderTopRightRadius: 18, padding: 20, paddingBottom: 32 },
  fmtTitle:     { fontSize: 15, fontWeight: '800', color: C.text, marginBottom: 14, textAlign: 'center' },
  fmtRow:       { paddingVertical: 14, paddingHorizontal: 8, borderBottomWidth: 1, borderBottomColor: C.border },
  fmtLabel:     { fontSize: 15, fontWeight: '700', color: C.text },
  fmtSub:       { fontSize: 11, color: C.textMuted, marginTop: 2 },
  fmtCancel:    { marginTop: 14, alignItems: 'center', paddingVertical: 10 },
  footer:       { flexDirection: 'row', gap: 8, padding: 14, backgroundColor: C.card, borderTopWidth: 1, borderTopColor: C.border },
  printBtn:     { flex: 1, backgroundColor: C.violetBg, borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  printBtnText: { color: C.violet, fontWeight: '800', fontSize: 14 },
  returnBtn:    { flex: 1, backgroundColor: C.warningBg, borderRadius: 10, paddingVertical: 13, alignItems: 'center', borderWidth: 1, borderColor: C.warning },
  returnBtnText:{ color: C.warning, fontWeight: '800', fontSize: 14 },
});
