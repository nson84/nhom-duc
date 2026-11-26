use std::fs::{self, OpenOptions};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::Duration;
use serde::{Deserialize, Serialize};

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RecoveryProfile {
    database_name: String,
    jdbc_url: String,
    image_directory: String,
    verified: bool,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ActiveRecovery {
    profile_path: String,
    database_username: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ActivationResult {
    database_name: String,
    first_run_credentials: Option<String>,
}

static CHILD: Mutex<Option<Child>> = Mutex::new(None);
static STOPPED: AtomicBool = AtomicBool::new(false);
static SWITCHING: AtomicBool = AtomicBool::new(false);

// Windows canonicalize() may produce verbatim paths (\\\\?\\C:\\...). The Windows
// JVM rejects those strings when an application passes them to Path.of(), even
// though Rust accepts them. Only simplify paths at the Java process boundary.
#[cfg(windows)]
fn java_compatible_path(path: &std::path::Path) -> PathBuf {
    let value = path.to_string_lossy();
    if let Some(unc) = value.strip_prefix(r"\\?\UNC\") {
        return PathBuf::from(format!(r"\\{}", unc));
    }
    if let Some(drive) = value.strip_prefix(r"\\?\") {
        if drive.as_bytes().get(1) == Some(&b':')
            && drive.as_bytes().get(2).is_some_and(|ch| *ch == b'\\' || *ch == b'/') {
            return PathBuf::from(drive);
        }
    }
    path.to_path_buf()
}

#[cfg(not(windows))]
fn java_compatible_path(path: &std::path::Path) -> PathBuf {
    path.to_path_buf()
}

#[cfg(test)]
mod java_path_tests {
    use super::java_compatible_path;
    use std::path::Path;
    #[test]
    fn regular_paths_are_preserved() {
        let path = Path::new(r"C:\Users\Test User\AppData\AluminumManager\jwt.key");
        assert_eq!(java_compatible_path(path), path);
    }
    #[cfg(windows)]
    #[test]
    fn verbatim_drive_paths_are_accepted_by_java() {
        let path = Path::new(r"\\?\C:\Users\Test User\AppData\AluminumManager\jwt.key");
        assert_eq!(java_compatible_path(path), Path::new(r"C:\Users\Test User\AppData\AluminumManager\jwt.key"));
    }
    #[cfg(windows)]
    #[test]
    fn verbatim_unc_paths_become_standard_unc_paths() {
        let path = Path::new(r"\\?\UNC\server\share\AluminumManager\jwt.key");
        assert_eq!(java_compatible_path(path), Path::new(r"\\server\share\AluminumManager\jwt.key"));
    }
    #[cfg(windows)]
    #[test]
    fn unicode_verbatim_paths_are_preserved_without_prefix() {
        let path = Path::new(r"\\?\C:\Users\Nguyễn Sơn\AluminumManager\jwt.key");
        assert_eq!(java_compatible_path(path), Path::new(r"C:\Users\Nguyễn Sơn\AluminumManager\jwt.key"));
    }
}

fn active_file() -> PathBuf { app_data_dir().join("active-recovery.json") }
fn previous_file() -> PathBuf { app_data_dir().join("previous-recovery.json") }

fn read_active_recovery() -> Result<Option<ActiveRecovery>, String> {
    read_selection(&active_file())
}

fn read_selection(path: &PathBuf) -> Result<Option<ActiveRecovery>, String> {
    if !path.exists() { return Ok(None); }
    serde_json::from_slice(&fs::read(path).map_err(|err| err.to_string())?)
        .map_err(|err| format!("Cấu hình phục hồi không hợp lệ: {err}"))
}

fn write_selection(path: &PathBuf, selection: &Option<ActiveRecovery>) -> Result<(), String> {
    let temporary = path.with_extension("json.tmp");
    fs::write(&temporary, serde_json::to_vec(selection).map_err(|err| err.to_string())?)
        .map_err(|err| err.to_string())?;
    fs::rename(&temporary, path).map_err(|err| err.to_string())
}

fn validate_profile(value: &str) -> Result<(PathBuf, RecoveryProfile), String> {
    let path = fs::canonicalize(value).map_err(|err| format!("Không đọc được hồ sơ phục hồi: {err}"))?;
    let root = fs::canonicalize(app_data_dir().join("restore-staging"))
        .map_err(|err| format!("Không tìm thấy thư mục dựng DB: {err}"))?;
    let folder = path.parent().ok_or("Đường dẫn hồ sơ không hợp lệ")?;
    if !folder.starts_with(&root) || folder.parent() != Some(root.as_path())
        || path.file_name().and_then(|v| v.to_str()) != Some("verified-recovery.json") {
        return Err("Hồ sơ phải nằm trong thư mục dựng DB của ứng dụng".to_string());
    }
    let profile: RecoveryProfile = serde_json::from_slice(&fs::read(&path).map_err(|err| err.to_string())?)
        .map_err(|err| format!("Hồ sơ phục hồi không hợp lệ: {err}"))?;
    let name = &profile.database_name;
    if !profile.verified || name.len() != 29 || !name.starts_with("aluminum_restore_")
        || !name[17..].bytes().all(|byte| byte.is_ascii_hexdigit())
        || folder.file_name().and_then(|v| v.to_str()) != Some(name) {
        return Err("Hồ sơ chưa được xác minh".to_string());
    }
    let tail = profile.jdbc_url.strip_prefix("jdbc:mysql://127.0.0.1:").ok_or("MySQL phục hồi phải ở máy này")?;
    let (port, database_part) = tail.split_once('/').ok_or("URL MySQL không hợp lệ")?;
    if port.parse::<u16>().ok().filter(|port| *port > 0).is_none()
        || database_part.split('?').next() != Some(name) {
        return Err("Hồ sơ trỏ tới database không khớp".to_string());
    }
    let image = fs::canonicalize(&profile.image_directory).map_err(|err| err.to_string())?;
    if image != folder.join("image") { return Err("Thư mục ảnh không khớp hồ sơ".to_string()); }
    Ok((path, profile))
}

pub fn ensure_started() -> Result<(), String> {
    let active = read_active_recovery()?;
    let port = std::env::var("ALUMINUM_API_PORT").unwrap_or_else(|_| "8080".to_string());
    let addr = format!("127.0.0.1:{port}")
        .parse::<SocketAddr>()
        .map_err(|err| err.to_string())?;
    if let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_millis(400)) {
        stream.set_read_timeout(Some(Duration::from_secs(3))).map_err(|err| err.to_string())?;
        stream.set_write_timeout(Some(Duration::from_secs(3))).map_err(|err| err.to_string())?;
        stream.write_all(format!("GET /api/v1/system/ready HTTP/1.0\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n").as_bytes())
            .map_err(|err| format!("Không kiểm tra được backend ở cổng {port}: {err}"))?;
        let mut response = String::new();
        stream.take(16384).read_to_string(&mut response).map_err(|err| err.to_string())?;
        let body = response.split_once("\r\n\r\n").map(|(_, body)| body).unwrap_or("");
        let ready: serde_json::Value = serde_json::from_str(body).unwrap_or(serde_json::Value::Null);
        if !response.starts_with("HTTP/1.1 200") && !response.starts_with("HTTP/1.0 200") {
            return Err(format!("Cổng {port} đang có dịch vụ nhưng backend chưa sẵn sàng. Kiểm tra Java, MySQL và backend.log."));
        }
        if ready["data"]["application"] != "aluminum-manager" || ready["data"]["database"] != "UP" {
            return Err(format!("Cổng {port} đang được dịch vụ khác sử dụng. Hãy kiểm tra tiến trình trước khi mở ứng dụng."));
        }
        if let Some(selection) = &active {
            let (_, profile) = validate_profile(&selection.profile_path)?;
            if ready["data"]["databaseName"].as_str() != Some(profile.database_name.as_str()) {
                return Err("Backend đang chạy không dùng database phục hồi đã chọn. Không tiếp tục để tránh dùng sai dữ liệu.".to_string());
            }
        }
        log("Backend đã chạy, ứng dụng desktop không khởi động thêm tiến trình.");
        return Ok(());
    }

    start_backend(active.as_ref())
}

fn start_backend(active: Option<&ActiveRecovery>) -> Result<(), String> {
    let jar = find_jar().ok_or_else(|| {
        let message = "Không thấy file JAR backend. Khi phát triển, hãy chạy scripts/dev-backend.ps1.";
        log(message);
        message.to_string()
    })?;

    let data_dir = app_data_dir();
    let profile = active.map(|selection| validate_profile(&selection.profile_path)).transpose()?;
    let image_dir = profile.as_ref().map(|(_, item)| PathBuf::from(&item.image_directory))
        .unwrap_or_else(|| {
            fs::read_to_string(data_dir.join("dev-original-image.json")).ok()
                .and_then(|value| serde_json::from_str::<PathBuf>(&value).ok())
                .filter(|path| path.is_absolute())
                .unwrap_or_else(|| data_dir.join("image"))
        });
    fs::create_dir_all(&image_dir).map_err(|err| format!("Không tạo được thư mục ảnh: {err}"))?;
    let mut command = Command::new("java");
    command
        .arg("-jar")
        .arg(&jar)
        .current_dir(&data_dir)
        .env("ALUMINUM_RESTORE_STAGING_DIR", java_compatible_path(&data_dir.join("restore-staging")))
        .env("ALUMINUM_IMAGE_DIR", java_compatible_path(&image_dir))
        .stdin(Stdio::piped());

    if let (Some(selection), Some((path, profile))) = (active, profile.as_ref()) {
        let runtime = path.parent().ok_or("Hồ sơ không có thư mục")?.join("runtime");
        fs::create_dir_all(&runtime).map_err(|err| err.to_string())?;
        let runtime_app = runtime.join("AluminumManager");
        fs::create_dir_all(&runtime_app).map_err(|err| err.to_string())?;
        let source_r2 = app_data_dir().join("r2-config.properties");
        let target_r2 = runtime_app.join("r2-config.properties");
        if source_r2.is_file() && !target_r2.exists() {
            fs::copy(source_r2, target_r2).map_err(|err| format!("Không chuyển được cấu hình R2: {err}"))?;
        }
        let password = crate::credentials::load_recovery_password(&profile.database_name)?
            .ok_or("Không tìm thấy mật khẩu MySQL của bản phục hồi trong Windows Credential Manager")?;
        let recovery_key = crate::credentials::load_recovery_key(&profile.database_name)?
            .ok_or("Không tìm thấy khóa phục hồi của bản sao R2 trong Windows Credential Manager")?;
        command.env("SPRING_DATASOURCE_URL", &profile.jdbc_url)
            .env("SPRING_DATASOURCE_USERNAME", &selection.database_username)
            .env("SPRING_DATASOURCE_PASSWORD", password)
            .env("ALUMINUM_INITIAL_RECOVERY_KEY", recovery_key)
            .env("APPDATA", java_compatible_path(&runtime))
            .env("ALUMINUM_JWT_SECRET_FILE", java_compatible_path(&runtime.join("jwt.key")))
            .env("ALUMINUM_IMAGE_DIR", &image_dir)
            .env("ALUMINUM_FIRST_RUN_CREDENTIAL_DIR", java_compatible_path(&runtime.join("credentials")))
            .env("ALUMINUM_BACKUP_SCHEDULED_ENABLED", "true");
    }

    let log_path = app_data_dir().join("backend.log");
    if let Ok(file) = OpenOptions::new().create(true).append(true).open(&log_path) {
        if let Ok(error_log) = file.try_clone() {
            command.stdout(Stdio::from(file)).stderr(Stdio::from(error_log));
        }
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        command.creation_flags(CREATE_NO_WINDOW);
    }

    let mut child = command.spawn().map_err(|err| {
        let message = format!("Không khởi động được Java. Cài Java 17, MySQL 8 rồi mở lại ứng dụng. Chi tiết: {err}");
        log(&message);
        message
    })?;
    log(&format!("Đã khởi động backend, pid {}", child.id()));
    if STOPPED.load(Ordering::SeqCst) {
        let _ = child.kill();
        let _ = child.wait();
        return Ok(());
    }
    *CHILD.lock().map_err(|err| err.to_string())? = Some(child);
    Ok(())
}

pub fn stop() {
    while SWITCHING.load(Ordering::SeqCst) { std::thread::sleep(Duration::from_millis(100)); }
    if STOPPED.swap(true, Ordering::SeqCst) { return; }
    let Ok(mut guard) = CHILD.lock() else {
        return;
    };
    if guard.is_none() {
        if let Err(err) = desktop_backup("shutdown") {
            log(&format!("Không sao lưu được khi đóng desktop dùng backend đã chạy sẵn: {err}"));
        }
        return;
    }
    if let Some(child) = guard.as_mut() {
        if let Some(mut input) = child.stdin.take() {
            let _ = input.write_all(b"BACKUP_AND_EXIT\n");
            let _ = input.flush();
        }
        let deadline = std::time::Instant::now() + Duration::from_secs(90);
        while std::time::Instant::now() < deadline {
            if let Ok(Some(_)) = child.try_wait() {
                log("Backend đã sao lưu và dừng khi đóng ứng dụng.");
                *guard = None;
                return;
            }
            std::thread::sleep(Duration::from_millis(200));
        }
        log("Backend chưa dừng sau 90 giây; xem backend.log để kiểm tra bản sao lưu cuối.");
        let _ = child.kill();
        let _ = child.wait();
    }
    *guard = None;
}

fn stop_owned(child: &mut Child) -> Result<(), String> {
    let mut input = child.stdin.take().ok_or("Không điều khiển được backend hiện tại")?;
    input.write_all(b"BACKUP_AND_EXIT\n").map_err(|err| err.to_string())?;
    input.flush().map_err(|err| err.to_string())?;
    let deadline = std::time::Instant::now() + Duration::from_secs(90);
    while std::time::Instant::now() < deadline {
        if child.try_wait().map_err(|err| err.to_string())?.is_some() { return Ok(()); }
        std::thread::sleep(Duration::from_millis(200));
    }
    Err("Backend chưa đóng sau 90 giây; dữ liệu cũ vẫn được giữ".to_string())
}

fn ready_database() -> Result<Option<String>, String> {
    let port = std::env::var("ALUMINUM_API_PORT").unwrap_or_else(|_| "8080".to_string());
    let addr = format!("127.0.0.1:{port}").parse::<SocketAddr>().map_err(|err| err.to_string())?;
    let mut stream = match TcpStream::connect_timeout(&addr, Duration::from_millis(500)) {
        Ok(stream) => stream, Err(_) => return Ok(None),
    };
    stream.set_read_timeout(Some(Duration::from_secs(3))).map_err(|err| err.to_string())?;
    stream.write_all(format!("GET /api/v1/system/ready HTTP/1.0\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n").as_bytes())
        .map_err(|err| err.to_string())?;
    let mut response = String::new();
    stream.take(16384).read_to_string(&mut response).map_err(|err| err.to_string())?;
    if !response.starts_with("HTTP/1.1 200") && !response.starts_with("HTTP/1.0 200") { return Ok(None); }
    let body = response.split_once("\r\n\r\n").map(|(_, body)| body).unwrap_or("");
    let ready: serde_json::Value = serde_json::from_str(body).unwrap_or(serde_json::Value::Null);
    if ready["data"]["application"] != "aluminum-manager" || ready["data"]["database"] != "UP" { return Ok(None); }
    Ok(ready["data"]["databaseName"].as_str().map(str::to_owned))
}

fn wait_for_database(expected: &str) -> Result<(), String> {
    let deadline = std::time::Instant::now() + Duration::from_secs(90);
    while std::time::Instant::now() < deadline {
        if let Some(actual) = ready_database()? {
            if actual == expected { return Ok(()); }
            return Err(format!("Backend dùng database {actual}, không phải {expected}"));
        }
        std::thread::sleep(Duration::from_millis(500));
    }
    Err("Backend phục hồi không sẵn sàng sau 90 giây. Kiểm tra backend.log.".to_string())
}

fn credentials_after_start(path: &PathBuf, since: std::time::SystemTime) -> Option<String> {
    let directory = path.parent()?.join("runtime").join("credentials");
    let entries = fs::read_dir(directory).ok()?;
    entries.flatten().filter_map(|item| {
        let name = item.file_name().to_string_lossy().to_string();
        if name.starts_with("first-run-credentials-") && name.ends_with(".txt")
            && item.metadata().ok()?.modified().ok()? >= since {
            fs::read_to_string(item.path()).ok()
        } else { None }
    }).next()
}

fn switch_to(target: Option<ActiveRecovery>, save_previous: bool) -> Result<ActivationResult, String> {
    if SWITCHING.swap(true, Ordering::SeqCst) { return Err("Đang chuyển database, vui lòng đợi".to_string()); }
    let result = switch_to_inner(target, save_previous);
    SWITCHING.store(false, Ordering::SeqCst);
    result
}

fn switch_to_inner(target: Option<ActiveRecovery>, save_previous: bool) -> Result<ActivationResult, String> {
    if STOPPED.load(Ordering::SeqCst) { return Err("Ứng dụng đang đóng".to_string()); }
    let current = read_active_recovery()?;
    let target_profile = target.as_ref().map(|selection| validate_profile(&selection.profile_path)).transpose()?;
    let default_name = std::env::var("ALUMINUM_DB_NAME").unwrap_or_else(|_| "aluminum_app".to_string());
    let target_name = target_profile.as_ref().map(|(_, profile)| profile.database_name.as_str()).unwrap_or(&default_name);
    if find_jar().is_none() {
        return Err("Không tìm thấy JAR backend để mở database sau khi bàn giao.".to_string());
    }
    let mut guard = CHILD.lock().map_err(|err| err.to_string())?;
    let started = std::time::SystemTime::now();
    let result = (|| {
        if let Some(child) = guard.as_mut() {
            stop_owned(child)?;
        } else {
            if current.is_some() || default_name != "aluminum_app" || ready_database()?.as_deref() != Some("aluminum_app") {
                return Err("Backend bên ngoài không khớp cấu hình database dev mặc định.".to_string());
            }
            handoff_dev_backend()?;
        }
        *guard = None;
        drop(guard);
        let launched = start_backend(target.as_ref()).and_then(|_| wait_for_database(target_name));
        if let Err(error) = launched {
            if let Some(mut child) = CHILD.lock().map_err(|err| err.to_string())?.take() { let _ = child.kill(); let _ = child.wait(); }
            let old_name = current.as_ref().map(|selection| validate_profile(&selection.profile_path))
                .transpose()?.map(|(_, profile)| profile.database_name).unwrap_or_else(|| default_name.clone());
            let rollback = start_backend(current.as_ref()).and_then(|_| wait_for_database(&old_name));
            return Err(format!("Không chuyển được dữ liệu: {error}. {}", if rollback.is_ok() { "Đã mở lại database trước." } else { "Không mở lại được backend cũ; kiểm tra backend.log." }));
        }
        let persistence = (|| {
            if save_previous { write_selection(&previous_file(), &current)?; }
            write_selection(&active_file(), &target)
        })();
        if let Err(error) = persistence {
            if let Some(mut child) = CHILD.lock().map_err(|err| err.to_string())?.take() { let _ = child.kill(); let _ = child.wait(); }
            let old_name = current.as_ref().map(|selection| validate_profile(&selection.profile_path))
                .transpose()?.map(|(_, profile)| profile.database_name).unwrap_or_else(|| default_name.clone());
            let rollback = start_backend(current.as_ref()).and_then(|_| wait_for_database(&old_name));
            if rollback.is_ok() { let _ = write_selection(&active_file(), &current); }
            return Err(format!("Không lưu được trạng thái chuyển DB: {error}. {}", if rollback.is_ok() { "Đã mở lại database trước." } else { "Không mở lại được backend cũ; kiểm tra backend.log." }));
        }
        Ok(ActivationResult {
            database_name: target_name.to_string(),
            first_run_credentials: target_profile.as_ref().and_then(|(path, _)| credentials_after_start(path, started)),
        })
    })();
    result
}

#[tauri::command]
pub async fn activate_recovery(profile_path: String, database_username: String, database_password: String, recovery_key: String) -> Result<ActivationResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let (path, profile) = validate_profile(&profile_path)?;
        if database_username.trim().is_empty() || database_password.is_empty() { return Err("Nhập tài khoản và mật khẩu MySQL".to_string()); }
        if recovery_key.trim().is_empty() { return Err("Thiếu khóa phục hồi đã dùng để giải mã bản sao".to_string()); }
        crate::credentials::save_recovery_password(&profile.database_name, &database_password)?;
        crate::credentials::save_recovery_key(&profile.database_name, &recovery_key)?;
        switch_to(Some(ActiveRecovery { profile_path: path.to_string_lossy().to_string(), database_username }), true)
    }).await.map_err(|err| err.to_string())?
}

#[tauri::command]
pub async fn rollback_recovery() -> Result<ActivationResult, String> {
    tauri::async_runtime::spawn_blocking(|| {
        if !previous_file().exists() { return Err("Chưa có database trước để quay lại".to_string()); }
        switch_to(read_selection(&previous_file())?, true)
    }).await.map_err(|err| err.to_string())?
}

#[tauri::command]
pub fn recovery_status() -> Result<Option<String>, String> {
    let selection = read_active_recovery()?;
    Ok(selection.map(|selection| validate_profile(&selection.profile_path))
        .transpose()?.map(|(_, profile)| profile.database_name))
}

pub fn backup_after_start() {
    std::thread::sleep(Duration::from_secs(30));
    if STOPPED.load(Ordering::SeqCst) || SWITCHING.load(Ordering::SeqCst) { return; }
    for _ in 0..6 {
        if STOPPED.load(Ordering::SeqCst) || SWITCHING.load(Ordering::SeqCst) { return; }
        match desktop_backup("startup") {
            Ok(()) => { log("Đã sao lưu sau khi mở desktop."); return; }
            Err(err) => {
                if STOPPED.load(Ordering::SeqCst) { return; }
                log(&format!("Chưa sao lưu được sau khi mở desktop: {err}"));
                std::thread::sleep(Duration::from_secs(5));
            }
        }
    }
}

fn desktop_backup(reason: &str) -> Result<(), String> {
    desktop_control(reason).map(|_| ())
}

fn handoff_dev_backend() -> Result<(), String> {
    let info = desktop_control("dev-handoff-check")
        .map_err(|error| format!("Backend đang chạy chưa hỗ trợ bàn giao từ dev-backend.ps1: {error}"))?;
    let profile: serde_json::Value = serde_json::from_str(&info).map_err(|err| err.to_string())?;
    let image = profile["imageDirectory"].as_str().map(PathBuf::from)
        .filter(|path| path.is_absolute() && path.is_dir())
        .ok_or("Không tìm thấy thư mục ảnh của backend dev")?;
    let saved_image = serde_json::to_string(&image).map_err(|err| err.to_string())?;
    fs::write(app_data_dir().join("dev-original-image.json"), saved_image).map_err(|err| err.to_string())?;
    desktop_control("dev-handoff")?;
    let port = std::env::var("ALUMINUM_API_PORT").unwrap_or_else(|_| "8080".to_string());
    let addr = format!("127.0.0.1:{port}").parse::<SocketAddr>().map_err(|err| err.to_string())?;
    let deadline = std::time::Instant::now() + Duration::from_secs(30);
    while std::time::Instant::now() < deadline {
        if TcpStream::connect_timeout(&addr, Duration::from_millis(400)).is_err() { return Ok(()); }
        std::thread::sleep(Duration::from_millis(200));
    }
    Err("Backend dev chưa nhường cổng sau 30 giây.".to_string())
}

fn desktop_control(reason: &str) -> Result<String, String> {
    let token_dir = read_active_recovery()?.map(|selection| {
        let (path, _) = validate_profile(&selection.profile_path)?;
        Ok::<PathBuf, String>(path.parent().ok_or("Hồ sơ không có thư mục")?.join("runtime").join("AluminumManager"))
    }).transpose()?.unwrap_or_else(app_data_dir);
    let token = fs::read_to_string(token_dir.join("desktop-backup-token"))
        .map_err(|err| format!("Không đọc được mã điều khiển backup: {err}"))?;
    let port = std::env::var("ALUMINUM_API_PORT").unwrap_or_else(|_| "8080".to_string());
    let addr = format!("127.0.0.1:{port}").parse::<SocketAddr>().map_err(|err| err.to_string())?;
    let mut stream = TcpStream::connect_timeout(&addr, Duration::from_secs(3))
        .map_err(|err| format!("Không kết nối được backend: {err}"))?;
    stream.set_read_timeout(Some(Duration::from_secs(90))).map_err(|err| err.to_string())?;
    stream.set_write_timeout(Some(Duration::from_secs(3))).map_err(|err| err.to_string())?;
    let request = format!("POST /api/v1/system/desktop-backup/{reason} HTTP/1.0\r\nHost: 127.0.0.1:{port}\r\nX-Desktop-Backup-Token: {}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n", token.trim());
    stream.write_all(request.as_bytes()).map_err(|err| err.to_string())?;
    let mut response = String::new();
    stream.read_to_string(&mut response).map_err(|err| err.to_string())?;
    if !response.starts_with("HTTP/1.1 200") && !response.starts_with("HTTP/1.0 200") {
        return Err(format!("Backend trả về {}", response.lines().next().unwrap_or("không có phản hồi")));
    }
    Ok(response.split_once("\r\n\r\n").map(|(_, body)| body).unwrap_or("").to_string())
}

fn find_jar() -> Option<PathBuf> {
    let mut candidates = Vec::new();
    if let Ok(path) = std::env::var("ALUMINUM_BACKEND_JAR") {
        candidates.push(PathBuf::from(path));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            candidates.push(dir.join("aluminum-manager.jar"));
            candidates.push(dir.join("resources").join("aluminum-manager.jar"));
        }
    }
    candidates.push(PathBuf::from("../backend/target/aluminum-manager-0.0.1-SNAPSHOT.jar"));
    candidates.push(PathBuf::from("backend/target/aluminum-manager-0.0.1-SNAPSHOT.jar"));
    candidates.into_iter().find(|path| path.is_file())
}

fn app_data_dir() -> PathBuf {
    let dir = std::env::var("APPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("."))
        .join("AluminumManager");
    let _ = fs::create_dir_all(&dir);
    dir
}

fn log(message: &str) {
    let path = app_data_dir().join("backend.log");
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
        let _ = writeln!(file, "{message}");
    }
}
