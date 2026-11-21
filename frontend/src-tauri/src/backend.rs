use std::fs::{self, OpenOptions};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::Duration;

static CHILD: Mutex<Option<Child>> = Mutex::new(None);
static STOPPED: AtomicBool = AtomicBool::new(false);

pub fn ensure_started() -> Result<(), String> {
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
        log("Backend đã chạy, ứng dụng desktop không khởi động thêm tiến trình.");
        return Ok(());
    }

    let jar = find_jar().ok_or_else(|| {
        let message = "Không thấy file JAR backend. Khi phát triển, hãy chạy scripts/dev-backend.ps1.";
        log(message);
        message.to_string()
    })?;

    let data_dir = app_data_dir();
    let image_dir = data_dir.join("image");
    fs::create_dir_all(&image_dir).map_err(|err| format!("Không tạo được thư mục ảnh: {err}"))?;
    let mut command = Command::new("java");
    command
        .arg("-jar")
        .arg(&jar)
        .current_dir(&data_dir)
        .env("ALUMINUM_IMAGE_DIR", &image_dir)
        .stdin(Stdio::piped());

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

pub fn backup_after_start() {
    std::thread::sleep(Duration::from_secs(30));
    if STOPPED.load(Ordering::SeqCst) { return; }
    for _ in 0..6 {
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
    let token = fs::read_to_string(app_data_dir().join("desktop-backup-token"))
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
    Ok(())
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
