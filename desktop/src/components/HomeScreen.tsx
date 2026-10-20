import React, { useState } from 'react';
import { BrandLogo } from './BrandLogo';
import { translations } from '../localization/translations';
import type { AppLanguage, DiscoveredDevice, ClipboardItem, TransferItem, UserProfile } from '../types';
import {
  Laptop,
  Smartphone,
  Send,
  Clipboard,
  ArrowUpRight,
  ArrowDownLeft,
  Search,
  Check,
  Copy,
  Shield,
  Globe,
  Sun,
  Moon,
  LogOut,
  FolderOpen,
  QrCode,
  FileText
} from 'lucide-react';

interface HomeScreenProps {
  language: AppLanguage;
  setLanguage: (lang: AppLanguage) => void;
  isDark: boolean;
  setIsDark: (dark: boolean) => void;
  currentUser: UserProfile | null;
  devices: DiscoveredDevice[];
  clipboardHistory: ClipboardItem[];
  transfers: TransferItem[];
  onLogout: () => void;
  onSendClipboard: (text: string) => void;
  onPickFiles: () => void;
  onShowPairModal: () => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  language,
  setLanguage,
  isDark,
  setIsDark,
  currentUser,
  devices,
  clipboardHistory,
  transfers,
  onLogout,
  onSendClipboard,
  onPickFiles,
  onShowPairModal
}) => {
  const t = translations[language] || translations.en;

  const [activeTab, setActiveTab] = useState<'home' | 'clipboard' | 'transfers'>('home');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [manualClipInput, setManualClipInput] = useState('');

  const handleCopy = (clip: ClipboardItem) => {
    navigator.clipboard.writeText(clip.content);
    setCopiedId(clip.id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const handleSendManualClip = (e: React.FormEvent) => {
    e.preventDefault();
    if (!manualClipInput.trim()) return;
    onSendClipboard(manualClipInput.trim());
    setManualClipInput('');
  };

  const filteredDevices = devices.filter(d =>
    d.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div
      className={`min-h-screen w-full flex flex-col transition-colors duration-200 select-none font-['Manrope'] ${
        isDark ? 'bg-[#121829] text-white' : 'bg-[#F4F6F9] text-[#121829]'
      }`}
    >
      {/* Top Navbar */}
      <header className="px-8 py-4 border-b border-white/[0.06] flex items-center justify-between">
        <div className="flex items-center gap-6">
          <BrandLogo isDark={isDark} />
          <nav className="flex items-center gap-1 bg-white/[0.04] p-1 rounded-xl border border-white/[0.05]">
            <button
              onClick={() => setActiveTab('home')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'home'
                  ? 'bg-[#2563EB] text-white'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {t.homeTab}
            </button>
            <button
              onClick={() => setActiveTab('clipboard')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'clipboard'
                  ? 'bg-[#2563EB] text-white'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {t.clipboardHistoryTab}
            </button>
            <button
              onClick={() => setActiveTab('transfers')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'transfers'
                  ? 'bg-[#2563EB] text-white'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {t.transfersTab}
            </button>
          </nav>
        </div>

        {/* User & Controls */}
        <div className="flex items-center gap-3">
          {currentUser && (
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white/[0.03] border border-white/[0.05]">
              <div className="w-6 h-6 rounded-full bg-[#4D90FE]/20 text-[#4D90FE] flex items-center justify-center text-xs font-bold">
                {currentUser.provider === 'local' || !currentUser.email ? 'L' : (currentUser.firstName?.[0] || 'U')}
              </div>
              <div className="text-left">
                <span className="text-xs font-bold leading-none block">
                  {currentUser.provider === 'local' || !currentUser.email
                    ? 'Local Kullanıcı'
                    : `${currentUser.firstName} ${currentUser.lastName}`.trim() || 'Kullanıcı'}
                </span>
                {currentUser.provider !== 'local' && currentUser.email ? (
                  <span className="text-[10px] text-slate-400 block">{currentUser.email}</span>
                ) : null}
              </div>
              {currentUser.isPro ? (
                <span className="ml-1 px-1.5 py-0.5 rounded text-[9px] font-extrabold bg-[#4D90FE]/20 text-[#4D90FE] border border-[#4D90FE]/30">
                  PRO
                </span>
              ) : (
                <span className="ml-1 px-1.5 py-0.5 rounded text-[9px] font-semibold bg-slate-500/20 text-slate-400">
                  {currentUser.provider === 'local' || !currentUser.email ? 'LOCAL' : 'FREE'}
                </span>
              )}
            </div>
          )}

          {/* Theme Toggle */}
          <button
            onClick={() => setIsDark(!isDark)}
            className="p-2 rounded-xl border border-white/[0.06] text-slate-400 hover:text-white transition-colors"
          >
            {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>

          {/* Language Toggle */}
          <button
            onClick={() => setLanguage(language === 'tr' ? 'en' : 'tr')}
            className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl border border-white/[0.06] text-xs font-semibold text-slate-400 hover:text-white transition-colors"
          >
            <Globe className="w-3.5 h-3.5" />
            <span>{language.toUpperCase()}</span>
          </button>

          {/* Logout Button */}
          <button
            onClick={onLogout}
            title={t.logout}
            className="p-2 rounded-xl border border-red-500/20 text-red-400 hover:bg-red-500/10 transition-colors"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-8 overflow-y-auto">
        {activeTab === 'home' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
            
            {/* Left 7 Columns: Action Cards (Clipboard + Send Files) */}
            <div className="lg:col-span-7 space-y-6">
              
              {/* Card 1: Live Clipboard Sync */}
              <div
                className={`rounded-2xl p-6 border transition-all ${
                  isDark ? 'bg-[#131B28] border-[#1E293B]' : 'bg-white border-slate-200 shadow-sm'
                }`}
              >
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2.5">
                    <div className="p-2 rounded-xl bg-[#4D90FE]/10 text-[#4D90FE]">
                      <Clipboard className="w-5 h-5" />
                    </div>
                    <div>
                      <h2 className="text-sm font-bold tracking-tight">{t.clipboard}</h2>
                      <p className="text-xs text-slate-400">{t.autoSyncEnabled}</p>
                    </div>
                  </div>
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
                </div>

                {/* Latest Clip Preview */}
                {clipboardHistory[0] ? (
                  <div
                    className={`p-4 rounded-xl border text-xs font-mono mb-4 leading-relaxed ${
                      isDark ? 'bg-[#1A2436] border-[#1E293B]' : 'bg-slate-50 border-slate-200'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <p className="line-clamp-3 select-text text-slate-300">
                        {clipboardHistory[0].content}
                      </p>
                      <button
                        onClick={() => handleCopy(clipboardHistory[0])}
                        className="p-2 rounded-lg bg-[#4D90FE]/10 text-[#4D90FE] hover:bg-[#4D90FE]/20 shrink-0"
                      >
                        {copiedId === clipboardHistory[0].id ? (
                          <Check className="w-4 h-4 text-emerald-400" />
                        ) : (
                          <Copy className="w-4 h-4" />
                        )}
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="py-6 text-center text-xs text-slate-500">
                    {t.noRecentClips}
                  </div>
                )}

                {/* Manual Send Input */}
                <form onSubmit={handleSendManualClip} className="flex gap-2">
                  <input
                    type="text"
                    value={manualClipInput}
                    onChange={e => setManualClipInput(e.target.value)}
                    placeholder={language === 'tr' ? 'Cihazlara göndermek için bir metin veya link yazın...' : 'Type text or link to beam to devices...'}
                    className={`flex-1 px-4 py-2.5 rounded-xl text-xs outline-none border transition-all ${
                      isDark
                        ? 'bg-[#1A2436] border-[#1E293B] text-white placeholder-slate-500 focus:border-[#4D90FE]'
                        : 'bg-white border-slate-200 text-slate-900 placeholder-slate-400 focus:border-[#1D58D8]'
                    }`}
                  />
                  <button
                    type="submit"
                    disabled={!manualClipInput.trim()}
                    className={`px-4 py-2.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 text-white transition-all ${
                      manualClipInput.trim()
                        ? 'bg-[#3875D7] hover:bg-[#2B5EB5] cursor-pointer'
                        : 'bg-slate-700/40 text-slate-400 cursor-not-allowed'
                    }`}
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>{t.send}</span>
                  </button>
                </form>
              </div>

              {/* Card 2: P2P File Drop & Stream Zone */}
              <div
                onClick={onPickFiles}
                className={`rounded-2xl p-8 border-2 border-dashed text-center cursor-pointer transition-all ${
                  isDark
                    ? 'border-[#1E293B] bg-[#131B28]/40 hover:border-[#4D90FE] hover:bg-[#4D90FE]/5'
                    : 'border-slate-200 bg-white hover:border-[#1D58D8] hover:bg-slate-50'
                }`}
              >
                <div className="w-12 h-12 rounded-2xl bg-[#4D90FE]/10 text-[#4D90FE] mx-auto flex items-center justify-center mb-3">
                  <FolderOpen className="w-6 h-6" />
                </div>
                <h3 className="text-sm font-bold mb-1">{t.dropZoneText}</h3>
                <p className="text-xs text-slate-400 mb-4">{t.dropZoneSub}</p>
                <button
                  type="button"
                  className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#4D90FE]/10 text-[#4D90FE] hover:bg-[#4D90FE]/20 transition-colors inline-flex items-center gap-1.5"
                >
                  <FileText className="w-3.5 h-3.5" />
                  <span>{language === 'tr' ? 'Dosya veya Klasör Seç' : 'Select Files or Folders'}</span>
                </button>
              </div>

              {/* Card 3: Recent Transfers */}
              <div
                className={`rounded-2xl p-6 border ${
                  isDark ? 'bg-[#131B28] border-[#1E293B]' : 'bg-white border-slate-200 shadow-sm'
                }`}
              >
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-bold tracking-tight">{t.transfers}</h3>
                  <span className="text-xs text-slate-400">{transfers.length} aktarım</span>
                </div>

                <div className="space-y-2.5">
                  {transfers.length > 0 ? (
                    transfers.map(tr => (
                      <div
                        key={tr.id}
                        className={`p-3 rounded-xl border flex items-center justify-between text-xs ${
                          isDark ? 'bg-[#1A2436] border-[#1E293B]' : 'bg-slate-50 border-slate-200'
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-lg bg-[#4D90FE]/10 text-[#4D90FE] flex items-center justify-center">
                            {tr.direction === 'outgoing' ? (
                              <ArrowUpRight className="w-4 h-4" />
                            ) : (
                              <ArrowDownLeft className="w-4 h-4" />
                            )}
                          </div>
                          <div>
                            <p className="font-semibold">{tr.fileName}</p>
                            <p className="text-[10px] text-slate-400">
                              {(tr.fileSize / (1024 * 1024)).toFixed(1)} MB · {tr.targetDeviceName}
                            </p>
                          </div>
                        </div>

                        <div className="text-right">
                          <span className="text-xs font-semibold text-emerald-400">
                            {tr.status === 'completed' ? 'Tamamlandı' : `${tr.progress}%`}
                          </span>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="py-4 text-center text-xs text-slate-500">
                      {language === 'tr' ? 'Henüz dosya aktarımı yapılmadı.' : 'No transfers yet.'}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Right 5 Columns: Connected Devices & Pairing */}
            <div className="lg:col-span-5 space-y-6">
              
              {/* Linked Devices Card */}
              <div
                className={`rounded-2xl p-6 border ${
                  isDark ? 'bg-[#131B28] border-[#1E293B]' : 'bg-white border-slate-200 shadow-sm'
                }`}
              >
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="text-sm font-bold tracking-tight">{t.connectedDevicesMenu}</h3>
                    <p className="text-xs text-slate-400">{devices.length} {t.devices}</p>
                  </div>
                  <button
                    onClick={onShowPairModal}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#4D90FE]/10 text-[#4D90FE] hover:bg-[#4D90FE]/20 transition-colors"
                  >
                    <QrCode className="w-3.5 h-3.5" />
                    <span>{language === 'tr' ? 'Cihaz Eşle' : 'Pair Device'}</span>
                  </button>
                </div>

                {/* Search */}
                <div className="relative mb-3">
                  <Search className="w-3.5 h-3.5 absolute left-3.5 top-3 text-slate-400" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={e => setSearchQuery(e.target.value)}
                    placeholder={language === 'tr' ? 'Cihaz ara...' : 'Search devices...'}
                    className={`w-full pl-9 pr-4 py-2 rounded-xl text-xs outline-none border transition-all ${
                      isDark
                        ? 'bg-[#1A2436] border-[#1E293B] text-white placeholder-slate-500 focus:border-[#4D90FE]'
                        : 'bg-white border-slate-200 text-slate-900 placeholder-slate-400 focus:border-[#1D58D8]'
                    }`}
                  />
                </div>

                {/* Device List */}
                <div className="space-y-2">
                  {filteredDevices.map(dev => (
                    <div
                      key={dev.id}
                      className={`p-3 rounded-xl border flex items-center justify-between transition-colors ${
                        isDark ? 'bg-[#1A2436] border-[#1E293B]' : 'bg-slate-50 border-slate-200'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-xl bg-[#4D90FE]/10 text-[#4D90FE] flex items-center justify-center">
                          {dev.osType === 'Windows' || dev.osType === 'macOS' ? (
                            <Laptop className="w-4 h-4" />
                          ) : (
                            <Smartphone className="w-4 h-4" />
                          )}
                        </div>
                        <div>
                          <p className="text-xs font-bold leading-tight">{dev.name}</p>
                          <p className="text-[10px] text-slate-400">{dev.osType} · {dev.host}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 text-xs font-medium text-emerald-400">
                        <span className="w-2 h-2 rounded-full bg-emerald-400" />
                        <span>{t.deviceOnline}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Zero-Knowledge Privacy Card */}
              <div
                className={`rounded-2xl p-5 border text-xs leading-relaxed ${
                  isDark ? 'bg-[#131B28] border-[#1E293B] text-slate-300' : 'bg-white border-slate-200 text-slate-600 shadow-sm'
                }`}
              >
                <div className="flex items-center gap-2 font-bold text-white mb-2">
                  <Shield className="w-4 h-4 text-emerald-400" />
                  <span className={isDark ? 'text-white' : 'text-slate-900'}>
                    {language === 'tr' ? 'Sıfır-Bilgi & P2P Güvenliği' : 'Zero-Knowledge & P2P Security'}
                  </span>
                </div>
                <p className="text-[11px] text-slate-400">
                  {language === 'tr'
                    ? 'Tüm dosya aktarımları ve pano içerikleri Curve25519 & AES-256-GCM ile uçtan uca şifrelenir. Veriler doğrudan cihazdan cihaza yerel ağda iletilir, asla üçüncü taraf sunucularda saklanmaz.'
                    : 'All file transfers and clipboard contents are end-to-end encrypted with Curve25519 & AES-256-GCM. Data flows directly peer-to-peer on local networks and is never stored on third-party servers.'}
                </p>
              </div>

            </div>
          </div>
        )}

        {/* Tab 2: Full Clipboard History */}
        {activeTab === 'clipboard' && (
          <div className="max-w-4xl mx-auto space-y-4">
            <h2 className="text-lg font-bold">{t.clipboardHistoryTab}</h2>
            <div className="space-y-3">
              {clipboardHistory.map(item => (
                <div
                  key={item.id}
                  className={`p-4 rounded-2xl border flex items-start justify-between gap-4 ${
                    isDark ? 'bg-[#131B28] border-[#1E293B]' : 'bg-white border-slate-200 shadow-sm'
                  }`}
                >
                  <div className="flex-1 font-mono text-xs select-text leading-relaxed">
                    {item.content}
                  </div>
                  <button
                    onClick={() => handleCopy(item)}
                    className="p-2 rounded-xl bg-[#4D90FE]/10 text-[#4D90FE] hover:bg-[#4D90FE]/20 shrink-0"
                  >
                    {copiedId === item.id ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Tab 3: Full Transfers */}
        {activeTab === 'transfers' && (
          <div className="max-w-4xl mx-auto space-y-4">
            <h2 className="text-lg font-bold">{t.transfersTab}</h2>
            <div className="space-y-3">
              {transfers.map(tr => (
                <div
                  key={tr.id}
                  className={`p-4 rounded-2xl border flex items-center justify-between ${
                    isDark ? 'bg-[#131B28] border-[#1E293B]' : 'bg-white border-slate-200 shadow-sm'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-[#4D90FE]/10 text-[#4D90FE] flex items-center justify-center">
                      {tr.direction === 'outgoing' ? <ArrowUpRight className="w-5 h-5" /> : <ArrowDownLeft className="w-5 h-5" />}
                    </div>
                    <div>
                      <p className="text-sm font-bold">{tr.fileName}</p>
                      <p className="text-xs text-slate-400">
                        {(tr.fileSize / (1024 * 1024)).toFixed(1)} MB · {tr.targetDeviceName} ({tr.targetDeviceOs})
                      </p>
                    </div>
                  </div>
                  <span className="text-xs font-bold text-emerald-400">
                    {tr.status === 'completed' ? 'Tamamlandı' : `${tr.progress}%`}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
