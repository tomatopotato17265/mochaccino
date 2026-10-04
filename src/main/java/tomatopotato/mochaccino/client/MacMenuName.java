package tomatopotato.mochaccino.client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.LibFFI;
import org.lwjgl.system.macosx.ObjCRuntime;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;

// renames the menu bar app name from "java" by talking to AppKit through LWJGL's ObjCRuntime
public final class MacMenuName {
	private static final long MSG_SEND = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");

	private MacMenuName() {
	}

	public static void rename(String name) {
		long app = send(ObjCRuntime.objc_getClass("NSApplication"), "sharedApplication");
		long mainMenu = send(app, "mainMenu");
		if (mainMenu == ObjCRuntime.nil) {
			return;
		}

		long appItem = send(mainMenu, "itemAtIndex:", 0L);
		long appMenu = send(appItem, "submenu");
		long title = nsString(name);

		send(appItem, "setTitle:", title);
		if (appMenu != ObjCRuntime.nil) {
			send(appMenu, "setTitle:", title);
			renameItems(appMenu, name);
		}
	}

	// sets the Dock icon from encoded image data (PNG, etc.)
	public static void setIcon(byte[] image) {
		java.nio.ByteBuffer bytes = MemoryUtil.memAlloc(image.length);
		try {
			bytes.put(image).flip();

			long data = send(ObjCRuntime.objc_getClass("NSData"), "dataWithBytes:length:", MemoryUtil.memAddress(bytes), image.length);
			long icon = send(send(ObjCRuntime.objc_getClass("NSImage"), "alloc"), "initWithData:", data);
			if (icon == ObjCRuntime.nil) {
				return;
			}
			long app = send(ObjCRuntime.objc_getClass("NSApplication"), "sharedApplication");
			send(app, "setApplicationIconImage:", icon);
		} finally {
			MemoryUtil.memFree(bytes);
		}
	}

	// wires the application menu's existing "Settings…" item to run the given action
	public static void addSettingsItem(Runnable action) {
		long app = send(ObjCRuntime.objc_getClass("NSApplication"), "sharedApplication");
		long mainMenu = send(app, "mainMenu");
		if (mainMenu == ObjCRuntime.nil) {
			return;
		}
		long appMenu = send(send(mainMenu, "itemAtIndex:", 0L), "submenu");
		if (appMenu == ObjCRuntime.nil) {
			return;
		}

		actionCallback = new ActionCallback(action);
		long targetClass = ObjCRuntime.objc_allocateClassPair(ObjCRuntime.objc_getClass("NSObject"), "MochaccinoMenuTarget", 0);
		if (targetClass == ObjCRuntime.nil) {
			return;
		}

		ObjCRuntime.class_addMethod(targetClass, ObjCRuntime.sel_registerName("openSettings:"), actionCallback.address(), "v@:@");
		ObjCRuntime.objc_registerClassPair(targetClass);
		long target = send(send(targetClass, "alloc"), "init");

		long count = send(appMenu, "numberOfItems");
		for (long i = 0; i < count; i++) {
			long item = send(appMenu, "itemAtIndex:", i);
			if (javaString(send(item, "keyEquivalent")).equals(",")) {
				send(item, "setTarget:", target);
				send(item, "setAction:", ObjCRuntime.sel_registerName("openSettings:"));
				return;
			}
		}
	}

	public static void keepRenderingWhileTracking(Runnable frame) {
		timerCallback = new ActionCallback(frame);
		long targetClass = ObjCRuntime.objc_allocateClassPair(ObjCRuntime.objc_getClass("NSObject"), "MochaccinoTimerTarget", 0);
		if (targetClass == ObjCRuntime.nil) {
			return;
		}
		long tick = ObjCRuntime.sel_registerName("tick:");
		ObjCRuntime.class_addMethod(targetClass, tick, timerCallback.address(), "v@:@");
		ObjCRuntime.objc_registerClassPair(targetClass);
		long target = send(send(targetClass, "alloc"), "init");

		try (MemoryStack stack = MemoryStack.stackPush()) {
			FFICIF cif = APIUtil.apiCreateCIF(
				LibFFI.ffi_type_pointer,
				LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_double,
				LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_uint8
			);

			PointerBuffer args = stack.mallocPointer(7);
			args.put(0, pointerTo(stack, ObjCRuntime.objc_getClass("NSTimer")));
			args.put(1, pointerTo(stack, ObjCRuntime.sel_registerName("timerWithTimeInterval:target:selector:userInfo:repeats:")));
			args.put(2, MemoryUtil.memAddress(stack.malloc(8).putDouble(0, 1.0 / 60.0)));
			args.put(3, pointerTo(stack, target));
			args.put(4, pointerTo(stack, tick));
			args.put(5, pointerTo(stack, ObjCRuntime.nil));
			args.put(6, MemoryUtil.memAddress(stack.malloc(1).put(0, (byte) 1)));
			ByteBuffer result = stack.malloc(8);
			LibFFI.ffi_call(cif, MSG_SEND, result, args);

			long timer = result.getLong(0);
			long runLoop = send(ObjCRuntime.objc_getClass("NSRunLoop"), "currentRunLoop");
			send(runLoop, "addTimer:forMode:", timer, nsString("NSEventTrackingRunLoopMode"));
		}
	}

	private static long pointerTo(MemoryStack stack, long value) {
		return MemoryUtil.memAddress(stack.malloc(8).putLong(0, value));
	}

	// adds File, Edit and View after the application menu, and Help after the existing Window menu
	public static void addStandardMenus() {
		long app = send(ObjCRuntime.objc_getClass("NSApplication"), "sharedApplication");
		long mainMenu = send(app, "mainMenu");
		if (mainMenu == ObjCRuntime.nil) {
			return;
		}

		long file = addMenu(mainMenu, "File", 1L);
		addItem(file, "Close Window", "performClose:", "w");

		long edit = addMenu(mainMenu, "Edit", 2L);
		addItem(edit, "Undo", "undo:", "z");
		addItem(edit, "Redo", "redo:", "Z");
		addSeparator(edit);
		addItem(edit, "Cut", "cut:", "x");
		addItem(edit, "Copy", "copy:", "c");
		addItem(edit, "Paste", "paste:", "v");
		addItem(edit, "Select All", "selectAll:", "a");

		long view = addMenu(mainMenu, "View", 3L);
		long fullScreen = addItem(view, "Enter Full Screen", "toggleFullScreen:", "f");

		send(fullScreen, "setKeyEquivalentModifierMask:", (1L << 20) | (1L << 18));

		long help = addMenu(mainMenu, "Help", send(mainMenu, "numberOfItems"));
		send(app, "setHelpMenu:", help);
	}

	private static long addMenu(long mainMenu, String title, long index) {
		long item = send(send(ObjCRuntime.objc_getClass("NSMenuItem"), "alloc"), "init");
		long menu = send(send(ObjCRuntime.objc_getClass("NSMenu"), "alloc"), "initWithTitle:", nsString(title));
		send(item, "setSubmenu:", menu);
		JNI.invokePPPPP(mainMenu, ObjCRuntime.sel_registerName("insertItem:atIndex:"), item, index, MSG_SEND);
		return menu;
	}

	private static long addItem(long menu, String title, String action, String key) {
		long item = JNI.invokePPPPPP(
			send(ObjCRuntime.objc_getClass("NSMenuItem"), "alloc"),
			ObjCRuntime.sel_registerName("initWithTitle:action:keyEquivalent:"),
			nsString(title),
			ObjCRuntime.sel_registerName(action),
			nsString(key),
			MSG_SEND
		);
		send(menu, "addItem:", item);
		return item;
	}

	private static void addSeparator(long menu) {
		send(menu, "addItem:", send(ObjCRuntime.objc_getClass("NSMenuItem"), "separatorItem"));
	}

	private static ActionCallback actionCallback;
	private static ActionCallback timerCallback;

	@FunctionalInterface
	private interface ActionCallbackI extends CallbackI {
		Callback.Descriptor DESCRIPTOR = new Callback.Descriptor(
			ActionCallbackI.class,
			MethodHandles.lookup(),
			APIUtil.apiCreateCIF(LibFFI.ffi_type_void, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer)
		);

		@Override
		default Callback.Descriptor getDescriptor() {
			return DESCRIPTOR;
		}

		@Override
		default void callback(long ret, long args) {
			invoke();
		}
		void invoke();
	}

	private static final class ActionCallback extends Callback implements ActionCallbackI {
		private final Runnable action;

		ActionCallback(Runnable action) {
			super(DESCRIPTOR);
			this.action = action;
		}

		@Override
		public void invoke() {
			action.run();
		}
	}

	// "About java", "Hide java", "Quit java", etc
	private static void renameItems(long menu, String name) {
		long count = send(menu, "numberOfItems");
		for (long i = 0; i < count; i++) {
			long item = send(menu, "itemAtIndex:", i);
			String itemTitle = javaString(send(item, "title"));
			if (itemTitle.contains("java")) {
				send(item, "setTitle:", nsString(itemTitle.replace("java", name)));
			}
		}
	}

	private static long send(long receiver, String selector) {
		return JNI.invokePPP(receiver, ObjCRuntime.sel_registerName(selector), MSG_SEND);
	}

	private static long send(long receiver, String selector, long arg) {
		return JNI.invokePPPP(receiver, ObjCRuntime.sel_registerName(selector), arg, MSG_SEND);
	}

	private static long send(long receiver, String selector, long arg1, long arg2) {
		return JNI.invokePPPPP(receiver, ObjCRuntime.sel_registerName(selector), arg1, arg2, MSG_SEND);
	}

	private static long nsString(String value) {
		java.nio.ByteBuffer utf8 = MemoryUtil.memUTF8(value);
		try {
			return send(ObjCRuntime.objc_getClass("NSString"), "stringWithUTF8String:", MemoryUtil.memAddress(utf8));
		} finally {
			MemoryUtil.memFree(utf8);
		}
	}

	private static String javaString(long nsString) {
		if (nsString == ObjCRuntime.nil) {
			return "";
		}
		String value = MemoryUtil.memUTF8Safe(send(nsString, "UTF8String"));
		return value == null ? "" : value;
	}
}
