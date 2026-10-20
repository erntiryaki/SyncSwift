use tauri::{
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    Manager, PhysicalPosition,
};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            // Create System Tray Icon
            if let Some(icon) = app.default_window_icon().cloned() {
                let _tray = TrayIconBuilder::with_id("main-tray")
                    .icon(icon)
                    .tooltip("SyncSwift")
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        let app = tray.app_handle();
                        if let Some(panel) = app.get_webview_window("tray_panel") {
                            let is_visible = panel.is_visible().unwrap_or(false);
                            if is_visible {
                                let _ = panel.hide();
                            } else {
                                // Calculate position:
                                // On Windows: bottom-right (near taskbar clock)
                                // On macOS: top-right (near menubar clock)
                                if let Ok(Some(monitor)) = panel.current_monitor() {
                                    let screen_size = monitor.size();
                                    let screen_pos = monitor.position();
                                    let scale = monitor.scale_factor();

                                    let panel_w = (360.0 * scale) as i32;
                                    let panel_h = (520.0 * scale) as i32;
                                    let margin_x = (16.0 * scale) as i32;
                                    let margin_y = (64.0 * scale) as i32;

                                    #[cfg(target_os = "windows")]
                                    let (x, y) = {
                                        let pos_x = screen_pos.x + screen_size.width as i32 - panel_w - margin_x;
                                        let pos_y = screen_pos.y + screen_size.height as i32 - panel_h - margin_y;
                                        (pos_x, pos_y)
                                    };

                                    #[cfg(target_os = "macos")]
                                    let (x, y) = {
                                        let pos_x = screen_pos.x + screen_size.width as i32 - panel_w - margin_x;
                                        let pos_y = screen_pos.y + (36.0 * scale) as i32;
                                        (pos_x, pos_y)
                                    };

                                    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
                                    let (x, y) = {
                                        let pos_x = screen_pos.x + screen_size.width as i32 - panel_w - margin_x;
                                        let pos_y = screen_pos.y + screen_size.height as i32 - panel_h - margin_y;
                                        (pos_x, pos_y)
                                    };

                                    let _ = panel.set_position(PhysicalPosition::new(x, y));
                                    let _ = panel.show();
                                    let _ = panel.set_focus();
                                } else {
                                    let _ = panel.show();
                                    let _ = panel.set_focus();
                                }
                            }
                        }
                    }
                })
                .build(app)?;
            }

            // Automatically hide tray_panel when it loses focus (Native Popover behavior)
            if let Some(panel) = app.get_webview_window("tray_panel") {
                let panel_clone = panel.clone();
                panel.on_window_event(move |event| {
                    if let tauri::WindowEvent::Focused(false) = event {
                        let _ = panel_clone.hide();
                    }
                });
            }

            // Ensure main window is shown and focused on startup
            if let Some(main_win) = app.get_webview_window("main") {
                let _ = main_win.show();
                let _ = main_win.set_focus();
            }

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while building tauri application");
}
