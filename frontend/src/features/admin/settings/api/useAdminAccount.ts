import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { settingsKeys } from './queryKeys';
import {
  changePassword,
  getAdminAccount,
  updateAdminAccount,
  uploadLogo,
  type AdminAccountData,
  type ChangePasswordPayload,
  type LogoUploadResult,
  type UpdateAdminAccountPayload,
} from './settings.api';

export function useAdminAccount() {
  return useQuery<AdminAccountData, Error>({
    queryKey: settingsKeys.account(),
    queryFn: getAdminAccount,
    staleTime: 60 * 1000,
  });
}

export function useUpdateAdminAccount() {
  const queryClient = useQueryClient();

  return useMutation<AdminAccountData, Error, UpdateAdminAccountPayload>({
    mutationFn: updateAdminAccount,
    onSuccess: (data) => {
      queryClient.setQueryData(settingsKeys.account(), data);
      queryClient.invalidateQueries({ queryKey: settingsKeys.account() });
    },
  });
}

export function useChangePassword() {
  return useMutation<{ message: string }, Error, ChangePasswordPayload>({
    mutationFn: changePassword,
  });
}

export function useUploadLogo() {
  return useMutation<LogoUploadResult, Error, File>({
    mutationFn: uploadLogo,
  });
}
