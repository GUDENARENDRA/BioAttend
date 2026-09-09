#include "oled_manager.h"
#include "config.h"

OledManager oled;

bool OledManager::begin() {
  Wire.begin(OLED_SDA, OLED_SCL);
  ready = display.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  if (ready) {
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setCursor(0, 0);
    display.println("=== BIOATTEND ===");
    display.display();
  }
  return ready;
}

void OledManager::showBoot(const String& line) {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("=== BIOATTEND ===");
  display.setCursor(0, 20);
  display.println(line);
  display.display();
}

void OledManager::showWifi(const String& ssid, const String& ip) {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("WiFi: " + ssid);
  display.setCursor(0, 18);
  display.println("IP Address:");
  display.setCursor(0, 32);
  display.println(ip);
  display.setCursor(0, 50);
  display.println("REST API READY");
  display.display();
}

void OledManager::showAP(const String& ssid, const String& ip) {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("SETUP AP MODE");
  display.setCursor(0, 16);
  display.println("SSID: " + ssid);
  display.setCursor(0, 32);
  display.println("IP: " + ip);
  display.setCursor(0, 50);
  display.println("Connect via App");
  display.display();
}

void OledManager::showStatus(const String& title, const String& line2) {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println(title);
  display.setCursor(0, 24);
  display.println(line2);
  display.display();
}

void OledManager::showResult(const String& title, const String& detail) {
  showStatus(title, detail);
}

void OledManager::showEnrollSuccess(int id) {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("ID: " + String(id));
  display.setCursor(0, 24);
  display.println("Enrolled Success");
  display.display();
}

void OledManager::showEnrollFailed(int id) {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("ID: " + String(id));
  display.setCursor(0, 24);
  display.println("Enroll Failed");
  display.display();
}

void OledManager::showMatchFound(int id, int score) {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("=== ATTENDANCE ===");
  display.setTextSize(2);
  display.setCursor(0, 20);
  display.println("ID " + String(id));
  display.setTextSize(1);
  display.setCursor(0, 44);
  display.println("MATCHED [OK] ");
  display.display();
}

void OledManager::showNoMatch() {
  if (!ready) return;
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.println("=== ATTENDANCE ===");
  display.setTextSize(2);
  display.setCursor(0, 20);
  display.println("NO MATCH");
  display.setTextSize(1);
  display.setCursor(0, 44);
  display.println("REJECTED [X]");
  display.display();
}
