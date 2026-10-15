-- Product catalog = the app's transaction menu (reference data for every environment).
-- Money transactions run in the EkoConnect widget and are switched off in production
-- (ADR-0004); the catalog still drives history types, dashboards and commissions.
CREATE TABLE product
(
    id                    INT          NOT NULL,
    label                 VARCHAR(80)  NOT NULL,
    icon                  VARCHAR(40)  NULL,
    behavior              INT          NOT NULL,
    visible               BOOLEAN      NOT NULL,
    category              VARCHAR(40)  NULL,
    description           VARCHAR(200) NULL,
    tx_typeid             INT          NOT NULL,
    history_label         VARCHAR(80)  NULL,
    group_interaction_ids VARCHAR(200) NULL,
    important             BOOLEAN      NOT NULL,
    sort_order            INT          NOT NULL,
    PRIMARY KEY (id)
);

-- behavior: 1 normal, 6 group (widget shows children), 7 grid (app shows children)
INSERT INTO product (id, label, icon, behavior, visible, category, description, tx_typeid, history_label, group_interaction_ids, important, sort_order) VALUES
 (243, 'Money Transfer',          'money-note',   1, TRUE,  'Banking',  'Send money to any bank account',          221, 'Money Transfer', NULL,      FALSE, 10),
 (252, 'AePS Cashout',            'fingerprint',  1, TRUE,  'Banking',  'Aadhaar-enabled cash withdrawal',         225, 'AePS',           NULL,      FALSE, 20),
 (253, 'Aadhaar Pay',             'fingerprint',  1, TRUE,  'Banking',  'Accept payments with Aadhaar',            226, 'Aadhaar Pay',    NULL,      FALSE, 30),
 (244, 'Recharge & Bill Payment', 'invoice',      7, TRUE,  'Payments', 'Electricity, water, mobile and more',     244, 'Bill Payment',   '64,249',  FALSE, 40),
 (260, 'QR Payment',              'qrcode',       1, TRUE,  'Payments', 'Collect payments by QR code',             860, 'QR Payment',     NULL,      FALSE, 50),
 (261, 'Indo-Nepal Transfer',     'nepal',        1, TRUE,  'Banking',  'Send money to Nepal',                     231, 'Indo-Nepal',     NULL,      FALSE, 60),
 (262, 'Travel Booking',          'flight',       1, TRUE,  'Services', 'Train, flight and bus tickets',           290, 'Travel',         NULL,      FALSE, 70),
 (64,  'Mobile Recharge',         'phone',        1, FALSE, 'Payments', NULL,                                       64, 'Recharge',       NULL,      FALSE, 41),
 (249, 'Electricity',             'flash-on',     1, FALSE, 'Payments', NULL,                                        0, NULL,             NULL,      FALSE, 42),
 (491, 'Load E-value',            'account-balance-wallet', 1, FALSE, NULL, NULL,                                    0, NULL,             NULL,      FALSE, 90),
 (536, 'Manage My Account',       'person',       6, TRUE,  NULL,       'Mobile number, PIN and account settings',   0, NULL,             '640,736', FALSE, 95),
 (640, 'Update Registered Mobile','phone',        1, FALSE, NULL,       NULL,                                        0, NULL,             NULL,      FALSE, 96),
 (736, 'Set PIN',                 'lock',         1, FALSE, NULL,       NULL,                                        0, NULL,             NULL,      FALSE, 97);
