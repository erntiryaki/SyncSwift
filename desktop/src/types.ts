export type AppLanguage = 'en' | 'tr' | 'de' | 'fr' | 'es' | 'it' | 'pt' | 'ru' | 'ar';

export interface LanguageOption {
  code: AppLanguage;
  name: string;
  nativeName: string;
  isRtl?: boolean;
}

export type ThemeMode = 'system' | 'dark' | 'light';

export type IdentityProvider = 'email' | 'google' | 'apple' | 'local';

export interface UserProfile {
  email: string;
  firstName: string;
  lastName: string;
  provider: IdentityProvider;
  isPro: boolean;
  proStartDate?: number;
  proRenewalDate?: number;
}

export interface DiscoveredDevice {
  id: string;
  name: string;
  host: string;
  port: number;
  osType: 'Windows' | 'macOS' | 'Android' | 'iOS' | 'Linux' | 'Other';
  isSelf?: boolean;
  isOnline?: boolean;
  isRemote?: boolean;
  pushToken?: string;
  language?: string;
  modelCode?: string;
}

export interface ClipboardItem {
  id: string;
  content: string;
  timestamp: number;
  type: 'text' | 'url' | 'phone' | 'color' | 'code';
  isFavorite?: boolean;
  isEphemeral?: boolean;
  expiresAt?: number;
  sourceDeviceName?: string;
}

export type TransferStatus = 'queued' | 'transferring' | 'completed' | 'failed' | 'cancelled';

export interface TransferItem {
  id: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  direction: 'incoming' | 'outgoing';
  targetDeviceName: string;
  targetDeviceOs: string;
  progress: number; // 0 - 100
  speedBytesPerSec: number;
  status: TransferStatus;
  timestamp: number;
  filePath?: string;
}

export interface SmartAction {
  type: 'open_url' | 'call_phone' | 'copy_hex' | 'copy_rgb' | 'format_code';
  label: string;
  payload: string;
}
