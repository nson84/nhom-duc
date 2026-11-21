mod backend;
mod credentials;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![credentials::saved_password, credentials::save_password])
        .setup(|_app| {
            std::thread::spawn(|| {
                if let Err(err) = backend::ensure_started() {
                    eprintln!("backend: {err}");
                } else {
                    backend::backup_after_start();
                }
            });
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("Không mở được ứng dụng desktop")
        .run(|_app, event| {
            if matches!(event, tauri::RunEvent::ExitRequested { .. } | tauri::RunEvent::Exit) {
                backend::stop();
            }
        });
}
