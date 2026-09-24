/** How far a floating layer sits from what it is anchored to, in px. A tooltip has to clear
 *  the cursor as well as its target, so it floats further than a menu opened by a click that
 *  already landed on that target; submenus and selects hug theirs. Four call sites carried
 *  three of these numbers as bare literals with nothing to say which difference was meant. */
export const OVERLAY_OFFSET = {
  menu: 6,
  submenu: 4,
  select: 4,
  tooltip: 8,
} as const
