package com.xiaolong.happytalky.core

object Protocol {
    const val BASE = "/happytalky"
    const val CALL_RING = "$BASE/call/ring"
    const val CALL_ANSWER = "$BASE/call/answer"
    const val CALL_DECLINE = "$BASE/call/decline"
    const val CALL_CANCEL = "$BASE/call/cancel"
    const val CALL_BUSY = "$BASE/call/busy"
    const val CALL_END = "$BASE/call/end"
    const val CALL_DISCONNECTED = "$BASE/call/disconnected"
    // Legacy v1 escalation path kept for mixed-version compatibility.
    const val CALL_PRIORITY = "$BASE/call/priority"
    const val CALL_PRIORITY_LOCKED = "$BASE/call/priority-locked"
    const val CALL_AUDIO_PREFIX = "$BASE/call/audio/"
    const val VOICE_PREFIX = "$BASE/voice/"
    const val MESSAGE_PREFIX = "$BASE/message/"
    const val DEVICE_INFO_PREFIX = "$BASE/device-info/"
    const val DEVICE_INFO_REQUEST = "$BASE/device-info/request"
    const val PROXIMITY_START = "$BASE/proximity/start"
    const val PROXIMITY_STOP = "$BASE/proximity/stop"
    const val PROXIMITY_READY = "$BASE/proximity/ready"
    const val PROXIMITY_ERROR = "$BASE/proximity/error"

    const val KEY_ID = "id"
    const val KEY_ORIGIN = "origin"
    const val KEY_CREATED_AT = "createdAt"
    const val KEY_AUDIO = "audio"
    const val KEY_TEXT = "text"
    const val KEY_ROLE = "role"
    const val KEY_MANUFACTURER = "manufacturer"
    const val KEY_MODEL = "model"
    const val KEY_APP_VERSION = "appVersion"
    const val KEY_PROTOCOL_VERSION = "protocolVersion"
    const val KEY_CAPABILITIES = "capabilities"
    const val KEY_PRIORITY_AUTO_ANSWER = "priorityAutoAnswer"
    const val KEY_DEVICE_INFO_UPDATED_AT = "deviceInfoUpdatedAt"

    const val CAPABILITY_TALK_V1 = "talk_v1"
    const val CAPABILITY_CALL_V1 = "call_v1"
    const val CAPABILITY_DEVICE_INFO_V1 = "device_info_v1"
    const val CAPABILITY_CONVERSATION_V1 = "conversation_v1"
    // Legacy v1 capability; new locked behavior uses the capability below.
    const val CAPABILITY_PRIORITY_CALL_V1 = "priority_call_v1"
    const val CAPABILITY_PRIORITY_LOCKED_CALL_V1 =
        "priority_locked_call_v1"
    const val CAPABILITY_TEXT_V1 = "text_v1"
    const val CAPABILITY_BLE_PROXIMITY_V1 = "ble_proximity_v1"
    const val CAPABILITY_BLE_PROXIMITY_V2 = "ble_proximity_v2"

    const val META_ROLE = "happytalky.role"
    const val CAPABILITY_PHONE = "happytalky_phone"
    const val CAPABILITY_WATCH = "happytalky_watch"
    const val ACTION_STATE_CHANGED = "com.xiaolong.happytalky.STATE_CHANGED"
    const val EXTRA_OPEN_INBOX = "happytalky.open_inbox"

    const val CALL_TIMEOUT_MS = 20_000L
    const val RECONNECT_GRACE_MS = 15_000L
    const val RECONNECT_RETRY_MS = 1_500L
    const val MAX_RECORDING_MS = 60_000
    const val MAX_TEXT_LENGTH = 500

    const val AUDIO_SAMPLE_RATE = 16_000
}
