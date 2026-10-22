import { useState, useEffect } from "react";
import QRCode from "qrcode";
import { 
  Send, 
  Smartphone, 
  Laptop, 
  FolderLock, 
  Settings, 
  Sun, 
  Moon, 
  Copy, 
  Check, 
  Wifi, 
  Minus, 
  Square, 
  X,
  HardDrive,
  Trash2,
  Plus,
  ClipboardList,
  RefreshCw,
  FolderSync,
  Globe,
  Share2,
  DownloadCloud,
  ShieldCheck,
  Crown,
  Monitor,
  ExternalLink
} from "lucide-react";

type NavTab = "home" | "clipboard" | "vault" | "sync" | "remote" | "settings";

interface DeviceIdentity {
  device_id: string;
  device_name: string;
  os_type: string;
  signing_pubkey_b64: string;
  agreement_pubkey_b64: string;
}

interface LanDevice {
  device_id: string;
  device_name: string;
  os_type: string;
  host: string;
  port: number;
}

interface TransferItem {
  transfer_id: string;
  file_name: string;
  file_size: number;
  bytes_transferred: number;
  speed_bps: number;
  status: string;
}

interface VaultFile {
  id: string;
  file_name: string;
  file_size: number;
  mime_type: string;
  is_starred: boolean;
  created_at_ms: number;
}

interface PairingSession {
  session_id: string;
  pairing_code: string;
  sas_code: string;
  status: string;
  expires_at_ms: number;
}

interface SyncRule {
  id: string;
  local_path: string;
  target_device: string;
  direction: "two_way" | "one_way";
  delete_propagation: boolean;
  is_active: boolean;
}

interface ClipboardItem {
  id: string;
  content: string;
  timestamp: string;
  source_device: string;
}

async function callTauri<T>(cmd: string, args: Record<string, unknown> = {}, fallback: T): Promise<T> {
  try {
    const { invoke } = await import("@tauri-apps/api/core");
    return await invoke<T>(cmd, args);
  } catch (_e) {
    return fallback;
  }
}

export default function App() {
  const [tab, setTab] = useState<NavTab>("home");
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [copied, setCopied] = useState(false);
  const [qrDataUrl, setQrDataUrl] = useState<string>("");
  const isTrayMode = window.location.search.includes("mode=tray");

  // Live state from Tauri backend
  const [identity, setIdentity] = useState<DeviceIdentity | null>(null);
  const [devices, setDevices] = useState<LanDevice[]>([]);
  const [transfers, setTransfers] = useState<TransferItem[]>([]);
  const [vaultFiles, setVaultFiles] = useState<VaultFile[]>([]);
  const [pairingSession, setPairingSession] = useState<PairingSession | null>(null);

  // Feature States
  const [autoSyncClipboard, setAutoSyncClipboard] = useState(true);
  const [clipboardItems, setClipboardItems] = useState<ClipboardItem[]>([
    {
      id: "1",
      content: "https://syncswift.net/docs/architecture",
      timestamp: "10 dk önce",
      source_device: "Pixel 9 Pro"
    },
    {
      id: "2",
      content: "ssh root@169.58.143.66 -p 22",
      timestamp: "1 saat önce",
      source_device: "Bu Bilgisayar"
    },
    {
      id: "3",
      content: "SyncSwift2026SecurePass!",
      timestamp: "3 saat önce",
      source_device: "MacBook Air"
    }
  ]);

  const [syncRules] = useState<SyncRule[]>([
    {
      id: "rule_1",
      local_path: "C:\\Users\\btc\\Documents\\SyncSwift",
      target_device: "Pixel 9 Pro",
      direction: "two_way",
      delete_propagation: true,
      is_active: true
    },
    {
      id: "rule_2",
      local_path: "C:\\Users\\btc\\Pictures\\Screenshots",
      target_device: "Tüm Bağlı Cihazlar",
      direction: "one_way",
      delete_propagation: false,
      is_active: true
    }
  ]);

  const [remoteFolders] = useState([
    { name: "Camera", device: "Pixel 9 Pro", itemsCount: 142, size: "3.4 GB" },
    { name: "Downloads", device: "Pixel 9 Pro", itemsCount: 28, size: "840 MB" },
    { name: "Documents", device: "Pixel 9 Pro", itemsCount: 12, size: "45 MB" }
  ]);

  const [isPro] = useState(true);
  const [language, setLanguage] = useState<"tr" | "en">("tr");

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
  }, [theme]);

  // Load device identity & state on mount
  useEffect(() => {
    callTauri<DeviceIdentity | null>("get_or_create_device_identity", {}, null).then(id => {
      if (id) {
        setIdentity(id);
        Promise.all([
          callTauri<PairingSession | null>("create_pairing_session", { deviceId: id.device_id }, null),
          callTauri<string>("get_local_ip_address", {}, "127.0.0.1")
        ]).then(([sess, localIp]) => {
          if (sess) {
            setPairingSession(sess);
            const actualHost = localIp && localIp !== "127.0.0.1" ? localIp : "127.0.0.1";
            const qrUri = `syncswift://remote?code=${sess.pairing_code}&host=${actualHost}&port=52424&name=${encodeURIComponent(id.device_name)}&os=Windows`;
            QRCode.toDataURL(qrUri, { margin: 1, width: 180, color: { dark: "#0A0F1D", light: "#FFFFFF" } })
              .then(setQrDataUrl)
              .catch(() => {});
          }
        });
      }
    });

    const refreshData = () => {
      callTauri<LanDevice[]>("get_lan_devices", {}, []).then(setDevices);
      callTauri<TransferItem[]>("get_active_transfers", {}, []).then(setTransfers);
      callTauri<VaultFile[]>("get_vault_files", {}, []).then(setVaultFiles);
    };

    refreshData();
    const interval = setInterval(refreshData, 3000);
    return () => clearInterval(interval);
  }, []);

  const toggleTheme = () => {
    setTheme(prev => (prev === "dark" ? "light" : "dark"));
  };

  const handleCopyCode = () => {
    const code = pairingSession?.pairing_code || "482910";
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleStartMockTransfer = async (targetDev: LanDevice) => {
    const newTx = await callTauri<TransferItem | null>("start_lan_transfer", {
      targetDeviceId: targetDev.device_id,
      fileName: "Photo_Package.zip",
      fileSize: 450 * 1024 * 1024,
    }, null);

    if (newTx) {
      setTransfers(prev => [newTx, ...prev]);
    }
  };

  const handleUploadVaultMock = async () => {
    const newFile = await callTauri<VaultFile | null>("upload_vault_file", {
      fileName: `Backup_Document_${Date.now().toString().slice(-4)}.pdf`,
      fileSize: 2400000,
      mimeType: "application/pdf"
    }, null);

    if (newFile) {
      setVaultFiles(prev => [newFile, ...prev]);
    }
  };

  const handleDeleteVault = async (fileId: string) => {
    const ok = await callTauri<boolean>("delete_vault_file", { fileId }, false);
    if (ok) {
      setVaultFiles(prev => prev.filter(f => f.id !== fileId));
    }
  };

  const handleCancelTransfer = async (transferId: string) => {
    await callTauri<boolean>("cancel_transfer", { transferId }, false);
    setTransfers(prev => prev.map(t => t.transfer_id === transferId ? { ...t, status: "CANCELLED" } : t));
  };

  const handleWindowAction = async (action: "minimize" | "maximize" | "close") => {
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      const appWindow = getCurrentWindow();
      if (action === "minimize") await appWindow.minimize();
      else if (action === "maximize") {
        const isMax = await appWindow.isMaximized();
        if (isMax) await appWindow.unmaximize();
        else await appWindow.maximize();
      }
      else if (action === "close") await appWindow.close();
    } catch (_e) {}
  };

  if (isTrayMode) {
    const latestTx = transfers.find(t => t.status === "TRANSFERRING");
    return (
      <div className="app-shell" style={{ width: 360, height: 480, padding: 18, flexDirection: "column", justifyContent: "space-between" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", paddingBottom: 12, borderBottom: "1px solid var(--border-input)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <div className="online-dot" />
              <span style={{ fontWeight: 700, fontSize: 14 }}>SyncSwift Hazır</span>
            </div>
            <button onClick={toggleTheme} className="icon-btn">
              {theme === "dark" ? <Sun size={14} /> : <Moon size={14} />}
            </button>
          </div>

          <div style={{ marginTop: 16, padding: 14, borderRadius: 14, backgroundColor: "var(--bg-card)", border: "1px solid var(--border-input)" }}>
            <div style={{ fontSize: 11, color: "var(--text-mist)" }}>Bu Bilgisayar</div>
            <div style={{ fontSize: 14, fontWeight: 700, marginTop: 4 }}>
              {identity?.device_name || "SyncSwift Windows PC"}
            </div>
          </div>

          <div style={{ marginTop: 16 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "var(--text-mist)", textTransform: "uppercase", letterSpacing: 0.5, marginBottom: 8 }}>
              Aktif Aktarım
            </div>
            {latestTx ? (
              <div className="swift-card" style={{ padding: 14 }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, marginBottom: 6 }}>
                  <span style={{ fontWeight: 600 }}>{latestTx.file_name}</span>
                  <span style={{ color: "var(--accent-blue)", fontWeight: 700 }}>Aktarılıyor</span>
                </div>
                <div className="progress-track">
                  <div className="progress-fill" style={{ width: "68%" }} />
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "var(--text-mist)", marginTop: 6 }}>
                  <span>24.8 MB/s (LAN Mesh)</span>
                  <span>{(latestTx.file_size / (1024 * 1024)).toFixed(1)} MB</span>
                </div>
              </div>
            ) : (
              <div className="swift-card" style={{ padding: 16, textAlign: "center", fontSize: 13, color: "var(--text-placeholder)" }}>
                Aktif aktarım bulunmuyor
              </div>
            )}
          </div>
        </div>

        <div style={{ paddingTop: 12, borderTop: "1px solid var(--border-input)", display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--text-mist)" }}>
          <span>v2.0 Self-Hosted</span>
          <button onClick={() => window.close()} className="icon-btn sm" style={{ border: "none", background: "none" }}>Kapat</button>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      {/* Sidebar Navigation */}
      <aside className="app-sidebar">
        <div>
          {/* Brand Header */}
          <div className="brand-header">
            <div className="brand-logo-icon">S</div>
            <div className="brand-text">
              <h1>SyncSwift</h1>
              <div className="status-indicator">
                <span className="online-dot" />
                <span>{identity ? identity.device_name : "LAN Mesh Hazır"}</span>
              </div>
            </div>
          </div>

          {/* Nav Items */}
          <nav className="nav-menu">
            <button 
              onClick={() => setTab("home")} 
              className={`nav-item ${tab === "home" ? "active" : ""}`}
            >
              <Send size={18} />
              <span>Hızlı Paylaşım</span>
            </button>

            <button 
              onClick={() => setTab("clipboard")} 
              className={`nav-item ${tab === "clipboard" ? "active" : ""}`}
            >
              <ClipboardList size={18} />
              <span>Pano Geçmişi</span>
              <span className="nav-item-badge">{clipboardItems.length}</span>
            </button>

            <button 
              onClick={() => setTab("vault")} 
              className={`nav-item ${tab === "vault" ? "active" : ""}`}
            >
              <FolderLock size={18} />
              <span>Kasa & Takım</span>
            </button>

            <button 
              onClick={() => setTab("sync")} 
              className={`nav-item ${tab === "sync" ? "active" : ""}`}
            >
              <FolderSync size={18} />
              <span>Klasör Eşitleme</span>
              <span className="nav-item-badge">{syncRules.length}</span>
            </button>

            <button 
              onClick={() => setTab("remote")} 
              className={`nav-item ${tab === "remote" ? "active" : ""}`}
            >
              <Monitor size={18} />
              <span>Uzak Gezgin</span>
            </button>

            <button 
              onClick={() => setTab("settings")} 
              className={`nav-item ${tab === "settings" ? "active" : ""}`}
            >
              <Settings size={18} />
              <span>Ayarlar</span>
            </button>
          </nav>
        </div>

        {/* Sidebar Footer */}
        <div className="sidebar-footer">
          <div className="device-badge-row">
            <div>
              <div style={{ color: "var(--text-mist)", fontSize: 10, textTransform: "uppercase", fontWeight: 700 }}>Ağ Durumu</div>
              <div style={{ color: "var(--text-cloud)", fontWeight: 700, marginTop: 2 }}>Port 52424 • Aktif</div>
            </div>
            <span className="os-tag" style={{ color: "var(--accent-blue)" }}>P2P Mesh</span>
          </div>
        </div>
      </aside>

      {/* Main Viewport */}
      <main className="main-viewport">
        {/* Custom Window Title Bar */}
        <header className="window-titlebar">
          <div className="titlebar-title">
            <Laptop size={16} color="var(--accent-blue)" />
            <span>SyncSwift Desktop v2.0</span>
          </div>
          <div className="titlebar-actions">
            <button onClick={toggleTheme} className="icon-btn" title="Tema Değiştir">
              {theme === "dark" ? <Sun size={15} /> : <Moon size={15} />}
            </button>
            <button onClick={() => handleWindowAction("minimize")} className="icon-btn" title="Simge Durumuna Küçült">
              <Minus size={14} />
            </button>
            <button onClick={() => handleWindowAction("maximize")} className="icon-btn" title="Pencereyi Büyüt">
              <Square size={12} />
            </button>
            <button onClick={() => handleWindowAction("close")} className="icon-btn danger" title="Kapat">
              <X size={15} />
            </button>
          </div>
        </header>

        {/* Dynamic Content Body based on tab */}
        <div className="content-body">
          {tab === "home" && (
            <div>
              <div className="section-header">
                <h2 className="section-title">Hızlı Paylaşım & Cihazlar</h2>
                <p className="section-desc">Yerel ağdaki cihazlara anında dosya ve pano aktarın.</p>
              </div>

              {/* 2-Column Hero: Pairing Code + QR Code */}
              <div className="cards-grid-2">
                {/* 6-Digit Code Card */}
                <div className="swift-card">
                  <div className="card-title">
                    <Share2 size={18} color="var(--accent-blue)" />
                    <span>Hızlı Eşleşme Kodu</span>
                  </div>
                  <p style={{ fontSize: 13, color: "var(--text-mist)", marginTop: 8 }}>
                    Mobil cihazınızdan <strong>Bağlı Cihazlar</strong> menüsüne girip bu 6 haneli kodu yazın:
                  </p>
                  <div className="pair-code-hero">
                    <span className="pair-digits">{pairingSession?.pairing_code || "482910"}</span>
                    <button onClick={handleCopyCode} className="pill-button sm">
                      {copied ? <Check size={14} /> : <Copy size={14} />}
                      <span>{copied ? "Kopyalandı" : "Kopyala"}</span>
                    </button>
                  </div>
                </div>

                {/* QR Code Card */}
                <div className="swift-card" style={{ display: "flex", alignItems: "center", gap: 20 }}>
                  <div style={{ backgroundColor: "#FFFFFF", padding: 8, borderRadius: 14, display: "flex", alignItems: "center", justifyContent: "center" }}>
                    {qrDataUrl ? (
                      <img src={qrDataUrl} alt="Pairing QR" style={{ width: 110, height: 110, display: "block" }} />
                    ) : (
                      <div style={{ width: 110, height: 110, backgroundColor: "#E2E8F0", borderRadius: 10 }} />
                    )}
                  </div>
                  <div>
                    <div className="card-title" style={{ fontSize: 14 }}>
                      <Smartphone size={16} color="var(--accent-blue)" />
                      <span>Kamera ile Tara</span>
                    </div>
                    <p style={{ fontSize: 12, color: "var(--text-mist)", marginTop: 6, lineHeight: 1.4 }}>
                      Mobil uygulamadaki <strong>QR Tara</strong> butonuna dokunarak doğrudan anında bağlanın.
                    </p>
                  </div>
                </div>
              </div>

              {/* Discovered LAN Mesh Devices */}
              <div style={{ marginTop: 32 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
                  <div>
                    <h3 style={{ fontSize: 17, fontWeight: 700, letterSpacing: -0.2 }}>Ağdaki Cihazlar ({devices.length})</h3>
                    <p style={{ fontSize: 12.5, color: "var(--text-mist)", marginTop: 2 }}>Port 52424 UDP yayını ile bulunan cihazlar</p>
                  </div>
                  <button onClick={() => callTauri<LanDevice[]>("get_lan_devices", {}, []).then(setDevices)} className="pill-button secondary sm">
                    <RefreshCw size={13} />
                    <span>Yenile</span>
                  </button>
                </div>

                {devices.length === 0 ? (
                  <div className="swift-card" style={{ textAlign: "center", padding: "36px 20px" }}>
                    <Wifi size={32} color="var(--text-placeholder)" style={{ margin: "0 auto 12px auto" }} />
                    <div style={{ fontWeight: 700, fontSize: 15 }}>Ağda Cihaz Aranıyor...</div>
                    <p style={{ color: "var(--text-mist)", fontSize: 13, marginTop: 4 }}>
                      Telefonunuz ile bu bilgisayarın aynı Wi-Fi ağına bağlı olduğundan emin olun.
                    </p>
                  </div>
                ) : (
                  <div className="cards-grid-3">
                    {devices.map(dev => (
                      <div key={dev.device_id} className="mesh-device-card">
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                          <div className="device-icon-bubble">
                            {dev.os_type.toLowerCase().includes("android") ? <Smartphone size={22} /> : <Laptop size={22} />}
                          </div>
                          <span className="os-tag">{dev.os_type}</span>
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 14 }}>{dev.device_name}</div>
                          <div style={{ fontSize: 11, color: "var(--text-mist)", marginTop: 2 }}>{dev.host}:{dev.port}</div>
                        </div>
                        <button onClick={() => handleStartMockTransfer(dev)} className="pill-button sm" style={{ width: "100%" }}>
                          <Send size={13} />
                          <span>Dosya Gönder</span>
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Active Transfers */}
              {transfers.length > 0 && (
                <div style={{ marginTop: 36 }}>
                  <h3 style={{ fontSize: 17, fontWeight: 700, letterSpacing: -0.2, marginBottom: 14 }}>Aktif & Tamamlanan Aktarımlar</h3>
                  <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                    {transfers.map(tx => (
                      <div key={tx.transfer_id} className="swift-card" style={{ padding: "14px 18px" }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                          <div>
                            <span style={{ fontWeight: 700, fontSize: 13.5 }}>{tx.file_name}</span>
                            <span style={{ fontSize: 11, color: "var(--text-mist)", marginLeft: 10 }}>
                              {(tx.file_size / (1024 * 1024)).toFixed(1)} MB
                            </span>
                          </div>
                          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                            <span style={{ fontSize: 12, fontWeight: 700, color: tx.status === "COMPLETED" ? "var(--success-green)" : "var(--accent-blue)" }}>
                              {tx.status}
                            </span>
                            {tx.status === "TRANSFERRING" && (
                              <button onClick={() => handleCancelTransfer(tx.transfer_id)} className="pill-button secondary sm">
                                İptal
                              </button>
                            )}
                          </div>
                        </div>
                        {tx.status === "TRANSFERRING" && (
                          <div className="progress-track">
                            <div className="progress-fill" style={{ width: "65%" }} />
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {tab === "clipboard" && (
            <div>
              <div className="section-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <h2 className="section-title">Pano Geçmişi</h2>
                  <p className="section-desc">Cihazlar arasında 256-bit uçtan uca şifreli otomatik senkronizasyon.</p>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <span style={{ fontSize: 13, fontWeight: 600 }}>Otomatik Pano Senkronizasyonu</span>
                  <label className="switch-wrapper">
                    <input 
                      type="checkbox" 
                      checked={autoSyncClipboard} 
                      onChange={e => setAutoSyncClipboard(e.target.checked)} 
                    />
                    <span className="switch-slider" />
                  </label>
                </div>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {clipboardItems.map(item => (
                  <div key={item.id} className="clipboard-row">
                    <div style={{ flex: 1, paddingRight: 20 }}>
                      <div style={{ fontFamily: "monospace", fontSize: 13, color: "var(--text-cloud)" }}>
                        {item.content}
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 10, marginTop: 6, fontSize: 11, color: "var(--text-mist)" }}>
                        <span>{item.source_device}</span>
                        <span>•</span>
                        <span>{item.timestamp}</span>
                        <span>•</span>
                        <span style={{ color: "var(--success-green)", display: "flex", alignItems: "center", gap: 4 }}>
                          <ShieldCheck size={12} /> E2EE Şifreli
                        </span>
                      </div>
                    </div>
                    <div style={{ display: "flex", gap: 8 }}>
                      <button 
                        onClick={() => navigator.clipboard.writeText(item.content)}
                        className="pill-button secondary sm"
                      >
                        <Copy size={13} />
                        <span>Kopyala</span>
                      </button>
                      <button 
                        onClick={() => setClipboardItems(prev => prev.filter(i => i.id !== item.id))}
                        className="icon-btn danger sm"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === "vault" && (
            <div>
              <div className="section-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <h2 className="section-title">Kasa & Takım Alanı</h2>
                  <p className="section-desc">Kişisel şifreli kasa ve paylaşımlı çalışma alanları.</p>
                </div>
                <button onClick={handleUploadVaultMock} className="pill-button">
                  <Plus size={16} />
                  <span>Dosya Yükle</span>
                </button>
              </div>

              {/* Storage Quota Meter */}
              <div className="swift-card" style={{ marginBottom: 24 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <HardDrive size={18} color="var(--accent-blue)" />
                    <span style={{ fontWeight: 700, fontSize: 14 }}>Bulut Depolama Alanı ({isPro ? "Pro 20 GB" : "Ücretsiz 2 GB"})</span>
                  </div>
                  <span style={{ fontSize: 12, color: "var(--text-mist)", fontWeight: 600 }}>1.4 GB / 20 GB (%7)</span>
                </div>
                <div className="progress-track" style={{ margin: "12px 0 6px 0" }}>
                  <div className="progress-fill" style={{ width: "7%" }} />
                </div>
              </div>

              {/* Files Table */}
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {vaultFiles.length === 0 ? (
                  <div className="swift-card" style={{ textAlign: "center", padding: 32, color: "var(--text-mist)" }}>
                    Kasada dosya bulunmuyor. "Dosya Yükle" butonunu kullanarak ekleyebilirsiniz.
                  </div>
                ) : (
                  vaultFiles.map(file => (
                    <div key={file.id} className="clipboard-row">
                      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                        <div className="device-icon-bubble" style={{ width: 38, height: 38 }}>
                          <FolderLock size={18} />
                        </div>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 13.5 }}>{file.file_name}</div>
                          <div style={{ fontSize: 11, color: "var(--text-mist)", marginTop: 2 }}>
                            {(file.file_size / (1024 * 1024)).toFixed(2)} MB • {file.mime_type}
                          </div>
                        </div>
                      </div>
                      <div style={{ display: "flex", gap: 8 }}>
                        <button className="pill-button secondary sm">
                          <DownloadCloud size={13} />
                          <span>İndir</span>
                        </button>
                        <button onClick={() => handleDeleteVault(file.id)} className="icon-btn danger sm">
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {tab === "sync" && (
            <div>
              <div className="section-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <h2 className="section-title">Klasör Eşitleme Kuralları</h2>
                  <p className="section-desc">Belirlenen klasörleri cihazlar arasında otomatik arka planda senkronize edin.</p>
                </div>
                <button className="pill-button">
                  <Plus size={16} />
                  <span>Yeni Kural Ekle</span>
                </button>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                {syncRules.map(rule => (
                  <div key={rule.id} className="swift-card">
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 14.5 }}>{rule.local_path}</div>
                        <div style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 12, color: "var(--text-mist)", marginTop: 4 }}>
                          <span>Hedef: <strong>{rule.target_device}</strong></span>
                          <span>•</span>
                          <span>{rule.direction === "two_way" ? "Çift Yönlü Senkronizasyon" : "Tek Yönlü Gönderim"}</span>
                          <span>•</span>
                          <span>Silmeleri Yay: {rule.delete_propagation ? "Evet" : "Hayır"}</span>
                        </div>
                      </div>
                      <span className="os-tag" style={{ color: "var(--success-green)", backgroundColor: "rgba(16, 185, 129, 0.15)" }}>
                        Aktif
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === "remote" && (
            <div>
              <div className="section-header">
                <h2 className="section-title">Uzak Dosya Gezgini</h2>
                <p className="section-desc">Bağlı telefon ve bilgisayarların izin verilen klasörlerine uzaktan göz atın.</p>
              </div>

              <div className="cards-grid-3">
                {remoteFolders.map((rf, idx) => (
                  <div key={idx} className="mesh-device-card">
                    <div className="device-icon-bubble">
                      <HardDrive size={22} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 15 }}>{rf.name}</div>
                      <div style={{ fontSize: 12, color: "var(--text-mist)", marginTop: 2 }}>{rf.device} • {rf.itemsCount} dosya</div>
                      <div style={{ fontSize: 11, color: "var(--accent-blue)", marginTop: 4 }}>{rf.size}</div>
                    </div>
                    <button className="pill-button secondary sm" style={{ width: "100%" }}>
                      <ExternalLink size={13} />
                      <span>Klasörü Aç</span>
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === "settings" && (
            <div>
              <div className="section-header">
                <h2 className="section-title">Ayarlar & Abonelik</h2>
                <p className="section-desc">Hesap detayları, kotalar, dil ve ağ yapılandırması.</p>
              </div>

              {/* Profile Card */}
              <div className="swift-card" style={{ marginBottom: 20 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                    <div className="brand-logo-icon" style={{ width: 44, height: 44 }}>
                      <Crown size={22} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 800, fontSize: 16 }}>Eren Tiryaki</div>
                      <div style={{ fontSize: 12, color: "var(--text-mist)", marginTop: 2 }}>erntiryaki@gmail.com • Google Hesabı</div>
                    </div>
                  </div>
                  <span className="pill-button sm" style={{ backgroundColor: "#F59E0B", borderColor: "#D97706" }}>
                    PRO PLAN AKTİF
                  </span>
                </div>
              </div>

              {/* Quotas Panel */}
              <div className="cards-grid-2" style={{ marginBottom: 20 }}>
                <div className="swift-card">
                  <div className="card-title">
                    <HardDrive size={16} color="var(--accent-blue)" />
                    <span>Bulut Depolama Kotası</span>
                  </div>
                  <div style={{ margin: "14px 0 6px 0", fontSize: 20, fontWeight: 800 }}>
                    1.4 GB <span style={{ fontSize: 13, color: "var(--text-mist)", fontWeight: 500 }}>/ 20 GB</span>
                  </div>
                  <div className="progress-track">
                    <div className="progress-fill" style={{ width: "7%" }} />
                  </div>
                  <div style={{ fontSize: 11, color: "var(--text-mist)" }}>Ücretsiz pakette 2 GB, Pro pakette 20 GB</div>
                </div>

                <div className="swift-card">
                  <div className="card-title">
                    <Share2 size={16} color="var(--accent-blue)" />
                    <span>Aylık Relay Aktarım Kotası</span>
                  </div>
                  <div style={{ margin: "14px 0 6px 0", fontSize: 20, fontWeight: 800 }}>
                    4.2 GB <span style={{ fontSize: 13, color: "var(--text-mist)", fontWeight: 500 }}>/ 50 GB</span>
                  </div>
                  <div className="progress-track">
                    <div className="progress-fill" style={{ width: "8.4%" }} />
                  </div>
                  <div style={{ fontSize: 11, color: "var(--text-mist)" }}>Yerel LAN aktarımları kotaya dahil değildir (Sınırsız)</div>
                </div>
              </div>

              {/* Preferences */}
              <div className="swift-card">
                <div className="card-title" style={{ marginBottom: 16 }}>
                  <Globe size={16} color="var(--accent-blue)" />
                  <span>Uygulama Tercihleri</span>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13.5 }}>Uygulama Dili</div>
                      <div style={{ fontSize: 11.5, color: "var(--text-mist)" }}>Arayüz dilini seçin</div>
                    </div>
                    <select 
                      value={language} 
                      onChange={e => setLanguage(e.target.value as "tr" | "en")}
                      className="pill-input" 
                      style={{ width: 140, padding: "8px 14px" }}
                    >
                      <option value="tr">Türkçe</option>
                      <option value="en">English</option>
                    </select>
                  </div>

                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13.5 }}>Görünüm Teması</div>
                      <div style={{ fontSize: 11.5, color: "var(--text-mist)" }}>Karanlık veya aydınlık mod</div>
                    </div>
                    <button onClick={toggleTheme} className="pill-button secondary sm">
                      {theme === "dark" ? "Koyu Mod" : "Açık Mod"}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
