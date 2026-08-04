/**
 * English translation (mobile).
 * Mirrors zh-CN.ts key structure; shares the same key namespace as the backend MessageSource.
 */
export default {
  auth: {
    error: {
      tokenExpired: 'Session expired, please login again',
      disabled: 'Account disabled',
      tooFrequent: 'Login attempts too frequent',
      loginFailed: 'The account or credentials are invalid',
      permissionDenied: 'The required permission is missing',
      unauthenticated: 'Authentication is required',
    },
  },
  common: {
    error: {
      internal: 'Request failed',
      network: 'Network request failed',
      notFound: 'The requested resource was not found',
      rateLimited: 'Too many requests. Try again later',
    },
  },
};