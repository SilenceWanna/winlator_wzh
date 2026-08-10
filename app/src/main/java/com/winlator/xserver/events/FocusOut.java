package com.winlator.xserver.events;

import com.winlator.xserver.Window;

public class FocusOut extends FocusNotify {
    public FocusOut(Detail detail, Window event, Mode mode) {
        super(10, detail, event, mode);
    }
}
