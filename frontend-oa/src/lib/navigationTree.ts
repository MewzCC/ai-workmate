import type { OaMenuItem } from '@/types/oa';
import type { NavigationRoute } from './navigationApi';

export function toMenuItem(route: NavigationRoute): OaMenuItem {
  return {
    id: route.routeKey, parentId: route.parentKey, name: route.name,
    type: route.routeType.toLowerCase() as OaMenuItem['type'],
    icon: route.icon, path: route.path, componentKey: route.componentKey,
    permissionCode: route.permissionCode, sort: route.sortOrder, visible: true,
    children: route.children?.length ? route.children.map(toMenuItem) : undefined,
  };
}

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
