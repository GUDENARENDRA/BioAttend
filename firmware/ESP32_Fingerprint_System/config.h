#pragma once

// ===== PINS =====
#define FINGERPRINT_RX_PIN  16   // ESP32 receives (from sensor Yellow TX)
#define FINGERPRINT_TX_PIN  17   // ESP32 transmits (to sensor Green RX)
#define FINGERPRINT_BAUD    57600
#define OLED_SDA            21
#define OLED_SCL            22

// ===== WIFI =====
#define AP_SSID      "ESP32_SETUP"
#define AP_PASSWORD  "12345678"
#define WIFI_TIMEOUT 15000   // ms to wait for router connection

// ===== API =====
#define API_PORT 80

// ===== FINGERPRINT =====
#define FP_CAPACITY        512        // max templates (R307/AS608 = 512)
#define FP_IDLE_TIMEOUT_MS 15000      // give up if no finger in 15s
#define FP_POLL_MS         100        // state machine step interval
