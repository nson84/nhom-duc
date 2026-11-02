mod backend;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|_app| {
            std::thread::spawn(|| {
                if let Err(err) = backend::ensure_started() {
                    eprintln!("backend: {err}");
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
