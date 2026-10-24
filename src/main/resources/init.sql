INSERT INTO users (login, password, role, can_edit, can_reports) VALUES
    ('admin', 'admin', 'ADMIN', TRUE, TRUE),
    ('operator', '1111', 'USER', TRUE, TRUE),
    ('guest', 'guest', 'USER', FALSE, FALSE);

INSERT INTO settings (name, val, description) VALUES
    ('airport_name', 'Аеропорт Ніжин', 'Назва аеропорту (заголовок програми)'),
    ('log_keep_days', '90', 'Скільки днів зберігати журнал');

INSERT INTO airlines (name, country) VALUES
    ('МАУ', 'Україна'),
    ('LOT', 'Польща'),
    ('Lufthansa', 'Німеччина'),
    ('Wizz Air', 'Угорщина'),
    ('Turkish Airlines', 'Туреччина');

-- airline_id follows the insertion order of airlines above (1 - МАУ, 2 - LOT, ...)
INSERT INTO flights (flight_no, airline_id, type, city, sched_time, gate, status) VALUES
    ('PS101', 1, 'Виліт', 'Варшава', '2026-10-24 08:30:00', 'A1', 'Відбув'),
    ('LO752', 2, 'Приліт', 'Варшава', '2026-10-24 10:15:00', 'A2', 'Прибув'),
    ('LH1491', 3, 'Виліт', 'Франкфурт', '2026-10-24 12:00:00', 'B1', 'Затримано'),
    ('W61234', 4, 'Виліт', 'Будапешт', '2026-10-24 14:40:00', 'B3', 'Посадка'),
    ('TK458', 5, 'Приліт', 'Стамбул', '2026-10-24 16:05:00', 'C1', 'За розкладом'),
    ('PS205', 1, 'Виліт', 'Стамбул', '2026-10-25 07:50:00', 'A3', 'За розкладом'),
    ('LH1492', 3, 'Приліт', 'Франкфурт', '2026-10-25 11:20:00', 'B2', 'За розкладом'),
    ('W61235', 4, 'Приліт', 'Будапешт', '2026-10-25 18:00:00', 'B3', 'Скасовано');
