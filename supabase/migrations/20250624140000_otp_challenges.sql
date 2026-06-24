-- OTP challenges for server-side verification (edge functions use service role).
CREATE TABLE IF NOT EXISTS public.otp_challenges (
    phone_number TEXT PRIMARY KEY,
    otp_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.otp_challenges ENABLE ROW LEVEL SECURITY;

-- No client policies: only service role (edge functions) may read/write.
