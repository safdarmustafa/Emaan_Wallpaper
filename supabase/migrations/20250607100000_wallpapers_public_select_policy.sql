-- Home loads `wallpapers` with the anon key. If RLS was enabled on this table without a SELECT policy,
-- PostgREST returns zero rows (reels can still work). This restores public read for the wallpaper catalog.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'wallpapers'
  ) THEN
    ALTER TABLE public.wallpapers ENABLE ROW LEVEL SECURITY;

    DROP POLICY IF EXISTS "wallpapers_select_anon_authenticated" ON public.wallpapers;

    CREATE POLICY "wallpapers_select_anon_authenticated"
      ON public.wallpapers
      FOR SELECT
      TO anon, authenticated
      USING (true);
  END IF;
END $$;
