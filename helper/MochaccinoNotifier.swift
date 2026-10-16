import AppKit
import UserNotifications

var title = "", body = ""
var parentPid: Int32 = 0
let notificationId = UUID().uuidString

var arguments = CommandLine.arguments.dropFirst().makeIterator()
while let flag = arguments.next() {
	switch flag {
	case "--title": title = arguments.next() ?? title
	case "--body": body = arguments.next() ?? body
	case "--pid": parentPid = Int32(arguments.next() ?? "") ?? 0
	default: break
	}
}

final class Delegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
	func applicationDidFinishLaunching(_ notification: Notification) {
		let center = UNUserNotificationCenter.current()
		center.delegate = self

		center.removeAllDeliveredNotifications()
		center.removeAllPendingNotificationRequests()

		center.requestAuthorization(options: [.alert]) { granted, _ in
			guard granted else { exit(2) }

			let content = UNMutableNotificationContent()
			content.title = title
			content.body = body
			let request = UNNotificationRequest(identifier: notificationId, content: content, trigger: nil)
			center.add(request) { error in
				if error != nil { exit(3) }
			}
		}

		if parentPid > 0 {
			Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { _ in
				if kill(parentPid, 0) != 0 { removeAndExit() }
			}
		}
	}

	func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
		withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
		completionHandler([.banner, .list])
	}

	func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
		withCompletionHandler completionHandler: @escaping () -> Void) {
		// clicking the notification only brings the game forward. dismissing it does nothing
		guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else {
			completionHandler()
			return
		}
		print("focus")
		fflush(stdout)
		completionHandler()
		removeAndExit()
	}
}

func removeAndExit() {
	UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [notificationId])
	DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { exit(0) }
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let delegate = Delegate()
app.delegate = delegate
app.run()
