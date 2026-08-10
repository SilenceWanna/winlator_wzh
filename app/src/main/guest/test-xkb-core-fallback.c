#include "xkb-core-fallback.c"

static int last_column = -1;

KeySym XKeycodeToKeysym(Display *display, KeyCode keycode, int column) {
    (void)display;
    last_column = column;
    return (KeySym)keycode + (KeySym)column;
}

int main(void) {
    Display *display = (Display *)1;
    if (XkbKeycodeToKeysym(display, 38, 1, 1) != 41 || last_column != 3) return 1;
    if (XkbKeycodeToKeysym((Display *)0, 38, 0, 0) != 0) return 2;

    KeySym keysym = 'a';
    char buffer[2] = {0, 0};
    int extra = -1;
    if (XkbTranslateKeySym(display, &keysym, LOCK_MASK, buffer, 2, &extra) != 1) return 3;
    if (keysym != 'A' || buffer[0] != 'A' || buffer[1] != '\0' || extra != 0) return 4;

    keysym = 'A';
    if (XkbTranslateKeySym(display, &keysym, CONTROL_MASK, buffer, 1, (int *)0) != 1) return 5;
    if (buffer[0] != 1) return 6;

    keysym = 0x100;
    buffer[0] = 'x';
    if (XkbTranslateKeySym(display, &keysym, 0, buffer, 1, &extra) != 0) return 7;
    if (buffer[0] != '\0') return 8;
    return 0;
}
