package tomatopotato.mochaccino.client;

import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.macosx.ObjCRuntime;

/**
 * Renames the macOS menu bar app name from "java" by talking to AppKit through LWJGL's ObjCRuntime.
 */
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
