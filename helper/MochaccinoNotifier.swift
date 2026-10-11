import AppKit
import UserNotifications

var title = "", body = "", button = "Restart"
var parentPid: Int32 = 0

var arguments = CommandLine.arguments.dropFirst().makeIterator()
while let flag = arguments.next() {
	switch flag {
	case "--title": title = arguments.next() ?? title
	case "--body": body = arguments.next() ?? body
	case "--button": button = arguments.next() ?? button
	case "--pid": parentPid = Int32(arguments.next() ?? "") ?? 0
	default: break
	}
}

final class Delegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
	func applicationDidFinishLaunching(_ notification: Notification) {
		let center = UNUserNotificationCenter.current()
		center.delegate = self

		// no .foreground option: pressing the button must not bring this helper to the front
		let restart = UNNotificationAction(identifier: "restart", title: button, options: [])
		let category = UNNotificationCategory(identifier: "restart", actions: [restart], intentIdentifiers: [], options: [])
		center.setNotificationCategories([category])

		center.requestAuthorization(options: [.alert]) { granted, _ in
			guard granted else { exit(2) }

			let content = UNMutableNotificationContent()
			content.title = title
			content.body = body
			content.categoryIdentifier = "restart"
			// a fixed identifier, so posting again replaces the earlier notification
			let request = UNNotificationRequest(identifier: "mochaccino-restart", content: content, trigger: nil)
			center.add(request) { error in
				if error != nil { exit(3) }
			}
		}

		if parentPid > 0 {
			Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { _ in
				if kill(parentPid, 0) != 0 { exit(0) }
			}
		}
	}

	func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
		withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
		completionHandler([.banner, .list])
	}

	func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
		withCompletionHandler completionHandler: @escaping () -> Void) {
		switch response.actionIdentifier {
		case "restart":
			print("restart")
		case UNNotificationDefaultActionIdentifier:
			print("focus")
		default:
			completionHandler()
			return
		}
		fflush(stdout)
		completionHandler()
		exit(0)
	}
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let delegate = Delegate()
app.delegate = delegate
app.run()
