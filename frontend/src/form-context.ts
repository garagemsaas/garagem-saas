import { createContext } from 'react';

export const FormErrors = createContext<Record<string, string>>({});
