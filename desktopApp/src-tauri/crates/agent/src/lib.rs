use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SyncRule {
    pub id: String,
    pub name: String,
    pub local_path: String,
    pub remote_device_id: String,
    pub remote_path: String,
    pub direction: String,
    pub is_enabled: bool,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ClipboardPayload {
    pub text: String,
    pub timestamp_ms: u64,
}

pub struct AgentEngine;

impl AgentEngine {
    pub fn new() -> Self {
        Self
    }
}
