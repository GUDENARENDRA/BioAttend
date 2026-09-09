#pragma once
#include <WiFi.h>
#include "config.h" 

enum WifiMode { WM_NONE, WM_STA, WM_AP };

class WifiManager {
public:
  void begin();                 // decide AP vs STA using storage
  bool connectSTA(const String& ssid, const String& pass, String& err);
  void startAP();
  void disconnectAndClear();
  WifiMode getMode() { return mode; }
  String getIP() {
    return mode == WM_STA ? WiFi.localIP().toString()
                          : WiFi.softAPIP().toString();
  }
  String getSSID() { return mode == WM_STA ? WiFi.SSID() : String(AP_SSID); }
  bool isConnected() { return mode == WM_STA && WiFi.status() == WL_CONNECTED; }
private:
  WifiMode mode = WM_NONE;
};

extern WifiManager wifiMgr;
