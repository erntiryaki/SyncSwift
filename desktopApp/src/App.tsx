import { useState, useEffect, useRef } from "react";
import { QRCodeSVG } from "qrcode.react";
import { listen } from "@tauri-apps/api/event";
import {
  Send,
  Clipboard,
  History,
  Settings,
  FolderOpen,
  RefreshCw,
  Smartphone,
  CheckCircle2,
  FileText,
  Unlink,
  ExternalLink,
  Laptop,
  Plus,
  LogOut,
  X
} from "lucide-react";
import {
  RemoteDevice,
  TransferHistoryItem,
  getLocalIp,
  getComputerName,
  openDownloadsFolder,
  sendFileToDevice,
  sendClipboardToDevice,
  unpairDevice,
  pingDevice,
  formatBytes
} from "./services/network";
import { publishPairingCode, checkIfCodeClaimed } from "./services/supabase";
import { translations, Language } from "./i18n";
import logoWhite from "./assets/logo_syncswift.webp";
import "./App.css";

export function App() {
  const [lang, setLang] = useState<Language>("tr");
  const t = translations[lang] || translations.tr;

  // Authentication State
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => {
    try {
      return !!localStorage.getItem("syncswift_desktop_auth");
    } catch {
      return false;
    }
  });

  const [authUser, setAuthUser] = useState<{ email?: string; deviceName?: string } | null>(() => {
    try {
      const saved = localStorage.getItem("syncswift_desktop_auth");
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });



  // App Navigation
  const [activeTab, setActiveTab] = useState<"dashboard" | "send" | "clipboard" | "history" | "settings">("dashboard");
  const [showAddDeviceModal, setShowAddDeviceModal] = useState(false);

  // Local device state
  const [localIp, setLocalIp] = useState("127.0.0.1");
  const [computerName, setComputerName] = useState("Windows PC");
  const [deviceId] = useState(() => {
    try {
      let id = localStorage.getItem("syncswift_desktop_device_id");
      if (!id || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
        id = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : "2ffad419-fd5d-44bb-9102-ced1ebe3a603";
        localStorage.setItem("syncswift_desktop_device_id", id);
      }
      return id;
    } catch {
      return "2ffad419-fd5d-44bb-9102-ced1ebe3a603";
    }
  });

  // Pairing state (6-digit code)
  const [pairingCode, setPairingCode] = useState("482 910");
  const [remainingSeconds, setRemainingSeconds] = useState(60);

  // Connected Devices state
  const [connectedDevices, setConnectedDevices] = useState<RemoteDevice[]>(() => {
    try {
      const saved = localStorage.getItem("syncswift_desktop_devices");
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  // Transfer history
  const [transfers, setTransfers] = useState<TransferHistoryItem[]>(() => {
    try {
      const saved = localStorage.getItem("syncswift_desktop_transfers");
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  // Action states
  const [selectedDevice, setSelectedDevice] = useState<RemoteDevice | null>(null);
  const [clipboardText, setClipboardText] = useState("");
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  function showToast(msg: string) {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3800);
  }

  function handleLoginSuccess(user: { email?: string; deviceName?: string }) {
    setAuthUser(user);
    setIsAuthenticated(true);
    try {
      localStorage.setItem("syncswift_desktop_auth", JSON.stringify(user));
    } catch (_e) {}
    showToast("Giriş başarılı! SyncSwift'e hoş geldiniz.");
  }

  function handleLogout() {
    try {
      localStorage.removeItem("syncswift_desktop_auth");
    } catch (_e) {}
    setAuthUser(null);
    setIsAuthenticated(false);
    showToast("Oturum kapatıldı.");
  }

  // Generate 6-digit pairing code
  function rotatePairingCode() {
    const d1 = Math.floor(100 + Math.random() * 900);
    const d2 = Math.floor(100 + Math.random() * 900);
    const code = `${d1} ${d2}`;
    setPairingCode(code);
    setRemainingSeconds(60);

    publishPairingCode(code, localIp, 52424, computerName, deviceId);
  }

  // Load system info on start
  useEffect(() => {
    async function init() {
      const ip = await getLocalIp();
      const name = await getComputerName();
      setLocalIp(ip);
      setComputerName(name);

      const d1 = Math.floor(100 + Math.random() * 900);
      const d2 = Math.floor(100 + Math.random() * 900);
      const code = `${d1} ${d2}`;
      setPairingCode(code);
      publishPairingCode(code, ip, 52424, name, deviceId);
    }
    init();
  }, [deviceId]);

  // 60s countdown timer for pairing code renewal
  useEffect(() => {
    const timer = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev <= 1) {
          rotatePairingCode();
          return 60;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [localIp, computerName, deviceId]);

  // Polling: Auto-detect when mobile pairs via Supabase
  useEffect(() => {
    if (isAuthenticated && !showAddDeviceModal) return;
    const interval = setInterval(async () => {
      try {
        const res = await checkIfCodeClaimed(pairingCode);
        if (res && res.claimed) {
          if (!isAuthenticated) {
            handleLoginSuccess({
              email: res.pushToken || "Mobile Device",
              deviceName: "Mobile Device"
            });
          }
          setShowAddDeviceModal(false);
        }
      } catch (_e) {}
    }, 1500);

    return () => clearInterval(interval);
  }, [pairingCode, isAuthenticated, showAddDeviceModal]);

  // Save connected devices to localStorage
  useEffect(() => {
    localStorage.setItem("syncswift_desktop_devices", JSON.stringify(connectedDevices));
  }, [connectedDevices]);

  // Save transfers to localStorage
  useEffect(() => {
    localStorage.setItem("syncswift_desktop_transfers", JSON.stringify(transfers));
  }, [transfers]);

  // Heartbeat loop: ping connected devices every 3.5 seconds
  useEffect(() => {
    if (!isAuthenticated) return;
    const hb = setInterval(async () => {
      if (connectedDevices.length === 0) return;
      setConnectedDevices((prev) =>
        prev.map((dev) => {
          pingDevice(dev.host, dev.port).then((online) => {
            if (dev.isOnline !== online) {
              setConnectedDevices((curr) =>
                curr.map((d) => (d.id === dev.id ? { ...d, isOnline: online } : d))
              );
            }
          });
          return dev;
        })
      );
    }, 3500);
    return () => clearInterval(hb);
  }, [connectedDevices, isAuthenticated]);

  // Listen to Tauri events from local embedded server
  useEffect(() => {
    let unlistenFile: (() => void) | undefined;
    let unlistenClip: (() => void) | undefined;
    let unlistenPair: (() => void) | undefined;
    let unlistenUnpair: (() => void) | undefined;

    listen<string>("file_received", (e) => {
      try {
        const data = JSON.parse(e.payload);
        showToast(`${t.fileDownloaded}: ${data.fileName}`);
        setTransfers((prev) => [
          {
            id: Math.random().toString(),
            fileName: data.fileName,
            fileSize: data.fileSize || 0,
            isSent: false,
            peerName: data.sender || "Mobile Device",
            status: "completed",
            timestamp: Date.now(),
            localPath: data.path
          },
          ...prev
        ]);
      } catch (_err) {}
    }).then((un) => (unlistenFile = un));

    listen<string>("clipboard_received", (e) => {
      try {
        const data = JSON.parse(e.payload);
        navigator.clipboard.writeText(data.text);
        showToast(`${t.clipboardCopied}: ${data.text.slice(0, 30)}`);
      } catch (_err) {}
    }).then((un) => (unlistenClip = un));

    listen<string>("device_paired", (e) => {
      try {
        const data = JSON.parse(e.payload);
        const newDev: RemoteDevice = {
          id: data.id || `dev-${data.host}`,
          name: data.name || "Mobile Device",
          host: data.host,
          port: data.port || 52424,
          os: data.os || "Android",
          isOnline: true
        };
        setConnectedDevices((prev) => {
          const filtered = prev.filter((d) => d.host !== newDev.host && d.id !== newDev.id);
          return [newDev, ...filtered];
        });
        showToast(`${t.connectionAccepted}: ${newDev.name}`);

        // If currently in Auth screen, automatically authenticate!
        if (!isAuthenticated) {
          handleLoginSuccess({
            email: data.email || newDev.name,
            deviceName: newDev.name
          });
        }
        setShowAddDeviceModal(false);
      } catch (_err) {}
    }).then((un) => (unlistenPair = un));

    listen<string>("device_unpaired", (e) => {
      try {
        const data = JSON.parse(e.payload);
        setConnectedDevices((prev) => prev.filter((d) => d.host !== data.host && d.id !== data.id));
        showToast("Cihaz bağlantısı kesildi.");
      } catch (_err) {}
    }).then((un) => (unlistenUnpair = un));

    return () => {
      unlistenFile?.();
      unlistenClip?.();
      unlistenPair?.();
      unlistenUnpair?.();
    };
  }, [t, isAuthenticated]);





  // Handle direct file send (ZERO APPROVAL)
  async function handleSendFile(target: RemoteDevice, file: File) {
    showToast(`${t.sendingFile}: ${file.name}`);
    setUploadProgress(0);

    const ok = await sendFileToDevice(target, file, computerName, (p) => setUploadProgress(p));
    setUploadProgress(null);

    if (ok) {
      showToast(`${t.fileSentSuccess}: ${file.name}`);
      setTransfers((prev) => [
        {
          id: Math.random().toString(),
          fileName: file.name,
          fileSize: file.size,
          isSent: true,
          peerName: target.name,
          status: "completed",
          timestamp: Date.now()
        },
        ...prev
      ]);
    } else {
      showToast("Dosya gönderilemedi.");
    }
  }

  // Handle direct clipboard send (ZERO APPROVAL)
  async function handleSendClipboard(target: RemoteDevice, text: string) {
    if (!text.trim()) return;
    const ok = await sendClipboardToDevice(target, text, computerName);
    if (ok) {
      showToast(`${t.clipboardSent}: ${text.slice(0, 30)}`);
      setClipboardText("");
    } else {
      showToast("Pano gönderilemedi.");
    }
  }

  // Disconnect device
  async function handleDisconnect(target: RemoteDevice) {
    await unpairDevice(target.host, target.port, deviceId, localIp);
    setConnectedDevices((prev) => prev.filter((d) => d.id !== target.id));
    showToast(`${target.name} bağlantısı kesildi.`);
  }

  const qrPayload = `syncswift://remote?code=${pairingCode.replace(/\s+/g, "")}&host=${localIp}&port=52424&name=${encodeURIComponent(computerName)}&os=Windows&id=${deviceId}`;

  // ========================================================
  // 1. AUTH SCREEN (IF NOT AUTHENTICATED)
  // ========================================================
  if (!isAuthenticated) {
    return (
      <div className="auth-container">
        {toastMessage && (
          <div className="toast-container">
            <CheckCircle2 size={18} color="#10b981" />
            <span>{toastMessage}</span>
          </div>
        )}

        {/* Large White Logo in Auth */}
        <img src={logoWhite} alt="SyncSwift" className="auth-brand-logo" />

        <div className="auth-header">
          <h2>Cihazınızı Bağlayın</h2>
          <p>
            Mobil uygulamanızdan <b>Kendi Cihazlarım</b> sayfasına gidin, QR kodu tarayın veya aşağıdaki 6 haneli kodu girin.
          </p>
        </div>

        <div className="auth-columns">
          {/* Left Column: QR Code */}
          <div className="auth-col-qr">
            <div className="auth-qr-frame">
              <QRCodeSVG value={qrPayload} size={210} level="M" />
            </div>
            <span className="auth-qr-caption">Kamerayla QR kodu okutun</span>
          </div>

          {/* Clean Minimalist Divider */}
          <div className="auth-divider" />

          {/* Right Column: 6-Digit Code */}
          <div className="auth-col-code">
            <span className="auth-code-title">Eşleşme Kodu</span>
            <div className="auth-code-value">{pairingCode}</div>

            <div className="auth-timer-row">
              <RefreshCw size={13} className="spin-slow" />
              <span>Kod {remainingSeconds} saniye içinde yenilenecek</span>
            </div>

            <button
              className="btn-secondary"
              style={{ marginTop: 4, display: "flex", alignItems: "center", gap: 8 }}
              onClick={rotatePairingCode}
            >
              <RefreshCw size={14} />
              <span>Kodu Yenile</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ========================================================
  // 2. MAIN APPLICATION (AUTHENTICATED)
  // ========================================================
  return (
    <div className="app-container">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="toast-container">
          <CheckCircle2 size={18} color="#10b981" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Hidden File Picker */}
      <input
        type="file"
        ref={fileInputRef}
        style={{ display: "none" }}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file && selectedDevice) {
            handleSendFile(selectedDevice, file);
          }
          if (fileInputRef.current) fileInputRef.current.value = "";
        }}
      />

      {/* Modal: + Cihaz Ekle */}
      {showAddDeviceModal && (
        <div className="modal-overlay" onClick={() => setShowAddDeviceModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>+ Yeni Cihaz Ekle</h3>
              <button className="modal-close-btn" onClick={() => setShowAddDeviceModal(false)}>
                <X size={20} />
              </button>
            </div>

            <div className="auth-qr-section">
              <p style={{ fontSize: 13, color: "var(--text-muted)", marginBottom: 16, textAlign: "center" }}>
                Mobil cihazınızdan <b>Kendi Cihazlarım</b> sayfasına gidin ve QR kodu okutun:
              </p>

              <div className="auth-qr-box">
                <QRCodeSVG value={qrPayload} size={180} level="M" />
              </div>

              <div className="auth-code-box">{pairingCode}</div>

              <div className="auth-timer">
                <RefreshCw size={12} className="spin-slow" />
                <span>Kod {remainingSeconds} saniye içinde yenilenecek</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Sidebar with WHITE LOGO at top left */}
      <aside className="sidebar">
        <div className="logo-area">
          <img src={logoWhite} alt="SyncSwift" className="sidebar-brand-logo" />
        </div>

        <nav className="nav-menu">
          <div
            className={`nav-item ${activeTab === "dashboard" ? "active" : ""}`}
            onClick={() => setActiveTab("dashboard")}
          >
            <Smartphone size={18} />
            <span>Kendi Cihazlarım</span>
          </div>

          <div
            className={`nav-item ${activeTab === "send" ? "active" : ""}`}
            onClick={() => setActiveTab("send")}
          >
            <Send size={18} />
            <span>{t.send}</span>
          </div>

          <div
            className={`nav-item ${activeTab === "clipboard" ? "active" : ""}`}
            onClick={() => setActiveTab("clipboard")}
          >
            <Clipboard size={18} />
            <span>{t.sharedClipboard}</span>
          </div>

          <div
            className={`nav-item ${activeTab === "history" ? "active" : ""}`}
            onClick={() => setActiveTab("history")}
          >
            <History size={18} />
            <span>{t.transferHistory}</span>
          </div>

          <div
            className={`nav-item ${activeTab === "settings" ? "active" : ""}`}
            onClick={() => setActiveTab("settings")}
          >
            <Settings size={18} />
            <span>{t.settings}</span>
          </div>
        </nav>

        <div className="sidebar-footer">
          <div className="device-badge">
            <div className="device-badge-name">{authUser?.email || computerName}</div>
            <div className="device-badge-ip">IP: {localIp}:52424</div>
          </div>

          <button className="btn-secondary" onClick={() => openDownloadsFolder()}>
            <FolderOpen size={16} />
            <span>İndirilenler</span>
          </button>

          <button
            className="btn-secondary"
            style={{ color: "#f87171", borderColor: "rgba(239, 68, 68, 0.2)" }}
            onClick={handleLogout}
            title="Oturumu Kapat"
          >
            <LogOut size={16} />
            <span>Çıkış Yap</span>
          </button>
        </div>
      </aside>

      {/* Main Screen Content */}
      <main className="main-content">
        {/* TAB 1: KENDİ CİHAZLARIM */}
        {activeTab === "dashboard" && (
          <>
            <div className="header-bar">
              <div className="header-title">
                <h1>Kendi Cihazlarım</h1>
                <p>Aynı hesap veya yerel Wi-Fi ağı üzerinden bağlı tüm cihazlarınız.</p>
              </div>

              <div className="header-actions">
                <button className="btn-primary" onClick={() => setShowAddDeviceModal(true)}>
                  <Plus size={16} />
                  <span>+ Cihaz Ekle</span>
                </button>

                <button className="btn-secondary" onClick={rotatePairingCode} title="Kodu Yenile">
                  <RefreshCw size={15} />
                  <span>{t.refresh}</span>
                </button>
              </div>
            </div>

            <div className="dashboard-grid" style={{ gridTemplateColumns: "1fr" }}>
              {/* Connected Devices List */}
              <div className="card">
                <div className="card-header">
                  <div className="card-title">
                    <Smartphone size={18} color="#2563eb" />
                    <span>Bağlı Cihazlar ({connectedDevices.length})</span>
                  </div>
                </div>

                {connectedDevices.length > 0 ? (
                  <div className="device-list">
                    {connectedDevices.map((dev) => (
                      <div key={dev.id} className="device-item">
                        <div className="device-info">
                          <div className="device-avatar">
                            {dev.os?.toLowerCase().includes("win") || dev.os?.toLowerCase().includes("pc") ? (
                              <Laptop size={22} color="#60a5fa" />
                            ) : (
                              <Smartphone size={22} color="#60a5fa" />
                            )}
                          </div>
                          <div>
                            <div className="device-name">{dev.name}</div>
                            <div className="device-meta">
                              <span className="status-dot" style={{ background: dev.isOnline ? "#10b981" : "#94a3b8" }} />
                              <span>{dev.isOnline ? t.online : t.offline}</span>
                              <span>•</span>
                              <span>{dev.os || "Android"}</span>
                              <span>•</span>
                              <span>{dev.host}:{dev.port}</span>
                            </div>
                          </div>
                        </div>

                        <div className="device-actions">
                          <button
                            className="btn-icon"
                            title="Dosya Gönder"
                            onClick={() => {
                              setSelectedDevice(dev);
                              fileInputRef.current?.click();
                            }}
                          >
                            <Send size={16} />
                          </button>

                          <button
                            className="btn-icon"
                            title="Ortak Pano Gönder"
                            onClick={async () => {
                              const text = await navigator.clipboard.readText();
                              if (text) {
                                handleSendClipboard(dev, text);
                              } else {
                                showToast("Panonuzda metin bulunamadı.");
                              }
                            }}
                          >
                            <Clipboard size={16} />
                          </button>

                          <button
                            className="btn-icon danger"
                            title="Bağlantıyı Kes"
                            onClick={() => handleDisconnect(dev)}
                          >
                            <Unlink size={16} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--text-muted)" }}>
                    <Laptop size={48} style={{ opacity: 0.35, marginBottom: 14 }} />
                    <p style={{ fontWeight: 700, fontSize: 16, color: "var(--text-main)" }}>Henüz bağlı bir cihazınız yok</p>
                    <p style={{ fontSize: 13, marginTop: 6, maxWidth: 440, marginInline: "auto" }}>
                      Mobil cihazınızdan veya başka bir bilgisayardan senkronizasyon yapmak için yukarıdaki <b>"+ Cihaz Ekle"</b> butonuna tıklayın.
                    </p>
                    <button
                      className="btn-primary"
                      style={{ marginTop: 20 }}
                      onClick={() => setShowAddDeviceModal(true)}
                    >
                      <Plus size={16} />
                      <span>İlk Cihazınızı Bağlayın</span>
                    </button>
                  </div>
                )}
              </div>
            </div>
          </>
        )}

        {/* TAB 2: SEND FILES */}
        {activeTab === "send" && (
          <div>
            <div className="header-bar">
              <div className="header-title">
                <h1>{t.send}</h1>
                <p>{t.sendFilesSubtitle}</p>
              </div>
            </div>

            <div className="card" style={{ maxWidth: 640 }}>
              <div
                className="drop-zone"
                onClick={() => {
                  if (connectedDevices.length === 0) {
                    showToast(t.noConnectedDevices);
                    setShowAddDeviceModal(true);
                    return;
                  }
                  setSelectedDevice(connectedDevices[0]);
                  fileInputRef.current?.click();
                }}
              >
                <Send size={36} color="#2563eb" />
                <p>{t.dragDropFiles}</p>
                <span>{t.selectFiles}</span>
              </div>

              {uploadProgress !== null && (
                <div style={{ marginTop: 20 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
                    <span>Aktarılıyor...</span>
                    <span>%{uploadProgress}</span>
                  </div>
                  <div style={{ height: 8, background: "rgba(255,255,255,0.08)", borderRadius: 4, overflow: "hidden" }}>
                    <div style={{ height: "100%", width: `${uploadProgress}%`, background: "#2563eb", transition: "width 0.2s" }} />
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 3: SHARED CLIPBOARD */}
        {activeTab === "clipboard" && (
          <div>
            <div className="header-bar">
              <div className="header-title">
                <h1>{t.sharedClipboard}</h1>
                <p>{t.clipboardSubtitle}</p>
              </div>
            </div>

            <div className="card" style={{ maxWidth: 640 }}>
              <textarea
                className="text-input"
                style={{ width: "100%", height: 140, resize: "vertical", fontFamily: "inherit" }}
                placeholder="Gönderilecek metni buraya yazın veya yapıştırın..."
                value={clipboardText}
                onChange={(e) => setClipboardText(e.target.value)}
              />

              <div style={{ display: "flex", justifyContent: "space-between", marginTop: 16 }}>
                <button
                  className="btn-secondary"
                  onClick={async () => {
                    const text = await navigator.clipboard.readText();
                    setClipboardText(text);
                  }}
                >
                  Bilgisayar Panosundan Yapıştır
                </button>

                <button
                  className="btn-primary"
                  onClick={() => {
                    if (connectedDevices.length === 0) {
                      showToast("Lütfen önce bir mobil cihaz bağlayın.");
                      setShowAddDeviceModal(true);
                      return;
                    }
                    handleSendClipboard(connectedDevices[0], clipboardText);
                  }}
                >
                  <Send size={16} />
                  <span>Cihaza Gönder</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: TRANSFER HISTORY */}
        {activeTab === "history" && (
          <div>
            <div className="header-bar">
              <div className="header-title">
                <h1>{t.transferHistory}</h1>
                <p>Gelen ve giden tüm dosyaların listesi.</p>
              </div>

              <button className="btn-secondary" onClick={() => setTransfers([])}>
                Geçmişi Temizle
              </button>
            </div>

            <div className="card">
              {transfers.length > 0 ? (
                <div className="device-list">
                  {transfers.map((item) => (
                    <div key={item.id} className="device-item">
                      <div className="device-info">
                        <div className="device-avatar" style={{ background: item.isSent ? "rgba(37,99,235,0.15)" : "rgba(16,185,129,0.15)" }}>
                          <FileText size={22} color={item.isSent ? "#60a5fa" : "#34d399"} />
                        </div>
                        <div>
                          <div className="device-name">{item.fileName}</div>
                          <div className="device-meta">
                            <span>{formatBytes(item.fileSize)}</span>
                            <span>•</span>
                            <span>{item.isSent ? `Gönderildi -> ${item.peerName}` : `Alındı <- ${item.peerName}`}</span>
                            <span>•</span>
                            <span>{new Date(item.timestamp).toLocaleTimeString()}</span>
                          </div>
                        </div>
                      </div>

                      {item.localPath && (
                        <button className="btn-icon" onClick={() => openDownloadsFolder()} title="Klasörde Göster">
                          <ExternalLink size={16} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{ textAlign: "center", padding: "40px", color: "var(--text-muted)" }}>
                  <History size={40} style={{ opacity: 0.3, marginBottom: 12 }} />
                  <p>{t.noTransfersYet}</p>
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 5: SETTINGS */}
        {activeTab === "settings" && (
          <div>
            <div className="header-bar">
              <div className="header-title">
                <h1>{t.settings}</h1>
                <p>SyncSwift uygulama tercihleri ve cihaz yönetimi.</p>
              </div>
            </div>

            <div className="card" style={{ maxWidth: 640 }}>
              <div style={{ marginBottom: 20 }}>
                <label style={{ display: "block", fontSize: 13, fontWeight: 700, marginBottom: 6 }}>{t.language}</label>
                <select
                  className="text-input"
                  value={lang}
                  onChange={(e) => setLang(e.target.value as Language)}
                >
                  <option value="tr">Türkçe (TR)</option>
                  <option value="en">English (EN)</option>
                  <option value="de">Deutsch (DE)</option>
                  <option value="fr">Français (FR)</option>
                  <option value="es">Español (ES)</option>
                  <option value="it">Italiano (IT)</option>
                  <option value="pt">Português (PT)</option>
                  <option value="ru">Русский (RU)</option>
                  <option value="ar">العربية (AR)</option>
                </select>
              </div>

              <div style={{ marginBottom: 20 }}>
                <label style={{ display: "block", fontSize: 13, fontWeight: 700, marginBottom: 6 }}>{t.deviceName}</label>
                <input
                  className="text-input"
                  value={computerName}
                  onChange={(e) => setComputerName(e.target.value)}
                />
              </div>

              <div>
                <label style={{ display: "block", fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Yerel Port</label>
                <input className="text-input" value="52424" disabled style={{ opacity: 0.6 }} />
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
