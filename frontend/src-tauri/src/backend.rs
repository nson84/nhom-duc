use std::fs::{self, OpenOptions};
use std::io::Write;
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
    if TcpStream::connect_timeout(&addr, Duration::from_millis(400)).is_ok() {
        log("Backend đã chạy, ứng dụng desktop không khởi động thêm tiến trình.");
        return Ok(());
    }

    let jar = find_jar().ok_or_else(|| {
        let message = "Không thấy file JAR backend. Khi phát triển, hãy chạy scripts/dev-backend.ps1.";
        log(message);
        message.to_string()
    })?;

    let mut command = Command::new("java");
    command
        .arg("-jar")
        .arg(&jar)
        .current_dir(jar.parent().unwrap_or(std::path::Path::new(".")))
        .stdin(Stdio::null());

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

    let child = command.spawn().map_err(|err| {
        let message = format!("Không khởi động được Java: {err}");
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
    STOPPED.store(true, Ordering::SeqCst);
    let Ok(mut guard) = CHILD.lock() else {
        return;
    };
    if let Some(child) = guard.as_mut() {
        let _ = child.kill();
        let _ = child.wait();
        log("Đã dừng backend do ứng dụng đóng.");
    }
    *guard = None;
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
