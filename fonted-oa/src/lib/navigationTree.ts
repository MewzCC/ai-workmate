import type { OaMenuItem } from '@/types/oa';

export function findMenu(menuId: string, menus: OaMenuItem[]): OaMenuItem | undefined {
  for (const menu of menus) {
    if (menu.id === menuId) return menu;
    const child = menu.children?.length ? findMenu(menuId, menu.children) : undefined;
    if (child) return child;
  }
  return undefined;
}

export function firstPage(menus: OaMenuItem[]): OaMenuItem | undefined {
  for (const menu of menus) {
    if (menu.type === 'page') return menu;
    const child = firstPage(menu.children || []);
    if (child) return child;
  }
  return undefined;
}

export function flattenPages(menus: OaMenuItem[]): OaMenuItem[] {
  return menus.flatMap((menu) => [
    ...(menu.type === 'page' ? [menu] : []),
    ...flattenPages(menu.children || []),
  ]);
}
