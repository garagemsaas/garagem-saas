import { useEffect, useState } from 'react';
import { photoBlob } from './api';

export default function PrivatePhoto({ path, alt, className }: { path: string; alt: string; className?: string }) {
  const [loaded, setLoaded] = useState({ path: '', url: '', error: '' });
  useEffect(() => {
    let active = true;
    let objectUrl = '';
    photoBlob(path).then(blob => {
      if (active) { objectUrl = URL.createObjectURL(blob); setLoaded({ path, url: objectUrl, error: '' }); }
    }).catch(() => { if (active) setLoaded({ path, url: '', error: 'Não foi possível carregar a foto privada.' }); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [path]);
  return loaded.path !== path ? <span role="status">Carregando foto…</span> : loaded.error ? <p role="alert">{loaded.error}</p> : <img src={loaded.url} alt={alt} className={className} />;
}
