#pragma once
#include <Adafruit_Fingerprint.h>
#include <HardwareSerial.h>

enum FpState {
  FP_IDLE,
  FP_ENROLL_WAIT_FIRST,   // waiting for first finger
  FP_ENROLL_WAIT_REMOVE,  // finger captured, ask to remove
  FP_ENROLL_WAIT_SECOND,  // waiting for second scan
  FP_ENROLL_PROCESS,
  FP_ENROLL_SUCCESS,
  FP_ENROLL_FAILED,
  FP_SEARCH_WAIT,         // waiting for finger to identify
  FP_SEARCH_PROCESS,
  FP_SEARCH_MATCHED,
  FP_SEARCH_NOT_FOUND,
  FP_ERROR
};

class FingerprintManager {
public:
  bool begin();
  void update();   // call every loop()

  bool isSensorOk() { return sensorOk; }
  bool busy() { return state != FP_IDLE; }

  bool startEnroll(int id, String& err);
  bool startSearch(String& err);
  void cancel();

  bool deleteFingerprint(int id);
  bool deleteAll();
  void blinkLed(uint8_t color = 0x01, uint8_t count = 2);

  FpState getState() { return state; }
  const char* stateName();
  int getActiveId() { return activeId; }
  int getMatchedId() { return matchedId; }
  int getMatchedScore() { return matchedScore; }

  uint8_t capacity() { return sensorCapacity; }

  int templateCount() {
    if (!sensorOk) return 0;
    finger.getTemplateCount();
    return finger.templateCount; 
  }  
  bool slotIsFree(int id);
  bool getUsedIds(bool* usedFlags);  // fills array indexed 1..capacity

private:
  void stepEnroll();
  void stepSearch();
  bool timedOut();

  HardwareSerial fpSerial{2};
  Adafruit_Fingerprint finger{&fpSerial};
  bool sensorOk = false;
  uint8_t sensorCapacity = 200;

  FpState state = FP_IDLE;
  int activeId = 0;
  int matchedId = -1;
  int matchedScore = 0;
  unsigned long stateStart = 0;
  unsigned long lastStep = 0;
};

extern FingerprintManager fp;
