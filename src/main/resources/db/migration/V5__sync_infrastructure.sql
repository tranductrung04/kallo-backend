CREATE SEQUENCE sync_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION touch_sync()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.sync_seq := nextval('sync_seq');
  NEW.server_updated_at := clock_timestamp();
  RETURN NEW;
END;
$$;
