#pragma once
#include <Preferences.h>

class StorageManager {
public:
  void begin();
  void saveWifi(const String& ssid, const String& pass);
  String getWifiSSID();
  String getWifiPass();
  void clearWifi();
private:
  Preferences prefs;
};

extern StorageManager storage;
