#include "storage_manager.h"

StorageManager storage;

void StorageManager::begin() {
  prefs.begin("fp_system", false);
}

void StorageManager::saveWifi(const String& ssid, const String& pass) {
  prefs.putString("wifi_ssid", ssid);
  prefs.putString("wifi_pass", pass);
}

String StorageManager::getWifiSSID() { return prefs.getString("wifi_ssid", ""); }
String StorageManager::getWifiPass() { return prefs.getString("wifi_pass", ""); }

void StorageManager::clearWifi() {
  prefs.remove("wifi_ssid");
  prefs.remove("wifi_pass");
}
