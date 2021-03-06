/*
 * Copyright © 2013-2014 Jason Ekstrand
 *
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting documentation, and
 * that the name of the copyright holders not be used in advertising or
 * publicity pertaining to distribution of the software without specific,
 * written prior permission.  The copyright holders make no representations
 * about the suitability of this software for any purpose.  It is provided "as
 * is" without express or implied warranty.
 *
 * THE COPYRIGHT HOLDERS DISCLAIM ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
 * INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO
 * EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY SPECIAL, INDIRECT OR
 * CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THIS SOFTWARE.
 */
package net.jlekstrand.wheatley.wayland;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.WindowManager;

import net.jlekstrand.wheatley.config.Client;

public class Compositor
{
    private static final String LOG_TAG = "wheatley:wayland.Compositor";

    private long _nativeHandle;

    private static native long createNative();
    private static native void destroyNative(long nativeHandle);
    private static native void launchClientNative(long nativeHandle,
            byte[] command, boolean runAsRoot);
    private static native void addToLooperNative(long nativeHandle);
    private static native void removeFromLooperNative(long nativeHandle);

    private static native void hasKeyboardNative(long nativeHandle,
            boolean hasKeyboard);
    private static native void hasTouchNative(long nativeHandle,
            boolean hasTouch);
    private static native void hasPointerNative(long nativeHandle,
            boolean hasPointer);

    private Output _output;
    private Seat _seat;
    private Touch _touch;
    private Pointer _pointer;
    private Keyboard _keyboard;

    private Thread _assignedThread;

    public Compositor(Context context)
    {
        WindowManager wm =
                (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        _nativeHandle = createNative();

        _output = new Output(this, wm.getDefaultDisplay());
        _seat = new Seat(this);
        _touch = new Touch(_seat);
        _pointer = new Pointer(_seat);
        _keyboard = new Keyboard(_seat);

        _assignedThread = null;
    }

    public Compositor(Context context, Client client)
    {
        this(context);
        launchClient(client);
    }

    long getNativeHandle()
    {
        return _nativeHandle;
    }

    /**
     * Ensures that the Compositor is not assigned to some other thread's
     * Looper
     */
    private void ensureCorrectThread()
    {
        if (_assignedThread != null && _assignedThread != Thread.currentThread())
            throw new IllegalStateException("Method called from the wrong thread");
    }

    public Output getPrimaryOutput()
    {
        ensureCorrectThread();

        return _output;
    }

    public synchronized void launchClient(Client client)
    {
        ensureCorrectThread();

        launchClientNative(_nativeHandle, client.getCommand().getBytes(),
                client.getRunAsRoot());
    }

    public void addToLooper()
    {
        if (_assignedThread != null)
            throw new IllegalStateException("Compositor already assigned to a looper");

        addToLooperNative(_nativeHandle);

        _assignedThread = Thread.currentThread();
    }

    public synchronized void removeFromLooper()
    {
        if (_assignedThread == null)
            return;

        removeFromLooperNative(_nativeHandle);

        _assignedThread = null;
    }

    private boolean onMotionEvent(MotionEvent event, Output output)
    {
        ensureCorrectThread();

        if (event.getSource() == InputDevice.SOURCE_MOUSE)
            return _pointer.onMotionEvent(event, output);
        else if (event.getSource() == InputDevice.SOURCE_TOUCHSCREEN)
            return _touch.onTouchEvent(event, output);

        return false;
    }

    public boolean onMotionEvent(MotionEvent event)
    {
        ensureCorrectThread();

        return onMotionEvent(event, _output);
    }

    public boolean onKeyEvent(KeyEvent event)
    {
        ensureCorrectThread();

        return _keyboard.onKeyEvent(event);
    }

    public void destroy()
    {
        if (_nativeHandle == 0)
            return;

        _touch.destroy();
        _pointer.destroy();
        _keyboard.destroy();
        _seat.destroy();

        _output.destroy();

        destroyNative(_nativeHandle);
        _nativeHandle = 0;
    }

    static {
        System.loadLibrary("wheatley");
    }
}

// vim: set ts=4 sw=4 sts=4 expandtab:
