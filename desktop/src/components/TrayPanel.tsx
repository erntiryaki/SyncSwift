import React, { useState } from 'react';
import { translations } from '../localization/translations';
import type { AppLanguage, DiscoveredDevice, ClipboardItem, UserProfile } from '../types';
import {
  Maximize2,
  Power,
  Copy,
  ExternalLink,
  UploadCloud,
  Smartphone,
  Laptop,
  Check,
  Zap
} from 'lucide-react';

interface TrayPanelProps {
  language: AppLanguage;
  isDark: boolean;
  currentUser: UserProfile | null;
  devices: DiscoveredDevice[];
  clipboardHistory: ClipboardItem[];
  onOpenMainWindow: () => void;
  onQuitApp: () => void;
  onCopyClip: (content: string) => void;
  onSendFiles: (files: FileList) => void;
}

export const TrayPanel: React.FC<TrayPanelProps> = ({
  language,
  isDark,
  currentUser,
  devices,
  clipboardHistory,
  onOpenMainWindow,
  onQuitApp,
  onCopyClip,
  onSendFiles
}) => {
  const t = translations[language] || translations.en;
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);

  const handleCopy = (clip: ClipboardItem) => {
    navigator.clipboard.writeText(clip.content);
    setCopiedId(clip.id);
    onCopyClip(clip.content);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      onSendFiles(e.dataTransfer.files);
    }
  };

  const latestClip = clipboardHistory[0];

  return (
    <div
      className={`w-full h-full rounded-2xl flex flex-col justify-between shadow-2xl border backdrop-blur-2xl overflow-hidden select-none transition-colors font-['Manrope'] ${
        isDark
          ? 'bg-[#121829]/95 border-[#1E293B] text-white shadow-black/70'
          : 'bg-[#F4F6F9]/95 border-[#DDE3EA] text-[#121829] shadow-slate-300/70'
      }`}
    >
      {/* Header */}
      <div className="px-4 py-3.5 border-b border-white/[0.06] flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <img src="./assets/logo-icon/app-icon.png" alt="SyncSwift" className="w-5 h-5 rounded-md object-contain" />
          <span className="font-bold text-sm tracking-tight">SyncSwift</span>
          {currentUser?.isPro ? (
            <span className="px-1.5 py-0.5 rounded text-[10px] font-extrabold bg-[#2563EB]/25 text-[#60A5FA] border border-[#2563EB]/35">
              PRO
            </span>
          ) : (
            <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-500/20 text-slate-400">
              FREE
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5">
          <button
            onClick={onOpenMainWindow}
            title={t.openMainApp}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
          >
            <Maximize2 className="w-4 h-4" />
          </button>
          <button
            onClick={onQuitApp}
            title={t.quitApp}
            className="p-1.5 rounded-lg text-slate-400 hover:text-red-400 hover:bg-red-500/10 transition-colors cursor-pointer"
          >
            <Power className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Scrollable Content */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* Zero-click Auto Sync Indicator */}
        <div className="flex items-center justify-between px-3 py-2 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
          <div className="flex items-center gap-2">
            <Zap className="w-3.5 h-3.5 text-emerald-400" />
            <span>{t.autoSyncEnabled}</span>
          </div>
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
        </div>

        {/* Drop Zone */}
        <div
          onDragOver={e => {
            e.preventDefault();
            setIsDragOver(true);
          }}
          onDragLeave={() => setIsDragOver(false)}
          onDrop={handleDrop}
          className={`border-2 border-dashed rounded-2xl p-4 text-center transition-all ${
            isDragOver
              ? 'border-[#2563EB] bg-[#2563EB]/10 scale-[1.01]'
              : isDark
              ? 'border-[#1E293B] bg-[#161F33] hover:border-slate-500'
              : 'border-[#DDE3EA] bg-white hover:border-slate-400'
          }`}
        >
          <UploadCloud className="w-6 h-6 mx-auto mb-1 text-[#60A5FA]" />
          <p className="text-xs font-bold">{t.dropZoneText}</p>
          <p className="text-[11px] text-[#94A3B8] mt-0.5">{t.dropZoneSub}</p>
        </div>

        {/* Live Shared Clipboard Card */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold text-[#94A3B8] uppercase tracking-wider">{t.recentClips}</span>
            <span className="text-[10px] text-slate-500 font-medium">{t.thisDeviceOnline}</span>
          </div>

          {latestClip ? (
            <div
              className={`p-3 rounded-2xl border transition-all ${
                isDark ? 'bg-[#161F33] border-[#1E293B]' : 'bg-white border-[#DDE3EA] shadow-sm'
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <p className="text-xs line-clamp-3 font-mono leading-relaxed select-text">
                  {latestClip.content}
                </p>
                <button
                  onClick={() => handleCopy(latestClip)}
                  className="p-1.5 rounded-lg bg-[#2563EB]/10 text-[#60A5FA] hover:bg-[#2563EB]/20 transition-colors shrink-0 cursor-pointer"
                >
                  {copiedId === latestClip.id ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>

              {/* Smart Action for URL */}
              {latestClip.type === 'url' && (
                <div className="mt-2.5 pt-2 border-t border-white/[0.06] flex items-center justify-between">
                  <span className="text-[10px] text-[#94A3B8] font-medium">Link algılandı</span>
                  <a
                    href={latestClip.content}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 text-[11px] font-semibold text-[#60A5FA] hover:underline"
                  >
                    <span>{t.openInBrowser}</span>
                    <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
              )}
            </div>
          ) : (
            <div className="text-center py-4 text-xs text-slate-500">
              {t.noRecentClips}
            </div>
          )}
        </div>

        {/* Connected Devices Mini List */}
        <div>
          <span className="text-xs font-bold text-[#94A3B8] uppercase tracking-wider block mb-2">
            {t.connectedDevicesMenu} ({devices.length})
          </span>

          <div className="space-y-1.5">
            {devices.map(device => (
              <div
                key={device.id}
                className={`flex items-center justify-between p-2.5 rounded-2xl border transition-colors ${
                  isDark ? 'bg-[#161F33] border-[#1E293B]' : 'bg-white border-[#DDE3EA]'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-[#2563EB]/10 text-[#60A5FA] flex items-center justify-center">
                    {device.osType === 'Windows' || device.osType === 'macOS' ? (
                      <Laptop className="w-4 h-4" />
                    ) : (
                      <Smartphone className="w-4 h-4" />
                    )}
                  </div>
                  <div>
                    <p className="text-xs font-semibold leading-tight">{device.name}</p>
                    <p className="text-[10px] text-[#94A3B8]">{device.osType}</p>
                  </div>
                </div>

                <div className="flex items-center gap-1 text-[11px] text-emerald-400 font-medium">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                  <span>{t.deviceOnline}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="px-4 py-2.5 border-t border-white/[0.06] flex items-center justify-between text-xs text-[#94A3B8]">
        <span className="text-[11px]">SyncSwift Desktop v1.0.0</span>
        <button
          onClick={onOpenMainWindow}
          className="text-xs font-semibold text-[#60A5FA] hover:underline cursor-pointer"
        >
          {t.openMainApp} →
        </button>
      </div>
    </div>
  );
};
