package org.mushroomrecompiled;

final class NativeBridge {
    static final int LOAD_ROM=1, RESET=2, SAVE=3, LOAD_STATE=4, QUIT=5, CAPTURE=7;
    static final int READY=1, LOADED=2, CONTROLLER=4;
    private NativeBridge() {}
    static native void beginSession();
    static native boolean command(int operation, String path, int slot);
    static native void setTouch(int nesMask);
    static native int touchMask();
    static native void setControlGutter(int percent);
    static native void setPaused(boolean paused);
    static native void configure(int[] settings, int[] buttons, int deadzone, String device);
    static native void safeArea(int left,int top,int right,int bottom,int width,int height);
    static native int state();
    static native String result();
    static native String diagnostics();
    static native String controllers();
}
