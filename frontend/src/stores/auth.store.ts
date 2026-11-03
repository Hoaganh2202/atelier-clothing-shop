import { create } from 'zustand';
import type { components } from '@/api/schema';
import apiClient from '@/api/client';

export type UserSummary = components['schemas']['UserSummaryResponse'] & {
  gender?: 'MALE' | 'FEMALE' | 'OTHER' | null;
  birthDate?: string | null;
};

interface AuthState {
  accessToken: string | null;
  user: UserSummary | null;
  isInitializing: boolean;
  setAuth: (token: string, user: UserSummary) => void;
  setUser: (user: UserSummary) => void;
  logout: () => void;
  initSession: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  // TODO: Move to HttpOnly cookie once refresh token is implemented in PR 2
  accessToken: typeof window !== 'undefined' ? sessionStorage.getItem('accessToken') : null,
  user: null,
  isInitializing: true,

  setAuth: (token: string, user: UserSummary) => {
    // TODO: Move to HttpOnly cookie once refresh token is implemented in PR 2
    sessionStorage.setItem('accessToken', token);
    set({ accessToken: token, user, isInitializing: false });
  },

  setUser: (user: UserSummary) => {
    set({ user });
  },

  logout: () => {
    // TODO: Move to HttpOnly cookie once refresh token is implemented in PR 2
    sessionStorage.removeItem('accessToken');
    set({ accessToken: null, user: null, isInitializing: false });
  },

  initSession: async () => {
    const token = sessionStorage.getItem('accessToken');
    if (!token) {
      set({ accessToken: null, user: null, isInitializing: false });
      return;
    }

    try {
      const response = await apiClient.get<UserSummary>('/auth/me');
      set({ accessToken: token, user: response.data, isInitializing: false });
    } catch {
      sessionStorage.removeItem('accessToken');
      set({ accessToken: null, user: null, isInitializing: false });
    }
  },
}));
