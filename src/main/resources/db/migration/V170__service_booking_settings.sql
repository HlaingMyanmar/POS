-- Singleton settings for booking / outdoor / service-job customer-facing policies.
CREATE TABLE service_booking_settings (
    id                              INT            NOT NULL AUTO_INCREMENT,
    outdoor_transportation_notice   TEXT           NULL,
    outdoor_transportation_fee      DECIMAL(15, 2) NULL,
    updated_at                      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                    ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

INSERT INTO service_booking_settings (outdoor_transportation_notice, outdoor_transportation_fee)
VALUES (
    'အိမ်အရောက်ဝန်ဆောင်မှုအတွက် သယ်ယူခကျသင့်ပါသည်။ ခရီးအကွာအဝေးအလိုက် ဆိုင်သတ်မှတ်နှုန်းအတိုင်း ကောက်ခံပါမည်။',
    NULL
);
