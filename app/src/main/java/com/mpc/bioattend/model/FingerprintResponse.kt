package com.mpc.bioattend.model

import com.google.gson.annotations.SerializedName

data class FingerprintDataDetails(
    @SerializedName("fingerprint_id") val fingerprintId: Int? = null,
    @SerializedName("id") val id: Int? = null,
    @SerializedName("slot_id") val slotId: Int? = null,
    @SerializedName("finished") val finished: Boolean = false,
    @SerializedName("matched") val matched: Boolean = false,
    @SerializedName("state") val state: String? = null,
    @SerializedName("message") val message: String? = null
)

data class FingerprintResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("matched") val matched: Boolean = false,
    @SerializedName("fingerprint_id") val fingerprintId: Int? = null,
    @SerializedName("id") val id: Int? = null,
    @SerializedName("slot_id") val slotId: Int? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val dataDetails: FingerprintDataDetails? = null
) {
    val effectiveFingerprintId: Int?
        get() {
            if (fingerprintId != null && fingerprintId > 0) return fingerprintId
            if (id != null && id > 0) return id
            if (slotId != null && slotId > 0) return slotId
            if (dataDetails?.fingerprintId != null && dataDetails.fingerprintId > 0) return dataDetails.fingerprintId
            if (dataDetails?.id != null && dataDetails.id > 0) return dataDetails.id
            if (dataDetails?.slotId != null && dataDetails.slotId > 0) return dataDetails.slotId
            return null
        }

    val isEnrollSuccess: Boolean
        get() {
            val st = (status ?: dataDetails?.state ?: "").uppercase(java.util.Locale.ROOT)
            val msg = (message ?: dataDetails?.message ?: "").uppercase(java.util.Locale.ROOT)

            if (st.contains("FAIL") || st.contains("ERROR") || st.contains("CANCEL") || st.contains("TIMEOUT")) return false
            if (msg.contains("FAIL") || msg.contains("ERROR") || msg.contains("CANCEL") || msg.contains("TIMEOUT")) return false

            if (st == "FP_ENROLL_SUCCESS" || st == "SUCCESS" || st == "ENROLL_SUCCESS" || st == "COMPLETE" || st == "OK") return true
            if (msg.contains("SUCCESS") || msg.contains("ENROLLED") || msg.contains("COMPLETE")) return true

            if (dataDetails?.finished == true && effectiveFingerprintId != null && effectiveFingerprintId!! > 0) {
                return true
            }

            return false
        }

    val isTrueMatch: Boolean
        get() {
            if (matched || dataDetails?.matched == true) return true

            val msg = message?.lowercase(java.util.Locale.ROOT) ?: ""
            val st = status?.lowercase(java.util.Locale.ROOT) ?: ""

            // Exclude non-match, waiting, or error status messages
            if (msg.contains("no finger") || msg.contains("waiting") || msg.contains("not found") || msg.contains("no match") || msg.contains("error") || msg.contains("failed")) {
                return false
            }
            if (st.contains("waiting") || st.contains("no_match") || st.contains("failed") || st.contains("error")) {
                return false
            }

            // If effectiveFingerprintId is present (> 0), it is a valid match
            if (effectiveFingerprintId != null && effectiveFingerprintId!! > 0) {
                return true
            }

            if (st.contains("match") || st.contains("found") || st.contains("success")) return true
            if (msg.contains("match") || msg.contains("found") || msg.contains("success")) return true

            return false
        }
}

data class OledShowRequest(
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String
)

data class EnrollRequest(
    @SerializedName("fingerprint_id") val fingerprintId: Int
)

data class FingerprintListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: FingerprintListData? = null,
    @SerializedName("message") val message: String? = null
)

data class FingerprintListData(
    @SerializedName("used_ids") val usedIds: List<Int>? = emptyList(),
    @SerializedName("used_count") val usedCount: Int = 0,
    @SerializedName("available_count") val availableCount: Int = 0
)
