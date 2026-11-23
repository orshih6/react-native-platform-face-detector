import * as ImagePicker from 'expo-image-picker';
import { useRef, useState } from 'react';
import { Button, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  detectFacesInImage,
  FaceCameraView,
  requestCameraPermission,
  type Face,
  type FaceCameraViewRef,
} from 'react-native-platform-face-detector';

const round = (n: number | null) => (n === null ? '—' : n.toFixed(1));

export default function App() {
  const camera = useRef<FaceCameraViewRef>(null);
  const [live, setLive] = useState(false);
  const [faces, setFaces] = useState<Face[]>([]);
  const [log, setLog] = useState('Nothing yet.');

  const openCamera = async () => {
    const status = await requestCameraPermission();
    if (status === 'granted') setLive(true);
    else setLog(`Camera permission: ${status}`);
  };

  const pickPhoto = async () => {
    const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'] });
    const uri = picked.assets?.[0]?.uri;
    if (!uri) return;
    try {
      const found = await detectFacesInImage(uri, { landmarks: true });
      setLog(JSON.stringify(found, null, 2));
    } catch (e) {
      setLog(`detectFacesInImage failed: ${String(e)}`);
    }
  };

  const snap = async () => {
    try {
      const shot = await camera.current?.takeSnapshot({ maxWidth: 640, quality: 0.6 });
      setLog(JSON.stringify(shot, null, 2));
    } catch (e) {
      setLog(`takeSnapshot failed: ${String(e)}`);
    }
  };

  const first = faces[0];
  return (
    <ScrollView style={styles.page} contentContainerStyle={styles.content}>
      <Text style={styles.title}>react-native-platform-face-detector</Text>

      <View style={styles.row}>
        <Button title={live ? 'Close camera' : 'Open camera'} onPress={live ? () => setLive(false) : openCamera} />
        <Button title="Snapshot" onPress={snap} disabled={!live} />
        <Button title="Pick a photo" onPress={pickPhoto} />
      </View>

      {live ? (
        <View style={[styles.window, { borderColor: faces.length > 0 ? '#059669' : '#cbd5e1' }]}>
          <FaceCameraView
            ref={camera}
            style={StyleSheet.absoluteFill}
            facing="front"
            minIntervalMs={100}
            onFacesDetected={(e) => setFaces(e.nativeEvent.faces)}
            onError={(e) => setLog(`${e.nativeEvent.code}: ${e.nativeEvent.message}`)}
          />
        </View>
      ) : null}

      {live ? (
        <Text style={styles.readout}>
          {first
            ? `faces ${faces.length} · yaw ${round(first.yaw)}° · pitch ${round(first.pitch)}° · roll ${round(first.roll)}°`
            : 'no face in view'}
        </Text>
      ) : null}

      <Text style={styles.log} testID="log">
        {log}
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: '#fff' },
  content: { padding: 16, paddingTop: 72, gap: 16 },
  title: { fontSize: 18, fontWeight: '600' },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  window: { width: 240, height: 320, alignSelf: 'center', borderWidth: 3, borderRadius: 16, overflow: 'hidden' },
  readout: { textAlign: 'center', fontVariant: ['tabular-nums'] },
  log: { fontFamily: 'Menlo', fontSize: 11, color: '#334155' },
});
