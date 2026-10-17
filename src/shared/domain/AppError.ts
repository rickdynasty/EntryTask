export type ErrorKind =
  | 'configuration'
  | 'network'
  | 'http'
  | 'business'
  | 'protocol'
  | 'cancelled'
  | 'timeout'
  | 'unavailable';

export class AppError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly kind: ErrorKind = 'protocol',
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export function normalizeError(error: unknown): AppError {
  if (error instanceof AppError) {
    return error;
  }
  return new AppError('UNEXPECTED_ERROR', '操作未完成，请重试', 'network');
}
