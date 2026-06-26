import React, { useState } from 'react';
import {
  Modal, View, Text, TouchableOpacity, StyleSheet, Alert,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { C } from '../theme';

interface Props {
  visible: boolean;
  onDetected: (code: string) => void;
  onClose: () => void;
  title?: string;
}

export default function ScannerModal({ visible, onDetected, onClose, title = 'Scan Barcode / QR' }: Props) {
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);

  const handleScan = ({ data }: { data: string }) => {
    if (scanned) return;
    setScanned(true);
    onDetected(data.trim());
    setTimeout(() => setScanned(false), 1200);
  };

  const handleClose = () => {
    setScanned(false);
    onClose();
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={handleClose}>
      <View style={st.root}>
        {/* Header */}
        <View style={st.header}>
          <Text style={st.title}>{title}</Text>
          <TouchableOpacity onPress={handleClose} style={st.closeBtn}>
            <Text style={st.closeText}>✕ Close</Text>
          </TouchableOpacity>
        </View>

        {/* Camera area */}
        <View style={st.camWrap}>
          {!permission ? (
            <Text style={st.msg}>Checking permission...</Text>
          ) : !permission.granted ? (
            <View style={st.center}>
              <Text style={st.msg}>Camera permission လိုပါသည်</Text>
              <TouchableOpacity style={st.permBtn} onPress={requestPermission}>
                <Text style={st.permBtnText}>ခွင့်ပြုမည်</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <CameraView
              style={StyleSheet.absoluteFillObject}
              facing="back"
              onBarcodeScanned={scanned ? undefined : handleScan}
              barcodeScannerSettings={{
                barcodeTypes: ['qr', 'code128', 'code39', 'ean13', 'ean8', 'upc_a', 'upc_e', 'itf14'],
              }}
            >
              {/* Scan frame overlay */}
              <View style={st.overlay}>
                <View style={st.frame}>
                  <View style={[st.corner, st.tl]} />
                  <View style={[st.corner, st.tr]} />
                  <View style={[st.corner, st.bl]} />
                  <View style={[st.corner, st.br]} />
                </View>
                <Text style={st.hint}>
                  {scanned ? '✓ Detected!' : 'Frame ထဲ ထည့်ပါ'}
                </Text>
              </View>
            </CameraView>
          )}
        </View>
      </View>
    </Modal>
  );
}

const FRAME = 240;
const CW    = 3;
const CL    = 24;

const st = StyleSheet.create({
  root:    { flex: 1, backgroundColor: '#000' },
  header:  { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: C.primary, paddingHorizontal: 16, paddingVertical: 14, paddingTop: 48 },
  title:   { color: '#fff', fontWeight: '700', fontSize: 16 },
  closeBtn:{ backgroundColor: 'rgba(255,255,255,0.2)', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 8 },
  closeText:{ color: '#fff', fontSize: 13, fontWeight: '600' },
  camWrap: { flex: 1, position: 'relative' },
  center:  { flex: 1, justifyContent: 'center', alignItems: 'center' },
  msg:     { color: '#fff', fontSize: 15, textAlign: 'center' },
  permBtn: { marginTop: 16, backgroundColor: C.primary, paddingHorizontal: 24, paddingVertical: 10, borderRadius: 8 },
  permBtnText: { color: '#fff', fontWeight: '700' },
  overlay: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.45)' },
  frame:   { width: FRAME, height: FRAME, backgroundColor: 'transparent', position: 'relative' },
  corner:  { position: 'absolute', width: CL, height: CL, borderColor: '#fff', borderWidth: 0 },
  tl:      { top: 0, left: 0,   borderTopWidth: CW, borderLeftWidth: CW },
  tr:      { top: 0, right: 0,  borderTopWidth: CW, borderRightWidth: CW },
  bl:      { bottom: 0, left: 0,  borderBottomWidth: CW, borderLeftWidth: CW },
  br:      { bottom: 0, right: 0, borderBottomWidth: CW, borderRightWidth: CW },
  hint:    { color: '#fff', marginTop: 20, fontSize: 14, fontWeight: '600', textAlign: 'center' },
});
