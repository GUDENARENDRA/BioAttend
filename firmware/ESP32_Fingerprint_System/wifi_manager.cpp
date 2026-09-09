#include "wifi_manager.h"
#include "config.h"
#include "storage_manager.h"
#include "oled_manager.h"

WifiManager wifiMgr;

void WifiManager::begin() {
  String ssid = storage.getWifiSSID();
  String pass = storage.getWifiPass();

  if (ssid.length() > 0) {
    String err;
    if (connectSTA(ssid, pass, err)) {
      mode = WM_STA;
      oled.showWifi(ssid, getIP());
      Serial.printf("[WIFI] Connected to %s, IP: %s\n",
                    ssid.c_str(), getIP().c_str());
      return;
    }
    Serial.printf("[WIFI] STA failed: %s — falling back to AP\n", err.c_str());
  }
  startAP();
}

bool WifiManager::connectSTA(const String& ssid, const String& pass, String& err) {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid.c_str(), pass.c_str());

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - start > WIFI_TIMEOUT) {
      err = "Connection timeout";
      WiFi.disconnect();
      return false;
    }
    delay(100);
  }
  mode = WM_STA;
  return true;
}

void WifiManager::startAP() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  mode = WM_AP;
  oled.showAP(AP_SSID, getIP());
  Serial.printf("[WIFI] AP started: %s  IP: %s\n", AP_SSID, getIP().c_str());
}

void WifiManager::disconnectAndClear() {
  storage.clearWifi();
  WiFi.disconnect(true);
  delay(300);
  startAP();
}
