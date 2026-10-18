//! Password login uses Steam's authentication service; secrets never go to disk or events.
use super::*;
use rsa::{BigUint, Pkcs1v15Encrypt, RsaPublicKey, rand_core::OsRng};
use zeroize::Zeroizing;

static COMMIT: Mutex<()> = Mutex::new(());
static GENERATION: AtomicU64 = AtomicU64::new(0);
static PENDING: Mutex<Option<Pending>> = Mutex::new(None);
#[derive(Clone)]
struct Pending { generation: u64, client_id: u64, steamid: u64, codes: Vec<u32> }
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Challenge { code_types: Vec<u32>, message: String }

pub fn current(generation: u64) -> bool { GENERATION.load(Ordering::SeqCst) == generation }
pub fn cancel() -> u64 {
    let _commit = COMMIT.lock().unwrap_or_else(|p|p.into_inner());
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    if let Ok(mut pending) = PENDING.lock() { *pending = None; }
    generation
}
pub fn commit<T>(generation: u64, action: impl FnOnce() -> T) -> Option<T> {
    let _commit = COMMIT.lock().unwrap_or_else(|p|p.into_inner());
    if current(generation) { Some(action()) } else { None }
}
#[tauri::command]
pub fn steam_auth_cancel() { cancel(); }

// Minimal typed wire reader. Reject truncated responses instead of using partial auth data.
fn fields(data: &[u8]) -> Result<Vec<(u32, u64, Vec<u8>)>, String> {
    let mut out = Vec::new(); let mut i = 0;
    while i < data.len() {
        let key = read_proto_varint(data, &mut i).ok_or("Invalid Steam response")?;
        let (number, wire) = ((key >> 3) as u32, key & 7);
        if number == 0 { return Err("Invalid Steam response".into()); }
        match wire {
            0 => out.push((number, read_proto_varint(data, &mut i).ok_or("Invalid Steam response")?, vec![])),
            2 => out.push((number, 0, read_proto_len(data, &mut i).ok_or("Invalid Steam response")?)),
            5 if data.len().saturating_sub(i) >= 4 => { out.push((number, u32::from_le_bytes(data[i..i+4].try_into().unwrap()) as u64, vec![])); i += 4; },
            _ => { skip_proto_field(data, &mut i, wire as u8).ok_or("Invalid Steam response")?; }
        }
    }
    Ok(out)
}
fn number(data: &[(u32,u64,Vec<u8>)], key: u32) -> u64 { data.iter().find(|x|x.0==key).map(|x|x.1).unwrap_or(0) }
fn bytes(data: &[(u32,u64,Vec<u8>)], key: u32) -> Vec<u8> { data.iter().find(|x|x.0==key).map(|x|x.2.clone()).unwrap_or_default() }
fn string(data: &[(u32,u64,Vec<u8>)], key: u32) -> String { String::from_utf8_lossy(&bytes(data,key)).into_owned() }
fn friendly(error: String) -> String {
    match error.as_str() {
        "Steam auth EResult 5" => "Incorrect Steam account name or password.",
        "Steam auth EResult 65" | "Steam auth EResult 88" => "That Steam Guard code was not accepted. Try a new code.",
        "Steam auth EResult 84" => "Steam is limiting sign-in attempts. Please wait before trying again.",
        "Steam auth EResult 63" => "Steam requires a browser verification step. Try again later or use QR sign-in.",
        _ => "Steam sign-in could not complete. Check your connection and try again.",
    }.into()
}
fn encrypt_password(key: &[(u32,u64,Vec<u8>)], password: &[u8]) -> Result<String,String> {
    let modulus=BigUint::parse_bytes(string(key,1).as_bytes(),16).ok_or("Steam returned an invalid password key")?;
    let exponent=BigUint::parse_bytes(string(key,2).as_bytes(),16).ok_or("Steam returned an invalid password key")?;
    let public=RsaPublicKey::new(modulus,exponent).map_err(|_|"Steam returned an invalid password key")?;
    public.encrypt(&mut OsRng,Pkcs1v15Encrypt,password).map(|b|general_purpose::STANDARD.encode(b)).map_err(|_|"Could not encrypt the Steam password".into())
}
fn credentials_request(account: &str, encrypted: &str, timestamp: u64) -> Vec<u8> {
    let mut buf=vec![];
    proto_field_string(&mut buf,1,"CreteOS"); proto_field_string(&mut buf,2,account);
    proto_field_string(&mut buf,3,encrypted); proto_field_varint(&mut buf,4,timestamp);
    proto_field_varint(&mut buf,5,1); proto_field_varint(&mut buf,6,STEAM_WEBBROWSER_PLATFORM);
    proto_field_varint(&mut buf,7,1); proto_field_string(&mut buf,8,"Community");
    proto_field_bytes(&mut buf,9,&build_steam_device_details()); buf
}
fn parse_begin(data: &[u8]) -> Result<(u64,Vec<u8>,f32,u64,Challenge),String> {
    let values=fields(data)?;
    let (client_id,request_id,steamid)=(number(&values,1),bytes(&values,2),number(&values,5));
    if client_id==0 || request_id.is_empty() || steamid==0 { return Err("Steam did not return a sign-in session. Try again or use QR sign-in.".into()); }
    if !string(&values,7).is_empty() { return Err("Steam requires an account agreement. Complete it in Steam, then try again.".into()); }
    let mut codes=vec![]; let mut approval=false; let mut email=false;
    for field in values.iter().filter(|x|x.0==4) {
        let c=fields(&field.2)?; match number(&c,1) { 2=>{codes.push(2);email=true},3=>codes.push(3),4|5=>approval=true,1=>{},_=>{} }
    }
    let message=if email { "Enter the Steam Guard code sent to your email." } else if codes.contains(&3) { "Enter a Steam Guard authenticator code. If your phone is unavailable, you may need to finish sign-in later." } else if approval { "Approve this sign-in using the verification method Steam offers for your account." } else { "Completing Steam sign-in…" };
    let interval=f32::from_bits(number(&values,3) as u32);
    let interval=if interval.is_finite(){interval.clamp(2.0,10.0)}else{5.0};
    Ok((client_id,request_id,interval,steamid,Challenge{code_types:codes,message:message.into()}))
}
#[tauri::command]
pub async fn steam_credentials_begin(app_handle: tauri::AppHandle, account_name: String, password: String) -> Result<Challenge,String> {
    let password=Zeroizing::new(password);
    let account=account_name.trim().to_string();
    if account.is_empty() || account.len()>128 || password.is_empty() || password.len()>1024 { return Err("Enter your Steam account name and password.".into()); }
    let generation=cancel();
    tauri::async_runtime::spawn_blocking(move || {
        let mut key_request=vec![]; proto_field_string(&mut key_request,1,&account);
        let key_response=steam_auth_post("GetPasswordRSAPublicKey",key_request).map_err(friendly)?;
        let key=fields(&key_response)?;
        let encrypted=encrypt_password(&key,password.as_bytes())?;
        drop(password);
        let request=credentials_request(&account,&encrypted,number(&key,3));
        if !current(generation){return Err("Sign-in cancelled".into());}
        let response=steam_auth_post("BeginAuthSessionViaCredentials",request).map_err(friendly)?;
        let (client_id,request_id,interval,steamid,challenge)=parse_begin(&response)?;
        if !current(generation){return Err("Sign-in cancelled".into());}
        // Browser sessions with MachineToken confirmation require this call to send the email.
        let needs_email = fields(&response)?.iter().filter(|f|f.0==4).any(|f| fields(&f.2).is_ok_and(|v|number(&v,1)==6));
        if needs_email && challenge.code_types.contains(&2) {
            let result: serde_json::Value = reqwest::blocking::Client::builder().timeout(Duration::from_secs(20)).build()
                .map_err(|_|"Steam email verification unavailable")?
                .post("https://login.steampowered.com/jwt/checkdevice")
                .header("Origin","https://steamcommunity.com").header("Referer","https://steamcommunity.com/")
                .form(&[("clientid",client_id.to_string()),("steamid",steamid.to_string())])
                .send().and_then(|r|r.error_for_status()).map_err(|_|"Could not request a Steam Guard email")?
                .json().map_err(|_|"Steam email verification unavailable")?;
            if result["result"].as_u64()==Some(84){return Err("Steam is limiting sign-in attempts. Please wait before trying again.".into());}
        }
        if !current(generation){return Err("Sign-in cancelled".into());}
        {
            let mut pending=PENDING.lock().map_err(|_|"Steam session unavailable")?;
            if !current(generation){return Err("Sign-in cancelled".into());}
            *pending=Some(Pending{generation,client_id,steamid,codes:challenge.code_types.clone()});
        }
        tauri::async_runtime::spawn_blocking(move || {
            let result=poll(app_handle.clone(),generation,client_id,request_id,interval);
            if current(generation) {
                if let Err(error)=result { let _=app_handle.emit("steam-login-error",error); }
                if let Ok(mut pending)=PENDING.lock(){if pending.as_ref().is_some_and(|p|p.generation==generation){*pending=None;}}
            }
        });
        Ok(challenge)
    }).await.map_err(|_|"Steam sign-in could not start".to_string())?
}
#[tauri::command]
pub async fn steam_guard_submit(code: String, code_type: u32) -> Result<(),String> {
    let code=Zeroizing::new(code.trim().to_uppercase());
    if code.len()!=5 || !code.bytes().all(|c|c.is_ascii_alphanumeric()) { return Err("Enter the five-character Steam Guard code.".into()); }
    let pending=PENDING.lock().map_err(|_|"Steam session unavailable")?.clone().ok_or("Start a new Steam sign-in first")?;
    if !current(pending.generation) || !pending.codes.contains(&code_type){return Err("Start a new Steam sign-in first".into());}
    tauri::async_runtime::spawn_blocking(move || {
        if !current(pending.generation){return Err("Sign-in cancelled".into());}
        let mut request=vec![];
        proto_field_varint(&mut request,1,pending.client_id); proto_field_fixed64(&mut request,2,pending.steamid);
        proto_field_string(&mut request,3,&code);proto_field_varint(&mut request,4,code_type as u64);
        let response=steam_auth_post("UpdateAuthSessionWithSteamGuardCode",request).map_err(friendly)?;
        let response=fields(&response)?;
        if !string(&response,7).is_empty(){return Err("Complete the account agreement in Steam, then try again.".into());}
        Ok(())
    }).await.map_err(|_|"Could not submit Steam Guard code".to_string())?
}
fn poll(app: tauri::AppHandle,generation:u64,mut client_id:u64,request_id:Vec<u8>,interval:f32)->Result<(),String>{
    let start=Instant::now();
    while current(generation) {
        if start.elapsed()>Duration::from_secs(STEAM_QR_TIMEOUT_SECS){return Err("Steam sign-in expired. Please start again.".into());}
        std::thread::sleep(Duration::from_secs_f32(interval));
        if !current(generation){break;}
        let response=steam_auth_post("PollAuthSessionStatus",build_steam_poll_request(client_id,&request_id)).map_err(friendly)?;
        if !current(generation){break;}
        let poll=decode_steam_poll_response(&response);
        if poll.new_client_id!=0 {
            client_id=poll.new_client_id;
            if let Ok(mut pending)=PENDING.lock(){if let Some(p)=pending.as_mut(){if p.generation==generation{p.client_id=client_id;}}}
        }
        if !poll.refresh_token.is_empty() && !poll.access_token.is_empty(){
            let steamid=jwt_steamid(&poll.access_token)?;
            let account=if poll.account_name.is_empty(){steamid.clone()}else{poll.account_name};
            if !current(generation){break;}
            commit(generation,||store_steam_refresh_token(&account,&poll.refresh_token)).ok_or("Sign-in cancelled")?.map_err(|_|"Could not securely remember this Steam sign-in")?;
            let session=SteamSession{account_name:account,steamid,access_token:poll.access_token};
            finish_steam_login(&app,session,generation);
            return Ok(());
        }
    }
    Ok(())
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn cancelled_generation_cannot_commit() {
        let generation=cancel();assert_eq!(commit(generation,||42),Some(42));cancel();assert_eq!(commit(generation,||42),None);
    }
    #[test] fn rsa_password_encryption_round_trips_without_putting_plaintext_on_wire() {
        use rsa::{RsaPrivateKey,traits::PublicKeyParts};
        let private=RsaPrivateKey::new(&mut OsRng,1024).unwrap();
        let fields=vec![(1,0,private.n().to_str_radix(16).into_bytes()),(2,0,private.e().to_str_radix(16).into_bytes())];
        let encrypted=encrypt_password(&fields,b"fixture-only-password").unwrap();
        let cipher=general_purpose::STANDARD.decode(&encrypted).unwrap();
        assert_eq!(private.decrypt(Pkcs1v15Encrypt,&cipher).unwrap(),b"fixture-only-password");
        assert!(!String::from_utf8_lossy(&credentials_request("fixture-user",&encrypted,123)).contains("fixture-only-password"));
    }
    #[test] fn credentials_wire_contains_encrypted_password_and_persistent_browser_session(){
        let wire=credentials_request("tester","encrypted-only",123);
        let f=fields(&wire).unwrap(); assert_eq!(string(&f,2),"tester");assert_eq!(string(&f,3),"encrypted-only");
        assert_eq!(number(&f,4),123);assert_eq!(number(&f,6),2);assert_eq!(number(&f,7),1);
    }
    #[test] fn decodes_email_and_device_guard_without_exposing_session_ids(){
        let mut b=vec![];proto_field_varint(&mut b,1,42);proto_field_bytes(&mut b,2,b"private-session");
        b.extend_from_slice(&[29,0,0,0,64]);proto_field_varint(&mut b,5,76561198000000000);
        for kind in [2,3]{let mut c=vec![];proto_field_varint(&mut c,1,kind);proto_field_bytes(&mut b,4,&c);}
        let (_,_,interval,_,challenge)=parse_begin(&b).unwrap();assert_eq!(interval,2.0);assert_eq!(challenge.code_types,vec![2,3]);
        let json=serde_json::to_string(&challenge).unwrap();assert!(!json.contains("private-session"));assert!(!json.contains("76561198"));
    }
    #[test] fn rejects_partial_or_empty_auth_responses(){assert!(fields(&[10,5,1]).is_err());assert!(parse_begin(&[]).is_err());}
}
