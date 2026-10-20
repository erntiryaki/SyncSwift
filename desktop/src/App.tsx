import React, { useState, useEffect } from 'react';
import { AuthScreen } from './components/AuthScreen';
import { HomeScreen } from './components/HomeScreen';
import { TrayPanel } from './components/TrayPanel';
import { SYNC_SWIFT_CONFIG } from './services/authService';
import type { AppLanguage, DiscoveredDevice, ClipboardItem, TransferItem, UserProfile } from './types';

export const App: React.FC = () => {
  // Check URL query to see if this window is opened as the Tray Popover Panel
  const isTrayWindow = window.location.search.includes('window=panel') || window.location.hash.includes('panel');

  const [language, setLanguage] = useState<AppLanguage>('tr');
  const [isDark, setIsDark] = useState<boolean>(true);

  // User Profile
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(() => {
    const saved = localStorage.getItem('syncswift_user');
    return saved ? JSON.parse(saved) : null;
  });

  // Devices state (simulated nearby and connected devices via mDNS / UDP socket)
  const [devices] = useState<DiscoveredDevice[]>([
    {
      id: 'dev-1',
      name: 'iPhone 15 Pro',
      host: '192.168.1.42',
      port: 52424,
      osType: 'iOS',
      isOnline: true,
      isRemote: false
    },
    {
      id: 'dev-2',
      name: 'Pixel 8',
      host: '192.168.1.58',
      port: 52424,
      osType: 'Android',
      isOnline: true,
      isRemote: false
    },
    {
      id: 'dev-3',
      name: 'MacBook Air M3',
      host: '192.168.1.19',
      port: 52424,
      osType: 'macOS',
      isOnline: true,
      isRemote: false
    }
  ]);

  // Shared Clipboard History
  const [clipboardHistory, setClipboardHistory] = useState<ClipboardItem[]>([
    {
      id: 'clip-1',
      content: 'https://getsyncdrop.com/download/windows-setup.exe',
      timestamp: Date.now() - 1000 * 60 * 2,
      type: 'url'
    },
    {
      id: 'clip-2',
      content: 'TR33 0006 1005 1234 5678 9012 34',
      timestamp: Date.now() - 1000 * 60 * 15,
      type: 'text'
    },
    {
      id: 'clip-3',
      content: '#4D90FE',
      timestamp: Date.now() - 1000 * 60 * 30,
      type: 'color'
    }
  ]);

  // Transfers
  const [transfers, setTransfers] = useState<TransferItem[]>([
    {
      id: 'tr-1',
      fileName: 'Project_Assets_2026.zip',
      fileSize: 48 * 1024 * 1024,
      mimeType: 'application/zip',
      direction: 'incoming',
      targetDeviceName: 'iPhone 15 Pro',
      targetDeviceOs: 'iOS',
      progress: 100,
      speedBytesPerSec: 12 * 1024 * 1024,
      status: 'completed',
      timestamp: Date.now() - 1000 * 60 * 5
    }
  ]);

  const handleLoginSuccess = (profile: UserProfile) => {
    setCurrentUser(profile);
    localStorage.setItem('syncswift_user', JSON.stringify(profile));
  };

  // Listen for OAuth callbacks (redirects and popup messages)
  useEffect(() => {
    const raw = window.location.hash || window.location.search;
    if (raw.includes('access_token=')) {
      const params = new URLSearchParams(raw.replace(/^#/, '?'));
      const token = params.get('access_token');
      if (token) {
        if (window.opener) {
          window.opener.postMessage({ type: 'SYNCSWIFT_AUTH_TOKEN', hash: raw }, '*');
          window.close();
          return;
        }

        fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/auth/v1/user`, {
          headers: {
            apikey: SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY,
            Authorization: `Bearer ${token}`
          }
        })
          .then(res => res.json())
          .then(data => {
            if (data && data.email) {
              const meta = data.user_metadata || {};
              const email = data.email;
              const firstName = meta.first_name || meta.name || email.split('@')[0];
              const lastName = meta.last_name || '';
              handleLoginSuccess({
                email,
                firstName,
                lastName,
                provider: data.app_metadata?.provider || 'google',
                isPro: true
              });
              window.history.replaceState({}, document.title, window.location.pathname);
            }
          })
          .catch(console.error);
      }
    }

    const messageHandler = (e: MessageEvent) => {
      if (e.data && e.data.type === 'SYNCSWIFT_AUTH_TOKEN' && e.data.hash) {
        const params = new URLSearchParams(e.data.hash.replace(/^#/, '?'));
        const token = params.get('access_token');
        if (token) {
          fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/auth/v1/user`, {
            headers: {
              apikey: SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY,
              Authorization: `Bearer ${token}`
            }
          })
            .then(res => res.json())
            .then(data => {
              if (data && data.email) {
                const meta = data.user_metadata || {};
                const email = data.email;
                const firstName = meta.first_name || meta.name || email.split('@')[0];
                const lastName = meta.last_name || '';
                handleLoginSuccess({
                  email,
                  firstName,
                  lastName,
                  provider: data.app_metadata?.provider || 'google',
                  isPro: true
                });
              }
            })
            .catch(console.error);
        }
      }
    };
    window.addEventListener('message', messageHandler);
    return () => window.removeEventListener('message', messageHandler);
  }, []);

  const handleContinueLocally = () => {
    const localUser: UserProfile = {
      email: '',
      firstName: 'Local',
      lastName: 'User',
      provider: 'local',
      isPro: false
    };
    setCurrentUser(localUser);
    localStorage.setItem('syncswift_user', JSON.stringify(localUser));
  };

  const handleLogout = useCallback(() => {
    // 1. Masaüstü cihazını Supabase devices tablosundan kaldır
    const devId = localStorage.getItem('syncswift_desktop_id');
    if (devId) {
      const key = SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY;
      fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/devices?id=eq.${devId}`, {
        method: 'DELETE',
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`
        }
      }).catch(() => {});
    }
    // 2. Yerel oturumu temizle
    setCurrentUser(null);
    localStorage.removeItem('syncswift_user');
  }, []);

  // Telefondan "Cihazı Kaldır / Hesabı Kaldır" dendiğinde masaüstünden otomatik çıkış yap
  useEffect(() => {
    if (!currentUser || currentUser.provider === 'local') return;
    const devId = localStorage.getItem('syncswift_desktop_id');
    if (!devId) return;

    const interval = setInterval(async () => {
      try {
        const key = SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY;
        const res = await fetch(
          `${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/devices?id=eq.${devId}&select=id,user_id`,
          {
            headers: {
              apikey: key,
              Authorization: `Bearer ${key}`
            }
          }
        );
        if (res.ok) {
          const list = await res.json();
          // Eğer mobilden cihaz silinmişse veya hesaptan ayrılmışsa masaüstünü hemen çıkış yaptır
          if (Array.isArray(list)) {
            if (list.length === 0 || !list[0]?.user_id || list[0]?.user_id === '') {
              handleLogout();
            }
          }
        }
      } catch (_e) {}
    }, 3000);

    return () => clearInterval(interval);
  }, [currentUser, handleLogout]);

  const handleSendClipboard = (text: string) => {
    const newItem: ClipboardItem = {
      id: `clip-${Date.now()}`,
      content: text,
      timestamp: Date.now(),
      type: text.startsWith('http') ? 'url' : 'text'
    };
    setClipboardHistory(prev => [newItem, ...prev]);
  };

  const handlePickFiles = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = true;
    input.onchange = (e: any) => {
      const files: FileList = e.target.files;
      if (files && files.length > 0) {
        const file = files[0];
        const newTransfer: TransferItem = {
          id: `tr-${Date.now()}`,
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type || 'application/octet-stream',
          direction: 'outgoing',
          targetDeviceName: devices[0]?.name || 'iPhone 15 Pro',
          targetDeviceOs: devices[0]?.osType || 'iOS',
          progress: 100,
          speedBytesPerSec: 15 * 1024 * 1024,
          status: 'completed',
          timestamp: Date.now()
        };
        setTransfers(prev => [newTransfer, ...prev]);
      }
    };
    input.click();
  };

  // If this window is the compact tray popover (Windows sağ alt / Mac sağ üst)
  if (isTrayWindow) {
    return (
      <TrayPanel
        language={language}
        isDark={isDark}
        currentUser={currentUser}
        devices={devices}
        clipboardHistory={clipboardHistory}
        onOpenMainWindow={() => {
          window.open('/', '_blank');
        }}
        onQuitApp={() => {
          window.close();
        }}
        onCopyClip={content => {
          console.log('Copied:', content);
        }}
        onSendFiles={files => {
          console.log('Sending files from tray:', files);
        }}
      />
    );
  }

  // If user is not logged in, show AuthScreen (Split layout: Left form + Right QR code)
  if (!currentUser) {
    return (
      <AuthScreen
        language={language}
        setLanguage={setLanguage}
        isDark={isDark}
        onLoginSuccess={handleLoginSuccess}
        onContinueLocally={handleContinueLocally}
      />
    );
  }

  // Authenticated Main Window
  return (
    <HomeScreen
      language={language}
      setLanguage={setLanguage}
      isDark={isDark}
      setIsDark={setIsDark}
      currentUser={currentUser}
      devices={devices}
      clipboardHistory={clipboardHistory}
      transfers={transfers}
      onLogout={handleLogout}
      onSendClipboard={handleSendClipboard}
      onPickFiles={handlePickFiles}
      onShowPairModal={() => {
        alert(language === 'tr' ? 'Mobil uygulamanızdan Cihaz Eşle sekmesine gidin.' : 'Navigate to Pair Device on your mobile app.');
      }}
    />
  );
};

export default App;
