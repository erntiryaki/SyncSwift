import { en } from './en';
import { tr } from './tr';
import { de } from './de';
import { fr } from './fr';
import { es } from './es';
import { it } from './it';
import { pt } from './pt';
import { ru } from './ru';
import { ar } from './ar';
import type { AppLanguage, LanguageOption } from '../types';

export { en, tr, de, fr, es, it, pt, ru, ar };

export const translations: Record<AppLanguage, typeof en> = {
  en,
  tr,
  de,
  fr,
  es,
  it,
  pt,
  ru,
  ar,
};

export const availableLanguages: LanguageOption[] = [
  { code: 'en', name: 'English', nativeName: 'English' },
  { code: 'tr', name: 'Turkish', nativeName: 'Türkçe' },
  { code: 'de', name: 'German', nativeName: 'Deutsch' },
  { code: 'fr', name: 'French', nativeName: 'Français' },
  { code: 'es', name: 'Spanish', nativeName: 'Español' },
  { code: 'it', name: 'Italian', nativeName: 'Italiano' },
  { code: 'pt', name: 'Portuguese', nativeName: 'Português' },
  { code: 'ru', name: 'Russian', nativeName: 'Русский' },
  { code: 'ar', name: 'Arabic', nativeName: 'العربية', isRtl: true },
];
