#pragma once
#include <Adafruit_SSD1306.h>
#include <Wire.h>

class OledManager {
public:
  bool begin();
  void showBoot(const String& line);
  void showWifi(const String& ssid, const String& ip);
  void showAP(const String& ssid, const String& ip);
  void showStatus(const String& title, const String& line2);
  void showResult(const String& title, const String& detail);
  
  // Specific OLED feedback routines
  void showEnrollSuccess(int id);
  void showEnrollFailed(int id);
  void showMatchFound(int id, int score);
  void showNoMatch();
  
  bool isReady() { return ready; }
private:
  Adafruit_SSD1306 display{128, 64, &Wire, -1};
  bool ready = false;
};

extern OledManager oled;
