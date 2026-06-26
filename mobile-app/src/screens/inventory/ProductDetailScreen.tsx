import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, ScrollView, StyleSheet, TouchableOpacity,
  ActivityIndicator, Image, Alert, Dimensions, Modal,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';
import { ProductDTO, ProductSerialDTO, ProductType, ApiResponse } from '../../types';
import { fmtProductWarranty, fmtSerialWarranty } from '../../utils/warrantyFormat';
import { api } from '../../api/client';
import { C } from '../../theme';

const { width, height: screenHeight } = Dimensions.get('window');
const CARD_W = (width - 48) / 2;

const COND_COLORS: Record<string, { bg: string; text: string }> = {
  [ProductType.NEW]:        { bg: C.primaryLight, text: C.primary },
  [ProductType.SECOND]:     { bg: C.warningBg,    text: C.warning  },
  [ProductType.SECOND_NEW]: { bg: C.violetBg,     text: C.violet   },
};

const STATUS_COL: Record<string, { bg: string; text: string }> = {
  Available:       { bg: C.successBg, text: C.success },
  Sold:            { bg: C.dangerBg,  text: C.danger  },
  Used_In_Service: { bg: C.warningBg, text: C.warning },
  Damaged:         { bg: C.dangerBg,  text: C.danger  },
  Lost:            { bg: '#F1F5F9',   text: '#64748B' },
};

const MAX_DIM = 400;

async function compressPhoto(uri: string, origWidth: number, origHeight: number): Promise<string> {
  const isLandscape = origWidth >= origHeight;
  const action = isLandscape
    ? { resize: { width: Math.min(MAX_DIM, origWidth) } }
    : { resize: { height: Math.min(MAX_DIM, origHeight) } };

  const result = await manipulateAsync(uri, [action], {
    compress: 0.75,
    format: SaveFormat.JPEG,
    base64: true,
  });
  return `data:image/jpeg;base64,${result.base64}`;
}

export default function ProductDetailScreen({ route, navigation }: any) {
  const product: ProductDTO       = route.params?.product;
  const scannedSerial: string | undefined = route.params?.scannedSerial;

  const [serials,              setSerials]              = useState<ProductSerialDTO[]>([]);
  const [serialsLoading,       setSerialsLoading]       = useState(false);
  const [uploadingId,          setUploadingId]          = useState<number | null>(null);
  const [viewPhotoUri,         setViewPhotoUri]         = useState<string | null>(null);
  const [productPhoto,         setProductPhoto]         = useState<string | undefined>(product?.photoBase64 || undefined);
  const [uploadingProductPhoto, setUploadingProductPhoto] = useState(false);

  const loadSerials = useCallback(() => {
    if (!product?.hasSerial || !product?.id) return;
    setSerialsLoading(true);
    api.get<ApiResponse<ProductSerialDTO[]>>(`/product-serials/by-product/${product.id}`)
      .then(r => setSerials(r.data ?? []))
      .catch(() => {})
      .finally(() => setSerialsLoading(false));
  }, [product?.id]);

  useEffect(() => { loadSerials(); }, [loadSerials]);

  if (!product) return null;

  const avail = product.availableSerialCount ?? product.stockQty ?? product.currentStock ?? 0;
  const cond  = COND_COLORS[product.productType] ?? COND_COLORS[ProductType.NEW];

  const pickAndCompress = async (launcher: () => Promise<ImagePicker.ImagePickerResult>): Promise<string | null> => {
    const result = await launcher();
    if (result.canceled || !result.assets[0]) return null;
    const asset = result.assets[0];
    return compressPhoto(asset.uri, asset.width, asset.height);
  };

  const handlePickPhoto = (serial: ProductSerialDTO) => {
    Alert.alert('Add / Change Photo', 'Choose source', [
      {
        text: 'Camera',
        onPress: async () => {
          const perm = await ImagePicker.requestCameraPermissionsAsync();
          if (!perm.granted) { Alert.alert('Permission denied'); return; }
          const photoBase64 = await pickAndCompress(() =>
            ImagePicker.launchCameraAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images, quality: 1 })
          );
          if (photoBase64) await savePhoto(serial, photoBase64);
        },
      },
      {
        text: 'Gallery',
        onPress: async () => {
          const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
          if (!perm.granted) { Alert.alert('Permission denied'); return; }
          const photoBase64 = await pickAndCompress(() =>
            ImagePicker.launchImageLibraryAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images, quality: 1 })
          );
          if (photoBase64) await savePhoto(serial, photoBase64);
        },
      },
      { text: 'Cancel', style: 'cancel' },
    ]);
  };

  const savePhoto = async (serial: ProductSerialDTO, photoBase64: string) => {
    setUploadingId(serial.id);
    try {
      await api.put(`/product-serials/${serial.id}`, { ...serial, photoBase64 });
      setSerials(prev => prev.map(s => s.id === serial.id ? { ...s, photoBase64 } : s));
    } catch {
      Alert.alert('Error', 'Failed to save photo');
    } finally {
      setUploadingId(null);
    }
  };

  const handlePickProductPhoto = () => {
    Alert.alert('Product Photo', 'Choose source', [
      {
        text: 'Camera',
        onPress: async () => {
          const perm = await ImagePicker.requestCameraPermissionsAsync();
          if (!perm.granted) { Alert.alert('Permission denied'); return; }
          const photoBase64 = await pickAndCompress(() =>
            ImagePicker.launchCameraAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images, quality: 1 })
          );
          if (photoBase64) await saveProductPhoto(photoBase64);
        },
      },
      {
        text: 'Gallery',
        onPress: async () => {
          const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
          if (!perm.granted) { Alert.alert('Permission denied'); return; }
          const photoBase64 = await pickAndCompress(() =>
            ImagePicker.launchImageLibraryAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images, quality: 1 })
          );
          if (photoBase64) await saveProductPhoto(photoBase64);
        },
      },
      { text: 'Cancel', style: 'cancel' },
    ]);
  };

  const saveProductPhoto = async (photoBase64: string) => {
    setUploadingProductPhoto(true);
    try {
      await api.put(`/products/${product.id}/photo`, { photoBase64 });
      setProductPhoto(photoBase64);
    } catch {
      Alert.alert('Error', 'Failed to save product photo');
    } finally {
      setUploadingProductPhoto(false);
    }
  };

  const rows: [string, string][] = [
    ['Product Code',  product.productCode],
    ['Category',      product.categoryName  ?? '—'],
    ['Brand',         product.brandName     ?? '—'],
    ['Unit',          product.unitName      ?? '—'],
    ['Selling Price', `${product.sellingPrice.toLocaleString()} Ks`],
    ['Available',     String(avail)],
    ['Reorder Level', product.reorderLevel ? String(product.reorderLevel) : '—'],
    ['Warranty',      fmtProductWarranty(product.warrantyTerms, product.warrantyMonths) || '—'],
  ];

  const visibleSerials = product.hasSerial ? serials : [];

  return (
    <>
      <ScrollView style={st.root} contentContainerStyle={{ padding: 16, paddingBottom: 40 }}>

        {/* Header card */}
        <View style={st.headerCard}>
          {/* Product photo — tappable */}
          <View style={st.productPhotoWrap}>
            {uploadingProductPhoto ? (
              <View style={st.productPhotoPlaceholder}>
                <ActivityIndicator color={C.primary} size="large" />
              </View>
            ) : productPhoto ? (
              <>
                <Image source={{ uri: productPhoto }} style={st.photo} resizeMode="contain" />
                <View style={st.productPhotoBtnRow}>
                  <TouchableOpacity style={st.productPhotoBtn} onPress={handlePickProductPhoto} activeOpacity={0.8}>
                    <Text style={st.productPhotoBtnText}>📷</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={[st.productPhotoBtn, st.productPhotoBtnView]} onPress={() => setViewPhotoUri(productPhoto)} activeOpacity={0.8}>
                    <Text style={st.productPhotoBtnText}>👁</Text>
                  </TouchableOpacity>
                </View>
              </>
            ) : (
              <TouchableOpacity style={st.productPhotoPlaceholder} onPress={handlePickProductPhoto} activeOpacity={0.7}>
                <Text style={{ fontSize: 32, marginBottom: 4 }}>📷</Text>
                <Text style={{ fontSize: 11, color: C.textMuted, fontWeight: '600' }}>Add Product Photo</Text>
              </TouchableOpacity>
            )}
          </View>
          <Text style={st.name}>{product.name}</Text>
          <View style={[st.condBadge, { backgroundColor: cond.bg }]}>
            <Text style={[st.condText, { color: cond.text }]}>{product.productType.replace('_', ' ')}</Text>
          </View>
          <View style={st.pillRow}>
            <View style={[st.pill, { backgroundColor: avail > 0 ? C.successBg : C.dangerBg }]}>
              <Text style={[st.pillText, { color: avail > 0 ? C.success : C.danger }]}>
                {avail > 0 ? `${avail} available` : 'Out of stock'}
              </Text>
            </View>
            {product.hasSerial && (
              <View style={[st.pill, { backgroundColor: C.primaryLight }]}>
                <Text style={[st.pillText, { color: C.primary }]}>Serial tracked</Text>
              </View>
            )}
          </View>
        </View>

        {/* Remark */}
        {product.remark ? (
          <View style={st.remarkCard}>
            <Text style={st.remarkLabel}>REMARK</Text>
            <Text style={st.remarkText}>{product.remark}</Text>
          </View>
        ) : null}

        {product.warrantyTerms ? (
          <View style={[st.remarkCard, { borderLeftColor: C.success }]}>
            <Text style={[st.remarkLabel, { color: C.success }]}>WARRANTY TERMS</Text>
            <Text style={st.remarkText}>{product.warrantyTerms}</Text>
          </View>
        ) : null}

        {/* Details table */}
        <View style={st.table}>
          {rows.map(([label, value]) => (
            <View key={label} style={st.tableRow}>
              <Text style={st.tableLabel}>{label}</Text>
              <Text style={st.tableValue}>{value}</Text>
            </View>
          ))}
        </View>

        {/* ── SERIAL CARDS ── */}
        {product.hasSerial && (
          <View style={st.serialSection}>
            <View style={st.serialSectionHeader}>
              <Text style={st.serialSectionTitle}>SERIAL NUMBERS</Text>
              {serialsLoading && <ActivityIndicator color={C.primary} size="small" />}
            </View>

            {scannedSerial && (
              <View style={st.scannedBanner}>
                <Text style={st.scannedLabel}>📷 Scanned</Text>
                <Text style={st.scannedSerial}>{scannedSerial}</Text>
              </View>
            )}

            {!serialsLoading && serials.length === 0 && (
              <Text style={st.noSerial}>No serial numbers found</Text>
            )}

            {/* 2-column card grid */}
            <View style={st.grid}>
              {visibleSerials.map(s => {
                const col = STATUS_COL[s.status] ?? STATUS_COL.Available;
                const isScanned = s.serialNumber === scannedSerial;
                const uploading = uploadingId === s.id;

                return (
                  <View key={s.id} style={[st.serialCard, isScanned && st.serialCardHighlight]}>
                    {/* Photo area */}
                    <View style={st.photoWrap}>
                      {uploading ? (
                        <View style={st.photoPlaceholder}>
                          <ActivityIndicator color={C.primary} />
                        </View>
                      ) : s.photoBase64 ? (
                        <>
                          <Image source={{ uri: s.photoBase64 }} style={st.serialPhoto} resizeMode="cover" />
                          {/* Overlay buttons */}
                          <View style={st.photoBtnRow}>
                            <TouchableOpacity style={st.photoBtn} onPress={() => handlePickPhoto(s)} activeOpacity={0.8}>
                              <Text style={st.photoBtnText}>📷</Text>
                            </TouchableOpacity>
                            <TouchableOpacity style={[st.photoBtn, st.photoBtnView]} onPress={() => setViewPhotoUri(s.photoBase64!)} activeOpacity={0.8}>
                              <Text style={st.photoBtnText}>👁</Text>
                            </TouchableOpacity>
                          </View>
                        </>
                      ) : (
                        <TouchableOpacity style={st.photoPlaceholder} onPress={() => handlePickPhoto(s)} activeOpacity={0.7}>
                          <Text style={st.photoIcon}>📷</Text>
                          <Text style={st.photoHint}>Tap to add photo</Text>
                        </TouchableOpacity>
                      )}
                    </View>

                    {/* Info */}
                    <View style={st.serialInfo}>
                      <Text style={[st.serialNum, isScanned && { color: C.primary }]} numberOfLines={1}>
                        {isScanned ? '▶ ' : ''}{s.serialNumber}
                      </Text>

                      {s.condition ? (
                        <View style={st.conditionBadge}>
                          <Text style={st.conditionText}>{s.condition}</Text>
                        </View>
                      ) : null}

                      {fmtSerialWarranty(s.warrantyMonths) ? (
                        <Text style={st.warrantyText}>🛡 {fmtSerialWarranty(s.warrantyMonths)}</Text>
                      ) : null}

                      <View style={[st.statusBadge, { backgroundColor: col.bg }]}>
                        <Text style={[st.statusText, { color: col.text }]}>{s.status}</Text>
                      </View>
                    </View>
                  </View>
                );
              })}
            </View>
          </View>
        )}

        {/* Add to Sale */}
        <TouchableOpacity
          style={st.saleBtn}
          onPress={() => navigation.navigate('NewSale', { prefill: product })}
        >
          <Text style={st.saleBtnText}>+ Add to New Sale</Text>
        </TouchableOpacity>
      </ScrollView>

      {/* Photo Viewer Modal */}
      <Modal
        visible={!!viewPhotoUri}
        transparent
        animationType="fade"
        onRequestClose={() => setViewPhotoUri(null)}
      >
        <TouchableOpacity
          style={st.modalOverlay}
          activeOpacity={1}
          onPress={() => setViewPhotoUri(null)}
        >
          <View style={st.modalPhotoBox}>
            {viewPhotoUri && (
              <Image
                source={{ uri: viewPhotoUri }}
                style={st.modalPhoto}
                resizeMode="contain"
              />
            )}
            <Text style={st.modalCloseHint}>Tap anywhere to close</Text>
          </View>
        </TouchableOpacity>
      </Modal>
    </>
  );
}

const st = StyleSheet.create({
  root:                { flex: 1, backgroundColor: C.bg },
  headerCard:          { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 18, marginBottom: 12, alignItems: 'center' },
  photo:               { width: 140, height: 140, borderRadius: 12, borderWidth: 1, borderColor: C.border, backgroundColor: '#F8FAFC' },
  // product photo interactive area
  productPhotoWrap:        { width: 140, height: 140, marginBottom: 14, borderRadius: 12, overflow: 'hidden', position: 'relative' },
  productPhotoPlaceholder: { width: 140, height: 140, borderRadius: 12, borderWidth: 1, borderColor: C.border, borderStyle: 'dashed', backgroundColor: '#F8FAFC', alignItems: 'center', justifyContent: 'center' },
  productPhotoBtnRow:      { position: 'absolute', bottom: 0, left: 0, right: 0, flexDirection: 'row' },
  productPhotoBtn:         { flex: 1, paddingVertical: 7, backgroundColor: 'rgba(0,0,0,0.50)', alignItems: 'center', justifyContent: 'center' },
  productPhotoBtnView:     { backgroundColor: 'rgba(99,102,241,0.80)' },
  productPhotoBtnText:     { fontSize: 16 },
  name:                { fontSize: 20, fontWeight: '800', color: C.text, marginBottom: 10, textAlign: 'center' },
  condBadge:           { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8, marginBottom: 10 },
  condText:            { fontSize: 11, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.5 },
  pillRow:             { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  pill:                { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8 },
  pillText:            { fontSize: 12, fontWeight: '700' },
  remarkCard:          { backgroundColor: C.card, borderRadius: 12, borderWidth: 1, borderColor: C.border, borderLeftWidth: 4, borderLeftColor: C.warning, padding: 14, marginBottom: 12 },
  remarkLabel:         { fontSize: 10, fontWeight: '800', color: C.warning, textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 5 },
  remarkText:          { fontSize: 13, color: C.text, lineHeight: 20 },
  table:               { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, overflow: 'hidden', marginBottom: 12 },
  tableRow:            { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: C.border },
  tableLabel:          { fontSize: 13, fontWeight: '600', color: C.textMuted, flex: 1 },
  tableValue:          { fontSize: 13, fontWeight: '700', color: C.text, flex: 1, textAlign: 'right' },

  // serial section
  serialSection:       { marginBottom: 12 },
  serialSectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  serialSectionTitle:  { fontSize: 11, fontWeight: '800', color: C.textMuted, textTransform: 'uppercase', letterSpacing: 0.6 },
  scannedBanner:       { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.primaryLight, borderRadius: 8, padding: 10, marginBottom: 10 },
  scannedLabel:        { fontSize: 11, fontWeight: '700', color: C.primary },
  scannedSerial:       { fontSize: 13, fontWeight: '800', color: C.primary },
  noSerial:            { textAlign: 'center', color: C.textMuted, paddingVertical: 20, fontSize: 13 },

  // grid
  grid:                { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  serialCard:          { width: CARD_W, backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, overflow: 'hidden' },
  serialCardHighlight: { borderColor: C.primary, borderWidth: 2 },

  // photo
  photoWrap:           { width: '100%', height: CARD_W, position: 'relative' },
  serialPhoto:         { width: '100%', height: '100%' },
  photoPlaceholder:    { width: '100%', height: '100%', backgroundColor: '#F8FAFC', alignItems: 'center', justifyContent: 'center', gap: 4 },
  photoIcon:           { fontSize: 28 },
  photoHint:           { fontSize: 10, color: C.textMuted, fontWeight: '600' },

  // photo overlay buttons
  photoBtnRow:         { position: 'absolute', bottom: 0, left: 0, right: 0, flexDirection: 'row' },
  photoBtn:            { flex: 1, paddingVertical: 6, backgroundColor: 'rgba(0,0,0,0.50)', alignItems: 'center', justifyContent: 'center' },
  photoBtnView:        { backgroundColor: 'rgba(99,102,241,0.75)' },
  photoBtnText:        { fontSize: 16 },

  // info
  serialInfo:          { padding: 10, gap: 4 },
  serialNum:           { fontSize: 12, fontWeight: '800', color: C.text },
  conditionBadge:      { alignSelf: 'flex-start', backgroundColor: '#FFFBEB', borderWidth: 1, borderColor: '#FDE68A', borderRadius: 5, paddingHorizontal: 6, paddingVertical: 2 },
  conditionText:       { fontSize: 10, fontWeight: '700', color: '#92400E' },
  warrantyText:        { fontSize: 10, color: C.textMuted },
  statusBadge:         { alignSelf: 'flex-start', paddingHorizontal: 7, paddingVertical: 3, borderRadius: 5, marginTop: 2 },
  statusText:          { fontSize: 10, fontWeight: '700' },

  saleBtn:             { backgroundColor: C.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center', marginBottom: 8 },
  saleBtnText:         { color: '#fff', fontWeight: '700', fontSize: 15 },

  // photo viewer modal
  modalOverlay:        { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', alignItems: 'center', justifyContent: 'center' },
  modalPhotoBox:       { width: width * 0.92, height: screenHeight * 0.50, backgroundColor: '#111', borderRadius: 16, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  modalPhoto:          { width: '100%', height: '100%' },
  modalCloseHint:      { position: 'absolute', bottom: 10, color: 'rgba(255,255,255,0.5)', fontSize: 11 },
});
