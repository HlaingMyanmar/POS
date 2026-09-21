-- Customer-facing booking rejection copy + outdoor booking availability.
ALTER TABLE service_booking_settings
    ADD COLUMN booking_rejection_message      TEXT         NULL
        AFTER max_photos_per_item,
    ADD COLUMN outdoor_booking_enabled        TINYINT(1)   NOT NULL DEFAULT 1
        AFTER booking_rejection_message,
    ADD COLUMN outdoor_booking_disabled_reason TEXT        NULL
        AFTER outdoor_booking_enabled;

UPDATE service_booking_settings
SET booking_rejection_message = 'သင့် service တောင်းဆိုမှုကို ဆိုင်မှ လက်မခံနိုင်ပါ။ နောက်ထပ်အသေးစိတ်အတွက် ဆိုင်သို့ ဆက်သွယ်ပေးပါ။',
    outdoor_booking_enabled = 1
WHERE booking_rejection_message IS NULL;
