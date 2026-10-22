import { invoke } from "@tauri-apps/api/core";

export interface RemoteDevice {
  id: string;
  name: string;
  host: string;
  port: number;
  os: string;
  isOnline: boolean;
  lastSeen?: number;
}

export interface TransferHistoryItem {
  id: string;
  fileName: string;
  fileSize: number;
  isSent: boolean;
  peerName: string;
  status: 'completed' | 'failed' | 'in_progress';
  timestamp: number;
  localPath?: string;
}

export async function getLocalIp(): Promise<string> {
  try {
    return await invoke<string>("get_local_ip");
  } catch (_e) {
    return "127.0.0.1";
  }
}

export async function getComputerName(): Promise<string> {
  try {
    return await invoke<string>("get_computer_name");
  } catch (_e) {
    return "Windows PC";
  }
}

export async function openDownloadsFolder(): Promise<void> {
  try {
    await invoke("open_downloads_folder");
  } catch (_e) {
    console.error("Failed to open downloads folder");
  }
}

function cleanHost(host: string): string {
  if (!host) return "";
  return host.replace(/^https?:\/\//i, "").split(":")[0].trim();
}

// 1. Send File directly over local LAN via HTTP POST (LocalSend Architecture) - ZERO CONFIRMATION
export async function sendFileToDevice(
  target: RemoteDevice,
  file: File,
  selfName: string,
  onProgress?: (progress: number) => void
): Promise<boolean> {
  try {
    const clean = cleanHost(target.host);
    const effectivePort = target.port || 52424;
    const url = `http://${clean}:${effectivePort}/api/file`;

    // Standard XMLHttpRequest for upload progress tracking
    return new Promise((resolve) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", url, true);
      xhr.timeout = 180000; // 3 minutes timeout

      xhr.setRequestHeader("Content-Type", "application/octet-stream");
      xhr.setRequestHeader("X-File-Name", encodeURIComponent(file.name));
      xhr.setRequestHeader("X-File-Size", file.size.toString());
      xhr.setRequestHeader("X-Mime-Type", file.type || "application/octet-stream");
      xhr.setRequestHeader("X-Sender-Name", encodeURIComponent(selfName));

      if (xhr.upload && onProgress) {
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            const p = Math.round((e.loaded / e.total) * 100);
            onProgress(p);
          }
        };
      }

      xhr.onload = () => {
        if (xhr.status === 200) {
          resolve(true);
        } else {
          console.error("Upload error status:", xhr.status, xhr.responseText);
          resolve(false);
        }
      };

      xhr.onerror = () => {
        console.error("XHR network error sending file to", target.host);
        resolve(false);
      };

      xhr.ontimeout = () => {
        console.error("XHR timeout sending file to", target.host);
        resolve(false);
      };

      xhr.send(file);
    });
  } catch (e) {
    console.error("Error in sendFileToDevice:", e);
    return false;
  }
}

// 2. Send Clipboard directly over LAN via HTTP POST - ZERO CONFIRMATION
export async function sendClipboardToDevice(
  target: RemoteDevice,
  text: string,
  selfName: string
): Promise<boolean> {
  try {
    const clean = cleanHost(target.host);
    const effectivePort = target.port || 52424;
    const url = `http://${clean}:${effectivePort}/api/clipboard`;

    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "X-Sender-Name": encodeURIComponent(selfName)
      },
      body: JSON.stringify({ text }),
      signal: AbortSignal.timeout(5000)
    });

    return res.ok;
  } catch (e) {
    console.error("Error sending clipboard:", e);
    return false;
  }
}

// 3. Send Pair request directly over LAN via HTTP POST - ZERO CONFIRMATION
export async function pairWithDevice(
  targetHost: string,
  targetPort: number,
  selfId: string,
  selfName: string,
  selfHost: string
): Promise<boolean> {
  try {
    const clean = cleanHost(targetHost);
    const effectivePort = targetPort || 52424;
    const url = `http://${clean}:${effectivePort}/api/pair`;

    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=utf-8"
      },
      body: JSON.stringify({
        id: selfId,
        name: selfName,
        host: selfHost,
        port: 52424,
        os: "Windows"
      }),
      signal: AbortSignal.timeout(4000)
    });

    return res.ok;
  } catch (e) {
    console.error("Error pairing with device:", e);
    return false;
  }
}

// 4. Send Unpair request directly over LAN
export async function unpairDevice(
  targetHost: string,
  targetPort: number,
  selfId: string,
  selfHost: string
): Promise<boolean> {
  try {
    const clean = cleanHost(targetHost);
    const effectivePort = targetPort || 52424;
    const url = `http://${clean}:${effectivePort}/api/unpair`;

    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=utf-8"
      },
      body: JSON.stringify({
        id: selfId,
        host: selfHost
      }),
      signal: AbortSignal.timeout(3000)
    });

    return res.ok;
  } catch (_e) {
    return false;
  }
}

// 5. Ping device (Heartbeat check)
export async function pingDevice(targetHost: string, targetPort: number): Promise<boolean> {
  try {
    const clean = cleanHost(targetHost);
    const effectivePort = targetPort || 52424;
    const url = `http://${clean}:${effectivePort}/api/ping`;
    const res = await fetch(url, {
      method: "GET",
      signal: AbortSignal.timeout(1500)
    });
    return res.ok;
  } catch (_e) {
    return false;
  }
}

export function formatBytes(bytes: number): string {
  if (bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
}
