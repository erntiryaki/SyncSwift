import SwiftUI
import UserNotifications
import ComposeApp

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound, .provisional]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }

        // If app was launched from a tapped remote notification (cold start)
        if let remoteNotification = launchOptions?[.remoteNotification] as? [AnyHashable : Any] {
            print("[SyncSwift iOS] Launched from cold-start remote notification: \(remoteNotification)")
            // Wait slightly so Compose UI hierarchy and StateFlow collectors are ready
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                Platform_iosKt.onIosRemoteNotificationReceived(userInfo: remoteNotification)
            }
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        print("[SyncSwift iOS] APNs Device Token registered: \(token)")
        UserDefaults.standard.set(token, forKey: "syncswift_push_token")
        Platform_iosKt.onIosPushTokenReceived(token: token)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[SyncSwift iOS] Failed to register for remote notifications: \(error.localizedDescription)")
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey : Any] = [:]
    ) -> Bool {
        return true
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable : Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        print("[SyncSwift iOS] didReceiveRemoteNotification: \(userInfo)")
        DispatchQueue.main.async {
            Platform_iosKt.onIosRemoteNotificationReceived(userInfo: userInfo)
        }
        completionHandler(.newData)
    }

    // Show banners even when app is active in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        print("[SyncSwift iOS] userNotificationCenter willPresent: \(userInfo)")
        DispatchQueue.main.async {
            Platform_iosKt.onIosRemoteNotificationReceived(userInfo: userInfo)
        }
        completionHandler([.banner, .sound, .badge])
    }

    // Handle user tap on notification
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        print("[SyncSwift iOS] userNotificationCenter didReceive tap: \(userInfo)")
        DispatchQueue.main.async {
            Platform_iosKt.onIosRemoteNotificationReceived(userInfo: userInfo)
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
