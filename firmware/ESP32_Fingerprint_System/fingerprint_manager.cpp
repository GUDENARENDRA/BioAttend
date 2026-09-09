#include "fingerprint_manager.h"
#include "config.h"
#include "oled_manager.h"

FingerprintManager fp;

bool FingerprintManager::begin() {
  fpSerial.begin(FINGERPRINT_BAUD, SERIAL_8N1,
                 FINGERPRINT_RX_PIN, FINGERPRINT_TX_PIN);
  delay(300);
  finger.begin(FINGERPRINT_BAUD);
  delay(100);

  if (finger.verifyPassword()) {
    sensorOk = true;
    finger.getParameters();
    sensorCapacity = finger.capacity;
    Serial.printf("[FP] Sensor found. Capacity: %d\n", sensorCapacity);
    blinkLed(0x01, 2); // Blue flash on boot
  } else {
    sensorOk = false;
    Serial.println("[FP] ERROR: sensor not found!");
  }
  return sensorOk;
}

void FingerprintManager::blinkLed(uint8_t color, uint8_t count) {
  if (!sensorOk) return;
  for (uint8_t i = 0; i < count; i++) {
    finger.LEDcontrol(FINGERPRINT_LED_ON, 0, color, 0);
    delay(120);
    finger.LEDcontrol(FINGERPRINT_LED_OFF, 0, color, 0);
    delay(120);
  }
}

bool FingerprintManager::startEnroll(int id, String& err) {
  if (!sensorOk) { err = "Sensor not ready"; return false; }
  if (state != FP_IDLE) { err = "Device busy"; return false; }
  if (id < 1 || id > sensorCapacity) { err = "ID out of range"; return false; }
  if (!slotIsFree(id)) { err = "ID already in use"; return false; }

  activeId = id;
  matchedId = -1;
  matchedScore = 0;
  state = FP_ENROLL_WAIT_FIRST;
  stateStart = millis();
  oled.showStatus("ID: " + String(id), "Place Finger");
  blinkLed(0x01, 1);
  return true;
}

bool FingerprintManager::startSearch(String& err) {
  if (!sensorOk) { err = "Sensor not ready"; return false; }
  if (state != FP_IDLE) { err = "Device busy"; return false; }

  matchedId = -1;
  matchedScore = 0;
  state = FP_SEARCH_WAIT;
  stateStart = millis();
  oled.showStatus("IDENTIFY", "PLACE FINGER");
  blinkLed(0x01, 1);
  return true;
}

void FingerprintManager::update() {
  if (state == FP_IDLE || !sensorOk) return;

  static unsigned long resultTime = 0;
  bool isResultState = (state == FP_ENROLL_SUCCESS || state == FP_ENROLL_FAILED ||
                        state == FP_SEARCH_MATCHED || state == FP_SEARCH_NOT_FOUND ||
                        state == FP_ERROR);
  if (isResultState) {
    if (resultTime == 0) resultTime = millis();
    if (millis() - resultTime > 3000) {
      state = FP_IDLE;
      resultTime = 0;
      oled.showStatus("READY", "BioAttend Connected");
    }
    return;
  } else {
    resultTime = 0;
  }

  if (millis() - lastStep < FP_POLL_MS) return;
  lastStep = millis();

  switch (state) {

    case FP_ENROLL_WAIT_FIRST:
      if (finger.getImage() == FINGERPRINT_OK) {
        if (finger.image2Tz(1) == FINGERPRINT_OK) {
          state = FP_ENROLL_WAIT_REMOVE;
          stateStart = millis();
          oled.showStatus("ID: " + String(activeId), "Remove Finger");
          blinkLed(0x02, 1);
        } else {
          state = FP_ENROLL_FAILED;
          oled.showEnrollFailed(activeId);
          blinkLed(0x04, 2);
        }
      } else if (timedOut()) {
        state = FP_ERROR;
        oled.showEnrollFailed(activeId);
        blinkLed(0x04, 2);
      }
      break;

    case FP_ENROLL_WAIT_REMOVE:
      if (finger.getImage() == FINGERPRINT_NOFINGER) {
        state = FP_ENROLL_WAIT_SECOND;
        stateStart = millis();
        oled.showStatus("ID: " + String(activeId), "Place Finger Again");
        blinkLed(0x01, 1);
      }
      break;

    case FP_ENROLL_WAIT_SECOND:
      if (finger.getImage() == FINGERPRINT_OK) {
        if (finger.image2Tz(2) == FINGERPRINT_OK) {
          state = FP_ENROLL_PROCESS;
        } else {
          state = FP_ENROLL_FAILED;
          oled.showEnrollFailed(activeId);
          blinkLed(0x04, 2);
        }
      } else if (timedOut()) {
        state = FP_ERROR;
        oled.showEnrollFailed(activeId);
        blinkLed(0x04, 2);
      }
      break;

    case FP_ENROLL_PROCESS:
      if (finger.createModel() == FINGERPRINT_OK) {
        if (finger.storeModel(activeId) == FINGERPRINT_OK) {
          state = FP_ENROLL_SUCCESS;
          oled.showEnrollSuccess(activeId);
          blinkLed(0x02, 3); // Green 3 flashes on success
        } else {
          state = FP_ENROLL_FAILED;
          oled.showEnrollFailed(activeId);
          blinkLed(0x04, 2);
        }
      } else {
        state = FP_ENROLL_FAILED;
        oled.showEnrollFailed(activeId);
        blinkLed(0x04, 2);
      }
      break;

    case FP_SEARCH_WAIT:
      if (finger.getImage() == FINGERPRINT_OK) {
        if (finger.image2Tz() == FINGERPRINT_OK) {
          state = FP_SEARCH_PROCESS;
        } else {
          state = FP_SEARCH_NOT_FOUND;
          oled.showNoMatch();
          blinkLed(0x04, 2);
        }
      } else if (timedOut()) {
        state = FP_SEARCH_NOT_FOUND;
        oled.showNoMatch();
        blinkLed(0x04, 2);
      }
      break;

    case FP_SEARCH_PROCESS:
      if (finger.fingerSearch() == FINGERPRINT_OK) {
        matchedId = finger.fingerID;
        matchedScore = finger.confidence;
        state = FP_SEARCH_MATCHED;
        oled.showMatchFound(matchedId, matchedScore);
        blinkLed(0x02, 2); // Green match flash
      } else {
        matchedId = -1;
        state = FP_SEARCH_NOT_FOUND;
        oled.showNoMatch();
        blinkLed(0x04, 2);
      }
      break;

    default:
      break;
  }
}

bool FingerprintManager::timedOut() {
  if (millis() - stateStart > FP_IDLE_TIMEOUT_MS) {
    return true;
  }
  return false;
}

void FingerprintManager::cancel() {
  if (state != FP_IDLE) {
    state = FP_IDLE;
    oled.showStatus("CANCELLED", "");
  }
}

bool FingerprintManager::deleteFingerprint(int id) {
  if (!sensorOk || id < 1 || id > sensorCapacity) return false;
  bool ok = (finger.deleteModel(id) == FINGERPRINT_OK);
  if (ok) blinkLed(0x04, 2); // Red LED blink on deletion
  return ok;
}

bool FingerprintManager::deleteAll() {
  if (!sensorOk) return false;
  bool ok = (finger.emptyDatabase() == FINGERPRINT_OK);
  if (ok) blinkLed(0x04, 4); // Red LED 4 flashes on total wipe
  return ok;
}

bool FingerprintManager::slotIsFree(int id) {
  if (!sensorOk || id < 1 || id > sensorCapacity) return false;
  return finger.loadModel(id) != FINGERPRINT_OK;
}

bool FingerprintManager::getUsedIds(bool* usedFlags) {
  if (!sensorOk) return false;
  for (int i = 1; i <= sensorCapacity; i++) {
    usedFlags[i] = (finger.loadModel(i) == FINGERPRINT_OK);
  }
  return true;
}

const char* FingerprintManager::stateName() {
  switch (state) {
    case FP_IDLE:                return "IDLE";
    case FP_ENROLL_WAIT_FIRST:   return "WAITING_FINGER";
    case FP_ENROLL_WAIT_REMOVE:  return "REMOVE_FINGER";
    case FP_ENROLL_WAIT_SECOND:  return "WAITING_FINGER_AGAIN";
    case FP_ENROLL_PROCESS:      return "PROCESSING";
    case FP_ENROLL_SUCCESS:      return "SUCCESS";
    case FP_ENROLL_FAILED:       return "FAILED";
    case FP_SEARCH_WAIT:         return "WAITING_FINGER";
    case FP_SEARCH_PROCESS:      return "PROCESSING";
    case FP_SEARCH_MATCHED:      return "MATCHED";
    case FP_SEARCH_NOT_FOUND:    return "NOT_FOUND";
    case FP_ERROR:               return "ERROR";
  }
  return "UNKNOWN";
}
