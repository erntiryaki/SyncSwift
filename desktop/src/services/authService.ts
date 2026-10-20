/**
 * SyncSwift Desktop Authentication Service
 * Fully aligned with Mobile App (Supabase + Platform-specific Social Logins)
 */

export const SYNC_SWIFT_CONFIG = {
  SUPABASE_URL: (import.meta.env.VITE_SUPABASE_URL as string) || 'https://api.syncswift.net',
  SUPABASE_ANON_KEY: (import.meta.env.VITE_SUPABASE_ANON_KEY as string) || '',
  SUPABASE_SERVICE_ROLE_KEY: (import.meta.env.VITE_SUPABASE_SERVICE_ROLE_KEY as string) || '',
  GOOGLE_WEB_CLIENT_ID: (import.meta.env.VITE_GOOGLE_WEB_CLIENT_ID as string) || ''
};

export interface AuthResponse {
  success: boolean;
  user?: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    provider: 'email' | 'google' | 'apple' | 'local';
    isPro: boolean;
  };
  accessToken?: string;
  error?: string;
}

/** Check if running on macOS */
export function isMac(): boolean {
  if (typeof navigator === 'undefined') return false;
  const platform = navigator.platform || '';
  const userAgent = navigator.userAgent || '';
  return /Mac|iPhone|iPod|iPad/i.test(platform) || /Macintosh/i.test(userAgent);
}

/** Password validation matching mobile app Auth.kt:
 * Min 8 chars, 1 uppercase, 1 lowercase, 1 special character
 */
export function isValidPassword(password: string): boolean {
  if (password.length < 8) return false;
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasSpecial = /[^A-Za-z0-9]/.test(password);
  return hasUpper && hasLower && hasSpecial;
}

/** Email validation matching mobile app Auth.kt */
export function isValidEmail(email: string): boolean {
  const clean = email.trim();
  const atIndex = clean.indexOf('@');
  const lastDotIndex = clean.lastIndexOf('.');
  return atIndex > 0 && lastDotIndex > atIndex + 1 && lastDotIndex < clean.length - 1;
}

/**
 * Fetch user profile from Supabase profiles table (identical to mobile app)
 */
export async function fetchUserProfile(emailOrId: string): Promise<{
  email?: string;
  firstName?: string;
  lastName?: string;
  provider?: 'email' | 'google' | 'apple' | 'local';
  isPro?: boolean;
} | null> {
  const clean = emailOrId.trim();
  try {
    const key = SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY;
    const query = clean.includes('@')
      ? `email=eq.${encodeURIComponent(clean.toLowerCase())}`
      : `or=(id.eq.${encodeURIComponent(clean)},email.eq.${encodeURIComponent(clean.toLowerCase())})`;
    const res = await fetch(
      `${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/profiles?${query}&select=*`,
      {
        method: 'GET',
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`,
          Accept: 'application/json'
        }
      }
    );
    if (res.ok) {
      const list = await res.json();
      if (Array.isArray(list) && list.length > 0) {
        const item = list[0];
        return {
          email: item.email || clean,
          firstName: item.first_name || '',
          lastName: item.last_name || '',
          provider: item.auth_provider || 'email',
          isPro: Boolean(item.is_pro)
        };
      }
    }
  } catch (err) {
    console.warn('Fetch user profile error:', err);
  }
  return null;
}

/**
 * Check if an email is already registered in Supabase
 * Identical to mobile SupabaseClient.android.kt checkUserExists
 */
export async function checkUserExists(email: string): Promise<boolean> {
  const cleanEmail = email.trim().toLowerCase();
  try {
    const key = SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY;
    const res = await fetch(
      `${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(cleanEmail)}&select=id`,
      {
        method: 'GET',
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`,
          Accept: 'application/json'
        }
      }
    );
    if (res.ok) {
      const data = await res.json();
      const exists = Array.isArray(data) && data.length > 0 && Boolean(data[0]?.id);
      return exists;
    }
  } catch (err) {
    console.warn('Check user exists network check failed, falling back:', err);
  }
  // Default to true so user proceeds to password prompt
  return true;
}

/**
 * Sign in with email and password via Supabase Auth
 */
export async function signInWithEmailPassword(
  email: string,
  password: string
): Promise<AuthResponse> {
  const cleanEmail = email.trim().toLowerCase();
  try {
    const res = await fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/auth/v1/token?grant_type=password`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY
      },
      body: JSON.stringify({
        email: cleanEmail,
        password
      })
    });

    const data = await res.json();

    if (!res.ok) {
      const msg = data.error_description || data.msg || data.message || 'E-Postanız veya şifreniz hatalıdır. Lütfen tekrar deneyiniz.';
      return { success: false, error: msg };
    }

    // Enrich with profile data if available
    const profile = await fetchUserProfile(cleanEmail);
    const userObj = data.user;
    const meta = userObj?.user_metadata || {};
    const firstName = profile?.firstName || meta.first_name || meta.name || cleanEmail.split('@')[0];
    const lastName = profile?.lastName || meta.last_name || '';

    return {
      success: true,
      user: {
        id: userObj.id,
        email: userObj.email || cleanEmail,
        firstName,
        lastName,
        provider: (profile?.provider as any) || 'email',
        isPro: profile?.isPro ?? Boolean(meta.is_pro ?? true)
      },
      accessToken: data.access_token
    };
  } catch (err: any) {
    return {
      success: false,
      error: err.message || 'Ağ hatası. Lütfen internet bağlantınızı kontrol edin.'
    };
  }
}

/**
 * Register new user with email & password via Supabase
 */
export async function signUpWithEmailPassword(
  email: string,
  password: string,
  firstName = ''
): Promise<AuthResponse> {
  const cleanEmail = email.trim().toLowerCase();
  try {
    const res = await fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/auth/v1/signup`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY
      },
      body: JSON.stringify({
        email: cleanEmail,
        password,
        data: {
          first_name: firstName || cleanEmail.split('@')[0],
          is_pro: false
        }
      })
    });

    const data = await res.json();
    if (!res.ok) {
      return { success: false, error: data.msg || data.message || 'Kayıt başarısız oldu.' };
    }

    return {
      success: true,
      user: {
        id: data.user?.id || 'new-user',
        email: cleanEmail,
        firstName: firstName || cleanEmail.split('@')[0],
        lastName: '',
        provider: 'email',
        isPro: false
      },
      accessToken: data.access_token
    };
  } catch (err: any) {
    return { success: false, error: err.message || 'Bağlantı hatası oluştu.' };
  }
}

/**
 * Send password reset email via Supabase Auth
 */
export async function sendPasswordResetEmail(email: string): Promise<{ success: boolean; error?: string }> {
  const cleanEmail = email.trim().toLowerCase();
  try {
    const res = await fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/auth/v1/recover`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY
      },
      body: JSON.stringify({ email: cleanEmail })
    });
    if (!res.ok) {
      const data = await res.json();
      return { success: false, error: data.msg || data.message || 'Sıfırlama e-postası gönderilemedi.' };
    }
    return { success: true };
  } catch (err: any) {
    return { success: false, error: err.message || 'Ağ hatası.' };
  }
}

/**
 * Trigger OAuth Sign-in (Google / Apple)
 */
export function getOAuthUrl(provider: 'google' | 'apple'): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:5173';
  return `${SYNC_SWIFT_CONFIG.SUPABASE_URL}/auth/v1/authorize?provider=${provider}&redirect_to=${encodeURIComponent(origin)}`;
}

/**
 * Social Sign-in via Supabase (Google & Apple)
 * Aligned with mobile app SupabaseNetwork & AuthPolicy
 */
export async function signInWithSocial(
  email: string,
  provider: 'google' | 'apple'
): Promise<AuthResponse> {
  const cleanEmail = email.trim().toLowerCase();
  if (!isValidEmail(cleanEmail)) {
    return {
      success: false,
      error: 'Lütfen geçerli bir e-posta adresi girin.'
    };
  }

  try {
    // 1. Check if user already exists with email_password provider
    const existingProfile = await fetchUserProfile(cleanEmail);
    if (existingProfile && existingProfile.provider === 'email') {
      return {
        success: false,
        error: 'Bu e-posta adresi şifre ile kayıt edilmiş. Lütfen e-posta ve şifrenizle giriş yapın.'
      };
    }

    // 2. Derive user name
    let firstName = existingProfile?.firstName || '';
    let lastName = existingProfile?.lastName || '';
    if (!firstName) {
      const parts = cleanEmail.split('@')[0].split(/[._-]/);
      firstName = parts[0] ? parts[0].charAt(0).toUpperCase() + parts[0].slice(1) : (provider === 'google' ? 'Google' : 'Apple');
      lastName = parts[1] ? parts[1].charAt(0).toUpperCase() + parts[1].slice(1) : 'User';
    }

    // 3. Upsert profile in Supabase
    try {
      const key = SYNC_SWIFT_CONFIG.SUPABASE_SERVICE_ROLE_KEY || SYNC_SWIFT_CONFIG.SUPABASE_ANON_KEY;
      await fetch(`${SYNC_SWIFT_CONFIG.SUPABASE_URL}/rest/v1/profiles`, {
        method: 'POST',
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`,
          'Content-Type': 'application/json',
          Prefer: 'resolution=merge-duplicates'
        },
        body: JSON.stringify({
          email: cleanEmail,
          first_name: firstName,
          last_name: lastName,
          auth_provider: provider,
          is_pro: existingProfile?.isPro ?? true
        })
      });
    } catch (e) {
      console.warn('Upsert profile failed:', e);
    }

    return {
      success: true,
      user: {
        id: `social-${cleanEmail}`,
        email: cleanEmail,
        firstName,
        lastName,
        provider,
        isPro: existingProfile?.isPro ?? true
      }
    };
  } catch (err: any) {
    return {
      success: false,
      error: err.message || 'Sosyal giriş yapılırken bir hata oluştu.'
    };
  }
}

declare global {
  interface Window {
    google?: any;
  }
}

/**
 * Ensures Google Identity Services client is loaded
 */
export function ensureGoogleGsiLoaded(): Promise<boolean> {
  return new Promise((resolve) => {
    if (typeof window === 'undefined') return resolve(false);
    if (window.google?.accounts?.oauth2 || window.google?.accounts?.id) {
      return resolve(true);
    }
    const existingScript = document.querySelector('script[src*="accounts.google.com/gsi/client"]');
    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(true));
      setTimeout(() => resolve(Boolean(window.google?.accounts)), 2000);
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.head.appendChild(script);
  });
}

/**
 * Launch Google Account Chooser via Web Client ID (Identical to mobile GoogleAuth launcher)
 */
export async function launchGoogleWebAuth(
  onSuccess: (userData: {
    email: string;
    displayName?: string;
    givenName?: string;
    familyName?: string;
    idToken?: string;
  }) => void,
  onError: (error: string) => void
) {
  try {
    const isLoaded = await ensureGoogleGsiLoaded();
    if (!isLoaded || !window.google?.accounts?.oauth2) {
      onError('Google kimlik doğrulama servisi yüklenemedi. İnternet bağlantınızı kontrol edin.');
      return;
    }

    const client = window.google.accounts.oauth2.initTokenClient({
      client_id: SYNC_SWIFT_CONFIG.GOOGLE_WEB_CLIENT_ID,
      scope: 'openid email profile',
      prompt: 'select_account',
      callback: async (tokenResponse: any) => {
        if (tokenResponse.error) {
          if (tokenResponse.error === 'popup_closed_by_user' || tokenResponse.error === 'access_denied') {
            onError('canceled');
          } else {
            onError(tokenResponse.error_description || tokenResponse.error || 'Google girişi başarısız.');
          }
          return;
        }

        if (tokenResponse.access_token) {
          try {
            const userRes = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
              headers: {
                Authorization: `Bearer ${tokenResponse.access_token}`
              }
            });
            if (userRes.ok) {
              const u = await userRes.json();
              onSuccess({
                email: u.email || '',
                displayName: u.name || `${u.given_name || ''} ${u.family_name || ''}`.trim(),
                givenName: u.given_name || '',
                familyName: u.family_name || '',
                idToken: tokenResponse.access_token
              });
              return;
            }
          } catch (e: any) {
            console.error('Failed to fetch google userinfo:', e);
          }
        }
        onError('Google hesabı bilgileri alınamadı.');
      }
    });

    client.requestAccessToken({ prompt: 'select_account' });
  } catch (err: any) {
    onError(err.message || 'Google oturum açma penceresi açılamadı.');
  }
}
