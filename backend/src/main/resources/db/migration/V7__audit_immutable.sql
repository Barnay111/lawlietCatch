-- V7: журнал аудита неизменяем на уровне БД. Запрещаем UPDATE, DELETE и TRUNCATE даже пользователю приложения.
-- Дополнительно в эксплуатации: приложению выдаются только INSERT и SELECT на audit_log (см. security/SECURITY.md).
CREATE OR REPLACE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log: изменение и удаление записей запрещены';
END;
$$ LANGUAGE plpgsql;

-- FOR EACH STATEMENT: срабатывает даже когда под запрос не попало ни одной строки.
CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_immutable();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_immutable();
