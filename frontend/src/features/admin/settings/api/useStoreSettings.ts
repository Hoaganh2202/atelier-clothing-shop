import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { settingsKeys } from './queryKeys';
import {
  getStoreSettings,
  updateStoreSettings,
  type StoreSettingsData,
  type StoreSettingsPayload,
} from './settings.api';

export function useStoreSettings(options?: { enabled?: boolean }) {
  return useQuery<StoreSettingsData, Error>({
    queryKey: settingsKeys.store(),
    queryFn: getStoreSettings,
    staleTime: 60 * 1000,
    enabled: options?.enabled,
  });
}

export function useUpdateStoreSettings() {
  const queryClient = useQueryClient();

  return useMutation<StoreSettingsData, Error, StoreSettingsPayload>({
    mutationFn: updateStoreSettings,
    onSuccess: (data) => {
      queryClient.setQueryData(settingsKeys.store(), data);
      queryClient.invalidateQueries({ queryKey: settingsKeys.store() });
    },
  });
}
