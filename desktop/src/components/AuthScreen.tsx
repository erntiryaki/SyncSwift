import React, { useState, useEffect, useRef, useCallback } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { translations, availableLanguages } from '../localization';
import type { AppLanguage, UserProfile } from '../types';
import { SYNC_SWIFT_CONFIG, isMac, fetchUserProfile } from '../services/authService';
import {
  Globe,
  RefreshCw,
  CheckCircle2,
  ChevronDown,
  Check,
  Copy,
  ArrowRight
} from 'lucide-react';

interface AuthScreenProps {
  language: AppLanguage;
  setLanguage: (lang: AppLanguage) => void;
  isDark: boolean;
  onLoginSuccess: (profile: UserProfile) => void;
  onContinueLocally: () => void;
}

export const AuthScreen: React.FC<AuthScreenProps> = ({
  language,
  setLanguage,
  isDark: _isDark,
  onLoginSuccess,
  onContinueLocally
}) => {
  const t = translations[language] || translations.en;

  // Language Popover Dropdown state
  const [isLangOpen, setIsLangOpen] = useState(false);
  const langDropdownRef = useRef<HTMLDivElement>(null);

  // Close language dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (langDropdownRef.current && !langDropdownRef.current.contains(event.target as Node)) {
        setIsLangOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Desktop Device ID persistent in localStorage (Must be valid UUID for Supabase)
  const [deviceId] = useState(() => {
    const saved = localStorage.getItem('syncswift_desktop_id');
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    if (saved && uuidRegex.test(saved)) return saved;
    const newId = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
    localStorage.setItem('syncswift_desktop_id', newId);
    return newId;
  });

  // Desktop Pairing QR & 6-digit code state (Tam 1 dakika / 60 saniye)
  const [pairCode, setPairCode] = useState('749 201');
  const [timeLeft, setTimeLeft] = useState(60); // 1 dakika (60s)
  const [isPairingSuccess, setIsPairingSuccess] = useState(false);
  const [copied, setCopied] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);

  const activeCodeRef = useRef('749201');

  // Supabase'e pairing code kaydetme
  const publishCodeToSupabase = useCallback(async (code: string) => {
    try {
      setIsPublishing(true);
      const clean = code.replace(/\s+/g, '');
      // Supabase'te zaman farkından dolayı süresi dolmuş dememesi için geniş tolerans (10 dk) veriyoruz
      const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

      await fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/pairing_codes`, {
        method: 'POST',
        headers: {
          'apikey': SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY,
          'Authorization': `Bearer ${SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY}`,
          'Content-Type': 'application/json',
          'Prefer': 'resolution=merge-duplicates'
        },
        body: JSON.stringify({
          code: clean,
          device_id: deviceId,
          device_name: isMac() ? 'SyncSwift Mac' : 'SyncSwift Windows',
          device_type: isMac() ? 'macOS' : 'Windows',
          push_token: '',
          host: '',
          port: 52424,
          expires_at: expiresAt
        })
      });
    } catch (_err) {
      // Offline fallback
    } finally {
      setIsPublishing(false);
    }
  }, [deviceId]);

  // Yeni 6 haneli kod üretme ve 1 dakikalık sayacı başlatma
  const generateNewPairCode = useCallback(() => {
    const c1 = Math.floor(100 + Math.random() * 900);
    const c2 = Math.floor(100 + Math.random() * 900);
    const formatted = `${c1} ${c2}`;
    const clean = `${c1}${c2}`;
    setPairCode(formatted);
    activeCodeRef.current = clean;
    setTimeLeft(60); // 1 dk
    publishCodeToSupabase(clean);
  }, [publishCodeToSupabase]);

  // İlk açılışta kodu Supabase'e kaydet
  useEffect(() => {
    generateNewPairCode();
  }, [generateNewPairCode]);

  // 1 Dakikalık Geri Sayım Zamanlayıcısı
  useEffect(() => {
    if (timeLeft <= 0) {
      generateNewPairCode();
      return;
    }
    const interval = setInterval(() => {
      setTimeLeft(prev => prev - 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [timeLeft, generateNewPairCode]);

  // Supabase Real-time Polling: Mobilden kod okutulduğunda otomatik giriş yap
  useEffect(() => {
    let isCancelled = false;
    const pollInterval = setInterval(async () => {
      if (isCancelled || isPairingSuccess) return;
      const currentCode = activeCodeRef.current;
      if (!currentCode) return;

      try {
        const resp = await fetch(
          `${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.${currentCode}&select=*`,
          {
            headers: {
              'apikey': SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY,
              'Authorization': `Bearer ${SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY}`
            }
          }
        );
        if (!resp.ok) return;
        const data = await resp.json();
        if (Array.isArray(data) && data.length > 0) {
          const row = data[0];
          // Mobilden eşleşildiğinde push_token veya user_id alanına kullanıcının bilgisi yazılır
          const pairedIdentifier = (row.push_token && typeof row.push_token === 'string' && row.push_token.trim().length > 0)
            ? row.push_token.trim()
            : (row.user_id && typeof row.user_id === 'string' && row.user_id.trim().length > 0)
              ? row.user_id.trim()
              : null;

          if (pairedIdentifier) {
            setIsPairingSuccess(true);

            // Tüketilen kodu sil
            fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.${currentCode}`, {
              method: 'DELETE',
              headers: {
                'apikey': SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY,
                'Authorization': `Bearer ${SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY}`
              }
            }).catch(() => {});

            // Profil bilgilerini alıp başarıyla giriş yap
            const profile = await fetchUserProfile(pairedIdentifier);
            const userEmail = profile?.email || (pairedIdentifier.includes('@') ? pairedIdentifier : 'user@syncswift.net');

            // Masaüstü cihazını Supabase devices tablosuna kaydet
            fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/devices`, {
              method: 'POST',
              headers: {
                'apikey': SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY,
                'Authorization': `Bearer ${SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY}`,
                'Content-Type': 'application/json',
                'Prefer': 'resolution=merge-duplicates'
              },
              body: JSON.stringify({
                id: deviceId,
                device_name: isMac() ? 'SyncSwift Mac' : 'SyncSwift Windows',
                device_type: isMac() ? 'macOS' : 'Windows',
                user_id: userEmail,
                is_online: true
              })
            }).catch(() => {});

            setTimeout(() => {
              onLoginSuccess({
                email: userEmail,
                firstName: profile?.firstName || userEmail.split('@')[0],
                lastName: profile?.lastName || '',
                provider: profile?.provider || 'local',
                isPro: profile?.isPro ?? true
              });
            }, 600);
          }
        }
      } catch (_err) {}
    }, 1500);

    return () => {
      isCancelled = true;
      clearInterval(pollInterval);
    };
  }, [isPairingSuccess, onLoginSuccess]);

  const cleanCodeDigits = pairCode.replace(/\s+/g, '');
  const qrPayload = `syncswift://remote?code=${cleanCodeDigits}&id=${deviceId}&name=SyncSwift%20Desktop&os=Desktop`;

  // Kodu panoya kopyalama
  const handleCopyCode = () => {
    navigator.clipboard?.writeText(cleanCodeDigits);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Simülasyon testi
  const simulatePhonePairing = () => {
    setIsPairingSuccess(true);
    setTimeout(() => {
      onLoginSuccess({
        email: 'synced-device@syncswift.net',
        firstName: 'Synced',
        lastName: 'Device',
        provider: 'local',
        isPro: true
      });
    }, 800);
  };

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const currentLangObj = availableLanguages.find(l => l.code === language) || availableLanguages[0];

  return (
    <div className="relative min-h-screen w-full flex flex-col justify-between bg-[#121829] text-white select-none transition-colors duration-200 font-['Manrope'] overflow-x-hidden">
      
      {/* ================= 9-LANGUAGE DROPDOWN (SAĞ ÜST) ================= */}
      <div ref={langDropdownRef} className="absolute top-6 right-6 sm:top-8 sm:right-8 z-50">
        <button
          onClick={() => setIsLangOpen(!isLangOpen)}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-white/10 bg-[#1E293B]/80 hover:bg-[#1E293B] text-slate-200 hover:text-white transition-all text-xs font-semibold backdrop-blur-md cursor-pointer shadow-lg"
        >
          <Globe className="w-3.5 h-3.5 text-[#60A5FA]" />
          <span>{currentLangObj.nativeName}</span>
          <ChevronDown className={`w-3.5 h-3.5 text-slate-400 transition-transform ${isLangOpen ? 'rotate-180' : ''}`} />
        </button>

        {/* 9 Language Menu Popover */}
        {isLangOpen && (
          <div className="absolute right-0 mt-2 w-48 rounded-2xl bg-[#1A2436] border border-[#1E293B] shadow-2xl py-2 backdrop-blur-xl animate-in fade-in zoom-in-95 duration-150">
            {availableLanguages.map(l => (
              <button
                key={l.code}
                onClick={() => {
                  setLanguage(l.code);
                  setIsLangOpen(false);
                }}
                className={`w-full px-4 py-2 text-left text-xs flex items-center justify-between hover:bg-[#2563EB]/20 transition-colors cursor-pointer ${
                  language === l.code ? 'text-[#60A5FA] font-bold bg-[#2563EB]/10' : 'text-slate-300'
                }`}
              >
                <span>{l.nativeName}</span>
                {language === l.code && <Check className="w-3.5 h-3.5 text-[#60A5FA]" />}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ================= KUTUSUZ & FERAH MERKEZİ ALAN ================= */}
      <main className="flex-1 w-full max-w-lg mx-auto px-6 py-8 flex flex-col items-center justify-center">
        
        {/* Beyaz SyncSwift Logosu */}
        <div className="mb-5 flex items-center justify-center select-none">
          <img
            src="./assets/logo-icon/logo.png"
            alt="SyncSwift"
            className="h-16 sm:h-20 w-auto object-contain transition-all drop-shadow-md"
          />
        </div>

        {/* Başlık ve Açıklama */}
        <div className="text-center mb-6 max-w-md">
          <h1 className="text-2xl sm:text-[26px] font-bold tracking-tight text-white mb-2">
            {t.scanWithPhone}
          </h1>
          <p className="text-sm text-[#94A3B8] leading-relaxed">
            {t.scanWithPhoneDesc}
          </p>
        </div>

        {/* ================= KUTUSUZ: DOĞRUDAN ZEMİN ÜZERİNDE ELEMANLAR ================= */}
        <div className="w-full flex flex-col items-center text-center">
          
          {/* YALNIZCA BEYAZ QR KOD TUVALİ (KUTUSUZ, DOĞRUDAN ZEMİNDE) */}
          <div
            onClick={simulatePhonePairing}
            title={language === 'tr' ? 'Telefon taramasını test etmek için tıklayın' : 'Click to simulate phone pairing'}
            className="p-3.5 bg-white rounded-2xl shadow-2xl flex items-center justify-center cursor-pointer hover:scale-[1.03] active:scale-[0.98] transition-all relative group ring-4 ring-white/5"
          >
            <QRCodeSVG
              value={qrPayload}
              size={196}
              level="M"
              marginSize={1}
            />

            {/* Simüle Edilen Başarı Durumu */}
            {isPairingSuccess && (
              <div className="absolute inset-0 bg-emerald-600/95 rounded-2xl flex flex-col items-center justify-center text-white backdrop-blur-xs animate-in zoom-in-95 duration-200">
                <CheckCircle2 className="w-12 h-12 mb-2 animate-bounce" />
                <span className="text-xs font-bold tracking-wide uppercase">{t.pairSuccess}</span>
              </div>
            )}
          </div>

          {/* VEYA BÖLÜMÜ */}
          <div className="w-full max-w-xs flex items-center my-6">
            <div className="flex-1 h-px bg-white/10" />
            <span className="px-4 text-[11px] uppercase tracking-widest font-semibold text-[#94A3B8]">{t.or}</span>
            <div className="flex-1 h-px bg-white/10" />
          </div>

          {/* 6 HANELİ EŞLEŞME KODU ALANI */}
          <div className="flex flex-col items-center">
            <span className="text-[11px] font-medium tracking-wider uppercase text-[#94A3B8] mb-2">
              {t.pairCodeLabel}
            </span>

            {/* Büyük Monospaced Kod ve Kopyalama Butonu */}
            <div className="flex items-center gap-3">
              <span className="text-3xl sm:text-4xl font-black tracking-[0.22em] text-white font-mono select-all px-3 py-1">
                {pairCode}
              </span>
              <button
                type="button"
                onClick={handleCopyCode}
                title={copied ? (language === 'tr' ? 'Kopyalandı' : 'Copied') : (language === 'tr' ? 'Kodu Kopyala' : 'Copy Code')}
                className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white border border-white/10 transition-all cursor-pointer shadow-sm active:scale-95"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* GERİ SAYIM (1 DAKİKA) & CANLI DURUM */}
          <div className="mt-5 flex items-center justify-center gap-4 text-xs text-[#94A3B8]">
            <div className="flex items-center gap-2">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
              </span>
              <span className="text-[12px] text-slate-300">{t.waitingForPhone}</span>
            </div>

            <span className="text-slate-600">&bull;</span>

            <div className="flex items-center gap-2">
              <span className="font-mono text-slate-300 font-medium">{formatTimer(timeLeft)}</span>
              <button
                type="button"
                onClick={generateNewPairCode}
                title={t.refreshCode}
                disabled={isPublishing}
                className="p-1 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isPublishing ? 'animate-spin' : ''}`} />
              </button>
            </div>
          </div>

        </div>

        {/* ================= YEREL KULLANICI OLARAK DEVAM ET ================= */}
        <div className="mt-8 flex flex-col items-center">
          <button
            type="button"
            onClick={onContinueLocally}
            className="flex items-center gap-2 px-5 py-2.5 rounded-full text-[13.5px] font-medium text-slate-300 hover:text-white bg-[#1E293B]/70 hover:bg-[#1E293B] border border-white/10 shadow-sm transition-all cursor-pointer active:scale-98 group"
          >
            <span>{t.continueLocally}</span>
            <ArrowRight className="w-3.5 h-3.5 text-[#60A5FA] group-hover:translate-x-0.5 transition-transform" />
          </button>
        </div>

        {/* Alt Bilgi & Güvenlik */}
        <div className="mt-6 text-center text-[11px] leading-relaxed text-[#94A3B8] max-w-xs">
          <span>{t.privacy} &bull; </span>
          <span className="text-[#60A5FA] hover:underline cursor-pointer">{t.termsOfService}</span>
          <span> &bull; </span>
          <span className="text-[#60A5FA] hover:underline cursor-pointer">{t.privacyPolicy}</span>
        </div>

      </main>

      {/* Footer */}
      <footer className="py-4 text-center text-[11px] text-slate-600">
        SyncSwift Desktop &bull; v1.0.3
      </footer>

    </div>
  );
};
