-- Admin-managed categories for wallpapers, reels and ringtones.
-- `name` is the slug stored in wallpapers/reels/ringtones.category
-- (ringtones historically use capitalised values, so clients match case-insensitively).

ALTER TABLE public.categories
  ADD COLUMN IF NOT EXISTS label        text,
  ADD COLUMN IF NOT EXISTS content_type text    NOT NULL DEFAULT 'wallpaper',
  ADD COLUMN IF NOT EXISTS sort_order   integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS is_active    boolean NOT NULL DEFAULT true;

UPDATE public.categories SET name = lower(trim(name)) WHERE name <> lower(trim(name));

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
     WHERE conrelid = 'public.categories'::regclass
       AND conname = 'categories_content_type_check'
  ) THEN
    ALTER TABLE public.categories
      ADD CONSTRAINT categories_content_type_check
      CHECK (content_type IN ('wallpaper', 'reel', 'ringtone'));
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
     WHERE conrelid = 'public.categories'::regclass
       AND conname = 'categories_content_type_name_key'
  ) THEN
    ALTER TABLE public.categories
      ADD CONSTRAINT categories_content_type_name_key UNIQUE (content_type, name);
  END IF;
END $$;

-- Seed exactly what the app hardcoded until now (same order and labels).
INSERT INTO public.categories (name, label, content_type, sort_order)
VALUES
  ('kaaba',          'Kaaba',          'wallpaper', 1),
  ('madinah',        'Madinah',        'wallpaper', 2),
  ('quran',          'Quran',          'wallpaper', 3),
  ('mosque',         'Mosque',         'wallpaper', 4),
  ('islamic_quotes', 'Islamic Quotes', 'wallpaper', 5),
  ('ramadan',        'Ramadan',        'wallpaper', 6),
  ('allah',          'Allah',          'wallpaper', 7),
  ('general',        'General',        'wallpaper', 8),
  ('ramadan',        'Ramadan',        'reel', 1),
  ('dua',            'Dua',            'reel', 2),
  ('quran',          'Quran',          'reel', 3),
  ('prayer',         'Prayer',         'reel', 4),
  ('makkah',         'Makkah',         'reel', 5),
  ('madinah',        'Madinah',        'reel', 6),
  ('mosque',         'Mosque',         'reel', 7),
  ('islamic_night',  'Islamic Night',  'reel', 8),
  ('peaceful',       'Peaceful',       'reel', 9),
  ('architecture',   'Architecture',   'reel', 10)
ON CONFLICT (content_type, name) DO UPDATE
  SET label      = COALESCE(public.categories.label, EXCLUDED.label),
      sort_order = EXCLUDED.sort_order;

-- Ringtone categories: whatever is already in use, ordered by popularity.
INSERT INTO public.categories (name, label, content_type, sort_order)
SELECT slug,
       initcap(replace(slug, '_', ' ')),
       'ringtone',
       row_number() OVER (ORDER BY cnt DESC, slug)
  FROM (
    SELECT lower(trim(category)) AS slug, count(*) AS cnt
      FROM public.ringtones
     WHERE category IS NOT NULL AND trim(category) <> ''
     GROUP BY 1
  ) used
ON CONFLICT (content_type, name) DO NOTHING;

UPDATE public.categories
   SET label = initcap(replace(name, '_', ' '))
 WHERE label IS NULL OR trim(label) = '';

ALTER TABLE public.categories ALTER COLUMN label SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_categories_type_order
  ON public.categories (content_type, sort_order);

-- App (anon key, no Supabase Auth): read active categories only.
-- Admin panel (Supabase Auth → authenticated): full management.
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS categories_select_active_anon ON public.categories;
CREATE POLICY categories_select_active_anon
  ON public.categories FOR SELECT
  TO anon
  USING (is_active);

DROP POLICY IF EXISTS categories_insert_authenticated ON public.categories;
CREATE POLICY categories_insert_authenticated
  ON public.categories FOR INSERT
  TO authenticated
  WITH CHECK (true);

DROP POLICY IF EXISTS categories_update_authenticated ON public.categories;
CREATE POLICY categories_update_authenticated
  ON public.categories FOR UPDATE
  TO authenticated
  USING (true)
  WITH CHECK (true);

DROP POLICY IF EXISTS categories_delete_authenticated ON public.categories;
CREATE POLICY categories_delete_authenticated
  ON public.categories FOR DELETE
  TO authenticated
  USING (true);
