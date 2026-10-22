use std::fs::{self, File};
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{TcpListener, UdpSocket};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use tauri::Emitter;

static SERVER_RUNNING: AtomicBool = AtomicBool::new(false);

#[tauri::command]
fn get_local_ip() -> String {
    if let Ok(socket) = UdpSocket::bind("0.0.0.0:0") {
        if socket.connect("8.8.8.8:80").is_ok() {
            if let Ok(addr) = socket.local_addr() {
                return addr.ip().to_string();
            }
        }
    }
    "127.0.0.1".to_string()
}

#[tauri::command]
fn get_computer_name() -> String {
    std::env::var("COMPUTERNAME")
        .or_else(|_| std::env::var("HOSTNAME"))
        .unwrap_or_else(|_| "Windows PC".to_string())
}

#[tauri::command]
fn get_downloads_dir() -> String {
    if let Some(user_dirs) = dirs_next_fallback() {
        return user_dirs.to_string_lossy().to_string();
    }
    std::env::var("USERPROFILE")
        .map(|p| format!("{}\\Downloads\\SyncSwift", p))
        .unwrap_or_else(|_| "Downloads".to_string())
}

#[tauri::command]
fn open_downloads_folder() {
    let dir = get_downloads_dir();
    let _ = fs::create_dir_all(&dir);
    #[cfg(target_os = "windows")]
    {
        let _ = std::process::Command::new("explorer").arg(&dir).spawn();
    }
}

fn dirs_next_fallback() -> Option<PathBuf> {
    if let Ok(profile) = std::env::var("USERPROFILE") {
        let p = PathBuf::from(profile).join("Downloads").join("SyncSwift");
        return Some(p);
    }
    None
}

fn start_local_server(app_handle: tauri::AppHandle) {
    if SERVER_RUNNING.swap(true, Ordering::SeqCst) {
        return;
    }

    thread::spawn(move || {
        let listener = match TcpListener::bind("0.0.0.0:52424") {
            Ok(l) => l,
            Err(_) => match TcpListener::bind("0.0.0.0:52425") {
                Ok(l) => l,
                Err(e) => {
                    eprintln!("Failed to bind local REST server: {}", e);
                    SERVER_RUNNING.store(false, Ordering::SeqCst);
                    return;
                }
            },
        };

        println!("Desktop LocalSend REST Server listening on port 52424/52425");

        for stream in listener.incoming() {
            if let Ok(mut stream) = stream {
                let handle = app_handle.clone();
                thread::spawn(move || {
                    let mut reader = BufReader::new(stream.try_clone().unwrap());
                    let mut request_line = String::new();
                    if reader.read_line(&mut request_line).is_err() || request_line.is_empty() {
                        return;
                    }

                    let parts: Vec<&str> = request_line.split_whitespace().collect();
                    if parts.len() < 2 {
                        return;
                    }
                    let method = parts[0];
                    let full_path = parts[1];
                    let path = full_path.split('?').next().unwrap_or("");

                    let mut headers = std::collections::HashMap::new();
                    loop {
                        let mut line = String::new();
                        if reader.read_line(&mut line).is_err() || line.trim().is_empty() {
                            break;
                        }
                        if let Some((k, v)) = line.split_once(':') {
                            headers.insert(k.trim().to_lowercase(), v.trim().to_string());
                        }
                    }

                    let content_length: usize = headers
                        .get("content-length")
                        .and_then(|v| v.parse().ok())
                        .unwrap_or(0);

                    match (method, path) {
                        // 0. OPTIONS - CORS PREFLIGHT
                        ("OPTIONS", _) => {
                            let response = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                            let _ = stream.write_all(response.as_bytes());
                            let _ = stream.flush();
                        }

                        // 1. GET /api/ping
                        ("GET", p) if p.ends_with("/api/ping") => {
                            let name = get_computer_name();
                            let resp = format!(
                                "{{\"status\":\"ok\",\"name\":\"{}\",\"os\":\"Windows\",\"port\":52424}}",
                                name
                            );
                            send_json_response(&mut stream, 200, &resp);
                        }

                        // 2. POST /api/pair - ZERO PROMPT AUTO-ACCEPT
                        ("POST", p) if p.ends_with("/api/pair") => {
                            let mut body = vec![0u8; content_length];
                            let _ = reader.read_exact(&mut body);
                            let body_str = String::from_utf8_lossy(&body).to_string();

                            let _ = handle.emit("device_paired", body_str.clone());
                            let name = get_computer_name();
                            let resp = format!(
                                "{{\"status\":\"accepted\",\"name\":\"{}\"}}",
                                name
                            );
                            send_json_response(&mut stream, 200, &resp);
                        }

                        // 3. POST /api/unpair
                        ("POST", p) if p.ends_with("/api/unpair") => {
                            let mut body = vec![0u8; content_length];
                            let _ = reader.read_exact(&mut body);
                            let body_str = String::from_utf8_lossy(&body).to_string();

                            let _ = handle.emit("device_unpaired", body_str);
                            send_json_response(&mut stream, 200, "{\"status\":\"unpaired\"}");
                        }

                        // 4. POST /api/clipboard - ZERO PROMPT AUTO-ACCEPT
                        ("POST", p) if p.ends_with("/api/clipboard") => {
                            let mut body = vec![0u8; content_length];
                            let _ = reader.read_exact(&mut body);
                            let body_str = String::from_utf8_lossy(&body).to_string();

                            let text = if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body_str) {
                                json.get("text").and_then(|t| t.as_str()).unwrap_or(&body_str).to_string()
                            } else {
                                body_str
                            };

                            let sender = headers.get("x-sender-name").cloned().unwrap_or_else(|| "Mobile Device".to_string());
                            let payload = serde_json::json!({
                                "text": text,
                                "sender": sender
                            });
                            let _ = handle.emit("clipboard_received", payload.to_string());
                            send_json_response(&mut stream, 200, "{\"status\":\"ok\"}");
                        }

                        // 5. POST /api/file - ZERO PROMPT 100+ MB/s DIRECT STREAM
                        ("POST", p) if p.ends_with("/api/file") => {
                            let raw_name = headers.get("x-file-name").cloned().unwrap_or_else(|| "received_file".to_string());
                            let file_name = urlencoding_decode(&raw_name);
                            let file_size: u64 = headers.get("x-file-size").and_then(|s| s.parse().ok()).unwrap_or(content_length as u64);
                            let sender_name = headers.get("x-sender-name").map(|s| urlencoding_decode(s)).unwrap_or_else(|| "Mobile Device".to_string());

                            let downloads_dir = get_downloads_dir();
                            let _ = fs::create_dir_all(&downloads_dir);
                            let dest_path = PathBuf::from(&downloads_dir).join(&file_name);

                            let mut file_ok = false;
                            if let Ok(mut out_file) = File::create(&dest_path) {
                                let mut buffer = [0u8; 256 * 1024]; // 256 KB buffer for max LAN speed
                                let mut total_read = 0u64;

                                while total_read < file_size {
                                    let rem = file_size - total_read;
                                    let to_read = (buffer.len() as u64).min(rem) as usize;
                                    match reader.read(&mut buffer[..to_read]) {
                                        Ok(0) => break,
                                        Ok(n) => {
                                            if out_file.write_all(&buffer[..n]).is_err() {
                                                break;
                                            }
                                            total_read += n as u64;
                                        }
                                        Err(_) => break,
                                    }
                                }
                                let _ = out_file.flush();
                                file_ok = total_read == file_size || file_size == 0;
                            }

                            if file_ok {
                                let payload = serde_json::json!({
                                    "fileName": file_name,
                                    "fileSize": file_size,
                                    "path": dest_path.to_string_lossy().to_string(),
                                    "sender": sender_name
                                });
                                let _ = handle.emit("file_received", payload.to_string());
                                send_json_response(&mut stream, 200, "{\"status\":\"ok\"}");
                            } else {
                                send_json_response(&mut stream, 500, "{\"status\":\"error\"}");
                            }
                        }

                        _ => {
                            send_json_response(&mut stream, 404, "{\"status\":\"not_found\"}");
                        }
                    }
                });
            }
        }
    });
}

fn urlencoding_decode(s: &str) -> String {
    let replaced = s.replace('+', " ");
    let mut bytes = Vec::new();
    let mut chars = replaced.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '%' {
            let h1 = chars.next().unwrap_or('0');
            let h2 = chars.next().unwrap_or('0');
            let hex_str = format!("{}{}", h1, h2);
            if let Ok(b) = u8::from_str_radix(&hex_str, 16) {
                bytes.push(b);
            }
        } else {
            bytes.extend_from_slice(c.to_string().as_bytes());
        }
    }
    String::from_utf8(bytes).unwrap_or_else(|_| s.to_string())
}

fn send_json_response(stream: &mut std::net::TcpStream, code: u16, json: &str) {
    let status_text = if code == 200 {
        "OK"
    } else if code == 404 {
        "Not Found"
    } else {
        "Internal Server Error"
    };
    let response = format!(
        "HTTP/1.1 {} {}\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        code,
        status_text,
        json.len(),
        json
    );
    let _ = stream.write_all(response.as_bytes());
    let _ = stream.flush();
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            start_local_server(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_local_ip,
            get_computer_name,
            get_downloads_dir,
            open_downloads_folder
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
