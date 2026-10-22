use ed25519_dalek::{SigningKey, VerifyingKey, Signer, Verifier, Signature};
use x25519_dalek::{StaticSecret, PublicKey as XPublicKey};
use rand::rngs::OsRng;
use base64::engine::general_purpose::STANDARD as BASE64;
use base64::Engine;
use serde::{Serialize, Deserialize};
use chacha20poly1305::{
    aead::{Aead, KeyInit, Payload},
    ChaCha20Poly1305, Nonce, Key
};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct DeviceIdentityDto {
    pub device_id: String,
    pub device_name: String,
    pub os_type: String,
    pub signing_pubkey_b64: String,
    pub agreement_pubkey_b64: String,
}

pub struct CryptoEngine;

impl CryptoEngine {
    /// Generate Ed25519 Signing Keypair
    pub fn generate_signing_key() -> (SigningKey, VerifyingKey) {
        let mut csprng = OsRng;
        let signing_key = SigningKey::generate(&mut csprng);
        let verifying_key = signing_key.verifying_key();
        (signing_key, verifying_key)
    }

    /// Generate X25519 Agreement Keypair
    pub fn generate_agreement_key() -> (StaticSecret, XPublicKey) {
        let mut csprng = OsRng;
        let secret = StaticSecret::random_from_rng(&mut csprng);
        let public = XPublicKey::from(&secret);
        (secret, public)
    }

    /// Sign message with Ed25519
    pub fn sign(signing_key: &SigningKey, message: &[u8]) -> Vec<u8> {
        let sig: Signature = signing_key.sign(message);
        sig.to_bytes().to_vec()
    }

    /// Verify message with Ed25519
    pub fn verify(verifying_key: &VerifyingKey, message: &[u8], signature_bytes: &[u8]) -> bool {
        if signature_bytes.len() != 64 {
            return false;
        }
        let mut arr = [0u8; 64];
        arr.copy_from_slice(signature_bytes);
        let sig = Signature::from_bytes(&arr);
        verifying_key.verify(message, &sig).is_ok()
    }

    /// Diffie-Hellman Key Agreement (X25519)
    pub fn diffie_hellman(secret: &StaticSecret, peer_public_bytes: &[u8; 32]) -> [u8; 32] {
        let peer_pub = XPublicKey::from(*peer_public_bytes);
        let shared = secret.diffie_hellman(&peer_pub);
        *shared.as_bytes()
    }

    /// Encrypt chunk payload with ChaCha20-Poly1305
    pub fn encrypt_chunk(
        key_bytes: &[u8; 32],
        nonce_bytes: &[u8; 12],
        aad: &[u8],
        plaintext: &[u8],
    ) -> Result<Vec<u8>, String> {
        let key = Key::from_slice(key_bytes);
        let cipher = ChaCha20Poly1305::new(key);
        let nonce = Nonce::from_slice(nonce_bytes);
        let payload = Payload {
            msg: plaintext,
            aad,
        };
        cipher.encrypt(nonce, payload).map_err(|e| e.to_string())
    }

    /// Decrypt chunk payload with ChaCha20-Poly1305
    pub fn decrypt_chunk(
        key_bytes: &[u8; 32],
        nonce_bytes: &[u8; 12],
        aad: &[u8],
        ciphertext: &[u8],
    ) -> Result<Vec<u8>, String> {
        let key = Key::from_slice(key_bytes);
        let cipher = ChaCha20Poly1305::new(key);
        let nonce = Nonce::from_slice(nonce_bytes);
        let payload = Payload {
            msg: ciphertext,
            aad,
        };
        cipher.decrypt(nonce, payload).map_err(|e| e.to_string())
    }

    pub fn to_base64(data: &[u8]) -> String {
        BASE64.encode(data)
    }

    pub fn from_base64(data: &str) -> Result<Vec<u8>, base64::DecodeError> {
        BASE64.decode(data)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ed25519_sign_and_verify() {
        let (signing, verifying) = CryptoEngine::generate_signing_key();
        let message = b"SyncSwift v2 Desktop Handshake Test";
        let signature = CryptoEngine::sign(&signing, message);
        assert_eq!(signature.len(), 64);
        assert!(CryptoEngine::verify(&verifying, message, &signature));
    }

    #[test]
    fn test_x25519_diffie_hellman_agreement() {
        let (secret_a, public_a) = CryptoEngine::generate_agreement_key();
        let (secret_b, public_b) = CryptoEngine::generate_agreement_key();

        let shared_ab = CryptoEngine::diffie_hellman(&secret_a, public_b.as_bytes());
        let shared_ba = CryptoEngine::diffie_hellman(&secret_b, public_a.as_bytes());

        assert_eq!(shared_ab, shared_ba);
    }

    #[test]
    fn test_chacha20_poly1305_roundtrip() {
        let key = [0x42u8; 32];
        let nonce = [0x24u8; 12];
        let aad = b"SWIF_HEADER_TEST";
        let plaintext = b"Hello SyncSwift 1MiB Chunk Stream";

        let ciphertext = CryptoEngine::encrypt_chunk(&key, &nonce, aad, plaintext).unwrap();
        let decrypted = CryptoEngine::decrypt_chunk(&key, &nonce, aad, &ciphertext).unwrap();

        assert_eq!(decrypted, plaintext);
    }
}
