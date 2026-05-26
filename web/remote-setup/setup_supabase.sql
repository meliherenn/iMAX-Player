-- setup_supabase.sql
-- Run this SQL in your Supabase SQL Editor to prepare the database for iMAX Player Remote Setup.

-- 1. Create the pairings table
CREATE TABLE IF NOT EXISTS public.tv_pairings (
    pairing_code text NOT NULL CONSTRAINT tv_pairings_pkey PRIMARY KEY,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

-- 2. Enable Row Level Security (RLS)
ALTER TABLE public.tv_pairings ENABLE ROW LEVEL SECURITY;

-- 3. Create RLS Policies for Anonymous Client Access (Anon Key)
CREATE POLICY "Allow anonymous inserts" ON public.tv_pairings
    FOR INSERT TO anon WITH CHECK (true);

CREATE POLICY "Allow anonymous selects" ON public.tv_pairings
    FOR SELECT TO anon USING (true);

CREATE POLICY "Allow anonymous updates" ON public.tv_pairings
    FOR UPDATE TO anon USING (true) WITH CHECK (true);

-- 4. Enable Realtime updates on this table
ALTER PUBLICATION supabase_realtime ADD TABLE public.tv_pairings;

-- 5. Auto-Cleanup Function & Trigger (deletes rows older than 10 minutes)
-- This avoids database bloating and cleans up expired codes on every new code request.
CREATE OR REPLACE FUNCTION public.clean_expired_pairings()
RETURNS trigger AS $$
BEGIN
    DELETE FROM public.tv_pairings 
    WHERE created_at < (now() - interval '10 minutes');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER trigger_clean_expired_pairings
    BEFORE INSERT ON public.tv_pairings
    FOR EACH ROW
    EXECUTE FUNCTION public.clean_expired_pairings();
