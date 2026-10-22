import { useState, useEffect } from "react";
import { 
  Send, 
  Smartphone, 
  Laptop, 
  ShieldCheck, 
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
  Plus
} from "lucide-react";

type NavTab = "home" | "devices" | "transfers" | "vault" | "settings";

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

// Helper to safely call Tauri IPC with graceful fallback
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
  const isTrayMode = window.location.search.includes("mode=tray");

  // Live state from Tauri backend
  const [identity, setIdentity] = useState<DeviceIdentity | null>(null);
  const [devices, setDevices] = useState<LanDevice[]>([]);
  const [transfers, setTransfers] = useState<TransferItem[]>([]);
  const [vaultFiles, setVaultFiles] = useState<VaultFile[]>([]);
  const [pairingSession, setPairingSession] = useState<PairingSession | null>(null);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
  }, [theme]);

  // Load device identity & state on mount
  useEffect(() => {
    callTauri<DeviceIdentity | null>("get_or_create_device_identity", {}, null).then(id => {
      if (id) {
        setIdentity(id);
        callTauri<PairingSession | null>("create_pairing_session", { deviceId: id.device_id }, null).then(setPairingSession);
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
    const code = pairingSession?.pairing_code || "SWIFT-2026-X";
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

  if (isTrayMode) {
    const latestTx = transfers.find(t => t.status === "TRANSFERRING");
    return (
      <div className="w-[360px] h-[480px] bg-[var(--bg-night)] text-[var(--text-cloud)] p-4 flex flex-col justify-between select-none font-sans">
        <div>
          <div className="flex items-center justify-between pb-3 border-b border-[var(--border-input)]">
            <div className="flex items-center gap-2">
              <div className="w-2.5 h-2.5 rounded-full bg-[var(--success-green)] animate-pulse" />
              <span className="font-bold text-sm">SyncSwift Ready</span>
            </div>
            <button 
              onClick={toggleTheme}
              className="p-1.5 rounded-full hover:bg-[var(--bg-input)] text-[var(--text-mist)]"
            >
              {theme === "dark" ? <Sun size={14} /> : <Moon size={14} />}
            </button>
          </div>

          <div className="mt-4 p-3 rounded-xl bg-[var(--bg-card)] border border-[var(--border-input)] flex items-center justify-between">
            <div>
              <div className="text-xs text-[var(--text-mist)]">This Device</div>
              <div className="text-sm font-semibold mt-0.5 truncate max-w-[200px]">
                {identity?.device_name || "SyncSwift Desktop"}
              </div>
            </div>
            <span className="text-[10px] bg-[var(--bg-input)] px-2 py-1 rounded text-[var(--accent-blue)] font-mono">
              {identity?.device_id || "win_..."}
            </span>
          </div>

          <div className="mt-4">
            <div className="text-xs font-bold text-[var(--text-mist)] uppercase tracking-wider mb-2">Active Transfer</div>
            {latestTx ? (
              <div className="p-3 rounded-xl bg-[var(--bg-card)] border border-[var(--border-input)]">
                <div className="flex items-center justify-between text-xs mb-1">
                  <span className="font-medium truncate max-w-[180px]">{latestTx.file_name}</span>
                  <span className="text-[var(--accent-blue)] font-bold">In Progress</span>
                </div>
                <div className="w-full h-1.5 bg-[var(--bg-input)] rounded-full overflow-hidden">
                  <div className="h-full bg-[var(--accent-blue)] rounded-full animate-pulse w-[65%]" />
                </div>
                <div className="flex justify-between items-center text-[10px] text-[var(--text-placeholder)] mt-1.5">
                  <span>12.5 MB/s (LAN Mesh)</span>
                  <span>{(latestTx.file_size / (1024 * 1024)).toFixed(1)} MB</span>
                </div>
              </div>
            ) : (
              <div className="p-3 rounded-xl bg-[var(--bg-card)] border border-[var(--border-input)] text-xs text-center text-[var(--text-placeholder)]">
                No active transfers
              </div>
            )}
          </div>
        </div>

        <div className="pt-3 border-t border-[var(--border-input)] flex items-center justify-between text-xs text-[var(--text-mist)]">
          <span>v2.0 Self-Hosted</span>
          <button 
            onClick={() => window.close()}
            className="hover:text-[var(--text-cloud)]"
          >
            Close
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen w-screen bg-[var(--bg-night)] text-[var(--text-cloud)] overflow-hidden font-sans">
      {/* Sidebar */}
      <aside className="w-64 bg-[var(--bg-card)] border-r border-[var(--border-input)] flex flex-col justify-between p-4 z-10">
        <div>
          {/* Logo & App Name */}
          <div className="flex items-center gap-3 px-2 py-3 mb-6">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-[var(--btn-pill-bg)] to-[var(--accent-blue)] flex items-center justify-center shadow-lg shadow-blue-500/20 text-white font-extrabold text-lg">
              S
            </div>
            <div>
              <h1 className="font-extrabold text-base tracking-tight">SyncSwift</h1>
              <div className="flex items-center gap-1.5 text-[11px] text-[var(--text-mist)]">
                <span className="w-1.5 h-1.5 rounded-full bg-[var(--success-green)]" />
                {identity ? `${identity.device_name}` : "LAN Mesh Active"}
              </div>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="space-y-1">
            <button
              onClick={() => setTab("home")}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                tab === "home" 
                  ? "bg-[var(--bg-input)] text-[var(--accent-blue)] border border-[var(--border-highlight)]" 
                  : "text-[var(--text-mist)] hover:text-[var(--text-cloud)] hover:bg-[var(--bg-input)]"
              }`}
            >
              <Send size={18} />
              <span>Transfers & Mesh</span>
            </button>

            <button
              onClick={() => setTab("devices")}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                tab === "devices" 
                  ? "bg-[var(--bg-input)] text-[var(--accent-blue)] border border-[var(--border-highlight)]" 
                  : "text-[var(--text-mist)] hover:text-[var(--text-cloud)] hover:bg-[var(--bg-input)]"
              }`}
            >
              <Smartphone size={18} />
              <span>Paired Devices</span>
            </button>

            <button
              onClick={() => setTab("vault")}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                tab === "vault" 
                  ? "bg-[var(--bg-input)] text-[var(--accent-blue)] border border-[var(--border-highlight)]" 
                  : "text-[var(--text-mist)] hover:text-[var(--text-cloud)] hover:bg-[var(--bg-input)]"
              }`}
            >
              <FolderLock size={18} />
              <span>Personal Vault</span>
            </button>

            <button
              onClick={() => setTab("settings")}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                tab === "settings" 
                  ? "bg-[var(--bg-input)] text-[var(--accent-blue)] border border-[var(--border-highlight)]" 
                  : "text-[var(--text-mist)] hover:text-[var(--text-cloud)] hover:bg-[var(--bg-input)]"
              }`}
            >
              <Settings size={18} />
              <span>Settings</span>
            </button>
          </nav>
        </div>

        {/* VPS & Storage Status Banner */}
        <div className="p-3.5 rounded-xl bg-[var(--bg-input)] border border-[var(--border-input)] space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="text-[var(--text-mist)] font-medium flex items-center gap-1.5">
              <HardDrive size={13} /> MinIO S3 Vault
            </span>
            <span className="text-[var(--success-green)] font-semibold">Online</span>
          </div>
          <div className="w-full h-1.5 bg-[var(--border-input)] rounded-full overflow-hidden">
            <div className="h-full bg-[var(--accent-blue)] rounded-full w-[20%]" />
          </div>
          <div className="flex justify-between text-[10px] text-[var(--text-placeholder)]">
            <span>{vaultFiles.length} file(s) synced</span>
            <span>Zero-Knowledge</span>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* TopBar with controls */}
        <header className="h-14 border-b border-[var(--border-input)] flex items-center justify-between px-6 bg-[var(--bg-card)]/50 backdrop-blur-md">
          <div className="flex items-center gap-3 text-xs text-[var(--text-mist)]">
            <span className="flex items-center gap-1.5 font-medium">
              <Wifi size={14} className="text-[var(--accent-blue)]" />
              Direct Mesh: Ready
            </span>
            <span>•</span>
            <span className="flex items-center gap-1.5">
              <ShieldCheck size={14} className="text-[var(--success-green)]" />
              ChaCha20-Poly1305 AEAD
            </span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={toggleTheme}
              className="p-2 rounded-xl bg-[var(--bg-input)] text-[var(--text-mist)] hover:text-[var(--text-cloud)] transition-colors"
              title="Toggle Theme"
            >
              {theme === "dark" ? <Sun size={15} /> : <Moon size={15} />}
            </button>

            {/* Window Controls */}
            <div className="flex items-center gap-1 pl-2 border-l border-[var(--border-input)]">
              <button className="p-2 rounded-lg hover:bg-[var(--bg-input)] text-[var(--text-mist)]">
                <Minus size={14} />
              </button>
              <button className="p-2 rounded-lg hover:bg-[var(--bg-input)] text-[var(--text-mist)]">
                <Square size={12} />
              </button>
              <button 
                onClick={() => window.close()}
                className="p-2 rounded-lg hover:bg-red-500/20 hover:text-red-400 text-[var(--text-mist)]"
              >
                <X size={14} />
              </button>
            </div>
          </div>
        </header>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-8 animate-fade-in">
          {tab === "home" && (
            <div className="max-w-4xl mx-auto space-y-6">
              {/* Hero Drop Target */}
              <div 
                onClick={() => {
                  if (devices.length > 0) {
                    handleStartMockTransfer(devices[0]);
                  }
                }}
                className="p-8 rounded-2xl bg-[var(--bg-card)] border-2 border-dashed border-[var(--border-input)] hover:border-[var(--accent-blue)]/50 transition-all text-center flex flex-col items-center justify-center gap-4 cursor-pointer group"
              >
                <div className="w-14 h-14 rounded-2xl bg-[var(--bg-input)] group-hover:scale-105 transition-transform flex items-center justify-center text-[var(--accent-blue)]">
                  <Send size={26} />
                </div>
                <div>
                  <h2 className="text-lg font-bold">Drag & drop files or click to stream</h2>
                  <p className="text-xs text-[var(--text-mist)] mt-1">
                    Chunked into 1 MiB AEAD frames and encrypted with Ed25519 authenticated session keys
                  </p>
                </div>
                <button className="pill-button text-xs py-2.5 px-6">
                  Select Files to Send
                </button>
              </div>

              {/* Active Transfers Queue */}
              {transfers.length > 0 && (
                <div className="space-y-3">
                  <h3 className="text-sm font-bold tracking-tight">Active Transfer Queue</h3>
                  <div className="space-y-2">
                    {transfers.map(tx => (
                      <div key={tx.transfer_id} className="card-panel p-4 flex items-center justify-between">
                        <div className="space-y-1">
                          <div className="font-semibold text-sm">{tx.file_name}</div>
                          <div className="text-xs text-[var(--text-mist)]">
                            {(tx.file_size / (1024 * 1024)).toFixed(1)} MB • Status: {tx.status}
                          </div>
                        </div>
                        {tx.status === "TRANSFERRING" ? (
                          <button 
                            onClick={() => handleCancelTransfer(tx.transfer_id)}
                            className="text-xs text-red-400 hover:text-red-300 px-3 py-1 bg-[var(--bg-input)] rounded-lg"
                          >
                            Cancel
                          </button>
                        ) : (
                          <span className="text-xs text-[var(--text-placeholder)] font-mono">{tx.status}</span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Nearby Mesh Devices */}
              <div>
                <h3 className="text-sm font-bold tracking-tight mb-3 flex items-center justify-between">
                  <span>Nearby Mesh Devices</span>
                  <span className="text-xs font-normal text-[var(--text-mist)]">mDNS Active</span>
                </h3>

                {devices.length > 0 ? (
                  <div className="grid grid-cols-2 gap-4">
                    {devices.map(dev => (
                      <div key={dev.device_id} className="card-panel p-4 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-xl bg-[var(--bg-input)] flex items-center justify-center text-[var(--accent-blue)]">
                            {dev.os_type.toLowerCase().includes("mac") || dev.os_type.toLowerCase().includes("win") ? (
                              <Laptop size={20} />
                            ) : (
                              <Smartphone size={20} />
                            )}
                          </div>
                          <div>
                            <div className="font-semibold text-sm">{dev.device_name}</div>
                            <div className="text-xs text-[var(--text-mist)]">{dev.os_type} • {dev.host}</div>
                          </div>
                        </div>
                        <button 
                          onClick={() => handleStartMockTransfer(dev)}
                          className="pill-button text-xs py-1.5 px-3.5"
                        >
                          Send
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="card-panel p-6 text-center text-xs text-[var(--text-mist)]">
                    Scanning local network for SyncSwift devices... Keep mobile app open on the same Wi-Fi.
                  </div>
                )}
              </div>

              {/* Dynamic Quick Pair Code */}
              <div className="card-panel p-5 flex items-center justify-between">
                <div>
                  <div className="font-bold text-sm">Direct Pairing Code</div>
                  <div className="text-xs text-[var(--text-mist)] mt-0.5">
                    {pairingSession?.sas_code ? `SAS Security Hash: ${pairingSession.sas_code}` : "Enter this code on your mobile app to link devices"}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-mono font-bold text-sm bg-[var(--bg-input)] px-3 py-1.5 rounded-lg border border-[var(--border-input)] text-[var(--accent-blue)]">
                    {pairingSession?.pairing_code || "SWIFT-2026-X"}
                  </span>
                  <button 
                    onClick={handleCopyCode}
                    className="p-2 rounded-lg bg-[var(--bg-input)] hover:bg-[var(--border-input)] text-[var(--text-mist)] hover:text-[var(--text-cloud)] transition-colors"
                  >
                    {copied ? <Check size={16} className="text-[var(--success-green)]" /> : <Copy size={16} />}
                  </button>
                </div>
              </div>
            </div>
          )}

          {tab === "devices" && (
            <div className="max-w-4xl mx-auto space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold">Trusted Paired Devices</h2>
                  <p className="text-xs text-[var(--text-mist)]">
                    Paired using mutual Ed25519 identity verification and 6-digit SAS codes.
                  </p>
                </div>
              </div>

              <div className="card-panel p-6 text-center text-xs text-[var(--text-mist)] space-y-3">
                <p>Pair this desktop with your Android or iOS device using code:</p>
                <div className="font-mono text-base font-bold text-[var(--accent-blue)]">
                  {pairingSession?.pairing_code || "SWIFT-2026-X"}
                </div>
                {pairingSession?.sas_code && (
                  <p className="text-[11px] text-[var(--text-placeholder)]">
                    Short Authentication String (SAS): <b>{pairingSession.sas_code}</b>
                  </p>
                )}
              </div>
            </div>
          )}

          {tab === "vault" && (
            <div className="max-w-4xl mx-auto space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold">Personal Encrypted Vault</h2>
                  <p className="text-xs text-[var(--text-mist)]">
                    Zero-Knowledge backup on your self-hosted MinIO storage, encrypted with recovery keys.
                  </p>
                </div>
                <button 
                  onClick={handleUploadVaultMock}
                  className="pill-button text-xs py-2 px-4 flex items-center gap-1.5"
                >
                  <Plus size={14} /> Add File to Vault
                </button>
              </div>

              {vaultFiles.length > 0 ? (
                <div className="space-y-2">
                  {vaultFiles.map(file => (
                    <div key={file.id} className="card-panel p-4 flex items-center justify-between">
                      <div className="space-y-0.5">
                        <div className="font-semibold text-sm">{file.file_name}</div>
                        <div className="text-xs text-[var(--text-mist)]">
                          {(file.file_size / (1024 * 1024)).toFixed(2)} MB • {file.mime_type}
                        </div>
                      </div>
                      <button 
                        onClick={() => handleDeleteVault(file.id)}
                        className="p-2 rounded-lg hover:bg-red-500/20 text-red-400 transition-colors"
                        title="Delete from Vault"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="card-panel p-8 text-center text-xs text-[var(--text-mist)]">
                  Your personal vault is empty. Click <b>Add File to Vault</b> to upload encrypted files to MinIO.
                </div>
              )}
            </div>
          )}

          {tab === "settings" && (
            <div className="max-w-4xl mx-auto space-y-6">
              <h2 className="text-lg font-bold">Device Identity & Infrastructure</h2>
              
              {identity && (
                <div className="card-panel p-5 space-y-3">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--text-mist)]">
                    Local Device Cryptographic Identity
                  </h3>
                  <div className="grid grid-cols-2 gap-4 text-xs">
                    <div>
                      <div className="text-[var(--text-mist)]">Device ID</div>
                      <div className="font-mono font-semibold text-[var(--accent-blue)] mt-0.5">{identity.device_id}</div>
                    </div>
                    <div>
                      <div className="text-[var(--text-mist)]">Operating System</div>
                      <div className="font-semibold capitalize mt-0.5">{identity.os_type}</div>
                    </div>
                    <div className="col-span-2">
                      <div className="text-[var(--text-mist)]">Ed25519 Signing Public Key (Base64)</div>
                      <div className="font-mono text-[11px] text-[var(--text-placeholder)] break-all mt-0.5">
                        {identity.signing_pubkey_b64}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              <div className="card-panel p-5 space-y-4">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--text-mist)]">
                  Self-Hosted VPS Connectivity
                </h3>
                <div>
                  <label className="block text-xs font-semibold text-[var(--text-mist)] mb-1">
                    Supabase API Gateway
                  </label>
                  <input 
                    type="text" 
                    readOnly
                    value="https://api.syncswift.net"
                    className="pill-input w-full bg-[var(--bg-night)] cursor-not-allowed"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[var(--text-mist)] mb-1">
                    MinIO Object Storage (S3)
                  </label>
                  <input 
                    type="text" 
                    readOnly
                    value="https://storage.syncswift.net"
                    className="pill-input w-full bg-[var(--bg-night)] cursor-not-allowed"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[var(--text-mist)] mb-1">
                    Coturn WebRTC TURN Signaling
                  </label>
                  <input 
                    type="text" 
                    readOnly
                    value="turn:api.syncswift.net:3478"
                    className="pill-input w-full bg-[var(--bg-night)] cursor-not-allowed"
                  />
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
