#include "config.h"
#include "storage_manager.h"
#include "oled_manager.h"
#include "fingerprint_manager.h"
#include "wifi_manager.h"
#include "api_server.h"

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n===== ESP32 BioAttend System =====");

  // 1. OLED Display
  if (!oled.begin()) {
    Serial.println("[OLED] FAILED - continuing without display");
  } else {
    oled.showBoot("Starting...");
  }

  // 2. Storage (load saved Wi-Fi credentials from NVS flash)
  storage.begin();

  // 3. R307S Fingerprint Sensor
  if (fp.begin()) {
    Serial.printf("[FP] Sensor OK. Capacity: %d, Templates: %d\n",
                  fp.capacity(), fp.templateCount());
    oled.showBoot("Sensor ready");
  } else {
    Serial.println("[FP] ERROR: check wiring (Yellow->16, Green->17, Red->5V)");
    oled.showBoot("SENSOR ERROR");
  }
  delay(800);

  // 4. Wi-Fi (STA mode if saved credentials work, else AP setup mode)
  wifiMgr.begin();

  // 5. HTTP REST API Server
  api.begin();

  Serial.println("[SYS] Ready. IP: " + wifiMgr.getIP());
}

void loop() {
  api.handleClient();   // serve HTTP REST requests
  fp.update();          // advance fingerprint non-blocking state machine
  delay(2);             // small breather for stability
}
