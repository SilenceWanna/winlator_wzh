/*
 * Compatibility bridge for X servers that expose the core keyboard map but
 * not the XKEYBOARD extension. This library is loaded only by Box64 guests.
 */

typedef struct _XDisplay Display;
typedef unsigned long KeySym;
typedef unsigned char KeyCode;

enum {
    LOCK_MASK = 1 << 1,
    CONTROL_MASK = 1 << 2
};

extern KeySym XKeycodeToKeysym(Display *display, KeyCode keycode, int column);

__attribute__((visibility("default")))
KeySym XkbKeycodeToKeysym(Display *display, KeyCode keycode, int group, int level) {
    if (display == (Display *)0 || group < 0 || level < 0) return 0;
    return XKeycodeToKeysym(display, keycode, group * 2 + level);
}

static char to_control(char value) {
    if ((value >= '@' && value < 0x7f) || value == ' ') return value & 0x1f;
    if (value == '2') return 0;
    if (value >= '3' && value <= '7') return value - ('3' - 0x1b);
    if (value == '8') return 0x7f;
    if (value == '/') return '_' & 0x1f;
    return value;
}

__attribute__((visibility("default")))
int XkbTranslateKeySym(Display *display, KeySym *keysym, unsigned int modifiers,
                       char *buffer, int buffer_size, int *extra) {
    (void)display;
    if (extra != (int *)0) *extra = 0;
    if (keysym == (KeySym *)0 || *keysym > 0xff) {
        if (buffer != (char *)0 && buffer_size > 0) buffer[0] = '\0';
        return 0;
    }

    char value = (char)(*keysym & 0xff);
    if ((modifiers & LOCK_MASK) && value >= 'a' && value <= 'z') {
        value -= 'a' - 'A';
        *keysym = (KeySym)(unsigned char)value;
    }
    if (modifiers & CONTROL_MASK) value = to_control(value);

    if (buffer != (char *)0 && buffer_size > 0) {
        buffer[0] = value;
        if (buffer_size > 1) buffer[1] = '\0';
    }
    return 1;
}
