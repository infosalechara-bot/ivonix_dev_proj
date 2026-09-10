ALTER TABLE public.meet_recordings ADD COLUMN IF NOT EXISTS transcription_started_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_meet_recordings_processing ON public.meet_recordings(status,transcription_started_at);
INSERT INTO storage.buckets(id,name,public) VALUES('pulse-assets','pulse-assets',false),('recordings','recordings',false) ON CONFLICT(id) DO UPDATE SET public=false;
