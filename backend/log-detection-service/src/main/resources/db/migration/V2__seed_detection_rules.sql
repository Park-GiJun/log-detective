INSERT INTO detection.detection_rules (id, name, enabled, severity, config) VALUES
('R001', 'BruteForceLoginRule',      TRUE, 'HIGH',   '{"window_seconds":300,"threshold":10}'::jsonb),
('R002', 'SqlInjectionPatternRule',  TRUE, 'HIGH',   '{"patterns":["UNION SELECT","OR 1=1","--","; DROP","xp_cmdshell"]}'::jsonb),
('R003', 'ErrorRateSpikeRule',       TRUE, 'MEDIUM', '{"baseline_minutes":60,"multiplier":3.0}'::jsonb),
('R004', 'OffHourAccessRule',        TRUE, 'MEDIUM', '{"start_hour":0,"end_hour":5}'::jsonb),
('R005', 'GeoAnomalyRule',           TRUE, 'HIGH',   '{"window_seconds":3600}'::jsonb),
('R006', 'RareEventRule',            TRUE, 'LOW',    '{"lookback_days":30}'::jsonb);
