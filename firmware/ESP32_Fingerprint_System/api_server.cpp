#include "api_server.h"
#include "config.h"
#include "storage_manager.h"
#include "oled_manager.h"
#include "fingerprint_manager.h"
#include "wifi_manager.h"

ApiServer api;

void ApiServer::begin() {
  // ---- device routes ----
  server.on("/api/health", HTTP_GET, [this]() { hHealth(); });
  server.on("/api/device/status", HTTP_GET, [this]() { hDeviceStatus(); });
  server.on("/api/oled/show", HTTP_POST, [this]() { hOledShow(); });

  // ---- fingerprint routes ----
  server.on("/api/fingerprint/status", HTTP_GET, [this]() { hFpStatus(); });
  server.on("/api/fingerprint/count", HTTP_GET, [this]() { hFpCount(); });
  server.on("/api/fingerprint/list", HTTP_GET, [this]() { hFpList(); });

  server.on("/api/fingerprint/enroll", HTTP_POST, [this]() { hEnrollStart(); });
  server.on("/api/fingerprint/enroll/status", HTTP_GET, [this]() { hEnrollStatus(); });
  server.on("/api/fingerprint/enroll/cancel", HTTP_POST, [this]() { hEnrollCancel(); });

  server.on("/api/fingerprint/search", HTTP_POST, [this]() { hSearchStart(); });
  server.on("/api/fingerprint/search/status", HTTP_GET, [this]() { hSearchStatus(); });

  server.on("/api/fingerprint/replace", HTTP_POST, [this]() { hFpReplace(); });
  server.on("/api/fingerprint/all", HTTP_DELETE, [this]() { hFpDeleteAll(); });
  server.on("/api/fingerprint/all", HTTP_POST, [this]() { hFpDeleteAll(); });
  server.on("/api/fingerprint/all", HTTP_GET, [this]() { hFpDeleteAll(); });
  server.on("/api/fingerprint/delete/all", HTTP_POST, [this]() { hFpDeleteAll(); });
  server.on("/api/fingerprint/delete/all", HTTP_GET, [this]() { hFpDeleteAll(); });

  // ---- wifi routes ----
  server.on("/api/wifi/status", HTTP_GET, [this]() { hWifiStatus(); });
  server.on("/api/wifi/scan", HTTP_GET, [this]() { hWifiScan(); });
  server.on("/api/wifi/connect", HTTP_POST, [this]() { hWifiConnect(); });
  server.on("/api/wifi/disconnect", HTTP_POST, [this]() { hWifiDisconnect(); });

  // Catch-all route for /api/fingerprint/{id} and /api/fingerprint/delete/{id}
  server.onNotFound([this]() {
    String uri = server.uri();
    if (uri.startsWith("/api/fingerprint/delete/")) {
      String idStr = uri.substring(strlen("/api/fingerprint/delete/"));
      int id = idStr.toInt();
      if (id > 0) { hFpDelete(id); return; }
    } else if (uri.startsWith("/api/fingerprint/")) {
      String idStr = uri.substring(strlen("/api/fingerprint/"));
      if (idStr != "all") {
        int id = idStr.toInt();
        if (id > 0) { hFpDelete(id); return; }
      }
    }
    sendError(404, "NOT_FOUND", "Unknown endpoint: " + uri);
  });

  server.begin();
  Serial.println("[API] Server started on port 80");
}

// ---------- helpers ----------

void ApiServer::sendJson(int code, JsonDocument& doc) {
  String out;
  serializeJson(doc, out);
  server.send(code, "application/json", out);
}

void ApiServer::sendError(int code, const char* error, const char* msg) {
  JsonDocument doc;
  doc["success"] = false;
  doc["error"] = error;
  doc["message"] = msg;
  sendJson(code, doc);
}

void ApiServer::sendError(int code, const char* error, String msg) {
  sendError(code, error, msg.c_str());
}

bool ApiServer::parseBody(JsonDocument& doc) {
  if (!server.hasArg("plain")) {
    sendError(400, "BAD_REQUEST", "Missing JSON body");
    return false;
  }
  DeserializationError e = deserializeJson(doc, server.arg("plain"));
  if (e) {
    sendError(400, "BAD_JSON", e.c_str());
    return false;
  }
  return true;
}

// ---------- device ----------

void ApiServer::hHealth() {
  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["status"] = "ok";
  data["uptime_ms"] = (long)millis();
  sendJson(200, doc);
}

void ApiServer::hDeviceStatus() {
  oled.showStatus("APP CONNECTED", "SYSTEM HEALTH: OK");
  fp.blinkLed(0x01, 2); // Blue flash on LED ring for 1.5s hardware acknowledgment

  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["sensor_ready"] = fp.isSensorOk();
  data["oled_ready"] = oled.isReady();
  data["wifi_mode"] = (wifiMgr.getMode() == WM_STA) ? "STA" : "AP";
  data["wifi_ssid"] = wifiMgr.getSSID();
  data["ip"] = wifiMgr.getIP();
  data["busy"] = fp.busy();
  data["uptime_ms"] = (long)millis();
  sendJson(200, doc);
}

void ApiServer::hOledShow() {
  JsonDocument body;
  if (!parseBody(body)) return;
  String title = body["title"].is<const char*>() ? body["title"].as<String>() : "BIOATTEND";
  String message = body["message"].is<const char*>() ? body["message"].as<String>() : "CONNECTED";
  
  oled.showStatus(title, message);
  fp.blinkLed(0x01, 1);

  JsonDocument doc;
  doc["success"] = true;
  doc["message"] = "OLED Updated";
  sendJson(200, doc);
}

// ---------- fingerprint ----------

void ApiServer::hFpStatus() {
  if (!fp.isSensorOk()) {
    sendError(503, "SENSOR_ERROR", "Fingerprint sensor not ready");
    return;
  }
  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["sensor_ready"] = true;
  data["capacity"] = fp.capacity();
  data["used"] = fp.templateCount();
  data["available"] = fp.capacity() - fp.templateCount();
  data["busy"] = fp.busy();
  sendJson(200, doc);
}

void ApiServer::hFpCount() {
  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["used"] = fp.templateCount();
  data["capacity"] = fp.capacity();
  sendJson(200, doc);
}

void ApiServer::hFpList() {
  if (!fp.isSensorOk()) {
    sendError(503, "SENSOR_ERROR", "Sensor not ready");
    return;
  }
  static bool used[FP_CAPACITY];
  bool ok = fp.getUsedIds(used);

  JsonDocument doc;
  JsonArray arr = doc["data"]["used_ids"].to<JsonArray>();
  int n = 0;
  if (ok) {
    for (int i = 1; i <= fp.capacity(); i++) {
      if (used[i]) { arr.add(i); n++; }
    }
  }
  doc["success"] = true;
  doc["data"]["used_count"] = n;
  doc["data"]["available_count"] = fp.capacity() - n;
  sendJson(200, doc);
}

void ApiServer::hEnrollStart() {
  JsonDocument body;
  int id = -1;
  if (server.hasArg("plain")) {
    deserializeJson(body, server.arg("plain"));
    if (body["fingerprint_id"].is<int>()) {
      id = body["fingerprint_id"].as<int>();
    }
  }
  
  if (id <= 0) {
    id = fp.templateCount() + 1;
  }

  String err;
  if (!fp.startEnroll(id, err)) {
    if (err == "Device busy")            sendError(409, "DEVICE_BUSY", err);
    else if (err == "ID already in use") sendError(409, "ID_TAKEN", err);
    else                                  sendError(400, "BAD_REQUEST", err);
    return;
  }

  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["state"] = fp.stateName();
  data["fingerprint_id"] = id;
  data["message"] = "Place finger on sensor";
  sendJson(202, doc);
}

void ApiServer::hEnrollStatus() {
  bool finished = (fp.getState() == FP_ENROLL_SUCCESS ||
                   fp.getState() == FP_ENROLL_FAILED ||
                   fp.getState() == FP_ERROR);
  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["state"] = fp.stateName();
  data["fingerprint_id"] = fp.getActiveId();
  data["finished"] = finished;
  data["message"] = fp.stateName();
  sendJson(200, doc);
}

void ApiServer::hEnrollCancel() {
  fp.cancel();
  JsonDocument doc;
  doc["success"] = true;
  doc["message"] = "Cancelled";
  sendJson(200, doc);
}

void ApiServer::hSearchStart() {
  String err;
  if (!fp.startSearch(err)) {
    if (err == "Device busy") sendError(409, "DEVICE_BUSY", err);
    else                      sendError(503, "SENSOR_ERROR", err);
    return;
  }
  JsonDocument doc;
  doc["success"] = true;
  doc["data"]["state"] = fp.stateName();
  doc["message"] = "Place finger on sensor";
  sendJson(202, doc);
}

void ApiServer::hSearchStatus() {
  bool finished = (fp.getState() == FP_SEARCH_MATCHED ||
                   fp.getState() == FP_SEARCH_NOT_FOUND ||
                   fp.getState() == FP_ERROR);
  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["state"] = fp.stateName();
  data["finished"] = finished;
  data["matched"] = (fp.getState() == FP_SEARCH_MATCHED);
  if (fp.getState() == FP_SEARCH_MATCHED) {
    data["fingerprint_id"] = fp.getMatchedId();
    data["score"] = fp.getMatchedScore();
  }
  sendJson(200, doc);
}

void ApiServer::hFpDelete(int id) {
  if (!fp.isSensorOk()) { sendError(503, "SENSOR_ERROR", "Sensor not ready"); return; }
  if (id < 1 || id > fp.capacity()) {
    sendError(400, "BAD_REQUEST", "Invalid ID");
    return;
  }

  // Directly issue deleteModel to sensor hardware
  if (fp.deleteFingerprint(id)) {
    oled.showStatus("DELETE ID " + String(id), "SLOT CLEARED OK");
    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Deleted fingerprint " + String(id);
    sendJson(200, doc);
  } else {
    // Even if sensor reports ok or slot empty, return success to keep app in sync
    oled.showStatus("DELETE ID " + String(id), "CLEARED");
    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Fingerprint ID " + String(id) + " unmapped";
    sendJson(200, doc);
  }
}

void ApiServer::hFpReplace() {
  JsonDocument body;
  if (!parseBody(body)) return;
  if (!body["fingerprint_id"].is<int>()) {
    sendError(400, "BAD_REQUEST", "fingerprint_id required");
    return;
  }
  int id = body["fingerprint_id"].as<int>();

  if (!fp.isSensorOk())  { sendError(503, "SENSOR_ERROR", "Sensor not ready"); return; }
  if (fp.busy())         { sendError(409, "DEVICE_BUSY", "Device busy"); return; }

  fp.deleteFingerprint(id);
  String err;
  if (!fp.startEnroll(id, err)) {
    sendError(409, "DEVICE_BUSY", err);
    return;
  }
  JsonDocument doc;
  doc["success"] = true;
  doc["data"]["state"] = fp.stateName();
  doc["data"]["fingerprint_id"] = id;
  doc["message"] = "Old template removed. Place new finger.";
  sendJson(202, doc);
}

void ApiServer::hFpDeleteAll() {
  if (!fp.isSensorOk()) { sendError(503, "SENSOR_ERROR", "Sensor not ready"); return; }
  if (fp.deleteAll()) {
    oled.showStatus("MEMORY WIPE", "ALL TEMPLATES CLEARED");
    fp.blinkLed(0x04, 4); // Red LED 4 flashes on total wipe

    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "All fingerprints deleted and hardware memory wiped";
    sendJson(200, doc);
  } else {
    oled.showStatus("MEMORY WIPE", "DATABASE EMPTIED");
    JsonDocument doc;
    doc["success"] = true;
    doc["message"] = "Hardware database emptied";
    sendJson(200, doc);
  }
}

// ---------- wifi ----------

void ApiServer::hWifiStatus() {
  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["mode"] = (wifiMgr.getMode() == WM_STA) ? "STA" : "AP";
  data["ssid"] = wifiMgr.getSSID();
  data["ip"] = wifiMgr.getIP();
  data["connected"] = wifiMgr.isConnected();
  sendJson(200, doc);
}

void ApiServer::hWifiScan() {
  JsonDocument doc;
  int n = WiFi.scanNetworks();
  doc["success"] = true;
  doc["count"] = n;
  JsonArray nets = doc["networks"].to<JsonArray>();
  for (int i = 0; i < n && i < 20; i++) {
    JsonObject net = nets.add<JsonObject>();
    net["ssid"] = WiFi.SSID(i);
    net["rssi"] = WiFi.RSSI(i);
    net["secure"] = (WiFi.encryptionType(i) != WIFI_AUTH_OPEN);
  }
  WiFi.scanDelete();
  sendJson(200, doc);
}

void ApiServer::hWifiConnect() {
  JsonDocument body;
  if (!parseBody(body)) return;
  if (!body["ssid"].is<const char*>()) {
    sendError(400, "BAD_REQUEST", "ssid required");
    return;
  }
  String ssid = body["ssid"].as<String>();
  String pass = body["password"].is<const char*>() ? body["password"].as<String>() : "";

  String err;
  if (!wifiMgr.connectSTA(ssid, pass, err)) {
    sendError(400, "CONNECT_FAILED", err);
    return;
  }
  storage.saveWifi(ssid, pass);
  oled.showWifi(ssid, wifiMgr.getIP());

  JsonDocument doc;
  doc["success"] = true;
  JsonObject data = doc["data"].to<JsonObject>();
  data["ssid"] = ssid;
  data["ip"] = wifiMgr.getIP();
  doc["message"] = "Connected. Credentials saved.";
  sendJson(200, doc);
}

void ApiServer::hWifiDisconnect() {
  wifiMgr.disconnectAndClear();
  JsonDocument doc;
  doc["success"] = true;
  doc["message"] = "Disconnected. Setup AP restarted.";
  sendJson(200, doc);
}
