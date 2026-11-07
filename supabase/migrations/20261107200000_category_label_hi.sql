alter table public.categories
  add column if not exists label_hi text;

update public.categories
set label_hi = case lower(trim(name))
  when 'kaaba' then 'काबा'
  when 'madinah' then 'मदीना'
  when 'quran' then 'कुरान'
  when 'mosque' then 'मस्जिद'
  when 'islamic_quotes' then 'इस्लामिक कोट्स'
  when 'ramadan' then 'रमज़ान'
  when 'allah' then 'अल्लाह'
  when 'general' then 'सामान्य'
  when 'madni_channel' then 'मदनी चैनल'
  when 'dua' then 'दुआ'
  when 'prayer' then 'नमाज़'
  when 'makkah' then 'मक्का'
  when 'islamic_night' then 'इस्लामिक रात'
  when 'peaceful' then 'सुखद'
  when 'architecture' then 'आर्किटेक्चर'
  when 'nasheed' then 'नशीद'
  when 'islamic' then 'इस्लामिक'
  when 'adhan' then 'अज़ान'
  else label_hi
end
where label_hi is null or btrim(label_hi) = '';
