import { useEffect, useState } from 'react';
import { photoBlob } from './api';


export function useImagemEmpresa(path?: string) {
  const [state, setState] = useState<{ path: string; url: string }>();
  useEffect(() => {
    if (!path) return;
    let active = true; let url: string | undefined;
    photoBlob(path).then(blob => {
      if (active) { url = URL.createObjectURL(blob); setState({ path, url }); }
    }).catch(() => { /* O nome da empresa continua visível se a imagem estiver indisponível. */ });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [path]);
  return state?.path === path ? state?.url : undefined;
}
