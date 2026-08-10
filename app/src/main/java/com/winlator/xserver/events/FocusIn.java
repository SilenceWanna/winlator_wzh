package com.winlator.xserver.events;

import com.winlator.xserver.Window;

public class FocusIn extends FocusNotify {
    public FocusIn(Detail detail, Window event, Mode mode) {
        super(9, detail, event, mode);
    }
}
