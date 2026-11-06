/**
 * Centralized environment variable access for Emaan Edge Functions.
 * Reads secrets via Deno.env.get() only — never hardcodes values.
 */

/** Reads a required environment variable; throws when missing. */
export function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}
