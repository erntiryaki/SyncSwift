import { createClient } from "@supabase/supabase-js";

export const SUPABASE_URL = "https://api.syncswift.net";
export const SUPABASE_ANON_KEY = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc5MjI0NTA5MiwiZXhwIjo0OTQ3NDEzNDYwLCJyb2xlIjoiYW5vbiJ9.W4EgdJGRf2-pBcLOp8Ofh4KYgcD8j2EvFhJu3jfz1Vs";

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

export async function publishPairingCode(
  code: string,
  host: string,
  port: number,
  deviceName: string,
  deviceId: string
): Promise<boolean> {
  try {
    const cleanCode = code.replace(/\s+/g, "");
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
    const cleanDeviceId = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(deviceId)
      ? deviceId
      : (typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : "2ffad419-fd5d-44bb-9102-ced1ebe3a603");

    const { error } = await supabase.from("pairing_codes").upsert(
      {
        code: cleanCode,
        host: host,
        port: port,
        device_name: deviceName,
        device_id: cleanDeviceId,
        device_type: "Windows",
        expires_at: expiresAt
      },
      { onConflict: "code" }
    );

    if (error) {
      console.warn("Supabase pairing_codes upsert warning:", error.message);
    }
    return !error;
  } catch (e) {
    console.error("Error publishing pairing code to Supabase:", e);
    return false;
  }
}

export async function getPairingCodeDetails(code: string): Promise<{
  deviceId: string;
  deviceName: string;
  host: string;
  port: number;
  deviceType: string;
} | null> {
  try {
    const cleanCode = code.replace(/\s+/g, "");
    const { data, error } = await supabase
      .from("pairing_codes")
      .select("device_id, device_name, host, port, device_type, expires_at")
      .eq("code", cleanCode)
      .maybeSingle();

    if (error || !data) return null;

    if (data.expires_at) {
      // Allow 5 minutes clock drift tolerance between devices
      const isExpired = new Date(data.expires_at).getTime() + (5 * 60 * 1000) < Date.now();
      if (isExpired) return null;
    }

    const host = data.host || `remote-${cleanCode}`;

    return {
      deviceId: data.device_id || `dev-${cleanCode}`,
      deviceName: data.device_name || "SyncSwift Device",
      host: host,
      port: data.port || 52424,
      deviceType: data.device_type || "Android"
    };
  } catch (e) {
    console.error("Error getting pairing code details:", e);
    return null;
  }
}

export async function checkIfCodeClaimed(code: string): Promise<{
  claimed: boolean;
  pushToken?: string;
  userId?: string;
} | null> {
  try {
    const cleanCode = code.replace(/\s+/g, "");
    const { data, error } = await supabase
      .from("pairing_codes")
      .select("push_token, user_id")
      .eq("code", cleanCode)
      .maybeSingle();

    if (error || !data) return null;
    if (data.push_token || data.user_id) {
      return {
        claimed: true,
        pushToken: data.push_token,
        userId: data.user_id
      };
    }
    return { claimed: false };
  } catch (_e) {
    return null;
  }
}


