import React, { useEffect, useState, useMemo, useRef } from 'react';
import {
  View, Text, TextInput, FlatList, TouchableOpacity,
  StyleSheet, ActivityIndicator, RefreshControl, Image,
} from 'react-native';
import { api } from '../../api/client';
import { ApiResponse, ProductDTO, ProductSerialDTO, ProductType } from '../../types';
import ScannerModal from '../../components/ScannerModal';
import { C } from '../../theme';
import { useWsTopic } from '../../hooks/useWsTopic';

const CONDITION_COLOR: Record<string, { bg: string; text: string }> = {
  [ProductType.NEW]:        { bg: C.primaryLight, text: C.primary },
  [ProductType.SECOND]:     { bg: C.warningBg,    text: C.warning  },
  [ProductType.SECOND_NEW]: { bg: C.violetBg,     text: C.violet   },
};

export default function ProductListScreen({ navigation }: any) {
  const [products,   setProducts]   = useState<ProductDTO[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search,     setSearch]     = useState('');
  const [scanning,   setScanning]   = useState(false);
  const [scanLoading, setScanLoading] = useState(false);

  const load = async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const res = await api.get<ApiResponse<ProductDTO[]>>('/products');
      setProducts(res.data ?? []);
    } catch {}
    setLoading(false);
    setRefreshing(false);
  };

  useEffect(() => { load(); }, []);
  useWsTopic('/topic/product', () => load(true));

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    if (!q) return products;
    return products.filter(p =>
      p.name.toLowerCase().includes(q) ||
      p.productCode.toLowerCase().includes(q) ||
      (p.brandName ?? '').toLowerCase().includes(q) ||
      (p.categoryName ?? '').toLowerCase().includes(q)
    );
  }, [products, search]);

  // Serial number text search — when local filter yields nothing, try serial API
  const serialSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (serialSearchTimer.current) clearTimeout(serialSearchTimer.current);
    const q = search.trim();
    if (q.length < 3 || filtered.length > 0) return;
    serialSearchTimer.current = setTimeout(async () => {
      setScanLoading(true);
      try {
        const res = await api.get<ApiResponse<ProductSerialDTO>>(`/product-serials/by-serial/${encodeURIComponent(q)}`);
        if (res.data?.productId) {
          const pRes = await api.get<ApiResponse<ProductDTO>>(`/products/${res.data.productId}`);
          if (pRes.data) {
            navigation.navigate('ProductDetail', { product: pRes.data, scannedSerial: q });
          }
        }
      } catch {}
      setScanLoading(false);
    }, 700);
    return () => { if (serialSearchTimer.current) clearTimeout(serialSearchTimer.current); };
  }, [search, filtered.length]);

  const findProductBySerial = async (code: string, scannedSerial?: string): Promise<void> => {
    setScanLoading(true);
    try {
      const res = await api.get<ApiResponse<ProductSerialDTO>>(`/product-serials/by-serial/${encodeURIComponent(code)}`);
      if (res.data?.productId) {
        const pRes = await api.get<ApiResponse<ProductDTO>>(`/products/${res.data.productId}`);
        if (pRes.data) {
          setScanLoading(false);
          navigation.navigate('ProductDetail', { product: pRes.data, scannedSerial: scannedSerial ?? code });
          return;
        }
      }
    } catch {}
    setScanLoading(false);
    setSearch(code);
  };

  const handleScan = async (code: string) => {
    setScanning(false);
    // 1. Try productCode match first (local, instant)
    const byCode = products.find(p => p.productCode.toUpperCase() === code.toUpperCase());
    if (byCode) {
      navigation.navigate('ProductDetail', { product: byCode });
      return;
    }
    // 2. Try serial number lookup via API (fetches product by ID directly)
    await findProductBySerial(code);
  };

  const stock = (p: ProductDTO) => p.stockQty ?? p.currentStock ?? 0;
  const available = (p: ProductDTO) => p.availableSerialCount ?? stock(p);

  const renderItem = ({ item: p }: { item: ProductDTO }) => {
    const avail = available(p);
    const cond  = CONDITION_COLOR[p.productType] ?? CONDITION_COLOR[ProductType.NEW];
    const inStock = avail > 0;
    return (
      <TouchableOpacity
        style={st.card}
        onPress={() => navigation.navigate('ProductDetail', { product: p })}
        activeOpacity={0.75}
      >
        <View style={st.cardInner}>
          {/* Photo thumbnail */}
          {p.photoBase64
            ? <Image source={{ uri: p.photoBase64 }} style={st.thumb} resizeMode="contain" />
            : <View style={st.thumbPlaceholder}><Text style={st.thumbIcon}>📦</Text></View>}

          {/* Content */}
          <View style={{ flex: 1 }}>
            {/* Top row */}
            <View style={st.cardTop}>
              <View style={[st.condDot, { backgroundColor: cond.text }]} />
              <Text style={st.productName} numberOfLines={1}>{p.name}</Text>
            </View>

            {/* Code + meta */}
            <View style={st.cardMid}>
              <View style={st.codeChip}>
                <Text style={st.codeText}>{p.productCode}</Text>
              </View>
              {p.brandName ? <Text style={st.metaText}>{p.brandName}</Text> : null}
              {p.categoryName ? <Text style={st.metaText}>· {p.categoryName}</Text> : null}
            </View>

            {/* Bottom row: price + stock */}
            <View style={st.cardBottom}>
              <Text style={st.price}>{p.sellingPrice.toLocaleString()} <Text style={st.ks}>Ks</Text></Text>
              <View style={[st.stockBadge, { backgroundColor: inStock ? C.successBg : C.dangerBg }]}>
                <Text style={[st.stockText, { color: inStock ? C.success : C.danger }]}>
                  {inStock ? `${avail} in stock` : 'Out of stock'}
                </Text>
              </View>
            </View>

            {/* Condition badge */}
            <View style={[st.condBadge, { backgroundColor: cond.bg }]}>
              <Text style={[st.condText, { color: cond.text }]}>
                {p.productType.replace('_', ' ')}
              </Text>
            </View>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={st.root}>
      {/* Search bar */}
      <View style={st.searchWrap}>
        <View style={st.searchBox}>
          <Text style={st.searchIcon}>🔍</Text>
          <TextInput
            style={st.searchInput}
            placeholder="Name, code, brand..."
            placeholderTextColor={C.textMuted}
            value={search}
            onChangeText={setSearch}
            returnKeyType="search"
          />
          {search.length > 0 && (
            <TouchableOpacity onPress={() => setSearch('')} style={st.clearBtn}>
              <Text style={st.clearText}>✕</Text>
            </TouchableOpacity>
          )}
        </View>
        <TouchableOpacity style={st.scanBtn} onPress={() => setScanning(true)}>
          {scanLoading
            ? <ActivityIndicator color="#fff" size="small" />
            : <Text style={st.scanBtnText}>📷</Text>
          }
        </TouchableOpacity>
      </View>

      {/* Count bar */}
      <View style={st.countBar}>
        <Text style={st.countText}>{filtered.length} products</Text>
        {search.length > 0
          ? <Text style={st.searchHint}>Searching: "{search}"</Text>
          : <TouchableOpacity
              style={{ backgroundColor: C.primaryLight, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 }}
              onPress={() => navigation.navigate('ProductSerials')}
            >
              <Text style={{ fontSize: 11, fontWeight: '700', color: C.primary }}>🔖 Serial Registry</Text>
            </TouchableOpacity>
        }
      </View>

      {loading
        ? <ActivityIndicator color={C.primary} style={{ marginTop: 40 }} size="large" />
        : (
          <FlatList
            data={filtered}
            keyExtractor={p => String(p.id)}
            renderItem={renderItem}
            contentContainerStyle={{ padding: 12, paddingTop: 6, paddingBottom: 24 }}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={() => { setRefreshing(true); load(true); }}
                colors={[C.primary]}
              />
            }
            ListEmptyComponent={
              <View style={st.emptyWrap}>
                <Text style={st.emptyIcon}>📭</Text>
                <Text style={st.emptyText}>No products found</Text>
                {search.length > 0 && (
                  <Text style={st.emptyHint}>Try scanning barcode for serial number search</Text>
                )}
              </View>
            }
          />
        )
      }

      <ScannerModal
        visible={scanning}
        onDetected={handleScan}
        onClose={() => setScanning(false)}
        title="Scan Product Barcode / Serial"
      />
    </View>
  );
}

const st = StyleSheet.create({
  root:        { flex: 1, backgroundColor: C.bg },
  searchWrap:  { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, paddingVertical: 10, gap: 8, backgroundColor: C.card, borderBottomWidth: 1, borderBottomColor: C.border },
  searchBox:   { flex: 1, flexDirection: 'row', alignItems: 'center', backgroundColor: C.bg, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 10, paddingVertical: 8, gap: 6 },
  searchIcon:  { fontSize: 14 },
  searchInput: { flex: 1, fontSize: 14, color: C.text, padding: 0 },
  clearBtn:    { padding: 2 },
  clearText:   { fontSize: 12, color: C.textMuted },
  scanBtn:     { width: 42, height: 42, backgroundColor: C.primary, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
  scanBtnText: { fontSize: 20 },
  countBar:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 5 },
  countText:   { fontSize: 11, fontWeight: '700', color: C.textMuted, textTransform: 'uppercase' },
  searchHint:  { fontSize: 11, color: C.primary, fontWeight: '600' },
  // Card
  card:             { backgroundColor: C.card, borderRadius: 14, borderWidth: 1, borderColor: C.border, padding: 14, marginBottom: 9 },
  cardInner:        { flexDirection: 'row', gap: 12, alignItems: 'flex-start' },
  thumb:            { width: 60, height: 60, borderRadius: 10, borderWidth: 1, borderColor: C.border, backgroundColor: '#F8FAFC' },
  thumbPlaceholder: { width: 60, height: 60, borderRadius: 10, borderWidth: 1, borderColor: C.border, backgroundColor: C.bg, justifyContent: 'center', alignItems: 'center' },
  thumbIcon:        { fontSize: 24 },
  cardTop:          { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 6 },
  condDot:          { width: 8, height: 8, borderRadius: 4 },
  productName: { flex: 1, fontSize: 15, fontWeight: '700', color: C.text },
  cardMid:     { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6, marginBottom: 10 },
  codeChip:    { backgroundColor: C.primaryLight, borderRadius: 6, paddingHorizontal: 8, paddingVertical: 2 },
  codeText:    { fontSize: 11, fontWeight: '800', color: C.primary },
  metaText:    { fontSize: 11, color: C.textMuted, fontWeight: '500' },
  cardBottom:  { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  price:       { fontSize: 15, fontWeight: '800', color: C.text },
  ks:          { fontSize: 11, fontWeight: '600', color: C.textMuted },
  stockBadge:  { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  stockText:   { fontSize: 11, fontWeight: '700' },
  condBadge:   { alignSelf: 'flex-start', paddingHorizontal: 7, paddingVertical: 2, borderRadius: 5, marginTop: 8 },
  condText:    { fontSize: 10, fontWeight: '700', textTransform: 'uppercase' },
  // Empty
  emptyWrap:   { alignItems: 'center', marginTop: 60 },
  emptyIcon:   { fontSize: 40, marginBottom: 10 },
  emptyText:   { fontSize: 15, color: C.textMuted, fontWeight: '600' },
  emptyHint:   { fontSize: 12, color: C.textMuted, marginTop: 6, textAlign: 'center', paddingHorizontal: 32 },
});
