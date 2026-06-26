import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  View, Text, TextInput, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl, Alert, Modal, ScrollView,
  Image, KeyboardAvoidingView, Platform,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import { api } from '../../api/client';
import { ApiResponse, ProductSerialDTO, ProductDTO } from '../../types';
import ScannerModal from '../../components/ScannerModal';
import { C } from '../../theme';
import { useWsTopic } from '../../hooks/useWsTopic';
import { useAuth } from '../../context/AuthContext';
import { fmtSerialWarranty } from '../../utils/warrantyFormat';

// ── Status config ──────────────────────────────────────────────────────────────
const STATUS_ALL = ['Available', 'Sold', 'Used_In_Service', 'Damaged', 'Lost'] as const;
type SerialStatusVal = typeof STATUS_ALL[number];

const STATUS_CFG: Record<string, { bg: string; text: string; label: string }> = {
  Available:       { bg: C.successBg, text: C.success, label: 'Available' },
  Sold:            { bg: C.primaryLight, text: C.primary, label: 'Sold' },
  Used_In_Service: { bg: C.warningBg, text: C.warning, label: 'In Service' },
  Damaged:         { bg: C.dangerBg, text: C.danger, label: 'Damaged' },
  Lost:            { bg: '#F1F5F9', text: '#64748B', label: 'Lost' },
};

// ── Warranty helpers ───────────────────────────────────────────────────────────
const toMonths = (val: number, unit: 'ရက်' | 'လ' | 'နှစ်'): number => {
  if (unit === 'ရက်') return Math.round(val / 30);
  if (unit === 'နှစ်') return val * 12;
  return val;
};

const fromMonths = (m: number): { val: number; unit: 'ရက်' | 'လ' | 'နှစ်' } => {
  if (m <= 0) return { val: 0, unit: 'လ' };
  if (m % 12 === 0) return { val: m / 12, unit: 'နှစ်' };
  return { val: m, unit: 'လ' };
};

const fmtWarrantyShort = (m?: number): string => {
  if (!m || m <= 0) return '—';
  if (m % 12 === 0) return `${m / 12} နှစ်`;
  return `${m} လ`;
};

// ── Photo helper ───────────────────────────────────────────────────────────────
const pickAndResizeImage = async (): Promise<string | null> => {
  const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!perm.granted) {
    Alert.alert('Permission', 'ဓာတ်ပုံ library သုံးခွင့်မရပါ');
    return null;
  }
  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ImagePicker.MediaTypeOptions.Images,
    quality: 0.8,
  });
  if (result.canceled || !result.assets[0]) return null;
  const uri = result.assets[0].uri;
  const w = result.assets[0].width;
  const h = result.assets[0].height;
  const scale = Math.min(1, 400 / Math.max(w, h));
  const resized = await ImageManipulator.manipulateAsync(
    uri,
    [{ resize: { width: Math.round(w * scale), height: Math.round(h * scale) } }],
    { compress: 0.75, format: ImageManipulator.SaveFormat.JPEG, base64: true },
  );
  return resized.base64 ? `data:image/jpeg;base64,${resized.base64}` : null;
};

const takePhoto = async (): Promise<string | null> => {
  const perm = await ImagePicker.requestCameraPermissionsAsync();
  if (!perm.granted) {
    Alert.alert('Permission', 'ကင်မရာ သုံးခွင့်မရပါ');
    return null;
  }
  const result = await ImagePicker.launchCameraAsync({ quality: 0.8 });
  if (result.canceled || !result.assets[0]) return null;
  const uri = result.assets[0].uri;
  const w = result.assets[0].width;
  const h = result.assets[0].height;
  const scale = Math.min(1, 400 / Math.max(w, h));
  const resized = await ImageManipulator.manipulateAsync(
    uri,
    [{ resize: { width: Math.round(w * scale), height: Math.round(h * scale) } }],
    { compress: 0.75, format: ImageManipulator.SaveFormat.JPEG, base64: true },
  );
  return resized.base64 ? `data:image/jpeg;base64,${resized.base64}` : null;
};

// ── Empty form ─────────────────────────────────────────────────────────────────
const emptyForm = () => ({
  serialNumber: '',
  productId: undefined as number | undefined,
  status: 'Available' as SerialStatusVal,
  wVal: 0,
  wUnit: 'လ' as 'ရက်' | 'လ' | 'နှစ်',
  warrantyStartDate: '',
  condition: '',
  photoBase64: undefined as string | undefined,
});

// ══════════════════════════════════════════════════════════════════════════════
export default function ProductSerialScreen() {
  const { hasPermission } = useAuth();
  const [serials,    setSerials]    = useState<ProductSerialDTO[]>([]);
  const [products,   setProducts]   = useState<ProductDTO[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search,     setSearch]     = useState('');
  const [statusTab,  setStatusTab]  = useState<'ALL' | SerialStatusVal>('ALL');

  const [modalVisible, setModalVisible] = useState(false);
  const [editing,      setEditing]      = useState<ProductSerialDTO | null>(null);
  const [form,         setForm]         = useState(emptyForm());
  const [saving,       setSaving]       = useState(false);

  const [showScanner,    setShowScanner]    = useState(false);
  const [showProdPicker, setShowProdPicker] = useState(false);
  const [prodSearch,     setProdSearch]     = useState('');
  const [photoViewer,    setPhotoViewer]    = useState<string | null>(null);

  const canCreate = hasPermission('CAN_ACCESS_PRODUCT_SERIAL_CREATE');
  const canUpdate = hasPermission('CAN_ACCESS_PRODUCT_SERIAL_UPDATE');
  const canDelete = hasPermission('CAN_ACCESS_PRODUCT_SERIAL_DELETE');

  // ── Load ──────────────────────────────────────────────────────────────────
  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const [sRes, pRes] = await Promise.all([
        api.get<ApiResponse<ProductSerialDTO[]>>('/product-serials'),
        api.get<ApiResponse<ProductDTO[]>>('/products'),
      ]);
      setSerials(sRes.data ?? []);
      setProducts(pRes.data ?? []);
    } catch {}
    setLoading(false);
    setRefreshing(false);
  }, []);

  useEffect(() => { load(); }, [load]);
  useWsTopic('/topic/productSerial', () => load(true));

  // ── Filter ────────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return serials.filter(s => {
      const matchSearch = !q
        || s.serialNumber.toLowerCase().includes(q)
        || (s.productName ?? '').toLowerCase().includes(q);
      const matchTab = statusTab === 'ALL' || s.status === statusTab;
      return matchSearch && matchTab;
    });
  }, [serials, search, statusTab]);

  // ── Status counts ─────────────────────────────────────────────────────────
  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: serials.length };
    STATUS_ALL.forEach(s => { c[s] = serials.filter(x => x.status === s).length; });
    return c;
  }, [serials]);

  // ── Open modal ────────────────────────────────────────────────────────────
  const openAdd = () => {
    setEditing(null);
    setForm(emptyForm());
    setModalVisible(true);
  };

  const openEdit = (s: ProductSerialDTO) => {
    const { val, unit } = fromMonths(s.warrantyMonths ?? 0);
    setEditing(s);
    setForm({
      serialNumber: s.serialNumber,
      productId: s.productId,
      status: (s.status as SerialStatusVal) ?? 'Available',
      wVal: val,
      wUnit: unit,
      warrantyStartDate: s.warrantyStartDate ? String(s.warrantyStartDate).slice(0, 10) : '',
      condition: s.condition ?? '',
      photoBase64: s.photoBase64,
    });
    setModalVisible(true);
  };

  // ── Save ──────────────────────────────────────────────────────────────────
  const handleSave = async () => {
    if (!form.serialNumber.trim()) { Alert.alert('Required', 'Serial number ထည့်ပါ'); return; }
    if (!form.productId) { Alert.alert('Required', 'Product ရွေးပါ'); return; }
    setSaving(true);
    try {
      const payload = {
        serialNumber: form.serialNumber.trim().toUpperCase(),
        productId: form.productId,
        status: form.status,
        warrantyMonths: toMonths(form.wVal, form.wUnit),
        warrantyStartDate: form.warrantyStartDate || undefined,
        condition: form.condition.trim() || undefined,
        photoBase64: form.photoBase64,
      };
      if (editing) {
        await api.put(`/product-serials/${editing.id}`, payload);
      } else {
        await api.post('/product-serials', payload);
      }
      setModalVisible(false);
      load(true);
    } catch (e: any) { Alert.alert('Error', e?.message ?? 'Save failed'); }
    setSaving(false);
  };

  // ── Delete ────────────────────────────────────────────────────────────────
  const handleDelete = (s: ProductSerialDTO) => {
    Alert.alert('Delete Serial?', `"${s.serialNumber}" ကို ဖျက်မည်လား?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: async () => {
        try {
          await api.delete(`/product-serials/${s.id}`);
          load(true);
        } catch (e: any) { Alert.alert('Error', e?.message ?? 'Delete failed'); }
      }},
    ]);
  };

  // ── Photo helpers ─────────────────────────────────────────────────────────
  const handlePhoto = () => {
    Alert.alert('Photo', 'ဘယ်လိုရယူမလဲ?', [
      { text: 'ကင်မရာ', onPress: async () => { const b64 = await takePhoto(); if (b64) setForm(p => ({ ...p, photoBase64: b64 })); } },
      { text: 'Gallery', onPress: async () => { const b64 = await pickAndResizeImage(); if (b64) setForm(p => ({ ...p, photoBase64: b64 })); } },
      { text: 'Cancel', style: 'cancel' },
    ]);
  };

  // ── Product picker ────────────────────────────────────────────────────────
  const filteredProds = useMemo(() => {
    const q = prodSearch.toLowerCase();
    return products.filter(p =>
      p.name.toLowerCase().includes(q) || p.productCode.toLowerCase().includes(q)
    );
  }, [products, prodSearch]);

  const selectedProduct = products.find(p => p.id === form.productId);

  // ── Render serial row ─────────────────────────────────────────────────────
  const renderItem = ({ item }: { item: ProductSerialDTO }) => {
    const cfg = STATUS_CFG[item.status] ?? STATUS_CFG['Lost'];
    return (
      <View style={st.card}>
        <View style={st.cardRow}>
          {item.photoBase64
            ? <TouchableOpacity onPress={() => setPhotoViewer(item.photoBase64!)}>
                <Image source={{ uri: item.photoBase64 }} style={st.thumb} />
              </TouchableOpacity>
            : <View style={st.thumbPlaceholder}>
                <Text style={{ fontSize: 18 }}>🔖</Text>
              </View>
          }
          <View style={{ flex: 1 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={st.snText}>{item.serialNumber}</Text>
              <View style={[st.badge, { backgroundColor: cfg.bg }]}>
                <Text style={[st.badgeText, { color: cfg.text }]}>{cfg.label}</Text>
              </View>
            </View>
            <Text style={st.prodName}>{item.productName ?? '—'}</Text>
            <View style={{ flexDirection: 'row', gap: 12, marginTop: 3 }}>
              {(item.warrantyMonths ?? 0) > 0 && (
                <Text style={st.metaText}>
                  🛡 {fmtSerialWarranty(item.warrantyMonths)}
                </Text>
              )}
              {item.condition ? <Text style={st.metaText}>📋 {item.condition}</Text> : null}
            </View>
            {item.supplierName && (
              <Text style={[st.metaText, { marginTop: 2 }]}>
                🏪 {item.supplierName}{item.purchaseCode ? ` · ${item.purchaseCode}` : ''}
              </Text>
            )}
          </View>
        </View>
        {(canUpdate || canDelete) && (
          <View style={st.actionRow}>
            {canUpdate && (
              <TouchableOpacity style={st.editBtn} onPress={() => openEdit(item)}>
                <Text style={st.editBtnText}>✏️ Edit</Text>
              </TouchableOpacity>
            )}
            {canDelete && (
              <TouchableOpacity style={st.deleteBtn} onPress={() => handleDelete(item)}>
                <Text style={st.deleteBtnText}>🗑 Delete</Text>
              </TouchableOpacity>
            )}
          </View>
        )}
      </View>
    );
  };

  // ═══════════════════════════════════════════════════════════════════════════
  return (
    <View style={{ flex: 1, backgroundColor: C.bg }}>

      {/* Search + Add */}
      <View style={st.topBar}>
        <TextInput
          style={st.searchInput}
          placeholder="Serial / Product နာမည် ရှာပါ..."
          placeholderTextColor={C.textMuted}
          value={search}
          onChangeText={setSearch}
        />
        {canCreate && (
          <TouchableOpacity style={st.addBtn} onPress={openAdd}>
            <Text style={st.addBtnText}>+ Add</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Status tabs */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={st.tabScroll} contentContainerStyle={{ paddingHorizontal: 12, gap: 6 }}>
        {(['ALL', ...STATUS_ALL] as const).map(s => {
          const active = statusTab === s;
          const cfg = s === 'ALL' ? null : STATUS_CFG[s];
          return (
            <TouchableOpacity
              key={s}
              style={[st.tab, active && { backgroundColor: cfg?.text ?? C.primary, borderColor: cfg?.text ?? C.primary }]}
              onPress={() => setStatusTab(s)}
            >
              <Text style={[st.tabText, active && { color: '#fff' }]}>
                {s === 'ALL' ? 'All' : (cfg?.label ?? s)}
                {' '}({counts[s] ?? 0})
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {/* List */}
      {loading
        ? <ActivityIndicator color={C.primary} size="large" style={{ marginTop: 40 }} />
        : <FlatList
            data={filtered}
            keyExtractor={i => String(i.id)}
            renderItem={renderItem}
            contentContainerStyle={{ padding: 12, paddingBottom: 40 }}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={() => { setRefreshing(true); load(true); }}
                colors={[C.primary]} tintColor={C.primary}
              />
            }
            ListEmptyComponent={
              <Text style={st.empty}>
                {search ? 'ရှာမတွေ့ပါ' : 'Serial number မရှိသေးပါ'}
              </Text>
            }
          />
      }

      {/* Photo viewer */}
      {photoViewer && (
        <Modal visible animationType="fade" transparent onRequestClose={() => setPhotoViewer(null)}>
          <TouchableOpacity style={st.photoOverlay} activeOpacity={1} onPress={() => setPhotoViewer(null)}>
            <Image source={{ uri: photoViewer }} style={st.photoFull} resizeMode="contain" />
            <Text style={{ color: '#fff', marginTop: 12, fontWeight: '700' }}>Tap to close</Text>
          </TouchableOpacity>
        </Modal>
      )}

      {/* Product Picker Modal */}
      <Modal visible={showProdPicker} animationType="slide" onRequestClose={() => setShowProdPicker(false)}>
        <View style={{ flex: 1, backgroundColor: C.bg }}>
          <View style={st.modalHeader}>
            <Text style={st.modalTitle}>Product ရွေးပါ</Text>
            <TouchableOpacity onPress={() => setShowProdPicker(false)}><Text style={st.modalClose}>✕</Text></TouchableOpacity>
          </View>
          <TextInput
            style={st.modalSearch}
            placeholder="Search name / code..."
            placeholderTextColor={C.textMuted}
            value={prodSearch}
            onChangeText={setProdSearch}
          />
          <FlatList
            data={filteredProds}
            keyExtractor={p => String(p.id)}
            renderItem={({ item }) => (
              <TouchableOpacity
                style={st.pickerItem}
                onPress={() => {
                  setForm(f => ({ ...f, productId: item.id }));
                  setProdSearch('');
                  setShowProdPicker(false);
                }}
              >
                <Text style={st.pickerItemName}>{item.name}</Text>
                <Text style={st.pickerItemSub}>
                  {item.productCode}  ·  {item.sellingPrice.toLocaleString()} Ks
                  {item.hasSerial ? '  · Serial tracked' : ''}
                </Text>
              </TouchableOpacity>
            )}
            ListEmptyComponent={<Text style={st.empty}>ထုတ်ကုန် မတွေ့ပါ</Text>}
          />
        </View>
      </Modal>

      {/* Scanner */}
      <ScannerModal
        visible={showScanner}
        onDetected={code => { setForm(f => ({ ...f, serialNumber: code.trim().toUpperCase() })); setShowScanner(false); }}
        onClose={() => setShowScanner(false)}
        title="Serial Number Scan"
      />

      {/* Add / Edit Modal */}
      <Modal visible={modalVisible} animationType="slide" onRequestClose={() => setModalVisible(false)}>
        <KeyboardAvoidingView
          style={{ flex: 1, backgroundColor: C.bg }}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
          <View style={st.modalHeader}>
            <Text style={st.modalTitle}>{editing ? 'Serial Edit' : 'Serial အသစ်'}</Text>
            <TouchableOpacity onPress={() => setModalVisible(false)}><Text style={st.modalClose}>✕</Text></TouchableOpacity>
          </View>

          <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 40 }}>

            {/* Serial Number */}
            <Text style={st.label}>Serial Number *</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <TextInput
                style={[st.input, { flex: 1 }]}
                value={form.serialNumber}
                onChangeText={v => setForm(f => ({ ...f, serialNumber: v.toUpperCase() }))}
                placeholder="e.g. SN-8829-XL"
                placeholderTextColor={C.textMuted}
                autoCapitalize="characters"
              />
              <TouchableOpacity style={st.scanBtn} onPress={() => setShowScanner(true)}>
                <Text style={st.scanBtnText}>📷</Text>
              </TouchableOpacity>
            </View>

            {/* Product */}
            <Text style={st.label}>Product *</Text>
            <TouchableOpacity style={st.picker} onPress={() => setShowProdPicker(true)}>
              <Text style={[st.pickerVal, !selectedProduct && { color: C.textMuted }]}>
                {selectedProduct ? `${selectedProduct.name} (${selectedProduct.productCode})` : 'Product ရွေးပါ...'}
              </Text>
              <Text style={{ color: C.textMuted, fontSize: 18 }}>›</Text>
            </TouchableOpacity>

            {/* Status */}
            <Text style={st.label}>Status</Text>
            <View style={st.statusGrid}>
              {STATUS_ALL.map(s => {
                const cfg = STATUS_CFG[s];
                const active = form.status === s;
                return (
                  <TouchableOpacity
                    key={s}
                    style={[st.statusBtn, active && { backgroundColor: cfg.text, borderColor: cfg.text }]}
                    onPress={() => setForm(f => ({ ...f, status: s }))}
                  >
                    <Text style={[st.statusBtnText, active && { color: '#fff' }]}>{cfg.label}</Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            {/* Warranty */}
            <Text style={st.label}>အာမခံကာလ</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <TextInput
                style={[st.input, { flex: 1 }]}
                value={form.wVal > 0 ? String(form.wVal) : ''}
                onChangeText={v => setForm(f => ({ ...f, wVal: Math.max(0, parseInt(v) || 0) }))}
                keyboardType="numeric"
                placeholder="0"
                placeholderTextColor={C.textMuted}
              />
              <View style={[st.input, { paddingHorizontal: 0, justifyContent: 'center' }]}>
                {(['ရက်', 'လ', 'နှစ်'] as const).map(u => (
                  <TouchableOpacity
                    key={u}
                    style={[st.unitBtn, form.wUnit === u && st.unitBtnActive]}
                    onPress={() => setForm(f => ({ ...f, wUnit: u }))}
                  >
                    <Text style={[st.unitBtnText, form.wUnit === u && st.unitBtnTextActive]}>{u}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
            {form.wVal > 0 && (
              <Text style={st.hint}>= {toMonths(form.wVal, form.wUnit)} လ</Text>
            )}

            {/* Warranty Start Date */}
            <Text style={st.label}>Warranty Start Date</Text>
            <TextInput
              style={st.input}
              value={form.warrantyStartDate}
              onChangeText={v => setForm(f => ({ ...f, warrantyStartDate: v }))}
              placeholder="YYYY-MM-DD"
              placeholderTextColor={C.textMuted}
            />

            {/* Condition */}
            <Text style={st.label}>Condition</Text>
            <TextInput
              style={st.input}
              value={form.condition}
              onChangeText={v => setForm(f => ({ ...f, condition: v }))}
              placeholder="e.g. New, Good, Scratched..."
              placeholderTextColor={C.textMuted}
            />

            {/* Photo */}
            <Text style={st.label}>Photo</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              {form.photoBase64
                ? <TouchableOpacity onPress={() => setPhotoViewer(form.photoBase64!)}>
                    <Image source={{ uri: form.photoBase64 }} style={st.photoPreview} />
                  </TouchableOpacity>
                : <View style={st.photoEmpty}><Text style={{ color: C.textMuted, fontSize: 24 }}>📷</Text></View>
              }
              <View style={{ gap: 6 }}>
                <TouchableOpacity style={st.photoBtn} onPress={handlePhoto}>
                  <Text style={st.photoBtnText}>{form.photoBase64 ? '📷 Change' : '📷 Upload'}</Text>
                </TouchableOpacity>
                {form.photoBase64 && (
                  <TouchableOpacity
                    style={[st.photoBtn, { backgroundColor: C.dangerBg }]}
                    onPress={() => setForm(f => ({ ...f, photoBase64: undefined }))}
                  >
                    <Text style={[st.photoBtnText, { color: C.danger }]}>✕ Remove</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>

            {/* Buttons */}
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 24 }}>
              <TouchableOpacity style={st.cancelBtn} onPress={() => setModalVisible(false)}>
                <Text style={st.cancelBtnText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[st.saveBtn, saving && { opacity: 0.6 }]}
                onPress={handleSave}
                disabled={saving}
              >
                {saving
                  ? <ActivityIndicator color="#fff" size="small" />
                  : <Text style={st.saveBtnText}>💾 Save</Text>
                }
              </TouchableOpacity>
            </View>

          </ScrollView>
        </KeyboardAvoidingView>
      </Modal>
    </View>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────────
const st = StyleSheet.create({
  topBar:       { flexDirection: 'row', gap: 8, padding: 12, backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border },
  searchInput:  { flex: 1, backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
  addBtn:       { backgroundColor: C.primary, borderRadius: 10, paddingHorizontal: 16, paddingVertical: 9, justifyContent: 'center' },
  addBtnText:   { color: '#fff', fontWeight: '800', fontSize: 13 },
  tabScroll:    { backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border, paddingVertical: 8, flexGrow: 0 },
  tab:          { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, borderWidth: 1, borderColor: C.border, backgroundColor: C.bg },
  tabText:      { fontSize: 11, fontWeight: '700', color: C.textMuted },
  card:         { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 8 },
  cardRow:      { flexDirection: 'row', gap: 12, alignItems: 'flex-start' },
  thumb:        { width: 48, height: 48, borderRadius: 10, borderWidth: 1, borderColor: C.border },
  thumbPlaceholder: { width: 48, height: 48, borderRadius: 10, backgroundColor: C.primaryLight, justifyContent: 'center', alignItems: 'center' },
  snText:       { fontSize: 14, fontWeight: '800', color: C.text },
  prodName:     { fontSize: 12, color: C.textMuted, fontWeight: '600', marginTop: 2 },
  metaText:     { fontSize: 11, color: C.textMuted, fontWeight: '600' },
  badge:        { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 8 },
  badgeText:    { fontSize: 10, fontWeight: '800' },
  actionRow:    { flexDirection: 'row', gap: 8, marginTop: 10, paddingTop: 8, borderTopWidth: 1, borderTopColor: C.border },
  editBtn:      { flex: 1, backgroundColor: C.primaryLight, paddingVertical: 7, borderRadius: 8, alignItems: 'center' },
  editBtnText:  { fontSize: 12, fontWeight: '700', color: C.primary },
  deleteBtn:    { flex: 1, backgroundColor: C.dangerBg, paddingVertical: 7, borderRadius: 8, alignItems: 'center' },
  deleteBtnText:{ fontSize: 12, fontWeight: '700', color: C.danger },
  empty:        { textAlign: 'center', color: C.textMuted, marginTop: 40, fontSize: 14 },
  photoOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', alignItems: 'center' },
  photoFull:    { width: '90%', height: '60%', borderRadius: 12 },
  modalHeader:  { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.primary, paddingHorizontal: 16, paddingVertical: 14, paddingTop: 48 },
  modalTitle:   { color: '#fff', fontWeight: '700', fontSize: 15, flex: 1 },
  modalClose:   { color: '#fff', fontSize: 18, paddingHorizontal: 8 },
  modalSearch:  { margin: 12, backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: C.text },
  pickerItem:   { paddingHorizontal: 16, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border },
  pickerItemName: { fontSize: 14, fontWeight: '600', color: C.text },
  pickerItemSub: { fontSize: 11, color: C.textMuted, marginTop: 2 },
  label:        { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.5, marginTop: 16, marginBottom: 6 },
  input:        { backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 11, fontSize: 14, color: C.text, flexDirection: 'row' },
  picker:       { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.card, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 11 },
  pickerVal:    { fontSize: 14, fontWeight: '600', color: C.text, flex: 1 },
  statusGrid:   { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  statusBtn:    { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8, borderWidth: 1, borderColor: C.border, backgroundColor: C.bg },
  statusBtnText:{ fontSize: 11, fontWeight: '700', color: C.textMuted },
  scanBtn:      { width: 48, backgroundColor: C.violetBg, borderRadius: 10, justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: C.border },
  scanBtnText:  { fontSize: 18 },
  unitBtn:      { paddingHorizontal: 10, paddingVertical: 8 },
  unitBtnActive:{ backgroundColor: C.primary, borderRadius: 6 },
  unitBtnText:  { fontSize: 13, fontWeight: '700', color: C.textMuted },
  unitBtnTextActive: { color: '#fff' },
  hint:         { fontSize: 11, color: C.primary, fontWeight: '600', marginTop: 4, marginLeft: 4 },
  photoPreview: { width: 64, height: 64, borderRadius: 10, borderWidth: 1, borderColor: C.border },
  photoEmpty:   { width: 64, height: 64, borderRadius: 10, backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderStyle: 'dashed', justifyContent: 'center', alignItems: 'center' },
  photoBtn:     { backgroundColor: C.primaryLight, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 8 },
  photoBtnText: { fontSize: 12, fontWeight: '700', color: C.primary },
  cancelBtn:    { flex: 1, borderWidth: 1, borderColor: C.border, borderRadius: 12, paddingVertical: 14, alignItems: 'center', backgroundColor: C.card },
  cancelBtnText:{ color: C.textMuted, fontWeight: '700' },
  saveBtn:      { flex: 2, backgroundColor: C.success, borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  saveBtnText:  { color: '#fff', fontWeight: '800', fontSize: 15 },
});
