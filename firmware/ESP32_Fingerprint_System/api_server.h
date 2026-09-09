#pragma once
#include <WebServer.h>
#include <ArduinoJson.h>
#include "config.h"

class ApiServer {
public:
  void begin();
  void handleClient() { server.handleClient(); }
private:
  WebServer server{API_PORT};

  // helpers
  void sendJson(int code, JsonDocument& doc);
  void sendError(int code, const char* error, const char* msg);
  void sendError(int code, const char* error, String msg);
  bool parseBody(JsonDocument& doc);

  // handlers
  void hHealth();
  void hDeviceStatus();
  void hOledShow();
  void hFpStatus();
  void hFpCount();
  void hFpList();
  void hEnrollStart();
  void hEnrollStatus();
  void hEnrollCancel();
  void hSearchStart();
  void hSearchStatus();
  void hFpDelete(int id);
  void hFpReplace();
  void hFpDeleteAll();
  void hWifiStatus();
  void hWifiScan();
  void hWifiConnect();
  void hWifiDisconnect();
};

extern ApiServer api;
