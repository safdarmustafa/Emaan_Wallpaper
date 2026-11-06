/**
 * Centralized error codes and messages for Emaan Edge Functions.
 * Every user-facing error must come from here so clients can rely on
 * stable, machine-readable codes.
 */

export const ERROR_CODES = {
  PHONE_INVALID: "PHONE_INVALID",
  METHOD_NOT_ALLOWED: "METHOD_NOT_ALLOWED",
  BAD_REQUEST: "BAD_REQUEST",
  INTERNAL_SERVER_ERROR: "INTERNAL_SERVER_ERROR",
} as const;

export type ErrorCode = (typeof ERROR_CODES)[keyof typeof ERROR_CODES];

interface ErrorDefinition {
  /** Human-readable message safe to show to end users. */
  message: string;
  /** HTTP status the error should be served with. */
  httpStatus: number;
}

export const ERROR_DEFINITIONS: Record<ErrorCode, ErrorDefinition> = {
  [ERROR_CODES.PHONE_INVALID]: {
    message: "Please enter a valid Indian mobile number.",
    httpStatus: 400,
  },
  [ERROR_CODES.METHOD_NOT_ALLOWED]: {
    message: "This HTTP method is not allowed on this endpoint.",
    httpStatus: 405,
  },
  [ERROR_CODES.BAD_REQUEST]: {
    message: "The request is malformed or missing required fields.",
    httpStatus: 400,
  },
  [ERROR_CODES.INTERNAL_SERVER_ERROR]: {
    message: "Something went wrong on our side. Please try again later.",
    httpStatus: 500,
  },
};
