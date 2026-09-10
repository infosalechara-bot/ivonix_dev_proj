-- PULSE device credentials expire and require rotation.
ALTER TABLE public.key_devices ADD COLUMN IF NOT EXISTS credential_expires_at timestamptz;
UPDATE public.key_devices SET credential_expires_at = COALESCE(credential_expires_at, created_at + interval '30 days') WHERE credential_expires_at IS NULL;
CREATE INDEX IF NOT EXISTS key_devices_active_expiry_idx ON public.key_devices (status, credential_expires_at);
