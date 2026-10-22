pub use syncswift_core as crypto;
use syncswift_core::{CryptoEngine, DeviceIdentityDto};
use std::sync::Mutex;
use serde::{Serialize, Deserialize};

static CACHED_IDENTITY: Mutex<Option<DeviceIdentityDto>> = Mutex::new(None);
static ACTIVE_TRANSFERS: Mutex<Vec<TransferItemDto>> = Mutex::new(Vec::new());
static LAN_DEVICES: Mutex<Vec<LanDeviceDto>> = Mutex::new(Vec::new());
static VAULT_FILES: Mutex<Vec<VaultFileDto>> = Mutex::new(Vec::new());
static SYNC_RULES: Mutex<Vec<SyncRuleDto>> = Mutex::new(Vec::new());

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct PairingSessionDto {
    pub session_id: String,
    pub pairing_code: String,
    pub sas_code: String,
    pub status: String,
    pub expires_at_ms: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct LanDeviceDto {
    pub device_id: String,
    pub device_name: String,
    pub os_type: String,
    pub host: String,
    pub port: u16,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct TransferItemDto {
    pub transfer_id: String,
    pub file_name: String,
    pub file_size: u64,
    pub bytes_transferred: u64,
    pub speed_bps: u64,
    pub status: String, // "PREPARING", "TRANSFERRING", "COMPLETED", "FAILED", "CANCELLED"
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct VaultFileDto {
    pub id: String,
    pub file_name: String,
    pub file_size: u64,
    pub mime_type: String,
    pub is_starred: bool,
    pub created_at_ms: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct RelayConfigDto {
    pub stun_url: String,
    pub turn_url: String,
    pub minio_endpoint: String,
    pub minio_vault_bucket: String,
    pub minio_temp_bucket: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SyncRuleDto {
    pub id: String,
    pub name: String,
    pub local_path: String,
    pub remote_device_id: String,
    pub remote_path: String,
    pub direction: String,
    pub is_enabled: bool,
}

#[tauri::command]
fn get_or_create_device_identity(device_name: Option<String>) -> Result<DeviceIdentityDto, String> {
    let mut guard = CACHED_IDENTITY.lock().map_err(|e| e.to_string())?;
    if let Some(ref identity) = *guard {
        return Ok(identity.clone());
    }

    let (_, verifying) = CryptoEngine::generate_signing_key();
    let (_, agree_pub) = CryptoEngine::generate_agreement_key();

    let name = device_name.unwrap_or_else(|| {
        hostname::get()
            .map(|h| h.to_string_lossy().to_string())
            .unwrap_or_else(|_| "SyncSwift Desktop".to_string())
    });

    let identity = DeviceIdentityDto {
        device_id: format!("win_{}", &CryptoEngine::to_base64(&verifying.to_bytes())[..12]),
        device_name: name,
        os_type: "windows".to_string(),
        signing_pubkey_b64: CryptoEngine::to_base64(&verifying.to_bytes()),
        agreement_pubkey_b64: CryptoEngine::to_base64(agree_pub.as_bytes()),
    };

    *guard = Some(identity.clone());
    Ok(identity)
}

fn get_local_ip() -> String {
    if let Ok(socket) = std::net::UdpSocket::bind("0.0.0.0:0") {
        if socket.connect("8.8.8.8:80").is_ok() {
            if let Ok(addr) = socket.local_addr() {
                return addr.ip().to_string();
            }
        }
    }
    "127.0.0.1".to_string()
}

fn start_lan_discovery_thread() {
    std::thread::spawn(|| {
        let socket = match std::net::UdpSocket::bind("0.0.0.0:52424") {
            Ok(s) => s,
            Err(_) => return,
        };
        let _ = socket.set_broadcast(true);
        let _ = socket.set_read_timeout(Some(std::time::Duration::from_millis(1500)));

        let mut buf = [0u8; 1024];
        loop {
            let my_ip = get_local_ip();
            let my_name = hostname::get()
                .map(|h| h.to_string_lossy().to_string())
                .unwrap_or_else(|_| "Windows PC".to_string());
            let msg = format!("SYNCSWIFT:ANNOUNCE|win_desktop|{}|Windows|52424", my_name);
            let _ = socket.send_to(msg.as_bytes(), "255.255.255.255:52424");

            if let Ok((len, src)) = socket.recv_from(&mut buf) {
                if let Ok(text) = std::str::from_utf8(&buf[..len]) {
                    if text.starts_with("SYNCSWIFT:ANNOUNCE|") {
                        let parts: Vec<&str> = text.split('|').collect();
                        if parts.len() >= 5 {
                            let peer_id = parts[1].to_string();
                            let peer_name = parts[2].to_string();
                            let peer_os = parts[3].to_string();
                            let peer_port: u16 = parts[4].parse().unwrap_or(52424);
                            let peer_host = src.ip().to_string();

                            if peer_host != my_ip && peer_host != "127.0.0.1" {
                                if let Ok(mut devs) = LAN_DEVICES.lock() {
                                    devs.retain(|d| d.device_id != peer_id && d.host != peer_host);
                                    devs.push(LanDeviceDto {
                                        device_id: peer_id,
                                        device_name: peer_name,
                                        os_type: peer_os,
                                        host: peer_host,
                                        port: peer_port,
                                    });
                                }
                            }
                        }
                    }
                }
            }
            std::thread::sleep(std::time::Duration::from_millis(2500));
        }
    });
}

#[tauri::command]
fn create_pairing_session(_device_id: String) -> Result<PairingSessionDto, String> {
    let rand_num: u32 = rand::random::<u32>() % 900000 + 100000;
    let sas_code = format!("{:06}", rand_num);
    let pairing_code = sas_code.clone();

    Ok(PairingSessionDto {
        session_id: format!("sess_{}", rand_num),
        pairing_code,
        sas_code,
        status: "PENDING".to_string(),
        expires_at_ms: 300_000,
    })
}

#[tauri::command]
fn get_local_ip_address() -> Result<String, String> {
    Ok(get_local_ip())
}

#[tauri::command]
fn approve_pairing_session(session_id: String, _peer_device_id: String) -> Result<bool, String> {
    Ok(!session_id.is_empty())
}

#[tauri::command]
fn get_lan_devices() -> Result<Vec<LanDeviceDto>, String> {
    let guard = LAN_DEVICES.lock().map_err(|e| e.to_string())?;
    Ok(guard.clone())
}

#[tauri::command]
fn add_or_update_lan_device(device: LanDeviceDto) -> Result<bool, String> {
    let mut guard = LAN_DEVICES.lock().map_err(|e| e.to_string())?;
    guard.retain(|d| d.device_id != device.device_id);
    guard.push(device);
    Ok(true)
}

#[tauri::command]
fn get_active_transfers() -> Result<Vec<TransferItemDto>, String> {
    let guard = ACTIVE_TRANSFERS.lock().map_err(|e| e.to_string())?;
    Ok(guard.clone())
}

#[tauri::command]
fn start_lan_transfer(target_device_id: String, file_name: String, file_size: u64) -> Result<TransferItemDto, String> {
    let mut guard = ACTIVE_TRANSFERS.lock().map_err(|e| e.to_string())?;
    let transfer_id = format!("tx_{}_{}", target_device_id, rand::random::<u32>() % 10000);
    let item = TransferItemDto {
        transfer_id: transfer_id.clone(),
        file_name,
        file_size,
        bytes_transferred: 0,
        speed_bps: 12_500_000, // 12.5 MB/s typical LAN initial rate
        status: "TRANSFERRING".to_string(),
    };
    guard.insert(0, item.clone());
    Ok(item)
}

#[tauri::command]
fn cancel_transfer(transfer_id: String) -> Result<bool, String> {
    let mut guard = ACTIVE_TRANSFERS.lock().map_err(|e| e.to_string())?;
    for item in guard.iter_mut() {
        if item.transfer_id == transfer_id {
            item.status = "CANCELLED".to_string();
            return Ok(true);
        }
    }
    Ok(false)
}

#[tauri::command]
fn get_vault_files() -> Result<Vec<VaultFileDto>, String> {
    let guard = VAULT_FILES.lock().map_err(|e| e.to_string())?;
    Ok(guard.clone())
}

#[tauri::command]
fn upload_vault_file(file_name: String, file_size: u64, mime_type: String) -> Result<VaultFileDto, String> {
    let mut guard = VAULT_FILES.lock().map_err(|e| e.to_string())?;
    let item = VaultFileDto {
        id: format!("vault_{}", rand::random::<u32>() % 100000),
        file_name,
        file_size,
        mime_type,
        is_starred: false,
        created_at_ms: std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0),
    };
    guard.insert(0, item.clone());
    Ok(item)
}

#[tauri::command]
fn delete_vault_file(file_id: String) -> Result<bool, String> {
    let mut guard = VAULT_FILES.lock().map_err(|e| e.to_string())?;
    let before_len = guard.len();
    guard.retain(|f| f.id != file_id);
    Ok(guard.len() < before_len)
}

#[tauri::command]
fn get_relay_config() -> Result<RelayConfigDto, String> {
    Ok(RelayConfigDto {
        stun_url: "stun:127.0.0.1:3478".to_string(),
        turn_url: "turn:127.0.0.1:3478".to_string(),
        minio_endpoint: "http://127.0.0.1:9000".to_string(),
        minio_vault_bucket: "vault".to_string(),
        minio_temp_bucket: "transfers-temp".to_string(),
    })
}

#[tauri::command]
fn sync_clipboard_text(text: String) -> Result<bool, String> {
    Ok(!text.is_empty())
}

#[tauri::command]
fn get_sync_rules() -> Result<Vec<SyncRuleDto>, String> {
    let guard = SYNC_RULES.lock().map_err(|e| e.to_string())?;
    Ok(guard.clone())
}

#[tauri::command]
fn add_sync_rule(
    name: String,
    local_path: String,
    remote_device_id: String,
    remote_path: String,
    direction: String,
) -> Result<SyncRuleDto, String> {
    let mut guard = SYNC_RULES.lock().map_err(|e| e.to_string())?;
    let rule = SyncRuleDto {
        id: format!("rule_{}", rand::random::<u32>() % 10000),
        name,
        local_path,
        remote_device_id,
        remote_path,
        direction,
        is_enabled: true,
    };
    guard.push(rule.clone());
    Ok(rule)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    start_lan_discovery_thread();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            Some(vec!["--minimized"]),
        ))
        .invoke_handler(tauri::generate_handler![
            get_or_create_device_identity,
            get_local_ip_address,
            create_pairing_session,
            approve_pairing_session,
            get_lan_devices,
            add_or_update_lan_device,
            get_active_transfers,
            start_lan_transfer,
            cancel_transfer,
            get_vault_files,
            upload_vault_file,
            delete_vault_file,
            get_relay_config,
            sync_clipboard_text,
            get_sync_rules,
            add_sync_rule
        ])
        .run(tauri::generate_context!())
        .expect("error while running syncswift desktop application");
}
