alter table public.reels drop column if exists views;
alter table public.reels drop column if exists likes;

delete from public.otp_challenges where expires_at < now();
