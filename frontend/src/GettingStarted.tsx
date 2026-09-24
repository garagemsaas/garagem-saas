import { Drawer } from './ui';
import Help from './Help';
import type { Page } from './navigation';
import type { Role } from './model';
export default function GettingStarted({ close }: { role: Role; close: () => void; navigate: (page: Page) => void; recovery: () => void }) {
 return <Drawer title="Como podemos ajudar?" close={close}><Help /></Drawer>;
}
