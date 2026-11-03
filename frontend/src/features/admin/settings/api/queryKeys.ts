export const settingsKeys = {
  all: ['settings'] as const,
  store: () => [...settingsKeys.all, 'store'] as const,
  account: () => [...settingsKeys.all, 'account'] as const,
};
