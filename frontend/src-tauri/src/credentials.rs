#[cfg(windows)]
mod windows {
    use std::ffi::c_void;
    use std::ptr;
    use windows_sys::Win32::Security::Credentials::{
        CredFree, CredReadW, CredWriteW, CREDENTIALW, CRED_PERSIST_LOCAL_MACHINE, CRED_TYPE_GENERIC,
    };

    fn target(account: &str) -> Result<Vec<u16>, String> {
        if !matches!(account, "admin" | "phanphoi" | "xuong") {
            return Err("Tài khoản không hợp lệ".to_string());
        }
        Ok(format!("AluminumManager/login/{account}\0").encode_utf16().collect())
    }

    pub fn load(account: &str) -> Result<Option<String>, String> {
        let target = target(account)?;
        let mut credential: *mut CREDENTIALW = ptr::null_mut();
        let found = unsafe { CredReadW(target.as_ptr(), CRED_TYPE_GENERIC, 0, &mut credential) };
        if found == 0 {
            let error = std::io::Error::last_os_error();
            if error.raw_os_error() == Some(1168) { return Ok(None); }
            return Err(format!("Không đọc được mật khẩu từ Windows: {error}"));
        }
        let result = unsafe {
            let cred = &*credential;
            if cred.CredentialBlobSize == 0 || cred.CredentialBlob.is_null() {
                Ok(String::new())
            } else {
                let bytes = std::slice::from_raw_parts(cred.CredentialBlob, cred.CredentialBlobSize as usize);
                String::from_utf8(bytes.to_vec()).map_err(|err| err.to_string())
            }
        };
        unsafe { CredFree(credential as *const c_void); }
        result.map(Some)
    }

    pub fn save(account: &str, password: &str) -> Result<(), String> {
        let mut target = target(account)?;
        let mut value = password.as_bytes().to_vec();
        if value.is_empty() || value.len() > 2560 { return Err("Mật khẩu không hợp lệ".to_string()); }
        let credential = CREDENTIALW {
            Flags: 0,
            Type: CRED_TYPE_GENERIC,
            TargetName: target.as_mut_ptr(),
            Comment: ptr::null_mut(),
            LastWritten: unsafe { std::mem::zeroed() },
            CredentialBlobSize: value.len() as u32,
            CredentialBlob: value.as_mut_ptr(),
            Persist: CRED_PERSIST_LOCAL_MACHINE,
            AttributeCount: 0,
            Attributes: ptr::null_mut(),
            TargetAlias: ptr::null_mut(),
            UserName: ptr::null_mut(),
        };
        if unsafe { CredWriteW(&credential, 0) } == 0 {
            return Err(format!("Không lưu được mật khẩu vào Windows: {}", std::io::Error::last_os_error()));
        }
        Ok(())
    }
}

#[tauri::command]
pub fn saved_password(account: String) -> Result<Option<String>, String> {
    #[cfg(windows)] { windows::load(&account) }
    #[cfg(not(windows))] { let _ = account; Ok(None) }
}

#[tauri::command]
pub fn save_password(account: String, password: String) -> Result<(), String> {
    #[cfg(windows)] { windows::save(&account, &password) }
    #[cfg(not(windows))] { let _ = (account, password); Ok(()) }
}
