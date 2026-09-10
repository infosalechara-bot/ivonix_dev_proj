-- PULSE controlled storage foundation. Intentionally NOT applied to production by the hardening pass.
-- Object paths must begin with the tenant UUID: <organization_id>/<domain>/<object>.

INSERT INTO storage.buckets (id, name, public, file_size_limit)
VALUES ('pulse-assets', 'pulse-assets', false, 52428800)
ON CONFLICT (id) DO UPDATE SET public = false, file_size_limit = 52428800;

DROP POLICY IF EXISTS pulse_assets_select ON storage.objects;
CREATE POLICY pulse_assets_select ON storage.objects
FOR SELECT TO authenticated
USING (
  bucket_id = 'pulse-assets'
  AND (storage.foldername(name))[1] = (select auth.jwt() ->> 'organization_id')
  AND public.is_org_member((storage.foldername(name))[1]::uuid)
);

DROP POLICY IF EXISTS pulse_assets_insert ON storage.objects;
CREATE POLICY pulse_assets_insert ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (
  bucket_id = 'pulse-assets'
  AND (storage.foldername(name))[1] = (select auth.jwt() ->> 'organization_id')
  AND public.is_org_member((storage.foldername(name))[1]::uuid)
);

DROP POLICY IF EXISTS pulse_assets_update ON storage.objects;
CREATE POLICY pulse_assets_update ON storage.objects
FOR UPDATE TO authenticated
USING (
  bucket_id = 'pulse-assets'
  AND (storage.foldername(name))[1] = (select auth.jwt() ->> 'organization_id')
  AND public.is_org_member((storage.foldername(name))[1]::uuid)
)
WITH CHECK (
  bucket_id = 'pulse-assets'
  AND (storage.foldername(name))[1] = (select auth.jwt() ->> 'organization_id')
  AND public.is_org_member((storage.foldername(name))[1]::uuid)
);

DROP POLICY IF EXISTS pulse_assets_delete ON storage.objects;
CREATE POLICY pulse_assets_delete ON storage.objects
FOR DELETE TO authenticated
USING (
  bucket_id = 'pulse-assets'
  AND (storage.foldername(name))[1] = (select auth.jwt() ->> 'organization_id')
  AND public.is_org_member((storage.foldername(name))[1]::uuid)
);
