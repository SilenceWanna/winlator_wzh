package com.winlator.xserver.events;

import com.winlator.xconnector.XOutputStream;
import com.winlator.xconnector.XStreamLock;
import com.winlator.xserver.Window;

import java.io.IOException;

public abstract class FocusNotify extends Event {
    public enum Detail {ANCESTOR, VIRTUAL, INFERIOR, NONLINEAR, NONLINEAR_VIRTUAL, POINTER, POINTER_ROOT, NONE}
    public enum Mode {NORMAL, GRAB, UNGRAB, WHILE_GRABBED}
    private final Detail detail;
    private final Window event;
    private final Mode mode;

    protected FocusNotify(int code, Detail detail, Window event, Mode mode) {
        super(code);
        this.detail = detail;
        this.event = event;
        this.mode = mode;
    }

    @Override
    public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(code);
            outputStream.writeByte((byte)detail.ordinal());
            outputStream.writeShort(sequenceNumber);
            outputStream.writeInt(event.id);
            outputStream.writeByte((byte)mode.ordinal());
            outputStream.writePad(23);
        }
    }
}
